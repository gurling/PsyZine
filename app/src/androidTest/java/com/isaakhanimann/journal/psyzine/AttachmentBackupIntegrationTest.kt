// PsyZine, 2026-10-11. GPL-3.0-or-later.
package com.isaakhanimann.journal.psyzine

import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.exifinterface.media.ExifInterface
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.isaakhanimann.journal.data.attachments.*
import com.isaakhanimann.journal.data.backup.*
import com.isaakhanimann.journal.data.preferences.*
import com.isaakhanimann.journal.data.room.AppDatabase
import com.isaakhanimann.journal.data.room.attachments.*
import com.isaakhanimann.journal.data.room.experiences.ExperienceRepository
import com.isaakhanimann.journal.data.room.experiences.entities.Experience
import com.isaakhanimann.journal.ui.theme.ThemeId
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.util.UUID
import java.util.zip.*

@RunWith(AndroidJUnit4::class)
class AttachmentBackupIntegrationTest {
    private lateinit var directory: File
    private lateinit var storage: AttachmentStorage
    private lateinit var processor: ImageProcessor
    private lateinit var database: AppDatabase
    private lateinit var backup: JournalBackupRepository
    private lateinit var appearance: AppearanceRepository
    private lateinit var presentation: PresentationPreferences
    private lateinit var attachments: AttachmentRepository
    private lateinit var scope: CoroutineScope
    @Before fun setup() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        directory = File(app.cacheDir, "parity-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(app) {
            override fun getFilesDir() = File(directory, "files").apply { mkdirs() }
            override fun getCacheDir() = File(directory, "cache").apply { mkdirs() }
        }
        database = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).build()
        storage = AttachmentStorage(context); processor = ImageProcessor(context, storage)
        val lock = JournalMutationLock()
        val entries = ExperienceRepository(database.experienceDao(), database.photoAttachmentDao(), storage, lock)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = PreferenceDataStoreFactory.create(scope = scope) { File(directory, "preferences.preferences_pb") }
        appearance = AppearanceRepository(store); presentation = PresentationPreferences(store)
        backup = JournalBackupRepository(database, entries, storage, appearance, presentation, lock,
            com.isaakhanimann.journal.data.substances.repositories.SubstanceRepository(context, com.isaakhanimann.journal.data.substances.parse.SubstanceParser()), context)
        attachments = AttachmentRepository(storage, AttachmentDraftStore(storage), processor, database, lock, context)
    }
    @After fun cleanup() { scope.cancel(); database.close(); directory.deleteRecursively() }
    private fun source(alpha: Boolean = false, width: Int = 100, height: Int = 60): File {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(if (alpha) Color.TRANSPARENT else Color.RED)
        val file = File(directory, "source-${UUID.randomUUID()}.${if (alpha) "png" else "jpg"}")
        FileOutputStream(file).use { assertTrue(bitmap.compress(if (alpha) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 100, it)) }
        bitmap.recycle(); return file
    }
    private suspend fun seededPhoto(): PhotoAttachment {
        database.experienceDao().insert(Experience(42, "kept", "private notes", sortDate = Instant.EPOCH, location = null))
        val image = processor.normalize(source(), directory)
        val path = storage.relative(image.id, image.mimeType)
        File(directory, image.fileName).copyTo(storage.file(path))
        val row = PhotoAttachment(image.id, 42, path, image.mimeType, image.width, image.height, image.byteSize, Instant.EPOCH, 0, image.sha256)
        database.photoAttachmentDao().insert(listOf(row)); return row
    }
    @Test fun processingNormalizesEveryExifOrientationAndStripsLocation() = runBlocking {
        for (orientation in 1..8) {
            val original = source()
            val pattern = Bitmap.createBitmap(100, 60, Bitmap.Config.ARGB_8888)
            for (y in 0 until 60) for (x in 0 until 100) pattern.setPixel(x, y,
                if (y < 30) { if (x < 50) Color.RED else Color.GREEN } else { if (x < 50) Color.BLUE else Color.YELLOW })
            original.outputStream().use { pattern.compress(Bitmap.CompressFormat.JPEG, 100, it) }; pattern.recycle()
            ExifInterface(original).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
                setLatLong(50.0, 14.0); saveAttributes()
            }
            val result = processor.normalize(original, directory)
            val swapped = orientation in 5..8
            assertEquals(if (swapped) 60 else 100, result.width)
            assertEquals(if (swapped) 100 else 60, result.height)
            val pixels = BitmapFactory.decodeFile(File(directory, result.fileName).path)
            val expected = listOf(Color.RED, Color.GREEN, Color.YELLOW, Color.BLUE, Color.RED, Color.BLUE, Color.YELLOW, Color.GREEN)[orientation - 1]
            val actual = pixels.getPixel(pixels.width / 4, pixels.height / 4)
            assertTrue("orientation $orientation red", kotlin.math.abs(Color.red(expected) - Color.red(actual)) < 40)
            assertTrue("orientation $orientation green", kotlin.math.abs(Color.green(expected) - Color.green(actual)) < 40)
            assertTrue("orientation $orientation blue", kotlin.math.abs(Color.blue(expected) - Color.blue(actual)) < 40)
            pixels.recycle()
            val exif = ExifInterface(File(directory, result.fileName))
            assertNull(exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE))
            assertNull(exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE))
            assertNull(exif.getAttribute(ExifInterface.TAG_XMP))
            val savedOrientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)
            assertTrue(savedOrientation == ExifInterface.ORIENTATION_UNDEFINED ||
                savedOrientation == ExifInterface.ORIENTATION_NORMAL)
        }
    }
    @Test fun transparencyResizeAndMissingThumbnailRecovery() = runBlocking {
        val transparent = processor.normalize(source(alpha = true), directory)
        assertEquals("image/png", transparent.mimeType)
        BitmapFactory.decodeFile(File(directory, transparent.fileName).path).let { pixels ->
            assertEquals(0, Color.alpha(pixels.getPixel(0, 0))); pixels.recycle()
        }
        val resized = processor.normalize(source(width = 3000, height = 1500), directory)
        assertEquals(2560, resized.width); assertEquals(1280, resized.height)
        val thumb = processor.thumbnail(resized.id, File(directory, resized.fileName))
        assertTrue(thumb.delete())
        assertTrue(processor.thumbnail(resized.id, File(directory, resized.fileName)).isFile)
    }
    @Test fun corruptOversizedCancelledAndFailedWritesDoNotCreateStoredPhotos() = runBlocking {
        val corrupt = File(directory, "corrupt").apply { writeText("not an image") }
        try { processor.normalize(corrupt, directory); fail("corrupt image accepted") } catch (_: IllegalArgumentException) { }
        val oversized = File(directory, "oversized").apply { java.io.RandomAccessFile(this, "rw").use { it.setLength(MAX_IMAGE_BYTES + 1) } }
        try { processor.normalize(oversized, directory); fail("oversized image accepted") } catch (_: IllegalArgumentException) { }
        val blocked = File(directory, "not-a-directory").apply { writeText("block output") }
        try { processor.normalize(source(), blocked); fail("write failure expected") } catch (_: java.io.IOException) { }
        val cancelled = launch { processor.normalize(source(), directory) }; cancelled.cancelAndJoin()
        assertTrue(database.photoAttachmentDao().all().isEmpty())
        assertTrue(storage.root.listFiles().orEmpty().isEmpty())
    }
    @Test fun photoInclusiveRoundTripRestoresAssociationsAndPreferences() = runBlocking {
        val original = seededPhoto()
        appearance.select(ThemeId.NORD); presentation.widgetDetails(false)
        val archive = File(directory, "roundtrip.psyzine")
        backup.export(Uri.fromFile(archive))
        appearance.select(ThemeId.DRACULA); presentation.widgetDetails(true)
        assertNull(backup.restore(Uri.fromFile(archive)))
        val restored = database.photoAttachmentDao().all().single()
        assertNotEquals(original.id, restored.id)
        assertEquals(1, restored.experienceId)
        assertEquals("private notes", database.experienceDao().getExperience(1)!!.text)
        assertEquals(original.sha256, storage.hash(storage.file(restored.relativePath)))
        assertEquals(ThemeId.NORD, appearance.theme.first()); assertFalse(presentation.settings.first().widgetDetails)
        assertFalse(presentation.settings.first().lockScreenDetails)
    }
    @Test fun unsafeMissingExtraAndDamagedArchiveKeepOriginalJournal() = runBlocking {
        val original = seededPhoto(); val valid = File(directory, "valid.psyzine")
        backup.export(Uri.fromFile(valid))
        val entries = ZipFile(valid).use { zip -> zip.entries().asSequence().associate { it.name to zip.getInputStream(it).use { stream -> stream.readBytes() } } }
        val imagePath = entries.keys.single { it != "manifest.json" }
        val variants = listOf(entries - imagePath, entries + ("../escape" to byteArrayOf(1)),
            entries + (imagePath to byteArrayOf(1, 2)), entries + ("images/${UUID.randomUUID()}.jpg" to byteArrayOf(1)))
        variants.forEachIndexed { index, content ->
            val archive = File(directory, "invalid-$index.psyzine")
            ZipOutputStream(archive.outputStream()).use { zip -> content.forEach { (name, data) -> zip.putNextEntry(ZipEntry(name)); zip.write(data); zip.closeEntry() } }
            try { backup.restore(Uri.fromFile(archive)); fail("invalid restore accepted") } catch (_: Exception) { }
            assertEquals("private notes", database.experienceDao().getExperience(42)!!.text)
            assertEquals(original.id, database.photoAttachmentDao().all().single().id)
            assertEquals(original.sha256, storage.hash(storage.file(original.relativePath)))
        }
    }
    @Test fun reconciliationKeepsReferencedPhotosAndActiveDraftsAndCollectsOldOrphans() = runBlocking {
        val saved = seededPhoto()
        val draft = AttachmentDraft(experienceId = 42, generation = attachments.generation(), title = "draft", text = "body", location = "")
        attachments.drafts.save(draft)
        val activeDirectory = storage.draftDir(draft.id)
        val orphan = File(storage.root, "${UUID.randomUUID()}.jpg").apply { writeText("orphan"); setLastModified(0) }
        val orphanThumbnail = File(storage.thumbnails, "${UUID.randomUUID()}.png").apply { writeText("orphan"); setLastModified(0) }
        val abandoned = storage.draftDir(UUID.randomUUID().toString()).apply { setLastModified(0) }
        storage.file(saved.relativePath).setLastModified(0)
        activeDirectory.setLastModified(0)
        AttachmentReconciler(storage, attachments.drafts, database, JournalMutationLock()).reconcile()
        assertTrue(storage.file(saved.relativePath).isFile); assertTrue(activeDirectory.isDirectory)
        assertFalse(orphan.exists()); assertFalse(orphanThumbnail.exists()); assertFalse(abandoned.exists())
        assertNotNull(attachments.drafts.load(42))
    }
    @Test fun stagedRemovalDiscardAndStaleSavePreserveSavedPhotos() = runBlocking {
        val original = seededPhoto(); val generation = attachments.generation()
        val staged = processor.normalize(source(alpha = true), directory)
        val draft = AttachmentDraft(experienceId = 42, generation = generation, title = "edited", text = "new notes", location = "",
            removedIds = listOf(original.id), photos = listOf(StagedPhoto(staged.id, staged.mimeType, staged.width, staged.height, staged.byteSize, staged.sha256, staged.fileName, 0)))
        File(directory, staged.fileName).copyTo(File(storage.draftDir(draft.id), staged.fileName))
        attachments.drafts.save(draft); attachments.discard(draft)
        assertEquals(original.id, database.photoAttachmentDao().all().single().id)
        assertEquals("private notes", database.experienceDao().getExperience(42)!!.text)
        val stale = draft.copy(id = UUID.randomUUID().toString(), photos = emptyList(), generation = "previous-generation")
        try { attachments.save(stale); fail("stale draft accepted") } catch (_: IllegalStateException) { }
        assertEquals(original.id, database.photoAttachmentDao().all().single().id)
    }
}
