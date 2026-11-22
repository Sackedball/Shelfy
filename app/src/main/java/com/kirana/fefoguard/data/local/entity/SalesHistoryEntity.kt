package com.kirana.fefoguard.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant

@Entity(
    tableName = "sales_history",
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
        Index("soldAt"),
        Index("checkoutId"),
        Index("originalSaleId"),
    ],
)
data class SalesHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val productId: Long,
    val batchId: Long?,
    val quantity: Int,
    val sellingPrice: Double,
    val purchasePrice: Double,
    val revenue: Double,
    val profit: Double,
    val soldAt: Instant,
    val source: SaleSource,
    val checkoutId: String? = null,
    val originalSaleId: Long? = null,
    val unitScale: Int = 1,
    val unit: String = "PCS",
)
