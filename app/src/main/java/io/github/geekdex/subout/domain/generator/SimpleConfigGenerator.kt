package io.github.geekdex.subout.domain.generator

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.github.geekdex.subout.data.db.entities.Node
import io.github.geekdex.subout.domain.model.AppRulePresets
import io.github.geekdex.subout.domain.model.PresetApp
import io.github.geekdex.subout.domain.model.SimpleConfig
import io.github.geekdex.subout.domain.model.SimpleDnsConfig
import io.github.geekdex.subout.domain.model.SimpleInboundConfig
import io.github.geekdex.subout.domain.model.SimpleLogConfig
import io.github.geekdex.subout.domain.model.SimpleRouteConfig

object SimpleConfigGenerator {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    private fun List<String>.toJsonArray(): JsonArray {
        val arr = JsonArray()
        for (item in this) {
            arr.add(item)
        }
        return arr
    }

    private fun List<PresetApp>.toPackagesJsonArray(): JsonArray {
        val arr = JsonArray()
        for (app in this) {
            arr.add(app.packageName)
        }
        return arr
    }

    fun generate(config: SimpleConfig, enabledNodes: List<Node>): JsonObject {
        val root = JsonObject()

        // 1. Log
        root.add("log", buildLogSection(config.log))

        // Collect enabled nodes and prepare node outbounds
        val proxyNodeTags = mutableListOf<String>()
        val nodeOutbounds = JsonArray()

        for (node in enabledNodes) {
            if (node.enabled) {
                proxyNodeTags.add(node.tag)
                try {
                    val nodeVal = gson.fromJson(node.rawJson, JsonObject::class.java)
                    nodeVal.addProperty("tag", node.tag)
                    sanitizeOutboundValue(nodeVal)
                    nodeOutbounds.add(nodeVal)
                } catch (_: Exception) {
                }
            }
        }

        val hasNodes = proxyNodeTags.isNotEmpty()

        // Determine target proxy
        val targetProxy = when {
            config.route.default_outbound == "direct" || !hasNodes -> "direct"
            config.route.default_outbound == "proxy" -> "proxy"
            config.route.default_outbound == "AUTO-Test" -> "AUTO-Test"
            proxyNodeTags.contains(config.route.default_outbound) -> config.route.default_outbound
            else -> "AUTO-Test"
        }

        // 2. DNS
        root.add("dns", buildDnsSection(config.dns, config.route, hasNodes, targetProxy, proxyNodeTags))

        // 3. Inbounds
        root.add("inbounds", buildInboundsSection(config.inbound))

        // 4. Outbounds
        root.add("outbounds", buildOutboundsSection(config.route, proxyNodeTags, nodeOutbounds, hasNodes))

        // 5. Route
        root.add("route", buildRouteSection(config.route, proxyNodeTags, hasNodes, targetProxy))

        // 6. HTTP Clients (sing-box 1.14+ standard for remote rule-sets and downloads)
        root.add("http_clients", buildHttpClientsSection(hasNodes, targetProxy))

        // 7. Experimental
        root.add("experimental", JsonObject())

        return root
    }

    fun generatePrettyString(config: SimpleConfig, enabledNodes: List<Node>): String {
        val json = generate(config, enabledNodes)
        return gson.toJson(json)
    }

    private fun buildLogSection(logConfig: SimpleLogConfig): JsonObject {
        val logObj = JsonObject()
        val level = when (logConfig.level.trim().lowercase()) {
            "trace", "debug", "info", "warn", "error", "fatal", "panic" -> logConfig.level.trim().lowercase()
            else -> "info"
        }
        logObj.addProperty("level", level)
        logObj.addProperty("timestamp", logConfig.timestamp)
        if (logConfig.disabled) {
            logObj.addProperty("disabled", true)
        }
        if (logConfig.output.isNotBlank()) {
            logObj.addProperty("output", logConfig.output.trim())
        }
        return logObj
    }

    private fun buildDnsSection(
        dnsConfig: SimpleDnsConfig,
        routeConfig: SimpleRouteConfig,
        hasNodes: Boolean,
        targetProxy: String,
        proxyNodeTags: List<String>
    ): JsonObject {
        val isFakeIpMode = dnsConfig.mode == "preset_fakeip" ||
                dnsConfig.mode == "fakeip" ||
                dnsConfig.foreign_dns.trim().equals("fakeip", ignoreCase = true)

        val domesticDns = if (dnsConfig.domestic_dns.trim().isEmpty()) "223.5.5.5" else dnsConfig.domestic_dns.trim()
        val foreignDns = if (dnsConfig.foreign_dns.trim().isEmpty()) {
            if (isFakeIpMode) "fakeip" else "https://1.1.1.1/dns-query"
        } else {
            dnsConfig.foreign_dns.trim()
        }

        val servers = JsonArray()
        if (isFakeIpMode) {
            servers.add(buildDnsServer("dns_local", domesticDns, null))
            servers.add(buildDnsServer("dns_fakeip", "fakeip", null))
        } else {
            val remoteDnsDetour = if (hasNodes && targetProxy != "direct") "proxy" else null
            servers.add(buildDnsServer("dns_local", domesticDns, null))
            servers.add(buildDnsServer("dns_remote", foreignDns, remoteDnsDetour))
        }

        val rules = JsonArray()

        if (routeConfig.block_ads) {
            rules.add(JsonObject().apply {
                addProperty("rule_set", "geosite-category-ads-all")
                addProperty("action", "predefined")
                addProperty("rcode", "NOERROR")
            })
        }

        // 1. Intercept HTTPS (Type 65) and SVCB (Type 64) queries with predefined NOERROR when enabled
        // RFC 9460: Returning empty NOERROR gracefully informs clients (e.g. Chrome, Android)
        // that no HTTPS RR exists, forcing instant fallback to standard A/AAAA queries.
        // This prevents ECH (Encrypted Client Hello) from encrypting SNI (which breaks sing-box TLS sniffing),
        // prevents foreign domain leakage to domestic DNS, and eliminates FakeIP non-IP query errors.
        if (dnsConfig.isSuppressEch) {
            rules.add(JsonObject().apply {
                val qTypes = JsonArray().apply {
                    add("HTTPS")
                    add("SVCB")
                }
                add("query_type", qTypes)
                addProperty("action", "predefined")
                addProperty("rcode", "NOERROR")
            })
        }

        // 2. In FakeIP mode, divert all non-IP queries (anything that is NOT A and NOT AAAA, such as PTR, TXT, SRV)
        // to dns_local so that fakeip server never receives unsupported non-IP queries.
        if (isFakeIpMode) {
            rules.add(JsonObject().apply {
                val ipTypes = JsonArray().apply {
                    add("A")
                    add("AAAA")
                }
                add("query_type", ipTypes)
                addProperty("invert", true)
                addProperty("server", "dns_local")
            })
        }

        val remoteDnsTag = if (hasNodes && targetProxy != "direct") {
            if (isFakeIpMode) "dns_fakeip" else "dns_remote"
        } else {
            "dns_local"
        }

        // 1. Custom App Groups DNS rules
        val enabledCustomGroups = routeConfig.customGroups.filter { it.isEnabled && it.packageNames.isNotEmpty() }
        for (group in enabledCustomGroups) {
            val groupTarget = resolveOutbound(group.outboundTag, proxyNodeTags, hasNodes, targetProxy)
            val dnsServer = getDnsServerForTarget(groupTarget, isFakeIpMode, hasNodes)
            val pkgArr = JsonArray()
            group.packageNames.forEach { pkgArr.add(it) }
            rules.add(JsonObject().apply {
                add("package_name", pkgArr)
                addProperty("server", dnsServer)
            })
        }

        // 1.1 Custom Domain Groups DNS rules
        val enabledCustomDomainGroups = routeConfig.customDomainGroups.filter { it.isEnabled && it.domainSuffixes.isNotEmpty() }
        for (group in enabledCustomDomainGroups) {
            val groupTarget = resolveOutbound(group.outboundTag, proxyNodeTags, hasNodes, targetProxy)
            val dnsServer = getDnsServerForTarget(groupTarget, isFakeIpMode, hasNodes)
            val domainArr = JsonArray()
            group.domainSuffixes.forEach { domainArr.add(it) }
            rules.add(JsonObject().apply {
                add("domain_suffix", domainArr)
                addProperty("server", dnsServer)
            })
        }

        // 2. Google Preset DNS rules
        if (routeConfig.isRouteGoogle) {
            val googleTarget = resolveOutbound(routeConfig.googleOutbound, proxyNodeTags, hasNodes, targetProxy)
            val googleDns = getDnsServerForTarget(googleTarget, isFakeIpMode, hasNodes)
            rules.add(JsonObject().apply {
                add("package_name", AppRulePresets.google.apps.toPackagesJsonArray())
                addProperty("server", googleDns)
            })
            rules.add(JsonObject().apply {
                add("domain_suffix", AppRulePresets.google.domainSuffixes.toJsonArray())
                addProperty("server", googleDns)
            })
            AppRulePresets.google.ruleSets.forEach { rs ->
                rules.add(JsonObject().apply {
                    addProperty("rule_set", rs)
                    addProperty("server", googleDns)
                })
            }
        }

        // 3. Social Preset DNS rules
        if (routeConfig.isRouteSocial) {
            val socialTarget = resolveOutbound(routeConfig.socialOutbound, proxyNodeTags, hasNodes, targetProxy)
            val socialDns = getDnsServerForTarget(socialTarget, isFakeIpMode, hasNodes)
            rules.add(JsonObject().apply {
                add("package_name", AppRulePresets.social.apps.toPackagesJsonArray())
                addProperty("server", socialDns)
            })
            rules.add(JsonObject().apply {
                add("domain_suffix", AppRulePresets.social.domainSuffixes.toJsonArray())
                addProperty("server", socialDns)
            })
            AppRulePresets.social.ruleSets.forEach { rs ->
                rules.add(JsonObject().apply {
                    addProperty("rule_set", rs)
                    addProperty("server", socialDns)
                })
            }
        }

        // 4. AI Preset DNS rules
        if (routeConfig.isRouteAi) {
            val aiTarget = resolveOutbound(routeConfig.aiOutbound, proxyNodeTags, hasNodes, targetProxy)
            val aiDns = getDnsServerForTarget(aiTarget, isFakeIpMode, hasNodes)
            rules.add(JsonObject().apply {
                add("package_name", AppRulePresets.ai.apps.toPackagesJsonArray())
                addProperty("server", aiDns)
            })
            rules.add(JsonObject().apply {
                add("domain_suffix", AppRulePresets.ai.domainSuffixes.toJsonArray())
                addProperty("server", aiDns)
            })
            AppRulePresets.ai.ruleSets.forEach { rs ->
                rules.add(JsonObject().apply {
                    addProperty("rule_set", rs)
                    addProperty("server", aiDns)
                })
            }
        }

        when (routeConfig.mode) {
            "smart" -> {
                rules.add(JsonObject().apply {
                    addProperty("rule_set", "geosite-cn")
                    addProperty("server", "dns_local")
                })
                rules.add(JsonObject().apply {
                    addProperty("rule_set", "geosite-geolocation-!cn")
                    addProperty("server", remoteDnsTag)
                })
            }
            "gfw" -> {
                rules.add(JsonObject().apply {
                    addProperty("rule_set", "geosite-geolocation-!cn")
                    addProperty("server", remoteDnsTag)
                })
            }
            "global" -> {
                rules.add(JsonObject().apply {
                    addProperty("server", remoteDnsTag)
                })
            }
            "direct" -> {
                rules.add(JsonObject().apply {
                    addProperty("server", "dns_local")
                })
            }
        }

        val finalDns = if (routeConfig.mode == "global" && hasNodes && targetProxy != "direct" && !isFakeIpMode) remoteDnsTag else "dns_local"

        return JsonObject().apply {
            add("servers", servers)
            add("rules", rules)
            addProperty("final", finalDns)
            addProperty("strategy", "ipv4_only")
        }
    }

    private fun buildDnsServer(tag: String, addressStr: String, detour: String?): JsonObject {
        val s = addressStr.trim()
        if (s.equals("fakeip", ignoreCase = true)) {
            return JsonObject().apply {
                addProperty("tag", tag)
                addProperty("type", "fakeip")
                addProperty("inet4_range", "198.18.0.0/15")
            }
        }

        val serverObj = JsonObject()
        serverObj.addProperty("tag", tag)

        when {
            s.startsWith("https://", ignoreCase = true) || s.startsWith("h3://", ignoreCase = true) -> {
                serverObj.addProperty("type", "https")
                serverObj.addProperty("server", s)
            }
            s.startsWith("tls://", ignoreCase = true) -> {
                serverObj.addProperty("type", "tls")
                val clean = s.substring(6)
                if (clean.contains(":")) {
                    val parts = clean.split(":")
                    serverObj.addProperty("server", parts[0])
                    parts[1].toIntOrNull()?.let { serverObj.addProperty("server_port", it) }
                } else {
                    serverObj.addProperty("server", clean)
                }
            }
            s.startsWith("tcp://", ignoreCase = true) -> {
                serverObj.addProperty("type", "tcp")
                val clean = s.substring(6)
                if (clean.contains(":")) {
                    val parts = clean.split(":")
                    serverObj.addProperty("server", parts[0])
                    parts[1].toIntOrNull()?.let { serverObj.addProperty("server_port", it) }
                } else {
                    serverObj.addProperty("server", clean)
                }
            }
            else -> {
                serverObj.addProperty("type", "udp")
                val clean = if (s.startsWith("udp://", ignoreCase = true)) s.substring(6) else s
                if (clean.contains(":")) {
                    val parts = clean.split(":")
                    serverObj.addProperty("server", parts[0])
                    parts[1].toIntOrNull()?.let { serverObj.addProperty("server_port", it) }
                } else {
                    serverObj.addProperty("server", clean)
                }
            }
        }

        if (detour != null) {
            serverObj.addProperty("detour", detour)
        }

        return serverObj
    }

    private fun buildInboundsSection(inboundConfig: SimpleInboundConfig): JsonArray {
        val inbounds = JsonArray()

        when (inboundConfig.inbound_type) {
            "tun" -> {
                val tunObj = JsonObject().apply {
                    addProperty("type", "tun")
                    addProperty("tag", "tun-in")
                    val addrArr = JsonArray()
                    addrArr.add("172.19.0.1/30")
                    add("address", addrArr)
                    addProperty("auto_route", inboundConfig.tun_auto_route)
                    addProperty("stack", inboundConfig.tun_stack)
                }
                inbounds.add(tunObj)
            }
            "mixed" -> {
                val mixedObj = JsonObject().apply {
                    addProperty("type", "mixed")
                    addProperty("tag", "mixed-in")
                    addProperty("listen", if (inboundConfig.allow_lan) "0.0.0.0" else "127.0.0.1")
                    addProperty("listen_port", inboundConfig.mixed_port)
                }
                inbounds.add(mixedObj)
            }
            else -> {
                val tunObj = JsonObject().apply {
                    addProperty("type", "tun")
                    addProperty("tag", "tun-in")
                    val addrArr = JsonArray()
                    addrArr.add("172.19.0.1/30")
                    add("address", addrArr)
                    addProperty("auto_route", true)
                    addProperty("stack", "mixed")
                }
                inbounds.add(tunObj)
            }
        }

        return inbounds
    }

    private fun buildOutboundsSection(
        routeConfig: SimpleRouteConfig,
        proxyNodeTags: List<String>,
        nodeOutbounds: JsonArray,
        hasNodes: Boolean
    ): JsonArray {
        val outbounds = JsonArray()

        // 1. direct
        outbounds.add(JsonObject().apply {
            addProperty("type", "direct")
            addProperty("tag", "direct")
        })

        // 2. block
        outbounds.add(JsonObject().apply {
            addProperty("type", "block")
            addProperty("tag", "block")
        })

        // 3. selector proxy
        val selectorOutbounds = JsonArray()
        if (hasNodes) {
            selectorOutbounds.add("AUTO-Test")
        }
        selectorOutbounds.add("direct")
        if (hasNodes) {
            for (tag in proxyNodeTags) {
                selectorOutbounds.add(tag)
            }
        }

        val selectorObj = JsonObject().apply {
            addProperty("type", "selector")
            addProperty("tag", "proxy")
            add("outbounds", selectorOutbounds)
            if (routeConfig.default_outbound == "direct") {
                addProperty("default", "direct")
            } else if (proxyNodeTags.contains(routeConfig.default_outbound)) {
                addProperty("default", routeConfig.default_outbound)
            } else if (hasNodes && routeConfig.default_outbound == "AUTO-Test") {
                addProperty("default", "AUTO-Test")
            }
        }
        outbounds.add(selectorObj)

        // 4. urltest AUTO-Test
        if (hasNodes) {
            val urltestOutbounds = JsonArray()
            for (tag in proxyNodeTags) {
                urltestOutbounds.add(tag)
            }

            outbounds.add(JsonObject().apply {
                addProperty("type", "urltest")
                addProperty("tag", "AUTO-Test")
                addProperty("url", "http://cp.cloudflare.com/generate_204")
                addProperty("interval", "3m")
                addProperty("tolerance", 50)
                add("outbounds", urltestOutbounds)
            })
        }

        // 5. individual node outbounds
        for (nodeVal in nodeOutbounds) {
            outbounds.add(nodeVal)
        }

        return outbounds
    }

    private fun buildRouteSection(
        routeConfig: SimpleRouteConfig,
        proxyNodeTags: List<String>,
        hasNodes: Boolean,
        targetProxy: String
    ): JsonObject {
        val rules = JsonArray()

        rules.add(JsonObject().apply {
            addProperty("action", "sniff")
        })
        rules.add(JsonObject().apply {
            addProperty("protocol", "dns")
            addProperty("action", "hijack-dns")
        })
        rules.add(JsonObject().apply {
            addProperty("port", 53)
            addProperty("action", "hijack-dns")
        })

        // Helper function to add a route rule with either target outbound or action reject
        fun addRouteRule(target: String, populate: (JsonObject) -> Unit) {
            val rule = JsonObject()
            populate(rule)
            if (target == "block") {
                rule.addProperty("action", "reject")
            } else {
                rule.addProperty("outbound", target)
            }
            rules.add(rule)
        }

        // Block QUIC (UDP 443) traffic so that Android apps (especially Google Play & YouTube)
        // immediately fall back to TCP HTTP/2 instead of hanging on UDP timeouts or unsupported UDP proxies.
        // Using action reject instead of outbound block terminates packets cleanly at the router layer
        // and avoids "listen packet connection using outbound/block: operation not permitted" error.
        if (routeConfig.isBlockQuic) {
            rules.add(JsonObject().apply {
                addProperty("port", 443)
                addProperty("network", "udp")
                addProperty("action", "reject")
            })
        }

        if (routeConfig.block_ads) {
            rules.add(JsonObject().apply {
                addProperty("rule_set", "geosite-category-ads-all")
                addProperty("action", "reject")
            })
        }

        if (routeConfig.bypass_lan) {
            rules.add(JsonObject().apply {
                addProperty("ip_is_private", true)
                addProperty("outbound", "direct")
            })
        }

        // 1. Custom App Groups routing (Strict app-level rules placed before general rules)
        val enabledCustomGroups = routeConfig.customGroups.filter { it.isEnabled && it.packageNames.isNotEmpty() }
        for (group in enabledCustomGroups) {
            val groupTarget = resolveOutbound(group.outboundTag, proxyNodeTags, hasNodes, targetProxy)
            val pkgArr = JsonArray()
            group.packageNames.forEach { pkgArr.add(it) }
            addRouteRule(groupTarget) {
                it.add("package_name", pkgArr)
            }
        }

        // 1.1 Custom Domain Groups routing
        val enabledCustomDomainGroups = routeConfig.customDomainGroups.filter { it.isEnabled && it.domainSuffixes.isNotEmpty() }
        for (group in enabledCustomDomainGroups) {
            val groupTarget = resolveOutbound(group.outboundTag, proxyNodeTags, hasNodes, targetProxy)
            val domainArr = JsonArray()
            group.domainSuffixes.forEach { domainArr.add(it) }
            addRouteRule(groupTarget) {
                it.add("domain_suffix", domainArr)
            }
        }

        // 2. Google Preset routing
        if (routeConfig.isRouteGoogle) {
            val googleTarget = resolveOutbound(routeConfig.googleOutbound, proxyNodeTags, hasNodes, targetProxy)
            addRouteRule(googleTarget) {
                it.add("package_name", AppRulePresets.google.apps.toPackagesJsonArray())
            }
            addRouteRule(googleTarget) {
                it.add("domain_suffix", AppRulePresets.google.domainSuffixes.toJsonArray())
            }
            AppRulePresets.google.ruleSets.forEach { rs ->
                addRouteRule(googleTarget) {
                    it.addProperty("rule_set", rs)
                }
            }
        }

        // 3. Social Preset routing
        if (routeConfig.isRouteSocial) {
            val socialTarget = resolveOutbound(routeConfig.socialOutbound, proxyNodeTags, hasNodes, targetProxy)
            addRouteRule(socialTarget) {
                it.add("package_name", AppRulePresets.social.apps.toPackagesJsonArray())
            }
            addRouteRule(socialTarget) {
                it.add("domain_suffix", AppRulePresets.social.domainSuffixes.toJsonArray())
            }
            AppRulePresets.social.ruleSets.forEach { rs ->
                addRouteRule(socialTarget) {
                    it.addProperty("rule_set", rs)
                }
            }
        }

        // 4. AI Preset routing
        if (routeConfig.isRouteAi) {
            val aiTarget = resolveOutbound(routeConfig.aiOutbound, proxyNodeTags, hasNodes, targetProxy)
            addRouteRule(aiTarget) {
                it.add("package_name", AppRulePresets.ai.apps.toPackagesJsonArray())
            }
            addRouteRule(aiTarget) {
                it.add("domain_suffix", AppRulePresets.ai.domainSuffixes.toJsonArray())
            }
            AppRulePresets.ai.ruleSets.forEach { rs ->
                addRouteRule(aiTarget) {
                    it.addProperty("rule_set", rs)
                }
            }
        }

        // 5. Mode-specific routing
        when (routeConfig.mode) {
            "smart" -> {
                rules.add(JsonObject().apply {
                    addProperty("rule_set", "geosite-cn")
                    addProperty("outbound", "direct")
                })
                rules.add(JsonObject().apply {
                    addProperty("rule_set", "geoip-cn")
                    addProperty("outbound", "direct")
                })
                rules.add(JsonObject().apply {
                    addProperty("rule_set", "geosite-geolocation-!cn")
                    addProperty("outbound", targetProxy)
                })
                rules.add(JsonObject().apply {
                    addProperty("outbound", targetProxy)
                })
            }
            "gfw" -> {
                rules.add(JsonObject().apply {
                    addProperty("rule_set", "geosite-geolocation-!cn")
                    addProperty("outbound", targetProxy)
                })
                rules.add(JsonObject().apply {
                    addProperty("outbound", "direct")
                })
            }
            "direct" -> {
                rules.add(JsonObject().apply {
                    addProperty("outbound", "direct")
                })
            }
            else -> {
                rules.add(JsonObject().apply {
                    addProperty("outbound", targetProxy)
                })
            }
        }

        val downloadClientRemote = if (hasNodes && targetProxy != "direct") "proxy" else "direct"
        val neededRuleSets = LinkedHashSet<String>()

        if (routeConfig.block_ads) {
            neededRuleSets.add("geosite-category-ads-all")
        }
        if (routeConfig.isRouteGoogle) {
            neededRuleSets.addAll(AppRulePresets.google.ruleSets)
        }
        if (routeConfig.isRouteSocial) {
            neededRuleSets.addAll(AppRulePresets.social.ruleSets)
        }
        if (routeConfig.isRouteAi) {
            neededRuleSets.addAll(AppRulePresets.ai.ruleSets)
        }
        if (routeConfig.mode == "smart") {
            neededRuleSets.add("geosite-cn")
            neededRuleSets.add("geosite-geolocation-!cn")
            neededRuleSets.add("geoip-cn")
        } else if (routeConfig.mode == "gfw") {
            neededRuleSets.add("geosite-geolocation-!cn")
        }

        val ruleSets = JsonArray()
        for (tag in neededRuleSets) {
            val url = if (tag == "geoip-cn") {
                "https://cdn.jsdelivr.net/gh/SagerNet/sing-geoip@rule-set/geoip-cn.srs"
            } else {
                "https://cdn.jsdelivr.net/gh/SagerNet/sing-geosite@rule-set/$tag.srs"
            }
            val client = if (tag == "geosite-cn" || tag == "geoip-cn") "direct" else downloadClientRemote
            ruleSets.add(JsonObject().apply {
                addProperty("tag", tag)
                addProperty("type", "remote")
                addProperty("format", "binary")
                addProperty("url", url)
                addProperty("http_client", client)
                addProperty("update_interval", "1d")
            })
        }

        val finalOutbound = when (routeConfig.mode) {
            "direct", "gfw" -> "direct"
            else -> targetProxy
        }

        return JsonObject().apply {
            addProperty("auto_detect_interface", true)
            addProperty("default_domain_resolver", "dns_local")
            addProperty("default_http_client", "direct")
            add("rules", rules)
            add("rule_set", ruleSets)
            addProperty("final", finalOutbound)
        }
    }

    private fun resolveOutbound(
        customOutbound: String,
        proxyNodeTags: List<String>,
        hasNodes: Boolean,
        targetProxy: String
    ): String {
        return when {
            customOutbound.isNotBlank() && (proxyNodeTags.contains(customOutbound) || listOf("proxy", "AUTO-Test", "direct", "block").contains(customOutbound)) -> customOutbound
            hasNodes -> targetProxy
            else -> "direct"
        }
    }

    private fun getDnsServerForTarget(
        target: String,
        isFakeIpMode: Boolean,
        hasNodes: Boolean
    ): String {
        return when {
            target == "direct" || target == "block" || !hasNodes -> "dns_local"
            isFakeIpMode -> "dns_fakeip"
            else -> "dns_remote"
        }
    }

    private fun buildHttpClientsSection(hasNodes: Boolean, targetProxy: String): JsonArray {
        return JsonArray().apply {
            // direct client: connects directly (no detour needed)
            add(JsonObject().apply {
                addProperty("tag", "direct")
            })
            if (hasNodes && targetProxy != "direct") {
                // proxy client: connects via proxy selector
                add(JsonObject().apply {
                    addProperty("tag", "proxy")
                    addProperty("detour", "proxy")
                })
            }
        }
    }

    private fun sanitizeOutboundValue(outbound: JsonObject) {
        val type = outbound.get("type")?.asString ?: return
        val tlsSupported = type in listOf(
            "http", "vmess", "vless", "trojan", "anytls",
            "hysteria", "hysteria2", "shadowtls", "tuic", "v2ray"
        )
        if (!tlsSupported) {
            outbound.remove("tls")
        }
    }
}
