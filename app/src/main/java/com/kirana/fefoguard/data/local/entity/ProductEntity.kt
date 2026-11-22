package com.kirana.fefoguard.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "products",
    indices = [
        Index(value = ["barcode"], unique = true),
        Index(value = ["category"]),
    ],
)
data class ProductEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val barcode: String,
    val name: String,
    val category: String,
    val purchasePrice: Double,
    val sellingPrice: Double,
    val minStockLevel: Int = 5,
    val unit: String = "PCS",
    val packSize: Int = 1,
    val expiryTracked: Boolean = true,
    /** Other names staff type or say, for search ("chini", "cheeni", "चीनी"). */
    val alias: String = "",
    /** Printed maximum retail price in rupees, when known. */
    val mrp: Double? = null,
    /** Pinned to the quick-tap grid on Scan. */
    val favourite: Boolean = false,
) {
    @get:androidx.room.Ignore
    val unitScale: Int get() = if (unit == "PCS") 1 else 1000
}
