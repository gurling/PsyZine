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

package com.isaakhanimann.journal.data.substances.repositories

import com.isaakhanimann.journal.data.substances.classes.SubstanceWithCategories
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchRepository @Inject constructor(
    val substanceRepo: SubstanceRepository
) : SearchRepositoryInterface {

    override fun getMatchingSubstances(searchText: String, filterCategories: List<String>,
        recentlyUsedSubstanceNamesSorted: List<String>): List<SubstanceWithCategories> {
        val query = normalizeSearch(searchText)
        val candidates = substanceRepo.searchEntries().filter { entry ->
            filterCategories.all { it in entry.value.substance.categories }
        }
        val matched = if (query.isEmpty()) candidates else candidates.filter { entry -> entry.names.any { query in it } }
            .sortedBy { entry -> when {
                entry.primary.startsWith(query) -> 0
                entry.names.any { it.startsWith(query) } -> 1
                else -> 2
            } }
        val recent = recentlyUsedSubstanceNamesSorted.distinct().withIndex().associate { it.value to it.index }
        return matched.sortedWith(compareBy<SubstanceRepository.SearchEntry> { recent[it.value.substance.name] ?: Int.MAX_VALUE }
            .thenBy { if (it.value.categories.any { category -> category.name == "common" }) 0 else 1 })
            .map { it.value }
    }
}
