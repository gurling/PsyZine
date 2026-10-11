// PsyZine modifications, 2026. GPL-3.0-or-later; see COPYING.
package com.isaakhanimann.journal.ui.utils

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun JournalActionErrorDialog(message: String?, onDismiss: () -> Unit) {
    if (message != null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Could not save changes") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        )
    }
}
