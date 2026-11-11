// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.psyzine

import com.isaakhanimann.journal.ui.tabs.settings.about.SupportInfo
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class SupportInfoTest {
    private val info = SupportInfo("1.0.3", 4, "app.psyzine.debug", "direct", "debug", 37, 1.3f, 440)

    @Test fun reportContainsOnlyTheExplicitBuildPlatformAndScalingFields() {
        assertEquals("""PsyZine support info
Version: 1.0.3
Version code: 4
Package: app.psyzine.debug
Flavor: direct
Build type: debug
Android API: 37
Font scale: 130%
Display density: 440 dpi""", info.text())
        assertEquals(9, info.text().lines().size)
    }

    @Test fun unexpectedPathsUrlsControlCharactersAndOversizedMetadataAreNotExported() {
        val report = info.copy(versionName = "/data/user/private-journal", applicationId = "https://user:secret@example.test",
            flavor = "direct\nprivate note", buildType = "x".repeat(81)).text()
        listOf("/data", "private", "secret", "https", "example.test", "x".repeat(81)).forEach {
            assertFalse(report.contains(it))
        }
        assertEquals(9, report.lines().size)
        assertEquals(4, report.lines().count { it.endsWith(": unavailable") })
    }

    @Test fun invalidScalingAndPlatformValuesAreReadableRatherThanNaNOrInfinity() {
        for (scale in listOf(Float.NaN, Float.POSITIVE_INFINITY, -1f, 0f)) {
            val report = info.copy(fontScale = scale, densityDpi = -1, androidApi = -1, versionCode = 0).text()
            assertTrue(report.contains("Font scale: unavailable"))
            assertTrue(report.contains("Display density: unavailable"))
            assertTrue(report.contains("Android API: unavailable"))
            assertTrue(report.contains("Version code: unavailable"))
        }
    }

    @Test fun compatibilityVersionsAndFontPercentagesHaveStableFormattingAcrossLocales() {
        val old = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ar"))
            val report = info.copy(versionName = "1.0.3-compat.4", fontScale = 1.15f).text()
            assertTrue(report.contains("Version: 1.0.3-compat.4"))
            assertTrue(report.contains("Font scale: 115%"))
        } finally { Locale.setDefault(old) }
    }
}
