// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.ui.utils

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.State
import java.time.Instant
import com.isaakhanimann.journal.data.preferences.JournalDisplaySettings

val LocalJournalDisplaySettings = staticCompositionLocalOf { JournalDisplaySettings() }
val LocalJournalDateTime = staticCompositionLocalOf<State<Instant>?> { null }
