// PsyZine, 2026-10-11. GPL-3.0-or-later.
package com.isaakhanimann.journal.ui.tabs.journal.experience.edit

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.isaakhanimann.journal.ui.tabs.journal.experience.photos.PhotoTile

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
        TopAppBar(title = { Text("Edit experience") }, navigationIcon = {
            IconButton(onClick = { back() }, enabled = !viewModel.saving) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close editor") }
        }, actions = { TextButton(onClick = { viewModel.save(navigateBack) }, enabled = viewModel.canSave) { Text(if (viewModel.saving) "Saving…" else "Save") } })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (viewModel.loading) CircularProgressIndicator()
            viewModel.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            OutlinedTextField(viewModel.enteredTitle, viewModel::editTitle, label = { Text("Title") }, enabled = !viewModel.loading && !viewModel.saving, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(viewModel.enteredText, viewModel::editText, label = { Text("Notes") }, enabled = !viewModel.loading && !viewModel.saving, modifier = Modifier.fillMaxWidth(), minLines = 4)
            OutlinedTextField(viewModel.enteredLocation, viewModel::editLocation, label = { Text("Location name") }, enabled = !viewModel.loading && !viewModel.saving, modifier = Modifier.fillMaxWidth())
            Text("Photos", style = MaterialTheme.typography.titleLarge)
            Text("Private copies · location metadata removed", style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { viewModel.prepareCamera(camera::launch) }, enabled = viewModel.canSave) { Text("Take photo") }
                OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = viewModel.canSave) { Text("Choose photos") }
                OutlinedButton(onClick = { files.launch(arrayOf("image/*")) }, enabled = viewModel.canSave) { Text("Choose files") }
            }
            if (viewModel.importing) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(viewModel.progress) }
            val draft = viewModel.draft
            if (draft != null) {
                if (draft.pendingCamera != null) {
                    Text("A camera capture is pending.")
                    Row { TextButton(onClick = viewModel::recoverCamera) { Text("Recover photo") }; TextButton(onClick = viewModel::cancelCamera) { Text("Cancel capture") } }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    viewModel.savedPhotos.forEachIndexed { index, photo -> key(photo.id) {
                        Column {
                            PhotoTile(photo.id, viewModel.attachments.file(photo), "Saved photo ${index + 1}", viewModel.attachments.processor) {}
                            val removed = photo.id in draft.removedIds
                            TextButton(onClick = { if (removed) viewModel.undoRemove(photo.id) else viewModel.removeSaved(photo.id) }, enabled = !viewModel.saving) { Text(if (removed) "Undo removal" else "Remove photo ${index + 1}") }
                        }
                    } }
                    draft.photos.forEachIndexed { index, photo -> key(photo.id) {
                        Column {
                            PhotoTile(photo.id, viewModel.attachments.file(draft, photo), "New photo ${index + 1}", viewModel.attachments.processor) {}
                            TextButton(onClick = { viewModel.removeStaged(photo.id) }, enabled = !viewModel.saving) { Text("Remove new photo ${index + 1}") }
                        }
                    } }
                }
                draft.failed.forEachIndexed { index, failure ->
                    Text("Photo ${index + 1}: ${failure.message}", color = MaterialTheme.colorScheme.error)
                    Row {
                        TextButton(onClick = { viewModel.retry(failure) }, enabled = !viewModel.importing && !viewModel.saving) { Text("Retry") }
                        TextButton(onClick = { viewModel.removeFailure(failure) }, enabled = !viewModel.importing && !viewModel.saving) { Text("Remove failed photo") }
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
