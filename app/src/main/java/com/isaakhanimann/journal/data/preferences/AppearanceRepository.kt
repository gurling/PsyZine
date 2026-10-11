// PsyZine, 2026-10-11. GPL-3.0-or-later.
package com.isaakhanimann.journal.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.isaakhanimann.journal.ui.theme.ThemeId
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppearanceRepository @Inject constructor(private val store: DataStore<Preferences>) {
    private val key = stringPreferencesKey("appearance_theme")
    val theme = store.data.map { ThemeId.fromKey(it[key]) }.catch {
        if (it is IOException) emit(ThemeId.SYSTEM) else throw it
    }
    suspend fun select(theme: ThemeId) { store.edit { it[key] = theme.key } }
}
