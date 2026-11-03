package com.music.bitchord.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.components.backdrop.backdrops.LayerBackdrop
import com.music.bitchord.ui.components.backdrop.drawBackdrop
import com.music.bitchord.ui.components.backdrop.effects.blur
import com.music.bitchord.ui.components.backdrop.effects.colorControls
import com.music.bitchord.ui.components.backdrop.effects.blur
import com.music.bitchord.ui.components.backdrop.effects.colorControls
import com.music.bitchord.ui.components.backdrop.effects.lens
import com.music.bitchord.ui.components.backdrop.highlight.Highlight
import com.music.bitchord.ui.components.backdrop.shadow.Shadow
import kotlin.math.roundToInt

/**
 * The thumb of iOS 26's switch and slider: a white pill at rest that, once
 * lifted, goes clear and refracts whatever is under it.
 *
 * [backdrop] is a recording of the control's own track, taken by the caller
 * with `layerBackdrop` — the glass looks through to the track and nothing
 * else, so it costs a tiny lens rather than a capture of the page. [glass] is
 * how much of the material is on, 0 at rest to 1 lifted; it is read at draw
 * time, so a frame of the animation is a redraw and not a recomposition.
 *
 * Without glass support, or with "Reduce dynamic blur" on, it is the plain
 * white pill the controls had before.
 */
@Composable
internal fun LiquidGlassThumb(
    backdrop: LayerBackdrop,
    glass: () -> Float,
    modifier: Modifier = Modifier,
) {
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    if (!isGlassSupported() || reduceDynamicBlur) {
        RestingThumb(modifier)
        return
    }
    val shape = remember { RoundedCornerShape(percent = 50) }
    Box(
        modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                val g = glass().coerceIn(0f, 1f)
                if (g > 0.01f) {
                    colorControls(saturation = 1f + 0.6f * g)
                    blur(1.dp.toPx() * g)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        lens(
                            refractionHeight = 12.dp.toPx() * g,
                            refractionAmount = 18.dp.toPx() * g,
                            depthEffect = true,
                        )
                    }
                }
            },
            highlight = {
                val g = glass().coerceIn(0f, 1f)
                if (g > 0.02f) Highlight.Default.copy(alpha = g) else null
            },
            shadow = {
                val g = glass().coerceIn(0f, 1f)
                Shadow(
                    radius = (3f + 8f * g).dp,
                    offset = DpOffset(0.dp, (1f + 3f * g).dp),
                    color = Color.Black.copy(alpha = 0.22f - 0.02f * g),
                )
            },
            onDrawSurface = {
                // Opaque white at rest; lifted, a frosted white glass with a
                // bright rim, so the thumb still reads as a pill over the track.
                val g = glass().coerceIn(0f, 1f)
                val fill = 1f - 0.64f * g
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = fill),
                            Color.White.copy(alpha = fill - 0.14f * g),
                        ),
                    ),
                    size = size,
                )
                if (g > 0.02f) {
                    val w = 1.5.dp.toPx()
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.8f * g),
                        topLeft = Offset(w / 2f, w / 2f),
                        size = Size(size.width - w, size.height - w),
                        cornerRadius = CornerRadius(size.height / 2f),
                        style = Stroke(w),
                    )
                }
            },
        ),
    )
}

/** The thumb at rest: plain drawing, so a column of switches stays cheap to scroll. */
@Composable
internal fun RestingThumb(modifier: Modifier = Modifier) {
    Box(modifier.shadow(2.dp, CircleShape).background(Color.White, CircleShape))
}

/** [rest] to [lifted] in pixels at [t]; [t] may overshoot, which is the bounce. */
internal fun Density.liftedPx(rest: Dp, lifted: Dp, t: Float): Float =
    rest.toPx() + (lifted.toPx() - rest.toPx()) * t

/**
 * Sizes the thumb between its resting and lifted dimensions, read on layout so
 * a frame of the lift is a relayout. It may stand taller than the track around
 * it: what overflows is centred, as it is on iOS.
 */
internal fun Modifier.liftingSize(
    restWidth: Dp,
    restHeight: Dp,
    liftedWidth: Dp,
    liftedHeight: Dp,
    lift: () -> Float,
): Modifier = layout { measurable, constraints ->
    val t = lift()
    val w = liftedPx(restWidth, liftedWidth, t).roundToInt().coerceAtLeast(1)
    val h = liftedPx(restHeight, liftedHeight, t).roundToInt().coerceAtLeast(1)
    val placeable = measurable.measure(Constraints.fixed(w, h))
    val outW = w.coerceAtMost(constraints.maxWidth)
    val outH = h.coerceAtMost(constraints.maxHeight)
    layout(outW, outH) { placeable.place((outW - w) / 2, (outH - h) / 2) }
}