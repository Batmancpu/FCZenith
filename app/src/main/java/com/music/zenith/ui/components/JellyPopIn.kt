package com.music.zenith.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.collectAsState
import com.music.zenith.data.settings.AppSettings

/**
 * Kemunculan popup dengan spring jelly: kartu membesar dari ~0.8x, melewati
 * ukuran akhir sedikit, lalu bergoyang (X dan Y berlawanan fase) sebelum diam.
 * Dipasang di kartu popup (alert, popup audio) tepat setelah width, sebelum clip.
 * "Reduce animation" mematikannya.
 */
@Composable
internal fun Modifier.jellyPopIn(): Modifier {
    val reduce by AppSettings.reduceAnimation.collectAsState()
    val progress = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(reduce) {
        if (reduce) {
            progress.snapTo(1f)
        } else {
            progress.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 500f))
        }
    }
    return graphicsLayer {
        val p = progress.value
        alpha = (p * 3f).coerceIn(0f, 1f)
        val scale = 0.92f + 0.08f * p
        val wobble = (p - 1f) * p * p * 0.12f
        scaleX = scale - wobble
        scaleY = scale + wobble
    }
}