package com.kirana.fefoguard.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.datetime.LocalDate

@Entity(
    tableName = "inventory_batches",
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index("productId"),
        Index(value = ["status", "expiryDate"]),
        Index(value = ["productId", "status", "expiryDate"]),
    ],
)
data class InventoryBatchEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val productId: Long,
    val quantity: Int,
    val expiryDate: LocalDate,
    val status: BatchStatus = BatchStatus.ACTIVE,
    val unitCost: Double? = null,
)
