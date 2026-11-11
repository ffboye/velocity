package com.example.myapplication

import android.Manifest
import android.app.Activity
import android.content.*
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.tracking.*
import com.example.myapplication.ui.VelocityApp
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.example.myapplication.updates.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val updater
        get() = (application as VelocityApplication).updates

    private val updateInstaller =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            updater.installResult(
                it.resultCode == Activity.RESULT_OK,
                it.resultCode == Activity.RESULT_CANCELED,
            )
        }
    private val installPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            updater.permissionResult(
                Build.VERSION.SDK_INT < 26 || packageManager.canRequestPackageInstalls()
            )
        }
    private val repository
        get() = (application as VelocityApplication).journeys

    private val maps
        get() = (application as VelocityApplication).maps

    private var binding = false
    private var tracking: TrackingService.TrackingBinder? = null
    private var pendingStart = false
    private var exportId: String? = null
    private val connection =
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                tracking = service as? TrackingService.TrackingBinder
                tracking?.refresh()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                binding = false
                tracking = null
            }
        }
    private val locationPermission =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            refreshPermissions()
            if (hasLocation()) {
                bindGps()
                if (pendingStart) startJourney()
            } else
                repository.message(
                    "Precise location is needed to measure speed. Enable it in Android's app permissions."
                )
            pendingStart = false
        }
    private val notifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    private val backup =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri
            ->
            if (uri != null && repository.state.value.recording == Recording.IDLE) {
                val journeys = repository.state.value.journeys
                lifecycleScope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            val encoded = JourneyCodec.encodeAll(journeys, null)
                            JourneyBackup.read(encoded.toByteArray(Charsets.UTF_8))
                            val output =
                                contentResolver.openOutputStream(uri) ?: error("Cannot open backup")
                            output.bufferedWriter().use { it.write(encoded) }
                        }
                        repository.message(
                            "Journey backup saved. Keep it until your restored journeys are verified."
                        )
                    } catch (_: Exception) {
                        repository.message(
                            "Couldn't save the journey backup. Try another file location."
                        )
                    }
                }
            }
        }
    private val restore =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null)
                lifecycleScope.launch {
                    try {
                        val journeys =
                            withContext(Dispatchers.IO) {
                                val input =
                                    contentResolver.openInputStream(uri)
                                        ?: error("Cannot read backup")
                                val bytes =
                                    input.use { it.readBytesBounded(JourneyBackup.MAX_BYTES) }
                                JourneyBackup.read(bytes)
                            }
                        if (!repository.restore(journeys))
                            repository.message("Finish your journey before restoring a backup.")
                    } catch (_: Exception) {
                        repository.message(
                            "This backup couldn't be restored. Select a valid Velocity journey backup up to 16 MB."
                        )
                    }
                }
        }
    private val export =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/gpx+xml")) {
            uri ->
            val journey = repository.state.value.journeys.firstOrNull { it.id == exportId }
            if (uri != null && journey != null)
                lifecycleScope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            val output =
                                contentResolver.openOutputStream(uri)
                                    ?: error("Cannot open document")
                            output.bufferedWriter().use { it.write(JourneyCodec.gpx(journey)) }
                        }
                        repository.message("GPX route exported.")
                    } catch (_: Exception) {
                        repository.message(
                            "Couldn't export the route. Choose another file location and retry."
                        )
                    }
                }
            exportId = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingStart = savedInstanceState?.getBoolean("pending_start") ?: false
        exportId = savedInstanceState?.getString("export_id")
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            val state by repository.state.collectAsState()
            val updateState by updater.state.collectAsState()
            val mapState by maps.state.collectAsState()
            LaunchedEffect(Unit) { updater.startup() }
            var page by rememberSaveable { mutableIntStateOf(0) }
            LaunchedEffect(state.daylight) {
                val bars =
                    if (state.daylight)
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT,
                        )
                    else SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
            }
            LaunchedEffect(state.keepAwake, page) {
                if (state.keepAwake && page == 0)
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            MyApplicationTheme(
                easyReading = state.easyReading,
                daylight = state.daylight,
                tapHaptics = state.tapHaptics,
                tripHaptics = state.tripHaptics,
            ) {
                VelocityApp(
                    state,
                    page,
                    onPage = { page = it },
                    onStart = { requestLocation(start = true) },
                    onPause = { command(TrackingService.PAUSE) },
                    onFinish = { name, notes ->
                        startService(
                            Intent(this, TrackingService::class.java)
                                .setAction(TrackingService.FINISH)
                                .putExtra("name", name)
                                .putExtra("notes", notes)
                        )
                    },
                    onEnableLocation = { requestLocation(start = false) },
                    onRetryGps = { command(TrackingService.RETRY) },
                    onTracking = { enabled ->
                        if (enabled && !hasLocation()) {
                            repository.tracking(true)
                            requestLocation(start = false)
                        } else
                            command(
                                if (enabled) TrackingService.TRACKING_ON
                                else TrackingService.TRACKING_OFF
                            )
                    },
                    onEditJourney = { id, name, notes -> repository.editJourney(id, name, notes) },
                    onEditJourneyTags = { id, name, notes, tags ->
                        repository.editJourney(id, name, notes, tags)
                    },
                    onFinishTagged = { name, notes, tags ->
                        startService(
                            Intent(this, TrackingService::class.java)
                                .setAction(TrackingService.FINISH)
                                .putExtra("name", name)
                                .putExtra("notes", notes)
                                .putStringArrayListExtra("tags", ArrayList(tags))
                        )
                    },
                    mapState = mapState,
                    mapController = maps,
                    onGpsSettings = {
                        startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    },
                    onAppSettings = {
                        startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:$packageName"),
                            )
                        )
                    },
                    onUnit = repository::unit,
                    onAwake = repository::keepAwake,
                    onGpsTabVisibility = repository::showGpsTab,
                    onEasyReading = repository::easyReading,
                    onDaylight = repository::daylight,
                    onTapHaptics = repository::tapHaptics,
                    onTripHaptics = repository::tripHaptics,
                    onDistanceChimes = repository::distanceChimes,
                    onLimit = repository::limit,
                    onDelete = repository::delete,
                    onDismissMessage = { repository.message(null) },
                    onRetrySave = repository::persist,
                    updates = {
                        UpdateSettings(
                            updateState,
                            { updater.check() },
                            updater::automatic,
                            updater::show,
                            updater::cancel,
                        )
                    },
                    onBackup = { backup.launch("Velocity-journeys-backup.json") },
                    onRestore = {
                        restore.launch(
                            arrayOf("application/json", "application/octet-stream", "text/plain")
                        )
                    },
                    onExport = { journey ->
                        exportId = journey.id
                        export.launch("Velocity-${journey.id.take(8)}.gpx")
                    },
                )
                UpdateDialog(
                    updateState,
                    state.loading ||
                        state.saving ||
                        state.saveFailed ||
                        state.recording != Recording.IDLE,
                    onLater = updater::later,
                    onRetry = { updater.check() },
                    onDownload = updater::download,
                    onCancel = updater::cancel,
                    onInstall = ::installUpdate,
                    blockingMessage =
                        when {
                            state.saveFailed ->
                                "Save your journey changes before installing an update. Free some storage, then use Retry save."
                            state.saving ->
                                "Finishing the journey save. Installation will be available when it completes."
                            state.loading ->
                                "Wait for your journeys to finish loading before installing."
                            else -> null
                        },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        updater.startup()
        maps.foreground(true)
        refreshPermissions()
        bindGps()
    }

    override fun onResume() {
        super.onResume()
        refreshPermissions()
        bindGps()
    }

    override fun onStop() {
        maps.foreground(false)
        if (binding) {
            unbindService(connection)
            binding = false
            tracking = null
        }
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("pending_start", pendingStart)
        outState.putString("export_id", exportId)
        super.onSaveInstanceState(outState)
    }

    private fun hasLocation() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun refreshPermissions() {
        repository.permissions(
            hasLocation(),
            getSystemService(LocationManager::class.java).locationEnabled(),
        )
        repository.tick()
    }

    private fun bindGps() {
        if (binding) tracking?.refresh()
        if (!binding && hasLocation())
            binding =
                bindService(Intent(this, TrackingService::class.java), connection, BIND_AUTO_CREATE)
    }

    private fun requestLocation(start: Boolean) {
        if (hasLocation()) {
            bindGps()
            if (start) startJourney()
        } else {
            pendingStart = start
            locationPermission.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                )
            )
        }
    }

    private fun startJourney() {
        refreshPermissions()
        if (!repository.state.value.gpsEnabled) {
            repository.message("Turn on your device's GPS, then start your journey.")
            return
        }
        try {
            ContextCompat.startForegroundService(
                this,
                Intent(this, TrackingService::class.java).setAction(TrackingService.START),
            )
            if (
                Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ) != PackageManager.PERMISSION_GRANTED
            ) {
                notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } catch (_: RuntimeException) {
            repository.message(
                "Android couldn't start recording. Keep Velocity open and try again."
            )
        }
    }

    private fun command(action: String) {
        try {
            startService(Intent(this, TrackingService::class.java).setAction(action))
        } catch (_: RuntimeException) {
            repository.message("Couldn't change the recording. Please try again.")
        }
    }

    @Suppress("DEPRECATION")
    private fun installUpdate() {
        updater.install { file ->
            if (Build.VERSION.SDK_INT >= 26 && !packageManager.canRequestPackageInstalls()) {
                installPermission.launch(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:$packageName"),
                    )
                )
            } else {
                val uri = FileProvider.getUriForFile(this, "$packageName.updates", file)
                updateInstaller.launch(
                    Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                        setDataAndType(uri, "application/vnd.android.package-archive")
                        clipData = ClipData.newRawUri("Verified Velocity update", uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        putExtra(Intent.EXTRA_RETURN_RESULT, true)
                    }
                )
            }
        }
    }

    private fun java.io.InputStream.readBytesBounded(limit: Int): ByteArray {
        val result = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            require(result.size() + count <= limit)
            result.write(buffer, 0, count)
        }
        return result.toByteArray()
    }
}
