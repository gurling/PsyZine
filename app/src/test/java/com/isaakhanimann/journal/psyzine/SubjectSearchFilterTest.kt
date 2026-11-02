// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.psyzine

import com.isaakhanimann.journal.data.definitions.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class SubjectSearchFilterTest {
    @Test fun subjectTokensIntersectAcrossAliasesAndIgnoreEmptySeparators() {
        assertTrue(matchesSubjectTokens(listOf("Alpha compound", "Café-name"), " alpha, CAFE "))
        assertTrue(matchesSubjectTokens(listOf("LSD"), "l-s d"))
        assertTrue(matchesSubjectTokens(listOf("Alpha"), " , \t"))
        assertFalse(matchesSubjectTokens(listOf("Alpha"), "alpha,beta"))
    }
    @Test fun tagFiltersUseAnyOrUntaggedAndKeepNamespacesSeparate() {
        val rows = mapOf("pw:Same" to setOf("a", "b"), "local:Same" to setOf("c"))
        assertTrue(matchesSubjectTags("pw:Same", rows, setOf("a", "missing"), false))
        assertFalse(matchesSubjectTags("local:Same", rows, setOf("a"), false))
        assertTrue(matchesSubjectTags("custom:1", rows, setOf("a"), true))
        assertFalse(matchesSubjectTags("local:Same", rows, setOf("a"), true))
        assertTrue(matchesSubjectTags("local:Same", rows, emptySet(), false))
        assertFalse(matchesSubjectTags("pw:Same", rows, emptySet(), true))
    }
    @Test fun subjectMatchingDoesNotDependOnTurkishSystemLocale() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertTrue(matchesSubjectTokens(listOf("INDOLE"), "indole"))
            assertEquals("cafe", normalizedTagName(" Café "))
        } finally { Locale.setDefault(original) }
    }
}
