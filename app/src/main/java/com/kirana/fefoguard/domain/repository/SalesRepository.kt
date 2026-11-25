package com.kirana.fefoguard.domain.repository

import com.kirana.fefoguard.data.local.entity.CheckoutEntity
import com.kirana.fefoguard.data.local.entity.SaleSource
import com.kirana.fefoguard.data.local.entity.SalesHistoryEntity
import com.kirana.fefoguard.domain.cart.CartLineItem
import kotlinx.coroutines.flow.Flow

enum class ReturnDisposition { RESTOCK, DISCARD, MONEY_ONLY }

data class RefundRequest(
    val operationId: String,
    val saleId: Long,
    val quantity: Int,
    val disposition: ReturnDisposition,
    val paymentMethod: String = "CASH",
)

data class CheckoutQuote(
    /** What the customer pays: lines, minus the bill discount, plus round-off. */
    val totalPaise: Long,
    val costPaise: Long,
    val linesPaise: Long = totalPaise,
    val billDiscountPaise: Long = 0,
    val roundOffPaise: Long = 0,
) {
    val lossPaise: Long get() = (costPaise - totalPaise).coerceAtLeast(0)
}

interface SalesRepository {
    /** The lines of the most recent bill that was not voided, for "repeat last bill". Empty if there is none. */
    suspend fun lastBillLines(): List<CartLineItem>

    suspend fun quote(
        items: List<CartLineItem>,
        billDiscountPaise: Long = 0,
    ): CheckoutQuote

    /**
     * Cancels a whole bill that has no refunds yet, inside the void window. The sale rows stay (with
     * reversal rows against them), stock returns to the shelf or is written off if the batch expired,
     * and cash expected in the till drops by exactly what was paid in cash.
     */
    suspend fun voidBill(
        billId: String,
        reason: String,
    )

    /** A refund with its disposition and payment method; there is deliberately no shortcut that drops them. */
    suspend fun refund(request: RefundRequest)

    suspend fun checkout(
        items: List<CartLineItem>,
        bill: CheckoutEntity,
    )

    suspend fun refund(
        saleId: Long,
        quantity: Int,
    )

    // Note: Sale must be atomic (deduct quantity + insert log)
    suspend fun recordSale(
        productId: Long,
        batchId: Long?,
        quantity: Int,
        sellingPrice: Double,
        purchasePrice: Double,
        source: SaleSource,
    )

    // Automatically find the earliest expiring batch and deduct from it
    suspend fun recordSaleFefo(
        productId: Long,
        quantity: Int,
        source: SaleSource,
        checkoutId: String? = null,
        priceOverride: Double? = null,
        lineRevenuePaise: Long? = null,
    )

    fun getRecentSales(limit: Int = 50): Flow<List<SalesHistoryEntity>>
}
