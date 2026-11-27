package com.kirana.fefoguard.data.repository

import com.kirana.fefoguard.data.local.FefoGuardDatabase
import javax.inject.Inject

class BillRepository
    @Inject
    constructor(
        private val db: FefoGuardDatabase,
    ) {
        fun observeBills() = db.checkoutDao().observeAll()
    }
