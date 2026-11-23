package com.kirana.fefoguard.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kirana.fefoguard.data.local.entity.AppSettingEntity
import com.kirana.fefoguard.data.local.entity.PriceChangeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(setting: AppSettingEntity): Long

    @Query("SELECT value FROM app_settings WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Query("SELECT * FROM app_settings")
    fun observeAll(): Flow<List<AppSettingEntity>>

    @Query("SELECT * FROM app_settings")
    suspend fun all(): List<AppSettingEntity>

    @Insert
    suspend fun insertPriceChange(change: PriceChangeEntity): Long

    @Query("SELECT * FROM price_changes ORDER BY changedAt DESC, id DESC")
    suspend fun priceChanges(): List<PriceChangeEntity>
}
