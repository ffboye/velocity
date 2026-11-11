package com.example.myapplication

import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication.tracking.*
import java.io.File
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RedesignPersistenceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun appearanceFeedbackPreferencesAndJourneyTagsSurviveRecreation() {
        assumeTrue(Build.HARDWARE in listOf("ranchu", "goldfish"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repo = (context.applicationContext as VelocityApplication).journeys
        compose.waitUntil(15000) { !repo.state.value.loading && !repo.state.value.saving }
        val before = repo.state.value
        val journey =
            Journey(
                "redesign-check",
                1700000000000,
                1000,
                25.0,
                12.0,
                listOf(RoutePoint(52.0, 4.0, 1700000000000, 0)),
                name = "Design check",
            )
        try {
            compose.runOnIdle {
                repo.easyReading(false)
                repo.daylight(false)
                repo.tapHaptics(false)
                repo.tripHaptics(false)
                repo.distanceChimes(false)
                assertTrue(repo.restore(listOf(journey)))
                repo.message(null)
            }
            compose.onNodeWithText("Settings", useUnmergedTree = true).performClick()
            compose
                .onNodeWithContentDescription("High-contrast daylight mode")
                .performScrollTo()
                .performClick()
            compose.waitUntil(10000) { repo.state.value.daylight }
            compose.waitForIdle()
            val pixels = compose.onRoot().captureToImage().toPixelMap()
            assertTrue(
                "Daylight must change the actual page background",
                pixels[2, pixels.height / 2].luminance() > 0.8f,
            )
            compose
                .onNodeWithContentDescription("Haptic feedback on tap")
                .performScrollTo()
                .performClick()
            compose.onNodeWithContentDescription("Trip haptics").performScrollTo().performClick()
            compose.onNodeWithContentDescription("Distance chimes").performScrollTo().performClick()
            compose.waitUntil(10000) {
                repo.state.value.tapHaptics &&
                    repo.state.value.tripHaptics &&
                    repo.state.value.distanceChimes
            }
            compose.activityRule.scenario.recreate()
            compose
                .onNodeWithContentDescription("High-contrast daylight mode")
                .performScrollTo()
                .assertIsOn()
            compose.onNodeWithContentDescription("Distance chimes").performScrollTo().assertIsOn()
            compose.onNodeWithText("Journeys", useUnmergedTree = true).performClick()
            compose.onNodeWithTag("journeys-content").performScrollToNode(hasText(journey.name))
            compose.onNodeWithText(journey.name).performClick()
            compose.onNodeWithText("Edit name & notes").performScrollTo().performClick()
            compose.onNodeWithText("Commute").performScrollTo().performClick()
            compose.onNodeWithText("Cycling").performScrollTo().performClick()
            compose.onNodeWithText("Save changes").performClick()
            compose.waitUntil(15000) {
                repo.state.value.journeys.first { it.id == journey.id }.tags ==
                    listOf("Commute", "Cycling") && !repo.state.value.saving
            }
            val disk =
                JourneyCodec.decodeAll(File(context.filesDir, "journeys.json").readText())
                    .journeys
                    .first { it.id == journey.id }
            assertEquals(listOf("Commute", "Cycling"), disk.tags)
            val file =
                InstrumentationRegistry.getInstrumentation()
                    .uiAutomation
                    .executeShellCommand("screencap -p /data/local/tmp/velocity-daylight-tags.png")
            ParcelFileDescriptor.AutoCloseInputStream(file).use { it.readBytes() }
        } finally {
            compose.runOnIdle {
                repo.delete(journey.id)
                repo.easyReading(before.easyReading)
                repo.daylight(before.daylight)
                repo.tapHaptics(before.tapHaptics)
                repo.tripHaptics(before.tripHaptics)
                repo.distanceChimes(before.distanceChimes)
            }
            compose.waitUntil(15000) { !repo.state.value.saving }
        }
    }
}
