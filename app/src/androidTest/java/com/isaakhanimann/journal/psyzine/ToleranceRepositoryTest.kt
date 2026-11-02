// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.psyzine

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.platform.app.InstrumentationRegistry
import com.isaakhanimann.journal.data.attachments.JournalMutationLock
import com.isaakhanimann.journal.data.definitions.*
import com.isaakhanimann.journal.data.room.*
import com.isaakhanimann.journal.data.room.attachments.JournalState
import com.isaakhanimann.journal.data.room.experiences.entities.*
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import com.isaakhanimann.journal.data.substances.parse.SubstanceParser
import com.isaakhanimann.journal.data.substances.repositories.SubstanceRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import org.junit.*
import org.junit.Assert.*
import java.time.*

class ToleranceRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var guard: JournalSessionGuard
    private lateinit var repo: ToleranceRepository
    private val now = CalendarNow(Instant.parse("2026-03-15T12:00:00Z"), ZoneId.of("Europe/Prague"))
    private fun unit() = CustomUnit(1, null, "g0", Instant.EPOCH, null, null, null, false, false, "capsule", null, null, "",
        halfToleranceInDays = 2.0, zeroToleranceInDays = 4.0)
    @Before fun setup(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        guard = JournalSessionGuard(db, JournalMutationLock())
        repo = ToleranceRepository(db, guard, SubstanceRepository(context, SubstanceParser()))
        db.photoAttachmentDao().setGeneration(JournalState(generation = "g0"))
        db.experienceDao().insert(unit())
        db.experienceDao().insert(Experience(1, "Synthetic", "", Instant.EPOCH, now.instant, false, null))
        db.experienceDao().insert(Ingestion(id = 1, substanceName = null, time = now.instant.minus(Duration.ofHours(24)),
            administrationRoute = AdministrationRoute.ORAL, dose = null, isDoseAnEstimate = false, estimatedDoseStandardDeviation = null,
            units = "capsule", experienceId = 1, notes = null, stomachFullness = null, consumerName = null, customUnitId = 1))
    }
    @After fun cleanup() { db.close() }
    @Test fun definitionChangesAndRestoreRefreshTheSameSubscription() = runBlocking {
        val received = Channel<ToleranceReport>(Channel.UNLIMITED)
        val job = launch { repo.observe(now, TolerancePeriod.PAST_2M).collect { received.send(it) } }
        suspend fun next(predicate: (ToleranceReport) -> Boolean) = withTimeout(15000) { var value = received.receive(); while (!predicate(value)) value = received.receive(); value }
        try {
            assertEquals("g0", next { it.projection.rows.isNotEmpty() }.projection.rows.single().name)
            db.experienceDao().update(unit().copy(name = "Updated", halfToleranceInDays = 3.0))
            val changed = next { it.projection.rows.firstOrNull()?.name == "Updated" }
            val interval = changed.projection.rows.single().bands.getValue(ToleranceBand.FULL).single()
            assertEquals(3L, Duration.between(now.instant.minus(Duration.ofHours(24)).atZone(now.zone).toLocalDate().atStartOfDay(now.zone).toInstant(), interval.end).toDays())
            db.experienceDao().deleteEverythingAndInvalidate("restored")
            assertTrue(next { it.generation == "restored" }.projection.rows.isEmpty())
        } finally { job.cancelAndJoin(); received.close() }
    }
    @Test fun snapshotsNeverMixGenerationWithDefinitionMetadata() = runBlocking {
        val observed = mutableListOf<ToleranceReport>()
        val job = launch { repo.observe(now, TolerancePeriod.PAST_2M).collect { observed += it } }
        try {
            withTimeout(15000) { while (observed.isEmpty()) delay(10) }
            repeat(15) { index ->
                db.withTransaction {
                    db.photoAttachmentDao().setGeneration(JournalState(generation = "g$index"))
                    delay(2)
                    db.experienceDao().update(unit().copy(name = "g$index"))
                }
            }
            withTimeout(15000) { while (observed.none { it.generation == "g14" }) delay(10) }
            assertTrue(observed.all { it.generation == it.projection.rows.single().name })
        } finally { job.cancelAndJoin() }
    }
    @Test fun newRecordsRefreshCutoffAndZoneWithoutReopeningTheChart() = runBlocking {
        val clock = java.util.concurrent.atomic.AtomicReference(now)
        val received = Channel<ToleranceReport>(Channel.UNLIMITED)
        val job = launch { repo.observe({ clock.get() }, TolerancePeriod.PAST_2M).collect { received.send(it) } }
        try {
            withTimeout(15000) { received.receive() }
            val next = now.copy(instant = now.instant.plus(Duration.ofDays(1)), zone = ZoneId.of("America/New_York"))
            clock.set(next)
            val original = db.experienceDao().getIngestion(1)!!
            db.experienceDao().insert(original.copy(id = 2, time = next.instant.minusSeconds(3600)))
            val report = withTimeout(15000) {
                var value = received.receive()
                while (value.projection.rows.none { 2 in it.ingestionIds }) value = received.receive()
                value
            }
            assertEquals(next.instant, report.cutoff); assertEquals(next.zone, report.zone)
            assertEquals(2, report.projection.rows.single().ingestionIds.size)
        } finally { job.cancelAndJoin(); received.close() }
    }
}
