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

data class PresentationSettings(val enabled: Boolean = true, val lockScreenDetails: Boolean = false, val widgetDetails: Boolean = true,
    val permissionRequested: Boolean = false, val accepted: Boolean = false)

@Singleton
class PresentationPreferences @Inject constructor(private val store: DataStore<Preferences>) {
    private val enabled = booleanPreferencesKey("active_notification_enabled")
    private val lockScreen = booleanPreferencesKey("active_notification_lock_screen_details")
    private val widget = booleanPreferencesKey("active_widget_details")
    private val requested = booleanPreferencesKey("notification_permission_requested")
    val settings = store.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.map {
        PresentationSettings(it[enabled] ?: true, it[lockScreen] ?: false, it[widget] ?: true, it[requested] ?: false, it[ARE_CONDITIONS_ACCEPTED] ?: false)
    }
    suspend fun notificationEnabled(value: Boolean) { store.edit { it[enabled] = value } }
    suspend fun lockScreenDetails(value: Boolean) { store.edit { it[lockScreen] = value } }
    suspend fun widgetDetails(value: Boolean) { store.edit { it[widget] = value } }
    suspend fun restore(value: com.isaakhanimann.journal.data.backup.BackupPresentation) {
        store.edit { it[enabled] = value.enabled; it[lockScreen] = value.lockScreenDetails; it[widget] = value.widgetDetails }
    }
    suspend fun markPermissionRequested() { store.edit { it[requested] = true } }
}
