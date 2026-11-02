// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.data.definitions

import com.isaakhanimann.journal.data.backup.BackupValidation
import com.isaakhanimann.journal.data.backup.importers.JournalImportAdapter
import com.isaakhanimann.journal.ui.tabs.settings.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

@Serializable
private data class DefinitionLibraryExport(val exportSource: String = "PsyZine Definitions 1",
    val customUnits: List<CustomUnitSerializable>, val tags: List<SubstanceTag> = emptyList(),
    val tagMemberships: List<SubstanceTagMembership> = emptyList())

/** Separate from full-journal restore: choosing a library must never replace journal entries. */
object DefinitionLibraryCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    fun decode(text: String): JournalExport {
        val root = json.parseToJsonElement(text).jsonObject
        val library = when (root["exportSource"]?.jsonPrimitive?.contentOrNull) {
            "Android Custom Substances 17.1" -> JournalImportAdapter.decode(JsonObject(root.toMutableMap().apply {
                put("exportSource", JsonPrimitive("Android Journal 17.1"))
                put("experiences", JsonArray(emptyList())); put("substanceCompanions", JsonArray(emptyList()))
            }).toString()).journal
            "PsyZine Definitions 1" -> json.decodeFromString<DefinitionLibraryExport>(text).let {
                JournalExport(customUnits = it.customUnits, tags = it.tags, tagMemberships = it.tagMemberships)
            }
            else -> throw IllegalArgumentException("Choose a PsyZine definition library or an Android Custom Substances 17.1 file. Full journals belong in Restore backup.")
        }
        require(library.customUnits.isNotEmpty()) { "This file contains no custom definitions." }
        BackupValidation.journal(library)
        require(library.tagMemberships.all { SubjectId.parse(it.subjectId) is SubjectId.Definition }) { "This library contains assignments outside its definitions." }
        return library
    }
    fun encode(records: List<DefinitionWithContent>, selected: Set<Int>, tags: List<SubstanceTag>, memberships: List<SubstanceTagMembership>): String {
        require(selected.isNotEmpty()) { "Select at least one definition to export." }
        val byId = records.associateBy { it.unit.id }
        val included = linkedSetOf<Int>()
        val pending = ArrayDeque<Int>().apply { addAll(selected.sorted()) }
        while (pending.isNotEmpty()) {
            val id = pending.removeFirst()
            if (!included.add(id)) continue
            val record = byId[id] ?: error("A selected or nested definition was deleted. Reopen the export list.")
            record.components.mapNotNull { it.childCustomUnitId }.forEach(pending::addLast)
        }
        val rows = included.map { byId.getValue(it) }
        val subjects = included.map { id -> "custom:$id" }.toSet()
        val assignments = memberships.filter { it.subjectId in subjects }
        val tagIds = assignments.map { it.tagId }.toSet()
        val exportedTags = tags.filter { it.id in tagIds }
        val units = rows.map { it.unit.toPortable(it.components, it.roaInfos) }
        BackupValidation.journal(JournalExport(customUnits = units, tags = exportedTags, tagMemberships = assignments))
        return json.encodeToString(DefinitionLibraryExport(customUnits = units, tags = exportedTags, tagMemberships = assignments))
    }
}
