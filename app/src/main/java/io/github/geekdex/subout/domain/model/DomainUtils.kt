package io.github.geekdex.subout.domain.model

import java.net.URI

object DomainUtils {

    private val DOMAIN_REGEX = Regex("^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$")

    /**
     * 规范化域名输入：
     * 1. 过滤前后空格，转为全小写；
     * 2. 若粘贴的是包含 http:// 或 https:// 的完整 URL，提取其 host；
     * 3. 去除端口号（如 :443）、路径、查询参数；
     * 4. 去除通配符或前导点（如 *.google.com 或 .google.com -> google.com）；
     * 5. 去除末尾点。
     */
    fun normalize(input: String): String {
        var s = input.trim().lowercase()
        if (s.isEmpty()) return ""

        // 如果用户直接粘贴了带协议头的完整 URL
        if (s.startsWith("http://") || s.startsWith("https://") || s.startsWith("ftp://")) {
            try {
                val uri = URI(s)
                val host = uri.host
                if (!host.isNullOrBlank()) {
                    s = host.lowercase()
                }
            } catch (_: Exception) {
                val schemeEnd = s.indexOf("://")
                if (schemeEnd != -1) {
                    s = s.substring(schemeEnd + 3)
                }
            }
        }

        // 截断路径与参数
        val pathIndex = s.indexOfAny(charArrayOf('/', '?', '#'))
        if (pathIndex != -1) {
            s = s.substring(0, pathIndex)
        }

        // 截断端口号
        val portIndex = s.lastIndexOf(':')
        if (portIndex != -1) {
            s = s.substring(0, portIndex)
        }

        // 去除通配符与前导点
        while (s.startsWith("*.") || s.startsWith(".")) {
            s = if (s.startsWith("*.")) s.removePrefix("*.") else s.removePrefix(".")
        }

        // 去除末尾点
        s = s.removeSuffix(".")

        return s.trim()
    }

    /**
     * 校验域名格式合法性
     */
    fun isValidDomain(domain: String): Boolean {
        if (domain.isBlank()) return false
        if (domain.length > 253) return false
        if (!domain.contains('.')) return false
        return DOMAIN_REGEX.matches(domain)
    }

    /**
     * 判断 child 是否是 parent 的子域名或自身（例如 api.github.com 是 github.com 的子域名）
     */
    fun isSubdomain(child: String, parent: String): Boolean {
        val c = normalize(child)
        val p = normalize(parent)
        if (c.isEmpty() || p.isEmpty()) return false
        if (c == p) return true
        return c.endsWith(".$p")
    }

    /**
     * 批量解析输入的文本，支持换行、逗号、分号、空格分隔，自动规范化并去重
     */
    fun parseDomains(rawInput: String): List<String> {
        val tokens = rawInput.split('\n', '\r', ',', ';', ' ', '\t')
        val list = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        for (token in tokens) {
            val normalized = normalize(token)
            if (normalized.isNotEmpty() && isValidDomain(normalized) && seen.add(normalized)) {
                list.add(normalized)
            }
        }
        return list
    }
}
