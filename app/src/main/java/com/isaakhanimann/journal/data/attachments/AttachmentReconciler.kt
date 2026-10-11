// PsyZine, 2026-10-11. GPL-3.0-or-later.
package com.isaakhanimann.journal.data.attachments

import com.isaakhanimann.journal.data.room.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AttachmentReconciler @Inject constructor(private val storage: AttachmentStorage,
    private val drafts: AttachmentDraftStore, private val db: AppDatabase, private val lock: JournalMutationLock) {
    suspend fun reconcile() = lock.mutex.withLock { withContext(Dispatchers.IO) {
        // A corrupt manifest stops collection: never guess which draft files are safe to delete.
        val active = drafts.active()
        val references = db.photoAttachmentDao().all().map { storage.file(it.relativePath).canonicalPath }.toSet()
        val before = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        storage.root.listFiles().orEmpty().filter { it.canonicalPath !in references && it.lastModified() < before }.forEach { it.delete() }
        val thumbnailIds = db.photoAttachmentDao().all().map { it.id }.toSet() + active.flatMap { draft -> draft.photos.map { it.id } }
        storage.thumbnails.listFiles().orEmpty().filter { it.name.removeSuffix(".png") !in thumbnailIds && it.lastModified() < before }.forEach { it.delete() }
        val ids = active.map { it.id }.toSet()
        storage.staging.listFiles().orEmpty().filter { it.isDirectory && it.name != "camera" && it.name !in ids && it.lastModified() < before }.forEach { it.deleteRecursively() }
        val cameraNames = active.flatMap { draft -> listOfNotNull(draft.pendingCamera) + (draft.pendingSources + draft.failed.map { it.source }).mapNotNull { android.net.Uri.parse(it).lastPathSegment } }.toSet()
        storage.camera.listFiles().orEmpty().filter { it.name !in cameraNames && it.lastModified() < before }.forEach { it.delete() }
        // Clean orphan source/intermediate files within active drafts after an interrupted decoder.
        active.forEach { draft ->
            val keep = draft.photos.map { it.fileName }.toSet()
            storage.draftDir(draft.id).listFiles().orEmpty().filter { it.name !in keep && it.lastModified() < before }.forEach { it.delete() }
        }
    } }
}
