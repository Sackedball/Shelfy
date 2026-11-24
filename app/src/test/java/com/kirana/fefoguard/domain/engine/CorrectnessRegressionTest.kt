package com.kirana.fefoguard.domain.engine

import com.kirana.fefoguard.data.local.entity.ProductEntity
import com.kirana.fefoguard.domain.cart.CartManager
import com.kirana.fefoguard.ui.checkout.CheckoutViewModelTest
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CorrectnessRegressionTest {
    @Test
    fun demandIsSharedAcrossLotsAndSplittingDoesNotChangeLoss() {
        val split =
            forecastLots(listOf(ForecastLot(1, 10, 2, 20.0), ForecastLot(2, 10, 2, 20.0)), 5.0)
        val whole = forecastLots(listOf(ForecastLot(1, 20, 2, 20.0)), 5.0)
        assertEquals(200.0, split.values.sumOf { it.expectedLoss }, 0.0)
        assertEquals(
            whole.values.sumOf {
                it.expectedLoss
            },
            split.values.sumOf { it.expectedLoss },
            0.0,
        )
        val expired =
            forecastLots(listOf(ForecastLot(1, 10, -1, 20.0), ForecastLot(2, 10, 2, 20.0)), 5.0)
        assertEquals(200.0, expired.getValue(1).expectedLoss, 0.0)
        assertEquals(0.0, expired.getValue(2).expectedLoss, 0.0)
    }

    @Test
    fun velocityHasExactlyFourteenCalendarDatesAndRejectsFutureSales() {
        val today = LocalDate(2026, 11, 12)
        val dates = (0..14).map { LocalDate.fromEpochDays(today.toEpochDays() - it) to 1 }
        assertEquals(1.0, FefoEngine.calculateVelocity(dates, today = today), 0.0)
        assertEquals(
            0.0,
            FefoEngine.calculateVelocity(listOf(LocalDate(2026, 11, 13) to 14), today = today),
            0.0,
        )
    }

    @Test
    fun monetaryRoundingAndDiscountAreConsistent() {
        assertEquals(85.0, Money.discountedUnit(100.0, 15.0), 0.0)
        assertEquals(0.05, Money.discountedUnit(0.05, 10.0), 0.0)
        assertEquals(0.15, Money.total(Money.discountedUnit(0.05, 10.0), 3), 0.0)
    }

    @Test
    fun mergingPreservesDiscountAndZeroStockRemovesLine() =
        runTest {
            val repository = CheckoutViewModelTest.FakeProductRepo()
            val cart = CartManager(repository)
            val product = ProductEntity(1, "test", "Test", "Test", 20.0, 100.0, 5)
            cart.addItem(product, 1, 15.0)
            val first = async { cart.addItem(product, 1) }
            val second = async { cart.addItem(product, 1) }
            first.await()
            second.await()
            assertEquals(
                3,
                cart.cart.value
                    .single()
                    .quantity,
            )
            assertEquals(
                15.0,
                cart.cart.value
                    .single()
                    .discountPercent,
                0.0,
            )
            repository.availableStock = 0
            cart.updateQuantity(1, 2)
            assertTrue(cart.cart.value.isEmpty())
        }
}
