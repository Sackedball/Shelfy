package com.kirana.fefoguard.domain.engine

import java.math.BigDecimal
import java.math.RoundingMode

/** How the amount payable is rounded. Stored as text in app settings. */
enum class RoundOffMode {
    /** Charge the exact amount. */
    OFF,

    /** Nearest whole rupee, half up (₹85.50 becomes ₹86, ₹85.49 becomes ₹85). */
    NEAREST,

    /** Always down to the whole rupee, the usual counter habit when the customer pays in notes. */
    DOWN,
}

/** The money on one bill. Invariant: [payablePaise] = [linesPaise] - [billDiscountPaise] + [roundOffPaise]. */
data class BillAmounts(
    val linesPaise: Long,
    val billDiscountPaise: Long,
    val roundOffPaise: Long,
) {
    val payablePaise: Long get() = linesPaise - billDiscountPaise + roundOffPaise
}

object BillMath {
    /** The signed change that rounds [amountPaise] to a whole rupee under [mode]. */
    fun roundOff(
        amountPaise: Long,
        mode: RoundOffMode,
    ): Long {
        require(amountPaise >= 0)
        val remainder = amountPaise % 100
        return when (mode) {
            RoundOffMode.OFF -> 0L
            RoundOffMode.DOWN -> -remainder
            RoundOffMode.NEAREST -> if (remainder >= 50) 100 - remainder else -remainder
        }
    }

    fun amounts(
        linePaise: List<Long>,
        billDiscountPaise: Long,
        mode: RoundOffMode,
    ): BillAmounts {
        val lines = linePaise.sum()
        require(billDiscountPaise in 0..lines) { "Bill discount cannot exceed the bill" }
        return BillAmounts(lines, billDiscountPaise, roundOff(lines - billDiscountPaise, mode))
    }

    /**
     * Shares [amount] (any sign) over [weights] so the shares add up to exactly [amount]:
     * each gets its floor, then the leftover paise go to the largest remainders, earliest first.
     */
    fun allocate(
        amount: Long,
        weights: List<Long>,
    ): List<Long> {
        require(weights.isNotEmpty() && weights.all { it >= 0 })
        val total = weights.sum()
        if (amount == 0L || total == 0L) return List(weights.size) { 0L }
        val magnitude = BigDecimal(Math.abs(amount))
        val exact = weights.map { magnitude.multiply(BigDecimal(it)).divide(BigDecimal(total), 10, RoundingMode.DOWN) }
        val floors = exact.map { it.setScale(0, RoundingMode.DOWN).longValueExact() }.toMutableList()
        var left = Math.abs(amount) - floors.sum()
        exact.indices
            .sortedWith(compareByDescending<Int> { exact[it].subtract(BigDecimal(floors[it])) }.thenBy { it })
            .forEach { index ->
                if (left > 0 && weights[index] > 0) {
                    floors[index] += 1
                    left--
                }
            }
        return floors.map { if (amount < 0) -it else it }
    }

    /**
     * What each line really earns once the bill discount and round-off are shared in.
     * The result adds up to exactly [BillAmounts.payablePaise], so refunds and cash sessions stay in step.
     */
    fun lineRevenue(
        linePaise: List<Long>,
        amounts: BillAmounts,
    ): List<Long> {
        val discounts = allocate(amounts.billDiscountPaise, linePaise)
        val afterDiscount = linePaise.zip(discounts) { line, discount -> line - discount }
        val roundOff = allocate(amounts.roundOffPaise, afterDiscount)
        return afterDiscount.zip(roundOff) { value, round -> (value + round).coerceAtLeast(0) }
    }

    /**
     * "Give me ₹20 of sugar": the whole base units that [amountPaise] buys at [unitPricePaise] per
     * [scale] base units (1000 for kg and litres), rounded down. Returns the quantity and what it costs.
     */
    fun quantityForAmount(
        amountPaise: Long,
        unitPricePaise: Long,
        scale: Int,
    ): Pair<Int, Long> {
        require(amountPaise > 0 && unitPricePaise > 0 && scale > 0)
        val quantity = BigDecimal(amountPaise).multiply(BigDecimal(scale)).divide(BigDecimal(unitPricePaise), 0, RoundingMode.DOWN)
        require(quantity.signum() > 0 && quantity <= BigDecimal(Int.MAX_VALUE)) { "Amount is too small or too large" }
        val units = quantity.toInt()
        return units to Money.linePaise(unitPricePaise / 100.0, units, scale)
    }
}
