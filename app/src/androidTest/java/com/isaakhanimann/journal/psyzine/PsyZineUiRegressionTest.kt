// PsyZine, 2026-10-11. GPL-3.0-or-later.
package com.isaakhanimann.journal.psyzine

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.isaakhanimann.journal.ui.theme.*
import com.isaakhanimann.journal.ui.tabs.settings.appearance.ThemePreviewCard
import com.isaakhanimann.journal.ui.tabs.journal.experience.photos.PhotoViewerContent
import com.isaakhanimann.journal.ui.tabs.journal.addingestion.time.DatePickerButton
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDateTime
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PsyZineUiRegressionTest {
    @get:Rule val compose = createComposeRule()
    @Test fun themeCardSelectionAndDisabledControlsAreAccessible() {
        var selected by mutableStateOf(false)
        var enabled by mutableStateOf(true)
        compose.setContent { PsyZineTheme(ThemeId.NORD) { ThemePreviewCard(ThemeId.DRACULA, selected, enabled) { selected = true } } }
        compose.onNodeWithText("Dracula", substring = true).performClick()
        compose.onNodeWithText("Dracula", substring = true).assertIsSelected()
        compose.runOnIdle { selected = false; enabled = false }
        compose.onNodeWithText("Journal").assertIsNotEnabled()
    }
    @Test fun dateDialogPreservesTimeAndAccessibleDateLabel() {
        val original = LocalDateTime.of(2026, 1, 2, 23, 45)
        var result: LocalDateTime? = null
        compose.setContent { PsyZineTheme(ThemeId.SOLARIZED_LIGHT) { DatePickerButton(original, { result = it }, "2 January") } }
        compose.onNodeWithContentDescription("Choose date").performClick()
        compose.onNodeWithText("OK").performClick()
        compose.runOnIdle { assertEquals(original, result) }
    }
    @Test fun captureEveryPaletteForVisualReview() {
        var theme by mutableStateOf(ThemeId.LATTE)
        compose.setContent { PsyZineTheme(theme) { ThemePreviewCard(theme, true) {} } }
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(app.getExternalFilesDir(null), "parity-screenshots").apply { mkdirs() }
        ThemeId.entries.filter { it != ThemeId.SYSTEM }.forEach { id ->
            compose.runOnIdle { theme = id }
            compose.waitForIdle()
            File(directory, "${id.key}.png").outputStream().use { output ->
                assertTrue(compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, output))
            }
        }
    }
    @Test fun photoViewerClampsInitialPagePagesAndClosesAccessibly() {
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "viewer-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val photos = (1..2).map { number ->
                val file = File(directory, "$number.png")
                val pixels = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
                file.outputStream().use { pixels.compress(Bitmap.CompressFormat.PNG, 100, it) }; pixels.recycle(); file
            }
            var closed = false
            compose.setContent { PsyZineTheme(ThemeId.LATTE) { PhotoViewerContent(photos, 99) { closed = true } } }
            compose.onNodeWithText("2 / 2").assertExists()
            compose.onNodeWithText("Next photo").assertIsNotEnabled()
            compose.onNodeWithText("Previous photo").performClick()
            compose.onNodeWithText("1 / 2").assertExists()
            compose.onNodeWithContentDescription("Close photo viewer").performClick()
            compose.runOnIdle { assertTrue(closed) }
        } finally { directory.deleteRecursively() }
    }
}
