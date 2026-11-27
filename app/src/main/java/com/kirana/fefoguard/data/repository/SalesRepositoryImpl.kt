package com.kirana.fefoguard.data.repository

import androidx.room.withTransaction
import com.kirana.fefoguard.data.local.FefoGuardDatabase
import com.kirana.fefoguard.data.local.entity.AdjustmentType
import com.kirana.fefoguard.data.local.entity.BatchStatus
import com.kirana.fefoguard.data.local.entity.CheckoutEntity
import com.kirana.fefoguard.data.local.entity.InventoryBatchEntity
import com.kirana.fefoguard.data.local.entity.SaleSource
import com.kirana.fefoguard.data.local.entity.SalesHistoryEntity
import com.kirana.fefoguard.data.local.entity.ShopOperationEntity
import com.kirana.fefoguard.data.local.entity.StockAdjustmentEntity
import com.kirana.fefoguard.domain.cart.CartLineItem
import com.kirana.fefoguard.domain.engine.BillMath
import com.kirana.fefoguard.domain.engine.Money
import com.kirana.fefoguard.domain.engine.Quantities
import com.kirana.fefoguard.domain.engine.RoundOffMode
import com.kirana.fefoguard.domain.repository.CheckoutQuote
import com.kirana.fefoguard.domain.repository.RefundRequest
import com.kirana.fefoguard.domain.repository.ReturnDisposition
import com.kirana.fefoguard.domain.repository.SalesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.json.JSONObject
import java.util.UUID

class SalesRepositoryImpl(
    private val db: FefoGuardDatabase,
) : SalesRepository {
    private suspend fun lots(
        productId: Long,
        quantity: Int,
    ): List<Pair<InventoryBatchEntity, Int>> {
        require(quantity > 0) { "Quantity must be positive" }
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val rows =
            db
                .inventoryBatchDao()
                .getActiveBatchesForProduct(productId)
                .filter {
                    it.quantity > 0 && it.expiryDate > today
                }.sortedWith(compareBy({ it.expiryDate }, { it.id }))
        require(
            rows.sumOf {
                it.quantity.toLong()
            } >= quantity,
        ) { "Not enough unexpired stock; review the cart" }
        var left = quantity
        return rows.mapNotNull { row ->
            if (left ==
                0
            ) {
                null
            } else {
                (row to minOf(left, row.quantity)).also { left -= it.second }
            }
        }
    }

    private suspend fun roundOffMode(): RoundOffMode = SettingsRepository.parseRoundOff(db.settingsDao().get(SettingsRepository.ROUND_OFF))

    override suspend fun quote(
        items: List<CartLineItem>,
        billDiscountPaise: Long,
    ): CheckoutQuote =
        db.withTransaction {
            require(items.isNotEmpty()) { "Cart is empty" }
            var cost = 0L
            items.forEach { item ->
                val product = requireNotNull(db.productDao().getProductById(item.product.id))
                require(
                    product.sellingPrice == item.product.sellingPrice &&
                        product.unit == item.product.unit,
                ) {
                    "Product price changed; remove and add the product again to review its new price"
                }
                lots(product.id, item.quantity).forEach { (lot, quantity) ->
                    cost =
                        Math.addExact(
                            cost,
                            Money.linePaise(
                                lot.unitCost ?: product.purchasePrice,
                                quantity,
                                product.unitScale,
                            ),
                        )
                }
            }
            val amounts = BillMath.amounts(items.map { it.totalPaise }, billDiscountPaise, roundOffMode())
            CheckoutQuote(amounts.payablePaise, cost, amounts.linesPaise, amounts.billDiscountPaise, amounts.roundOffPaise)
        }

    override suspend fun checkout(
        items: List<CartLineItem>,
        bill: CheckoutEntity,
    ) {
        require(items.isNotEmpty()) { "Cart is empty" }
        require(bill.paymentMethod in listOf("CASH", "UPI_QR", "SPLIT"))
        require(items.map { it.product.id }.distinct().size == items.size)
        val linePaise = items.map { it.totalPaise }
        val amounts = BillMath.amounts(linePaise, bill.billDiscountPaise, roundOffMode())
        val total = amounts.payablePaise
        require(total == bill.totalPaise && amounts.roundOffPaise == bill.roundOffPaise) { "Bill total changed; review the cart" }
        val revenue = BillMath.lineRevenue(linePaise, amounts)
        val cash = if (bill.paymentMethod == "CASH") total else bill.cashPaise
        val upi = if (bill.paymentMethod == "UPI_QR") total else bill.upiPaise
        require(cash >= 0 && upi >= 0 && Math.addExact(cash, upi) == total) {
            "Payments must equal the bill total"
        }
        val request =
            CartCodec.encode(items.sortedBy { it.product.id }) + ":" + bill.paymentMethod + ":" +
                cash +
                ":" +
                upi + ":" + bill.billDiscountPaise + ":" + bill.roundOffPaise
        db.withTransaction {
            db.checkoutDao().get(bill.id)?.let {
                require(it.requestKey == request) {
                    "Checkout identifier belongs to a different bill"
                }
                return@withTransaction
            }
            require(quote(items, bill.billDiscountPaise).lossPaise <= bill.acceptedLossPaise) {
                "Confirm the below-cost clearance before payment"
            }
            items.forEachIndexed { index, item ->
                recordSaleFefo(
                    item.product.id,
                    item.quantity,
                    SaleSource.MANUAL_LIST_SELL,
                    bill.id,
                    item.unitPrice,
                    revenue[index],
                )
            }
            db.checkoutDao().insert(
                bill.copy(
                    requestKey = request,
                    cashPaise = cash,
                    upiPaise = upi,
                    linesJson = CartCodec.encode(items),
                    cashSessionId = db.shopStateDao().openSession()?.id,
                ),
            )
        }
    }

    override suspend fun recordSale(
        productId: Long,
        batchId: Long?,
        quantity: Int,
        sellingPrice: Double,
        purchasePrice: Double,
        source: SaleSource,
    ) {
        if (batchId ==
            null
        ) {
            recordSaleFefo(productId, quantity, source, null, sellingPrice)
            return
        }
        db.withTransaction {
            require(quantity > 0)
            val batch = requireNotNull(db.inventoryBatchDao().getBatchById(batchId))
            require(batch.productId == productId && batch.status == BatchStatus.ACTIVE)
            require(batch.expiryDate > Clock.System.todayIn(TimeZone.currentSystemDefault())) {
                "Expired stock cannot be sold"
            }
            require(batch.quantity >= quantity)
            val product = requireNotNull(db.productDao().getProductById(productId))
            deduct(batch, quantity)
            insertSale(
                productId,
                batch,
                quantity,
                sellingPrice,
                batch.unitCost ?: product.purchasePrice,
                product.unitScale,
                product.unit,
                source,
                null,
                Money.linePaise(
                    sellingPrice,
                    quantity,
                    product.unitScale,
                ),
            )
        }
    }

    override suspend fun recordSaleFefo(
        productId: Long,
        quantity: Int,
        source: SaleSource,
        checkoutId: String?,
        priceOverride: Double?,
        lineRevenuePaise: Long?,
    ) {
        db.withTransaction {
            val product = requireNotNull(db.productDao().getProductById(productId))
            val price = priceOverride ?: product.sellingPrice
            var allocated = 0
            for ((batch, sold) in lots(productId, quantity)) {
                val revenue =
                    if (lineRevenuePaise == null) {
                        Money.linePaise(price, allocated + sold, product.unitScale) -
                            Money.linePaise(price, allocated, product.unitScale)
                    } else {
                        Quantities.proportional(lineRevenuePaise, allocated + sold, quantity) -
                            Quantities.proportional(lineRevenuePaise, allocated, quantity)
                    }
                allocated += sold
                deduct(batch, sold)
                insertSale(
                    productId,
                    batch,
                    sold,
                    price,
                    batch.unitCost ?: product.purchasePrice,
                    product.unitScale,
                    product.unit,
                    source,
                    checkoutId,
                    revenue,
                )
            }
        }
    }

    private suspend fun deduct(
        batch: InventoryBatchEntity,
        quantity: Int,
    ) {
        val changed =
            if (batch.quantity ==
                quantity
            ) {
                db.inventoryBatchDao().zeroBatchQuantity(batch.id, BatchStatus.SOLD_OUT)
            } else {
                db.inventoryBatchDao().decrementBatchQuantity(batch.id, quantity)
            }
        check(changed == 1) { "Stock changed; retry checkout" }
    }

    private suspend fun insertSale(
        productId: Long,
        batch: InventoryBatchEntity,
        quantity: Int,
        price: Double,
        cost: Double,
        scale: Int,
        unit: String,
        source: SaleSource,
        checkoutId: String?,
        revenue: Long,
    ) {
        val costPaise = Money.linePaise(cost, quantity, scale)
        db.salesHistoryDao().insertSale(
            SalesHistoryEntity(
                productId = productId,
                batchId = batch.id,
                quantity = quantity,
                sellingPrice = price,
                purchasePrice = cost,
                revenue =
                    Money.rupees(
                        revenue,
                    ),
                profit = Money.rupees(revenue - costPaise),
                soldAt = Clock.System.now(),
                source = source,
                checkoutId = checkoutId,
                unitScale = scale,
                unit = unit,
            ),
        )
    }

    override suspend fun voidBill(
        billId: String,
        reason: String,
    ) {
        require(reason.isNotBlank()) { "Enter a reason for cancelling the bill" }
        db.withTransaction {
            val bill = requireNotNull(db.checkoutDao().get(billId)) { "Bill not found" }
            require(bill.voidedAt == null) { "This bill is already cancelled" }
            val windowMillis = SettingsRepository.parseVoidWindow(db.settingsDao().get(SettingsRepository.VOID_WINDOW)) * 60_000L
            require(System.currentTimeMillis() - bill.createdAt <= windowMillis) { "This bill is too old to cancel; use Refund" }
            val rows = db.salesHistoryDao().forBill(billId)
            require(rows.isNotEmpty() && rows.none { it.originalSaleId != null }) { "This bill already has refunds; use Refund" }
            val now = Clock.System.now()
            val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
            rows.forEach { sale ->
                val batch = sale.batchId?.let { db.inventoryBatchDao().getBatchById(it) }
                if (batch != null && batch.expiryDate > today) {
                    db.inventoryBatchDao().incrementQuantity(batch.id, sale.quantity)
                } else {
                    db.stockAdjustmentDao().insertAdjustment(
                        StockAdjustmentEntity(
                            productId = sale.productId,
                            batchId = sale.batchId,
                            adjustmentType = AdjustmentType.DAMAGED,
                            quantity = sale.quantity,
                            notes = "Cancelled bill " + billId + ": batch no longer sellable",
                            adjustedAt = now,
                            unitCost = sale.purchasePrice,
                            unitScale = sale.unitScale,
                            unit = sale.unit,
                        ),
                    )
                }
                db.salesHistoryDao().insertSale(
                    sale.copy(
                        id = 0,
                        quantity = -sale.quantity,
                        revenue = -sale.revenue,
                        profit = -sale.profit,
                        soldAt = now,
                        originalSaleId = sale.id,
                    ),
                )
            }
            check(db.checkoutDao().markVoid(billId, System.currentTimeMillis(), reason.trim()) == 1)
            // A bill from an earlier till session was already counted there; take its cash back out of today's till.
            val open = db.shopStateDao().openSession()
            if (open != null && bill.cashSessionId != open.id && bill.cashPaise > 0) {
                db.shopStateDao().insertMovement(
                    com.kirana.fefoguard.data.local.entity.CashMovementEntity(
                        UUID.randomUUID().toString(),
                        open.id,
                        -bill.cashPaise,
                        "Cancelled bill " + billId,
                        System.currentTimeMillis(),
                    ),
                )
            }
        }
    }

    override suspend fun refund(
        saleId: Long,
        quantity: Int,
    ) = refund(
        RefundRequest(UUID.randomUUID().toString(), saleId, quantity, ReturnDisposition.RESTOCK),
    )

    override suspend fun refund(request: RefundRequest) {
        require(request.quantity > 0)
        require(request.paymentMethod in listOf("CASH", "UPI_QR"))
        db.withTransaction {
            val key =
                listOf(
                    request.saleId,
                    request.quantity,
                    request.disposition,
                    request.paymentMethod,
                ).joinToString(":")
            db.shopStateDao().operation(request.operationId)?.let {
                require(it.kind == "REFUND" && it.request == key) {
                    "Operation identifier already used"
                }
                return@withTransaction
            }
            val sale = requireNotNull(db.salesHistoryDao().getById(request.saleId))
            require(sale.quantity > 0 && sale.originalSaleId == null)
            val returned = db.salesHistoryDao().returnedQuantity(sale.id)
            require(request.quantity <= sale.quantity - returned) {
                "Return exceeds unrefunded quantity"
            }
            val batch = sale.batchId?.let { db.inventoryBatchDao().getBatchById(it) }
            val revenuePaise = Money.paise(sale.revenue)
            val originalCost = Money.paise(sale.revenue - sale.profit)

            fun delta(total: Long) =
                Quantities.proportional(total, returned + request.quantity, sale.quantity) -
                    Quantities.proportional(total, returned, sale.quantity)
            val refund = delta(revenuePaise)
            val cost =
                if (request.disposition ==
                    ReturnDisposition.MONEY_ONLY
                ) {
                    0
                } else {
                    delta(originalCost)
                }
            if (request.disposition == ReturnDisposition.RESTOCK) {
                requireNotNull(batch) { "Original lot is unavailable" }
                require(batch.expiryDate > Clock.System.todayIn(TimeZone.currentSystemDefault())) {
                    "Expired returns must be discarded"
                }
                require(batch.quantity.toLong() + request.quantity <= Int.MAX_VALUE)
                db.inventoryBatchDao().incrementQuantity(batch.id, request.quantity)
            }
            if (request.disposition == ReturnDisposition.DISCARD) {
                db.stockAdjustmentDao().insertAdjustment(
                    StockAdjustmentEntity(
                        productId = sale.productId,
                        batchId = sale.batchId,
                        adjustmentType = AdjustmentType.DAMAGED,
                        quantity = request.quantity,
                        notes =
                            "Refund " + request.operationId,
                        adjustedAt = Clock.System.now(),
                        unitCost =
                            if (request.quantity >
                                0
                            ) {
                                Money.rupees(cost) * sale.unitScale / request.quantity
                            } else {
                                0.0
                            },
                        unitScale = sale.unitScale,
                        unit = sale.unit,
                    ),
                )
            }
            db.salesHistoryDao().insertSale(
                sale.copy(
                    id = 0,
                    quantity = -request.quantity,
                    revenue = -Money.rupees(refund),
                    profit = Money.rupees(cost - refund),
                    soldAt = Clock.System.now(),
                    originalSaleId = sale.id,
                ),
            )
            val payload =
                JSONObject()
                    .put("saleId", sale.id)
                    .put("billId", sale.checkoutId ?: "")
                    .put("amountPaise", refund)
                    .put(
                        "sessionId",
                        db.shopStateDao().openSession()?.id ?: JSONObject.NULL,
                    ).put(
                        "paymentMethod",
                        request.paymentMethod,
                    ).put("disposition", request.disposition.name)
                    .toString()
            db.shopStateDao().insertOperation(
                ShopOperationEntity(
                    request.operationId,
                    "REFUND",
                    key,
                    payload,
                    System.currentTimeMillis(),
                ),
            )
        }
    }

    override fun getRecentSales(limit: Int): Flow<List<SalesHistoryEntity>> = db.salesHistoryDao().getRecentSales(limit)

    override suspend fun lastBillLines(): List<CartLineItem> = db.checkoutDao().latestBill()?.let { CartCodec.decode(it.linesJson) } ?: emptyList()
}
