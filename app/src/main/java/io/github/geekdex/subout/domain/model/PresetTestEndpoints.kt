package io.github.geekdex.subout.domain.model

data class PresetTestEndpoint(
    val name: String,
    val url: String,
    val description: String = ""
) {
    val isRecommended: Boolean get() = description.contains("推荐")
    val cleanDescription: String get() = description.replace("（推荐）", "").replace("(推荐)", "").trim()
}

object PresetTestEndpoints {
    val GOOGLE_GSTATIC: PresetTestEndpoint = PresetTestEndpoint(
        name = "Google Gstatic",
        url = "https://www.gstatic.com/generate_204",
        description = "Google 轻量 204 快速检测（推荐）"
    )
    val GOOGLE_204: PresetTestEndpoint = PresetTestEndpoint(
        name = "Google 204",
        url = "https://www.google.com/generate_204",
        description = "Google 官方 204 生成检测"
    )
    val CLOUDFLARE_CP: PresetTestEndpoint = PresetTestEndpoint(
        name = "Cloudflare CP",
        url = "https://cp.cloudflare.com/generate_204",
        description = "Cloudflare 全球边缘 204"
    )
    val CLOUDFLARE_TRACE: PresetTestEndpoint = PresetTestEndpoint(
        name = "Cloudflare Trace",
        url = "https://www.cloudflare.com/cdn-cgi/trace",
        description = "Cloudflare 边缘路由链路检测"
    )
    val APPLE_CAPTIVE: PresetTestEndpoint = PresetTestEndpoint(
        name = "Apple Captive",
        url = "https://www.apple.com/library/test/success.html",
        description = "Apple 设备网络连通性测试"
    )
    val GOOGLE_CONNECTIVITY: PresetTestEndpoint = PresetTestEndpoint(
        name = "Google Connectivity",
        url = "https://connectivitycheck.gstatic.com/generate_204",
        description = "Android 默认网络状态检测"
    )

    val PRESETS: List<PresetTestEndpoint> = listOf(
        GOOGLE_GSTATIC,
        GOOGLE_204,
        CLOUDFLARE_CP,
        CLOUDFLARE_TRACE,
        APPLE_CAPTIVE,
        GOOGLE_CONNECTIVITY
    )

    val DEFAULT: PresetTestEndpoint = GOOGLE_GSTATIC

    fun findByUrl(url: String): PresetTestEndpoint? {
        val cleanUrl = url.trim()
        return PRESETS.firstOrNull { it.url.equals(cleanUrl, ignoreCase = true) }
    }
}
