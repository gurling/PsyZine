// PsyZine, 2026-10-11. GPL-3.0-or-later.
package com.isaakhanimann.journal.psyzine

import android.content.ContextWrapper
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.isaakhanimann.journal.data.attachments.*
import com.isaakhanimann.journal.data.room.AppDatabase
import com.isaakhanimann.journal.data.room.attachments.JournalState
import com.isaakhanimann.journal.data.room.experiences.ExperienceRepository
import com.isaakhanimann.journal.data.room.experiences.entities.Experience
import com.isaakhanimann.journal.ui.tabs.journal.experience.edit.*
import com.isaakhanimann.journal.ui.theme.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ExperienceEditorUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun failedSaveKeepsEditorAndDraftAndSuccessfulRetryDismissesAfterCommit() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(app.cacheDir, "editor-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(app) {
            override fun getFilesDir() = File(directory, "files").apply { mkdirs() }
            override fun getCacheDir() = File(directory, "cache").apply { mkdirs() }
        }
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).build()
        try {
            val storage = AttachmentStorage(context); val lock = JournalMutationLock()
            val entries = ExperienceRepository(db.experienceDao(), db.photoAttachmentDao(), storage, lock, com.isaakhanimann.journal.data.room.JournalSessionGuard(db, lock))
            val attachments = AttachmentRepository(storage, AttachmentDraftStore(storage), ImageProcessor(context, storage), db, lock, context)
            runBlocking { db.experienceDao().insert(Experience(42, "original", "notes", sortDate = Instant.EPOCH, location = null)) }
            lateinit var vm: EditExperienceViewModel
            var closed = false
            compose.runOnIdle { vm = EditExperienceViewModel(entries, attachments, SavedStateHandle(mapOf("experienceId" to 42))) }
            compose.setContent { PsyZineTheme(ThemeId.MOCHA) { EditExperienceScreen({ closed = true }, vm) } }
            compose.waitUntil(10000) { !vm.loading }
            compose.onNodeWithText("Title").performTextReplacement("updated")
            runBlocking { db.photoAttachmentDao().setGeneration(JournalState(generation = "injected-other-generation")) }
            compose.onNodeWithText("Save").performClick()
            compose.waitUntil(10000) { vm.error != null && !vm.saving }
            compose.runOnIdle { assertFalse(closed); assertEquals("updated", vm.enteredTitle) }
            runBlocking { assertEquals("original", db.experienceDao().getExperience(42)!!.title) }
            compose.onNodeWithContentDescription("Close editor").performClick()
            compose.onNodeWithText("Discard changes?").assertExists()
            compose.onNodeWithText("Keep editing").performClick()
            runBlocking { db.photoAttachmentDao().setGeneration(JournalState(generation = vm.draft!!.generation)) }
            compose.onNodeWithText("Save").performClick()
            compose.waitUntil(10000) { closed }
            runBlocking { assertEquals("updated", db.experienceDao().getExperience(42)!!.title) }
        } finally { db.close(); directory.deleteRecursively() }
    }
    @Test fun recreatedFormFromPreviousGenerationCannotSaveIntoRestoredParent() {
        val app=InstrumentationRegistry.getInstrumentation().targetContext
        val directory=File(app.cacheDir,"stale-form-${java.util.UUID.randomUUID()}").apply { mkdirs() }
        val context=object : android.content.ContextWrapper(app) {
            override fun getFilesDir()=File(directory,"files").apply { mkdirs() }
            override fun getCacheDir()=File(directory,"cache").apply { mkdirs() }
        }
        val db=Room.inMemoryDatabaseBuilder(app,AppDatabase::class.java).build()
        try {
            val lock=JournalMutationLock(); val storage=AttachmentStorage(context)
            val guard=com.isaakhanimann.journal.data.room.JournalSessionGuard(db,lock)
            val entries=ExperienceRepository(db.experienceDao(),db.photoAttachmentDao(),storage,lock,guard)
            val attachments=AttachmentRepository(storage,AttachmentDraftStore(storage),ImageProcessor(context,storage),db,lock,context)
            runBlocking {
                db.experienceDao().insert(Experience(42,"restored","",sortDate=Instant.EPOCH,location=null))
                db.photoAttachmentDao().setGeneration(JournalState(generation="restored-generation"))
            }
            lateinit var vm: EditExperienceViewModel
            compose.runOnIdle { vm=EditExperienceViewModel(entries,attachments,SavedStateHandle(mapOf("experienceId" to 42,
                "journalGeneration" to "old-generation", "title" to "stale title", "text" to "stale notes"))) }
            compose.setContent { PsyZineTheme(ThemeId.MOCHA) { EditExperienceScreen({},vm) } }
            compose.waitUntil(10000) { !vm.loading }
            compose.runOnIdle { assertFalse(vm.canSave); assertNotNull(vm.error); assertNull(vm.draft) }
            runBlocking { assertEquals("restored",db.experienceDao().getExperience(42)!!.title) }
        } finally { db.close(); directory.deleteRecursively() }
    }

}
