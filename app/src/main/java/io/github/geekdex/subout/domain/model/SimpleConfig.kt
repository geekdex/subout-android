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
    val foreign_dns: String = "fakeip"
)

data class SimpleInboundConfig(
    val inbound_type: String = "tun", // "tun", "mixed"
    val mixed_port: Int = 2080,
    val allow_lan: Boolean = false,
    val tun_stack: String = "system", // "system", "gvisor", "mixed"
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
    val ai_outbound: String? = ""
) {
    val isBlockQuic: Boolean get() = block_quic ?: true
    val isRouteGoogle: Boolean get() = route_google ?: true
    val isRouteSocial: Boolean get() = route_social ?: true
    val isRouteAi: Boolean get() = route_ai ?: true
    val googleOutbound: String get() = google_outbound ?: ""
    val socialOutbound: String get() = social_outbound ?: ""
    val aiOutbound: String get() = ai_outbound ?: ""
}

data class SimpleConfig(
    val log: SimpleLogConfig = SimpleLogConfig(),
    val dns: SimpleDnsConfig = SimpleDnsConfig(),
    val inbound: SimpleInboundConfig = SimpleInboundConfig(),
    val route: SimpleRouteConfig = SimpleRouteConfig()
)
