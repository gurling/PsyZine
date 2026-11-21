// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.psyzine

import com.isaakhanimann.journal.data.definitions.*
import com.isaakhanimann.journal.data.room.CalendarNow
import com.isaakhanimann.journal.data.room.experiences.entities.*
import com.isaakhanimann.journal.data.room.experiences.relations.ExperienceWithIngestionsTimedNotesAndRatings
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test
import java.time.*
import java.util.Locale

class AdvancedStatisticsTest {
    @Test fun currentBreakIncludesLaterRecordsOutsideTheSelectedDatesButNotOtherConsumers() {
        val snapshot = data(entry(1, listOf(raw(1, "2026-10-20T09:00:00Z"))),
            entry(2, listOf(raw(2, "2026-10-25T09:00:00Z"), raw(3, "2026-10-25T10:00:00Z").copy(consumerName = "Someone else"))))
        val selected = request(StatisticsPeriod.CUSTOM).copy(customStart = LocalDate.of(2026, 10, 20), customEnd = LocalDate.of(2026, 10, 20))
        val group = report(snapshot, selected).groups.single()
        assertEquals(listOf(1), group.records.map { it.ingestionId })
        assertEquals(2, group.currentBreak!!.ingestionId)
        assertEquals(Duration.ofHours(2), group.currentBreak.elapsed)
    }
    @Test fun experienceDistributionsSumMatchingRecordsAndExcludeTheWholeIncompleteExperience() {
        val snapshot = data(entry(1, listOf(raw(1, dose = 2.0), raw(2, dose = 4.0))),
            entry(2, listOf(raw(3, dose = 8.0), raw(4, dose = null))), entry(3, listOf(raw(5, dose = 10.0))))
        val dose = report(snapshot, request()).groups.single().doses.single()
        assertEquals(4, dose.distribution!!.sampleCount)
        val summary = dose.experienceDistribution!!
        assertEquals(2, summary.sampleCount)
        assertEquals(1, summary.excludedExperiences)
        assertEquals(8.0, summary.mean, 0.0)
        assertEquals(8.0, summary.median, 0.0)
        assertEquals(listOf(1), summary.minimum.experienceIds)
        assertEquals(listOf(3), summary.maximum.experienceIds)
    }

    @Test fun experienceDistributionsNormalizeMassButRejectIncompatibleUnitsAndOverflow() {
        val snapshot = data(entry(1, listOf(raw(1, dose = 0.002).copy(units = "g"), raw(2, dose = 4.0))),
            entry(2, listOf(raw(3), raw(4).copy(units = "ml"))),
            entry(3, listOf(raw(5, dose = Double.MAX_VALUE), raw(6, dose = Double.MAX_VALUE))))
        val doses = report(snapshot, request()).groups.single().doses
        val mg = doses.first { it.units == "mg" }
        assertEquals(6.0, mg.experienceDistribution!!.mean, 0.0)
        assertEquals(2, mg.excludedExperiences)
        assertNull(doses.first { it.units == "ml" }.experienceDistribution)
    }
    @Test fun intervalIntegrationKeepsSubjectConsumerAndBucketFilters() {
        val first = raw(1, "2026-10-24T09:00:00Z")
        val other = raw(2, "2026-10-24T10:00:00Z").copy(consumerName = "Someone else", endTime = Instant.parse("2026-11-01T00:00:00Z"))
        val next = raw(3, "2026-10-25T09:00:00Z")
        val snapshot = data(entry(1, listOf(first, other)), entry(2, listOf(next)))
        val selected = request().copy(subjectKey = "pw:A")
        assertEquals(Duration.ofHours(24), report(snapshot, selected).groups.single().intervals.betweenExperiences.single().elapsed)
        assertTrue(report(snapshot, selected, 6).groups.single().intervals.betweenExperiences.isEmpty())
    }
    @Test fun coOccurrencesUseWholeExperiencesButKeepTheConsumerAndDeduplicateRecords() {
        val a = raw(1)
        val b = raw(2, "2025-01-01T12:00:00Z", name = "B")
        val other = raw(3, name = "Other").copy(consumerName = "Someone else")
        val snapshot = data(entry(1, listOf(a, b, b, other)))
        val result = report(snapshot, request().copy(subjectKey = "pw:A"))
        val companions = result.groups.single().coOccurrences
        assertEquals(listOf("pw:B"), companions.map { it.subject.key })
        assertEquals(listOf(1), companions.single().experienceIds)
        assertEquals(1.0, result.buckets.sumOf { it ?: 0.0 }, 0.0)
        assertEquals(1, result.groups.single().ingestionCount)
    }

    @Test fun coOccurrencesFollowSelectedBucketsAndHaveAnEmptyState() {
        val snapshot = data(entry(1, listOf(raw(1, "2026-10-24T12:00:00Z"), raw(2, name = "B"))),
            entry(2, listOf(raw(3))))
        val selected = report(snapshot, request().copy(subjectKey = "pw:A"), 6)
        assertTrue(selected.groups.single().coOccurrences.isEmpty())
        val all = report(snapshot, request().copy(subjectKey = "pw:A"))
        assertEquals(listOf(1), all.groups.single().coOccurrences.single().experienceIds)
    }
    private val zone = ZoneId.of("Europe/Prague")
    private val now = CalendarNow(Instant.parse("2026-10-25T11:00:00Z"), zone)
    private val locale = Locale.forLanguageTag("cs-CZ")
    private fun raw(id: Int, time: String = "2026-10-25T09:00:00Z", dose: Double? = 2.0, name: String = "A") =
        Ingestion(id, name, Instant.parse(time), administrationRoute = AdministrationRoute.ORAL, dose = dose,
            isDoseAnEstimate = false, estimatedDoseStandardDeviation = null, units = "mg", experienceId = id,
            notes = null, stomachFullness = null, consumerName = null, customUnitId = null)
    private fun entry(id: Int, rows: List<Ingestion>, sort: Instant = rows.minOf { it.time }) =
        ExperienceWithIngestionsTimedNotesAndRatings(Experience(id, "Entry $id", "", sortDate = sort, location = null), rows, emptyList(), emptyList())
    private fun data(vararg entries: ExperienceWithIngestionsTimedNotesAndRatings, definitions: List<DefinitionWithContent> = emptyList(),
        tags: TagFilterSnapshot = TagFilterSnapshot("generation", emptyList(), emptyMap())) =
        SubjectBrowserSnapshot(tags, definitions, emptyList(), emptyList(), entries.toList())
    private fun report(data: SubjectBrowserSnapshot, request: StatisticsRequest = StatisticsRequest(), bucket: Int? = null) =
        projectAdvancedStatistics(data, request, now, setOf("A", "B"), locale, bucket)
    private fun request(period: StatisticsPeriod = StatisticsPeriod.DAYS_7, metric: StatisticsMetric = StatisticsMetric.INGESTIONS) =
        StatisticsRequest(StatisticsSelection(metric, period))

    @Test fun weeksFollowLocaleAndIncludeTheRestOfTheCurrentWeek() {
        val today = LocalDate.of(2026, 10, 21)
        val czech = advancedStatisticsWindow(request(StatisticsPeriod.WEEKS_12), today, null, locale)
        val us = advancedStatisticsWindow(request(StatisticsPeriod.WEEKS_12), today, null, Locale.US)
        assertEquals(LocalDate.of(2026, 10, 25), czech.endInclusive)
        assertEquals(LocalDate.of(2026, 10, 24), us.endInclusive)
        assertEquals(12, czech.buckets.size)
    }

    @Test fun customRangeChoosesTheFinestCompatibleGranularityAndClipsBucketDetails() {
        val selected = request(StatisticsPeriod.CUSTOM).copy(customStart = LocalDate.of(2026, 10, 10), customEnd = LocalDate.of(2026, 11, 14))
        val window = advancedStatisticsWindow(selected, now.date, null, locale)
        assertEquals(StatisticsGranularity.WEEK, window.granularity)
        assertEquals(6, window.buckets.size)
        assertEquals(StatisticsBucketRange(LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 11)), window.buckets.first())
        assertEquals(LocalDate.of(2026, 11, 14), window.buckets.last().endInclusive)
        assertEquals(StatisticsGranularity.MONTH, advancedStatisticsWindow(selected.copy(granularity = StatisticsGranularity.MONTH), now.date, null, locale).granularity)
        assertThrows(IllegalArgumentException::class.java) { advancedStatisticsWindow(selected.copy(customEnd = selected.customStart!!.minusDays(1)), now.date, null, locale) }
    }

    @Test fun yearsUseGlobalEarliestIngestionWithoutCustomRangeBinCap() {
        val old = raw(1, "1900-01-01T00:00:00Z").copy(consumerName = "Someone else")
        val result = report(data(entry(1, listOf(old)), entry(2, listOf(raw(2)))), request(StatisticsPeriod.YEARS))
        assertEquals(127, result.buckets.size)
        assertEquals(1.0, result.buckets.last()!!, 0.0)
        assertEquals(0.0, result.buckets.first()!!, 0.0)
    }

    @Test fun customLocalDaysIncludeBothMidnightsAcrossBothDstTransitions() {
        listOf(LocalDate.of(2026, 3, 29), LocalDate.of(2026, 10, 25)).forEach { day ->
            val start = day.atStartOfDay(zone).toInstant()
            val end = day.plusDays(1).atStartOfDay(zone).toInstant()
            val rows = listOf(raw(1).copy(time = start), raw(2).copy(time = end.minusNanos(1)), raw(3).copy(time = end))
            val selected = request(StatisticsPeriod.CUSTOM).copy(customStart = day, customEnd = day)
            val result = report(data(entry(1, rows)), selected)
            assertEquals(2.0, result.buckets.single()!!, 0.0)
        }
    }

    @Test fun metricsUseDifferentAnchorsAndIncludeFutureRecordsInsideTheCalendarRange() {
        val rows = listOf(raw(1, "2026-10-25T20:00:00Z"), raw(2, "2026-10-26T00:00:00Z"))
        val snapshot = data(entry(1, rows, Instant.parse("2026-10-20T10:00:00Z")))
        val experiences = report(snapshot, request(metric = StatisticsMetric.EXPERIENCES))
        assertEquals(1.0, experiences.buckets.sumOf { it ?: 0.0 }, 0.0)
        val ingestions = report(snapshot, request())
        assertEquals(1.0, ingestions.buckets.last()!!, 0.0)
        assertEquals(1, ingestions.groups.single().ingestionCount)
    }

    @Test fun repeatedHiddenLeafPathsAddDosesWithoutMultiplyingRecordCounts() {
        val unit = CustomUnit(7, null, "Blend", Instant.EPOCH, null, null, null, false, false, "capsule", null, null, "")
        val parts = listOf(DoseComponent(1, 7, "A", "mg", dose = 3.0, isHiddenInTimeline = true),
            DoseComponent(2, 7, "A", "mg", dose = 5.0), DoseComponent(3, 7, "B", "mg", dose = null))
        val ingestion = raw(1).copy(customUnitId = 7, substanceName = null, isHiddenInTimeline = true)
        val snapshot = data(entry(1, listOf(ingestion)), definitions = listOf(DefinitionWithContent(unit, parts, emptyList())))
        val result = report(snapshot, request())
        assertEquals(1.0, result.buckets.last()!!, 0.0)
        assertEquals(1, result.groups.first { it.key == "pw:A" }.ingestionCount)
        assertEquals("A", result.groups.first { it.key == "pw:A" }.name)
        assertEquals(16.0, result.groups.first { it.key == "pw:A" }.doses.single().knownTotal!!, 0.0)
        assertNull(result.groups.first { it.key == "pw:B" }.doses.single().knownTotal)
        val dosage = report(snapshot, request(metric = StatisticsMetric.DOSAGE).copy(subjectKey = "pw:A"))
        assertEquals(16.0, dosage.buckets.last()!!, 0.0)
    }

    @Test fun unknownDosesStayUnknownAndMassConversionPreservesKnownTotals() {
        val rows = listOf(raw(1, dose = null), raw(2, dose = 0.003).copy(units = "g"), raw(3, dose = 2.0))
        val result = report(data(entry(1, rows)), request(metric = StatisticsMetric.DOSAGE).copy(subjectKey = "pw:A"))
        val dose = result.groups.single().doses.single()
        assertEquals("mg", dose.units)
        assertEquals(5.0, dose.knownTotal!!, 1e-12)
        assertEquals(2, dose.knownRecords)
        assertEquals(1, dose.unknownRecords)
        assertNull(report(data(entry(1, listOf(rows.first()))), request(metric = StatisticsMetric.DOSAGE).copy(subjectKey = "pw:A")).buckets.last())
        assertTrue(report(data(entry(1, rows)), request(metric = StatisticsMetric.DOSAGE)).dosageNeedsSubject)
    }

    @Test fun primaryTagGroupingDoesNotCountSecondaryMembershipsOrDuplicateRawIds() {
        val tags = TagFilterSnapshot("generation", listOf(SubstanceTag("primary", "Primary", "RED"), SubstanceTag("secondary", "Secondary", "BLUE")),
            mapOf("pw:A" to setOf("primary", "secondary")), mapOf("pw:A" to "primary"))
        val result = report(data(entry(1, listOf(raw(1), raw(1))), tags = tags), request().copy(groupByPrimaryTag = true))
        assertEquals("primary", result.groups.single().key)
        assertEquals(1, result.groups.single().ingestionCount)
        assertEquals(1.0, result.buckets.last()!!, 0.0)
    }

    @Test fun selectedBucketFiltersRecordsAndEmptyConsumerMeansYouButWhitespaceDoesNot() {
        val rows = listOf(raw(1, "2026-10-24T12:00:00Z"), raw(2).copy(consumerName = ""), raw(3).copy(consumerName = " "))
        val result = report(data(entry(1, rows)), request(), 6)
        assertEquals(listOf(2), result.groups.single().records.map { it.ingestionId })
        assertEquals(1.0, result.buckets[5]!!, 0.0)
        assertEquals(1.0, result.buckets[6]!!, 0.0)
    }

    @Test fun missingDefinitionsAndInvalidNumbersRetainCountedRecordsAndCancellationEscapes() {
        val rows = listOf(raw(1).copy(customUnitId = 999, substanceName = null), raw(2, dose = Double.NaN))
        val snapshot = data(entry(1, rows))
        val result = report(snapshot, request())
        assertEquals(2.0, result.buckets.last()!!, 0.0)
        assertEquals(2, result.invalidDefinitions)
        assertTrue(result.groups.all { it.doses.single().knownTotal == null })
        assertThrows(CancellationException::class.java) {
            projectAdvancedStatistics(snapshot, request(), now, emptySet(), locale) { throw CancellationException() }
        }
    }

    @Test fun distributionsExcludeIncompleteRecordsAndRetainAllTiedExtremeIds() {
        val distribution = statisticsDistribution(listOf(7 to 3.0, 3 to 1.0, 2 to 1.0, 8 to 5.0), 6)!!
        assertEquals(2.5, distribution.mean, 0.0)
        assertEquals(2.0, distribution.median, 0.0)
        assertEquals(listOf(2, 3), distribution.minimum.ingestionIds)
        assertEquals(listOf(8), distribution.maximum.ingestionIds)
        assertEquals(2, distribution.excludedRecords)
        val rows = listOf(raw(1, dose = null), raw(2, dose = 2.0), raw(3, dose = 4.0))
        val actual = report(data(entry(1, rows)), request()).groups.single().doses.single().distribution!!
        assertEquals(2, actual.sampleCount)
        assertEquals(1, actual.excludedRecords)
        assertEquals(3.0, actual.mean, 0.0)
    }

    @Test fun distributionAveragesAvoidOverflowAndPreserveSubnormalValues() {
        assertEquals(Double.MAX_VALUE, statisticsDistribution(listOf(1 to Double.MAX_VALUE, 2 to Double.MAX_VALUE), 2)!!.mean, 0.0)
        val tiny = statisticsDistribution(listOf(1 to Double.MIN_VALUE, 2 to Double.MIN_VALUE), 2)!!
        assertEquals(Double.MIN_VALUE, tiny.mean, 0.0)
        assertEquals(Double.MIN_VALUE, tiny.median, 0.0)
        assertNull(statisticsDistribution(emptyList(), 2))
        assertEquals("4.9E-324", com.isaakhanimann.journal.ui.tabs.stats.advanced.statisticsQuantityText(Double.MIN_VALUE))
    }
}
