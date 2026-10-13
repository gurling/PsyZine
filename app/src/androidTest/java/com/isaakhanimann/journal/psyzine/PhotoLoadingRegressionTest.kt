// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.psyzine

import android.content.ContextWrapper
import android.graphics.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.isaakhanimann.journal.data.attachments.*
import com.isaakhanimann.journal.data.room.attachments.PhotoAttachment
import com.isaakhanimann.journal.ui.tabs.journal.experience.photos.*
import com.isaakhanimann.journal.ui.theme.PsyZineTheme
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class PhotoLoadingRegressionTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var directory: File
    private lateinit var context: android.content.Context
    private lateinit var storage: AttachmentStorage
    @Before fun setup() {
        val app=InstrumentationRegistry.getInstrumentation().targetContext
        directory=File(app.cacheDir,"photo-loading-${UUID.randomUUID()}").apply { mkdirs() }
        context=object : ContextWrapper(app) {
            override fun getFilesDir()=File(directory,"files").apply { mkdirs() }
            override fun getCacheDir()=File(directory,"cache").apply { mkdirs() }
        }
        storage=AttachmentStorage(context)
    }
    @After fun cleanup() { directory.deleteRecursively() }
    private fun source(): File {
        val bitmap=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888)
        val file=File(directory,"source.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle()
        return file
    }
    @Test fun concurrentThumbnailRequestsNeverDecodeMoreThanTwoAtOnce() = runBlocking {
        val active=AtomicInteger(); val maximum=AtomicInteger()
        val decoder=object : ThumbnailDecoder() {
            override fun decode(path: String,options: BitmapFactory.Options): Bitmap? {
                if (options.inJustDecodeBounds) return super.decode(path,options)
                val running=active.incrementAndGet(); maximum.updateAndGet { maxOf(it,running) }
                try { Thread.sleep(25); return super.decode(path,options) } finally { active.decrementAndGet() }
            }
        }
        val processor=ImageProcessor(context,storage,decoder); val file=source()
        coroutineScope { (0 until 20).map { async(Dispatchers.Default) { processor.thumbnail(UUID.randomUUID().toString(),file) } }.awaitAll() }
        assertTrue(maximum.get() in 1..2); assertEquals(0,active.get()); assertTrue(file.isFile)
    }
    @Test fun allocationFailureIsRecoverableAndOriginalIsUntouched() = runBlocking {
        val file=source(); val before=file.readBytes()
        val processor=ImageProcessor(context,storage,object : ThumbnailDecoder() {
            override fun decode(path: String,options: BitmapFactory.Options): Bitmap? {
                if (!options.inJustDecodeBounds) throw OutOfMemoryError("injected")
                return super.decode(path,options)
            }
        })
        val id=UUID.randomUUID().toString()
        try { processor.thumbnail(id,file); fail("Allocation failure must become a recoverable error") } catch (_: java.io.IOException) { }
        assertArrayEquals(before,file.readBytes()); assertFalse(File(storage.thumbnails,"$id.png").exists())
        assertTrue(ImageProcessor(context,storage).thumbnail(id,file).isFile)
    }
    @Test fun corruptCachedThumbnailCanBeRegeneratedWithoutChangingOriginal() = runBlocking<Unit> {
        val file=source(); val before=file.readBytes(); val id=UUID.randomUUID().toString()
        val processor=ImageProcessor(context,storage)
        val cached=processor.thumbnail(id,file); cached.writeText("corrupt cached preview")
        val regenerated=processor.thumbnail(id,file,regenerate=true)
        val decoded=BitmapFactory.decodeFile(regenerated.path)
        assertNotNull(decoded); decoded?.recycle(); assertArrayEquals(before,file.readBytes())
    }

    @Test fun fiveHundredPhotosComposeOnlyVisibleRowsAndKeepViewerIndices() {
        val source=source(); val requests=AtomicInteger(); var opened=-1
        val photos=(0 until 500).map { PhotoAttachment(UUID.randomUUID().toString(),42,"unused","image/png",64,64,source.length(),Instant.EPOCH,it,"a".repeat(64)) }
        val processor=ImageProcessor(context,storage)
        compose.setContent { PsyZineTheme { LazyColumn(Modifier.height(240.dp).width(340.dp).testTag("gallery")) {
            photoRows(photos,{ requests.incrementAndGet(); source },processor) { opened=it }
        } } }
        compose.waitUntil(10000) { requests.get() > 0 }
        assertTrue("Offscreen images must stay lazy",requests.get() < 30)
        compose.onNodeWithTag("gallery").performScrollToIndex(166)
        compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("Open photo 500 of 500").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Open photo 500 of 500").performClick()
        assertEquals(499,opened); assertTrue(requests.get() < 60)
    }
    @Test fun passiveEditorPreviewHasNoIneffectiveClickAction() {
        val source=source()
        compose.setContent { PsyZineTheme { PhotoTile(UUID.randomUUID().toString(),source,"Preview",ImageProcessor(context,storage)) } }
        compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("Preview").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Preview").assert(hasClickAction().not())
    }
}
