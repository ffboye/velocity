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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp

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
    return {
        // No ignore-setting flags: Android controls whether touch feedback is enabled.
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
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
