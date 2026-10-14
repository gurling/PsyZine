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

package com.isaakhanimann.journal.ui.tabs.journal.experience.timeline

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.IntOffset
import kotlin.math.min
import kotlin.math.max
import androidx.compose.ui.platform.testTag
import com.isaakhanimann.journal.ui.tabs.search.substance.roa.toReadableString
import kotlin.math.roundToInt
import com.isaakhanimann.journal.ui.theme.LocalPsyZinePalette
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.isaakhanimann.journal.data.room.experiences.entities.AdaptiveColor
import com.isaakhanimann.journal.data.room.experiences.entities.ShulginRatingOption
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.DataForOneEffectLine
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.TimeDisplayOption
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.getDurationText
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.drawables.AxisDrawable
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.drawables.TimeRangeDrawable
import com.isaakhanimann.journal.ui.utils.getShortTimeText
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit


@Preview(showBackground = true)
@Composable
fun AllTimelinesPreview(
    @PreviewParameter(
        TimelinesPreviewProvider::class,
    ) dataForEffectLines: List<DataForOneEffectLine>
) {
    AllTimelines(
        model = AllTimelinesModel(
            dataForLines = dataForEffectLines,
            dataForRatings = listOf(
                DataForOneRating(
                    time = Instant.now().minus(3, ChronoUnit.HOURS),
                    option = ShulginRatingOption.MINUS
                ),
                DataForOneRating(
                    time = Instant.now().minus(2, ChronoUnit.HOURS),
                    option = ShulginRatingOption.TWO_PLUS
                ),
                DataForOneRating(
                    time = Instant.now().minus(1, ChronoUnit.HOURS),
                    option = ShulginRatingOption.THREE_PLUS
                ),
                DataForOneRating(
                    time = Instant.now().plus(2, ChronoUnit.HOURS),
                    option = ShulginRatingOption.FOUR_PLUS
                )
            ),
            timedNotes = listOf(
                DataForOneTimedNote(
                    time = Instant.now().minus(30, ChronoUnit.MINUTES),
                    color = AdaptiveColor.PURPLE
                ),
                DataForOneTimedNote(
                    time = Instant.now().plus(30, ChronoUnit.MINUTES),
                    color = AdaptiveColor.BLUE
                ),
            ),
            areSubstanceHeightsIndependent = false
        ),
        isShowingCurrentTime = true,
        timeDisplayOption = TimeDisplayOption.REGULAR,
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
    )
}


@Composable
fun AllTimelines(
    model: AllTimelinesModel,
    isShowingCurrentTime: Boolean,
    timeDisplayOption: TimeDisplayOption,
    modifier: Modifier = Modifier,
) {
    val isDarkTheme = LocalPsyZinePalette.current
    val density = LocalDensity.current
    val axisLabelSize = MaterialTheme.typography.labelMedium.fontSize
    val ratingLineColor = MaterialTheme.colorScheme.onSurfaceVariant
    val axisLabelTextPaint = remember(density, isDarkTheme) {
        Paint().apply {
            color =
                isDarkTheme.scheme.onSurface.toArgb()
            textAlign = Paint.Align.CENTER
            textSize = density.run { axisLabelSize.toPx() }
        }
    }

    val ratingSize = MaterialTheme.typography.labelLarge.fontSize
    val ratingTextPaint = remember(density, isDarkTheme) {
        Paint().apply {
            color =
                isDarkTheme.scheme.onSurface.toArgb()
            textAlign = Paint.Align.CENTER
            textSize = density.run { ratingSize.toPx() }
        }
    }
    val currentTime by com.isaakhanimann.journal.ui.utils.rememberUiTime(
        isShowingCurrentTime || timeDisplayOption == TimeDisplayOption.RELATIVE_TO_NOW)
    var dragPoint by remember(model) { mutableStateOf<Offset?>(null) }
    var hoverPoint by remember(model) { mutableStateOf<Offset?>(null) }
    var canvasWidthPx by remember { mutableStateOf(0) }
    var canvasHeightPx by remember { mutableStateOf(0) }
    var visibleLeft by remember { mutableStateOf(0f) }
    var visibleRight by remember { mutableStateOf(0f) }
    var accessibleX by remember(model) { mutableStateOf<Float?>(null) }
    val cursorPoint = dragPoint ?: hoverPoint ?: accessibleX?.let { Offset(it, 0f) }
    fun moveCursor(direction: Int): Boolean {
        if (canvasWidthPx <= 0) return false
        val left = visibleLeft.coerceIn(0f, canvasWidthPx.toFloat())
        val right = (visibleRight.takeIf { it > left } ?: canvasWidthPx.toFloat()).coerceIn(left, canvasWidthPx.toFloat())
        val step = canvasWidthPx * 900f / model.widthInSeconds
        accessibleX = ((accessibleX ?: left) + direction * step).coerceIn(left, right)
        return true
    }
    val accessibleTime = cursorPoint?.let { model.startTime.plusMillis((it.x.coerceIn(0f, canvasWidthPx.toFloat()) / maxOf(1, canvasWidthPx) * model.widthInSeconds * 1000).toLong()) }
    val cursorSummary = accessibleTime?.let { time ->
        cursorTimeLabel(time, model.startTime, currentTime, timeDisplayOption) + ". " + model.cursorModel.at(time).joinToString { value ->
            "${value.ingestion.substanceName}, ${value.ingestion.route.displayText}, " +
                (value.peakFraction?.let { "${(it * 100).roundToInt()} percent of peak" } ?: "peak percentage unavailable") + ", " +
                (value.milligramEquivalent?.let { "${it.toReadableString()} mg equivalent" } ?: "mg equivalent unavailable")
        }
    } ?: "Cursor hidden"

    Box(modifier = modifier.semantics {
        contentDescription = "Estimated effect timeline from ${model.startTime.getShortTimeText()}. " +
            model.cursorModel.at(model.startTime).joinToString { "${it.ingestion.substanceName}, ${it.ingestion.route.displayText}, ${it.ingestion.startTime.getShortTimeText()}" }
        stateDescription = cursorSummary
        customActions = listOf(
            CustomAccessibilityAction("Move cursor earlier by 15 minutes") { moveCursor(-1) },
            CustomAccessibilityAction("Move cursor later by 15 minutes") { moveCursor(1) },
            CustomAccessibilityAction("Hide timeline cursor") { accessibleX = null; true })
    }.onKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) false else when (event.key) {
            Key.DirectionLeft -> moveCursor(-1)
            Key.DirectionRight -> moveCursor(1)
            Key.Escape -> { accessibleX = null; true }
            else -> false
        }
    }.focusable().onSizeChanged {
        canvasWidthPx = it.width
        canvasHeightPx = it.height
    }.onGloballyPositioned {
        val origin = it.localToWindow(Offset.Zero)
        val visible = it.boundsInWindow()
        visibleLeft = (visible.left - origin.x).coerceAtLeast(0f)
        visibleRight = (visible.right - origin.x).coerceAtMost(it.size.width.toFloat())
    }.pointerInput(model) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                when (event.type) {
                    PointerEventType.Enter, PointerEventType.Move -> {
                        if (event.changes.none { it.pressed }) hoverPoint = event.changes.firstOrNull()?.position
                    }
                    PointerEventType.Exit -> hoverPoint = null
                }
            }
        }
    }) {
        Canvas(modifier = Modifier.matchParentSize().testTag("timeline_canvas").pointerInput(model) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                dragPoint = down.position
                try {
                    val drag = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ ->
                        change.consume()
                        dragPoint = change.position
                    }
                    if (drag != null) horizontalDrag(drag.id) { change ->
                        change.consume()
                        dragPoint = change.position
                    }
                } finally {
                    dragPoint = null
                }
            }
        }) {
            val canvasWithLabelsHeight = size.height
            val labelsHeight = axisLabelSize.toPx()
            val canvasWidth = size.width
            val pixelsPerSec = canvasWidth / model.widthInSeconds

            inset(left = 0f, top = 0f, right = 0f, bottom = labelsHeight + strokeWidth) {
                val canvasHeightWithVerticalLine = size.height
                model.groupDrawables.forEach { group ->
                    group.drawTimeLine(
                        drawScope = this,
                        canvasHeight = canvasHeightWithVerticalLine,
                        pixelsPerSec = pixelsPerSec,
                        color = group.color.getComposeColor(isDarkTheme),
                        density = density
                    )
                }
                model.dataForRatings.forEach { dataForOneRating ->
                    drawRating(
                        startTime = model.startTime,
                        ratingTime = dataForOneRating.time,
                        pixelsPerSec = pixelsPerSec,
                        canvasHeightOuter = canvasHeightWithVerticalLine,
                        rating = dataForOneRating.option,
                        textPaint = ratingTextPaint,
                        lineColor = ratingLineColor
                    )
                }
                model.timedNotes.forEach { dataForOneTimedNote ->
                    drawTimedNote(
                        startTime = model.startTime,
                        noteTime = dataForOneTimedNote.time,
                        color = dataForOneTimedNote.color,
                        pixelsPerSec = pixelsPerSec,
                        canvasHeightOuter = canvasHeightWithVerticalLine,
                        isDarkTheme = isDarkTheme
                    )
                }
                if (isShowingCurrentTime) {
                    drawCurrentTime(
                        startTime = model.startTime,
                        timelineWidthInSeconds = model.widthInSeconds,
                        currentTime = currentTime,
                        pixelsPerSec = pixelsPerSec,
                        isDarkTheme = isDarkTheme,
                        canvasHeightOuter = canvasHeightWithVerticalLine,
                    )
                }
                cursorPoint?.let {
                    drawVerticalDragLine(
                        isDarkTheme.scheme.onSurface,
                        Offset(it.x.coerceIn(0f, canvasWidth), it.y),
                        canvasHeightWithVerticalLine
                    )
                }
            }
            drawAxis(
                axisDrawable = model.axisDrawable,
                pixelsPerSec = pixelsPerSec,
                canvasWidth = canvasWidth,
                canvasHeight = canvasWithLabelsHeight,
                textPaint = axisLabelTextPaint
            )
        }
        cursorPoint?.takeIf { canvasWidthPx > 0 }?.let { point ->
            val seconds = point.x.coerceIn(0f, canvasWidthPx.toFloat()) / canvasWidthPx * model.widthInSeconds
            val time = model.startTime.plusMillis((seconds * 1000).toLong())
            val readouts = model.cursorModel.at(time)
            val margin = with(density) { 8.dp.toPx() }
            val right = visibleRight.takeIf { it > visibleLeft } ?: canvasWidthPx.toFloat()
            val panelWidth = min(with(density) { 260.dp.toPx() }, right - visibleLeft - 2 * margin).coerceAtLeast(1f)
            val panelX = (point.x - panelWidth / 2).coerceIn(
                visibleLeft + margin, max(visibleLeft + margin, right - margin - panelWidth))
            Surface(
                modifier = Modifier.offset { IntOffset(panelX.roundToInt(), margin.roundToInt()) }
                    .width(with(density) { panelWidth.toDp() })
                    .heightIn(max = with(density) { (canvasHeightPx - 2 * margin).coerceAtLeast(1f).toDp() })
                    .testTag("timeline_readout"),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(12.dp),
                shadowElevation = 4.dp
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(cursorTimeLabel(time, model.startTime, currentTime, timeDisplayOption),
                        style = MaterialTheme.typography.labelLarge)
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                        readouts.forEach { readout ->
                            Text("${readout.ingestion.substanceName} · ${readout.ingestion.route.displayText} · ${readout.ingestion.startTime.getShortTimeText()}",
                                style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
                            val percentage = readout.peakFraction?.let { "${(it * 100).roundToInt()}% of peak" }
                                ?: "Peak percentage unavailable"
                            val equivalent = readout.milligramEquivalent?.let { "${it.toReadableString()} mg equivalent" }
                                ?: "mg equivalent unavailable"
                            Text("$percentage · $equivalent", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Text("Per-ingestion timeline estimate", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}

private fun cursorTimeLabel(time: Instant, start: Instant, now: Instant, option: TimeDisplayOption): String =
    when (option) {
        TimeDisplayOption.RELATIVE_TO_NOW -> if (time < now) {
            getDurationText(fromInstant = time, toInstant = now) + " ago"
        } else {
            "in " + getDurationText(fromInstant = now, toInstant = time)
        }
        TimeDisplayOption.RELATIVE_TO_START -> getDurationText(fromInstant = start, toInstant = time) + " in"
        TimeDisplayOption.TIME_BETWEEN, TimeDisplayOption.REGULAR -> time.getShortTimeText()
    }


private fun DrawScope.drawVerticalDragLine(
    dragLineColor: Color,
    horizontallyLimitedDragPoint: Offset,
    canvasHeightWithVerticalLine: Float
) {
    drawLine(
        color = dragLineColor,
        start = Offset(x = horizontallyLimitedDragPoint.x, y = canvasHeightWithVerticalLine),
        end = Offset(x = horizontallyLimitedDragPoint.x, y = 0f),
        strokeWidth = 4.dp.toPx(),
        cap = StrokeCap.Round
    )
}

fun DrawScope.drawCurrentTime(
    startTime: Instant,
    timelineWidthInSeconds: Float,
    currentTime: Instant,
    pixelsPerSec: Float,
    isDarkTheme: com.isaakhanimann.journal.ui.theme.ThemePalette,
    canvasHeightOuter: Float,
) {
    val endTime = startTime.plusSeconds(timelineWidthInSeconds.toLong())
    if (startTime.isBefore(currentTime) && endTime.isAfter(currentTime)) {
        val timeStartInSec = Duration.between(startTime, currentTime).seconds
        val timeStartX = timeStartInSec * pixelsPerSec
        val color = isDarkTheme.scheme.onSurface
        drawLine(
            color = color,
            start = Offset(x = timeStartX, y = canvasHeightOuter),
            end = Offset(x = timeStartX, y = 0f),
            strokeWidth = 4.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

fun DrawScope.drawTimeRange(
    timeRangeDrawable: TimeRangeDrawable,
    canvasHeight: Float,
    pixelsPerSec: Float,
    color: Color,
) {
    val startX = timeRangeDrawable.ingestionStartInSeconds * pixelsPerSec
    val endX = timeRangeDrawable.ingestionEndInSeconds * pixelsPerSec
    val minLineHeight = 12.dp.toPx()
    val horizontalLineWidth = 8.dp.toPx()
    val offset = timeRangeDrawable.intersectionCountWithPreviousRanges * horizontalLineWidth
    val horizontalLineHeight = minLineHeight / 2 + offset
    val verticalLineHeight = minLineHeight + offset
    val verticalLineTopY = canvasHeight - verticalLineHeight
    val horizontalLineY = canvasHeight - horizontalLineHeight
    val verticalLineStrokeWidth = 4.dp.toPx()
    drawLine(
        color = color,
        start = Offset(x = startX, y = verticalLineTopY),
        end = Offset(x = startX, y = canvasHeight),
        strokeWidth = verticalLineStrokeWidth,
        cap = StrokeCap.Round
    )
    drawLine(
        color = color,
        start = Offset(x = startX, y = horizontalLineY),
        end = Offset(x = endX, y = horizontalLineY),
        strokeWidth = horizontalLineWidth,
        cap = StrokeCap.Butt
    )
    drawLine(
        color = color,
        start = Offset(x = endX, y = verticalLineTopY),
        end = Offset(x = endX, y = canvasHeight),
        strokeWidth = verticalLineStrokeWidth,
        cap = StrokeCap.Round
    )
}

fun DrawScope.drawRating(
    startTime: Instant,
    ratingTime: Instant,
    pixelsPerSec: Float,
    canvasHeightOuter: Float,
    rating: ShulginRatingOption,
    textPaint: Paint,
    lineColor: Color
) {
    val timeStartInSec = Duration.between(startTime, ratingTime).seconds
    val timeStartX = timeStartInSec * pixelsPerSec
    val lineHeight = textPaint.textSize
    val lines = rating.verticalSign.split("\n")
    val signHeight = lines.size * lineHeight
    val verticalLineHeight = (canvasHeightOuter - 2 * lineHeight - signHeight) / 2
    drawLine(
        color = lineColor,
        start = Offset(x = timeStartX, y = 0f),
        end = Offset(x = timeStartX, y = verticalLineHeight),
        strokeWidth = 3.dp.toPx(),
        cap = StrokeCap.Round
    )
    var y = verticalLineHeight + 1.5f * lineHeight
    drawContext.canvas.nativeCanvas.apply {
        for (line in lines) {
            drawText(
                line,
                timeStartX,
                y,
                textPaint
            )
            y += lineHeight
        }
    }
    drawLine(
        color = lineColor,
        start = Offset(x = timeStartX, y = y),
        end = Offset(x = timeStartX, y = canvasHeightOuter),
        strokeWidth = 4.dp.toPx(),
        cap = StrokeCap.Round
    )
}

fun DrawScope.drawTimedNote(
    startTime: Instant,
    noteTime: Instant,
    pixelsPerSec: Float,
    canvasHeightOuter: Float,
    color: AdaptiveColor,
    isDarkTheme: com.isaakhanimann.journal.ui.theme.ThemePalette
) {
    val timeStartInSec = Duration.between(startTime, noteTime).seconds
    val timeStartX = timeStartInSec * pixelsPerSec
    val strokeWidth = 3.dp.toPx()
    drawLine(
        color = color.getComposeColor(palette = isDarkTheme),
        start = Offset(x = timeStartX, y = 0f),
        end = Offset(x = timeStartX, y = canvasHeightOuter),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(strokeWidth, strokeWidth * 2))
    )
}

fun DrawScope.drawAxis(
    axisDrawable: AxisDrawable,
    pixelsPerSec: Float,
    canvasWidth: Float,
    canvasHeight: Float,
    textPaint: Paint
) {
    val fullHours = axisDrawable.getFullHours(
        pixelsPerSec = pixelsPerSec,
        widthInPixels = canvasWidth
    )
    drawContext.canvas.nativeCanvas.apply {
        fullHours.forEach { fullHour ->
            drawText(
                fullHour.label,
                fullHour.distanceFromStart,
                canvasHeight,
                textPaint
            )
        }
    }
}
