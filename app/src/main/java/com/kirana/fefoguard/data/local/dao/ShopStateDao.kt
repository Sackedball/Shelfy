package com.kirana.fefoguard.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kirana.fefoguard.data.local.entity.CashMovementEntity
import com.kirana.fefoguard.data.local.entity.CashSessionEntity
import com.kirana.fefoguard.data.local.entity.DraftBillEntity
import com.kirana.fefoguard.data.local.entity.ShopOperationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShopStateDao {
    @Query("SELECT * FROM draft_bills ORDER BY updatedAt DESC")
    fun drafts(): Flow<List<DraftBillEntity>>

    @Query("SELECT * FROM draft_bills ORDER BY updatedAt DESC")
    suspend fun allDrafts(): List<DraftBillEntity>

    @Query("SELECT * FROM draft_bills WHERE id = :id")
    suspend fun draft(id: String): DraftBillEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveDraft(value: DraftBillEntity): Long

    @Query("DELETE FROM draft_bills WHERE id = :id")
    suspend fun deleteDraft(id: String): Int

    @Query("SELECT * FROM shop_operations WHERE id = :id")
    suspend fun operation(id: String): ShopOperationEntity?

    @Query("SELECT * FROM shop_operations")
    fun observeOperations(): Flow<List<ShopOperationEntity>>

    @Query("SELECT * FROM shop_operations")
    suspend fun operations(): List<ShopOperationEntity>

    @Query("SELECT * FROM shop_operations WHERE kind = 'REFUND' AND createdAt >= :sinceMillis")
    suspend fun refundsSince(sinceMillis: Long): List<ShopOperationEntity>

    @Insert suspend fun insertOperation(value: ShopOperationEntity): Long

    @Query("SELECT * FROM cash_sessions ORDER BY openedAt DESC")
    fun sessions(): Flow<List<CashSessionEntity>>

    @Query("SELECT * FROM cash_sessions ORDER BY openedAt DESC")
    suspend fun allSessions(): List<CashSessionEntity>

    @Query("SELECT * FROM cash_sessions WHERE closedAt IS NULL LIMIT 1")
    suspend fun openSession(): CashSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSession(value: CashSessionEntity): Long

    @Query("SELECT * FROM cash_movements ORDER BY createdAt DESC")
    fun movements(): Flow<List<CashMovementEntity>>

    @Query("SELECT * FROM cash_movements")
    suspend fun allMovements(): List<CashMovementEntity>

    @Insert suspend fun insertMovement(value: CashMovementEntity): Long
}
