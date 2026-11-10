package com.example.myapplication

import android.os.ParcelFileDescriptor
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication.tracking.DriveState
import com.example.myapplication.ui.*
import com.example.myapplication.ui.theme.MyApplicationTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class MotionInteractionTest {
    private val duration =
        object : MotionDurationScale {
            override var scaleFactor = 1f
        }
    @get:Rule val compose = createComposeRule(effectContext = duration)

    private fun capture(name: String) {
        compose.waitForIdle()
        val file =
            InstrumentationRegistry.getInstrumentation()
                .uiAutomation
                .executeShellCommand("screencap -p /data/local/tmp/velocity-$name.png")
        ParcelFileDescriptor.AutoCloseInputStream(file).use { it.readBytes() }
    }

    @Test
    fun pressRespondsAndCancellationRestoresButtonWithoutInvokingAction() {
        var clicks = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MyApplicationTheme {
                Column(Modifier.padding(20.dp)) {
                    AppButton({ clicks++ }, Modifier.width(200.dp).testTag("motion-action")) {
                        Text("Action")
                    }
                    AppOutlinedButton({ clicks++ }, enabled = false) { Text("Disabled") }
                }
            }
        }
        compose.mainClock.advanceTimeBy(1000)
        val button = compose.onNodeWithTag("motion-action")
        val resting = button.fetchSemanticsNode().boundsInRoot
        button.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(160)
        assertTrue(button.fetchSemanticsNode().boundsInRoot.width < resting.width)
        capture("motion-pressed")
        button.performTouchInput { cancel() }
        compose.mainClock.advanceTimeBy(1000)
        assertEquals(resting.width, button.fetchSemanticsNode().boundsInRoot.width, 1f)
        assertEquals(0, clicks)
        button.performTouchInput { click() }
        compose.mainClock.advanceTimeBy(1000)
        assertEquals(1, clicks)
        compose.onNodeWithText("Disabled").performTouchInput { click() }
        assertEquals(1, clicks)
    }

    private fun app() {
        var page by mutableIntStateOf(0)
        val state = DriveState(loading = false)
        compose.setContent {
            MyApplicationTheme {
                VelocityApp(
                    state,
                    page,
                    { page = it },
                    onStart = {},
                    onPause = {},
                    onFinish = { _, _ -> },
                    onEnableLocation = {},
                    onGpsSettings = {},
                    onAppSettings = {},
                    onUnit = {},
                    onAwake = {},
                    onLimit = {},
                    onDelete = {},
                    onExport = {},
                    onDismissMessage = {},
                    onRetrySave = {},
                )
            }
        }
    }

    @Test
    fun rapidTabChangesExposeOnlyTheCurrentPageDuringMotion() {
        compose.mainClock.autoAdvance = false
        app()
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithText("Settings", useUnmergedTree = true).performClick()
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithText("Measurement units").assertExists()
        compose.onNodeWithText("Speedometer").assertDoesNotExist()
        compose.onNodeWithText("Journeys", useUnmergedTree = true).performClick()
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithText("Your journeys").assertExists()
        compose.onNodeWithText("Measurement units").assertDoesNotExist()
        compose.onNodeWithText("Speed", useUnmergedTree = true).performClick()
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithText("Speedometer").assertIsDisplayed()
        compose.onNodeWithText("Start journey").assertIsDisplayed()
    }

    @Test
    fun disabledMotionSettlesOnTheNextFrameAndKeepsNavigationUsable() {
        duration.scaleFactor = 0f
        compose.mainClock.autoAdvance = false
        app()
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithText("Settings", useUnmergedTree = true).performClick()
        compose.mainClock.advanceTimeBy(48)
        val heading = compose.onNodeWithText("Measurement units")
        heading.assertIsDisplayed()
        val position = heading.fetchSemanticsNode().boundsInRoot.top
        compose.mainClock.advanceTimeBy(1000)
        assertEquals(position, heading.fetchSemanticsNode().boundsInRoot.top, 0.5f)
        compose.onNodeWithText("Speed", useUnmergedTree = true).performClick()
        compose.mainClock.advanceTimeBy(48)
        compose.onNodeWithText("Start journey").assertIsDisplayed()
        capture("motion-disabled")
    }
}
