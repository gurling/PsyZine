// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.psyzine

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.*
import androidx.test.platform.app.InstrumentationRegistry
import com.isaakhanimann.journal.ui.tabs.search.substance.roa.dose.DoseClassificationRow
import com.isaakhanimann.journal.ui.theme.PsyZineTheme
import com.isaakhanimann.journal.ui.tabs.settings.SettingsScreen
import com.isaakhanimann.journal.ui.tabs.journal.calendar.Day
import com.isaakhanimann.journal.ui.tabs.stats.StatsScreen
import com.isaakhanimann.journal.ui.tabs.stats.StatsPreviewProvider
import com.isaakhanimann.journal.ui.tabs.settings.customunits.add.EditCustomUnitSections
import com.isaakhanimann.journal.ui.tabs.journal.addingestion.dose.customunit.ChooseDoseCustomUnitScreen
import com.isaakhanimann.journal.ui.tabs.journal.experience.rating.FloatingDoneButton
import com.isaakhanimann.journal.ui.tabs.journal.experience.rating.RatingPickerSection
import com.isaakhanimann.journal.data.room.experiences.entities.ShulginRatingOption
import com.isaakhanimann.journal.data.room.experiences.entities.CustomUnit
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import com.isaakhanimann.journal.data.substances.classes.roa.RoaDose
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.DayPosition
import java.time.LocalDate
import com.isaakhanimann.journal.ui.tabs.journal.components.ExperienceRow
import com.isaakhanimann.journal.ui.tabs.journal.components.ExperienceWithIngestionsCompanionsAndRatingsPreviewProvider
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.ingestion.IngestionRow
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.ingestion.IngestionRowPreviewProvider
import org.junit.*
import org.junit.Assert.*
import java.io.File

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
class LargeTextLayoutTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Before fun prepareEmulatorTestHost() {
        Assume.assumeTrue("Uses emulator keyboard settings only", android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            .executeShellCommand("settings put secure show_ime_with_hard_keyboard 1")
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
    }

    @Test fun doseReferenceFitsNarrowScreensAtLargeFonts() {
        compose.setContent { scaled { DoseClassificationRow(5.0, 15.0, 25.0, 45.0, "mg") } }
        capture("dose-before-or-after")
        assertNoTextOverflow()
        compose.onNodeWithText("mg").assertIsDisplayed()
    }

    @Test fun settingsSwitchesRemainVisibleBesideWrappingLabels() {
        compose.setContent { scaled {
            SettingsScreen(navigateToFAQ = {}, navigateToComboSettings = {}, navigateToSubstanceColors = {},
                navigateToCustomUnits = {}, navigateToDonate = {}, deleteEverything = {}, importFile = {}, exportFile = {},
                snackbarHostState = remember { SnackbarHostState() }, areDosageDotsHidden = false,
                saveDosageDotsAreHidden = {}, isTimelineHidden = false, saveIsTimelineHidden = {},
                areSubstanceHeightsIndependent = false, saveAreSubstanceHeightsIndependent = {})
        } }
        compose.onNodeWithText("Independent substance heights").performScrollTo()
        capture("settings-before-or-after")
        assertNoTextOverflow()
        val switches = compose.onAllNodes(isToggleable()).fetchSemanticsNodes()
        assertEquals(6, switches.size)
        val independent = compose.onAllNodes(isToggleable())[2].assertIsDisplayed().fetchSemanticsNode()
        assertTrue("Switch clipped: ${independent.boundsInRoot}", independent.boundsInRoot.width >= 48 * compose.density.density)
        assertTrue("Switch outside the card", independent.boundsInRoot.right <= compose.onRoot().fetchSemanticsNode().boundsInRoot.right)
    }

    @Test fun journalEntryKeepsFavoriteAndLongTitleVisible() {
        val sample = ExperienceWithIngestionsCompanionsAndRatingsPreviewProvider().values.first()
        val entry = sample.copy(experience = sample.experience.copy(title = "A long synthetic journal title with several words", isFavorite = true))
        compose.setContent { scaled { ExperienceRow(entry, isTimeRelativeToNow = false) } }
        capture("journal-before-or-after")
        assertNoTextOverflow()
        val favorite = compose.onNodeWithContentDescription("Is favorite").assertIsDisplayed().fetchSemanticsNode()
        assertTrue("Favorite clipped: ${favorite.boundsInRoot}", favorite.boundsInRoot.width >= 20 * compose.density.density)
        assertTrue("Favorite outside the entry", favorite.boundsInRoot.right <= compose.onRoot().fetchSemanticsNode().boundsInRoot.right)
    }

    @Test fun ingestionKeepsDosageDotsAndWrappedTextVisible() {
        val original = IngestionRowPreviewProvider().values.first()
        val sample = original.copy(ingestionWithCompanionAndCustomUnit = original.ingestionWithCompanionAndCustomUnit.copy(
            ingestion = original.ingestionWithCompanionAndCustomUnit.ingestion.copy(units = "long synthetic units", dose = 123456.789)))
        compose.setContent { scaled { IngestionRow(sample, false, Modifier.fillMaxWidth()) { Text("Friday 07:17") } } }
        capture("ingestion-before-or-after")
        assertNoTextOverflow()
    }

    @Test fun calendarDatesFitAtLargeFonts() {
        compose.setContent { scaled {
            Row {
                (18..24).forEach { day -> Box(Modifier.weight(1f)) {
                    Day(CalendarDay(LocalDate.of(2026, 10, day), DayPosition.MonthDate), {}, {})
                } }
            }
        } }
        capture("calendar-before-or-after")
        assertNoTextOverflow()
        (18..24).forEach { compose.onNodeWithText(it.toString()).assertIsDisplayed() }
    }

    @Test fun ratingDescriptionsRemainReadableAndSelectable() {
        var selected by mutableStateOf(ShulginRatingOption.TWO_PLUS)
        compose.setContent { scaled {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                RatingPickerSection(selected) { selected = it }
            }
        } }
        ShulginRatingOption.entries.forEach { rating ->
            compose.onNodeWithText(rating.shortDescription, useUnmergedTree = true).performScrollTo().assertIsDisplayed()
            capture("rating-${rating.name}")
            assertNoTextOverflow()
            compose.onNodeWithText(rating.shortDescription).performClick()
            compose.runOnIdle { assertEquals(rating, selected) }
        }
    }

    @Test fun statisticsPeriodsRemainReadableAtLargeFonts() {
        val sample = StatsPreviewProvider().values.first()
        compose.setContent { scaled { StatsScreen({ _, _ -> }, {}, sample, {}, emptyList()) } }
        capture("statistics-before-or-after")
        assertNoTextOverflow()
        listOf("7D", "30D", "26W", "12M", "10Y").forEach { label ->
            val tab = compose.onNodeWithText(label)
            if (compose.onAllNodes(hasText(label) and hasAnyAncestor(hasScrollAction())).fetchSemanticsNodes().isNotEmpty()) tab.performScrollTo()
            tab.assertIsDisplayed()
            tab.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { get ->
                val layouts = mutableListOf<TextLayoutResult>(); get(layouts)
                assertTrue("Period split across lines: $label", layouts.all { it.lineCount == 1 })
            }
        }
    }

    @Test fun customUnitFieldsWrapAndLastSectionClearsDoneButton() {
        compose.setContent { scaled {
            unitForm()
        } }
        compose.onNodeWithText("Uncertainty").performScrollTo()
        capture("custom-unit-fields")
        assertNoTextOverflow(scrollableValues = setOf("Synthetic capsule", "handmade capsule", "handmade capsules"))
        scrollToBottom()
        capture("custom-unit-bottom")
        val last = compose.onNodeWithText("Archived custom units don't show up when adding ingestions").assertIsDisplayed().fetchSemanticsNode()
        val done = compose.onNodeWithContentDescription("Done").assertIsDisplayed().fetchSemanticsNode()
        assertTrue("Done overlaps the final section", last.boundsInRoot.bottom <= done.boundsInRoot.top)
    }

    @Test fun customUnitChooserKeepsAmountsAndBottomActionsVisible() {
        val unit = CustomUnit.twoCBSample.copy(name = "Long synthetic custom unit name", unit = "handmade capsule", unitPlural = "handmade capsules")
        compose.setContent { scaled {
            ChooseDoseCustomUnitScreen(customUnit = unit, roaDose = RoaDose("mg", 5.0, 15.0, 25.0, 45.0),
                doseRemark = null, dose = 2.0, doseText = "2", onChangeDoseText = {}, estimatedDoseDeviationText = "1",
                estimatedDoseDeviation = 1.0, onChangeEstimatedDoseDeviationText = {}, isEstimate = true, onChangeIsEstimate = {},
                navigateToNext = {}, navigateToCreateCustomUnit = { _, _ -> }, useUnknownDoseAndNavigate = {},
                currentDoseClass = null, customUnitCalculationText = "2 handmade capsules × 10 mg = 20 mg")
        } }
        compose.onNodeWithText("Uncertainty").performScrollTo()
        capture("custom-unit-chooser-fields")
        assertNoTextOverflow(ellipsizedTitles = setOf(unit.name))
        scrollToBottom()
        capture("custom-unit-chooser-bottom")
        val create = compose.onNodeWithText("Create new unit").assertIsDisplayed().fetchSemanticsNode()
        val next = compose.onNodeWithContentDescription("Next").assertIsDisplayed().fetchSemanticsNode()
        assertTrue("Next overlaps the last action", create.boundsInRoot.bottom <= next.boundsInRoot.top)
        compose.onNodeWithText("Log unknown dose").assertIsDisplayed()
    }

    @Test fun doseReferenceAdaptsAcrossFontsWidthsAndDirections() {
        var scale by mutableStateOf(1f)
        var width by mutableStateOf(393.dp)
        var direction by mutableStateOf(LayoutDirection.Ltr)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale), LocalLayoutDirection provides direction) {
                PsyZineTheme { Surface(Modifier.width(width)) { DoseClassificationRow(5.0, 15.0, 25.0, 45.0, "mg") } }
            }
        }
        for (font in listOf(1f, 1.3f, 1.5f, 2f)) for (size in listOf(320.dp, 393.dp)) for (layout in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
            compose.runOnIdle { scale = font; width = size; direction = layout }
            assertNoTextOverflow()
            compose.onNodeWithText("mg").assertIsDisplayed()
            capture("dose-$font-${size.value}-${layout.name}")
        }
    }

    @Composable private fun unitForm() {
        Scaffold(topBar = { androidx.compose.material3.TopAppBar(title = { Text("2C-B unit") }) }, floatingActionButton = { FloatingDoneButton({}, Modifier.consumeWindowInsets(WindowInsets.navigationBars).imePadding()) }) { padding ->
                EditCustomUnitSections(padding = padding, substanceName = "2C-B", administrationRoute = AdministrationRoute.ORAL,
                    numberOfIngestionsWithThisCustomUnit = 0, roaDose = RoaDose("mg", 5.0, 15.0, 25.0, 45.0),
                    name = "Synthetic capsule", onChangeOfName = {}, doseText = "10", onChangeDoseText = {},
                    estimatedDoseStandardDeviationText = "1", onChangeEstimatedDoseStandardDeviationText = {},
                    isEstimate = true, onChangeIsEstimate = {}, currentDoseClass = null, isShowingUnitsField = true,
                    unit = "handmade capsule", onChangeOfUnits = {}, unitPlural = "handmade capsules", onChangeOfUnitPlural = {},
                    originalUnit = "mg", onChangeOfOriginalUnit = {}, note = "Synthetic note", onChangeOfNote = {},
                    isArchived = false, onChangeOfIsArchived = {})
            }
    }

    @Test fun focusedDoseAndDoneButtonRemainAboveKeyboard() {
        compose.setContent { scaled { unitForm() } }
        val field = compose.onNode(hasSetTextAction() and hasText("Dose per unit"))
        field.performScrollTo().performClick()
        val decor = compose.activity.window.decorView
        compose.waitUntil(60000) {
            androidx.core.view.ViewCompat.getRootWindowInsets(decor)?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true
        }
        compose.waitForIdle()
        capture("custom-unit-keyboard")
        // Older Android resizes the window itself; subtracting IME height again would
        // incorrectly report overlap. Compare window coordinates to its visible frame.
        val visible = android.graphics.Rect()
        val location = IntArray(2)
        decor.getWindowVisibleDisplayFrame(visible)
        decor.getLocationOnScreen(location)
        val keyboardTop = visible.bottom - location[1]
        val done = compose.onNodeWithContentDescription("Done").assertIsDisplayed().fetchSemanticsNode().boundsInWindow
        assertTrue("Done hidden by keyboard: $done, visible=$visible", done.bottom <= keyboardTop)
        val focused = field.assertIsDisplayed().fetchSemanticsNode().boundsInWindow
        assertTrue("Focused field under Done: $focused, Done=$done", focused.bottom <= done.top)
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "large-text-captures")
        // Some software-rendered older emulators cannot supply a native screenshot.
        // The component capture and the geometry assertions above remain mandatory.
        androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            .takeScreenshot(File(directory, "custom-unit-keyboard-native.png"))
    }

    @Test fun ordinaryDoseFieldsKeepLongUnitsOutsideTheAmount() {
        compose.setContent { scaled {
            com.isaakhanimann.journal.ui.tabs.journal.addingestion.dose.ChooseDoseScreen(
                navigateToVolumetricDosingScreen = {}, navigateToSaferSniffingScreen = {}, substanceName = "Synthetic substance",
                roaDose = null, administrationRoute = AdministrationRoute.ORAL, doseRemark = null,
                doseText = "2", onChangeDoseText = {}, estimatedDoseStandardDeviationText = "1",
                onChangeEstimatedDoseStandardDeviationText = {}, isValidDose = true, isEstimate = true,
                onChangeIsEstimate = {}, navigateToNext = {}, useUnknownDoseAndNavigate = {}, currentDoseClass = null,
                purityText = "100", onPurityChange = {}, isValidPurity = true, convertedDoseAndUnitText = null,
                isShowingUnitsField = true, units = longUnit, onChangeOfUnits = {}, navigateToCreateCustomUnit = {})
        } }
        compose.onNodeWithContentDescription("Expand").performScrollTo().performClick()
        checkDoseFields("ordinary-dose-fields", "Synthetic substance Oral dose")
    }

    @Test fun customSubstanceDoseFieldsKeepLongUnitsOutsideTheAmount() {
        compose.setContent { scaled {
            com.isaakhanimann.journal.ui.tabs.journal.addingestion.dose.customsubstance.CustomSubstanceChooseDoseScreen(
                navigateToSaferSniffingScreen = {}, navigateToCreateCustomUnit = {}, substanceName = "Synthetic substance",
                administrationRoute = AdministrationRoute.ORAL, doseText = "2", onChangeDoseText = {},
                estimatedDoseStandardDeviationText = "1", onChangeEstimatedStandardDeviationText = {},
                isValidDose = true, isEstimate = true, onChangeIsEstimate = {}, navigateToNext = {},
                useUnknownDoseAndNavigate = {}, purityText = "100", onPurityChange = {}, isValidPurity = true,
                convertedDoseAndUnitText = null, units = longUnit)
        } }
        checkDoseFields("custom-substance-dose-fields", "Oral Synthetic substance Dosage")
    }

    @Test fun ingestionEditorFieldsKeepLongUnitsAndNotesReadable() {
        val date = java.time.LocalDateTime.of(2026, 10, 14, 12, 30)
        compose.setContent { scaled {
            com.isaakhanimann.journal.ui.tabs.journal.experience.editingestion.EditIngestionScreen(
                note = "Synthetic note", onNoteChange = {}, isEstimate = true, onChangeIsEstimate = {},
                isKnown = true, toggleIsKnown = {}, dose = "2", onDoseChange = {}, estimatedDoseStandardDeviation = "1",
                onEstimatedDoseStandardDeviationChange = {}, units = longUnit, onUnitsChange = {}, experiences = emptyList(),
                selectedExperienceId = 2, onChangeId = {}, navigateBack = {}, deleteIngestion = {}, onDone = {},
                ingestionTimePickerOption = com.isaakhanimann.journal.ui.tabs.journal.addingestion.time.IngestionTimePickerOption.POINT_IN_TIME,
                onChangeTimePickerOption = {}, onChangeStartDateOrTime = {}, localDateTimeStart = date,
                onChangeEndDateOrTime = {}, localDateTimeEnd = date, consumerName = "", onChangeConsumerName = {},
                consumerNamesSorted = emptyList(), customUnit = null, onCustomUnitChange = {}, otherCustomUnits = emptyList(),
                addIngestionWithClonedTime = {})
        } }
        checkDoseFields("ingestion-editor-fields", "Edit ingestion")
        compose.onNodeWithText("Notes").performScrollTo().assertIsDisplayed()
    }

    private val longUnit = "very long synthetic measurement units"
    private fun checkDoseFields(captureName: String, title: String) {
        compose.onNode(hasText("Uncertainty") and hasSetTextAction()).performScrollTo().assertIsDisplayed()
        capture(captureName)
        assertNoTextOverflow(ellipsizedTitles = setOf(title), scrollableValues = setOf(longUnit))
        // The merged field also includes the converted-dose preview, which can
        // extend below the viewport. Check the visible description itself.
        compose.onNodeWithText("Standard deviation in $longUnit.", useUnmergedTree = true).assertIsDisplayed()
    }

    private fun scrollToBottom() {
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
            .performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 100000f) }
        compose.waitForIdle()
    }

    @Composable private fun scaled(content: @Composable () -> Unit) {
        val density = LocalDensity.current
        val platform = InstrumentationRegistry.getArguments().getString("platformFontScale") == "true"
        if (platform) assertEquals(InstrumentationRegistry.getArguments().getString("expectedFontScale")!!.toFloat(), density.fontScale, .01f)
        CompositionLocalProvider(LocalDensity provides if (platform) density else Density(density.density, 2f)) {
            PsyZineTheme { Surface(if (platform) Modifier.fillMaxWidth() else Modifier.width(320.dp)) { content() } }
        }
    }

    private fun assertNoTextOverflow(ellipsizedTitles: Set<String> = emptySet(), scrollableValues: Set<String> = emptySet()) {
        val errors = mutableListOf<String>()
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
            .fetchSemanticsNodes().forEach { node ->
                val layouts = mutableListOf<TextLayoutResult>()
                compose.runOnIdle { node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts) }
                layouts.forEach layoutLoop@ { layout ->
                    if (layout.layoutInput.text.text in scrollableValues || layout.layoutInput.text.text in ellipsizedTitles) return@layoutLoop
                    // Paragraph bounds can differ from rounded layout sizes by a fraction of a
                    // pixel. Check actual line bounds and retained characters, rather than treating
                    // that rounding as user-visible clipping.
                    val lines = (0 until layout.lineCount)
                    val clipped = layout.size.width == 0 || layout.size.height == 0 || lines.any {
                        layout.getLineLeft(it) < -1f || layout.getLineRight(it) > layout.size.width + 1f ||
                            layout.getLineBottom(it) > layout.size.height + 1f || layout.isLineEllipsized(it)
                    } || layout.getLineEnd(layout.lineCount - 1) < layout.layoutInput.text.length
                    if (clipped) errors.add("${layout.layoutInput.text.text}: size=${layout.size}, " +
                        "lines=${lines.map { listOf(layout.getLineLeft(it), layout.getLineRight(it), layout.getLineBottom(it)) }}")
                }
            }
        assertTrue("Clipped text: ${errors.joinToString("; ")}", errors.isEmpty())
    }

    private fun capture(name: String) {
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "large-text-captures").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            // Focusing text can create a second root for a selection handle popup.
            assertTrue(compose.onAllNodes(isRoot())[0].captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
        }
    }
}
