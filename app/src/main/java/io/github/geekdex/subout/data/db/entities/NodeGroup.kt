package io.github.geekdex.subout.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 节点组类型
 * MANUAL: 手动选择组 - 用户手动添加/移除节点
 * DYNAMIC: 动态匹配组 - 根据规则自动匹配节点
 */
enum class NodeGroupType {
    MANUAL,
    DYNAMIC;

    /**
     * 获取中文显示名称
     */
    fun getDisplayName(): String = when (this) {
        MANUAL -> "静态组合"
        DYNAMIC -> "动态筛选组合"
    }

    /**
     * 获取类型说明
     */
    fun getDescription(): String = when (this) {
        MANUAL -> "手动勾选固定节点，订阅更新时若节点失效会被移除"
        DYNAMIC -> "按订阅与关键词自动筛选，订阅更新时自动同步匹配节点"
    }
}

/**
 * 动态匹配规则配置（JSON 字符串存储）
 */
data class DynamicMatchRule(
    val subscriptionIds: List<Long> = emptyList(),  // 空表示所有订阅
    val includeKeywords: List<String> = emptyList(), // 包含关键词（或关系）
    val excludeKeywords: List<String> = emptyList()  // 排除关键词（或关系）
) {
    /**
     * 检查节点是否符合此规则
     * @param nodeName 节点名称/Tag
     * @param subscriptionId 节点所属订阅 ID（可为 null，如手动添加的节点）
     */
    fun matches(nodeName: String, subscriptionId: Long? = null): Boolean {
        // 1. 检查所属订阅（如果指定了订阅列表，必须属于选中的订阅之一）
        if (subscriptionIds.isNotEmpty()) {
            if (subscriptionId == null || subscriptionId !in subscriptionIds) {
                return false
            }
        }

        // 2. 检查排除关键词（如果包含任何排除关键词，则不匹配）
        if (excludeKeywords.isNotEmpty()) {
            for (keyword in excludeKeywords) {
                if (keyword.isNotBlank() && nodeName.contains(keyword, ignoreCase = true)) {
                    return false
                }
            }
        }

        // 3. 检查包含关键词
        if (includeKeywords.isEmpty()) {
            // 如果没有包含关键词，则默认匹配（除非被排除关键词过滤）
            return true
        }

        // 如果有包含关键词，节点名必须至少包含一个
        for (keyword in includeKeywords) {
            if (keyword.isNotBlank() && nodeName.contains(keyword, ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    companion object {
        fun toJson(rule: DynamicMatchRule): String = Gson().toJson(rule)
        fun fromJson(json: String): DynamicMatchRule? = try {
            Gson().fromJson(json, DynamicMatchRule::class.java)
        } catch (_: Exception) {
            null
        }
    }
}

@Entity(tableName = "node_groups")
data class NodeGroup(
    @PrimaryKey
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val testUrl: String = "https://www.gstatic.com/generate_204",
    val testUrlName: String = "Google Gstatic",
    val nodeTagsJson: String = "[]",  // 仅用于 MANUAL 类型；DYNAMIC 类型计算得出
    val type: String = NodeGroupType.MANUAL.name,  // 默认为手动选择组
    val dynamicRuleJson: String = "",  // 仅用于 DYNAMIC 类型
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
) {
    val nodeTags: List<String>
        get() = try {
            val type = object : TypeToken<List<String>>() {}.type
            Gson().fromJson(nodeTagsJson, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }

    val groupType: NodeGroupType
        get() = try {
            NodeGroupType.valueOf(type)
        } catch (_: Exception) {
            NodeGroupType.MANUAL
        }

    val dynamicRule: DynamicMatchRule?
        get() = if (groupType == NodeGroupType.DYNAMIC) {
            DynamicMatchRule.fromJson(dynamicRuleJson)
        } else {
            null
        }

    fun copyWithNodeTags(tags: List<String>): NodeGroup {
        return copy(nodeTagsJson = Gson().toJson(tags))
    }

    fun copyWithDynamicRule(rule: DynamicMatchRule): NodeGroup {
        return copy(dynamicRuleJson = DynamicMatchRule.toJson(rule))
    }
}
