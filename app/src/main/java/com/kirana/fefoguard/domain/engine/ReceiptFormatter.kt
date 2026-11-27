package com.kirana.fefoguard.domain.engine

import com.kirana.fefoguard.domain.cart.CartLineItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ReceiptLabels(
    val bill: String = "Bill",
    val subtotal: String = "Subtotal",
    val discount: String = "Discount",
    val total: String = "Total",
    val cash: String = "Cash",
    val upi: String = "UPI",
    val change: String = "Change",
    val confirmed: String = "Payment manually confirmed",
    val thanks: String = "Thank you for shopping with us!",
    val roundOff: String = "Round off",
)

object ReceiptFormatter {
    private fun money(value: Long) = String.format(Locale.US, "%.2f", Money.rupees(value))

    fun format(
        store: String,
        id: String,
        timestamp: Long,
        items: List<CartLineItem>,
        cash: Long,
        upi: Long,
        change: Long,
        labels: ReceiptLabels = ReceiptLabels(),
        phone: String? = null,
        billDiscountPaise: Long = 0,
        roundOffPaise: Long = 0,
    ): String =
        buildString {
            appendLine(store)
            phone?.takeIf { it.isNotBlank() }?.let { appendLine(it) }
            appendLine(labels.bill + ": " + id)
            appendLine(SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(timestamp)))
            items.forEach {
                appendLine(it.product.name + " x " + Quantities.format(it.quantity, it.product.unitScale) + " " + it.product.unit + " = INR " + money(it.totalPaise))
            }
            val subtotal = items.sumOf { it.subtotalPaise }
            val total = items.sumOf { it.totalPaise } - billDiscountPaise + roundOffPaise
            appendLine(labels.subtotal + " INR " + money(subtotal))
            appendLine(labels.discount + " INR " + money(subtotal - total + roundOffPaise))
            if (roundOffPaise != 0L) {
                appendLine(labels.roundOff + " INR " + (if (roundOffPaise < 0) "-" else "") + money(Math.abs(roundOffPaise)))
            }
            appendLine(labels.total + " INR " + money(total))
            appendLine(labels.cash + " INR " + money(cash))
            appendLine(labels.upi + " INR " + money(upi))
            appendLine(labels.change + " INR " + money(change))
            appendLine(labels.confirmed)
            appendLine(labels.thanks)
        }
}
