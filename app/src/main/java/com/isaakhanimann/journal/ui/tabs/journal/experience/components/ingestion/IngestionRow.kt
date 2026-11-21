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

package com.isaakhanimann.journal.ui.tabs.journal.experience.components.ingestion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.isaakhanimann.journal.ui.utils.ContentWithSideStripe
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.isaakhanimann.journal.data.room.experiences.entities.AdaptiveColor
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.DotRows
import com.isaakhanimann.journal.ui.tabs.journal.experience.models.IngestionElement

@Preview(showBackground = true)
@Composable
fun IngestionRowPreview(@PreviewParameter(IngestionRowPreviewProvider::class) ingestionElement: IngestionElement) {
    IngestionRow(
        ingestionElement = ingestionElement,
        areDosageDotsHidden = false,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "Fri 07:17",
            style = MaterialTheme.typography.labelMedium
        )
    }
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IngestionRow(
    ingestionElement: IngestionElement,
    areDosageDotsHidden: Boolean,
    modifier: Modifier = Modifier,
    time: @Composable () -> Unit,
) {
    val ingestionWithCompanionAndCustomUnit = ingestionElement.ingestionWithCompanionAndCustomUnit
    val ingestion = ingestionWithCompanionAndCustomUnit.ingestion
    val customUnit = ingestionWithCompanionAndCustomUnit.customUnit
    ContentWithSideStripe(
        stripeWidth = 5.dp,
        modifier = modifier,
        stripe = { VerticalLine(color = ingestionElement.displayColor ?: ingestionWithCompanionAndCustomUnit.substanceCompanion?.color ?: AdaptiveColor.RED) }
    ) {
        time()
        val title = if (customUnit != null && ingestion.substanceName != null) {
            "${ingestion.substanceName}, ${customUnit.name}"
        } else {
            ingestionWithCompanionAndCustomUnit.displayName
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium
        )
        FlowRow(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            val text = buildAnnotatedString {
                append(ingestionWithCompanionAndCustomUnit.doseDescription)
                withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                    if (customUnit == null || customUnit.substanceName == null) {
                        append(" " + ingestion.administrationRoute.displayText.lowercase())
                    } else {
                        ingestionWithCompanionAndCustomUnit.customUnitDose?.calculatedDoseDescription?.let {
                            append(" = $it ${ingestion.administrationRoute.displayText.lowercase()}")
                        } ?: run {
                            append(" = unknown dose ${ingestion.administrationRoute.displayText.lowercase()}")
                        }
                    }
                }
            }
            Text(text = text, style = MaterialTheme.typography.titleSmall)
            val numDots = ingestionElement.numDots
            if (numDots != null && !areDosageDotsHidden) {
                DotRows(numDots = numDots)
            }
        }
        ingestionElement.calculationError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        CalculatedComponents(ingestion.id, ingestionElement.components, areDosageDotsHidden)
        if (ingestion.isHiddenInTimeline) Text("Hidden from timeline", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        ingestion.stomachFullness?.let { fullness ->
            Text("Stomach fullness: ${fullness.text}", style = MaterialTheme.typography.bodySmall)
        }
        val note = ingestion.notes
        if (!note.isNullOrBlank()) {
            Text(text = note, style = MaterialTheme.typography.bodySmall)
        }
    }
}
