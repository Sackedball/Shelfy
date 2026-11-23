package com.kirana.fefoguard.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val ShopToolsMigration =
    object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE products ADD COLUMN alias TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE products ADD COLUMN mrp REAL")
            db.execSQL("ALTER TABLE products ADD COLUMN favourite INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE checkouts ADD COLUMN billDiscountPaise INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE checkouts ADD COLUMN roundOffPaise INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE checkouts ADD COLUMN voidedAt INTEGER")
            db.execSQL("ALTER TABLE checkouts ADD COLUMN voidReason TEXT")
            db.execSQL("ALTER TABLE draft_bills ADD COLUMN billDiscountPaise INTEGER NOT NULL DEFAULT 0")
            db.execSQL(
                "CREATE TABLE price_changes (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, productId INTEGER NOT NULL, oldPrice REAL NOT NULL, newPrice REAL NOT NULL, changedAt INTEGER NOT NULL, reason TEXT NOT NULL)",
            )
            db.execSQL("CREATE TABLE app_settings (`key` TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL)")
        }
    }

val RetailMigration =
    object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE checkouts ADD COLUMN cashSessionId TEXT")
            db.execSQL("ALTER TABLE products ADD COLUMN unit TEXT NOT NULL DEFAULT 'PCS'")
            db.execSQL("ALTER TABLE products ADD COLUMN packSize INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE products ADD COLUMN expiryTracked INTEGER NOT NULL DEFAULT 1")
            for (table in listOf("sales_history", "stock_adjustments")) {
                db.execSQL("ALTER TABLE $table ADD COLUMN unitScale INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE $table ADD COLUMN unit TEXT NOT NULL DEFAULT 'PCS'")
            }
            db.execSQL("ALTER TABLE checkouts ADD COLUMN cashPaise INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE checkouts ADD COLUMN upiPaise INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE checkouts ADD COLUMN linesJson TEXT NOT NULL DEFAULT '[]'")
            db.execSQL("ALTER TABLE checkouts ADD COLUMN acceptedLossPaise INTEGER NOT NULL DEFAULT 0")
            db.execSQL("UPDATE checkouts SET cashPaise = totalPaise WHERE paymentMethod = 'CASH'")
            db.execSQL("UPDATE checkouts SET upiPaise = totalPaise WHERE paymentMethod = 'UPI_QR'")
            db.execSQL(
                "CREATE TABLE draft_bills (id TEXT NOT NULL PRIMARY KEY, state TEXT NOT NULL, payload TEXT NOT NULL, updatedAt INTEGER NOT NULL)",
            )
            db.execSQL(
                "CREATE TABLE shop_operations (id TEXT NOT NULL PRIMARY KEY, kind TEXT NOT NULL, request TEXT NOT NULL, payload TEXT NOT NULL, createdAt INTEGER NOT NULL)",
            )
            db.execSQL(
                "CREATE TABLE cash_sessions (id TEXT NOT NULL PRIMARY KEY, openedAt INTEGER NOT NULL, openingPaise INTEGER NOT NULL, closedAt INTEGER, countedPaise INTEGER, expectedPaise INTEGER)",
            )
            db.execSQL(
                "CREATE TABLE cash_movements (id TEXT NOT NULL PRIMARY KEY, sessionId TEXT NOT NULL, amountPaise INTEGER NOT NULL, note TEXT NOT NULL, createdAt INTEGER NOT NULL)",
            )
        }
    }

/**
 * Indices for the lookups that scan the whole table today: the lines of one bill, the refunds against one sale
 * (asked once per sale when refunding and when verifying a restore) and bills by date. Nothing is rewritten.
 */
val IndexMigration =
    object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_sales_history_checkoutId` ON `sales_history` (`checkoutId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_sales_history_originalSaleId` ON `sales_history` (`originalSaleId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_checkouts_createdAt` ON `checkouts` (`createdAt`)")
        }
    }
