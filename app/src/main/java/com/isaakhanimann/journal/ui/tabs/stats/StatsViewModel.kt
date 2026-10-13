/*
 * Copyright (c) 2022-2023. Isaak Hanimann.
 * This file is part of PsychonautWiki Journal.
 *
 * PsychonautWiki Journal is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * PsychonautWiki Journal is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with PsychonautWiki Journal.  If not, see https://www.gnu.org/licenses/gpl-3.0.en.html.
 */

package com.isaakhanimann.journal.ui.tabs.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.isaakhanimann.journal.data.room.LocalCalendar
import com.isaakhanimann.journal.data.room.CalendarNow
import com.isaakhanimann.journal.data.room.DateRange
import com.isaakhanimann.journal.data.room.localDateRange
import com.isaakhanimann.journal.data.room.experiences.ExperienceRepository
import com.isaakhanimann.journal.data.room.experiences.entities.AdaptiveColor
import com.isaakhanimann.journal.data.room.experiences.relations.ExperienceWithIngestionsAndCompanions
import com.isaakhanimann.journal.data.room.experiences.relations.IngestionWithCompanionAndCustomUnit
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import java.time.*
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class StatsViewModel @Inject constructor(entries: ExperienceRepository, calendar: LocalCalendar) : ViewModel() {
    private val option = MutableStateFlow(TimePickerOption.WEEKS_26)
    private val consumer = MutableStateFlow<String?>(null)
    fun onTapOption(value: TimePickerOption) { option.value = value }
    fun onChangeConsumer(value: String?) { consumer.value = value }

    val statsModelFlow = combine(entries.getSortedExperiencesWithIngestionsAndCustomUnitsFlow(), option, consumer, calendar.changes) {
        experiences, selected, name, now -> aggregateStatistics(experiences, selected, name, now)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(0),
        StatsModel(TimePickerOption.WEEKS_26, false, "", emptyList(), emptyList(), null))

    val sortedConsumerNamesFlow = entries.getSortedIngestions(200).map { ingestions ->
        ingestions.mapNotNull { it.consumerName }.distinct()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), emptyList())
}

/** Oldest first. Calendar months/years include the current partial period. */
fun statisticsRanges(option: TimePickerOption, today: LocalDate, zone: ZoneId): List<DateRange> {
    val tomorrow = today.plusDays(1)
    return (option.bucketCount - 1 downTo 0).map { ago ->
        val start: LocalDate
        val end: LocalDate
        when (option) {
            TimePickerOption.MONTHS_12 -> {
                start = today.withDayOfMonth(1).minusMonths(ago.toLong())
                end = if (ago == 0) tomorrow else start.plusMonths(1)
            }
            TimePickerOption.YEARS_10 -> {
                start = today.withDayOfYear(1).minusYears(ago.toLong())
                end = if (ago == 0) tomorrow else start.plusYears(1)
            }
            else -> {
                val days = if (option == TimePickerOption.WEEKS_26) 7L else 1L
                end = tomorrow.minusDays(ago * days)
                start = end.minusDays(days)
            }
        }
        localDateRange(start, end, zone)
    }
}

fun aggregateStatistics(experiences: List<ExperienceWithIngestionsAndCompanions>, option: TimePickerOption,
    consumer: String?, now: CalendarNow): StatsModel {
    val ranges = statisticsRanges(option, now.date, now.zone)
    val relevant = experiences.filter { it.sortInstant >= ranges.first().start && it.sortInstant <= now.instant }
        .sortedWith(compareBy<ExperienceWithIngestionsAndCompanions> { it.sortInstant }.thenBy { it.experience.id })
    val filtered = relevant.associateWith { exp -> exp.ingestionsWithCompanionAndCustomUnit.filter { it.ingestion.consumerName == consumer } }
    val all = filtered.values.flatten()
    val colors = all.sortedBy { it.ingestion.id }.associate { it.ingestion.substanceName to (it.substanceCompanion?.color ?: AdaptiveColor.RED) }
    val stats = all.groupBy { it.ingestion.substanceName }.map { (name, records) ->
        StatItem(name, colors.getValue(name), filtered.values.count { list -> list.any { it.ingestion.substanceName == name } }, records.size,
            records.groupBy { it.ingestion.administrationRoute }.entries.sortedBy { it.key.name }.map { RouteCount(it.key, it.value.size) }, totalDose(records))
    }.sortedWith(compareByDescending<StatItem> { it.experienceCount }.thenBy { it.substanceName })
    val buckets = ranges.map { range ->
        relevant.filter { it.sortInstant in range }.flatMap { exp -> filtered.getValue(exp).map { it.ingestion.substanceName }.distinct() }
            .groupingBy { it }.eachCount().entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { ColorCount(colors.getValue(it.key), it.value, it.key) }
    }
    val start = ranges.first().start.atZone(now.zone).format(DateTimeFormatter.ofPattern("EEE, dd MMM yyyy"))
    return StatsModel(option, experiences.any { exp -> exp.ingestionsWithCompanionAndCustomUnit.any { it.ingestion.consumerName == consumer } },
        start, stats, buckets, consumer)
}

private fun totalDose(records: List<IngestionWithCompanionAndCustomUnit>): TotalDose? {
    val unit = records.firstOrNull()?.originalUnit ?: return null
    if (records.any { it.originalUnit != unit || it.pureDose == null || it.pureDose?.let { dose -> !dose.isFinite() || dose < 0 } == true }) return null
    val dose = records.sumOf { it.pureDose ?: 0.0 }
    val uncertainty = records.sumOf { it.pureDoseStandardDeviation ?: 0.0 }
    if (!dose.isFinite() || !uncertainty.isFinite() || uncertainty < 0) return null
    return TotalDose(dose, unit, records.any { it.isEstimate }, uncertainty.takeIf { it > 0 })
}

data class StatsModel(
    val selectedOption: TimePickerOption,
    val areThereAnyIngestions: Boolean,
    val startDateText: String,
    val statItems: List<StatItem>,
    val chartBuckets: List<List<ColorCount>>,
    val consumerName: String?
)

data class ColorCount(
    val color: AdaptiveColor,
    val count: Int,
    val substanceName: String? = null
)

data class StatItem(
    val substanceName: String,
    val color: AdaptiveColor,
    val experienceCount: Int,
    val ingestionCount: Int,
    val routeCounts: List<RouteCount>,
    val totalDose: TotalDose?,
)

data class RouteCount(
    val administrationRoute: AdministrationRoute,
    val count: Int
)

data class TotalDose(
    val dose: Double,
    val units: String,
    val isEstimate: Boolean,
    val estimatedDoseStandardDeviation: Double?
)

enum class TimePickerOption(val displayText: String, val longDisplayText: String, val tabIndex: Int, val bucketCount: Int) {
    DAYS_7("7D", "Last week", 0, 7),
    DAYS_30("30D", "Last month", 1, 30),
    WEEKS_26("26W", "Half year", 2, 26),
    MONTHS_12("12M", "Last year", 3, 12),
    YEARS_10("10Y", "10 years", 4, 10)
}
