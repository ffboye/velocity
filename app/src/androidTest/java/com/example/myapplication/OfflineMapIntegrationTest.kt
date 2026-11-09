package com.example.myapplication

import android.graphics.RectF
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication.maps.MapArea
import com.example.myapplication.tracking.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

@RunWith(AndroidJUnit4::class)
class OfflineMapIntegrationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun streetMapOfflineDownloadRenderingAndDeletion() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as VelocityApplication
        val controller = app.maps
        val oldEnabled = controller.state.value.enabled
        val oldWifi = controller.state.value.wifiOnly
        var offlineId: Long? = null
        val trip =
            Journey(
                "map-verification",
                System.currentTimeMillis(),
                10000,
                120.0,
                12.0,
                listOf(
                    RoutePoint(52.366, 4.901, 1000, 0),
                    RoutePoint(52.367, 4.903, 2000, 0),
                    RoutePoint(52.369, 4.905, 3000, 1),
                    RoutePoint(52.370, 4.907, 4000, 1),
                ),
                name = "Map verification",
            )
        fun mapView(view: View): MapView? {
            if (view is MapView) return view
            if (view is ViewGroup)
                for (index in 0 until view.childCount) mapView(view.getChildAt(index))?.let {
                    return it
                }
            return null
        }
        lateinit var view: MapView
        var map: MapLibreMap? = null
        fun openRoute() {
            compose.onNodeWithText("Journeys", useUnmergedTree = true).performClick()
            compose
                .onNodeWithTag("journeys-content")
                .performScrollToNode(hasText("Map verification"))
            compose.onNodeWithText("Map verification").performClick()
            compose
                .onNodeWithText("Route", useUnmergedTree = true)
                .performScrollTo()
                .assertIsDisplayed()
            map = null
            compose.runOnIdle {
                view =
                    WindowInspector.getGlobalWindowViews().firstNotNullOfOrNull { mapView(it) }
                        ?: error("Journey MapView missing")
                view.getMapAsync { map = it }
            }
            compose.waitUntil(60000) {
                var rendered = false
                InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    val ready = map
                    if (ready?.style?.isFullyLoaded == true && view.width > 0) {
                        val bounds = RectF(0f, 0f, view.width.toFloat(), view.height.toFloat())
                        rendered =
                            ready.queryRenderedFeatures(bounds).size > 5 &&
                                ready
                                    .queryRenderedFeatures(bounds, "velocity-route-line")
                                    .isNotEmpty()
                    }
                }
                rendered
            }
        }
        try {
            compose.waitUntil(15000) {
                !controller.state.value.loading && !app.journeys.state.value.loading
            }
            compose.runOnIdle { assertTrue(app.journeys.restore(listOf(trip))) }
            compose.onNodeWithText("Map", useUnmergedTree = true).assertDoesNotExist()
            openRoute()
            compose.runOnIdle {
                controller.enable(true)
                controller.wifiOnly(false)
                controller.foreground(false)
                controller.download(MapArea(52.38, 4.93, 52.35, 4.88), "Offline verification")
            }
            compose.waitUntil(20000) {
                controller.state.value.areas.any { it.name == "Offline verification" }
            }
            val area = controller.state.value.areas.first { it.name == "Offline verification" }
            offlineId = area.id
            assertFalse(area.downloading)
            compose.runOnIdle {
                controller.foreground(true)
                controller.resume(area.id)
            }
            compose.waitUntil(180000) {
                controller.state.value.areas.first { it.id == area.id }.complete
            }
            val complete = controller.state.value.areas.first { it.id == area.id }
            assertTrue(complete.bytes > 0)
            assertTrue(complete.bytes <= MapArea.MAX_BYTES)
            compose.onNodeWithText("Done").performClick()
            compose.onNodeWithText("Settings", useUnmergedTree = true).performClick()
            compose
                .onNodeWithContentDescription("Enable offline map downloads")
                .performScrollTo()
                .performClick()
            compose.waitUntil(10000) { !controller.state.value.enabled }
            compose
                .onNodeWithText("Available offline", substring = true)
                .performScrollTo()
                .assertIsDisplayed()
            assertTrue(controller.state.value.areas.any { it.id == area.id })
            compose.runOnIdle { MapLibre.setConnected(false) }
            // Recreate the actual journey map while offline and downloads are switched off.
            openRoute()
            val screenshot =
                InstrumentationRegistry.getInstrumentation()
                    .uiAutomation
                    .executeShellCommand("screencap -p /data/local/tmp/velocity-journey-route.png")
            android.os.ParcelFileDescriptor.AutoCloseInputStream(screenshot).use { it.readBytes() }
            compose.onNodeWithText("Done").performClick()
            compose.onNodeWithText("Settings", useUnmergedTree = true).performClick()
            compose.onNodeWithText("Delete map").performScrollTo().performClick()
            compose.onNodeWithText("Delete downloaded map?").assertIsDisplayed()
            compose.onNodeWithText("Delete", useUnmergedTree = true).performClick()
            compose.waitUntil(20000) { controller.state.value.areas.none { it.id == area.id } }
            assertTrue(app.journeys.state.value.journeys.any { it.id == trip.id })
        } finally {
            compose.runOnIdle {
                MapLibre.setConnected(true)
                offlineId?.let(controller::delete)
                controller.enable(oldEnabled)
                controller.wifiOnly(oldWifi)
                app.journeys.delete(trip.id)
            }
        }
    }
}
