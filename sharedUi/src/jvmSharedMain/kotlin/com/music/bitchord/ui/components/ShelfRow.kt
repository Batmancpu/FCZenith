package com.music.bitchord.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * What a sideways shelf is wrapped in, on top of the row itself.
 *
 * Nothing on the phone, where a finger drags the row. A window's mouse can't
 * swipe sideways, so the desktop wraps each row in the arrows it pages with,
 * the drag that pulls it, and the horizontal wheel.
 */
interface ShelfRowChrome {
    @Composable
    fun Wrap(state: LazyListState, row: @Composable () -> Unit)
}

private object NoShelfRowChrome : ShelfRowChrome {
    @Composable
    override fun Wrap(state: LazyListState, row: @Composable () -> Unit) = row()
}

val LocalShelfRowChrome = staticCompositionLocalOf<ShelfRowChrome> { NoShelfRowChrome }

/**
 * A shelf card's entrance: slides in from the right and fades up, each card a
 * step after the one before it, so a row swiped left arrives in sequence.
 */
@Composable
fun Modifier.shelfStagger(index: Int): Modifier {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay((index % 3) * 70L)
        progress.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
    }
    return this.graphicsLayer {
        val p = progress.value
        alpha = p
        translationX = (1f - p) * 56.dp.toPx()
    }
}

/** A sideways-scrolling shelf, in whatever the platform wraps one in — see [ShelfRowChrome].
 *
 * Snaps to each cell rather than stopping mid-card: a fling always settles with
 * a column aligned to the leading edge instead of leaving items cut in half.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShelfRow(
    contentPadding: PaddingValues,
    horizontalArrangement: Arrangement.Horizontal,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit,
) {
    LocalShelfRowChrome.current.Wrap(state) {
        LazyRow(
            modifier = modifier,
            state = state,
            contentPadding = contentPadding,
            horizontalArrangement = horizontalArrangement,
            flingBehavior = rememberSnapFlingBehavior(lazyListState = state),
            content = content,
        )
    }
}
