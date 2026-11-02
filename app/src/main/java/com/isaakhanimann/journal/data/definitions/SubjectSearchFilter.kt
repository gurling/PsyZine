// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.data.definitions

/** Subject identities intersect tokens; each token may match a different alias. */
fun matchesSubjectTokens(names: List<String>, query: String): Boolean {
    val normalizedNames = names.map { normalizedTagName(it).replace("-", "").replace(" ", "") }
    return query.split(Regex("[\\s,]+")).filter { it.isNotEmpty() }.all { token ->
        val normalized = normalizedTagName(token).replace("-", "")
        normalizedNames.any { normalized in it }
    }
}

/** Selected tags match any; untagged is an independent OR branch. */
fun matchesSubjectTags(subject: String, memberships: Map<String, Set<String>>, selected: Set<String>, includeUntagged: Boolean): Boolean {
    if (selected.isEmpty() && !includeUntagged) return true
    val tags = memberships[subject].orEmpty()
    return tags.any { it in selected } || (includeUntagged && tags.isEmpty())
}
