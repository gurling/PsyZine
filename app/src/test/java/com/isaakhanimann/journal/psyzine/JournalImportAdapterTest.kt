// PsyZine, 2026-10-11. GPL-3.0-or-later.
package com.isaakhanimann.journal.psyzine

import com.isaakhanimann.journal.data.backup.importers.*
import org.junit.Assert.*
import org.junit.Test

class JournalImportAdapterTest {
    private fun newer(units: String, ingestions: String = "[]", custom: String = "[]") = """
      {"exportSource":"Android Journal","customUnits":$units,"customSubstances":$custom,
       "experiences":[{"title":"Preserved","text":"notes","sortDate":1700000000,"ingestions":$ingestions}]}
    """
    private fun ingestion(id: String) = """[{"substanceName":null,"customUnitId":"$id","time":1700000000,"endTime":null,"administrationRoute":"ORAL","dose":2,"isDoseAnEstimate":false}]"""
    private fun unit(id: String, components: String, name: String = id) = """{"id":"$id","name":"$name","unit":"tablet","note":"source note","doseComponents":$components}"""

    @Test fun legacyExportAndUnknownFieldsAreAccepted() {
        val result = JournalImportAdapter.decode("""{"experiences":[{"title":"a","text":"b","sortDate":1700000000,"futureField":7}]}""")
        assertEquals("a", result.journal.experiences.single().title)
        assertTrue(result.warnings.isEmpty())
    }
    @Test fun singleIngredientKeepsIdentityDoseAndUnitReference() {
        val result = JournalImportAdapter.decode(newer("[${unit("uuid", """[{"substanceName":"Caffeine","dose":100,"units":"mg"}]""")}]", ingestion("uuid")))
        val unit = result.journal.customUnits.single()
        assertEquals("Caffeine", unit.substanceName)
        assertEquals(100.0, unit.dose!!, .0)
        assertEquals("mg", unit.originalUnit)
        assertEquals(unit.id, result.journal.experiences.single().ingestions.single().customUnitId)
        assertEquals(2.0, result.journal.experiences.single().ingestions.single().dose!!, .0)
    }
    @Test fun recursivelyNestedUnitsPreserveQuantityAndUncertainty() {
        val first = unit("base", """[{"substanceName":"Caffeine","dose":100,"units":"mg","estimatedDoseStandardDeviation":10}]""")
        val second = unit("child", """[{"customUnitId":"base","dose":2,"units":"tablet"}]""")
        val third = unit("grandchild", """[{"customUnitId":"child","dose":3,"units":"tablet"}]""")
        val result = JournalImportAdapter.decode(newer("[$third,$second,$first]", ingestion("grandchild")))
        val converted = result.journal.customUnits.first()
        assertEquals("Caffeine", converted.substanceName)
        assertEquals(600.0, converted.dose!!, .0)
        assertEquals("mg", converted.originalUnit)
        assertEquals(60.0, converted.estimatedDoseStandardDeviation!!, .0)
        assertTrue(converted.isEstimate)
    }
    @Test fun multiIngredientProductRetainsEveryComponentWithoutFirstIngredientInference() {
        val product = unit("mix", """[{"substanceName":"Caffeine","dose":80,"units":"mg"},{"substanceName":"Unknown ingredient","dose":10,"units":"mg"}]""", "Mixture")
        val result = JournalImportAdapter.decode(newer("[$product]", ingestion("mix")))
        assertEquals("Mixture", result.journal.experiences.single().ingestions.single().substanceName)
        assertTrue(result.journal.customSubstances.single().description!!.contains("Unknown ingredient"))
        assertTrue(result.journal.customUnits.single().note.contains("Caffeine"))
        assertTrue(result.warnings.isNotEmpty())
    }
    @Test fun existingCustomSubstancesSurviveAndNameCollisionsStayDistinct() {
        val result = JournalImportAdapter.decode(newer("[${unit("new", "[]", "Product")}]", ingestion("new"), """[{"name":"Product","units":"mg","description":"original"}]"""))
        assertEquals(2, result.journal.customSubstances.size)
        assertEquals("original", result.journal.customSubstances.first().description)
        assertEquals("Product (imported 2)", result.journal.experiences.single().ingestions.single().substanceName)
    }
    @Test fun mixtureNameCannotBeMistakenForBundledReferenceSubstance() {
        val product = unit("mix", """[{"substanceName":"Caffeine","dose":80,"units":"mg"},{"substanceName":"Other","dose":10,"units":"mg"}]""", "Caffeine")
        val result = JournalImportAdapter.decode(newer("[$product]", ingestion("mix")), setOf("Caffeine"))
        assertEquals("Caffeine (imported product)", result.journal.experiences.single().ingestions.single().substanceName)
    }
    @Test fun missingIngredientDoseUnitsStayUnknownRatherThanBeingInvented() {
        val product = unit("one", """[{"substanceName":"Caffeine","dose":100}]""")
        val result = JournalImportAdapter.decode(newer("[$product]", ingestion("one")))
        assertNull(result.journal.customUnits.single().dose)
        assertTrue(result.warnings.isNotEmpty())
    }
    @Test fun cyclesAndMissingReferencesAreRejected() {
        for (units in listOf("[${unit("a", """[{"customUnitId":"b","dose":1}]""")},${unit("b", """[{"customUnitId":"a","dose":1}]""")}]",
            "[${unit("a", """[{"customUnitId":"missing","dose":1}]""")}]")) {
            assertThrows(JournalImportException::class.java) { JournalImportAdapter.decode(newer(units)) }
        }
    }
    @Test fun duplicateIdsAmbiguousIngredientsAndMalformedNumbersAreRejected() {
        val valid = unit("a", "[]")
        for (units in listOf("[$valid,$valid]", "[${unit("a", """[{"substanceName":"X","customUnitId":"a","dose":1}]""")}]",
            "[${unit("a", """[{"substanceName":"X","dose":-1},{"substanceName":"Y","dose":1}]""")}]")) {
            assertThrows(JournalImportException::class.java) { JournalImportAdapter.decode(newer(units)) }
        }
    }
    @Test fun unsupportedSourcesAndUnrelatedJsonDoNotBecomeEmptyRestores() {
        assertThrows(JournalImportException::class.java) { JournalImportAdapter.decode("""{"exportSource":"Other","experiences":[]}""") }
        assertThrows(JournalImportException::class.java) { JournalImportAdapter.decode("""{"random":[]}""") }
        assertThrows(JournalImportException::class.java) { JournalImportAdapter.decode("broken") }
    }
}
