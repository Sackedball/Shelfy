package com.kirana.fefoguard.domain.engine

import com.kirana.fefoguard.data.local.entity.CheckoutEntity
import com.kirana.fefoguard.data.local.entity.ProductEntity
import com.kirana.fefoguard.data.local.entity.SaleSource
import com.kirana.fefoguard.data.local.entity.SalesHistoryEntity
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShopReportsTest {
    private val from = Instant.parse("2026-11-15T00:00:00Z")
    private val to = Instant.parse("2026-11-16T00:00:00Z")
    private val noon = Instant.parse("2026-11-15T12:00:00Z")
    private val milk = ProductEntity(id = 1, barcode = "m", name = "Milk", category = "Dairy", purchasePrice = 20.0, sellingPrice = 27.0)
    private val tea = ProductEntity(id = 2, barcode = "t", name = "Tea", category = "Staples", purchasePrice = 100.0, sellingPrice = 120.0)

    private fun sale(
        product: ProductEntity,
        quantity: Int,
        revenue: Double,
        profit: Double,
        at: Instant = noon,
    ) = SalesHistoryEntity(productId = product.id, batchId = null, quantity = quantity, sellingPrice = product.sellingPrice, purchasePrice = product.purchasePrice, revenue = revenue, profit = profit, soldAt = at, source = SaleSource.MANUAL_LIST_SELL)

    @Test fun todayCountsOnlyTodaysUnvoidedBills() {
        val ms = noon.toEpochMilliseconds()
        val bills =
            listOf(
                CheckoutEntity("a", "", "SPLIT", 5400, "", ms, cashPaise = 2000, upiPaise = 3400, billDiscountPaise = 100, roundOffPaise = -20),
                CheckoutEntity("b", "", "CASH", 1000, "", ms, cashPaise = 1000, voidedAt = ms + 1),
                CheckoutEntity("c", "", "CASH", 999, "", from.toEpochMilliseconds() - 1, cashPaise = 999),
            )
        val summary = ShopReports.today(bills, listOf(sale(milk, 2, 54.0, 14.0), sale(milk, -1, -27.0, -7.0)), emptyList(), listOf(milk), from, to)
        assertEquals(1, summary.billCount)
        assertEquals(5400L, summary.totalPaise)
        assertEquals(2000L, summary.cashPaise)
        assertEquals(3400L, summary.upiPaise)
        assertEquals(100L, summary.discountPaise)
        assertEquals(-20L, summary.roundOffPaise)
        assertEquals(2700L, summary.netRevenuePaise)
        assertEquals(700L, summary.profitPaise)
    }

    @Test fun todayTakesRefundsOutOfCashUpiAndTotalOnTheDayTheMoneyWentBack() {
        val ms = noon.toEpochMilliseconds()
        val yesterday = from.toEpochMilliseconds() - 1
        val bills =
            listOf(
                CheckoutEntity("a", "", "SPLIT", 5400, "", ms, cashPaise = 2000, upiPaise = 3400),
                CheckoutEntity("b", "", "CASH", 1000, "", ms, cashPaise = 1000, voidedAt = ms + 1),
                CheckoutEntity("c", "", "CASH", 999, "", yesterday, cashPaise = 999),
            )
        val refunds =
            listOf(
                RefundRecord("a", 1000, "CASH", ms),
                RefundRecord("a", 1700, "UPI_QR", ms),
                // An older bill refunded today still reduces today's takings.
                RefundRecord("c", 500, "CASH", ms),
                // Refunded yesterday: belongs to yesterday.
                RefundRecord("a", 300, "CASH", yesterday),
                // The voided bill was already reversed as a whole, so this must not be taken off again.
                RefundRecord("b", 400, "UPI_QR", ms),
            )
        val summary = ShopReports.today(bills, emptyList(), emptyList(), listOf(milk), from, to, refunds)
        assertEquals(500L, summary.cashPaise)
        assertEquals(1700L, summary.upiPaise)
        assertEquals(2200L, summary.totalPaise)
        assertEquals(3200L, summary.refundsPaise)
    }

    @Test fun todayNetTotalMatchesNetRevenueWhenEverySaleHasABill() {
        val ms = noon.toEpochMilliseconds()
        val bills = listOf(CheckoutEntity("a", "", "UPI_QR", 5400, "", ms, upiPaise = 5400))
        val sales = listOf(sale(milk, 2, 54.0, 14.0), sale(milk, -1, -27.0, -7.0))
        val summary = ShopReports.today(bills, sales, emptyList(), listOf(milk), from, to, listOf(RefundRecord("a", 2700, "UPI_QR", ms)))
        assertEquals(2700L, summary.totalPaise)
        assertEquals(summary.netRevenuePaise, summary.totalPaise)
    }

    @Test fun salesCsvShowsRefundedAndNetPerBill() {
        val ms = noon.toEpochMilliseconds()
        val bills =
            listOf(
                CheckoutEntity("a", "", "SPLIT", 5400, "", ms, cashPaise = 2000, upiPaise = 3400, billDiscountPaise = 100, roundOffPaise = -20),
                CheckoutEntity("b", "", "CASH", 1000, "", ms + 1, cashPaise = 1000, voidedAt = ms + 2),
            )
        val refunds = listOf(RefundRecord("a", 600, "CASH", ms), RefundRecord("a", 400, "UPI_QR", ms), RefundRecord("b", 100, "CASH", ms))
        val lines = ShopReports.salesCsv(bills, from, to, refunds) { "2026-11-15" }.lines()
        assertEquals("bill,date,payment,total,cash,upi,bill_discount,round_off,refunded,net,status", lines[0])
        assertEquals("\"a\",\"2026-11-15\",\"SPLIT\",54.00,20.00,34.00,1.00,-0.20,10.00,44.00,OK", lines[1])
        assertEquals("\"b\",\"2026-11-15\",\"CASH\",10.00,10.00,0.00,0.00,0.00,1.00,0.00,VOID", lines[2])
    }

    @Test fun topEarnersRankByProfitNotRevenue() {
        val sales = listOf(sale(milk, 10, 270.0, 70.0), sale(tea, 1, 120.0, 20.0), sale(tea, 1, 120.0, 20.0))
        val ranked = ShopReports.topEarners(sales, listOf(milk, tea), from, to)
        assertEquals(listOf("Milk", "Tea"), ranked.map { it.product.name })
        assertEquals(259L, ranked[0].marginTenthsPercent)
    }

    @Test fun slowStockListsNeverSoldFirstThenByMoneyTiedUp() {
        val sales = listOf(sale(milk, 1, 27.0, 7.0))
        val list =
            ShopReports.slowStock(
                products = listOf(milk, tea),
                onHand = mapOf(1L to 50, 2L to 3),
                costPerUnit = mapOf(1L to 20.0, 2L to 100.0),
                sales = sales,
                from = from,
                to = to,
                days = 30,
            )
        assertEquals(listOf("Tea", "Milk"), list.map { it.product.name })
        assertTrue(list[0].neverSold)
        assertEquals(30000L, list[0].valueTiedUpPaise)
    }

    @Test fun salesCsvMarksVoidedBillsAndEscapesQuotes() {
        val ms = noon.toEpochMilliseconds()
        val bills = listOf(CheckoutEntity("say \"hi\"", "", "CASH", 1000, "", ms, cashPaise = 1000, voidedAt = ms))
        val csv = ShopReports.salesCsv(bills, from, to) { "2026-11-15" }
        assertTrue(csv.lines()[0].startsWith("bill,date"))
        assertTrue(csv.lines()[1].startsWith("\"say \"\"hi\"\"\""))
        assertTrue(csv.lines()[1].endsWith("VOID"))
    }
}
