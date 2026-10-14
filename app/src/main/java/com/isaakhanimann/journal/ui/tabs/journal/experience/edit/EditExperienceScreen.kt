// PsyZine, 2026-10-11. GPL-3.0-or-later.
package com.isaakhanimann.journal.ui.tabs.journal.experience.edit

import androidx.compose.ui.text.style.TextOverflow

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.isaakhanimann.journal.ui.tabs.journal.experience.photos.PhotoTileFromSource

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditExperienceScreen(navigateBack: () -> Unit, viewModel: EditExperienceViewModel = hiltViewModel()) {
    var confirmDiscard by remember { mutableStateOf(false) }
    val back = { if (viewModel.dirty) confirmDiscard = true else viewModel.discard(navigateBack) }
    BackHandler(enabled = !viewModel.saving) { back() }
    // Always intercept back during save so committing bytes cannot outlive this editor unexpectedly.
    BackHandler(enabled = viewModel.saving) {}
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { viewModel.cameraResult(it) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { viewModel.acquire(it) }
    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { viewModel.acquire(it) }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Edit experience", maxLines = 1, overflow = TextOverflow.Ellipsis) }, navigationIcon = {
            IconButton(onClick = { back() }, enabled = !viewModel.saving) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close editor") }
        }, actions = { TextButton(onClick = { viewModel.save(navigateBack) }, enabled = viewModel.canSave) { Text(if (viewModel.saving) "Saving…" else "Save") } })
    }) { padding ->
        val draft = viewModel.draft
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)) {
            item(key = "status") {
                if (viewModel.loading) CircularProgressIndicator()
                viewModel.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            item(key = "title") { OutlinedTextField(viewModel.enteredTitle, viewModel::editTitle, label = { Text("Title") }, enabled = viewModel.draft != null && !viewModel.loading && !viewModel.saving, modifier = Modifier.fillMaxWidth(), singleLine = true) }
            item(key = "text") { OutlinedTextField(viewModel.enteredText, viewModel::editText, label = { Text("Notes") }, enabled = viewModel.draft != null && !viewModel.loading && !viewModel.saving, modifier = Modifier.fillMaxWidth(), minLines = 4) }
            item(key = "location") { OutlinedTextField(viewModel.enteredLocation, viewModel::editLocation, label = { Text("Location name") }, enabled = viewModel.draft != null && !viewModel.loading && !viewModel.saving, modifier = Modifier.fillMaxWidth()) }
            item(key = "photo-actions") {
                Text("Photos", style = MaterialTheme.typography.titleLarge)
                Text("Private copies · location metadata removed", style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { viewModel.prepareCamera(camera::launch) }, enabled = viewModel.canSave) { Text("Take photo") }
                    OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = viewModel.canSave) { Text("Choose photos") }
                    OutlinedButton(onClick = { files.launch(arrayOf("image/*")) }, enabled = viewModel.canSave) { Text("Choose files") }
                }
                if (viewModel.importing) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(viewModel.progress) }
            }
            if (draft != null) {
                if (draft.pendingCamera != null) item(key = "camera") {
                    Text("A camera capture is pending.")
                    Row { TextButton(onClick = viewModel::recoverCamera) { Text("Recover photo") }; TextButton(onClick = viewModel::cancelCamera) { Text("Cancel capture") } }
                }
                val saved = viewModel.savedPhotos.associateBy { it.id }
                val staged = draft.photos.associateBy { it.id }
                val ids = saved.keys.toList() + staged.keys.toList()
                ids.chunked(3).forEach { row ->
                    item(key = "photos-${row.first()}", contentType = "photos") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { id -> key(id) {
                                Column(Modifier.weight(1f)) {
                                    val photo = saved[id]
                                    val newPhoto = staged[id]
                                    val label = if (photo != null) "Saved photo ${viewModel.savedPhotos.indexOf(photo) + 1}" else "New photo ${draft.photos.indexOf(newPhoto) + 1}"
                                    PhotoTileFromSource(id, { if (photo != null) viewModel.attachments.file(photo) else viewModel.attachments.file(draft, requireNotNull(newPhoto)) },
                                        label, viewModel.attachments.processor, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
                                    val removed = id in draft.removedIds
                                    TextButton(onClick = {
                                        if (photo != null) { if (removed) viewModel.undoRemove(id) else viewModel.removeSaved(id) }
                                        else viewModel.removeStaged(id)
                                    }, enabled = !viewModel.saving) { Text(if (removed) "Undo removal" else "Remove $label") }
                                }
                            } }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                draft.failed.forEachIndexed { index, failure ->
                    item(key = "failure-${failure.source}") {
                        Text("Photo ${index + 1}: ${failure.message}", color = MaterialTheme.colorScheme.error)
                        Row {
                            TextButton(onClick = { viewModel.retry(failure) }, enabled = !viewModel.importing && !viewModel.saving) { Text("Retry") }
                            TextButton(onClick = { viewModel.removeFailure(failure) }, enabled = !viewModel.importing && !viewModel.saving) { Text("Remove failed photo") }
                        }
                    }
                }
            }
        }
    }
    if (confirmDiscard) AlertDialog(onDismissRequest = { confirmDiscard = false }, title = { Text("Discard changes?") },
        text = { Text("New photos and edits will be discarded. Saved photos remain attached.") },
        confirmButton = { TextButton(onClick = { confirmDiscard = false; viewModel.discard(navigateBack) }) { Text("Discard") } },
        dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } })
}
