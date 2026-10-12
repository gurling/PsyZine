// PsyZine, 2026-10-11. GPL-3.0-or-later.
package com.isaakhanimann.journal.psyzine

import androidx.room.Room
import androidx.room.withTransaction
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.isaakhanimann.journal.data.room.AppDatabase
import com.isaakhanimann.journal.data.room.attachments.PhotoAttachment
import com.isaakhanimann.journal.data.room.experiences.entities.*
import com.isaakhanimann.journal.data.room.migrations.Migration8To9
import com.isaakhanimann.journal.data.room.migrations.Migration7To8
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.Instant
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DatabaseParityTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    @get:Rule val helper = MigrationTestHelper(instrumentation, AppDatabase::class.java)

    @Test fun everyOriginalSchemaMigratesAndRetainsAllOriginalColumnsAndIds() {
        for (version in 1..8) {
            val name = "migration-$version-${UUID.randomUUID()}"
            val schema = JSONObject(instrumentation.context.assets.open("com.isaakhanimann.journal.data.room.AppDatabase/$version.json").bufferedReader().use { it.readText() })
            val entities = schema.getJSONObject("database").getJSONArray("entities")
            val records = mutableMapOf<String, Map<String, String?>>()
            helper.createDatabase(name, version).use { database ->
                val ordered = (0 until entities.length()).map { entities.getJSONObject(it) }.sortedBy { if (it.getString("tableName") == "Experience") 0 else 1 }
                for (entity in ordered) {
                    val fields = entity.getJSONArray("fields")
                    val values = (0 until fields.length()).associate { index ->
                        val field = fields.getJSONObject(index); val column = field.getString("columnName")
                        column to if (!field.getBoolean("notNull")) null else when (field.getString("affinity")) {
                            "INTEGER" -> if (entity.getString("tableName") == "journal_state" && column == "id") "1" else "42"
                            "REAL" -> "1.5"
                            else -> when (column) { "administrationRoute" -> "ORAL"; "color" -> "RED"; "option" -> "PLUS"; else -> "retained:$column" }
                        }
                    }
                    val table = entity.getString("tableName")
                    database.execSQL("INSERT INTO `$table` (${values.keys.joinToString { "`$it`" }}) VALUES (${values.keys.joinToString { "?" }})", values.values.toTypedArray())
                    records[table] = values
                }
            }
            helper.runMigrationsAndValidate(name, 9, true, Migration7To8, Migration8To9).use { database ->
                records.forEach { (table, values) ->
                    database.query("SELECT * FROM `$table`").use { cursor ->
                        assertTrue("$table has its original row", cursor.moveToFirst())
                        values.forEach { (column, value) -> assertEquals("$table.$column from v$version", value, cursor.getString(cursor.getColumnIndexOrThrow(column))) }
                        assertFalse(cursor.moveToNext())
                    }
                }
                for ((sql, expected) in listOf(
                    "SELECT * FROM Experience WHERE sortDate >= 0 AND sortDate < 10" to "index_Experience_sortDate",
                    "SELECT * FROM Ingestion WHERE experienceId = 42 ORDER BY time,id" to "index_Ingestion_experienceId_time_id",
                    "SELECT * FROM Ingestion WHERE time >= 0 AND time < 10" to "index_Ingestion_time",
                    "SELECT * FROM Ingestion WHERE substanceName = 'Caffeine' ORDER BY time,id" to "index_Ingestion_substanceName_time_id",
                    "SELECT * FROM TimedNote WHERE experienceId = 42 ORDER BY time,id" to "index_TimedNote_experienceId_time_id",
                    "SELECT * FROM ShulginRating WHERE experienceId = 42" to "index_ShulginRating_experienceId")) {
                    database.query("EXPLAIN QUERY PLAN $sql").use { cursor ->
                        assertTrue(cursor.moveToFirst()); assertTrue(cursor.getString(3).contains(expected))
                    }
                }
                database.execSQL("DELETE FROM Experience WHERE id = 42")
                database.execSQL("INSERT INTO Experience (title, text, creationDate, sortDate, isFavorite) VALUES ('new', '', 0, 0, 0)")
                database.query("SELECT id FROM Experience").use { cursor ->
                    assertTrue(cursor.moveToFirst()); assertTrue("Deleted IDs must not be reused from v$version", cursor.getInt(0) > 42)
                }
            }
            instrumentation.targetContext.deleteDatabase(name)
        }
    }

    private fun database() = Room.inMemoryDatabaseBuilder(instrumentation.targetContext, AppDatabase::class.java).build()
    private inline fun <T> withDatabase(block: (AppDatabase) -> T): T {
        val db = database()
        try { return block(db) } finally { db.close() }
    }
    private fun experience(id: Int, time: Instant) = Experience(id, "title", "notes", sortDate = time, location = Location("place", 2.0, 1.0))
    private fun photo(id: String, experienceId: Int, position: Int) = PhotoAttachment(id, experienceId, "attachments/$id.jpg", "image/jpeg", 10, 10, 100, Instant.EPOCH, position, "a".repeat(64))
    private fun ingestion(experienceId: Int, time: Instant) = Ingestion(substanceName = "Caffeine", time = time, administrationRoute = AdministrationRoute.ORAL,
        dose = 100.0, isDoseAnEstimate = false, estimatedDoseStandardDeviation = null, units = "mg", experienceId = experienceId, notes = null, stomachFullness = null, consumerName = null, customUnitId = null)

    @Test fun generatedIdsUpdatesAndDeletionPreserveAttachmentContract() = runBlocking {
        withDatabase { db ->
            val dao = db.experienceDao(); val id = dao.insert(experience(0, Instant.EPOCH)).toInt()
            assertTrue(id > 0)
            val first = UUID.randomUUID().toString(); val second = UUID.randomUUID().toString()
            db.photoAttachmentDao().insert(listOf(photo(first, id, 1), photo(second, id, 0)))
            dao.updateFavorite(id, true)
            dao.updateEditableFields(id, "changed", "body", null)
            assertTrue(dao.getExperience(id)!!.isFavorite)
            assertEquals(Instant.EPOCH, dao.getExperience(id)!!.sortDate)
            assertNull(dao.getExperience(id)!!.location)
            assertEquals(listOf(second, first), db.photoAttachmentDao().forExperience(id).map { it.id })
            try { dao.insert(experience(id, Instant.EPOCH)); fail("conflict must abort, not replace the parent") } catch (_: android.database.sqlite.SQLiteConstraintException) { }
            assertEquals(2, db.photoAttachmentDao().forExperience(id).size)
            dao.insert(ingestion(id, Instant.EPOCH))
            dao.insert(TimedNote(time = Instant.EPOCH, note = "note", color = AdaptiveColor.RED, experienceId = id, isPartOfTimeline = true))
            dao.insert(ShulginRating(time = Instant.EPOCH, option = ShulginRatingOption.PLUS, experienceId = id))
            dao.deleteEverythingOfExperience(id)
            assertNull(dao.getExperience(id)); assertTrue(db.photoAttachmentDao().forExperience(id).isEmpty())
            assertTrue(dao.getTimedNotes(id).isEmpty()); assertTrue(dao.getRatingsFlow(id).first().isEmpty())
            assertTrue(dao.getIngestionsWithCompanions(id).isEmpty())
        }
    }
    @Test fun activeQueryExcludesFutureOnlyAndTwelveHourBoundary() = runBlocking {
        withDatabase { db ->
            val now = Instant.parse("2026-01-01T12:00:00Z"); val since = now.minusSeconds(43200)
            val dao = db.experienceDao()
            dao.insert(experience(1, now.minusSeconds(60))); dao.insert(ingestion(1, now.minusSeconds(60)))
            dao.insert(experience(2, now.plusSeconds(3600))); dao.insert(ingestion(2, now.plusSeconds(3600)))
            dao.insert(experience(3, since)); dao.insert(ingestion(3, since))
            assertEquals(1, dao.getMostRecentActiveExperience(since, now)!!.experience.id)
            dao.deleteEverythingOfExperience(1)
            assertNull(dao.getMostRecentActiveExperience(since, now))
        }
    }
    @Test fun failedReplacementTransactionKeepsOriginalRecordsAndPhotos() = runBlocking {
        withDatabase { db ->
            val dao = db.experienceDao(); dao.insert(experience(42, Instant.EPOCH))
            db.photoAttachmentDao().insert(listOf(photo(UUID.randomUUID().toString(), 42, 0)))
            try {
                db.withTransaction { dao.deleteEverything(); dao.insert(experience(1, Instant.EPOCH)); error("injected insertion failure") }
            } catch (_: IllegalStateException) { }
            assertNotNull(dao.getExperience(42)); assertNull(dao.getExperience(1)); assertEquals(1, db.photoAttachmentDao().all().size)
        }
    }

    @Test fun staleEditorCannotModifyRestoredIdsAndFailedSessionWritesRollBack() = runBlocking {
        withDatabase { db ->
            val guard = com.isaakhanimann.journal.data.room.JournalSessionGuard(db,
                com.isaakhanimann.journal.data.attachments.JournalMutationLock())
            val originalGeneration = guard.currentGeneration()
            val dao = db.experienceDao()
            dao.insert(experience(42, Instant.EPOCH))
            val originalUnit = CustomUnit.twoCBSample.copy(id = 1, name = "original unit")
            dao.insert(originalUnit)
            guard.withSession(originalGeneration) { dao.update(originalUnit.copy(name = "saved")) }
            assertEquals("saved", dao.getCustomUnit(1)!!.name)
            db.withTransaction {
                dao.deleteEverythingAndInvalidate(UUID.randomUUID().toString())
                dao.insert(experience(42, Instant.EPOCH).copy(title = "restored entry"))
                dao.insert(originalUnit.copy(name = "restored unit"))
            }
            try {
                guard.withSession(originalGeneration) {
                    dao.update(originalUnit.copy(name = "stale edit"))
                    dao.insert(ingestion(42, Instant.EPOCH))
                }
                fail("An editor from before restore must be rejected")
            } catch (_: IllegalStateException) { }
            assertEquals("restored unit", dao.getCustomUnit(1)!!.name)
            assertTrue(dao.getIngestionsWithCompanions(42).isEmpty())
            try {
                guard.withSession(guard.currentGeneration()) {
                    dao.update(originalUnit.copy(name = "failed edit"))
                    error("injected failure")
                }
            } catch (_: IllegalStateException) { }
            assertEquals("restored unit", dao.getCustomUnit(1)!!.name)
        }
    }
    @Test fun failedIndexMigrationRollsBackAndLeavesSchemaEightRecordsIntact() {
        val name = "failed-index-${UUID.randomUUID()}"
        helper.createDatabase(name,8).use { database ->
            database.execSQL("INSERT INTO Experience (id,title,text,creationDate,sortDate,isFavorite) VALUES (42,'retained','',0,0,1)")
            var statements = 0
            val failing = object : androidx.sqlite.db.SupportSQLiteDatabase by database {
                override fun execSQL(sql: String) {
                    if (++statements == 3) throw android.database.sqlite.SQLiteFullException("injected low space")
                    database.execSQL(sql)
                }
            }
            database.beginTransaction()
            try { Migration8To9.migrate(failing); fail("Injected low space must fail migration") }
            catch (_: android.database.sqlite.SQLiteFullException) { }
            finally { database.endTransaction() }
            assertEquals(8,database.version)
            database.query("SELECT title FROM Experience WHERE id=42").use { cursor -> assertTrue(cursor.moveToFirst()); assertEquals("retained",cursor.getString(0)) }
            database.query("SELECT count(*) FROM sqlite_master WHERE type='index' AND name='index_Ingestion_time'").use { cursor -> cursor.moveToFirst(); assertEquals(0,cursor.getInt(0)) }
        }
        helper.runMigrationsAndValidate(name,9,true,Migration8To9).close()
        instrumentation.targetContext.deleteDatabase(name)
    }

}
