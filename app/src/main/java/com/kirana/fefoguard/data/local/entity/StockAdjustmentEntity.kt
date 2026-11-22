package com.kirana.fefoguard.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant

@Entity(
    tableName = "stock_adjustments",
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = InventoryBatchEntity::class,
            parentColumns = ["id"],
            childColumns = ["batchId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("productId"),
        Index("batchId"),
        Index("adjustedAt"),
    ],
)
data class StockAdjustmentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val productId: Long,
    val batchId: Long?,
    val adjustmentType: AdjustmentType,
    val quantity: Int,
    val notes: String?,
    val adjustedAt: Instant,
    val unitCost: Double? = null,
    val unitScale: Int = 1,
    val unit: String = "PCS",
)
