package com.kirana.fefoguard.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Immutable paid-bill snapshot. Refunds are linked reversal rows in sales_history. */
@Entity(tableName = "checkouts", indices = [Index("createdAt")])
data class CheckoutEntity(
    @PrimaryKey val id: String,
    val requestKey: String,
    val paymentMethod: String,
    val totalPaise: Long,
    val receiptText: String,
    val createdAt: Long,
    val cashPaise: Long = 0,
    val upiPaise: Long = 0,
    val linesJson: String = "[]",
    val acceptedLossPaise: Long = 0,
    val cashSessionId: String? = null,
    /** Whole-bill discount already shared into each line's revenue. */
    val billDiscountPaise: Long = 0,
    /** Rounding to a whole rupee, shared into line revenue. Positive means the customer paid more. */
    val roundOffPaise: Long = 0,
    val voidedAt: Long? = null,
    val voidReason: String? = null,
)
