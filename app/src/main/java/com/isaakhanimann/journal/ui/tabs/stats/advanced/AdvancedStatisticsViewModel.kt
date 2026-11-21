// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.ui.tabs.stats.advanced

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.isaakhanimann.journal.data.definitions.*
import com.isaakhanimann.journal.data.room.LocalCalendar
import com.isaakhanimann.journal.data.substances.repositories.SubstanceRepository
import com.isaakhanimann.journal.ui.tabs.settings.combinations.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import java.time.LocalDate
import javax.inject.Inject

@Serializable object AdvancedStatisticsRoute
data class AdvancedStatisticsState(val loading: Boolean = true, val report: AdvancedStatisticsReport? = null,
    val request: StatisticsRequest = StatisticsRequest(), val error: String? = null)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AdvancedStatisticsViewModel @Inject constructor(browser: SubjectBrowserRepository, substances: SubstanceRepository,
    private val preferences: UserPreferences, calendar: LocalCalendar, private val saved: SavedStateHandle) : ViewModel() {
    private val retry = MutableStateFlow(0)
    var saving by mutableStateOf(false); private set
    var saveError by mutableStateOf<String?>(null); private set
    fun dismissError() { saveError = null }
    fun retry() { retry.value++ }
    fun consumer(name: String?) { saved["consumer"] = name; clearBucket() }
    fun subject(key: String?) { saved["subject"] = key; clearBucket() }
    fun grouping(enabled: Boolean) { saved["tagGrouping"] = enabled; clearBucket() }
    fun bucket(index: Int?) { saved["bucket"] = index }
    private fun clearBucket() { saved["bucket"] = null }
    fun granularity(value: StatisticsGranularity) { saved["granularity"] = value.name; clearBucket() }
    fun metric(value: StatisticsMetric) = persist(metric = value)
    fun period(value: StatisticsPeriod) = persist(period = value)
    /** Only valid date ranges are applied; invalid drafts remain in the dialog. */
    fun customRange(start: LocalDate, end: LocalDate) {
        persist(period = StatisticsPeriod.CUSTOM, start = start, end = end)
    }
    private fun persist(metric: StatisticsMetric? = null, period: StatisticsPeriod? = null,
        start: LocalDate? = null, end: LocalDate? = null) {
        if (saving) return
        saving = true
        viewModelScope.launch {
            try { preferences.saveAdvancedStatisticsSelection(metric, period, start, end); clearBucket() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { saveError = "Could not save this statistics choice. The previous choice is retained. Try again." }
            finally { saving = false }
        }
    }

    private val filters = combine(saved.getStateFlow<String?>("consumer", null), saved.getStateFlow<String?>("subject", null),
        saved.getStateFlow("tagGrouping", false)) { consumer, subject, grouping ->
        StatisticsRequest(consumer = consumer, subjectKey = subject, groupByPrimaryTag = grouping)
    }
    private val request = combine(filters, preferences.advancedStatisticsSelectionFlow,
        saved.getStateFlow<String?>("granularity", null)) { filter, selection, granularity ->
        filter.copy(selection = selection, customStart = selection.customStart, customEnd = selection.customEnd,
            granularity = StatisticsGranularity.entries.firstOrNull { it.name == granularity })
    }
    val state = retry.flatMapLatest {
        flow {
            substances.initialize()
            val catalog = substances.getAllSubstances().mapTo(hashSetOf()) { it.name }
            emitAll(combine(browser.observe(includeJournal = true), request, calendar.changes,
                saved.getStateFlow<Int?>("bucket", null)) { data, request, now, bucket ->
                val oldGeneration = saved.get<String>("generation")
                val effective = if (oldGeneration != null && oldGeneration != data.tags.generation) {
                    saved["subject"] = null; saved["bucket"] = null
                    request.copy(subjectKey = null)
                } else request
                saved["generation"] = data.tags.generation
                Input(data, effective, now, if (oldGeneration != null && oldGeneration != data.tags.generation) null else bucket)
            }.mapLatest { input ->
                try {
                    val report = withContext(Dispatchers.Default) {
                        val context = currentCoroutineContext()
                        projectAdvancedStatistics(input.data, input.request, input.now, catalog, selectedBucket = input.bucket) { context.ensureActive() }
                    }
                    AdvancedStatisticsState(false, report, input.request)
                } catch (e: CancellationException) { throw e }
                catch (e: IllegalArgumentException) { AdvancedStatisticsState(false, request = input.request, error = e.message ?: "Choose a valid date range.") }
            })
        }.catch { emit(AdvancedStatisticsState(false, error = "Could not load statistics. Your journal is unchanged. Try again.")) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), AdvancedStatisticsState())

    private data class Input(val data: SubjectBrowserSnapshot, val request: StatisticsRequest,
        val now: com.isaakhanimann.journal.data.room.CalendarNow, val bucket: Int?)
}
