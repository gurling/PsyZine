// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.data.definitions

/** Search depends on structure, never on valid dose arithmetic or timeline visibility. */
data class DefinitionSearchLeaf(val subject: SubjectId, val name: String)

class DefinitionSearchIndex(records: List<DefinitionWithContent>, private val checkCancelled: () -> Unit = {}) {
    private val recordsById = records.associateBy { it.unit.id }
    private val cached = mutableMapOf<Int, Set<DefinitionSearchLeaf>>()

    fun leafNames(id: Int): Set<String> = leaves(id).mapTo(linkedSetOf()) { it.name }

    fun leaves(id: Int): Set<DefinitionSearchLeaf> = cached.getOrPut(id) {
        val names = linkedSetOf<DefinitionSearchLeaf>()
        val seenAtDepth = mutableMapOf<Int, Int>()
        fun visit(current: Int, depth: Int) {
            checkCancelled()
            if (depth > 64 || (seenAtDepth[current] ?: Int.MAX_VALUE) <= depth) return
            seenAtDepth[current] = depth
            val record = recordsById[current] ?: return
            if (record.components.isEmpty()) names += DefinitionSearchLeaf(SubjectId.Definition(current), record.unit.name)
            else record.components.forEach { part ->
                checkCancelled()
                if (part.childCustomUnitId != null) visit(part.childCustomUnitId, depth + 1)
                else part.substanceName?.let { names += DefinitionSearchLeaf(SubjectId.Catalog(it), it) }
            }
        }
        visit(id, 1)
        names
    }
}

/** Journal/definition-name matching differs from catalog name normalization. */
fun normalizedJournalText(value: String): String = normalizedTagName(value)
    .replace(Regex("[^\\p{L}\\p{N}_]"), "")

fun matchesDefinitionTerm(record: DefinitionWithContent, leafNames: Set<String>, term: String,
    matchedCatalogNames: Set<String>, categoryNames: Set<String>?, memberships: Map<String, Set<String>>,
    selectedTags: Set<String>, includeUntagged: Boolean): Boolean {
    if (!matchesSubjectTags(SubjectId.Definition(record.unit.id).key, memberships, selectedTags, includeUntagged)) return false
    // The same leaf must meet every selected category, rather than merging categories across ingredients.
    if (categoryNames != null && leafNames.none { it in categoryNames }) return false
    if (term.isBlank()) return categoryNames == null || leafNames.any { it in matchedCatalogNames }
    return normalizedJournalText(term) in normalizedJournalText(record.unit.name) ||
        record.unit.note.contains(term, ignoreCase = true) || leafNames.any { it in matchedCatalogNames }
}
