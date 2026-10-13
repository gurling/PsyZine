// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.ui.utils

import androidx.compose.runtime.*
import androidx.lifecycle.SavedStateHandle
import com.isaakhanimann.journal.data.room.JournalSessionGuard
import kotlinx.coroutines.*

class JournalEditorSession(private val scope: CoroutineScope, private val guard: JournalSessionGuard,
    private val state: SavedStateHandle) {
    var loading by mutableStateOf(true); private set
    var busy by mutableStateOf(false); private set
    var ready by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var generation: String? = null; private set
    fun dismissError() { error = null }

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
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "Could not load this entry. Close this screen and try again."; loading = false }
        }
    }

    fun run(onSuccess: () -> Unit = {}, action: suspend () -> Unit) {
        if (!ready || busy) return
        busy = true
        scope.launch {
            try { action(); onSuccess() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "Could not save changes. Your draft is still open." }
            finally { busy = false }
        }
    }
}

@Composable
fun JournalEditorStatus(session: JournalEditorSession) {
    JournalActionErrorDialog(session.error, session::dismissError)
    if (session.busy || session.loading && session.error == null) {
        androidx.compose.material3.AlertDialog(onDismissRequest = {}, title = { androidx.compose.material3.Text(if (session.loading) "Loading entry" else "Saving changes") },
            text = { androidx.compose.material3.CircularProgressIndicator() }, confirmButton = {})
    }
}
