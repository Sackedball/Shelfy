package com.kirana.fefoguard.domain.engine

data class ForecastLot(
    val id: Long,
    val quantity: Int,
    val daysLeft: Int,
    val unitCost: Double,
)

/** Future demand is consumed once per product, in FEFO order. Expired units consume none. */
fun forecastLots(
    lots: List<ForecastLot>,
    dailyRate: Double,
): Map<Long, PredictedLoss> {
    require(dailyRate.isFinite() && dailyRate >= 0.0)
    var allocated = 0.0
    return lots.sortedWith(compareBy({ it.daysLeft }, { it.id })).associate { lot ->
        require(lot.quantity >= 0 && lot.unitCost.isFinite() && lot.unitCost >= 0.0)
        val demand = (dailyRate * lot.daysLeft.coerceAtLeast(0) - allocated).coerceAtLeast(0.0)
        val sold = minOf(lot.quantity.toDouble(), demand)
        allocated += sold
        val leftover = lot.quantity - sold
        lot.id to PredictedLoss(leftover, Money.rupees(Money.paise(leftover * lot.unitCost)))
    }
}
