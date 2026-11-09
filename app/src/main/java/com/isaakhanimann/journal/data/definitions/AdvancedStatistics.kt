// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.data.definitions

import com.isaakhanimann.journal.data.room.CalendarNow
import com.isaakhanimann.journal.data.room.experiences.entities.Ingestion
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import com.isaakhanimann.journal.data.substances.classes.roa.convertDoseUnits
import kotlinx.coroutines.CancellationException
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

enum class StatisticsMetric(val label: String) { EXPERIENCES("Experiences"), INGESTIONS("Ingestions"), DOSAGE("Dosage") }
enum class StatisticsPeriod(val label: String) { DAYS_7("7D"), DAYS_14("14D"), WEEKS_12("12W"), MONTHS_12("12M"), YEARS("Years"), CUSTOM("Custom") }
enum class StatisticsGranularity(val label: String, val unit: ChronoUnit) {
    DAY("Days", ChronoUnit.DAYS), WEEK("Weeks", ChronoUnit.WEEKS), MONTH("Months", ChronoUnit.MONTHS), YEAR("Years", ChronoUnit.YEARS);
    fun floor(date: LocalDate, locale: Locale): LocalDate = when (this) {
        DAY -> date
        WEEK -> date.with(TemporalAdjusters.previousOrSame(WeekFields.of(locale).firstDayOfWeek))
        MONTH -> date.withDayOfMonth(1)
        YEAR -> date.withDayOfYear(1)
    }
}

data class StatisticsSelection(val metric: StatisticsMetric = StatisticsMetric.EXPERIENCES,
    val period: StatisticsPeriod = StatisticsPeriod.WEEKS_12, val customStart: LocalDate? = null, val customEnd: LocalDate? = null)
data class StatisticsRequest(val selection: StatisticsSelection = StatisticsSelection(), val consumer: String? = null,
    val subjectKey: String? = null, val groupByPrimaryTag: Boolean = false,
    val customStart: LocalDate? = null, val customEnd: LocalDate? = null, val granularity: StatisticsGranularity? = null)
data class StatisticsBucketRange(val start: LocalDate, val endInclusive: LocalDate)
data class StatisticsWindow(val start: LocalDate, val endInclusive: LocalDate, val granularity: StatisticsGranularity,
    val buckets: List<StatisticsBucketRange>, val availableGranularities: List<StatisticsGranularity>)

/** Calendar boundaries, including the whole current week/month/year. Custom ranges alone have a 31-bin limit. */
fun advancedStatisticsWindow(request: StatisticsRequest, today: LocalDate, earliest: LocalDate?, locale: Locale): StatisticsWindow {
    val period = request.selection.period
    val default = when (period) {
        StatisticsPeriod.DAYS_7, StatisticsPeriod.DAYS_14 -> StatisticsGranularity.DAY
        StatisticsPeriod.WEEKS_12 -> StatisticsGranularity.WEEK
        StatisticsPeriod.MONTHS_12 -> StatisticsGranularity.MONTH
        StatisticsPeriod.YEARS -> StatisticsGranularity.YEAR
        StatisticsPeriod.CUSTOM -> StatisticsGranularity.DAY
    }
    val start: LocalDate
    val end: LocalDate
    if (period == StatisticsPeriod.CUSTOM) {
        start = requireNotNull(request.customStart) { "Enter a start date in YYYY-MM-DD format." }
        end = requireNotNull(request.customEnd) { "Enter an end date in YYYY-MM-DD format." }
    } else {
        val floor = default.floor(today, locale)
        start = when (period) {
            StatisticsPeriod.DAYS_7 -> floor.minusDays(6)
            StatisticsPeriod.DAYS_14 -> floor.minusDays(13)
            StatisticsPeriod.WEEKS_12 -> floor.minusWeeks(11)
            StatisticsPeriod.MONTHS_12 -> floor.minusMonths(11)
            else -> minOf(earliest ?: today, today).withDayOfYear(1)
        }
        end = floor.plus(1, default.unit).minusDays(1)
    }
    require(start <= end) { "The end date must be on or after the start date." }
    // java.time supports dates outside the database's usable epoch-millisecond range.
    require(start.year in 1..9999 && end.year in 1..9999) { "Choose dates between years 1 and 9999." }
    val available = if (period == StatisticsPeriod.CUSTOM) StatisticsGranularity.entries.filter {
        it.unit.between(it.floor(start, locale), it.floor(end, locale)) + 1 in 1..31
    } else listOf(default)
    require(available.isNotEmpty()) { "This range is too long. Choose a range with at most 31 yearly buckets." }
    val granularity = request.granularity?.takeIf { period == StatisticsPeriod.CUSTOM && it in available } ?: available.first()
    val first = granularity.floor(start, locale)
    val count = granularity.unit.between(first, granularity.floor(end, locale)).toInt() + 1
    val buckets = List(count) { index ->
        val anchor = first.plus(index.toLong(), granularity.unit)
        StatisticsBucketRange(maxOf(start, anchor), minOf(end, anchor.plus(1, granularity.unit).minusDays(1)))
    }
    return StatisticsWindow(start, end, granularity, buckets, available)
}

data class StatisticsSubject(val key: String, val name: String)
data class StatisticsRecord(val ingestionId: Int, val experienceId: Int, val title: String, val date: LocalDate,
    val firstMatchingTime: java.time.Instant)
data class StatisticsExtreme(val amount: Double, val ingestionIds: List<Int>)
data class StatisticsDistribution(val sampleCount: Int, val excludedRecords: Int, val mean: Double, val median: Double,
    val minimum: StatisticsExtreme, val maximum: StatisticsExtreme)
data class StatisticsDose(val subject: StatisticsSubject, val route: AdministrationRoute, val units: String?,
    val knownTotal: Double?, val knownRecords: Int, val unknownRecords: Int, val isEstimate: Boolean,
    val distribution: StatisticsDistribution? = null)
data class StatisticsCoOccurrence(val subject: StatisticsSubject, val experienceIds: List<Int>)
data class StatisticsGroup(val key: String, val name: String, val experienceCount: Int, val ingestionCount: Int,
    val doses: List<StatisticsDose>, val records: List<StatisticsRecord>,
    val coOccurrences: List<StatisticsCoOccurrence> = emptyList(),
    val intervals: StatisticsIntervals = StatisticsIntervals())
data class AdvancedStatisticsReport(val generation: String, val request: StatisticsRequest, val window: StatisticsWindow,
    val buckets: List<Double?>, val groups: List<StatisticsGroup>, val subjects: List<StatisticsSubject>,
    val consumers: List<String>, val invalidDefinitions: Int, val selectedBucket: Int?, val quantityUnits: String? = null,
    val dosageNeedsSubject: Boolean = false, val dosageHasMixedUnits: Boolean = false)

private data class StatisticsContribution(val subject: StatisticsSubject, val units: String?, val quantity: DoseQuantity)
private data class StatisticsSource(val ingestion: Ingestion, val experience: com.isaakhanimann.journal.data.room.experiences.entities.Experience,
    val contributions: List<StatisticsContribution>, val anchor: java.time.Instant)

/** Raw IDs count once. Repeated leaf paths add quantities; hidden curves still participate in accounting. */
fun projectAdvancedStatistics(data: SubjectBrowserSnapshot, request: StatisticsRequest, now: CalendarNow,
    catalogNames: Set<String>, locale: Locale = Locale.getDefault(), selectedBucket: Int? = null,
    checkCancelled: () -> Unit = {}): AdvancedStatisticsReport {
    val graph = data.definitions.toGraph()
    val localNames = data.locals.mapTo(hashSetOf()) { it.name }
    fun named(name: String) = StatisticsSubject(
        if (name !in catalogNames && name in localNames) SubjectId.Local(name).key else SubjectId.Catalog(name).key, name)
    var invalid = 0
    fun contributions(raw: Ingestion): List<StatisticsContribution> {
        val id = raw.customUnitId
        fun quantity() = DoseQuantity(raw.dose, raw.isDoseAnEstimate, raw.estimatedDoseStandardDeviation).times(DoseQuantity(1.0))
        fun unreadable() = fallback(raw, id).map { contribution ->
            if (id == null) contribution.copy(subject = named(raw.substanceName ?: "Unknown substance"))
            else contribution.copy(subject = contribution.subject.copy(name = graph.definitions[id]?.name ?: contribution.subject.name),
                units = graph.definitions[id]?.unit ?: raw.units)
        }
        return try {
            if (id == null) listOf(StatisticsContribution(named(raw.substanceName ?: "Unknown substance"), raw.units, quantity()))
            else {
                val definition = graph.definitions[id]
                val parent = StatisticsContribution(StatisticsSubject(SubjectId.Definition(id).key,
                    definition?.name ?: "Missing definition $id"), definition?.unit, quantity())
                val leaves = graph.resolve(id, quantity(), checkCancelled).filter { it.subject.key != parent.subject.key }.map {
                    StatisticsContribution(if (it.subject is SubjectId.Catalog) named(it.name) else StatisticsSubject(it.subject.key, it.name), it.units, it.quantity)
                }
                listOf(parent) + leaves
            }
        } catch (e: CancellationException) { throw e }
        catch (_: IllegalArgumentException) { invalid++; unreadable() }
        catch (_: IllegalStateException) { invalid++; unreadable() }
    }
    val all = data.journal.flatMap { entry -> entry.ingestions.map { raw ->
        checkCancelled()
        StatisticsSource(raw, entry.experience, contributions(raw),
            if (request.selection.metric == StatisticsMetric.EXPERIENCES) entry.experience.sortDate else raw.time)
    } }.distinctBy { it.ingestion.id }
    val earliest = all.minOfOrNull { it.ingestion.time }?.atZone(now.zone)?.toLocalDate()
    val window = advancedStatisticsWindow(request, now.date, earliest, locale)
    val start = window.start.atStartOfDay(now.zone).toInstant()
    val end = window.endInclusive.plusDays(1).atStartOfDay(now.zone).toInstant()
    val consumerRecords = all.filter { it.ingestion.consumerName.orEmpty() == request.consumer.orEmpty() }
    val contextByExperience = consumerRecords.groupBy { it.experience.id }
    val candidates = consumerRecords
        .map { source -> source.copy(contributions = source.contributions.filter { request.subjectKey == null || it.subject.key == request.subjectKey }) }
        .filter { it.contributions.isNotEmpty() && it.anchor >= start && it.anchor < end }
    val selected = selectedBucket?.takeIf { it in window.buckets.indices }
    val detail = selected?.let { window.buckets[it] }
    val records = if (detail == null) candidates else candidates.filter { it.anchor.atZone(now.zone).toLocalDate() in detail.start..detail.endInclusive }
    val consumers = all.mapNotNull { it.ingestion.consumerName?.takeIf(String::isNotEmpty) }.distinct().sorted()
    val subjects = all.flatMap { it.contributions }.map { it.subject }.distinctBy { it.key }.sortedWith(compareBy({ it.name }, { it.key }))
    val tags = data.tags.tags.associateBy { it.id }
    fun groupKey(subject: StatisticsSubject) = if (request.groupByPrimaryTag) data.tags.primaryTags[subject.key]?.takeIf { it in tags } ?: "untagged" else subject.key
    val grouped = linkedMapOf<String, MutableList<StatisticsSource>>()
    records.forEach { source ->
        checkCancelled()
        source.contributions.groupBy { groupKey(it.subject) }.forEach { (key, values) ->
            grouped.getOrPut(key) { mutableListOf() }.add(source.copy(contributions = values))
        }
    }
    val groups = grouped.map { (key, values) ->
        checkCancelled()
        val doses = doseTotals(values)
        val rows = values.distinctBy { it.ingestion.id }.sortedWith(compareBy<StatisticsSource> { it.ingestion.time }.thenBy { it.ingestion.id })
        val ownSubjects = values.flatMap { it.contributions }.mapTo(hashSetOf()) { it.subject.key }
        val context = linkedMapOf<String, Pair<StatisticsSubject, MutableSet<Int>>>()
        rows.map { it.experience.id }.distinct().forEach { experienceId ->
            checkCancelled()
            contextByExperience[experienceId].orEmpty().forEach { source ->
                source.contributions.forEach { contribution ->
                    if (contribution.subject.key !in ownSubjects) {
                        context.getOrPut(contribution.subject.key) { contribution.subject to linkedSetOf() }.second.add(experienceId)
                    }
                }
            }
        }
        val coOccurrences = context.values.map { (subject, ids) -> StatisticsCoOccurrence(subject, ids.sorted()) }
            .sortedWith(compareByDescending<StatisticsCoOccurrence> { it.experienceIds.size }.thenBy { it.subject.name }.thenBy { it.subject.key })
        StatisticsGroup(key, if (request.groupByPrimaryTag) tags[key]?.name ?: "Untagged" else values.first().contributions.first().subject.name,
            rows.map { it.experience.id }.distinct().size, rows.size, doses,
            rows.map { StatisticsRecord(it.ingestion.id, it.experience.id, it.experience.title,
                it.ingestion.time.atZone(now.zone).toLocalDate(), it.ingestion.time) }, coOccurrences,
            statisticsIntervals(rows.map { StatisticsIntervalRecord(it.ingestion.id, it.experience.id, it.ingestion.time, it.ingestion.endTime) }, checkCancelled))
    }.sortedWith(compareByDescending<StatisticsGroup> { it.ingestionCount }.thenBy { it.name }.thenBy { it.key })
    val quantityUnits = doseTotals(candidates).map { it.units }.distinct()
    val needsSubject = request.selection.metric == StatisticsMetric.DOSAGE && request.subjectKey == null
    val mixed = request.selection.metric == StatisticsMetric.DOSAGE && !needsSubject && quantityUnits.isNotEmpty() &&
        (quantityUnits.size != 1 || quantityUnits.firstOrNull() == null)
    val candidatesByBucket = candidates.groupBy { window.granularity.floor(it.anchor.atZone(now.zone).toLocalDate(), locale) }
    val buckets = window.buckets.map { bucket ->
        checkCancelled()
        val values = candidatesByBucket[window.granularity.floor(bucket.start, locale)].orEmpty()
        when (request.selection.metric) {
            StatisticsMetric.EXPERIENCES -> values.map { it.experience.id }.distinct().size.toDouble()
            StatisticsMetric.INGESTIONS -> values.size.toDouble()
            StatisticsMetric.DOSAGE -> if (needsSubject || mixed) null else {
                val totals = doseTotals(values)
                if (totals.any { it.knownTotal == null }) null else totals.sumOf { it.knownTotal ?: 0.0 }.takeIf(Double::isFinite)
            }
        }
    }
    return AdvancedStatisticsReport(data.tags.generation, request, window, buckets, groups, subjects, consumers, invalid,
        selected, quantityUnits.singleOrNull(), needsSubject, mixed)
}

private fun fallback(raw: Ingestion, id: Int?): List<StatisticsContribution> = listOf(StatisticsContribution(
    if (id != null) StatisticsSubject(SubjectId.Definition(id).key, "Unavailable definition $id")
    else StatisticsSubject(SubjectId.Catalog(raw.substanceName ?: "Unknown substance").key, raw.substanceName ?: "Unknown substance"),
    raw.units, DoseQuantity(null)))

private fun doseTotals(sources: List<StatisticsSource>): List<StatisticsDose> {
    data class Key(val subject: StatisticsSubject, val route: AdministrationRoute, val units: String?)
    data class Value(val rawId: Int, val quantity: DoseQuantity)
    val grouped = linkedMapOf<Key, MutableList<Value>>()
    sources.forEach { source -> source.contributions.forEach { contribution ->
        val units = contribution.units?.trim()?.takeIf(String::isNotEmpty)
        val mass = units in setOf("g", "mg", "µg", "μg", "ug", "mcg")
        val quantity = if (mass) contribution.quantity.copy(amount = convertDoseUnits(contribution.quantity.amount, units, "mg")) else contribution.quantity
        val key = Key(contribution.subject, source.ingestion.administrationRoute, if (mass) "mg" else units)
        grouped.getOrPut(key) { mutableListOf() }.add(Value(source.ingestion.id, quantity))
    } }
    return grouped.map { (key, values) ->
        val byRecord = values.groupBy { it.rawId }
        val samples = byRecord.mapNotNull { (id, rows) ->
            if (key.units == null || rows.any { it.quantity.amount == null }) null
            else rows.sumOf { it.quantity.amount!! }.takeIf(Double::isFinite)?.let { id to it }
        }
        val known = values.mapNotNull { it.quantity.amount?.takeIf { amount -> key.units != null && amount.isFinite() && amount >= 0 } }
        StatisticsDose(key.subject, key.route, key.units, known.takeIf { it.isNotEmpty() }?.sum()?.takeIf(Double::isFinite), samples.size,
            byRecord.size - samples.size, values.any { it.quantity.isEstimate }, statisticsDistribution(samples, byRecord.size))
    }.sortedWith(compareBy({ it.subject.name }, { it.subject.key }, { it.route.name }, { it.units.orEmpty() }))
}

/** Unweighted, complete-known per-ingestion samples. Partial leaf totals do not enter this population. */
fun statisticsDistribution(samples: List<Pair<Int, Double>>, recordCount: Int): StatisticsDistribution? {
    require(recordCount >= samples.size && samples.map { it.first }.distinct().size == samples.size)
    require(samples.all { it.second.isFinite() && it.second >= 0 })
    if (samples.isEmpty()) return null
    val sorted = samples.sortedWith(compareBy({ it.second }, { it.first }))
    val minimum = sorted.first().second
    val maximum = sorted.last().second
    // Scale before averaging so the mean remains finite even when the total overflows.
    val mean = if (maximum == 0.0) 0.0 else sorted.sumOf { it.second / maximum / sorted.size } * maximum
    val upper = sorted[sorted.size / 2].second
    val lower = sorted[(sorted.size - 1) / 2].second
    val median = lower + (upper - lower) / 2
    return StatisticsDistribution(sorted.size, recordCount - sorted.size, mean.coerceIn(minimum, maximum), median,
        StatisticsExtreme(minimum, sorted.filter { it.second == minimum }.map { it.first }.sorted()),
        StatisticsExtreme(maximum, sorted.filter { it.second == maximum }.map { it.first }.sorted()))
}
