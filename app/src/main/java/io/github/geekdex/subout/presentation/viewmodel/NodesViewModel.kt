package io.github.geekdex.subout.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.geekdex.subout.SuboutApplication
import io.github.geekdex.subout.data.db.entities.Node
import io.github.geekdex.subout.data.repository.NodeRepository
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
    val message: String? = null
)

class NodesViewModel(
    private val nodeRepository: NodeRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _selectedProtocol = MutableStateFlow("ALL")
    private val _sortOption = MutableStateFlow(NodeSortOption.DEFAULT)
    private val _testingNodeIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _isTestingAll = MutableStateFlow(false)
    private val _totalTestingCount = MutableStateFlow(0)
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
        val msg: String?
    )

    private val filterFlow = combine(_searchQuery, _selectedProtocol, _sortOption) { query, protocol, sort ->
        FilterParams(query, protocol, sort)
    }

    private val statusFlow = combine(
        _testingNodeIds,
        _isTestingAll,
        _totalTestingCount,
        _message
    ) { ids, all, total, msg ->
        StatusParams(ids, all, total, msg)
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

    fun deleteNode(node: Node) {
        viewModelScope.launch {
            nodeRepository.deleteNode(node)
            _message.value = "已删除节点"
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    companion object {
        fun Factory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return NodesViewModel(SuboutApplication.instance.nodeRepository) as T
            }
        }
    }
}
