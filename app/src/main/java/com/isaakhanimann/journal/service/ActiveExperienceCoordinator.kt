// PsyZine, 2026-10-11. GPL-3.0-or-later.
// PsyLog 0c29a70f feature port, copyright 2025–2026 PsyLog contributors.
package com.isaakhanimann.journal.service

import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.isaakhanimann.journal.data.active.ActiveExperienceRepository
import com.isaakhanimann.journal.data.preferences.PresentationPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** Called only from a visible activity or its user action, never Application.onCreate. */
@Singleton
class ActiveExperienceCoordinator @Inject constructor(@ApplicationContext private val context: Context,
    private val data: ActiveExperienceRepository, private val preferences: PresentationPreferences) {
    private val mutex = Mutex()
    suspend fun resumeFromForeground() = mutex.withLock {
        val settings = preferences.settings.first()
        val intent = Intent(context, ActiveExperienceService::class.java)
        if (!settings.accepted || !settings.enabled || !activeNotificationsAllowed(context) || data.current() == null) {
            context.stopService(intent)
        } else {
            try { ContextCompat.startForegroundService(context, intent) }
            catch (e: CancellationException) { throw e }
            catch (e: RuntimeException) {
                // A lifecycle transition can race Android's foreground-start check. Data is already committed.
                android.util.Log.w("ActiveExperience", "Foreground startup unavailable: ${e.javaClass.simpleName}")
            }
        }
    }
}

fun activeNotificationsAllowed(context: Context): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled() &&
    context.getSystemService(android.app.NotificationManager::class.java)
        .getNotificationChannel(ActiveExperienceService.CHANNEL_ID)?.importance != android.app.NotificationManager.IMPORTANCE_NONE
