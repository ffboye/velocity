package com.example.myapplication

import android.location.GnssStatus
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.location.GnssStatusCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.tracking.*
import com.example.myapplication.ui.GpsScreen
import com.example.myapplication.ui.theme.MyApplicationTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GpsConnectivityTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun satelliteReportReachesCountsAndSignalBars() {
        // Exercise the actual Android callback data adapter, including multi-band duplicates.
        val native =
            GnssStatus.Builder()
                .addSatellite(
                    GnssStatus.CONSTELLATION_GPS,
                    7,
                    42f,
                    45f,
                    90f,
                    true,
                    true,
                    true,
                    true,
                    1575420000f,
                    false,
                    0f,
                )
                .addSatellite(
                    GnssStatus.CONSTELLATION_GPS,
                    7,
                    30f,
                    45f,
                    90f,
                    true,
                    true,
                    false,
                    true,
                    1176450000f,
                    false,
                    0f,
                )
                .addSatellite(
                    GnssStatus.CONSTELLATION_GALILEO,
                    12,
                    35f,
                    20f,
                    180f,
                    true,
                    true,
                    false,
                    false,
                    0f,
                    false,
                    0f,
                )
                .build()
        val readings = GnssState.normalize(GnssStatusCompat.wrap(native).readSatellites())
        assertEquals(2, readings.size)
        val gnss = GnssState(ReceiverStatus.LISTENING, readings, 1000, 2500)
        assertEquals(1, gnss.usedCount)
        val state =
            DriveState(
                loading = false,
                permission = true,
                gpsEnabled = true,
                hasSignal = true,
                fix = GpsFix(52.0, 4.0, 7f, 1000, 1000, source = LocationSource.FUSED),
                fixAgeMillis = 500,
                gnss = gnss,
                gnssFresh = true,
            )
        show(state)
        compose.onNodeWithText("Ready for your journey").assertIsDisplayed()
        compose.onNodeWithText("±7").assertExists()
        compose.onNodeWithTag("gps-content").performScrollToNode(hasText("Satellite signals"))
        compose.onNodeWithText("Satellite signals").assertIsDisplayed()
        compose.onNodeWithTag("gps-content").performScrollToNode(hasText("GPS 7"))
        compose.onNodeWithText("GPS 7").assertIsDisplayed()
        compose.onNode(hasText("GPS 7") and hasText("42 dB-Hz")).assertIsDisplayed()
        compose.onNodeWithTag("gps-content").performScrollToNode(hasText("Galileo 12"))
        compose.onNodeWithText("Galileo 12").assertIsDisplayed()
        compose.onNodeWithTag("gps-content").performScrollToNode(hasText("Location source"))
        compose.onNodeWithText("Location source").assertIsDisplayed()
        compose.onNodeWithText("Fused location").assertIsDisplayed()
    }

    @Test
    fun missingSatelliteReportDoesNotBecomeFakeZeroCount() {
        show(
            DriveState(
                loading = false,
                permission = true,
                gpsEnabled = true,
                hasSignal = true,
                fix = GpsFix(52.0, 4.0, 7f, 1000, 1000, source = LocationSource.FUSED),
                gnss = GnssState(status = ReceiverStatus.UNAVAILABLE),
            )
        )
        compose.onNodeWithTag("gps-content").performScrollToNode(hasText("SATELLITE RECEIVER"))
        compose
            .onNodeWithText(
                "This device isn't reporting satellite details. Fused location can still work."
            )
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithContentDescription("Satellites in view: unavailable").assertExists()
        compose.onNodeWithContentDescription("Satellites used in fix: unavailable").assertExists()
        compose.onNodeWithText("Satellite signals").assertDoesNotExist()
    }

    private fun show(state: DriveState) {
        compose.setContent {
            MyApplicationTheme {
                GpsScreen(
                    state,
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                    {},
                    {},
                    {},
                    {},
                )
            }
        }
    }
}
