// PsyZine modifications, 2026-10-11. GPL-3.0-or-later; see COPYING.
package com.isaakhanimann.journal.ui.theme

import androidx.compose.ui.graphics.Color
import com.isaakhanimann.journal.data.room.experiences.entities.AdaptiveColor
import com.isaakhanimann.journal.data.substances.classes.InteractionType

object DomainColors {
    fun adaptive(color: AdaptiveColor, palette: ThemePalette): Color {
        val n = color.name
        val family = when {
            n.contains("RED") || n in listOf("RUST", "CINNABAR", "TOMATO", "MAROON", "AUBURN") -> 6
            n.contains("ORANGE") || n.contains("BROWN") || n in listOf("CORAL", "BRONZE") -> 5
            n.contains("GOLD") || n in listOf("YELLOW", "KHAKI", "OLIVE") -> 4
            n.contains("GREEN") || n.contains("LIME") || n.contains("OLIVE") -> 3
            n in listOf("MINT", "TEAL", "CYAN", "DARK_TURQUOISE") -> 2
            n.contains("PINK") || n.contains("MAGENTA") || n in listOf("FUCHSIA", "JAZZBERRY_JAM") -> 7
            n.contains("VIOLET") || n.contains("LAVENDER") || n in listOf("PURPLE", "INDIGO", "HELIOTROPE", "BYZANTIUM") -> 1
            else -> 0
        }
        return palette.accent(family)
    }
    fun category(name: String, palette: ThemePalette) = palette.accent(name.hashCode())
    fun severity(type: InteractionType, palette: ThemePalette) = palette.accent(when(type) {
        InteractionType.DANGEROUS -> 6
        InteractionType.UNSAFE -> 5
        InteractionType.UNCERTAIN -> 4
    })
}
