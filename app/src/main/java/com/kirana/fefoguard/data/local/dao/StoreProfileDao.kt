package com.kirana.fefoguard.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kirana.fefoguard.data.local.entity.StoreProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StoreProfileDao {
    @Query("SELECT * FROM store_profile WHERE id = 1 LIMIT 1")
    fun getStoreProfile(): Flow<StoreProfileEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStoreProfile(profile: StoreProfileEntity): Long
}
