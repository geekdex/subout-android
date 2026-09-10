package io.github.geekdex.subout.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.geekdex.subout.SuboutApplication
import io.github.geekdex.subout.data.db.entities.Subscription
import io.github.geekdex.subout.data.repository.ConfigRepository
import io.github.geekdex.subout.data.repository.NodeRepository
import io.github.geekdex.subout.data.repository.SubscriptionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SubscriptionsUiState(
    val subscriptions: List<Subscription> = emptyList(),
    val syncingSubIds: Set<Long> = emptySet(),
    val message: String? = null
)

class SubscriptionsViewModel(
    private val subscriptionRepository: SubscriptionRepository,
    private val configRepository: ConfigRepository,
    private val nodeRepository: NodeRepository
) : ViewModel() {

    private val _syncingSubIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SubscriptionsUiState> = combine(
        subscriptionRepository.subscriptions,
        _syncingSubIds,
        _message
    ) { subs, syncingIds, msg ->
        SubscriptionsUiState(
            subscriptions = subs,
            syncingSubIds = syncingIds,
            message = msg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SubscriptionsUiState()
    )

    fun addSubscription(name: String, url: String, syncNow: Boolean = true) {
        viewModelScope.launch {
            try {
                val id = subscriptionRepository.addSubscription(name, url)
                _message.value = "添加成功"
                if (syncNow) {
                    syncSubscription(id)
                }
            } catch (e: Exception) {
                _message.value = "添加失败: ${e.message}"
            }
        }
    }

    fun updateSubscription(subscription: Subscription) {
        viewModelScope.launch {
            try {
                subscriptionRepository.updateSubscription(subscription)
                _message.value = "更新成功"
            } catch (e: Exception) {
                _message.value = "更新失败: ${e.message}"
            }
        }
    }

    fun deleteSubscription(id: Long) {
        viewModelScope.launch {
            try {
                val nodesToDelete = subscriptionRepository.getNodesBySubscriptionId(id)
                val tags = nodesToDelete.map { it.tag }.toSet()
                val resetList = configRepository.resetOutboundIfUsed(tags)
                subscriptionRepository.deleteSubscription(id)
                if (resetList.isNotEmpty()) {
                    _message.value = "已删除订阅，涉及的规则【${resetList.joinToString("、")}】已重置为直连 (direct)"
                } else {
                    _message.value = "已删除订阅及相关节点"
                }
            } catch (e: Exception) {
                _message.value = "删除失败: ${e.message}"
            }
        }
    }

    fun toggleEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch {
            subscriptionRepository.setSubscriptionEnabled(id, enabled)
        }
    }

    fun syncSubscription(id: Long) {
        viewModelScope.launch {
            _syncingSubIds.value = _syncingSubIds.value + id
            try {
                val res = subscriptionRepository.syncSubscription(id)
                if (res.isSuccess) {
                    val allNodes = nodeRepository.getAllNodesSync()
                    val availableTags = allNodes.filter { it.enabled }.map { it.tag }.toSet()
                    val resetList = configRepository.validateAndCleanMissingNodes(availableTags)
                    if (resetList.isNotEmpty()) {
                        _message.value = "同步完成，获取到 ${res.getOrNull()} 个节点。已失效节点【${resetList.joinToString("、")}】已自动重置为 direct"
                    } else {
                        _message.value = "同步完成，获取到 ${res.getOrNull()} 个节点"
                    }
                } else {
                    _message.value = "同步失败: ${res.exceptionOrNull()?.message}"
                }
            } catch (e: Exception) {
                _message.value = "同步异常: ${e.message}"
            } finally {
                _syncingSubIds.value = _syncingSubIds.value - id
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
                return SubscriptionsViewModel(
                    app.subscriptionRepository,
                    app.configRepository,
                    app.nodeRepository
                ) as T
            }
        }
    }
}
