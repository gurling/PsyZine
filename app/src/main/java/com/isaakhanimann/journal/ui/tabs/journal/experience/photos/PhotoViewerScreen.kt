// PsyZine, 2026-10-11. GPL-3.0-or-later.
package com.isaakhanimann.journal.ui.tabs.journal.experience.photos

import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import java.io.File
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.IntSize

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PhotoViewerScreen(navigateBack: () -> Unit, viewModel: PhotoViewerViewModel = hiltViewModel()) {
    val photos by viewModel.photos.collectAsStateWithLifecycle()
    PhotoViewerContent(photos.map { viewModel.attachments.file(it) }, viewModel.route.index, navigateBack)
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PhotoViewerContent(photoFiles: List<File>, initialIndex: Int, navigateBack: () -> Unit) {
    val pager = rememberPagerState(initialPage = initialIndex.coerceIn(0, photoFiles.lastIndex.coerceAtLeast(0)), pageCount = { photoFiles.size })
    var openedInitial by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(photoFiles.size) {
        if (photoFiles.isNotEmpty()) {
            if (!openedInitial) { pager.scrollToPage(initialIndex.coerceIn(0, photoFiles.lastIndex)); openedInitial = true }
            else if (pager.currentPage > photoFiles.lastIndex) pager.scrollToPage(photoFiles.lastIndex)
        }
    }
    val scope = rememberCoroutineScope()
    Scaffold(topBar = { TopAppBar(title = { Text(if (photoFiles.isEmpty()) "Photos" else "${pager.currentPage + 1} / ${photoFiles.size}") },
        navigationIcon = { IconButton(onClick = navigateBack) { Icon(Icons.Default.Close, "Close photo viewer") } }) },
        bottomBar = {
            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }, enabled = pager.currentPage > 0) { Text("Previous photo") }
                TextButton(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }, enabled = pager.currentPage < photoFiles.lastIndex) { Text("Next photo") }
            }
        }) { padding ->
        if (photoFiles.isEmpty()) Text("No photos available", Modifier.padding(padding).padding(16.dp))
        else HorizontalPager(state = pager, key = { photoFiles[it].path }, modifier = Modifier.fillMaxSize().padding(padding)) { page ->
            var scale by remember(photoFiles[page].path) { mutableFloatStateOf(1f) }
            var offset by remember(photoFiles[page].path) { mutableStateOf(Offset.Zero) }
            var viewport by remember { mutableStateOf(IntSize.Zero) }
            val transform = rememberTransformableState { zoom, pan, _ ->
                scale = (scale * zoom).coerceIn(1f, 5f)
                val next = offset + pan
                val xLimit = viewport.width * (scale - 1f) / 2
                val yLimit = viewport.height * (scale - 1f) / 2
                offset = if (scale == 1f) Offset.Zero else Offset(next.x.coerceIn(-xLimit, xLimit), next.y.coerceIn(-yLimit, yLimit))
            }
            Box(Modifier.fillMaxSize().onSizeChanged { viewport = it }.clipToBounds().transformable(transform, canPan = { scale > 1f })) {
                AsyncImage(model = photoFiles[page], contentDescription = "Photo ${page + 1} of ${photoFiles.size}. Pinch to zoom.",
                    contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().graphicsLayer {
                        scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y
                    })
                if (scale > 1f) TextButton(onClick = { scale = 1f; offset = Offset.Zero }) { Text("Reset zoom") }
            }
        }
    }
}
