/*
 * Copyright (c) 2023. Isaak Hanimann.
 * This file is part of PsychonautWiki Journal.
 *
 * PsychonautWiki Journal is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * PsychonautWiki Journal is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with PsychonautWiki Journal.  If not, see https://www.gnu.org/licenses/gpl-3.0.en.html.
 */

package com.isaakhanimann.journal.ui.tabs.journal.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

@Composable
fun RelativeDateTextNew(
    dateTime: Instant,
    style: TextStyle = MaterialTheme.typography.bodyMedium
) {
    val clock = com.isaakhanimann.journal.ui.utils.LocalJournalDateTime.current
        ?: com.isaakhanimann.journal.ui.utils.rememberUiTime(true, 60_000L)
    Text(
        text = relativeJournalDateText(dateTime, clock.value),
        style = style
    )
}

/** Elapsed-time labels; calendar browsing continues to use local calendar dates. */
fun relativeJournalDateText(dateTime: Instant, now: Instant): String {
    val future = dateTime > now
    val duration = Duration.between(dateTime, now).abs()
    val (number, unit) = when {
        duration.toDays() > 500 -> roundToOneDecimal(duration.toDays() / 365.0) to "year"
        duration.toDays() > 60 -> roundToOneDecimal(duration.toDays() / 30.0) to "month"
        duration.toDays() > 20 -> roundToOneDecimal(duration.toDays() / 7.0) to "week"
        duration.toHours() >= 24 -> roundToOneDecimal(duration.toHours() / 24.0) to "day"
        duration.toHours() >= 1 -> roundToOneDecimal(duration.toMinutes() / 60.0) to "hour"
        duration.toMinutes() >= 1 -> duration.toMinutes().toString() to "minute"
        else -> return if (future) "in less than a minute" else "just now"
    }
    val amount = "$number $unit" + if (number == "1") "" else "s"
    return if (future) "in $amount" else "$amount ago"
}

fun roundToOneDecimal(num: Double): String {
    return try {
        val df = DecimalFormat("#.#", DecimalFormatSymbols.getInstance(Locale.US))
        df.format(num)
    } catch (e: NumberFormatException) {
        println("Failed to format the number: $e")
        ""
    }
}

@Preview(showBackground = true)
@Composable
fun RelativeDateTextPreview() {
    Column {
        val dateTime1 = remember { Instant.now().minus(3, ChronoUnit.DAYS) }
        val dateTime2 = remember { Instant.now().minus(5, ChronoUnit.HOURS) }
        val dateTime3 = remember { Instant.now().minus(30, ChronoUnit.MINUTES) }
        RelativeDateTextNew(dateTime = dateTime1)
        RelativeDateTextNew(dateTime = dateTime2)
        RelativeDateTextNew(dateTime = dateTime3)
    }
}
