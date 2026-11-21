// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.data.definitions

import com.isaakhanimann.journal.data.room.experiences.entities.CustomUnit
import com.isaakhanimann.journal.data.room.experiences.entities.Ingestion

data class ExperienceCardNames(val parents: List<String> = emptyList(), val components: List<String> = emptyList()) {
    fun selected(showComponents: Boolean) = if (showComponents) components else parents
}

/** Card summaries use direct named components; nested children keep their parent's name. */
fun experienceCardNames(ingestions: List<Ingestion>, definitions: Map<Int, CustomUnit>,
    components: Map<Int, List<DoseComponent>>, checkCancelled: () -> Unit = {}): ExperienceCardNames {
    val parents = linkedSetOf<String>()
    val expanded = linkedSetOf<String>()
    ingestions.sortedWith(compareBy<Ingestion> { it.time }.thenBy { it.id }).forEach { ingestion ->
        checkCancelled()
        val unit = ingestion.customUnitId?.let(definitions::get)
        val parent = unit?.name?.takeIf(String::isNotBlank) ?: ingestion.substanceName?.takeIf(String::isNotBlank) ?: "Unknown substance"
        parents += parent
        val parts = unit?.id?.let(components::get).orEmpty()
        if (parts.isNotEmpty() && parts.all { !it.substanceName.isNullOrBlank() && it.childCustomUnitId == null }) {
            parts.sortedWith(compareBy<DoseComponent> { it.creationDate }.thenBy { it.id }).forEach {
                checkCancelled(); expanded += it.substanceName!!
            }
        } else expanded += parent
    }
    return ExperienceCardNames(parents.toList(), expanded.toList())
}
