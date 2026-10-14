/*
 * Copyright (c) 2024. Isaak Hanimann.
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

package com.isaakhanimann.journal.ui.tabs.search.substance.roa.dose

import com.isaakhanimann.journal.ui.theme.LocalPsyZinePalette
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.isaakhanimann.journal.data.substances.classes.roa.DoseClass
import com.isaakhanimann.journal.ui.tabs.search.substance.roa.toReadableString


@Preview
@Composable
fun DoseClassificationPreview() {
    DoseClassificationRow(
        lightMin = 20.0,
        commonMin = 50.0,
        strongMin = 90.0,
        heavyMin = 120.0,
        unit = "mg"
    )
}
@Composable
fun DoseClassificationRow(
    lightMin: Double?,
    commonMin: Double?,
    strongMin: Double?,
    heavyMin: Double?,
    unit: String,
    modifier: Modifier = Modifier
) {
    val numberStyle = MaterialTheme.typography.labelLarge
    val labelStyle = MaterialTheme.typography.labelSmall
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    fun number(value: Double?) = value?.toReadableString() ?: ".."
    fun width(text: String, label: Boolean = false) = measurer.measure(
        text, style = if (label) labelStyle else numberStyle, softWrap = false
    ).size.width
    // Measure with the current font resolver and density, including platform font scaling.
    val requiredWidth = maxOf(width(number(lightMin)), width("thresh  ", true)) +
        maxOf(width("-"), width("light", true)) + width(number(commonMin)) +
        maxOf(width("-"), width("common", true)) + width(number(strongMin)) +
        maxOf(width("-"), width("strong", true)) + width(number(heavyMin)) +
        maxOf(width("-"), width("heavy", true)) + width(unit) + with(density) { 48.dp.toPx() }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        if (constraints.maxWidth >= requiredWidth) {
            CompactDoseClassificationRow(lightMin, commonMin, strongMin, heavyMin, unit)
        } else {
            val palette = LocalPsyZinePalette.current
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(
                    DoseClass.THRESHOLD to "Threshold: <${number(lightMin)}",
                    DoseClass.LIGHT to "Light: ${number(lightMin)}–${number(commonMin)}",
                    DoseClass.COMMON to "Common: ${number(commonMin)}–${number(strongMin)}",
                    DoseClass.STRONG to "Strong: ${number(strongMin)}–${number(heavyMin)}",
                    DoseClass.HEAVY to "Heavy: ≥${number(heavyMin)}"
                ).forEach { (classification, text) ->
                    Text(text, style = numberStyle, color = classification.getComposeColor(palette))
                }
                Text(unit, style = numberStyle)
            }
        }
    }
}

@Composable
private fun CompactDoseClassificationRow(
    lightMin: Double?, commonMin: Double?, strongMin: Double?, heavyMin: Double?, unit: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        val isDarkTheme = LocalPsyZinePalette.current
        val threshColor = DoseClass.THRESHOLD.getComposeColor(isDarkTheme)
        val lightColor = DoseClass.LIGHT.getComposeColor(isDarkTheme)
        val commonColor = DoseClass.COMMON.getComposeColor(isDarkTheme)
        val strongColor = DoseClass.STRONG.getComposeColor(isDarkTheme)
        val heavyColor = DoseClass.HEAVY.getComposeColor(isDarkTheme)
        val labelStyle = MaterialTheme.typography.labelSmall
        val numberStyle = MaterialTheme.typography.labelLarge
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.textBrush(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        threshColor, lightColor
                    )
                )
            )
        ) {
            Text(text = lightMin?.toReadableString() ?: "..", style = numberStyle)
            Text("thresh  ", style = labelStyle)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("-", color = lightColor)
            Text("light", style = labelStyle, color = lightColor)
        }
        Text(
            text = commonMin?.toReadableString() ?: "..",
            style = numberStyle,
            modifier = Modifier.textBrush(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        lightColor, commonColor
                    )
                )
            )
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("-", color = commonColor, style = numberStyle)
            Text("common", style = labelStyle, color = commonColor)
        }
        Text(
            text = strongMin?.toReadableString() ?: "..",
            style = numberStyle,
            modifier = Modifier.textBrush(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        commonColor, strongColor
                    )
                )
            )
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("-", color = strongColor, style = numberStyle)
            Text("strong", style = labelStyle, color = strongColor)
        }
        Text(
            text = heavyMin?.toReadableString() ?: "..",
            style = numberStyle,
            modifier = Modifier.textBrush(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        strongColor, heavyColor
                    )
                )
            )
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("-", color = heavyColor, style = numberStyle)
            Text("heavy", style = labelStyle, color = heavyColor)
        }
        Text(text = unit, style = numberStyle)
    }
}