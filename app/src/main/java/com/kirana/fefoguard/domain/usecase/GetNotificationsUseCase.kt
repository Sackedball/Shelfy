package com.kirana.fefoguard.domain.usecase

import com.kirana.fefoguard.domain.engine.BatchStatusTier
import com.kirana.fefoguard.domain.engine.StockStatus
import com.kirana.fefoguard.domain.repository.ProductRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

data class NotificationSummary(
    val expiredCount: Int,
    val criticalCount: Int,
    val lowStockCount: Int,
) {
    val totalAlerts = expiredCount + criticalCount + lowStockCount
    val hasAlerts = totalAlerts > 0
}

class GetNotificationsUseCase
    @Inject
    constructor(
        private val productRepository: ProductRepository,
    ) {
        operator fun invoke(): Flow<NotificationSummary> =
            productRepository.getHomeDashboardData().map { dashboardData ->
                val data = dashboardData.batchesWithAnalysis
                val expired = data.count { it.second.status == BatchStatusTier.EXPIRED }
                val critical = data.count { it.second.status == BatchStatusTier.CRITICAL }
                val lowStock = dashboardData.restock.size

                NotificationSummary(
                    expiredCount = expired,
                    criticalCount = critical,
                    lowStockCount = lowStock,
                )
            }
    }
