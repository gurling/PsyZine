# Third-party notices

PsyZine is GPL-3.0-or-later. Copyright 2022-2025 Isaak Hanimann and contributors.
PsyZine modifications dated 2026-10-11. The full GPL is packaged as COPYING.
Based on PsychonautWiki Journal baseline 8224449a62e26ffd448aade0a7079b005fdd9d37.
PsyLog ports: https://codeberg.org/PsyLog/PsyLog-android at 0c29a70fa1ced6106c8379b2c02541699d61f966; copyright 2025–2026 PsyLog contributors, GPL-3.0-or-later.

Palette sources: Catppuccin, Dracula, Gruvbox, Nord, Solarized, One Dark, Rose Pine (MIT); Tokyo Night (Apache-2.0). Licenses are packaged alongside this notice. See docs/theme-palettes.md for revisions.

Libraries: AndroidX/Compose/Room/DataStore/Glance and Coil (Apache-2.0); Kotlin and kotlinx (Apache-2.0); Dagger/Hilt (Apache-2.0); Kizitonwose Calendar (MIT). Dependency notices are recorded in THIRD_PARTY_NOTICES.md.

Reference information is attributed to PsychonautWiki, TripSit, and DIZ/saferparty.ch. Its rights are separate from the application code. Attribution does not establish redistribution permission; production binaries remain subject to the content rights review. The owner-approved compatibility testing exception permits testing pre-releases after the documented signing and verification gates; it does not establish redistribution clearance.

Full palette license texts are in `app/src/main/assets/licenses/`. All upstream copyright headers and the complete GPL text are retained. New dependencies must be reviewed against this record before publication.

The offline notice lists the pinned runtime library versions and source repositories. Full Apache-2.0, Calendar MIT, palette MIT, and Tokyo Night Apache-2.0 texts are packaged. No Tokyo Night source code was copied; only its palette values are mapped, with its original license retained. Glance and its AndroidX/WorkManager dependencies are Apache-2.0; Coil is used without network fetchers. Build/test tools (Gradle, AGP, Kotlin compiler, Room compiler, Hilt compiler, Android test tools, JUnit, and JSON-java) are not journal services. Their build distributions retain their own notices. JSON-java 20240303 is a test-only public-domain library.
