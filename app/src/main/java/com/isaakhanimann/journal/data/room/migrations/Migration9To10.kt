// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.data.room.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Preserve all old rows and sequence high-water marks; never repair or purge historical data. */
object Migration9To10 : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        rebuild(db, "Ingestion", "id,substanceName,time,endTime,creationDate,administrationRoute,dose,isDoseAnEstimate,estimatedDoseStandardDeviation,units,experienceId,notes,stomachFullness,consumerName,customUnitId", """
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, substanceName TEXT, time INTEGER NOT NULL,
            endTime INTEGER, creationDate INTEGER, administrationRoute TEXT NOT NULL, dose REAL,
            isDoseAnEstimate INTEGER NOT NULL, estimatedDoseStandardDeviation REAL, units TEXT,
            experienceId INTEGER NOT NULL, notes TEXT, stomachFullness TEXT, consumerName TEXT,
            customUnitId INTEGER, isHiddenInTimeline INTEGER NOT NULL DEFAULT 0
        """.trimIndent())
        rebuild(db, "CustomUnit", "id,substanceName,name,creationDate,administrationRoute,dose,estimatedDoseStandardDeviation,isEstimate,isArchived,unit,unitPlural,originalUnit,note", """
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, substanceName TEXT, name TEXT NOT NULL,
            creationDate INTEGER NOT NULL, administrationRoute TEXT, dose REAL,
            estimatedDoseStandardDeviation REAL, isEstimate INTEGER NOT NULL, isArchived INTEGER NOT NULL,
            unit TEXT NOT NULL, unitPlural TEXT, originalUnit TEXT, note TEXT NOT NULL, color TEXT,
            isHiddenInTimeline INTEGER NOT NULL DEFAULT 0,
            areTimelinesOfSubcomponentsShown INTEGER NOT NULL DEFAULT 0,
            areTolerancesOfSubcomponentsShown INTEGER NOT NULL DEFAULT 0,
            halfToleranceInDays REAL, zeroToleranceInDays REAL
        """.trimIndent())
        db.execSQL("ALTER TABLE SubstanceCompanion ADD COLUMN isHiddenInTimeline INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE SubstanceCompanion ADD COLUMN referenceColor TEXT")
        db.execSQL("ALTER TABLE ShulginRating ADD COLUMN isHiddenInTimeline INTEGER NOT NULL DEFAULT 0")
        db.execSQL("""CREATE TABLE IF NOT EXISTS DoseComponent (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, parentCustomUnitId INTEGER NOT NULL,
            substanceName TEXT, substanceUnit TEXT, childCustomUnitId INTEGER, dose REAL,
            isEstimate INTEGER NOT NULL, estimatedDoseStandardDeviation REAL, note TEXT,
            creationDate INTEGER NOT NULL, isHiddenInTimeline INTEGER NOT NULL DEFAULT 0)
        """.trimIndent())
        for (column in listOf("parentCustomUnitId", "childCustomUnitId", "substanceName")) {
            db.execSQL("CREATE INDEX IF NOT EXISTS index_DoseComponent_$column ON DoseComponent ($column)")
        }
        db.execSQL("""INSERT INTO DoseComponent
            (parentCustomUnitId,substanceName,substanceUnit,dose,isEstimate,estimatedDoseStandardDeviation,note,creationDate)
            SELECT id,substanceName,originalUnit,dose,isEstimate,estimatedDoseStandardDeviation,note,creationDate FROM CustomUnit
        """.trimIndent())
        db.execSQL("""CREATE TABLE IF NOT EXISTS CustomRoaInfo (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, customUnitId INTEGER NOT NULL,
            administrationRoute TEXT NOT NULL, creationDate INTEGER NOT NULL,
            lightMinDose REAL, commonMinDose REAL, strongMinDose REAL, heavyMinDose REAL,
            onsetMin REAL, onsetMax REAL, onsetUnit TEXT,
            comeupMin REAL, comeupMax REAL, comeupUnit TEXT,
            peakMin REAL, peakMax REAL, peakUnit TEXT,
            offsetMin REAL, offsetMax REAL, offsetUnit TEXT)
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_CustomRoaInfo_customUnitId_administrationRoute ON CustomRoaInfo (customUnitId,administrationRoute)")
        db.execSQL("CREATE TABLE IF NOT EXISTS SubstanceTag (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, color TEXT NOT NULL, creationTime INTEGER)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS SubstanceTagMembership (
            subjectId TEXT NOT NULL, tagId TEXT NOT NULL, isPrimary INTEGER NOT NULL,
            PRIMARY KEY(subjectId,tagId), FOREIGN KEY(tagId) REFERENCES SubstanceTag(id) ON UPDATE NO ACTION ON DELETE CASCADE)
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_SubstanceTagMembership_tagId ON SubstanceTagMembership (tagId)")
        Migration8To9.migrate(db)
    }

    private fun rebuild(db: SupportSQLiteDatabase, table: String, columns: String, definition: String) {
        val highWater = db.query("SELECT seq FROM sqlite_sequence WHERE name = ?", arrayOf(table)).use {
            if (it.moveToFirst()) it.getLong(0) else 0L
        }
        db.execSQL("CREATE TABLE ${table}_native ($definition)")
        db.execSQL("INSERT INTO ${table}_native ($columns) SELECT $columns FROM $table")
        db.execSQL("DROP TABLE $table")
        db.execSQL("ALTER TABLE ${table}_native RENAME TO $table")
        db.execSQL("UPDATE sqlite_sequence SET seq = MAX(seq, ?) WHERE name = ?", arrayOf(highWater, table))
        db.execSQL("INSERT INTO sqlite_sequence(name,seq) SELECT ?,? WHERE NOT EXISTS (SELECT 1 FROM sqlite_sequence WHERE name = ?)", arrayOf(table, highWater, table))
    }
}
