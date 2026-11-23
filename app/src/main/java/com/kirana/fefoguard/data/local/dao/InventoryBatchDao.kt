package com.kirana.fefoguard.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.kirana.fefoguard.data.local.entity.BatchStatus
import com.kirana.fefoguard.data.local.entity.BatchWithProduct
import com.kirana.fefoguard.data.local.entity.InventoryBatchEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryBatchDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBatch(batch: InventoryBatchEntity): Long

    @Query(
        "SELECT * FROM inventory_batches WHERE productId = :productId AND status = :status ORDER BY expiryDate ASC",
    )
    fun getActiveBatchesForProductFlow(
        productId: Long,
        status: BatchStatus = BatchStatus.ACTIVE,
    ): Flow<List<InventoryBatchEntity>>

    @Query(
        "SELECT * FROM inventory_batches WHERE productId = :productId AND status = :status ORDER BY expiryDate ASC",
    )
    suspend fun getActiveBatchesForProduct(
        productId: Long,
        status: BatchStatus = BatchStatus.ACTIVE,
    ): List<InventoryBatchEntity>

    @Transaction
    @Query("SELECT * FROM inventory_batches WHERE status = :status ORDER BY expiryDate ASC")
    fun getAllActiveBatchesWithProduct(
        status: BatchStatus = BatchStatus.ACTIVE,
    ): Flow<List<BatchWithProduct>>

    @Query(
        "UPDATE inventory_batches SET quantity = quantity - :amount WHERE id = :batchId AND quantity >= :amount",
    )
    suspend fun decrementBatchQuantity(
        batchId: Long,
        amount: Int,
    ): Int

    @Query("UPDATE inventory_batches SET quantity = 0, status = :soldOutStatus WHERE id = :batchId")
    suspend fun zeroBatchQuantity(
        batchId: Long,
        soldOutStatus: BatchStatus = BatchStatus.SOLD_OUT,
    ): Int

    @Query("UPDATE inventory_batches SET status = :soldOutStatus WHERE id = :batchId")
    suspend fun markBatchSoldOut(
        batchId: Long,
        soldOutStatus: BatchStatus = BatchStatus.SOLD_OUT,
    ): Int

    @Query(
        "UPDATE inventory_batches SET quantity = quantity + :amount, status = :status WHERE id = :id",
    )
    suspend fun incrementQuantity(
        id: Long,
        amount: Int,
        status: BatchStatus = BatchStatus.ACTIVE,
    ): Int

    @Query("SELECT * FROM inventory_batches WHERE productId = :productId ORDER BY expiryDate ASC")
    suspend fun getAllForProduct(productId: Long): List<InventoryBatchEntity>

    @Query("SELECT * FROM inventory_batches WHERE id = :batchId LIMIT 1")
    suspend fun getBatchById(batchId: Long): InventoryBatchEntity?
}
