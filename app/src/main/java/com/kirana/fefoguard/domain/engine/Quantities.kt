package com.kirana.fefoguard.domain.engine

import java.math.BigDecimal
import java.math.RoundingMode

object Quantities {
    fun parse(
        value: String,
        scale: Int,
    ): Int =
        BigDecimal(value.trim())
            .multiply(BigDecimal(scale))
            .setScale(0, RoundingMode.UNNECESSARY)
            .intValueExact()
            .also {
                require(it > 0) { "Quantity must be positive" }
            }

    fun format(
        quantity: Int,
        scale: Int,
    ): String = BigDecimal(quantity).divide(BigDecimal(scale)).stripTrailingZeros().toPlainString()

    fun proportional(
        totalPaise: Long,
        quantity: Int,
        totalQuantity: Int,
    ): Long =
        BigDecimal(totalPaise)
            .multiply(
                BigDecimal(quantity),
            ).divide(BigDecimal(totalQuantity), 0, RoundingMode.HALF_UP)
            .longValueExact()
}
