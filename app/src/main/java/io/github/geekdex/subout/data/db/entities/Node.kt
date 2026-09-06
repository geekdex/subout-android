package io.github.geekdex.subout.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "nodes",
    indices = [
        Index("subscriptionId"),
        Index("tag"),
        Index("protocol"),
        Index("enabled")
    ]
)
data class Node(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val subscriptionId: Long?,
    val tag: String,
    val protocol: String, // vmess, vless, ss, trojan, hysteria, hysteria2, socks5, http
    val server: String,
    val serverPort: Int,
    val rawJson: String,
    val enabled: Boolean = true,
    val latency: Int? = null, // ms, null means untested or timeout
    val testTime: Long? = null
)
