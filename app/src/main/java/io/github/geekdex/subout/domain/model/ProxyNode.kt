package io.github.geekdex.subout.domain.model

data class ProxyNode(
    val tag: String,
    val protocol: String,
    val server: String,
    val serverPort: Int,
    val rawJson: String,
    val insecure: Boolean = false
) {
    fun isAnnouncement(): Boolean {
        val s = server.trim().lowercase()
        if (s == "127.0.0.1" || s == "0.0.0.0" || s == "localhost" || s == "hostloc.com") {
            return true
        }

        val t = tag.lowercase()
        val keywords = listOf(
            "公告", "提示", "通知", "到期", "流量", "官网", "购买",
            "地址", "订阅", "更新", "警告", "说明", "剩余", "充值",
            "防失联", "电报", "群", "notice", "announcement",
            "warning", "info", "expire", "traffic"
        )
        return keywords.any { t.contains(it) }
    }
}
