package com.kirana.fefoguard.domain.engine

import com.kirana.fefoguard.data.local.entity.CheckoutEntity
import com.kirana.fefoguard.data.local.entity.ProductEntity
import com.kirana.fefoguard.data.local.entity.SalesHistoryEntity
import com.kirana.fefoguard.data.local.entity.StockAdjustmentEntity
import kotlinx.datetime.Instant
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/** Plain numbers for the end-of-day view. Everything is in paise except [lossPaise]'s source rows. */
data class TodaySummary(
    val billCount: Int,
    val totalPaise: Long,
    val cashPaise: Long,
    val upiPaise: Long,
    val discountPaise: Long,
    val roundOffPaise: Long,
    val netRevenuePaise: Long,
    val profitPaise: Long,
    val lossPaise: Long,
    /** Money paid back to customers today; already taken out of [totalPaise], [cashPaise] and [upiPaise]. */
    val refundsPaise: Long = 0,
)

/** A refund paid out. [paymentMethod] is CASH or UPI_QR; [createdAt] is epoch milliseconds. */
data class RefundRecord(
    val billId: String,
    val amountPaise: Long,
    val paymentMethod: String,
    val createdAt: Long,
)

data class ProductEarning(
    val product: ProductEntity,
    val revenuePaise: Long,
    val profitPaise: Long,
    val quantity: Int,
) {
    /** Margin in tenths of a percent (123 means 12.3%). */
    val marginTenthsPercent: Long get() = if (revenuePaise <= 0) 0 else profitPaise * 1000 / revenuePaise
}

data class SlowStock(
    val product: ProductEntity,
    val onHand: Int,
    val soldInPeriod: Int,
    val valueTiedUpPaise: Long,
) {
    val neverSold: Boolean get() = soldInPeriod <= 0
}

object ShopReports {
    private fun paise(value: Double) =
        BigDecimal
            .valueOf(value)
            .movePointRight(2)
            .setScale(0, RoundingMode.HALF_UP)
            .toLong()

    fun today(
        bills: List<CheckoutEntity>,
        sales: List<SalesHistoryEntity>,
        adjustments: List<StockAdjustmentEntity>,
        products: List<ProductEntity>,
        from: Instant,
        to: Instant,
        refunds: List<RefundRecord> = emptyList(),
    ): TodaySummary {
        val dayBills = bills.filter { it.voidedAt == null && it.createdAt >= from.toEpochMilliseconds() && it.createdAt < to.toEpochMilliseconds() }
        // A refund counts on the day the money went back, even for an older bill. A voided bill is reversed
        // as a whole, so a refund against it must not be taken off a second time.
        val voidedBillIds = bills.filter { it.voidedAt != null }.map { it.id }.toSet()
        val dayRefunds =
            refunds.filter {
                it.createdAt >= from.toEpochMilliseconds() && it.createdAt < to.toEpochMilliseconds() && it.billId !in voidedBillIds
            }
        val cashRefunds = dayRefunds.filter { it.paymentMethod == "CASH" }.sumOf { it.amountPaise }
        val upiRefunds = dayRefunds.filter { it.paymentMethod != "CASH" }.sumOf { it.amountPaise }
        val daySales = sales.filter { it.soldAt >= from && it.soldAt < to }
        val costs = products.associate { it.id to it.purchasePrice }
        val loss =
            adjustments
                .filter { it.adjustedAt >= from && it.adjustedAt < to && it.adjustmentType != com.kirana.fefoguard.data.local.entity.AdjustmentType.RETURNED }
                .sumOf { paise(it.quantity * (it.unitCost ?: costs[it.productId] ?: 0.0) / it.unitScale) }
        return TodaySummary(
            billCount = dayBills.size,
            totalPaise = dayBills.sumOf { it.totalPaise } - cashRefunds - upiRefunds,
            cashPaise = dayBills.sumOf { it.cashPaise } - cashRefunds,
            upiPaise = dayBills.sumOf { it.upiPaise } - upiRefunds,
            discountPaise = dayBills.sumOf { it.billDiscountPaise },
            roundOffPaise = dayBills.sumOf { it.roundOffPaise },
            netRevenuePaise = daySales.sumOf { paise(it.revenue) },
            profitPaise = daySales.sumOf { paise(it.profit) },
            lossPaise = loss,
            refundsPaise = cashRefunds + upiRefunds,
        )
    }

    /** Products ranked by profit (not revenue) in [from, to); refunds count against the product. */
    fun topEarners(
        sales: List<SalesHistoryEntity>,
        products: List<ProductEntity>,
        from: Instant,
        to: Instant,
        limit: Int = 10,
    ): List<ProductEarning> {
        val byId = products.associateBy { it.id }
        return sales
            .filter { it.soldAt >= from && it.soldAt < to }
            .groupBy { it.productId }
            .mapNotNull { (id, rows) ->
                val product = byId[id] ?: return@mapNotNull null
                ProductEarning(product, rows.sumOf { paise(it.revenue) }, rows.sumOf { paise(it.profit) }, rows.sumOf { it.quantity })
            }.filter { it.profitPaise > 0 }
            .sortedByDescending { it.profitPaise }
            .take(limit)
    }

    /**
     * Stock that is sitting on the shelf: items with stock whose net sales in the period are at or below
     * [slowBelowPerDay] retail units a day. Never-sold items come first, then by money tied up.
     */
    fun slowStock(
        products: List<ProductEntity>,
        onHand: Map<Long, Int>,
        costPerUnit: Map<Long, Double>,
        sales: List<SalesHistoryEntity>,
        from: Instant,
        to: Instant,
        days: Int,
        slowBelowPerDay: Double = FefoEngine.SLOW_MOVING_THRESHOLD,
    ): List<SlowStock> {
        val sold = sales.filter { it.soldAt >= from && it.soldAt < to }.groupBy { it.productId }.mapValues { (_, rows) -> rows.sumOf { it.quantity } }
        return products
            .mapNotNull { product ->
                val stock = onHand[product.id] ?: 0
                if (stock <= 0) return@mapNotNull null
                val net = (sold[product.id] ?: 0).coerceAtLeast(0)
                val perDay = net.toDouble() / days / product.unitScale
                if (perDay > slowBelowPerDay) return@mapNotNull null
                val value = paise(stock * (costPerUnit[product.id] ?: product.purchasePrice) / product.unitScale)
                SlowStock(product, stock, net, value)
            }.sortedWith(compareByDescending<SlowStock> { it.neverSold }.thenByDescending { it.valueTiedUpPaise })
    }

    private fun csv(value: String) = "\"" + value.replace("\"", "\"\"") + "\""

    private fun rupees(paise: Long) = String.format(Locale.US, "%.2f", paise / 100.0)

    /**
     * One row per bill in [from, to): for an accountant. This is a plain sales list, not a tax invoice.
     * Voided bills are included and marked, with their amounts so totals can be reconciled.
     * [refunded] is everything paid back against the bill so far and [net] is total minus that (0 for a voided bill).
     */
    fun salesCsv(
        bills: List<CheckoutEntity>,
        from: Instant,
        to: Instant,
        refunds: List<RefundRecord> = emptyList(),
        zoneFormat: (Long) -> String,
    ): String =
        buildString {
            val refundedByBill = refunds.groupBy { it.billId }.mapValues { (_, rows) -> rows.sumOf { it.amountPaise } }
            appendLine("bill,date,payment,total,cash,upi,bill_discount,round_off,refunded,net,status")
            bills
                .filter { it.createdAt >= from.toEpochMilliseconds() && it.createdAt < to.toEpochMilliseconds() }
                .sortedBy { it.createdAt }
                .forEach {
                    val refunded = refundedByBill[it.id] ?: 0L
                    appendLine(
                        listOf(
                            csv(it.id),
                            csv(zoneFormat(it.createdAt)),
                            csv(it.paymentMethod),
                            rupees(it.totalPaise),
                            rupees(it.cashPaise),
                            rupees(it.upiPaise),
                            rupees(it.billDiscountPaise),
                            rupees(it.roundOffPaise),
                            rupees(refunded),
                            rupees(if (it.voidedAt != null) 0L else it.totalPaise - refunded),
                            if (it.voidedAt != null) "VOID" else "OK",
                        ).joinToString(","),
                    )
                }
        }
}
