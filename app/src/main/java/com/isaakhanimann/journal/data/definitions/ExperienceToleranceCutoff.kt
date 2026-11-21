// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.data.definitions

import java.time.Instant

/** All recorded starts participate in the anchor; event consumer filtering happens afterward. */
fun experienceToleranceCutoff(ingestionStarts: List<Instant>, experienceDate: Instant): Instant =
    ingestionStarts.maxOrNull() ?: experienceDate
