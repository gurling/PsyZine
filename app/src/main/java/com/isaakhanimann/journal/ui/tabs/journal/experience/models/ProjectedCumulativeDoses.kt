// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.ui.tabs.journal.experience.models

import com.isaakhanimann.journal.data.definitions.*
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import com.isaakhanimann.journal.data.substances.classes.Substance
import com.isaakhanimann.journal.data.substances.classes.roa.convertDoseUnits
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.PresentedIngestion

/** Count stored IDs, while summing every accounting contribution, including hidden components. */
fun projectedCumulativeDoses(records: List<PresentedIngestion>, graph: CustomDefinitionGraph,
    catalog: Map<String, Substance>, checkCancelled: () -> Unit = {}): CumulativeProjection {
    data class Contribution(val recordId: Int, val route: AdministrationRoute, val dose: ResolvedDose)
    val rows = records.flatMap { record ->
        checkCancelled()
        val ingestion = record.element.ingestionWithCompanionAndCustomUnit.ingestion
        record.contributions.map { checkCancelled(); Contribution(ingestion.id, ingestion.administrationRoute, it) }
    }
    val notices = linkedSetOf<String>()
    val totals = rows.groupBy { checkCancelled(); it.dose.subject }.mapNotNull { (subject, contributions) ->
        checkCancelled()
        val routes = contributions.groupBy { it.route to it.dose.units }.mapNotNull route@{ (key, values) ->
            val (route, unit) = key
            val repeated = values.map { it.recordId }.distinct().size > 1
            if (unit.isNullOrBlank() || values.any { it.dose.quantity.amount == null }) {
                if (repeated) notices += "Some totals are unavailable because a recorded amount or measurement unit is unknown."
                return@route null
            }
            val amount = values.sumOf { checkCancelled(); it.dose.quantity.amount!! }
            // Retain the established cumulative uncertainty convention.
            val uncertainty = values.sumOf { checkCancelled(); it.dose.quantity.uncertainty ?: 0.0 }
            if (!amount.isFinite() || !uncertainty.isFinite()) {
                if (repeated) notices += "A total is too large to calculate. The original amounts remain saved."
                return@route null
            }
            val reference = when (subject) {
                is SubjectId.Catalog -> catalog[subject.name]?.getRoa(route)?.roaDose
                is SubjectId.Definition -> graph.ownRoutes[subject.id to route]?.doseReference(unit)
                is SubjectId.Local -> null
            }
            val classifiedAmount = reference?.let { convertDoseUnits(amount, unit, it.units) }
            CumulativeRouteAndDose(amount, unit, values.any { it.dose.quantity.isEstimate }, uncertainty.takeIf { it > 0.0 },
                reference?.getNumDots(classifiedAmount), route, repeated)
        }
        if (routes.none { it.hasMoreThanOneIngestion }) null
        else CumulativeDose(contributions.first().dose.name, routes, subject.key)
    }
    return CumulativeProjection(totals, notices.toList())
}
