// PsyZine modifications, 2026-10-11. GPL-3.0-or-later.
// Adapted from PsyLog 0c29a70f, copyright 2025–2026 PsyLog contributors.
package com.isaakhanimann.journal.data.substances.classes.roa

import java.time.Duration
import java.time.Instant

enum class IngestionPhase(val displayName: String) {
    ONSET("onset"), COMEUP("comeup"), PEAK("peak"), OFFSET("offset"), AFTERGLOW("afterglow"), ENDED("ended"), UNKNOWN("unknown")
}

/** An estimate from reference ranges, not a measurement of a person's state. */
fun calculateCurrentPhase(ingestionTime: Instant, roaDuration: RoaDuration?, now: Instant): IngestionPhase {
    if (roaDuration == null) return IngestionPhase.UNKNOWN
    val elapsed = Duration.between(ingestionTime, now).seconds.toDouble()
    if (elapsed < 0) return IngestionPhase.ONSET
    val phases = listOf(IngestionPhase.ONSET to roaDuration.onset, IngestionPhase.COMEUP to roaDuration.comeup,
        IngestionPhase.PEAK to roaDuration.peak, IngestionPhase.OFFSET to roaDuration.offset, IngestionPhase.AFTERGLOW to roaDuration.afterglow)
    var accumulated = 0.0
    for ((phase, range) in phases) {
        if (range?.minInSec?.let { !it.isFinite() || it < 0 } == true ||
            range?.maxInSec?.let { !it.isFinite() || it < 0 } == true ||
            range?.minInSec != null && range.maxInSec != null && range.minInSec > range.maxInSec) continue
        val duration = range?.interpolateAtValueInSeconds(0.5f)?.takeIf { it.isFinite() && it > 0 } ?: continue
        accumulated += duration
        if (elapsed < accumulated) return phase
    }
    return if (accumulated == 0.0) IngestionPhase.UNKNOWN else IngestionPhase.ENDED
}
