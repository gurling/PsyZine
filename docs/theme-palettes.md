# Theme palettes

Source colors retain canonical base/foreground values from the implementation plan. Palette variants: Catppuccin Latte, Frappé, Macchiato, Mocha; Dracula; Gruvbox medium dark/light; Nord; Tokyo Night Night; Solarized dark/light; One Dark; Rosé Pine main. Follow system resolves to Latte/Mocha; a named palette fixes its appearance.

The following upstream revisions pin the palette provenance and packaged license texts (retrieved 2026-10-11).

| Source repository | Revision | License |
| --- | --- | --- |
| catppuccin/catppuccin | `d09787dd98ca6fba08af5ef2ae94a7e09f17daca` | MIT |
| dracula/dracula-theme | `e22ebcc048b8aff586f9bc94db30ff5347070b42` | MIT |
| morhetz/gruvbox | `ef8864bb42bf244f0295d1c5a403b27e3d139695` | MIT |
| nordtheme/nord | `1cef71605416a222e57225b544540ce0fcec18d4` | MIT |
| folke/tokyonight.nvim | `cdc07ac78467a233fd62c493de29a17e0cf2b2b6` | Apache-2.0 |
| altercation/solarized | `62f656a02f93c5190a8753159e34b385588d5ff3` | MIT |
| atom/one-dark-syntax | `9c96f4454362267ac45322063e193ccf9d2debb1` | MIT |
| rose-pine/rose-pine-palette | `92af52b465ab6e47437aca223c9b8d3009a2023b` | MIT |

All Material color roles are explicitly mapped. Surface containers mix the source panel with the base. Accents and body colors are adjusted by the smallest mix toward black or white that reaches 4.5:1 on the least contrasting neutral surface, including raised cards. Meaningful outlines reach 3:1. Solarized Light's canonical foreground needs adjustment. The original values remain available in the catalog; adjusted presentation never changes stored journal color enums. Domain hues use active palette families; severity retains labels and warning icons. Headings and compact metadata use system monospace, while entry and editor text use proportional system fonts. Android camera/photo picker styling and the fixed launch splash are controlled separately.

Gruvbox declares MIT in its pinned `package.json` (packaged alongside the standard MIT terms); its repository has no standalone LICENSE file. Tokyo Night’s full Apache-2.0 license is packaged unchanged.
