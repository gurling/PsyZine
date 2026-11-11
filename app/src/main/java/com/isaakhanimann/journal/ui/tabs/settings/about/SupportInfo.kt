// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.ui.tabs.settings.about

import java.util.Locale

/** An explicit allowlist. Never accept journal data, logs, paths, URLs or device identifiers. */
data class SupportInfo(
    val versionName: String,
    val versionCode: Long,
    val applicationId: String,
    val flavor: String,
    val buildType: String,
    val androidApi: Int,
    val fontScale: Float,
    val densityDpi: Int,
) {
    fun text(): String {
        fun token(value: String): String = value.takeIf {
            it.length in 1..80 && it.matches(Regex("[A-Za-z0-9._+\\-]+"))
        } ?: "unavailable"
        val scale = if (fontScale.isFinite() && fontScale > 0)
            String.format(Locale.ROOT, "%.0f%%", fontScale.toDouble() * 100) else "unavailable"
        return listOf(
            "PsyZine support info",
            "Version: ${token(versionName)}",
            "Version code: ${versionCode.takeIf { it > 0 } ?: "unavailable"}",
            "Package: ${token(applicationId)}",
            "Flavor: ${token(flavor)}",
            "Build type: ${token(buildType)}",
            "Android API: ${androidApi.takeIf { it > 0 } ?: "unavailable"}",
            "Font scale: $scale",
            "Display density: ${densityDpi.takeIf { it > 0 }?.let { "$it dpi" } ?: "unavailable"}",
        ).joinToString("\n")
    }
}
