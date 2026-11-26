package com.kirana.fefoguard.data.repository

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/** Refresh rolling windows and date-based state even when no database rows change. */
internal fun timeFlow() =
    flow {
        while (true) {
            emit(Clock.System.now())
            delay(30_000)
        }
    }

private const val DAY_MILLIS = 24L * 60 * 60 * 1000

/**
 * Runs [query] for rows from the last [days] days (plus up to a day of slack, so callers still trim to the exact
 * moment). The bound moves only when the day rolls over, so the database is not re-queried every 30 seconds.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun <T> windowedByDay(
    days: Int,
    query: (Instant) -> Flow<T>,
): Flow<T> =
    timeFlow()
        .map { Instant.fromEpochMilliseconds((it.toEpochMilliseconds() / DAY_MILLIS - days - 1) * DAY_MILLIS) }
        .distinctUntilChanged()
        .flatMapLatest(query)
