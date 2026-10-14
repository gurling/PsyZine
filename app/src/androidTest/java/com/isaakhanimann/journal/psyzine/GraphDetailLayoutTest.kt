// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.psyzine

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.isaakhanimann.journal.data.room.experiences.entities.AdaptiveColor
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import com.isaakhanimann.journal.data.substances.classes.Tolerance
import com.isaakhanimann.journal.data.substances.classes.roa.*
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.*
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.*
import com.isaakhanimann.journal.ui.tabs.search.substance.roa.ToleranceSection
import com.isaakhanimann.journal.ui.tabs.stats.substancecompanion.*
import com.isaakhanimann.journal.ui.theme.PsyZineTheme
import com.isaakhanimann.journal.ui.utils.getDateWithWeekdayText
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import java.io.File
import java.time.Instant

/** Synthetic composables only: no database, preferences or device settings are changed. */
class GraphDetailLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun draggingTimelineKeepsCompleteEstimateCaptionVisible() {
        showTimeline(1)
        val canvas = compose.onNodeWithTag("timeline_canvas")
        canvas.performTouchInput { down(Offset(width * .2f, height * .5f)); moveTo(Offset(width * .4f, height * .5f)) }
        try {
            assertCaptionInsidePanel()
            capture("timeline-drag-caption")
            val before = compose.onNodeWithTag("timeline_readout").fetchSemanticsNode().boundsInRoot
            canvas.performTouchInput { moveTo(Offset(width * .98f, height * .5f)) }
            assertCaptionInsidePanel()
            val after = compose.onNodeWithTag("timeline_readout").fetchSemanticsNode().boundsInRoot
            assertTrue("Readout should follow the cursor", after.left > before.left)
            val graph = canvas.fetchSemanticsNode().boundsInRoot
            assertTrue("Readout extends past graph", after.right <= graph.right + 1f)
            capture("timeline-drag-edge")
        } finally {
            canvas.performTouchInput { up() }
        }
        compose.onNodeWithTag("timeline_readout").assertDoesNotExist()
    }

    @Test fun multipleIngestionsKeepEstimateCaptionVisible() {
        showTimeline(6)
        val canvas = compose.onNodeWithTag("timeline_canvas")
        canvas.performTouchInput { down(center); moveTo(Offset(width * .8f, height * .5f)) }
        try {
            assertCaptionInsidePanel()
            capture("timeline-multiple-ingestions")
        } finally {
            canvas.performTouchInput { up() }
        }
    }

    @Test fun toleranceLabelsStayWholeBesideWrappingDescriptions() {
        compose.setContent { scaled {
            ToleranceSection(Tolerance("within several months of continuous use", "two weeks", "7 - 14 days"), emptyList())
        } }
        listOf("full:", "half:", "zero:").forEach {
            val text = layout(it)
            assertEquals("Tolerance label split: $it", 1, text.lineCount)
            assertComplete(text)
        }
        listOf("within several months of continuous use", "two weeks", "7 - 14 days").forEach { assertComplete(layout(it)) }
        capture("tolerance-wrapping")
    }

    @Test fun statisticsDetailKeepsLongTitleAndDateReadable() {
        val sample = SubstanceCompanionScreenPreviewProvider().values.first()
        val title = "A long synthetic experience title with several words"
        val burst = sample.second.first().let { it.copy(experience = it.experience.copy(title = title)) }
        val date = burst.experience.sortDate.getDateWithWeekdayText()
        compose.setContent { scaled {
            SubstanceCompanionScreen(sample.first, listOf(burst), null, emptyList())
        } }
        compose.onNodeWithText(date).performScrollTo().assertIsDisplayed()
        assertComplete(layout(title))
        val dateLayout = layout(date)
        assertComplete(dateLayout)
        assertTrue("Date squeezed into character-sized lines", dateLayout.lineCount <= 3)
        val titleBounds = compose.onNodeWithText(title).fetchSemanticsNode().boundsInRoot
        val dateBounds = compose.onNodeWithText(date).fetchSemanticsNode().boundsInRoot
        assertTrue("Title and date overlap", dateBounds.top >= titleBounds.bottom - 1f)
        capture("substance-statistics-title-date")
    }

    private fun showTimeline(count: Int) {
        fun range(seconds: Float) = DurationRange(seconds, seconds, DurationUnits.SECONDS)
        val duration = RoaDuration(range(60f), range(120f), range(180f), range(240f), null, null)
        val lines = (0 until count).map { DataForOneEffectLine("Synthetic substance", AdministrationRoute.ORAL, duration,
            1f, .5f, AdaptiveColor.RED, Instant.EPOCH.plusSeconds(it.toLong()), null, 300.0) }
        val model = AllTimelinesModel(lines, emptyList(), emptyList(), true)
        compose.setContent { scaled { AllTimelines(model, false, TimeDisplayOption.REGULAR, Modifier.fillMaxWidth().height(200.dp)) } }
    }

    private fun assertCaptionInsidePanel() {
        val caption = compose.onNodeWithText("Per-ingestion timeline estimate").assertIsDisplayed()
        val captionLayout = layout("Per-ingestion timeline estimate")
        assertComplete(captionLayout)
        val text = caption.fetchSemanticsNode().boundsInRoot
        val panel = compose.onNodeWithTag("timeline_readout").fetchSemanticsNode().boundsInRoot
        assertTrue("Estimate caption is cut off by panel", text.top >= panel.top && text.bottom <= panel.bottom + 1f)
        assertTrue("Estimate caption extends past panel", text.left >= panel.left && text.right <= panel.right + 1f)
        assertTrue("Estimate caption is only partly visible", text.height + 1f >= captionLayout.size.height)
    }

    private fun layout(text: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single()
    }

    private fun assertComplete(layout: TextLayoutResult) {
        assertTrue("Text lost characters", layout.getLineEnd(layout.lineCount - 1) == layout.layoutInput.text.length)
        (0 until layout.lineCount).forEach {
            assertFalse("Text ellipsized", layout.isLineEllipsized(it))
            assertTrue("Text exceeds available width", layout.getLineLeft(it) >= -1f && layout.getLineRight(it) <= layout.size.width + 1f)
            assertTrue("Text exceeds available height", layout.getLineBottom(it) <= layout.size.height + 1f)
        }
    }

    @Composable private fun scaled(content: @Composable () -> Unit) {
        val density = LocalDensity.current
        val platform = InstrumentationRegistry.getArguments().getString("platformFontScale") == "true"
        CompositionLocalProvider(LocalDensity provides if (platform) density else Density(density.density, 2f)) {
            PsyZineTheme { Surface(if (platform) Modifier.fillMaxWidth() else Modifier.width(320.dp)) { content() } }
        }
    }

    private fun capture(name: String) {
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "large-text-captures").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            assertTrue(compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
        }
    }
}
