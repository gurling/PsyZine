/*
 * Copyright (c) 2022-2023. Isaak Hanimann.
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

package com.isaakhanimann.journal.ui.tabs.settings

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.isaakhanimann.journal.data.room.experiences.ExperienceRepository
import com.isaakhanimann.journal.ui.tabs.settings.combinations.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject


@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val experienceRepository: ExperienceRepository,
    private val backup: com.isaakhanimann.journal.data.backup.JournalBackupRepository,
    private val userPreferences: UserPreferences,
    private val presentation: com.isaakhanimann.journal.data.preferences.PresentationPreferences,
) : ViewModel() {

    fun saveDosageDotsAreHidden(value: Boolean) = viewModelScope.launch {
        userPreferences.saveDosageDotsAreHidden(value)
    }

    fun saveAreSubstanceHeightsIndependent(value: Boolean) = viewModelScope.launch {
        userPreferences.saveAreSubstanceHeightsIndependent(value)
    }

    fun saveIsTimelineHidden(value: Boolean) = viewModelScope.launch {
        userPreferences.saveIsTimelineHidden(value)
    }

    val isTimelineHiddenFlow = userPreferences.isTimelineHiddenFlow.stateIn(
        initialValue = false,
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000)
    )

    val areSubstanceHeightsIndependentFlow = userPreferences.areSubstanceHeightsIndependentFlow.stateIn(
        initialValue = true,
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000)
    )

    val areDosageDotsHiddenFlow = userPreferences.areDosageDotsHiddenFlow.stateIn(
        initialValue = false,
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000)
    )

    val presentationSettings = presentation.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.isaakhanimann.journal.data.preferences.PresentationSettings())
    private fun savePresentation(block: suspend () -> Unit) = viewModelScope.launch {
        try { block() }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { snackbarHostState.showSnackbar("Preference could not be saved") }
    }
    fun saveNotificationEnabled(value: Boolean) = savePresentation { presentation.notificationEnabled(value) }
    fun saveLockScreenDetails(value: Boolean) = savePresentation { presentation.lockScreenDetails(value) }
    fun saveWidgetDetails(value: Boolean) = savePresentation { presentation.widgetDetails(value) }

    val snackbarHostState = SnackbarHostState()

    var importError by androidx.compose.runtime.mutableStateOf<String?>(null)
        private set
    fun clearImportError() { importError = null }

    var busy by androidx.compose.runtime.mutableStateOf(false)
        private set

    private fun operation(importing: Boolean = false, block: suspend () -> String) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            if (importing) importError = null
            val message = try { block() }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) {
                if (importing) {
                    importError = e.message ?: "Restore failed. Existing journal preserved."
                    if (com.isaakhanimann.journal.BuildConfig.DEBUG) android.util.Log.d("JournalImport", "Import rejected: " + ((e as? com.isaakhanimann.journal.data.backup.importers.JournalImportException)?.code ?: e.javaClass.simpleName))
                }
                e.message ?: "Operation failed; existing journal preserved."
            }
            finally { busy = false }
            snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Long)
        }
    }
    fun importFile(uri: Uri) = operation(importing = true) { backup.restore(uri) ?: "Journal restored" }
    fun exportFile(uri: Uri) = operation { backup.export(uri); "Backup saved" }
    fun exportLegacyFile(uri: Uri) = operation { backup.export(uri, legacy = true); "Entries-only JSON saved" }
    fun deleteEverything() = operation { experienceRepository.deleteEverything(); "Journal deleted" }
}
