/*
 * Copyright (c) 2023. Isaak Hanimann.
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

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.platform.testTag
import com.isaakhanimann.journal.ui.tabs.search.substance.roa.toReadableString
import androidx.hilt.navigation.compose.hiltViewModel
import com.isaakhanimann.journal.ui.tabs.search.substancerow.SubstanceRow
import com.isaakhanimann.journal.ui.theme.horizontalPadding

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    searchViewModel: SearchViewModel = hiltViewModel(),
    onSubstanceTap: (substanceModel: SubstanceModel) -> Unit,
    onCustomSubstanceTap: (customSubstanceId: Int) -> Unit,
    navigateToAddCustomSubstanceScreen: () -> Unit,
    manageTags: () -> Unit,
    subjectTags: (com.isaakhanimann.journal.data.definitions.SubjectId) -> Unit,
    onDefinitionTap: (Int, String?) -> Unit = { _, _ -> },
    onExperienceTap: (Int, String?) -> Unit = { _, _ -> },
    onIngestionTap: (Int, String?) -> Unit = { _, _ -> },
) {
    val focusRequester = remember { FocusRequester() }
    var isFocused by remember { mutableStateOf(false) }
    Scaffold(
        floatingActionButton = {
            if (!isFocused) {
                FloatingActionButton(
                    onClick = { focusRequester.requestFocus() }) {
                    Icon(Icons.Default.Keyboard, contentDescription = "Keyboard")
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            val query by searchViewModel.searchTextFlow.collectAsStateWithLifecycle()
            val selectedCategories by searchViewModel.selectedCategories.collectAsStateWithLifecycle()
            SearchField(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { focusState ->
                        isFocused = focusState.isFocused
                    },
                searchText = query,
                onChange = {
                    searchViewModel.filterSubstances(searchText = it)
                },
                categories = searchViewModel.chipCategoriesFlow.collectAsStateWithLifecycle().value,
                onFilterTapped = searchViewModel::onFilterTapped,
                isShowingFilter = true, placeholder = "Search"
            )
            val activeFilters =
                searchViewModel.chipCategoriesFlow.collectAsStateWithLifecycle().value.filter { it.isActive }
            val onFilterTapped = searchViewModel::onFilterTapped
            val results by searchViewModel.results.collectAsStateWithLifecycle()
            val filteredSubstances = results.catalogNames.mapNotNull(searchViewModel::catalogModel)
            val filteredCustomSubstances = results.locals
            val selectedScope by searchViewModel.scope.collectAsStateWithLifecycle()
            val tagState by searchViewModel.tagState.collectAsStateWithLifecycle()
            val selectedTags by searchViewModel.selectedTags.collectAsStateWithLifecycle()
            val untagged by searchViewModel.includeUntagged.collectAsStateWithLifecycle()
            val tagSnapshot = tagState.snapshot
            val currentRequest = com.isaakhanimann.journal.data.definitions.JournalSearchRequest(query,
                selectedCategories, selectedTags.toSet(), untagged,
                com.isaakhanimann.journal.data.definitions.JournalSearchScope.fromSaved(selectedScope))
            val awaitingResults = results.generation != tagSnapshot?.generation || results.request != currentRequest
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalPadding).testTag("journal-search-scopes")) {
                items(com.isaakhanimann.journal.data.definitions.JournalSearchScope.entries, key = { it.name }) { scope ->
                    FilterChip(selected = scope == currentRequest.scope, onClick = { searchViewModel.changeScope(scope) },
                        label = { Text(scope.label) })
                }
            }
            FlowRow {
                TextButton(onClick = manageTags) { Text("Manage tags") }
                TextButton(onClick = { onDefinitionTap(0, tagSnapshot?.generation) },
                    enabled = tagSnapshot != null) { Text("New definition") }
            }
            if (tagSnapshot != null) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = horizontalPadding)) {
                item { FilterChip(selected = untagged, onClick = { searchViewModel.untagged(!untagged) }, label = { Text("Untagged") }) }
                items(tagSnapshot.tags, key = { it.id }) { tag ->
                    FilterChip(selected = tag.id in selectedTags, onClick = { searchViewModel.toggleTag(tag.id) }, label = { Text(tag.name) })
                }
                if (selectedTags.isNotEmpty() || untagged) item { TextButton(onClick = searchViewModel::clearTagFilters) { Text("Clear tags") } }
            }
            if (activeFilters.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.padding(vertical = 6.dp)
                ) {
                    item {
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    items(activeFilters.size) {
                        val categoryChipModel = activeFilters[it]
                        CategoryChipDelete(categoryChipModel = categoryChipModel) {
                            onFilterTapped(categoryChipModel.chipName)
                        }
                    }
                    item {
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                }
            }
            if (tagState.loading || tagState.error == null && awaitingResults) {
                CircularProgressIndicator(modifier = Modifier.padding(12.dp))
            } else if (tagState.error != null) {
                Text(tagState.error!!, modifier = Modifier.padding(12.dp))
                TextButton(onClick = searchViewModel::retryTags) { Text("Retry search") }
            } else if (results.isEmpty) {
                Column {
                    Text("No results match this search and filters.", modifier = Modifier.padding(12.dp))
                    if (selectedScope in listOf("ALL", "CUSTOM")) TextButton(onClick = navigateToAddCustomSubstanceScreen) {
                        Text("Add custom substance")
                    }
                }
            } else {
                LazyColumn {
                    items(results.definitions, key = { "definition:${it.unit.id}" }) { record ->
                        OutlinedCard(onClick = { onDefinitionTap(record.unit.id, results.generation) },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalPadding, vertical = 6.dp)) {
                            Column(Modifier.padding(12.dp)) {
                                Text(record.unit.name, style = MaterialTheme.typography.titleMedium)
                                Text(if (record.unit.isArchived) "Archived definition" else "Definition", style = MaterialTheme.typography.labelLarge)
                                Text("${record.components.size} ${if (record.components.size == 1) "component" else "components"} · ${record.unit.unit}",
                                    style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.ContentOrLtr))
                                if (record.unit.note.isNotBlank()) Text(record.unit.note)
                                SubjectTagSummary(com.isaakhanimann.journal.data.definitions.SubjectId.Definition(record.unit.id).key, tagSnapshot)
                                TextButton(onClick = { subjectTags(com.isaakhanimann.journal.data.definitions.SubjectId.Definition(record.unit.id)) },
                                    modifier = Modifier.semantics { contentDescription = "Tags for definition ${record.unit.name}" }) { Text("Tags") }
                            }
                        }
                    }
                    items(filteredCustomSubstances, key = { "local:${it.id}" }) { customSubstance ->
                        SubstanceRow(substanceModel = SubstanceModel(
                            name = customSubstance.name,
                            commonNames = emptyList(),
                            categories = listOf(
                                CategoryModel(
                                    name = "custom", color = customColor
                                )
                            ),
                            hasSaferUse = false,
                            hasInteractions = false
                        ), onTap = {
                            onCustomSubstanceTap(customSubstance.id)
                        })
                        SubjectTagSummary(com.isaakhanimann.journal.data.definitions.SubjectId.Local(customSubstance.name).key, tagSnapshot)
                        TextButton(onClick = { subjectTags(com.isaakhanimann.journal.data.definitions.SubjectId.Local(customSubstance.name)) },
                            modifier = Modifier.semantics { contentDescription = "Tags for ${customSubstance.name}" }) { Text("Tags") }
                        HorizontalDivider()
                    }
                    if (results.experiences.isNotEmpty()) item(key = "experience-heading") {
                        Text("Experiences (${results.experiences.size})", style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(horizontalPadding).semantics { heading() })
                    }
                    items(results.experiences, key = { "experience:${it.id}" }) { experience ->
                        OutlinedCard(onClick = { onExperienceTap(experience.id, results.generation) },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalPadding, vertical = 6.dp)) {
                            Column(Modifier.padding(12.dp)) {
                                Text(experience.title.ifBlank { "Untitled experience" }, style = MaterialTheme.typography.titleMedium)
                                Text(searchDate(experience.sortDate), style = MaterialTheme.typography.bodySmall)
                                if (experience.isFavorite) Text("Favorite", style = MaterialTheme.typography.labelLarge)
                                if (experience.text.isNotBlank()) Text(experience.text, maxLines = 3,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            }
                        }
                    }
                    if (results.ingestions.isNotEmpty()) item(key = "ingestion-heading") {
                        Text("Ingestions (${results.ingestions.size})", style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(horizontalPadding).semantics { heading() })
                    }
                    items(results.ingestions, key = { "ingestion:${it.ingestion.id}" }) { hit ->
                        OutlinedCard(onClick = { onIngestionTap(hit.ingestion.id, results.generation) },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalPadding, vertical = 6.dp)) {
                            Column(Modifier.padding(12.dp)) {
                                Text(hit.name.ifBlank { "Unnamed substance" }, style = MaterialTheme.typography.titleMedium)
                                Text(searchDate(hit.ingestion.time), style = MaterialTheme.typography.bodySmall)
                                Text(ingestionSearchAmount(hit.ingestion),
                                    style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.ContentOrLtr))
                                Text(hit.ingestion.administrationRoute.displayText)
                                Text(hit.experience.title.ifBlank { "Untitled experience" })
                                Text(hit.ingestion.consumerName?.takeIf { it.isNotBlank() } ?: "Me")
                                if (hit.hasAmbiguousSubject) Text("Multiple subjects share this name; the original subject is unknown.")
                                hit.ingestion.notes?.takeIf { it.isNotBlank() }?.let { Text(it, maxLines = 3,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
                            }
                        }
                    }
                    items(filteredSubstances, key = { "pw:${it.name}" }) { substance ->
                        SubstanceRow(substanceModel = substance, onTap = {
                            onSubstanceTap(substance)
                        })
                        SubjectTagSummary(com.isaakhanimann.journal.data.definitions.SubjectId.Catalog(substance.name).key, tagSnapshot)
                        TextButton(onClick = { subjectTags(com.isaakhanimann.journal.data.definitions.SubjectId.Catalog(substance.name)) },
                            modifier = Modifier.semantics { contentDescription = "Tags for ${substance.name}" }) { Text("Tags") }
                        HorizontalDivider()
                    }

                    item {
                        val addCustomSubstanceText = "Add custom substance"
                        TextButton(
                            onClick = navigateToAddCustomSubstanceScreen,
                            modifier = Modifier
                                .padding(horizontal = horizontalPadding)
                                .semantics { contentDescription = addCustomSubstanceText }
                        ) {
                            Icon(
                                Icons.Outlined.Add, contentDescription = "Add"
                            )
                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                            Text(text = addCustomSubstanceText)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubjectTagSummary(subject: String, snapshot: com.isaakhanimann.journal.data.definitions.TagFilterSnapshot?) {
    val ids = snapshot?.memberships?.get(subject).orEmpty()
    if (ids.isNotEmpty()) Text(snapshot?.tags.orEmpty().filter { it.id in ids }.joinToString { it.name },
        modifier = Modifier.padding(horizontal = horizontalPadding))
}


private fun searchDate(value: java.time.Instant): String =
    java.time.format.DateTimeFormatter.ofPattern("d MMM uuuu · HH:mm", java.util.Locale.getDefault())
        .withZone(java.time.ZoneId.systemDefault()).format(value)

private fun ingestionSearchAmount(value: com.isaakhanimann.journal.data.room.experiences.entities.Ingestion): String {
    val amount = value.dose ?: return "Unknown amount"
    val uncertainty = value.estimatedDoseStandardDeviation.takeIf { value.isDoseAnEstimate }
    val prefix = if (value.isDoseAnEstimate && uncertainty == null) "~" else ""
    return prefix + amount.toReadableString() + (uncertainty?.let { "±${it.toReadableString()}" } ?: "") +
        value.units?.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()
}
