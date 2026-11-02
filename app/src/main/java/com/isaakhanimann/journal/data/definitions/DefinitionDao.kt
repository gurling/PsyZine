// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.data.definitions

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DefinitionDao {
    @RawQuery(observedEntities = [com.isaakhanimann.journal.data.room.experiences.entities.Experience::class,
        com.isaakhanimann.journal.data.room.experiences.entities.Ingestion::class,
        com.isaakhanimann.journal.data.room.experiences.entities.TimedNote::class,
        com.isaakhanimann.journal.data.room.experiences.entities.ShulginRating::class,
        com.isaakhanimann.journal.data.room.experiences.entities.CustomUnit::class,
        com.isaakhanimann.journal.data.room.experiences.entities.CustomSubstance::class,
        DoseComponent::class, CustomRoaInfo::class, SubstanceTag::class, SubstanceTagMembership::class,
        com.isaakhanimann.journal.data.room.attachments.JournalState::class])
    fun observeJournalSearchChanges(query: androidx.sqlite.db.SupportSQLiteQuery): Flow<Int>
    @RawQuery(observedEntities = [com.isaakhanimann.journal.data.room.experiences.entities.Ingestion::class,
        com.isaakhanimann.journal.data.room.experiences.entities.CustomUnit::class,
        com.isaakhanimann.journal.data.room.experiences.entities.CustomSubstance::class,
        DoseComponent::class, CustomRoaInfo::class, SubstanceTag::class, SubstanceTagMembership::class,
        com.isaakhanimann.journal.data.room.attachments.JournalState::class])
    fun observeSubjectBrowserChanges(query: androidx.sqlite.db.SupportSQLiteQuery): Flow<Int>
    @Query("SELECT * FROM Ingestion ORDER BY time DESC, id DESC LIMIT 500")
    suspend fun recentBrowserIngestions(): List<com.isaakhanimann.journal.data.room.experiences.entities.Ingestion>
    @RawQuery(observedEntities = [com.isaakhanimann.journal.data.room.experiences.entities.Ingestion::class,
        com.isaakhanimann.journal.data.room.experiences.entities.CustomUnit::class,
        com.isaakhanimann.journal.data.room.experiences.entities.CustomSubstance::class,
        com.isaakhanimann.journal.data.room.experiences.entities.SubstanceCompanion::class,
        DoseComponent::class, com.isaakhanimann.journal.data.room.attachments.JournalState::class])
    fun observeToleranceChanges(query: androidx.sqlite.db.SupportSQLiteQuery): Flow<Int>
    @Query("SELECT * FROM Ingestion WHERE time > :from AND time <= :cutoff AND (consumerName IS NULL OR consumerName = '') ORDER BY time, id")
    suspend fun toleranceIngestions(from: java.time.Instant, cutoff: java.time.Instant): List<com.isaakhanimann.journal.data.room.experiences.entities.Ingestion>
    @Transaction @Query("SELECT * FROM CustomUnit ORDER BY creationDate, id")
    fun observeDefinitions(): Flow<List<DefinitionWithContent>>
    @Transaction @Query("SELECT * FROM CustomUnit ORDER BY creationDate, id")
    suspend fun definitions(): List<DefinitionWithContent>
    @Transaction @Query("SELECT * FROM CustomUnit WHERE id = :id")
    suspend fun definition(id: Int): DefinitionWithContent?
    @Query("SELECT * FROM DoseComponent ORDER BY creationDate, id")
    fun observeComponents(): Flow<List<DoseComponent>>
    @Query("SELECT * FROM DoseComponent ORDER BY creationDate, id")
    suspend fun components(): List<DoseComponent>
    @Query("SELECT * FROM CustomRoaInfo ORDER BY id")
    fun observeRoaInfos(): Flow<List<CustomRoaInfo>>
    @Query("SELECT * FROM CustomRoaInfo ORDER BY id")
    suspend fun roaInfos(): List<CustomRoaInfo>
    @Insert suspend fun insertComponents(values: List<DoseComponent>)
    @Insert suspend fun insertRoaInfos(values: List<CustomRoaInfo>)
    @Query("DELETE FROM DoseComponent WHERE parentCustomUnitId = :id") suspend fun deleteComponents(id: Int)
    @Query("DELETE FROM CustomRoaInfo WHERE customUnitId = :id") suspend fun deleteRoaInfos(id: Int)
    @Query("SELECT COUNT(*) FROM DoseComponent WHERE childCustomUnitId = :id") suspend fun parentCount(id: Int): Int
    @Query("DELETE FROM DoseComponent") suspend fun deleteAllComponents()
    @Query("DELETE FROM CustomRoaInfo") suspend fun deleteAllRoaInfos()

    @Query("SELECT * FROM SubstanceTag ORDER BY name COLLATE NOCASE, id")
    fun observeTags(): Flow<List<SubstanceTag>>
    @Query("SELECT * FROM SubstanceTag ORDER BY name COLLATE NOCASE, id") suspend fun tags(): List<SubstanceTag>
    @Query("SELECT * FROM SubstanceTagMembership ORDER BY subjectId, tagId")
    fun observeMemberships(): Flow<List<SubstanceTagMembership>>
    @Query("SELECT * FROM SubstanceTagMembership ORDER BY subjectId, tagId") suspend fun memberships(): List<SubstanceTagMembership>
    @Upsert suspend fun saveTag(tag: SubstanceTag)
    @Insert suspend fun insertMemberships(values: List<SubstanceTagMembership>)
    @Query("DELETE FROM SubstanceTagMembership WHERE subjectId = :subjectId") suspend fun deleteMemberships(subjectId: String)
    @Query("DELETE FROM SubstanceTag WHERE id = :id") suspend fun deleteTag(id: String)
    @Query("DELETE FROM SubstanceTagMembership") suspend fun deleteAllMemberships()
    @Query("DELETE FROM SubstanceTag") suspend fun deleteAllTags()
}
