// PsyZine, 2026-10-11. GPL-3.0-or-later.
package com.isaakhanimann.journal.data.backup

import com.isaakhanimann.journal.ui.tabs.settings.JournalExport
import com.isaakhanimann.journal.ui.theme.ThemeId
import com.isaakhanimann.journal.data.attachments.MAX_IMAGE_BYTES
import java.util.UUID

object BackupValidation {
    fun journal(journal: JournalExport) {
        require(journal.experiences.size <= 100000) { "Too many experiences" }
        require(journal.customUnits.map { it.id }.distinct().size == journal.customUnits.size) { "Duplicate custom unit IDs" }
        // IDs are transient and names were never unique in the original schema/UI.
        // Preserve repeated custom-substance definitions; Room allocates fresh IDs.
        require(journal.substanceCompanions.map { it.substanceName }.distinct().size == journal.substanceCompanions.size) { "Duplicate substance companions" }
        val units = journal.customUnits.map { it.id }.toSet()
        journal.customUnits.forEach { unit ->
            require(unit.id > 0 && unit.name.isNotBlank() && unit.substanceName.isNotBlank() && unit.unit.isNotBlank()) { "Invalid custom unit" }
            com.isaakhanimann.journal.data.room.validateDose(unit.dose, unit.estimatedDoseStandardDeviation)
        }
        var total = 0L
        journal.experiences.forEach { entry ->
            require(entry.title.length <= 1000000 && entry.text.length <= 1000000) { "Entry text is too large" }
            total += entry.ingestions.size + entry.ratings.size + entry.timedNotes.size
            require(total <= 1000000) { "Too many journal records" }
            entry.ingestions.forEach { ingestion ->
                require(ingestion.customUnitId == null || ingestion.customUnitId in units) { "Missing custom unit reference" }
                require(ingestion.substanceName.isNotBlank()) { "Missing ingestion substance" }
                com.isaakhanimann.journal.data.room.validateDose(ingestion.dose, ingestion.estimatedDoseStandardDeviation)
                require(ingestion.endTime == null || ingestion.endTime!! >= ingestion.time) { "Invalid ingestion end time" }
            }
        }
    }
    fun manifest(manifest: BackupManifest) {
        require(manifest.format == "PsyZine" && manifest.version == 1) { "Unsupported backup version" }
        require(ThemeId.entries.any { it.key == manifest.theme }) { "Unknown backup theme" }
        journal(manifest.journal)
        require(manifest.photos.size < BackupArchive.MAX_ENTRIES) { "Too many photos in backup" }
        require(manifest.photos.map { it.id }.distinct().size == manifest.photos.size) { "Duplicate photo IDs" }
        require(manifest.photos.map { it.path }.distinct().size == manifest.photos.size) { "Duplicate photo paths" }
        manifest.photos.forEach { photo ->
            require(UUID.fromString(photo.id).toString() == photo.id) { "Invalid photo ID" }
            require(photo.experienceIndex in manifest.journal.experiences.indices) { "Photo references a missing experience" }
            require(BackupArchive.validImagePath(photo.path) && photo.path.substringAfter('/').substringBefore('.') == photo.id) { "Invalid photo path" }
            require(photo.mimeType == "image/jpeg" || photo.mimeType == "image/png") { "Unsupported stored image" }
            require(photo.path.endsWith(if (photo.mimeType == "image/png") ".png" else ".jpg")) { "Image extension does not match MIME type" }
            require(photo.width in 1..2560 && photo.height in 1..2560 && photo.byteSize in 1..MAX_IMAGE_BYTES) { "Invalid photo size" }
            require(photo.position >= 0 && photo.sha256.matches(Regex("[a-f0-9]{64}"))) { "Invalid photo metadata" }
        }
    }
}
