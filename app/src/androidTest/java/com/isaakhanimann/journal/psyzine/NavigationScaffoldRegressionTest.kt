// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.psyzine

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.*
import com.isaakhanimann.journal.ui.main.PsyZineNavigationItem
import com.isaakhanimann.journal.ui.main.PsyZineNavigationScaffold
import com.isaakhanimann.journal.ui.theme.PsyZineTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class NavigationScaffoldRegressionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun hidingAndResizingNavigationPreservesContentAndSystemBack() {
        var layout by mutableStateOf(NavigationSuiteType.NavigationBar)
        var creations = 0
        var disposals = 0
        var selectedTab = ""
        lateinit var navigate: (String) -> Unit
        compose.setContent {
            PsyZineTheme {
                val controller = rememberNavController()
                navigate = { controller.navigate(it) }
                Box(Modifier.width(240.dp)) {
                    PsyZineNavigationScaffold(layout, listOf("Journal", "Stats", "Drugs", "Safer", "Settings").map {
                        PsyZineNavigationItem(it, Icons.Default.Star, false, { selectedTab = it })
                    }) {
                        remember { creations++ }
                        DisposableEffect(Unit) { onDispose { disposals++ } }
                        NavHost(controller, startDestination = "journal") {
                            composable("journal") { Text("Journal destination") }
                            composable("entry") { Text("Entry destination") }
                            composable("photo") { Text("Photo destination") }
                        }
                    }
                }
            }
        }
        compose.runOnIdle { navigate("entry") }
        compose.onNodeWithText("Entry destination").assertIsDisplayed()
        compose.runOnIdle { navigate("photo"); layout = NavigationSuiteType.None }
        compose.onNodeWithText("Photo destination").assertIsDisplayed()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Entry destination").assertIsDisplayed()
        compose.runOnIdle { layout = NavigationSuiteType.NavigationBar }
        compose.waitForIdle()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Journal destination").assertIsDisplayed()
        for (type in listOf(NavigationSuiteType.NavigationRail, NavigationSuiteType.NavigationBar)) {
            compose.runOnIdle { layout = type }
            compose.onNodeWithText("Journal destination").assertIsDisplayed()
        }
        compose.onNode(isSelectable() and hasText("Settings")).performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals("Settings tab is unreachable", "Settings", selectedTab)
            assertEquals("Navigation content was recreated", 1, creations)
            assertEquals("Navigation content was disposed", 0, disposals)
        }
    }
}
