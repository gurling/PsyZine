// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.ui.tabs.settings.customunits

import androidx.compose.material3.*
import androidx.compose.runtime.Composable

@Composable
fun CustomUnitEditorStatus(model: CustomUnitEditorViewModel, onClose: () -> Unit = {},
    onArchive: (() -> Unit)? = null) {
    if (model.loading || model.isSaving) {
        AlertDialog(onDismissRequest = {}, title = { Text(if (model.loading) "Loading unit" else "Saving changes") },
            text = { CircularProgressIndicator() }, confirmButton = {})
    }
    model.saveError?.let { message ->
        AlertDialog(onDismissRequest = if (model.ready) model::dismissSaveError else onClose, title = { Text(model.errorTitle) },
            text = { Text(message) }, confirmButton = {
                TextButton(onClick = if (model.requiresReopen) onClose else if (model.ready) model::dismissSaveError else model::retryLoad) {
                    Text(if (model.requiresReopen) "Close" else if (model.ready) "Keep editing" else "Retry")
                }
            }, dismissButton = {
                if (!model.requiresReopen) TextButton(onClick = onClose) { Text("Close") }
            })
    }
    model.usedUnitCount?.let { count ->
        AlertDialog(onDismissRequest = model::dismissUsedUnit, title = { Text("Archive this unit instead?") },
            text = { Text("This unit is used in $count ${if (count == 1) "ingestion" else "ingestions"}. Archiving hides it from new entries and preserves past entries. Only the archive status will be saved; other edits in this form will not be saved.") },
            confirmButton = { TextButton(onClick = { onArchive?.invoke() }) { Text("Archive saved unit") } },
            dismissButton = { TextButton(onClick = model::dismissUsedUnit) { Text("Keep editing") } })
    }
}
