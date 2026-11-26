package com.kirana.fefoguard.data.repository

import androidx.room.withTransaction
import com.kirana.fefoguard.data.local.FefoGuardDatabase
import com.kirana.fefoguard.data.local.entity.BatchStatus
import com.kirana.fefoguard.data.local.entity.BatchWithProduct
import com.kirana.fefoguard.data.local.entity.InventoryBatchEntity
import com.kirana.fefoguard.data.local.entity.ProductEntity
import com.kirana.fefoguard.domain.engine.FefoEngine
import com.kirana.fefoguard.domain.engine.ForecastLot
import com.kirana.fefoguard.domain.engine.Money
import com.kirana.fefoguard.domain.engine.StockStatus
import com.kirana.fefoguard.domain.engine.forecastLots
import com.kirana.fefoguard.domain.repository.DashboardData
import com.kirana.fefoguard.domain.repository.ProductRepository
import com.kirana.fefoguard.domain.repository.RestockItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn

class ProductRepositoryImpl(
    private val db: FefoGuardDatabase,
) : ProductRepository {
    override suspend fun receive(
        operationId: String,
        product: ProductEntity,
        quantity: Int,
        cost: Double,
        expiry: LocalDate,
    ) = db.withTransaction {
        val key = listOf(product, quantity, cost, expiry).joinToString("|")
        val previous = db.shopStateDao().operation(operationId)
        if (previous != null) {
            require(previous.kind == "RECEIVE" && previous.request == key) {
                "Receiving identifier already used"
            }
        } else {
            addProductManual(product, quantity, cost, expiry)
            db.shopStateDao().insertOperation(
                com.kirana.fefoguard.data.local.entity.ShopOperationEntity(
                    operationId,
                    "RECEIVE",
                    key,
                    "{}",
                    System.currentTimeMillis(),
                ),
            )
            Unit
        }
    }

    override suspend fun updateProduct(product: ProductEntity) =
        db.withTransaction {
            val old = requireNotNull(db.productDao().getProductById(product.id))
            require(product.unit == old.unit && product.expiryTracked == old.expiryTracked) {
                "Existing stock units and expiry mode cannot change"
            }
            require(
                product.barcode.isNotBlank() && product.name.isNotBlank() && product.minStockLevel >= 0 &&
                    product.packSize > 0,
            )
            Money.paise(product.sellingPrice)
            require(
                db.productDao().getProductByBarcode(product.barcode)?.id.let {
                    it == null ||
                        it == product.id
                },
            ) { "Barcode already exists" }
            require(product.mrp == null || product.mrp > 0.0) { "MRP must be above zero" }
            product.mrp?.let { Money.paise(it) }
            check(db.productDao().updateProduct(product) == 1)
            if (old.sellingPrice != product.sellingPrice) {
                db.settingsDao().insertPriceChange(
                    com.kirana.fefoguard.data.local.entity.PriceChangeEntity(
                        productId = product.id,
                        oldPrice = old.sellingPrice,
                        newPrice = product.sellingPrice,
                        changedAt = System.currentTimeMillis(),
                        reason = "Edited",
                    ),
                )
            }
        }

    /**
     * Changes selling prices for many products at once. [percent] raises (or lowers) each price,
     * rounding to the nearest paisa; products with an MRP are capped at it. Returns how many changed.
     */
    override suspend fun bulkChangePrices(
        category: String?,
        percent: Double,
        reason: String,
    ): Int =
        db.withTransaction {
            require(percent.isFinite() && percent > -90.0 && percent < 500.0 && percent != 0.0) { "Enter a percent that is not zero" }
            val factor =
                java.math.BigDecimal.ONE +
                    java.math.BigDecimal
                        .valueOf(percent)
                        .movePointLeft(2)
            var changed = 0
            db.productDao().searchProducts("").first().filter { category == null || it.category == category }.forEach { product ->
                var next =
                    java.math
                        .BigDecimal(Money.paise(product.sellingPrice))
                        .multiply(factor)
                        .setScale(0, java.math.RoundingMode.HALF_UP)
                        .toLong()
                product.mrp?.let { next = minOf(next, Money.paise(it)) }
                if (next > 0 && next != Money.paise(product.sellingPrice)) {
                    db.productDao().updateProduct(product.copy(sellingPrice = Money.rupees(next)))
                    db.settingsDao().insertPriceChange(
                        com.kirana.fefoguard.data.local.entity.PriceChangeEntity(
                            productId = product.id,
                            oldPrice = product.sellingPrice,
                            newPrice = Money.rupees(next),
                            changedAt = System.currentTimeMillis(),
                            reason = reason,
                        ),
                    )
                    changed++
                }
            }
            changed
        }

    override suspend fun setFavourite(
        productId: Long,
        favourite: Boolean,
    ) {
        db.withTransaction {
            val product = requireNotNull(db.productDao().getProductById(productId))
            db.productDao().updateProduct(product.copy(favourite = favourite))
        }
    }

    override suspend fun addProductManual(
        product: ProductEntity,
        quantity: Int,
        purchasePrice: Double,
        expiryDate: LocalDate,
    ) {
        require(quantity > 0) { "Quantity must be positive" }
        require(product.unit in listOf("PCS", "KG", "L") && product.packSize > 0)
        require(product.name.isNotBlank() && product.barcode.isNotBlank()) {
            "Name and barcode are required"
        }
        require(product.minStockLevel >= 0) { "Minimum stock cannot be negative" }
        require(expiryDate > Clock.System.todayIn(TimeZone.currentSystemDefault())) {
            "Choose an expiry date after today"
        }
        Money.paise(purchasePrice)
        Money.paise(product.sellingPrice)
        db.withTransaction {
            val existing = db.productDao().getProductByBarcode(product.barcode)
            val productId =
                if (existing == null) {
                    require(product.id == 0L) { "Product no longer exists" }
                    db.productDao().insertProduct(
                        product.copy(purchasePrice = Money.rupees(Money.paise(purchasePrice))),
                    )
                } else {
                    require(product.id == existing.id) { "Barcode already exists; use restock" }
                    existing.id
                }
            db.inventoryBatchDao().insertBatch(
                InventoryBatchEntity(
                    productId = productId,
                    quantity = quantity,
                    expiryDate = expiryDate,
                    status = BatchStatus.ACTIVE,
                    unitCost = Money.rupees(Money.paise(purchasePrice)),
                ),
            )
        }
    }

    override suspend fun getProductByBarcode(barcode: String): ProductEntity? = db.productDao().getProductByBarcode(barcode)

    override suspend fun getProductById(id: Long): ProductEntity? = db.productDao().getProductById(id)

    override fun searchProducts(query: String): Flow<List<ProductEntity>> = db.productDao().searchProducts(query)

    override fun getDistinctCategories(): Flow<List<String>> = db.productDao().getDistinctCategories()

    override fun getHomeDashboardData(): Flow<DashboardData> =
        combine(
            db.inventoryBatchDao().getAllActiveBatchesWithProduct(),
            db.productDao().searchProducts(""),
            // Velocity only looks back 14 days, and "dormant" only needs to know who sold at all in a quarter.
            windowedByDay(FefoEngine.VELOCITY_WINDOW_DAYS) { db.salesHistoryDao().observeSalesSince(it) },
            windowedByDay(DORMANT_DAYS) { db.salesHistoryDao().observeProductIdsSoldSince(it) },
            timeFlow(),
        ) { batches, products, sales, soldIds, _ ->
            val now = Clock.System.now()
            val today = now.toLocalDateTime(TimeZone.currentSystemDefault()).date
            val stock =
                batches
                    .filter { it.batch.expiryDate > today }
                    .groupBy { it.product.id }
                    .mapValues { (_, lots) ->
                        lots
                            .sumOf {
                                it.batch.quantity.toLong()
                            }.coerceAtMost(Int.MAX_VALUE.toLong())
                            .toInt()
                    }
            // A product with nothing on the shelf that has not sold for a quarter is dormant, not "needs restocking":
            // without this, every item the shop stopped carrying would be flagged on Home forever.
            val soldRecently = soldIds.toSet()
            val restock =
                products
                    .map { RestockItem(it, stock[it.id] ?: 0) }
                    .filter { it.usableQuantity < it.product.minStockLevel || it.usableQuantity == 0 }
                    .filterNot { it.usableQuantity == 0 && it.product.id !in soldRecently }
            val salesByProduct = sales.filter { it.quantity > 0 && it.soldAt <= now }.groupBy { it.productId }
            val analyzed =
                batches.groupBy { it.product.id }.flatMap { (productId, lots) ->
                    val rate =
                        FefoEngine.calculateVelocity(
                            salesByProduct[productId]
                                .orEmpty()
                                .map {
                                    it.soldAt.toLocalDateTime(TimeZone.currentSystemDefault()).date to
                                        it.quantity
                                },
                            today = today,
                        )
                    val forecasts =
                        forecastLots(
                            lots.map {
                                ForecastLot(
                                    it.batch.id,
                                    it.batch.quantity,
                                    today.daysUntil(it.batch.expiryDate),
                                    (it.batch.unitCost ?: it.product.purchasePrice) / it.product.unitScale,
                                )
                            },
                            rate,
                        )
                    lots.map { lot ->
                        val result =
                            FefoEngine.analyzeBatch(
                                lot.batch.expiryDate,
                                lot.batch.quantity,
                                (lot.batch.unitCost ?: lot.product.purchasePrice) / lot.product.unitScale,
                                lot.product.minStockLevel,
                                rate,
                                today,
                                lot.product.unitScale,
                            )
                        val forecast = forecasts.getValue(lot.batch.id)
                        lot to
                            result.copy(
                                expectedLeftover = if (lot.product.expiryTracked) forecast.expectedLeftover else 0.0,
                                expectedLoss = if (lot.product.expiryTracked) forecast.expectedLoss else 0.0,
                                stockStatus =
                                    if ((stock[productId] ?: 0) <
                                        lot.product.minStockLevel
                                    ) {
                                        StockStatus.LOW
                                    } else {
                                        StockStatus.OK
                                    },
                            )
                    }
                }
            DashboardData(analyzed, restock, products.size)
        }

    override fun getActiveBatchesWithProduct(): Flow<List<BatchWithProduct>> = db.inventoryBatchDao().getAllActiveBatchesWithProduct()

    override suspend fun getActiveBatchesForProduct(productId: Long): List<InventoryBatchEntity> = db.inventoryBatchDao().getActiveBatchesForProduct(productId)
}

private const val DORMANT_DAYS = 90
