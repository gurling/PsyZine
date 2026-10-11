// PsyZine, 2026-10-11. GPL-3.0-or-later.
package com.isaakhanimann.journal.data.room.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val Migration7To8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Schema 1 omitted AUTOINCREMENT. Room's generated 1→2 migration is empty,
        // so those installations can otherwise reuse a deleted entry ID. Rebuild
        // only that legacy shape, retaining every row/column and its original ID.
        val originalSql = db.query("SELECT sql FROM sqlite_master WHERE type = 'table' AND name = 'Experience' COLLATE NOCASE").use {
            check(it.moveToFirst()) { "Experience table is missing" }; it.getString(0)
        }
        if (!originalSql.contains("AUTOINCREMENT", ignoreCase = true)) {
            db.execSQL("""CREATE TABLE `_psyzine_experience_v8` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `text` TEXT NOT NULL,
                `creationDate` INTEGER NOT NULL, `sortDate` INTEGER NOT NULL, `isFavorite` INTEGER NOT NULL,
                `name` TEXT, `longitude` REAL, `latitude` REAL)""")
            db.execSQL("""INSERT INTO `_psyzine_experience_v8` (id, title, text, creationDate, sortDate, isFavorite, name, longitude, latitude)
                SELECT id, title, text, creationDate, sortDate, isFavorite, name, longitude, latitude FROM Experience""")
            db.execSQL("DROP TABLE `Experience`")
            db.execSQL("ALTER TABLE `_psyzine_experience_v8` RENAME TO `Experience`")
        }
        db.execSQL("CREATE TABLE IF NOT EXISTS `journal_state` (`id` INTEGER NOT NULL, `generation` TEXT NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("""CREATE TABLE IF NOT EXISTS `photo_attachment` (
            `id` TEXT NOT NULL, `experienceId` INTEGER NOT NULL, `relativePath` TEXT NOT NULL,
            `mimeType` TEXT NOT NULL, `width` INTEGER NOT NULL, `height` INTEGER NOT NULL,
            `byteSize` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `position` INTEGER NOT NULL,
            `sha256` TEXT NOT NULL, PRIMARY KEY(`id`),
            FOREIGN KEY(`experienceId`) REFERENCES `Experience`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)""")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_photo_attachment_experienceId` ON `photo_attachment` (`experienceId`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_photo_attachment_relativePath` ON `photo_attachment` (`relativePath`)")
    }
}
