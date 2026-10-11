// PsyZine, 2026-10-11. GPL-3.0-or-later.
// Adapted from PsyLog 0c29a70f, copyright PsyLog contributors.
package com.isaakhanimann.journal.widget

import com.isaakhanimann.journal.data.active.ActiveExperienceRepository
import com.isaakhanimann.journal.data.preferences.*
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint @InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun activeExperienceRepository(): ActiveExperienceRepository
    fun appearanceRepository(): AppearanceRepository
    fun presentationPreferences(): PresentationPreferences
}
