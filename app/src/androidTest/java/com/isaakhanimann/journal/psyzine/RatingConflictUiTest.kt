// PsyZine, 2026. GPL-3.0-or-later.
package com.isaakhanimann.journal.psyzine

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.isaakhanimann.journal.data.room.experiences.entities.ShulginRatingOption
import com.isaakhanimann.journal.ui.tabs.journal.experience.rating.add.AddRatingScreen
import com.isaakhanimann.journal.ui.theme.PsyZineTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDateTime

class RatingConflictUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun anotherOverallRatingAppearingLeavesTimedFallbackAvailableAndRetainsInputs() {
        val canAddOverall = mutableStateOf(true)
        val overall = mutableStateOf(true)
        val time = mutableStateOf(LocalDateTime.of(2026, 11, 11, 12, 0))
        val rating = mutableStateOf(ShulginRatingOption.TWO_PLUS)
        val originalTime = time.value
        compose.setContent {
            PsyZineTheme {
                AddRatingScreen(onDone = {}, selectedTime = time.value, onTimeChange = { time.value = it },
                    selectedRating = rating.value, onRatingChange = { rating.value = it },
                    canAddOverallRating = canAddOverall.value, isOverallRating = overall.value,
                    onChangeIsOverallRating = { overall.value = it })
            }
        }
        compose.onNodeWithText("Overall rating").assertExists()
        compose.runOnIdle { canAddOverall.value = false }
        compose.onNodeWithText("An overall rating is already saved. Turn off Overall rating to add a timed rating.")
            .assertExists()
        compose.onNodeWithText("Overall rating").performClick()
        compose.runOnIdle {
            assertFalse(overall.value)
            assertEquals(originalTime, time.value)
            assertEquals(ShulginRatingOption.TWO_PLUS, rating.value)
        }
        compose.onNodeWithText("Overall rating").assertDoesNotExist()
        compose.onNodeWithText("Show in timeline").assertExists()
    }
}
