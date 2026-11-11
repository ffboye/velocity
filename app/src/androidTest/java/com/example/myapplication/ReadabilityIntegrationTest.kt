package com.example.myapplication

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReadabilityIntegrationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun shell(command: String) {
        val descriptor =
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        // Semantics can lead the rendered surface during a page transition.
        Thread.sleep(300)
        shell("screencap -p /data/local/tmp/velocity-readability-$name.png")
    }

    @Test
    fun easyReadingIsRememberedAndCriticalControlsFitWithLargeSystemText() {
        // This case changes device text size; never modify a physical phone's setting.
        assumeTrue(Build.HARDWARE in listOf("ranchu", "goldfish"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as VelocityApplication
        val repository = app.journeys
        val previousReading = repository.state.value.easyReading
        val previousOffline = app.maps.state.value.enabled
        val previousScale =
            Settings.System.getFloat(context.contentResolver, Settings.System.FONT_SCALE, 1f)
        try {
            compose.runOnIdle { repository.easyReading(false) }
            compose.waitUntil(10000) { compose.onNodeWithText("Start journey").isDisplayed() }
            compose.onNodeWithText("Start journey").assertHeightIsAtLeast(64.dp)
            screenshot("standard")
            compose.onNodeWithText("Settings", useUnmergedTree = true).performClick()
            compose.onNodeWithContentDescription("Large text mode").performScrollTo().performClick()
            compose.waitUntil(10000) { repository.state.value.easyReading }
            assertTrue(
                context
                    .getSharedPreferences("velocity_preferences", Context.MODE_PRIVATE)
                    .getBoolean("easy_reading", false)
            )
            compose.activityRule.scenario.recreate()
            compose.onNodeWithContentDescription("Large text mode").assertIsOn()
            shell("settings put system font_scale 1.5")
            compose.waitUntil(15000) { compose.activity.resources.configuration.fontScale >= 1.49f }
            compose.onNodeWithContentDescription("Large text mode").assertIsOn()
            compose.onNodeWithText("Speed", useUnmergedTree = true).performClick()
            compose.waitUntil(15000) {
                compose.onNodeWithText("Start journey").isDisplayed() &&
                    compose
                        .onNodeWithText("Start journey")
                        .fetchSemanticsNode()
                        .layoutInfo
                        .density
                        .fontScale >= 1.70f
            }
            compose.onNodeWithText("Start journey").assertIsDisplayed().assertHeightIsAtLeast(72.dp)
            for (label in listOf("Speed", "Journeys", "Settings")) compose
                .onNodeWithText(label, useUnmergedTree = true)
                .assertIsDisplayed()
            screenshot("large")
            compose.onNodeWithText("Settings", useUnmergedTree = true).performClick()
            compose.waitUntil(10000) { !app.maps.state.value.loading }
            compose.runOnIdle { app.maps.enable(true) }
            compose.onNodeWithText("Choose area to download").performScrollTo().performClick()
            compose.onNodeWithText("Download area").assertIsDisplayed().assertHeightIsAtLeast(72.dp)
            compose.onNodeWithText("Done").assertIsDisplayed().performClick()
        } finally {
            shell("settings put system font_scale $previousScale")
            compose.waitUntil(15000) {
                kotlin.math.abs(
                    compose.activity.resources.configuration.fontScale - previousScale
                ) < 0.01f
            }
            compose.runOnIdle {
                repository.easyReading(previousReading)
                app.maps.enable(previousOffline)
            }
        }
    }
}
