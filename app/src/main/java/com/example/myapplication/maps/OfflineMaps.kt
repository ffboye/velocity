package com.example.myapplication.maps

import android.content.Context
import android.net.*
import android.os.Handler
import android.os.Looper
import androidx.core.content.edit
import com.example.myapplication.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.module.http.HttpRequestUtil
import org.maplibre.android.offline.*

class OfflineMaps(private val context: Context) {
    private val prefs = context.getSharedPreferences("velocity_maps", Context.MODE_PRIVATE)
    private val mutable =
        MutableStateFlow(
            MapState(
                enabled = prefs.getBoolean("offline", false),
                wifiOnly = prefs.getBoolean("wifi_only", true),
            )
        )
    val state = mutable.asStateFlow()
    private val handler = Handler(Looper.getMainLooper())
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val regions = mutableMapOf<Long, OfflineRegion>()
    private var wanted: Long? = null
    private var foreground = false
    private val manager: OfflineManager

    init {
        MapLibre.getInstance(context)
        val dispatcher =
            Dispatcher().apply {
                maxRequests = 4
                maxRequestsPerHost = 4
            }
        HttpRequestUtil.setOkHttpClient(
            OkHttpClient.Builder()
                .dispatcher(dispatcher)
                .addInterceptor { chain ->
                    chain.proceed(
                        chain
                            .request()
                            .newBuilder()
                            .header(
                                "User-Agent",
                                "Velocity/${BuildConfig.VERSION_NAME} (Android; https://github.com/ffboye/velocity)",
                            )
                            .build()
                    )
                }
                .build()
        )
        HttpRequestUtil.setPrintRequestUrlOnFailure(false)
        manager = OfflineManager.getInstance(context)
        manager.setMaximumAmbientCacheSize(32_000_000L, null)
        connectivity.registerDefaultNetworkCallback(
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    handler.post { networkChanged() }
                }

                override fun onLost(network: Network) {
                    handler.post { networkChanged() }
                }

                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    handler.post { networkChanged() }
                }
            }
        )
        networkChanged()
        manager.listOfflineRegions(
            object : OfflineManager.ListOfflineRegionsCallback {
                override fun onList(offlineRegions: Array<OfflineRegion>?) {
                    offlineRegions.orEmpty().forEach { region ->
                        region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                        attach(region)
                    }
                    mutable.update { it.copy(loading = false) }
                }

                override fun onError(error: String) {
                    mutable.update {
                        it.copy(
                            loading = false,
                            message = "Offline maps couldn't be read. Try reopening the app.",
                        )
                    }
                }
            }
        )
    }

    private fun networkChanged() {
        val caps = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
        mutable.update {
            it.copy(
                wifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true,
                network = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true,
            )
        }
        applyPolicy()
    }

    fun foreground(visible: Boolean) {
        foreground = visible
        applyPolicy()
    }

    fun enable(enabled: Boolean) {
        prefs.edit { putBoolean("offline", enabled) }
        mutable.update { it.copy(enabled = enabled) }
        if (!enabled) wanted = null
        applyPolicy()
    }

    fun wifiOnly(enabled: Boolean) {
        prefs.edit { putBoolean("wifi_only", enabled) }
        mutable.update { it.copy(wifiOnly = enabled) }
        applyPolicy()
    }

    fun message(text: String?) {
        mutable.update { it.copy(message = text) }
    }

    private fun applyPolicy() {
        val s = mutable.value
        regions.forEach { (id, region) ->
            val run =
                wanted == id &&
                    s.mayDownload(foreground) &&
                    s.areas.none { it.id == id && it.complete }
            region.setDownloadState(
                if (run) OfflineRegion.STATE_ACTIVE else OfflineRegion.STATE_INACTIVE
            )
            update(id) {
                it.copy(
                    downloading = run,
                    message =
                        if (wanted == id && !run && !it.complete)
                            when {
                                !foreground -> "Paused while Velocity is in the background"
                                !s.enabled -> "Offline downloads are off"
                                !s.network -> "Waiting for a connection"
                                s.wifiOnly && !s.wifi -> "Waiting for Wi-Fi"
                                else -> it.message
                            }
                        else if (run) null else it.message,
                )
            }
        }
    }

    fun download(area: MapArea, name: String) {
        if (!mutable.value.enabled || mutable.value.loading) return
        if (!area.downloadable) {
            message(
                "Choose a smaller area by zooming in. Downloads include street detail through zoom 14."
            )
            return
        }
        if (context.filesDir.usableSpace < area.estimatedBytes + 50_000_000L) {
            message(
                "Not enough storage. Free at least ${area.estimatedBytes / 1_000_000 + 50} MB and try again."
            )
            return
        }
        if (mutable.value.areas.size >= 10) {
            message("You can keep up to 10 areas. Remove an area before adding another.")
            return
        }
        mutable.update { it.copy(loading = true, message = null) }
        val definition =
            OfflineTilePyramidRegionDefinition(
                MapArea.STYLE,
                LatLngBounds.from(area.north, area.east, area.south, area.west),
                MapArea.MIN_ZOOM.toDouble(),
                MapArea.MAX_ZOOM.toDouble(),
                context.resources.displayMetrics.density,
                false,
            )
        val metadata =
            JSONObject()
                .put("name", name.trim().ifBlank { "Offline area" }.take(60))
                .toString()
                .toByteArray()
        manager.createOfflineRegion(
            definition,
            metadata,
            object : OfflineManager.CreateOfflineRegionCallback {
                override fun onCreate(region: OfflineRegion) {
                    attach(region)
                    wanted = region.id
                    mutable.update { it.copy(loading = false) }
                    applyPolicy()
                }

                override fun onError(error: String) {
                    mutable.update {
                        it.copy(
                            loading = false,
                            message = "The area couldn't be created. Check storage and try again.",
                        )
                    }
                }
            },
        )
    }

    private fun attach(region: OfflineRegion) {
        val definition = region.definition as? OfflineTilePyramidRegionDefinition ?: return
        val bounds = definition.bounds ?: return
        val area =
            MapArea(
                bounds.latitudeNorth,
                bounds.longitudeEast,
                bounds.latitudeSouth,
                bounds.longitudeWest,
            )
        val name =
            runCatching { JSONObject(String(region.metadata)).optString("name", "Offline area") }
                .getOrDefault("Offline area")
        regions[region.id] = region
        mutable.update {
            it.copy(
                areas =
                    it.areas.filterNot { a -> a.id == region.id } +
                        OfflineArea(region.id, name, area)
            )
        }
        fun status(status: OfflineRegionStatus) {
            if (region.id !in regions) return
            if (status.completedResourceSize >= MapArea.MAX_BYTES && !status.isComplete) {
                if (wanted == region.id) wanted = null
                region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                update(region.id) {
                    it.copy(
                        downloading = false,
                        message = "Size limit reached. Delete this area and choose a smaller one.",
                        bytes = status.completedResourceSize,
                    )
                }
                return
            }
            update(region.id) {
                it.copy(
                    bytes = status.completedResourceSize,
                    progress =
                        if (status.requiredResourceCount > 0)
                            (status.completedResourceCount.toDouble() /
                                    status.requiredResourceCount)
                                .coerceIn(0.0, 1.0)
                                .toFloat()
                        else 0f,
                    complete = status.isComplete,
                    message = if (status.isComplete) null else it.message,
                    downloading = it.downloading && !status.isComplete,
                )
            }
            if (status.isComplete && wanted == region.id) {
                wanted = null
                region.setDownloadState(OfflineRegion.STATE_INACTIVE)
            }
        }
        region.setObserver(
            object : OfflineRegion.OfflineRegionObserver {
                override fun onStatusChanged(status: OfflineRegionStatus) = status(status)

                override fun onError(error: OfflineRegionError) {
                    if (wanted == region.id) wanted = null
                    region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                    update(region.id) {
                        it.copy(
                            downloading = false,
                            message = "Download interrupted. Check the connection and tap Resume.",
                        )
                    }
                }

                override fun mapboxTileCountLimitExceeded(limit: Long) {
                    if (wanted == region.id) wanted = null
                    region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                    update(region.id) {
                        it.copy(
                            downloading = false,
                            message = "Tile limit reached. Choose a smaller area.",
                        )
                    }
                }
            }
        )
        region.getStatus(
            object : OfflineRegion.OfflineRegionStatusCallback {
                override fun onStatus(status: OfflineRegionStatus?) {
                    status?.let { status(it) }
                }

                override fun onError(error: String?) {
                    update(region.id) {
                        it.copy(message = "Status unavailable. Try Resume or delete this area.")
                    }
                }
            }
        )
    }

    fun resume(id: Long) {
        if (!mutable.value.enabled) return
        wanted = id
        applyPolicy()
    }

    fun pause(id: Long) {
        if (wanted == id) wanted = null
        regions[id]?.setDownloadState(OfflineRegion.STATE_INACTIVE)
        update(id) { it.copy(downloading = false, message = "Paused") }
    }

    fun delete(id: Long) {
        val region = regions[id] ?: return
        pause(id)
        region.delete(
            object : OfflineRegion.OfflineRegionDeleteCallback {
                override fun onDelete() {
                    regions.remove(id)
                    mutable.update { it.copy(areas = it.areas.filterNot { a -> a.id == id }) }
                }

                override fun onError(error: String) {
                    message("This area couldn't be removed. Try again.")
                }
            }
        )
    }

    private fun update(id: Long, change: (OfflineArea) -> OfflineArea) {
        mutable.update { it.copy(areas = it.areas.map { a -> if (a.id == id) change(a) else a }) }
    }
}
