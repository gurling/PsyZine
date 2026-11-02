// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.ui.utils

import androidx.lifecycle.SavedStateHandle

/** Shared feature models accept the same argument keys from typed contextual routes. */
fun SavedStateHandle.journalRecordId(key: String): Int =
    checkNotNull(get<Int>(key)) { "This record reference is missing. Close this screen and choose the record again." }
