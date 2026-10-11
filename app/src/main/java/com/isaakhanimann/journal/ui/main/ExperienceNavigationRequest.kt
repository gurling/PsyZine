// PsyZine, 2026-10-11. GPL-3.0-or-later.
// Adapted from PsyLog 0c29a70f, copyright PsyLog contributors.
package com.isaakhanimann.journal.ui.main

data class ExperienceNavigationRequest(val experienceId: Int, val generation: String, val token: String = java.util.UUID.randomUUID().toString())
