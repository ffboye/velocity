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
        compose.waitUntil(10000) { compose.onNodeWithText("Start journey").isDisplayed() }
        compose.onNodeWithText("Speedometer").assertExists()
        compose.onNodeWithText("Map", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Journeys", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Your journeys").assertIsDisplayed()
        compose.onNodeWithText("Settings", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Measurement units").assertIsDisplayed()
        compose.onNodeWithText("App Updates").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Check for Updates").assertExists()
        compose.onNodeWithText("Measurement units").performScrollTo()
        compose.onNodeWithText("mph · mi").performClick()
        compose.onNodeWithText("Speed", useUnmergedTree = true).performClick()
        compose.onNodeWithText("MPH").assertExists()
        compose.onNodeWithText("GPS", useUnmergedTree = true).performClick()
        compose.onNodeWithText("GPS connectivity").assertIsDisplayed()
        compose.onNodeWithTag("gps-content").performScrollToNode(hasText("SATELLITE RECEIVER"))
        compose.onNodeWithText("SATELLITE RECEIVER").assertIsDisplayed()
        compose.onNodeWithTag("gps-content").performScrollToNode(hasText("LOCATION DETAILS"))
        compose.onNodeWithText("LOCATION DETAILS").assertIsDisplayed()
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            .uiAutomation
            .performGlobalAction(
                android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK
            )
        compose.waitUntil(10000) { compose.onNodeWithText("Start journey").isDisplayed() }
    }

    @Test
    fun offlineGuideAndAreaPickerLiveInSettings() {
        val context =
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val maps = (context.applicationContext as VelocityApplication).maps
        val oldEnabled = maps.state.value.enabled
        val oldWifiOnly = maps.state.value.wifiOnly
        try {
            compose.waitUntil(10000) { !maps.state.value.loading }
            compose.runOnIdle { maps.enable(false) }
            compose.onNodeWithText("Settings", useUnmergedTree = true).performClick()
            compose.onNodeWithText("Offline maps").performScrollTo().assertIsDisplayed()
            compose
                .onNodeWithText("Journeys record without internet.", substring = true)
                .assertExists()
            compose.onNodeWithText("Choose area to download").assertDoesNotExist()
            compose.onNodeWithText("How offline maps work").performScrollTo().performClick()
            compose
                .onNodeWithText("1. Enable offline maps", substring = true)
                .performScrollTo()
                .assertIsDisplayed()
            compose.onNodeWithText("Hide offline guide").performScrollTo().performClick()
            compose
                .onNodeWithContentDescription("Enable offline map downloads")
                .performScrollTo()
                .performClick()
            compose.waitUntil(10000) { maps.state.value.enabled }
            compose
                .onNodeWithContentDescription("Download maps over Wi-Fi only")
                .performScrollTo()
                .assertExists()
            compose.onNodeWithTag("offline-map-settings").performScrollTo()
            val screenshot =
                androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                    .uiAutomation
                    .executeShellCommand(
                        "screencap -p /data/local/tmp/velocity-offline-settings.png"
                    )
            android.os.ParcelFileDescriptor.AutoCloseInputStream(screenshot).use { it.readBytes() }
            compose.onNodeWithText("Choose area to download").performScrollTo().performClick()
            compose.onNodeWithText("Choose offline area").assertIsDisplayed()
            compose.onNodeWithText("Download area").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Area name (optional)").performTextInput("Weekend area")
            compose.activityRule.scenario.recreate()
            compose.onNodeWithText("Choose offline area").assertIsDisplayed()
            compose.onNodeWithText("Weekend area").assertExists()
            compose.onNodeWithText("Done").performClick()
            compose.onNodeWithText("Measurement units").performScrollTo().assertIsDisplayed()
        } finally {
            compose.runOnIdle {
                maps.enable(oldEnabled)
                maps.wifiOnly(oldWifiOnly)
            }
        }
    }
}
