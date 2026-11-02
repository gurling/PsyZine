// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.data.definitions

import androidx.sqlite.db.SimpleSQLiteQuery
import com.isaakhanimann.journal.data.room.AppDatabase
import com.isaakhanimann.journal.data.room.CalendarNow
import com.isaakhanimann.journal.data.room.JournalSessionGuard
import com.isaakhanimann.journal.data.substances.repositories.SubstanceRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

enum class TolerancePeriod(val label: String, val days: Long) {
    PAST_3D("Past 3D", 3), PAST_1W("Past 1W", 7), PAST_2W("Past 2W", 14), PAST_1M("Past 1M", 30),
    PAST_2M("Past 2M", 60), PAST_3M("Past 3M", 90), PAST_5M("Past 5M", 150), PAST_8M("Past 8M", 240), PAST_12M("Past 12M", 360);
    companion object { fun fromName(value: String?): TolerancePeriod = entries.firstOrNull { it.name == value } ?: PAST_2M }
}

data class ToleranceReport(val projection: ToleranceProjection, val start: Instant, val cutoff: Instant,
    val unresolvedDefinitions: Set<Int>, val generation: String, val zone: ZoneId = ZoneId.systemDefault())

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class ToleranceRepository @Inject constructor(private val db: AppDatabase, private val guard: JournalSessionGuard,
    private val substances: SubstanceRepository) {
    fun observe(now: CalendarNow, period: TolerancePeriod): Flow<ToleranceReport> = observe({ now }, period)
    fun observe(now: () -> CalendarNow, period: TolerancePeriod): Flow<ToleranceReport> = flow {
        substances.initialize()
        val catalog = substances.getAllSubstances().associateBy { it.name }
        emitAll(db.definitionDao().observeToleranceChanges(SimpleSQLiteQuery("SELECT 1")).mapLatest {
            val current = now()
            val start = current.instant.minus(Duration.ofDays(period.days))
            val snapshot = guard.readSession(null) { generation ->
                val dao = db.experienceDao()
                val events = db.definitionDao().toleranceIngestions(start.minus(Duration.ofDays(90)), current.instant)
                val graph = db.definitionDao().definitions().toGraph()
                val colors = dao.getAllSubstanceCompanions().associate { it.substanceName to it.color.name }
                val locals = dao.getAllCustomSubstances().map { it.name }.toSet()
                Snapshot(events, graph, colors, locals, generation)
            }
            withContext(Dispatchers.Default) {
                val sources = definitionToleranceEvents(snapshot.ingestions, snapshot.graph, catalog, snapshot.colors, snapshot.locals)
                ToleranceReport(projectTolerance(sources.events, start, current.instant, current.zone), start, current.instant,
                    sources.unresolvedDefinitionIds, snapshot.generation, current.zone)
            }
        })
    }
    private data class Snapshot(val ingestions: List<com.isaakhanimann.journal.data.room.experiences.entities.Ingestion>,
        val graph: CustomDefinitionGraph, val colors: Map<String, String>, val locals: Set<String>, val generation: String)
}
