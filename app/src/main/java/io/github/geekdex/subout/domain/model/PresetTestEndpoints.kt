package io.github.geekdex.subout.domain.model

data class PresetTestEndpoint(
    val name: String,
    val url: String,
    val description: String = ""
)

object PresetTestEndpoints {
    val PRESETS: List<PresetTestEndpoint> = listOf(
        PresetTestEndpoint(
            name = "Google Gstatic",
            url = "https://www.gstatic.com/generate_204",
            description = "Google 轻量 204 快速检测（推荐）"
        ),
        PresetTestEndpoint(
            name = "Google 204",
            url = "https://www.google.com/generate_204",
            description = "Google 官方 204 生成检测"
        ),
        PresetTestEndpoint(
            name = "Cloudflare CP",
            url = "https://cp.cloudflare.com/generate_204",
            description = "Cloudflare 全球边缘 204"
        ),
        PresetTestEndpoint(
            name = "Cloudflare Trace",
            url = "https://www.cloudflare.com/cdn-cgi/trace",
            description = "Cloudflare 边缘路由链路检测"
        ),
        PresetTestEndpoint(
            name = "Apple Captive",
            url = "https://www.apple.com/library/test/success.html",
            description = "Apple 设备网络连通性测试"
        ),
        PresetTestEndpoint(
            name = "Google Connectivity",
            url = "https://connectivitycheck.gstatic.com/generate_204",
            description = "Android 默认网络状态检测"
        )
    )

    val DEFAULT: PresetTestEndpoint = PRESETS[0]

    fun findByUrl(url: String): PresetTestEndpoint? {
        val cleanUrl = url.trim()
        return PRESETS.firstOrNull { it.url.equals(cleanUrl, ignoreCase = true) }
    }
}
