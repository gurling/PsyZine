// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.data.definitions

import com.isaakhanimann.journal.data.room.AppDatabase
import com.isaakhanimann.journal.data.room.JournalSessionGuard
import com.isaakhanimann.journal.data.room.experiences.entities.CustomUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

fun List<DefinitionWithContent>.toGraph() = CustomDefinitionGraph(map { it.unit }, flatMap { it.components }, flatMap { it.roaInfos })

fun List<DefinitionWithContent>.fingerprint(): String = MessageDigest.getInstance("SHA-256").digest(
    Json.encodeToString(sortedBy { it.unit.id }.map { row -> row.unit.toPortable(row.components.sortedBy { it.id }, row.roaInfos.sortedBy { it.id }) })
        .toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

fun List<DefinitionWithContent>.definitionFingerprint(id: Int): String {
    val byId = associateBy { it.unit.id }
    val selected = linkedSetOf<Int>()
    val pending = ArrayDeque<Int>().apply { add(id) }
    while (pending.isNotEmpty()) {
        val next = pending.removeFirst()
        if (!selected.add(next)) continue
        val record = byId[next] ?: error("A nested definition was deleted. Choose the definition again.")
        record.components.mapNotNull { it.childCustomUnitId }.forEach(pending::addLast)
    }
    return selected.map { byId.getValue(it) }.fingerprint()
}

@Singleton
class DefinitionRepository @Inject constructor(private val db: AppDatabase, private val guard: JournalSessionGuard) {
    val definitions = db.definitionDao().observeDefinitions().flowOn(Dispatchers.IO)
    val graphs = definitions.map { it.toGraph() }.flowOn(Dispatchers.Default)
    suspend fun snapshot(): List<DefinitionWithContent> = db.definitionDao().definitions()

    suspend fun save(generation: String?, unit: CustomUnit, components: List<DoseComponent>, roas: List<CustomRoaInfo>,
        original: DefinitionWithContent?): Int = guard.withSession(generation) {
        val current = if (unit.id != 0) db.definitionDao().definition(unit.id) else null
        check(current == original) { "This definition changed or was deleted. Reopen it before saving." }
        require(unit.name.isNotBlank() && unit.unit.isNotBlank()) { "Enter a name and a singular unit, for example capsule." }
        val existingIds = original?.components.orEmpty().map { it.id }.toSet()
        require(components.all { it.id == 0 || it.id in existingIds }) { "A component changed. Reopen this definition." }
        val existingRoas = original?.roaInfos.orEmpty().map { it.id }.toSet()
        require(roas.all { it.id == 0 || it.id in existingRoas }) { "Route information changed. Reopen this definition." }
        val id = if (unit.id == 0) db.experienceDao().insert(unit).also {
            require(it in 1L..Int.MAX_VALUE.toLong()) { "This journal has reached the supported definition ID limit. No changes were saved." }
        }.toInt() else unit.id
        var candidate = unit.copy(id = id,
            name = if (unit.name == original?.unit?.name) unit.name else unit.name.trim(),
            unit = if (unit.unit == original?.unit?.unit) unit.unit else unit.unit.trim())
        if (candidate.substanceName != null) {
            require(roas.all { it.administrationRoute == candidate.administrationRoute }) { "Convert this legacy unit to a blend before changing its fixed route." }
            val leaf = components.singleOrNull()
            require(leaf?.substanceName != null && leaf.childCustomUnitId == null) { "Convert this legacy unit to a blend before adding multiple or nested components." }
            candidate = candidate.copy(substanceName = leaf.substanceName, originalUnit = leaf.substanceUnit,
                dose = leaf.dose, estimatedDoseStandardDeviation = leaf.estimatedDoseStandardDeviation, isEstimate = leaf.isEstimate)
        }
        val saved = DefinitionWithContent(candidate, components.map { it.copy(parentCustomUnitId = id) }, roas.map { it.copy(customUnitId = id) })
        val all = snapshot().filterNot { it.unit.id == id } + saved
        all.toGraph().validateFrom(id)
        check(db.experienceDao().update(candidate) == 1) { "This definition was deleted." }
        db.definitionDao().deleteComponents(id); db.definitionDao().deleteRoaInfos(id)
        db.definitionDao().insertComponents(saved.components); db.definitionDao().insertRoaInfos(saved.roaInfos)
        id
    }

    suspend fun delete(generation: String?, original: DefinitionWithContent) = guard.withSession(generation) {
        check(db.definitionDao().definition(original.unit.id) == original) { "This definition changed or was deleted. Reopen it." }
        val uses = db.experienceDao().getCustomUnitWithIngestions(original.unit.id)?.ingestions.orEmpty().size
        require(uses == 0 && db.definitionDao().parentCount(original.unit.id) == 0) {
            "This definition is used by journal entries or another blend. Archive it to keep those records intact."
        }
        db.definitionDao().deleteComponents(original.unit.id); db.definitionDao().deleteRoaInfos(original.unit.id)
        db.definitionDao().deleteMemberships(SubjectId.Definition(original.unit.id).key)
        check(db.experienceDao().delete(original.unit) == 1) { "This definition was deleted." }
    }
}
