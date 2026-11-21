// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.ui.tabs.settings.about

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.isaakhanimann.journal.BuildConfig

private val releaseNotes = listOf(
    "Journal and editing" to listOf(
        "Preserve dose precision when editing and reject invalid numeric input with readable errors.",
        "Keep stale screens from writing to entries replaced by a restore, and keep failed saves open for correction.",
        "Record native definitions and ingredients, organize subjects with tags, and search across journal records.",
    ),
    "Timelines and statistics" to listOf(
        "Show ingredient timelines and quantity details, with optional grouped readouts and independent section visibility.",
        "Explore calendar statistics, quantity distributions, co-occurrences and recorded breaks with links to contributing entries.",
        "View current tolerance estimates from statistics or an experience.",
    ),
    "Personalization" to listOf(
        "Remember calendar/relative date display and recent/calendar time-entry choices.",
        "Use recent-time presets for point or range ingestions without resetting entered form values when switching modes.",
        "Choose Android system colors alongside the thirteen existing palettes and Follow system.",
    ),
    "Notifications and data" to listOf(
        "Select an experience for the live notification, stop it, or open ingestion logging from the notification.",
        "Keep full photo backups and support older backup formats while retaining native definitions, tags and optional display choices.",
        "Preview a minimal support report before explicitly copying or sharing it.",
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReleaseNotesScreen(back: () -> Unit) {
    Scaffold(topBar = {
        TopAppBar(title = { Text("Release notes") }, navigationIcon = {
            IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
        })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item(key = "version") {
                Text("Included in this build", style = MaterialTheme.typography.titleLarge)
                Text("PsyZine ${BuildConfig.VERSION_NAME} · code ${BuildConfig.VERSION_CODE}", style = MaterialTheme.typography.bodySmall)
            }
            releaseNotes.forEach { (title, changes) ->
                item(key = title) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(title, style = MaterialTheme.typography.titleMedium)
                        changes.forEach { Text("• $it") }
                    }
                }
            }
        }
    }
}
