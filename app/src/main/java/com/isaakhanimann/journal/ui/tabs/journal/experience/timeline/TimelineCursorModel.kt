// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.ui.tabs.journal.experience.timeline

import com.isaakhanimann.journal.data.substances.classes.roa.DurationRange
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.DataForOneEffectLine
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.drawables.timelines.FullTimelines
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.drawables.timelines.toFullTimelines
import java.time.Duration
import java.time.Instant
import java.util.Locale
import com.isaakhanimann.journal.ui.tabs.search.substance.roa.toReadableString

fun doseToMilligrams(dose: Double?, unit: String?): Double? {
    if (dose == null || !dose.isFinite() || dose < 0) return null
    val factor = when (unit?.trim()?.lowercase(Locale.ROOT)?.replace('μ', 'µ')) {
        "mg", "mg thc", "mg psilocybin" -> 1.0
        "g" -> 1000.0
        "µg", "ug", "mcg" -> 0.001
        else -> return null // Volume, counts and unknown units are not mass.
    }
    return (dose * factor).takeIf { it.isFinite() }
}

data class TimelineCursorReadout(
    val ingestion: DataForOneEffectLine,
    val peakFraction: Float?,
    val milligramEquivalent: Double?,
    val quantityEquivalent: Double? = null,
)
data class GroupedTimelineReadout(val value: TimelineCursorReadout, val contributionCount: Int,
    val knownDoseCount: Int = contributionCount)

fun TimelineCursorReadout.equivalentText(): String {
    val unit = ingestion.quantityUnit?.takeIf { it.isNotBlank() }
    if (unit != null) {
        val amount = quantityEquivalent ?: return "$unit equivalent unavailable"
        val label = if (amount == 1.0) unit else ingestion.quantityUnitPlural?.takeIf { it.isNotBlank() } ?: unit
        return "${amount.toReadableString()} $label equivalent"
    }
    return milligramEquivalent?.let { "${it.toReadableString()} mg equivalent" } ?: "mg equivalent unavailable"
}

/** Per-ingestion estimates, independent of the graph's display-height normalization. */
class TimelineCursorModel(lines: List<DataForOneEffectLine>) {
    private data class Curve(val ingestion: DataForOneEffectLine, val full: FullTimelines?)

    private val curves = lines.groupBy { it.groupingKey to it.route }.values.flatMap { group ->
        // The renderer uses the first route duration for every ingestion in the group.
        group.map { original ->
            val line = original.copy(roaDuration = group.first().roaDuration)
            val duration = line.roaDuration
            val full = if (duration != null && listOf(duration.onset, duration.comeup, duration.peak, duration.offset)
                    .all { it.validSeconds(0.5f) != null } &&
                (line.endTime == null || line.endTime > line.startTime)) {
                duration.toFullTimelines(
                    weightedLines = listOf(WeightedLine(line.startTime, line.endTime, line.horizontalWeight, 1f)),
                    startTimeGraph = line.startTime
                )?.takeIf { it.nonNormalisedHeight.isFinite() && it.nonNormalisedHeight > 0f }
            } else null
            Curve(line, full)
        }
    }

    val ingestions: List<DataForOneEffectLine> = curves.map { it.ingestion }
    private data class Group(val indices: List<Int>, val start: Instant, val full: FullTimelines?)
    private val groups by lazy { curves.indices.groupBy { curves[it].ingestion.groupingKey to curves[it].ingestion.route }.values.map { indices ->
        val lines = indices.map { curves[it].ingestion }
        val start = lines.minOf { it.startTime }
        val full = if (indices.all { curves[it].full != null } && lines.all { it.height.isFinite() && it.height >= 0 }) {
            lines.first().roaDuration?.toFullTimelines(lines.map { WeightedLine(it.startTime, it.endTime, it.horizontalWeight, it.height) }, start)
                ?.takeIf { it.nonNormalisedHeight.isFinite() && it.nonNormalisedHeight > 0 }
        } else null
        Group(indices, start, full)
    } }

    /** Subject/route peaks use the combined raw curve, independent of the drawing scale. */
    fun groupedAt(time: Instant): List<GroupedTimelineReadout> {
        val values = at(time)
        return groups.map { group ->
            val parts = group.indices.map { values[it] }
            val units = parts.map { it.ingestion.quantityUnit }.distinct()
            val seconds = Duration.between(group.start, time).toMillis() / 1000f
            val rawHeight = group.full?.heightAt(seconds)?.takeIf { it.isFinite() && it >= 0 }
            fun ratios(amount: (DataForOneEffectLine) -> Double?): List<Double> = parts.mapNotNull { part ->
                val value = amount(part.ingestion) ?: return@mapNotNull null
                val height = part.ingestion.height.toDouble()
                if (!value.isFinite() || value < 0 || !height.isFinite() || height <= 0) return@mapNotNull null
                (value / height).takeIf { it.isFinite() }
            }
            fun equivalent(amount: (DataForOneEffectLine) -> Double?): Double? {
                val ratios = ratios(amount)
                // Unknown doses stay unknown: never extrapolate them from another ingestion.
                if (ratios.size != parts.size) return null
                val maximum = ratios.maxOrNull() ?: return null
                val average = if (maximum == 0.0) 0.0 else ratios.sumOf { it / maximum } / ratios.size * maximum
                return rawHeight?.let { (it.toDouble() * average).takeIf { result -> result.isFinite() && result >= 0 } }
            }
            val quantity = if (units.size == 1 && !units.single().isNullOrBlank()) equivalent { it.quantity } else null
            val mg = equivalent { it.doseInMilligrams }
            val line = if (units.size == 1) parts.first().ingestion else parts.first().ingestion.copy(quantityUnit = null, quantityUnitPlural = null)
            val fraction = group.full?.let { (it.heightAt(seconds) / it.nonNormalisedHeight).takeIf(Float::isFinite)?.coerceIn(0f, 1f) }
            val knownCount = if (!line.quantityUnit.isNullOrBlank()) ratios { it.quantity }.size else ratios { it.doseInMilligrams }.size
            GroupedTimelineReadout(TimelineCursorReadout(line, fraction, mg, quantity), parts.size, knownCount)
        }.filter { it.value.peakFraction?.let { fraction -> fraction >= 0.005f } ?: true }
    }

    fun at(time: Instant): List<TimelineCursorReadout> = curves.map { (line, full) ->
        val seconds = Duration.between(line.startTime, time).toMillis() / 1000f
        val fraction = if (full != null) {
            (full.heightAt(seconds) / full.nonNormalisedHeight).takeIf { it.isFinite() }?.coerceIn(0f, 1f)
        } else {
            knownPartialFraction(line, seconds)
        }
        val equivalent = if (fraction != null) line.doseInMilligrams?.let { it * fraction }
            ?.takeIf { it.isFinite() && it >= 0 } else null
        val quantityEquivalent = if (fraction != null) line.quantity?.let { it * fraction }
            ?.takeIf { it.isFinite() && it >= 0 } else null
        TimelineCursorReadout(line, fraction, equivalent, quantityEquivalent)
    }

    private fun knownPartialFraction(line: DataForOneEffectLine, seconds: Float): Float? {
        // Dotted trajectories and ranges without full timing data have unknown intensity.
        if (line.endTime != null) return null
        val duration = line.roaDuration ?: return null
        val onset = duration.onset.validSeconds(0.5f) ?: return null
        if (seconds < onset) return 0f
        val comeup = duration.comeup.validSeconds(0.5f) ?: return null
        if (comeup > 0 && seconds < onset + comeup) return ((seconds - onset) / comeup).coerceIn(0f, 1f)
        val peak = duration.peak.validSeconds(line.horizontalWeight) ?: return null
        return if (seconds <= onset + comeup + peak) 1f else null
    }
}

private fun DurationRange?.validSeconds(weight: Float): Float? {
    val min = this?.minInSec ?: return null
    val max = maxInSec ?: return null
    if (!min.isFinite() || !max.isFinite() || min < 0 || max < min || !weight.isFinite()) return null
    return interpolateAtValueInSeconds(weight.coerceIn(0f, 1f))?.takeIf { it.isFinite() }
}
