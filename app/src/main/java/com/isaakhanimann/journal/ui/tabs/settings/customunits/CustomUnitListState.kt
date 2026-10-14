// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.ui.tabs.settings.customunits

import com.isaakhanimann.journal.data.room.experiences.entities.CustomUnit
import kotlinx.coroutines.flow.*

data class CustomUnitListState(val units: List<CustomUnit> = emptyList(), val loading: Boolean = true, val error: String? = null)
fun Flow<List<CustomUnit>>.unitListState(): Flow<CustomUnitListState> = map { CustomUnitListState(it, loading = false) }
    .onStart { emit(CustomUnitListState()) }
    .catch { if (it is kotlinx.coroutines.CancellationException) throw it; emit(CustomUnitListState(loading = false, error = "Could not load custom units. Try again.")) }
