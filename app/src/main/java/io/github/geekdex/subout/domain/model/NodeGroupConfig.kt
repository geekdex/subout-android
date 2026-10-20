package io.github.geekdex.subout.domain.model

/**
 * 节点组操作配置模型
 * 用于 UI 层与业务逻辑层的通信
 */

/**
 * 节点组类型（用于 UI 显示和用户操作）
 */
enum class NodeGroupTypeUI {
    MANUAL,      // 手动选择组（静态节点组）
    DYNAMIC;     // 动态匹配组（动态筛选组）

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
 * 动态匹配规则配置（UI 表单模型）
 */
data class DynamicMatchRuleUI(
    val subscriptionIds: List<Long> = emptyList(),      // 订阅过滤（空=所有订阅）
    val includeKeywords: List<String> = emptyList(),    // 包含关键词（用逗号分隔或列表）
    val excludeKeywords: List<String> = emptyList()     // 排除关键词（用逗号分隔或列表）
) {
    /**
     * 解析关键词字符串（支持逗号分隔或换行分隔）
     */
    companion object {
        fun parseKeywords(input: String): List<String> {
            return input
                .split(Regex("[,\\n\\r]+"))
                .map { it.trim() }
                .filter { it.isNotBlank() }
        }

        fun keywordsToString(keywords: List<String>): String {
            return keywords.joinToString(", ")
        }
    }
}

/**
 * 节点组创建/编辑表单数据
 */
data class NodeGroupFormData(
    val id: String = "",                              // 空表示新建
    val name: String = "",
    val type: NodeGroupTypeUI = NodeGroupTypeUI.MANUAL,
    val testUrl: String = "https://www.gstatic.com/generate_204",
    val testUrlName: String = "Google Gstatic",
    val enabled: Boolean = true,
    
    // MANUAL 类型字段
    val manualNodeIds: List<Long> = emptyList(),      // 选中的节点 ID
    
    // DYNAMIC 类型字段
    val dynamicRule: DynamicMatchRuleUI = DynamicMatchRuleUI()
) {
    val isNew: Boolean get() = id.isBlank()
    val isManualType: Boolean get() = type == NodeGroupTypeUI.MANUAL
    val isDynamicType: Boolean get() = type == NodeGroupTypeUI.DYNAMIC
}

/**
 * 节点组查看/编辑的 UI 状态
 */
data class NodeGroupDetailUiState(
    val groupId: String = "",
    val formData: NodeGroupFormData = NodeGroupFormData(),
    val availableNodes: List<Pair<Long, String>> = emptyList(),  // (nodeId, nodeTag)
    val selectedNodeTags: List<String> = emptyList(),            // 当前分组中的节点 tag
    val matchedNodeTags: List<String> = emptyList(),             // 动态分组计算得出的匹配节点 tag（预览）
    val availableSubscriptions: List<Pair<Long, String>> = emptyList(),  // (subscriptionId, name)
    val selectedSubscriptionIds: List<Long> = emptyList(),
    val includeKeywordsInput: String = "",                        // 用户输入的关键词
    val excludeKeywordsInput: String = "",
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val message: String? = null,
    val previewMatches: List<String> = emptyList()               // 预览匹配结果
)
