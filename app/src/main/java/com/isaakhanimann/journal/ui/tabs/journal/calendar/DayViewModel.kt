/*
 * Copyright (c) 2024. Isaak Hanimann.
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

package com.isaakhanimann.journal.ui.tabs.journal.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.isaakhanimann.journal.data.room.*
import com.isaakhanimann.journal.data.room.experiences.ExperienceRepository
import com.isaakhanimann.journal.data.room.experiences.entities.AdaptiveColor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import java.time.*
import javax.inject.Inject

@HiltViewModel
class DayViewModel @Inject constructor(private val entries: ExperienceRepository, calendar: LocalCalendar) : ViewModel() {
    private val visibleMonths = MutableStateFlow(YearMonth.now() to YearMonth.now())
    fun showMonths(first: YearMonth, last: YearMonth) { visibleMonths.value = first to last }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val model = combine(visibleMonths, calendar.changes) { months, now ->
        val range = localDateRange(months.first.atDay(1), months.second.plusMonths(1).atDay(1), now.zone)
        Triple(range, now.zone, now.date)
    }.flatMapLatest { (range, zone, today) ->
        entries.observeCalendarIngestions(range.start, range.end).map { records ->
            val days = records.groupBy { it.ingestion.time.atZone(zone).toLocalDate() }.mapValues { (_, ingestions) ->
                ExperienceInfo(ingestions.map { it.ingestion.experienceId }.distinct(),
                    ingestions.map { it.substanceCompanion?.color ?: AdaptiveColor.RED }.distinct())
            }
            CalendarMonthInfo(today, days)
        }
    }.flowOn(kotlinx.coroutines.Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(0),
        CalendarMonthInfo(calendar.now().date, emptyMap()))
}

data class ExperienceInfo(val experienceIds: List<Int> = emptyList(), val colors: List<AdaptiveColor> = emptyList())

data class CalendarMonthInfo(val today: LocalDate, val days: Map<LocalDate, ExperienceInfo>)
