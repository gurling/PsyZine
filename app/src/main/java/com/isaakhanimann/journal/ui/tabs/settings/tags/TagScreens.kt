// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.ui.tabs.settings.tags

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.isaakhanimann.journal.data.room.experiences.entities.AdaptiveColor
import com.isaakhanimann.journal.ui.tabs.journal.addingestion.time.ColorPicker
import com.isaakhanimann.journal.ui.theme.LocalPsyZinePalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagLibraryScreen(back: () -> Unit, edit: (String?) -> Unit, assign: (String) -> Unit, model: TagLibraryViewModel = hiltViewModel()) {
    val state by model.library.collectAsStateWithLifecycle()
    Scaffold(topBar = { TopAppBar(title = { Text("Tags") }, navigationIcon = { TagBackButton(back) }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("Organize substances and definitions. A subject can have several tags and one primary tag for grouped statistics.") }
            item { Button(onClick = { edit(null) }, enabled = !state.loading && state.error == null) { Text("New tag") } }
            when {
                state.loading -> item { CircularProgressIndicator(); Text("Loading tags") }
                state.error != null -> item { Text(state.error!!); TextButton(onClick = model::reload) { Text("Retry") } }
                state.tags.isEmpty() -> item { Text("No tags yet. Create a tag, then assign it from a substance or definition's Tags button.") }
                else -> items(state.tags, key = { it.id }) { tag ->
                    OutlinedCard(onClick = { edit(tag.id) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            TagLabel(tag.name, tag.color)
                            val count = state.memberships.count { it.tagId == tag.id }
                            Text("$count ${if (count == 1) "subject" else "subjects"}")
                            TextButton(onClick = { assign(tag.id) }) { Text("Assign subjects") }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagSubjectsScreen(back: () -> Unit, model: TagSubjectsViewModel = hiltViewModel()) {
    val query by model.query.collectAsStateWithLifecycle()
    val filtered by model.filtered.collectAsStateWithLifecycle()
    Scaffold(topBar = { TopAppBar(title = { Text("Assign subjects") }, navigationIcon = { TagBackButton(back) }) },
        bottomBar = { TagSaveButton(model, "Save subjects") { model.save(back) } }) { padding ->
        if (model.loading || !model.ready) TagLoadStatus(model, padding, model::load)
        else Column(Modifier.fillMaxSize().padding(padding)) {
            Text(model.tagName, Modifier.padding(12.dp), style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(query, model::search, Modifier.fillMaxWidth().padding(horizontal = 12.dp), label = { Text("Search subjects") })
            Text("${model.selected.size} selected. Other tags and existing primary choices are retained.", Modifier.padding(12.dp))
            LazyColumn(Modifier.weight(1f).testTag("tag-subject-list"), contentPadding = PaddingValues(12.dp)) {
                if (filtered.isEmpty()) item { Text("No matching subjects.") }
                items(filtered, key = { it.key }) { subject ->
                    Row(Modifier.fillMaxWidth().toggleable(subject.key in model.selected, enabled = model.canSave, role = Role.Checkbox,
                        onValueChange = { model.toggle(subject.key) }).padding(vertical = 8.dp).testTag("tag-subject:${subject.key}")) {
                        Checkbox(subject.key in model.selected, onCheckedChange = null)
                        Column(Modifier.weight(1f).padding(12.dp)) {
                            Text(subject.name, style = MaterialTheme.typography.titleMedium)
                            Text(subject.kind, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
    TagError(model)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagEditorScreen(back: () -> Unit, model: TagEditorViewModel = hiltViewModel()) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val nameFocus = remember { FocusRequester() }
    LaunchedEffect(model.nameError) { if (model.nameError != null) nameFocus.requestFocus() }
    Scaffold(topBar = { TopAppBar(title = { Text(if (model.isNew) "New tag" else "Edit tag") }, navigationIcon = { TagBackButton(back) }) },
        bottomBar = { TagSaveButton(model, "Save tag") { model.save(back) } }) { padding ->
        if (model.loading || !model.ready) TagLoadStatus(model, padding, model::load)
        else LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("tag-form"), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                OutlinedTextField(model.name, model::name, Modifier.fillMaxWidth().focusRequester(nameFocus).testTag("tag-name"),
                    enabled = model.canSave, label = { Text("Name") }, isError = model.nameError != null,
                    supportingText = { model.nameError?.let { Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }) } })
            }
            item {
                Text("Color", style = MaterialTheme.typography.titleMedium)
                Text(model.color.replace('_', ' ').lowercase())
                val known = AdaptiveColor.entries.firstOrNull { it.name == model.color }
                if (known == null) Text("This imported color is preserved. Choose another color to replace it.")
                if (model.canSave) ColorPicker(known ?: AdaptiveColor.BLUE, { model.color(it.name) }, emptyList(), AdaptiveColor.entries)
            }
            if (!model.isNew) item { OutlinedButton(onClick = { confirmDelete = true }, enabled = model.canSave) { Text("Delete tag") } }
        }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Delete tag?") },
        text = { Text("This removes the tag from all subjects. Journal entries and definitions are retained. If this was primary, the first remaining tag by name becomes primary.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; model.delete(back) }, enabled = model.canSave) { Text("Delete tag") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } })
    TagError(model)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectTagsScreen(back: () -> Unit, manageTags: () -> Unit, model: SubjectTagsViewModel = hiltViewModel()) {
    LifecycleResumeEffect(model) {
        model.refreshTags()
        onPauseOrDispose { }
    }
    Scaffold(topBar = { TopAppBar(title = { Text("Subject tags") }, navigationIcon = { TagBackButton(back) }) },
        bottomBar = { TagSaveButton(model, "Save tags") { model.save(back) } }) { padding ->
        if (model.loading || !model.ready) TagLoadStatus(model, padding, model::load)
        else LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("subject-tags"), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text(model.subjectName, style = MaterialTheme.typography.headlineSmall) }
            item { Text("Select tags, then choose a primary tag for grouped statistics. Removing the primary selects the first remaining tag by name.") }
            item { OutlinedButton(onClick = manageTags, enabled = model.canSave) { Text("Manage tags") } }
            if (model.tags.isEmpty()) item { Text("No tags yet. Use Manage tags to create one, then return here.") }
            items(model.tags, key = { it.id }) { tag ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(8.dp)) {
                        Row(Modifier.fillMaxWidth().toggleable(tag.id in model.selected, enabled = model.canSave, role = Role.Checkbox,
                            onValueChange = { model.toggle(tag.id) }).padding(4.dp)) {
                            Checkbox(tag.id in model.selected, onCheckedChange = null)
                            Box(Modifier.weight(1f).padding(12.dp)) { TagLabel(tag.name, tag.color) }
                        }
                        if (tag.id in model.selected) {
                            if (model.primary == tag.id) Text("Primary tag", Modifier.padding(horizontal = 12.dp).semantics { stateDescription = "Primary" })
                            else TextButton(onClick = { model.makePrimary(tag.id) }, enabled = model.canSave,
                                modifier = Modifier.semantics { contentDescription = "Make ${tag.name} primary" }) { Text("Make primary") }
                        }
                    }
                }
            }
        }
    }
    TagError(model)
}

@Composable
fun TagLabel(name: String, color: String) {
    val palette = LocalPsyZinePalette.current
    val tint = AdaptiveColor.entries.firstOrNull { it.name == color }?.getComposeColor(palette) ?: MaterialTheme.colorScheme.primary
    // Text carries the meaning; the color mark is supplementary.
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("●", color = tint, modifier = Modifier.clearAndSetSemantics { })
        Text(name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun TagSaveButton(model: TagMutationViewModel, text: String, save: () -> Unit) {
    Surface(tonalElevation = 3.dp) {
        Button(onClick = save, enabled = model.canSave,
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(12.dp)) {
            Text(if (model.completed) "Saved" else if (model.busy) "Saving…" else text)
        }
    }
}

@Composable
private fun TagLoadStatus(model: TagMutationViewModel, padding: PaddingValues, retry: () -> Unit) {
    Column(Modifier.padding(padding).padding(16.dp)) {
        if (model.loading) { CircularProgressIndicator(); Text("Loading tags") }
        else { Text("Tags could not be loaded."); TextButton(onClick = retry) { Text("Retry") } }
    }
}

@Composable
private fun TagError(model: TagMutationViewModel) {
    model.error?.let { message -> AlertDialog(onDismissRequest = model::dismissError, title = { Text("Tags need attention") },
        text = { Text(message) }, confirmButton = { TextButton(onClick = model::dismissError) { Text("OK") } }) }
}

@Composable
private fun TagBackButton(back: () -> Unit) {
    IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
}
