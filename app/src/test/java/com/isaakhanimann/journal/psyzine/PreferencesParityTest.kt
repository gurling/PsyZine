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
    @Test fun relativeDatesPersistAndPartialRestoresAndFailedWritesKeepThePreviousChoice() = runTest {
        val store = Store(); val prefs = UserPreferences(store)
        assertFalse(prefs.journalDisplaySettingsFlow.first().relativeDates)
        prefs.saveRelativeDates(true)
        assertTrue(UserPreferences(store).journalDisplaySettingsFlow.first().relativeDates)
        prefs.restoreJournalDisplaySettings(false, true)
        assertTrue(prefs.journalDisplaySettingsFlow.first().relativeDates)
        store.fail = true
        try { prefs.saveRelativeDates(false); fail("Write unexpectedly succeeded") } catch (_: IOException) { }
        assertTrue(prefs.journalDisplaySettingsFlow.first().relativeDates)
        store.fail = false
        prefs.restoreJournalDisplaySettings(null, null, relativeDates = false)
        assertEquals(JournalDisplaySettings(false, true), prefs.journalDisplaySettingsFlow.first())
    }
    @Test fun manualNotificationSelectionSurvivesReentryWithoutEnablingAutomaticNotificationsOrChangingWidgetPrivacy() = runTest {
        val store = Store(); val prefs = PresentationPreferences(store)
        prefs.notificationEnabled(false); prefs.widgetDetails(false)
        val target = ManualNotificationTarget(42, "journal-one")
        prefs.selectManualNotification(target)
        val restored = PresentationPreferences(store).settings.first()
        assertEquals(target, restored.manualTarget)
        assertFalse(restored.enabled); assertFalse(restored.widgetDetails)
        store.fail = true
        try { prefs.selectManualNotification(ManualNotificationTarget(99, "journal-two")); fail("Write unexpectedly succeeded") } catch (_: IOException) { }
        assertEquals(target, prefs.settings.first().manualTarget)
        try { prefs.notificationEnabled(false); fail("Stop unexpectedly succeeded") } catch (_: IOException) { }
        assertEquals(target, prefs.settings.first().manualTarget)
        store.fail = false
        prefs.notificationEnabled(false)
        assertNull(prefs.settings.first().manualTarget)
    }
    @Test fun expiryOfAnOldSelectionCannotClearANewerJournalOrExperience() = runTest {
        val store = Store(); val prefs = PresentationPreferences(store)
        val old = ManualNotificationTarget(42, "old")
        val replacement = ManualNotificationTarget(42, "new")
        prefs.selectManualNotification(old); prefs.selectManualNotification(replacement)
        prefs.clearManualNotification(old)
        assertEquals(replacement, prefs.settings.first().manualTarget)
        prefs.clearManualNotification(ManualNotificationTarget(99, "new"))
        assertEquals(replacement, prefs.settings.first().manualTarget)
        prefs.clearManualNotification(replacement)
        assertNull(prefs.settings.first().manualTarget)
        assertTrue(prefs.settings.first().enabled)
    }
    @Test fun explicitAutomaticModeAndPortableRestoreClearLocalNotificationTargets() = runTest {
        val prefs = PresentationPreferences(Store()); val target = ManualNotificationTarget(42, "local-journal")
        prefs.selectManualNotification(target); prefs.notificationEnabled(true)
        assertNull(prefs.settings.first().manualTarget)
        prefs.selectManualNotification(target); prefs.restore(BackupPresentation(false, false, true))
        assertNull(prefs.settings.first().manualTarget)
        assertFalse(prefs.settings.first().enabled)
    }
    @Test fun notificationTimeResetIsAtomicAndDoesNotChangePresentationPreferences() = runTest {
        val store = Store(); val prefs = UserPreferences(store)
        val time = java.time.Instant.ofEpochSecond(123)
        prefs.saveIngestionTimes(time, time)
        store.fail = true
        try { prefs.saveIngestionTimes(null, null); fail("Write unexpectedly succeeded") } catch (_: IOException) { }
        assertEquals(time, prefs.lastIngestionTimeOfExperienceFlow.first())
        store.fail = false
        prefs.saveIngestionTimes(null, null)
        assertNull(prefs.lastIngestionTimeOfExperienceFlow.first())
        assertTrue(PresentationPreferences(store).settings.first().enabled)
    }
    @Test fun stoppingAutomaticNotificationPersistsWithoutChangingWidgetPrivacyAndFailuresRetainTheChoice() = runTest {
        val store = Store(); val prefs = PresentationPreferences(store)
        prefs.widgetDetails(false)
        prefs.notificationEnabled(false)
        val stopped = PresentationPreferences(store).settings.first()
        assertFalse(stopped.enabled)
        assertFalse(stopped.widgetDetails)
        store.fail = true
        try { prefs.notificationEnabled(true); fail("Write unexpectedly succeeded") } catch (_: IOException) { }
        assertFalse(prefs.settings.first().enabled)
    }
    @Test fun sectionVisibilityChoicesPersistAndPartialRestoreKeepsAbsentFields() = runTest {
        val store = Store(); val user = UserPreferences(store)
        assertFalse(user.journalDisplaySettingsFlow.first().hideInteractions)
        assertFalse(user.journalDisplaySettingsFlow.first().hideTolerance)
        user.saveHideInteractions(true); user.saveHideTolerance(true)
        val restored = UserPreferences(store)
        assertTrue(restored.journalDisplaySettingsFlow.first().hideInteractions)
        assertTrue(restored.journalDisplaySettingsFlow.first().hideTolerance)
        restored.restoreJournalDisplaySettings(false, null)
        assertEquals(JournalDisplaySettings(false, false, true, true), restored.journalDisplaySettingsFlow.first())
        restored.restoreJournalDisplaySettings(null, null, hideInteractions = false)
        assertEquals(JournalDisplaySettings(false, false, false, true), restored.journalDisplaySettingsFlow.first())
        store.fail = true
        try { restored.restoreJournalDisplaySettings(null, null, true, false); fail("Write unexpectedly succeeded") } catch (_: IOException) { }
        assertEquals(JournalDisplaySettings(false, false, false, true), restored.journalDisplaySettingsFlow.first())
    }
    @Test fun inlineTolerancePeriodIsIndependentAndRetainedOnFailure() = runTest {
        val store = Store(); val user = UserPreferences(store)
        val month = com.isaakhanimann.journal.data.definitions.TolerancePeriod.PAST_1M
        val week = com.isaakhanimann.journal.data.definitions.TolerancePeriod.PAST_1W
        assertEquals(month, user.toleranceExperiencePeriodFlow.first())
        assertEquals(com.isaakhanimann.journal.data.definitions.TolerancePeriod.PAST_2M, user.toleranceStatsPeriodFlow.first())
        user.saveToleranceExperiencePeriod(week)
        assertEquals(week, UserPreferences(store).toleranceExperiencePeriodFlow.first())
        assertEquals(com.isaakhanimann.journal.data.definitions.TolerancePeriod.PAST_2M, user.toleranceStatsPeriodFlow.first())
        store.fail = true
        try { user.saveToleranceExperiencePeriod(month); fail("Write unexpectedly succeeded") } catch (_: IOException) { }
        assertEquals(week, user.toleranceExperiencePeriodFlow.first())
    }
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
