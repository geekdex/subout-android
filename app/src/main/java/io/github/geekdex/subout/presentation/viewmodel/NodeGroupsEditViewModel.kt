package io.github.geekdex.subout.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.geekdex.subout.SuboutApplication
import io.github.geekdex.subout.data.db.entities.Node
import io.github.geekdex.subout.data.db.entities.NodeGroup
import io.github.geekdex.subout.data.db.entities.NodeGroupType
import io.github.geekdex.subout.data.db.entities.DynamicMatchRule
import io.github.geekdex.subout.data.repository.ConfigRepository
import io.github.geekdex.subout.data.repository.NodeRepository
import io.github.geekdex.subout.data.repository.NodeGroupRepository
import io.github.geekdex.subout.data.repository.SubscriptionRepository
import io.github.geekdex.subout.domain.model.DynamicMatchRuleUI
import io.github.geekdex.subout.domain.model.NodeGroupDetailUiState
import io.github.geekdex.subout.domain.model.NodeGroupFormData
import io.github.geekdex.subout.domain.model.NodeGroupTypeUI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NodeGroupsEditViewModel(
    private val nodeGroupRepository: NodeGroupRepository,
    private val nodeRepository: NodeRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val configRepository: ConfigRepository
) : ViewModel() {

    private val _detailState = MutableStateFlow<NodeGroupDetailUiState>(NodeGroupDetailUiState())
    val detailState: StateFlow<NodeGroupDetailUiState> = _detailState.asStateFlow()

    /**
     * 获取所有订阅列表（用于动态分组的订阅过滤）
     */
    fun getAvailableSubscriptions(): List<Pair<Long, String>> {
        return _detailState.value.availableSubscriptions
    }

    /**
     * 加载节点组详情以供编辑
     */
    fun loadGroupForEdit(groupId: String) {
        viewModelScope.launch {
            _detailState.value = _detailState.value.copy(isLoading = true)
            try {
                val group = nodeGroupRepository.getGroupById(groupId) ?: return@launch

                val availableNodes = nodeRepository.getAllNodesSync()
                    .map { it.id to it.tag }

                val availableSubscriptions = subscriptionRepository.getAllSync()
                    .map { it.id to it.name }

                val formData = when (group.groupType) {
                    NodeGroupType.MANUAL -> NodeGroupFormData(
                        id = group.id,
                        name = group.name,
                        type = NodeGroupTypeUI.MANUAL,
                        testUrl = group.testUrl,
                        testUrlName = group.testUrlName,
                        enabled = group.enabled,
                        manualNodeIds = availableNodes
                            .filter { (_, tag) -> tag in group.nodeTags }
                            .map { (id, _) -> id }
                    )
                    NodeGroupType.DYNAMIC -> {
                        val rule = group.dynamicRule ?: DynamicMatchRule()
                        NodeGroupFormData(
                            id = group.id,
                            name = group.name,
                            type = NodeGroupTypeUI.DYNAMIC,
                            testUrl = group.testUrl,
                            testUrlName = group.testUrlName,
                            enabled = group.enabled,
                            dynamicRule = DynamicMatchRuleUI(
                                subscriptionIds = rule.subscriptionIds,
                                includeKeywords = rule.includeKeywords,
                                excludeKeywords = rule.excludeKeywords
                            ),
                            manualNodeIds = emptyList()
                        )
                    }
                }

                _detailState.value = NodeGroupDetailUiState(
                    groupId = group.id,
                    formData = formData,
                    availableNodes = availableNodes,
                    availableSubscriptions = availableSubscriptions,
                    selectedNodeTags = group.nodeTags,
                    selectedSubscriptionIds = formData.dynamicRule.subscriptionIds,
                    includeKeywordsInput = DynamicMatchRuleUI.keywordsToString(formData.dynamicRule.includeKeywords),
                    excludeKeywordsInput = DynamicMatchRuleUI.keywordsToString(formData.dynamicRule.excludeKeywords)
                )
            } catch (e: Exception) {
                _detailState.value = _detailState.value.copy(
                    message = "加载失败: ${e.message}"
                )
            } finally {
                _detailState.value = _detailState.value.copy(isLoading = false)
            }
        }
    }

    /**
     * 初始化新建节点组表单
     */
    fun initializeNewGroup(type: NodeGroupTypeUI) {
        viewModelScope.launch {
            val availableNodes = nodeRepository.getAllNodesSync()
                .map { it.id to it.tag }

            val availableSubscriptions = subscriptionRepository.getAllSync()
                .map { it.id to it.name }

            _detailState.value = NodeGroupDetailUiState(
                formData = NodeGroupFormData(
                    type = type
                ),
                availableNodes = availableNodes,
                availableSubscriptions = availableSubscriptions,
                selectedSubscriptionIds = emptyList(),
                includeKeywordsInput = "",
                excludeKeywordsInput = ""
            )
        }
    }

    /**
     * 更新表单数据
     */
    fun updateFormData(update: (NodeGroupFormData) -> NodeGroupFormData) {
        val current = _detailState.value.formData
        val updated = update(current)
        _detailState.value = _detailState.value.copy(formData = updated)

        // 如果是动态分组且改变了规则，更新预览
        if (updated.isDynamicType) {
            refreshDynamicPreview()
        }
    }

    /**
     * 刷新动态分组的匹配预览
     */
    private fun refreshDynamicPreview() {
        viewModelScope.launch {
            val rule = _detailState.value.formData.dynamicRule
            val allNodes = nodeRepository.getAllNodesSync()

            val matched = allNodes.filter { node ->
                // 检查订阅过滤
                if (rule.subscriptionIds.isNotEmpty()) {
                    if (node.subscriptionId == null || node.subscriptionId !in rule.subscriptionIds) {
                        return@filter false
                    }
                }

                // 检查关键词匹配
                val matchedTags = mutableListOf<String>()
                
                // 检查排除关键词
                if (rule.excludeKeywords.isNotEmpty()) {
                    for (keyword in rule.excludeKeywords) {
                        if (keyword.isNotBlank() && node.tag.contains(keyword, ignoreCase = true)) {
                            return@filter false
                        }
                    }
                }

                // 检查包含关键词
                if (rule.includeKeywords.isEmpty()) {
                    return@filter true
                }

                for (keyword in rule.includeKeywords) {
                    if (keyword.isNotBlank() && node.tag.contains(keyword, ignoreCase = true)) {
                        return@filter true
                    }
                }
                false
            }.map { it.tag }

            _detailState.value = _detailState.value.copy(
                matchedNodeTags = matched,
                previewMatches = matched
            )
        }
    }

    /**
     * 更新包含关键词（从文本输入）
     */
    fun updateIncludeKeywords(input: String) {
        val keywords = DynamicMatchRuleUI.parseKeywords(input)
        updateFormData { form ->
            form.copy(
                dynamicRule = form.dynamicRule.copy(
                    includeKeywords = keywords
                )
            )
        }
        _detailState.value = _detailState.value.copy(includeKeywordsInput = input)
        refreshDynamicPreview()
    }

    /**
     * 更新排除关键词（从文本输入）
     */
    fun updateExcludeKeywords(input: String) {
        val keywords = DynamicMatchRuleUI.parseKeywords(input)
        updateFormData { form ->
            form.copy(
                dynamicRule = form.dynamicRule.copy(
                    excludeKeywords = keywords
                )
            )
        }
        _detailState.value = _detailState.value.copy(excludeKeywordsInput = input)
        refreshDynamicPreview()
    }

    /**
     * 切换订阅选中状态
     */
    fun toggleSubscriptionSelection(subscriptionId: Long) {
        val currentIds = _detailState.value.selectedSubscriptionIds.toMutableList()
        if (currentIds.contains(subscriptionId)) {
            currentIds.remove(subscriptionId)
        } else {
            currentIds.add(subscriptionId)
        }
        _detailState.value = _detailState.value.copy(selectedSubscriptionIds = currentIds)
        
        updateFormData { form ->
            form.copy(
                dynamicRule = form.dynamicRule.copy(
                    subscriptionIds = currentIds
                )
            )
        }
        refreshDynamicPreview()
    }

    /**
     * 切换节点选中状态（仅用于手动分组）
     */
    fun toggleNodeSelection(nodeId: Long, nodeTag: String, selected: Boolean) {
        updateFormData { form ->
            val currentIds = form.manualNodeIds.toMutableList()
            if (selected) {
                if (nodeId !in currentIds) currentIds.add(nodeId)
            } else {
                currentIds.remove(nodeId)
            }
            form.copy(manualNodeIds = currentIds)
        }
    }

    /**
     * 保存节点组
     */
    fun saveNodeGroup() {
        viewModelScope.launch {
            _detailState.value = _detailState.value.copy(isSaving = true)
            try {
                val form = _detailState.value.formData
                val groupId = _detailState.value.groupId

                when {
                    form.isNew && form.isManualType -> {
                        // 新建手动分组
                        val allNodes = nodeRepository.getAllNodesSync()
                        val selectedTags = allNodes
                            .filter { it.id in form.manualNodeIds }
                            .map { it.tag }

                        val group = NodeGroup(
                            name = form.name,
                            type = NodeGroupType.MANUAL.name,
                            testUrl = form.testUrl,
                            testUrlName = form.testUrlName,
                            enabled = form.enabled,
                            nodeTagsJson = com.google.gson.Gson().toJson(selectedTags)
                        )
                        nodeGroupRepository.insertGroup(group)
                        _detailState.value = _detailState.value.copy(
                            message = "已创建手动分组: ${form.name}"
                        )
                    }

                    form.isNew && form.isDynamicType -> {
                        // 新建动态分组
                        nodeGroupRepository.createDynamicGroup(
                            name = form.name,
                            subscriptionIds = form.dynamicRule.subscriptionIds,
                            includeKeywords = form.dynamicRule.includeKeywords,
                            excludeKeywords = form.dynamicRule.excludeKeywords,
                            testUrl = form.testUrl,
                            testUrlName = form.testUrlName
                        )
                        _detailState.value = _detailState.value.copy(
                            message = "已创建动态分组: ${form.name}"
                        )
                    }

                    !form.isNew && form.isManualType -> {
                        // 编辑手动分组
                        val group = nodeGroupRepository.getGroupById(groupId)
                        if (group != null) {
                            val allNodes = nodeRepository.getAllNodesSync()
                            val selectedTags = allNodes
                                .filter { it.id in form.manualNodeIds }
                                .map { it.tag }

                            val updated = group.copyWithNodeTags(selectedTags).copy(
                                name = form.name,
                                testUrl = form.testUrl,
                                testUrlName = form.testUrlName,
                                enabled = form.enabled
                            )
                            nodeGroupRepository.updateGroup(updated)
                            _detailState.value = _detailState.value.copy(
                                message = "已更新静态分组: ${form.name}"
                            )
                        }
                    }

                    !form.isNew && form.isDynamicType -> {
                        // 编辑动态分组
                        val updated = nodeGroupRepository.updateDynamicGroup(
                            groupId = groupId,
                            name = form.name,
                            testUrl = form.testUrl,
                            testUrlName = form.testUrlName,
                            subscriptionIds = form.dynamicRule.subscriptionIds,
                            includeKeywords = form.dynamicRule.includeKeywords,
                            excludeKeywords = form.dynamicRule.excludeKeywords
                        )
                        if (updated != null) {
                            _detailState.value = _detailState.value.copy(
                                message = "已更新动态分组规则: ${form.name}"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _detailState.value = _detailState.value.copy(
                    message = "保存失败: ${e.message}"
                )
            } finally {
                _detailState.value = _detailState.value.copy(isSaving = false)
            }
        }
    }

    /**
     * 删除节点组
     */
    fun deleteNodeGroup(groupId: String) {
        viewModelScope.launch {
            try {
                val group = nodeGroupRepository.getGroupById(groupId)
                if (group != null) {
                    // 检查配置中是否有引用这个分组（通过分组名称）
                    val resetList = configRepository.resetOutboundIfUsedByGroupName(setOf(group.name))

                    nodeGroupRepository.deleteGroupById(groupId)

                    if (resetList.isNotEmpty()) {
                        _detailState.value = _detailState.value.copy(
                            message = "已删除分组【${group.name}】，涉及的配置【${resetList.joinToString("、")}】已重置为 direct"
                        )
                    } else {
                        _detailState.value = _detailState.value.copy(
                            message = "已删除分组【${group.name}】"
                        )
                    }
                }
            } catch (e: Exception) {
                _detailState.value = _detailState.value.copy(
                    message = "删除失败: ${e.message}"
                )
            }
        }
    }

    fun clearMessage() {
        _detailState.value = _detailState.value.copy(message = null)
    }

    companion object {
        fun Factory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = SuboutApplication.instance
                return NodeGroupsEditViewModel(
                    app.nodeGroupRepository,
                    app.nodeRepository,
                    app.subscriptionRepository,
                    app.configRepository
                ) as T
            }
        }
    }
}
