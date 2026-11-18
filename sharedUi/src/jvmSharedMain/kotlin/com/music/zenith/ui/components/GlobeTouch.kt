package com.music.zenith.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Touch feedback for the search source globe: it shrinks while held, then
 * springs back and spins a full turn on tap.
 */
class GlobeTouch internal constructor(
    val interactionSource: MutableInteractionSource,
    internal val pressed: State<Boolean>,
    internal val spins: MutableIntState,
) {
    /** Wraps [action] so every tap also triggers the spin. */
    fun onTap(action: () -> Unit): () -> Unit = {
        spins.intValue += 1
        action()
    }
}

@Composable
fun rememberGlobeTouch(): GlobeTouch {
    val source = remember { MutableInteractionSource() }
    val pressed = source.collectIsPressedAsState()
    val spins = remember { mutableIntStateOf(0) }
    return remember(source) { GlobeTouch(source, pressed, spins) }
}

@Composable
fun Modifier.globeTouch(touch: GlobeTouch): Modifier {
    val scale = animateFloatAsState(
        targetValue = if (touch.pressed.value) 0.78f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "globeScale",
    )
    val spin = animateFloatAsState(
        targetValue = touch.spins.intValue * 360f,
        animationSpec = spring(
            dampingRatio = 0.7f,
            stiffness = Spring.StiffnessLow,
        ),
        label = "globeSpin",
    )
    return this.graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
        rotationY = spin.value
        cameraDistance = 12f * density
    }
}