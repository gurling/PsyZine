// PsyZine, 2026-10-11. GPL-3.0-or-later.
package com.isaakhanimann.journal.psyzine

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.isaakhanimann.journal.data.preferences.*
import com.isaakhanimann.journal.data.backup.BackupPresentation
import com.isaakhanimann.journal.ui.tabs.settings.combinations.UserPreferences
import com.isaakhanimann.journal.ui.theme.ThemeId
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class PreferencesParityTest {
    private class Store(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
        private val state = MutableStateFlow(initial)
        override val data: Flow<Preferences> = state
        var fail = false
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            if (fail) throw IOException("disk unavailable")
            return transform(state.value).also { state.value = it }
        }
    }
    @Test fun defaultIndependentHeightsDoNotOverrideSavedFalse() = runTest {
        val store = Store(); val user = UserPreferences(store)
        assertTrue(user.areSubstanceHeightsIndependentFlow.first())
        user.saveAreSubstanceHeightsIndependent(false)
        assertFalse(UserPreferences(store).areSubstanceHeightsIndependentFlow.first())
    }
    @Test fun journalDisplayChoicesPersistAndAbsentBackupFieldsRetainCurrentValues() = runTest {
        val store = Store(); val user = UserPreferences(store)
        assertEquals(JournalDisplaySettings(), user.journalDisplaySettingsFlow.first())
        user.saveShowComponents(false); user.saveHideTimelineQuantities(true)
        val restored = UserPreferences(store)
        assertEquals(JournalDisplaySettings(false, true), restored.journalDisplaySettingsFlow.first())
        restored.restoreJournalDisplaySettings(null, null)
        assertEquals(JournalDisplaySettings(false, true), restored.journalDisplaySettingsFlow.first())
        restored.restoreJournalDisplaySettings(true, null)
        assertEquals(JournalDisplaySettings(true, true), restored.journalDisplaySettingsFlow.first())
        assertTrue(restored.areSubstanceHeightsIndependentFlow.first())
    }
    @Test fun failedJournalDisplayWriteIsAtomicAndKeepsBothChoices() = runTest {
        val store = Store(); val user = UserPreferences(store)
        user.restoreJournalDisplaySettings(false, true); store.fail = true
        try { user.restoreJournalDisplaySettings(true, false); fail("Write unexpectedly succeeded") } catch (_: IOException) { }
        assertEquals(JournalDisplaySettings(false, true), user.journalDisplaySettingsFlow.first())
    }
    @Test fun statisticsCustomPeriodRetainsItsDatesAcrossReentryAndFailedWrites() = runTest {
        val store = Store(); val user = UserPreferences(store)
        val start = java.time.LocalDate.of(2026, 10, 10); val end = java.time.LocalDate.of(2026, 11, 14)
        val metric = com.isaakhanimann.journal.data.definitions.StatisticsMetric.DOSAGE
        val period = com.isaakhanimann.journal.data.definitions.StatisticsPeriod.CUSTOM
        assertEquals(com.isaakhanimann.journal.data.definitions.StatisticsSelection(), user.advancedStatisticsSelectionFlow.first())
        user.saveAdvancedStatisticsSelection(metric, period, start, end)
        val expected = com.isaakhanimann.journal.data.definitions.StatisticsSelection(metric, period, start, end)
        assertEquals(expected, UserPreferences(store).advancedStatisticsSelectionFlow.first())
        store.fail = true
        try { user.saveAdvancedStatisticsSelection(customStart = start.plusDays(1), customEnd = end); fail("Write unexpectedly succeeded") } catch (_: IOException) { }
        assertEquals(expected, user.advancedStatisticsSelectionFlow.first())
    }
    @Test fun everyThemePersistsAndUnknownSelectionRecovers() = runTest {
        val store = Store(); val appearance = AppearanceRepository(store)
        ThemeId.entries.forEach { theme ->
            appearance.select(theme)
            assertEquals(theme, AppearanceRepository(store).theme.first())
        }
        store.edit { it[stringPreferencesKey("appearance_theme")] = "obsolete" }
        assertEquals(ThemeId.SYSTEM, appearance.theme.first())
    }
    @Test fun writeFailurePropagatesAndKeepsPreviousSelection() = runTest {
        val store = Store(); val appearance = AppearanceRepository(store)
        appearance.select(ThemeId.DRACULA); store.fail = true
        try { appearance.select(ThemeId.NORD); fail("write should fail") } catch (_: IOException) { }
        assertEquals(ThemeId.DRACULA, appearance.theme.first())
    }
    @Test fun presentationRestoreNeverGrantsConsentOrNotificationPermission() = runTest {
        val store = Store(); val repository = PresentationPreferences(store)
        assertFalse(repository.settings.first().lockScreenDetails)
        repository.restore(BackupPresentation(true, true, false))
        val restored = PresentationPreferences(store).settings.first()
        assertTrue(restored.lockScreenDetails); assertFalse(restored.widgetDetails)
        assertFalse(restored.accepted); assertFalse(restored.permissionRequested)
        repository.markPermissionRequested()
        assertTrue(repository.settings.first().permissionRequested)
    }
}
