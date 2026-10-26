package io.github.geekdex.subout.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.geekdex.subout.SuboutApplication
import io.github.geekdex.subout.data.db.entities.Subscription
import io.github.geekdex.subout.data.repository.ConfigRepository
import io.github.geekdex.subout.data.repository.NodeRepository
import io.github.geekdex.subout.data.repository.SubscriptionRepository
import io.github.geekdex.subout.data.repository.NodeGroupRepository
import io.github.geekdex.subout.domain.service.DataSyncService
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
    private val nodeRepository: NodeRepository,
    private val nodeGroupRepository: NodeGroupRepository,
    private val dataSyncService: DataSyncService
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
                subscriptionRepository.deleteSubscription(id)
                // 订阅删除后，全面同步数据至配置：清理组、校验并重置配置引用、即时重新生成 sing-box 配置
                val syncResult = dataSyncService.syncDataToConfig()
                if (syncResult.configResetList.isNotEmpty()) {
                    _message.value = "已删除订阅，涉及的规则【${syncResult.configResetList.joinToString("、")}】已重置为直连 (direct)"
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
            try {
                subscriptionRepository.setSubscriptionEnabled(id, enabled)
                val syncResult = dataSyncService.syncDataToConfig()
                val action = if (enabled) "启用" else "禁用"
                if (syncResult.configResetList.isNotEmpty()) {
                    _message.value = "已${action}订阅，涉及的规则【${syncResult.configResetList.joinToString("、")}】已重置为 direct"
                } else {
                    _message.value = "已${action}订阅"
                }
            } catch (e: Exception) {
                _message.value = "更新状态失败: ${e.message}"
            }
        }
    }

    /**
     * 同步单个订阅：
     * 1. 对应订阅旧节点全量清空，新节点完整替换入库
     * 2. 调用 DataSyncService 完成全链路数据同步至配置：
     *    - 刷新动态与静态分组
     *    - 校验配置，若原规则指向的节点在新数据中仍有同名项则保留，无同名项则重置为 direct
     *    - 立即重新生成 sing-box.json 并更新服务缓存与导出文件
     */
    fun syncSubscription(id: Long) {
        viewModelScope.launch {
            _syncingSubIds.value = _syncingSubIds.value + id
            try {
                val res = subscriptionRepository.syncSubscription(id)
                if (res.isSuccess) {
                    val syncResult = dataSyncService.syncDataToConfig()
                    val nodeCount = res.getOrNull() ?: 0
                    if (syncResult.configResetList.isNotEmpty()) {
                        _message.value = "同步完成，获取到 $nodeCount 个节点。已失效规则【${syncResult.configResetList.joinToString("、")}】已自动重置为 direct"
                    } else {
                        _message.value = "同步完成，获取到 $nodeCount 个节点"
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

    /**
     * 同步所有已启用的订阅源：
     * 所有订阅原本的节点数据全部清空并由新数据替换，并全量同步到配置中
     */
    fun syncAllSubscriptions() {
        viewModelScope.launch {
            val subs = uiState.value.subscriptions.filter { it.enabled }
            if (subs.isEmpty()) {
                _message.value = "暂无可同步的已启用订阅源"
                return@launch
            }
            _syncingSubIds.value = subs.map { it.id }.toSet()
            try {
                val results = subscriptionRepository.syncAll()
                val syncResult = dataSyncService.syncDataToConfig()
                val successCount = results.values.count { it.isSuccess }
                val totalNodes = results.values.sumOf { it.getOrNull() ?: 0 }
                val baseMsg = "全量同步完成: 成功 $successCount/${subs.size} 个订阅，共 $totalNodes 个节点"
                if (syncResult.configResetList.isNotEmpty()) {
                    _message.value = "$baseMsg。已失效规则【${syncResult.configResetList.joinToString("、")}】已自动重置为 direct"
                } else {
                    _message.value = baseMsg
                }
            } catch (e: Exception) {
                _message.value = "全量同步异常: ${e.message}"
            } finally {
                _syncingSubIds.value = emptySet()
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
                    app.nodeRepository,
                    app.nodeGroupRepository,
                    app.dataSyncService
                ) as T
            }
        }
    }
}
