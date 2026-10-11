// PsyZine modifications, 2026-10-11. GPL-3.0-or-later; see COPYING.
package com.isaakhanimann.journal.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme

fun contrast(a: Color, b: Color): Double {
    val x = a.luminance().toDouble(); val y = b.luminance().toDouble()
    return (maxOf(x, y) + .05) / (minOf(x, y) + .05)
}
/** Keep the hue; mix minimally toward black/white when a source color is too faint. */
fun readable(color: Color, background: Color, ratio: Double = 4.5): Color {
    if (contrast(color, background) >= ratio) return color
    val target = if (contrast(Color.White, background) > contrast(Color.Black, background)) Color.White else Color.Black
    var low = 0f; var high = 1f
    repeat(24) {
        val mid = (low + high) / 2
        if (contrast(lerp(color, target, mid), background) >= ratio) high = mid else low = mid
    }
    return lerp(color, target, high)
}

@Immutable
data class ThemePalette(
    val id: ThemeId, val dark: Boolean, val background: Color, val foreground: Color,
    val panel: Color, val muted: Color, val accents: List<Color>, val source: String, val license: String, val sourceRevision: String
) {
    // Domain labels and Material action text also appear on raised cards. Use
    // the least contrasting neutral surface, rather than only the window base.
    private val contrastSurface: Color get() {
        val high = lerp(background, panel, .85f)
        return if (dark) listOf(background, high).maxBy { it.luminance() }
        else listOf(background, high).minBy { it.luminance() }
    }
    fun accent(index: Int) = readable(accents[Math.floorMod(index, accents.size)], contrastSurface)
    val scheme: ColorScheme get() {
        val surface = lerp(background, panel, .55f)
        val high = lerp(background, panel, .85f)
        val body = readable(foreground, contrastSurface)
        val subdued = readable(muted, contrastSurface)
        val primary = accent(1)
        val secondary = accent(2)
        val tertiary = accent(3)
        val error = accent(6)
        fun container(c: Color) = lerp(background, c, .15f)
        val pc = container(primary); val sc = container(secondary); val tc = container(tertiary); val ec = container(error)
        val base = if (dark) darkColorScheme() else lightColorScheme()
        return base.copy(
            primary = primary, onPrimary = readable(background, primary),
            primaryContainer = pc, onPrimaryContainer = readable(primary, pc),
            secondary = secondary, onSecondary = readable(background, secondary),
            secondaryContainer = sc, onSecondaryContainer = readable(secondary, sc),
            tertiary = tertiary, onTertiary = readable(background, tertiary),
            tertiaryContainer = tc, onTertiaryContainer = readable(tertiary, tc),
            error = error, onError = readable(background, error),
            errorContainer = ec, onErrorContainer = readable(error, ec),
            background = background, onBackground = readable(foreground, background),
            surface = background, onSurface = body, surfaceVariant = high, onSurfaceVariant = subdued,
            surfaceDim = surface, surfaceBright = high,
            surfaceContainerLowest = background, surfaceContainerLow = lerp(background, panel, .25f),
            surfaceContainer = surface, surfaceContainerHigh = high, surfaceContainerHighest = high,
            outline = readable(muted, contrastSurface, 3.0), outlineVariant = readable(muted, contrastSurface, 3.0),
            inverseSurface = foreground, inverseOnSurface = readable(background, foreground),
            inversePrimary = readable(primary, foreground), surfaceTint = primary, scrim = Color.Black
        )
    }
}

object ThemePalettes {
    private val revisions = mapOf(
        "https://github.com/catppuccin/catppuccin" to "d09787dd98ca6fba08af5ef2ae94a7e09f17daca",
        "https://github.com/dracula/dracula-theme" to "e22ebcc048b8aff586f9bc94db30ff5347070b42",
        "https://github.com/morhetz/gruvbox" to "ef8864bb42bf244f0295d1c5a403b27e3d139695",
        "https://github.com/nordtheme/nord" to "1cef71605416a222e57225b544540ce0fcec18d4",
        "https://github.com/folke/tokyonight.nvim" to "cdc07ac78467a233fd62c493de29a17e0cf2b2b6",
        "https://github.com/altercation/solarized" to "62f656a02f93c5190a8753159e34b385588d5ff3",
        "https://github.com/atom/one-dark-syntax" to "9c96f4454362267ac45322063e193ccf9d2debb1",
        "https://github.com/rose-pine/rose-pine-palette" to "92af52b465ab6e47437aca223c9b8d3009a2023b"
    )
    private fun palette(id: ThemeId, dark: Boolean, hex: String, source: String, license: String): ThemePalette {
        val colors = hex.split(" ").map { Color(0xff000000L or it.toLong(16)) }
        return ThemePalette(id, dark, colors[0], colors[1], colors[2], colors[3], colors.drop(4), source, license, revisions.getValue(source))
    }
    val all = listOf(
        palette(ThemeId.LATTE, false, "eff1f5 4c4f69 e6e9ef 6c6f85 1e66f5 8839ef 179299 40a02b df8e1d fe640b d20f39 ea76cb", "https://github.com/catppuccin/catppuccin", "MIT"),
        palette(ThemeId.FRAPPE, true, "303446 c6d0f5 292c3c a5adce 8caaee ca9ee6 81c8be a6d189 e5c890 ef9f76 e78284 f4b8e4", "https://github.com/catppuccin/catppuccin", "MIT"),
        palette(ThemeId.MACCHIATO, true, "24273a cad3f5 1e2030 a5adcb 8aadf4 c6a0f6 8bd5ca a6da95 eed49f f5a97f ed8796 f5bde6", "https://github.com/catppuccin/catppuccin", "MIT"),
        palette(ThemeId.MOCHA, true, "1e1e2e cdd6f4 181825 a6adc8 89b4fa cba6f7 94e2d5 a6e3a1 f9e2af fab387 f38ba8 f5c2e7", "https://github.com/catppuccin/catppuccin", "MIT"),
        palette(ThemeId.DRACULA, true, "282a36 f8f8f2 44475a 6272a4 8be9fd bd93f9 8be9fd 50fa7b f1fa8c ffb86c ff5555 ff79c6", "https://github.com/dracula/dracula-theme", "MIT"),
        palette(ThemeId.GRUVBOX_DARK, true, "282828 ebdbb2 3c3836 a89984 83a598 d3869b 8ec07c b8bb26 fabd2f fe8019 fb4934 d3869b", "https://github.com/morhetz/gruvbox", "MIT"),
        palette(ThemeId.GRUVBOX_LIGHT, false, "fbf1c7 3c3836 ebdbb2 7c6f64 076678 8f3f71 427b58 79740e b57614 af3a03 9d0006 8f3f71", "https://github.com/morhetz/gruvbox", "MIT"),
        palette(ThemeId.NORD, true, "2e3440 d8dee9 3b4252 81a1c1 81a1c1 b48ead 88c0d0 a3be8c ebcb8b d08770 bf616a b48ead", "https://github.com/nordtheme/nord", "MIT"),
        palette(ThemeId.TOKYO_NIGHT, true, "1a1b26 c0caf5 24283b a9b1d6 7aa2f7 bb9af7 7dcfff 9ece6a e0af68 ff9e64 f7768e bb9af7", "https://github.com/folke/tokyonight.nvim", "Apache-2.0"),
        palette(ThemeId.SOLARIZED_DARK, true, "002b36 839496 073642 586e75 268bd2 6c71c4 2aa198 859900 b58900 cb4b16 dc322f d33682", "https://github.com/altercation/solarized", "MIT"),
        palette(ThemeId.SOLARIZED_LIGHT, false, "fdf6e3 657b83 eee8d5 839496 268bd2 6c71c4 2aa198 859900 b58900 cb4b16 dc322f d33682", "https://github.com/altercation/solarized", "MIT"),
        palette(ThemeId.ONE_DARK, true, "282c34 abb2bf 21252b 5c6370 61afef c678dd 56b6c2 98c379 e5c07b d19a66 e06c75 c678dd", "https://github.com/atom/one-dark-syntax", "MIT"),
        palette(ThemeId.ROSE_PINE, true, "191724 e0def4 1f1d2e 908caa 31748f c4a7e7 9ccfd8 9ccfd8 f6c177 eb6f92 eb6f92 eb6f92", "https://github.com/rose-pine/rose-pine-palette", "MIT"),
    )
    fun get(id: ThemeId, systemDark: Boolean = false) = all.first { it.id == id.resolve(systemDark) }
}
