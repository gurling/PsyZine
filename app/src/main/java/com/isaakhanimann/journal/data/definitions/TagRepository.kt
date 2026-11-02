// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.data.definitions

import com.isaakhanimann.journal.data.room.AppDatabase
import com.isaakhanimann.journal.data.room.JournalSessionGuard
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.coroutines.flow.combine

@Serializable
data class TagAssignmentSnapshot(val tags: List<SubstanceTag>, val memberships: List<SubstanceTagMembership>, val subjectVersion: String?, val subjectName: String)

data class TagFilterSnapshot(val generation: String, val tags: List<SubstanceTag>, val memberships: Map<String, Set<String>>)

fun primaryTagId(tags: List<SubstanceTag>, selected: Set<String>, preferred: String?): String? =
    preferred?.takeIf { it in selected } ?: tags.filter { it.id in selected }
        .minWithOrNull(compareBy<SubstanceTag, String>(String.CASE_INSENSITIVE_ORDER) { it.name }.thenBy { it.id })?.id

@Singleton
class TagRepository @Inject constructor(private val db: AppDatabase, private val guard: JournalSessionGuard) {
    val tags = db.definitionDao().observeTags()
    val memberships = db.definitionDao().observeMemberships()
    val filterSnapshots = combine(tags, memberships) { _, _ -> guard.readSession(null) { generation ->
        val (values, rows) = snapshot()
        TagFilterSnapshot(generation, values, rows.groupBy { it.subjectId }.mapValues { (_, items) -> items.map { it.tagId }.toSet() })
    } }
    suspend fun snapshot(): Pair<List<SubstanceTag>, List<SubstanceTagMembership>> =
        db.definitionDao().tags() to db.definitionDao().memberships()
    suspend fun assignment(subject: SubjectId): TagAssignmentSnapshot = TagAssignmentSnapshot(
        db.definitionDao().tags(), db.definitionDao().memberships().filter { it.subjectId == subject.key }, subjectVersion(subject),
        when (subject) {
            is SubjectId.Definition -> db.experienceDao().getCustomUnit(subject.id)?.name ?: "Missing definition"
            is SubjectId.Local -> subject.name
            is SubjectId.Catalog -> subject.name
        })
    private suspend fun subjectVersion(subject: SubjectId): String? = when (subject) {
        is SubjectId.Definition -> db.experienceDao().getCustomUnit(subject.id)?.toString()
            ?: error("This definition was deleted. Close this screen.")
        is SubjectId.Local -> db.experienceDao().getCustomSubstance(subject.name)?.toString()
        is SubjectId.Catalog -> null // Keep assignments readable when bundled metadata is absent.
    }
    suspend fun subjectVersions(subjects: Set<String>): Map<String, String?> = subjects.associateWith { subjectVersion(SubjectId.parse(it)) }
    suspend fun save(generation: String?, tag: SubstanceTag, original: SubstanceTag?) = guard.withSession(generation) {
        val all = db.definitionDao().tags()
        check(all.find { it.id == tag.id } == original) { "This tag changed or was deleted. Reopen it." }
        require(tag.name.isNotBlank()) { "Enter a name for this tag." }
        require(tag.color.isNotBlank()) { "Choose a color for this tag." }
        require(all.none { it.id != tag.id && normalizedTagName(it.name) == normalizedTagName(tag.name) }) { "A tag with this name already exists, ignoring accents and case." }
        UUID.fromString(tag.id)
        db.definitionDao().saveTag(tag.copy(name = tag.name.trim()))
    }
    suspend fun assign(generation: String?, subject: SubjectId, selected: Set<String>, primary: String?) = guard.withSession(generation) {
        if (subject is SubjectId.Definition) check(db.experienceDao().getCustomUnit(subject.id) != null) { "This definition was deleted." }
        replace(subject.key, selected, primary)
    }
    /** UI drafts must not overwrite another assignment or a renamed/deleted target. */
    suspend fun assign(generation: String?, subject: SubjectId, selected: Set<String>, primary: String?, original: TagAssignmentSnapshot) = guard.withSession(generation) {
        check(assignment(subject) == original) { "This subject or its tags changed while this screen was open. Reopen Tags to review the latest assignments." }
        replace(subject.key, selected, primary)
    }
    /** Multi-subject tag assignment commits as one transaction and retains all other tags. */
    suspend fun assignSubjects(generation: String?, tag: SubstanceTag, selected: Set<String>,
        originalMemberships: List<SubstanceTagMembership>, versions: Map<String, String?>) = guard.withSession(generation) {
        check(db.definitionDao().tags().find { it.id == tag.id } == tag) { "This tag changed or was deleted. Reopen it." }
        val current = db.definitionDao().memberships()
        check(current == originalMemberships) { "Tag assignments changed while this screen was open. Reopen it to review the latest assignments." }
        val before = current.filter { it.tagId == tag.id }.map { it.subjectId }.toSet()
        val affected = (before - selected) + (selected - before)
        for (key in affected) {
            check(key in versions && subjectVersion(SubjectId.parse(key)) == versions[key]) { "A selected subject changed or was deleted. Reopen this screen." }
            val rows = current.filter { it.subjectId == key }
            val ids = rows.map { it.tagId }.toSet().let { if (key in selected) it + tag.id else it - tag.id }
            replace(key, ids, rows.firstOrNull { it.isPrimary }?.tagId)
        }
    }
    private suspend fun replace(subject: String, selected: Set<String>, primary: String?) {
        val all = db.definitionDao().tags()
        require(selected.all { id -> all.any { it.id == id } }) { "A selected tag was deleted. Reopen the tag list." }
        val preferred = primaryTagId(all, selected, primary)
        db.definitionDao().deleteMemberships(subject)
        db.definitionDao().insertMemberships(selected.sorted().map { SubstanceTagMembership(subject, it, it == preferred) })
    }
    suspend fun delete(generation: String?, original: SubstanceTag) = guard.withSession(generation) {
        check(db.definitionDao().tags().find { it.id == original.id } == original) { "This tag changed or was deleted. Reopen it." }
        val affected = db.definitionDao().memberships().filter { it.tagId == original.id }.map { it.subjectId }.distinct()
        db.definitionDao().deleteTag(original.id)
        val remaining = db.definitionDao().memberships()
        affected.forEach { subject ->
            val rows = remaining.filter { it.subjectId == subject }
            replace(subject, rows.map { it.tagId }.toSet(), rows.firstOrNull { it.isPrimary }?.tagId)
        }
    }
}
