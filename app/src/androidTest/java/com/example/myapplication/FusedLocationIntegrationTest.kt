package com.example.myapplication

import android.content.Intent
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication.tracking.*
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Tasks
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FusedLocationIntegrationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun shell(command: String) {
        val descriptor =
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
    }

    @Suppress("DEPRECATION")
    @Test
    fun fusedLocationWorksWithoutRawGpsAndRetryKeepsJourney() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = (context.applicationContext as VelocityApplication).journeys
        val locations = context.getSystemService(LocationManager::class.java)
        val fused = LocationServices.getFusedLocationProviderClient(context)
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
        locations.setTestProviderEnabled(LocationManager.GPS_PROVIDER, false)
        var savedId: String? = null
        try {
            Tasks.await(fused.setMockMode(true), 20, TimeUnit.SECONDS)
            compose.activityRule.scenario.recreate()
            compose.waitUntil(10000) {
                repository.state.value.permission && !repository.state.value.loading
            }
            compose.runOnIdle { repository.resetSignal() }
            fun inject(longitude: Double, accuracy: Float = 3f) {
                Tasks.await(
                    fused.setMockLocation(
                        Location("fused").apply {
                            latitude = 52.0
                            this.longitude = longitude
                            this.accuracy = accuracy
                            speed = 10f
                            time = System.currentTimeMillis()
                            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                        }
                    ),
                    10,
                    TimeUnit.SECONDS,
                )
            }
            fun feedUntil(longitude: Double, accuracy: Float = 3f, condition: () -> Boolean) {
                val deadline = SystemClock.elapsedRealtime() + 20000
                // Registration and retry are asynchronous. A single fix can arrive before
                // registration and correctly be rejected as a cached fix by Play services.
                do {
                    inject(longitude, accuracy)
                    Thread.sleep(1100)
                    if (condition()) return
                } while (SystemClock.elapsedRealtime() < deadline)
                fail("Fresh fused fixes were not observed within 20 seconds")
            }
            // Raw GPS is disabled: every observed position here must arrive via Play services.
            feedUntil(4.0, 250f) { repository.state.value.searchAccuracy == 250f }
            assertFalse(repository.state.value.hasSignal)
            assertEquals(0.0, repository.state.value.speed, 0.0)
            compose.onNodeWithText("Retry GPS").performScrollTo().performClick()
            feedUntil(4.0) { repository.state.value.hasSignal }
            compose.onNodeWithText("Start journey").performClick()
            compose.waitUntil(10000) { repository.state.value.recording == Recording.RECORDING }
            feedUntil(4.0) { repository.state.value.points.isNotEmpty() }
            feedUntil(4.00015) { repository.state.value.distanceMeters > 2.0 }
            val before = repository.state.value.distanceMeters
            compose.runOnIdle {
                compose.activity.startService(
                    Intent(context, TrackingService::class.java).setAction(TrackingService.RETRY)
                )
            }
            compose.waitUntil(10000) { !repository.state.value.hasSignal }
            feedUntil(4.001) { repository.state.value.hasSignal }
            assertEquals(Recording.RECORDING, repository.state.value.recording)
            assertEquals(before, repository.state.value.distanceMeters, 0.01)
            compose.runOnIdle {
                compose.activity.startService(
                    Intent(context, TrackingService::class.java).setAction(TrackingService.FINISH)
                )
            }
            compose.waitUntil(10000) { repository.state.value.recording == Recording.IDLE }
            savedId = repository.state.value.journeys.first().id
        } finally {
            compose.runOnIdle {
                if (repository.state.value.recording != Recording.IDLE) {
                    repository.finish()
                    savedId = repository.state.value.journeys.first().id
                }
                savedId?.let(repository::delete)
            }
            Tasks.await(fused.setMockMode(false), 10, TimeUnit.SECONDS)
            locations.removeTestProvider(LocationManager.GPS_PROVIDER)
            shell("appops set ${context.packageName} android:mock_location default")
        }
    }
}
