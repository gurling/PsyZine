// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.ui.utils

import androidx.compose.runtime.staticCompositionLocalOf
import com.isaakhanimann.journal.data.preferences.JournalDisplaySettings

val LocalJournalDisplaySettings = staticCompositionLocalOf { JournalDisplaySettings() }
