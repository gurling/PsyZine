// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.psyzine

import com.isaakhanimann.journal.data.backup.*
import com.isaakhanimann.journal.ui.tabs.settings.JournalExport
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class JournalDisplayBackupTest {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    @Test fun displayOptionsRoundTripWithoutChangingThePortableFormatVersion() {
        val value = BackupManifest(version = 2, journal = JournalExport(), journalDisplay = BackupJournalDisplay(false, true))
        val restored = json.decodeFromString<BackupManifest>(json.encodeToString(value))
        assertEquals(2, restored.version)
        assertEquals(BackupJournalDisplay(false, true), restored.journalDisplay)
        BackupValidation.manifest(restored)
    }

    @Test fun oldAndPartiallySpecifiedManifestsDoNotInventDisplaySelections() {
        val old = json.decodeFromString<BackupManifest>("""{"version":1,"journal":{}}""")
        assertNull(old.journalDisplay)
        val partial = json.decodeFromString<BackupManifest>("""{"version":2,"journal":{},"journalDisplay":{"showComponents":false,"futureSetting":true}}""")
        assertEquals(false, partial.journalDisplay!!.showComponents)
        assertNull(partial.journalDisplay.hideTimelineQuantities)
    }
}
