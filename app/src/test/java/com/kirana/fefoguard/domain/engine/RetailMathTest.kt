package com.kirana.fefoguard.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RetailMathTest {
    @Test fun looseQuantitiesAreExactAndRejectOverprecision() {
        assertEquals(1250, Quantities.parse("1.250", 1000))
        assertEquals("1.25", Quantities.format(1250, 1000))
        assertTrue(runCatching { Quantities.parse("0.0001", 1000) }.isFailure)
        assertTrue(runCatching { Quantities.parse("1.5", 1) }.isFailure)
        assertTrue(runCatching { Quantities.parse("2147484", 1000) }.isFailure)
    }

    @Test fun gramSaleRoundsOnceToPaise() {
        assertEquals(4993L, Money.linePaise(199.70, 250, 1000))
        assertEquals(8488L, Money.linePaise(Money.discountedUnit(199.70, 15.0), 500, 1000))
    }

    @Test fun cumulativePartialRefundsReturnExactlyOriginalAmount() {
        val cumulative = (0..3).map { Quantities.proportional(100, it, 3) }
        assertEquals(listOf(0L, 33L, 67L, 100L), cumulative)
        assertEquals(100L, cumulative.zipWithNext { first, next -> next - first }.sum())
    }

    @Test fun nonMovingThresholdUsesRetailUnits() {
        assertEquals(Movement.SLOW_MOVING, FefoEngine.classifyMovement(250.0 / 1000))
    }
}
