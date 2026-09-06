package io.github.geekdex.subout.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "subscriptions")
data class Subscription(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val url: String,
    val enabled: Boolean = true,
    val autoUpdate: Boolean = false,
    val updateInterval: Int = 60, // in minutes
    val lastFetchTime: Long? = null,
    val lastFetchStatus: String? = null,
    val nodeCount: Int = 0
)
