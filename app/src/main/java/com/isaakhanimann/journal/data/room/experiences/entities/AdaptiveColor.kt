/*
 * Copyright (c) 2022. Isaak Hanimann.
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

package com.isaakhanimann.journal.data.room.experiences.entities

import androidx.compose.ui.graphics.Color
import com.isaakhanimann.journal.ui.theme.ThemePalette
import com.isaakhanimann.journal.ui.theme.DomainColors

enum class AdaptiveColor(val isPreferred: Boolean) {
    RED(true),
    ORANGE(true),
    YELLOW(true),
    GREEN(true),
    MINT(true),
    TEAL(true),
    CYAN(true),
    BLUE(true),
    INDIGO(true),
    PURPLE(true),
    PINK(true),
    BROWN(true),
    FIRE_ENGINE_RED(false),
    CORAL(false),
    TOMATO(false),
    CINNABAR(false),
    RUST(false),
    ORANGE_RED(false),
    AUBURN(false),
    SADDLE_BROWN(false),
    DARK_ORANGE(false),
    DARK_GOLD(false),
    KHAKI(false),
    BRONZE(false),
    GOLD(false),
    OLIVE(false),
    OLIVE_DRAB(false),
    DARK_OLIVE_GREEN(false),
    MOSS_GREEN(false),
    LIME_GREEN(false),
    LIME(false),
    FOREST_GREEN(false),
    SEA_GREEN(false),
    JUNGLE_GREEN(false),
    LIGHT_SEA_GREEN(false),
    DARK_TURQUOISE(false),
    DODGER_BLUE(false),
    ROYAL_BLUE(false),
    DEEP_LAVENDER(false),
    BLUE_VIOLET(false),
    DARK_VIOLET(false),
    HELIOTROPE(false),
    BYZANTIUM(false),
    MAGENTA(false),
    DARK_MAGENTA(false),
    FUCHSIA(false),
    DEEP_PINK(false),
    GRAYISH_MAGENTA(false),
    HOT_PINK(false),
    JAZZBERRY_JAM(false),
    MAROON(false);
    fun getComposeColor(palette: ThemePalette): Color = DomainColors.adaptive(this, palette)
}
