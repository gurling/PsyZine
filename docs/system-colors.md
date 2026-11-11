# Android system colors

Appearance includes an optional **Android system colors** selection. It uses
Android's wallpaper-based Material colors on Android 12/API 31 or newer and
follows system light/dark mode. The existing **Follow system** option retains its
Catppuccin Latte/Mocha behavior, and all thirteen fixed palettes remain intact.

Older devices, unavailable platform color resources and callers without an
Android context use the existing Latte/Mocha fallback. The preview explains
when fallback is active. Selection uses the existing stable appearance key and
portable backup theme field; no schema or backup-format change is needed.
Older PsyZine builds that do not recognize this new theme key may reject a
backup containing it; restore those backups with a build supporting the option.

Compose, the live notification and Glance use one palette resolver. Compose
reloads on configuration changes; notifications and widgets resolve current
resources on their normal refresh paths. No wallpaper image, permission or
network request is required. Substance and warning colors retain their existing
semantic hue families, adjusted for readable contrast against the new surfaces.
Material action text, body text, containers and outlines receive the existing
contrast treatment as well.

The platform API requirements were checked through Context7 against the
[official Compose Material documentation](https://developer.android.com/reference/kotlin/androidx/compose/material3/package-summary).

## Focused checks — 11 November 2026

Twenty-five JVM tests passed: four system-color adapter tests, five existing
palette tests and sixteen preference tests. They cover unchanged fixed-palette
defaults, resource-free fallback, light/dark surface mapping, action/domain/text
contrast, portable theme-key serialization and persisted selection. The Direct
debug APK built and instrumentation sources compiled. Diff checks passed.

The tests use synthetic Material schemes; they do not establish actual wallpaper
resource loading, wallpaper-change refresh, older-device fallback, notification
or widget rendering, TalkBack or physical-device appearance. Those runtime paths
have not been exercised for this batch.
