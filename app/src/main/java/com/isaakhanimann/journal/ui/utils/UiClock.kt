// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.ui.utils

import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import java.time.Instant

/** Presentation time belongs to the visible UI; service/widget clocks remain independent. */
@Composable
fun rememberUiTime(active: Boolean, intervalMillis: Long = 5000L, now: () -> Instant = Instant::now): State<Instant> {
    val value = remember { mutableStateOf(now()) }
    val owner = LocalLifecycleOwner.current
    val currentNow by rememberUpdatedState(now)
    LaunchedEffect(owner, active, intervalMillis) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            value.value = currentNow()
            if (active) while (true) { delay(intervalMillis); value.value = currentNow() }
        }
    }
    return value
}
