package com.kirana.fefoguard.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

data class AdjustmentWithProduct(
    @Embedded val adjustment: StockAdjustmentEntity,
    @Relation(
        parentColumn = "productId",
        entityColumn = "id",
    )
    val product: ProductEntity,
)
