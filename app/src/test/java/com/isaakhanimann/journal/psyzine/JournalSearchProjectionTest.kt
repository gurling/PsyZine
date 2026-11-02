// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.psyzine

import com.isaakhanimann.journal.data.definitions.*
import com.isaakhanimann.journal.data.room.experiences.entities.*
import com.isaakhanimann.journal.data.room.experiences.relations.ExperienceWithIngestionsTimedNotesAndRatings
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import com.isaakhanimann.journal.data.substances.repositories.CatalogSearchRecord
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.util.concurrent.CancellationException

class JournalSearchProjectionTest {
    private val catalog = listOf(CatalogSearchRecord("Caffeine", listOf("Coffee"), setOf("stimulant")),
        CatalogSearchRecord("LSD", emptyList(), setOf("psychedelic")))
    private fun ingestion(id: Int, parent: Int, name: String?, note: String? = null, consumer: String? = null,
        definition: Int? = null) = Ingestion(id, name, Instant.ofEpochSecond(id.toLong()), administrationRoute = AdministrationRoute.ORAL,
        dose = Double.NaN, isDoseAnEstimate = false, estimatedDoseStandardDeviation = null, units = "mg",
        experienceId = parent, notes = note, stomachFullness = null, consumerName = consumer, customUnitId = definition, isHiddenInTimeline = true)
    private fun entry(id: Int, title: String, ingestions: List<Ingestion> = emptyList(), favorite: Boolean = false,
        notes: List<TimedNote> = emptyList()) = ExperienceWithIngestionsTimedNotesAndRatings(
        Experience(id, title, "", Instant.EPOCH, Instant.ofEpochSecond(10), favorite, null), ingestions, notes, emptyList())
    private fun unit(id: Int, name: String, components: List<DoseComponent> = emptyList()) = DefinitionWithContent(
        CustomUnit(id, null, name, Instant.EPOCH, null, null, null, false, true, "capsule", null, null, "", isHiddenInTimeline = true), components, emptyList())
    private fun snapshot(entries: List<ExperienceWithIngestionsTimedNotesAndRatings>,
        definitions: List<DefinitionWithContent> = emptyList(), memberships: Map<String, Set<String>> = emptyMap(),
        locals: List<CustomSubstance> = emptyList()) = SubjectBrowserSnapshot(TagFilterSnapshot("g0", emptyList(), memberships),
        definitions, locals, emptyList(), entries)

    @Test fun experienceTermsCanMatchDifferentChildrenWhileIngestionTermsIntersectIds() {
        val data = snapshot(listOf(entry(1, "Session", listOf(ingestion(1, 1, "Caffeine"), ingestion(2, 1, "LSD")))))
        val result = projectJournalSearch(data, catalog, JournalSearchRequest("coffee,lsd"))
        assertEquals(listOf(1), result.experiences.map { it.id }); assertTrue(result.ingestions.isEmpty())
        assertEquals("g0", result.generation)
    }
    @Test fun tagsAndCategoriesRequireAMatchingIngestionForEachTitleTerm() {
        val data = snapshot(listOf(entry(1, "Standalone title", listOf(ingestion(1, 1, "Caffeine")))) ,
            memberships = mapOf("pw:Caffeine" to setOf("a")))
        assertEquals(1, projectJournalSearch(data, catalog, JournalSearchRequest("standalone")).experiences.size)
        assertTrue(projectJournalSearch(data, catalog, JournalSearchRequest("standalone", tags = setOf("a"))).experiences.isEmpty())
        assertEquals(1, projectJournalSearch(data, catalog, JournalSearchRequest("coffee", categories = listOf("stimulant"), tags = setOf("a"))).experiences.size)
        assertTrue(projectJournalSearch(data, catalog, JournalSearchRequest("coffee", categories = listOf("stimulant", "psychedelic"))).experiences.isEmpty())
    }
    @Test fun scopedResultsKeepFavoritesAndTextSourcesSeparateWithoutDroppingMissingMetadata() {
        val note = TimedNote(1, Instant.EPOCH, Instant.EPOCH, "Café-clock", AdaptiveColor.BLUE, 1, false)
        val data = snapshot(listOf(entry(1, "One", listOf(ingestion(1, 1, "Lost metadata", "Nóté", "Other person")), favorite = true, notes = listOf(note)),
            entry(2, "Two", listOf(ingestion(2, 2, "LSD")))))
        assertEquals(listOf(1), projectJournalSearch(data, catalog, JournalSearchRequest("cafe clock", scope = JournalSearchScope.EXPERIENCES)).experiences.map { it.id })
        val favorites = projectJournalSearch(data, catalog, JournalSearchRequest(scope = JournalSearchScope.FAVORITES))
        assertEquals(listOf(1), favorites.experiences.map { it.id }); assertTrue(favorites.ingestions.isEmpty()); assertTrue(favorites.catalogNames.isEmpty())
        val unknown = projectJournalSearch(data, catalog, JournalSearchRequest("note,person", scope = JournalSearchScope.INGESTIONS))
        assertEquals(listOf(1), unknown.ingestions.map { it.ingestion.id }); assertTrue(unknown.experiences.isEmpty())
        assertEquals(listOf(2, 1), projectJournalSearch(data, catalog, JournalSearchRequest(scope = JournalSearchScope.INGESTIONS)).ingestions.map { it.ingestion.id })
    }
    @Test fun hiddenNativeIngredientsRemainSearchableAndProduceOneRawIngestionHit() {
        val parts = listOf(DoseComponent(1, 1, substanceName = "Caffeine", dose = -1.0, isHiddenInTimeline = true),
            DoseComponent(2, 1, substanceName = "Caffeine", dose = Double.NaN))
        val data = snapshot(listOf(entry(1, "Session", listOf(ingestion(1, 1, null, definition = 1)))), listOf(unit(1, "Blend", parts)))
        val result = projectJournalSearch(data, catalog, JournalSearchRequest("coffee"))
        assertEquals(1, result.definitions.size); assertEquals(1, result.ingestions.size)
        assertEquals("Blend", result.ingestions.single().name)
        assertTrue(result.ingestions.single().ingestion.dose!!.isNaN())
    }
    @Test fun catalogLocalNameAmbiguityIsLabelledAndDoesNotRelinkStoredRecords() {
        val original = ingestion(1, 1, "Caffeine")
        val data = snapshot(listOf(entry(1, "Session", listOf(original))),
            memberships = mapOf("local:Caffeine" to setOf("a")), locals = listOf(CustomSubstance(1, "Caffeine", "drops", "")))
        val result = projectJournalSearch(data, catalog, JournalSearchRequest("caffeine", tags = setOf("a"), scope = JournalSearchScope.INGESTIONS))
        assertTrue(result.ingestions.single().hasAmbiguousSubject)
        assertSame(original, result.ingestions.single().ingestion)
        assertEquals("Caffeine", original.substanceName); assertNull(original.customUnitId)
    }
    @Test fun typedNativeIngredientsDoNotAcquireTagsFromEqualLegacyLocalNames() {
        val data = snapshot(listOf(entry(1, "Session", listOf(ingestion(1, 1, "Caffeine", definition = 1), ingestion(2, 1, "Caffeine")))),
            listOf(unit(1, "Native", listOf(DoseComponent(1, 1, substanceName = "Caffeine")))),
            memberships = mapOf("local:Caffeine" to setOf("local-tag")), locals = listOf(CustomSubstance(1, "Caffeine", "mg", "")))
        val result = projectJournalSearch(data, catalog, JournalSearchRequest("caff", tags = setOf("local-tag"), scope = JournalSearchScope.INGESTIONS))
        assertEquals(listOf(2), result.ingestions.map { it.ingestion.id })
        assertTrue(result.ingestions.single().hasAmbiguousSubject)
    }
    @Test fun customScopeKeepsLegacyLocalsAndDefinitionsAndOrderingIsDeterministic() {
        val data = snapshot(listOf(entry(1, "One"), entry(2, "Two")), listOf(unit(1, "One"), unit(2, "Two")),
            locals = listOf(CustomSubstance(1, "Local", "mg", "")))
        val custom = projectJournalSearch(data, catalog, JournalSearchRequest(scope = JournalSearchScope.CUSTOM))
        assertEquals(listOf(2, 1), custom.definitions.map { it.unit.id }); assertEquals(1, custom.locals.size)
        assertTrue(custom.experiences.isEmpty()); assertTrue(custom.catalogNames.isEmpty())
        assertEquals(listOf(2, 1), projectJournalSearch(data, catalog, JournalSearchRequest(scope = JournalSearchScope.EXPERIENCES)).experiences.map { it.id })
        assertEquals(JournalSearchScope.ALL, JournalSearchScope.fromSaved("future-scope"))
    }
    @Test fun cancellationChecksInterruptLargeGraphsRatherThanReturningAnObsoleteProjection() {
        val data = snapshot(emptyList(), listOf(unit(1, "Large", (1..1000).map { DoseComponent(it, 1, substanceName = "Caffeine") })))
        var checks = 0
        try {
            projectJournalSearch(data, catalog, JournalSearchRequest()) { if (++checks == 50) throw CancellationException("replaced query") }
            fail("Expected cancellation")
        } catch (_: CancellationException) { assertEquals(50, checks) }
    }
}
