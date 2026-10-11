// PsyZine, 2026-10-11. GPL-3.0-or-later.
package com.isaakhanimann.journal.data.backup

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.room.withTransaction
import com.isaakhanimann.journal.data.attachments.*
import com.isaakhanimann.journal.data.preferences.AppearanceRepository
import com.isaakhanimann.journal.data.room.AppDatabase
import com.isaakhanimann.journal.data.room.attachments.PhotoAttachment
import com.isaakhanimann.journal.data.room.experiences.ExperienceRepository
import com.isaakhanimann.journal.ui.tabs.settings.*
import com.isaakhanimann.journal.ui.theme.ThemeId
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.io.*
import java.time.Instant
import java.util.UUID
import java.util.zip.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JournalBackupRepository @Inject constructor(private val db: AppDatabase, private val entries: ExperienceRepository,
    private val storage: AttachmentStorage, private val appearance: AppearanceRepository,
    private val presentation: com.isaakhanimann.journal.data.preferences.PresentationPreferences, private val lock: JournalMutationLock,
    private val substances: com.isaakhanimann.journal.data.substances.repositories.SubstanceRepository,
    @ApplicationContext private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private suspend fun readJournal(): JournalExport {
            val experiencesWithIngestionsAndRatings =
                entries.getAllExperiencesWithIngestionsTimedNotesAndRatingsSorted()
            val experiencesSerializable = experiencesWithIngestionsAndRatings.map {
                val location = it.experience.location
                return@map ExperienceSerializable(
                    title = it.experience.title,
                    text = it.experience.text,
                    creationDate = it.experience.creationDate,
                    sortDate = it.experience.sortDate,
                    isFavorite = it.experience.isFavorite,
                    ingestions = it.ingestions.map { ingestion ->
                        IngestionSerializable(
                            substanceName = ingestion.substanceName,
                            time = ingestion.time,
                            endTime = ingestion.endTime,
                            creationDate = ingestion.creationDate,
                            administrationRoute = ingestion.administrationRoute,
                            dose = ingestion.dose,
                            estimatedDoseStandardDeviation = ingestion.estimatedDoseStandardDeviation,
                            isDoseAnEstimate = ingestion.isDoseAnEstimate,
                            units = ingestion.units,
                            notes = ingestion.notes,
                            stomachFullness = ingestion.stomachFullness,
                            consumerName = ingestion.consumerName,
                            customUnitId = ingestion.customUnitId
                        )
                    },
                    location = if (location != null) {
                        LocationSerializable(
                            name = location.name,
                            latitude = location.latitude,
                            longitude = location.longitude
                        )
                    } else {
                        null
                    },
                    ratings = it.ratings.map { rating ->
                        RatingSerializable(
                            option = rating.option,
                            time = rating.time,
                            creationDate = rating.creationDate
                        )
                    },
                    timedNotes = it.timedNotes.map { timedNote ->
                        TimedNoteSerializable(
                            creationDate = timedNote.creationDate,
                            time = timedNote.time,
                            note = timedNote.note,
                            color = timedNote.color,
                            isPartOfTimeline = timedNote.isPartOfTimeline
                        )
                    }
                )
            }
            val customUnitsSerializable = entries.getAllCustomUnitsSorted().map {
                CustomUnitSerializable(
                    id = it.id,
                    substanceName = it.substanceName,
                    name = it.name,
                    creationDate = it.creationDate,
                    administrationRoute = it.administrationRoute,
                    dose = it.dose,
                    estimatedDoseStandardDeviation = it.estimatedDoseStandardDeviation,
                    isEstimate = it.isEstimate,
                    isArchived = it.isArchived,
                    unit = it.unit,
                    unitPlural = it.unitPlural,
                    originalUnit = it.originalUnit,
                    note = it.note
                )
            }
            return JournalExport(
                experiences = experiencesSerializable,
                substanceCompanions = entries.getAllSubstanceCompanions(),
                customSubstances = entries.getAllCustomSubstances(),
                customUnits = customUnitsSerializable
            )
    }
    suspend fun export(uri: Uri, legacy: Boolean = false) = lock.mutex.withLock { withContext(Dispatchers.IO) {
        try {
        val snapshot = db.withTransaction {
            Triple(readJournal(), entries.getAllExperiencesWithIngestionsTimedNotesAndRatingsSorted().map { it.experience.id }, db.photoAttachmentDao().all())
        }
        val journal = snapshot.first
        context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
            if (legacy) output.write(json.encodeToString(journal).toByteArray())
            else {
                val indices = snapshot.second.withIndex().associate { it.value to it.index }
                val photos = snapshot.third.map { photo ->
                    val file = storage.file(photo.relativePath)
                    check(file.length() == photo.byteSize && storage.hash(file) == photo.sha256) { "Photo missing or damaged. Backup was not completed." }
                    BackupPhoto(photo.id, indices[photo.experienceId] ?: error("Photo has no experience"),
                        "images/${file.name}", photo.mimeType, photo.width, photo.height, photo.byteSize,
                        photo.createdAt.epochSecond, photo.position, photo.sha256)
                }
                val settings = presentation.settings.first()
                val manifest = BackupManifest(journal = journal, photos = photos, theme = appearance.theme.first().key,
                    presentation = BackupPresentation(settings.enabled, settings.lockScreenDetails, settings.widgetDetails))
                BackupValidation.manifest(manifest)
                ZipOutputStream(BufferedOutputStream(output)).use { zip ->
                    zip.putNextEntry(ZipEntry("manifest.json")); zip.write(json.encodeToString(manifest).toByteArray()); zip.closeEntry()
                    snapshot.third.forEach { photo ->
                        currentCoroutineContext().ensureActive()
                        zip.putNextEntry(ZipEntry("images/${storage.file(photo.relativePath).name}"))
                        storage.file(photo.relativePath).inputStream().use { input -> BackupArchive.copyLimited(input, zip, MAX_IMAGE_BYTES) }
                        zip.closeEntry()
                    }
                }
            }
        } ?: error("Backup destination is unavailable")
        } catch (_: OutOfMemoryError) { throw IOException("Not enough memory to write this backup. Backup was not completed.") }
    } }
    /** Returns a preference warning only when the journal restore itself succeeded. */
    suspend fun restore(uri: Uri): String? = lock.mutex.withLock { withContext(Dispatchers.IO) {
        val directory = File(storage.staging, "restore-${UUID.randomUUID()}").apply { check(mkdirs()) }
        val promoted = mutableListOf<File>(); var committed = false
        val importWarnings = mutableListOf<String>()
        try {
            val input = context.contentResolver.openInputStream(uri)?.buffered() ?: error("Backup is unavailable")
            val manifest = input.use { buffered ->
                buffered.mark(4); val prefix = ByteArray(4); var n = 0
                while (n < prefix.size) {
                    val read = buffered.read(prefix, n, prefix.size - n)
                    if (read < 0) break
                    n += read
                }
                buffered.reset()
                if (n == 4 && prefix[0] == 0x50.toByte() && prefix[1] == 0x4b.toByte()) extract(buffered, directory)
                else {
                    val text = ByteArrayOutputStream().also { BackupArchive.copyLimited(buffered, it, BackupArchive.MAX_MANIFEST_BYTES) }.toString("UTF-8")
                    val imported = com.isaakhanimann.journal.data.backup.importers.JournalImportAdapter.decode(text, substances.getAllSubstances().map { it.name }.toSet())
                    importWarnings += imported.warnings
                    BackupManifest(journal = imported.journal, theme = appearance.theme.first().key)
                }
            }
            BackupValidation.manifest(manifest)
            manifest.photos.forEach { photo ->
                currentCoroutineContext().ensureActive()
                val source = File(directory, photo.path)
                require(source.isFile && source.length() == photo.byteSize && storage.hash(source) == photo.sha256) { "Photo is missing or checksum is invalid" }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(source.path, bounds)
                require(bounds.outWidth == photo.width && bounds.outHeight == photo.height && bounds.outMimeType == photo.mimeType) { "Image dimensions or format do not match manifest" }
                val pixels = BitmapFactory.decodeFile(source.path) ?: error("Backup contains an unreadable photo")
                pixels.recycle()
                val exif = ExifInterface(source)
                require(exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE) == null && exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE) == null &&
                    exif.getAttribute(ExifInterface.TAG_XMP) == null && exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) in listOf(0, 1)) { "Backup contains unnormalised image metadata" }
            }
            // Promote to fresh IDs. Never overwrite a file referenced by the current journal or an active edit.
            val photos = manifest.photos.map { photo ->
                val id = UUID.randomUUID().toString(); val relative = storage.relative(id, photo.mimeType)
                val target = storage.file(relative); promoted += target
                File(directory, photo.path).inputStream().use { input -> FileOutputStream(target).use { output -> input.copyTo(output); output.fd.sync() } }
                PhotoAttachment(id, photo.experienceIndex + 1, relative, photo.mimeType, photo.width, photo.height,
                    photo.byteSize, Instant.ofEpochSecond(photo.createdAt), photo.position, photo.sha256)
            }
            currentCoroutineContext().ensureActive()
            val old = db.photoAttachmentDao().all()
            withContext(NonCancellable) {
                db.withTransaction {
                    db.experienceDao().replaceEverything(manifest.journal)
                    db.photoAttachmentDao().setGeneration(com.isaakhanimann.journal.data.room.attachments.JournalState(generation = UUID.randomUUID().toString()))
                    db.photoAttachmentDao().insert(photos)
                }
                committed = true
                old.forEach { runCatching { storage.file(it.relativePath).setLastModified(System.currentTimeMillis()) } }
                // Existing edit manifests must not later update a restored entry with a reused integer ID.
                val drafts = AttachmentDraftStore(storage)
                runCatching { drafts.active().forEach { draft ->
                    (draft.pendingSources + draft.failed.map { it.source }).forEach { source ->
                        runCatching { context.contentResolver.releasePersistableUriPermission(Uri.parse(source), android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                    }
                    draft.pendingCamera?.let { name -> runCatching {
                        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(storage.camera, name))
                        context.revokeUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    } }
                    drafts.discard(draft)
                } }
                try { appearance.select(ThemeId.fromKey(manifest.theme)); manifest.presentation?.let { presentation.restore(it) }; importWarnings.distinct().joinToString("\n").takeIf { it.isNotEmpty() } }
                catch (_: Exception) { "Journal restored. Appearance or presentation preferences could not be applied; choose them again in Settings." }
            }
        } catch (_: OutOfMemoryError) {
            if (committed) "Journal restored. Preferences could not be applied; choose them again in Settings."
            else throw IOException("Not enough memory to read this backup. The existing journal was preserved.")
        } finally {
            if (!committed) promoted.forEach { it.delete() }
            directory.deleteRecursively()
        }
    } }
    private suspend fun extract(input: InputStream, directory: File): BackupManifest {
        val paths = mutableSetOf<String>(); var total = 0L
        ZipInputStream(input).use { zip ->
            while (true) {
                currentCoroutineContext().ensureActive()
                val entry = zip.nextEntry ?: break
                require(!entry.isDirectory) { "Unexpected archive directory" }
                BackupArchive.validateEntry(entry.name)
                require(paths.add(entry.name) && paths.size <= BackupArchive.MAX_ENTRIES) { "Duplicate or too many archive entries" }
                val destination = File(directory, entry.name)
                require(destination.canonicalPath.startsWith(directory.canonicalPath + File.separator)) { "Unsafe archive path" }
                destination.parentFile?.mkdirs()
                val remaining = BackupArchive.MAX_ARCHIVE_BYTES - total
                val limit = minOf(remaining, if (entry.name == "manifest.json") BackupArchive.MAX_MANIFEST_BYTES else MAX_IMAGE_BYTES)
                val coroutine = currentCoroutineContext()
                FileOutputStream(destination).use { total += BackupArchive.copyLimited(zip, it, limit) { coroutine.ensureActive() } }
                zip.closeEntry()
            }
        }
        require("manifest.json" in paths) { "Backup manifest missing" }
        val manifest = json.decodeFromString<BackupManifest>(File(directory, "manifest.json").readText())
        BackupValidation.manifest(manifest)
        require(paths == manifest.photos.map { it.path }.toSet() + "manifest.json") { "Backup photos do not match manifest" }
        return manifest
    }
}
