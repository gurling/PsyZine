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
