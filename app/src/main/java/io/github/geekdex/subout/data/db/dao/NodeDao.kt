package io.github.geekdex.subout.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.github.geekdex.subout.data.db.entities.Node
import kotlinx.coroutines.flow.Flow

@Dao
interface NodeDao {
    @Query("SELECT * FROM nodes ORDER BY id ASC")
    fun getAll(): Flow<List<Node>>

    @Query("SELECT * FROM nodes ORDER BY id ASC")
    suspend fun getAllSync(): List<Node>

    @Query("SELECT * FROM nodes WHERE enabled = 1 ORDER BY id ASC")
    suspend fun getEnabledNodesSync(): List<Node>

    @Query("SELECT * FROM nodes WHERE subscriptionId = :subId ORDER BY id ASC")
    fun getBySubscriptionId(subId: Long): Flow<List<Node>>

    @Query("SELECT * FROM nodes WHERE subscriptionId = :subId ORDER BY id ASC")
    suspend fun getBySubscriptionIdSync(subId: Long): List<Node>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(node: Node): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(nodes: List<Node>)

    @Update
    suspend fun update(node: Node)

    @Delete
    suspend fun delete(node: Node)

    @Query("DELETE FROM nodes WHERE subscriptionId = :subId")
    suspend fun deleteBySubscriptionId(subId: Long)

    @Query("UPDATE nodes SET latency = :latency, testTime = :testTime WHERE id = :id")
    suspend fun updateLatency(id: Long, latency: Int?, testTime: Long)

    @Query("UPDATE nodes SET enabled = :enabled WHERE id = :id")
    suspend fun updateEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE nodes SET enabled = :enabled")
    suspend fun enableAll(enabled: Boolean)

    @Query("UPDATE nodes SET latency = NULL, testTime = NULL")
    suspend fun clearAllLatencies()

    @Query("DELETE FROM nodes WHERE latency = :timeoutLatency")
    suspend fun deleteTimeoutNodes(timeoutLatency: Int = Node.LATENCY_TIMEOUT): Int

    @Query("DELETE FROM nodes WHERE id IN (:ids)")
    suspend fun deleteNodesByIds(ids: List<Long>): Int

    @Query("DELETE FROM nodes WHERE enabled = 0")
    suspend fun deleteDisabledNodes(): Int

    @Query("UPDATE nodes SET enabled = :enabled WHERE id IN (:ids)")
    suspend fun updateNodesEnabled(ids: List<Long>, enabled: Boolean): Int
}
