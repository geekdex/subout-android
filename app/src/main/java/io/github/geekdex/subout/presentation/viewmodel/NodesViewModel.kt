package io.github.geekdex.subout.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.geekdex.subout.SuboutApplication
import io.github.geekdex.subout.data.db.entities.Node
import io.github.geekdex.subout.data.repository.NodeRepository
import io.github.geekdex.subout.domain.tester.NodeTester
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
    private val _message = MutableStateFlow<String?>(null)

    private data class FilterParams(
        val query: String,
        val protocol: String,
        val sort: NodeSortOption
    )

    private data class StatusParams(
        val testingIds: Set<Long>,
        val testingAll: Boolean,
        val msg: String?
    )

    private val filterFlow = combine(_searchQuery, _selectedProtocol, _sortOption) { query, protocol, sort ->
        FilterParams(query, protocol, sort)
    }

    private val statusFlow = combine(_testingNodeIds, _isTestingAll, _message) { ids, all, msg ->
        StatusParams(ids, all, msg)
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
                compareBy<Node> { it.latency == null }
                    .thenBy { it.latency ?: Int.MAX_VALUE }
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
        viewModelScope.launch {
            _testingNodeIds.value = _testingNodeIds.value + node.id
            try {
                val latency = NodeTester.testTcpPing(node.server, node.serverPort)
                nodeRepository.updateNodeLatency(node.id, latency)
            } finally {
                _testingNodeIds.value = _testingNodeIds.value - node.id
            }
        }
    }

    fun testAllVisibleNodes() {
        val visibleNodes = uiState.value.filteredNodes
        if (visibleNodes.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            _isTestingAll.value = true
            _testingNodeIds.value = visibleNodes.map { it.id }.toSet()
            try {
                visibleNodes.map { node ->
                    async {
                        val latency = NodeTester.testTcpPing(node.server, node.serverPort)
                        nodeRepository.updateNodeLatency(node.id, latency)
                        _testingNodeIds.value = _testingNodeIds.value - node.id
                    }
                }.awaitAll()
                _message.value = "测速完成"
            } catch (e: Exception) {
                _message.value = "测速出错: ${e.message}"
            } finally {
                _isTestingAll.value = false
                _testingNodeIds.value = emptySet()
            }
        }
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
