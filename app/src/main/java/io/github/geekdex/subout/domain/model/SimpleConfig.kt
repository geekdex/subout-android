package io.github.geekdex.subout.domain.model

data class SimpleLogConfig(
    val level: String = "info",
    val timestamp: Boolean = true,
    val disabled: Boolean = false,
    val output: String = ""
)

data class SimpleDnsConfig(
    val mode: String = "preset_fakeip", // "preset_fakeip", "preset_domestic_foreign", "fast_public", "custom"
    val domestic_dns: String = "223.5.5.5",
    val foreign_dns: String = "fakeip",
    val suppress_ech: Boolean? = true // 抑制 HTTPS (Type 65) / SVCB DNS 查询，防止浏览器 ECH 加密导致分流失效，并杜绝 FakeIP 非 IP 查询报错
) {
    val isSuppressEch: Boolean get() = suppress_ech ?: true
}

data class SimpleInboundConfig(
    val inbound_type: String = "tun", // "tun", "mixed"
    val mixed_port: Int = 2080,
    val allow_lan: Boolean = false,
    val tun_stack: String = "mixed", // "mixed" (推荐，Android SFA 黄金标准), "system", "gvisor"
    val tun_auto_route: Boolean = true
)

data class SimpleRouteConfig(
    val mode: String = "smart", // "smart", "global", "gfw", "direct"
    val block_ads: Boolean = true,
    val bypass_lan: Boolean = true,
    val block_quic: Boolean? = true, // 阻断 QUIC (UDP 443)，防止 Google Play / YouTube 缓冲卡死
    val default_outbound: String = "AUTO-Test", // "AUTO-Test", "proxy", "direct"

    // 所见即所得常用应用规则
    val route_google: Boolean? = true, // Google 全家桶 (Play、GMS、YouTube、Gmail、Maps 等)
    val google_outbound: String? = "", // 留空则跟从 default_outbound / proxy

    val route_social: Boolean? = true, // 常用海外社交 (X / Twitter, Facebook, Instagram, Telegram 等)
    val social_outbound: String? = "",

    val route_ai: Boolean? = true, // 热门 AI 应用 (ChatGPT, Claude, Gemini, Grok, Copilot 等)
    val ai_outbound: String? = "",

    // 自定义应用分组 (一个应用仅属于一个分组，互斥去重)
    val custom_groups: List<CustomAppGroup>? = emptyList(),

    // 自定义域名后缀分组 (一个域名仅属于一个分组，严格互斥、防冗余覆盖)
    val custom_domain_groups: List<CustomDomainGroup>? = emptyList(),

    // 路由规则执行顺序列表 (包含自定义分组、预设规则与系统安全规则的唯一标识符)
    val rule_order: List<String>? = null
) {
    val isBlockQuic: Boolean get() = block_quic ?: true
    val isRouteGoogle: Boolean get() = route_google ?: true
    val isRouteSocial: Boolean get() = route_social ?: true
    val isRouteAi: Boolean get() = route_ai ?: true
    val googleOutbound: String get() = google_outbound ?: ""
    val socialOutbound: String get() = social_outbound ?: ""
    val aiOutbound: String get() = ai_outbound ?: ""
    val customGroups: List<CustomAppGroup> get() = custom_groups ?: emptyList()
    val customDomainGroups: List<CustomDomainGroup> get() = custom_domain_groups ?: emptyList()
    val ruleOrder: List<String>? get() = rule_order

    /**
     * 获取标准默认的路由规则优先级顺序
     */
    fun getDefaultRuleOrder(): List<String> {
        val list = mutableListOf<String>()
        list.add("sys_block_quic")
        list.add("sys_block_ads")
        list.add("sys_bypass_lan")
        customGroups.forEach { list.add("app_group_${it.id}") }
        customDomainGroups.forEach { list.add("domain_group_${it.id}") }
        list.add("preset_google")
        list.add("preset_social")
        list.add("preset_ai")
        return list
    }

    /**
     * 获取当前生效的规则执行顺序 (若保存过自定义顺序则优先采用，并自动合并新规则、剔除失效规则)
     */
    fun getEffectiveRuleOrder(): List<String> {
        val defaultList = getDefaultRuleOrder()
        val saved = rule_order
        if (saved.isNullOrEmpty()) {
            return defaultList
        }
        val validSaved = saved.filter { it in defaultList }
        val remaining = defaultList.filter { it !in validSaved }
        return validSaved + remaining
    }
}

data class CustomAppGroup(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "自定义应用分组",
    val outbound: String? = "", // 留空则跟从 default_outbound / proxy
    val package_names: List<String>? = emptyList(),
    val enabled: Boolean? = true
) {
    val outboundTag: String get() = outbound ?: ""
    val packageNames: List<String> get() = package_names ?: emptyList()
    val isEnabled: Boolean get() = enabled ?: true
}

data class CustomDomainGroup(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "自定义域名分组",
    val outbound: String? = "", // 留空则跟从 default_outbound / proxy
    val domain_suffixes: List<String>? = emptyList(),
    val enabled: Boolean? = true
) {
    val outboundTag: String get() = outbound ?: ""
    val domainSuffixes: List<String> get() = domain_suffixes ?: emptyList()
    val isEnabled: Boolean get() = enabled ?: true
}

data class SimpleConfig(
    val log: SimpleLogConfig = SimpleLogConfig(),
    val dns: SimpleDnsConfig = SimpleDnsConfig(),
    val inbound: SimpleInboundConfig = SimpleInboundConfig(),
    val route: SimpleRouteConfig = SimpleRouteConfig()
)

/**
 * 校验并清理 SimpleConfig 中失效的出站规则：
 * 若引用的节点在新节点或节点组中存在同名项，则予以保留；
 * 若引用的节点或节点组已下线或不存在，则自动重置为 "direct"。
 * 
 * @return 包含更新后的 SimpleConfig 和被重置的规则描述列表
 */
fun SimpleConfig.validateAndCleanOutbounds(
    availableNodeTags: Set<String>,
    availableGroupNames: Set<String> = emptySet()
): Pair<SimpleConfig, List<String>> {
    val standardOutbounds = setOf("", "AUTO-Test", "proxy", "direct", "block")
    val allValidOutbounds = standardOutbounds + availableNodeTags + availableGroupNames
    val resetList = mutableListOf<String>()

    var newGoogle = route.google_outbound
    if (!route.google_outbound.isNullOrEmpty() && route.google_outbound !in allValidOutbounds) {
        newGoogle = "direct"
        resetList.add("Google 全家桶出站")
    }

    var newSocial = route.social_outbound
    if (!route.social_outbound.isNullOrEmpty() && route.social_outbound !in allValidOutbounds) {
        newSocial = "direct"
        resetList.add("常用海外社交出站")
    }

    var newAi = route.ai_outbound
    if (!route.ai_outbound.isNullOrEmpty() && route.ai_outbound !in allValidOutbounds) {
        newAi = "direct"
        resetList.add("热门 AI 应用出站")
    }

    var newDefault = route.default_outbound
    if (route.default_outbound !in allValidOutbounds) {
        newDefault = "direct"
        resetList.add("默认出站")
    }

    val newGroups = route.customGroups.map { group ->
        if (!group.outbound.isNullOrEmpty() && group.outbound !in allValidOutbounds) {
            resetList.add("应用分组「${group.name}」")
            group.copy(outbound = "direct")
        } else {
            group
        }
    }

    val newDomainGroups = route.customDomainGroups.map { group ->
        if (!group.outbound.isNullOrEmpty() && group.outbound !in allValidOutbounds) {
            resetList.add("域名分组「${group.name}」")
            group.copy(outbound = "direct")
        } else {
            group
        }
    }

    val updatedRoute = route.copy(
        google_outbound = newGoogle,
        social_outbound = newSocial,
        ai_outbound = newAi,
        default_outbound = newDefault,
        custom_groups = newGroups,
        custom_domain_groups = newDomainGroups
    )

    return Pair(this.copy(route = updatedRoute), resetList)
}
