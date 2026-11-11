// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.ui.tabs.journal.addingestion.time

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

@Composable
fun IngestionTimelineVisibility(isHidden: Boolean, onChangeHidden: (Boolean) -> Unit, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
        .toggleable(value = !isHidden, enabled = enabled, role = Role.Switch,
            onValueChange = { onChangeHidden(!it) }).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Show in timeline")
            Text("Controls this ingestion's curves. Doses, interactions and statistics still include the record. Subject and definition visibility settings still apply.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = !isHidden, enabled = enabled, onCheckedChange = null)
    }
}
