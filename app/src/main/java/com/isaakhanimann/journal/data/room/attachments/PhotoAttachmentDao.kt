// PsyZine, 2026-10-11. GPL-3.0-or-later.
package com.isaakhanimann.journal.data.room.attachments

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoAttachmentDao {
    @Query("SELECT * FROM photo_attachment WHERE experienceId = :experienceId ORDER BY position, createdAt, id")
    fun observe(experienceId: Int): Flow<List<PhotoAttachment>>
    @Query("SELECT * FROM photo_attachment WHERE experienceId = :experienceId ORDER BY position, createdAt, id")
    suspend fun forExperience(experienceId: Int): List<PhotoAttachment>
    @Query("SELECT * FROM photo_attachment") suspend fun all(): List<PhotoAttachment>
    @Query("SELECT generation FROM journal_state WHERE id = 1") suspend fun generation(): String?
    @Query("SELECT generation FROM journal_state WHERE id = 1") fun observeGeneration(): Flow<String?>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun setGeneration(state: JournalState)
    @Insert suspend fun insert(photos: List<PhotoAttachment>)
    @Query("DELETE FROM photo_attachment WHERE experienceId = :experienceId AND id IN (:ids)")
    suspend fun remove(experienceId: Int, ids: List<String>)
}
