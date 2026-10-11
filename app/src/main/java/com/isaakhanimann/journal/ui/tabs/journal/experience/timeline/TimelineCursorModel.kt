// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.ui.tabs.journal.experience.timeline

import com.isaakhanimann.journal.data.substances.classes.roa.DurationRange
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.DataForOneEffectLine
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.drawables.timelines.FullTimelines
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.drawables.timelines.toFullTimelines
import java.time.Duration
import java.time.Instant
import java.util.Locale

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
    val milligramEquivalent: Double?
)

/** Per-ingestion estimates, independent of the graph's display-height normalization. */
class TimelineCursorModel(lines: List<DataForOneEffectLine>) {
    private data class Curve(val ingestion: DataForOneEffectLine, val full: FullTimelines?)

    private val curves = lines.groupBy { it.substanceName to it.route }.values.flatMap { group ->
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

    fun at(time: Instant): List<TimelineCursorReadout> = curves.map { (line, full) ->
        val seconds = Duration.between(line.startTime, time).toMillis() / 1000f
        val fraction = if (full != null) {
            (full.heightAt(seconds) / full.nonNormalisedHeight).takeIf { it.isFinite() }?.coerceIn(0f, 1f)
        } else {
            knownPartialFraction(line, seconds)
        }
        val equivalent = if (fraction != null) line.doseInMilligrams?.let { it * fraction }
            ?.takeIf { it.isFinite() && it >= 0 } else null
        TimelineCursorReadout(line, fraction, equivalent)
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
