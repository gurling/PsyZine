// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.data.definitions

import android.content.Context
import android.net.Uri
import com.isaakhanimann.journal.data.backup.BackupArchive
import com.isaakhanimann.journal.data.backup.BackupValidation
import com.isaakhanimann.journal.data.room.AppDatabase
import com.isaakhanimann.journal.data.room.JournalSessionGuard
import com.isaakhanimann.journal.ui.tabs.settings.JournalExport
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class LibraryImportPreview(val journal: JournalExport, val generation: String, val base: String,
    val existingTags: List<SubstanceTag>, val tagMap: Map<String, String>, val plannedTags: List<SubstanceTag>,
    val messages: List<String>)

@Singleton
class DefinitionLibraryExchange @Inject constructor(private val db: AppDatabase, private val guard: JournalSessionGuard,
    @ApplicationContext private val context: Context) {
    suspend fun preview(uri: Uri, generation: String?): LibraryImportPreview = withContext(Dispatchers.IO) {
        val journal = try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val coroutine = currentCoroutineContext()
                val bytes = ByteArrayOutputStream()
                BackupArchive.copyLimited(input, bytes, BackupArchive.MAX_MANIFEST_BYTES) { coroutine.ensureActive() }
                DefinitionLibraryCodec.decode(bytes.toString("UTF-8"))
            } ?: error("The definition library is unavailable. Choose the file again.")
        } catch (_: OutOfMemoryError) { throw IOException("Not enough memory to read this library. No definitions were changed.") }
        guard.readSession(generation) { token ->
            val current = db.definitionDao().definitions()
            val oldTags = db.definitionDao().tags()
            val mergedTags = oldTags.toMutableList()
            val tagMap = mutableMapOf<String, String>()
            val planned = mutableListOf<SubstanceTag>()
            val messages = mutableListOf<String>()
            for (tag in journal.tags) {
                val same = oldTags.find { it.id == tag.id }
                if (same == tag) { tagMap[tag.id] = tag.id; continue }
                val id = if (same != null) UUID.randomUUID().toString() else tag.id
                var name = tag.name
                var suffix = 1
                while (mergedTags.any { normalizedTagName(it.name) == normalizedTagName(name) }) {
                    name = "${tag.name} (imported${if (suffix == 1) "" else " $suffix"})"; suffix++
                }
                val value = tag.copy(id = id, name = name)
                planned += value; mergedTags += value; tagMap[tag.id] = id
                if (id != tag.id || name != tag.name) messages += "Tag ‘${tag.name}’ will be added separately as ‘$name’. The existing tag is retained."
            }
            val names = current.map { it.unit.name }.toSet()
            val collisions = journal.customUnits.count { it.name in names }
            if (collisions > 0) messages += "$collisions definition names already exist. Separate definitions will be added without overwriting them."
            LibraryImportPreview(journal, token, current.fingerprint(), oldTags, tagMap, planned, messages)
        }
    }

    suspend fun merge(preview: LibraryImportPreview): Int = guard.withSession(preview.generation) {
        val dao = db.definitionDao()
        check(dao.definitions().fingerprint() == preview.base && dao.tags() == preview.existingTags) { "Definitions or tags changed after this preview. Choose the file again to review the new result." }
        BackupValidation.journal(preview.journal)
        val ids = linkedMapOf<Int, Int>()
        for (unit in preview.journal.customUnits) {
            val id = db.experienceDao().insert(unit.toEntity().copy(id = 0))
            require(id in 1L..Int.MAX_VALUE.toLong()) { "The journal has reached its supported definition ID limit. No changes were saved." }
            ids[unit.id] = id.toInt()
        }
        preview.journal.customUnits.forEach { unit ->
            val source = unit.doseComponents.ifEmpty { listOfNotNull(unit.toEntity().legacyComponent()) }
            dao.insertComponents(source.map { it.copy(id = 0, parentCustomUnitId = ids.getValue(unit.id),
                childCustomUnitId = it.childCustomUnitId?.let(ids::getValue)) })
            dao.insertRoaInfos(unit.roaInfos.map { it.copy(id = 0, customUnitId = ids.getValue(unit.id)) })
        }
        preview.plannedTags.forEach { dao.saveTag(it) }
        dao.insertMemberships(preview.journal.tagMemberships.map { row ->
            val subject = SubjectId.parse(row.subjectId) as SubjectId.Definition
            row.copy(subjectId = SubjectId.Definition(ids.getValue(subject.id)).key, tagId = preview.tagMap.getValue(row.tagId))
        })
        ids.size
    }

    suspend fun export(uri: Uri, generation: String?, selected: Set<Int>) = withContext(Dispatchers.IO) {
        val bytes = try {
            guard.readSession(generation) {
                DefinitionLibraryCodec.encode(db.definitionDao().definitions(), selected, db.definitionDao().tags(), db.definitionDao().memberships())
            }.toByteArray(Charsets.UTF_8)
        } catch (_: OutOfMemoryError) {
            // Complete encoding before opening/truncating the selected destination.
            throw IOException("Not enough memory to export this library. Export fewer definitions at a time.")
        }
        require(bytes.size <= BackupArchive.MAX_MANIFEST_BYTES) { "This definition library is too large. Export fewer definitions at a time." }
        context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
            output.write(bytes); output.flush()
        } ?: error("The export destination is unavailable. Choose another destination.")
    }
}
