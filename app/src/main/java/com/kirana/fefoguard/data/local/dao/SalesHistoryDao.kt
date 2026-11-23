package com.kirana.fefoguard.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kirana.fefoguard.data.local.entity.SalesHistoryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

@Dao
interface SalesHistoryDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSale(sale: SalesHistoryEntity): Long

    @Query("SELECT * FROM sales_history WHERE id = :id")
    suspend fun getById(id: Long): SalesHistoryEntity?

    @Query("SELECT * FROM sales_history WHERE checkoutId = :billId ORDER BY id ASC")
    suspend fun forBill(billId: String): List<SalesHistoryEntity>

    @Query("SELECT COALESCE(-SUM(quantity), 0) FROM sales_history WHERE originalSaleId = :saleId")
    suspend fun returnedQuantity(saleId: Long): Int

    @Query("SELECT * FROM sales_history ORDER BY soldAt DESC LIMIT :limit")
    fun getRecentSales(limit: Int): Flow<List<SalesHistoryEntity>>

    /** Sales at or after [since], live. For rolling windows; the caller trims to the exact moment. */
    @Query("SELECT * FROM sales_history WHERE soldAt >= :since ORDER BY soldAt DESC")
    fun observeSalesSince(since: Instant): Flow<List<SalesHistoryEntity>>

    /** Which products have sold at all since [since]; far cheaper than loading the rows. */
    @Query("SELECT DISTINCT productId FROM sales_history WHERE quantity > 0 AND soldAt >= :since")
    fun observeProductIdsSoldSince(since: Instant): Flow<List<Long>>

    /** Rows at or after [since], oldest bound only; the caller trims to the exact day. */
    @Query("SELECT * FROM sales_history WHERE soldAt >= :since")
    suspend fun salesSince(since: Instant): List<SalesHistoryEntity>

    @Query(
        "SELECT * FROM sales_history WHERE productId = :productId AND quantity > 0 AND soldAt >= :since ORDER BY soldAt ASC",
    )
    suspend fun getSalesForProductSince(
        productId: Long,
        since: Instant,
    ): List<SalesHistoryEntity>

    @Query("SELECT SUM(revenue) FROM sales_history WHERE soldAt >= :since")
    fun getRevenueSince(since: Instant): Flow<Double?>

    @Query("SELECT SUM(profit) FROM sales_history WHERE soldAt >= :since")
    fun getProfitSince(since: Instant): Flow<Double?>

    @Query("SELECT SUM(quantity) FROM sales_history WHERE soldAt >= :since")
    fun getUnitsSoldSince(since: Instant): Flow<Int?>
}
