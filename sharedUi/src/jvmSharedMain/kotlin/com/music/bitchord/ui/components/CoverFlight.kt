package com.music.bitchord.ui.components

import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.runtime.withFrameNanos
import kotlin.math.exp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/*
 * A cover that flies from the card that was tapped to the sleeve of the page it
 * opens — the same idea as the mini player's pill growing into the player
 * (PlayerDock): measure where the cover is in the window, measure where it is
 * going, and move ONE picture between the two on a single eased curve.
 *
 * Nothing flies unless the page reports its sleeve within a moment; then the
 * card simply stays as it is and the page cross-fades in as before.
 */

private val FlightEasing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)
private const val FLIGHT_MS = 780
private const val TARGET_TIMEOUT_MS = 700L
private const val RETURN_TIMEOUT_MS = 450L

/** How fast the flight's idea of its destination follows the real one, per second. */
private const val CHASE_RATE = 16f

/** A card or sleeve must be at least this much on screen to be flown to or from. */
private const val MIN_VISIBLE = 0.5f

private class FlightSource(
    var bounds: Rect = Rect.Zero,
    var model: Any? = null,
    var cornerPx: Float = 0f,
    var footFade: Boolean = false,
)

private class Flight(
    val browseId: String,
    /** True when the page is closing and the cover flies back to its card. */
    val reverse: Boolean,
    val from: Rect,
    val fromCornerPx: Float,
    val fromWash: Color = Color.Unspecified,
) {
    var model: Any? = null
    var toCornerPx: Float = 0f
    var footFade: Boolean = false

    /** The card the cover leaves from or returns to. Set last: the layer waits on it. */
    var token by mutableStateOf<Any?>(null)
}

private object CoverFlightState {
    val sources = HashMap<Any, FlightSource>()
    val ids = HashMap<Any, String>()
    var flight by mutableStateOf<Flight?>(null)
    var target by mutableStateOf<Rect?>(null)
    var targetCornerPx by mutableStateOf(0f)
    var targetWash by mutableStateOf(Color.Unspecified)
    var origin by mutableStateOf(Offset.Zero)
    var host: Rect = Rect.Zero

    /** The page last flown into: only that page is flown back out of. */
    var landed: String? = null

    /** The cover is in the air: the card and the sleeve both stand aside for it. */
    val flying: Boolean
        get() {
            val f = flight ?: return false
            return f.token != null && (f.reverse || target != null)
        }

    fun hidesSource(token: Any): Boolean = flying && flight?.token === token

    fun hidesTarget(browseId: String): Boolean = flying && flight?.browseId == browseId

    fun destination(f: Flight): Rect? =
        if (f.reverse) {
            f.token?.let { sources[it]?.bounds }?.takeIf { !it.isEmpty }
        } else {
            target
        }

    /** Finds the card the page was opened from (the new one, after a back), if it is on screen. */
    fun resolveReturn(f: Flight): Boolean {
        var bestToken: Any? = null
        var bestShare = MIN_VISIBLE
        for ((token, source) in sources) {
            if (ids[token] != f.browseId) continue
            val share = visibleShare(source.bounds)
            if (share >= bestShare) {
                bestShare = share
                bestToken = token
            }
        }
        val token = bestToken ?: return false
        val source = sources[token] ?: return false
        f.model = source.model
        f.toCornerPx = source.cornerPx
        f.footFade = source.footFade
        f.token = token
        return true
    }
}

private fun LayoutCoordinates.whole(): Rect = Rect(positionInWindow(), size.toSize())

private fun visibleShare(r: Rect): Float {
    if (r.isEmpty) return 0f
    val host = CoverFlightState.host
    if (host.isEmpty) return 1f
    val i = r.intersect(host)
    if (i.isEmpty) return 0f
    return (i.width * i.height) / (r.width * r.height)
}

private fun startFlight(browseId: String, token: Any) {
    val source = CoverFlightState.sources[token] ?: return
    if (source.bounds.isEmpty) return
    CoverFlightState.target = null
    CoverFlightState.targetWash = Color.Unspecified
    CoverFlightState.landed = browseId
    CoverFlightState.flight = Flight(browseId, false, source.bounds, source.cornerPx).also {
        it.model = source.model
        it.footFade = source.footFade
        it.token = token
    }
}

private fun startReturn(browseId: String, from: Rect, cornerPx: Float, wash: Color) {
    if (CoverFlightState.landed != browseId) return
    CoverFlightState.landed = null
    if (visibleShare(from) < MIN_VISIBLE) return
    CoverFlightState.flight = Flight(browseId, true, from, cornerPx, wash)
}

private fun lerpF(a: Float, b: Float, p: Float): Float = a + (b - a) * p

private fun lerpRect(a: Rect, b: Rect, p: Float): Rect = Rect(
    lerpF(a.left, b.left, p),
    lerpF(a.top, b.top, p),
    lerpF(a.right, b.right, p),
    lerpF(a.bottom, b.bottom, p),
)

/**
 * Marks a card's cover as where a flight starts. [model] is what the picture
 * is loaded from; the card's own size and corner are measured here. [footFade]
 * is for a cover whose lower edge dissolves into the card behind it.
 */
@Composable
fun Modifier.coverFlightSource(
    token: Any,
    model: Any?,
    corner: Dp = 12.dp,
    footFade: Boolean = false,
): Modifier {
    val cornerPx = with(LocalDensity.current) { corner.toPx() }
    val info = remember(token) { FlightSource() }
    info.model = model
    info.cornerPx = cornerPx
    info.footFade = footFade
    DisposableEffect(token) {
        CoverFlightState.sources[token] = info
        onDispose { CoverFlightState.sources.remove(token) }
    }
    return this
        .drawWithContent { if (!CoverFlightState.hidesSource(token)) drawContent() }
        // Whole, not clipped to what shows: a card half off the row still flies as itself.
        .onGloballyPositioned { info.bounds = it.whole() }
}

/**
 * Marks the page's sleeve as where the flight lands, and where it leaves from
 * again when the page closes. [wash] is the colour a photo page dissolves into
 * at its foot, so the flying picture can take it on as it lands.
 */
@Composable
fun Modifier.coverFlightTarget(
    browseId: String?,
    corner: Dp = 12.dp,
    wash: Color = Color.Unspecified,
): Modifier {
    if (browseId.isNullOrEmpty()) return this
    val cornerPx = with(LocalDensity.current) { corner.toPx() }
    val holder = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val visibility = LocalCoverVisibilityScope.current
    val leaving = visibility?.transition?.targetState == EnterExitState.PostExit
    LaunchedEffect(leaving) {
        if (!leaving) return@LaunchedEffect
        val coordinates = holder[0]?.takeIf { it.isAttached } ?: return@LaunchedEffect
        startReturn(browseId, coordinates.whole(), cornerPx, wash)
    }
    return this
        .drawWithContent { if (!CoverFlightState.hidesTarget(browseId)) drawContent() }
        .onGloballyPositioned { coordinates ->
            holder[0] = coordinates
            val flight = CoverFlightState.flight
            if (flight != null && !flight.reverse && flight.browseId == browseId) {
                CoverFlightState.target = coordinates.whole()
                CoverFlightState.targetCornerPx = cornerPx
                CoverFlightState.targetWash = wash
            }
        }
}

/** Whatever sits on a card around its cover (captions) leaves with it. */
fun Modifier.hideDuringCoverFlight(token: Any): Modifier =
    this.drawWithContent { if (!CoverFlightState.hidesSource(token)) drawContent() }

/** A card's tap that opens a page: starts the flight, then runs [action]. */
@Composable
fun rememberCoverFlightTap(browseId: String?, token: Any, action: () -> Unit): () -> Unit {
    val latest = rememberUpdatedState(action)
    // Lets a flight back find the card again, which is a new composition after the page closes.
    DisposableEffect(token, browseId) {
        if (!browseId.isNullOrEmpty()) CoverFlightState.ids[token] = browseId
        onDispose { CoverFlightState.ids.remove(token) }
    }
    return {
        if (!browseId.isNullOrEmpty()) startFlight(browseId, token)
        latest.value()
    }
}

/** The area the page lives in, with the flying cover drawn over it. */
@Composable
fun CoverFlightHost(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier) {
        content()
        CoverFlightOverlay()
    }
}

@Composable
private fun CoverFlightOverlay() {
    val flight = CoverFlightState.flight
    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned {
                CoverFlightState.origin = it.positionInWindow()
                CoverFlightState.host = it.whole()
            },
    ) {
        if (flight != null) FlightLayer(flight)
    }
}

@Composable
private fun FlightLayer(flight: Flight) {
    val progress = remember(flight) { Animatable(0f) }
    // Where the flight believes it is heading. It trails the real destination a
    // little, so a page that settles or shifts while the cover is in the air
    // bends the path instead of making the picture jump.
    val chase = remember(flight) { mutableStateOf<Rect?>(null) }
    LaunchedEffect(flight) {
        val ready = if (flight.reverse) {
            withTimeoutOrNull(RETURN_TIMEOUT_MS) {
                while (!CoverFlightState.resolveReturn(flight)) withFrameNanos { }
                true
            }
        } else {
            // Wait for the page to say where its sleeve is; if it never does, the
            // card was not opening a page this flight can follow — stand down.
            withTimeoutOrNull(TARGET_TIMEOUT_MS) {
                snapshotFlow { CoverFlightState.target }.filterNotNull().first()
            }
        }
        if (ready != null) {
            var current = CoverFlightState.destination(flight) ?: flight.from
            chase.value = current
            coroutineScope {
                val follower = launch {
                    var last = withFrameNanos { it }
                    while (true) {
                        val now = withFrameNanos { it }
                        val dt = (now - last) / 1_000_000_000f
                        last = now
                        val live = CoverFlightState.destination(flight) ?: continue
                        current = lerpRect(current, live, 1f - exp(-CHASE_RATE * dt))
                        chase.value = current
                    }
                }
                progress.animateTo(1f, tween(durationMillis = FLIGHT_MS, easing = FlightEasing))
                follower.cancel()
            }
        }
        if (CoverFlightState.flight === flight) {
            CoverFlightState.flight = null
            CoverFlightState.target = null
        }
    }
    val live = CoverFlightState.destination(flight) ?: return
    // One picture at the card's own size (so it is the one already in the cache),
    // centre-cropped by the moving frame and scaled to cover it.
    val cardSide = if (flight.reverse) max(live.width, live.height) else max(flight.from.width, flight.from.height)
    val sidePx = cardSide.coerceAtLeast(1f)
    val sideDp = with(LocalDensity.current) { sidePx.toDp() }
    val wash = if (flight.reverse) flight.fromWash else CoverFlightState.targetWash
    // The frame at progress p: from where it started to the (trailing) destination,
    // landing exactly on the real one at the very end.
    val rectAt = { p: Float ->
        val end = CoverFlightState.destination(flight) ?: live
        val chased = chase.value ?: end
        lerpRect(flight.from, lerpRect(chased, end, p * p), p)
    }

    Box(
        Modifier
            .layout { measurable, _ ->
                val rect = rectAt(progress.value)
                val w = rect.width.roundToInt().coerceAtLeast(1)
                val h = rect.height.roundToInt().coerceAtLeast(1)
                val placeable = measurable.measure(Constraints.fixed(w, h))
                val origin = CoverFlightState.origin
                layout(w, h) {
                    placeable.place(
                        (rect.left - origin.x).roundToInt(),
                        (rect.top - origin.y).roundToInt(),
                    )
                }
            }
            .graphicsLayer {
                val toCorner = if (flight.reverse) flight.toCornerPx else CoverFlightState.targetCornerPx
                shape = RoundedCornerShape(lerpF(flight.fromCornerPx, toCorner, progress.value))
                clip = true
            },
    ) {
        AsyncImage(
            model = flight.model,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .requiredSize(sideDp)
                .graphicsLayer {
                    val rect = rectAt(progress.value)
                    val scale = max(rect.width, rect.height) / sidePx
                    scaleX = scale
                    scaleY = scale
                    if (flight.footFade) compositingStrategy = CompositingStrategy.Offscreen
                }
                .drawWithContent {
                    drawContent()
                    if (flight.footFade) {
                        // The card's own cover dissolves into the blur under it;
                        // the picture keeps that foot at the card and loses it on the page.
                        val fade = if (flight.reverse) progress.value else 1f - progress.value
                        drawRect(
                            Brush.verticalGradient(
                                0.78f to Color.Black,
                                1f to Color.Black.copy(alpha = 1f - fade),
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    }
                },
        )
        if (wash != Color.Unspecified) {
            Box(
                Modifier
                    .matchParentSize()
                    .drawBehind {
                        val a = if (flight.reverse) 1f - progress.value else progress.value
                        drawRect(
                            Brush.verticalGradient(
                                0.45f to Color.Transparent,
                                0.65f to wash.copy(alpha = 0.30f),
                                0.82f to wash.copy(alpha = 0.72f),
                                0.94f to wash.copy(alpha = 0.95f),
                                1.00f to wash,
                            ),
                            alpha = a,
                        )
                    },
            )
        }
    }
}
