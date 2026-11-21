// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.data.room.experiences

import com.isaakhanimann.journal.data.room.experiences.entities.Ingestion
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import java.time.DateTimeException
import java.time.Instant

/** Presentation timing never changes the recorded ingestion or its interval length. */
fun Ingestion.delayedTimelineTime(time: Instant): Instant? {
    val seconds = if (administrationRoute == AdministrationRoute.ORAL)
        ((stomachFullness?.onsetDelayForOralInHours ?: 0.0) * 3600).toLong() else 0L
    return try { time.plusSeconds(seconds) } catch (_: DateTimeException) { null }
}
