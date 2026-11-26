package com.kirana.fefoguard.data.repository

import com.kirana.fefoguard.data.local.FefoGuardDatabase
import com.kirana.fefoguard.data.local.entity.StoreProfileEntity
import com.kirana.fefoguard.domain.engine.UpiAddress
import com.kirana.fefoguard.domain.repository.StoreProfileRepository
import kotlinx.coroutines.flow.Flow

class StoreProfileRepositoryImpl(
    private val db: FefoGuardDatabase,
) : StoreProfileRepository {
    override fun getStoreProfile(): Flow<StoreProfileEntity?> = db.storeProfileDao().getStoreProfile()

    override suspend fun upsertStoreProfile(
        storeName: String,
        address: String,
        phone: String,
        gstNumber: String?,
        currencySymbol: String,
        upiId: String?,
    ) {
        require(upiId.isNullOrBlank() || UpiAddress.isValid(upiId)) {
            "Enter a valid UPI address"
        }
        val profile =
            StoreProfileEntity(
                id = 1,
                storeName = storeName,
                address = address,
                phone = phone,
                gstNumber = gstNumber,
                currencySymbol = "\u20b9",
                upiId = upiId,
            )
        db.storeProfileDao().upsertStoreProfile(profile)
    }
}
