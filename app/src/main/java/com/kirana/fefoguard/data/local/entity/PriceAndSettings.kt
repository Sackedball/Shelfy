package com.kirana.fefoguard.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One row per selling-price change, so a bulk update can be reviewed later. */
@Entity(tableName = "price_changes")
data class PriceChangeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val oldPrice: Double,
    val newPrice: Double,
    val changedAt: Long,
    val reason: String,
)

/** Shop settings that must travel with a backup (round-off mode, discount tiers, void window). */
@Entity(tableName = "app_settings")
data class AppSettingEntity(
    @PrimaryKey val key: String,
    val value: String,
)
