// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.psyzine

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.isaakhanimann.journal.data.room.experiences.entities.AdaptiveColor
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import com.isaakhanimann.journal.data.substances.classes.roa.*
import com.isaakhanimann.journal.ui.main.AcceptConditionsScreen
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.DataForOneEffectLine
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.TimeDisplayOption
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.AllTimelines
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.AllTimelinesModel
import com.isaakhanimann.journal.ui.theme.PsyZineTheme
import com.isaakhanimann.journal.ui.theme.ThemeId
import org.junit.Rule
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class OnboardingTimelineUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun onboardingOpensOnlyWhenEveryAcknowledgementIsCheckedAndClosesAgain() {
        compose.setContent { PsyZineTheme(ThemeId.NORD) { AcceptConditionsScreen {} } }
        compose.onNodeWithContentDescription("PsyZine eye closed").assertExists()
        compose.onNodeWithText("Continue").assertIsNotEnabled()
        capture("onboarding-closed")
        compose.onAllNodes(isToggleable()).assertCountEquals(4)
        repeat(3) { index ->
            compose.onAllNodes(isToggleable())[index].performScrollTo().performClick()
            compose.onNodeWithContentDescription("PsyZine eye closed").assertExists()
        }
        compose.onAllNodes(isToggleable())[3].performScrollTo().performClick()
        compose.onNodeWithContentDescription("PsyZine eye open").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Continue").assertIsEnabled()
        capture("onboarding-open")
        compose.onAllNodes(isToggleable())[0].performScrollTo().performClick()
        compose.onNodeWithContentDescription("PsyZine eye closed").assertExists()
        compose.onNodeWithText("Continue").assertIsNotEnabled()
    }

    @Test fun touchingAndDraggingUpdatesPeakAndMilligramsThenReleaseClearsReadout() {
        showGraph()
        compose.onNodeWithTag("timeline_canvas").performTouchInput {
            down(Offset(width * (1200f / 7800f), height * .85f))
        }
        compose.onNodeWithText("50% of peak · 50 mg equivalent").assertExists()
        capture("timeline-touch")
        compose.onNodeWithTag("timeline_canvas").performTouchInput {
            moveTo(Offset(width * (3000f / 7800f), height * .85f))
        }
        compose.onNodeWithText("100% of peak · 100 mg equivalent").assertExists()
        compose.onNodeWithTag("timeline_canvas").performTouchInput { up() }
        compose.onNodeWithTag("timeline_readout").assertDoesNotExist()
    }

    @Test fun mouseHoverUpdatesWithoutPressingAndExitClearsReadout() {
        showGraph()
        compose.onNodeWithTag("timeline_canvas").performMouseInput {
            enter(Offset(width * (1200f / 7800f), height * .85f))
            moveTo(Offset(width * (3000f / 7800f), height * .85f))
        }
        compose.onNodeWithText("100% of peak · 100 mg equivalent").assertExists()
        compose.onNodeWithTag("timeline_canvas").performMouseInput { exit() }
        compose.onNodeWithTag("timeline_readout").assertDoesNotExist()
    }

    @Test fun readoutStaysInVisibleViewportWhenTheGraphIsWiderThanTheScreen() {
        showGraph(wide = true)
        compose.onNodeWithTag("timeline_canvas").performTouchInput {
            down(Offset(width * .1f, height * .85f))
        }
        val panel = compose.onNodeWithTag("timeline_readout").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val viewport = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue(panel.left >= viewport.left && panel.right <= viewport.right)
        compose.onNodeWithTag("timeline_canvas").performTouchInput { up() }
    }

    @Test fun verticalGestureStillScrollsTheJournalAndCancelsTheCursor() {
        val scroll = ScrollState(0)
        val model = exampleModel()
        compose.setContent { PsyZineTheme(ThemeId.NORD) {
            Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                AllTimelines(model, false, TimeDisplayOption.RELATIVE_TO_START,
                    Modifier.fillMaxWidth().height(240.dp))
                Spacer(Modifier.height(1200.dp))
            }
        } }
        compose.onNodeWithTag("timeline_canvas").performTouchInput { swipeUp() }
        compose.runOnIdle { assertTrue(scroll.value > 0) }
        compose.onNodeWithTag("timeline_readout").assertDoesNotExist()
    }

    private fun showGraph(wide: Boolean = false) {
        val model = exampleModel()
        compose.setContent { PsyZineTheme(ThemeId.NORD) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                // TouchInputScope.width uses clipped bounds. Keep the normal fixture
                // fully visible so fractional touches map to the intended graph time.
                val graphWidth = if (wide) 1200.dp else minOf(360.dp, maxWidth)
                Box(Modifier.horizontalScroll(rememberScrollState())) {
                    AllTimelines(model, false, TimeDisplayOption.RELATIVE_TO_START,
                        Modifier.width(graphWidth).height(240.dp))
                }
            }
        } }
    }

    private fun exampleModel(): AllTimelinesModel {
        val start = Instant.parse("2026-01-01T12:00:00Z")
        fun range(seconds: Float) = DurationRange(seconds, seconds, DurationUnits.SECONDS)
        val line = DataForOneEffectLine("Example", AdministrationRoute.ORAL,
            RoaDuration(range(600f), range(1200f), range(1800f), range(2400f), null, null),
            1f, .5f, AdaptiveColor.BLUE, start, null, 100.0)
        return AllTimelinesModel(listOf(line), emptyList(), emptyList(), true)
    }

    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "onboarding-timeline-screenshots").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
