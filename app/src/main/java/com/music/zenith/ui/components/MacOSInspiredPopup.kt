package com.music.zenith.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.Window
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.music.zenith.data.settings.AppSettings
import kotlin.math.hypot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Motion of a macOS-style window: one progress value (0 = folded into the
 * trigger, 1 = open) drives scale, alpha, a short pull toward the origin and the
 * scrim. Opening and closing read the same mapping, so closing is the exact
 * reverse and an interrupted animation continues from where it is.
 */
internal data class MacOSPopupSpec(
    /** Scale at progress 0, about the trigger point. */
    val startScale: Float = 0.9f,
    /** How quickly alpha reaches 1; 2.4 means fully opaque at ~42% progress. */
    val alphaRamp: Float = 2.4f,
    /** Fraction of the card-to-trigger distance the card is pulled while closed. */
    val originPull: Float = 0.16f,
    /** Upper bound of that pull, so a far-away trigger never makes the card fly. */
    val maxTravel: Dp = 56.dp,
    val openSpring: SpringSpec<Float> = spring(
        dampingRatio = 0.82f,
        stiffness = 380f,
        visibilityThreshold = 0.001f,
    ),
    val closeSpring: SpringSpec<Float> = spring(
        dampingRatio = 1f,
        stiffness = 560f,
        visibilityThreshold = 0.001f,
    ),
    val scrimColor: Color = Color.Black.copy(alpha = 0.32f),
)

/** A trigger element the popup opens from and shrinks back into. */
@Stable
internal class PopupAnchor {
    internal var bounds by mutableStateOf<Rect?>(null)
    val center: Offset? get() = bounds?.center
}

@Composable
internal fun rememberPopupAnchor(): PopupAnchor = remember { PopupAnchor() }

/** Marks this element as the origin of a popup; bounds are kept in screen coordinates. */
internal fun Modifier.popupAnchor(anchor: PopupAnchor): Modifier = onGloballyPositioned { c ->
    if (c.isAttached) anchor.bounds = Rect(c.localToScreen(Offset.Zero), c.size.toSize())
}

/**
 * Where the last touch landed, for popups whose trigger registered no
 * [PopupAnchor]. Only a fresh touch counts: a popup opened by a timer or a
 * callback should not appear to come from an old tap.
 */
internal object PopupTapOrigin {
    private const val FRESH_MS = 1500L
    private var x = 0f
    private var y = 0f
    private var at = Long.MIN_VALUE

    fun record(rawX: Float, rawY: Float) {
        x = rawX
        y = rawY
        at = SystemClock.uptimeMillis()
    }

    fun take(): Offset? =
        if (at != Long.MIN_VALUE && SystemClock.uptimeMillis() - at <= FRESH_MS) Offset(x, y) else null
}

/** Records touches on the activity window into [PopupTapOrigin]; never consumes anything. */
@Composable
internal fun PopupTapOriginTracker() {
    val context = LocalContext.current
    DisposableEffect(context) {
        val window = context.popupActivity()?.window
        val base = window?.callback
        if (window == null || base == null) return@DisposableEffect onDispose { }
        val tracking = object : Window.Callback by base {
            override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                if (event.actionMasked == MotionEvent.ACTION_DOWN ||
                    event.actionMasked == MotionEvent.ACTION_UP
                ) {
                    PopupTapOrigin.record(event.rawX, event.rawY)
                }
                return base.dispatchTouchEvent(event)
            }
        }
        window.callback = tracking
        onDispose { if (window.callback === tracking) window.callback = base }
    }
}

private fun Context.popupActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/**
 * The animation state of one popup. [requestClose] plays the reverse animation
 * and only then calls the owner's `onClosed`, so the composable is released
 * after the motion, not before. A second request while closing is ignored, and
 * [reopen] turns a close around mid-flight from the current progress and velocity.
 */
@Stable
internal class MacOSPopupState internal constructor(
    private val scope: CoroutineScope,
    initialProgress: Float,
    private val tapOrigin: Offset?,
) {
    internal var spec: MacOSPopupSpec = MacOSPopupSpec()
    internal var animated: Boolean = true
    internal var onClosed: () -> Unit = {}
    internal var anchor: PopupAnchor? = null

    val progress = Animatable(initialProgress)

    var closing by mutableStateOf(false)
        private set

    internal var bounds by mutableStateOf(Rect.Zero)
    internal val origin: Offset? get() = anchor?.center ?: tapOrigin

    internal suspend fun open() {
        if (animated) progress.animateTo(1f, spec.openSpring) else progress.snapTo(1f)
    }

    fun requestClose() {
        if (closing) return
        closing = true
        scope.launch {
            if (animated) progress.animateTo(0f, spec.closeSpring) else progress.snapTo(0f)
            onClosed()
            // An owner that declines (a guarded onDismiss) leaves the popup
            // composed; it is still here two frames later, so bring it back
            // rather than leave an invisible card on top of the page.
            withFrameNanos { }
            withFrameNanos { }
            if (closing) {
                closing = false
                progress.animateTo(1f, spec.openSpring)
            }
        }
    }

    internal fun reopen() {
        if (!closing) return
        closing = false
        scope.launch { progress.animateTo(1f, spec.openSpring) }
    }
}

/**
 * Creates the motion for a popup that is composed only while shown. It opens
 * on entry; [onClosed] runs once the exit animation has finished. "Reduce
 * animation" skips both.
 */
@Composable
internal fun rememberMacOSPopupState(
    spec: MacOSPopupSpec = MacOSPopupSpec(),
    anchor: PopupAnchor? = null,
    onClosed: () -> Unit,
): MacOSPopupState {
    val reduce by AppSettings.reduceAnimation.collectAsState()
    val scope = rememberCoroutineScope()
    val state = remember { MacOSPopupState(scope, if (reduce) 1f else 0f, PopupTapOrigin.take()) }
    state.spec = spec
    state.animated = !reduce
    state.anchor = anchor
    state.onClosed = onClosed
    LaunchedEffect(state) { state.open() }
    return state
}

/**
 * Scale, alpha and a bounded pull toward the origin, all from the one
 * progress value and read in the graphics layer only, so a frame never
 * recomposes. Put it before the card's clip and background.
 */
internal fun Modifier.macOSPopupMotion(state: MacOSPopupState): Modifier = this
    .onGloballyPositioned { c ->
        if (c.isAttached) state.bounds = Rect(c.localToScreen(Offset.Zero), c.size.toSize())
    }
    .graphicsLayer {
        val p = state.progress.value
        val spec = state.spec
        alpha = (p * spec.alphaRamp).coerceIn(0f, 1f)
        val s = spec.startScale + (1f - spec.startScale) * p
        scaleX = s
        scaleY = s

        val o = state.origin
        val b = state.bounds
        if (o != null && b.width > 0f && b.height > 0f) {
            transformOrigin = TransformOrigin(
                ((o.x - b.left) / b.width).coerceIn(-1f, 2f),
                ((o.y - b.top) / b.height).coerceIn(-1f, 2f),
            )
            val pull = (1f - p).coerceIn(0f, 1f) * spec.originPull
            var dx = (o.x - b.center.x) * pull
            var dy = (o.y - b.center.y) * pull
            val travel = spec.maxTravel.toPx()
            val d = hypot(dx, dy)
            if (d > travel && d > 0f) {
                dx *= travel / d
                dy *= travel / d
            }
            translationX = dx
            translationY = dy
        } else {
            transformOrigin = TransformOrigin.Center
        }
    }

/** The dim behind the card, fading with the same progress as the card. */
internal fun Modifier.macOSPopupScrim(state: MacOSPopupState, color: Color? = null): Modifier =
    drawBehind {
        drawRect(color ?: state.spec.scrimColor, alpha = state.progress.value.coerceIn(0f, 1f))
    }

/**
 * A window-like popup for any content: menu, queue, sleep timer, audio
 * settings, playlist picker, confirmation, context menu.
 *
 * Controlled by [visible]: it opens from [anchor] (or the last tap), and when
 * [visible] turns false it plays the reverse animation before leaving the
 * composition. Outside taps and Back call [onDismissRequest]; the owner decides
 * whether to hide it. [modifier] sizes the card, [shape] rounds it, [container]
 * paints it, and [spec] tunes the motion.
 */
@Composable
internal fun MacOSInspiredPopup(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier.width(ALERT_WIDTH),
    anchor: PopupAnchor? = null,
    spec: MacOSPopupSpec = MacOSPopupSpec(),
    shape: Shape = RoundedCornerShape(ALERT_CORNER),
    container: @Composable (Shape) -> Modifier = { s ->
        Modifier.background(MaterialTheme.colorScheme.surface, s)
    },
    dismissOnOutsideTap: Boolean = true,
    dismissOnBack: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    var present by remember { mutableStateOf(visible) }
    LaunchedEffect(visible) { if (visible) present = true }
    if (!visible && !present) return

    val state = rememberMacOSPopupState(spec = spec, anchor = anchor) {
        if (!visible) present = false
    }
    LaunchedEffect(visible) {
        if (visible) state.reopen() else state.requestClose()
    }
    // Kept enabled while closing so a second Back does not pop the page behind.
    BackHandler(enabled = dismissOnBack) { if (visible) onDismissRequest() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .macOSPopupScrim(state)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = { if (dismissOnOutsideTap && visible) onDismissRequest() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = modifier
                .macOSPopupMotion(state)
                .clip(shape)
                .then(container(shape))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = {},
                ),
            content = content,
        )
    }
}