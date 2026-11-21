// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.ui.utils

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import com.isaakhanimann.journal.data.room.JournalSessionGuard
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect

class JournalEditorSession(private val scope: CoroutineScope, private val guard: JournalSessionGuard,
    private val state: SavedStateHandle) {
    var loading by mutableStateOf(true); private set
    var busy by mutableStateOf(false); private set
    var ready by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var unavailableMessage by mutableStateOf<String?>(null); private set
    var generation: String? = null; private set
    private var generationMonitor: Job? = null
    fun dismissError() { error = null }
    private fun unavailable(message: String) {
        ready = false
        unavailableMessage = message
        error = message
    }

    fun load(action: suspend () -> Unit) {
        scope.launch {
            try {
                guard.readSession(state.get<String>("journalGeneration")) { token ->
                    generation = token
                    state["journalGeneration"] = token
                    action()
                }
                ready = true
                loading = false
                unavailableMessage = null
                error = null
                generationMonitor?.cancel()
                generationMonitor = scope.launch {
                    guard.generations.catch {
                        unavailable("The journal could not be checked. Close this screen and reopen the record.")
                    }.collect { current ->
                        if (current != generation) {
                            unavailable("The journal changed after this screen opened. Close it and choose the record again.")
                        }
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { unavailable(e.message ?: "Could not load this entry. Close this screen and try again."); loading = false }
        }
    }

    fun run(onSuccess: () -> Unit = {}, action: suspend () -> Unit) {
        if (!ready || busy) return
        busy = true
        scope.launch {
            try { action(); onSuccess() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (e is com.isaakhanimann.journal.data.room.StaleJournalSession)
                    unavailable(e.message ?: "The journal changed. Close this screen and choose the record again.")
                else error = e.message ?: "Could not save changes. Your draft is still open."
            }
            finally { busy = false }
        }
    }
}

@Composable
fun JournalRecordUnavailable(back: () -> Unit, message: String = "This record is unavailable. Go back and choose it again.") {
    androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(24.dp)) {
        androidx.compose.material3.Text(message)
        androidx.compose.material3.TextButton(onClick = back) { androidx.compose.material3.Text("Back") }
    }
}

@Composable
fun JournalEditorStatus(session: JournalEditorSession) {
    JournalActionErrorDialog(session.error, session::dismissError,
        title = if (session.ready) "Could not save changes" else "Entry unavailable")
    if (session.busy || session.loading && session.error == null) {
        androidx.compose.material3.AlertDialog(onDismissRequest = {}, title = { androidx.compose.material3.Text(if (session.loading) "Loading entry" else "Saving changes") },
            text = { androidx.compose.material3.CircularProgressIndicator() }, confirmButton = {})
    }
}

/** Hide unusable form actions, while leaving a failed-save draft open when the session is valid. */
@Composable
fun JournalEditorGate(session: JournalEditorSession, back: () -> Unit): Boolean {
    JournalEditorStatus(session)
    if (session.ready) return true
    if (!session.loading) {
        val message = session.unavailableMessage
        if (message == null) JournalRecordUnavailable(back) else JournalRecordUnavailable(back, message)
    }
    return false
}
