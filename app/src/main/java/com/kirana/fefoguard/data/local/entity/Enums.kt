package com.kirana.fefoguard.data.local.entity

enum class BatchStatus {
    ACTIVE,
    SOLD_OUT,
}

enum class SaleSource {
    SCAN,
    MANUAL_LIST_SELL,
}

enum class AdjustmentType {
    DAMAGED,
    MISSING,
    RETURNED,
    SPOILED,
}
