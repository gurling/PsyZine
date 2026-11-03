// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.data.substances.classes.roa

/** Convert only identical unit labels or explicitly supported mass units. Never infer count/volume conversions. */
fun convertDoseUnits(amount: Double?, from: String?, to: String?): Double? {
    if (amount == null || !amount.isFinite() || amount < 0) return null
    val source = from?.trim()?.replace('μ', 'µ')?.takeIf { it.isNotEmpty() } ?: return null
    val target = to?.trim()?.replace('μ', 'µ')?.takeIf { it.isNotEmpty() } ?: return null
    if (source == target) return amount
    fun factor(unit: String) = when (unit) { "g" -> 1000.0; "mg" -> 1.0; "µg", "ug", "mcg" -> 0.001; else -> null }
    val ratio = (factor(source) ?: return null) / (factor(target) ?: return null)
    return (amount * ratio).takeIf { it.isFinite() && !(amount > 0.0 && it == 0.0) }
}
