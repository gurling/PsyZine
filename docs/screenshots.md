# PsyZine screenshots

`PsyZineUiRegressionTest.captureEveryPaletteForVisualReview` renders synthetic preview cards in all 13 palettes and saves PNGs to the target app’s external-files `parity-screenshots` directory. Android CI pulls these into its per-API artifacts for visual review. All 13 PNGs were generated on the Pixel and the six CI suites passed. Use `-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true` to retain target external files for the subsequent ADB pull. Synthetic palette output does not establish complete screen or clipping acceptance. They are test fixtures, not screenshots of a user’s private journal.

After device acceptance, capture the actual journal/detail with synthetic photo fixtures, photo editor acquisition/error/discard states, full-screen viewer, Appearance light/dark variants, notification collapsed/expanded/private-lock-screen states, widget small/large/empty/private states, and tablet navigation. Review large text, touch targets, contrast, and clipping. Add approved current phone captures to `metadata/en-US/images/phoneScreenshots/`. Keep real private journals out of captures.

Upstream screenshots were removed from the fork listing because they do not show PsyZine. No generated mockup is presented as a device capture and no visual pass is claimed before the review.
