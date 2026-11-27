package com.kirana.fefoguard.domain.engine

import com.kirana.fefoguard.data.local.entity.ProductEntity
import com.kirana.fefoguard.domain.cart.CartLineItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptFormatterTest {
    private val milk = ProductEntity(barcode = "m", name = "Milk", category = "Dairy", purchasePrice = 20.0, sellingPrice = 27.0)

    @Test fun receiptShowsShopPhoneTotalsAndThanks() {
        val lines = listOf(CartLineItem(milk, 2, 15.0))
        val text =
            ReceiptFormatter.format("Shop", "B1", 0L, lines, 5000, 0, 0, phone = "98765 43210")
        val rows = text.lines()
        assertEquals("Shop", rows[0])
        assertEquals("98765 43210", rows[1])
        assertTrue(text.contains("Total INR 45.90"))
        assertEquals("Thank you for shopping with us!", rows.last { it.isNotBlank() })
    }

    @Test fun blankPhoneIsOmitted() {
        val text = ReceiptFormatter.format("Shop", "B1", 0L, listOf(CartLineItem(milk, 1)), 2700, 0, 0, phone = " ")
        assertEquals("Bill: B1", text.lines()[1])
    }
}
