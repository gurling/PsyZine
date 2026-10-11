// PsyZine, 2026-10-11. GPL-3.0-or-later.
package com.isaakhanimann.journal.data.attachments

import android.util.AtomicFile
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class StagedPhoto(val id: String, val mimeType: String, val width: Int, val height: Int,
    val byteSize: Long, val sha256: String, val fileName: String, val createdAt: Long)
@Serializable
data class FailedPhoto(val source: String, val message: String)
@Serializable
data class AttachmentDraft(val id: String = UUID.randomUUID().toString(), val experienceId: Int, val generation: String,
    val title: String, val text: String, val location: String,
    val photos: List<StagedPhoto> = emptyList(), val removedIds: List<String> = emptyList(),
    val failed: List<FailedPhoto> = emptyList(), val pendingCamera: String? = null,
    val pendingSources: List<String> = emptyList())

@Singleton
class AttachmentDraftStore @Inject constructor(private val storage: AttachmentStorage) {
    private val json = Json { ignoreUnknownKeys = true }
    @Synchronized fun load(experienceId: Int): AttachmentDraft? {
        val file = File(storage.staging, "edit-$experienceId.json")
        if (!file.exists()) return null
        return json.decodeFromString<AttachmentDraft>(AtomicFile(file).openRead().use { it.readBytes().toString(Charsets.UTF_8) })
            .also { validate(it); require(it.experienceId == experienceId); storage.draftDir(it.id) }
    }
    @Synchronized fun save(draft: AttachmentDraft) {
        validate(draft)
        val file = AtomicFile(File(storage.staging, "edit-${draft.experienceId}.json"))
        val stream = file.startWrite()
        try { stream.write(json.encodeToString(draft).toByteArray()); file.finishWrite(stream) }
        catch (e: Throwable) { file.failWrite(stream); throw e }
    }
    @Synchronized fun discard(draft: AttachmentDraft) {
        validate(draft)
        AtomicFile(File(storage.staging, "edit-${draft.experienceId}.json")).delete()
        storage.draftDir(draft.id).deleteRecursively()
        draft.pendingCamera?.let { File(storage.camera, it).delete() }
    }
    fun active(): List<AttachmentDraft> = storage.staging.listFiles().orEmpty()
        .filter { it.name.matches(Regex("edit-[0-9]+\\.json")) }
        .map { json.decodeFromString<AttachmentDraft>(AtomicFile(it).openRead().use { stream -> stream.readBytes().toString(Charsets.UTF_8) }).also(::validate) }

    private fun validate(draft: AttachmentDraft) {
        fun uuid(value: String) = runCatching { UUID.fromString(value).toString() == value }.getOrDefault(false)
        require(draft.experienceId > 0 && uuid(draft.id)) { "Invalid edit draft" }
        require(draft.pendingCamera == null || draft.pendingCamera.endsWith(".jpg") && uuid(draft.pendingCamera.removeSuffix(".jpg"))) { "Invalid camera staging path" }
        require(draft.removedIds.all(::uuid)) { "Invalid saved photo reference" }
        require(draft.photos.map { it.id }.distinct().size == draft.photos.size) { "Duplicate staged photo IDs" }
        draft.photos.forEach { photo ->
            require(uuid(photo.id) && photo.mimeType in listOf("image/jpeg", "image/png") &&
                photo.fileName == "${photo.id}.${if (photo.mimeType == "image/png") "png" else "jpg"}") { "Invalid staged photo path" }
            require(photo.width in 1..2560 && photo.height in 1..2560 && photo.byteSize in 1..MAX_IMAGE_BYTES &&
                photo.sha256.matches(Regex("[a-f0-9]{64}"))) { "Invalid staged photo metadata" }
        }
    }
}
