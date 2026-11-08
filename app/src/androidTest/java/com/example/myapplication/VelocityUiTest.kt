package com.example.myapplication

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VelocityUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun appOpensOnSpeedometerAndHasWorkingNavigation() {
        compose.onNodeWithText("Start journey").assertIsDisplayed()
        compose.onNodeWithText("YOUR PACE. YOUR JOURNEY.").assertExists()
        compose.onNodeWithText("Journeys", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Your journeys").assertIsDisplayed()
        compose.onNodeWithText("Settings", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Measurement units").assertIsDisplayed()
        compose.onNodeWithText("App Updates").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("ffboye/velocity · GitHub Releases").assertExists()
        compose.onNodeWithText("Measurement units").performScrollTo()
        compose.onNodeWithText("mph · mi").performClick()
        compose.onNodeWithText("Speedometer", useUnmergedTree = true).performClick()
        compose.onNodeWithText("MPH").assertExists()
        compose.onNodeWithText("GPS", useUnmergedTree = true).performClick()
        compose.onNodeWithText("GPS connectivity").assertIsDisplayed()
        compose.onNodeWithText("SATELLITE RECEIVER").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("gps-content").performScrollToNode(hasText("HOW VELOCITY FINDS YOU"))
        compose.onNodeWithText("HOW VELOCITY FINDS YOU").assertIsDisplayed()
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            .uiAutomation
            .performGlobalAction(
                android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK
            )
        compose.onNodeWithText("Start journey").assertIsDisplayed()
    }
}
