// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.psyzine

import com.isaakhanimann.journal.data.active.ActiveExperience
import com.isaakhanimann.journal.data.active.hasRemainingNotificationTimeline
import com.isaakhanimann.journal.data.room.experiences.entities.AdaptiveColor
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import com.isaakhanimann.journal.data.substances.classes.roa.*
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.DataForOneEffectLine
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.AllTimelinesModel
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class ManualNotificationTimelineTest {
    private val start = Instant.parse("2026-11-10T12:00:00Z")
    private fun range(seconds: Float) = DurationRange(seconds, seconds, DurationUnits.SECONDS)
    private val duration = RoaDuration(range(10f), range(20f), range(30f), range(40f), null, null)
    private fun line(time: Instant = start) = DataForOneEffectLine("Example", AdministrationRoute.ORAL,
        duration, 1f, .5f, AdaptiveColor.BLUE, time, null)
    private fun active(now: Instant, lines: List<DataForOneEffectLine> = listOf(line())) = ActiveExperience(
        42, "journal", "Example", emptyList(), AllTimelinesModel(lines, emptyList(), emptyList(), true), start, now)

    @Test fun expiresAtCurveEndWithoutCountingGraphPaddingOrMinimumWidth() {
        val value = active(start.plusSeconds(99))
        assertTrue(value.timeline!!.widthInSeconds > 100f)
        assertTrue(value.hasRemainingNotificationTimeline())
        assertFalse(value.copy(updatedAt = start.plusSeconds(100)).hasRemainingNotificationTimeline())
        assertFalse(value.copy(updatedAt = start.plusSeconds(101)).hasRemainingNotificationTimeline())
        assertTrue(value.copy(updatedAt = start.plusSeconds(99).plusNanos(999_999_999)).hasRemainingNotificationTimeline())
    }
    @Test fun futureOnlyAndMissingTimelinesCannotStartManualNotifications() {
        assertFalse(active(start.minusSeconds(1)).hasRemainingNotificationTimeline())
        assertFalse(active(start).copy(timeline = null).hasRemainingNotificationTimeline())
        assertFalse(active(start, listOf(line().copy(roaDuration = null))).hasRemainingNotificationTimeline())
    }
    @Test fun latestCurveKeepsSelectionActiveBeyondTheAutomaticTwelveHourWindow() {
        val long = line().copy(roaDuration = duration.copy(offset = range(14 * 3600f)))
        assertTrue(active(start.plusSeconds(13 * 3600), listOf(long)).hasRemainingNotificationTimeline())
        val later = line(start.plusSeconds(200))
        assertTrue(active(start.plusSeconds(299), listOf(line(), later)).hasRemainingNotificationTimeline())
        assertFalse(active(start.plusSeconds(300), listOf(line(), later)).hasRemainingNotificationTimeline())
    }
}
