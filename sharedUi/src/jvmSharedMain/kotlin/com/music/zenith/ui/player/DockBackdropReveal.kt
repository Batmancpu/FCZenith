package com.music.zenith.ui.player

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer

/** Drag progress at which the blurred artwork under the cover starts to appear. */
private const val DOCK_BLUR_FROM = 0.05f

/**
 * How much of the blurred artwork below the cover is revealed for a given
 * open fraction (0 = mini player, 1 = full player). One curve, driven only by
 * the sheet's progress, so the blur follows the finger and shortens again
 * when the sheet is pulled back down. Roughly 0.07 at 0.2, 0.30 at 0.4,
 * 0.65 at 0.6, 0.91 at 0.8 and 1 at 1.0.
 */
internal fun dockBlurExtension(open: Float): Float {
    val x = ((open - DOCK_BLUR_FROM) / (1f - DOCK_BLUR_FROM)).coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

/**
 * Draws the recorded cover + blur set in its own (unscaled) coordinates, but
 * only down to a lead edge that grows with [extension].
 *
 * [coverBottom] is where the flying cover ends in the set's coordinates. The
 * blur is hidden above the cover and fully opaque right at its foot, so it
 * joins the cover with no seam. Below the cover it is revealed down to the
 * lead edge, whose last stretch is feathered with a soft eased gradient mask
 * (the feather shrinks to nothing as [extension] reaches 1, so the finished
 * player is untouched). The mask is applied in one offscreen pass.
 */
internal fun DrawScope.drawDockBackdropReveal(
    layer: GraphicsLayer,
    coverBottom: Float,
    extension: Float,
) {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f || extension <= 0.001f) return
    val cover = coverBottom.coerceIn(1f, h)
    val lead = cover + (h - cover) * extension.coerceIn(0f, 1f)
    val feather = (lead - cover) * 0.5f * (1f - extension.coerceIn(0f, 1f))
    val solidTo = lead - feather

    var last = 0f
    fun at(y: Float): Float {
        last = maxOf(last, (y / h).coerceIn(0f, 1f))
        return last
    }

    val mask = Brush.verticalGradient(
        colorStops = arrayOf(
            0f to Color.Transparent,
            at(cover * 0.35f) to Color.Transparent,
            at(cover * 0.65f) to Color.Black,
            at(solidTo) to Color.Black,
            at(solidTo + feather * 0.25f) to Color.Black.copy(alpha = 0.9f),
            at(solidTo + feather * 0.50f) to Color.Black.copy(alpha = 0.5f),
            at(solidTo + feather * 0.75f) to Color.Black.copy(alpha = 0.1f),
            at(lead) to Color.Transparent,
            1f to Color.Transparent,
        ),
        startY = 0f,
        endY = h,
    )

    val canvas = drawContext.canvas
    canvas.saveLayer(Rect(0f, 0f, w, h), Paint())
    drawLayer(layer)
    drawRect(brush = mask, size = size, blendMode = BlendMode.DstIn)
    canvas.restore()
}