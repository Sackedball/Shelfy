package com.kirana.fefoguard.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val LedgerMigration =
    object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE inventory_batches ADD COLUMN unitCost REAL")
            db.execSQL(
                "UPDATE inventory_batches SET unitCost = (SELECT purchasePrice FROM products WHERE products.id = inventory_batches.productId)",
            )
            db.execSQL("ALTER TABLE stock_adjustments ADD COLUMN unitCost REAL")
            db.execSQL(
                "UPDATE stock_adjustments SET unitCost = (SELECT purchasePrice FROM products WHERE products.id = stock_adjustments.productId)",
            )
            db.execSQL("ALTER TABLE sales_history ADD COLUMN originalSaleId INTEGER")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS checkouts (id TEXT NOT NULL PRIMARY KEY, requestKey TEXT NOT NULL, paymentMethod TEXT NOT NULL, totalPaise INTEGER NOT NULL, receiptText TEXT NOT NULL, createdAt INTEGER NOT NULL)",
            )
        }
    }
