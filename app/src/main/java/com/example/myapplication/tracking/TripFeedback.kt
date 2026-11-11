package com.example.myapplication.tracking

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings

/** Optional local feedback. Uses system touch/notification policies and never changes volume. */
internal class TripFeedback(private val context: Context) {
    private var tone: ToneGenerator? = null

    @Suppress("DEPRECATION")
    fun tripHaptic() {
        try {
            if (
                Settings.System.getInt(
                    context.contentResolver,
                    Settings.System.HAPTIC_FEEDBACK_ENABLED,
                    1,
                ) == 0
            )
                return
            val vibrator = context.getSystemService(Vibrator::class.java) ?: return
            if (!vibrator.hasVibrator()) return
            val attributes =
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .build()
            if (Build.VERSION.SDK_INT >= 33) {
                vibrator.vibrate(
                    VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE),
                    VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH),
                )
            } else if (Build.VERSION.SDK_INT >= 26) {
                vibrator.vibrate(
                    VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE),
                    attributes,
                )
            } else vibrator.vibrate(20, attributes)
        } catch (_: RuntimeException) {
            /* Feedback must never interrupt recording. */
        }
    }

    fun distanceChime() {
        try {
            val player =
                tone ?: ToneGenerator(AudioManager.STREAM_NOTIFICATION, 45).also { tone = it }
            player.startTone(ToneGenerator.TONE_PROP_BEEP, 140)
        } catch (_: RuntimeException) {
            /* A muted or unavailable audio device is fine. */
        }
    }

    fun release() {
        tone?.release()
        tone = null
    }
}
