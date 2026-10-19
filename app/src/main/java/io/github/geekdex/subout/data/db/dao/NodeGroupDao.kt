package io.github.geekdex.subout.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.github.geekdex.subout.data.db.entities.NodeGroup
import kotlinx.coroutines.flow.Flow

@Dao
interface NodeGroupDao {
    @Query("SELECT * FROM node_groups ORDER BY createdAt ASC")
    fun getAll(): Flow<List<NodeGroup>>

    @Query("SELECT * FROM node_groups ORDER BY createdAt ASC")
    suspend fun getAllSync(): List<NodeGroup>

    @Query("SELECT * FROM node_groups WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): NodeGroup?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(group: NodeGroup)

    @Update
    suspend fun update(group: NodeGroup)

    @Delete
    suspend fun delete(group: NodeGroup)

    @Query("DELETE FROM node_groups WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE node_groups SET enabled = :enabled WHERE id = :id")
    suspend fun updateEnabled(id: String, enabled: Boolean)
}
