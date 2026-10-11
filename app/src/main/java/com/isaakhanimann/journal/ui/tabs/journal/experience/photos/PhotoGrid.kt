// PsyZine, 2026-10-11. GPL-3.0-or-later.
package com.isaakhanimann.journal.ui.tabs.journal.experience.photos

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.isaakhanimann.journal.data.attachments.ImageProcessor
import com.isaakhanimann.journal.data.room.attachments.PhotoAttachment
import java.io.File
import kotlinx.coroutines.CancellationException

@Composable
fun PhotoTile(id: String, file: File, label: String, processor: ImageProcessor, onClick: () -> Unit) {
    var error by remember(id, file) { mutableStateOf(false) }
    var thumbnail by remember(id, file) { mutableStateOf<File?>(null) }
    LaunchedEffect(id, file) {
        try { thumbnail = processor.thumbnail(id, file) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = true }
    }
    Card(onClick = onClick, modifier = Modifier.size(104.dp)) {
        if (error) Text("Photo unavailable", Modifier.padding(8.dp), color = MaterialTheme.colorScheme.error)
        else if (thumbnail == null) CircularProgressIndicator(Modifier.padding(24.dp))
        else AsyncImage(model = thumbnail, contentDescription = label, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PhotoGrid(photos: List<PhotoAttachment>, file: (PhotoAttachment) -> File, processor: ImageProcessor, onPhoto: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        photos.forEachIndexed { index, photo -> key(photo.id) {
            PhotoTile(photo.id, file(photo), "Open photo ${index + 1} of ${photos.size}", processor) { onPhoto(index) }
        } }
    }
}
