package com.kirana.fefoguard.domain.repository

import com.kirana.fefoguard.data.local.entity.AdjustmentType
import com.kirana.fefoguard.data.local.entity.StockAdjustmentEntity
import kotlinx.coroutines.flow.Flow

interface AdjustmentRepository {
    // Note: Must handle quantity adjustment + insert log
    suspend fun recordAdjustment(
        productId: Long,
        batchId: Long?,
        type: AdjustmentType,
        quantity: Int,
        notes: String?,
    )

    /**
     * Writes every expired lot still counted as stock off as spoiled, in one transaction, and returns how many
     * lots it cleared. Calling it again finds nothing left to do.
     */
    suspend fun writeOffExpired(): Int

    fun getRecentAdjustments(limit: Int = 50): Flow<List<StockAdjustmentEntity>>
}
