// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.psyzine

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.platform.app.InstrumentationRegistry
import com.isaakhanimann.journal.data.definitions.*
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.ingestion.CalculatedComponents
import com.isaakhanimann.journal.ui.tabs.journal.experience.models.CalculatedComponent
import com.isaakhanimann.journal.ui.theme.PsyZineTheme
import org.junit.*
import org.junit.Assert.*
import java.io.File

/** The optional platform profile is set and restored by the emulator-only host check. */
class CalculatedComponentsUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val platformFontScale get() = InstrumentationRegistry.getArguments().getString("componentPlatformFontScale")?.toFloat()

    @Before fun prepare() {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        platformFontScale?.let { assertEquals(it, compose.activity.resources.configuration.fontScale, 0.01f) }
    }

    private fun show(count: Int, rtl: Boolean = false, hidden: Boolean = false) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides if (platformFontScale != null) density else Density(density.density, 2f),
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                PsyZineTheme {
                    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
                        .testTag("component-parent").verticalScroll(rememberScrollState())) {
                        CalculatedComponents(42, List(count) { index ->
                            CalculatedComponent(ResolvedDose(SubjectId.Catalog("Ingredient ${index + 1}"),
                                "Ingredient ${index + 1}", "mg", DoseQuantity(if (hidden) null else 0.0001), listOf(index), hidden),
                                numDots = null, isShownAsCurve = !hidden)
                        }, hideDots = false)
                    }
                }
            }
        }
    }

    @Test fun largeListAtTwoHundredPercentFontComposesLazilyAndEveryComponentIsReachable() {
        show(500)
        compose.onNodeWithText("Ingredient 21: 0.0001 mg").assertDoesNotExist()
        compose.onNodeWithText("Show all 500 components").performScrollTo().performClick()
        compose.onNodeWithTag("calculated-component-list").assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("Calculated components").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val title = layouts.single()
        assertFalse(title.hasVisualOverflow)
        val lines = (0 until title.lineCount).map { title.layoutInput.text.text.substring(title.getLineStart(it), title.getLineEnd(it)).trim() }
        assertTrue("Title split inside a word: $lines", lines == listOf("Calculated components") || lines == listOf("Calculated", "components"))
        compose.onNodeWithText("Ingredient 500: 0.0001 mg").assertDoesNotExist()
        compose.onNodeWithTag("calculated-component-list").performScrollToNode(hasText("Ingredient 500: 0.0001 mg"))
        compose.onNodeWithText("Ingredient 500: 0.0001 mg").assertIsDisplayed()
        capture("components-500-font-200", "calculated-component-list")
        compose.onNodeWithText("Close components").performClick()
        compose.waitUntil { compose.onAllNodes(hasTestTag("calculated-component-list")).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("Show all 500 components").assertIsDisplayed()
    }

    @Test fun unknownQuantitiesAndHiddenCurveExplanationRemainAccessibleInRtl() {
        show(1, rtl = true, hidden = true)
        compose.onNodeWithText("Ingredient 1: Unknown dose").assertIsDisplayed()
        compose.onNodeWithText("Not shown separately on timeline").assertIsDisplayed()
        compose.onNodeWithText("Show all 1 components").assertDoesNotExist()
        val text = compose.onNodeWithText("Ingredient 1: Unknown dose").fetchSemanticsNode().boundsInRoot
        val parent = compose.onNodeWithTag("component-parent").fetchSemanticsNode().boundsInRoot
        assertTrue(text.left >= parent.left && text.right <= parent.right)
        capture("components-unknown-font-200-rtl", "component-parent")
    }

    private fun capture(name: String, descendantTag: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val output = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
        val directory = if (output != null) File(output, "component-checks")
            else File(instrumentation.targetContext.getExternalFilesDir(null), "component-checks")
        directory.mkdirs()
        // Draw the selected owner first, then capture the compositor: a dialog's root
        // capture can otherwise select the activity window beneath the modal.
        compose.onNodeWithTag(descendantTag).captureToImage()
        compose.waitForIdle()
        instrumentation.waitForIdleSync()
        android.os.SystemClock.sleep(150)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            File(directory, "$name.png").outputStream().use {
                assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally { bitmap.recycle() }
    }
}
