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
            val beforePause = repository.state.value.distanceMeters
            compose.onNodeWithText("Pause", useUnmergedTree = true).performClick()
            compose.waitUntil(10000) { repository.state.value.recording == Recording.PAUSED }
            compose.runOnIdle { assertFalse(repository.tryBeginInstallation()) }
            Thread.sleep(1100)
            inject(0.00045)
            compose.waitUntil(10000) {
                repository.state.value.fix?.longitude == longitude + 0.00045
            }
            assertEquals(beforePause, repository.state.value.distanceMeters, 0.01)
            compose.onNodeWithText("Resume", useUnmergedTree = true).performClick()
            compose.waitUntil(10000) { repository.state.value.recording == Recording.RECORDING }
            val previousCount = repository.state.value.journeys.size
            compose.onNodeWithText("Finish & save", useUnmergedTree = true).performClick()
            compose.onNodeWithText("Save journey", useUnmergedTree = true).performClick()
            compose.waitUntil(10000) { repository.state.value.journeys.size > previousCount }
            val saved = repository.state.value.journeys.first()
            assertTrue(saved.distanceMeters > 2)
            assertTrue(saved.durationMillis > 0)
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
                assertTrue(repository.tryBeginInstallation())
                assertFalse(repository.start())
                repository.endInstallation()
            }
            compose.runOnIdle { repository.delete(saved.id) }
        } finally {
            compose.runOnIdle {
                if (repository.state.value.recording != Recording.IDLE) repository.finish()
            }
            locations.removeTestProvider(LocationManager.GPS_PROVIDER)
            shell("appops set ${context.packageName} android:mock_location default")
        }
    }
}
