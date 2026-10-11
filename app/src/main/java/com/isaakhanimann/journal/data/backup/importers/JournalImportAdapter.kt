// PsyZine, 2026-10-11. GPL-3.0-or-later.
// Android Journal detection adapted from PsyLog ca4ab9d0, copyright PsyLog contributors.
package com.isaakhanimann.journal.data.backup.importers

import com.isaakhanimann.journal.data.backup.BackupValidation
import com.isaakhanimann.journal.ui.tabs.settings.JournalExport
import kotlinx.serialization.json.*

class JournalImportException(val code: String, message: String) : IllegalArgumentException(message)
data class ImportedJournal(val journal: JournalExport, val warnings: List<String> = emptyList())

/** Normalize a source export before touching Room or attachment files. */
object JournalImportAdapter {
    private val json = Json { ignoreUnknownKeys = true }
    fun decode(text: String, referenceSubstanceNames: Set<String> = emptySet()): ImportedJournal {
        try {
            val root = json.parseToJsonElement(text).jsonObject
            require(root.keys.any { it in setOf("experiences", "substanceCompanions", "customSubstances", "customUnits") }) { "This is not a journal export" }
            val warnings = mutableListOf<String>()
            val normalized = when (root["exportSource"]?.jsonPrimitive?.contentOrNull) {
                null, "" -> root
                "Android Journal" -> convertAndroidJournal(root, warnings, referenceSubstanceNames)
                else -> throw JournalImportException("unsupported_source", "This export source is not supported. Use legacy JSON or a PsyZine backup.")
            }
            val journal = json.decodeFromJsonElement<JournalExport>(normalized)
            BackupValidation.journal(journal)
            return ImportedJournal(journal, warnings)
        } catch (e: JournalImportException) { throw e }
        catch (e: Exception) { throw JournalImportException("invalid_export", "The journal export could not be decoded or validated. ${e.message?.take(500) ?: "Invalid data"}") }
    }

    private fun convertAndroidJournal(root: JsonObject, warnings: MutableList<String>, referenceNames: Set<String>): JsonObject {
        val units = root["customUnits"]?.jsonArray.orEmpty().map { it.jsonObject }
        require(units.size <= 100000) { "Too many custom units" }
        val byId = units.associateBy { it.string("id")?.takeIf { id -> id.isNotBlank() } ?: error("Custom unit has no ID") }
        require(byId.size == units.size) { "Duplicate custom unit IDs" }
        val ids = byId.keys.withIndex().associate { it.value to it.index + 1 }
        val normalizedUnits = mutableMapOf<String, JsonObject>()
        val customSubstances = root["customSubstances"]?.jsonArray.orEmpty().map { it.jsonObject }.toMutableList()
        val visiting = mutableSetOf<String>()
        fun customSubstance(name: String, unit: String, note: String): String {
            val existing = customSubstances.firstOrNull { it.string("name") == name }
            if (name !in referenceNames && existing?.string("units") == unit && existing.string("description") == note) return name
            val base = if (name in referenceNames) "$name (imported product)" else name
            var available = base
            var suffix = 2
            while (available in referenceNames || customSubstances.any { it.string("name") == available }) available = "$base (imported ${suffix++})"
            customSubstances += buildJsonObject { put("name", available); put("units", unit); put("description", note) }
            return available
        }
        fun validateComponent(component: JsonObject) {
            require((component.string("substanceName") != null) xor (component.string("customUnitId") != null)) { "Ingredient must reference one substance or custom unit" }
            for (key in listOf("dose", "estimatedDoseStandardDeviation")) {
                val value = component[key]
                if (value != null && value != JsonNull) {
                    val number = value.jsonPrimitive.doubleOrNull
                    require(number != null && number.isFinite() && number >= 0) { "Invalid ingredient $key" }
                }
            }
        }
        fun resolve(id: String, depth: Int = 0): JsonObject {
            normalizedUnits[id]?.let { return it }
            require(depth <= 64 && visiting.add(id)) { "Cyclic or deeply nested custom unit reference" }
            val source = byId[id] ?: error("Missing custom unit reference")
            val name = source.string("name")?.takeIf { it.isNotBlank() } ?: error("Custom unit has no name")
            val unit = source.string("unit")?.takeIf { it.isNotBlank() } ?: error("Custom unit has no unit")
            val components = source["doseComponents"]?.jsonArray.orEmpty().map { it.jsonObject }
            components.forEach(::validateComponent)
            val notes = source.string("note") ?: ""
            // Multiple ingredients cannot be represented as a single substance dose. Keep the
            // product as a custom substance and retain every source component in its definition.
            val definition = if (components.size > 1) {
                components.forEach { component ->
                    component.string("customUnitId")?.let { resolve(it, depth + 1) }
                    require(component.string("substanceName") != null || component.string("customUnitId") != null) { "Ingredient has no substance or unit" }
                }
                val description = "$notes\nImported composition: ${source}".trim()
                val product = customSubstance(name, unit, description)
                warnings += "A multi-ingredient product was retained as a custom substance, with its complete composition. No single-ingredient dose was inferred."
                buildJsonObject { put("substanceName", product); put("dose", 1.0); put("originalUnit", unit) }
            } else if (components.size == 1) {
                val component = components.single()
                val nested = component.string("customUnitId")?.let { resolve(it, depth + 1) }
                val substance = component.string("substanceName") ?: nested?.string("substanceName") ?: error("Ingredient has no substance")
                val quantity = component["dose"]?.jsonPrimitive?.doubleOrNull
                require(quantity == null || quantity.isFinite() && quantity >= 0) { "Invalid ingredient dose" }
                val nestedDose = nested?.get("dose")?.jsonPrimitive?.doubleOrNull
                val originalUnit = nested?.string("originalUnit") ?: component.string("units") ?: component.string("unit") ?: source.string("originalUnit")
                val dose = if (nested != null) quantity?.let { q -> nestedDose?.times(q) } else if (originalUnit != null) quantity else null
                if (quantity != null && originalUnit == null) warnings += "An ingredient's dose units were missing. Its source definition was retained and the effective dose left unknown."
                val uncertainty = component["estimatedDoseStandardDeviation"]?.jsonPrimitive?.doubleOrNull
                val nestedUncertainty = nested?.get("estimatedDoseStandardDeviation")?.jsonPrimitive?.doubleOrNull
                // Retain uncertainty without assuming statistically independent ingredients.
                val combinedUncertainty = if (nested == null) uncertainty else if (dose == null) null
                    else if (uncertainty == null && nestedUncertainty == null) null
                    else (uncertainty ?: 0.0) * (nestedDose ?: 0.0) + (nestedUncertainty ?: 0.0) * (quantity ?: 0.0)
                val estimated = component["isEstimate"]?.jsonPrimitive?.booleanOrNull == true ||
                    component["isDoseAnEstimate"]?.jsonPrimitive?.booleanOrNull == true || nested?.get("isEstimate")?.jsonPrimitive?.booleanOrNull == true || combinedUncertainty != null
                buildJsonObject {
                    put("substanceName", substance)
                    put("dose", dose?.let(::JsonPrimitive) ?: JsonNull)
                    put("originalUnit", originalUnit ?: unit)
                    put("estimatedDoseStandardDeviation", combinedUncertainty?.let(::JsonPrimitive) ?: JsonNull)
                    put("isEstimate", estimated)
                }
            } else if (source.string("substanceName") != null) {
                source
            } else {
                val product = customSubstance(name, unit, notes)
                buildJsonObject { put("substanceName", product); put("dose", 1.0); put("originalUnit", unit) }
            }
            val normalized = JsonObject(source.toMutableMap().apply {
                remove("doseComponents")
                putAll(definition)
                put("id", JsonPrimitive(ids.getValue(id)))
                put("name", JsonPrimitive(name)); put("unit", JsonPrimitive(unit))
                putIfAbsent("unitPlural", JsonNull)
                putIfAbsent("administrationRoute", JsonPrimitive("ORAL"))
                putIfAbsent("isEstimate", JsonPrimitive(false)); putIfAbsent("isArchived", JsonPrimitive(false))
                // Keep complete source provenance, including information the legacy unit model lacks.
                put("note", JsonPrimitive(if (components.isEmpty()) notes else "$notes\nImported definition: $source".trim()))
            })
            visiting.remove(id); normalizedUnits[id] = normalized
            return normalized
        }
        byId.keys.forEach { resolve(it) }
        val experiences = root["experiences"]?.jsonArray.orEmpty().map { element ->
            val experience = element.jsonObject
            JsonObject(experience.toMutableMap().apply {
                put("ingestions", JsonArray(experience["ingestions"]?.jsonArray.orEmpty().map { ingestionElement ->
                    val ingestion = ingestionElement.jsonObject
                    val unitId = ingestion.string("customUnitId")
                    val customUnit = unitId?.let { normalizedUnits[it] ?: error("Ingestion references a missing custom unit") }
                    JsonObject(ingestion.toMutableMap().apply {
                        if (customUnit != null) {
                            put("customUnitId", JsonPrimitive(ids.getValue(unitId!!)))
                            if (ingestion.string("substanceName") == null || customUnit.string("substanceName") in customSubstances.map { it.string("name") }) {
                                put("substanceName", customUnit.getValue("substanceName"))
                            }
                        }
                    })
                }))
            })
        }
        return JsonObject(root.toMutableMap().apply {
            remove("exportSource")
            put("customUnits", JsonArray(byId.keys.map { normalizedUnits.getValue(it) }))
            put("customSubstances", JsonArray(customSubstances))
            put("experiences", JsonArray(experiences))
        })
    }
    private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
}
