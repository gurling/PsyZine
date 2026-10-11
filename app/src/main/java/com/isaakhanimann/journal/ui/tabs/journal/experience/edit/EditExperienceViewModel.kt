// PsyZine, 2026-10-11. GPL-3.0-or-later.
package com.isaakhanimann.journal.ui.tabs.journal.experience.edit

import android.net.Uri
import androidx.compose.runtime.*
import androidx.lifecycle.*
import androidx.navigation.toRoute
import com.isaakhanimann.journal.data.attachments.*
import com.isaakhanimann.journal.data.room.attachments.PhotoAttachment
import com.isaakhanimann.journal.data.room.experiences.ExperienceRepository
import com.isaakhanimann.journal.ui.main.navigation.graphs.EditExperienceRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import java.io.File
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class EditExperienceViewModel @Inject constructor(private val entries: ExperienceRepository,
    val attachments: AttachmentRepository, private val state: SavedStateHandle) : ViewModel() {
    private val route = state.toRoute<EditExperienceRoute>()
    val experienceId = route.experienceId
    var draft by mutableStateOf<AttachmentDraft?>(null); private set
    var savedPhotos by mutableStateOf<List<PhotoAttachment>>(emptyList()); private set
    var loading by mutableStateOf(true); private set
    var saving by mutableStateOf(false); private set
    var importing by mutableStateOf(false); private set
    var progress by mutableStateOf(""); private set
    var error by mutableStateOf<String?>(null); private set
    private var originalTitle = ""; private var originalText = ""; private var originalLocation = ""
    private val ready = CompletableDeferred<Unit>()
    private var acquisition: Job? = null
    private var persistence: Job? = null
    private var discarding = false
    var enteredTitle by mutableStateOf(state.get<String>("title") ?: ""); private set
    var enteredText by mutableStateOf(state.get<String>("text") ?: ""); private set
    var enteredLocation by mutableStateOf(state.get<String>("location") ?: ""); private set
    val dirty get() = enteredTitle != originalTitle || enteredText != originalText || enteredLocation != originalLocation ||
        draft?.let { it.photos.isNotEmpty() || it.removedIds.isNotEmpty() || it.pendingCamera != null || it.pendingSources.isNotEmpty() || it.failed.isNotEmpty() } == true
    val canSave get() = !loading && !saving && !discarding && !importing && draft?.pendingCamera == null && enteredTitle.isNotBlank()

    init {
        viewModelScope.launch {
            try {
                val entry = entries.getExperience(experienceId) ?: error("Experience no longer exists")
                originalTitle = entry.title; originalText = entry.text; originalLocation = entry.location?.name ?: ""
                savedPhotos = attachments.saved(experienceId)
                val generation = attachments.generation()
                val restored = withContext(Dispatchers.IO) { attachments.drafts.load(experienceId) }
                if (restored != null && restored.generation != generation) attachments.discard(restored)
                draft = restored?.takeIf { it.generation == generation } ?: AttachmentDraft(experienceId = experienceId, generation = generation, title = entry.title, text = entry.text, location = originalLocation)
                enteredTitle = state.get<String>("title") ?: draft!!.title
                enteredText = state.get<String>("text") ?: draft!!.text
                enteredLocation = state.get<String>("location") ?: draft!!.location
                persistNow()
                ready.complete(Unit)
                loading = false
                if (draft!!.pendingSources.isNotEmpty()) processPending()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "Could not load experience"; loading = false; ready.completeExceptionally(e) }
        }
    }
    private fun snapshot() = requireNotNull(draft).copy(title = enteredTitle, text = enteredText, location = enteredLocation)
    private suspend fun persistNow() {
        val pending = persistence; persistence = null; pending?.cancelAndJoin()
        val value = snapshot(); draft = value
        withContext(Dispatchers.IO) { attachments.drafts.save(value) }
    }
    fun editTitle(value: String) { enteredTitle = value; state["title"] = value; schedulePersist() }
    fun editText(value: String) { enteredText = value; state["text"] = value; schedulePersist() }
    fun editLocation(value: String) { enteredLocation = value; state["location"] = value; schedulePersist() }
    private fun schedulePersist() {
        val pending = persistence
        pending?.cancel()
        persistence = viewModelScope.launch {
            pending?.join()
            delay(250)
            try { val value = snapshot(); draft = value; withContext(Dispatchers.IO) { attachments.drafts.save(value) } }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { error = "Draft could not be saved. Keep this screen open and try again." }
        }
    }
    fun removeSaved(id: String) {
        draft = draft?.let { it.copy(removedIds = (it.removedIds + id).distinct()) }; schedulePersist()
    }
    fun undoRemove(id: String) { draft = draft?.let { it.copy(removedIds = it.removedIds - id) }; schedulePersist() }
    fun removeStaged(id: String) {
        val current = draft ?: return
        // Defer deleting its bytes; a manifest write failure must not damage the recoverable draft.
        draft = current.copy(photos = current.photos.filterNot { it.id == id }); schedulePersist()
    }
    fun acquire(uris: List<Uri>) {
        if (importing || saving || loading) return
        if (uris.size > MAX_BATCH_IMAGES) { error = "Choose at most 20 images at a time."; return }
        if (uris.isEmpty()) return
        importing = true
        acquisition = viewModelScope.launch {
            try {
                uris.forEach { attachments.persist(it) }
                draft = snapshot().copy(pendingSources = uris.map { it.toString() })
                persistNow(); processPending()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "Could not acquire photos" }
            finally { importing = false }
        }
    }
    private suspend fun processPending() {
        acquisition = currentCoroutineContext()[Job]
        importing = true; error = null
        val total = draft!!.pendingSources.size
        try {
            while (draft!!.pendingSources.isNotEmpty()) {
                val source = draft!!.pendingSources.first(); val uri = Uri.parse(source)
                progress = "Importing photo ${total - draft!!.pendingSources.size + 1} of $total"
                try {
                    val image = attachments.processor.process(uri, attachments.storage.draftDir(draft!!.id))
                    val staged = StagedPhoto(image.id, image.mimeType, image.width, image.height, image.byteSize,
                        image.sha256, image.fileName, Instant.now().epochSecond)
                    draft = snapshot().copy(photos = draft!!.photos + staged, pendingSources = draft!!.pendingSources.drop(1))
                    persistNow()
                    attachments.release(uri)
                    if (uri.authority == "${com.isaakhanimann.journal.BuildConfig.APPLICATION_ID}.fileprovider") {
                        uri.lastPathSegment?.let { File(attachments.storage.camera, it).delete() }
                    }
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    draft = snapshot().copy(failed = draft!!.failed + FailedPhoto(source, e.message ?: "Image import failed"), pendingSources = draft!!.pendingSources.drop(1))
                    persistNow()
                }
            }
        } finally { importing = false; progress = "" }
    }
    fun retry(failed: FailedPhoto) {
        draft = draft?.let { it.copy(failed = it.failed - failed) }; acquire(listOf(Uri.parse(failed.source)))
    }
    fun removeFailure(failed: FailedPhoto) {
        draft = draft?.let { it.copy(failed = it.failed - failed) }
        attachments.release(Uri.parse(failed.source)); schedulePersist()
    }
    fun prepareCamera(launch: (Uri) -> Unit) {
        if (!canSave) return
        viewModelScope.launch {
            try {
                val name = withContext(Dispatchers.IO) { attachments.newCameraFile() }
                draft = snapshot().copy(pendingCamera = name); persistNow()
                launch(attachments.cameraUri(name))
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = "No camera app is available, or capture could not start."; cancelCamera() }
        }
    }
    fun cameraResult(success: Boolean) = viewModelScope.launch {
        try {
            ready.await()
            val name = draft?.pendingCamera ?: return@launch
            attachments.revokeCamera(name)
            if (success && File(attachments.storage.camera, name).length() > 0) {
                val uri = attachments.cameraUri(name)
                draft = snapshot().copy(pendingCamera = null, pendingSources = listOf(uri.toString()))
                persistNow(); processPending()
            } else cancelCamera()
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message ?: "Camera photo could not be imported" }
    }
    fun cancelCamera() {
        val name = draft?.pendingCamera ?: return
        attachments.revokeCamera(name); File(attachments.storage.camera, name).delete()
        draft = draft?.copy(pendingCamera = null); schedulePersist()
    }
    fun recoverCamera() {
        val name = draft?.pendingCamera ?: return
        if (File(attachments.storage.camera, name).length() > 0) cameraResult(true) else cancelCamera()
    }
    fun save(onSuccess: () -> Unit) {
        if (!canSave) return
        viewModelScope.launch {
            saving = true; error = null
            try { persistNow(); attachments.save(snapshot()); onSuccess() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "Save failed. Your journal and draft have been preserved." }
            finally { saving = false }
        }
    }
    fun discard(onSuccess: () -> Unit) {
        if (saving || discarding) return
        discarding = true
        viewModelScope.launch {
            try {
                acquisition?.cancelAndJoin(); persistence?.cancelAndJoin()
                draft?.let { attachments.discard(it) }; onSuccess()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { error = "Draft could not be discarded. Try again." }
            finally { discarding = false }
        }
    }
}
