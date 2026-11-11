package com.example.myapplication.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.example.myapplication.ui.theme.LocalTapHaptics

/** Finite, event-driven motion. Compose applies Android's animator duration scale. */
@Composable
internal fun Modifier.pressMotion(
    interactions: MutableInteractionSource,
    enabled: Boolean = true,
    pressedScale: Float = 0.98f,
): Modifier {
    val pressed by interactions.collectIsPressedAsState()
    val scale =
        animateFloatAsState(
            if (enabled && pressed) pressedScale else 1f,
            animationSpec = if (pressed) tween(90) else spring(dampingRatio = 1f, stiffness = 650f),
            label = "Touch response",
        )
    return graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

@Composable
internal fun clickWithFeedback(action: () -> Unit): () -> Unit {
    val view = LocalView.current
    val enabled = LocalTapHaptics.current
    return {
        // No ignore-setting flags: Android controls whether touch feedback is enabled.
        if (enabled) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        action()
    }
}

@Composable
internal fun PageArrival(page: Int, modifier: Modifier, content: @Composable () -> Unit) {
    val arrival = remember(page) { Animatable(0f) }
    LaunchedEffect(arrival) { arrival.animateTo(1f, tween(220, easing = FastOutSlowInEasing)) }
    // Keep only the current page composed and interactive, even during a rapid tab switch.
    Box(modifier.clipToBounds()) {
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                alpha = 0.85f + 0.15f * arrival.value
                translationY = (1f - arrival.value) * 8.dp.toPx()
            }
        ) {
            content()
        }
    }
}

@Composable
internal fun StatusDot(color: Color, state: Any) {
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(state) {
        pulse.snapTo(0f)
        pulse.animateTo(1f, tween(600, easing = LinearOutSlowInEasing))
    }
    Canvas(Modifier.size(10.dp)) {
        val progress = pulse.value
        if (progress < 1f) {
            drawCircle(
                color.copy(alpha = (1f - progress) * 0.45f),
                radius = (2.5f + 2f * progress).dp.toPx(),
                style = Stroke(1.dp.toPx()),
            )
        }
        drawCircle(color, radius = 2.5.dp.toPx())
    }
}

@Composable
internal fun LiveRecordingDot(active: Boolean) {
    if (!active) {
        StatusDot(Amber, false)
        return
    }
    val color = Green
    val transition = rememberInfiniteTransition(label = "Recording indicator")
    val pulse =
        transition.animateFloat(
            0.35f,
            1f,
            infiniteRepeatable(tween(1100), RepeatMode.Reverse),
            label = "Recording pulse",
        )
    Canvas(Modifier.size(12.dp)) {
        drawCircle(color.copy(alpha = pulse.value * 0.22f), radius = size.minDimension / 2)
        drawCircle(color.copy(alpha = 0.65f + pulse.value * 0.35f), radius = 3.dp.toPx())
    }
}

@Composable
internal fun Modifier.recordingHighlight(active: Boolean, color: Color): Modifier {
    if (!active) return this
    val transition = rememberInfiniteTransition(label = "Active journey action")
    val pulse =
        transition.animateFloat(
            0.12f,
            0.4f,
            infiniteRepeatable(tween(1100), RepeatMode.Reverse),
            label = "Action pulse",
        )
    return drawWithContent {
        drawContent()
        drawRoundRect(
            color.copy(alpha = pulse.value),
            cornerRadius = CornerRadius(14.dp.toPx()),
            style = Stroke(2.dp.toPx()),
        )
    }
}
