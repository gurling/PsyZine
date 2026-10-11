// PsyZine modifications, 2026-10-11. GPL-3.0-or-later.
// Adapted from PsyLog 0c29a70f, copyright 2025–2026 PsyLog contributors.
package com.isaakhanimann.journal.ui.tabs.journal.experience.timeline

import com.isaakhanimann.journal.data.substances.classes.Substance
import com.isaakhanimann.journal.data.room.experiences.entities.AdaptiveColor
import com.isaakhanimann.journal.ui.tabs.journal.experience.models.IngestionElement
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.DataForOneEffectLine
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.getStrengthRelativeToCommonDose

object TimelineModelFactory {
    fun effectLines(
            ingestionElements: List<IngestionElement>,
            substances: List<Substance>
        ) =
            ingestionElements.map { oneElement ->
                val horizontalWeight = if (oneElement.numDots == null) {
                    0.5f
                } else if (oneElement.numDots > 4) {
                    1f
                } else {
                    oneElement.numDots.toFloat() / 4f
                }
                val ingestion = oneElement.ingestionWithCompanionAndCustomUnit.ingestion
                DataForOneEffectLine(
                    substanceName = ingestion.substanceName,
                    route = ingestion.administrationRoute,
                    roaDuration = oneElement.roaDuration,
                    height = getStrengthRelativeToCommonDose(
                        ingestion = oneElement.ingestionWithCompanionAndCustomUnit,
                        allIngestions = ingestionElements.map { it.ingestionWithCompanionAndCustomUnit },
                        roaDose = substances.firstOrNull { it.name == ingestion.substanceName }
                            ?.getRoa(ingestion.administrationRoute)?.roaDose
                    ).toFloat().takeIf { it.isFinite() && it > 0f } ?: 1f,
                    horizontalWeight = horizontalWeight,
                    color = oneElement.ingestionWithCompanionAndCustomUnit.substanceCompanion?.color
                        ?: AdaptiveColor.RED,
                    startTime = ingestion.time,
                    endTime = ingestion.endTime,
                    doseInMilligrams = doseToMilligrams(
                        oneElement.ingestionWithCompanionAndCustomUnit.pureDose,
                        oneElement.ingestionWithCompanionAndCustomUnit.originalUnit
                    )
                )
            }

}
