package io.github.geekdex.subout.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.github.geekdex.subout.data.db.entities.Subscription
import kotlinx.coroutines.flow.Flow

@Dao
interface SubscriptionDao {
    @Query("SELECT * FROM subscriptions ORDER BY id ASC")
    fun getAll(): Flow<List<Subscription>>

    @Query("SELECT * FROM subscriptions ORDER BY id ASC")
    suspend fun getAllSync(): List<Subscription>

    @Query("SELECT * FROM subscriptions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): Subscription?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(subscription: Subscription): Long

    @Update
    suspend fun update(subscription: Subscription)

    @Delete
    suspend fun delete(subscription: Subscription)

    @Query("DELETE FROM subscriptions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE subscriptions SET lastFetchStatus = :status, lastFetchTime = :fetchTime, nodeCount = :nodeCount WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, fetchTime: Long, nodeCount: Int)

    @Query("UPDATE subscriptions SET enabled = :enabled WHERE id = :id")
    suspend fun updateEnabled(id: Long, enabled: Boolean)
}
