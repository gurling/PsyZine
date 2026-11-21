/*
 * Copyright (c) 2022-2023. Isaak Hanimann.
 * This file is part of PsychonautWiki Journal.
 *
 * PsychonautWiki Journal is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * PsychonautWiki Journal is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with PsychonautWiki Journal.  If not, see https://www.gnu.org/licenses/gpl-3.0.en.html.
 */

package com.isaakhanimann.journal.ui.tabs.search

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.isaakhanimann.journal.data.substances.repositories.SubstanceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import javax.inject.Inject
import com.isaakhanimann.journal.data.definitions.*

data class SearchTagsState(val loading: Boolean = true, val snapshot: TagFilterSnapshot? = null,
    val error: String? = null, val browser: SubjectBrowserSnapshot? = null)

@OptIn(kotlinx.coroutines.FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val substanceRepo: SubstanceRepository,
    browser: SubjectBrowserRepository,
    private val state: androidx.lifecycle.SavedStateHandle,
) : ViewModel() {
    val searchTextFlow = state.getStateFlow("subjectQuery", "")
    private val filtersFlow = state.getStateFlow("subjectCategories", emptyList<String>())
    val selectedCategories get() = filtersFlow
    val selectedTags = state.getStateFlow("subjectTags", emptyList<String>())
    val includeUntagged = state.getStateFlow("subjectUntagged", false)
    val scope = state.getStateFlow("subjectScope", JournalSearchScope.ALL.name)
    private val _tagState = MutableStateFlow(SearchTagsState())
    val tagState = _tagState.asStateFlow()
    private val retry = MutableStateFlow(0)
    private val catalog = substanceRepo.searchEntries().map { it.ranking }

    init {
        viewModelScope.launch {
            combine(retry, scope.map { JournalSearchScope.fromSaved(it).includesJournal }.distinctUntilChanged()) { _, journal -> journal }
                .flatMapLatest { includeJournal ->
                    browser.observe(includeJournal).map { SearchTagsState(false, it.tags, browser = it) }
                        .onStart { emit(SearchTagsState()) }
                        .catch { emit(SearchTagsState(false, error = "Could not load your search results. Retry to load the current journal.")) }
                }.collect { value ->
                    value.snapshot?.let { snapshot ->
                        val previous = state.get<String>("subjectTagGeneration")
                        val available = snapshot.tags.map { it.id }.toSet()
                        if (previous != null && previous != snapshot.generation) {
                            state["subjectTags"] = emptyList<String>()
                            state["subjectUntagged"] = false
                        } else state["subjectTags"] = selectedTags.value.filter { it in available }
                        state["subjectTagGeneration"] = snapshot.generation
                    }
                    _tagState.value = value
                }
        }
    }

    fun retryTags() { retry.value++ }
    fun changeScope(value: JournalSearchScope) { state["subjectScope"] = value.name }
    fun toggleTag(id: String) {
        state["subjectTags"] = if (id in selectedTags.value) selectedTags.value - id else selectedTags.value + id
    }
    fun untagged(value: Boolean) { state["subjectUntagged"] = value }
    fun clearTagFilters() { state["subjectTags"] = emptyList<String>(); state["subjectUntagged"] = false }
    fun onFilterTapped(filterName: String) {
        state["subjectCategories"] = if (filterName in filtersFlow.value) filtersFlow.value - filterName else filtersFlow.value + filterName
    }
    fun filterSubstances(searchText: String) { state["subjectQuery"] = searchText }

    val chipCategoriesFlow = filtersFlow.map { filters ->
        listOf(CategoryChipModel("custom", customColor, "custom" in filters)) + substanceRepo.getAllCategories().map {
            CategoryChipModel(it.name, it.color, it.name in filters)
        }
    }.flowOn(kotlinx.coroutines.Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val tagRequest = combine(selectedTags, includeUntagged) { tags, untagged -> tags.toSet() to untagged }
    private val request = combine(searchTextFlow.debounce(200), filtersFlow, tagRequest, scope) { query, categories, tags, selectedScope ->
        JournalSearchRequest(query, categories, tags.first, tags.second, JournalSearchScope.fromSaved(selectedScope))
    }
    val results = combine(request, tagState) { request, data -> request to data.browser }
        .mapLatest { (request, snapshot) ->
            if (snapshot == null) JournalSearchProjection(request = request)
            else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                val context = kotlinx.coroutines.currentCoroutineContext()
                projectJournalSearch(snapshot, catalog, request) { context.ensureActive() }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), JournalSearchProjection())

    fun catalogModel(name: String) = substanceRepo.getSubstanceWithCategories(name)?.toSubstanceModel()
}


val customColor = Color.Cyan

data class CategoryChipModel(
    val chipName: String, val color: Color, val isActive: Boolean
)

data class CategoryModel(
    val name: String, val color: Color
)
