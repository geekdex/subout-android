package io.github.geekdex.subout.data.repository

import io.github.geekdex.subout.data.db.dao.NodeDao
import io.github.geekdex.subout.data.db.dao.NodeGroupDao
import io.github.geekdex.subout.data.db.entities.Node
import io.github.geekdex.subout.data.db.entities.NodeGroup
import io.github.geekdex.subout.data.db.entities.NodeGroupType
import io.github.geekdex.subout.data.db.entities.DynamicMatchRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class NodeGroupRepository(
    private val nodeGroupDao: NodeGroupDao,
    private val nodeDao: NodeDao
) {
    val nodeGroups: Flow<List<NodeGroup>> = nodeGroupDao.getAll()

    suspend fun getAllGroupsSync(): List<NodeGroup> = withContext(Dispatchers.IO) {
        nodeGroupDao.getAllSync()
    }

    suspend fun getGroupById(id: String): NodeGroup? = withContext(Dispatchers.IO) {
        nodeGroupDao.getById(id)
    }

    suspend fun insertGroup(group: NodeGroup) = withContext(Dispatchers.IO) {
        nodeGroupDao.insert(group)
    }

    suspend fun updateGroup(group: NodeGroup) = withContext(Dispatchers.IO) {
        nodeGroupDao.update(group)
    }

    suspend fun deleteGroup(group: NodeGroup) = withContext(Dispatchers.IO) {
        nodeGroupDao.delete(group)
    }

    suspend fun deleteGroupById(id: String) = withContext(Dispatchers.IO) {
        nodeGroupDao.deleteById(id)
    }

    suspend fun setGroupEnabled(id: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        nodeGroupDao.updateEnabled(id, enabled)
    }

    /**
     * 当节点被删除时，从所有手动选择组中移除这些节点
     * 仅适用于 MANUAL 类型的分组
     */
    suspend fun removeNodesFromAllManualGroups(nodeTags: Set<String>) = withContext(Dispatchers.IO) {
        if (nodeTags.isEmpty()) return@withContext
        val allGroups = nodeGroupDao.getAllSync()
        for (group in allGroups) {
            if (group.groupType == NodeGroupType.MANUAL) {
                val currentTags = group.nodeTags
                val filtered = currentTags.filter { it !in nodeTags }
                if (filtered.size != currentTags.size) {
                    nodeGroupDao.update(group.copyWithNodeTags(filtered))
                }
            }
        }
    }

    /**
     * 重新计算所有动态匹配组的节点列表
     * 应该在订阅更新、节点变化时调用
     */
    suspend fun refreshDynamicGroupsNodes() = withContext(Dispatchers.IO) {
        val allGroups = nodeGroupDao.getAllSync()
        val allNodes = nodeDao.getAllSync()

        for (group in allGroups) {
            if (group.groupType == NodeGroupType.DYNAMIC) {
                val rule = group.dynamicRule ?: continue
                val matchedTags = calculateDynamicGroupNodes(rule, allNodes)
                if (matchedTags != group.nodeTags) {
                    val updatedGroup = group.copyWithNodeTags(matchedTags)
                    nodeGroupDao.update(updatedGroup)
                }
            }
        }
    }

    /**
     * 根据动态规则计算匹配的节点 tag 列表
     */
    suspend fun calculateDynamicGroupNodes(
        rule: DynamicMatchRule,
        allNodes: List<Node>
    ): List<String> = withContext(Dispatchers.IO) {
        val filtered = allNodes.filter { node ->
            rule.matches(node.tag, node.subscriptionId)
        }
        filtered.map { it.tag }
    }

    /**
     * 创建一个手动选择组
     */
    suspend fun createManualGroup(
        name: String,
        nodeTags: List<String> = emptyList(),
        testUrl: String = "https://www.gstatic.com/generate_204",
        testUrlName: String = "Google Gstatic"
    ): NodeGroup {
        val group = NodeGroup(
            name = name,
            type = NodeGroupType.MANUAL.name,
            testUrl = testUrl,
            testUrlName = testUrlName,
            nodeTagsJson = com.google.gson.Gson().toJson(nodeTags)
        )
        insertGroup(group)
        return group
    }

    /**
     * 创建一个动态匹配组
     */
    suspend fun createDynamicGroup(
        name: String,
        subscriptionIds: List<Long> = emptyList(),
        includeKeywords: List<String> = emptyList(),
        excludeKeywords: List<String> = emptyList(),
        testUrl: String = "https://www.gstatic.com/generate_204",
        testUrlName: String = "Google Gstatic"
    ): NodeGroup {
        val rule = DynamicMatchRule(
            subscriptionIds = subscriptionIds,
            includeKeywords = includeKeywords.filter { it.isNotBlank() },
            excludeKeywords = excludeKeywords.filter { it.isNotBlank() }
        )
        val allNodes = nodeDao.getAllSync()
        val matchedTags = calculateDynamicGroupNodes(rule, allNodes)
        val group = NodeGroup(
            name = name,
            type = NodeGroupType.DYNAMIC.name,
            testUrl = testUrl,
            testUrlName = testUrlName,
            dynamicRuleJson = DynamicMatchRule.toJson(rule)
        ).copyWithNodeTags(matchedTags)
        insertGroup(group)
        return group
    }

    /**
     * 完整更新动态匹配组合（包含名称、测速目标与匹配规则）
     */
    suspend fun updateDynamicGroup(
        groupId: String,
        name: String,
        testUrl: String,
        testUrlName: String,
        subscriptionIds: List<Long>,
        includeKeywords: List<String>,
        excludeKeywords: List<String>
    ): NodeGroup? = withContext(Dispatchers.IO) {
        val group = nodeGroupDao.getById(groupId) ?: return@withContext null
        val rule = DynamicMatchRule(
            subscriptionIds = subscriptionIds,
            includeKeywords = includeKeywords.filter { it.isNotBlank() },
            excludeKeywords = excludeKeywords.filter { it.isNotBlank() }
        )
        val allNodes = nodeDao.getAllSync()
        val matchedTags = calculateDynamicGroupNodes(rule, allNodes)
        val updatedGroup = group.copy(
            name = name,
            testUrl = testUrl.ifBlank { "https://www.gstatic.com/generate_204" },
            testUrlName = testUrlName.ifBlank { "Google Gstatic" },
            dynamicRuleJson = DynamicMatchRule.toJson(rule)
        ).copyWithNodeTags(matchedTags)
        nodeGroupDao.update(updatedGroup)
        updatedGroup
    }

    /**
     * 更新动态匹配规则
     */
    suspend fun updateDynamicRule(
        groupId: String,
        subscriptionIds: List<Long>,
        includeKeywords: List<String>,
        excludeKeywords: List<String>
    ) = withContext(Dispatchers.IO) {
        val group = nodeGroupDao.getById(groupId) ?: return@withContext
        if (group.groupType != NodeGroupType.DYNAMIC) return@withContext

        val rule = DynamicMatchRule(
            subscriptionIds = subscriptionIds,
            includeKeywords = includeKeywords.filter { it.isNotBlank() },
            excludeKeywords = excludeKeywords.filter { it.isNotBlank() }
        )
        val updatedGroup = group.copyWithDynamicRule(rule)
        
        // 立即重新计算节点列表
        val allNodes = nodeDao.getAllSync()
        val matchedTags = calculateDynamicGroupNodes(rule, allNodes)
        val finalGroup = updatedGroup.copyWithNodeTags(matchedTags)
        nodeGroupDao.update(finalGroup)
    }
}
