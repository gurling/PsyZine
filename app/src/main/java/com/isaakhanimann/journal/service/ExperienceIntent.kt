// PsyZine, 2026-10-11. GPL-3.0-or-later.
// PsyLog 0c29a70f feature port, copyright 2025–2026 PsyLog contributors.
package com.isaakhanimann.journal.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.isaakhanimann.journal.MainActivity

const val EXTRA_EXPERIENCE_ID = "psyzine.experience_id"
const val EXTRA_JOURNAL_GENERATION = "psyzine.journal_generation"
fun experienceIntent(context: Context, id: Int, generation: String) = Intent(context, MainActivity::class.java).apply {
    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
    data = Uri.parse("psyzine://experience/$id?generation=${generation ?: "initial"}")
    putExtra(EXTRA_EXPERIENCE_ID, id)
    putExtra(EXTRA_JOURNAL_GENERATION, generation)
}
