package com.music.bitchord.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.unit.IntOffset
import com.music.bitchord.ui.components.backdrop.backdrops.layerBackdrop
import com.music.bitchord.ui.components.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.music.bitchord.ui.haptics.Haptic
import com.music.bitchord.ui.haptics.rememberHaptics

/**
 * The iOS 26 switch: a wide green capsule and a white pill of a thumb, which
 * stretches a little toward where it is going while pressed.
 *
 * Pressed, or flipped by its row, the thumb lifts into iOS 26's glass: it grows
 * past the track and goes clear, refracting the track under it — see
 * [LiquidGlassThumb]. At rest it is plain drawing — a track, a thumb, a shadow —
 * and the glass is composed only while it is lifted, because every settings
 * page has a column of these and anything heavier per switch is paid that many
 * times over on every scroll frame.
 *
 * It takes Material3's `Switch` call shape so a settings row adopts it by name
 * alone. [thumbContent] and [colors] are accepted and ignored: on is iOS green
 * whatever the theme's accent — the one place the platform keeps its own
 * colour, and the reason it reads as a switch.
 *
 * A null [onCheckedChange] makes it a read-out, the way M3's does: the row
 * around it owns the tap.
 *
 * Interactive, it takes up M3's 48dp touch target around the drawn capsule, as
 * M3's does. The settings rows were spaced around that height — a sub-row has
 * no top padding of its own — and without it they close up on each other.
 *
 * It buzzes when [checked] flips rather than when it is tapped: most rows also
 * toggle from their own `onClick`, which never passes through here, and the
 * value changing is the one thing every path has in common.
 */
@Composable
fun IosSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") thumbContent: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    @Suppress("UNUSED_PARAMETER") colors: SwitchColors? = null,
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val position = remember { Animatable(if (checked) 1f else 0f) }
    val thumbSpring = spring<Float>(dampingRatio = 0.78f, stiffness = 700f)
    LaunchedEffect(checked) { position.animateTo(if (checked) 1f else 0f, thumbSpring) }
    // Jari di atas thumb: thumb mengikuti geseran di sepanjang track.
    var dragging by remember { mutableStateOf(false) }
    val currentChecked by rememberUpdatedState(checked)
    val currentOnChange by rememberUpdatedState(onCheckedChange)
    val dragTravelPx = with(density) { (TrackWidth - ThumbWidth - ThumbPadding * 2).toPx() }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val dragState = rememberDraggableState { delta ->
        val step = (if (rtl) -delta else delta) / dragTravelPx
        scope.launch { position.snapTo((position.value + step).coerceIn(0f, 1f)) }
    }
    // Held for a beat after a flip, so a switch toggled by its row's tap — no
    // finger on the thumb — lifts into glass for the trip as well.
    var flash by remember { mutableStateOf(false) }
    // Under-damped: the overshoot is the thumb popping past the track.
    val lift by animateFloatAsState(
        targetValue = if (pressed || flash || dragging) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 520f),
        label = "iosSwitchLift",
    )
    val glassActive by remember { derivedStateOf { pressed || flash || dragging || lift > 0.002f } }
    val track by animateColorAsState(
        targetValue = when {
            checked && dark -> Color(0xFF30D158)
            checked -> Color(0xFF34C759)
            else -> Color(0xFF8E8E93).copy(alpha = if (dark) 0.36f else 0.22f)
        },
        label = "iosSwitchTrack",
    )
    val trackBackdrop = rememberLayerBackdrop()

    val haptics = rememberHaptics()
    // Skips the first run, so a switch scrolling into view stays quiet.
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(checked) {
        if (settled) {
            haptics.play(if (checked) Haptic.ToggleOn else Haptic.ToggleOff)
            flash = true
            delay(320)
            flash = false
        }
        settled = true
    }

    Box(
        modifier = modifier
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .then(
                if (onCheckedChange != null) {
                    Modifier.minimumInteractiveComponentSize().toggleable(
                        value = checked,
                        enabled = enabled,
                        interactionSource = interaction,
                        indication = null,
                        role = Role.Switch,
                        onValueChange = onCheckedChange,
                    ).draggable(
                        state = dragState,
                        orientation = Orientation.Horizontal,
                        enabled = enabled,
                        onDragStarted = { dragging = true },
                        onDragStopped = {
                            dragging = false
                            val target = position.value >= 0.5f
                            if (target != currentChecked) currentOnChange?.invoke(target)
                            scope.launch { position.animateTo(if (target) 1f else 0f, thumbSpring) }
                        },
                    )
                } else {
                    Modifier
                },
            )
            .size(TrackWidth, TrackHeight),
        contentAlignment = Alignment.CenterStart,
    ) {
        // The track on its own: what the thumb's glass looks through. It is
        // recorded only while the glass is up.
        Box(
            Modifier
                .matchParentSize()
                .then(if (glassActive) Modifier.layerBackdrop(trackBackdrop) else Modifier)
                .clip(CircleShape)
                .background(track),
        )
        val thumb = Modifier
            .offset {
                // Travel is measured with the thumb's current width, so it
                // grows from the edge it is leaving and always lands flush.
                val w = liftedPx(ThumbWidth, LiftedThumbWidth, lift)
                val pad = ThumbPadding.toPx()
                val travel = (TrackWidth.toPx() - w - pad * 2f).coerceAtLeast(0f)
                IntOffset((pad + travel * position.value).roundToInt(), 0)
            }
            .liftingSize(
                restWidth = ThumbWidth,
                restHeight = ThumbHeight,
                liftedWidth = LiftedThumbWidth,
                liftedHeight = LiftedThumbHeight,
                lift = { lift },
            )
        if (glassActive) {
            LiquidGlassThumb(backdrop = trackBackdrop, glass = { lift }, modifier = thumb)
        } else {
            RestingThumb(thumb)
        }
    }
}

private val TrackWidth = 64.dp
private val TrackHeight = 28.dp
private val ThumbWidth = 40.dp
private val ThumbHeight = 24.dp
private val ThumbPadding = 2.dp
private val LiftedThumbWidth = 58.dp
private val LiftedThumbHeight = 38.dp
