package com.kirana.fefoguard.domain.repository

import kotlinx.coroutines.flow.Flow

data class RevenueProfitSummary(
    val totalRevenue: Double,
    val totalProfit: Double,
    val unitsSold: Int,
    val quantitySummary: String = unitsSold.toString(),
)

data class CategoryLoss(
    val category: String,
    val potentialLoss: Double,
)

data class MonthlyLoss(
    // e.g. "2026-09"
    val monthYear: String,
    val lossAmount: Double,
)

data class FefoImpact(
    val beforeFefoLoss: Double,
    val withFefoLoss: Double,
    val savedAmount: Double,
    // True for estimates; measured losses have no counterfactual savings.
    val isEstimate: Boolean,
)

interface AnalyticsRepository {
    fun getRevenueProfitSummary(days: Int): Flow<RevenueProfitSummary>

    // Grouped by category, sums the potential loss of Expired + Critical items
    fun getCategoryLossBreakdown(): Flow<List<CategoryLoss>>

    // Grouped by month, sums Damaged + Spoiled adjustments value
    fun getMonthlyLossTrend(): Flow<List<MonthlyLoss>>

    fun getBeforeVsFefoImpact(): Flow<FefoImpact>
}
