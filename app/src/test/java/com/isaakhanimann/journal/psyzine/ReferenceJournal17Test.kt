package com.isaakhanimann.journal.psyzine

import com.isaakhanimann.journal.data.backup.importers.JournalImportAdapter
import com.isaakhanimann.journal.data.backup.importers.JournalImportException
import com.isaakhanimann.journal.data.room.experiences.entities.AdaptiveColor
import org.junit.Assert.*
import org.junit.Test

/** Hand-authored synthetic fixture for the versioned Android journal import format. */
class ReferenceJournal17Test {
    private val fixture = """{
      "exportSource":"Android Journal 17.1",
      "experiences":[{"title":"Synthetic test","text":"","sortDate":1000,"creationDate":1000,
        "ingestions":[{"substanceName":null,"time":1000,"endTime":null,"administrationRoute":"ORAL",
          "dose":2.0,"isDoseAnEstimate":false,"customUnitId":-44}]}],
      "customUnits":[{"id":-44,"name":"Synthetic blend","creationDate":1000,"unit":"capsule","unitPlural":"",
        "doseComponents":[{"id":-7,"creationDate":1000,"substanceName":"Synthetic A","substanceUnit":"mg","customUnitId":null,"dose":0.0001,"isEstimate":false,"notes":"kept"},
          {"id":13,"creationDate":2000,"substanceName":"Synthetic B","substanceUnit":"mg","customUnitId":null,"dose":1.234567,"isEstimate":true,"estimatedDoseStandardDeviation":0.00002}],
        "color":"CITRUS_LEAF","halfToleranceInDays":2,"zeroToleranceInDays":4,
        "tags":[{"id":"00000000-0000-0000-0000-000000000001","name":"Test","color":"BLUE","creationTime":1000}],
        "primaryTagID":"00000000-0000-0000-0000-000000000001"}],
      "substanceCompanions":[{"substanceName":"Synthetic A","color":"FUTURE_COLOR","tags":[],"primaryTagID":null}],
      "tags":[{"id":"00000000-0000-0000-0000-000000000001","name":"Test","color":"BLUE","creationTime":1000}]
    }"""
    @Test fun nativeBlendSignedIdsTagsColorsAndMillisecondDatesSurviveDecoding() {
        val result = JournalImportAdapter.decode(fixture)
        val unit = result.journal.customUnits.single()
        assertEquals(1, unit.id); assertNull(unit.substanceName)
        assertEquals(1, result.journal.experiences.single().ingestions.single().customUnitId)
        assertEquals(0.0001, unit.doseComponents[0].dose!!, 0.0)
        assertEquals("mg", unit.doseComponents[0].substanceUnit)
        assertEquals("kept", unit.doseComponents[0].note)
        assertEquals(1L, unit.creationDate.epochSecond)
        assertEquals("CITRUS_LEAF", unit.color)
        assertEquals("custom:1", result.journal.tagMemberships.single().subjectId)
        assertTrue(result.journal.tagMemberships.single().isPrimary)
        assertEquals(AdaptiveColor.RED, result.journal.substanceCompanions.single().color)
        assertEquals("FUTURE_COLOR", result.journal.substanceCompanions.single().referenceColor)
        assertFalse(result.warnings.isEmpty())
    }
    @Test fun conflictingEmbeddedTagsDoNotSilentlyOverwriteGlobalTags() {
        assertThrows(JournalImportException::class.java) { JournalImportAdapter.decode(fixture.replaceFirst("\"name\":\"Test\"", "\"name\":\"Different\"")) }
    }
    @Test fun missingDefinitionAndNonfiniteInputAreRejected() {
        assertThrows(JournalImportException::class.java) { JournalImportAdapter.decode(fixture.replace("\"customUnitId\":-44", "\"customUnitId\":88")) }
        assertThrows(JournalImportException::class.java) { JournalImportAdapter.decode(fixture.replace("1.234567", "-1")) }
    }
    @Test fun futureSourceIsNotAssumedCompatible() {
        val error = assertThrows(JournalImportException::class.java) { JournalImportAdapter.decode(fixture.replace("17.1", "99.0")) }
        assertEquals("unsupported_source", error.code)
    }
}
