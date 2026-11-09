// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.data.definitions

import com.isaakhanimann.journal.data.room.experiences.entities.*
import com.isaakhanimann.journal.data.substances.repositories.CatalogSearchRecord
import com.isaakhanimann.journal.data.substances.repositories.rankCatalogSubjects

enum class JournalSearchScope(val label: String, val includesJournal: Boolean) {
    ALL("All", true), CUSTOM("Custom substances", false), EXPERIENCES("Experiences", true),
    INGESTIONS("Ingestions", true), FAVORITES("Favorite experiences", true);
    companion object {
        fun fromSaved(value: String) = entries.firstOrNull { it.name == value } ?: ALL
    }
}

data class JournalSearchRequest(val query: String = "", val categories: List<String> = emptyList(),
    val tags: Set<String> = emptySet(), val untagged: Boolean = false, val scope: JournalSearchScope = JournalSearchScope.ALL)

data class IngestionSearchHit(val ingestion: Ingestion, val experience: Experience, val name: String,
    val hasAmbiguousSubject: Boolean = false)

data class JournalSearchProjection(val generation: String? = null, val request: JournalSearchRequest = JournalSearchRequest(),
    val catalogNames: List<String> = emptyList(), val locals: List<CustomSubstance> = emptyList(),
    val definitions: List<DefinitionWithContent> = emptyList(), val experiences: List<Experience> = emptyList(),
    val ingestions: List<IngestionSearchHit> = emptyList(),
    val experienceNames: Map<Int, ExperienceCardNames> = emptyMap()) {
    val isEmpty get() = catalogNames.isEmpty() && locals.isEmpty() && definitions.isEmpty() && experiences.isEmpty() && ingestions.isEmpty()
}

/** One deterministic projection; presentation flags and invalid dose arithmetic never hide records. */
fun projectJournalSearch(data: SubjectBrowserSnapshot, catalog: List<CatalogSearchRecord>, request: JournalSearchRequest,
    checkCancelled: () -> Unit = {}): JournalSearchProjection {
    val terms = request.query.split(Regex("[\\s,]+")).filter(String::isNotEmpty).distinct().ifEmpty { listOf("") }
    val categories = request.categories.filterNot { it == "custom" }
    val wantsCustom = "custom" in request.categories
    val memberships = data.tags.memberships
    fun tagged(subject: SubjectId) = matchesSubjectTags(subject.key, memberships, request.tags, request.untagged)
    val categoryNames = if (categories.isEmpty()) null else catalog.filter { it.categories.containsAll(categories) }.map { it.name }.toSet()
    val catalogNames = catalog.map { it.name }.toSet()
    val localNames = data.locals.map { it.name }.toSet()
    val definitions = data.definitions.associateBy { it.unit.id }
    val index = DefinitionSearchIndex(data.definitions, checkCancelled)
    val catalogByTerm = terms.associateWith { term ->
        checkCancelled()
        rankCatalogSubjects(catalog, term, categories, data.recentNames).filter { tagged(SubjectId.Catalog(it)) }.toSet()
    }
    val localByTerm = terms.associateWith { term ->
        data.locals.filter { checkCancelled(); matchesSubjectTokens(listOf(it.name), term) && tagged(SubjectId.Local(it.name)) }.map { it.name }.toSet()
    }
    val definitionsByTerm = terms.associateWith { term ->
        data.definitions.filter { record ->
            checkCancelled()
            matchesDefinitionTerm(record, index.leafNames(record.unit.id), term, catalogByTerm.getValue(term),
                categoryNames, memberships, request.tags, request.untagged)
        }.map { it.unit.id }.toSet()
    }
    val showSubjects = request.scope == JournalSearchScope.ALL || request.scope == JournalSearchScope.CUSTOM
    val visibleDefinitions = if (showSubjects) data.definitions.filter { row -> terms.all { row.unit.id in definitionsByTerm.getValue(it) } }
        .sortedWith(compareByDescending<DefinitionWithContent> { it.unit.creationDate }.thenByDescending { it.unit.id }) else emptyList()
    val visibleLocals = if (showSubjects && categories.isEmpty()) data.locals.filter { row -> terms.all { row.name in localByTerm.getValue(it) } } else emptyList()
    val visibleCatalog = if (request.scope == JournalSearchScope.ALL && !wantsCustom)
        rankCatalogSubjects(catalog, request.query, categories, data.recentNames).filter { tagged(SubjectId.Catalog(it)) } else emptyList()

    if (!request.scope.includesJournal) return JournalSearchProjection(data.tags.generation, request,
        visibleCatalog, visibleLocals, visibleDefinitions)

    // Old plain-name records cannot distinguish equal catalog/local names. Search both
    // possibilities and label the ambiguity; never rewrite their stored identity.
    fun identities(name: String): List<SubjectId> = buildList {
        if (name in catalogNames || name !in localNames) add(SubjectId.Catalog(name))
        if (name in localNames) add(SubjectId.Local(name))
    }
    val allHits = data.journal.flatMap { entry -> entry.ingestions.map { ingestion ->
        checkCancelled()
        val unit = ingestion.customUnitId?.let { definitions[it]?.unit }
        IngestionSearchHit(ingestion, entry.experience,
            unit?.name ?: ingestion.substanceName ?: ingestion.customUnitId?.let { "Missing definition $it" } ?: "Unknown substance",
            ingestion.customUnitId == null && ingestion.substanceName in catalogNames && ingestion.substanceName in localNames)
    } }.sortedWith(compareByDescending<IngestionSearchHit> { checkCancelled(); it.ingestion.time }.thenByDescending { it.ingestion.id })
    val hitsByTerm = terms.associateWith { term ->
        val matchedDefinitions = definitionsByTerm.getValue(term)
        val matchedCatalog = catalogByTerm.getValue(term)
        val matchedLocals = localByTerm.getValue(term)
        allHits.filter { hit ->
            checkCancelled()
            val ingestion = hit.ingestion
            val unitId = ingestion.customUnitId
            val leaves = if (unitId != null) index.leaves(unitId) else ingestion.substanceName?.let { name ->
                setOf(DefinitionSearchLeaf(SubjectId.Catalog(name), name))
            }.orEmpty()
            val subjects = if (unitId != null) leaves.map { it.subject }
                else ingestion.substanceName?.let(::identities).orEmpty()
            val directTag = unitId?.let { memberships[SubjectId.Definition(it).key].orEmpty().any(request.tags::contains) } == true
            val tagGate = request.tags.isEmpty() && !request.untagged || directTag || subjects.any(::tagged)
            val categoryGate = (categoryNames == null || leaves.any { it.name in categoryNames }) &&
                (!wantsCustom || unitId != null || ingestion.substanceName in localNames)
            val subjectMatch = unitId != null && unitId in matchedDefinitions || leaves.any { it.name in matchedCatalog } ||
                unitId == null && ingestion.substanceName in matchedLocals
            val textMatch = term.isNotEmpty() && listOf(hit.name, ingestion.notes.orEmpty(), ingestion.consumerName.orEmpty())
                .any { normalizedJournalText(term) in normalizedJournalText(it) }
            // Keep missing plain metadata readable in unfiltered lists, unlike the
            // catalog-only blank-query predicate.
            val readableUnknown = term.isEmpty() && request.categories.isEmpty() && request.tags.isEmpty() && !request.untagged
            tagGate && categoryGate && (subjectMatch || textMatch || readableUnknown)
        }
    }
    val experienceIdsByTerm = hitsByTerm.mapValues { (_, hits) -> hits.mapTo(mutableSetOf()) { it.experience.id } }
    val experiences = if (request.scope == JournalSearchScope.INGESTIONS) emptyList() else data.journal.filter { entry ->
        checkCancelled()
        (request.scope != JournalSearchScope.FAVORITES || entry.experience.isFavorite) && terms.all { term ->
            val hasIngestion = entry.experience.id in experienceIdsByTerm.getValue(term)
            val gated = request.categories.isEmpty() && request.tags.isEmpty() && !request.untagged || hasIngestion
            gated && (hasIngestion || listOf(entry.experience.title, entry.experience.text).any {
                normalizedJournalText(term) in normalizedJournalText(it)
            } || entry.timedNotes.any { normalizedJournalText(term) in normalizedJournalText(it.note) } ||
                entry.ratings.any { it.option.sign.contains(term, ignoreCase = true) })
        }
    }.map { it.experience }.sortedWith(compareByDescending<Experience> { it.sortDate }.thenByDescending { it.id })
    val ingestions = if (request.scope == JournalSearchScope.ALL || request.scope == JournalSearchScope.INGESTIONS) {
        val matchingIds = terms.map { term -> hitsByTerm.getValue(term).map { it.ingestion.id }.toSet() }
        allHits.filter { hit -> matchingIds.all { hit.ingestion.id in it } }
    } else emptyList()
    val visibleIds = experiences.mapTo(mutableSetOf()) { it.id }
    val definitionUnits = definitions.mapValues { it.value.unit }
    val definitionComponents = definitions.mapValues { it.value.components }
    val names = data.journal.filter { it.experience.id in visibleIds }.associate { entry ->
        checkCancelled()
        entry.experience.id to experienceCardNames(entry.ingestions, definitionUnits, definitionComponents, checkCancelled)
    }
    return JournalSearchProjection(data.tags.generation, request, visibleCatalog, visibleLocals, visibleDefinitions, experiences, ingestions, names)
}
