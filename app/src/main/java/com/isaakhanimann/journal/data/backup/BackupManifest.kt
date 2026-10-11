// PsyZine, 2026-10-11. GPL-3.0-or-later.
package com.isaakhanimann.journal.data.backup

import com.isaakhanimann.journal.ui.tabs.settings.JournalExport
import kotlinx.serialization.Serializable

@Serializable
data class BackupManifest(val format: String = "PsyZine", val version: Int = 1,
    val journal: JournalExport, val photos: List<BackupPhoto> = emptyList(), val theme: String = "system",
    val presentation: BackupPresentation? = null)
@Serializable
data class BackupPhoto(val id: String, val experienceIndex: Int, val path: String, val mimeType: String,
    val width: Int, val height: Int, val byteSize: Long, val createdAt: Long, val position: Int, val sha256: String)

@Serializable
data class BackupPresentation(val enabled: Boolean = true, val lockScreenDetails: Boolean = false, val widgetDetails: Boolean = true)
