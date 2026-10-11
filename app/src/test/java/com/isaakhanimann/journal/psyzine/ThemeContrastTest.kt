package com.isaakhanimann.journal.psyzine

import com.isaakhanimann.journal.ui.theme.*
import org.junit.Assert.*
import org.junit.Test

class ThemeContrastTest {
    @Test fun defaultsAndUnknownKeysRecover() {
        assertEquals(ThemeId.SYSTEM, ThemeId.fromKey("unknown"))
        assertEquals(ThemeId.LATTE, ThemeId.SYSTEM.resolve(false))
        assertEquals(ThemeId.MOCHA, ThemeId.SYSTEM.resolve(true))
        ThemePalettes.all.forEach { assertEquals(it.id, it.id.resolve(!it.dark)) }
        assertEquals(13, ThemePalettes.all.size)
    }
    @Test fun allTextRolePairsAreReadable() {
        ThemePalettes.all.forEach { palette ->
            val s = palette.scheme
            listOf(s.onBackground to s.background, s.onSurface to s.surface,
                s.onSurfaceVariant to s.surfaceVariant, s.onPrimary to s.primary,
                s.onPrimaryContainer to s.primaryContainer, s.onSecondary to s.secondary,
                s.onSecondaryContainer to s.secondaryContainer, s.onTertiary to s.tertiary,
                s.onTertiaryContainer to s.tertiaryContainer, s.onError to s.error,
                s.onErrorContainer to s.errorContainer, s.inverseOnSurface to s.inverseSurface
            ).forEach { (fg, bg) -> assertTrue("${palette.id}: ${contrast(fg, bg)}", contrast(fg, bg) >= 4.49) }
            assertTrue(contrast(s.outline, s.surfaceContainerHigh) >= 2.99)
        }
    }

    @Test fun domainLabelsAndActionTextAreReadableOnRaisedCardsInEveryTheme() {
        ThemePalettes.all.forEach { palette ->
            val s = palette.scheme
            val surfaces = listOf(s.background, s.surface, s.surfaceContainerLow,
                s.surfaceContainer, s.surfaceContainerHigh, s.surfaceContainerHighest, s.surfaceDim, s.surfaceBright)
            val colors = listOf(s.primary, s.secondary, s.tertiary, s.error, s.onSurface, s.onSurfaceVariant) +
                com.isaakhanimann.journal.data.room.experiences.entities.AdaptiveColor.entries.map { DomainColors.adaptive(it, palette) } +
                com.isaakhanimann.journal.data.substances.classes.roa.DoseClass.entries.map { it.getComposeColor(palette) } +
                com.isaakhanimann.journal.data.substances.classes.InteractionType.entries.map { DomainColors.severity(it, palette) }
            colors.forEach { color -> surfaces.forEach { surface ->
                assertTrue("${palette.id}: domain/action text ${contrast(color, surface)}", contrast(color, surface) >= 4.49)
            } }
            surfaces.forEach { surface -> assertTrue(contrast(s.outline, surface) >= 2.99) }
        }
    }

    @Test fun frappeSeparatesPageCardsAndNavigationLikeTheReference() {
        val s = ThemePalettes.get(ThemeId.FRAPPE).scheme
        assertEquals(androidx.compose.ui.graphics.Color(0xff303446), s.background)
        assertEquals(androidx.compose.ui.graphics.Color(0xff292c3c), s.surfaceContainerLow)
        assertEquals(androidx.compose.ui.graphics.Color(0xff414559), s.surfaceContainer)
        assertTrue(contrast(s.background, s.surfaceContainerLow) > 1.12)
        assertTrue(contrast(s.background, s.surfaceContainer) > 1.28)
    }

    @Test fun everyThemeKeepsDistinctCardAndNavigationSurfaces() {
        ThemePalettes.all.forEach { palette ->
            val s = palette.scheme
            // Guard against the old faint 25% blend while preserving each palette's neutrals.
            assertTrue("${palette.id}: card separation", contrast(s.background, s.surfaceContainerLow) > 1.05)
            assertTrue("${palette.id}: navigation separation", contrast(s.background, s.surfaceContainer) > 1.15)
            assertNotEquals(s.surfaceContainerLow, s.surfaceContainer)
        }
    }
}
