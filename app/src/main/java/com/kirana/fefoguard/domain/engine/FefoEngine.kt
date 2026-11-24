package com.kirana.fefoguard.domain.engine

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.todayIn
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object FefoEngine {
    const val FAST_MOVING_THRESHOLD = 1.0
    const val SLOW_MOVING_THRESHOLD = 0.15
    const val VELOCITY_WINDOW_DAYS = 14

    fun calculateVelocity(
        saleDatesAndQty: List<Pair<LocalDate, Int>>,
        windowDays: Int = VELOCITY_WINDOW_DAYS,
        createdAt: LocalDate? = null,
        today: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault()),
    ): Double {
        require(windowDays > 0)
        if (saleDatesAndQty.isEmpty()) {
            return 0.0
        }

        var actualWindow = windowDays
        if (createdAt != null) {
            val elapsedDays = createdAt.daysUntil(today) + 1
            actualWindow = min(windowDays, max(1, elapsedDays))
        }

        val cutoff = today.minusDays(actualWindow - 1)
        var totalQty = 0L
        for ((dateVal, qty) in saleDatesAndQty) {
            if (dateVal >= cutoff && dateVal <= today && qty > 0) {
                totalQty += qty
            }
        }

        return (totalQty.toDouble() / actualWindow).round(3)
    }

    private fun LocalDate.minusDays(days: Int): LocalDate {
        // Simple but might not be entirely correct without DatePeriod (kotlinx-datetime doesn't have minus(Int) easily on LocalDate without DatePeriod)
        // Let's implement this properly using EpochDays.
        val epoch = this.toEpochDays()
        return LocalDate.fromEpochDays(epoch - days)
    }

    private fun Double.round(decimals: Int): Double {
        var multiplier = 1.0
        repeat(decimals) { multiplier *= 10 }
        return kotlin.math.round(this * multiplier) / multiplier
    }

    fun classifyMovement(dailyRate: Double): Movement =
        when {
            dailyRate >= FAST_MOVING_THRESHOLD -> Movement.FAST_MOVING
            dailyRate >= SLOW_MOVING_THRESHOLD -> Movement.SLOW_MOVING
            else -> Movement.NON_MOVING
        }

    fun predictExpectedLoss(
        currentUnits: Int,
        dailyRate: Double,
        daysLeft: Int,
        purchasePrice: Double,
    ): PredictedLoss {
        val safeDaysLeft = max(daysLeft, 0)
        val projectedSold = dailyRate * safeDaysLeft
        val expectedLeftover = max(0.0, currentUnits - projectedSold)
        val expectedLoss = (expectedLeftover * purchasePrice).round(2)
        return PredictedLoss(
            expectedLeftover = expectedLeftover.round(2),
            expectedLoss = expectedLoss,
        )
    }

    fun analyzeBatch(
        expiryDate: LocalDate,
        quantity: Int,
        purchasePrice: Double,
        minStock: Int,
        dailyRate: Double = 0.0,
        today: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault()),
        unitScale: Int = 1,
    ): BatchAnalysis {
        val daysLeft = today.daysUntil(expiryDate)

        val status: BatchStatusTier
        val recommendation: String
        var baseRisk: Int

        when {
            daysLeft <= 0 -> {
                status = BatchStatusTier.EXPIRED
                recommendation = "REMOVE FROM SHELF"
                baseRisk = 100
            }
            daysLeft in 1..3 -> {
                status = BatchStatusTier.CRITICAL
                recommendation = "SELL FIRST"
                baseRisk = 80
            }
            daysLeft in 4..7 -> {
                status = BatchStatusTier.WARNING
                recommendation = "MONITOR (Move to front)"
                baseRisk = 50
            }
            else -> {
                status = BatchStatusTier.SAFE
                recommendation = "SAFE STOCK"
                baseRisk = 10
            }
        }

        var stockStatus = StockStatus.OK
        if (quantity < minStock) {
            baseRisk = min(100, baseRisk + 20)
            stockStatus = StockStatus.LOW
        }

        var potentialLoss = 0.0
        if (status == BatchStatusTier.EXPIRED || status == BatchStatusTier.CRITICAL) {
            potentialLoss = quantity * purchasePrice
        }

        val movement = classifyMovement(dailyRate / unitScale)
        val prediction = predictExpectedLoss(quantity, dailyRate, daysLeft, purchasePrice)

        if (movement == Movement.NON_MOVING &&
            (status == BatchStatusTier.CRITICAL || status == BatchStatusTier.WARNING)
        ) {
            baseRisk = min(100, baseRisk + 10)
        }

        return BatchAnalysis(
            status = status,
            daysLeft = daysLeft,
            recommendation = recommendation,
            riskScore = baseRisk,
            stockStatus = stockStatus,
            potentialLoss = potentialLoss,
            dailyRate = dailyRate,
            movement = movement,
            expectedLeftover = prediction.expectedLeftover,
            expectedLoss = prediction.expectedLoss,
        )
    }

    fun calculateGlobalRisk(batches: List<BatchAnalysis>): GlobalRisk {
        if (batches.isEmpty()) {
            return GlobalRisk(0, "Safe")
        }
        val totalScore = batches.sumOf { it.riskScore }
        val avgScore = totalScore / batches.size

        val label =
            when {
                avgScore >= 75 -> "High Risk"
                avgScore >= 40 -> "Moderate"
                else -> "Healthy"
            }
        return GlobalRisk(avgScore, label)
    }
}
