package com.kirana.fefoguard.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kirana.fefoguard.data.local.entity.AdjustmentType
import com.kirana.fefoguard.data.local.entity.StockAdjustmentEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

@Dao
interface StockAdjustmentDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAdjustment(adjustment: StockAdjustmentEntity): Long

    @Query("SELECT * FROM stock_adjustments ORDER BY adjustedAt DESC LIMIT :limit")
    fun getRecentAdjustments(limit: Int): Flow<List<StockAdjustmentEntity>>

    @Query("SELECT * FROM stock_adjustments WHERE adjustedAt >= :since ORDER BY adjustedAt DESC")
    fun observeAdjustmentsSince(since: Instant): Flow<List<StockAdjustmentEntity>>

    @Query("SELECT * FROM stock_adjustments WHERE adjustedAt >= :since")
    suspend fun adjustmentsSince(since: Instant): List<StockAdjustmentEntity>

    @Query(
        "SELECT * FROM stock_adjustments WHERE adjustmentType IN (:types) AND adjustedAt BETWEEN :startDate AND :endDate ORDER BY adjustedAt ASC",
    )
    fun getAdjustmentsByTypeAndDateRange(
        types: List<AdjustmentType>,
        startDate: Instant,
        endDate: Instant,
    ): Flow<List<StockAdjustmentEntity>>

    @androidx.room.Transaction
    @Query(
        "SELECT * FROM stock_adjustments WHERE adjustmentType IN (:types) AND adjustedAt BETWEEN :startDate AND :endDate ORDER BY adjustedAt ASC",
    )
    fun getAdjustmentsByTypeAndDateRangeWithProduct(
        types: List<AdjustmentType>,
        startDate: Instant,
        endDate: Instant,
    ): Flow<List<com.kirana.fefoguard.data.local.entity.AdjustmentWithProduct>>
}
