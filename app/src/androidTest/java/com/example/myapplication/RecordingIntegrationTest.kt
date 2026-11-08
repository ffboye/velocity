package com.example.myapplication

import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication.tracking.*
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecordingIntegrationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun shell(command: String) {
        val descriptor =
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use {
            it.readText()
        }
    }

    @Suppress("DEPRECATION")
    @Test
    fun gpsRecordingPauseFinishAndDiskPersistence() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = (context.applicationContext as VelocityApplication).journeys
        val locations = context.getSystemService(LocationManager::class.java)
        shell("pm grant ${context.packageName} android.permission.ACCESS_COARSE_LOCATION")
        shell("pm grant ${context.packageName} android.permission.ACCESS_FINE_LOCATION")
        shell("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
        shell("appops set ${context.packageName} android:mock_location allow")
        locations.addTestProvider(
            LocationManager.GPS_PROVIDER,
            false,
            false,
            false,
            false,
            true,
            true,
            true,
            Criteria.POWER_LOW,
            Criteria.ACCURACY_FINE,
        )
        locations.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)
        try {
            compose.activityRule.scenario.recreate()
            compose.waitUntil(10000) {
                repository.state.value.permission && !repository.state.value.loading
            }
            val origin = repository.state.value.fix
            val latitude = origin?.latitude ?: 52.0
            val longitude = origin?.longitude ?: 4.0
            fun inject(offset: Double) {
                locations.setTestProviderLocation(
                    LocationManager.GPS_PROVIDER,
                    Location(LocationManager.GPS_PROVIDER).apply {
                        this.latitude = latitude
                        this.longitude = longitude + offset
                        accuracy = 3f
                        speed = 10f
                        time = System.currentTimeMillis()
                        elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                    },
                )
            }
            compose.onNodeWithText("Start journey").performClick()
            compose.waitUntil(10000) { repository.state.value.recording == Recording.RECORDING }
            inject(0.0)
            compose.waitUntil(10000) { repository.state.value.points.isNotEmpty() }
            Thread.sleep(1100)
            inject(0.00015)
            compose.waitUntil(10000) { repository.state.value.distanceMeters > 2.0 }
            val beforeBackground = repository.state.value.distanceMeters
            compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            Thread.sleep(1100)
            inject(0.0003)
            compose.waitUntil(10000) {
                repository.state.value.distanceMeters > beforeBackground + 2.0
            }
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            val beforeLocationOff = repository.state.value.distanceMeters
            shell("cmd location set-location-enabled false")
            compose.waitUntil(10000) { !repository.state.value.gpsEnabled }
            assertFalse(repository.state.value.hasSignal)
            assertFalse(repository.state.value.gnssFresh)
            assertEquals(Recording.RECORDING, repository.state.value.recording)
            shell("cmd location set-location-enabled true")
            compose.waitUntil(10000) { repository.state.value.gpsEnabled }
            Thread.sleep(1100)
            inject(0.00045)
            compose.waitUntil(10000) { repository.state.value.hasSignal }
            assertEquals(beforeLocationOff, repository.state.value.distanceMeters, 0.01)
            // Play services can show its own LocationOffWarningActivity after the toggle.
            // Bring this test's existing Activity back to the foreground before UI actions.
            shell("am start -f 0x20000 -n ${context.packageName}/.MainActivity")
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            val beforePause = repository.state.value.distanceMeters
            compose.onNodeWithText("GPS", useUnmergedTree = true).performClick()
            compose.onNodeWithText("GPS connectivity").assertIsDisplayed()
            compose.onNodeWithContentDescription("Velocity location tracking").performClick()
            compose.waitUntil(10000) {
                !repository.state.value.trackingEnabled &&
                    repository.state.value.recording == Recording.PAUSED
            }
            assertFalse(repository.state.value.hasSignal)
            assertTrue(repository.state.value.gpsEnabled)
            Thread.sleep(1100)
            inject(0.0005)
            assertNull(repository.state.value.fix)
            compose.onNodeWithText("Turn tracking on").performScrollTo().performClick()
            compose.waitUntil(10000) { repository.state.value.trackingEnabled }
            assertEquals(Recording.PAUSED, repository.state.value.recording)
            compose.onNodeWithText("Resume", useUnmergedTree = true).performClick()
            compose.waitUntil(10000) { repository.state.value.recording == Recording.RECORDING }
            compose.onNodeWithText("Pause", useUnmergedTree = true).performClick()
            compose.waitUntil(10000) { repository.state.value.recording == Recording.PAUSED }
            compose.runOnIdle { assertFalse(repository.tryBeginInstallation()) }
            Thread.sleep(1100)
            inject(0.0006)
            compose.waitUntil(10000) { repository.state.value.fix?.longitude == longitude + 0.0006 }
            assertEquals(beforePause, repository.state.value.distanceMeters, 0.01)
            compose.onNodeWithText("Resume", useUnmergedTree = true).performClick()
            compose.waitUntil(10000) { repository.state.value.recording == Recording.RECORDING }
            val previousCount = repository.state.value.journeys.size
            compose.onNodeWithText("Finish & save", useUnmergedTree = true).performClick()
            compose.onNodeWithText("Journey name (optional)").performTextInput("Morning loop")
            compose.onNodeWithText("Notes (optional)").performTextInput("Along the river")
            compose.onNodeWithText("Save journey", useUnmergedTree = true).performClick()
            compose.waitUntil(10000) { repository.state.value.journeys.size > previousCount }
            val saved = repository.state.value.journeys.first()
            assertTrue(saved.distanceMeters > 2)
            assertTrue(saved.durationMillis > 0)
            assertEquals("Morning loop", saved.name)
            assertEquals("Along the river", saved.notes)
            assertTrue(saved.samples.isNotEmpty())
            compose.waitUntil(10000) {
                runCatching {
                        JourneyCodec.decodeAll(File(context.filesDir, "journeys.json").readText())
                            .journeys
                            .any { it.id == saved.id }
                    }
                    .getOrDefault(false)
            }
            assertEquals(Recording.IDLE, repository.state.value.recording)
            compose.runOnIdle {
                repository.persist()
                assertTrue(repository.state.value.saving)
                assertFalse(repository.tryBeginInstallation())
            }
            compose.waitUntil(10000) { !repository.state.value.saving }
            compose.runOnIdle {
                assertTrue(repository.tryBeginInstallation())
                assertFalse(repository.start())
                repository.endInstallation()
            }
            compose.onNodeWithText("Journeys", useUnmergedTree = true).performClick()
            compose.onNodeWithText("Morning loop").performScrollTo().performClick()
            compose.onNodeWithText("Edit name & notes").performClick()
            compose
                .onNodeWithText("Journey name (optional)")
                .performTextReplacement("River journey")
            compose.onNodeWithText("Save changes").performClick()
            compose.waitUntil(10000) {
                repository.state.value.journeys.first { it.id == saved.id }.name == "River journey"
            }
            compose.onNodeWithText("Speed over recorded time").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Done").performScrollTo().performClick()
            compose.runOnIdle { repository.delete(saved.id) }
        } finally {
            shell("cmd location set-location-enabled true")
            compose.runOnIdle {
                repository.tracking(true)
                if (repository.state.value.recording != Recording.IDLE) repository.finish()
            }
            locations.removeTestProvider(LocationManager.GPS_PROVIDER)
            shell("appops set ${context.packageName} android:mock_location default")
        }
    }
}
