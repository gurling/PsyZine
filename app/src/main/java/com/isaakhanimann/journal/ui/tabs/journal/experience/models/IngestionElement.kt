/*
 * Copyright (c) 2023. Isaak Hanimann.
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

package com.isaakhanimann.journal.ui.tabs.journal.experience.models

import com.isaakhanimann.journal.data.room.experiences.relations.IngestionWithCompanionAndCustomUnit
import com.isaakhanimann.journal.data.substances.classes.roa.RoaDuration
import com.isaakhanimann.journal.data.substances.classes.roa.RoaDose
import com.isaakhanimann.journal.data.definitions.ResolvedDose
import com.isaakhanimann.journal.data.room.experiences.entities.AdaptiveColor
import com.isaakhanimann.journal.ui.tabs.search.substance.roa.toReadableString

data class CalculatedComponent(val dose: ResolvedDose, val numDots: Int?, val isShownAsCurve: Boolean,
    val pluralUnits: String? = null) {
    val description: String get() {
        val amount = dose.quantity.amount ?: return "Unknown dose"
        val unit = if (amount == 1.0) dose.units.orEmpty() else pluralUnits ?: dose.units.orEmpty()
        val number = if (dose.quantity.isEstimate) {
            dose.quantity.uncertainty?.let { "${amount.toReadableString()}±${it.toReadableString()}" }
                ?: "~${amount.toReadableString()}"
        } else amount.toReadableString()
        return "$number $unit".trim()
    }
}

data class IngestionElement(
    val ingestionWithCompanionAndCustomUnit: IngestionWithCompanionAndCustomUnit,
    val roaDuration: RoaDuration?,
    val numDots: Int?,
    val doseReference: RoaDose? = null,
    val components: List<CalculatedComponent> = emptyList(),
    val calculationError: String? = null,
    val displayColor: AdaptiveColor? = null,
)
