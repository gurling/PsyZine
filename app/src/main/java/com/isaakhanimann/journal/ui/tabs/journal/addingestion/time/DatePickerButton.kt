/*
 * Copyright (c) 2022. Isaak Hanimann.
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

package com.isaakhanimann.journal.ui.tabs.journal.addingestion.time

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import java.time.LocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerButton(localDateTime: LocalDateTime, onChange: (LocalDateTime) -> Unit,
    dateString: String, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = modifier) {
        Icon(Icons.Outlined.Event, contentDescription = "Choose date")
        Spacer(Modifier.size(ButtonDefaults.IconSpacing)); Text(dateString)
    }
    if (open) {
        // Material date selection is UTC midnight; convert to LocalDate without local timezone shifts.
        val picker = rememberDatePickerState(initialSelectedDateMillis = localDateTime.toLocalDate().atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(onDismissRequest = { open = false }, confirmButton = {
            TextButton(onClick = {
                picker.selectedDateMillis?.let {
                    val date = java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate()
                    onChange(LocalDateTime.of(date, localDateTime.toLocalTime()))
                }
                open = false
            }, enabled = picker.selectedDateMillis != null) { Text("OK") }
        }, dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } }) {
            DatePicker(state = picker)
        }
    }
}
