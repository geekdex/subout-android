package io.github.geekdex.subout.domain.service

import io.github.geekdex.subout.data.repository.ConfigRepository
import io.github.geekdex.subout.data.repository.NodeRepository
import io.github.geekdex.subout.data.repository.NodeGroupRepository
import io.github.geekdex.subout.domain.generator.ConfigExporter
import io.github.geekdex.subout.domain.generator.SimpleConfigGenerator
import io.github.geekdex.subout.domain.server.ConfigServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 数据同步服务
 * 负责在订阅更新、节点增删改、组合变动等操作时，将数据完整同步到配置：
 * 1. 清理孤立节点（已被删除订阅遗留的节点）
 * 2. 清理静态分组中失效的节点
 * 3. 重新计算并刷新所有动态分组
 * 4. 校验配置出站引用：如果引用的节点下线或被删除（且新数据中无同名项），自动重置为 "direct" 并持久化
 * 5. 立即重新生成最新的 sing-box 配置，并同步更新 ConfigServer 内存缓存和系统公共存储导出文件
 */
class DataSyncService(
    private val nodeRepository: NodeRepository,
    private val nodeGroupRepository: NodeGroupRepository,
    private val configRepository: ConfigRepository,
    private val configServer: ConfigServer? = null,
    private val configExporter: ConfigExporter? = null
) {

    /**
     * 全局数据同步核心方法：
     * 节点、节点组合、订阅变动时统一调用，完成全链路数据同步至配置与导出文件
     */
    suspend fun syncDataToConfig(): SyncResult = withContext(Dispatchers.IO) {
        // 0. 清理残留孤立节点（订阅已删除但遗留的节点）
        nodeRepository.deleteOrphanedNodes()

        val allNodes = nodeRepository.getAllNodesSync()
        val allAvailableTags = allNodes.map { it.tag }.toSet()
        val availableEnabledTags = allNodes.filter { it.enabled }.map { it.tag }.toSet()

        // 1. 清理静态分组（仅清理 MANUAL 类型，移除已下线的节点）
        var affectedGroups = 0
        val initialGroups = nodeGroupRepository.getAllGroupsSync()
        for (group in initialGroups) {
            if (group.groupType == io.github.geekdex.subout.data.db.entities.NodeGroupType.MANUAL) {
                val currentTags = group.nodeTags
                val filtered = currentTags.filter { it in allAvailableTags }
                if (filtered.size != currentTags.size) {
                    nodeGroupRepository.updateGroup(group.copyWithNodeTags(filtered))
                    affectedGroups++
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

        // 5. 立即根据最新生效数据重新生成 sing-box 配置并写入服务与文件
        val currentConfig = configRepository.configState.value
        val enabledNodes = allNodes.filter { it.enabled }
        val enabledGroups = updatedGroups.filter { it.enabled }
        try {
            val jsonStr = SimpleConfigGenerator.generatePrettyString(
                currentConfig,
                enabledNodes,
                enabledGroups
            )
            configServer?.updateContent(jsonStr)
            configExporter?.exportToFile(jsonStr)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        SyncResult(
            configResetList = configResetList,
            affectedGroupCount = affectedGroups,
            totalEnabledNodes = enabledNodes.size
        )
    }

    /**
     * 兼容方法，指向 syncDataToConfig
     */
    suspend fun syncSubscriptionsCompleted(): SyncResult = syncDataToConfig()

    /**
     * 同步结果数据类
     */
    data class SyncResult(
        val configResetList: List<String> = emptyList(),
        val affectedGroupCount: Int = 0,
        val totalEnabledNodes: Int = 0
    )
}
