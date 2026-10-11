# PsyZine screenshots

`PsyZineUiRegressionTest.captureEveryPaletteForVisualReview` renders synthetic preview cards in all 13 palettes and saves PNGs to the target app’s external-files `parity-screenshots` directory. Android CI pulls these into its per-API artifacts for visual review. These tests and captures are configured but have not run here. They are test fixtures, not screenshots of a user’s private journal.

After device acceptance, capture the actual journal/detail with synthetic photo fixtures, photo editor acquisition/error/discard states, full-screen viewer, Appearance light/dark variants, notification collapsed/expanded/private-lock-screen states, widget small/large/empty/private states, and tablet navigation. Review large text, touch targets, contrast, and clipping. Add approved current phone captures to `metadata/en-US/images/phoneScreenshots/`. Keep real private journals out of captures.

Upstream screenshots were removed from the fork listing because they do not show PsyZine. No generated mockup is presented as a device capture and no visual pass is claimed before the review.
