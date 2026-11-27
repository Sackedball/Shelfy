package com.kirana.fefoguard.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BillMathTest {
    @Test fun roundOffModes() {
        assertEquals(10L, BillMath.roundOff(8590, RoundOffMode.NEAREST))
        assertEquals(-49L, BillMath.roundOff(8549, RoundOffMode.NEAREST))
        assertEquals(-90L, BillMath.roundOff(8590, RoundOffMode.DOWN))
        assertEquals(0L, BillMath.roundOff(8590, RoundOffMode.OFF))
        assertEquals(0L, BillMath.roundOff(8600, RoundOffMode.NEAREST))
    }

    @Test fun amountsAddUp() {
        val amounts = BillMath.amounts(listOf(5400, 4000), 1000, RoundOffMode.NEAREST)
        assertEquals(8400L, amounts.linesPaise - amounts.billDiscountPaise)
        assertEquals(0L, amounts.roundOffPaise)
        assertEquals(8400L, amounts.payablePaise)
        assertTrue(runCatching { BillMath.amounts(listOf(100), 101, RoundOffMode.OFF) }.isFailure)
    }

    @Test fun allocationIsExactAndStable() {
        val shares = BillMath.allocate(100, listOf(1, 1, 1))
        assertEquals(100L, shares.sum())
        assertEquals(listOf(34L, 33L, 33L), shares)
        assertEquals(-7L, BillMath.allocate(-7, listOf(5, 3, 2)).sum())
        assertEquals(listOf(0L, 0L), BillMath.allocate(50, listOf(0, 0)))
    }

    @Test fun lineRevenueSumsToPayableWithDiscountAndRoundOff() {
        val lines = listOf(5400L, 4000L, 333L)
        val amounts = BillMath.amounts(lines, 1234, RoundOffMode.DOWN)
        val revenue = BillMath.lineRevenue(lines, amounts)
        assertEquals(amounts.payablePaise, revenue.sum())
        assertTrue(revenue.all { it >= 0 })
        assertEquals(0L, amounts.payablePaise % 100)
    }

    @Test fun amountToWeight() {
        // Sugar at ₹45.00 a kg: ₹20 buys 444 g, which costs ₹19.98.
        val (grams, cost) = BillMath.quantityForAmount(2000, 4500, 1000)
        assertEquals(444, grams)
        assertEquals(1998L, cost)
        // Pieces: ₹20 at ₹6 each is 3 pieces.
        assertEquals(3 to 1800L, BillMath.quantityForAmount(2000, 600, 1))
        assertTrue(runCatching { BillMath.quantityForAmount(1, 4500, 1000) }.isFailure)
        assertTrue(runCatching { BillMath.quantityForAmount(1, 600, 1) }.isFailure)
    }
}
