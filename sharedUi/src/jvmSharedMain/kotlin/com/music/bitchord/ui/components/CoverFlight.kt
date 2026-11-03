package com.music.bitchord.ui.components

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
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
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

private class FlightSource(
    var bounds: Rect = Rect.Zero,
    var model: Any? = null,
    var cornerPx: Float = 0f,
)

private class Flight(
    val browseId: String,
    val token: Any,
    val model: Any?,
    val from: Rect,
    val fromCornerPx: Float,
)

private object CoverFlightState {
    val sources = HashMap<Any, FlightSource>()
    var flight by mutableStateOf<Flight?>(null)
    var target by mutableStateOf<Rect?>(null)
    var targetCornerPx by mutableStateOf(0f)
    var origin by mutableStateOf(Offset.Zero)

    /** The cover is in the air: the card and the sleeve both stand aside for it. */
    val flying: Boolean get() = flight != null && target != null

    fun hidesSource(token: Any): Boolean = flying && flight?.token === token

    fun hidesTarget(browseId: String): Boolean = flying && flight?.browseId == browseId
}

private fun startFlight(browseId: String, token: Any) {
    val source = CoverFlightState.sources[token] ?: return
    if (source.bounds.isEmpty) return
    CoverFlightState.target = null
    CoverFlightState.flight = Flight(browseId, token, source.model, source.bounds, source.cornerPx)
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
 * is loaded from; the card's own size and corner are measured here.
 */
@Composable
fun Modifier.coverFlightSource(token: Any, model: Any?, corner: Dp = 12.dp): Modifier {
    val cornerPx = with(LocalDensity.current) { corner.toPx() }
    val info = remember(token) { FlightSource() }
    info.model = model
    info.cornerPx = cornerPx
    DisposableEffect(token) {
        CoverFlightState.sources[token] = info
        onDispose { CoverFlightState.sources.remove(token) }
    }
    return this
        .drawWithContent { if (!CoverFlightState.hidesSource(token)) drawContent() }
        .onGloballyPositioned { info.bounds = it.boundsInWindow() }
}

/** Marks the page's sleeve as where the flight lands. */
@Composable
fun Modifier.coverFlightTarget(browseId: String?, corner: Dp = 12.dp): Modifier {
    if (browseId.isNullOrEmpty()) return this
    val cornerPx = with(LocalDensity.current) { corner.toPx() }
    return this
        .drawWithContent { if (!CoverFlightState.hidesTarget(browseId)) drawContent() }
        .onGloballyPositioned { coordinates ->
            val flight = CoverFlightState.flight
            if (flight != null && flight.browseId == browseId) {
                CoverFlightState.target = coordinates.boundsInWindow()
                CoverFlightState.targetCornerPx = cornerPx
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
            .onGloballyPositioned { CoverFlightState.origin = it.positionInWindow() },
    ) {
        if (flight != null) FlightLayer(flight)
    }
}

@Composable
private fun FlightLayer(flight: Flight) {
    val progress = remember(flight) { Animatable(0f) }
    LaunchedEffect(flight) {
        // Wait for the page to say where its sleeve is; if it never does, the
        // card was not opening a page this flight can follow — stand down.
        val reported = withTimeoutOrNull(TARGET_TIMEOUT_MS) {
            snapshotFlow { CoverFlightState.target }.filterNotNull().first()
        }
        if (reported != null) {
            progress.animateTo(1f, tween(durationMillis = FLIGHT_MS, easing = FlightEasing))
        }
        if (CoverFlightState.flight === flight) {
            CoverFlightState.flight = null
            CoverFlightState.target = null
        }
    }
    val target = CoverFlightState.target ?: return
    // One picture at the card's own size (so it is the one already in the cache),
    // centre-cropped by the moving frame and scaled to cover it.
    val sidePx = max(flight.from.width, flight.from.height).coerceAtLeast(1f)
    val sideDp = with(LocalDensity.current) { sidePx.toDp() }

    Box(
        Modifier
            .layout { measurable, _ ->
                val to = CoverFlightState.target ?: target
                val rect = lerpRect(flight.from, to, progress.value)
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
                shape = RoundedCornerShape(
                    lerpF(flight.fromCornerPx, CoverFlightState.targetCornerPx, progress.value),
                )
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
                    val to = CoverFlightState.target ?: target
                    val rect = lerpRect(flight.from, to, progress.value)
                    val scale = max(rect.width, rect.height) / sidePx
                    scaleX = scale
                    scaleY = scale
                },
        )
    }
}