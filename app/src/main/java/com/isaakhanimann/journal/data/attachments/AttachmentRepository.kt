// PsyZine, 2026-10-11. GPL-3.0-or-later.
package com.isaakhanimann.journal.data.attachments

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.room.withTransaction
import com.isaakhanimann.journal.data.room.AppDatabase
import com.isaakhanimann.journal.data.room.attachments.PhotoAttachment
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AttachmentRepository @Inject constructor(val storage: AttachmentStorage, val drafts: AttachmentDraftStore,
    val processor: ImageProcessor, private val db: AppDatabase, private val lock: JournalMutationLock,
    @ApplicationContext private val context: Context) {
    suspend fun generation(): String = db.withTransaction {
        db.photoAttachmentDao().generation() ?: UUID.randomUUID().toString().also {
            db.photoAttachmentDao().setGeneration(com.isaakhanimann.journal.data.room.attachments.JournalState(generation = it))
        }
    }
    fun observe(id: Int) = db.photoAttachmentDao().observe(id)
    suspend fun saved(id: Int) = db.photoAttachmentDao().forExperience(id)
    fun cameraUri(name: String): Uri {
        require(name.matches(Regex("[a-f0-9-]{36}\\.jpg")))
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(storage.camera, name))
    }
    fun newCameraFile(): String = "${UUID.randomUUID()}.jpg".also { check(File(storage.camera, it).createNewFile()) }
    fun revokeCamera(name: String) { context.revokeUriPermission(cameraUri(name), Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    fun persist(uri: Uri) = runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    fun release(uri: Uri) { runCatching { context.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
    fun file(photo: PhotoAttachment) = storage.file(photo.relativePath)
    fun file(draft: AttachmentDraft, photo: StagedPhoto) = File(storage.draftDir(draft.id), photo.fileName)
    suspend fun save(draft: AttachmentDraft) = lock.mutex.withLock {
        withContext(Dispatchers.IO) {
            val promoted = mutableListOf<File>()
            var committed = false
            try {
                val removed = db.photoAttachmentDao().forExperience(draft.experienceId).filter { it.id in draft.removedIds }
                val photos = draft.photos.map { photo ->
                    val source = file(draft, photo)
                    check(source.isFile && source.length() == photo.byteSize && storage.hash(source) == photo.sha256) { "A staged photo is missing or damaged" }
                    val relative = storage.relative(photo.id, photo.mimeType)
                    val target = storage.file(relative)
                    check(!target.exists()) { "Attachment ID already exists" }
                    promoted += target
                    source.inputStream().use { input -> java.io.FileOutputStream(target).use { output -> input.copyTo(output); output.fd.sync() } }
                    PhotoAttachment(photo.id, draft.experienceId, relative, photo.mimeType, photo.width, photo.height,
                        photo.byteSize, Instant.ofEpochSecond(photo.createdAt), 0, photo.sha256)
                }
                // Once the DB commit succeeds, cancellation must not remove its files.
                withContext(NonCancellable) {
                    db.withTransaction {
                        check(db.photoAttachmentDao().generation() == draft.generation) { "The journal was restored or deleted. Close this editor and reopen the entry." }
                        check(db.experienceDao().updateEditableFields(draft.experienceId, draft.title, draft.text,
                            draft.location.ifBlank { null }) == 1) { "Experience no longer exists" }
                        db.photoAttachmentDao().remove(draft.experienceId, draft.removedIds)
                        val next = (db.photoAttachmentDao().forExperience(draft.experienceId).maxOfOrNull { it.position } ?: -1) + 1
                        db.photoAttachmentDao().insert(photos.mapIndexed { index, photo -> photo.copy(position = next + index) })
                    }
                    committed = true
                    removed.forEach { runCatching { file(it).setLastModified(System.currentTimeMillis()) } }
                    // Files no longer referenced are collected after a grace period by reconciliation.
                    runCatching { drafts.discard(draft) }
                    draft.pendingSources.forEach { release(Uri.parse(it)) }
                    draft.failed.forEach { release(Uri.parse(it.source)) }
                }
            } finally {
                if (!committed) promoted.forEach { it.delete() }
            }
        }
    }
    suspend fun discard(draft: AttachmentDraft) = withContext(Dispatchers.IO) {
        draft.pendingSources.forEach { release(Uri.parse(it)) }
        draft.failed.forEach { release(Uri.parse(it.source)) }
        draft.pendingCamera?.let { revokeCamera(it) }
        drafts.discard(draft)
    }
}
