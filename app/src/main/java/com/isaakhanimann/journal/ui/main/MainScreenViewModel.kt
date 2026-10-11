/*
 * Copyright (c) 2022. Isaak Hanimann.
 * This file is part of PsychonautWiki Journal.
 *
 * PsychonautWiki Journal is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * PsychonautWiki Journal is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with PsychonautWiki Journal.  If not, see https://www.gnu.org/licenses/gpl-3.0.en.html.
 */

package com.isaakhanimann.journal.ui.main

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.catch
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.CancellationException
import androidx.compose.runtime.*
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.room.withTransaction
import com.isaakhanimann.journal.data.room.AppDatabase
import javax.inject.Inject

val ARE_CONDITIONS_ACCEPTED = booleanPreferencesKey("are_conditions_accepted")

@HiltViewModel
class MainScreenViewModel @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val database: AppDatabase,
) : ViewModel() {

    val isAcceptedFlow: StateFlow<Boolean?> = dataStore.data
        .catch { if (it is java.io.IOException) emit(emptyPreferences()) else throw it }
        .map { preferences ->
            preferences[ARE_CONDITIONS_ACCEPTED] ?: false
        }.stateIn<Boolean?>(
            initialValue = null,
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000)
        )

    suspend fun canOpen(request: ExperienceNavigationRequest): Boolean = try {
        database.withTransaction {
            database.photoAttachmentDao().generation() == request.generation && database.experienceDao().getExperience(request.experienceId) != null
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        android.util.Log.w("ActiveExperience", "Navigation unavailable: ${e.javaClass.simpleName}")
        false
    }

    var acceptError by mutableStateOf<String?>(null)
        private set
    fun clearAcceptError() { acceptError = null }

    fun accept() {
        viewModelScope.launch {
            try { dataStore.edit { settings -> settings[ARE_CONDITIONS_ACCEPTED] = true }; acceptError = null }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { acceptError = "Your acceptance could not be saved. Please try again." }
        }
    }
}
