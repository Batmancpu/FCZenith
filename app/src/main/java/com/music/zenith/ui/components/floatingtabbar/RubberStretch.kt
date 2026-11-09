package com.music.zenith.ui.components.floatingtabbar

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Tarikan karet pada accessory: 0 = diam, positif = tertarik ke atas dan
 * memanjang, negatif = terlepas ke bawah dan memipih. Transformasi layer saja,
 * tanpa shader, jadi tidak ada yang terpotong oleh batas layer. Dibaca di
 * graphicsLayer, jadi satu frame animasi tidak merekomposisi apa pun.
 */
internal fun Modifier.rubberStretch(liftPx: Float, stretch: () -> Float): Modifier = graphicsLayer {
    val p = stretch()
    transformOrigin = TransformOrigin(0.5f, 1f)
    translationY = -liftPx * p
    scaleY = 1f + 0.12f * p
    scaleX = 1f - 0.05f * p
    clip = false
}
