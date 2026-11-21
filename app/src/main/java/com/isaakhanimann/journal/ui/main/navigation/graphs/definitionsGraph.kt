// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.ui.main.navigation.graphs

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import com.isaakhanimann.journal.ui.main.navigation.composableWithTransitions
import com.isaakhanimann.journal.ui.tabs.settings.definitions.DefinitionEditRoute
import com.isaakhanimann.journal.ui.tabs.settings.definitions.DefinitionEditorScreen

fun NavGraphBuilder.definitionsGraph(navController: NavHostController) {
    composableWithTransitions<DefinitionEditRoute> { DefinitionEditorScreen(back = { navController.popBackStack() }) }
}
