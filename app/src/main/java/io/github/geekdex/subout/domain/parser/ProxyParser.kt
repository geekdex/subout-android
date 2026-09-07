package io.github.geekdex.subout.domain.parser

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.github.geekdex.subout.domain.model.ProxyNode
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Base64

object ProxyParser {

    private val gson = Gson()

    private val NON_PROXY_TYPES = setOf(
        "direct", "block", "dns", "selector", "urltest", "fallback", "drop"
    )

    fun decodeBase64(input: String): String? {
        val cleaned = input.filter { !it.isWhitespace() && it != '\r' && it != '\n' }
        if (cleaned.isEmpty()) return null

        val rem = cleaned.length % 4
        val padded = if (rem > 0) cleaned + "=".repeat(4 - rem) else cleaned

        // Try standard Base64 first
        try {
            val bytes = Base64.getDecoder().decode(padded)
            return String(bytes, StandardCharsets.UTF_8)
        } catch (_: Exception) {
        }

        // Try URL-safe Base64
        try {
            val bytes = Base64.getUrlDecoder().decode(padded)
            return String(bytes, StandardCharsets.UTF_8)
        } catch (_: Exception) {
        }

        return null
    }

    fun maybeDecodeBase64(input: String): String {
        val decoded = decodeBase64(input) ?: return input
        val isReadable = decoded.all { it.isLetterOrDigit() || it.isWhitespace() || it in "!@#$%^&*()-_=+[]{};:'\",.<>?/\\|`~" }
        return if (isReadable) decoded else input
    }

    fun parseSubscription(content: String): Pair<List<ProxyNode>, List<String>> {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return Pair(emptyList(), emptyList())

        // 1. Check if the content is JSON (e.g. sing-box JSON configuration or outbound array)
        val candidateJson = when {
            trimmed.startsWith("{") || trimmed.startsWith("[") -> trimmed
            else -> {
                val decoded = decodeBase64(trimmed)
                if (decoded != null && (decoded.trim().startsWith("{") || decoded.trim().startsWith("["))) {
                    decoded.trim()
                } else null
            }
        }

        if (candidateJson != null) {
            val jsonResult = parseSingBoxJson(candidateJson)
            if (jsonResult != null && (jsonResult.first.isNotEmpty() || jsonResult.second.isNotEmpty())) {
                return deduplicateNodes(jsonResult.first, jsonResult.second)
            }
        }

        // 2. Line-by-line URI parsing
        val rawNodes = mutableListOf<ProxyNode>()
        val skipped = mutableListOf<String>()

        val processedContent = decodeBase64(trimmed) ?: trimmed

        for (line in processedContent.lines()) {
            val lineTrimmed = line.trim()
            if (lineTrimmed.isEmpty()) continue

            val node = parseLine(lineTrimmed) ?: continue
            if (node.isAnnouncement()) {
                skipped.add(node.tag)
            } else {
                rawNodes.add(node)
            }
        }

        return deduplicateNodes(rawNodes, skipped)
    }

    fun deduplicateNodes(rawNodes: List<ProxyNode>, skipped: List<String>): Pair<List<ProxyNode>, List<String>> {
        val tagCounts = mutableMapOf<String, Int>()
        val deduplicatedNodes = mutableListOf<ProxyNode>()

        for (node in rawNodes) {
            val count = tagCounts.getOrDefault(node.tag, 0)
            tagCounts[node.tag] = count + 1

            val uniqueTag = if (count == 0) {
                node.tag
            } else {
                "${node.tag}-${count + 1}"
            }

            if (uniqueTag != node.tag) {
                val updatedJson = try {
                    val jsonObj = gson.fromJson(node.rawJson, JsonObject::class.java)
                    jsonObj.addProperty("tag", uniqueTag)
                    gson.toJson(jsonObj)
                } catch (_: Exception) {
                    node.rawJson
                }
                deduplicatedNodes.add(node.copy(tag = uniqueTag, rawJson = updatedJson))
            } else {
                deduplicatedNodes.add(node)
            }
        }

        return Pair(deduplicatedNodes, skipped)
    }

    fun parseSingBoxJson(jsonStr: String): Pair<List<ProxyNode>, List<String>>? {
        return try {
            val root = JsonParser.parseString(jsonStr)
            val outboundsArray = when {
                root.isJsonObject -> {
                    val obj = root.asJsonObject
                    when {
                        obj.has("outbounds") && obj.get("outbounds").isJsonArray -> obj.getAsJsonArray("outbounds")
                        obj.has("type") -> {
                            val arr = JsonArray()
                            arr.add(obj)
                            arr
                        }
                        else -> return null
                    }
                }
                root.isJsonArray -> root.asJsonArray
                else -> return null
            }

            val rawNodes = mutableListOf<ProxyNode>()
            val skipped = mutableListOf<String>()

            for (elem in outboundsArray) {
                if (!elem.isJsonObject) continue
                val outbound = elem.asJsonObject
                val type = outbound.get("type")?.asString?.lowercase() ?: continue

                // Skip internal routing / selector / direct outbounds
                if (type in NON_PROXY_TYPES) continue

                val tag = outbound.get("tag")?.asString ?: ""
                val server = outbound.get("server")?.asString ?: ""
                val serverPort = outbound.get("server_port")?.let {
                    if (it.isJsonPrimitive) {
                        val p = it.asJsonPrimitive
                        if (p.isNumber) p.asInt else p.asString.toIntOrNull() ?: 0
                    } else 0
                } ?: 0

                val tlsObj = outbound.getAsJsonObject("tls")
                val insecure = (tlsObj?.get("insecure")?.asBoolean ?: false) ||
                        (outbound.get("insecure")?.asBoolean ?: false)

                val finalTag = if (tag.isNotEmpty()) tag else if (server.isNotEmpty()) "$server:$serverPort" else "node"

                val node = ProxyNode(
                    tag = finalTag,
                    protocol = type,
                    server = server,
                    serverPort = serverPort,
                    rawJson = gson.toJson(outbound),
                    insecure = insecure
                )

                if (node.isAnnouncement()) {
                    skipped.add(node.tag)
                } else {
                    rawNodes.add(node)
                }
            }

            Pair(rawNodes, skipped)
        } catch (_: Exception) {
            null
        }
    }

    fun parseLine(line: String): ProxyNode? {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return null

        // 0. Sing-box outbound JSON line
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            try {
                val root = JsonParser.parseString(trimmed)
                if (root.isJsonObject) {
                    val outbound = root.asJsonObject
                    val type = outbound.get("type")?.asString?.lowercase()
                    if (type != null && type !in NON_PROXY_TYPES) {
                        val tag = outbound.get("tag")?.asString ?: ""
                        val server = outbound.get("server")?.asString ?: ""
                        val serverPort = outbound.get("server_port")?.let {
                            if (it.isJsonPrimitive) {
                                val p = it.asJsonPrimitive
                                if (p.isNumber) p.asInt else p.asString.toIntOrNull() ?: 0
                            } else 0
                        } ?: 0
                        val tlsObj = outbound.getAsJsonObject("tls")
                        val insecure = (tlsObj?.get("insecure")?.asBoolean ?: false) ||
                                (outbound.get("insecure")?.asBoolean ?: false)
                        val finalTag = if (tag.isNotEmpty()) tag else if (server.isNotEmpty()) "$server:$serverPort" else "node"
                        return ProxyNode(finalTag, type, server, serverPort, gson.toJson(outbound), insecure)
                    }
                }
            } catch (_: Exception) {}
        }

        // 1. VMess
        if (trimmed.startsWith("vmess://", ignoreCase = true)) {
            val payload = trimmed.substring(8)
            return parseVmess(payload)
        }

        // 2. HTTPS base64 special format: https://base64#tag or https://base64
        if (trimmed.startsWith("https://", ignoreCase = true)) {
            val rest = trimmed.substring(8)
            val hashIdx = rest.indexOf('#')
            val base64Part = if (hashIdx != -1) rest.substring(0, hashIdx) else rest
            val fragment = if (hashIdx != -1) rest.substring(hashIdx) else ""

            if (!base64Part.contains('@') && !base64Part.contains(':')) {
                val decoded = decodeBase64(base64Part)
                if (decoded != null) {
                    val cleanDecoded = decoded.trim()
                    if (cleanDecoded.contains("://")) {
                        val innerNode = parseLine(cleanDecoded)
                        if (innerNode != null) return innerNode
                    } else if (cleanDecoded.contains('@')) {
                        val mockUrl = "http://$cleanDecoded$fragment"
                        val parsed = parseUrl(mockUrl, forceHttps = true)
                        if (parsed != null) return parsed
                    }
                }
            }
        }

        // 3. Shadowsocks special format: ss://base64#tag where base64 contains everything
        if (trimmed.startsWith("ss://", ignoreCase = true)) {
            val ssNode = parseShadowsocks(trimmed)
            if (ssNode != null) return ssNode
        }

        // 4. Standard URI schemes
        return parseUrl(trimmed, forceHttps = false)
    }

    private fun parseVmess(payload: String): ProxyNode? {
        val decoded = decodeBase64(payload) ?: return null
        return try {
            val vj = gson.fromJson(decoded, JsonObject::class.java) ?: return null

            val add = vj.get("add")?.asString ?: return null
            val port = when {
                vj.get("port")?.isJsonPrimitive == true -> {
                    val prim = vj.getAsJsonPrimitive("port")
                    if (prim.isNumber) prim.asInt else prim.asString.toIntOrNull() ?: 443
                }
                else -> 443
            }

            val id = vj.get("id")?.asString ?: return null
            val aid = when {
                vj.get("aid")?.isJsonPrimitive == true -> {
                    val prim = vj.getAsJsonPrimitive("aid")
                    if (prim.isNumber) prim.asInt else prim.asString.toIntOrNull() ?: 0
                }
                else -> 0
            }

            val ps = vj.get("ps")?.asString?.trim()
            val tag = if (!ps.isNullOrEmpty()) ps else "$add:$port"

            val tlsStr = vj.get("tls")?.asString
            val hasTls = tlsStr.equals("tls", ignoreCase = true) || port == 443

            val sni = vj.get("sni")?.asString
                ?: vj.get("host")?.asString
                ?: if (!isIpAddress(add) && add.isNotEmpty()) add else null

            val tlsObj: JsonObject? = if (hasTls) {
                JsonObject().apply {
                    addProperty("enabled", true)
                    if (!sni.isNullOrEmpty()) {
                        addProperty("server_name", sni)
                    }
                }
            } else null

            val net = vj.get("net")?.asString
            val path = vj.get("path")?.asString ?: "/"
            val host = vj.get("host")?.asString ?: ""

            val transportObj: JsonObject? = when (net) {
                "ws" -> JsonObject().apply {
                    addProperty("type", "ws")
                    addProperty("path", path)
                    val headers = JsonObject()
                    if (host.isNotEmpty()) {
                        headers.addProperty("Host", host)
                    }
                    add("headers", headers)
                }
                "grpc" -> JsonObject().apply {
                    addProperty("type", "grpc")
                    addProperty("service_name", if (path != "/") path else "")
                }
                "h2", "http" -> JsonObject().apply {
                    addProperty("type", "http")
                    addProperty("path", path)
                    if (host.isNotEmpty()) {
                        val hostArr = com.google.gson.JsonArray()
                        hostArr.add(host)
                        add("host", hostArr)
                    }
                }
                else -> null
            }

            val outbound = JsonObject().apply {
                addProperty("type", "vmess")
                addProperty("tag", tag)
                addProperty("server", add)
                addProperty("server_port", port)
                addProperty("uuid", id)
                addProperty("alter_id", aid)
                addProperty("security", "auto")
                if (tlsObj != null) add("tls", tlsObj)
                if (transportObj != null) add("transport", transportObj)
            }

            ProxyNode(
                tag = tag,
                protocol = "vmess",
                server = add,
                serverPort = port,
                rawJson = gson.toJson(outbound),
                insecure = false
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseShadowsocks(line: String): ProxyNode? {
        try {
            val withoutScheme = line.substring(5)
            val hashIdx = withoutScheme.indexOf('#')
            val fragment = if (hashIdx != -1) withoutScheme.substring(hashIdx + 1) else ""
            val mainPart = if (hashIdx != -1) withoutScheme.substring(0, hashIdx) else withoutScheme

            val tag = if (fragment.isNotEmpty()) decodeUrl(fragment) else ""

            if (mainPart.contains('@')) {
                val atIdx = mainPart.indexOf('@')
                val userPart = mainPart.substring(0, atIdx)
                val serverPart = mainPart.substring(atIdx + 1)

                val (server, port) = parseHostPort(serverPart, 8388)
                val (method, password) = parseSsUserInfo(userPart) ?: return null

                val finalTag = if (tag.isNotEmpty()) tag else "$server:$port"
                val outbound = JsonObject().apply {
                    addProperty("type", "shadowsocks")
                    addProperty("tag", finalTag)
                    addProperty("server", server)
                    addProperty("server_port", port)
                    addProperty("method", method)
                    addProperty("password", password)
                }

                return ProxyNode(
                    tag = finalTag,
                    protocol = "shadowsocks",
                    server = server,
                    serverPort = port,
                    rawJson = gson.toJson(outbound)
                )
            } else {
                val decoded = decodeBase64(mainPart) ?: return null
                if (decoded.contains('@')) {
                    val atIdx = decoded.indexOf('@')
                    val userPart = decoded.substring(0, atIdx)
                    val serverPart = decoded.substring(atIdx + 1)

                    val (server, port) = parseHostPort(serverPart, 8388)
                    val (method, password) = parseSsUserInfo(userPart) ?: return null

                    val finalTag = if (tag.isNotEmpty()) tag else "$server:$port"
                    val outbound = JsonObject().apply {
                        addProperty("type", "shadowsocks")
                        addProperty("tag", finalTag)
                        addProperty("server", server)
                        addProperty("server_port", port)
                        addProperty("method", method)
                        addProperty("password", password)
                    }

                    return ProxyNode(
                        tag = finalTag,
                        protocol = "shadowsocks",
                        server = server,
                        serverPort = port,
                        rawJson = gson.toJson(outbound)
                    )
                }
            }
        } catch (_: Exception) {
        }
        return null
    }

    private fun parseSsUserInfo(userPart: String): Pair<String, String>? {
        if (userPart.contains(':')) {
            val parts = userPart.split(':', limit = 2)
            return Pair(parts[0], parts[1])
        }
        val decoded = decodeBase64(userPart) ?: return null
        if (decoded.contains(':')) {
            val parts = decoded.split(':', limit = 2)
            return Pair(parts[0], parts[1])
        }
        return null
    }

    data class ParsedUri(
        val scheme: String,
        val host: String,
        val port: Int?,
        val userInfo: String?,
        val path: String?,
        val queryParams: Map<String, String>,
        val fragment: String?
    )

    fun parseGenericUri(uriStr: String): ParsedUri? {
        try {
            val s = uriStr.trim()
            val schemeEnd = s.indexOf("://")
            if (schemeEnd == -1) return null

            val scheme = s.substring(0, schemeEnd).lowercase()
            var rest = s.substring(schemeEnd + 3)

            val fragment = if (rest.contains('#')) {
                val hashIdx = rest.indexOf('#')
                val frag = rest.substring(hashIdx + 1)
                rest = rest.substring(0, hashIdx)
                decodeUrl(frag)
            } else null

            val queryParams = mutableMapOf<String, String>()
            if (rest.contains('?')) {
                val qIdx = rest.indexOf('?')
                val queryStr = rest.substring(qIdx + 1)
                rest = rest.substring(0, qIdx)
                for (pair in queryStr.split('&')) {
                    val parts = pair.split('=', limit = 2)
                    if (parts.isNotEmpty() && parts[0].isNotEmpty()) {
                        val k = decodeUrl(parts[0])
                        val v = if (parts.size > 1) decodeUrl(parts[1]) else ""
                        queryParams[k] = v
                    }
                }
            }

            var path: String? = null
            if (rest.contains('/')) {
                val slashIdx = rest.indexOf('/')
                path = rest.substring(slashIdx)
                rest = rest.substring(0, slashIdx)
            }

            var userInfo: String? = null
            if (rest.contains('@')) {
                val atIdx = rest.lastIndexOf('@')
                userInfo = decodeUrl(rest.substring(0, atIdx))
                rest = rest.substring(atIdx + 1)
            }

            val (host, port) = parseHostPort(rest, -1)
            if (host.isEmpty()) return null

            return ParsedUri(
                scheme = scheme,
                host = host,
                port = if (port != -1) port else null,
                userInfo = userInfo,
                path = path,
                queryParams = queryParams,
                fragment = fragment
            )
        } catch (_: Exception) {
            return null
        }
    }

    private fun parseUrl(line: String, forceHttps: Boolean): ProxyNode? {
        val uri = parseGenericUri(line) ?: return null

        val scheme = uri.scheme
        val host = uri.host
        val defaultPort = when (scheme) {
            "https" -> 443
            "http" -> 80
            "socks", "socks5" -> 1080
            "trojan", "vless", "anytls", "hysteria", "hysteria2", "tuic" -> 443
            else -> 443
        }
        val port = uri.port ?: defaultPort
        val tag = uri.fragment?.ifEmpty { "$host:$port" } ?: "$host:$port"

        val userInfo = uri.userInfo
        val username = if (!userInfo.isNullOrEmpty()) {
            if (userInfo.contains(':')) userInfo.substringBefore(':') else userInfo
        } else null
        val password = if (!userInfo.isNullOrEmpty() && userInfo.contains(':')) {
            userInfo.substringAfter(':')
        } else null

        val params = uri.queryParams
        val sni = extractSni(host, params)

        when (scheme) {
            "socks", "socks5" -> {
                val outbound = JsonObject().apply {
                    addProperty("type", "socks")
                    addProperty("tag", tag)
                    addProperty("server", host)
                    addProperty("server_port", port)
                    if (!username.isNullOrEmpty()) addProperty("username", username)
                    if (!password.isNullOrEmpty()) addProperty("password", password)
                }
                return ProxyNode(tag, "socks", host, port, gson.toJson(outbound))
            }
            "http", "https" -> {
                val isSecure = scheme == "https" || forceHttps || port == 443
                val tlsObj = if (isSecure) {
                    JsonObject().apply {
                        addProperty("enabled", true)
                        if (!sni.isNullOrEmpty()) addProperty("server_name", sni)
                        val insecure = params["allowInsecure"] == "1" || params["allowInsecure"] == "true"
                        if (insecure) addProperty("insecure", true)
                    }
                } else null

                val outbound = JsonObject().apply {
                    addProperty("type", "http")
                    addProperty("tag", tag)
                    addProperty("server", host)
                    addProperty("server_port", port)
                    if (!username.isNullOrEmpty()) addProperty("username", username)
                    if (!password.isNullOrEmpty()) addProperty("password", password)
                    if (tlsObj != null) add("tls", tlsObj)
                }
                return ProxyNode(tag, "http", host, port, gson.toJson(outbound))
            }
            "trojan" -> {
                val pass = password ?: username ?: ""
                val hasTls = params["security"]?.equals("none", ignoreCase = true) != true
                val tlsObj = if (hasTls) {
                    JsonObject().apply {
                        addProperty("enabled", true)
                        if (!sni.isNullOrEmpty()) addProperty("server_name", sni)
                        val insecure = params["allowInsecure"] == "1" || params["allowInsecure"] == "true"
                        if (insecure) addProperty("insecure", true)
                    }
                } else null

                val transportObj = parseTransport(params)

                val outbound = JsonObject().apply {
                    addProperty("type", "trojan")
                    addProperty("tag", tag)
                    addProperty("server", host)
                    addProperty("server_port", port)
                    addProperty("password", pass)
                    if (tlsObj != null) add("tls", tlsObj)
                    if (transportObj != null) add("transport", transportObj)
                }
                return ProxyNode(tag, "trojan", host, port, gson.toJson(outbound))
            }
            "vless" -> {
                val uuid = username ?: ""
                val flow = params["flow"]
                val security = params["security"] ?: ""
                val hasTls = security == "tls" || security == "reality" || security.isNotEmpty()

                val tlsObj = if (hasTls) {
                    JsonObject().apply {
                        addProperty("enabled", true)
                        if (!sni.isNullOrEmpty()) addProperty("server_name", sni)
                        val insecure = params["allowInsecure"] == "1" || params["allowInsecure"] == "true"
                        if (insecure) addProperty("insecure", true)

                        if (security == "reality") {
                            val realityObj = JsonObject().apply {
                                addProperty("enabled", true)
                                addProperty("public_key", params["pbk"] ?: "")
                                val sid = params["sid"]
                                if (!sid.isNullOrEmpty()) addProperty("short_id", sid)
                            }
                            add("reality", realityObj)
                            val fp = params["fp"]?.ifEmpty { "chrome" } ?: "chrome"
                            val utlsObj = JsonObject().apply {
                                addProperty("enabled", true)
                                addProperty("fingerprint", fp)
                            }
                            add("utls", utlsObj)
                        } else {
                            val fp = params["fp"]
                            if (!fp.isNullOrEmpty()) {
                                val utlsObj = JsonObject().apply {
                                    addProperty("enabled", true)
                                    addProperty("fingerprint", fp)
                                }
                                add("utls", utlsObj)
                            }
                        }
                    }
                } else null

                val transportObj = parseTransport(params)
                val packetEncoding = params["packetEncoding"] ?: "xudp"

                val outbound = JsonObject().apply {
                    addProperty("type", "vless")
                    addProperty("tag", tag)
                    addProperty("server", host)
                    addProperty("server_port", port)
                    addProperty("uuid", uuid)
                    if (!flow.isNullOrEmpty()) addProperty("flow", flow)
                    if (tlsObj != null) add("tls", tlsObj)
                    addProperty("packet_encoding", packetEncoding)
                    if (transportObj != null) add("transport", transportObj)
                }
                return ProxyNode(tag, "vless", host, port, gson.toJson(outbound))
            }
            "hysteria" -> {
                val auth = if (password != null) "$username:$password" else username
                val authDecoded = auth?.let { maybeDecodeBase64(it) }
                val allowInsecure = params["insecure"] == "1" || params["insecure"] == "true"
                val upMbps = parseMbps(params["up"] ?: params["up_mbps"])
                val downMbps = parseMbps(params["down"] ?: params["down_mbps"])
                val obfs = params["obfs"] ?: params["obfs-password"] ?: params["obfs_password"]

                val tlsObj = JsonObject().apply {
                    addProperty("enabled", true)
                    if (!sni.isNullOrEmpty()) addProperty("server_name", sni)
                    if (allowInsecure) addProperty("insecure", true)
                }

                val outbound = JsonObject().apply {
                    addProperty("type", "hysteria")
                    addProperty("tag", tag)
                    addProperty("server", host)
                    addProperty("server_port", port)
                    if (!authDecoded.isNullOrEmpty()) addProperty("auth_str", authDecoded)
                    if (upMbps != null) addProperty("up_mbps", upMbps)
                    if (downMbps != null) addProperty("down_mbps", downMbps)
                    if (!obfs.isNullOrEmpty()) addProperty("obfs", maybeDecodeBase64(obfs))
                    add("tls", tlsObj)
                }
                return ProxyNode(tag, "hysteria", host, port, gson.toJson(outbound))
            }
            "hysteria2" -> {
                val pass = password ?: username ?: ""
                val passDecoded = maybeDecodeBase64(pass)
                val allowInsecure = params["insecure"] == "1" || params["insecure"] == "true"
                val upMbps = parseMbps(params["up"] ?: params["up_mbps"])
                val downMbps = parseMbps(params["down"] ?: params["down_mbps"])

                val obfsType = params["obfs"]
                val obfsPass = params["obfs-password"] ?: params["obfs_password"]
                val obfsObj = if (!obfsType.isNullOrEmpty()) {
                    JsonObject().apply {
                        addProperty("type", obfsType)
                        if (!obfsPass.isNullOrEmpty()) {
                            addProperty("password", maybeDecodeBase64(obfsPass))
                        }
                    }
                } else null

                val tlsObj = JsonObject().apply {
                    addProperty("enabled", true)
                    if (!sni.isNullOrEmpty()) addProperty("server_name", sni)
                    if (allowInsecure) addProperty("insecure", true)
                }

                val outbound = JsonObject().apply {
                    addProperty("type", "hysteria2")
                    addProperty("tag", tag)
                    addProperty("server", host)
                    addProperty("server_port", port)
                    addProperty("password", passDecoded)
                    if (upMbps != null) addProperty("up_mbps", upMbps)
                    if (downMbps != null) addProperty("down_mbps", downMbps)
                    if (obfsObj != null) add("obfs", obfsObj)
                    add("tls", tlsObj)
                }
                return ProxyNode(tag, "hysteria2", host, port, gson.toJson(outbound))
            }
            "anytls" -> {
                val pass = password ?: username ?: ""
                val peer = params["peer"] ?: sni ?: host
                val allowInsecure = params["allowInsecure"] == "1" || params["insecure"] == "1" || params["allowInsecure"] == "true"

                val tlsObj = JsonObject().apply {
                    addProperty("enabled", true)
                    if (peer.isNotEmpty()) addProperty("server_name", peer)
                    if (allowInsecure) addProperty("insecure", true)
                }

                val outbound = JsonObject().apply {
                    addProperty("type", "trojan")
                    addProperty("tag", tag)
                    addProperty("server", host)
                    addProperty("server_port", port)
                    addProperty("password", pass)
                    add("tls", tlsObj)
                }
                return ProxyNode(tag, "trojan", host, port, gson.toJson(outbound), allowInsecure)
            }
            "tuic" -> {
                val uuid = username ?: ""
                val pass = password ?: ""
                val congestionControl = params["congestion_control"] ?: params["congestion"] ?: "bbr"
                val alpn = params["alpn"] ?: "h3"
                val allowInsecure = params["allow_insecure"] == "1" || params["insecure"] == "1" || params["allowInsecure"] == "true"
                val udpRelayMode = params["udp_relay_mode"] ?: "native"

                val tlsObj = JsonObject().apply {
                    addProperty("enabled", true)
                    if (!sni.isNullOrEmpty()) addProperty("server_name", sni)
                    val alpnArr = JsonArray()
                    for (a in alpn.split(',')) {
                        if (a.isNotBlank()) alpnArr.add(a.trim())
                    }
                    if (alpnArr.size() > 0) {
                        add("alpn", alpnArr)
                    }
                    if (allowInsecure) addProperty("insecure", true)
                }

                val outbound = JsonObject().apply {
                    addProperty("type", "tuic")
                    addProperty("tag", tag)
                    addProperty("server", host)
                    addProperty("server_port", port)
                    if (uuid.isNotEmpty()) addProperty("uuid", uuid)
                    if (pass.isNotEmpty()) addProperty("password", pass)
                    addProperty("congestion_control", congestionControl)
                    addProperty("udp_relay_mode", udpRelayMode)
                    add("tls", tlsObj)
                }
                return ProxyNode(tag, "tuic", host, port, gson.toJson(outbound), allowInsecure)
            }
            else -> return null
        }
    }

    private fun extractSni(host: String, params: Map<String, String>): String? {
        val sni = params["sni"] ?: params["host"]
        if (!sni.isNullOrEmpty()) return sni
        if (!isIpAddress(host) && host.isNotEmpty()) return host
        return null
    }

    private fun parseTransport(params: Map<String, String>): JsonObject? {
        val type = params["type"] ?: return null
        return when (type) {
            "ws" -> JsonObject().apply {
                addProperty("type", "ws")
                addProperty("path", params["path"] ?: "/")
                val host = params["host"] ?: ""
                val headers = JsonObject()
                if (host.isNotEmpty()) {
                    headers.addProperty("Host", host)
                }
                add("headers", headers)
            }
            "grpc" -> JsonObject().apply {
                addProperty("type", "grpc")
                addProperty("service_name", params["serviceName"] ?: "")
            }
            "http", "h2" -> JsonObject().apply {
                addProperty("type", "http")
                addProperty("path", params["path"] ?: "/")
                val host = params["host"] ?: ""
                if (host.isNotEmpty()) {
                    val hostArr = com.google.gson.JsonArray()
                    hostArr.add(host)
                    add("host", hostArr)
                }
            }
            else -> null
        }
    }

    private fun parseMbps(valStr: String?): Int? {
        if (valStr == null) return null
        val num = valStr.takeWhile { it.isDigit() }
        return num.toIntOrNull()
    }

    private fun parseHostPort(serverPart: String, defaultPort: Int): Pair<String, Int> {
        val s = serverPart.trim()
        val colonIdx = s.lastIndexOf(':')
        if (colonIdx != -1 && !s.endsWith("]")) {
            val host = s.substring(0, colonIdx)
            val portStr = s.substring(colonIdx + 1)
            val port = portStr.toIntOrNull() ?: defaultPort
            return Pair(host, port)
        }
        return Pair(s, defaultPort)
    }

    private fun decodeUrl(s: String): String {
        return try {
            URLDecoder.decode(s, StandardCharsets.UTF_8.name())
        } catch (_: Exception) {
            s
        }
    }

    private fun isIpAddress(str: String): Boolean {
        if (str.isEmpty()) return false
        val parts = str.split('.')
        if (parts.size == 4 && parts.all { it.toIntOrNull() in 0..255 }) {
            return true
        }
        return str.contains(':')
    }
}
