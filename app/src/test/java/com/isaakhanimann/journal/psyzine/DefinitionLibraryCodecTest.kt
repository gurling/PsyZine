package com.isaakhanimann.journal.psyzine

import com.isaakhanimann.journal.data.definitions.*
import com.isaakhanimann.journal.data.room.experiences.entities.CustomUnit
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class DefinitionLibraryCodecTest {
    @Test fun legacyExportRefusesNativeDataInsteadOfSilentlyDiscardingIt() {
        val journal = com.isaakhanimann.journal.ui.tabs.settings.JournalExport(customUnits = listOf(unit(1).toPortable(emptyList(), emptyList())))
        val error = assertThrows(IllegalArgumentException::class.java) { com.isaakhanimann.journal.data.backup.BackupValidation.legacyJournal(journal) }
        assertTrue(error.message!!.contains("Full backup"))
    }
    private fun unit(id: Int) = CustomUnit(id, null, "Definition $id", Instant.EPOCH, null, null, null, false, false, "capsule", null, null, "kept")
    @Test fun selectedParentIncludesNestedDependenciesAndPreservesPartialLocalMetadata() {
        val child = DefinitionWithContent(unit(2).copy(isArchived = true), listOf(DoseComponent(8, 2, "Synthetic", "mg", dose = 0.0001)), emptyList())
        val parent = DefinitionWithContent(unit(1), listOf(DoseComponent(7, 1, childCustomUnitId = 2, dose = 3.0)),
            listOf(CustomRoaInfo(6, 1, AdministrationRoute.ORAL, onsetMin = 1.0, onsetMax = 2.0, onsetUnit = "MINUTES")))
        val tag = SubstanceTag("00000000-0000-0000-0000-000000000001", "Tag", "FUTURE_COLOR")
        val text = DefinitionLibraryCodec.encode(listOf(parent, child), setOf(1), listOf(tag), listOf(SubstanceTagMembership("custom:1", tag.id, true)))
        val restored = DefinitionLibraryCodec.decode(text)
        assertEquals(setOf(1, 2), restored.customUnits.map { it.id }.toSet())
        assertEquals(2, restored.customUnits.first { it.id == 1 }.doseComponents.single().childCustomUnitId)
        assertTrue(restored.customUnits.first { it.id == 2 }.isArchived)
        val roa = restored.customUnits.first { it.id == 1 }.roaInfos.single()
        assertEquals(1.0, roa.onsetMin!!, 0.0); assertNull(roa.peakMin)
        assertEquals(listOf(tag), restored.tags)
    }
    @Test fun referenceLibraryMapsSignedIdsWithoutBecomingAFullJournalRestore() {
        val text = """{"exportSource":"Android Custom Substances 17.1","customUnits":[
            {"id":-1,"name":"Synthetic","creationDate":1000,"unit":"capsule","doseComponents":[
            {"id":-8,"creationDate":1000,"substanceName":"Synthetic leaf","substanceUnit":"mg","dose":1.0,"isEstimate":false}]}]}"""
        val result = DefinitionLibraryCodec.decode(text)
        assertTrue(result.experiences.isEmpty())
        assertEquals(1, result.customUnits.single().id)
        assertEquals("mg", result.customUnits.single().doseComponents.single().substanceUnit)
    }
    @Test fun fullJournalWrongSourceAndMissingSelectionAreRejectedClearly() {
        val error = assertThrows(IllegalArgumentException::class.java) { DefinitionLibraryCodec.decode("""{"exportSource":"Android Journal 17.1","experiences":[]}""") }
        assertTrue(error.message!!.contains("Restore backup"))
        assertThrows(IllegalArgumentException::class.java) { DefinitionLibraryCodec.encode(emptyList(), emptySet(), emptyList(), emptyList()) }
    }
}
