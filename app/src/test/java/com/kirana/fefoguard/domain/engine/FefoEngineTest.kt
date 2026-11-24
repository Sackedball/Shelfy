package com.kirana.fefoguard.domain.engine

import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class FefoEngineTest {
    private val today = LocalDate(2026, 9, 12) // arbitrary fixed date for testing

    @Test
    fun analyzeBatch_Milk() {
        val expiry = today.plusDays(2)
        val result =
            FefoEngine.analyzeBatch(
                expiryDate = expiry,
                quantity = 20,
                purchasePrice = 30.0,
                minStock = 5,
                dailyRate = 0.5,
                today = today,
            )

        assertEquals(BatchStatusTier.CRITICAL, result.status)
        assertEquals(2, result.daysLeft)
        assertEquals(80, result.riskScore)
        assertEquals(StockStatus.OK, result.stockStatus)
        assertEquals(600.0, result.potentialLoss, 0.0)
        assertEquals(Movement.SLOW_MOVING, result.movement)
        assertEquals(19.0, result.expectedLeftover, 0.0)
        assertEquals(570.0, result.expectedLoss, 0.0)
    }

    @Test
    fun analyzeBatch_Bread() {
        val expiry = today.plusDays(-1)
        val result =
            FefoEngine.analyzeBatch(
                expiryDate = expiry,
                quantity = 3,
                purchasePrice = 20.0,
                minStock = 10,
                dailyRate = 0.0,
                today = today,
            )

        assertEquals(BatchStatusTier.EXPIRED, result.status)
        assertEquals(-1, result.daysLeft)
        assertEquals(100, result.riskScore)
        assertEquals(StockStatus.LOW, result.stockStatus)
        assertEquals(60.0, result.potentialLoss, 0.0)
        assertEquals(Movement.NON_MOVING, result.movement)
        assertEquals(3.0, result.expectedLeftover, 0.0)
        assertEquals(60.0, result.expectedLoss, 0.0)
    }

    @Test
    fun analyzeBatch_Rice() {
        val expiry = today.plusDays(30)
        val result =
            FefoEngine.analyzeBatch(
                expiryDate = expiry,
                quantity = 50,
                purchasePrice = 150.0,
                minStock = 5,
                dailyRate = 0.0,
                today = today,
            )

        assertEquals(BatchStatusTier.SAFE, result.status)
        assertEquals(30, result.daysLeft)
        assertEquals(10, result.riskScore)
        assertEquals(StockStatus.OK, result.stockStatus)
        assertEquals(0.0, result.potentialLoss, 0.0)
        assertEquals(Movement.NON_MOVING, result.movement)
        assertEquals(50.0, result.expectedLeftover, 0.0)
        assertEquals(7500.0, result.expectedLoss, 0.0)
    }

    @Test
    fun calculateVelocity() {
        val sales =
            listOf(
                Pair(today.plusDays(-5), 2),
                Pair(today.plusDays(-1), 3),
            )
        val result = FefoEngine.calculateVelocity(sales, today = today)
        assertEquals(0.357, result, 0.0)
    }

    @Test
    fun classifyMovement() {
        assertEquals(Movement.NON_MOVING, FefoEngine.classifyMovement(0.0))
        assertEquals(Movement.NON_MOVING, FefoEngine.classifyMovement(0.1))
        assertEquals(Movement.SLOW_MOVING, FefoEngine.classifyMovement(0.15))
        assertEquals(Movement.SLOW_MOVING, FefoEngine.classifyMovement(0.5))
        assertEquals(Movement.FAST_MOVING, FefoEngine.classifyMovement(1.0))
        assertEquals(Movement.FAST_MOVING, FefoEngine.classifyMovement(2.0))
    }

    @Test
    fun calculateGlobalRisk() {
        // Milk risk 80, Bread risk 100, Rice risk 10
        val batches =
            listOf(
                BatchAnalysis(BatchStatusTier.CRITICAL, 0, "", 80, StockStatus.OK, 0.0, 0.0, Movement.SLOW_MOVING, 0.0, 0.0),
                BatchAnalysis(BatchStatusTier.EXPIRED, 0, "", 100, StockStatus.LOW, 0.0, 0.0, Movement.NON_MOVING, 0.0, 0.0),
                BatchAnalysis(BatchStatusTier.SAFE, 0, "", 10, StockStatus.OK, 0.0, 0.0, Movement.NON_MOVING, 0.0, 0.0),
            )
        val result = FefoEngine.calculateGlobalRisk(batches)
        assertEquals(63, result.score)
        assertEquals("Moderate", result.label)
    }

    // Helper for testing
    private fun LocalDate.plusDays(days: Int): LocalDate = LocalDate.fromEpochDays(this.toEpochDays() + days)
}
