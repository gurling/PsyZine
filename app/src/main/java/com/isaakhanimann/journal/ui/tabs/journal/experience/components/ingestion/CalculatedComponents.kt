// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.ui.tabs.journal.experience.components.ingestion

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.DotRows
import com.isaakhanimann.journal.ui.tabs.journal.experience.models.CalculatedComponent
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatedComponents(recordId: Int, components: List<CalculatedComponent>, hideDots: Boolean) {
    if (components.isEmpty()) return
    var expanded by rememberSaveable(recordId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Text("Contains:", style = MaterialTheme.typography.labelMedium)
    components.take(20).forEach { CalculatedComponentRow(it, hideDots) }
    if (components.size > 20) {
        TextButton(onClick = { expanded = true }) { Text("Show all ${components.size} components") }
    }
    if (expanded) {
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { expanded = false }, sheetState = sheet) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text("Calculated components", Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleLarge)
                TextButton(modifier = Modifier.align(Alignment.End), onClick = {
                    scope.launch { sheet.hide() }.invokeOnCompletion { if (!sheet.isVisible) expanded = false }
                }) { Text("Close components") }
            }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.7f)
                .testTag("calculated-component-list"), contentPadding = PaddingValues(16.dp)) {
                items(components, key = { it.dose.subject.key + ":" + it.dose.componentPath.joinToString(".") }) {
                    CalculatedComponentRow(it, hideDots)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CalculatedComponentRow(component: CalculatedComponent, hideDots: Boolean) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${component.dose.name}: ${component.description}", style = MaterialTheme.typography.bodyMedium)
            if (!hideDots) component.numDots?.let { DotRows(it) }
        }
        if (!component.isShownAsCurve) Text("Not shown separately on timeline", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
