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

package com.isaakhanimann.journal

import android.os.Bundle
import android.content.Intent
import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.CancellationException
import com.isaakhanimann.journal.data.preferences.PresentationPreferences
import com.isaakhanimann.journal.data.active.ActiveExperienceRepository
import com.isaakhanimann.journal.data.room.AppDatabase
import com.isaakhanimann.journal.service.*
import com.isaakhanimann.journal.ui.main.ExperienceNavigationRequest
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.isaakhanimann.journal.ui.main.MainScreen
import com.isaakhanimann.journal.ui.theme.PsyZineTheme
import com.isaakhanimann.journal.ui.theme.ThemeId
import com.isaakhanimann.journal.ui.theme.LocalPsyZinePalette
import com.isaakhanimann.journal.data.preferences.AppearanceRepository
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.view.WindowCompat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.room.withTransaction
import androidx.glance.appwidget.updateAll
import javax.inject.Inject
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var appearance: AppearanceRepository
    @Inject lateinit var presentation: PresentationPreferences
    @Inject lateinit var active: ActiveExperienceRepository
    @Inject lateinit var coordinator: ActiveExperienceCoordinator
    @Inject lateinit var database: AppDatabase
    private var experienceRequest by mutableStateOf<ExperienceNavigationRequest?>(null)
    private var permissionAttempted = false
    private var notificationError by mutableStateOf<String?>(null)
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
        lifecycleScope.launch {
            try { coordinator.resumeFromForeground() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { android.util.Log.w("ActiveExperience", "Permission resume unavailable: ${e.javaClass.simpleName}") }
        }
    }
    private var loadedTheme by mutableStateOf<ThemeId?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { loadedTheme == null }
        lifecycleScope.launch { loadedTheme = appearance.theme.first() }
        enableEdgeToEdge()
        acceptExperienceIntent(intent)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(presentation.settings, active.changes, appearance.theme) { settings, _, _ -> settings }.collect { settings ->
                    if (settings.accepted && settings.enabled && !settings.permissionRequested && !permissionAttempted && Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        permissionAttempted = true
                        try {
                            presentation.markPermissionRequested()
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } catch (e: CancellationException) { throw e }
                        catch (_: Exception) {
                            notificationError = "Notification permission could not be requested. You can enable it from Android notification settings."
                        }
                    }
                    try {
                        coordinator.resumeFromForeground()
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                            com.isaakhanimann.journal.widget.TimelineWidget().updateAll(this@MainActivity)
                        }
                    }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { android.util.Log.w("ActiveExperience", "Resume unavailable: ${e.javaClass.simpleName}") }
                }
            }
        }
        setContent {
            val initial = loadedTheme
            if (initial != null) {
            val theme by appearance.theme.collectAsStateWithLifecycle(initialValue = initial)
            PsyZineTheme(theme) {
                val dark = LocalPsyZinePalette.current.dark
                SideEffect {
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !dark
                        isAppearanceLightNavigationBars = !dark
                    }
                }
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(experienceRequest = experienceRequest, consumeExperienceRequest = { experienceRequest = null; intent.removeExtra(EXTRA_EXPERIENCE_ID); intent.removeExtra(EXTRA_JOURNAL_GENERATION) })
                }
                notificationError?.let { message ->
                    androidx.compose.material3.AlertDialog(onDismissRequest = { notificationError = null },
                        title = { androidx.compose.material3.Text("Notification setting unavailable") },
                        text = { androidx.compose.material3.Text(message) },
                        confirmButton = { androidx.compose.material3.TextButton(onClick = { notificationError = null }) { androidx.compose.material3.Text("OK") } })
                }
            }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        acceptExperienceIntent(intent)
    }
    private fun acceptExperienceIntent(intent: Intent?) {
        val id = intent?.getIntExtra(EXTRA_EXPERIENCE_ID, -1) ?: return
        if (id <= 0) return
        val generation = intent.getStringExtra(EXTRA_JOURNAL_GENERATION) ?: return
        lifecycleScope.launch {
            try {
                val exists = database.withTransaction {
                    database.photoAttachmentDao().generation() == generation && database.experienceDao().getExperience(id) != null
                }
                if (exists) experienceRequest = ExperienceNavigationRequest(id, generation)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                android.util.Log.w("ActiveExperience", "Navigation unavailable: ${e.javaClass.simpleName}")
            }
        }
    }
}
