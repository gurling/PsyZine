// PsyZine, 2026-10-11. GPL-3.0-or-later.
package com.isaakhanimann.journal.data.room.attachments
import androidx.room.Entity
import androidx.room.PrimaryKey
/** A database generation prevents old editors from updating reused IDs after a restore. */
@Entity(tableName = "journal_state")
data class JournalState(@PrimaryKey val id: Int = 1, val generation: String)
