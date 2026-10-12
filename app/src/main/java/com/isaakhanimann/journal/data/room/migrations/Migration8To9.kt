// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.data.room.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Indexes only: legacy orphan rows and all journal data remain unchanged. */
object Migration8To9 : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_Experience_sortDate` ON `Experience` (`sortDate`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_Ingestion_experienceId_time_id` ON `Ingestion` (`experienceId`, `time`, `id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_Ingestion_time` ON `Ingestion` (`time`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_Ingestion_substanceName_time_id` ON `Ingestion` (`substanceName`, `time`, `id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_TimedNote_experienceId_time_id` ON `TimedNote` (`experienceId`, `time`, `id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ShulginRating_experienceId` ON `ShulginRating` (`experienceId`)")
    }
}
