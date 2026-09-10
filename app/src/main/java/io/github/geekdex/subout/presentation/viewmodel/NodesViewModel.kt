package io.github.geekdex.subout.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.geekdex.subout.SuboutApplication
import io.github.geekdex.subout.data.db.entities.Node
import io.github.geekdex.subout.data.repository.ConfigRepository
import io.github.geekdex.subout.data.repository.NodeRepository
import io.github.geekdex.subout.domain.generator.ConfigExporter
import io.github.geekdex.subout.domain.generator.SimpleConfigGenerator
import io.github.geekdex.subout.domain.model.ManualNodeConfig
import io.github.geekdex.subout.domain.server.ConfigServer
import io.github.geekdex.subout.domain.tester.NodeTester
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicInteger

enum class NodeSortOption {
    DEFAULT,
    LATENCY,
    NAME,
    PROTOCOL
}

data class NodesUiState(
    val rawNodes: List<Node> = emptyList(),
    val filteredNodes: List<Node> = emptyList(),
    val searchQuery: String = "",
    val selectedProtocol: String = "ALL",
    val sortOption: NodeSortOption = NodeSortOption.DEFAULT,
    val testingNodeIds: Set<Long> = emptySet(),
    val isTestingAll: Boolean = false,
    val totalTestingCount: Int = 0,
    val isSelectionMode: Boolean = false,
    val selectedNodeIds: Set<Long> = emptySet(),
    val message: String? = null
)

class NodesViewModel(
    private val nodeRepository: NodeRepository,
    private val configRepository: ConfigRepository,
    private val configExporter: ConfigExporter,
    private val configServer: ConfigServer
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _selectedProtocol = MutableStateFlow("ALL")
    private val _sortOption = MutableStateFlow(NodeSortOption.DEFAULT)
    private val _testingNodeIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _isTestingAll = MutableStateFlow(false)
    private val _totalTestingCount = MutableStateFlow(0)
    private val _isSelectionMode = MutableStateFlow(false)
    private val _selectedNodeIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _message = MutableStateFlow<String?>(null)

    private var batchTestJob: Job? = null

    private data class FilterParams(
        val query: String,
        val protocol: String,
        val sort: NodeSortOption
    )

    private data class StatusParams(
        val testingIds: Set<Long>,
        val testingAll: Boolean,
        val totalCount: Int,
        val isSelection: Boolean,
        val selectedIds: Set<Long>,
        val msg: String?
    )

    private val filterFlow = combine(_searchQuery, _selectedProtocol, _sortOption) { query, protocol, sort ->
        FilterParams(query, protocol, sort)
    }

    private val testingStatusFlow = combine(_testingNodeIds, _isTestingAll, _totalTestingCount) { ids, all, total ->
        Triple(ids, all, total)
    }

    private val statusFlow = combine(
        testingStatusFlow,
        _isSelectionMode,
        _selectedNodeIds,
        _message
    ) { (ids, all, total), selection, selectedIds, msg ->
        StatusParams(ids, all, total, selection, selectedIds, msg)
    }

    val uiState: StateFlow<NodesUiState> = combine(
        nodeRepository.nodes,
        filterFlow,
        statusFlow
    ) { nodes, filter, status ->
        val filtered = nodes.filter { node ->
            val matchesQuery = filter.query.isBlank() ||
                    node.tag.contains(filter.query, ignoreCase = true) ||
                    node.server.contains(filter.query, ignoreCase = true)
            val matchesProtocol = filter.protocol == "ALL" ||
                    node.protocol.equals(filter.protocol, ignoreCase = true)
            matchesQuery && matchesProtocol
        }

        val sorted = when (filter.sort) {
            NodeSortOption.DEFAULT -> filtered
            NodeSortOption.NAME -> filtered.sortedBy { it.tag.lowercase() }
            NodeSortOption.PROTOCOL -> filtered.sortedBy { it.protocol }
            NodeSortOption.LATENCY -> filtered.sortedWith(
                compareBy<Node> {
                    when {
                        it.latency == null -> 2
                        it.latency < 0 -> 1
                        else -> 0
                    }
                }.thenBy { it.latency ?: Int.MAX_VALUE }
            )
        }

        NodesUiState(
            rawNodes = nodes,
            filteredNodes = sorted,
            searchQuery = filter.query,
            selectedProtocol = filter.protocol,
            sortOption = filter.sort,
            testingNodeIds = status.testingIds,
            isTestingAll = status.testingAll,
            totalTestingCount = status.totalCount,
            isSelectionMode = status.isSelection,
            selectedNodeIds = status.selectedIds,
            message = status.msg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = NodesUiState()
    )

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedProtocol(protocol: String) {
        _selectedProtocol.value = protocol
    }

    fun setSortOption(sortOption: NodeSortOption) {
        _sortOption.value = sortOption
    }

    fun toggleNodeEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch {
            nodeRepository.setNodeEnabled(id, enabled)
        }
    }

    fun setAllEnabled(enabled: Boolean) {
        viewModelScope.launch {
            nodeRepository.setAllNodesEnabled(enabled)
            _message.value = if (enabled) "已启用全部节点" else "已禁用全部节点"
        }
    }

    fun testNode(node: Node) {
        if (_testingNodeIds.value.contains(node.id)) return

        viewModelScope.launch {
            _testingNodeIds.update { it + node.id }
            try {
                val startTime = System.currentTimeMillis()
                val latency = NodeTester.testNodePing(node)
                val elapsed = System.currentTimeMillis() - startTime
                if (elapsed < 250) {
                    delay(250 - elapsed)
                }
                nodeRepository.updateNodeLatency(node.id, latency)
                if (latency == Node.LATENCY_TIMEOUT) {
                    _message.value = "节点「${node.tag}」连接超时"
                }
            } finally {
                _testingNodeIds.update { it - node.id }
            }
        }
    }

    fun testAllVisibleNodes() {
        val visibleNodes = uiState.value.filteredNodes
        if (visibleNodes.isEmpty() || _isTestingAll.value) return

        batchTestJob?.cancel()
        batchTestJob = viewModelScope.launch(Dispatchers.IO) {
            _isTestingAll.value = true
            _totalTestingCount.value = visibleNodes.size
            _testingNodeIds.value = visibleNodes.map { it.id }.toSet()

            val semaphore = Semaphore(10)
            val successCount = AtomicInteger(0)
            val timeoutCount = AtomicInteger(0)

            try {
                coroutineScope {
                    visibleNodes.forEach { node ->
                        launch {
                            semaphore.withPermit {
                                val latency = NodeTester.testNodePing(node)
                                nodeRepository.updateNodeLatency(node.id, latency)
                                if (latency >= 0) {
                                    successCount.incrementAndGet()
                                } else {
                                    timeoutCount.incrementAndGet()
                                }
                                _testingNodeIds.update { it - node.id }
                            }
                        }
                    }
                }
                _message.value = "测速完成: 可用 ${successCount.get()} 个，超时 ${timeoutCount.get()} 个"
            } catch (e: CancellationException) {
                _message.value = "已停止测速"
            } catch (e: Exception) {
                _message.value = "测速出错: ${e.message}"
            } finally {
                _isTestingAll.value = false
                _totalTestingCount.value = 0
                _testingNodeIds.value = emptySet()
            }
        }
    }

    fun cancelBatchTesting() {
        batchTestJob?.cancel()
    }

    fun clearLatencies() {
        viewModelScope.launch {
            nodeRepository.clearAllLatencies()
            _message.value = "已清空测速结果"
        }
    }

    fun enterSelectionMode(initialId: Long? = null) {
        _isSelectionMode.value = true
        if (initialId != null) {
            _selectedNodeIds.value = setOf(initialId)
        }
    }

    fun exitSelectionMode() {
        _isSelectionMode.value = false
        _selectedNodeIds.value = emptySet()
    }

    fun toggleNodeSelection(id: Long) {
        _selectedNodeIds.update { current ->
            if (current.contains(id)) current - id else current + id
        }
    }

    fun selectAllFilteredNodes() {
        val allIds = uiState.value.filteredNodes.map { it.id }.toSet()
        _selectedNodeIds.value = allIds
    }

    fun clearSelection() {
        _selectedNodeIds.value = emptySet()
    }

    fun checkNodeUsage(tags: Set<String>): List<String> {
        return configRepository.checkNodeUsage(tags)
    }

    private suspend fun syncExportAfterReset(resetList: List<String>) {
        if (resetList.isNotEmpty()) {
            val currentConfig = configRepository.configState.value
            val currentNodes = nodeRepository.getEnabledNodes()
            val jsonStr = SimpleConfigGenerator.generatePrettyString(currentConfig, currentNodes)
            configServer.updateContent(jsonStr)
            configExporter.exportToFile(jsonStr)
        }
    }

    fun deleteSelectedNodes() {
        val ids = _selectedNodeIds.value.toList()
        if (ids.isEmpty()) return

        viewModelScope.launch {
            val nodesToDelete = nodeRepository.getNodesByIds(ids)
            val tags = nodesToDelete.map { it.tag }.toSet()
            val resetList = configRepository.resetOutboundIfUsed(tags)
            val count = nodeRepository.deleteNodesByIds(ids)
            exitSelectionMode()
            syncExportAfterReset(resetList)
            if (resetList.isNotEmpty()) {
                _message.value = "已删除 $count 个节点，涉及的【${resetList.joinToString("、")}】已自动重置为直连 (direct)"
            } else {
                _message.value = "已删除 $count 个节点"
            }
        }
    }

    fun setSelectedNodesEnabled(enabled: Boolean) {
        val ids = _selectedNodeIds.value.toList()
        if (ids.isEmpty()) return

        viewModelScope.launch {
            val count = nodeRepository.setNodesEnabled(ids, enabled)
            val action = if (enabled) "启用" else "禁用"
            _message.value = "已${action} $count 个节点"
            exitSelectionMode()
        }
    }

    fun deleteTimeoutNodes() {
        viewModelScope.launch {
            val timeoutNodes = nodeRepository.getTimeoutNodes()
            val tags = timeoutNodes.map { it.tag }.toSet()
            val resetList = configRepository.resetOutboundIfUsed(tags)
            val count = nodeRepository.deleteTimeoutNodes()
            syncExportAfterReset(resetList)
            if (count > 0) {
                if (resetList.isNotEmpty()) {
                    _message.value = "已删除 $count 个超时节点，涉及的【${resetList.joinToString("、")}】已自动重置为直连 (direct)"
                } else {
                    _message.value = "已删除 $count 个超时节点"
                }
            } else {
                _message.value = "暂无超时节点需要清理"
            }
        }
    }

    fun deleteDisabledNodes() {
        viewModelScope.launch {
            val disabledNodes = nodeRepository.getDisabledNodes()
            val tags = disabledNodes.map { it.tag }.toSet()
            val resetList = configRepository.resetOutboundIfUsed(tags)
            val count = nodeRepository.deleteDisabledNodes()
            syncExportAfterReset(resetList)
            if (count > 0) {
                if (resetList.isNotEmpty()) {
                    _message.value = "已删除 $count 个已禁用节点，涉及的【${resetList.joinToString("、")}】已自动重置为直连 (direct)"
                } else {
                    _message.value = "已删除 $count 个已禁用节点"
                }
            } else {
                _message.value = "暂无已禁用节点需要清理"
            }
        }
    }

    fun deleteNode(node: Node) {
        viewModelScope.launch {
            val resetList = configRepository.resetOutboundIfUsed(setOf(node.tag))
            nodeRepository.deleteNode(node)
            syncExportAfterReset(resetList)
            if (resetList.isNotEmpty()) {
                _message.value = "已删除节点「${node.tag}」，所引用的【${resetList.joinToString("、")}】已自动重置为直连 (direct)"
            } else {
                _message.value = "已删除节点「${node.tag}」"
            }
        }
    }

    fun addManualNode(config: ManualNodeConfig, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val existingNodes = nodeRepository.getAllNodesSync()
                if (existingNodes.any { it.tag.equals(config.tag.trim(), ignoreCase = true) }) {
                    onResult(false, "节点名称「${config.tag.trim()}」已存在，请使用其他名称")
                    return@launch
                }
                val node = config.toNode()
                nodeRepository.insertNode(node)
                _message.value = "已成功添加节点「${config.tag}」"
                onResult(true, "添加成功")
            } catch (e: Exception) {
                onResult(false, "添加失败: ${e.message}")
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    companion object {
        fun Factory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = SuboutApplication.instance
                return NodesViewModel(
                    app.nodeRepository,
                    app.configRepository,
                    app.configExporter,
                    app.configServer
                ) as T
            }
        }
    }
}
