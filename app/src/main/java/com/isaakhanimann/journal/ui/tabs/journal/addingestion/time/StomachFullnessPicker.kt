// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.ui.tabs.journal.addingestion.time

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import com.isaakhanimann.journal.data.room.experiences.entities.StomachFullness

@Composable
fun StomachFullnessPicker(value: StomachFullness?, onChange: (StomachFullness?) -> Unit,
    enabled: Boolean = true, affectsTimeline: Boolean = true) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Text("Stomach fullness: ${value?.text ?: "Not recorded"}")
            }
            DropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
                val options: List<StomachFullness?> = listOf(null) + StomachFullness.entries
                options.forEach { option ->
                    DropdownMenuItem(text = { Text(option?.text ?: "Not recorded") },
                        modifier = Modifier.semantics { selected = value == option },
                        onClick = { expanded = false; onChange(option) })
                }
            }
        }
        Text(if (affectsTimeline) "Optional. Adjusts the estimated oral timeline; recorded times stay unchanged."
            else "This value is retained with the record and does not affect this route's timeline.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
