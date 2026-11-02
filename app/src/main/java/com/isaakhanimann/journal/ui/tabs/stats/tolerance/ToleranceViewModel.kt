// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.ui.tabs.stats.tolerance

import androidx.compose.runtime.*
import androidx.lifecycle.*
import androidx.navigation.toRoute
import com.isaakhanimann.journal.data.definitions.*
import com.isaakhanimann.journal.data.room.LocalCalendar
import com.isaakhanimann.journal.ui.tabs.settings.combinations.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import java.time.ZoneId
import javax.inject.Inject

@Serializable data class ToleranceRoute(val subjectKey: String? = null, val generation: String? = null)
data class ToleranceUiState(val loading: Boolean = true, val report: ToleranceReport? = null,
    val period: TolerancePeriod = TolerancePeriod.PAST_2M, val zone: ZoneId = ZoneId.systemDefault(),
    val error: String? = null, val subjectKey: String? = null)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ToleranceViewModel @Inject constructor(repo: ToleranceRepository, calendar: LocalCalendar,
    private val preferences: UserPreferences, savedState: SavedStateHandle) : ViewModel() {
    private val route = savedState.toRoute<ToleranceRoute>()
    private val retry = MutableStateFlow(0)
    var saving by mutableStateOf(false); private set
    var saveError by mutableStateOf<String?>(null); private set
    fun retry() { retry.value++ }
    fun dismissError() { saveError = null }
    fun period(value: TolerancePeriod) {
        if (saving) return
        saving = true
        viewModelScope.launch {
            try { preferences.saveToleranceStatsPeriod(value) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { saveError = "Could not save this period. The previous period is retained. Try again." }
            finally { saving = false }
        }
    }
    val state = retry.flatMapLatest {
        combine(calendar.changes, preferences.toleranceStatsPeriodFlow) { _, period -> calendar.now() to period }
            .flatMapLatest { (now, period) ->
                repo.observe(calendar::now, period).map { report ->
                    if (route.generation != null && route.generation != report.generation)
                        ToleranceUiState(false, period = period, zone = report.zone, error = "The journal was restored or deleted. Reopen this chart from Statistics.")
                    else ToleranceUiState(false, report, period, report.zone, subjectKey = route.subjectKey)
                }.onStart { emit(ToleranceUiState(period = period, zone = now.zone)) }
            }.catch { emit(ToleranceUiState(false, error = "Could not load tolerance intervals. Your journal is unchanged. Try again.")) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), ToleranceUiState())
}
