// PsyZine modifications, 2026. GPL-3.0-or-later; see COPYING.
package com.isaakhanimann.journal.data.room

import androidx.room.withTransaction
import com.isaakhanimann.journal.data.attachments.JournalMutationLock
import com.isaakhanimann.journal.data.room.attachments.JournalState
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Restored IDs may refer to different entries or units. Never write through an old editor. */
@Singleton
class JournalSessionGuard @Inject constructor(
    private val database: AppDatabase,
    private val mutationLock: JournalMutationLock,
) {
    suspend fun currentGeneration(): String = database.withTransaction {
        database.photoAttachmentDao().generation() ?: UUID.randomUUID().toString().also {
            database.photoAttachmentDao().setGeneration(JournalState(generation = it))
        }
    }

    /** Capture token and read model in the same lock/transaction, including SavedState recovery. */
    suspend fun <T> readSession(recoveredGeneration: String?, action: suspend (String) -> T): T =
        mutationLock.mutex.withLock {
            database.withTransaction {
                val generation = currentGeneration()
                if (recoveredGeneration != null && recoveredGeneration != generation) throw StaleJournalSession()
                action(generation)
            }
        }

    suspend fun <T> withSession(generation: String?, parentId: Int? = null, action: suspend () -> T): T =
        mutationLock.mutex.withLock {
            database.withTransaction {
                if (generation == null || database.photoAttachmentDao().generation() != generation) throw StaleJournalSession()
                if (parentId != null) {
                    check(database.experienceDao().getExperience(parentId) != null) { "This experience was deleted. Close this screen." }
                }
                action()
            }
        }
}
