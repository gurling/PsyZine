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

This increment does not include co-occurrence statistics, between-experience
breaks, experience-level dose distributions or inline experience tolerance.
The new choices remain local preferences; portable backup metadata is unchanged.

## Focused development checks — 9 November 2026

The Direct debug APK build and 19 focused JVM tests passed: 12 aggregation and
distribution cases plus seven preference cases. These cover locale weeks, both
Prague DST boundaries, custom bucket clipping, long Years ranges, future records,
metric anchors, repeated/hidden ingredients, unknown values, tag grouping,
cancellation, numeric extremes, and custom-date persistence/write failure.
`git diff --check` passed. New-screen device interaction, full regression, lint
and release qualification were not run for this increment.
