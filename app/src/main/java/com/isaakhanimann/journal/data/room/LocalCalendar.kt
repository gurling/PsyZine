// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.data.room

import android.content.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import java.time.*
import javax.inject.Inject
import javax.inject.Singleton

data class CalendarNow(val instant: Instant, val zone: ZoneId) {
    val date: LocalDate get() = instant.atZone(zone).toLocalDate()
}

data class DateRange(val start: Instant, val end: Instant) {
    operator fun contains(instant: Instant): Boolean = instant >= start && instant < end
}

fun localDateRange(start: LocalDate, endExclusive: LocalDate, zone: ZoneId): DateRange =
    DateRange(start.atStartOfDay(zone).toInstant(), endExclusive.atStartOfDay(zone).toInstant())

/** Collection starts with fresh clock/zone values and stops all work with the UI subscriber. */
@Singleton
open class CalendarZone @Inject constructor() {
    open fun current(): ZoneId = ZoneId.systemDefault()
}

@Singleton
class LocalCalendar @Inject constructor(@ApplicationContext private val context: Context, private val clock: Clock,
    private val zones: CalendarZone) {
    fun now() = CalendarNow(clock.instant(), zones.current())
    val changes get() = callbackFlow {
        fun refresh() { trySend(now()) }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) { refresh() }
        }
        androidx.core.content.ContextCompat.registerReceiver(context, receiver, IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED); addAction(Intent.ACTION_TIME_CHANGED); addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        refresh()
        val midnight = launch {
            while (isActive) {
                val current = now()
                val next = current.date.plusDays(1).atStartOfDay(current.zone).toInstant()
                delay(maxOf(1, Duration.between(current.instant, next).toMillis()))
                refresh()
            }
        }
        awaitClose { midnight.cancel(); context.unregisterReceiver(receiver) }
    }
}
