// PsyZine modifications, 2026-10-11. GPL-3.0-or-later; see COPYING.
package com.isaakhanimann.journal.ui.theme

enum class ThemeId(val key: String, val label: String) {
    SYSTEM("system", "Follow system"),
    LATTE("catppuccin-latte", "Catppuccin Latte"),
    FRAPPE("catppuccin-frappe", "Catppuccin Frappé"),
    MACCHIATO("catppuccin-macchiato", "Catppuccin Macchiato"),
    MOCHA("catppuccin-mocha", "Catppuccin Mocha"),
    DRACULA("dracula", "Dracula"),
    GRUVBOX_DARK("gruvbox-dark", "Gruvbox Dark"),
    GRUVBOX_LIGHT("gruvbox-light", "Gruvbox Light"),
    NORD("nord", "Nord"),
    TOKYO_NIGHT("tokyo-night", "Tokyo Night"),
    SOLARIZED_DARK("solarized-dark", "Solarized Dark"),
    SOLARIZED_LIGHT("solarized-light", "Solarized Light"),
    ONE_DARK("one-dark", "One Dark"),
    ROSE_PINE("rose-pine", "Rosé Pine");
    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
    fun resolve(systemDark: Boolean) = if (this == SYSTEM) {
        if (systemDark) MOCHA else LATTE
    } else this
}
