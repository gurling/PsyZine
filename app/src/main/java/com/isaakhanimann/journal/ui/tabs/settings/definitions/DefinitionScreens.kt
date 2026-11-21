// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.ui.tabs.settings.definitions

import androidx.compose.foundation.clickable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.isaakhanimann.journal.data.room.experiences.entities.AdaptiveColor
import com.isaakhanimann.journal.data.substances.AdministrationRoute

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DefinitionLibraryScreen(edit: (Int, String?) -> Unit, legacyUnits: () -> Unit,
    legacyEdit: (Int) -> Unit, back: () -> Unit, log: (Int, String?) -> Unit,
    tags: () -> Unit, subjectTags: (Int) -> Unit, model: DefinitionLibraryViewModel = hiltViewModel()) {
    val state by model.library.collectAsStateWithLifecycle()
    val query by model.query.collectAsStateWithLifecycle()
    val archived by model.archived.collectAsStateWithLifecycle()
    var exporting by rememberSaveable { mutableStateOf(false) }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(model::readLibrary) }
    val exportFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(model::exportLibrary) }
    Scaffold(topBar = { TopAppBar(title = { Text("Definitions") }, navigationIcon = { BackButton(back) }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("Custom substances & blends", style = MaterialTheme.typography.headlineSmall) }
            item { OutlinedTextField(query, model::search, Modifier.fillMaxWidth(), label = { Text("Search definitions") }, singleLine = true) }
            item { FormSwitch("Show archived definitions", archived, model::archive) }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { edit(0, state.generation) }, enabled = !state.loading && state.error == null) { Text("New definition") }
                    OutlinedButton(onClick = legacyUnits) { Text("Legacy units") }
                    OutlinedButton(onClick = tags) { Text("Manage tags") }
                    OutlinedButton(onClick = { importFile.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, enabled = !model.busy && state.generation != null) { Text("Import library") }
                    OutlinedButton(onClick = { model.selectForExport(state.records.map { it.unit.id }); exporting = true }, enabled = !model.busy && state.records.isNotEmpty()) { Text("Export library") }
                }
            }
            model.message?.let { item { Text(it, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) } }
            when {
                state.loading -> item { CircularProgressIndicator(); Text("Loading definitions") }
                state.error != null -> item { Text(state.error!!); TextButton(onClick = model::reload) { Text("Retry") } }
                state.records.isEmpty() -> item { Text(if (query.isBlank()) "No definitions in this list." else "No matching definitions.") }
                else -> items(state.records, key = { it.unit.id }) { record ->
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().clickable { edit(record.unit.id, state.generation) }.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(record.unit.name.ifBlank { "Unnamed definition (edit to correct)" }, style = MaterialTheme.typography.titleMedium)
                            Text("${record.components.size} components · ${record.unit.unit}")
                            if (record.unit.note.isNotBlank()) Text(record.unit.note)
                        }
                        if (record.unit.substanceName != null) TextButton(onClick = { legacyEdit(record.unit.id) }) { Text("Open legacy unit editor") }
                        if (!record.unit.isArchived) TextButton(onClick = { log(record.unit.id, state.generation) }) { Text("Log ingestion") }
                        TextButton(onClick = { subjectTags(record.unit.id) }) { Text("Tags") }
                    }
                }
            }
        }
    }
    if (exporting) AlertDialog(onDismissRequest = { exporting = false }, title = { Text("Choose definitions to export") }, text = {
        Column {
            Text("Nested definitions are included automatically. Use a full backup for journal entries, photos and legacy custom-substance descriptions.")
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(state.records, key = { it.unit.id }) { row ->
                    Row(Modifier.fillMaxWidth().toggleable(row.unit.id in model.selection, role = Role.Checkbox, onValueChange = { model.toggleExport(row.unit.id) }).padding(vertical = 8.dp)) {
                        Checkbox(row.unit.id in model.selection, onCheckedChange = null)
                        Text(row.unit.name, Modifier.weight(1f).padding(8.dp))
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = { exporting = false; exportFile.launch("PsyZine-definitions.json") }, enabled = model.selection.isNotEmpty()) { Text("Choose destination") } },
        dismissButton = { TextButton(onClick = { exporting = false }) { Text("Cancel") } })
    model.preview?.let { preview -> AlertDialog(onDismissRequest = model::cancelPreview, title = { Text("Import definitions?") }, text = {
        LazyColumn(Modifier.heightIn(max = 420.dp)) {
            item { Text("${preview.journal.customUnits.size} definitions will be added. Existing entries and definitions will not be overwritten.") }
            items(preview.messages) { Text(it, modifier = Modifier.padding(vertical = 8.dp)) }
        }
    }, confirmButton = { TextButton(onClick = model::confirmImport, enabled = !model.busy) { Text("Import") } },
        dismissButton = { TextButton(onClick = model::cancelPreview, enabled = !model.busy) { Text("Cancel") } }) }
    model.error?.let { text -> AlertDialog(onDismissRequest = model::dismissError, title = { Text("Library needs attention") }, text = { Text(text) },
        confirmButton = { TextButton(onClick = model::dismissError) { Text("OK") } }) }
    if (model.busy) AlertDialog(onDismissRequest = {}, title = { Text("Working with library") }, text = { CircularProgressIndicator() }, confirmButton = {})
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DefinitionEditorScreen(back: () -> Unit, model: DefinitionEditorViewModel = hiltViewModel()) {
    val draft = model.draft
    val list = rememberLazyListState()
    var picker by rememberSaveable { mutableStateOf(false) }
    var routePicker by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var confirmConvert by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(model.validationAttempt) {
        val key = model.errors.keys.firstOrNull() ?: return@LaunchedEffect
        val component = draft.components.indexOfFirst { key.startsWith(it.key + ".") }
        val roa = draft.roas.indexOfFirst { key.startsWith(it.key + ".") }
        val index = when {
            key == "name" -> 1
            key == "unit" -> 2
            key == "halfDays" || key == "zeroDays" -> 7
            component >= 0 -> 10 + component
            roa >= 0 -> 11 + draft.components.size + roa
            else -> 0
        }
        list.animateScrollToItem(index)
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text(if (model.isNew) "New definition" else "Edit definition", maxLines = 1, overflow = TextOverflow.Ellipsis) }, navigationIcon = { BackButton(back) })
    }, bottomBar = {
        Surface(tonalElevation = 3.dp) {
            Button(onClick = { model.save(back) }, enabled = model.canSave,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(12.dp)) {
                Text(if (model.completed) "Saved" else "Save definition")
            }
        }
    }) { padding ->
        if (model.loading) {
            Column(Modifier.padding(padding).padding(16.dp)) { CircularProgressIndicator(); Text("Loading definition") }
        } else if (!model.ready) {
            Column(Modifier.padding(padding).padding(16.dp)) { Text("This definition could not be loaded."); TextButton(onClick = model::load) { Text("Retry") } }
        } else LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("definition-form"), state = list, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item(key = "errors") { if (model.errors.isNotEmpty()) ErrorText("Correct the highlighted fields. Your draft has not been saved.") }
            item(key = "name") { DraftField("Name", "name", draft.name, { model.change(draft.copy(name = it)) }, model) }
            item(key = "unit") { DraftField("Singular unit", "unit", draft.unit, { model.change(draft.copy(unit = it)) }, model) }
            item(key = "plural") { DraftField("Plural unit (optional)", "plural", draft.plural, { model.change(draft.copy(plural = it)) }, model) }
            item(key = "note") { DraftField("Note", "note", draft.note, { model.change(draft.copy(note = it)) }, model, multiline = true) }
            item(key = "color") { ChoiceField("Color", draft.color.ifBlank { "Inherit color" }, listOf("") + AdaptiveColor.entries.map { it.name },
                { model.change(draft.copy(color = it)) }, emptyLabel = "Inherit color") }
            item(key = "switches") {
                Column {
                    FormSwitch("Archived", draft.archived) { model.change(draft.copy(archived = it)) }
                    FormSwitch("Hide definition in timeline", draft.hidden) { model.change(draft.copy(hidden = it)) }
                    FormSwitch("Show component timelines alongside own timeline", draft.showComponents) { model.change(draft.copy(showComponents = it)) }
                    FormSwitch("Show component tolerances alongside own tolerance", draft.showTolerances) { model.change(draft.copy(showTolerances = it)) }
                }
            }
            item(key = "tolerance") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Tolerance", style = MaterialTheme.typography.titleLarge)
                    Text("Optional estimates in days. Blank means unknown.")
                    DraftField("Time to half tolerance (days)", "halfDays", draft.halfDays, { model.change(draft.copy(halfDays = it)) }, model, number = true)
                    DraftField("Time to zero tolerance (days)", "zeroDays", draft.zeroDays, { model.change(draft.copy(zeroDays = it)) }, model, number = true)
                }
            }
            item(key = "legacy") {
                if (!draft.native) {
                    Text("This legacy unit retains its original fixed route and single-substance conversion.")
                    OutlinedButton(onClick = { confirmConvert = true }) { Text("Convert to blend") }
                } else Text("Changes to a definition affect entries that use it. Archive used definitions to retain their history.")
            }
            item(key = "components") {
                Text("Components", style = MaterialTheme.typography.titleLarge)
                Text("Amounts describe one ${draft.unit.ifBlank { "unit" }} of this definition.")
                if (draft.native) OutlinedButton(onClick = { picker = true }) { Text("Add component") }
            }
            items(draft.components, key = { it.key }) { component ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(component.substance ?: model.records.find { it.unit.id == component.childId }?.unit?.name ?: "Missing definition (remove or correct)", style = MaterialTheme.typography.titleMedium)
                        DraftField("Amount (blank means unknown)", "${component.key}.dose", component.dose,
                            { model.component(component.copy(dose = it)) }, model, number = true)
                        if (component.childId == null) DraftField("Measurement unit", "${component.key}.units", component.units,
                            { model.component(component.copy(units = it)) }, model)
                        FormSwitch("Estimated amount", component.estimate) { model.component(component.copy(estimate = it)) }
                        if (component.estimate || component.uncertainty.isNotBlank()) {
                            DraftField("Standard deviation", "${component.key}.uncertainty", component.uncertainty,
                                { model.component(component.copy(uncertainty = it)) }, model, number = true)
                            Text("Uncertainty is used when Estimated amount is selected.")
                        }
                        DraftField("Component note", "${component.key}.note", component.note,
                            { model.component(component.copy(note = it)) }, model, multiline = true)
                        FormSwitch("Hide component in timeline", component.hidden) { model.component(component.copy(hidden = it)) }
                        if (draft.native) TextButton(onClick = { model.change(draft.copy(components = draft.components.filterNot { it.key == component.key })) }) { Text("Remove component") }
                    }
                }
            }
            item(key = "route-heading") {
                Text("Dose & duration", style = MaterialTheme.typography.titleLarge)
                Text("Own route information takes precedence over component information. Leave unknown values blank.")
                OutlinedButton(onClick = { routePicker = true }) { Text("Add route information") }
            }
            items(draft.roas, key = { it.key }) { roa ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(roa.route.displayText, style = MaterialTheme.typography.titleMedium)
                        Text("Dose thresholds are in ${draft.unit.ifBlank { "definition units" }}.")
                        listOf("lightMinDose" to "Light minimum", "commonMinDose" to "Common minimum", "strongMinDose" to "Strong minimum", "heavyMinDose" to "Heavy minimum").forEach { (key, label) ->
                            DraftField(label, "${roa.key}.$key", roa.fields[key].orEmpty(),
                                { model.roa(roa.copy(fields = roa.fields + (key to it))) }, model, number = true)
                        }
                        for (phase in listOf("onset", "comeup", "peak", "offset")) {
                            Text(phase.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleSmall)
                            for ((suffix, label) in listOf("Min" to "Minimum", "Max" to "Maximum")) {
                                val key = phase + suffix
                                DraftField("$phase $label", "${roa.key}.$key", roa.fields[key].orEmpty(),
                                    { model.roa(roa.copy(fields = roa.fields + (key to it))) }, model, number = true)
                            }
                            ChoiceField("$phase time unit", roa.fields[phase + "Unit"].orEmpty(), listOf("", "SECONDS", "MINUTES", "HOURS", "DAYS"),
                                { model.roa(roa.copy(fields = roa.fields + (phase + "Unit" to it))) }, emptyLabel = "Unknown")
                        }
                        model.errors["${roa.key}.range"]?.let { ErrorText(it) }
                        TextButton(onClick = { model.change(draft.copy(roas = draft.roas.filterNot { it.key == roa.key })) }) { Text("Remove route information") }
                    }
                }
            }
            if (!model.isNew) item(key = "delete") { TextButton(onClick = { confirmDelete = true }) { Text("Delete unused definition") } }
        }
    }
    if (picker) {
        var query by rememberSaveable { mutableStateOf("") }
        AlertDialog(onDismissRequest = { picker = false }, title = { Text("Choose component") }, text = {
            Column(Modifier.heightIn(max = 480.dp)) {
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("Search substances and definitions") }, singleLine = true)
                LazyColumn {
                    items(model.records.filter { row -> row.unit.id != model.original?.unit?.id && draft.components.none { it.childId == row.unit.id } && row.unit.name.contains(query, true) }, key = { "unit:${it.unit.id}" }) { row ->
                        TextButton(onClick = { model.addDefinition(row.unit.id); picker = false }) { Text("${row.unit.name} · definition") }
                    }
                    items(model.substanceNames.filter { name -> draft.components.none { it.substance == name } && name.contains(query, true) }, key = { "substance:$it" }) { name ->
                        TextButton(onClick = { model.addSubstance(name); picker = false }) { Text(name) }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { picker = false }) { Text("Cancel") } })
    }
    if (routePicker) AlertDialog(onDismissRequest = { routePicker = false }, title = { Text("Choose administration route") }, text = {
        LazyColumn(Modifier.heightIn(max = 420.dp)) {
            items(AdministrationRoute.entries.filter { route -> draft.roas.none { it.route == route } && (draft.native || route == model.original?.unit?.administrationRoute) }) { route ->
                TextButton(onClick = { model.addRoute(route); routePicker = false }) { Text(route.displayText) }
            }
        }
    }, confirmButton = { TextButton(onClick = { routePicker = false }) { Text("Cancel") } })
    if (confirmConvert) AlertDialog(onDismissRequest = { confirmConvert = false }, title = { Text("Convert this unit to a blend?") },
        text = { Text("The component amount is retained. Route choices will follow own route information or components instead of the old fixed route. Existing entries keep their recorded routes.") },
        confirmButton = { TextButton(onClick = { model.change(draft.copy(native = true)); confirmConvert = false }) { Text("Convert") } },
        dismissButton = { TextButton(onClick = { confirmConvert = false }) { Text("Cancel") } })
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Delete definition?") },
        text = { Text("Only unused definitions can be deleted. Archive a definition to hide it while keeping history and other blends intact.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; model.delete(back) }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } })
    model.error?.let { message -> AlertDialog(onDismissRequest = model::dismissError, title = { Text("Definition needs attention") },
        text = { Text(message) }, confirmButton = { TextButton(onClick = model::dismissError) { Text("OK") } }) }
    if (model.busy) AlertDialog(onDismissRequest = {}, title = { Text("Saving changes") }, text = { CircularProgressIndicator() }, confirmButton = {})
}

@Composable
private fun BackButton(back: () -> Unit) { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }

@Composable
private fun ErrorText(text: String) { Text(text, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }

@Composable
private fun DraftField(label: String, key: String, value: String, change: (String) -> Unit,
    model: DefinitionEditorViewModel, number: Boolean = false, multiline: Boolean = false) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(model.validationAttempt) {
        if (model.validationAttempt > 0 && model.errors.keys.firstOrNull() == key) {
            withFrameNanos { }
            focus.requestFocus()
        }
    }
    OutlinedTextField(value, change, Modifier.fillMaxWidth().focusRequester(focus).testTag("definition-field:$key"), label = { Text(label) },
        isError = model.errors[key] != null, supportingText = model.errors[key]?.let { message -> @Composable { Text(message) } },
        singleLine = !multiline, minLines = if (multiline) 2 else 1,
        keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Decimal else KeyboardType.Text))
}

@Composable
internal fun FormSwitch(label: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(checked, role = Role.Switch, onValueChange = change), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f).padding(vertical = 12.dp))
        Switch(checked, onCheckedChange = null)
    }
}

@Composable
internal fun ChoiceField(label: String, selected: String, options: List<String>, change: (String) -> Unit, emptyLabel: String = "Unknown") {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, Modifier.fillMaxWidth()) { Text("$label: ${selected.ifBlank { emptyLabel }}") }
        DropdownMenu(expanded, { expanded = false }) {
            options.forEach { value -> DropdownMenuItem(text = { Text(value.ifBlank { emptyLabel }) }, onClick = { change(value); expanded = false }) }
        }
    }
}
