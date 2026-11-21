// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.psyzine

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.isaakhanimann.journal.data.attachments.JournalMutationLock
import com.isaakhanimann.journal.data.definitions.*
import com.isaakhanimann.journal.data.room.*
import com.isaakhanimann.journal.data.substances.parse.SubstanceParser
import com.isaakhanimann.journal.data.substances.repositories.SubstanceRepository
import com.isaakhanimann.journal.ui.tabs.settings.combinations.UserPreferences
import com.isaakhanimann.journal.ui.tabs.stats.tolerance.*
import com.isaakhanimann.journal.ui.theme.PsyZineTheme
import kotlinx.coroutines.flow.flowOf
import org.junit.*
import org.junit.Assert.*
import java.io.IOException
import java.time.*

class ToleranceUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Before fun emulatorOnly() {
        Assume.assumeTrue("Synthetic emulator UI only", android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
    }
    @Test fun largeTextRtlChartExposesExactIntervalsAndPeriodAndFullViewActions() {
        val cutoff = Instant.parse("2026-03-15T12:00:00Z")
        val subject = SubjectId.Definition(1)
        val name = "Very long synthetic custom definition for tolerance"
        val row = SubjectToleranceIntervals(subject, name, "FUTURE_COLOR", mapOf(
            ToleranceBand.FULL to listOf(ToleranceInterval(cutoff.minus(Duration.ofDays(1)), cutoff.plus(Duration.ofDays(1)))),
            ToleranceBand.HALF to listOf(ToleranceInterval(cutoff.plus(Duration.ofDays(1)), cutoff.plus(Duration.ofDays(3)))),
        ), setOf(1))
        val missing = SubjectId.Catalog("Missing numeric metadata")
        val projection = ToleranceProjection(listOf(row), setOf(missing), mapOf(missing to "Missing numeric metadata"))
        val report = ToleranceReport(projection, cutoff.minus(Duration.ofDays(60)), cutoff, emptySet(), "synthetic")
        var opened: String? = null
        var selected: TolerancePeriod? = null
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f), LocalLayoutDirection provides LayoutDirection.Rtl) {
                PsyZineTheme { ToleranceScreenContent(ToleranceUiState(false, report, zone = ZoneId.of("Europe/Prague")), false,
                    period = { selected = it }, retry = {}, back = {}, openSubject = { key, generation -> opened = "$key/$generation" }) }
            }
        }
        compose.onNodeWithTag("tolerance-list").performScrollToNode(hasText("$name*"))
        compose.onNodeWithText("$name*").assertIsDisplayed()
        compose.onNodeWithTag("tolerance-list").performScrollToNode(hasText("Full:", substring = true))
        val node = compose.onNode(hasText("Full:", substring = true)).fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        compose.runOnIdle { node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts) }
        assertTrue(layouts.isNotEmpty()); assertTrue(layouts.none { it.hasVisualOverflow })
        val device = androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val capture = "/sdcard/Download/psyzine-ui-check-${java.util.UUID.randomUUID()}"
        device.waitForIdle()
        // Synthetic emulator output survives Gradle's test-package cleanup for host inspection.
        device.executeShellCommand("mkdir -p $capture")
        device.executeShellCommand("screencap -p $capture/tolerance-200-rtl.png")
        println("PsyZine tolerance screenshot: $capture/tolerance-200-rtl.png")
        compose.onNodeWithTag("tolerance-list").performScrollToNode(hasContentDescription("Open tolerance for $name"))
        compose.onNodeWithContentDescription("Open tolerance for $name").performClick()
        assertEquals("custom:1/synthetic", opened)
        compose.onNodeWithTag("tolerance-list").performScrollToNode(hasText("Missing numeric metadata"))
        compose.onNodeWithText("Missing numeric metadata").assertIsDisplayed()
        compose.onNodeWithTag("tolerance-list").performScrollToNode(hasText("Past 2M"))
        compose.onNodeWithText("Past 2M").performClick()
        compose.onNodeWithText("Past 8M").performScrollTo().performClick()
        assertEquals(TolerancePeriod.PAST_8M, selected)
    }
    @Test fun failedPeriodWriteExplainsTheErrorAndRetainsThePreviousPeriod() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val models = ViewModelStore()
        try {
            val guard = JournalSessionGuard(db, JournalMutationLock())
            val repo = ToleranceRepository(db, guard, SubstanceRepository(context, SubstanceParser()))
            val preferences = UserPreferences(object : DataStore<Preferences> {
                override val data = flowOf(emptyPreferences())
                override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences = throw IOException("injected failed write")
            })
            val calendar = LocalCalendar(context, Clock.fixed(Instant.parse("2026-03-15T12:00:00Z"), ZoneOffset.UTC), CalendarZone())
            lateinit var model: ToleranceViewModel
            compose.runOnUiThread { model = ToleranceViewModel(repo, calendar, preferences, SavedStateHandle()); models.put("tolerance", model) }
            compose.setContent { PsyZineTheme { ToleranceScreen(back = {}, openSubject = { _, _ -> }, model = model) } }
            compose.waitUntil(15000) { model.state.value.report != null || model.state.value.error != null }
            assertNull(model.state.value.error)
            compose.onNodeWithText("Past 2M").performClick()
            compose.onNodeWithText("Past 1M").performClick()
            compose.waitUntil(15000) { model.saveError != null }
            compose.onNodeWithText("Could not save this period. The previous period is retained. Try again.").assertIsDisplayed()
            assertEquals(TolerancePeriod.PAST_2M, model.state.value.period)
            assertFalse(model.saving)
        } finally { compose.runOnUiThread { models.clear() }; db.close() }
    }
}
