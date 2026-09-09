package io.github.geekdex.subout.domain.model

data class PresetApp(
    val name: String,
    val packageName: String,
    val description: String = ""
)

data class PresetCategory(
    val id: String,
    val title: String,
    val summary: String,
    val apps: List<PresetApp>,
    val domainSuffixes: List<String>,
    val ruleSets: List<String>
)

object AppRulePresets {

    val google = PresetCategory(
        id = "google",
        title = "Google 常用全家桶",
        summary = "涵盖 Google Play 商店、GMS 框架、YouTube、Gmail、Maps、Photos 等 11+ 常用服务",
        apps = listOf(
            PresetApp("Google Play 商店", "com.android.vending", "应用商店与自动更新"),
            PresetApp("Google Play 服务", "com.google.android.gms", "底层账号与推送服务 (GMS)"),
            PresetApp("Google 服务框架", "com.google.android.gsf", "核心框架服务 (GSF)"),
            PresetApp("YouTube", "com.google.android.youtube", "官方视频客户端"),
            PresetApp("YouTube Music", "com.google.android.apps.youtube.music", "官方音乐客户端"),
            PresetApp("Google 搜索 & 助理", "com.google.android.googlequicksearchbox", "搜索主入口"),
            PresetApp("Chrome 浏览器", "com.android.chrome", "谷歌浏览器"),
            PresetApp("Gmail 邮箱", "com.google.android.gm", "谷歌官方邮箱"),
            PresetApp("Google 地图", "com.google.android.apps.maps", "地图导航"),
            PresetApp("Google 相册", "com.google.android.apps.photos", "照片备份与同步"),
            PresetApp("Google 云端硬盘", "com.google.android.apps.docs", "云盘文件")
        ),
        domainSuffixes = listOf(
            ".google.com",
            ".googleapis.com",
            ".google.com.hk",
            ".goog",
            ".gstatic.com",
            ".googleusercontent.com",
            ".gvt1.com",
            ".gvt2.com",
            ".android.com",
            ".youtube.com",
            ".googlevideo.com",
            ".ytimg.com",
            ".ggpht.com",
            ".youtu.be"
        ),
        ruleSets = listOf("geosite-google", "geosite-youtube")
    )

    val social = PresetCategory(
        id = "social",
        title = "国际社交与通讯应用",
        summary = "涵盖 X (Twitter)、Instagram、Telegram、Facebook、WhatsApp、Discord 等",
        apps = listOf(
            PresetApp("X (Twitter)", "com.twitter.android", "推特/X 官方客户端"),
            PresetApp("Instagram", "com.instagram.android", "Ins 照片与社交"),
            PresetApp("Telegram", "org.telegram.messenger", "电报即时通讯"),
            PresetApp("Telegram X", "org.thunderdog.challegram", "电报官方实验版"),
            PresetApp("Facebook", "com.facebook.katana", "脸书主客户端"),
            PresetApp("Messenger", "com.facebook.orca", "脸书即时通讯"),
            PresetApp("WhatsApp", "com.whatsapp", "WhatsApp 通讯"),
            PresetApp("Discord", "com.discord", "社区语音与文字聊天"),
            PresetApp("Threads", "com.instagram.barcelona", "Meta 社交应用"),
            PresetApp("Reddit", "com.reddit.frontpage", "海外综合社区论坛")
        ),
        domainSuffixes = listOf(
            ".x.com",
            ".twitter.com",
            ".t.co",
            ".twimg.com",
            ".facebook.com",
            ".fbcdn.net",
            ".instagram.com",
            ".cdninstagram.com",
            ".threads.net",
            ".t.me",
            ".telegram.org",
            ".whatsapp.com",
            ".whatsapp.net",
            ".discord.com",
            ".discordapp.com",
            ".reddit.com",
            ".redd.it"
        ),
        ruleSets = listOf("geosite-twitter", "geosite-facebook", "geosite-instagram", "geosite-telegram", "geosite-discord")
    )

    val ai = PresetCategory(
        id = "ai",
        title = "热门 AI 智能应用",
        summary = "涵盖 ChatGPT、Claude、Gemini、Grok、Microsoft Copilot、Perplexity 等",
        apps = listOf(
            PresetApp("ChatGPT", "com.openai.chatgpt", "OpenAI 官方客户端"),
            PresetApp("Claude", "com.anthropic.claude", "Anthropic 官方客户端"),
            PresetApp("Google Gemini", "com.google.android.apps.bard", "谷歌 Gemini 客户端"),
            PresetApp("Microsoft Copilot", "com.microsoft.copilot", "微软 Copilot 助手"),
            PresetApp("Perplexity AI", "ai.perplexity.app.android", "AI 智能问答搜索")
        ),
        domainSuffixes = listOf(
            ".openai.com",
            ".chatgpt.com",
            ".oaistatic.com",
            ".oaiusercontent.com",
            ".anthropic.com",
            ".claude.ai",
            ".bard.google.com",
            ".gemini.google.com",
            ".x.ai",
            ".grok.com",
            ".copilot.microsoft.com",
            ".perplexity.ai"
        ),
        ruleSets = listOf("geosite-openai", "geosite-anthropic", "geosite-xai", "geosite-category-ai-chat-!cn")
    )
}
