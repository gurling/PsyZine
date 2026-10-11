// PsyZine modifications, 2026-10-11. GPL-3.0-or-later.
// Adapted from PsyLog 0c29a70f, copyright 2025–2026 PsyLog contributors.
package com.isaakhanimann.journal.ui.tabs.journal.experience.timeline

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.isaakhanimann.journal.ui.theme.ThemePalette
import java.time.Instant

/** Bounded local rendering shared by notification and widget; no journal text or photos. */
fun renderTimelineBitmap(model: AllTimelinesModel, widthPx: Int, heightPx: Int, palette: ThemePalette, density: Density, now: Instant): Bitmap {
    require(widthPx in 1..2048 && heightPx in 1..1024 && widthPx.toLong() * heightPx <= 1_048_576) { "Timeline bitmap dimensions exceed budget" }
    require(model.widthInSeconds.isFinite() && model.widthInSeconds > 0) { "Invalid timeline width" }
    val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(android.graphics.Canvas(bitmap)), Size(widthPx.toFloat(), heightPx.toFloat())) {
        drawRect(palette.scheme.surface)
        val pixelsPerSec = size.width / model.widthInSeconds
        model.groupDrawables.forEach { group -> group.drawTimeLine(this, size.height, pixelsPerSec, group.color.getComposeColor(palette), density) }
        drawCurrentTime(model.startTime, model.widthInSeconds, now, pixelsPerSec, palette, size.height)
    }
    return bitmap
}
