// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.ui.tabs.settings.about

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.isaakhanimann.journal.BuildConfig
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SupportInfoScreen(back: () -> Unit) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val report = remember(configuration) {
        SupportInfo(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE.toLong(), BuildConfig.APPLICATION_ID,
            BuildConfig.FLAVOR, BuildConfig.BUILD_TYPE, Build.VERSION.SDK_INT,
            configuration.fontScale, configuration.densityDpi).text()
    }
    var error by remember { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    error?.let { message ->
        AlertDialog(onDismissRequest = { error = null }, title = { Text("Support info") }, text = { Text(message) },
            confirmButton = { TextButton(onClick = { error = null }) { Text("OK") } })
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Support info") }, navigationIcon = {
            IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
        })
    }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Review the information below before copying or sharing it. It contains only app/build details, Android API level and display scaling.")
            Text("Journal records, photos, logs, accounts and device identifiers are not collected.")
            SelectionContainer {
                Text(report, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace))
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    try {
                        checkNotNull(context.getSystemService(ClipboardManager::class.java))
                            .setPrimaryClip(ClipData.newPlainText("PsyZine support info", report))
                        scope.launch { snackbar.showSnackbar("Support info copied") }
                    } catch (_: RuntimeException) { error = "Support info could not be copied. You can select the text above and try again." }
                }) { Text("Copy") }
                OutlinedButton(onClick = {
                    try {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "PsyZine support info")
                            putExtra(Intent.EXTRA_TEXT, report)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share support info"))
                    } catch (_: RuntimeException) { error = "No sharing app could be opened. Copy the support info instead." }
                }) { Text("Share…") }
            }
        }
    }
}
