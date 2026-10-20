package io.github.geekdex.subout.domain.service

import io.github.geekdex.subout.data.repository.ConfigRepository
import io.github.geekdex.subout.data.repository.NodeRepository
import io.github.geekdex.subout.data.repository.NodeGroupRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 数据同步服务
 * 负责在订阅更新、节点删除等操作时，保证：
 * 1. 配置中的出站引用不会指向已删除的节点
 * 2. 手动分组中不会包含已删除的节点
 * 3. 动态分组会自动重新计算以适应新的节点列表
 */
class DataSyncService(
    private val nodeRepository: NodeRepository,
    private val nodeGroupRepository: NodeGroupRepository,
    private val configRepository: ConfigRepository
) {

    /**
     * 订阅同步完成后的数据一致性清理
     * 
     * 流程：
     * 1. 获取当前可用节点列表
     * 2. 清理配置中失效的出站引用
     * 3. 清理手动分组中失效的节点
     * 4. 刷新所有动态分组
     */
    suspend fun syncSubscriptionsCompleted() = withContext(Dispatchers.IO) {
        val allNodes = nodeRepository.getAllNodesSync()
        val availableEnabledTags = allNodes.filter { it.enabled }.map { it.tag }.toSet()
        val allAvailableTags = allNodes.map { it.tag }.toSet()

        // 1. 清理配置
        val configResetList = configRepository.validateAndCleanMissingNodes(availableEnabledTags)

        // 2. 清理手动分组
        val allGroups = nodeGroupRepository.getAllGroupsSync()
        for (group in allGroups) {
            val currentTags = group.nodeTags
            val filtered = currentTags.filter { it in allAvailableTags }
            if (filtered.size != currentTags.size) {
                nodeGroupRepository.updateGroup(group.copyWithNodeTags(filtered))
            }
        }

        // 3. 刷新动态分组（重新计算匹配节点）
        nodeGroupRepository.refreshDynamicGroupsNodes()

        return@withContext SyncResult(
            configResetList = configResetList
        )
    }

    /**
     * 同步结果数据类
     */
    data class SyncResult(
        val configResetList: List<String> = emptyList()
    )
}
