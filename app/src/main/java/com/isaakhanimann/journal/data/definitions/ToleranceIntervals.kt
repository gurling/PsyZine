// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.data.definitions

import java.time.DateTimeException
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

enum class ToleranceBand { FULL, HALF, ZERO_ONLY }
data class ToleranceInterval(val start: Instant, val end: Instant)
data class ToleranceEvent(val subject: SubjectId, val name: String, val color: String?, val time: Instant,
    val ingestionId: Int, val consumer: String?, val halfHours: Double?, val zeroHours: Double?)
data class SubjectToleranceIntervals(val subject: SubjectId, val name: String, val color: String?,
    val bands: Map<ToleranceBand, List<ToleranceInterval>>, val ingestionIds: Set<Int>)
data class ToleranceProjection(val rows: List<SubjectToleranceIntervals>, val missingMetadata: Set<SubjectId>,
    val subjectNames: Map<SubjectId, String> = emptyMap())

/** Interval projection, not a decay curve. Quantity and visibility do not change these events. */
fun projectTolerance(events: List<ToleranceEvent>, start: Instant, cutoff: Instant, zone: ZoneId): ToleranceProjection {
    require(start <= cutoff) { "The tolerance interval start must not be after its cutoff." }
    val lookback = start.minus(Duration.ofDays(90))
    val eligible = events.filter { it.consumer.isNullOrEmpty() && it.time > lookback && it.time <= cutoff }
        .sortedWith(compareBy<ToleranceEvent> { it.time }.thenBy { it.ingestionId })
        .groupBy { it.subject }
    val missing = linkedSetOf<SubjectId>()
    val rows = eligible.mapNotNull { (subject, values) ->
        val full = mutableListOf<ToleranceInterval>()
        val half = mutableListOf<ToleranceInterval>()
        val zero = mutableListOf<ToleranceInterval>()
        var usableMetadata = false
        val days = values.distinctBy { it.time.atZone(zone).toLocalDate() }
        for (event in days) {
            val anchor = event.time.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant()
            val halfEnd = toleranceEnd(anchor, event.halfHours)
            val zeroEnd = toleranceEnd(anchor, event.zeroHours)
            usableMetadata = usableMetadata || halfEnd != null || zeroEnd != null
            if (halfEnd != null) {
                full += ToleranceInterval(anchor, halfEnd)
                if (zeroEnd != null && zeroEnd > halfEnd) half += ToleranceInterval(halfEnd, zeroEnd)
            } else if (zeroEnd != null) zero += ToleranceInterval(anchor, zeroEnd)
        }
        val fullUnion = unionToleranceIntervals(full)
        val bands = linkedMapOf(
            ToleranceBand.FULL to fullUnion,
            ToleranceBand.HALF to subtractToleranceIntervals(unionToleranceIntervals(half), fullUnion),
            ToleranceBand.ZERO_ONLY to unionToleranceIntervals(zero),
        ).mapValues { (_, intervals) -> intervals.mapNotNull { interval ->
            val lower = maxOf(start, interval.start)
            interval.copy(start = lower).takeIf { it.end > lower }
        } }.filterValues { it.isNotEmpty() }
        if (bands.isEmpty()) { if (!usableMetadata) missing += subject; null }
        else SubjectToleranceIntervals(subject, values.first().name, values.first().color, bands, values.map { it.ingestionId }.toSet())
    }.sortedWith(compareBy<SubjectToleranceIntervals, String>(String.CASE_INSENSITIVE_ORDER) { it.name }.thenBy { it.subject.key })
    return ToleranceProjection(rows, missing, eligible.mapValues { (_, values) -> values.first().name })
}

private fun toleranceEnd(start: Instant, hours: Double?): Instant? {
    if (hours == null || !hours.isFinite() || hours <= 24.0) return null
    val seconds = hours * 3600.0
    if (!seconds.isFinite() || seconds >= Long.MAX_VALUE.toDouble()) return null
    return try {
        // Add elapsed whole seconds, truncating fractional seconds.
        start.plusSeconds(seconds.toLong())
    } catch (_: DateTimeException) { null } catch (_: ArithmeticException) { null }
}

fun unionToleranceIntervals(values: List<ToleranceInterval>): List<ToleranceInterval> {
    val result = mutableListOf<ToleranceInterval>()
    for (interval in values.filter { it.end > it.start }.sortedWith(compareBy<ToleranceInterval> { it.start }.thenBy { it.end })) {
        val previous = result.lastOrNull()
        if (previous != null && interval.start <= previous.end) result[result.lastIndex] = previous.copy(end = maxOf(previous.end, interval.end))
        else result += interval
    }
    return result
}

fun subtractToleranceIntervals(values: List<ToleranceInterval>, removed: List<ToleranceInterval>): List<ToleranceInterval> {
    val blocks = unionToleranceIntervals(removed)
    val result = mutableListOf<ToleranceInterval>()
    var firstBlock = 0
    for (interval in unionToleranceIntervals(values)) {
        var cursor = interval.start
        while (firstBlock < blocks.size && blocks[firstBlock].end <= cursor) firstBlock++
        var index = firstBlock
        while (index < blocks.size && blocks[index].start < interval.end) {
            val block = blocks[index++]
            if (block.start > cursor) result += ToleranceInterval(cursor, minOf(block.start, interval.end))
            cursor = maxOf(cursor, block.end)
            if (cursor >= interval.end) break
        }
        if (cursor < interval.end) result += ToleranceInterval(cursor, interval.end)
    }
    return result
}
