// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.ui.tabs.settings.definitions

import androidx.compose.runtime.*
import androidx.lifecycle.*
import androidx.navigation.toRoute
import android.net.Uri
import com.isaakhanimann.journal.data.definitions.*
import com.isaakhanimann.journal.data.room.JournalSessionGuard
import com.isaakhanimann.journal.data.room.StaleJournalSession
import com.isaakhanimann.journal.data.room.experiences.ExperienceRepository
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import com.isaakhanimann.journal.data.substances.repositories.SubstanceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Clock
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

@Serializable object DefinitionLibraryRoute
@Serializable data class DefinitionEditRoute(val definitionId: Int = 0, val generation: String? = null, val origin: String = "Settings")

data class DefinitionLibraryState(val loading: Boolean = true, val records: List<DefinitionWithContent> = emptyList(),
    val generation: String? = null, val error: String? = null)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DefinitionLibraryViewModel @Inject constructor(repo: DefinitionRepository, guard: JournalSessionGuard,
    private val exchange: DefinitionLibraryExchange,
    private val state: SavedStateHandle) : ViewModel() {
    val query = state.getStateFlow("definitionQuery", "")
    val archived = state.getStateFlow("definitionArchive", false)
    private val retry = MutableStateFlow(0)
    fun search(text: String) { state["definitionQuery"] = text }
    fun archive(value: Boolean) { state["definitionArchive"] = value }
    fun reload() { retry.value++ }
    var busy by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var message by mutableStateOf<String?>(null); private set
    var preview by mutableStateOf<LibraryImportPreview?>(null); private set
    var selection by mutableStateOf(state.get<List<Int>>("definitionExportIds").orEmpty()); private set
    fun dismissError() { error = null }
    fun cancelPreview() { if (!busy) preview = null }
    fun selectForExport(ids: List<Int>) {
        selection = ids; state["definitionExportIds"] = ids
        state["definitionExportGeneration"] = library.value.generation
    }
    fun toggleExport(id: Int) { selection = if (id in selection) selection - id else selection + id; state["definitionExportIds"] = selection }
    fun readLibrary(uri: Uri) {
        val generation = library.value.generation ?: return
        operation { preview = exchange.preview(uri, generation) }
    }
    fun confirmImport() {
        val value = preview ?: return
        operation { val count = exchange.merge(value); preview = null; message = "$count definitions imported. Existing journal entries were retained." }
    }
    fun exportLibrary(uri: Uri) {
        val ids = selection.toSet()
        val generation = state.get<String>("definitionExportGeneration")
        if (generation == null) { error = "Choose the definitions again before exporting."; return }
        operation { exchange.export(uri, generation, ids); message = "Definition library saved." }
    }
    private fun operation(action: suspend () -> Unit) {
        if (busy) return
        busy = true; error = null; message = null
        viewModelScope.launch {
            try { action() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                error = when (e) {
                    is android.database.sqlite.SQLiteFullException -> "Not enough storage. Free some space and try again. The existing journal was retained."
                    is kotlinx.serialization.SerializationException -> "The library could not be decoded. Choose an unmodified JSON export."
                    is java.io.IOException -> e.message ?: "The library file could not be read or written. Choose another file or destination."
                    is IllegalArgumentException, is IllegalStateException -> e.message
                    else -> "The definition library operation failed. Existing journal data was retained. Try again."
                }
            } finally { busy = false }
        }
    }
    val library = retry.flatMapLatest {
        repo.definitions.mapLatest {
            guard.readSession(null) { token -> DefinitionLibraryState(false, repo.snapshot(), token) }
        }.onStart { emit(DefinitionLibraryState()) }
            .catch { emit(DefinitionLibraryState(false, error = "Could not load definitions. Try again.")) }
    }.combine(query) { snapshot, text -> snapshot.copy(records = snapshot.records.filter { record ->
        text.isBlank() || listOfNotNull(record.unit.name, record.unit.substanceName, record.unit.note, record.unit.unit)
            .any { it.contains(text.trim(), ignoreCase = true) }
    }) }.combine(archived) { snapshot, showArchive -> snapshot.copy(records = snapshot.records.filter { it.unit.isArchived == showArchive }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DefinitionLibraryState())
}

@HiltViewModel
class DefinitionEditorViewModel @Inject constructor(private val repo: DefinitionRepository,
    private val guard: JournalSessionGuard, private val substances: SubstanceRepository,
    private val experiences: ExperienceRepository, private val clock: Clock, private val state: SavedStateHandle) : ViewModel() {
    private val route = state.toRoute<DefinitionEditRoute>()
    private val json = Json { ignoreUnknownKeys = true }
    var draft by mutableStateOf(DefinitionDraft()); private set
    var errors by mutableStateOf<Map<String, String>>(emptyMap()); private set
    var validationAttempt by mutableIntStateOf(0); private set
    var loading by mutableStateOf(true); private set
    var busy by mutableStateOf(false); private set
    var completed by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var ready by mutableStateOf(false); private set
    var records by mutableStateOf<List<DefinitionWithContent>>(emptyList()); private set
    var substanceNames by mutableStateOf<List<String>>(emptyList()); private set
    var original: DefinitionWithContent? = null; private set
    private var generation: String? = null
    private var createdAt: Instant = Instant.EPOCH
    val isNew get() = route.definitionId == 0
    val canSave get() = ready && !busy && !completed

    init { load() }
    fun dismissError() { error = null }
    fun load() {
        if (busy) return
        loading = true; ready = false; error = null
        viewModelScope.launch {
            try {
                substances.initialize()
                val (token, snapshot) = guard.readSession(state.get<String>("journalGeneration") ?: route.generation) { token -> token to repo.snapshot() }
                val record = snapshot.find { it.unit.id == route.definitionId }
                check(isNew || record != null) { "This definition was deleted. Close this screen." }
                val fingerprint = listOfNotNull(record).fingerprint()
                check(state.get<String>("definitionBase")?.let { it == fingerprint } != false) { "This definition changed while your draft was open. Close this screen and reopen it." }
                generation = token; state["journalGeneration"] = token; state["definitionBase"] = fingerprint
                records = snapshot; original = record
                val created = state.get<Long>("definitionCreated") ?: record?.unit?.creationDate?.toEpochMilli() ?: clock.millis()
                state["definitionCreated"] = created; createdAt = Instant.ofEpochMilli(created)
                draft = state.get<String>("definitionDraft")?.let { json.decodeFromString<DefinitionDraft>(it) } ?: record?.toDraft() ?: DefinitionDraft()
                substanceNames = (substances.getAllSubstances().map { it.name } + experiences.getCustomSubstancesFlow().first().map { it.name }).distinct().sorted()
                ready = true
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "Could not load this definition. Try again." }
            finally { loading = false }
        }
    }

    fun change(value: DefinitionDraft) {
        if (!canSave) return
        draft = value; state["definitionDraft"] = json.encodeToString(value)
        if (validationAttempt > 0) errors = value.errors()
    }
    fun component(value: ComponentDraft) = change(draft.copy(components = draft.components.map { if (it.key == value.key) value else it }))
    fun roa(value: RoaDraft) = change(draft.copy(roas = draft.roas.map { if (it.key == value.key) value else it }))
    fun addSubstance(name: String) {
        val units = substances.getSubstance(name)?.roas?.mapNotNull { it.roaDose?.units }?.distinct()?.singleOrNull().orEmpty()
        change(draft.copy(components = draft.components + ComponentDraft(UUID.randomUUID().toString(), substance = name, units = units, createdAt = clock.millis())))
    }
    fun addDefinition(id: Int) = change(draft.copy(components = draft.components + ComponentDraft(UUID.randomUUID().toString(), childId = id, createdAt = clock.millis())))
    fun addRoute(route: AdministrationRoute) = change(draft.copy(roas = draft.roas + RoaDraft(UUID.randomUUID().toString(), route = route, createdAt = clock.millis())))
    fun save(onDone: () -> Unit) {
        if (!canSave) return
        errors = draft.errors(); validationAttempt++
        if (errors.isNotEmpty()) return
        val content = draft.content(original?.unit, createdAt)
        run(onDone) { repo.save(generation, content.unit, content.components, content.roaInfos, original) }
    }
    fun delete(onDone: () -> Unit) {
        val record = original ?: return
        if (canSave) run(onDone) { repo.delete(generation, record) }
    }
    private fun run(onDone: () -> Unit, action: suspend () -> Unit) {
        busy = true
        viewModelScope.launch {
            try { action(); completed = true }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                error = when (e) {
                    is android.database.sqlite.SQLiteFullException -> "Not enough storage. Free some space and retry. Your draft is still open."
                    is StaleJournalSession, is IllegalArgumentException, is IllegalStateException -> e.message
                    else -> "Could not save this definition. Your draft is still open; try again."
                }
            } finally { busy = false }
            if (completed) onDone()
        }
    }
}
