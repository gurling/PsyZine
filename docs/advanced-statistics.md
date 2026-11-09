# Advanced journal statistics

Statistics now offers a separate advanced view. The existing overview keeps its
date and counting rules. The advanced view provides:

- Experiences, ingestions and dosage metrics, with remembered metric/period choices.
- 7D, 14D, 12W, 12M, Years and inclusive custom local-date ranges.
- Consumer/subject filters, primary-tag grouping, selectable chart buckets and
  matching records linked to generation-bound journal screens.
- Separate quantities by subject, route and unit; supported mass units convert
  to mg. Definition quantities and ingredient quantities remain separate.
- Per-ingestion mean, median and linked minimum/maximum records, using only
  complete known quantities. Exclusions and unknown amounts are explicit.

Weeks follow the current locale. Calendar presets include the whole current
week/month/year, including future records inside that period. Experiences use
their stored journal date; ingestion and dosage metrics use ingestion time.
Years starts at the earliest recorded ingestion across the journal. Custom ranges
choose a compatible day/week/month/year granularity with at most 31 buckets;
their first and last details are clipped to the selected dates.

Raw ingestion IDs count once even when an ingredient occurs along several
component paths. Those paths add quantities. Timeline visibility does not hide
accounting. Unknown quantities are never replaced with zero. Tiny positive
values remain readable, and averaging avoids overflow where a total cannot fit.

Metric, period and custom dates persist in DataStore. Filters recover through
SavedStateHandle; restored journals invalidate subject and bucket references.
Preference failures retain previous choices and offer a readable error. No Room
schema, journal records, photos or existing overview behavior are changed.

Expanded matching-record details also list co-occurring subjects, counted once
per matching experience for the selected consumer. This context includes records
outside the chosen dates without adding them to chart counts or dose totals.
Select a subject in that list to open its matching experiences. Empty results
have an explicit message. These relationships do not establish simultaneous use
or an interaction.

Matching-record details include start-to-start intervals within experiences and
recorded-end-to-start breaks between experiences. Overlapping recorded spans
produce zero breaks; nested overlaps retain the latest preceding end. Only
matching subjects, consumers and bucket records participate. Missing end times
use the recorded start; invalid reversed ends cannot create a negative duration.
Rows link back to the contributing experiences. These elapsed durations do not
estimate effect duration or tolerance recovery.

Each subject/route/unit summary also has a separate experience-level distribution.
It sums matching records per experience before computing the mean, median and
linked extrema. An unknown quantity excludes that whole experience from this
population. Incompatible or missing units for the same subject and route also
exclude it; supported mass conversions remain compatible. Overflowing experience
sums are excluded. Counts of included and excluded experiences are explicit.
Per-ingestion samples and known totals retain their existing populations.

Experience screens offer an expandable current-tolerance panel. It reuses the
Statistics interval model and subject details, with an independent saved period
defaulting to Past 1M (30 elapsed days). It loads while expanded and collects
state with the screen lifecycle. The panel labels its journal-wide, current-time
scope explicitly; it does not reconstruct tolerance at a historical experience
date. Missing metadata remains unknown, and preference failures retain the
previous choice. This local period preference does not change portable backups.

Interval details also show a current recorded break, using all matching subjects
for the selected consumer across the journal, independently of the historical
date/bucket filter. Future starts do not interrupt the current break. Ongoing
recorded ranges show an explicit ongoing state; nested overlapping records use
the longest preceding end. The captured “as of” time is displayed and the
contributing experience is linked. This is elapsed recorded time, not an effect
duration or a tolerance recovery percentage. Tolerance percentages remain
outside this increment.
The new choices remain local preferences; portable backup metadata is unchanged.

## Focused development checks — 9 November 2026

The Direct debug APK build and 19 focused JVM tests passed: 12 aggregation and
distribution cases plus seven preference cases. These cover locale weeks, both
Prague DST boundaries, custom bucket clipping, long Years ranges, future records,
metric anchors, repeated/hidden ingredients, unknown values, tag grouping,
cancellation, numeric extremes, and custom-date persistence/write failure.
`git diff --check` passed. New-screen device interaction, full regression, lint
and release qualification were not run for this increment.

The co-occurrence follow-up passed 14 focused aggregation tests and the Direct
debug APK build. Added cases cover consumer isolation, duplicate records,
whole-experience context outside selected dates, selected buckets and empty
results. Device interaction checks were not run for this follow-up.

The recorded-interval follow-up passed 19 focused tests (15 aggregation and four
interval cases) and the Direct debug APK build. Diff checks passed. No device,
lint, full regression or release checks were run for this follow-up.

The experience-distribution follow-up passed 17 focused aggregation tests and
the Direct debug APK build. Added cases cover sums across records, whole-sample
exclusion for unknown quantities, mass conversion, incompatible units and
overflow. Diff checks passed; device, lint and full-regression checks were not run.

The inline current-tolerance follow-up passed 19 focused tests (seven interval,
four definition-source and eight preference cases) and the Direct debug APK
build. The new preference case verifies the independent period, persistence and
failed-write retention. Diff checks passed. Device interaction, lint and full
regression were not run for this follow-up.

The current-break follow-up passed 23 focused tests (18 aggregation and five
interval cases), the Direct debug APK build and diff checks. Added cases cover
future starts, ongoing and overlapping ranges, empty histories, consumer
isolation and current context outside the historical filter. Device, lint and
full-regression checks were not run for this follow-up.
