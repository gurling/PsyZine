// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.psyzine

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.isaakhanimann.journal.data.room.AppDatabase
import com.isaakhanimann.journal.data.room.migrations.Migration8To9
import com.isaakhanimann.journal.data.substances.parse.*
import com.isaakhanimann.journal.data.substances.repositories.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.measureNanoTime

class AuditPerformanceTest {
    @Test fun fiftyThousandIngestionsUseIndexesAndRepresentativeQueriesImprove() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val database=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build()
        try { withContext(Dispatchers.IO) {
            val db=database.openHelper.writableDatabase
            val indexes=listOf("index_Experience_sortDate","index_Ingestion_experienceId_time_id","index_Ingestion_time",
                "index_Ingestion_substanceName_time_id","index_TimedNote_experienceId_time_id","index_ShulginRating_experienceId")
            indexes.forEach { db.execSQL("DROP INDEX `$it`") }
            db.beginTransaction()
            try {
                val statement=db.compileStatement("INSERT INTO Ingestion (substanceName,time,administrationRoute,dose,isDoseAnEstimate,experienceId) VALUES (?,?,'ORAL',1,0,?)")
                statement.use { for (i in 1..50000) { it.bindString(1,"Synthetic ${i%100}"); it.bindLong(2,i.toLong()); it.bindLong(3,(i/10).toLong()); it.executeInsert() } }
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
            val queries=linkedMapOf(
                "parent" to "SELECT * FROM Ingestion WHERE experienceId=2500 ORDER BY time,id",
                "day" to "SELECT * FROM Ingestion WHERE time>=25000 AND time<25050 ORDER BY time,id",
                "substance" to "SELECT * FROM Ingestion WHERE substanceName='Synthetic 42' ORDER BY time,id")
            fun measure(sql: String): Long {
                repeat(10) { db.query(sql).use { while(it.moveToNext()) { it.getInt(0) } } }
                val times=List(51) { measureNanoTime { db.query(sql).use { cursor -> while(cursor.moveToNext()) { cursor.getInt(0) } } } }.sorted()
                return times[times.size/2]
            }
            val before=queries.mapValues { measure(it.value) }
            Migration8To9.migrate(db)
            val after=queries.mapValues { measure(it.value) }
            val report=JSONObject()
            queries.forEach { (name,sql) ->
                val plans=mutableListOf<String>(); db.query("EXPLAIN QUERY PLAN $sql").use { cursor -> while(cursor.moveToNext()) plans += cursor.getString(3) }
                assertTrue(plans.any { it.contains("USING INDEX") })
                report.put(name,JSONObject().put("baselineMedianNanoseconds",before.getValue(name)).put("indexedMedianNanoseconds",after.getValue(name)).put("improvement",before.getValue(name).toDouble()/after.getValue(name)).put("plan",plans.joinToString()))
            }
            val out=File(context.getExternalFilesDir(null),"audit-performance").apply { mkdirs() }
            File(out,"sqlite-50000.json").writeText(report.toString(2))
            assertTrue("Parent query must improve at least 2x",before.getValue("parent") >= 2*after.getValue("parent"))
            assertTrue("Day query must improve at least 2x",before.getValue("day") >= 2*after.getValue("day"))
        } } finally { database.close() }
    }

    @Test fun substanceInitializationIsSingleFlightOffMainAndFailureCanRetry() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val calls=AtomicInteger(); val delegate=SubstanceParser()
        val parser=object : SubstanceParserInterface by delegate {
            override fun parseSubstanceFile(string: String): com.isaakhanimann.journal.data.substances.classes.SubstanceFile {
                assertNotEquals(android.os.Looper.getMainLooper(),android.os.Looper.myLooper())
                if(calls.incrementAndGet()==1) throw java.io.IOException("injected first load failure")
                return delegate.parseSubstanceFile(string)
            }
        }
        val repository=SubstanceRepository(context,parser)
        try { repository.initialize(); fail("Injected failure must be surfaced") } catch (_: java.io.IOException) { }
        coroutineScope { List(20) { async { repository.initialize() } }.awaitAll() }
        assertEquals(2,calls.get()); assertEquals(289,repository.getAllSubstances().size)
        assertSame(repository.getAllSubstancesWithCategories(),repository.getAllSubstancesWithCategories())
        val search=SearchRepository(repository)
        assertEquals("LSD",search.getMatchingSubstances("l-s d",emptyList(),emptyList()).first().substance.name)
    }
    @Test fun journalSizeFixturesRecordQueryStatisticsAndCachedSearchLatency() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val queries=AtomicInteger()
        val database=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).setQueryCallback(
            object : androidx.room.RoomDatabase.QueryCallback {
                override fun onQuery(sqlQuery: String,bindArgs: List<Any?>) {
                    if(sqlQuery.trimStart().startsWith("SELECT",ignoreCase=true)) queries.incrementAndGet()
                }
            },java.util.concurrent.Executor { it.run() }).build()
        val now=com.isaakhanimann.journal.data.room.CalendarNow(java.time.Instant.now(),java.time.ZoneId.of("Europe/Prague"))
        val report=JSONObject()
        try { withContext(Dispatchers.IO) {
            val db=database.openHelper.writableDatabase
            for(size in listOf(100,1000,10000)) {
                db.beginTransaction()
                try {
                    db.execSQL("DELETE FROM Ingestion"); db.execSQL("DELETE FROM Experience")
                    db.compileStatement("INSERT INTO Experience(id,title,text,creationDate,sortDate,isFavorite) VALUES (?,'Synthetic','',0,?,0)").use { entry ->
                        db.compileStatement("INSERT INTO Ingestion(substanceName,time,administrationRoute,dose,isDoseAnEstimate,experienceId) VALUES (?,?,'ORAL',1,0,?)").use { ingestion ->
                            for(i in 1..size) {
                                val time=now.instant.minusSeconds(i*10L).epochSecond
                                entry.bindLong(1,i.toLong()); entry.bindLong(2,time); entry.executeInsert()
                                ingestion.bindString(1,"Synthetic ${i%100}"); ingestion.bindLong(2,time); ingestion.bindLong(3,i.toLong()); ingestion.executeInsert()
                            }
                        }
                    }
                    db.setTransactionSuccessful()
                } finally { db.endTransaction() }
                val queryTimes=mutableListOf<Long>(); val statisticsTimes=mutableListOf<Long>(); val queryCounts=mutableListOf<Int>()
                repeat(10) {
                    queries.set(0)
                    lateinit var records: List<com.isaakhanimann.journal.data.room.experiences.relations.ExperienceWithIngestionsAndCompanions>
                    val started=System.nanoTime()
                    records=database.experienceDao().getSortedExperiencesWithIngestionsAndCustomUnitsFlow().let { flow -> flow.first() }
                    queryTimes += System.nanoTime()-started
                    queryCounts += queries.get()
                    withContext(Dispatchers.Default) {
                        statisticsTimes += measureNanoTime {
                            val stats=com.isaakhanimann.journal.ui.tabs.stats.aggregateStatistics(records,com.isaakhanimann.journal.ui.tabs.stats.TimePickerOption.DAYS_7,null,now)
                            assertEquals(size,stats.statItems.sumOf { item -> item.ingestionCount })
                        }
                    }
                }
                report.put(size.toString(),JSONObject().put("queryMedianNanoseconds",queryTimes.sorted()[5]).put("statisticsMedianNanoseconds",statisticsTimes.sorted()[5])
                    .put("queryP95Nanoseconds",queryTimes.max()).put("statisticsP95Nanoseconds",statisticsTimes.max()).put("selectQueries",queryCounts.joinToString()))
            }
            val repository=SubstanceRepository(context,SubstanceParser()); repository.initialize()
            val search=SearchRepository(repository)
            val times=List(1000) { index -> measureNanoTime { search.getMatchingSubstances(listOf("c","lsd","2c","keta")[index%4],emptyList(),listOf("Caffeine")) } }.sorted()
            report.put("cachedSearch",JSONObject().put("medianNanoseconds",times[500]).put("p95Nanoseconds",times[949]))
            val out=File(context.getExternalFilesDir(null),"audit-performance").apply { mkdirs() }
            File(out,"journal-and-search.json").writeText(report.toString(2))
        } } finally { database.close() }
    }

}
