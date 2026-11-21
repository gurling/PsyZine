// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.psyzine

import com.isaakhanimann.journal.data.room.experiences.entities.*
import com.isaakhanimann.journal.ui.tabs.journal.experience.TimelineDisplayOption
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.buildTimelineDisplay
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class TimelineDisplayContentTest {
    private val time = Instant.parse("2026-11-11T12:00:00Z")
    private val rating = ShulginRating(1, time = time, option = ShulginRatingOption.PLUS, experienceId = 42)
    private val note = TimedNote(1, time = time.minusSeconds(60), note = "note", color = AdaptiveColor.BLUE,
        experienceId = 42, isPartOfTimeline = true)

    @Test fun visibleMarkersStillDrawWhenEveryIngestionCurveIsHidden() {
        val display = buildTimelineDisplay(emptyList(), listOf(rating), listOf(note), true)
        assertTrue(display is TimelineDisplayOption.Shown)
        val model = (display as TimelineDisplayOption.Shown).allTimelinesModel
        assertEquals(note.time, model.startTime)
        assertEquals(1, model.dataForRatings.size)
        assertEquals(1, model.timedNotes.size)
        assertTrue(model.groupDrawables.isEmpty())
        assertTrue(model.cursorModel.ingestions.isEmpty())
    }
    @Test fun hiddenAndUntimedMarkersDoNotCreateAnEmptyTimeline() {
        assertEquals(TimelineDisplayOption.NotWorthDrawing, buildTimelineDisplay(emptyList(),
            listOf(rating.copy(isHiddenInTimeline = true), rating.copy(id = 2, time = null)),
            listOf(note.copy(isPartOfTimeline = false)), false))
        assertEquals(TimelineDisplayOption.NotWorthDrawing, buildTimelineDisplay(emptyList(), emptyList(), emptyList(), true))
    }
    @Test fun globalHidingStillWinsAndEitherKindOfVisibleMarkerIsSufficient() {
        assertEquals(TimelineDisplayOption.Hidden, buildTimelineDisplay(emptyList(), listOf(rating), listOf(note), true, hidden = true))
        assertTrue(buildTimelineDisplay(emptyList(), listOf(rating), emptyList(), true) is TimelineDisplayOption.Shown)
        assertTrue(buildTimelineDisplay(emptyList(), emptyList(), listOf(note), false) is TimelineDisplayOption.Shown)
    }
    @Test fun showingAHiddenRatingRestoresItsMarkerWithoutChangingRatingOrTime() {
        val hidden = rating.copy(isHiddenInTimeline = true)
        assertEquals(TimelineDisplayOption.NotWorthDrawing,
            buildTimelineDisplay(emptyList(), listOf(hidden), emptyList(), true))
        val shown = hidden.copy(isHiddenInTimeline = false)
        val display = buildTimelineDisplay(emptyList(), listOf(shown), emptyList(), true) as TimelineDisplayOption.Shown
        assertEquals(rating.time, display.allTimelinesModel.dataForRatings.single().time)
        assertEquals(rating.option, display.allTimelinesModel.dataForRatings.single().option)
        assertEquals(hidden.id, shown.id)
        assertEquals(hidden.experienceId, shown.experienceId)
        assertEquals(hidden.creationDate, shown.creationDate)
        assertTrue(hidden.isHiddenInTimeline)
    }
}
