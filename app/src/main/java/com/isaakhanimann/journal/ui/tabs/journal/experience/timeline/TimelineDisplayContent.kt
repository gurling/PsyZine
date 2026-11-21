// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.ui.tabs.journal.experience.timeline

import com.isaakhanimann.journal.data.room.experiences.entities.ShulginRating
import com.isaakhanimann.journal.data.room.experiences.entities.TimedNote
import com.isaakhanimann.journal.ui.tabs.journal.experience.TimelineDisplayOption
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.DataForOneEffectLine

/** Marker visibility is independent of whether any ingestion curves remain visible. */
fun buildTimelineDisplay(lines: List<DataForOneEffectLine>, ratings: List<ShulginRating>, notes: List<TimedNote>,
    independentHeights: Boolean, hidden: Boolean = false): TimelineDisplayOption {
    if (hidden) return TimelineDisplayOption.Hidden
    val ratingMarkers = ratings.filterNot { it.isHiddenInTimeline }.mapNotNull { rating ->
        rating.time?.let { DataForOneRating(it, rating.option) }
    }
    val noteMarkers = notes.filter { it.isPartOfTimeline }.map { DataForOneTimedNote(it.time, it.color) }
    if (lines.none { it.roaDuration != null } && ratingMarkers.isEmpty() && noteMarkers.isEmpty())
        return TimelineDisplayOption.NotWorthDrawing
    return TimelineDisplayOption.Shown(AllTimelinesModel(lines, ratingMarkers, noteMarkers, independentHeights))
}
