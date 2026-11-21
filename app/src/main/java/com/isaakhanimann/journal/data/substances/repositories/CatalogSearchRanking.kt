// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.data.substances.repositories

import java.util.Locale

/** Immutable names keep normalization out of repeated search work. */
class CatalogSearchRecord(val name: String, aliases: List<String>, val categories: Set<String>) {
    val prefixName = name.lowercase(Locale.ROOT)
    val prefixAliases = aliases.map { it.lowercase(Locale.ROOT) }
    val normalizedName = normalizeSearch(name)
    val normalizedAliases = aliases.map(::normalizeSearch)
}

/** Per-term ranking followed by identity intersection in the first term's order. */
fun rankCatalogSubjects(records: List<CatalogSearchRecord>, query: String,
    categories: List<String>, recents: List<String>): List<String> {
    val candidates = records.filter { it.categories.containsAll(categories) }
    val terms = query.split(Regex("[\\s,]+")).filter(String::isNotEmpty).ifEmpty { listOf("") }
    val recentOrder = recents.distinct()
    fun rank(term: String): List<String> {
        val normalized = normalizeSearch(term)
        val primaryPrefix = candidates.filter { it.prefixName.startsWith(normalized) }
        val prefix = primaryPrefix.ifEmpty { candidates.filter { row -> row.prefixAliases.any { it.startsWith(normalized) } } }
        val contains = if (term.length >= 3 && prefix.size < 3) {
            candidates.filter { normalized in it.normalizedName }.ifEmpty {
                candidates.filter { row -> row.normalizedAliases.any { normalized in it } }
            }
        } else emptyList()
        val ranked = (prefix + contains).map { it.name }.distinct()
        val matched = ranked.toSet()
        return (recentOrder.filter { it in matched } + ranked).distinct()
    }
    val first = rank(terms.first())
    val allowed = terms.drop(1).map { rank(it).toSet() }
    return first.filter { name -> allowed.all { name in it } }
}
