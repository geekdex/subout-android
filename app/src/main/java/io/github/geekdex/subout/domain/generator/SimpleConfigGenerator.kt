package io.github.geekdex.subout.domain.generator

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.github.geekdex.subout.data.db.entities.Node
import io.github.geekdex.subout.domain.model.SimpleConfig
import io.github.geekdex.subout.domain.model.SimpleDnsConfig
import io.github.geekdex.subout.domain.model.SimpleInboundConfig
import io.github.geekdex.subout.domain.model.SimpleLogConfig
import io.github.geekdex.subout.domain.model.SimpleRouteConfig

object SimpleConfigGenerator {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

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
        root.add("dns", buildDnsSection(config.dns, config.route, hasNodes, targetProxy))

        // 3. Inbounds
        root.add("inbounds", buildInboundsSection(config.inbound))

        // 4. Outbounds
        root.add("outbounds", buildOutboundsSection(config.route, proxyNodeTags, nodeOutbounds, hasNodes))

        // 5. Route
        root.add("route", buildRouteSection(config.route, hasNodes, targetProxy))

        // 6. Experimental
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
        targetProxy: String
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

        val remoteDnsTag = if (hasNodes && targetProxy != "direct") {
            if (isFakeIpMode) "dns_fakeip" else "dns_remote"
        } else {
            "dns_local"
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
        }

        return JsonObject().apply {
            add("servers", servers)
            add("rules", rules)
            addProperty("final", "dns_local")
            addProperty("strategy", "ipv4_only")
            addProperty("independent_cache", true)
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
        if (s == "local") {
            val obj = JsonObject().apply {
                addProperty("tag", tag)
                addProperty("type", "local")
            }
            applyDnsDetour(obj, detour)
            return obj
        }

        try {
            val uri = java.net.URI(s)
            val scheme = uri.scheme?.lowercase()
            val host = uri.host ?: s
            val port = if (uri.port != -1) uri.port else null
            val path = uri.path

            when (scheme) {
                "https", "http" -> {
                    val obj = JsonObject().apply {
                        addProperty("tag", tag)
                        addProperty("type", "https")
                        addProperty("server", host)
                        if (port != null) addProperty("server_port", port)
                        if (!path.isNullOrEmpty() && path != "/") addProperty("path", path)
                    }
                    applyDnsDetour(obj, detour)
                    return obj
                }
                "h3" -> {
                    val obj = JsonObject().apply {
                        addProperty("tag", tag)
                        addProperty("type", "h3")
                        addProperty("server", host)
                        if (port != null) addProperty("server_port", port)
                        if (!path.isNullOrEmpty() && path != "/") addProperty("path", path)
                    }
                    applyDnsDetour(obj, detour)
                    return obj
                }
                "tls" -> {
                    val obj = JsonObject().apply {
                        addProperty("tag", tag)
                        addProperty("type", "tls")
                        addProperty("server", host)
                        if (port != null) addProperty("server_port", port)
                    }
                    applyDnsDetour(obj, detour)
                    return obj
                }
                "tcp" -> {
                    val obj = JsonObject().apply {
                        addProperty("tag", tag)
                        addProperty("type", "tcp")
                        addProperty("server", host)
                        if (port != null) addProperty("server_port", port)
                    }
                    applyDnsDetour(obj, detour)
                    return obj
                }
                "quic" -> {
                    val obj = JsonObject().apply {
                        addProperty("tag", tag)
                        addProperty("type", "quic")
                        addProperty("server", host)
                        if (port != null) addProperty("server_port", port)
                    }
                    applyDnsDetour(obj, detour)
                    return obj
                }
                "udp" -> {
                    val obj = JsonObject().apply {
                        addProperty("tag", tag)
                        addProperty("type", "udp")
                        addProperty("server", host)
                        if (port != null) addProperty("server_port", port)
                    }
                    applyDnsDetour(obj, detour)
                    return obj
                }
            }
        } catch (_: Exception) {
        }

        // Fallback: standard IP or host:port
        val (host, port) = if (s.contains(':') && !s.startsWith("[")) {
            val parts = s.split(':', limit = 2)
            Pair(parts[0], parts[1].toIntOrNull())
        } else {
            Pair(s, null)
        }

        val obj = JsonObject().apply {
            addProperty("tag", tag)
            addProperty("type", "udp")
            addProperty("server", host)
            if (port != null) addProperty("server_port", port)
        }
        applyDnsDetour(obj, detour)
        return obj
    }

    private fun applyDnsDetour(obj: JsonObject, detour: String?) {
        if (!detour.isNullOrBlank() && detour != "direct") {
            obj.addProperty("detour", detour.trim())
        }
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
                    addProperty("strict_route", true)
                    addProperty("stack", inboundConfig.tun_stack)
                }
                inbounds.add(tunObj)
            }
            else -> {
                val listenAddr = if (inboundConfig.allow_lan) "0.0.0.0" else "127.0.0.1"
                val mixedObj = JsonObject().apply {
                    addProperty("type", "mixed")
                    addProperty("tag", "mixed-in")
                    addProperty("listen", listenAddr)
                    addProperty("listen_port", inboundConfig.mixed_port)
                }
                inbounds.add(mixedObj)
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

        // 3. selector 'proxy'
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

        // 4. urltest 'AUTO-Test'
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

        if (routeConfig.block_ads) {
            rules.add(JsonObject().apply {
                addProperty("rule_set", "geosite-category-ads-all")
                addProperty("outbound", "block")
            })
        }

        if (routeConfig.bypass_lan) {
            rules.add(JsonObject().apply {
                addProperty("ip_is_private", true)
                addProperty("outbound", "direct")
            })
        }

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
            else -> {
                rules.add(JsonObject().apply {
                    addProperty("outbound", targetProxy)
                })
            }
        }

        val downloadDetourRemote = if (hasNodes && targetProxy != "direct") "proxy" else "direct"

        val ruleSets = JsonArray().apply {
            add(JsonObject().apply {
                addProperty("tag", "geosite-cn")
                addProperty("type", "remote")
                addProperty("format", "binary")
                addProperty("url", "https://cdn.jsdelivr.net/gh/SagerNet/sing-geosite@rule-set/geosite-cn.srs")
                addProperty("download_detour", "direct")
                addProperty("update_interval", "1d")
            })
            add(JsonObject().apply {
                addProperty("tag", "geosite-geolocation-!cn")
                addProperty("type", "remote")
                addProperty("format", "binary")
                addProperty("url", "https://cdn.jsdelivr.net/gh/SagerNet/sing-geosite@rule-set/geosite-geolocation-!cn.srs")
                addProperty("download_detour", downloadDetourRemote)
                addProperty("update_interval", "1d")
            })
            add(JsonObject().apply {
                addProperty("tag", "geoip-cn")
                addProperty("type", "remote")
                addProperty("format", "binary")
                addProperty("url", "https://cdn.jsdelivr.net/gh/SagerNet/sing-geoip@rule-set/geoip-cn.srs")
                addProperty("download_detour", "direct")
                addProperty("update_interval", "1d")
            })
            if (routeConfig.block_ads) {
                add(JsonObject().apply {
                    addProperty("tag", "geosite-category-ads-all")
                    addProperty("type", "remote")
                    addProperty("format", "binary")
                    addProperty("url", "https://cdn.jsdelivr.net/gh/SagerNet/sing-geosite@rule-set/geosite-category-ads-all.srs")
                    addProperty("download_detour", downloadDetourRemote)
                    addProperty("update_interval", "1d")
                })
            }
        }

        return JsonObject().apply {
            addProperty("auto_detect_interface", true)
            addProperty("default_domain_resolver", "dns_local")
            add("rules", rules)
            add("rule_set", ruleSets)
            addProperty("final", targetProxy)
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
