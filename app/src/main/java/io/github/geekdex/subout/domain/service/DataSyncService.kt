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
     * 订阅同步或节点变动完成后的数据一致性清理
     * 
     * 流程：
     * 1. 清理静态分组中已失效或被删除的节点
     * 2. 刷新所有动态分组（根据规则重新计算匹配，自动增删节点）
     * 3. 统计当前所有有效启用的节点与有效非空的节点组
     * 4. 校验配置，如果引用的节点丢失或节点组变空/失效，自动将其重置为 "direct"
     */
    suspend fun syncSubscriptionsCompleted(): SyncResult = withContext(Dispatchers.IO) {
        val allNodes = nodeRepository.getAllNodesSync()
        val allAvailableTags = allNodes.map { it.tag }.toSet()
        val availableEnabledTags = allNodes.filter { it.enabled }.map { it.tag }.toSet()

        // 1. 清理静态分组（仅清理 MANUAL 类型，移除已下线的节点）
        val initialGroups = nodeGroupRepository.getAllGroupsSync()
        for (group in initialGroups) {
            if (group.groupType == io.github.geekdex.subout.data.db.entities.NodeGroupType.MANUAL) {
                val currentTags = group.nodeTags
                val filtered = currentTags.filter { it in allAvailableTags }
                if (filtered.size != currentTags.size) {
                    nodeGroupRepository.updateGroup(group.copyWithNodeTags(filtered))
                }
            }
        }

        // 2. 刷新动态分组（根据当前最新节点重新计算匹配：自动新增符合条件的，移除不再符合条件的）
        nodeGroupRepository.refreshDynamicGroupsNodes()

        // 3. 获取更新后的所有节点组，统计有效且非空的节点组集合
        val updatedGroups = nodeGroupRepository.getAllGroupsSync()
        val validGroupNames = updatedGroups.filter { group ->
            group.enabled && group.nodeTags.isNotEmpty() && group.nodeTags.any { tag -> tag in availableEnabledTags }
        }.map { it.name }.toSet()

        // 4. 清理配置中失效的出站引用（单节点丢失、或节点组变空/失效，自动重置为 direct）
        val configResetList = configRepository.validateAndCleanMissingNodes(
            availableNodeTags = availableEnabledTags,
            availableGroupNames = validGroupNames
        )

        SyncResult(configResetList = configResetList)
    }

    /**
     * 同步结果数据类
     */
    data class SyncResult(
        val configResetList: List<String> = emptyList()
    )
}
