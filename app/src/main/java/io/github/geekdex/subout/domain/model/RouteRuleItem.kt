package io.github.geekdex.subout.domain.model

sealed class RouteRuleItem(val id: String) {
    abstract val isEnabled: Boolean

    data class SystemRule(
        val systemId: String,
        val title: String,
        val summary: String,
        override val isEnabled: Boolean
    ) : RouteRuleItem(systemId)

    data class PresetRule(
        val presetId: String,
        val category: PresetCategory,
        override val isEnabled: Boolean,
        val outbound: String
    ) : RouteRuleItem(presetId)

    data class AppGroupRule(
        val group: CustomAppGroup
    ) : RouteRuleItem("app_group_${group.id}") {
        override val isEnabled: Boolean get() = group.isEnabled
    }

    data class DomainGroupRule(
        val group: CustomDomainGroup
    ) : RouteRuleItem("domain_group_${group.id}") {
        override val isEnabled: Boolean get() = group.isEnabled
    }

    companion object {
        fun buildList(route: SimpleRouteConfig): List<RouteRuleItem> {
            val itemMap = mutableMapOf<String, RouteRuleItem>()

            itemMap["sys_block_quic"] = SystemRule(
                systemId = "sys_block_quic",
                title = "阻断 QUIC (UDP 443)",
                summary = "迫使 YouTube / Play 商店回退至高速稳定 TCP 隧道，彻底解决视频首包缓冲转圈",
                isEnabled = route.isBlockQuic
            )
            itemMap["sys_block_ads"] = SystemRule(
                systemId = "sys_block_ads",
                title = "拦截常见广告",
                summary = "使用 geosite-category-ads-all 规则阻断广告与分析跟踪流量",
                isEnabled = route.block_ads
            )
            itemMap["sys_bypass_lan"] = SystemRule(
                systemId = "sys_bypass_lan",
                title = "绕过局域网私有流量",
                summary = "私有网络 IP (192.168.x / 10.x / 172.16.x 等) 强制直连，不走代理",
                isEnabled = route.bypass_lan
            )

            itemMap["preset_google"] = PresetRule(
                presetId = "preset_google",
                category = AppRulePresets.google,
                isEnabled = route.isRouteGoogle,
                outbound = route.googleOutbound
            )
            itemMap["preset_social"] = PresetRule(
                presetId = "preset_social",
                category = AppRulePresets.social,
                isEnabled = route.isRouteSocial,
                outbound = route.socialOutbound
            )
            itemMap["preset_ai"] = PresetRule(
                presetId = "preset_ai",
                category = AppRulePresets.ai,
                isEnabled = route.isRouteAi,
                outbound = route.aiOutbound
            )

            route.customGroups.forEach { group ->
                itemMap["app_group_${group.id}"] = AppGroupRule(group)
            }

            route.customDomainGroups.forEach { group ->
                itemMap["domain_group_${group.id}"] = DomainGroupRule(group)
            }

            val effectiveOrder = route.getEffectiveRuleOrder()
            return effectiveOrder.mapNotNull { itemMap[it] }
        }
    }
}
