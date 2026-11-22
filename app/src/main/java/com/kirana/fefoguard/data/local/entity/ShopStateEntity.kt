package com.kirana.fefoguard.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "draft_bills")
data class DraftBillEntity(
    @PrimaryKey val id: String,
    val state: String,
    val payload: String,
    val updatedAt: Long,
    val billDiscountPaise: Long = 0,
)

/** Idempotency receipts, not an employee audit log. */
@Entity(tableName = "shop_operations")
data class ShopOperationEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val request: String,
    val payload: String,
    val createdAt: Long,
)

@Entity(tableName = "cash_sessions")
data class CashSessionEntity(
    @PrimaryKey val id: String,
    val openedAt: Long,
    val openingPaise: Long,
    val closedAt: Long? = null,
    val countedPaise: Long? = null,
    val expectedPaise: Long? = null,
)

@Entity(tableName = "cash_movements")
data class CashMovementEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val amountPaise: Long,
    val note: String,
    val createdAt: Long,
)
