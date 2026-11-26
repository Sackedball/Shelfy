package com.kirana.fefoguard.data.repository

import com.kirana.fefoguard.data.local.FefoGuardDatabase
import com.kirana.fefoguard.data.local.entity.DraftBillEntity
import javax.inject.Inject

class FormDraftRepository
    @Inject
    constructor(
        private val db: FefoGuardDatabase,
    ) {
        suspend fun load(id: String): String? = db.shopStateDao().draft(id)?.payload

        suspend fun save(
            id: String,
            payload: String,
            state: String = "RECEIVING",
        ) = db.shopStateDao().saveDraft(
            DraftBillEntity(id, state, payload, System.currentTimeMillis()),
        )

        suspend fun clear(id: String) = db.shopStateDao().deleteDraft(id)
    }
