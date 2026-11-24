package com.kirana.fefoguard.domain.engine

enum class BatchStatusTier {
    EXPIRED,
    CRITICAL,
    WARNING,
    SAFE,
}

enum class StockStatus {
    OK,
    LOW,
}

enum class Movement {
    FAST_MOVING,
    SLOW_MOVING,
    NON_MOVING,
}

data class PredictedLoss(
    val expectedLeftover: Double,
    val expectedLoss: Double,
)

data class BatchAnalysis(
    val status: BatchStatusTier,
    val daysLeft: Int,
    val recommendation: String,
    val riskScore: Int,
    val stockStatus: StockStatus,
    val potentialLoss: Double,
    val dailyRate: Double,
    val movement: Movement,
    val expectedLeftover: Double,
    val expectedLoss: Double,
)

data class GlobalRisk(
    val score: Int,
    val label: String,
)
