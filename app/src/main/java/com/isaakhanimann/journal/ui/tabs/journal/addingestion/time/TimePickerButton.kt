/*
 * Copyright (c) 2022-2023. Isaak Hanimann.
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
import android.text.format.DateFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerButton(localDateTime: LocalDateTime, onChange: (LocalDateTime) -> Unit,
    timeString: String, modifier: Modifier = Modifier, hasOutline: Boolean = true) {
    var open by remember { mutableStateOf(false) }
    val label: @Composable RowScope.() -> Unit = {
        Icon(Icons.Outlined.Schedule, contentDescription = "Choose time")
        Spacer(Modifier.size(ButtonDefaults.IconSpacing)); Text(timeString)
    }
    if (hasOutline) OutlinedButton(onClick = { open = true }, modifier = modifier, content = label)
    else TextButton(onClick = { open = true }, modifier = modifier, content = label)
    if (open) {
        val picker = rememberTimePickerState(initialHour = localDateTime.hour, initialMinute = localDateTime.minute,
            is24Hour = DateFormat.is24HourFormat(LocalContext.current))
        AlertDialog(onDismissRequest = { open = false }, title = { Text("Choose time") },
            text = { TimeInput(state = picker) },
            confirmButton = { TextButton(onClick = {
                onChange(localDateTime.withHour(picker.hour).withMinute(picker.minute)); open = false
            }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } })
    }
}
