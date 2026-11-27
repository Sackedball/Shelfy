package com.kirana.fefoguard.data.repository

import androidx.room.withTransaction
import com.kirana.fefoguard.data.local.FefoGuardDatabase
import com.kirana.fefoguard.data.local.entity.CashMovementEntity
import com.kirana.fefoguard.data.local.entity.CashSessionEntity
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject

data class CashSummary(
    val sessions: List<CashSessionEntity>,
    val movements: List<CashMovementEntity>,
    val cashSales: Long,
    val upiSales: Long,
    val cashRefunds: Long,
    val expected: Long,
)

class CashRepository
    @Inject
    constructor(
        private val db: FefoGuardDatabase,
    ) {
        fun observe() =
            combine(
                db.shopStateDao().sessions(),
                db.shopStateDao().movements(),
                db.checkoutDao().observeAll(),
                db.shopStateDao().observeOperations(),
            ) {
                sessions,
                movements,
                bills,
                operations,
                ->
                val open = sessions.firstOrNull { it.closedAt == null }
                val cash =
                    bills
                        .filter {
                            open != null && it.cashSessionId == open.id && it.voidedAt == null
                        }.sumOf { it.cashPaise }
                val upi =
                    bills
                        .filter {
                            open != null && it.cashSessionId == open.id && it.voidedAt == null
                        }.sumOf { it.upiPaise }
                val refunds =
                    operations
                        .filter {
                            it.kind == "REFUND" && open != null &&
                                JSONObject(it.payload).optString("sessionId") == open.id
                        }.sumOf {
                            val data = JSONObject(it.payload)
                            if (data.getString("paymentMethod") ==
                                "CASH"
                            ) {
                                data.getLong("amountPaise")
                            } else {
                                0L
                            }
                        }
                val moves = movements.filter { it.sessionId == open?.id }.sumOf { it.amountPaise }
                CashSummary(
                    sessions,
                    movements,
                    cash,
                    upi,
                    refunds,
                    (open?.openingPaise ?: 0) + cash - refunds + moves,
                )
            }

        suspend fun open(amount: Long) =
            db.withTransaction {
                require(amount >= 0)
                require(db.shopStateDao().openSession() == null) { "A cash session is already open" }
                db.shopStateDao().saveSession(
                    CashSessionEntity(UUID.randomUUID().toString(), System.currentTimeMillis(), amount),
                )
            }

        suspend fun move(
            amount: Long,
            note: String,
        ) = db.withTransaction {
            require(amount != 0L && note.isNotBlank()) { "Enter amount and reason" }
            val session =
                requireNotNull(db.shopStateDao().openSession()) { "Open a cash session first" }
            db.shopStateDao().insertMovement(
                CashMovementEntity(
                    UUID.randomUUID().toString(),
                    session.id,
                    amount,
                    note.trim(),
                    System.currentTimeMillis(),
                ),
            )
        }

        suspend fun close(counted: Long) =
            db.withTransaction {
                require(counted >= 0)
                val session = requireNotNull(db.shopStateDao().openSession())
                val summary = observe().first()
                db.shopStateDao().saveSession(
                    session.copy(
                        closedAt = System.currentTimeMillis(),
                        countedPaise = counted,
                        expectedPaise = summary.expected,
                    ),
                )
            }
    }
