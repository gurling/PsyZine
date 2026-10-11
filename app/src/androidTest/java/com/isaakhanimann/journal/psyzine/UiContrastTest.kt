// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.psyzine

import android.graphics.Bitmap
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.isaakhanimann.journal.data.room.experiences.entities.*
import com.isaakhanimann.journal.data.room.experiences.relations.IngestionWithCompanionAndCustomUnit
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import com.isaakhanimann.journal.data.substances.classes.roa.*
import com.isaakhanimann.journal.ui.main.navigation.topLevelRoutes
import com.isaakhanimann.journal.ui.tabs.journal.experience.ExperienceScreen
import com.isaakhanimann.journal.ui.tabs.journal.experience.TimelineDisplayOption
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.*
import com.isaakhanimann.journal.ui.tabs.journal.experience.models.*
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.AllTimelinesModel
import com.isaakhanimann.journal.ui.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class UiContrastTest {
    @get:Rule val compose = createComposeRule()

    @Test fun journalCardsAndNavigationRenderWithReferenceColorsAndWorkingActions() {
        var infoOpened = false
        compose.setContent { PsyZineTheme(ThemeId.FRAPPE) { JournalFixture { infoOpened = true } } }
        compose.onNodeWithText("Example entry").assertIsDisplayed()
        val title = compose.onNodeWithText("Effect timeline").fetchSemanticsNode().boundsInRoot
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        val scheme = ThemePalettes.get(ThemeId.FRAPPE).scheme
        assertEquals(scheme.surfaceContainerLow.toArgb(), image.getPixel(title.center.x.toInt(), (title.bottom + 4).toInt()))
        assertEquals(scheme.surfaceContainer.toArgb(), image.getPixel(2, image.height - 4))
        image.recycle()
        compose.onNodeWithText("Info").performClick()
        compose.runOnIdle { assertTrue(infoOpened) }
        capture("frappe-journal")
    }

    @Test fun captureJournalAcrossAllThirteenPalettes() {
        var theme by mutableStateOf(ThemeId.FRAPPE)
        compose.setContent { PsyZineTheme(theme) { JournalFixture {} } }
        ThemePalettes.all.forEach { palette ->
            compose.runOnIdle { theme = palette.id }
            compose.onNodeWithText("Example entry").assertExists()
            compose.onNodeWithText("Info").assertHasClickAction()
            compose.onNodeWithTag("timeline_canvas").assertIsDisplayed()
            capture(palette.id.key)
        }
    }

    @Composable
    private fun JournalFixture(onInfo: () -> Unit) {
        val model = remember { exampleModel() }
        val timeline = remember { AllTimelinesModel(model.dataForEffectLines, emptyList(), emptyList(), true) }
        NavigationSuiteScaffold(navigationSuiteItems = {
            topLevelRoutes.forEach { route ->
                item(selected = route.name == "Journal", onClick = {},
                    icon = { Icon(route.filledIcon, route.name) }, label = { Text(route.name) })
            }
        }) {
            ExperienceScreen(
                oneExperienceScreenModel = model,
                timelineDisplayOption = TimelineDisplayOption.Shown(timeline),
                isOralDisclaimerHidden = false, onChangeIsOralDisclaimerHidden = {},
                addIngestion = {}, deleteExperience = {}, navigateToEditExperienceScreen = {},
                navigateToExplainTimeline = onInfo, navigateToIngestionScreen = {},
                navigateToAddRatingScreen = {}, navigateToAddTimedNoteScreen = {}, navigateBack = {},
                saveIsFavorite = {}, navigateToEditRatingScreen = {}, navigateToEditTimedNoteScreen = {},
                savedTimeDisplayOption = SavedTimeDisplayOption.REGULAR,
                timeDisplayOption = TimeDisplayOption.REGULAR, onChangeTimeDisplayOption = {},
                navigateToTimelineScreen = {}, areDosageDotsHidden = false, isTimelineHidden = false,
                photosContent = {
                    CardWithTitle(title = "Photos") {
                        Text("Your private photos stay on this device.")
                        androidx.compose.material3.TextButton(onClick = {}) { Text("Add photos") }
                    }
                }
            )
        }
    }

    private fun exampleModel(): OneExperienceScreenModel {
        val start = Instant.now().minusSeconds(25 * 60)
        fun range(seconds: Float) = DurationRange(seconds, seconds, DurationUnits.SECONDS)
        val duration = RoaDuration(range(600f), range(1800f), range(5400f), range(3600f), null, null)
        val ingestion = Ingestion(substanceName = "Example entry", time = start,
            administrationRoute = AdministrationRoute.ORAL, dose = 60.0,
            isDoseAnEstimate = false, estimatedDoseStandardDeviation = null, units = "mg",
            experienceId = 0, notes = null, stomachFullness = null, consumerName = null, customUnitId = null)
        val element = IngestionElement(IngestionWithCompanionAndCustomUnit(ingestion,
            SubstanceCompanion("Example entry", AdaptiveColor.CYAN), null), duration, 6)
        return OneExperienceScreenModel(false, "11 October 2026", start, "", "", false,
            listOf(element), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
            listOf(DataForOneEffectLine("Example entry", AdministrationRoute.ORAL, duration,
                1f, .5f, AdaptiveColor.CYAN, start, null, 60.0)))
    }

    private fun capture(name: String) {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(app.getExternalFilesDir(null), "contrast-screenshots").apply { mkdirs() }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(directory, "$name.png").outputStream().use {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        bitmap.recycle()
    }
}
