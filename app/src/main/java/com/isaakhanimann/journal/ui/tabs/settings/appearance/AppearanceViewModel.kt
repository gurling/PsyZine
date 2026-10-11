// PsyZine, 2026-10-11. GPL-3.0-or-later.
package com.isaakhanimann.journal.ui.tabs.settings.appearance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.isaakhanimann.journal.data.preferences.AppearanceRepository
import com.isaakhanimann.journal.ui.theme.ThemeId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppearanceViewModel @Inject constructor(private val repository: AppearanceRepository) : ViewModel() {
    val theme = repository.theme.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeId.SYSTEM)
    var error by mutableStateOf<String?>(null); private set
    var saving by mutableStateOf(false); private set
    fun select(id: ThemeId) {
        if (saving) return
        viewModelScope.launch {
            saving = true; error = null
            try { repository.select(id) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { error = "Appearance could not be saved. Try again." }
            finally { saving = false }
        }
    }
}
