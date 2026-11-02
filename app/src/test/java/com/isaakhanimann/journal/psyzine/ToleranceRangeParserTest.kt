package com.isaakhanimann.journal.psyzine

import com.isaakhanimann.journal.data.definitions.toleranceHours
import org.junit.Assert.*
import org.junit.Test

class ToleranceRangeParserTest {
    @Test fun explicitRangesUseExactDecimalMidpointsAndRecordedUnits() {
        assertEquals(252.0, toleranceHours("7 - 14 days")!!, 0.0)
        assertEquals(252.0, toleranceHours("1 - 2 weeks")!!, 0.0)
        assertEquals(1080.0, toleranceHours("1 - 2 months")!!, 0.0)
        assertEquals(0.15, toleranceHours("0.1 - 0.2 hours")!!, 0.0)
        assertEquals(24.0, toleranceHours(" 24 HOURS ")!!, 0.0)
    }
    @Test fun missingVagueReversedAndNonfiniteDescriptionsRemainUnknown() {
        listOf(null, "", "several weeks", "about 7 days", "14 - 7 days", "-1 day", "NaN hours", "1e9 days").forEach {
            assertNull("Unsupported description: $it", toleranceHours(it))
        }
    }
}
