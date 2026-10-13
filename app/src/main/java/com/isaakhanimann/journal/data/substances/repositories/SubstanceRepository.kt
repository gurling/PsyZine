/*
 * Copyright (c) 2022. Isaak Hanimann.
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

import android.content.Context
import com.isaakhanimann.journal.data.substances.classes.Category
import com.isaakhanimann.journal.data.substances.classes.Substance
import com.isaakhanimann.journal.data.substances.classes.SubstanceFile
import com.isaakhanimann.journal.data.substances.classes.SubstanceWithCategories
import com.isaakhanimann.journal.data.substances.parse.SubstanceParserInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubstanceRepository @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val substanceParser: SubstanceParserInterface,
) : SubstanceRepositoryInterface {

    private val initialization = kotlinx.coroutines.sync.Mutex()
    @Volatile private var loaded: LoadedSubstances? = null
    private val data get() = checkNotNull(loaded) { "Substances are not initialized. Await initialize() before reading." }

    data class SearchEntry(val value: SubstanceWithCategories, val primary: String, val names: List<String>)
    private data class LoadedSubstances(val file: SubstanceFile, val categories: List<SubstanceWithCategories>,
        val byName: Map<String, SubstanceWithCategories>, val search: List<SearchEntry>)

    suspend fun initialize() {
        if (loaded != null) return
        initialization.withLock {
            if (loaded != null) return@withLock
            val text = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                appContext.assets.open("Substances.json").bufferedReader().use { it.readText() }
            }
            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                val file = substanceParser.parseSubstanceFile(string = text)
                val categories = file.categories.associateBy { it.name }
                val categorized = file.substances.map { substance ->
                    SubstanceWithCategories(substance, substance.categories.mapNotNull(categories::get))
                }
                LoadedSubstances(file, categorized, categorized.associateBy { it.substance.name }, categorized.map { value ->
                    SearchEntry(value, normalizeSearch(value.substance.name),
                        (value.substance.commonNames + value.substance.name).map(::normalizeSearch))
                })
            }
            loaded = result
        }
    }

    fun searchEntries(): List<SearchEntry> = data.search
    override fun getAllSubstances(): List<Substance> = data.file.substances
    override fun getAllSubstancesWithCategories(): List<SubstanceWithCategories> = data.categories
    override fun getAllCategories(): List<Category> = data.file.categories
    override fun getSubstance(substanceName: String): Substance? = data.file.substancesMap[substanceName]
    override fun getCategory(categoryName: String): Category? = data.file.categories.firstOrNull { it.name == categoryName }
    override fun getSubstanceWithCategories(substanceName: String): SubstanceWithCategories? = data.byName[substanceName]
}

fun normalizeSearch(value: String): String = value.filter { it != '-' && it != ' ' }.lowercase(java.util.Locale.ROOT)
