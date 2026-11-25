package com.kirana.fefoguard.domain.repository

import com.kirana.fefoguard.data.local.entity.BatchWithProduct
import com.kirana.fefoguard.data.local.entity.InventoryBatchEntity
import com.kirana.fefoguard.data.local.entity.ProductEntity
import com.kirana.fefoguard.domain.engine.BatchAnalysis
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

data class RestockItem(
    val product: ProductEntity,
    val usableQuantity: Int,
)

data class DashboardData(
    val batchesWithAnalysis: List<Pair<BatchWithProduct, BatchAnalysis>>,
    val restock: List<RestockItem> = emptyList(),
    val productCount: Int = 0,
)

interface ProductRepository {
    suspend fun receive(
        operationId: String,
        product: ProductEntity,
        quantity: Int,
        cost: Double,
        expiry: LocalDate,
    ) {
        addProductManual(product, quantity, cost, expiry)
    }

    suspend fun updateProduct(product: ProductEntity) {
        error("Product editing is unavailable")
    }

    suspend fun bulkChangePrices(
        category: String?,
        percent: Double,
        reason: String,
    ): Int = error("Bulk price update is unavailable")

    suspend fun setFavourite(
        productId: Long,
        favourite: Boolean,
    ) {
        error("Favourites are unavailable")
    }

    suspend fun addProductManual(
        product: ProductEntity,
        quantity: Int,
        purchasePrice: Double,
        expiryDate: LocalDate,
    )

    suspend fun getProductByBarcode(barcode: String): ProductEntity?

    suspend fun getProductById(id: Long): ProductEntity?

    fun getDistinctCategories(): Flow<List<String>>

    fun getHomeDashboardData(): Flow<DashboardData>

    fun searchProducts(query: String): Flow<List<ProductEntity>>

    fun getActiveBatchesWithProduct(): Flow<List<BatchWithProduct>>

    suspend fun getActiveBatchesForProduct(productId: Long): List<InventoryBatchEntity>
}
