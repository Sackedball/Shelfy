package com.kirana.fefoguard.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.kirana.fefoguard.data.local.entity.CheckoutEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CheckoutDao {
    @Insert
    suspend fun insert(checkout: CheckoutEntity): Long

    @Query("SELECT * FROM checkouts WHERE id = :id")
    suspend fun get(id: String): CheckoutEntity?

    @Query("UPDATE checkouts SET voidedAt = :at, voidReason = :reason WHERE id = :id AND voidedAt IS NULL")
    suspend fun markVoid(
        id: String,
        at: Long,
        reason: String,
    ): Int

    @Query("SELECT * FROM checkouts ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<CheckoutEntity>>

    @Query("SELECT * FROM checkouts WHERE voidedAt IS NULL AND linesJson != '[]' ORDER BY createdAt DESC LIMIT 1")
    suspend fun latestBill(): CheckoutEntity?

    @Query("SELECT * FROM checkouts WHERE createdAt >= :sinceMillis ORDER BY createdAt DESC")
    suspend fun since(sinceMillis: Long): List<CheckoutEntity>
}
