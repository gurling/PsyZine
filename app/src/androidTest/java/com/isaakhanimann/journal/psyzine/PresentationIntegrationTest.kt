// PsyZine, 2026-10-11. GPL-3.0-or-later.
package com.isaakhanimann.journal.psyzine

import android.app.Notification
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.room.Room
import com.isaakhanimann.journal.data.room.AppDatabase
import com.isaakhanimann.journal.data.room.experiences.entities.Experience
import com.isaakhanimann.journal.data.room.experiences.entities.Ingestion
import com.isaakhanimann.journal.data.substances.parse.SubstanceParser
import com.isaakhanimann.journal.data.substances.repositories.SubstanceRepository
import kotlinx.coroutines.runBlocking
import com.isaakhanimann.journal.data.active.*
import com.isaakhanimann.journal.data.room.experiences.entities.AdaptiveColor
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import com.isaakhanimann.journal.data.substances.classes.roa.*
import com.isaakhanimann.journal.service.*
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.DataForOneEffectLine
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.*
import com.isaakhanimann.journal.ui.theme.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.Clock
import java.time.ZoneOffset
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PresentationIntegrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val now = Instant.parse("2026-01-01T12:00:00Z")
    private fun model() = AllTimelinesModel(listOf(DataForOneEffectLine("Caffeine", AdministrationRoute.ORAL,
        RoaDuration(null, null, DurationRange(1f, 2f, DurationUnits.HOURS), null, null, null),
        1f, .5f, AdaptiveColor.RED, now, null)), emptyList(), emptyList(), true)
    @Test fun notificationDefaultsRedactLockScreenAndCarryElapsedTime() {
        val data = ActiveExperience(42, "generation", "private title", listOf(ActiveIngestion("Private substance", "100 mg", "oral", IngestionPhase.PEAK, AdaptiveColor.RED)), null, now, now.plusSeconds(30))
        val renderer = ActiveNotificationRenderer(context)
        val notification = renderer.notification(data, false, ThemePalettes.get(ThemeId.NORD))
        assertEquals(Notification.VISIBILITY_PRIVATE, notification.visibility)
        assertNotNull(notification.publicVersion)
        assertFalse(notification.publicVersion.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("Private substance"))
        assertEquals(now.toEpochMilli(), notification.`when`)
        assertTrue(notification.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertEquals(Notification.VISIBILITY_PUBLIC, renderer.notification(data, true, ThemePalettes.get(ThemeId.NORD)).visibility)
    }
    @Test fun notificationAndWidgetIntentRetainIdentityAndGeneration() {
        val intent = experienceIntent(context, 42, "generation")
        assertEquals(42, intent.getIntExtra(EXTRA_EXPERIENCE_ID, -1))
        assertEquals("generation", intent.getStringExtra(EXTRA_JOURNAL_GENERATION))
        assertNotEquals(intent.data, experienceIntent(context, 42, "replacement").data)
        assertNotEquals(intent.data, experienceIntent(context, 43, "generation").data)
    }
    @Test fun boundedRendererUsesEverySelectedPalette() {
        ThemeId.entries.filter { it != ThemeId.SYSTEM }.forEach { id ->
            val palette = ThemePalettes.get(id)
            val bitmap = renderTimelineBitmap(model(), 320, 60, palette, Density(1f), now.minusSeconds(1))
            assertEquals(320, bitmap.width); assertEquals(60, bitmap.height)
            assertEquals(palette.scheme.surface.toArgb(), bitmap.getPixel(319, 0))
            bitmap.recycle()
        }
        try { renderTimelineBitmap(model(), 100000, 100000, ThemePalettes.get(ThemeId.NORD), Density(1f), now); fail("oversized renderer accepted") } catch (_: IllegalArgumentException) { }
    }

    @Test fun upgradedJournalGetsStableNavigationGenerationAndRestoreInvalidatesIt() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = database.experienceDao()
            suspend fun addEntry() {
                dao.insert(Experience(id = 42, title = "fixture", text = "", sortDate = now, location = null))
                dao.insert(Ingestion(substanceName = "Caffeine", time = now, administrationRoute = AdministrationRoute.ORAL,
                    dose = 100.0, isDoseAnEstimate = false, estimatedDoseStandardDeviation = null, units = "mg",
                    experienceId = 42, notes = null, stomachFullness = null, consumerName = null, customUnitId = null))
            }
            addEntry()
            assertNull(database.photoAttachmentDao().generation())
            val active = ActiveExperienceRepository(database, SubstanceRepository(context, SubstanceParser()), Clock.fixed(now, ZoneOffset.UTC))
            val original = checkNotNull(active.current())
            assertEquals(UUID.fromString(original.generation).toString(), original.generation)
            assertEquals(original.generation, checkNotNull(active.current()).generation)
            assertEquals(original.generation, database.photoAttachmentDao().generation())
            val replacement = UUID.randomUUID().toString()
            dao.deleteEverythingAndInvalidate(replacement)
            assertNull(active.current())
            addEntry()
            assertEquals(replacement, checkNotNull(active.current()).generation)
            assertNotEquals(original.generation, checkNotNull(active.current()).generation)
        } finally {
            database.close()
        }
    }
}
