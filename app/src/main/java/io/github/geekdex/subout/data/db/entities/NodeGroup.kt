package io.github.geekdex.subout.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@Entity(tableName = "node_groups")
data class NodeGroup(
    @PrimaryKey
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val testUrl: String = "https://www.gstatic.com/generate_204",
    val testUrlName: String = "Google Gstatic",
    val nodeTagsJson: String = "[]",
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
) {
    val nodeTags: List<String>
        get() = try {
            val type = object : TypeToken<List<String>>() {}.type
            Gson().fromJson(nodeTagsJson, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }

    fun copyWithNodeTags(tags: List<String>): NodeGroup {
        return copy(nodeTagsJson = Gson().toJson(tags))
    }
}
