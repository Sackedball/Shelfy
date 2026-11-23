package com.kirana.fefoguard.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.kirana.fefoguard.data.local.dao.InventoryBatchDao
import com.kirana.fefoguard.data.local.dao.ProductDao
import com.kirana.fefoguard.data.local.dao.SalesHistoryDao
import com.kirana.fefoguard.data.local.dao.StockAdjustmentDao
import com.kirana.fefoguard.data.local.dao.StoreProfileDao
import com.kirana.fefoguard.data.local.entity.Converters
import com.kirana.fefoguard.data.local.entity.InventoryBatchEntity
import com.kirana.fefoguard.data.local.entity.ProductEntity
import com.kirana.fefoguard.data.local.entity.SalesHistoryEntity
import com.kirana.fefoguard.data.local.entity.StockAdjustmentEntity
import com.kirana.fefoguard.data.local.entity.StoreProfileEntity

@Database(
    entities = [
        ProductEntity::class,
        InventoryBatchEntity::class,
        SalesHistoryEntity::class,
        StockAdjustmentEntity::class,
        StoreProfileEntity::class,
        com.kirana.fefoguard.data.local.entity.CheckoutEntity::class,
        com.kirana.fefoguard.data.local.entity.DraftBillEntity::class,
        com.kirana.fefoguard.data.local.entity.ShopOperationEntity::class,
        com.kirana.fefoguard.data.local.entity.CashSessionEntity::class,
        com.kirana.fefoguard.data.local.entity.CashMovementEntity::class,
        com.kirana.fefoguard.data.local.entity.PriceChangeEntity::class,
        com.kirana.fefoguard.data.local.entity.AppSettingEntity::class,
    ],
    version = 8,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class FefoGuardDatabase : RoomDatabase() {
    abstract fun shopStateDao(): com.kirana.fefoguard.data.local.dao.ShopStateDao

    abstract fun checkoutDao(): com.kirana.fefoguard.data.local.dao.CheckoutDao

    abstract fun productDao(): ProductDao

    abstract fun inventoryBatchDao(): InventoryBatchDao

    abstract fun salesHistoryDao(): SalesHistoryDao

    abstract fun stockAdjustmentDao(): StockAdjustmentDao

    abstract fun storeProfileDao(): StoreProfileDao

    abstract fun settingsDao(): com.kirana.fefoguard.data.local.dao.SettingsDao
}
