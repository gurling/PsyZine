// PsyZine, 2026-10-11. GPL-3.0-or-later.
package com.isaakhanimann.journal.data.room.attachments

import androidx.room.*
import com.isaakhanimann.journal.data.room.experiences.entities.Experience
import java.time.Instant

@Entity(tableName = "photo_attachment", foreignKeys = [ForeignKey(entity = Experience::class,
    parentColumns = ["id"], childColumns = ["experienceId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("experienceId"), Index(value = ["relativePath"], unique = true)])
data class PhotoAttachment(
    @PrimaryKey val id: String,
    val experienceId: Int,
    val relativePath: String,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val byteSize: Long,
    val createdAt: Instant,
    val position: Int,
    val sha256: String
)
