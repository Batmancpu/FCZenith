package com.music.bitchord.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.requiredHeight
import com.music.bitchord.ui.components.backdrop.backdrops.layerBackdrop
import com.music.bitchord.ui.components.backdrop.backdrops.rememberLayerBackdrop
import kotlin.math.roundToInt

/**
 * The iOS 26 slider: a thin capsule track with a pill of a thumb. Touched, the
 * thumb lifts into glass and refracts the track under it (see
 * [LiquidGlassThumb]); let go, it settles back to white.
 *
 * It takes the shape of Material3's `Slider` the settings rows already call.
 * Horizontal drags and taps set the value, so a vertical swipe that starts on
 * it still scrolls the page. With [steps] above zero the value snaps to that
 * many evenly spaced stops between the ends. A null [activeColor] is the
 * theme's accent; a null [inactiveColor] is the platform's grey.
 */
@Composable
fun IosSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    enabled: Boolean = true,
    activeColor: Color? = null,
    inactiveColor: Color? = null,
) {
    val density = LocalDensity.current
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val active = activeColor ?: MaterialTheme.colorScheme.primary
    val inactive = inactiveColor ?: Color(0xFF8E8E93).copy(alpha = if (dark) 0.36f else 0.24f)

    val span = valueRange.endInclusive - valueRange.start
    val fraction = if (span > 0f) ((value - valueRange.start) / span).coerceIn(0f, 1f) else 0f
    // Eased, so the stops of a stepped slider glide instead of jumping.
    val shown by animateFloatAsState(
        targetValue = fraction,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 700f),
        label = "iosSliderFraction",
    )

    var widthPx by remember { mutableFloatStateOf(0f) }
    var pressing by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    val lift by animateFloatAsState(
        targetValue = if (pressing || dragging) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 520f),
        label = "iosSliderLift",
    )
    // The glass and the recording of the track exist only while it is lifted.
    val glassActive by remember { derivedStateOf { pressing || dragging || lift > 0.002f } }
    val trackBackdrop = rememberLayerBackdrop()
    val latestOnValueChange by rememberUpdatedState(onValueChange)

    // The thumb's centre travels between half a resting thumb in from each end,
    // so the mapping does not change while the thumb grows under the finger.
    fun valueAt(x: Float): Float {
        val inset = with(density) { SliderThumbWidth.toPx() } / 2f
        val usable = (widthPx - inset * 2f).coerceAtLeast(1f)
        var f = ((x - inset) / usable).coerceIn(0f, 1f)
        if (steps > 0) {
            val stops = steps + 1
            f = (f * stops).roundToInt() / stops.toFloat()
        }
        return valueRange.start + span * f
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(SliderHeight)
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .onSizeChanged { widthPx = it.width.toFloat() }
            .then(
                if (enabled) {
                    Modifier
                        .pointerInput(valueRange, steps) {
                            detectTapGestures(
                                onPress = {
                                    pressing = true
                                    tryAwaitRelease()
                                    pressing = false
                                },
                                onTap = { offset -> latestOnValueChange(valueAt(offset.x)) },
                            )
                        }
                        .pointerInput(valueRange, steps) {
                            detectHorizontalDragGestures(
                                onDragStart = { offset ->
                                    dragging = true
                                    latestOnValueChange(valueAt(offset.x))
                                },
                                onDragEnd = { dragging = false },
                                onDragCancel = { dragging = false },
                                onHorizontalDrag = { change, _ ->
                                    change.consume()
                                    latestOnValueChange(valueAt(change.position.x))
                                },
                            )
                        }
                } else {
                    Modifier
                },
            )
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(value.coerceIn(valueRange), valueRange, steps)
                if (enabled) {
                    setProgress { target ->
                        onValueChange(target.coerceIn(valueRange.start, valueRange.endInclusive))
                        true
                    }
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        // Track: yang dilihat kaca thumb. Saat kaca aktif ia direkam dalam kotak
        // setinggi thumb terangkat, supaya lensa tidak menarik piksel tepi.
        Box(
            Modifier
                .fillMaxWidth()
                .height(SliderTrackHeight)
                .then(
                    if (glassActive) {
                        Modifier.requiredHeight(SliderLiftedThumbHeight).layerBackdrop(trackBackdrop)
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(SliderTrackHeight)
                    .clip(CircleShape)
                    .background(inactive)
                    .drawBehind {
                        // The fill runs to the thumb's centre and on under it.
                        val inset = SliderThumbWidth.toPx() / 2f
                        val end = inset + (size.width - inset * 2f) * shown.coerceIn(0f, 1f)
                        drawRect(color = active, size = Size(end.coerceIn(0f, size.width), size.height))
                    },
            )
        }
        val thumb = Modifier
            .offset {
                val w = liftedPx(SliderThumbWidth, SliderLiftedThumbWidth, lift)
                val inset = SliderThumbWidth.toPx() / 2f
                val centre = inset + (widthPx - inset * 2f) * shown.coerceIn(0f, 1f)
                IntOffset((centre - w / 2f).roundToInt(), 0)
            }
            .liftingSize(
                restWidth = SliderThumbWidth,
                restHeight = SliderThumbHeight,
                liftedWidth = SliderLiftedThumbWidth,
                liftedHeight = SliderLiftedThumbHeight,
                lift = { lift },
            )
        if (glassActive) {
            LiquidGlassThumb(backdrop = trackBackdrop, glass = { lift }, modifier = thumb)
        } else {
            RestingThumb(thumb)
        }
    }
}

private val SliderHeight = 40.dp
private val SliderTrackHeight = 6.dp
private val SliderThumbWidth = 34.dp
private val SliderThumbHeight = 22.dp
private val SliderLiftedThumbWidth = 46.dp
private val SliderLiftedThumbHeight = 30.dp