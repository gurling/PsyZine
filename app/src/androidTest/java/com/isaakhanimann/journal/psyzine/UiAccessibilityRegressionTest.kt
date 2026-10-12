// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.psyzine

import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.isaakhanimann.journal.data.room.experiences.entities.AdaptiveColor
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import com.isaakhanimann.journal.data.substances.classes.roa.*
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.*
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.*
import com.isaakhanimann.journal.ui.tabs.stats.*
import com.isaakhanimann.journal.ui.theme.PsyZineTheme
import org.junit.*
import org.junit.Assert.*
import java.time.Instant

@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
class UiAccessibilityRegressionTest {
    @get:Rule val compose = createComposeRule()
    @Test fun chartHasEquivalentPerSubstancePeriodCounts() {
        compose.setContent { PsyZineTheme { BarChart(listOf(listOf(ColorCount(AdaptiveColor.RED,2,"Synthetic substance")),emptyList()),"12 Oct 2026") } }
        compose.onNode(hasContentDescription("Period 1: Synthetic substance: 2. Period 2: 0",substring=true)).assertExists()
    }
    @Test fun timelineActionsAreBoundedAndKeyboardCursorWorks() {
        fun range(seconds: Float)=DurationRange(seconds,seconds,DurationUnits.SECONDS)
        val duration=RoaDuration(range(60f),range(120f),range(180f),range(240f),null,null)
        val model=AllTimelinesModel(listOf(DataForOneEffectLine("Synthetic substance",AdministrationRoute.ORAL,duration,
            1f,.5f,AdaptiveColor.RED,Instant.EPOCH,null,60.0)),emptyList(),emptyList(),true)
        compose.setContent { PsyZineTheme { AllTimelines(model,false,TimeDisplayOption.REGULAR,Modifier.width(300.dp).height(200.dp)) } }
        val node=compose.onNode(hasContentDescription("Estimated effect timeline",substring=true))
        fun description()=node.fetchSemanticsNode().config[SemanticsProperties.StateDescription]
        val actions=node.fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertEquals(3,actions.size)
        compose.runOnIdle { repeat(100) { assertTrue(actions[1].action()) } }
        val end=description()
        compose.runOnIdle { repeat(100) { actions[1].action() } }
        assertEquals(end,description())
        compose.runOnIdle { repeat(100) { actions[0].action() } }
        val start=description(); assertNotEquals(start,end)
        compose.runOnIdle { repeat(100) { actions[0].action() } }
        assertEquals(start,description())
        compose.runOnIdle { actions[2].action() }
        assertEquals("Cursor hidden",description())
        node.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        node.performKeyInput { pressKey(Key.DirectionRight) }
        assertNotEquals("Cursor hidden",description())
        node.performKeyInput { pressKey(Key.Escape) }
        assertEquals("Cursor hidden",description())
    }
}
