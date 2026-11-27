package com.kirana.fefoguard.data.repository

import androidx.room.withTransaction
import com.kirana.fefoguard.data.local.FefoGuardDatabase
import com.kirana.fefoguard.data.local.entity.DraftBillEntity
import com.kirana.fefoguard.data.local.entity.ProductEntity
import com.kirana.fefoguard.domain.cart.CartLineItem
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

object CartCodec {
    fun encode(items: List<CartLineItem>): String =
        JSONArray(
            items.map { line ->
                JSONObject().apply {
                    put("id", line.product.id)
                    put("barcode", line.product.barcode)
                    put("name", line.product.name)
                    put("category", line.product.category)
                    put("cost", line.product.purchasePrice)
                    put("price", line.product.sellingPrice)
                    put("minimum", line.product.minStockLevel)
                    put("unit", line.product.unit)
                    put("packSize", line.product.packSize)
                    put("expiryTracked", line.product.expiryTracked)
                    put("quantity", line.quantity)
                    put("discount", line.discountPercent)
                    if (line.lineOffPaise != 0L) put("lineOff", line.lineOffPaise)
                    line.priceOverridePaise?.let { put("override", it) }
                    if (line.overrideReason.isNotBlank()) put("overrideReason", line.overrideReason)
                }
            },
        ).toString()

    fun decode(payload: String): List<CartLineItem> {
        val rows = JSONArray(payload)
        return (0 until rows.length()).map { index ->
            val r = rows.getJSONObject(index)
            CartLineItem(
                ProductEntity(
                    r.getLong("id"),
                    r.getString("barcode"),
                    r.getString("name"),
                    r.getString(
                        "category",
                    ),
                    r.getDouble("cost"),
                    r.getDouble("price"),
                    r.getInt("minimum"),
                    r.optString(
                        "unit",
                        "PCS",
                    ),
                    r.optInt("packSize", 1),
                    r.optBoolean("expiryTracked", true),
                ),
                r.getInt("quantity"),
                r.getDouble("discount"),
                lineOffPaise = r.optLong("lineOff", 0),
                priceOverridePaise = if (r.has("override")) r.getLong("override") else null,
                overrideReason = r.optString("overrideReason", ""),
            )
        }
    }
}

class DraftRepository
    @Inject
    constructor(
        private val db: FefoGuardDatabase,
    ) {
        fun observe(): Flow<List<DraftBillEntity>> = db.shopStateDao().drafts()

        suspend fun active(): DraftBillEntity? =
            db.shopStateDao().allDrafts().firstOrNull {
                it.state ==
                    "ACTIVE"
            }

        suspend fun save(
            id: String,
            items: List<CartLineItem>,
            state: String = "ACTIVE",
            billDiscountPaise: Long = 0,
        ) {
            if (items.isEmpty()) {
                delete(id)
            } else {
                db.shopStateDao().saveDraft(
                    DraftBillEntity(id, state, CartCodec.encode(items), System.currentTimeMillis(), billDiscountPaise),
                )
            }
        }

        suspend fun get(id: String): DraftBillEntity? = db.shopStateDao().draft(id)

        /** Keeps an unreadable draft out of billing without destroying its data. */
        suspend fun quarantine(draft: DraftBillEntity) = db.shopStateDao().saveDraft(draft.copy(state = "CORRUPT"))

        suspend fun delete(id: String) =
            db.withTransaction {
                db.shopStateDao().deleteDraft("payment:" + id)
                db.shopStateDao().deleteDraft(id)
            }

        suspend fun bill(id: String) = db.checkoutDao().get(id)

        suspend fun switch(
            activeId: String,
            items: List<CartLineItem>,
            target: DraftBillEntity,
            activeBillDiscountPaise: Long = 0,
        ) = db.withTransaction {
            save(activeId, items, "HELD", activeBillDiscountPaise)
            db.shopStateDao().saveDraft(target.copy(state = "ACTIVE"))
        }
    }
