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

@Serializable data class SearchExperienceRoute(val experienceId: Int, val journalGeneration: String)
@Serializable data class SearchEditIngestionRoute(val ingestionId: Int, val journalGeneration: String)
@Serializable data class SearchEditExperienceRoute(val experienceId: Int, val journalGeneration: String)
@Serializable data class SearchAddRatingRoute(val experienceId: Int, val journalGeneration: String)
@Serializable data class SearchEditRatingRoute(val ratingId: Int, val journalGeneration: String)
@Serializable data class SearchAddTimedNoteRoute(val experienceId: Int, val journalGeneration: String)
@Serializable data class SearchEditTimedNoteRoute(val timedNoteId: Int, val experienceId: Int, val journalGeneration: String)
@Serializable data class SearchPhotoViewerRoute(val experienceId: Int, val index: Int, val journalGeneration: String)
@Serializable data class SearchTimelineRoute(val consumerName: String, val experienceId: Int, val journalGeneration: String)

/** Reuse feature screens while retaining the search result's calling stack and generation. */
fun NavGraphBuilder.journalSearchGraph(navController: NavHostController) {
    composableWithTransitions<SearchPhotoViewerRoute> {
        com.isaakhanimann.journal.ui.tabs.journal.experience.photos.PhotoViewerScreen(navigateBack = { navController.popBackStack() })
    }
    composableWithTransitions<SearchTimelineRoute> {
        com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.screen.TimelineScreen(navigateBack = { navController.popBackStack() })
    }
    composableWithTransitions<SearchEditExperienceRoute> { EditExperienceScreen(navigateBack = { navController.popBackStack() }) }
    composableWithTransitions<SearchAddRatingRoute> { AddRatingScreen(navigateBack = { navController.popBackStack() }) }
    composableWithTransitions<SearchEditRatingRoute> { EditRatingScreen(navigateBack = { navController.popBackStack() }) }
    composableWithTransitions<SearchAddTimedNoteRoute> { AddTimedNoteScreen(navigateBack = { navController.popBackStack() }) }
    composableWithTransitions<SearchEditTimedNoteRoute> { EditTimedNoteScreen(navigateBack = { navController.popBackStack() }) }
    composableWithTransitions<SearchEditIngestionRoute> {
        EditIngestionScreen(navigateBack = { navController.popBackStack() },
            navigateToAddIngestion = { navController.navigate(AddIngestionRoute) })
    }
    composableWithTransitions<SearchExperienceRoute> { entry ->
        val route = entry.toRoute<SearchExperienceRoute>()
        val id = route.experienceId
        ExperienceScreen(
            navigateToPhoto = { navController.navigate(SearchPhotoViewerRoute(id, it, route.journalGeneration)) },
            navigateToAddIngestionSearch = { navController.navigate(AddIngestionRoute) },
            navigateToExplainTimeline = { navController.navigate(ExplainTimelineOnSearchTabRoute) },
            navigateToEditExperienceScreen = { navController.navigate(SearchEditExperienceRoute(id, route.journalGeneration)) },
            navigateToIngestionScreen = { navController.navigate(SearchEditIngestionRoute(it, route.journalGeneration)) },
            navigateBack = { navController.popBackStack() },
            navigateToAddRatingScreen = { navController.navigate(SearchAddRatingRoute(id, route.journalGeneration)) },
            navigateToAddTimedNoteScreen = { navController.navigate(SearchAddTimedNoteRoute(id, route.journalGeneration)) },
            navigateToEditRatingScreen = { navController.navigate(SearchEditRatingRoute(it, route.journalGeneration)) },
            navigateToTimelineScreen = { navController.navigate(SearchTimelineRoute(it, id, route.journalGeneration)) },
            navigateToEditTimedNoteScreen = { navController.navigate(SearchEditTimedNoteRoute(it, id, route.journalGeneration)) },
        )
    }
}
