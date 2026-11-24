package com.kirana.fefoguard.domain.engine

import java.math.BigDecimal
import java.math.RoundingMode

/** INR amounts are rounded half-up to paise at the unit-price boundary. */
object Money {
    fun paise(value: Double): Long {
        require(value.isFinite() && value >= 0.0) { "Amount must be finite and non-negative" }
        return BigDecimal
            .valueOf(
                value,
            ).movePointRight(2)
            .setScale(0, RoundingMode.HALF_UP)
            .longValueExact()
    }

    fun rupees(paise: Long): Double = BigDecimal.valueOf(paise, 2).toDouble()

    fun discountedUnit(
        price: Double,
        percent: Double,
    ): Double {
        require(percent.isFinite() && percent in 0.0..100.0) {
            "Discount must be between 0 and 100"
        }
        val value =
            BigDecimal
                .valueOf(paise(price))
                .multiply(
                    BigDecimal.ONE.subtract(BigDecimal.valueOf(percent).movePointLeft(2)),
                ).setScale(0, RoundingMode.HALF_UP)
                .longValueExact()
        return rupees(value)
    }

    fun total(
        price: Double,
        quantity: Int,
        scale: Int = 1,
    ): Double = rupees(linePaise(price, quantity, scale))

    fun linePaise(
        price: Double,
        quantity: Int,
        scale: Int = 1,
    ): Long {
        require(quantity >= 0 && scale > 0)
        return BigDecimal
            .valueOf(paise(price))
            .multiply(BigDecimal(quantity))
            .divide(BigDecimal(scale), 0, RoundingMode.HALF_UP)
            .longValueExact()
    }
}
