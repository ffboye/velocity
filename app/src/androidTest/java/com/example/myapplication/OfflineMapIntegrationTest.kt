package com.example.myapplication

import android.graphics.RectF
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication.maps.MapArea
import com.example.myapplication.tracking.*
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

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
        lateinit var map: MapLibreMap
        val loaded = AtomicBoolean(false)
        val offlineRendered = AtomicBoolean(false)
        try {
            compose.waitUntil(15000) {
                !controller.state.value.loading && !app.journeys.state.value.loading
            }
            compose.runOnIdle { assertTrue(app.journeys.restore(listOf(trip))) }
            compose.onNodeWithText("Map", useUnmergedTree = true).performClick()
            compose.onNodeWithText("Your map").assertIsDisplayed()
            compose.runOnIdle {
                view = mapView(compose.activity.window.decorView) ?: error("MapView missing")
                view.getMapAsync { ready ->
                    map = ready
                    ready.getStyle { style ->
                        loaded.set(style.getLayer("velocity-route-line") != null)
                    }
                }
            }
            compose.waitUntil(60000) { loaded.get() }
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
            compose.runOnIdle {
                MapLibre.setConnected(false)
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(52.365, 4.905), 14.0))
                view.addOnDidFinishRenderingMapListener { fully ->
                    if (fully && map.style?.isFullyLoaded == true) {
                        offlineRendered.set(
                            map.queryRenderedFeatures(
                                    RectF(0f, 0f, view.width.toFloat(), view.height.toFloat())
                                )
                                .size > 5
                        )
                    }
                }
                map.setStyle(Style.Builder().fromUri(MapArea.STYLE)) {}
            }
            compose.waitUntil(60000) { offlineRendered.get() }
            val screenshot =
                InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            File(context.getExternalFilesDir(null), "velocity-offline-map.png").outputStream().use {
                screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            compose.runOnIdle {
                MapLibre.setConnected(true)
                controller.delete(area.id)
            }
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
