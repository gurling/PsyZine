// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.ui.tabs.stats.tolerance

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.isaakhanimann.journal.data.definitions.*
import com.isaakhanimann.journal.data.room.experiences.entities.AdaptiveColor
import com.isaakhanimann.journal.ui.theme.LocalPsyZinePalette
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun ToleranceScreen(back: () -> Unit, openSubject: (String, String) -> Unit, model: ToleranceViewModel = hiltViewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    ToleranceScreenContent(state, model.saving, model::period, model::retry, back, openSubject)
    model.saveError?.let { message -> AlertDialog(onDismissRequest = model::dismissError, title = { Text("Period could not be saved") },
        text = { Text(message) }, confirmButton = { TextButton(onClick = model::dismissError) { Text("OK") } }) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToleranceScreenContent(state: ToleranceUiState, saving: Boolean, period: (TolerancePeriod) -> Unit,
    retry: () -> Unit, back: () -> Unit, openSubject: (String, String) -> Unit) {
    var choosingPeriod by remember { mutableStateOf(false) }
    val report = state.report
    val rows = report?.projection?.rows.orEmpty().filter { state.subjectKey == null || it.subject.key == state.subjectKey }
    val end = report?.let { value -> maxOf(value.cutoff, rows.flatMap { it.bands.values.flatten() }.maxOfOrNull { it.end } ?: value.cutoff) }
    Scaffold(topBar = { TopAppBar(title = { Text("Tolerance") }, navigationIcon = {
        IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
    }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("tolerance-list"), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Box {
                    OutlinedButton(onClick = { choosingPeriod = true }, enabled = !saving) { Text(state.period.label) }
                    DropdownMenu(choosingPeriod, { choosingPeriod = false }) {
                        TolerancePeriod.entries.forEach { option -> DropdownMenuItem(text = { Text(option.label) },
                            onClick = { choosingPeriod = false; period(option) }) }
                    }
                }
            }
            item { Text("Your recorded ingestions. Estimated interval bands use local dates and available tolerance metadata. Missing values remain unknown.") }
            when {
                state.loading -> item { CircularProgressIndicator(); Text("Loading tolerance intervals") }
                state.error != null -> item { Text(state.error, Modifier.semantics { liveRegion = LiveRegionMode.Polite }); TextButton(onClick = retry) { Text("Retry") } }
                report != null && end != null -> {
                    item {
                        Text("${date(report.start, state.zone)} – ${date(end, state.zone)}", style = MaterialTheme.typography.titleMedium)
                        Text("Recorded through ${date(report.cutoff, state.zone)}. Forecast intervals may extend beyond this date.")
                        Text("Past 1M is 30 elapsed days; Past 2M is 60. Durations extend from each recorded local day's midnight.")
                        Text("Times in ${state.zone.id}")
                    }
                    if (rows.isEmpty()) item { Text("No intervals in this view. A half or zero value longer than 24 hours is needed.") }
                    items(rows, key = { it.subject.key }) { row ->
                        val palette = LocalPsyZinePalette.current
                        val tint = AdaptiveColor.entries.firstOrNull { it.name == row.color }?.getComposeColor(palette) ?: MaterialTheme.colorScheme.primary
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                val label = row.name + if (row.subject is SubjectId.Definition) "*" else ""
                                Text(label, style = MaterialTheme.typography.titleMedium)
                                if (row.subject is SubjectId.Definition) Text("Custom definition", style = MaterialTheme.typography.bodySmall)
                                ToleranceBands(row, report.start, end, tint)
                                row.bands.forEach { (kind, intervals) ->
                                    intervals.forEach { interval -> Text("${bandLabel(kind)}: ${dateTime(interval.start, state.zone)} – ${dateTime(interval.end, state.zone)}") }
                                }
                                if (state.subjectKey == null) TextButton(onClick = { openSubject(row.subject.key, report.generation) },
                                    modifier = Modifier.semantics { contentDescription = "Open tolerance for ${row.name}" }) { Text("Full view") }
                            }
                        }
                    }
                    val missing = report.projection.missingMetadata.filter { state.subjectKey == null || it.key == state.subjectKey }
                    if (missing.isNotEmpty()) item { Text("${missing.size} ${if (missing.size == 1) "subject has" else "subjects have"} no usable tolerance interval metadata.") }
                    items(missing.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { report.projection.subjectNames[it].orEmpty() }), key = { "missing:${it.key}" }) { subject ->
                        Text(report.projection.subjectNames[subject] ?: "Missing subject metadata")
                    }
                    if (report.unresolvedDefinitions.isNotEmpty()) item { Text("Some custom definitions could not be resolved. Their journal records remain available; reopen the definition to correct missing components.") }
                }
            }
        }
    }
}

@Composable
private fun ToleranceBands(row: SubjectToleranceIntervals, start: Instant, end: Instant, tint: Color) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    fun seconds(a: Instant, b: Instant): Double = Duration.between(a, b).let { it.seconds.toDouble() + it.nano / 1_000_000_000.0 }
    val total = seconds(start, end).coerceAtLeast(1.0)
    // Exact intervals are readable below the drawing, with the same ordering and labels.
    Canvas(Modifier.fillMaxWidth().height(42.dp).clearAndSetSemantics { }) {
        row.bands.entries.forEachIndexed { index, (kind, intervals) ->
            val opacity = when (kind) { ToleranceBand.FULL -> 0.9f; ToleranceBand.HALF -> 0.45f; ToleranceBand.ZERO_ONLY -> 0.6f }
            intervals.forEach { interval ->
                val a = (seconds(start, interval.start) / total).toFloat().coerceIn(0f, 1f)
                val b = (seconds(start, interval.end) / total).toFloat().coerceIn(0f, 1f)
                val left = if (rtl) 1f - b else a
                drawRoundRect(tint.copy(alpha = opacity), topLeft = Offset(left * size.width, index * 14.dp.toPx()),
                    size = Size((b - a).coerceAtLeast(0f) * size.width, 10.dp.toPx()), cornerRadius = CornerRadius(3.dp.toPx()))
            }
        }
    }
}

private fun bandLabel(kind: ToleranceBand) = when (kind) { ToleranceBand.FULL -> "Full"; ToleranceBand.HALF -> "Half"; ToleranceBand.ZERO_ONLY -> "Zero-only" }
private fun date(value: Instant, zone: ZoneId): String = try {
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(value.atZone(zone))
} catch (_: java.time.DateTimeException) { "Date outside the supported range" }
private fun dateTime(value: Instant, zone: ZoneId): String = try {
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).format(value.atZone(zone))
} catch (_: java.time.DateTimeException) { "Date outside the supported range" }
