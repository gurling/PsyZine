/*
 * Copyright (c) 2022-2023. Isaak Hanimann.
 * This file is part of PsychonautWiki Journal.
 *
 * PsychonautWiki Journal is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * PsychonautWiki Journal is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with PsychonautWiki Journal.  If not, see https://www.gnu.org/licenses/gpl-3.0.en.html.
 */

package com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.drawables.timelines

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import com.isaakhanimann.journal.data.substances.classes.roa.RoaDuration
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.dottedStroke
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.drawables.TimelineDrawable
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.ingestionDotRadius
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.shapeAlpha

data class TotalTimeline(
    val total: FullDurationRange,
    val totalWeight: Float,
    val percentSmoothness: Float = 0.5f,
    val ingestionTimeRelativeToStartInSeconds: Float,
    override val nonNormalisedHeight: Float,
) : TimelineDrawable {

    override var referenceHeight = 1f

    override val endOfLineRelativeToStartInSeconds: Float =
        ingestionTimeRelativeToStartInSeconds + total.maxInSeconds

    override fun drawTimeLine(
        drawScope: DrawScope,
        canvasHeight: Float,
        pixelsPerSec: Float,
        color: Color,
        density: Density
    ) {
        val normalisedHeight = nonNormalisedHeight / referenceHeight
        val heightInPx = normalisedHeight * canvasHeight
        val top = canvasHeight - heightInPx
        val path = Path().apply {
            val totalMinX = total.minInSeconds * pixelsPerSec
            val totalX = total.interpolateAtValueInSeconds(totalWeight) * pixelsPerSec
            val startX = ingestionTimeRelativeToStartInSeconds * pixelsPerSec
            moveTo(startX, canvasHeight)
            endSmoothLineTo(
                smoothnessBetween0And1 = percentSmoothness,
                startX = startX,
                endX = startX + (totalMinX / 2),
                endY = top
            )
            startSmoothLineTo(
                smoothnessBetween0And1 = percentSmoothness,
                startX = startX + (totalMinX / 2),
                startY = top,
                endX = startX + totalX,
                endY = canvasHeight
            )
        }
        drawScope.drawPath(
            path = path,
            color = color,
            style = density.dottedStroke
        )
        path.close()
        drawScope.drawPath(
            path = path,
            color = color.copy(alpha = shapeAlpha)
        )
        drawScope.drawCircle(
            color = color,
            radius = density.ingestionDotRadius,
            center = Offset(x = ingestionTimeRelativeToStartInSeconds * pixelsPerSec, y = canvasHeight)
        )
    }
}

fun RoaDuration.toTotalTimeline(
    totalWeight: Float,
    ingestionTimeRelativeToStartInSeconds: Float,
    nonNormalisedHeight: Float,
): TotalTimeline? {
    val fullTotal = total?.toFullDurationRange()
    return if (fullTotal != null) {
        TotalTimeline(
            total = fullTotal,
            totalWeight = totalWeight,
            ingestionTimeRelativeToStartInSeconds = ingestionTimeRelativeToStartInSeconds,
            nonNormalisedHeight = nonNormalisedHeight,
        )
    } else {
        null
    }
}

// Adapted from PsyLog abb9323c; GPL-3.0-or-later, copyright PsyLog contributors.
// estimate total duration from whatever phases are available -
// sums all non-null phase durations into a synthetic total range
fun RoaDuration.toEstimatedTotalTimeline(
    totalWeight: Float,
    ingestionTimeRelativeToStartInSeconds: Float,
    nonNormalisedHeight: Float,
): TotalTimeline? {
    val phases = listOfNotNull(onset, comeup, peak, offset, afterglow)
    if (phases.isEmpty()) return null
    if (phases.any { it.minInSec != null && it.maxInSec != null && it.minInSec > it.maxInSec }) return null
    val mins = phases.mapNotNull { it.minInSec }
    val maxs = phases.mapNotNull { it.maxInSec }
    if (mins.isEmpty() || maxs.isEmpty()) return null
    if (mins.any { !it.isFinite() || it < 0 } || maxs.any { !it.isFinite() || it < 0 }) return null
    if (!mins.sum().isFinite() || !maxs.sum().isFinite() || maxs.sum() <= 0 || mins.sum() > maxs.sum()) return null
    val estimatedTotal = FullDurationRange(
        minInSeconds = mins.sum(),
        maxInSeconds = maxs.sum()
    )
    return TotalTimeline(
        total = estimatedTotal,
        totalWeight = totalWeight,
        ingestionTimeRelativeToStartInSeconds = ingestionTimeRelativeToStartInSeconds,
        nonNormalisedHeight = nonNormalisedHeight,
    )
}
