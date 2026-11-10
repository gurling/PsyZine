// PsyZine, 2026-10-11. GPL-3.0-or-later.
// PsyLog 0c29a70f feature port, copyright 2025–2026 PsyLog contributors.
package com.isaakhanimann.journal.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.isaakhanimann.journal.ui.main.ARE_CONDITIONS_ACCEPTED
import kotlinx.coroutines.flow.*
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

data class ManualNotificationTarget(val experienceId: Int, val generation: String)
data class PresentationSettings(val enabled: Boolean = true, val lockScreenDetails: Boolean = false, val widgetDetails: Boolean = true,
    val permissionRequested: Boolean = false, val accepted: Boolean = false,
    val manualTarget: ManualNotificationTarget? = null)

@Singleton
class PresentationPreferences @Inject constructor(private val store: DataStore<Preferences>) {
    private val enabled = booleanPreferencesKey("active_notification_enabled")
    private val lockScreen = booleanPreferencesKey("active_notification_lock_screen_details")
    private val widget = booleanPreferencesKey("active_widget_details")
    private val requested = booleanPreferencesKey("notification_permission_requested")
    private val manualId = intPreferencesKey("manual_notification_experience_id")
    private val manualGeneration = stringPreferencesKey("manual_notification_journal_generation")
    val settings = store.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.map {
        val target = it[manualId]?.let { id -> it[manualGeneration]?.takeIf(String::isNotBlank)?.let { token -> ManualNotificationTarget(id, token) } }
        PresentationSettings(it[enabled] ?: true, it[lockScreen] ?: false, it[widget] ?: true, it[requested] ?: false, it[ARE_CONDITIONS_ACCEPTED] ?: false, target)
    }
    // A Settings toggle explicitly returns to automatic mode; Stop also clears a manual selection.
    suspend fun notificationEnabled(value: Boolean) { store.edit {
        it[enabled] = value; it.remove(manualId); it.remove(manualGeneration)
    } }
    suspend fun selectManualNotification(target: ManualNotificationTarget) {
        require(target.generation.isNotBlank()) { "This journal session is unavailable. Reopen the experience." }
        store.edit { it[manualId] = target.experienceId; it[manualGeneration] = target.generation }
    }
    suspend fun clearManualNotification(expected: ManualNotificationTarget) { store.edit {
        if (it[manualId] == expected.experienceId && it[manualGeneration] == expected.generation) {
            it.remove(manualId); it.remove(manualGeneration)
        }
    } }
    suspend fun lockScreenDetails(value: Boolean) { store.edit { it[lockScreen] = value } }
    suspend fun widgetDetails(value: Boolean) { store.edit { it[widget] = value } }
    suspend fun restore(value: com.isaakhanimann.journal.data.backup.BackupPresentation) {
        store.edit {
            it[enabled] = value.enabled; it[lockScreen] = value.lockScreenDetails; it[widget] = value.widgetDetails
            it.remove(manualId); it.remove(manualGeneration)
        }
    }
    suspend fun markPermissionRequested() { store.edit { it[requested] = true } }
}
