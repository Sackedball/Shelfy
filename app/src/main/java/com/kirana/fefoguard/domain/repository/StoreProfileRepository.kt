package com.kirana.fefoguard.domain.repository

import com.kirana.fefoguard.data.local.entity.StoreProfileEntity
import kotlinx.coroutines.flow.Flow

interface StoreProfileRepository {
    fun getStoreProfile(): Flow<StoreProfileEntity?>

    suspend fun upsertStoreProfile(
        storeName: String,
        address: String,
        phone: String,
        gstNumber: String?,
        currencySymbol: String = "₹",
        upiId: String? = null,
    )
}
