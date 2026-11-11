// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.ui.utils

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

@Composable
fun TimelineVisibilitySwitch(isHidden: Boolean, onChangeHidden: (Boolean) -> Unit,
    description: String, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
        .toggleable(value = !isHidden, enabled = enabled, role = Role.Switch,
            onValueChange = { onChangeHidden(!it) }).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Show in timeline")
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = !isHidden, enabled = enabled, onCheckedChange = null)
    }
}
