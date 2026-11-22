package com.kirana.fefoguard.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "store_profile")
data class StoreProfileEntity(
    @PrimaryKey
    val id: Int = 1,
    val storeName: String,
    val address: String,
    val phone: String,
    val gstNumber: String?,
    @androidx.room.ColumnInfo(defaultValue = "$")
    val currencySymbol: String = "$",
    val upiId: String? = null,
)
