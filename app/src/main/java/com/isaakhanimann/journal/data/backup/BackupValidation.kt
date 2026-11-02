// PsyZine, 2026-10-11. GPL-3.0-or-later.
package com.isaakhanimann.journal.data.backup

import com.isaakhanimann.journal.ui.tabs.settings.JournalExport
import com.isaakhanimann.journal.ui.theme.ThemeId
import com.isaakhanimann.journal.data.attachments.MAX_IMAGE_BYTES
import java.util.UUID
import com.isaakhanimann.journal.data.definitions.*

object BackupValidation {
    /** Legacy readers cannot retain native graphs, tags or visibility metadata. Never drop them silently. */
    fun legacyJournal(journal: JournalExport): JournalExport {
        val representable = journal.tags.isEmpty() && journal.tagMemberships.isEmpty() &&
            journal.substanceCompanions.none { it.isHiddenInTimeline || it.referenceColor != null || it.color.name == "CITRUS_LEAF" } &&
            journal.experiences.none { entry -> entry.ingestions.any { it.isHiddenInTimeline } || entry.ratings.any { it.isHiddenInTimeline } } &&
            journal.customUnits.all { unit -> unit.substanceName != null && unit.color == null && !unit.isHiddenInTimeline &&
                !unit.areTimelinesOfSubcomponentsShown && !unit.areTolerancesOfSubcomponentsShown && unit.halfToleranceInDays == null &&
                unit.zeroToleranceInDays == null && unit.roaInfos.isEmpty() && (unit.doseComponents.isEmpty() ||
                unit.doseComponents.size == 1 && unit.doseComponents.single().let { part ->
                    part.childCustomUnitId == null && part.substanceName == unit.substanceName && part.substanceUnit == unit.originalUnit &&
                        part.dose == unit.dose && part.isEstimate == unit.isEstimate && part.estimatedDoseStandardDeviation == unit.estimatedDoseStandardDeviation &&
                        part.note.orEmpty() == unit.note && !part.isHiddenInTimeline
                }) }
        require(representable) { "Legacy JSON cannot preserve this journal's definitions, tags or timeline settings. Choose Full backup to keep all data." }
        return journal.copy(customUnits = journal.customUnits.map { it.copy(doseComponents = emptyList()) })
    }

    fun journal(journal: JournalExport) {
        require(journal.experiences.size <= 100000) { "Too many experiences" }
        require(journal.customUnits.map { it.id }.distinct().size == journal.customUnits.size) { "Duplicate custom unit IDs" }
        // IDs are transient and names were never unique in the original schema/UI.
        // Preserve repeated custom-substance definitions; Room allocates fresh IDs.
        require(journal.substanceCompanions.map { it.substanceName }.distinct().size == journal.substanceCompanions.size) { "Duplicate substance companions" }
        val units = journal.customUnits.map { it.id }.toSet()
        journal.customUnits.forEach { unit ->
            require(unit.id > 0 && unit.name.isNotBlank() && unit.unit.isNotBlank()) { "Invalid custom definition: enter a name and singular unit." }
            require(unit.substanceName == null || unit.substanceName.isNotBlank()) { "Missing custom unit substance." }
            require(unit.doseComponents.all { it.parentCustomUnitId == unit.id } && unit.roaInfos.all { it.customUnitId == unit.id }) { "A definition contains another definition's components or route information." }
            com.isaakhanimann.journal.data.room.validateDose(unit.dose, unit.estimatedDoseStandardDeviation)
        }
        CustomDefinitionGraph(journal.customUnits.map { it.toEntity() }, journal.customUnits.flatMap { it.doseComponents }, journal.customUnits.flatMap { it.roaInfos }).validate()
        require(journal.tags.size <= 100000 && journal.tagMemberships.size <= 1000000) { "Too many tags or assignments." }
        require(journal.tags.map { it.id }.distinct().size == journal.tags.size) { "Duplicate tag IDs." }
        require(journal.tags.map { normalizedTagName(it.name) }.distinct().size == journal.tags.size) { "Tag names must be unique, ignoring accents and case." }
        journal.tags.forEach { require(it.name.isNotBlank() && it.color.isNotBlank()) { "A tag needs a name and color." }; UUID.fromString(it.id) }
        val tags = journal.tags.map { it.id }.toSet()
        require(journal.tagMemberships.map { it.subjectId to it.tagId }.distinct().size == journal.tagMemberships.size) { "Duplicate tag assignments." }
        journal.tagMemberships.forEach {
            require(it.tagId in tags) { "A tag assignment refers to a missing tag." }
            val subject = SubjectId.parse(it.subjectId)
            require(subject !is SubjectId.Definition || subject.id in units) { "A tag assignment refers to a missing definition." }
        }
        require(journal.tagMemberships.filter { it.isPrimary }.groupBy { it.subjectId }.values.all { it.size <= 1 }) { "A subject has more than one primary tag." }
        var total = 0L
        journal.experiences.forEach { entry ->
            require(entry.title.length <= 1000000 && entry.text.length <= 1000000) { "Entry text is too large" }
            total += entry.ingestions.size + entry.ratings.size + entry.timedNotes.size
            require(total <= 1000000) { "Too many journal records" }
            entry.ingestions.forEach { ingestion ->
                require(ingestion.customUnitId == null || ingestion.customUnitId in units) { "Missing custom unit reference" }
                require(ingestion.customUnitId != null || !ingestion.substanceName.isNullOrBlank()) { "Choose a substance or custom definition for every ingestion." }
                com.isaakhanimann.journal.data.room.validateDose(ingestion.dose, ingestion.estimatedDoseStandardDeviation)
                require(ingestion.endTime == null || ingestion.endTime!! >= ingestion.time) { "Invalid ingestion end time" }
            }
        }
    }
    fun manifest(manifest: BackupManifest) {
        require(manifest.format == "PsyZine" && manifest.version in 1..2) { "This backup needs a newer PsyZine version. The existing journal was preserved." }
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
