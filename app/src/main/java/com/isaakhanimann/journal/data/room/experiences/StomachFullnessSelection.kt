// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.data.room.experiences

import com.isaakhanimann.journal.data.room.experiences.entities.StomachFullness

/** Null is an unrecorded choice, not an assertion that the stomach was empty. */
fun stomachFullnessSelection(value: String?): StomachFullness? = value?.let { saved ->
    StomachFullness.entries.firstOrNull { it.serialized == saved }
        ?: error("The saved stomach-fullness choice is unavailable. Close and reopen this form.")
}
