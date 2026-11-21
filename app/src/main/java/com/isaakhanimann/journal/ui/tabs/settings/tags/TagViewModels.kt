// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.ui.tabs.settings.tags

import androidx.compose.runtime.*
import androidx.lifecycle.*
import androidx.navigation.toRoute
import com.isaakhanimann.journal.data.definitions.*
import com.isaakhanimann.journal.data.room.JournalSessionGuard
import com.isaakhanimann.journal.data.room.AppDatabase
import com.isaakhanimann.journal.data.substances.repositories.SubstanceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Clock
import java.util.UUID
import javax.inject.Inject

@Serializable data class TagsRoute(val origin: String = "Drugs")
@Serializable data class TagEditRoute(val tagId: String? = null, val origin: String = "Drugs")
@Serializable data class SubjectTagsRoute(val subjectKey: String, val origin: String = "Drugs")
@Serializable data class TagSubjectsRoute(val tagId: String, val origin: String = "Drugs")

data class TagSubject(val key: String, val name: String, val kind: String)
private data class TagSubjectsLoaded(val generation: String, val tag: SubstanceTag, val memberships: List<SubstanceTagMembership>,
    val subjects: List<TagSubject>, val versions: Map<String, String?>, val base: String)

private fun String.tagDraftFingerprint(): String = java.security.MessageDigest.getInstance("SHA-256")
    .digest(toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

data class TagLibraryState(val loading: Boolean = true, val tags: List<SubstanceTag> = emptyList(),
    val memberships: List<SubstanceTagMembership> = emptyList(), val error: String? = null)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TagLibraryViewModel @Inject constructor(repo: TagRepository, guard: JournalSessionGuard) : ViewModel() {
    private val retry = MutableStateFlow(0)
    fun reload() { retry.value++ }
    val library = retry.flatMapLatest {
        combine(repo.tags, repo.memberships) { _, _ ->
            guard.readSession(null) {
                val (tags, memberships) = repo.snapshot()
                TagLibraryState(false, tags, memberships)
            }
        }.onStart { emit(TagLibraryState()) }.catch { emit(TagLibraryState(false, error = "Could not load tags. Try again.")) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TagLibraryState())
}

/** Common completion/error policy: drafts remain open; cancellation never becomes a save error. */
open class TagMutationViewModel : ViewModel() {
    var loading by mutableStateOf(true); protected set
    var ready by mutableStateOf(false); protected set
    var busy by mutableStateOf(false); private set
    var completed by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); protected set
    val canSave get() = ready && !busy && !completed
    fun dismissError() { error = null }
    protected fun mutate(done: () -> Unit, action: suspend () -> Unit) {
        if (!canSave) return
        busy = true; error = null
        viewModelScope.launch {
            try { action(); completed = true }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = tagError(e) }
            finally { busy = false }
            if (completed) done()
        }
    }
    protected fun tagError(e: Exception): String = when (e) {
        is android.database.sqlite.SQLiteFullException -> "Not enough storage to save tags. Free some space and try again. Your draft is still open."
        is IllegalArgumentException, is IllegalStateException -> e.message ?: "These tags changed. Close this screen and reopen it."
        else -> "Could not save tags. Your draft is still open. Try again."
    }
}

@HiltViewModel
class TagEditorViewModel @Inject constructor(private val repo: TagRepository, private val guard: JournalSessionGuard,
    private val clock: Clock, private val state: SavedStateHandle) : TagMutationViewModel() {
    private val route = state.toRoute<TagEditRoute>()
    val isNew get() = route.tagId == null
    var name by mutableStateOf(""); private set
    var color by mutableStateOf("BLUE"); private set
    var nameError by mutableStateOf<String?>(null); private set
    private var generation: String? = null
    private var original: SubstanceTag? = null
    private var allTags = emptyList<SubstanceTag>()
    private var id = ""
    private var created: Long? = null
    init { load() }
    fun load() {
        if (busy) return
        loading = true; ready = false; error = null
        viewModelScope.launch {
            try {
                val (token, tags) = guard.readSession(state.get<String>("tagGeneration")) { it to repo.snapshot().first }
                original = tags.find { it.id == route.tagId }
                check(isNew || original != null) { "This tag was deleted. Close this screen." }
                val base = Json.encodeToString(original)
                check(state.get<String>("tagBase")?.let { it == base } != false) { "This tag changed while your draft was open. Close this screen and reopen it." }
                generation = token; state["tagGeneration"] = token; state["tagBase"] = base
                allTags = tags
                id = original?.id ?: state.get<String>("newTagId") ?: UUID.randomUUID().toString()
                state["newTagId"] = id
                created = if (original != null) original?.creationTime else state.get<Long>("tagCreated") ?: clock.millis()
                state["tagCreated"] = created
                name = state.get<String>("tagName") ?: original?.name.orEmpty()
                color = state.get<String>("tagColor") ?: original?.color ?: "BLUE"
                ready = true
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = tagError(e) }
            finally { loading = false }
        }
    }
    fun name(value: String) { if (canSave) { name = value; state["tagName"] = value; if (nameError != null) validate() } }
    fun color(value: String) { if (canSave) { color = value; state["tagColor"] = value } }
    private fun validate(): Boolean {
        nameError = when {
            name.isBlank() -> "Enter a name for this tag."
            allTags.any { it.id != id && normalizedTagName(it.name) == normalizedTagName(name) } -> "A tag with this name already exists, ignoring accents and case. Choose another name."
            else -> null
        }
        return nameError == null
    }
    fun save(done: () -> Unit) {
        if (canSave && validate()) mutate(done) { repo.save(generation, SubstanceTag(id, name, color, created), original) }
    }
    fun delete(done: () -> Unit) {
        val value = original ?: return
        mutate(done) { repo.delete(generation, value) }
    }
}

@HiltViewModel
class SubjectTagsViewModel @Inject constructor(private val repo: TagRepository, private val guard: JournalSessionGuard,
    private val state: SavedStateHandle) : TagMutationViewModel() {
    private val route = state.toRoute<SubjectTagsRoute>()
    private val subject = SubjectId.parse(route.subjectKey)
    var tags by mutableStateOf<List<SubstanceTag>>(emptyList()); private set
    var selected by mutableStateOf<Set<String>>(emptySet()); private set
    var primary by mutableStateOf<String?>(null); private set
    var subjectName by mutableStateOf(""); private set
    private var original: TagAssignmentSnapshot? = null
    private var generation: String? = null
    /** Returning from tag management refreshes labels/new tags without overwriting membership edits. */
    fun refreshTags() {
        val base = original ?: return
        if (!canSave) return
        viewModelScope.launch {
            try {
                val snapshot = guard.readSession(generation) { repo.assignment(subject) }
                check(snapshot.memberships == base.memberships && snapshot.subjectVersion == base.subjectVersion) { "This subject's assignments changed. Close this screen and reopen Tags." }
                check(selected.all { id -> snapshot.tags.any { it.id == id } }) { "A selected tag was deleted. Close this screen and reopen Tags." }
                original = snapshot; tags = snapshot.tags; state["assignmentBase"] = Json.encodeToString(snapshot).tagDraftFingerprint()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = tagError(e) }
        }
    }
    init { load() }
    fun load() {
        if (busy) return
        loading = true; ready = false; error = null
        viewModelScope.launch {
            try {
                val (token, snapshot) = guard.readSession(state.get<String>("assignmentGeneration")) { it to repo.assignment(subject) }
                val base = Json.encodeToString(snapshot).tagDraftFingerprint()
                check(state.get<String>("assignmentBase")?.let { it == base } != false) { "This subject or its tags changed while your draft was open. Close this screen and reopen Tags." }
                state["assignmentGeneration"] = token; state["assignmentBase"] = base
                generation = token; original = snapshot; tags = snapshot.tags; subjectName = snapshot.subjectName
                selected = state.get<List<String>>("assignmentSelected")?.toSet() ?: snapshot.memberships.map { it.tagId }.toSet()
                primary = if (state.contains("assignmentSelected")) state["assignmentPrimary"] else snapshot.memberships.firstOrNull { it.isPrimary }?.tagId
                ready = true
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = tagError(e) }
            finally { loading = false }
        }
    }
    fun toggle(id: String) {
        if (!canSave || tags.none { it.id == id }) return
        selected = if (id in selected) selected - id else selected + id
        primary = primaryTagId(tags, selected, primary)
        persist()
    }
    fun makePrimary(id: String) {
        if (!canSave || id !in selected) return
        primary = id; persist()
    }
    private fun persist() { state["assignmentSelected"] = selected.sorted(); state["assignmentPrimary"] = primary }
    fun save(done: () -> Unit) {
        val base = original ?: return
        mutate(done) { repo.assign(generation, subject, selected, primary, base) }
    }
}

@HiltViewModel
class TagSubjectsViewModel @Inject constructor(private val repo: TagRepository, private val guard: JournalSessionGuard,
    private val db: AppDatabase, private val substances: SubstanceRepository, private val state: SavedStateHandle) : TagMutationViewModel() {
    private val route = state.toRoute<TagSubjectsRoute>()
    var tagName by mutableStateOf(""); private set
    var selected by mutableStateOf<Set<String>>(emptySet()); private set
    val query = state.getStateFlow("tagSubjectQuery", "")
    private val subjects = MutableStateFlow<List<TagSubject>>(emptyList())
    val filtered = combine(subjects, query) { values, text ->
        val tokens = normalizedTagName(text).split(Regex("[\\s,]+")).filter { it.isNotEmpty() }
        values.filter { value -> tokens.all { it in normalizedTagName(value.name) } }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private var generation: String? = null
    private var originalTag: SubstanceTag? = null
    private var memberships = emptyList<SubstanceTagMembership>()
    private var versions = emptyMap<String, String?>()
    init { load() }
    fun search(text: String) { state["tagSubjectQuery"] = text }
    fun toggle(key: String) {
        if (!canSave || subjects.value.none { it.key == key }) return
        selected = if (key in selected) selected - key else selected + key
        state["tagSubjectSelected"] = selected.sorted()
    }
    fun load() {
        if (busy) return
        loading = true; ready = false; error = null
        viewModelScope.launch {
            try {
                substances.initialize()
                val catalog = withContext(Dispatchers.Default) { substances.getAllSubstances().map { TagSubject(SubjectId.Catalog(it.name).key, it.name, "Catalog") } }
                val loaded = guard.readSession(state.get<String>("tagSubjectGeneration")) { token ->
                    val (tags, rows) = repo.snapshot()
                    val tag = tags.find { it.id == route.tagId } ?: error("This tag was deleted. Close this screen.")
                    val units = db.experienceDao().getAllCustomUnitsSorted()
                    val locals = db.experienceDao().getAllCustomSubstances()
                    val known = catalog + locals.map { TagSubject(SubjectId.Local(it.name).key, it.name, "Local substance") } +
                        units.map { TagSubject(SubjectId.Definition(it.id).key, it.name, if (it.isArchived) "Archived definition" else "Definition") }
                    val knownKeys = known.map { it.key }.toSet()
                    val unitsById = units.associateBy { it.id }
                    val localsByName = locals.associateBy { it.name }
                    val missing = rows.map { it.subjectId }.distinct().filter { it !in knownKeys }.map { key ->
                        val subject = SubjectId.parse(key)
                        TagSubject(key, when (subject) { is SubjectId.Catalog -> subject.name; is SubjectId.Local -> subject.name; is SubjectId.Definition -> "Missing definition ${subject.id}" }, "Missing metadata")
                    }
                    val all = (known + missing).distinctBy { it.key }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                    val versionMap = all.associate { it.key to when (val subject = SubjectId.parse(it.key)) {
                        is SubjectId.Definition -> unitsById[subject.id]?.toString()
                        is SubjectId.Local -> localsByName[subject.name]?.toString()
                        is SubjectId.Catalog -> null
                    } }
                    val base = (Json.encodeToString(tag) + Json.encodeToString(rows) + Json.encodeToString(versionMap)).tagDraftFingerprint()
                    check(state.get<String>("tagSubjectBase")?.let { it == base } != false) { "Subjects or tag assignments changed while your draft was open. Close this screen and reopen it." }
                    TagSubjectsLoaded(token, tag, rows, all, versionMap, base)
                }
                state["tagSubjectGeneration"] = loaded.generation; state["tagSubjectBase"] = loaded.base
                generation = loaded.generation; originalTag = loaded.tag; tagName = loaded.tag.name
                memberships = loaded.memberships; versions = loaded.versions; subjects.value = loaded.subjects
                selected = state.get<List<String>>("tagSubjectSelected")?.toSet() ?: loaded.memberships.filter { it.tagId == loaded.tag.id }.map { it.subjectId }.toSet()
                ready = true
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = tagError(e) }
            finally { loading = false }
        }
    }
    fun save(done: () -> Unit) {
        val tag = originalTag ?: return
        mutate(done) { repo.assignSubjects(generation, tag, selected, memberships, versions) }
    }
}
