package com.music.zenith.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.music.zenith.data.settings.AppSettings
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/**
 * Jelly untuk tombol kaca ala Apple Music: membesar secara fluid saat
 * ditekan, meregang dan bergeser mengikuti jari saat digeser, lalu memantul
 * (spring redaman rendah) saat dilepas. Event hanya diamati, tidak pernah
 * dikonsumsi, jadi klik dan gestur anak tidak berubah. Pasang di awal rantai
 * modifier tombol, sebelum size dan permukaan kaca, supaya seluruh kaca ikut
 * bergoyang. "Reduce animation" mematikannya.
 */
@Composable
internal fun Modifier.glassJelly(pressScale: Float = GLASS_JELLY_PRESS): Modifier {
    val reduce by AppSettings.reduceAnimation.collectAsState()
    val scope = rememberCoroutineScope()
    val offX = remember { Animatable(0f) }
    val offY = remember { Animatable(0f) }
    val stX = remember { Animatable(1f) }
    val stY = remember { Animatable(1f) }
    val maxShift = with(LocalDensity.current) { 3.dp.toPx() }
    val releaseJob = remember { arrayOfNulls<Job>(1) }
    return this
        .pointerInput(reduce, pressScale) {
            if (reduce) return@pointerInput
            val slop = viewConfiguration.touchSlop
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                releaseJob[0]?.cancel()
                var lastUp = down.uptimeMillis
                val press = spring<Float>(dampingRatio = 0.6f, stiffness = 520f)
                scope.launch { stX.animateTo(pressScale, press) }
                scope.launch { stY.animateTo(pressScale, press) }
                var total = Offset.Zero
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Final)
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change != null) lastUp = change.uptimeMillis
                    if (change == null || !change.pressed) break
                    if (change.isConsumed) continue
                    total += change.positionChange()
                    fun soft(d: Float): Float = if (abs(d) <= slop) 0f else (abs(d) - slop) * sign(d)
                    val dx = soft(total.x)
                    val dy = soft(total.y)
                    val w = size.width.coerceAtLeast(1).toFloat()
                    val h = size.height.coerceAtLeast(1).toFloat()
                    val ax = (abs(dx) / w * 0.25f).coerceAtMost(0.05f)
                    val ay = (abs(dy) / h * 0.25f).coerceAtMost(0.05f)
                    val tx = (dx * 0.06f).coerceIn(-maxShift, maxShift)
                    val ty = (dy * 0.06f).coerceIn(-maxShift, maxShift)
                    scope.launch { offX.snapTo(tx) }
                    scope.launch { offY.snapTo(ty) }
                    scope.launch { stX.snapTo(pressScale + ax - ay * 0.5f) }
                    scope.launch { stY.snapTo(pressScale + ay - ax * 0.5f) }
                }
                val wobble = spring<Float>(dampingRatio = 0.48f, stiffness = 320f)
                // Ketukan singkat tetap sempat membesar sebelum memantul kembali.
                val held = lastUp - down.uptimeMillis
                releaseJob[0] = scope.launch {
                    if (held < GLASS_JELLY_MIN_HOLD_MS) delay(GLASS_JELLY_MIN_HOLD_MS - held)
                    launch { offX.animateTo(0f, wobble) }
                    launch { offY.animateTo(0f, wobble) }
                    launch { stX.animateTo(1f, wobble) }
                    launch { stY.animateTo(1f, wobble) }
                }
            }
        }
        .graphicsLayer {
            translationX = offX.value
            translationY = offY.value
            scaleX = stX.value
            scaleY = stY.value
        }
}

/** Skala tombol saat ditekan (membesar), sebelum regangan mengikuti jari. */
internal const val GLASS_JELLY_PRESS = 1.12f

/** Skala untuk elemen lebar (kolom search, pil Play/Shuffle) agar tidak terlalu melebar. */
internal const val GLASS_JELLY_PRESS_WIDE = 1.05f

/** Lama minimal fase membesar sebelum memantul, supaya ketukan singkat tetap terasa. */
internal const val GLASS_JELLY_MIN_HOLD_MS = 90L