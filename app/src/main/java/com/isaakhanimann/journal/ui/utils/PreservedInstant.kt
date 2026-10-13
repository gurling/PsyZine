// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.ui.utils

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** An unchanged form must not reinterpret an existing instant across a DST fold or zone change. */
fun preservedInstant(selected: LocalDateTime, loaded: LocalDateTime?, original: Instant?,
    zone: ZoneId = ZoneId.systemDefault()): Instant =
    if (selected == loaded && original != null) original else selected.atZone(zone).toInstant()
