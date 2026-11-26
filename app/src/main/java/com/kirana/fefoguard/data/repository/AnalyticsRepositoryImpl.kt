package com.kirana.fefoguard.data.repository

import com.kirana.fefoguard.data.local.FefoGuardDatabase
import com.kirana.fefoguard.data.local.entity.AdjustmentType
import com.kirana.fefoguard.domain.engine.FefoEngine
import com.kirana.fefoguard.domain.repository.AnalyticsRepository
import com.kirana.fefoguard.domain.repository.CategoryLoss
import com.kirana.fefoguard.domain.repository.FefoImpact
import com.kirana.fefoguard.domain.repository.MonthlyLoss
import com.kirana.fefoguard.domain.repository.RevenueProfitSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.days

class AnalyticsRepositoryImpl(
    private val db: FefoGuardDatabase,
) : AnalyticsRepository {
    override fun getRevenueProfitSummary(days: Int): Flow<RevenueProfitSummary> =
        combine(windowedByDay(days) { db.salesHistoryDao().observeSalesSince(it) }, timeFlow()) { sales, _ ->
            val now = Clock.System.now()
            val recent = sales.filter { it.soldAt >= now.minus(days.days) && it.soldAt <= now }
            RevenueProfitSummary(
                recent.sumOf {
                    it.revenue
                },
                recent.sumOf { it.profit },
                recent.sumOf { it.quantity },
                recent
                    .groupBy {
                        it.unit
                    }.entries
                    .sortedBy { it.key }
                    .joinToString(" · ") { (unit, rows) ->
                        java.math.BigDecimal
                            .valueOf(
                                rows.sumOf {
                                    it.quantity.toLong()
                                },
                            ).divide(
                                java.math.BigDecimal.valueOf(rows.first().unitScale.toLong()),
                            ).stripTrailingZeros()
                            .toPlainString() +
                            " " +
                            unit
                    },
            )
        }

    override fun getCategoryLossBreakdown(): Flow<List<CategoryLoss>> =
        combine(db.inventoryBatchDao().getAllActiveBatchesWithProduct(), timeFlow()) { batches, _ ->
            val now = Clock.System.now()
            val today = now.toLocalDateTime(TimeZone.currentSystemDefault()).date
            batches
                .groupBy { it.product.category }
                .map { (category, lots) ->
                    CategoryLoss(
                        category,
                        lots.sumOf {
                            FefoEngine
                                .analyzeBatch(
                                    it.batch.expiryDate,
                                    it.batch.quantity,
                                    (it.batch.unitCost ?: it.product.purchasePrice) / it.product.unitScale,
                                    it.product.minStockLevel,
                                    today = today,
                                ).potentialLoss
                        },
                    )
                }.filter { it.potentialLoss > 0.0 }
        }

    private fun losses(days: Int) =
        combine(
            windowedByDay(days) { db.stockAdjustmentDao().observeAdjustmentsSince(it) },
            db.productDao().searchProducts(""),
            timeFlow(),
        ) { adjustments, products, _ ->
            val now = Clock.System.now()
            val prices = products.associate { it.id to it.purchasePrice }
            adjustments
                .filter {
                    it.adjustmentType != AdjustmentType.RETURNED && it.adjustedAt >= now.minus(days.days) &&
                        it.adjustedAt <= now
                }.map { it to (it.quantity * (it.unitCost ?: prices[it.productId] ?: 0.0) / it.unitScale) }
        }

    override fun getMonthlyLossTrend(): Flow<List<MonthlyLoss>> =
        losses(365).map { entries ->
            entries
                .groupBy {
                    it.first.adjustedAt
                        .toLocalDateTime(
                            TimeZone.currentSystemDefault(),
                        ).date
                        .toString()
                        .take(7)
                }.map { (month, rows) ->
                    MonthlyLoss(month, rows.sumOf { it.second })
                }.sortedBy { it.monthYear }
        }

    override fun getBeforeVsFefoImpact(): Flow<FefoImpact> =
        losses(30).map { entries ->
            // No counterfactual can be inferred from actual write-offs alone.
            FefoImpact(0.0, entries.sumOf { it.second }, 0.0, true)
        }
}
