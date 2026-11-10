package com.example.myapplication

import android.os.ParcelFileDescriptor
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication.maps.*
import com.example.myapplication.tracking.*
import com.example.myapplication.ui.VelocityApp
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.example.myapplication.updates.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Display-only fixtures: no journeys, downloads or updates are written by this audit. */
@RunWith(AndroidJUnit4::class)
class UiSpacingAuditTest {
    @get:Rule val compose = createComposeRule()
    private val layoutIssues = mutableListOf<String>()

    private fun shell(command: String) {
        val file =
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        ParcelFileDescriptor.AutoCloseInputStream(file).use { it.readBytes() }
    }

    private fun root(node: SemanticsNode): SemanticsNode {
        var result = node
        while (result.parent != null) result = result.parent!!
        return result
    }

    private fun ancestor(parent: SemanticsNode, child: SemanticsNode): Boolean {
        var current = child.parent
        while (current != null) {
            if (current.id == parent.id) return true
            current = current.parent
        }
        return false
    }

    private fun audit(name: String) {
        compose.waitForIdle()
        Thread.sleep(300)
        shell("screencap -p /data/local/tmp/velocity-ui-audit/$name.png")
        val text =
            compose
                .onAllNodes(
                    SemanticsMatcher.keyIsDefined(SemanticsProperties.Text),
                    useUnmergedTree = true,
                )
                .fetchSemanticsNodes()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            text.forEach { node ->
                val results = mutableListOf<TextLayoutResult>()
                node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
                results.forEach { result ->
                    // Compose rounds the measured size to integer pixels. Subpixel overflow is
                    // harmless; a lost line or overflow beyond that rounding tolerance is not.
                    val paragraph = result.multiParagraph
                    // Paragraph width can include unused wrapping space. Compare actual glyph
                    // spans, otherwise an ordinary short title looks like overflow.
                    val widestLine =
                        (0 until result.lineCount).maxOfOrNull {
                            result.getLineRight(it) - result.getLineLeft(it)
                        } ?: 0f
                    val clipped =
                        paragraph.didExceedMaxLines ||
                            widestLine > result.size.width + 1f ||
                            paragraph.height > result.size.height + 1f
                    if (name.startsWith("large-") && result.layoutInput.density.fontScale < 1.7f)
                        layoutIssues +=
                            "$name: reading scale missing from '${result.layoutInput.text}'"
                    if (clipped && result.layoutInput.overflow != TextOverflow.Ellipsis) {
                        layoutIssues +=
                            "$name: clipped text '${result.layoutInput.text}' " +
                                "(paragraph ${paragraph.width} x ${paragraph.height}, size ${result.size})"
                    }
                }
            }
        }
        val controls =
            compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().filter {
                val bounds = it.boundsInRoot.intersect(root(it).boundsInRoot)
                bounds.width > 1f && bounds.height > 1f
            }
        for (i in controls.indices) for (j in i + 1 until controls.size) {
            val a = controls[i]
            val b = controls[j]
            if (root(a).id != root(b).id || ancestor(a, b) || ancestor(b, a)) continue
            val intersection =
                a.boundsInRoot.intersect(b.boundsInRoot).intersect(root(a).boundsInRoot)
            if (intersection.width > 1f && intersection.height > 1f)
                layoutIssues += "$name: overlapping controls ${a.config} / ${b.config}"
        }
    }

    @Test
    fun mainScreensAndDialogsHaveNoControlOverlapOrAccidentalTextClipping() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as VelocityApplication
        lateinit var controller: OfflineMaps
        instrumentation.runOnMainSync { controller = app.maps }
        shell("mkdir -p /data/local/tmp/velocity-ui-audit")
        val trip =
            Journey(
                "spacing-preview",
                1700000000000,
                3725000,
                12345.6,
                18.0,
                listOf(
                    RoutePoint(52.366, 4.901, 1700000000000, 0),
                    RoutePoint(52.367, 4.903, 1700000001000, 0),
                    RoutePoint(52.369, 4.905, 1700000002000, 1),
                    RoutePoint(52.370, 4.907, 1700000003000, 1),
                ),
                name = "Morning canal route",
                notes = "Along the water, with a short break before heading home.",
                samples =
                    (0..12).map {
                        SpeedSample(it * 300000L, (it % 6 + 4).toDouble(), if (it < 6) 0 else 1)
                    },
            )
        val ready =
            DriveState(
                loading = false,
                permission = true,
                gpsEnabled = true,
                hasSignal = true,
                fix = GpsFix(52.366, 4.901, 7f, 1000, 1700000000000),
                fixAgeMillis = 500,
                gnssFresh = true,
                gnss =
                    GnssState(
                        ReceiverStatus.LISTENING,
                        listOf(
                            Satellite(1, 7, 42f, 45f, 90f, true),
                            Satellite(6, 12, 35f, 20f, 180f, false),
                        ),
                        1000,
                    ),
                durationMillis = trip.durationMillis,
                distanceMeters = trip.distanceMeters,
                maxSpeed = trip.maxSpeed,
                journeys = listOf(trip),
            )
        val area = MapArea(52.38, 4.93, 52.35, 4.88)
        val maps =
            MapState(
                enabled = true,
                wifiOnly = true,
                loading = false,
                areas =
                    listOf(
                        OfflineArea(90001, "Riverside", area, bytes = 12000000, complete = true),
                        OfflineArea(
                            90002,
                            "Neighbourhood",
                            area,
                            bytes = 4500000,
                            progress = 0.4f,
                            message = "Waiting for Wi-Fi",
                        ),
                    ),
            )
        val release =
            ReleaseManifest(
                "9.9.9",
                999,
                24,
                "https://github.com/ffboye/velocity/releases/download/v9.9.9/velocity.apk",
                "0".repeat(64),
                52000000,
                listOf(
                    "A clearer layout with larger controls.",
                    "Saved routes and offline maps stay on your device.",
                ),
                "com.example.myapplication",
            )
        var data by mutableStateOf(ready)
        var page by mutableIntStateOf(0)
        var scene by mutableIntStateOf(0)
        var large by mutableStateOf(false)
        var update by mutableStateOf(UpdateState())
        compose.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.FontScale(if (large) 1.5f else 1f)
            ) {
                key(scene, large) {
                    MyApplicationTheme(easyReading = large) {
                        VelocityApp(
                            data,
                            page,
                            { page = it },
                            onStart = { data = data.copy(recording = Recording.RECORDING) },
                            onPause = {
                                data =
                                    data.copy(
                                        recording =
                                            if (data.recording == Recording.RECORDING)
                                                Recording.PAUSED
                                            else Recording.RECORDING
                                    )
                            },
                            onFinish = { _, _ -> data = data.copy(recording = Recording.IDLE) },
                            onEnableLocation = {},
                            onGpsSettings = {},
                            onAppSettings = {},
                            onUnit = { data = data.copy(unit = it) },
                            onAwake = {},
                            onLimit = {},
                            onDelete = {},
                            onExport = {},
                            onDismissMessage = {},
                            onRetrySave = {},
                            onEasyReading = { data = data.copy(easyReading = it) },
                            mapState = maps,
                            mapController = controller,
                            updates = { UpdateSettings(UpdateState(), {}, {}, {}, {}) },
                        )
                        UpdateDialog(
                            update,
                            data.recording != Recording.IDLE,
                            onLater = { update = update.copy(dialog = false) },
                            onRetry = {},
                            onDownload = {},
                            onCancel = {},
                            onInstall = {},
                        )
                    }
                }
            }
        }
        for (profile in listOf("standard", "large")) {
            fun show(
                target: Int = 0,
                state: DriveState = ready,
                popup: UpdateState = UpdateState(),
            ) {
                compose.runOnIdle {
                    large = profile == "large"
                    data = state.copy(easyReading = large)
                    page = target
                    update = popup
                    scene++
                }
            }
            fun capture(name: String) = audit("$profile-$name")
            show()
            capture("speed")
            compose.onNodeWithText("RECORDED TIME").performScrollTo()
            capture("speed-metrics")
            show(state = ready.copy(permission = false, hasSignal = false, fix = null))
            compose.onNodeWithText("Enable GPS access").performScrollTo()
            capture("permissions")
            show(state = ready.copy(recording = Recording.RECORDING))
            capture("recording")
            compose.onNodeWithText("Finish & save").performClick()
            capture("finish-dialog")
            compose.onNodeWithText("Cancel").performClick()
            show(4, ready.copy(recording = Recording.PAUSED))
            capture("paused-settings")
            show(1, ready.copy(journeys = emptyList()))
            capture("empty-journeys")
            show(1)
            capture("journeys")
            compose.onNodeWithTag("journeys-content").performScrollToNode(hasText(trip.name))
            compose.onNodeWithText(trip.name).performClick()
            compose.onNode(hasText("Fit route") and hasClickAction()).performScrollTo()
            capture("journey-route")
            compose.onNodeWithText("Average calculation").performScrollTo()
            capture("journey-stats")
            compose.onNodeWithText("Average calculation").performClick()
            capture("average-dialog")
            compose.onAllNodesWithText("Done").onLast().performClick()
            compose.onNodeWithText("Edit name & notes").performScrollTo().performClick()
            capture("editor")
            compose
                .onNodeWithText("Notes (optional)")
                .performScrollTo()
                .performClick()
                .performTextInput(" Clear, readable notes.")
            capture("editor-keyboard")
            instrumentation.uiAutomation.performGlobalAction(
                android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK
            )
            compose.waitForIdle()
            if (compose.onNodeWithText("Cancel").isDisplayed())
                compose.onNodeWithText("Cancel").performClick()
            compose.onNodeWithText("Delete journey").performScrollTo().performClick()
            capture("delete-journey")
            compose.onNodeWithText("Keep journey").performClick()
            show(4)
            capture("settings")
            compose.onNodeWithContentDescription("Easy reading").performScrollTo()
            capture("display-settings")
            compose.onNodeWithText("Privacy details").performScrollTo().performClick()
            compose.onNodeWithText("OpenFreeMap supplies", substring = true).performScrollTo()
            capture("privacy")
            compose.onNodeWithText("Offline maps").performScrollTo()
            capture("offline-settings")
            compose.onNodeWithText("How offline maps work").performScrollTo().performClick()
            compose.onNodeWithText("1. Enable offline maps", substring = true).performScrollTo()
            capture("offline-guide")
            compose.onNodeWithText("Hide offline guide").performScrollTo().performClick()
            compose.onNodeWithText("Neighbourhood").performScrollTo()
            capture("saved-maps")
            compose.onAllNodesWithText("Delete map").onFirst().performScrollTo().performClick()
            capture("delete-map")
            compose.onNodeWithText("Keep map").performClick()
            compose.onNodeWithText("Choose area to download").performScrollTo().performClick()
            compose.onNodeWithText("Download area").assertIsDisplayed()
            capture("area-picker")
            compose.onNodeWithText("Done").performClick()
            show(3)
            capture("gps")
            compose.onNodeWithTag("gps-content").performScrollToNode(hasText("Satellite signals"))
            capture("satellites")
            compose.onNodeWithTag("gps-content").performScrollToNode(hasText("LOCATION DETAILS"))
            capture("gps-details")
            show(state = ready.copy(saveFailed = true))
            capture("save-error")
            for (phase in
                listOf(
                    UpdatePhase.AVAILABLE,
                    UpdatePhase.DOWNLOADING,
                    UpdatePhase.READY,
                    UpdatePhase.ERROR,
                )) {
                show(
                    state =
                        ready.copy(
                            recording =
                                if (phase == UpdatePhase.READY) Recording.RECORDING
                                else Recording.IDLE
                        ),
                    popup =
                        UpdateState(
                            phase = phase,
                            manifest = release,
                            progress = 0.42f,
                            dialog = true,
                            message =
                                if (phase == UpdatePhase.ERROR)
                                    "Download interrupted. Check your connection, then retry."
                                else null,
                        ),
                )
                compose.onNodeWithText("Remind Me Later").performScrollTo()
                capture("update-${phase.name.lowercase()}")
            }
        }
        assertTrue(layoutIssues.joinToString("\n"), layoutIssues.isEmpty())
    }
}
