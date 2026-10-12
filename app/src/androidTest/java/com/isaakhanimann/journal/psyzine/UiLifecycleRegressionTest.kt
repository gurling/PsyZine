// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.psyzine

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.isaakhanimann.journal.ui.utils.rememberUiTime
import org.junit.*
import org.junit.Assert.*
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

class UiLifecycleRegressionTest {
    @get:Rule val compose = createComposeRule()
    @Test fun stoppedUiHasNoRecurringTicksAndResumeRefreshesImmediately() {
        val calls=AtomicInteger()
        val owner=object : LifecycleOwner {
            val registry=LifecycleRegistry(this)
            override val lifecycle: Lifecycle get()=registry
        }
        compose.runOnIdle { owner.registry.currentState=Lifecycle.State.RESUMED }
        compose.setContent { CompositionLocalProvider(LocalLifecycleOwner provides owner) {
            Text(rememberUiTime(true,50) { calls.incrementAndGet(); Instant.now() }.value.toString())
        } }
        compose.waitUntil(10000) { calls.get() >= 3 }
        compose.runOnIdle { owner.registry.currentState=Lifecycle.State.CREATED }
        val stopped=calls.get()
        // Presentation is disabled: an actual two-minute stopped UI interval must remain idle.
        repeat(120) { Thread.sleep(1000); assertEquals(stopped,calls.get()) }
        compose.runOnIdle { owner.registry.currentState=Lifecycle.State.RESUMED }
        compose.waitUntil(10000) { calls.get() > stopped }
        compose.runOnIdle { owner.registry.currentState=Lifecycle.State.DESTROYED }
    }
}
