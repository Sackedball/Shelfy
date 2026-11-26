package com.kirana.fefoguard.data.repository

import com.kirana.fefoguard.data.local.FefoGuardDatabase
import com.kirana.fefoguard.data.local.entity.AppSettingEntity
import com.kirana.fefoguard.domain.engine.RoundOffMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** A near-expiry rule: with [days] or fewer days left, suggest [percent] off. */
data class DiscountTier(
    val days: Int,
    val percent: Int,
)

object DiscountTiers {
    val Default = listOf(DiscountTier(2, 30), DiscountTier(7, 15))

    fun encode(tiers: List<DiscountTier>) = tiers.joinToString(",") { it.days.toString() + ":" + it.percent }

    fun decode(text: String?): List<DiscountTier> =
        text
            ?.split(",")
            ?.mapNotNull { part ->
                val bits = part.split(":")
                val days = bits.getOrNull(0)?.trim()?.toIntOrNull()
                val percent = bits.getOrNull(1)?.trim()?.toIntOrNull()
                if (days != null && percent != null && days >= 0 && percent in 1..90) DiscountTier(days, percent) else null
            }?.takeIf { it.isNotEmpty() }
            ?.sortedBy { it.days }
            ?: Default

    /** The suggested percent for a batch with [daysLeft] days to go, or 0 when no tier applies. */
    fun percentFor(
        tiers: List<DiscountTier>,
        daysLeft: Int,
    ): Int = tiers.sortedBy { it.days }.firstOrNull { daysLeft <= it.days }?.percent ?: 0
}

/** Shop settings stored in the database so a backup carries them to a new phone. */
@Singleton
class SettingsRepository
    @Inject
    constructor(
        private val db: FefoGuardDatabase,
    ) {
        private val dao get() = db.settingsDao()

        fun roundOffMode(): Flow<RoundOffMode> = dao.observeAll().map { rows -> parseRoundOff(rows.firstOrNull { it.key == ROUND_OFF }?.value) }

        fun discountTiers(): Flow<List<DiscountTier>> = dao.observeAll().map { rows -> DiscountTiers.decode(rows.firstOrNull { it.key == TIERS }?.value) }

        suspend fun currentRoundOff(): RoundOffMode = parseRoundOff(dao.get(ROUND_OFF))

        suspend fun currentTiers(): List<DiscountTier> = DiscountTiers.decode(dao.get(TIERS))

        suspend fun voidWindowMinutes(): Int = parseVoidWindow(dao.get(VOID_WINDOW))

        suspend fun setRoundOff(mode: RoundOffMode) {
            dao.put(AppSettingEntity(ROUND_OFF, mode.name))
        }

        suspend fun setTiers(tiers: List<DiscountTier>) {
            require(tiers.isNotEmpty() && tiers.all { it.days >= 0 && it.percent in 1..90 }) { "Enter days and a percent between 1 and 90" }
            dao.put(AppSettingEntity(TIERS, DiscountTiers.encode(tiers)))
        }

        suspend fun setVoidWindowMinutes(minutes: Int) {
            require(minutes in 1..MAX_VOID_MINUTES)
            dao.put(AppSettingEntity(VOID_WINDOW, minutes.toString()))
        }

        companion object {
            const val ROUND_OFF = "round_off_mode"
            const val TIERS = "discount_tiers"
            const val VOID_WINDOW = "void_window_minutes"
            const val DEFAULT_VOID_MINUTES = 120
            const val MAX_VOID_MINUTES = 7 * 24 * 60

            /** The stored round-off mode, or OFF when nothing valid is stored. Shared with billing so both read it the same way. */
            fun parseRoundOff(text: String?) = runCatching { RoundOffMode.valueOf(text ?: "") }.getOrDefault(RoundOffMode.OFF)

            /** The stored void window in minutes, or the default when nothing valid is stored. */
            fun parseVoidWindow(text: String?): Int = text?.toIntOrNull()?.takeIf { it in 1..MAX_VOID_MINUTES } ?: DEFAULT_VOID_MINUTES
        }
    }
