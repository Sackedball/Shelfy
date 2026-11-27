package com.kirana.fefoguard.data.repository

import androidx.room.withTransaction
import com.kirana.fefoguard.data.local.FefoGuardDatabase
import com.kirana.fefoguard.data.local.entity.BatchStatus
import com.kirana.fefoguard.domain.engine.Money
import com.kirana.fefoguard.domain.engine.Quantities
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject

/** Writes the stock list as CSV: one row per lot, and one row for a product with no lot. */
class InventoryCsvExporter
    @Inject
    constructor(
        private val database: FefoGuardDatabase,
    ) {
        /** Reads the whole list inside one transaction, so the file is a consistent snapshot of the shelf. */
        suspend fun write(out: Appendable) {
            out.append(HEADER)
            val today =
                Clock.System
                    .now()
                    .toLocalDateTime(TimeZone.currentSystemDefault())
                    .date
            database.withTransaction {
                database.productDao().searchProducts("").first().forEach { product ->
                    val lots = database.inventoryBatchDao().getAllForProduct(product.id)
                    val rows = if (lots.isEmpty()) listOf(null) else lots
                    rows.forEach { lot ->
                        val quantity = lot?.quantity ?: 0
                        val cost = lot?.unitCost ?: product.purchasePrice
                        val saleable = if (lot?.status == BatchStatus.ACTIVE && lot.expiryDate > today) quantity else 0
                        val fields =
                            listOf(
                                product.barcode,
                                product.name,
                                product.category,
                                product.unit,
                                Quantities.format(product.packSize, product.unitScale),
                                Quantities.format(product.minStockLevel, product.unitScale),
                                lot?.id?.toString().orEmpty(),
                                Quantities.format(quantity, product.unitScale),
                                Quantities.format(saleable, product.unitScale),
                                if (product.expiryTracked) lot?.expiryDate?.toString().orEmpty() else "",
                                lot?.status?.name.orEmpty(),
                                cost.toString(),
                                product.sellingPrice.toString(),
                                Money.total(cost, quantity, product.unitScale).toString(),
                            )
                        out.append(fields.joinToString(",") { value -> "\"" + value.replace("\"", "\"\"") + "\"" }).append('\n')
                    }
                }
            }
        }

        private companion object {
            const val HEADER = "Barcode,Name,Category,Unit,PackSize,MinStock,BatchId,Quantity,SaleableQuantity,Expiry,Status,UnitCost,SellingPrice,StockValue\n"
        }
    }
