// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.ui.tabs.stats.advanced

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.isaakhanimann.journal.data.definitions.*
import com.isaakhanimann.journal.ui.tabs.search.substance.roa.toReadableString
import com.isaakhanimann.journal.ui.tabs.search.substance.roa.toPreservedString
import java.time.LocalDate
import java.util.Locale

@Composable
fun AdvancedStatisticsScreen(back: () -> Unit, openExperience: (Int, String) -> Unit,
    model: AdvancedStatisticsViewModel = hiltViewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    AdvancedStatisticsContent(state, model.saving, model::metric, model::period, model::consumer, model::subject,
        model::grouping, model::bucket, model::granularity, model::customRange, model::retry, back, openExperience)
    model.saveError?.let { message -> AlertDialog(onDismissRequest = model::dismissError,
        title = { Text("Choice could not be saved") }, text = { Text(message) },
        confirmButton = { TextButton(onClick = model::dismissError) { Text("OK") } }) }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AdvancedStatisticsContent(state: AdvancedStatisticsState, saving: Boolean, metric: (StatisticsMetric) -> Unit,
    period: (StatisticsPeriod) -> Unit, consumer: (String?) -> Unit, subject: (String?) -> Unit,
    grouping: (Boolean) -> Unit, bucket: (Int?) -> Unit, granularity: (StatisticsGranularity) -> Unit,
    customRange: (LocalDate, LocalDate) -> Unit, retry: () -> Unit, back: () -> Unit, openExperience: (Int, String) -> Unit) {
    val request = state.request
    val report = state.report
    var showCustom by rememberSaveable { mutableStateOf(false) }
    var expandedGroup by rememberSaveable(report?.generation, report?.selectedBucket) { mutableStateOf<String?>(null) }
    Scaffold(topBar = { TopAppBar(title = { Text("Advanced statistics") }, navigationIcon = {
        IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
    }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatisticsChoice("Metric", request.selection.metric, StatisticsMetric.entries.map { it to it.label }, !saving, metric)
                    StatisticsChoice("Period", request.selection.period, StatisticsPeriod.entries.map { it to it.label }, !saving, choose = {
                        if (it == StatisticsPeriod.CUSTOM) showCustom = true else period(it)
                    })
                    StatisticsChoice("Consumer", request.consumer, listOf(null to "You") + report?.consumers.orEmpty().map { it to it }, !state.loading, consumer)
                    StatisticsChoice("Subject", request.subjectKey,
                        listOf(null to "All subjects") + report?.subjects.orEmpty().map { it.key to "${it.name} (${subjectKind(it.key)})" }, !state.loading, subject,
                        searchable = true)
                    StatisticsChoice("Group by", request.groupByPrimaryTag, listOf(false to "Subject", true to "Primary tag"), !state.loading, grouping)
                }
                if (request.selection.period == StatisticsPeriod.CUSTOM) {
                    TextButton(onClick = { showCustom = true }, enabled = !saving) { Text("Change custom dates") }
                    report?.window?.let { window -> StatisticsChoice("Buckets", window.granularity,
                        window.availableGranularities.map { it to it.label }, true, granularity) }
                }
            }
            item {
                Text("Calendar periods include the whole current week, month or year, including future records inside that period. Experiences use their stored journal date; ingestions and quantities use the ingestion date.",
                    style = MaterialTheme.typography.bodySmall)
            }
            when {
                state.loading -> item { CircularProgressIndicator(); Text("Loading statistics") }
                state.error != null -> item {
                    Text(state.error, Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    TextButton(onClick = retry) { Text("Retry") }
                }
                report != null -> {
                    item {
                        Text("${report.window.start} – ${report.window.endInclusive}", style = MaterialTheme.typography.titleMedium)
                        Text("${report.window.granularity.label}; oldest period first", style = MaterialTheme.typography.bodySmall)
                        when {
                            report.dosageNeedsSubject -> Text("Choose one subject to chart its quantity. Quantities of different subjects are kept separate below.")
                            report.dosageHasMixedUnits -> Text("The selected subject has incompatible or missing units. Its quantities remain separate below.")
                            else -> StatisticsBars(report, bucket)
                        }
                        if (report.selectedBucket != null) {
                            val range = report.window.buckets[report.selectedBucket]
                            Text("Details: ${range.start} – ${range.endInclusive}", style = MaterialTheme.typography.titleSmall)
                            TextButton(onClick = { bucket(null) }) { Text("Show entire period") }
                        }
                        if (report.invalidDefinitions > 0) Text("${report.invalidDefinitions} recorded quantities or definitions in your journal could not be calculated. Their records remain available with unknown quantities.")
                        Text("Counts deduplicate recorded IDs. Definition and ingredient quantities are separate. Known totals exclude unknown amounts; mass units are converted to mg. Routes and other units stay separate.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    if (report.groups.isEmpty()) item { Text("No matching records in this period. Choose another subject, consumer or period.") }
                    items(report.groups, key = { "group:${it.key}" }) { group ->
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(group.name, style = MaterialTheme.typography.titleMedium)
                                Text("${group.experienceCount} experiences · ${group.ingestionCount} ingestions")
                                group.doses.forEach { dose ->
                                    val amount = dose.knownTotal?.let(::statisticsQuantityText) ?: "unknown"
                                    Text("${dose.subject.name} · ${dose.route.displayText}: ${if (dose.isEstimate) "~" else ""}$amount ${dose.units.orEmpty()}")
                                    Text("${dose.knownRecords} complete known records; ${dose.unknownRecords} incomplete or unknown",
                                        style = MaterialTheme.typography.bodySmall)
                                    dose.distribution?.let { distribution ->
                                        Text("Per ingestion: mean ${statisticsQuantityText(distribution.mean)}; median ${statisticsQuantityText(distribution.median)} ${dose.units.orEmpty()}")
                                        Text("${distribution.sampleCount} complete known samples; ${distribution.excludedRecords} excluded", style = MaterialTheme.typography.bodySmall)
                                        listOf("Minimum" to distribution.minimum, "Maximum" to distribution.maximum).forEach { (label, extreme) ->
                                            val record = group.records.firstOrNull { it.ingestionId == extreme.ingestionIds.first() }
                                            Text("$label: ${statisticsQuantityText(extreme.amount)} ${dose.units.orEmpty()} (${extreme.ingestionIds.size} matching records)")
                                            if (record != null) TextButton(onClick = { openExperience(record.experienceId, report.generation) }) {
                                                Text("Open a ${label.lowercase()} record")
                                            }
                                        }
                                    }
                                }
                                TextButton(onClick = { expandedGroup = if (expandedGroup == group.key) null else group.key }) {
                                    Text(if (expandedGroup == group.key) "Hide matching records" else "Show matching records")
                                }
                            }
                        }
                    }
                    report.groups.firstOrNull { it.key == expandedGroup }?.let { group ->
                        item { Text("${group.name}: matching records", style = MaterialTheme.typography.titleMedium)
                            Text("Ordered by ingestion time. An experience link opens the preserved journal record.", style = MaterialTheme.typography.bodySmall) }
                        items(group.records, key = { "record:${it.ingestionId}" }) { record ->
                            ListItem(headlineContent = { Text(record.title.ifBlank { "Untitled experience" }) },
                                supportingContent = { Text("${record.date} · ingestion #${record.ingestionId}") },
                                modifier = Modifier.clickable { openExperience(record.experienceId, report.generation) })
                        }
                    }
                }
            }
        }
    }
    if (showCustom) StatisticsDateDialog(request, dismiss = { showCustom = false }) { start, end ->
        showCustom = false; customRange(start, end)
    }
}

private fun subjectKind(key: String) = when {
    key.startsWith("custom:") -> "definition"
    key.startsWith("local:") -> "local"
    else -> "catalog"
}

@Composable
private fun StatisticsBars(report: AdvancedStatisticsReport, select: (Int?) -> Unit) {
    val maximum = report.buckets.filterNotNull().maxOrNull()?.takeIf { it > 0 } ?: 1.0
    LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(report.buckets) { index, value ->
            val dates = report.window.buckets[index]
            val label = value?.let(::statisticsQuantityText) ?: "unknown"
            val units = report.quantityUnits.takeIf { report.request.selection.metric == StatisticsMetric.DOSAGE }.orEmpty()
            val selected = report.selectedBucket == index
            Column(Modifier.width(88.dp).clickable { select(if (selected) null else index) }
                .semantics(mergeDescendants = true) {
                    contentDescription = "${dates.start} through ${dates.endInclusive}: $label $units"
                    this.selected = selected
                }.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, style = MaterialTheme.typography.labelMedium)
                Box(Modifier.height(140.dp).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    Box(Modifier.width(36.dp).height(((value ?: 0.0) / maximum * 136).toFloat().dp.coerceAtLeast(2.dp))
                        .background(if (selected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary))
                }
                Text(dates.start.toString(), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    if (report.quantityUnits != null && report.request.selection.metric == StatisticsMetric.DOSAGE) Text("Known quantities in ${report.quantityUnits}")
}

/** Display rounding must not turn a known positive quantity into a displayed zero. */
internal fun statisticsQuantityText(value: Double): String = value.toReadableString().let {
    if (value != 0.0 && it == "0") value.toPreservedString() else it
}

@Composable
private fun <T> StatisticsChoice(title: String, selected: T, options: List<Pair<T, String>>, enabled: Boolean,
    choose: (T) -> Unit, searchable: Boolean = false) {
    var open by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    OutlinedButton(onClick = { query = ""; open = true }, enabled = enabled) {
        Text("$title: ${options.firstOrNull { it.first == selected }?.second ?: "Unavailable selection"}")
    }
    if (open) AlertDialog(onDismissRequest = { open = false }, title = { Text(title) }, text = {
        Column {
            if (searchable) OutlinedTextField(query, { query = it }, label = { Text("Find subject") }, modifier = Modifier.fillMaxWidth())
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                items(options.filter { query.isBlank() || it.second.contains(query, ignoreCase = true) }) { (value, label) ->
                    TextButton(onClick = { open = false; choose(value) }, modifier = Modifier.fillMaxWidth().semantics { this.selected = value == selected }) {
                        Text(label, Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = { open = false }) { Text("Cancel") } })
}

@Composable
private fun StatisticsDateDialog(request: StatisticsRequest, dismiss: () -> Unit, apply: (LocalDate, LocalDate) -> Unit) {
    var start by rememberSaveable { mutableStateOf(request.customStart?.toString().orEmpty()) }
    var end by rememberSaveable { mutableStateOf(request.customEnd?.toString().orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Custom local dates") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(start, { start = it; error = null }, label = { Text("Start (YYYY-MM-DD)") }, isError = error != null)
            OutlinedTextField(end, { end = it; error = null }, label = { Text("End, inclusive (YYYY-MM-DD)") }, isError = error != null)
            error?.let { Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(onClick = {
        val first = runCatching { LocalDate.parse(start.trim()) }.getOrNull()
        val last = runCatching { LocalDate.parse(end.trim()) }.getOrNull()
        if (first == null || last == null) error = "Enter both dates in YYYY-MM-DD format, for example 2026-11-09."
        else try {
            advancedStatisticsWindow(request.copy(selection = request.selection.copy(period = StatisticsPeriod.CUSTOM),
                customStart = first, customEnd = last), LocalDate.now(), null, Locale.getDefault())
            apply(first, last)
        } catch (e: IllegalArgumentException) { error = e.message ?: "Choose a valid date range." }
    }) { Text("Apply") } }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}
