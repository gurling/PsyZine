// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.psyzine

import com.isaakhanimann.journal.data.substances.classes.roa.*
import org.junit.Assert.*
import org.junit.Test

class DoseUnitsTest {
    @Test fun documentedMassConversionsRetainSmallQuantitiesAndMicrogramAliases() {
        assertEquals(20.0, convertDoseUnits(0.02, "g", "mg")!!, 0.0)
        assertEquals(0.0001, convertDoseUnits(0.1, "µg", "mg")!!, 0.0)
        for (unit in listOf("µg", "μg", "ug", "mcg")) {
            assertEquals(1000.0, convertDoseUnits(1.0, "mg", unit)!!, 0.0)
        }
        assertEquals(2.0, convertDoseUnits(2.0, " capsule ", "capsule")!!, 0.0)
        assertEquals(0.0, convertDoseUnits(0.0, "g", "µg")!!, 0.0)
    }

    @Test fun unrelatedAndInvalidUnitsNeverInventConversions() {
        for ((from, to) in listOf("ml" to "mg", "capsule" to "mg", "mg THC" to "mg", "Mg" to "mg", "" to "", "drop" to "drops")) {
            assertNull(convertDoseUnits(1.0, from, to))
        }
        for (amount in listOf(null, -1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertNull(convertDoseUnits(amount, "mg", "mg"))
        }
        assertNull(convertDoseUnits(Double.MAX_VALUE, "g", "mg"))
        assertNull(convertDoseUnits(Double.MIN_VALUE, "µg", "g"))
    }

    @Test fun doseDotsRejectInvalidAndUnrepresentableInputsWithoutCrashing() {
        val reference = RoaDose("mg", 10.0, 20.0, 30.0, 40.0)
        for (amount in listOf(null, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertNull(reference.getDoseClass(amount))
            assertNull(reference.getNumDots(amount))
        }
        assertNull(reference.getNumDots(Double.MAX_VALUE))
        assertNull(RoaDose("mg", null, null, null, 0.0).getNumDots(0.0))
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 8), listOf(0.0, 10.0, 20.0, 30.0, 40.0, 50.0, 80.0).map(reference::getNumDots))
    }
}
