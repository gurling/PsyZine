// PsyZine, 2026-10-11. GPL-3.0-or-later.
// PsyLog 0c29a70f feature port, copyright 2025–2026 PsyLog contributors.
package com.isaakhanimann.journal.service

import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.isaakhanimann.journal.data.active.ActiveExperienceRepository
import com.isaakhanimann.journal.data.preferences.PresentationPreferences
import com.isaakhanimann.journal.data.preferences.ManualNotificationTarget
import com.isaakhanimann.journal.data.active.ActiveExperience
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

class NotificationActionException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Called only from a visible activity or its user action, never Application.onCreate. */
@Singleton
class ActiveExperienceCoordinator @Inject constructor(@ApplicationContext private val context: Context,
    private val data: ActiveExperienceRepository, private val preferences: PresentationPreferences) {
    private val mutex = Mutex()
    /** Widgets continue to use current(); only the notification follows this local override. */
    suspend fun notificationData(): ActiveExperience? {
        val settings = preferences.settings.first()
        if (!settings.accepted) return null
        val target = settings.manualTarget
        if (target != null) {
            val selected = data.forNotification(target)
            if (selected != null) return selected
            // A simultaneous selection must not be erased by an old expiry/restore check.
            preferences.clearManualNotification(target)
            val fresh = preferences.settings.first()
            if (fresh.manualTarget != null) return null
            return if (fresh.accepted && fresh.enabled) data.current() else null
        }
        return if (settings.enabled) data.current() else null
    }

    suspend fun startExperience(id: Int, generation: String) = mutex.withLock {
        if (!preferences.settings.first().accepted) throw NotificationActionException("Accept the app's terms before starting a live notification.")
        if (!activeNotificationsAllowed(context)) throw NotificationActionException(
            "Notifications are blocked. Enable PsyZine notifications and its Active experience channel in Android settings, then try again.")
        val target = ManualNotificationTarget(id, generation)
        if (data.forNotification(target) == null) throw NotificationActionException(
            "This entry has no remaining estimated timeline, or the journal changed. Check its ingestion times and duration information, then reopen the entry.")
        try { preferences.selectManualNotification(target) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { throw NotificationActionException("The notification selection could not be saved. Please try again.", e) }
        if (data.forNotification(target) == null || preferences.settings.first().manualTarget != target) {
            preferences.clearManualNotification(target)
            throw NotificationActionException("The entry changed before the notification could start. Reopen it and try again.")
        }
        try { ContextCompat.startForegroundService(context, Intent(context, ActiveExperienceService::class.java).setAction(ActiveExperienceService.ACTION_SHOW_MANUAL)) }
        catch (e: RuntimeException) {
            preferences.clearManualNotification(target)
            throw NotificationActionException("Android could not start the live notification. Keep PsyZine open and try again.", e)
        }
    }

    suspend fun stopNotification() = mutex.withLock {
        try { preferences.notificationEnabled(false) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { throw NotificationActionException("The notification could not be stopped because the setting could not be saved. Please try again.", e) }
        context.stopService(Intent(context, ActiveExperienceService::class.java))
        Unit
    }

    suspend fun resumeFromForeground() = mutex.withLock {
        val intent = Intent(context, ActiveExperienceService::class.java)
        if (!activeNotificationsAllowed(context) || notificationData() == null) {
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
