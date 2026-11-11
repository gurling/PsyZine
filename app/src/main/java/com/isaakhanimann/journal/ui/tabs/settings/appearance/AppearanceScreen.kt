// PsyZine, 2026-10-11. GPL-3.0-or-later.
package com.isaakhanimann.journal.ui.tabs.settings.appearance

import androidx.compose.ui.text.style.TextOverflow

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.isaakhanimann.journal.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(viewModel: AppearanceViewModel = hiltViewModel()) {
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    Scaffold(topBar = { TopAppBar(title = { Text("Appearance", maxLines = 1, overflow = TextOverflow.Ellipsis) }) }) { padding ->
        LazyColumn(contentPadding = padding, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text("Choose a palette. Journal text keeps its familiar proportional type.", Modifier.padding(16.dp)) }
            viewModel.error?.let { message -> item { Text(message, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) } }
            items(ThemeId.entries, key = { it.key }) { id ->
                ThemePreviewCard(id, theme == id, !viewModel.saving) { viewModel.select(id) }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun ThemePreviewCard(id: ThemeId, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    PsyZineTheme(id) {
        Card(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).semantics { this.selected = selected },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text((if (selected) "✓ " else "") + id.label, style = MaterialTheme.typography.titleLarge)
                if (id == ThemeId.DYNAMIC) Text(
                    if (LocalPsyZinePalette.current.id == ThemeId.DYNAMIC) "Use Android's wallpaper-based colors and system light/dark mode."
                    else "System colors are unavailable. Uses Catppuccin Latte or Mocha to follow system light/dark mode.",
                    style = MaterialTheme.typography.bodySmall)
                Text("Saturday · 18:45", style = MaterialTheme.typography.labelSmall)
                Text("A quiet afternoon by the lake. A few thoughts to remember.")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SuggestionChip(onClick = onClick, enabled = enabled, label = { Text("Journal") })
                    val swatches = if (id == ThemeId.DYNAMIC) listOf(MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.error)
                        else LocalPsyZinePalette.current.accents.take(4)
                    swatches.forEach { color ->
                        Surface(color = color, shape = MaterialTheme.shapes.small, modifier = Modifier.size(24.dp)) {}
                    }
                }
            }
        }
    }
}
