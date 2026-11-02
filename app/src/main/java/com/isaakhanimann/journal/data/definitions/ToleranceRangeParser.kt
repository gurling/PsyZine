// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.data.definitions

import java.math.BigDecimal
import java.util.Locale

/** Explicit existing-asset ranges only; never infer a value from vague or missing descriptions.
 * Use range midpoints and a fixed 30-day month when parsing numeric metadata.
 */
fun toleranceHours(text: String?): Double? {
    if (text == null || text.length > 64) return null
    val match = toleranceExpression.matchEntire(text) ?: return null
    val lower = match.groupValues[1].toBigDecimalOrNull() ?: return null
    val upper = match.groupValues[2].ifEmpty { match.groupValues[1] }.toBigDecimalOrNull() ?: return null
    if (upper < lower) return null
    val factor = when (match.groupValues[3].lowercase(Locale.ROOT)) {
        "hour" -> 1; "day" -> 24; "week" -> 168; "month" -> 720
        else -> return null
    }
    return lower.add(upper).divide(BigDecimal(2)).multiply(BigDecimal(factor)).toDouble()
        .takeIf { it.isFinite() && it >= 0.0 }
}

private val toleranceExpression = Regex("\\s*(\\d+(?:\\.\\d+)?)(?:\\s*-\\s*(\\d+(?:\\.\\d+)?))?\\s+(hour|day|week|month)s?\\s*", RegexOption.IGNORE_CASE)
