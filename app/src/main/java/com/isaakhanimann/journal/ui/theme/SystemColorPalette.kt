// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.ui.theme

import android.content.Context
import android.content.res.Resources
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** Resolve the same palette for Compose, RemoteViews and Glance. No wallpaper access is needed. */
fun paletteForContext(id: ThemeId, context: Context, systemDark: Boolean): ThemePalette {
    if (id != ThemeId.DYNAMIC || Build.VERSION.SDK_INT < 31) return ThemePalettes.get(id, systemDark)
    return try {
        systemColorPalette(if (systemDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context), systemDark)
    } catch (_: Resources.NotFoundException) {
        ThemePalettes.get(id, systemDark)
    }
}

internal fun neutralContrastSurface(scheme: ColorScheme, dark: Boolean): Color {
    val surfaces = listOf(scheme.background, scheme.surface, scheme.surfaceVariant,
        scheme.surfaceContainerLowest, scheme.surfaceContainerLow, scheme.surfaceContainer,
        scheme.surfaceContainerHigh, scheme.surfaceContainerHighest, scheme.surfaceDim, scheme.surfaceBright)
    return if (dark) surfaces.maxBy { it.luminance() } else surfaces.minBy { it.luminance() }
}

/** Preserve semantic color families for substances and warnings while adapting their contrast. */
fun systemColorPalette(source: ColorScheme, dark: Boolean): ThemePalette {
    val fallback = ThemePalettes.get(ThemeId.SYSTEM, dark)
    val surface = neutralContrastSurface(source, dark)
    val primary = readable(source.primary, surface)
    val secondary = readable(source.secondary, surface)
    val tertiary = readable(source.tertiary, surface)
    val error = readable(source.error, surface)
    val scheme = source.copy(
        primary = primary, onPrimary = readable(source.onPrimary, primary),
        secondary = secondary, onSecondary = readable(source.onSecondary, secondary),
        tertiary = tertiary, onTertiary = readable(source.onTertiary, tertiary),
        error = error, onError = readable(source.onError, error),
        onPrimaryContainer = readable(source.onPrimaryContainer, source.primaryContainer),
        onSecondaryContainer = readable(source.onSecondaryContainer, source.secondaryContainer),
        onTertiaryContainer = readable(source.onTertiaryContainer, source.tertiaryContainer),
        onErrorContainer = readable(source.onErrorContainer, source.errorContainer),
        onBackground = readable(source.onBackground, source.background),
        onSurface = readable(source.onSurface, surface), onSurfaceVariant = readable(source.onSurfaceVariant, surface),
        outline = readable(source.outline, surface, 3.0), outlineVariant = readable(source.outlineVariant, surface, 3.0),
        inverseOnSurface = readable(source.inverseOnSurface, source.inverseSurface),
        inversePrimary = readable(source.inversePrimary, source.inverseSurface), surfaceTint = primary,
    )
    return fallback.copy(id = ThemeId.DYNAMIC, background = scheme.background, foreground = scheme.onSurface,
        panel = scheme.surfaceContainerLow, muted = scheme.onSurfaceVariant, raised = scheme.surfaceContainer,
        source = "Android system resources via AndroidX Material 3", license = "Apache-2.0",
        sourceRevision = "Current device resources", systemScheme = scheme)
}
