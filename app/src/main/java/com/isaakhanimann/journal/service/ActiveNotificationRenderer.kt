// PsyZine, 2026-10-11. GPL-3.0-or-later.
// Adapted from PsyLog 0c29a70f, copyright 2025–2026 PsyLog contributors.
package com.isaakhanimann.journal.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.core.app.NotificationCompat
import com.isaakhanimann.journal.R
import com.isaakhanimann.journal.data.active.ActiveExperience
import com.isaakhanimann.journal.ui.theme.*
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.renderTimelineBitmap

/** Owns RemoteViews only; the service owns lifecycle and the single update job. */
class ActiveNotificationRenderer(private val context: Context) {
    fun base() = NotificationCompat.Builder(context, ActiveExperienceService.CHANNEL_ID).setSmallIcon(R.drawable.ic_notification)
        .setSilent(true).setOngoing(true).setOnlyAlertOnce(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
    fun notification(data: ActiveExperience, publicDetails: Boolean, palette: ThemePalette): Notification {
        val tap = PendingIntent.getActivity(context, 0, experienceIntent(context, data.id, data.generation), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val collapsed = RemoteViews(context.packageName, R.layout.notification_live_activity_collapsed)
        val summary = data.ingestions.take(4).joinToString(" · ") { "${it.substanceName.take(160)} (${it.phase.displayName})" } +
            if (data.ingestions.size > 4) " · +${data.ingestions.size - 4} more" else ""
        collapsed.setTextViewText(R.id.substance_summary, summary)
        collapsed.setTextColor(R.id.substance_summary, palette.scheme.onSurface.toArgb())
        collapsed.setInt(R.id.notification_root, "setBackgroundColor", palette.scheme.surface.toArgb())
        val expanded = RemoteViews(context.packageName, R.layout.notification_live_activity)
        expanded.setInt(R.id.notification_root, "setBackgroundColor", palette.scheme.surface.toArgb())
        expanded.setViewVisibility(R.id.timeline_image, if (data.timeline == null) View.GONE else View.VISIBLE)
        data.timeline?.let {
            val density = context.resources.displayMetrics.density
            val width = (context.resources.displayMetrics.widthPixels * .85f).toInt().coerceIn(200, 1024)
            expanded.setImageViewBitmap(R.id.timeline_image, renderTimelineBitmap(it, width, (60 * density).toInt().coerceIn(1, 512), palette, Density(density), data.updatedAt))
        }
        expanded.removeAllViews(R.id.ingestion_container)
        // Android constrains expanded custom notification height. The complete list remains a tap away.
        data.ingestions.take(4).forEach { ingestion ->
            val row = RemoteViews(context.packageName, R.layout.notification_ingestion_row)
            row.setInt(R.id.color_dot, "setColorFilter", ingestion.color.getComposeColor(palette).toArgb())
            row.setTextViewText(R.id.substance_info, "${ingestion.substanceName.take(160)} ${ingestion.dose.take(80)} ${ingestion.route}")
            row.setTextColor(R.id.substance_info, palette.scheme.onSurface.toArgb())
            row.setTextViewText(R.id.phase_text, ingestion.phase.displayName)
            row.setTextColor(R.id.phase_text, palette.scheme.onSurfaceVariant.toArgb())
            expanded.addView(R.id.ingestion_container, row)
        }
        expanded.setTextViewText(R.id.more_ingestions, if (data.ingestions.size > 4) "+${data.ingestions.size - 4} more · Open journal" else "Estimated phases")
        expanded.setTextColor(R.id.more_ingestions, palette.scheme.onSurfaceVariant.toArgb())
        val public = base().setContentTitle(context.getString(R.string.live_activity_channel_name)).setContentText("Open PsyZine to view details").setContentIntent(tap).build()
        return base().setStyle(NotificationCompat.DecoratedCustomViewStyle()).setCustomContentView(collapsed).setCustomBigContentView(expanded)
            .setContentTitle(context.getString(R.string.live_activity_channel_name)).setContentText(summary)
            .setContentIntent(tap).setCategory(NotificationCompat.CATEGORY_STOPWATCH).setUsesChronometer(true)
            .setWhen(data.firstIngestionTime.toEpochMilli()).setPublicVersion(public)
            .setVisibility(if (publicDetails) NotificationCompat.VISIBILITY_PUBLIC else NotificationCompat.VISIBILITY_PRIVATE).build()
    }
}
