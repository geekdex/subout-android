package io.github.geekdex.subout.domain.model

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import io.github.geekdex.subout.data.db.entities.Node

enum class ManualNodeType(val typeName: String, val displayName: String) {
    VLESS("vless", "VLESS"),
    VMESS("vmess", "VMess"),
    TROJAN("trojan", "Trojan"),
    SHADOWSOCKS("shadowsocks", "Shadowsocks"),
    HYSTERIA2("hysteria2", "Hysteria 2")
}

sealed interface ManualNodeConfig {
    val type: ManualNodeType
    val tag: String
    val server: String
    val serverPort: Int

    fun toJson(): JsonObject

    fun toNode(): Node {
        val gson = GsonBuilder().setPrettyPrinting().create()
        return Node(
            id = 0,
            subscriptionId = null,
            tag = tag,
            protocol = type.typeName,
            server = server,
            serverPort = serverPort,
            rawJson = gson.toJson(toJson()),
            enabled = true,
            latency = null,
            testTime = null
        )
    }
}

/**
 * 手动添加 VLESS 节点配置
 */
data class ManualVlessConfig(
    override val tag: String,
    override val server: String,
    override val serverPort: Int = 443,
    val uuid: String,
    val flow: String = "xtls-rprx-vision",
    val tlsEnabled: Boolean = true,
    val serverName: String = "",
    val utlsEnabled: Boolean = true,
    val utlsFingerprint: String = "chrome",
    val realityEnabled: Boolean = true,
    val realityPublicKey: String = "",
    val realityShortId: String = ""
) : ManualNodeConfig {
    override val type: ManualNodeType get() = ManualNodeType.VLESS

    override fun toJson(): JsonObject {
        val root = JsonObject()
        root.addProperty("type", "vless")
        root.addProperty("tag", tag)
        root.addProperty("server", server)
        root.addProperty("server_port", serverPort)
        root.addProperty("uuid", uuid)
        if (flow.isNotBlank()) {
            root.addProperty("flow", flow)
        }

        if (tlsEnabled) {
            val tls = JsonObject()
            tls.addProperty("enabled", true)
            if (serverName.isNotBlank()) {
                tls.addProperty("server_name", serverName)
            }
            if (utlsEnabled) {
                val utls = JsonObject()
                utls.addProperty("enabled", true)
                utls.addProperty("fingerprint", utlsFingerprint.ifBlank { "chrome" })
                tls.add("utls", utls)
            }
            if (realityEnabled) {
                val reality = JsonObject()
                reality.addProperty("enabled", true)
                reality.addProperty("public_key", realityPublicKey)
                if (realityShortId.isNotBlank()) {
                    reality.addProperty("short_id", realityShortId)
                }
                tls.add("reality", reality)
            }
            root.add("tls", tls)
        }

        return root
    }
}

// 预留后续协议扩展支持，业务架构完备
data class ManualVmessConfig(
    override val tag: String,
    override val server: String,
    override val serverPort: Int = 443,
    val uuid: String,
    val alterId: Int = 0,
    val security: String = "auto",
    val tlsEnabled: Boolean = false,
    val serverName: String = ""
) : ManualNodeConfig {
    override val type: ManualNodeType get() = ManualNodeType.VMESS

    override fun toJson(): JsonObject {
        val root = JsonObject()
        root.addProperty("type", "vmess")
        root.addProperty("tag", tag)
        root.addProperty("server", server)
        root.addProperty("server_port", serverPort)
        root.addProperty("uuid", uuid)
        root.addProperty("alter_id", alterId)
        root.addProperty("security", security)
        if (tlsEnabled) {
            val tls = JsonObject()
            tls.addProperty("enabled", true)
            if (serverName.isNotBlank()) tls.addProperty("server_name", serverName)
            root.add("tls", tls)
        }
        return root
    }
}

data class ManualTrojanConfig(
    override val tag: String,
    override val server: String,
    override val serverPort: Int = 443,
    val password: String,
    val tlsEnabled: Boolean = true,
    val serverName: String = ""
) : ManualNodeConfig {
    override val type: ManualNodeType get() = ManualNodeType.TROJAN

    override fun toJson(): JsonObject {
        val root = JsonObject()
        root.addProperty("type", "trojan")
        root.addProperty("tag", tag)
        root.addProperty("server", server)
        root.addProperty("server_port", serverPort)
        root.addProperty("password", password)
        if (tlsEnabled) {
            val tls = JsonObject()
            tls.addProperty("enabled", true)
            if (serverName.isNotBlank()) tls.addProperty("server_name", serverName)
            root.add("tls", tls)
        }
        return root
    }
}

data class ManualShadowsocksConfig(
    override val tag: String,
    override val server: String,
    override val serverPort: Int = 8388,
    val method: String = "2022-blake3-aes-128-gcm",
    val password: String
) : ManualNodeConfig {
    override val type: ManualNodeType get() = ManualNodeType.SHADOWSOCKS

    override fun toJson(): JsonObject {
        val root = JsonObject()
        root.addProperty("type", "shadowsocks")
        root.addProperty("tag", tag)
        root.addProperty("server", server)
        root.addProperty("server_port", serverPort)
        root.addProperty("method", method)
        root.addProperty("password", password)
        return root
    }
}

data class ManualHysteria2Config(
    override val tag: String,
    override val server: String,
    override val serverPort: Int = 443,
    val password: String,
    val serverName: String = "",
    val insecure: Boolean = false
) : ManualNodeConfig {
    override val type: ManualNodeType get() = ManualNodeType.HYSTERIA2

    override fun toJson(): JsonObject {
        val root = JsonObject()
        root.addProperty("type", "hysteria2")
        root.addProperty("tag", tag)
        root.addProperty("server", server)
        root.addProperty("server_port", serverPort)
        root.addProperty("password", password)
        val tls = JsonObject()
        tls.addProperty("enabled", true)
        if (serverName.isNotBlank()) tls.addProperty("server_name", serverName)
        if (insecure) tls.addProperty("insecure", true)
        root.add("tls", tls)
        return root
    }
}
