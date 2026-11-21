// PsyZine, 2026-10-11. GPL-3.0-or-later.
package com.isaakhanimann.journal.ui.tabs.journal.experience.photos

import androidx.lifecycle.*
import com.isaakhanimann.journal.data.attachments.AttachmentRepository
import com.isaakhanimann.journal.data.room.experiences.JournalReadSnapshot
import com.isaakhanimann.journal.data.room.experiences.JournalRecordRepository
import com.isaakhanimann.journal.ui.main.navigation.graphs.PhotoViewerRoute
import com.isaakhanimann.journal.ui.utils.journalReadSession
import com.isaakhanimann.journal.ui.utils.journalRecordId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

@HiltViewModel
class PhotoViewerViewModel @Inject constructor(
    val attachments: AttachmentRepository,
    records: JournalRecordRepository,
    state: SavedStateHandle,
) : ViewModel() {
    val route = PhotoViewerRoute(state.journalRecordId("experienceId"), state.get<Int>("index") ?: 0)
    val viewerState = journalReadSession(viewModelScope, state) { generation ->
        records.observePhotos(route.experienceId, generation).map { snapshot ->
            JournalReadSnapshot(snapshot.generation, snapshot.value.map { photo ->
                runCatching { attachments.file(photo) }.getOrElse {
                    java.io.File(attachments.storage.root, "unavailable-${photo.id}")
                }
            })
        }.flowOn(kotlinx.coroutines.Dispatchers.IO)
    }
}
