// PsyZine, 2026-10-11. GPL-3.0-or-later.
package com.isaakhanimann.journal.ui.tabs.journal.experience.photos

import androidx.lifecycle.*
import androidx.navigation.toRoute
import com.isaakhanimann.journal.data.attachments.AttachmentRepository
import com.isaakhanimann.journal.ui.main.navigation.graphs.PhotoViewerRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class PhotoViewerViewModel @Inject constructor(val attachments: AttachmentRepository, state: SavedStateHandle) : ViewModel() {
    val route = state.toRoute<PhotoViewerRoute>()
    val photos = attachments.observe(route.experienceId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
