package io.github.geekdex.subout.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.geekdex.subout.SuboutApplication
import io.github.geekdex.subout.data.repository.NodeRepository
import io.github.geekdex.subout.data.repository.SubscriptionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DashboardUiState(
    val subscriptionCount: Int = 0,
    val totalNodeCount: Int = 0,
    val enabledNodeCount: Int = 0,
    val lastSyncTime: Long? = null,
    val isSyncing: Boolean = false,
    val syncMessage: String? = null
)

class DashboardViewModel(
    private val subscriptionRepository: SubscriptionRepository,
    private val nodeRepository: NodeRepository
) : ViewModel() {

    private val _isSyncing = MutableStateFlow(false)
    private val _syncMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<DashboardUiState> = combine(
        subscriptionRepository.subscriptions,
        nodeRepository.nodes,
        _isSyncing,
        _syncMessage
    ) { subs, nodes, syncing, msg ->
        val latestSync = subs.mapNotNull { it.lastFetchTime }.maxOrNull()
        DashboardUiState(
            subscriptionCount = subs.size,
            totalNodeCount = nodes.size,
            enabledNodeCount = nodes.count { it.enabled },
            lastSyncTime = latestSync,
            isSyncing = syncing,
            syncMessage = msg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState()
    )

    fun syncAll() {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncMessage.value = null
            try {
                val results = subscriptionRepository.syncAll()
                val successCount = results.values.count { it.isSuccess }
                val totalNodes = results.values.sumOf { it.getOrNull() ?: 0 }
                _syncMessage.value = "同步完成: 成功 $successCount 个订阅，共 $totalNodes 个节点"
            } catch (e: Exception) {
                _syncMessage.value = "同步异常: ${e.message}"
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun clearMessage() {
        _syncMessage.value = null
    }

    companion object {
        fun Factory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = SuboutApplication.instance
                return DashboardViewModel(app.subscriptionRepository, app.nodeRepository) as T
            }
        }
    }
}
