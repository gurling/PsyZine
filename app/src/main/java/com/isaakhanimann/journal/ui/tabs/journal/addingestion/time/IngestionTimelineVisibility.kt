// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.ui.tabs.journal.addingestion.time

import androidx.compose.runtime.Composable

@Composable
fun IngestionTimelineVisibility(isHidden: Boolean, onChangeHidden: (Boolean) -> Unit, enabled: Boolean = true) {
    com.isaakhanimann.journal.ui.utils.TimelineVisibilitySwitch(isHidden, onChangeHidden,
        "Controls this ingestion's curves. Doses, interactions and statistics still include the record. Subject and definition visibility settings still apply.", enabled)
}
