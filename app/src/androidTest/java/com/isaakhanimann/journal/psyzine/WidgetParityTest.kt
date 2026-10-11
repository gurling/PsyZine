// PsyZine, 2026-10-11. GPL-3.0-or-later.
package com.isaakhanimann.journal.psyzine

import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasText
import com.isaakhanimann.journal.data.active.ActiveIngestion
import com.isaakhanimann.journal.data.room.experiences.entities.AdaptiveColor
import com.isaakhanimann.journal.data.substances.classes.roa.IngestionPhase
import com.isaakhanimann.journal.widget.ActiveIngestionWidgetRow
import org.junit.Test

class WidgetParityTest {
    @Test fun rowIncludesDoseRouteAndEstimatedPhase() = runGlanceAppWidgetUnitTest {
        provideComposable { ActiveIngestionWidgetRow(ActiveIngestion("Caffeine", "100 mg", "oral", IngestionPhase.PEAK, AdaptiveColor.RED)) }
        onNode(hasText("Caffeine 100 mg oral")).assertExists()
        onNode(hasText("peak")).assertExists()
    }
}
