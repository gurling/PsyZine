// PsyZine, 2026-10-11. GPL-3.0-or-later.
// PsyLog 0c29a70f feature port, copyright 2025–2026 PsyLog contributors.
package com.isaakhanimann.journal.service

import android.app.*
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.isaakhanimann.journal.R
import com.isaakhanimann.journal.data.active.ActiveExperience
import com.isaakhanimann.journal.data.active.ActiveExperienceRepository
import com.isaakhanimann.journal.data.preferences.*
import com.isaakhanimann.journal.ui.theme.*
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.renderTimelineBitmap
import com.isaakhanimann.journal.widget.TimelineWidget
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@AndroidEntryPoint
class ActiveExperienceService : Service() {
    @Inject lateinit var active: ActiveExperienceRepository
    @Inject lateinit var preferences: PresentationPreferences
    @Inject lateinit var appearance: AppearanceRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var updateJob: Job? = null
    @Volatile private var latestStartId = 0
    private val renderer by lazy { ActiveNotificationRenderer(this) }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.live_activity_channel_name), NotificationManager.IMPORTANCE_LOW).apply {
                description = getString(R.string.live_activity_channel_description); setShowBadge(false)
            })
    }
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    @SuppressLint("MissingPermission") // activeNotificationsAllowed checks permission/channel; revocation can still race and is caught below.
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        if (updateJob?.isActive == true) return START_STICKY
        val loading = renderer.base().setContentTitle(getString(R.string.live_activity_loading)).build()
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, loading,
                if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0)
        } catch (e: RuntimeException) {
            android.util.Log.w("ActiveExperience", "Foreground unavailable: ${e.javaClass.simpleName}")
            stopSelf(); return START_NOT_STICKY
        }
        updateJob = scope.launch {
            val ticks = flow { while (currentCoroutineContext().isActive) { emit(Unit); delay(UPDATE_INTERVAL_MS) } }
            merge(ticks, active.changes, preferences.settings.map { Unit }, appearance.theme.map { Unit })
                .debounce(100).collect {
                    try {
                        val settings = preferences.settings.first()
                        val data = active.current()
                        if (!settings.enabled || !settings.accepted || !activeNotificationsAllowed(this@ActiveExperienceService) || data == null) {
                            val observedStartId = latestStartId
                            // Clear placed widgets before stopping; periodic widget updates remain independent.
                            updateWidgets()
                            val fresh = preferences.settings.first()
                            if (fresh.enabled && fresh.accepted && activeNotificationsAllowed(this@ActiveExperienceService) && active.current() != null) return@collect
                            // A new start can arrive while the widget/DB work suspends. Do not
                            // cancel the sole updater or remove that newer start's notification.
                            if (!stopSelfResult(observedStartId)) return@collect
                            stopForeground(STOP_FOREGROUND_REMOVE); throw CancellationException("Active presentation ended")
                        }
                        val systemDark = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
                        val palette = ThemePalettes.get(appearance.theme.first(), systemDark)
                        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, renderer.notification(data, settings.lockScreenDetails, palette))
                        updateWidgets()
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) {
                        android.util.Log.w("ActiveExperience", "Presentation update failed: ${e.javaClass.simpleName}")
                        // Permission may have been revoked while the timeline was rendering.
                        // A fallback notification must not throw a second uncaught exception.
                        runCatching {
                            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID,
                                renderer.base().setContentTitle(getString(R.string.live_activity_unavailable)).build())
                        }
                    }
                }
        }
        return START_STICKY
    }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
    private suspend fun updateWidgets() {
        try { TimelineWidget().updateAll(this) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { android.util.Log.w("TimelineWidget", "Update unavailable: ${e.javaClass.simpleName}") }
    }
    companion object { const val CHANNEL_ID = "active_experience"; const val NOTIFICATION_ID = 101; const val UPDATE_INTERVAL_MS = 30_000L }
}
