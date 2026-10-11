// PsyZine modifications, 2026-10-11. GPL-3.0-or-later; see COPYING.
package com.isaakhanimann.journal.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

val horizontalPadding = 10.dp
val verticalPaddingCards = 4.dp
val minimumTouchTargetHeight = 48.dp
val LocalPsyZinePalette = staticCompositionLocalOf { ThemePalettes.get(ThemeId.LATTE) }
private val base = Typography()
val PsyZineTypography = base.copy(
    headlineLarge = base.headlineLarge.copy(fontFamily = FontFamily.Monospace),
    headlineMedium = base.headlineMedium.copy(fontFamily = FontFamily.Monospace),
    headlineSmall = base.headlineSmall.copy(fontFamily = FontFamily.Monospace),
    titleLarge = base.titleLarge.copy(fontFamily = FontFamily.Monospace),
    labelSmall = base.labelSmall.copy(fontFamily = FontFamily.Monospace)
)
@Composable
fun PsyZineTheme(theme: ThemeId = ThemeId.SYSTEM, content: @Composable () -> Unit) {
    val palette = ThemePalettes.get(theme, isSystemInDarkTheme())
    val scheme = palette.scheme
    CompositionLocalProvider(
        LocalPsyZinePalette provides palette,
        LocalTextSelectionColors provides TextSelectionColors(scheme.primary, scheme.primary.copy(alpha = .3f))
    ) {
        MaterialTheme(colorScheme = scheme, typography = PsyZineTypography, content = content)
    }
}
