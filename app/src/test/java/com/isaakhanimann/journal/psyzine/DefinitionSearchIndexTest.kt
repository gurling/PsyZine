// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.psyzine

import com.isaakhanimann.journal.data.definitions.*
import com.isaakhanimann.journal.data.room.experiences.entities.CustomUnit
import org.junit.Assert.*
import org.junit.Test

class DefinitionSearchIndexTest {
    private fun row(id: Int, name: String, parts: List<DoseComponent> = emptyList(), note: String = "") = DefinitionWithContent(
        CustomUnit(id = id, substanceName = null, name = name, administrationRoute = null, dose = null,
            estimatedDoseStandardDeviation = null, isEstimate = false, isArchived = true, unit = "capsule", originalUnit = null,
            note = note, isHiddenInTimeline = true), parts, emptyList())
    @Test fun nestedHiddenInvalidDosesRemainSearchableWithoutArithmetic() {
        val a = row(1, "Outer", listOf(DoseComponent(1, 1, childCustomUnitId = 2, dose = Double.NaN, isHiddenInTimeline = true)))
        val b = row(2, "Inner", listOf(DoseComponent(2, 2, substanceName = "Caffeine", dose = -1.0)))
        val index = DefinitionSearchIndex(listOf(a, b))
        assertEquals(setOf("Caffeine"), index.leafNames(1))
        assertTrue(matchesDefinitionTerm(a, index.leafNames(1), "caff", setOf("Caffeine"), null, emptyMap(), emptySet(), false))
    }
    @Test fun cyclesMissingChildrenAndSharedBranchesTerminateAndKeepValidLeaves() {
        val a = row(1, "Outer", listOf(DoseComponent(1, 1, childCustomUnitId = 2), DoseComponent(2, 1, childCustomUnitId = 99)))
        val b = row(2, "Inner", listOf(DoseComponent(3, 2, childCustomUnitId = 1), DoseComponent(4, 2, substanceName = "Caffeine")))
        val index = DefinitionSearchIndex(listOf(a, b, row(3, "Standalone")))
        assertEquals(setOf("Caffeine"), index.leafNames(1)); assertEquals(setOf("Caffeine"), index.leafNames(2))
        assertEquals(setOf("Standalone"), index.leafNames(3)); assertTrue(index.leafNames(99).isEmpty())
    }
    @Test fun namesNormalizeButNotesUseRawCaseInsensitiveMatchingAndOwnTags() {
        val record = row(1, "Café-blend", note = "Résumé note")
        fun match(term: String, tags: Set<String> = emptySet(), untagged: Boolean = false) = matchesDefinitionTerm(
            record, emptySet(), term, emptySet(), null, mapOf("pw:Café-blend" to setOf("a")), tags, untagged)
        assertTrue(match("cafe blend")); assertTrue(match("RÉSUMÉ")); assertFalse(match("resume"))
        assertFalse(match("cafe", setOf("a"))); assertTrue(match("cafe", setOf("a"), true))
        assertFalse(matchesDefinitionTerm(record, setOf("A", "B"), "", setOf("A", "B"), emptySet(), emptyMap(), emptySet(), false))
    }
    @Test fun filteredIngredientMatchesAreRequiredForBlankCategorySearchButNotOwnNameSearch() {
        val record = row(1, "Own blend")
        val memberships = mapOf("custom:1" to setOf("selected"))
        fun match(term: String, matched: Set<String>, categories: Set<String>?) = matchesDefinitionTerm(
            record, setOf("Caffeine"), term, matched, categories, memberships, setOf("selected"), false)
        assertTrue(match("", emptySet(), null))
        assertFalse(match("", emptySet(), setOf("Caffeine")))
        assertTrue(match("", setOf("Caffeine"), setOf("Caffeine")))
        assertFalse(match("caff", emptySet(), setOf("Caffeine")))
        assertTrue(match("own", emptySet(), setOf("Caffeine")))
    }
    @Test fun nestedRecentsUseDepthFirstComponentOrderBeforeDeduplicatingNames() {
        val outer = row(1, "Outer", listOf(DoseComponent(1, 1, childCustomUnitId = 2),
            DoseComponent(2, 1, substanceName = "C"), DoseComponent(3, 1, substanceName = "A")))
        val inner = row(2, "Inner", listOf(DoseComponent(4, 2, substanceName = "A"), DoseComponent(5, 2, substanceName = "B")))
        val legacyWithoutComponents = row(3, "Named unit").let { it.copy(unit = it.unit.copy(substanceName = "Stored legacy name")) }
        val index = DefinitionSearchIndex(listOf(outer, inner, legacyWithoutComponents))
        assertEquals(listOf("A", "B", "C"), index.leafNames(1).toList())
        assertEquals(listOf("Named unit"), index.leafNames(3).toList())
    }
}
