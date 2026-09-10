package io.github.geekdex.subout.data.repository

import android.content.Context
import com.google.gson.Gson
import io.github.geekdex.subout.domain.model.SimpleConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ConfigRepository(context: Context) {

    private val prefs = context.getSharedPreferences("subout_config_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    private val _configState = MutableStateFlow(loadConfig())
    val configState: StateFlow<SimpleConfig> = _configState.asStateFlow()

    private fun loadConfig(): SimpleConfig {
        val json = prefs.getString(KEY_SIMPLE_CONFIG, null) ?: return SimpleConfig()
        return try {
            gson.fromJson(json, SimpleConfig::class.java) ?: SimpleConfig()
        } catch (_: Exception) {
            SimpleConfig()
        }
    }

    fun saveConfig(config: SimpleConfig) {
        val json = gson.toJson(config)
        prefs.edit().putString(KEY_SIMPLE_CONFIG, json).commit()
        _configState.value = config
    }

    /**
     * 检查给定的节点标签集合是否在当前的简单配置（如 Google、社交、AI、默认出站或自定义应用组）中被使用。
     * 返回被引用的规则或分组名称列表。
     */
    fun checkNodeUsage(tags: Set<String>): List<String> {
        if (tags.isEmpty()) return emptyList()
        val route = _configState.value.route
        val usages = mutableListOf<String>()

        if (route.google_outbound in tags) {
            usages.add("Google 全家桶出站")
        }
        if (route.social_outbound in tags) {
            usages.add("常用海外社交出站")
        }
        if (route.ai_outbound in tags) {
            usages.add("热门 AI 应用出站")
        }
        if (route.default_outbound in tags) {
            usages.add("默认出站")
        }
        route.customGroups.forEach { group ->
            if (group.outbound in tags) {
                usages.add("应用分组「${group.name}」")
            }
        }
        route.customDomainGroups.forEach { group ->
            if (group.outbound in tags) {
                usages.add("域名分组「${group.name}」")
            }
        }
        return usages
    }

    /**
     * 当某些节点被删除时，检查当前配置。如果有规则使用了这些节点，自动将其重置为 "direct"，
     * 并立即通过 saveConfig() 保存持久化。
     * 返回被重置的规则描述列表。
     */
    fun resetOutboundIfUsed(tags: Set<String>): List<String> {
        if (tags.isEmpty()) return emptyList()
        val currentConfig = _configState.value
        val route = currentConfig.route
        val resetList = mutableListOf<String>()

        var newGoogle = route.google_outbound
        if (route.google_outbound in tags) {
            newGoogle = "direct"
            resetList.add("Google 全家桶出站")
        }

        var newSocial = route.social_outbound
        if (route.social_outbound in tags) {
            newSocial = "direct"
            resetList.add("常用海外社交出站")
        }

        var newAi = route.ai_outbound
        if (route.ai_outbound in tags) {
            newAi = "direct"
            resetList.add("热门 AI 应用出站")
        }

        var newDefault = route.default_outbound
        if (route.default_outbound in tags) {
            newDefault = "direct"
            resetList.add("默认出站")
        }

        var groupsChanged = false
        val newGroups = route.customGroups.map { group ->
            if (group.outbound in tags) {
                groupsChanged = true
                resetList.add("应用分组「${group.name}」")
                group.copy(outbound = "direct")
            } else {
                group
            }
        }

        val newDomainGroups = route.customDomainGroups.map { group ->
            if (group.outbound in tags) {
                groupsChanged = true
                resetList.add("域名分组「${group.name}」")
                group.copy(outbound = "direct")
            } else {
                group
            }
        }

        if (resetList.isNotEmpty()) {
            val updatedConfig = currentConfig.copy(
                route = route.copy(
                    google_outbound = newGoogle,
                    social_outbound = newSocial,
                    ai_outbound = newAi,
                    default_outbound = newDefault,
                    custom_groups = newGroups,
                    custom_domain_groups = newDomainGroups
                )
            )
            saveConfig(updatedConfig)
        }

        return resetList
    }

    /**
     * 校验当前配置引用的节点是否仍存在于可用节点列表中（如订阅更新后某些节点下线或被更名）。
     * 若引用了不存在的节点，自动重置为 "direct"。
     */
    fun validateAndCleanMissingNodes(availableNodeTags: Set<String>): List<String> {
        val standardOutbounds = setOf("", "AUTO-Test", "proxy", "direct", "block")
        val currentConfig = _configState.value
        val route = currentConfig.route
        val resetList = mutableListOf<String>()

        var newGoogle = route.google_outbound
        if (route.google_outbound !in standardOutbounds && route.google_outbound !in availableNodeTags) {
            newGoogle = "direct"
            resetList.add("Google 全家桶出站")
        }

        var newSocial = route.social_outbound
        if (route.social_outbound !in standardOutbounds && route.social_outbound !in availableNodeTags) {
            newSocial = "direct"
            resetList.add("常用海外社交出站")
        }

        var newAi = route.ai_outbound
        if (route.ai_outbound !in standardOutbounds && route.ai_outbound !in availableNodeTags) {
            newAi = "direct"
            resetList.add("热门 AI 应用出站")
        }

        var newDefault = route.default_outbound
        if (route.default_outbound !in standardOutbounds && route.default_outbound !in availableNodeTags) {
            newDefault = "direct"
            resetList.add("默认出站")
        }

        val newGroups = route.customGroups.map { group ->
            if (group.outbound !in standardOutbounds && group.outbound !in availableNodeTags) {
                resetList.add("应用分组「${group.name}」")
                group.copy(outbound = "direct")
            } else {
                group
            }
        }

        val newDomainGroups = route.customDomainGroups.map { group ->
            if (group.outbound !in standardOutbounds && group.outbound !in availableNodeTags) {
                resetList.add("域名分组「${group.name}」")
                group.copy(outbound = "direct")
            } else {
                group
            }
        }

        if (resetList.isNotEmpty()) {
            val updatedConfig = currentConfig.copy(
                route = route.copy(
                    google_outbound = newGoogle,
                    social_outbound = newSocial,
                    ai_outbound = newAi,
                    default_outbound = newDefault,
                    custom_groups = newGroups,
                    custom_domain_groups = newDomainGroups
                )
            )
            saveConfig(updatedConfig)
        }

        return resetList
    }

    companion object {
        private const val KEY_SIMPLE_CONFIG = "simple_config_json"
    }
}
