// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.psyzine

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import java.io.File

internal fun captureJournalDisplay(name: String, compose: ComposeTestRule) {
    compose.onAllNodes(isRoot())[0].captureToImage()
    compose.waitForIdle()
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    instrumentation.waitForIdleSync()
    android.os.SystemClock.sleep(150)
    val output = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
    val directory = if (output != null) File(output, "journal-display-checks")
        else File(instrumentation.targetContext.getExternalFilesDir(null), "journal-display-checks")
    directory.mkdirs()
    val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
    try { File(directory, "$name.png").outputStream().use { assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) } }
    finally { bitmap.recycle() }
}
