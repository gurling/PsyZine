// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.psyzine

import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.platform.app.InstrumentationRegistry
import com.isaakhanimann.journal.data.attachments.*
import com.isaakhanimann.journal.data.room.*
import com.isaakhanimann.journal.data.room.experiences.ExperienceRepository
import com.isaakhanimann.journal.data.room.experiences.entities.*
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import com.isaakhanimann.journal.ui.tabs.journal.calendar.DayViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.*
import org.junit.Assert.*
import java.time.*
import java.util.concurrent.atomic.AtomicReference

class CalendarRefreshRegressionTest {
    @Test fun resumeRefreshesTodayAndZoneEvenWhenTheJournalHasNotChanged() = runBlocking<Unit> {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val database=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build()
        val clock=object : Clock() {
            @Volatile var reading=Instant.parse("2026-10-12T10:00:00Z")
            override fun instant()=reading
            override fun getZone(): ZoneId=ZoneOffset.UTC
            override fun withZone(zone: ZoneId): Clock=Clock.fixed(reading,zone)
        }
        val zone=AtomicReference<ZoneId>(ZoneId.of("Europe/Prague"))
        val calendar=LocalCalendar(context,clock,object : CalendarZone() { override fun current()=zone.get() })
        val lock=JournalMutationLock()
        val entries=ExperienceRepository(database.experienceDao(),database.photoAttachmentDao(),AttachmentStorage(context),lock,JournalSessionGuard(database,lock))
        lateinit var vm: DayViewModel
        var created=false
        try {
            database.experienceDao().insert(Experience(42,"fixture","",sortDate=clock.reading,location=null))
            database.experienceDao().insert(Ingestion(substanceName="Caffeine",time=clock.reading,administrationRoute=AdministrationRoute.ORAL,
                dose=null,isDoseAnEstimate=false,estimatedDoseStandardDeviation=null,units=null,experienceId=42,notes=null,stomachFullness=null,consumerName=null,customUnitId=null))
            instrumentation.runOnMainSync { vm=DayViewModel(entries,calendar); created=true; vm.showMonths(YearMonth.of(2026,10),YearMonth.of(2026,10)) }
            val before=withTimeout(10000) { vm.model.first { it.days.isNotEmpty() } }
            assertEquals(LocalDate.of(2026,10,12),before.today)
            delay(100) // Allow the WhileSubscribed(0) receiver/Room collection to stop before resume.
            clock.reading=Instant.parse("2026-10-13T00:30:00Z")
            val resumed=withTimeout(10000) { vm.model.first { it.today==LocalDate.of(2026,10,13) } }
            assertEquals(before.days,resumed.days)
            delay(100)
            zone.set(ZoneOffset.ofHours(-12))
            val changed=withTimeout(10000) { vm.model.first { it.days.containsKey(LocalDate.of(2026,10,11)) } }
            assertEquals(LocalDate.of(2026,10,12),changed.today)
            assertEquals(listOf(42),changed.days.getValue(LocalDate.of(2026,10,11)).experienceIds)
        } finally {
            if(created) instrumentation.runOnMainSync { androidx.lifecycle.ViewModelStore().apply { put("calendar",vm); clear() } }
            database.close()
        }
    }

    @Test fun openCalendarRowsObserveTitleEditsRestoreAndDeletion() = runBlocking<Unit> {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val database=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build()
        val lock=JournalMutationLock()
        val entries=ExperienceRepository(database.experienceDao(),database.photoAttachmentDao(),AttachmentStorage(context),lock,JournalSessionGuard(database,lock))
        val updates=kotlinx.coroutines.channels.Channel<String?>(kotlinx.coroutines.channels.Channel.UNLIMITED)
        val observer=launch(Dispatchers.Default) {
            entries.observeExperienceWithIngestionsCompanionsAndRatings(42).collect { updates.send(it?.experience?.title) }
        }
        suspend fun awaitTitle(title: String?) = withTimeout(10000) { updates.receiveAsFlow().first { it==title } }
        try {
            database.experienceDao().insert(Experience(42,"original","",sortDate=Instant.EPOCH,location=null))
            assertEquals("original",awaitTitle("original"))
            database.experienceDao().updateEditableFields(42,"edited","",null)
            assertEquals("edited",awaitTitle("edited"))
            database.withTransaction {
                database.experienceDao().deleteEverythingAndInvalidate("restored")
                database.experienceDao().insert(Experience(42,"restored","",sortDate=Instant.EPOCH,location=null))
            }
            assertEquals("restored",awaitTitle("restored"))
            database.experienceDao().deleteEverythingOfExperience(42)
            assertNull(awaitTitle(null))
        } finally { observer.cancelAndJoin(); updates.close(); database.close() }
    }
}
