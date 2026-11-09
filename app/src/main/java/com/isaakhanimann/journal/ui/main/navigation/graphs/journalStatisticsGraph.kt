// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.ui.main.navigation.graphs

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.toRoute
import com.isaakhanimann.journal.ui.main.navigation.composableWithTransitions
import com.isaakhanimann.journal.ui.tabs.journal.experience.ExperienceScreen
import com.isaakhanimann.journal.ui.tabs.journal.experience.editingestion.EditIngestionScreen
import com.isaakhanimann.journal.ui.tabs.journal.experience.edit.EditExperienceScreen
import com.isaakhanimann.journal.ui.tabs.journal.experience.rating.add.AddRatingScreen
import com.isaakhanimann.journal.ui.tabs.journal.experience.rating.edit.EditRatingScreen
import com.isaakhanimann.journal.ui.tabs.journal.experience.timednote.add.AddTimedNoteScreen
import com.isaakhanimann.journal.ui.tabs.journal.experience.timednote.edit.EditTimedNoteScreen
import kotlinx.serialization.Serializable

@Serializable data class StatisticsExperienceRoute(val experienceId: Int, val journalGeneration: String)
@Serializable data class StatisticsEditIngestionRoute(val ingestionId: Int, val journalGeneration: String)
@Serializable data class StatisticsEditExperienceRoute(val experienceId: Int, val journalGeneration: String)
@Serializable data class StatisticsAddRatingRoute(val experienceId: Int, val journalGeneration: String)
@Serializable data class StatisticsEditRatingRoute(val ratingId: Int, val journalGeneration: String)
@Serializable data class StatisticsAddTimedNoteRoute(val experienceId: Int, val journalGeneration: String)
@Serializable data class StatisticsEditTimedNoteRoute(val timedNoteId: Int, val experienceId: Int, val journalGeneration: String)
@Serializable data class StatisticsPhotoViewerRoute(val experienceId: Int, val index: Int, val journalGeneration: String)
@Serializable data class StatisticsTimelineRoute(val consumerName: String, val experienceId: Int, val journalGeneration: String)

/** Reuse feature screens while retaining the statistics result's calling stack and generation. */
fun NavGraphBuilder.journalStatisticsGraph(navController: NavHostController) {
    composableWithTransitions<ExplainTimelineOnStatisticsTabRoute> {
        com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.ExplainTimelineScreen()
    }
    composableWithTransitions<StatisticsPhotoViewerRoute> {
        com.isaakhanimann.journal.ui.tabs.journal.experience.photos.PhotoViewerScreen(navigateBack = { navController.popBackStack() })
    }
    composableWithTransitions<StatisticsTimelineRoute> {
        com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.screen.TimelineScreen(navigateBack = { navController.popBackStack() })
    }
    composableWithTransitions<StatisticsEditExperienceRoute> { EditExperienceScreen(navigateBack = { navController.popBackStack() }) }
    composableWithTransitions<StatisticsAddRatingRoute> { AddRatingScreen(navigateBack = { navController.popBackStack() }) }
    composableWithTransitions<StatisticsEditRatingRoute> { EditRatingScreen(navigateBack = { navController.popBackStack() }) }
    composableWithTransitions<StatisticsAddTimedNoteRoute> { AddTimedNoteScreen(navigateBack = { navController.popBackStack() }) }
    composableWithTransitions<StatisticsEditTimedNoteRoute> { EditTimedNoteScreen(navigateBack = { navController.popBackStack() }) }
    composableWithTransitions<StatisticsEditIngestionRoute> {
        EditIngestionScreen(navigateBack = { navController.popBackStack() },
            navigateToAddIngestion = { navController.navigate(AddIngestionRoute) })
    }
    composableWithTransitions<StatisticsExperienceRoute> { entry ->
        val route = entry.toRoute<StatisticsExperienceRoute>()
        val id = route.experienceId
        ExperienceScreen(
            navigateToPhoto = { navController.navigate(StatisticsPhotoViewerRoute(id, it, route.journalGeneration)) },
            navigateToAddIngestionSearch = { navController.navigate(AddIngestionRoute) },
            navigateToExplainTimeline = { navController.navigate(ExplainTimelineOnStatisticsTabRoute) },
            navigateToEditExperienceScreen = { navController.navigate(StatisticsEditExperienceRoute(id, route.journalGeneration)) },
            navigateToIngestionScreen = { navController.navigate(StatisticsEditIngestionRoute(it, route.journalGeneration)) },
            navigateBack = { navController.popBackStack() },
            navigateToAddRatingScreen = { navController.navigate(StatisticsAddRatingRoute(id, route.journalGeneration)) },
            navigateToAddTimedNoteScreen = { navController.navigate(StatisticsAddTimedNoteRoute(id, route.journalGeneration)) },
            navigateToEditRatingScreen = { navController.navigate(StatisticsEditRatingRoute(it, route.journalGeneration)) },
            navigateToTimelineScreen = { navController.navigate(StatisticsTimelineRoute(it, id, route.journalGeneration)) },
            navigateToEditTimedNoteScreen = { navController.navigate(StatisticsEditTimedNoteRoute(it, id, route.journalGeneration)) },
        )
    }
}

@Serializable object ExplainTimelineOnStatisticsTabRoute
