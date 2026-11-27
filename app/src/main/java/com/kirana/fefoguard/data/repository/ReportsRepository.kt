package com.kirana.fefoguard.data.repository

import com.kirana.fefoguard.data.local.FefoGuardDatabase
import com.kirana.fefoguard.data.local.entity.ShopOperationEntity
import com.kirana.fefoguard.domain.engine.ProductEarning
import com.kirana.fefoguard.domain.engine.RefundRecord
import com.kirana.fefoguard.domain.engine.ShopReports
import com.kirana.fefoguard.domain.engine.SlowStock
import com.kirana.fefoguard.domain.engine.TodaySummary
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** Read-only numbers for the owner: end of day, best sellers, dead stock, and the accountant's sales list. */
@Singleton
class ReportsRepository
    @Inject
    constructor(
        private val db: FefoGuardDatabase,
    ) {
        private val zone get() = TimeZone.currentSystemDefault()

        private fun startOfToday(): Instant =
            Clock.System
                .now()
                .toLocalDateTime(zone)
                .date
                .atStartOfDayIn(zone)

        suspend fun today(): TodaySummary {
            val from = startOfToday()
            val to = from.plus(1, DateTimeUnit.DAY, zone)
            // Only today's rows are read. The text-stored times are compared a day early and trimmed exactly in ShopReports.
            val slack = from.minus(1, DateTimeUnit.DAY, zone)
            return ShopReports.today(
                db.checkoutDao().since(from.toEpochMilliseconds()),
                db.salesHistoryDao().salesSince(slack),
                db.stockAdjustmentDao().adjustmentsSince(slack),
                db.productDao().searchProducts("").first(),
                from,
                to,
                refundRecords(db.shopStateDao().refundsSince(from.toEpochMilliseconds())),
            )
        }

        /** Refunds from the shop's operation log. A row that cannot be read is skipped rather than hiding the day. */
        private fun refundRecords(operations: List<ShopOperationEntity>): List<RefundRecord> =
            operations
                .filter { it.kind == "REFUND" }
                .mapNotNull { operation ->
                    runCatching {
                        val data = JSONObject(operation.payload)
                        RefundRecord(
                            billId = data.getString("billId"),
                            amountPaise = data.getLong("amountPaise"),
                            paymentMethod = data.getString("paymentMethod"),
                            createdAt = operation.createdAt,
                        )
                    }.getOrNull()
                }

        suspend fun topEarners(days: Int = 30): List<ProductEarning> {
            val to = Clock.System.now()
            return ShopReports.topEarners(
                db.salesHistoryDao().salesSince(to.minus(days + 1, DateTimeUnit.DAY, zone)),
                db.productDao().searchProducts("").first(),
                to.minus(days, DateTimeUnit.DAY, zone),
                to.plus(1, DateTimeUnit.DAY, zone),
            )
        }

        suspend fun slowStock(days: Int = 30): List<SlowStock> {
            val now = Clock.System.now()
            val batches = db.inventoryBatchDao().getAllActiveBatchesWithProduct().first()
            val today = now.toLocalDateTime(zone).date
            val live = batches.filter { it.batch.quantity > 0 && it.batch.expiryDate > today }
            return ShopReports.slowStock(
                products = live.map { it.product }.distinctBy { it.id },
                onHand = live.groupBy { it.product.id }.mapValues { (_, rows) -> rows.sumOf { it.batch.quantity } },
                costPerUnit =
                    live.groupBy { it.product.id }.mapValues { (_, rows) ->
                        val units = rows.sumOf { it.batch.quantity.toDouble() }
                        rows.sumOf { it.batch.quantity * (it.batch.unitCost ?: it.product.purchasePrice) } / units
                    },
                sales = db.salesHistoryDao().salesSince(now.minus(days + 1, DateTimeUnit.DAY, zone)),
                from = now.minus(days, DateTimeUnit.DAY, zone),
                to = now.plus(1, DateTimeUnit.DAY, zone),
                days = days,
            )
        }

        /** Bills from the start of day [daysBack] days ago until now. A plain sales list, not a tax invoice. */
        suspend fun salesCsv(daysBack: Int): String {
            val to = Clock.System.now().plus(1, DateTimeUnit.DAY, zone)
            val from = startOfToday().minus(daysBack, DateTimeUnit.DAY, zone)
            val format = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
            return ShopReports.salesCsv(db.checkoutDao().observeAll().first(), from, to, refundRecords(db.shopStateDao().operations())) { format.format(java.util.Date(it)) }
        }

        suspend fun priceChanges() = db.settingsDao().priceChanges()
    }
