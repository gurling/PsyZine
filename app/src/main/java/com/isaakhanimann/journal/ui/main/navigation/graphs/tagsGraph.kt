// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.ui.main.navigation.graphs

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.toRoute
import com.isaakhanimann.journal.ui.main.navigation.composableWithTransitions
import com.isaakhanimann.journal.ui.tabs.settings.tags.*

/** Shared destinations retain the calling tab/subject beneath them. */
fun NavGraphBuilder.tagsGraph(navController: NavHostController) {
    composableWithTransitions<TagsRoute> { entry ->
        val origin = entry.toRoute<TagsRoute>().origin
        TagLibraryScreen(back = { navController.popBackStack() }, edit = { navController.navigate(TagEditRoute(it, origin)) },
            assign = { navController.navigate(TagSubjectsRoute(it, origin)) })
    }
    composableWithTransitions<TagEditRoute> { TagEditorScreen(back = { navController.popBackStack() }) }
    composableWithTransitions<SubjectTagsRoute> { entry ->
        SubjectTagsScreen(back = { navController.popBackStack() }, manageTags = { navController.navigate(TagsRoute(entry.toRoute<SubjectTagsRoute>().origin)) })
    }
    composableWithTransitions<TagSubjectsRoute> { TagSubjectsScreen(back = { navController.popBackStack() }) }
}
