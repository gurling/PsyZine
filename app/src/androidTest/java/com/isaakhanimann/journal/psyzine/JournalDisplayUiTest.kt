// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.psyzine

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.isaakhanimann.journal.data.preferences.JournalDisplaySettings
import com.isaakhanimann.journal.data.room.experiences.entities.AdaptiveColor
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import com.isaakhanimann.journal.data.substances.classes.roa.*
import com.isaakhanimann.journal.ui.tabs.journal.experience.TimelineDisplayOption
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.DataForOneEffectLine
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.TimeDisplayOption
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.*
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.screen.TimelineScreen
import com.isaakhanimann.journal.ui.theme.PsyZineTheme
import com.isaakhanimann.journal.ui.utils.LocalJournalDisplaySettings
import org.junit.*
import org.junit.Assert.*
import java.time.Instant

class JournalDisplayUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Before fun prepare() {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        val arguments = androidx.test.platform.app.InstrumentationRegistry.getArguments()
        if (arguments.getString("platformFontScale") == "true") {
            assertEquals(arguments.getString("expectedFontScale")!!.toFloat(), compose.activity.resources.configuration.fontScale, 0.01f)
        }
    }
    private fun model(current: Boolean = false, count: Int = 1): AllTimelinesModel {
        val start = if (current) Instant.now().minusSeconds(3600) else Instant.parse("2026-01-01T00:00:00Z")
        val duration = RoaDuration(DurationRange(1f, 1f, DurationUnits.MINUTES), DurationRange(1f, 1f, DurationUnits.MINUTES),
            DurationRange(10f, 10f, DurationUnits.HOURS), DurationRange(1f, 1f, DurationUnits.HOURS), null, null)
        return AllTimelinesModel(List(count) { index -> DataForOneEffectLine("Substance ${index + 1}", AdministrationRoute.ORAL,
            duration, 1f, 0.5f, AdaptiveColor.RED, start, null, 10.0, sourceIngestionId = index + 1) }, emptyList(), emptyList(), true)
    }
    private fun graph() = compose.onNode(hasContentDescription("Estimated effect timeline", substring = true))
    private fun moveCursor() {
        val action = graph().fetchSemanticsNode().config[SemanticsActions.CustomActions].first { it.label == "Move cursor later by 15 minutes" }
        compose.runOnIdle { assertTrue(action.action()) }
    }

    @Test fun labelledSettingsSwitchesWrapAndRemainToggleableAtTwoHundredPercent() {
        val settings = mutableStateOf(JournalDisplaySettings())
        compose.setContent {
            val density = LocalDensity.current
            val platform = androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("platformFontScale") == "true"
            CompositionLocalProvider(LocalDensity provides if (platform) density else Density(density.density, 2f)) {
                PsyZineTheme {
                    com.isaakhanimann.journal.ui.tabs.settings.SettingsScreen(navigateToFAQ = {}, navigateToComboSettings = {},
                        navigateToSubstanceColors = {}, navigateToCustomUnits = {}, navigateToDonate = {}, deleteEverything = {},
                        importFile = {}, exportFile = {}, snackbarHostState = androidx.compose.material3.SnackbarHostState(),
                        areDosageDotsHidden = false, saveDosageDotsAreHidden = {}, isTimelineHidden = false, saveIsTimelineHidden = {},
                        areSubstanceHeightsIndependent = true, saveAreSubstanceHeightsIndependent = {}, journalDisplaySettings = settings.value,
                        saveShowComponents = { settings.value = settings.value.copy(showComponents = it) },
                        saveHideTimelineQuantities = { settings.value = settings.value.copy(hideTimelineQuantities = it) })
                }
            }
        }
        compose.onNode(isToggleable() and hasText("Show components in experience")).performScrollTo().assertIsOn().performClick().assertIsOff()
        compose.onNode(isToggleable() and hasText("Hide timeline quantity details")).performScrollTo().assertIsOff().performClick().assertIsOn()
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithText("Hide timeline quantity details", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        assertEquals(layout.layoutInput.text.length, layout.getLineEnd(layout.lineCount - 1))
        assertTrue((0 until layout.lineCount).all { layout.getLineBottom(it) <= layout.size.height + 1f && !layout.isLineEllipsized(it) })
        assertEquals(JournalDisplaySettings(false, true), settings.value)
        captureJournalDisplay("settings-display-controls", compose)
    }

    @Test fun hiddenDetailsKeepTheTimeCursorAndSuppressVisualAndSemanticQuantities() {
        val hidden = mutableStateOf(true)
        val data = model()
        compose.setContent { PsyZineTheme {
            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            AllTimelines(data, false, TimeDisplayOption.REGULAR, Modifier.fillMaxWidth().height(240.dp), hideQuantities = hidden.value)
            }
        } }
        moveCursor()
        compose.onNodeWithTag("timeline_readout").assertIsDisplayed()
        compose.onNodeWithTag("timeline-quantity-list").assertDoesNotExist()
        assertFalse(graph().fetchSemanticsNode().config[SemanticsProperties.StateDescription].contains("equivalent"))
        compose.runOnIdle { hidden.value = false }
        compose.onNodeWithTag("timeline-quantity-list").assertIsDisplayed()
        compose.onNodeWithText("100% of peak · 10 mg equivalent").assertIsDisplayed()
        assertTrue(graph().fetchSemanticsNode().config[SemanticsProperties.StateDescription].contains("10 mg equivalent"))
        assertEquals(1, data.groupDrawables.size)
        captureJournalDisplay("timeline-selected-quantities", compose)
    }

    @Test fun currentReadoutIsLazyAndDraggingFromThePanelStillMovesTheCursor() {
        val data = model(current = true, count = 500)
        compose.setContent { PsyZineTheme { Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            AllTimelines(data, true, TimeDisplayOption.REGULAR, Modifier.fillMaxWidth().height(300.dp))
        } } }
        compose.waitUntil { compose.onAllNodes(hasTestTag("timeline_now_readout")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Substance 500", substring = true).assertDoesNotExist()
        compose.onNodeWithTag("timeline-quantity-list").performScrollToIndex(499)
        compose.onNodeWithText("Substance 500", substring = true).assertIsDisplayed()
        captureJournalDisplay("timeline-now-500", compose)
        compose.onNodeWithTag("timeline_now_readout").performTouchInput { down(center); moveBy(Offset(80f, 0f)) }
        try { compose.onNodeWithTag("timeline_readout").assertIsDisplayed() }
        finally { compose.onNodeWithTag("timeline_canvas").performTouchInput { up() } }
        compose.onNodeWithTag("timeline_readout").assertDoesNotExist()
        compose.onNodeWithTag("timeline_now_readout").assertIsDisplayed()
    }

    @Test fun fullscreenLandscapeHidesDetailsAndPortraitRestoresTheSavedChoice() {
        val orientation = mutableStateOf(Configuration.ORIENTATION_PORTRAIT)
        val saved = mutableStateOf(JournalDisplaySettings(hideTimelineQuantities = false))
        val data = model()
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply { this.orientation = orientation.value }
            CompositionLocalProvider(LocalConfiguration provides configuration, LocalJournalDisplaySettings provides saved.value) {
                PsyZineTheme { TimelineScreen("Me", TimelineDisplayOption.Shown(data), TimeDisplayOption.REGULAR) }
            }
        }
        moveCursor()
        compose.onNodeWithTag("timeline-quantity-list").assertIsDisplayed()
        compose.runOnIdle { orientation.value = Configuration.ORIENTATION_LANDSCAPE }
        compose.onNodeWithTag("timeline_readout").assertIsDisplayed()
        compose.onNodeWithTag("timeline-quantity-list").assertDoesNotExist()
        assertFalse(saved.value.hideTimelineQuantities)
        captureJournalDisplay("timeline-landscape-policy", compose)
        compose.runOnIdle { orientation.value = Configuration.ORIENTATION_PORTRAIT }
        compose.onNodeWithTag("timeline-quantity-list").assertIsDisplayed()
        compose.runOnIdle { saved.value = saved.value.copy(hideTimelineQuantities = true) }
        compose.onNodeWithTag("timeline-quantity-list").assertDoesNotExist()
    }

    @Test fun verticalGestureOverTheCurrentReadoutScrollsTheSurroundingJournal() {
        val data = model(current = true)
        val scroll = androidx.compose.foundation.ScrollState(0)
        compose.setContent { PsyZineTheme {
            Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(scroll)) {
                AllTimelines(data, true, TimeDisplayOption.REGULAR, Modifier.fillMaxWidth().height(300.dp))
                Spacer(Modifier.height(1200.dp)); Text("End")
            }
        } }
        compose.waitUntil { compose.onAllNodes(hasTestTag("timeline_now_readout")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("timeline_canvas").performTouchInput { swipeUp() }
        compose.runOnIdle { assertTrue(scroll.value > 0) }
        compose.onNodeWithTag("timeline_readout").assertDoesNotExist()
    }
}
