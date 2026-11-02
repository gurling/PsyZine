// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.psyzine

import com.isaakhanimann.journal.data.substances.repositories.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class CatalogSearchRankingTest {
    private fun row(name: String, vararg aliases: String) = CatalogSearchRecord(name, aliases.toList(), setOf("a", "b"))
    @Test fun primaryPrefixesSuppressAliasPrefixesAndShortSubstringMatches() {
        val rows = listOf(row("Alpha"), row("Other", "Alpine"), row("Salt"))
        assertEquals(listOf("Alpha"), rankCatalogSubjects(rows, "al", emptyList(), emptyList()))
        assertEquals(listOf("Other"), rankCatalogSubjects(rows, "alpi", emptyList(), emptyList()))
    }
    @Test fun sparsePrefixesAppendPrimarySubstringsBeforeConsideringAliasSubstrings() {
        val rows = listOf(row("Alpha"), row("Beta alpha"), row("Other", "Long alpha alias"))
        assertEquals(listOf("Alpha", "Beta alpha"), rankCatalogSubjects(rows, "alpha", emptyList(), emptyList()))
        assertEquals(listOf("Other"), rankCatalogSubjects(rows, "alias", emptyList(), emptyList()))
    }
    @Test fun threePrefixesPreventSubstringExpansionAndRecentOrderCannotAddNonMatches() {
        val rows = listOf(row("Alpha1"), row("Alpha2"), row("Alpha3"), row("Beta alpha"), row("Other"))
        assertEquals(listOf("Alpha3", "Alpha1", "Alpha2"),
            rankCatalogSubjects(rows, "alpha", emptyList(), listOf("Other", "Beta alpha", "Alpha3", "Alpha3")))
    }
    @Test fun termIntersectionKeepsFirstTermOrderAndRequiresAllCategoriesOnOneSubject() {
        val rows = listOf(row("Alpha", "First"), row("Alpine", "First"), row("Alone", "Second"),
            CatalogSearchRecord("Algebra", listOf("First"), setOf("a")))
        assertEquals(listOf("Alpine", "Alpha"), rankCatalogSubjects(rows, "al,first", listOf("a", "b"), listOf("Alpine")))
        assertTrue(rankCatalogSubjects(rows, "al,unrelated", emptyList(), emptyList()).isEmpty())
        assertEquals(rows.map { it.name }, rankCatalogSubjects(rows, " , \t", emptyList(), emptyList()))
    }
    @Test fun normalizedSubstringHandlesHyphensAndLocaleIsStable() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals(listOf("I-NDOLE"), rankCatalogSubjects(listOf(row("I-NDOLE")), "indole", emptyList(), emptyList()))
        } finally { Locale.setDefault(original) }
    }
}
