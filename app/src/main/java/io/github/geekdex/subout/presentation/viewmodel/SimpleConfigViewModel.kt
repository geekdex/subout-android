package io.github.geekdex.subout.presentation.viewmodel

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.geekdex.subout.SuboutApplication
import io.github.geekdex.subout.data.repository.ConfigRepository
import io.github.geekdex.subout.data.repository.NodeRepository
import io.github.geekdex.subout.domain.generator.SimpleConfigGenerator
import io.github.geekdex.subout.domain.model.AppRulePresets
import io.github.geekdex.subout.domain.model.CustomAppGroup
import io.github.geekdex.subout.domain.model.InstalledAppInfo
import io.github.geekdex.subout.domain.model.SimpleConfig
import io.github.geekdex.subout.domain.model.SimpleDnsConfig
import io.github.geekdex.subout.domain.model.SimpleInboundConfig
import io.github.geekdex.subout.domain.model.SimpleLogConfig
import io.github.geekdex.subout.domain.model.SimpleRouteConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SimpleConfigUiState(
    val config: SimpleConfig = SimpleConfig(),
    val availableOutbounds: List<String> = listOf("AUTO-Test", "proxy", "direct", "block"),
    val installedApps: List<InstalledAppInfo> = emptyList(),
    val isLoadingApps: Boolean = false,
    val message: String? = null
)

class SimpleConfigViewModel(
    private val configRepository: ConfigRepository,
    private val nodeRepository: NodeRepository,
    private val context: Context? = null
) : ViewModel() {

    private val _currentConfig = MutableStateFlow(configRepository.configState.value)
    private val _message = MutableStateFlow<String?>(null)
    private val _installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    private val _isLoadingApps = MutableStateFlow(false)

    init {
        loadInstalledApps()
    }

    val uiState: StateFlow<SimpleConfigUiState> = combine(
        _currentConfig,
        nodeRepository.nodes,
        _installedApps,
        _isLoadingApps,
        _message
    ) { cfg, nodes, apps, loadingApps, msg ->
        val outbounds = mutableListOf("AUTO-Test", "proxy", "direct", "block")
        val enabledNodeTags = nodes.filter { it.enabled }.map { it.tag }
        outbounds.addAll(enabledNodeTags)
        SimpleConfigUiState(
            config = cfg,
            availableOutbounds = outbounds,
            installedApps = apps,
            isLoadingApps = loadingApps,
            message = msg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SimpleConfigUiState()
    )

    fun loadInstalledApps() {
        val ctx = context ?: return
        if (_isLoadingApps.value) return
        viewModelScope.launch {
            _isLoadingApps.value = true
            try {
                val list = withContext(Dispatchers.IO) {
                    val pm = ctx.packageManager
                    val appMap = mutableMapOf<String, InstalledAppInfo>()

                    // Method 1: Launcher activities (guaranteed by <queries>)
                    try {
                        val mainIntent = android.content.Intent(android.content.Intent.ACTION_MAIN, null).apply {
                            addCategory(android.content.Intent.CATEGORY_LAUNCHER)
                        }
                        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            pm.queryIntentActivities(mainIntent, PackageManager.ResolveInfoFlags.of(0L))
                        } else {
                            @Suppress("DEPRECATION")
                            pm.queryIntentActivities(mainIntent, 0)
                        }
                        for (ri in resolveInfos) {
                            val ai = ri.activityInfo?.applicationInfo ?: continue
                            if (ai.packageName == ctx.packageName) continue
                            val name = ri.loadLabel(pm).toString().ifBlank {
                                pm.getApplicationLabel(ai).toString()
                            }
                            val isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                            appMap[ai.packageName] = InstalledAppInfo(name, ai.packageName, isSystem)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    // Method 2: All installed applications via QUERY_ALL_PACKAGES
                    try {
                        val installed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0L))
                        } else {
                            @Suppress("DEPRECATION")
                            pm.getInstalledApplications(PackageManager.GET_META_DATA)
                        }
                        for (ai in installed) {
                            if (ai.packageName == ctx.packageName) continue
                            if (!appMap.containsKey(ai.packageName)) {
                                val name = pm.getApplicationLabel(ai).toString()
                                val isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                                appMap[ai.packageName] = InstalledAppInfo(name, ai.packageName, isSystem)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    appMap.values.sortedWith(compareBy({ it.isSystemApp }, { it.name.lowercase() }))
                }
                _installedApps.value = list
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoadingApps.value = false
            }
        }
    }

    /**
     * 计算所有已被占用的应用包名映射 (包名 -> 占用来源名称)
     * 用于保证一个应用只能属于一个分组/预设，避免冗余和规则冲突
     */
    fun getOccupiedAppMap(excludeGroupId: String? = null): Map<String, String> {
        val route = _currentConfig.value.route
        val map = mutableMapOf<String, String>()

        if (route.isRouteGoogle) {
            AppRulePresets.google.apps.forEach { map[it.packageName] = "Google 全家桶" }
        }
        if (route.isRouteSocial) {
            AppRulePresets.social.apps.forEach { map[it.packageName] = "海外社交应用" }
        }
        if (route.isRouteAi) {
            AppRulePresets.ai.apps.forEach { map[it.packageName] = "热门 AI 应用" }
        }
        route.customGroups.forEach { g ->
            if (g.id != excludeGroupId) {
                g.packageNames.forEach { pkg ->
                    map[pkg] = "分组: ${g.name}"
                }
            }
        }
        return map
    }

    private fun sanitizeCustomGroups(
        groups: List<CustomAppGroup>,
        routeGoogle: Boolean,
        routeSocial: Boolean,
        routeAi: Boolean
    ): List<CustomAppGroup> {
        val presetOccupied = mutableSetOf<String>()
        if (routeGoogle) presetOccupied.addAll(AppRulePresets.google.apps.map { it.packageName })
        if (routeSocial) presetOccupied.addAll(AppRulePresets.social.apps.map { it.packageName })
        if (routeAi) presetOccupied.addAll(AppRulePresets.ai.apps.map { it.packageName })

        val seen = mutableSetOf<String>()
        return groups.map { g ->
            val filtered = g.packageNames.filter { pkg ->
                pkg !in presetOccupied && seen.add(pkg)
            }
            g.copy(package_names = filtered)
        }
    }

    fun updateDns(
        mode: String = _currentConfig.value.dns.mode,
        domesticDns: String = _currentConfig.value.dns.domestic_dns,
        foreignDns: String = _currentConfig.value.dns.foreign_dns
    ) {
        val updatedDns = _currentConfig.value.dns.copy(
            mode = mode,
            domestic_dns = domesticDns,
            foreign_dns = foreignDns
        )
        _currentConfig.value = _currentConfig.value.copy(dns = updatedDns)
    }

    fun updateInbound(
        inboundType: String = _currentConfig.value.inbound.inbound_type,
        mixedPort: Int = _currentConfig.value.inbound.mixed_port,
        allowLan: Boolean = _currentConfig.value.inbound.allow_lan,
        tunStack: String = _currentConfig.value.inbound.tun_stack,
        tunAutoRoute: Boolean = _currentConfig.value.inbound.tun_auto_route
    ) {
        val updatedInbound = _currentConfig.value.inbound.copy(
            inbound_type = inboundType,
            mixed_port = mixedPort,
            allow_lan = allowLan,
            tun_stack = tunStack,
            tun_auto_route = tunAutoRoute
        )
        _currentConfig.value = _currentConfig.value.copy(inbound = updatedInbound)
    }

    fun updateRoute(
        mode: String = _currentConfig.value.route.mode,
        blockAds: Boolean = _currentConfig.value.route.block_ads,
        bypassLan: Boolean = _currentConfig.value.route.bypass_lan,
        blockQuic: Boolean = _currentConfig.value.route.isBlockQuic,
        defaultOutbound: String = _currentConfig.value.route.default_outbound,
        routeGoogle: Boolean = _currentConfig.value.route.isRouteGoogle,
        googleOutbound: String = _currentConfig.value.route.googleOutbound,
        routeSocial: Boolean = _currentConfig.value.route.isRouteSocial,
        socialOutbound: String = _currentConfig.value.route.socialOutbound,
        routeAi: Boolean = _currentConfig.value.route.isRouteAi,
        aiOutbound: String = _currentConfig.value.route.aiOutbound,
        customGroups: List<CustomAppGroup> = _currentConfig.value.route.customGroups
    ) {
        val sanitizedGroups = sanitizeCustomGroups(customGroups, routeGoogle, routeSocial, routeAi)
        val updatedRoute = _currentConfig.value.route.copy(
            mode = mode,
            block_ads = blockAds,
            bypass_lan = bypassLan,
            block_quic = blockQuic,
            default_outbound = defaultOutbound,
            route_google = routeGoogle,
            google_outbound = googleOutbound,
            route_social = routeSocial,
            social_outbound = socialOutbound,
            route_ai = routeAi,
            ai_outbound = aiOutbound,
            custom_groups = sanitizedGroups
        )
        _currentConfig.value = _currentConfig.value.copy(route = updatedRoute)
    }

    fun addCustomGroup(name: String, outbound: String = "") {
        val currentRoute = _currentConfig.value.route
        val newGroup = CustomAppGroup(
            id = java.util.UUID.randomUUID().toString(),
            name = name.ifBlank { "自定义分组 ${currentRoute.customGroups.size + 1}" },
            outbound = outbound,
            package_names = emptyList(),
            enabled = true
        )
        val updated = currentRoute.customGroups + newGroup
        updateRoute(customGroups = updated)
        _message.value = "已创建分组: ${newGroup.name}"
    }

    fun updateCustomGroupName(groupId: String, newName: String) {
        val currentRoute = _currentConfig.value.route
        val updated = currentRoute.customGroups.map { g ->
            if (g.id == groupId) g.copy(name = newName) else g
        }
        updateRoute(customGroups = updated)
    }

    fun updateCustomGroupOutbound(groupId: String, outbound: String) {
        val currentRoute = _currentConfig.value.route
        val updated = currentRoute.customGroups.map { g ->
            if (g.id == groupId) g.copy(outbound = outbound) else g
        }
        updateRoute(customGroups = updated)
    }

    fun updateCustomGroupEnabled(groupId: String, enabled: Boolean) {
        val currentRoute = _currentConfig.value.route
        val updated = currentRoute.customGroups.map { g ->
            if (g.id == groupId) g.copy(enabled = enabled) else g
        }
        updateRoute(customGroups = updated)
    }

    fun deleteCustomGroup(groupId: String) {
        val currentRoute = _currentConfig.value.route
        val groupToDelete = currentRoute.customGroups.find { it.id == groupId }
        val updated = currentRoute.customGroups.filter { it.id != groupId }
        updateRoute(customGroups = updated)
        _message.value = "已删除分组${if (groupToDelete != null) ": " + groupToDelete.name else ""}"
    }

    fun updateCustomGroupPackages(groupId: String, packages: List<String>) {
        val occupied = getOccupiedAppMap(excludeGroupId = groupId)
        val validPackages = packages.filter { it !in occupied.keys }
        val currentRoute = _currentConfig.value.route
        val updated = currentRoute.customGroups.map { g ->
            if (g.id == groupId) g.copy(package_names = validPackages) else g
        }
        updateRoute(customGroups = updated)
    }

    fun enableAllRecommended() {
        val currentRoute = _currentConfig.value.route
        val sanitized = sanitizeCustomGroups(currentRoute.customGroups, true, true, true)
        val updatedRoute = currentRoute.copy(
            block_ads = true,
            bypass_lan = true,
            block_quic = true,
            route_google = true,
            route_social = true,
            route_ai = true,
            custom_groups = sanitized
        )
        _currentConfig.value = _currentConfig.value.copy(route = updatedRoute)
        _message.value = "已一键开启全部推荐规则 (Google/社交/AI/阻断QUIC)"
    }

    fun forceAllProxy() {
        val currentRoute = _currentConfig.value.route
        val sanitized = sanitizeCustomGroups(currentRoute.customGroups, true, true, true)
        val updatedRoute = currentRoute.copy(
            mode = "smart",
            block_ads = true,
            bypass_lan = true,
            block_quic = true,
            route_google = true,
            google_outbound = "proxy",
            route_social = true,
            social_outbound = "proxy",
            route_ai = true,
            ai_outbound = "proxy",
            custom_groups = sanitized
        )
        _currentConfig.value = _currentConfig.value.copy(route = updatedRoute)
        _message.value = "已强制将 Google、社交与 AI 路由至代理出站"
    }

    fun updateLog(
        level: String = _currentConfig.value.log.level,
        timestamp: Boolean = _currentConfig.value.log.timestamp
    ) {
        val updatedLog = _currentConfig.value.log.copy(
            level = level,
            timestamp = timestamp
        )
        _currentConfig.value = _currentConfig.value.copy(log = updatedLog)
    }

    fun saveConfig() {
        viewModelScope.launch {
            configRepository.saveConfig(_currentConfig.value)
            try {
                val enabledNodes = nodeRepository.getEnabledNodes()
                val jsonStr = withContext(Dispatchers.Default) {
                    SimpleConfigGenerator.generatePrettyString(_currentConfig.value, enabledNodes)
                }
                SuboutApplication.instance.configServer.updateContent(jsonStr)
                SuboutApplication.instance.configExporter.exportToFile(jsonStr)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            _message.value = "配置已保存并立即生效"
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
                return SimpleConfigViewModel(app.configRepository, app.nodeRepository, app.applicationContext) as T
            }
        }
    }
}
