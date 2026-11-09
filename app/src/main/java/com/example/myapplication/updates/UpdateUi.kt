package com.example.myapplication.updates

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.myapplication.BuildConfig
import com.example.myapplication.ui.AppButton
import com.example.myapplication.ui.AppTextButton
import com.example.myapplication.ui.PreferenceRow
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun UpdateSettings(
    state: UpdateState,
    onCheck: () -> Unit,
    onAutomatic: (Boolean) -> Unit,
    onShow: () -> Unit,
    onCancel: () -> Unit,
) {
    val busy =
        state.phase in
            setOf(
                UpdatePhase.CHECKING,
                UpdatePhase.DOWNLOADING,
                UpdatePhase.VERIFYING,
                UpdatePhase.INSTALLING,
            )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "App Updates",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text("Version ${BuildConfig.VERSION_NAME} · Stable", fontSize = 14.sp)
            if (BuildConfig.DEBUG)
                Text(
                    "This test build can back up your journeys. Install the release-signed build to receive production upgrades.",
                    fontSize = 14.sp,
                )
            PreferenceRow(
                "Automatic updates",
                "",
                state.automatic,
                onAutomatic,
                "Automatic update checks",
            )
            Text(
                "Last successful check: ${if (state.lastCheck == 0L) "Never" else DateFormat.getDateTimeInstance().format(Date(state.lastCheck))}",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AppButton(onCheck, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.phase == UpdatePhase.CHECKING) "Checking…" else "Check for Updates")
            }
            if (state.phase == UpdatePhase.DOWNLOADING) {
                DownloadProgress(state.progress)
                AppTextButton(onCancel) { Text("Cancel download") }
            }
            if (state.manifest != null && state.phase != UpdatePhase.CHECKING) {
                AppTextButton(onShow) {
                    Text(
                        if (state.phase == UpdatePhase.READY) "View verified update"
                        else "View available update"
                    )
                }
            }
            state.message?.let {
                Text(
                    it,
                    fontSize = 14.sp,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
}

@Composable
fun UpdateDialog(
    state: UpdateState,
    installationBlocked: Boolean,
    onLater: () -> Unit,
    onRetry: () -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onInstall: () -> Unit,
    blockingMessage: String? = null,
) {
    if (!state.dialog) return
    Dialog(onDismissRequest = onLater) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        ) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    Modifier.fillMaxWidth()
                        .height(4.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                )
                Text(
                    if (state.manifest == null) "Update check" else "Update available",
                    style = MaterialTheme.typography.headlineSmall,
                )
                state.manifest?.let { release ->
                    Text(
                        "Installed  ${BuildConfig.VERSION_NAME}\nAvailable  ${release.version}",
                        fontSize = 14.sp,
                    )
                    Text(
                        String.format(
                            Locale.getDefault(),
                            "Download size  %.1f MB",
                            release.sizeBytes / 1048576.0,
                        ),
                        fontSize = 14.sp,
                    )
                    Text("What's new", style = MaterialTheme.typography.titleSmall)
                    release.releaseNotes.forEach { Text("•  $it", fontSize = 14.sp) }
                }
                if (state.phase == UpdatePhase.DOWNLOADING) DownloadProgress(state.progress)
                if (
                    state.phase in
                        setOf(UpdatePhase.VERIFYING, UpdatePhase.INSTALLING, UpdatePhase.CHECKING)
                ) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                }
                state.message?.let {
                    Text(
                        it,
                        fontSize = 14.sp,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                if (installationBlocked)
                    Text(
                        blockingMessage
                            ?: "Installation waits until your journey is finished, including paused journeys.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                when (state.phase) {
                    UpdatePhase.AVAILABLE ->
                        AppButton(onDownload, modifier = Modifier.fillMaxWidth()) {
                            Text("Update Now")
                        }
                    UpdatePhase.DOWNLOADING -> AppTextButton(onCancel) { Text("Cancel download") }
                    UpdatePhase.READY ->
                        AppButton(
                            onInstall,
                            enabled = !installationBlocked,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Install update")
                        }
                    UpdatePhase.ERROR ->
                        AppButton(
                            if (state.manifest == null) onRetry else onDownload,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Retry")
                        }
                    else -> Unit
                }
                AppTextButton(onLater, modifier = Modifier.fillMaxWidth()) {
                    Text("Remind Me Later")
                }
            }
        }
    }
}

@Composable
private fun DownloadProgress(progress: Float) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), label = "Update download")
    LinearProgressIndicator(progress = { animated }, modifier = Modifier.fillMaxWidth())
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        Text("Downloading  ${(progress * 100).toInt()}%", fontSize = 14.sp)
    }
}
