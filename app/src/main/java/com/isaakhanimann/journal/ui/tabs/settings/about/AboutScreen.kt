// PsyZine, 2026-10-11. GPL-3.0-or-later.
package com.isaakhanimann.journal.ui.tabs.settings.about

import androidx.compose.ui.text.style.TextOverflow

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.isaakhanimann.journal.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen() {
    val context = LocalContext.current
    var notice by remember { mutableStateOf("Loading local license notices…") }
    var license by remember { mutableStateOf<String?>(null) }
    var selectedNotice by remember { mutableStateOf<String?>(null) }
    var files by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(Unit) {
        notice = withContext(Dispatchers.IO) { context.assets.open("licenses/NOTICES.txt").bufferedReader().use { it.readText() } }
        files = withContext(Dispatchers.IO) { context.assets.list("licenses").orEmpty().filter { it != "NOTICES.txt" && it != "gruvbox-package.json" }.sorted() }
    }
    LaunchedEffect(selectedNotice) {
        license = null
        selectedNotice?.let { name -> license = withContext(Dispatchers.IO) { context.assets.open("licenses/$name").bufferedReader().use { it.readText() } } }
    }
    Scaffold(topBar = { TopAppBar(title = { Text("About and licenses", maxLines = 1, overflow = TextOverflow.Ellipsis) }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("PsyZine ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleLarge)
            Text("An independent local journal, based on PsychonautWiki Journal. Copyright © 2022–2025 Isaak Hanimann and contributors. PsyZine modifications: 11 October 2026.")
            Text("GPL-3.0-or-later. Distributed without any warranty. See the full license below.")
            Text(if (BuildConfig.SOURCE_URL.isBlank()) "Local development build. Source repository has not been configured." else "Corresponding source: ${BuildConfig.SOURCE_URL}")
            Text("Upstream baseline: 8224449a62e26ffd448aade0a7079b005fdd9d37")
            Text("PsyLog feature baseline: 0c29a70fa1ced6106c8379b2c02541699d61f966. Copyright © 2025–2026 PsyLog contributors.")
            files.forEach { name ->
                TextButton(onClick = { selectedNotice = if (selectedNotice == name) null else name }) {
                    Text(if (name == "COPYING") "GPL license" else if (name == "PRIVACY.txt") "Privacy policy" else name.removeSuffix("-LICENSE").removeSuffix(".txt"))
                }
                if (selectedNotice == name) Text(license ?: "Loading…", style = MaterialTheme.typography.bodySmall)
            }
            Text(notice, style = MaterialTheme.typography.bodySmall)
        }
    }
}
