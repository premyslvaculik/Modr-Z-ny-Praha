package com.example.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.UserSubscriptionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SubscriptionDao {
    @Query("SELECT * FROM user_subscriptions ORDER BY validToTimestamp ASC")
    fun getAllSubscriptions(): Flow<List<UserSubscriptionEntity>>

    @Query("SELECT * FROM user_subscriptions ORDER BY validToTimestamp ASC")
    suspend fun getAllSubscriptionsDirect(): List<UserSubscriptionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(subscription: UserSubscriptionEntity): Long

    @Update
    suspend fun update(subscription: UserSubscriptionEntity)

    @Delete
    suspend fun delete(subscription: UserSubscriptionEntity)

    @Query("DELETE FROM user_subscriptions WHERE id = :id")
    suspend fun deleteById(id: Long)
}
