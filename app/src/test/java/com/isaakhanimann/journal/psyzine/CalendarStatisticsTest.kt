// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.psyzine

import com.isaakhanimann.journal.data.room.*
import com.isaakhanimann.journal.data.room.experiences.entities.*
import com.isaakhanimann.journal.data.room.experiences.relations.*
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import com.isaakhanimann.journal.ui.tabs.stats.*
import com.isaakhanimann.journal.ui.tabs.search.substance.roa.*
import org.junit.Test
import org.junit.Assert.*
import java.time.*
import kotlin.random.Random

class CalendarStatisticsTest {
    private val zone = ZoneId.of("Europe/Prague")
    private val now = CalendarNow(Instant.parse("2026-10-12T10:00:00Z"), zone)
    private fun ingestion(time: Instant, consumer: String? = null, name: String = "Caffeine", id: Int = 1) =
        IngestionWithCompanionAndCustomUnit(Ingestion(id, name, time, administrationRoute = AdministrationRoute.ORAL,
            dose = 0.0001, isDoseAnEstimate = true, estimatedDoseStandardDeviation = 1.234567, units = "mg", experienceId = 1,
            notes = null, stomachFullness = null, consumerName = consumer, customUnitId = null), null, null)
    private fun experience(vararg values: IngestionWithCompanionAndCustomUnit) = ExperienceWithIngestionsAndCompanions(
        Experience(1, "fixture", "", sortDate = now.instant, location = null), values.toList())

    @Test fun pragueSpringDayHasTwentyThreeHoursAndAutumnDayHasTwentyFive() {
        listOf(LocalDate.of(2026,3,29) to 23L, LocalDate.of(2026,10,25) to 25L).forEach { (day,hours) ->
            val range = localDateRange(day, day.plusDays(1), zone)
            assertEquals(hours, Duration.between(range.start,range.end).toHours())
            assertTrue(range.start in range); assertFalse(range.end in range)
        }
    }
    @Test fun leapMonthAndPartialCalendarPeriodsHaveExactBoundaries() {
        val day = LocalDate.of(2024,2,29)
        val months = statisticsRanges(TimePickerOption.MONTHS_12, day, zone)
        assertEquals(LocalDate.of(2023,3,1).atStartOfDay(zone).toInstant(), months.first().start)
        assertEquals(LocalDate.of(2024,2,1).atStartOfDay(zone).toInstant(), months.last().start)
        assertEquals(LocalDate.of(2024,3,1).atStartOfDay(zone).toInstant(), months.last().end)
        val years = statisticsRanges(TimePickerOption.YEARS_10, day, zone)
        assertEquals(LocalDate.of(2015,1,1).atStartOfDay(zone).toInstant(), years.first().start)
        assertEquals(LocalDate.of(2024,1,1).atStartOfDay(zone).toInstant(), years.last().start)
    }
    @Test fun dayAndWeekBucketsCoverAdjacentLocalDatesAcrossDst() {
        val day = LocalDate.of(2026,3,30)
        for (option in listOf(TimePickerOption.DAYS_7,TimePickerOption.DAYS_30,TimePickerOption.WEEKS_26)) {
            val ranges = statisticsRanges(option,day,zone)
            ranges.zipWithNext().forEach { (a,b) -> assertEquals(a.end,b.start) }
            assertEquals(day.plusDays(1).atStartOfDay(zone).toInstant(), ranges.last().end)
            assertEquals(day.plusDays(1).minusDays(if(option==TimePickerOption.WEEKS_26)182 else option.bucketCount.toLong()).atStartOfDay(zone).toInstant(),ranges.first().start)
        }
    }
    @Test fun reorderedChildrenAndConsumerFiltersDoNotChangeExperienceAnchor() {
        val older = ingestion(now.instant.minusSeconds(8*86400), "other")
        val recent = ingestion(now.instant.minusSeconds(60))
        val first = aggregateStatistics(listOf(experience(recent,older)),TimePickerOption.DAYS_7,null,now)
        val second = aggregateStatistics(listOf(experience(older,recent)),TimePickerOption.DAYS_7,null,now)
        assertEquals(first,second); assertTrue(first.statItems.isEmpty())
    }
    @Test fun missingCompanionStillCountsAndFutureAnchorIsExcluded() {
        val today = experience(ingestion(now.instant.minusSeconds(60)))
        val future = experience(ingestion(now.instant.plusSeconds(60)))
        val stats = aggregateStatistics(listOf(future,today), TimePickerOption.DAYS_7,null,now)
        assertEquals(1,stats.statItems.single().ingestionCount)
        assertEquals(AdaptiveColor.RED,stats.statItems.single().color)
        assertEquals(1,stats.chartBuckets.flatten().sumOf { it.count })
    }
    @Test fun emptyExperienceUsesStoredSortDateAndZoneChangesMoveMidnightBoundary() {
        assertEquals(now.instant,experience().sortInstant)
        val prague = statisticsRanges(TimePickerOption.DAYS_7,now.date,zone)
        val utc = statisticsRanges(TimePickerOption.DAYS_7,now.date,ZoneOffset.UTC)
        assertNotEquals(prague.first().start,utc.first().start)
    }
    @Test fun unchangedEditorDatesPreserveTheLaterDstFoldInstant() {
        val original=Instant.parse("2026-10-25T01:30:00Z")
        val local=original.atZone(zone).toLocalDateTime()
        assertNotEquals(original,local.atZone(zone).toInstant())
        assertEquals(original,com.isaakhanimann.journal.ui.utils.preservedInstant(local,local,original,zone))
    }
    @Test fun unchangedEditorDatesSurviveZoneChangeAndExplicitEditsUseSelectedZone() {
        val original=Instant.parse("2026-10-25T01:30:00Z")
        val local=original.atZone(zone).toLocalDateTime()
        assertEquals(original,com.isaakhanimann.journal.ui.utils.preservedInstant(local,local,original,ZoneOffset.UTC))
        val edited=local.plusHours(1)
        assertEquals(edited.atZone(zone).toInstant(),com.isaakhanimann.journal.ui.utils.preservedInstant(edited,local,original,zone))
    }

}
