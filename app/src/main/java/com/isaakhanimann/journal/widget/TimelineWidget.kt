// PsyZine, 2026-10-11. GPL-3.0-or-later.
// Adapted from PsyLog 0c29a70f, copyright PsyLog contributors.
package com.isaakhanimann.journal.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.*
import androidx.compose.ui.unit.*
import androidx.glance.*
import androidx.glance.action.clickable
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.layout.*
import androidx.glance.material3.ColorProviders
import androidx.glance.text.*
import com.isaakhanimann.journal.MainActivity
import com.isaakhanimann.journal.R
import com.isaakhanimann.journal.data.active.ActiveExperience
import com.isaakhanimann.journal.service.experienceIntent
import com.isaakhanimann.journal.ui.theme.*
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.renderTimelineBitmap
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import com.isaakhanimann.journal.data.preferences.PresentationSettings
import kotlinx.coroutines.flow.first

class TimelineWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(180.dp, 80.dp), DpSize(240.dp, 160.dp), DpSize(320.dp, 240.dp)))
    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val dependencies = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
        suspend fun load(): WidgetSnapshot {
            val settings = dependencies.presentationPreferences().settings.first()
            val systemDark = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
            val palette = ThemePalettes.get(dependencies.appearanceRepository().theme.first(), systemDark)
            return try { WidgetSnapshot(if (settings.accepted) dependencies.activeExperienceRepository().current() else null, settings, palette) }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { WidgetSnapshot(null, settings, palette, true) }
        }
        val initial = load()
        val ticks = flow { while (currentCoroutineContext().isActive) { emit(Unit); delay(30_000) } }
        // A running Glance session reuses provideContent on updateAll. Observe inside its
        // composition so it never continues showing a captured, stale pre-update snapshot.
        val updates = merge(ticks, dependencies.activeExperienceRepository().changes,
            dependencies.presentationPreferences().settings.map { Unit }, dependencies.appearanceRepository().theme.map { Unit })
            .debounce(100).map { load() }.catch { error ->
                if (error is CancellationException) throw error
                emit(initial.copy(data = null, failed = true))
            }
        provideContent {
            val snapshot by updates.collectAsState(initial)
            val data = snapshot.data; val palette = snapshot.palette
            GlanceTheme(colors = ColorProviders(light = palette.scheme, dark = palette.scheme)) {
                val tap = if (data == null) Intent(context, MainActivity::class.java) else experienceIntent(context, data.id, data.generation)
                Column(GlanceModifier.fillMaxSize().appWidgetBackground().background(GlanceTheme.colors.widgetBackground)
                    .cornerRadius(16.dp).padding(12.dp).clickable(actionStartActivity(tap))) {
                    if (snapshot.failed) Text(context.getString(R.string.live_activity_unavailable), style = textStyle(14.sp))
                    else if (data == null) Text(context.getString(R.string.widget_no_active_experience), style = textStyle(14.sp))
                    else if (!snapshot.settings.widgetDetails) Text("Active experience · Open PsyZine", style = textStyle(14.sp))
                    else Content(context, data, palette)
                }
            }
        }
    }
}

@Composable private fun textStyle(size: TextUnit) = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = size)

@Composable private fun ColumnScope.Content(context: Context, data: ActiveExperience, palette: ThemePalette) {
    val size = LocalSize.current
    if (size.height >= 160.dp) {
        Text("Estimated timeline", style = textStyle(12.sp), maxLines = 1)
        data.timeline?.let {
            val density = context.resources.displayMetrics.density
            val widthPx = ((size.width.value - 24) * density).toInt().coerceIn(1, 1024)
            val bitmap = renderTimelineBitmap(it, widthPx, (60 * density).toInt().coerceIn(1, 512), palette, Density(density), data.updatedAt)
            Image(BitmapImageProvider(bitmap), context.getString(R.string.timeline_description),
                modifier = GlanceModifier.fillMaxWidth().height(60.dp), contentScale = ContentScale.Fit)
        }
    }
    LazyColumn(GlanceModifier.fillMaxWidth().defaultWeight()) {
        items(data.ingestions.take(100)) { ingestion ->
            ActiveIngestionWidgetRow(ingestion)
        }
        if (data.ingestions.size > 100) item { Text("+${data.ingestions.size - 100} more · Open journal", style = textStyle(12.sp)) }
    }
}

@Composable
fun ActiveIngestionWidgetRow(ingestion: com.isaakhanimann.journal.data.active.ActiveIngestion) {
    Row(GlanceModifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text("${ingestion.substanceName.take(160)} ${ingestion.dose.take(80)} ${ingestion.route}", modifier = GlanceModifier.defaultWeight(), style = textStyle(12.sp), maxLines = 2)
        Text(ingestion.phase.displayName, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp, fontStyle = FontStyle.Italic))
    }
}

private data class WidgetSnapshot(val data: ActiveExperience?, val settings: PresentationSettings, val palette: ThemePalette, val failed: Boolean = false)
