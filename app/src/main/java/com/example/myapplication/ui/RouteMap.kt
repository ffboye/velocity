package com.example.myapplication.ui

import android.os.Bundle
import android.view.MotionEvent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.myapplication.maps.MapArea
import com.example.myapplication.tracking.RoutePoint
import com.example.myapplication.tracking.routeSections
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.*
import org.maplibre.android.style.layers.*
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.*

@Composable
internal fun RouteMap(
    points: List<RoutePoint>,
    modifier: Modifier,
    focus: LatLng? = null,
    focusArea: MapArea? = null,
    cameraKey: String = "",
    onArea: (MapArea) -> Unit = {},
    onFit: () -> Unit = {},
    fitLabel: String = "Fit route",
    description: String =
        "Street map with recorded journey route. Use zoom and fit controls below, or pan the map.",
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val view = remember {
        MapLibre.getInstance(context)
        MapView(context, MapLibreMapOptions.createFromAttributes(context).textureMode(true)).apply {
            onCreate(Bundle())
        }
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var fit by remember { mutableIntStateOf(0) }
    var reload by remember { mutableIntStateOf(0) }
    var disposed by remember { mutableStateOf(false) }
    val currentArea by rememberUpdatedState(onArea)
    DisposableEffect(view, owner) {
        var started = false
        var resumed = false
        fun sync() {
            val state = owner.lifecycle.currentState
            if (state.isAtLeast(Lifecycle.State.STARTED) && !started) {
                view.onStart()
                started = true
            }
            if (state.isAtLeast(Lifecycle.State.RESUMED) && !resumed) {
                view.onResume()
                resumed = true
            }
            if (!state.isAtLeast(Lifecycle.State.RESUMED) && resumed) {
                view.onPause()
                resumed = false
            }
            if (!state.isAtLeast(Lifecycle.State.STARTED) && started) {
                view.onStop()
                started = false
            }
        }
        val observer = LifecycleEventObserver { _, _ -> sync() }
        owner.lifecycle.addObserver(observer)
        sync()
        val failure =
            MapView.OnDidFailLoadingMapListener { _ ->
                if (disposed) return@OnDidFailLoadingMapListener
                error = "Map unavailable. Check your connection or use a downloaded area."
                loading = false
            }
        view.addOnDidFailLoadingMapListener(failure)
        view.getMapAsync { ready ->
            if (disposed) return@getMapAsync
            map = ready
            ready.uiSettings.isLogoEnabled = false
            ready.uiSettings.isAttributionEnabled = true
            ready.uiSettings.isCompassEnabled = true
            ready.addOnCameraIdleListener {
                val b = ready.projection.visibleRegion.latLngBounds
                currentArea(
                    MapArea(b.latitudeNorth, b.longitudeEast, b.latitudeSouth, b.longitudeWest)
                )
            }
        }
        onDispose {
            disposed = true
            owner.lifecycle.removeObserver(observer)
            view.removeOnDidFailLoadingMapListener(failure)
            if (resumed) view.onPause()
            if (started) view.onStop()
            view.onDestroy()
        }
    }
    LaunchedEffect(map, reload) {
        val ready = map ?: return@LaunchedEffect
        loading = true
        error = null
        style = null
        ready.setStyle(Style.Builder().fromUri(MapArea.STYLE)) { loaded ->
            if (disposed) return@setStyle
            loaded.addSource(GeoJsonSource("velocity-route"))
            loaded.addLayer(
                LineLayer("velocity-route-line", "velocity-route")
                    .withProperties(
                        lineColor("#426CB1"),
                        lineWidth(4f),
                        lineCap(Property.LINE_CAP_ROUND),
                        lineJoin(Property.LINE_JOIN_ROUND),
                    )
            )
            loaded.addSource(GeoJsonSource("velocity-start"))
            loaded.addLayer(
                CircleLayer("velocity-start-dot", "velocity-start")
                    .withProperties(
                        circleColor("#187852"),
                        circleRadius(6f),
                        circleStrokeColor("#FFFFFF"),
                        circleStrokeWidth(2f),
                    )
            )
            loaded.addSource(GeoJsonSource("velocity-finish"))
            loaded.addLayer(
                CircleLayer("velocity-finish-dot", "velocity-finish")
                    .withProperties(
                        circleColor("#202733"),
                        circleRadius(6f),
                        circleStrokeColor("#FFFFFF"),
                        circleStrokeWidth(2f),
                    )
            )
            style = loaded
            loading = false
        }
    }
    LaunchedEffect(style, points) {
        val loaded = style ?: return@LaunchedEffect
        val segments =
            routeSections(points)
                .filter { it.size > 1 }
                .map { segment ->
                    Feature.fromGeometry(
                        LineString.fromLngLats(
                            segment.map { Point.fromLngLat(it.longitude, it.latitude) }
                        )
                    )
                }
        loaded
            .getSourceAs<GeoJsonSource>("velocity-route")
            ?.setGeoJson(FeatureCollection.fromFeatures(segments))
        loaded
            .getSourceAs<GeoJsonSource>("velocity-start")
            ?.setGeoJson(
                FeatureCollection.fromFeatures(
                    points.firstOrNull()?.let {
                        listOf(Feature.fromGeometry(Point.fromLngLat(it.longitude, it.latitude)))
                    } ?: emptyList()
                )
            )
        loaded
            .getSourceAs<GeoJsonSource>("velocity-finish")
            ?.setGeoJson(
                FeatureCollection.fromFeatures(
                    points.lastOrNull()?.let {
                        listOf(Feature.fromGeometry(Point.fromLngLat(it.longitude, it.latitude)))
                    } ?: emptyList()
                )
            )
    }
    LaunchedEffect(map, cameraKey, fit, focusArea) {
        val ready = map ?: return@LaunchedEffect
        when {
            focusArea?.valid == true ->
                ready.moveCamera(
                    CameraUpdateFactory.newLatLngBounds(
                        LatLngBounds.from(
                            focusArea.north,
                            focusArea.east,
                            focusArea.south,
                            focusArea.west,
                        ),
                        32,
                    )
                )
            points.size > 1 &&
                points.any {
                    it.latitude != points[0].latitude || it.longitude != points[0].longitude
                } ->
                ready.moveCamera(
                    CameraUpdateFactory.newLatLngBounds(
                        LatLngBounds.Builder()
                            .includes(points.map { LatLng(it.latitude, it.longitude) })
                            .build(),
                        40,
                    )
                )
            points.isNotEmpty() ->
                ready.moveCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(points[0].latitude, points[0].longitude),
                        13.0,
                    )
                )
            focus != null -> ready.moveCamera(CameraUpdateFactory.newLatLngZoom(focus, 12.0))
            else -> ready.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(30.0, 0.0), 2.0))
        }
    }
    Column(modifier) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            AndroidView(
                factory = {
                    view.contentDescription = description
                    view.setOnTouchListener { v, event ->
                        if (event.actionMasked == MotionEvent.ACTION_DOWN)
                            v.parent.requestDisallowInterceptTouchEvent(true)
                        if (
                            event.actionMasked == MotionEvent.ACTION_UP ||
                                event.actionMasked == MotionEvent.ACTION_CANCEL
                        )
                            v.parent.requestDisallowInterceptTouchEvent(false)
                        false
                    }
                    view
                },
                modifier = Modifier.fillMaxSize(),
            )
            if (error != null && points.isNotEmpty()) {
                Column(Modifier.fillMaxSize()) {
                    RouteTrace(points, Modifier.fillMaxWidth().weight(1f))
                    Text(
                        "Route outline · streets unavailable",
                        color = Muted,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = Amber, style = MaterialTheme.typography.bodySmall) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                onClick = { map?.animateCamera(CameraUpdateFactory.zoomIn()) },
                enabled = map != null,
            ) {
                Text("Zoom in")
            }
            TextButton(
                onClick = { map?.animateCamera(CameraUpdateFactory.zoomOut()) },
                enabled = map != null,
            ) {
                Text("Zoom out")
            }
            TextButton(
                onClick = {
                    onFit()
                    fit++
                },
                enabled = map != null,
            ) {
                Text(fitLabel)
            }
            if (error != null) TextButton(onClick = { reload++ }) { Text("Retry map") }
        }
    }
}
