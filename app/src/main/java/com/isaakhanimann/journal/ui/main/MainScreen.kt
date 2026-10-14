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

package com.isaakhanimann.journal.ui.main

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.isaakhanimann.journal.ui.main.navigation.graphs.journalGraph
import com.isaakhanimann.journal.ui.main.navigation.graphs.saferGraph
import com.isaakhanimann.journal.ui.main.navigation.graphs.searchGraph
import com.isaakhanimann.journal.ui.main.navigation.graphs.settingsGraph
import com.isaakhanimann.journal.ui.main.navigation.graphs.statsGraph
import com.isaakhanimann.journal.ui.main.navigation.JournalTopLevelRoute
import com.isaakhanimann.journal.ui.main.navigation.topLevelRoutes

@Composable
fun MainScreen(
    viewModel: MainScreenViewModel = hiltViewModel(),
    experienceRequest: ExperienceNavigationRequest? = null,
    consumeExperienceRequest: () -> Unit = {}
) {
    val accepted = viewModel.isAcceptedFlow.collectAsStateWithLifecycle().value
    if (accepted == null) return
    if (accepted) {
        val navController = rememberNavController()
        androidx.compose.runtime.LaunchedEffect(experienceRequest?.token) {
            experienceRequest?.let {
                val entry = navController.currentBackStackEntry
                val editingSameEntry = entry?.destination?.hasRoute<com.isaakhanimann.journal.ui.main.navigation.graphs.EditExperienceRoute>() == true &&
                    entry.toRoute<com.isaakhanimann.journal.ui.main.navigation.graphs.EditExperienceRoute>().experienceId == it.experienceId
                // Keep an editor for this entry in focus; otherwise push without discarding drafts.
                if (!editingSameEntry && viewModel.canOpen(it)) {
                    navController.navigate(com.isaakhanimann.journal.ui.main.navigation.graphs.ExperienceRoute(it.experienceId)) { launchSingleTop = true }
                }
                consumeExperienceRequest()
            }
        }
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination
        val items = topLevelRoutes.map { topLevelRoute ->
            val selected = currentDestination?.hierarchy?.any { it.hasRoute(topLevelRoute.route::class) } == true
            PsyZineNavigationItem(
                name = topLevelRoute.name,
                icon = if (selected) topLevelRoute.filledIcon else topLevelRoute.outlinedIcon,
                selected = selected,
                onClick = {
                    if (selected) {
                        val isAlreadyOnTopOfTab =
                            topLevelRoutes.any { it.route == currentDestination?.route }
                        if (!isAlreadyOnTopOfTab) {
                            navController.popBackStack()
                        }
                    } else {
                        navController.navigate(topLevelRoute.route) {
                            // Pop up to the start destination of the graph to
                            // avoid building up a large stack of destinations
                            // on the back stack as users select items
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            // Avoid multiple copies of the same destination when
                            // reselecting the same item
                            launchSingleTop = true
                            // Restore state when reselecting a previously selected item
                            restoreState = true
                        }
                    }
                }
            )
        }
        PsyZineNavigationScaffold(
            layoutType = if (navBackStackEntry?.destination?.hasRoute<com.isaakhanimann.journal.ui.main.navigation.graphs.PhotoViewerRoute>() == true) androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType.None else androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(androidx.compose.material3.adaptive.currentWindowAdaptiveInfo()),
            items = items
        ) {
            NavHost(
                navController,
                startDestination = JournalTopLevelRoute
            ) {
                journalGraph(navController)
                statsGraph(navController)
                searchGraph(navController)
                saferGraph(navController)
                settingsGraph(navController)
            }
        }
    } else {
        AcceptConditionsScreen(onTapAccept = viewModel::accept)
        viewModel.acceptError?.let { message ->
            androidx.compose.material3.AlertDialog(onDismissRequest = viewModel::clearAcceptError,
                title = { Text("Preference could not be saved") }, text = { Text(message) },
                confirmButton = { androidx.compose.material3.TextButton(onClick = viewModel::clearAcceptError) { Text("OK") } })
        }
    }
}
