// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.psyzine

import androidx.room.Room
import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.isaakhanimann.journal.data.active.ActiveExperienceRepository
import com.isaakhanimann.journal.data.attachments.JournalMutationLock
import com.isaakhanimann.journal.data.definitions.*
import com.isaakhanimann.journal.data.room.*
import com.isaakhanimann.journal.data.room.experiences.*
import com.isaakhanimann.journal.data.room.experiences.entities.*
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import com.isaakhanimann.journal.data.substances.classes.roa.*
import com.isaakhanimann.journal.data.substances.parse.SubstanceParser
import com.isaakhanimann.journal.data.substances.repositories.SubstanceRepository
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.*
import com.isaakhanimann.journal.ui.theme.*
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class ComponentTimelineIntegrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val time = Instant.parse("2026-11-03T12:00:00Z")
    private lateinit var db: AppDatabase
    private lateinit var records: JournalRecordRepository
    private lateinit var generation: String
    private lateinit var substances: SubstanceRepository
    private val unit = CustomUnit(1, null, "Synthetic blend", Instant.EPOCH, null, null, null,
        false, false, "capsule", "capsules", null, "")
    private val own = CustomRoaInfo(id = 1, customUnitId = 1, administrationRoute = AdministrationRoute.ORAL,
        onsetMin = 1.0, onsetMax = 1.0, onsetUnit = "MINUTES",
        comeupMin = 2.0, comeupMax = 2.0, comeupUnit = "MINUTES",
        peakMin = 4.0, peakMax = 5.0, peakUnit = "HOURS",
        offsetMin = 1.0, offsetMax = 2.0, offsetUnit = "HOURS")

    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val guard = JournalSessionGuard(db, JournalMutationLock())
        generation = guard.currentGeneration()
        records = JournalRecordRepository(db, guard)
        substances = SubstanceRepository(context, SubstanceParser()).also { it.initialize() }
        db.experienceDao().insert(Experience(42, "Synthetic entry", "", sortDate = time, location = null))
        db.experienceDao().insert(unit)
        db.definitionDao().insertComponents(listOf(DoseComponent(10, 1, "Caffeine", "mg", dose = 10.0)))
        db.definitionDao().insertRoaInfos(listOf(own))
        db.experienceDao().insert(Ingestion(1, null, time, administrationRoute = AdministrationRoute.ORAL,
            dose = 2.0, isDoseAnEstimate = false, estimatedDoseStandardDeviation = null, units = "capsule",
            experienceId = 42, notes = null, stomachFullness = StomachFullness.FULL, consumerName = null, customUnitId = 1))
    }
    @After fun cleanup() { if (::db.isInitialized) db.close() }

    @Test fun componentAndRouteEditsInvalidateCoherentRecordAndExternalPresentationSnapshots() = runBlocking<Unit> {
        val snapshots = Channel<JournalTimelineRecord>(Channel.UNLIMITED)
        val activeChanges = Channel<Unit>(Channel.UNLIMITED)
        val watch = launch { records.observeTimeline(42, generation).collect { snapshots.send(it.value) } }
        val activeWatch = launch { db.experienceDao().observeActiveChanges(SimpleSQLiteQuery("SELECT 1")).collect { activeChanges.send(Unit) } }
        try {
            val initial = withTimeout(15000) { snapshots.receive() }
            withTimeout(15000) { activeChanges.receive() }
            assertEquals(10.0, initial.definitions.single().components.single().dose!!, 0.0)
            db.withTransaction {
                db.definitionDao().deleteComponents(1)
                db.definitionDao().insertComponents(listOf(DoseComponent(10, 1, "Caffeine", "mg", dose = 15.0)))
                db.definitionDao().deleteRoaInfos(1)
                db.definitionDao().insertRoaInfos(listOf(own.copy(peakMax = 6.0)))
            }
            val updated = withTimeout(15000) {
                var next = snapshots.receive()
                while (next.definitions.single().components.single().dose != 15.0) next = snapshots.receive()
                next
            }
            withTimeout(15000) { activeChanges.receive() }
            assertEquals(6.0, updated.definitions.single().roaInfos.single().peakMax!!, 0.0)
            assertEquals("Synthetic entry", updated.experience!!.title)
            assertEquals(1, updated.ingestions.size)
            val projected = TimelineModelFactory.projectRecords(updated.ingestions, substances.getAllSubstances(), updated.definitionGraph, updated.companionMap)
            assertEquals(30.0, projected.single().contributions.single().quantity.amount!!, 0.0)
            assertEquals(2.0, db.experienceDao().getIngestion(1)!!.dose!!, 0.0)
        } finally { watch.cancelAndJoin(); activeWatch.cancelAndJoin(); snapshots.close(); activeChanges.close() }
    }

    @Test fun recordAndExternalGraphsShareTypedCurvesTimingAndPreserveRawRowCounts() = runBlocking<Unit> {
        db.experienceDao().update(unit.copy(areTimelinesOfSubcomponentsShown = true))
        val record = records.observeTimeline(42, generation).first().value
        val presented = TimelineModelFactory.projectRecords(record.ingestions, substances.getAllSubstances(), record.definitionGraph, record.companionMap)
        val lines = presented.flatMap { it.curves }
        assertEquals(listOf("custom:1", "pw:Caffeine"), lines.map { it.groupingKey })
        assertEquals(1, presented.size)
        assertEquals(time, lines.first().recordedTime)
        assertEquals(time.plusSeconds(10800), lines.first().startTime)
        val now = time.plusSeconds(4 * 3600)
        val model = AllTimelinesModel(lines, emptyList(), emptyList(), true)
        val active = checkNotNull(ActiveExperienceRepository(db, substances, Clock.fixed(now, ZoneOffset.UTC)).current())
        assertEquals(1, active.ingestions.size)
        assertEquals("Synthetic blend", active.ingestions.single().substanceName)
        assertEquals("2 capsules", active.ingestions.single().dose)
        assertEquals(IngestionPhase.PEAK, active.ingestions.single().phase)
        val external = checkNotNull(active.timeline)
        assertEquals(model.startTime, external.startTime)
        assertEquals(model.widthInSeconds, external.widthInSeconds, 0f)
        assertEquals(model.cursorModel.at(now).map { it.equivalentText() }, external.cursorModel.at(now).map { it.equivalentText() })
        assertEquals(2, external.groupDrawables.size)
        ThemeId.entries.filter { it != ThemeId.SYSTEM }.forEach { theme ->
            renderTimelineBitmap(external, 320, 120, ThemePalettes.get(theme), Density(1f), now).recycle()
        }
        assertEquals(time, db.experienceDao().getIngestion(1)!!.time)
    }

    @Test fun componentOnlyDefinitionsRemainDrawableAndHiddenLeavesRemainAccountedFor() = runBlocking<Unit> {
        db.definitionDao().deleteRoaInfos(1)
        val active = checkNotNull(ActiveExperienceRepository(db, substances, Clock.fixed(time, ZoneOffset.UTC)).current())
        assertNotNull(active.timeline)
        assertEquals("Caffeine", active.timeline!!.cursorModel.at(time).single().ingestion.substanceName)
        db.withTransaction {
            db.definitionDao().deleteComponents(1)
            db.definitionDao().insertComponents(listOf(DoseComponent(10, 1, "Caffeine", "mg", dose = 10.0, isHiddenInTimeline = true)))
        }
        val record = records.observeTimeline(42, generation).first().value
        val projected = TimelineModelFactory.projectRecords(record.ingestions, substances.getAllSubstances(), record.definitionGraph, record.companionMap).single()
        assertTrue(projected.curves.isEmpty())
        assertEquals(20.0, projected.contributions.single().quantity.amount!!, 0.0)
        assertEquals(1, projected.element.components.size)
        assertFalse(projected.element.components.single().isShownAsCurve)
        assertNull(ActiveExperienceRepository(db, substances, Clock.fixed(time, ZoneOffset.UTC)).current()!!.timeline)
    }
}
