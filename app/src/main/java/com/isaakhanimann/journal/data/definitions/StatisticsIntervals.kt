// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.data.definitions

import java.time.Duration
import java.time.Instant

data class StatisticsIntervalRecord(val ingestionId: Int, val experienceId: Int, val start: Instant, val end: Instant?)
data class StatisticsInterval(val fromExperienceId: Int, val toExperienceId: Int,
    val fromIngestionId: Int, val toIngestionId: Int, val elapsed: Duration)
data class StatisticsIntervals(val withinExperiences: List<StatisticsInterval> = emptyList(),
    val betweenExperiences: List<StatisticsInterval> = emptyList())
data class StatisticsCurrentBreak(val ingestionId: Int, val experienceId: Int, val recordedEnd: Instant,
    val asOf: Instant, val elapsed: Duration, val ongoing: Boolean)

/** Current context spans the journal; planned future starts do not interrupt an existing break. */
fun statisticsCurrentBreak(records: List<StatisticsIntervalRecord>, now: Instant): StatisticsCurrentBreak? {
    val latest = records.filter { it.start <= now }.distinctBy { it.ingestionId }
        .maxWithOrNull(compareBy<StatisticsIntervalRecord> { maxOf(it.start, it.end ?: it.start) }
            .thenBy { it.ingestionId }) ?: return null
    val end = maxOf(latest.start, latest.end ?: latest.start)
    return StatisticsCurrentBreak(latest.ingestionId, latest.experienceId, end, now,
        if (end >= now) Duration.ZERO else Duration.between(end, now), end > now)
}

/** Within-entry intervals are start-to-start. Breaks use recorded ends, never estimated effect durations. */
fun statisticsIntervals(records: List<StatisticsIntervalRecord>, checkCancelled: () -> Unit = {}): StatisticsIntervals {
    val groups = records.distinctBy { it.ingestionId }.groupBy { it.experienceId }.values.map { rows ->
        checkCancelled()
        rows.sortedWith(compareBy({ it.start }, { it.ingestionId }))
    }.sortedWith(compareBy({ it.first().start }, { it.first().experienceId }))
    fun interval(from: StatisticsIntervalRecord, to: StatisticsIntervalRecord, end: Instant) = StatisticsInterval(
        from.experienceId, to.experienceId, from.ingestionId, to.ingestionId,
        if (to.start <= end) Duration.ZERO else Duration.between(end, to.start))
    val within = groups.flatMap { rows ->
        checkCancelled()
        rows.zipWithNext { a, b -> interval(a, b, a.start) }
    }
    var frontier: StatisticsIntervalRecord? = null
    var frontierEnd: Instant? = null
    val breaks = groups.mapNotNull { rows ->
        checkCancelled()
        val first = rows.first()
        val previous = frontier
        val gap = previous?.let { interval(it, first, requireNotNull(frontierEnd)) }
        rows.forEach { row ->
            val end = maxOf(row.start, row.end ?: row.start)
            if (end > (frontierEnd ?: Instant.MIN)) { frontier = row; frontierEnd = end }
        }
        gap
    }
    return StatisticsIntervals(within, breaks)
}
