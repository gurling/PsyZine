// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.ui.utils

import androidx.lifecycle.SavedStateHandle
import com.isaakhanimann.journal.data.room.StaleJournalSession
import com.isaakhanimann.journal.data.room.experiences.JournalReadSnapshot
import com.isaakhanimann.journal.data.room.experiences.MissingJournalRecord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*

data class JournalReadState<T>(val value: T? = null, val loading: Boolean = true, val error: String? = null)

fun <T> journalReadSession(
    scope: CoroutineScope,
    savedState: SavedStateHandle,
    observe: (String?) -> Flow<JournalReadSnapshot<T>>,
): StateFlow<JournalReadState<T>> = flow {
    // Recover again on resume; never silently bind an already-open screen to a new journal.
    emitAll(observe(savedState.get<String>("journalGeneration")))
}.map { snapshot ->
    savedState["journalGeneration"] = snapshot.generation
    JournalReadState(value = snapshot.value, loading = false)
}.catch { error ->
    if (error is CancellationException) throw error
    val message = when (error) {
        is StaleJournalSession -> "The journal changed after this screen opened. Go back and choose the record again."
        is MissingJournalRecord -> "This experience was deleted. Go back and choose another record."
        else -> "The experience could not be loaded. Go back and try again."
    }
    emit(JournalReadState(loading = false, error = message))
}.stateIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 0, replayExpirationMillis = 0), JournalReadState())
