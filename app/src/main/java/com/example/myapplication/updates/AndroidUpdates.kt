package com.example.myapplication.updates

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.edit
import java.io.File
import java.util.zip.ZipFile

class AndroidUpdatePreferences(context: Context) : UpdatePreferences {
    private val prefs = context.getSharedPreferences("velocity_updates", Context.MODE_PRIVATE)
    override var automatic: Boolean
        get() = prefs.getBoolean("automatic", true)
        set(value) {
            prefs.edit { putBoolean("automatic", value) }
        }

    override var lastCheck: Long
        get() = prefs.getLong("last_check", 0)
        set(value) {
            prefs.edit { putLong("last_check", value) }
        }

    override var highestCode: Long
        get() = prefs.getLong("highest_code", 0)
        set(value) {
            prefs.edit { putLong("highest_code", value) }
        }

    override var pendingCode: Long
        get() = prefs.getLong("pending_code", 0)
        set(value) {
            prefs.edit(commit = true) { putLong("pending_code", value) }
        }
}

@Suppress("DEPRECATION")
class AndroidApkInspector(private val context: Context) : ApkInspector {
    override fun verify(file: File, manifest: ReleaseManifest) {
        val flags =
            if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES
            else PackageManager.GET_SIGNATURES
        val installed = context.packageManager.getPackageInfo(context.packageName, flags)
        val candidate =
            context.packageManager.getPackageArchiveInfo(file.absolutePath, flags)
                ?: throw UpdateException("Invalid APK archive")
        ZipFile(file).use { zip ->
            val abis =
                zip.entries()
                    .asSequence()
                    .map { it.name }
                    .filter { it.startsWith("lib/") && it.endsWith(".so") }
                    .map { it.split('/')[1] }
                    .toSet()
            UpdateSecurity.archive(
                identity(candidate, abis),
                identity(installed),
                manifest,
                Build.VERSION.SDK_INT,
                Build.SUPPORTED_ABIS.toSet(),
            )
        }
    }

    private fun identity(info: PackageInfo, abis: Set<String> = emptySet()): ApkIdentity {
        val signers =
            if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners
            else info.signatures
        val code =
            if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        return ApkIdentity(
            info.packageName,
            info.versionName,
            code,
            info.applicationInfo?.minSdkVersion
                ?: throw UpdateException("Missing APK requirements"),
            signers?.map { it.toCharsString() }?.toSet().orEmpty(),
            abis,
        )
    }
}
