package com.kirana.fefoguard.data.repository

import androidx.room.withTransaction
import com.kirana.fefoguard.data.local.FefoGuardDatabase
import com.kirana.fefoguard.data.local.entity.AdjustmentType
import com.kirana.fefoguard.data.local.entity.BatchStatus
import com.kirana.fefoguard.data.local.entity.InventoryBatchEntity
import com.kirana.fefoguard.data.local.entity.StockAdjustmentEntity
import com.kirana.fefoguard.domain.repository.AdjustmentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

class AdjustmentRepositoryImpl(
    private val db: FefoGuardDatabase,
) : AdjustmentRepository {
    override suspend fun recordAdjustment(
        productId: Long,
        batchId: Long?,
        type: AdjustmentType,
        quantity: Int,
        notes: String?,
    ) {
        require(quantity > 0) { "Quantity must be positive" }
        db.withTransaction { record(productId, batchId, type, quantity, notes) }
    }

    override suspend fun writeOffExpired(): Int =
        db.withTransaction {
            val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
            val expired =
                db
                    .inventoryBatchDao()
                    .getAllActiveBatchesWithProduct()
                    .first()
                    .filter { it.batch.quantity > 0 && it.batch.expiryDate <= today }
            expired.forEach {
                record(it.product.id, it.batch.id, AdjustmentType.SPOILED, it.batch.quantity, "Expired: written off")
            }
            expired.size
        }

    /** The stock change and its log row. Must run inside a transaction. */
    private suspend fun record(
        productId: Long,
        batchId: Long?,
        type: AdjustmentType,
        quantity: Int,
        notes: String?,
    ) {
        run {
            val batch = batchId?.let { db.inventoryBatchDao().getBatchById(it) }

            require(batch == null || batch.productId == productId) {
                "Batch does not belong to product"
            }
            when (type) {
                AdjustmentType.DAMAGED,
                AdjustmentType.MISSING,
                AdjustmentType.SPOILED,
                -> {
                    if (batch == null) {
                        throw IllegalArgumentException("Batch ID required for negative adjustments")
                    }
                    if (batch.quantity < quantity) {
                        throw IllegalStateException("Not enough stock in batch to adjust")
                    }
                    if (batch.quantity == quantity) {
                        db.inventoryBatchDao().zeroBatchQuantity(batch.id, BatchStatus.SOLD_OUT)
                    } else {
                        db.inventoryBatchDao().decrementBatchQuantity(batch.id, quantity)
                    }
                }
                AdjustmentType.RETURNED -> {
                    throw IllegalArgumentException(
                        "Use Sales History to refund the original sale and restore its lot",
                    )
                }
            }

            val product = requireNotNull(db.productDao().getProductById(productId))
            val adjustment =
                StockAdjustmentEntity(
                    productId = productId,
                    batchId = batchId,
                    adjustmentType = type,
                    quantity = quantity,
                    notes = notes,
                    adjustedAt = Clock.System.now(),
                    unitCost = batch?.unitCost ?: product.purchasePrice,
                    unitScale = product.unitScale,
                    unit = product.unit,
                )
            db.stockAdjustmentDao().insertAdjustment(adjustment)
        }
    }

    override fun getRecentAdjustments(limit: Int): Flow<List<StockAdjustmentEntity>> = db.stockAdjustmentDao().getRecentAdjustments(limit)
}
