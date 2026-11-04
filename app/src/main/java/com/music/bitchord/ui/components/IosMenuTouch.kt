package com.music.bitchord.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.utils.rememberIosMenuOverscrollFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import kotlin.math.abs
import kotlin.math.sign
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Sentuhan di dalam panel menu, seperti menu iOS: satu indikator (pil) meluncur
 * ke baris yang sedang disentuh jari, jari boleh digeser antar baris lalu
 * dilepas untuk memilih, dan seluruh panel bergoyang seperti jelly mengikuti
 * tarikan jari lalu memantul kembali saat dilepas.
 *
 * Baris mendaftar sebagai [Target] lewat [iosMenuTouchTarget]; panelnya memasang
 * [iosMenuTouchHost]. Tanpa host (LocalIosMenuTouch null) baris bekerja seperti biasa.
 */
internal class IosMenuTouch(private val scope: CoroutineScope) {
    var reduceMotion: Boolean = false

    class Target {
        var coords: LayoutCoordinates? = null
        var onClick: (() -> Unit)? = null
        var enabled: Boolean = true
        var radiusPx: Float = 0f
    }

    val targets = mutableListOf<Target>()
    var hostSize: Size = Size.Zero
        private set

    private var host: LayoutCoordinates? = null
    private var hovered: Target? = null
    private var startTarget: Target? = null
    private var leftStart = false
    private var scrolled = false
    private var anchorTarget: Target? = null
    private var anchorTop = 0f
    private var down = Offset.Zero
    private var downWindow = Offset.Zero

    val indicator = Animatable(Rect.Zero, Rect.VectorConverter)
    val indicatorAlpha = Animatable(0f)
    var indicatorRadius = 0f

    val jellyX = Animatable(0f)
    val jellyY = Animatable(0f)
    val jellyScaleX = Animatable(1f)
    val jellyScaleY = Animatable(1f)

    fun attachHost(coordinates: LayoutCoordinates) {
        host = coordinates
        hostSize = coordinates.size.toSize()
    }

    private fun Target.local(): Rect? {
        val c = coords?.takeIf { it.isAttached } ?: return null
        val h = host?.takeIf { it.isAttached } ?: return null
        // Koordinat host, bukan root: skala/geser jelly dan pop panel (layer induk)
        // tidak boleh ikut terhitung, kalau tidak baris "bergeser" dan dianggap scroll.
        return Rect(h.localPositionOf(c, Offset.Zero), c.size.toSize())
    }

    /** Posisi jari di ruang window: stabil walau panel sedang bergoyang. */
    private fun toWindow(p: Offset): Offset =
        host?.takeIf { it.isAttached }?.localToWindow(p) ?: p

    private fun targetAt(p: Offset): Target? =
        targets.firstOrNull { it.enabled && it.onClick != null && it.local()?.contains(p) == true }

    fun begin(pos: Offset) {
        down = pos
        downWindow = toWindow(pos)
        scrolled = false
        leftStart = false
        val t = targetAt(pos)
        startTarget = t
        anchorTarget = t ?: targets.firstOrNull()
        anchorTop = anchorTarget?.local()?.top ?: 0f
        select(t, animate = false)
    }

    fun move(pos: Offset, slop: Float, dead: Float, maxShift: Float) {
        // Daftar menu ikut tergulir: jari sedang menggulir, bukan memilih.
        val anchor = anchorTarget?.local()
        if (anchor != null && abs(anchor.top - anchorTop) > 2f) scrolled = true
        val t = if (scrolled) null else targetAt(pos)
        if (t !== startTarget && (pos - down).getDistance() > slop) leftStart = true
        if (t !== hovered) select(t, animate = true)
        // Menu yang sedang digulir tidak ikut meregang: tarikan jari itu milik daftar.
        jellyTo(if (scrolled) downWindow else toWindow(pos), dead, maxShift)
    }

    private fun select(t: Target?, animate: Boolean) {
        val previous = hovered
        hovered = t
        scope.launch {
            if (t == null) {
                indicatorAlpha.animateTo(0f, tween(140))
                return@launch
            }
            val r = t.local() ?: return@launch
            indicatorRadius = t.radiusPx
            if (previous == null || !animate || indicatorAlpha.value < 0.05f) {
                indicator.snapTo(r)
                indicatorAlpha.animateTo(1f, tween(70))
            } else {
                launch { indicatorAlpha.animateTo(1f, tween(70)) }
                indicator.animateTo(r, spring(dampingRatio = 0.78f, stiffness = 650f))
            }
        }
    }

    private fun jellyTo(pos: Offset, dead: Float, maxShift: Float) {
        if (reduceMotion) return
        fun soft(d: Float): Float = if (abs(d) <= dead) 0f else (abs(d) - dead) * sign(d)
        val dx = soft(pos.x - downWindow.x)
        val dy = soft(pos.y - downWindow.y)
        val w = hostSize.width.coerceAtLeast(1f)
        val h = hostSize.height.coerceAtLeast(1f)
        val tx = (dx * 0.07f).coerceIn(-maxShift, maxShift)
        val ty = (dy * 0.07f).coerceIn(-maxShift, maxShift)
        val ax = (abs(dx) / w * 0.22f).coerceAtMost(0.06f)
        val ay = (abs(dy) / h * 0.22f).coerceAtMost(0.06f)
        val sx = 1f + ax - ay * 0.5f
        val sy = 1f + ay - ax * 0.5f
        val follow = spring<Float>(dampingRatio = 0.6f, stiffness = 500f)
        scope.launch { jellyX.animateTo(tx, follow) }
        scope.launch { jellyY.animateTo(ty, follow) }
        scope.launch { jellyScaleX.animateTo(sx, follow) }
        scope.launch { jellyScaleY.animateTo(sy, follow) }
    }

    /** Jari lepas: indikator memudar, panel memantul kembali. Mengembalikan baris yang harus dipilih. */
    fun finish(released: Boolean): Target? {
        val t = hovered
        val fire = if (released && !scrolled && t != null && (t !== startTarget || leftStart)) t else null
        hovered = null
        startTarget = null
        anchorTarget = null
        scope.launch { indicatorAlpha.animateTo(0f, tween(160)) }
        if (!reduceMotion) {
            val wobble = spring<Float>(dampingRatio = 0.34f, stiffness = 260f)
            scope.launch { jellyX.animateTo(0f, wobble) }
            scope.launch { jellyY.animateTo(0f, wobble) }
            scope.launch { jellyScaleX.animateTo(1f, wobble) }
            scope.launch { jellyScaleY.animateTo(1f, wobble) }
        }
        return fire
    }
}

internal val LocalIosMenuTouch = compositionLocalOf<IosMenuTouch?> { null }

@Composable
internal fun rememberIosMenuTouch(reduceMotion: Boolean): IosMenuTouch {
    val scope = rememberCoroutineScope()
    val touch = remember(scope) { IosMenuTouch(scope) }
    touch.reduceMotion = reduceMotion
    return touch
}

/** Dipasang pada isi panel: menggambar indikator di belakang baris dan membaca sentuhan jari. */
internal fun Modifier.iosMenuTouchHost(touch: IosMenuTouch, color: Color): Modifier = this
    .onGloballyPositioned { touch.attachHost(it) }
    .drawBehind {
        val a = touch.indicatorAlpha.value
        if (a > 0.01f) {
            val r = touch.indicator.value
            drawRoundRect(
                color = color.copy(alpha = color.alpha * a),
                topLeft = r.topLeft,
                size = r.size,
                cornerRadius = CornerRadius(touch.indicatorRadius),
            )
        }
    }
    .pointerInput(touch) {
        val slop = viewConfiguration.touchSlop
        val dead = 6.dp.toPx()
        val maxShift = 12.dp.toPx()
        awaitEachGesture {
            // Hanya mengamati (tanpa mengonsumsi) sampai jari lepas di baris lain.
            val first = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            touch.begin(first.position)
            var released: PointerInputChange? = null
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == first.id } ?: break
                if (change.changedToUpIgnoreConsumed()) {
                    released = change
                    break
                }
                if (!change.pressed) break
                touch.move(change.position, slop, dead, maxShift)
            }
            val fire = touch.finish(released != null)
            if (fire != null) {
                released?.consume()
                fire.onClick?.invoke()
            }
        }
    }

/** Mendaftarkan satu baris sebagai tujuan indikator. Dipasang setelah padding luar baris. */
@Composable
internal fun Modifier.iosMenuTouchTarget(enabled: Boolean, corner: Dp, onClick: (() -> Unit)?): Modifier {
    val touch = LocalIosMenuTouch.current ?: return this
    val target = remember { IosMenuTouch.Target() }
    val radius = with(LocalDensity.current) { corner.toPx() }
    target.onClick = onClick
    target.enabled = enabled
    target.radiusPx = radius
    DisposableEffect(touch, target) {
        touch.targets.add(target)
        onDispose { touch.targets.remove(target) }
    }
    return this.onGloballyPositioned { target.coords = it }
}

/**
 * Goyangan jelly di lapisan panel: bergeser dan meregang ke arah tarikan jari.
 * [grow] adalah skala pertumbuhan panel saat itu dan ([pivotX], [pivotY]) titik
 * tumpuannya, supaya regangan tetap berpusat di tengah panel.
 */
internal fun GraphicsLayerScope.applyIosMenuJelly(touch: IosMenuTouch, grow: Float, pivotX: Float, pivotY: Float) {
    if (touch.reduceMotion) return
    val jx = touch.jellyScaleX.value
    val jy = touch.jellyScaleY.value
    scaleX *= jx
    scaleY *= jy
    translationX += touch.jellyX.value + (0.5f - pivotX) * size.width * grow * (1f - jx)
    translationY += touch.jellyY.value + (0.5f - pivotY) * size.height * grow * (1f - jy)
}

/** Jelly untuk panel menu biasa (bukan [IosMenuPopover]), berpusat di ([originX], [originY]). */
internal fun Modifier.iosMenuJelly(touch: IosMenuTouch, originX: Float, originY: Float): Modifier =
    graphicsLayer {
        transformOrigin = TransformOrigin(originX, originY)
        applyIosMenuJelly(touch, 1f, originX, originY)
    }

/** Objek sentuhan untuk panel menu yang menggambar dirinya sendiri. */
@Composable
internal fun rememberJellyMenuTouch(): IosMenuTouch {
    val reduce by AppSettings.reduceAnimation.collectAsStateWithLifecycle()
    return rememberIosMenuTouch(reduce)
}

/** Pil sentuhan + jelly lewat [touch], dan overscroll pendek untuk daftar di dalam menu. */
@Composable
internal fun IosMenuScrollEnvironment(touch: IosMenuTouch, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalIosMenuTouch provides touch,
        LocalOverscrollFactory provides rememberIosMenuOverscrollFactory(),
        content = content,
    )
}

/** Isi panel menu biasa: kolom dengan pil sentuhan di belakang barisnya. */
@Composable
internal fun JellyMenuBody(
    touch: IosMenuTouch,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val pressed = iosMenuColors().pressed
    Column(modifier.iosMenuTouchHost(touch, pressed).padding(vertical = 8.dp)) {
        IosMenuScrollEnvironment(touch) { content() }
    }
}

/** Satu baris menu biasa: terdaftar ke pil sentuhan, tanpa ripple saat ada host. */
@Composable
internal fun Modifier.jellyMenuRow(
    corner: Dp = 14.dp,
    role: Role = Role.Button,
    onClick: () -> Unit,
): Modifier {
    val touch = LocalIosMenuTouch.current
    val source = remember { MutableInteractionSource() }
    return this
        .iosMenuTouchTarget(true, corner, onClick)
        .clip(RoundedCornerShape(corner))
        .clickable(
            interactionSource = source,
            indication = if (touch == null) LocalIndication.current else null,
            role = role,
            onClick = onClick,
        )
}