package com.isaakhanimann.journal.psyzine

import com.isaakhanimann.journal.data.backup.*
import com.isaakhanimann.journal.data.room.experiences.entities.CustomSubstance
import com.isaakhanimann.journal.ui.tabs.settings.JournalExport
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class BackupValidationTest {
    @Test fun multipleTransientCustomSubstanceIdsAreValid() {
        BackupValidation.journal(JournalExport(customSubstances = listOf(
            CustomSubstance(name = "A", units = "mg", description = ""),
            CustomSubstance(name = "B", units = "mL", description = "")
        )))
    }
    @Test fun repeatedCustomSubstanceDefinitionsRemainValid() {
        val substance = CustomSubstance(name = "A", units = "mg", description = "")
        BackupValidation.journal(JournalExport(customSubstances = listOf(substance, substance)))
    }
    @Test fun rejectsTraversalAndUnexpectedArchivePaths() {
        listOf("../manifest.json", "/manifest.json", "images/../../entry", "images\\photo.jpg", "extra.json").forEach {
            assertFalse(it, runCatching { BackupArchive.validateEntry(it) }.isSuccess)
        }
        BackupArchive.validateEntry("manifest.json")
        BackupArchive.validateEntry("images/12345678-1234-1234-1234-123456789abc.jpg")
    }
    @Test fun extractionLimitsApplyToActualBytes() {
        val output = ByteArrayOutputStream()
        assertTrue(runCatching { BackupArchive.copyLimited(ByteArrayInputStream(ByteArray(20)), output, 10) }.isFailure)
        assertEquals(0, output.size())
    }
}
