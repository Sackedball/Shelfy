package com.kirana.fefoguard.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Every step from version 1 to the current schema, in one place. Versions 4 to 8 live in LedgerMigration.kt and
 * RetailMigration.kt and are listed here so the app registers the whole chain from a single list.
 */
object DatabaseMigrations {
    val MIGRATION_1_2 =
        object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE store_profile ADD COLUMN currencySymbol TEXT NOT NULL DEFAULT '$'")
            }
        }

    val MIGRATION_2_3 =
        object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE store_profile ADD COLUMN upiId TEXT")
            }
        }

    val MIGRATION_3_4 =
        object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sales_history ADD COLUMN checkoutId TEXT")
            }
        }

    val all: Array<Migration> =
        arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, LedgerMigration, RetailMigration, ShopToolsMigration, IndexMigration)
}
