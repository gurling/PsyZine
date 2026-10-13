// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.ui.tabs.journal.experience.photos

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.isaakhanimann.journal.data.attachments.ImageProcessor
import com.isaakhanimann.journal.data.room.attachments.PhotoAttachment
import java.io.File
import kotlinx.coroutines.*

@Composable
fun PhotoTile(id: String, file: File, label: String, processor: ImageProcessor, onClick: (() -> Unit)? = null) {
    PhotoTileFromSource(id, { file }, label, processor, onClick)
}

@Composable
fun PhotoTileFromSource(id: String, source: () -> File, label: String, processor: ImageProcessor,
    onClick: (() -> Unit)? = null, modifier: Modifier = Modifier.size(104.dp)) {
    var error by remember(id) { mutableStateOf(false) }
    var attempt by remember(id) { mutableIntStateOf(0) }
    var thumbnail by remember(id) { mutableStateOf<File?>(null) }
    LaunchedEffect(id, attempt) {
        error = false
        try {
            val file = withContext(Dispatchers.IO) { source().also { require(it.isFile) } }
            thumbnail = processor.thumbnail(id, file, regenerate = attempt > 0)
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = true }
    }
    val content: @Composable () -> Unit = {
        if (error) Column(Modifier.padding(8.dp)) {
            Text("Photo unavailable", color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { attempt++ }) { Text("Retry") }
        } else if (thumbnail == null) CircularProgressIndicator(Modifier.padding(24.dp))
        else AsyncImage(model = thumbnail, contentDescription = label, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(), onError = { error = true })
    }
    if (onClick == null) Card(modifier = modifier) { content() }
    else Card(onClick = onClick, modifier = modifier) { content() }
}

fun LazyListScope.photoRows(photos: List<PhotoAttachment>, file: (PhotoAttachment) -> File,
    processor: ImageProcessor, onPhoto: (Int) -> Unit) {
    photos.chunked(3).forEachIndexed { row, values ->
        item(key = "photos-${values.first().id}", contentType = "photos") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                values.forEachIndexed { column, photo -> key(photo.id) {
                    val index = row * 3 + column
                    PhotoTileFromSource(photo.id, { file(photo) }, "Open photo ${index + 1} of ${photos.size}", processor,
                        { onPhoto(index) }, Modifier.weight(1f).aspectRatio(1f))
                } }
                repeat(3 - values.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
fun PhotoGrid(photos: List<PhotoAttachment>, file: (PhotoAttachment) -> File, processor: ImageProcessor, onPhoto: (Int) -> Unit) {
    LazyColumn(Modifier.heightIn(max = 352.dp)) { photoRows(photos, file, processor, onPhoto) }
}
