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
    val default_outbound: String = "AUTO-Test" // "AUTO-Test", "proxy", "direct"
)

data class SimpleConfig(
    val log: SimpleLogConfig = SimpleLogConfig(),
    val dns: SimpleDnsConfig = SimpleDnsConfig(),
    val inbound: SimpleInboundConfig = SimpleInboundConfig(),
    val route: SimpleRouteConfig = SimpleRouteConfig()
)
