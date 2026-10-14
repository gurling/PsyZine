// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.data.room

import com.isaakhanimann.journal.data.room.experiences.entities.CustomUnit
import java.security.MessageDigest

enum class CustomUnitField { NAME, UNIT, ORIGINAL_UNIT, DOSE, UNCERTAINTY }

/** One set of rules for editor fields and the ingestion chooser. Blank optional numbers are unknown. */
fun numericInputError(text: String, label: String, required: Boolean = false): String? {
    if (text.isBlank()) return if (required) "Enter an amount, or choose Log unknown dose." else null
    val number = text.replace(',', '.').toDoubleOrNull() ?: return "Enter a number, for example 0.5."
    if (!number.isFinite()) return "Enter a number that is not too large; NaN and Infinity are not valid."
    if (number < 0) return "$label cannot be negative."
    return null
}

fun customUnitInputErrors(name: String, unit: String, originalUnit: String, dose: String,
    uncertainty: String, validateUncertainty: Boolean): Map<CustomUnitField, String> = buildMap {
    if (name.isBlank()) put(CustomUnitField.NAME, "Enter a name for this custom unit.")
    if (unit.isBlank()) put(CustomUnitField.UNIT, "Enter a singular unit label, for example capsule.")
    numericInputError(dose, "Dose")?.let { put(CustomUnitField.DOSE, it) }
    if (dose.isNotBlank() && originalUnit.isBlank()) put(CustomUnitField.ORIGINAL_UNIT, "Enter the measurement unit, for example mg.")
    if (validateUncertainty) numericInputError(uncertainty, "Uncertainty")?.let { put(CustomUnitField.UNCERTAINTY, it) }
}

fun suggestedUnitPlural(unit: String): String = when {
    unit.isBlank() -> ""
    unit == "mg" || unit == "g" || unit.lowercase() == "ml" || unit.endsWith('s') -> unit
    else -> unit + "s"
}

/** Length-prefixed values make labels unambiguous; raw bits preserve exact numeric identity. Notes are not a definition change. */
fun CustomUnit.definitionFingerprint(): String {
    val values = listOf(substanceName, administrationRoute.name, name, unit, unitPlural,
        originalUnit, dose?.toRawBits()?.toString(), estimatedDoseStandardDeviation?.toRawBits()?.toString(),
        isEstimate.toString(), isArchived.toString())
    val bytes = values.joinToString("") { if (it == null) "-1:" else "${it.length}:$it" }.toByteArray(Charsets.UTF_8)
    return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}

open class CustomUnitProblem(message: String, val requiresReopen: Boolean = true) : IllegalStateException(message)
class UsedCustomUnit(val count: Int) : CustomUnitProblem("This unit is used in $count ${if (count == 1) "ingestion" else "ingestions"}. Archive it to hide it from new entries and keep past entries intact.")
class StaleJournalSession : IllegalStateException("The journal was restored or deleted. Close this screen and reopen it.")

fun customUnitFailure(error: Exception, action: String): String = when (error) {
    is CustomUnitProblem -> error.message!!
    is StaleJournalSession -> error.message!!
    is android.database.sqlite.SQLiteFullException -> "There is not enough storage. Free up space and try again. Your changes are still here."
    else -> "Could not $action. Your changes are still here. Try again."
}

/** Optimistic edit token also covers notes and record identity, without storing an entity in SavedState. */
fun CustomUnit.editorFingerprint(): String = MessageDigest.getInstance("SHA-256")
    .digest((definitionFingerprint() + ":" + id + ":" + creationDate + ":" + note).toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it) }
