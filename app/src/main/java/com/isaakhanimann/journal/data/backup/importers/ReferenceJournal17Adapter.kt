// PsyZine, 2026. GPL-3.0-or-later. Independently implemented from observed wire contracts.
package com.isaakhanimann.journal.data.backup.importers

import com.isaakhanimann.journal.data.definitions.*
import com.isaakhanimann.journal.data.room.experiences.entities.AdaptiveColor
import kotlinx.serialization.json.*

/** External signed IDs are references, not Room identities. No source record is overwritten by name. */
internal object ReferenceJournal17Adapter {
    private val json = Json { ignoreUnknownKeys = true }

    fun convert(root: JsonObject, warnings: MutableList<String>): JsonObject {
        require(root.text("exportSource") == "Android Journal 17.1") { "Unsupported Android Journal version." }
        val sourceUnits = root.array("customUnits").map { it.jsonObject }
        require(sourceUnits.size <= 100000) { "Too many custom definitions." }
        val ids = sourceUnits.mapIndexed { i, unit -> unit.id("id") to i + 1 }.toMap()
        require(ids.size == sourceUnits.size) { "Duplicate custom definition IDs." }
        fun mapped(element: JsonElement?): Int? = element?.takeUnless { it == JsonNull }?.let {
            ids[it.jsonPrimitive.content.toIntOrNull() ?: error("Invalid custom definition ID.")]
                ?: error("An ingestion or component refers to a missing custom definition.")
        }
        val tags = linkedMapOf<String, SubstanceTag>()
        val memberships = mutableListOf<SubstanceTagMembership>()
        fun collectTag(element: JsonElement): SubstanceTag {
            val tag = json.decodeFromJsonElement<SubstanceTag>(element)
            val old = tags.putIfAbsent(tag.id, tag)
            require(old == null || old == tag) { "The export contains conflicting definitions of the same tag." }
            return tag
        }
        root.array("tags").forEach(::collectTag)
        fun collectMemberships(subject: String, source: JsonObject) {
            val assigned = source.array("tags").map { collectTag(it).id }
            require(assigned.distinct().size == assigned.size) { "A subject has a duplicate tag assignment." }
            val primary = source.text("primaryTagID")
            require(primary == null || primary in assigned) { "A primary tag is not assigned to its subject." }
            memberships += assigned.map { SubstanceTagMembership(subject, it, it == primary) }
        }
        val componentIds = mutableSetOf<Int>()
        val roaIds = mutableSetOf<Int>()
        var componentId = 0
        var roaId = 0
        val units = sourceUnits.map { source ->
            val id = ids.getValue(source.id("id"))
            collectMemberships(SubjectId.Definition(id).key, source)
            val components = source.array("doseComponents").map { raw ->
                val component = raw.jsonObject
                require(componentIds.add(component.id("id"))) { "Duplicate component IDs." }
                JsonObject(component.toMutableMap().apply {
                    put("id", JsonPrimitive(++componentId))
                    put("parentCustomUnitId", JsonPrimitive(id))
                    put("childCustomUnitId", mapped(component["customUnitId"])?.let(::JsonPrimitive) ?: JsonNull)
                    put("note", component["notes"] ?: component["note"] ?: JsonNull)
                })
            }
            val roas = source.array("roaInfos").map { raw ->
                val roa = raw.jsonObject
                require(roaIds.add(roa.id("id"))) { "Duplicate custom route IDs." }
                val dose = roa["doseInfo"]?.jsonObject ?: error("Custom route dosage information is missing.")
                val duration = roa["durationInfo"]?.takeUnless { it == JsonNull }?.jsonObject
                buildJsonObject {
                    put("id", ++roaId); put("customUnitId", id)
                    put("creationDate", roa["creationDate"] ?: error("Custom route creation time is missing."))
                    put("administrationRoute", roa["administrationRoute"] ?: error("Choose an administration route."))
                    for (name in listOf("lightMin", "commonMin", "strongMin", "heavyMin")) {
                        put(name + "Dose", dose[name] ?: error("Custom dosage thresholds are incomplete."))
                    }
                    for (name in listOf("onset", "comeup", "peak", "offset")) {
                        val range = duration?.get(name)?.jsonObject
                        put(name + "Min", range?.get("min") ?: JsonNull)
                        put(name + "Max", range?.get("max") ?: JsonNull)
                        put(name + "Unit", range?.get("units") ?: JsonNull)
                    }
                }
            }
            JsonObject(source.toMutableMap().apply {
                put("id", JsonPrimitive(id))
                put("doseComponents", JsonArray(components))
                put("roaInfos", JsonArray(roas))
            })
        }
        val companions = root.array("substanceCompanions").map { raw ->
            val companion = raw.jsonObject
            val name = companion.text("substanceName") ?: error("A substance companion has no name.")
            collectMemberships(SubjectId.Catalog(name).key, companion)
            val color = companion.text("color") ?: error("A substance color is missing.")
            if (AdaptiveColor.entries.any { it.name == color }) companion
            else JsonObject(companion.toMutableMap().apply {
                put("color", JsonPrimitive(AdaptiveColor.RED.name)); put("referenceColor", JsonPrimitive(color))
                warnings += "An unfamiliar reference color is shown with a theme fallback. Its original name is preserved in the backup."
            })
        }
        val experiences = root.array("experiences").map { raw ->
            val experience = raw.jsonObject
            JsonObject(experience.toMutableMap().apply {
                put("ingestions", JsonArray(experience.array("ingestions").map { ingestion ->
                    JsonObject(ingestion.jsonObject.toMutableMap().apply {
                        put("customUnitId", mapped(get("customUnitId"))?.let(::JsonPrimitive) ?: JsonNull)
                    })
                }))
            })
        }
        return JsonObject(root.toMutableMap().apply {
            remove("exportSource")
            put("customUnits", JsonArray(units)); put("substanceCompanions", JsonArray(companions))
            put("experiences", JsonArray(experiences))
            put("tags", json.encodeToJsonElement(tags.values.toList()))
            put("tagMemberships", json.encodeToJsonElement(memberships))
        })
    }

    private fun JsonObject.array(key: String): List<JsonElement> = get(key)?.takeUnless { it == JsonNull }?.jsonArray.orEmpty()
    private fun JsonObject.text(key: String): String? = get(key)?.jsonPrimitive?.contentOrNull
    private fun JsonObject.id(key: String): Int = text(key)?.toIntOrNull() ?: error("Invalid or missing $key.")
}
