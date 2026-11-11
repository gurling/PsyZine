// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.psyzine

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import com.isaakhanimann.journal.data.backup.BackupManifest
import com.isaakhanimann.journal.data.backup.BackupValidation
import com.isaakhanimann.journal.data.room.experiences.entities.AdaptiveColor
import com.isaakhanimann.journal.data.substances.classes.InteractionType
import com.isaakhanimann.journal.ui.tabs.settings.JournalExport
import com.isaakhanimann.journal.ui.theme.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class SystemColorPaletteTest {
    @Test fun optionalSelectionKeepsTheOriginalSystemFallbackAndAllThirteenPalettes() {
        assertEquals(ThemeId.DYNAMIC, ThemeId.fromKey("android-system-colors"))
        for ((dark, fallback) in listOf(false to ThemeId.LATTE, true to ThemeId.MOCHA)) {
            assertEquals(fallback, ThemeId.SYSTEM.resolve(dark))
            assertEquals(fallback, ThemePalettes.get(ThemeId.DYNAMIC, dark).id)
        }
        assertEquals(13, ThemePalettes.all.size)
        assertFalse(ThemePalettes.all.any { it.id == ThemeId.DYNAMIC })
    }

    @Test fun dynamicSurfacesAndExistingSemanticColorFamiliesArePreserved() {
        for (dark in listOf(false, true)) {
            val source = if (dark) darkColorScheme() else lightColorScheme()
            val palette = systemColorPalette(source, dark)
            assertEquals(ThemeId.DYNAMIC, palette.id)
            assertEquals(dark, palette.dark)
            assertEquals(source.background, palette.background)
            assertEquals(source.surfaceContainerLow, palette.panel)
            assertEquals(source.surfaceContainer, palette.raised)
            assertEquals(source.surfaceContainerHighest, palette.scheme.surfaceContainerHighest)
            assertEquals(ThemePalettes.get(ThemeId.SYSTEM, dark).accents, palette.accents)
        }
    }

    @Test fun adaptedActionsDomainLabelsAndWarningsRemainReadableOnAllNeutralSurfaces() {
        for (dark in listOf(false, true)) {
            val base = if (dark) darkColorScheme() else lightColorScheme()
            // Deliberately faint action and outline inputs must be corrected.
            val palette = systemColorPalette(base.copy(primary = base.surface, outline = base.surface), dark)
            val s = palette.scheme
            val surfaces = listOf(s.background, s.surface, s.surfaceVariant, s.surfaceDim, s.surfaceBright,
                s.surfaceContainerLowest, s.surfaceContainerLow, s.surfaceContainer,
                s.surfaceContainerHigh, s.surfaceContainerHighest)
            val labels = listOf(s.primary, s.secondary, s.tertiary, s.error, s.onSurface, s.onSurfaceVariant) +
                AdaptiveColor.entries.map { DomainColors.adaptive(it, palette) } +
                InteractionType.entries.map { DomainColors.severity(it, palette) }
            for (background in surfaces) {
                labels.forEach { assertTrue("dark=$dark contrast=${contrast(it, background)}", contrast(it, background) >= 4.49) }
                assertTrue(contrast(s.outline, background) >= 2.99)
                assertTrue(contrast(s.outlineVariant, background) >= 2.99)
            }
            listOf(s.onPrimary to s.primary, s.onSecondary to s.secondary, s.onTertiary to s.tertiary,
                s.onError to s.error, s.onPrimaryContainer to s.primaryContainer,
                s.onSecondaryContainer to s.secondaryContainer, s.onTertiaryContainer to s.tertiaryContainer,
                s.onErrorContainer to s.errorContainer, s.inverseOnSurface to s.inverseSurface,
                s.inversePrimary to s.inverseSurface).forEach { (fg, bg) -> assertTrue(contrast(fg, bg) >= 4.49) }
        }
    }

    @Test fun portableThemeSelectionRoundTripsWithoutStoringWallpaperData() {
        val json = Json { encodeDefaults = true }
        val value = BackupManifest(version = 2, journal = JournalExport(), theme = ThemeId.DYNAMIC.key)
        val restored = json.decodeFromString<BackupManifest>(json.encodeToString(value))
        BackupValidation.manifest(restored)
        assertEquals(ThemeId.DYNAMIC, ThemeId.fromKey(restored.theme))
        assertEquals(ThemeId.DYNAMIC.key, restored.theme)
    }
}
