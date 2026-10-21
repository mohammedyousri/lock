package com.mohammedyousri.glasstv.ui.components

import android.view.KeyEvent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.mohammedyousri.glasstv.core.RemoteKeys
import com.mohammedyousri.glasstv.ui.theme.GlassColors

private class PressState {
    var down = false
    var longFired = false
}

/**
 * Makes a node focusable and clickable with a remote: OK fires on release,
 * holding OK fires [onLongClick] (the remote equivalent of a context menu).
 * Taps work too, for touch screens and mice.
 */
fun Modifier.remoteClickable(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
): Modifier = composed {
    val state = remember { PressState() }
    val click by rememberUpdatedState(onClick)
    val longClick by rememberUpdatedState(onLongClick)
    this
        .onKeyEvent { e ->
            val native = e.nativeKeyEvent
            if (!enabled || !RemoteKeys.isSelect(native.keyCode)) return@onKeyEvent false
            when (native.action) {
                KeyEvent.ACTION_DOWN -> {
                    if (native.repeatCount == 0) {
                        state.down = true
                        state.longFired = false
                    } else if (state.down && !state.longFired && longClick != null &&
                        ((native.flags and KeyEvent.FLAG_LONG_PRESS) != 0 || native.eventTime - native.downTime >= 550)
                    ) {
                        state.longFired = true
                        longClick?.invoke()
                    }
                    true
                }
                KeyEvent.ACTION_UP -> {
                    val fire = state.down && !state.longFired && (native.flags and KeyEvent.FLAG_CANCELED) == 0
                    state.down = false
                    if (fire) click()
                    true
                }
                else -> false
            }
        }
        .focusable(enabled)
        .pointerInput(enabled) {
            if (enabled) detectTapGestures(onTap = { click() }, onLongPress = { longClick?.invoke() })
        }
}

/**
 * The tvOS-style focus treatment: the focused element lifts (scale + shadow),
 * brightens, and a soft light sweeps across it once.
 */
@Composable
fun FocusSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(14.dp),
    color: Color = GlassColors.Fill,
    focusedColor: Color = color,
    focusedScale: Float = 1.1f,
    focusedElevation: Dp = 20.dp,
    sheen: Boolean = true,
    enabled: Boolean = true,
    contentAlignment: Alignment = Alignment.TopStart,
    onFocusChange: ((Boolean) -> Unit)? = null,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (focused) focusedScale else 1f,
        spring(dampingRatio = 0.62f, stiffness = 380f),
        label = "focusScale",
    )
    val elevation by animateDpAsState(if (focused) focusedElevation else 0.dp, label = "focusElevation")
    val sweep = remember { Animatable(-1f) }
    LaunchedEffect(focused) {
        if (focused && sheen) {
            sweep.snapTo(-0.5f)
            sweep.animateTo(1.5f, tween(950, easing = FastOutSlowInEasing))
        }
    }
    Box(
        modifier
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                shadowElevation = elevation.toPx()
                this.shape = shape
                clip = true
            }
            .background(if (focused) focusedColor else color)
            .drawWithContent {
                drawContent()
                val p = sweep.value
                if (focused && sheen && p > -0.5f && p < 1.5f) {
                    val w = size.width
                    drawRect(
                        Brush.linearGradient(
                            colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.22f), Color.Transparent),
                            start = Offset(w * (p - 0.35f), 0f),
                            end = Offset(w * (p + 0.35f), size.height),
                        ),
                    )
                }
            }
            .onFocusChanged {
                focused = it.isFocused
                onFocusChange?.invoke(it.isFocused)
            }
            .remoteClickable(onClick, onLongClick, enabled),
        contentAlignment = contentAlignment,
    ) {
        content(focused)
    }
}
