package com.kirana.fefoguard.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

data class BatchWithProduct(
    @Embedded val batch: InventoryBatchEntity,
    @Relation(
        parentColumn = "productId",
        entityColumn = "id",
    )
    val product: ProductEntity,
)
