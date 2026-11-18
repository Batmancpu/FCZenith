package com.music.zenith.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp as lerpDp
import com.music.zenith.ui.icons.SfIcons
import kotlinx.coroutines.flow.first

/** The bar at rest: the tab pill and the Search circle are both 62dp tall. */
private val BAR_ROW_HEIGHT = 62.dp
private val BAR_CIRCLE = 62.dp
private val BAR_SPACING = 8.dp

/** The bar once the field is open: a 48dp circle for the last tab and the field beside it. */
private val BAR_FIELD_HEIGHT = 48.dp
private val BAR_FIELD_CIRCLE = 48.dp
private val BAR_FIELD_GAP = 14.dp

/** The field's own end controls: the back arrow and the source switch. */
private val BAR_FIELD_CONTROL = 44.dp

/** The collapsed row's side margin (28dp) over the open bar's (19dp). */
private val BAR_INLINE_INSET = 9.dp

/** How far the last-tab circle may squash narrower than 48dp while the open spring overshoots. */
private val BAR_BOUNCE_GIVE = 10.dp

/**
 * A width floor that gives way a little instead of stopping dead: below [floor]
 * the width keeps shrinking, but ever more slowly, up to [give]. An overshooting
 * spring then squashes the circle softly rather than clipping flat.
 */
private fun softFloor(
    width: androidx.compose.ui.unit.Dp,
    floor: androidx.compose.ui.unit.Dp,
    give: androidx.compose.ui.unit.Dp,
): androidx.compose.ui.unit.Dp {
    if (width >= floor) return width
    val over = (floor - width).value
    val g = give.value
    return floor - (g * (1f - kotlin.math.exp(-over / g))).dp
}

/**
 * The mini player's side inset in the collapsed row: gutter + circle + spacing
 * (28 + 48 + 14 = 90dp), less the pill's own 19dp margin.
 */
private val MINI_INLINE_SIDE = 71.dp

/**
 * Moves the mini player from its slot between the two circles of the collapsed
 * row up to its place above the bar, on the same [progress] (0..1) that opens
 * the search field: it widens to full width while it rises. Read at layout and
 * draw time only, so nothing recomposes per frame.
 */
internal fun Modifier.searchMiniRise(progress: () -> Float): Modifier =
    layout { measurable, constraints ->
        val side = MINI_INLINE_SIDE.roundToPx()
        val inset = (side * (1f - progress().coerceIn(0f, 1f))).toInt()
        val width = (constraints.maxWidth - inset * 2).coerceAtLeast(0)
        val placeable = measurable.measure(
            constraints.copy(minWidth = width, maxWidth = width),
        )
        // From the pill's own centre down to the row's centre line.
        val dy = placeable.height / 2f + (BAR_ROW_HEIGHT / 2 + 8.dp).toPx()
        layout(constraints.maxWidth, placeable.height) {
            placeable.placeWithLayer(inset, 0) {
                translationY = (1f - progress().coerceAtLeast(0f)) * dy
            }
        }
    }

/**
 * Folds the mini player away while the keyboard is up: it sinks toward the bar
 * and fades, and its layout height closes with it so the page behind gets the
 * room. [hide] is 0 = shown, 1 = gone; read at layout and draw time only.
 */
internal fun Modifier.searchMiniHide(hide: () -> Float): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val reported = (placeable.height * (1f - hide().coerceIn(0f, 1f))).toInt()
        layout(placeable.width, reported) {
            placeable.placeWithLayer(0, 0) {
                val h = hide().coerceIn(0f, 1f)
                alpha = 1f - h
                translationY = h * placeable.height * 0.6f
                val s = 1f - 0.08f * h
                scaleX = s
                scaleY = s
            }
        }
    }

/**
 * The separate Search tab's bar on liquid glass, with ONE progress driving it.
 *
 * At rest (progress 0) it is shaped like the expanded [GlassNavBar]: a tab pill
 * and the Search circle. Opening, the Search circle shrinks and grows to the
 * left into the input field while the pill is pushed left and shrinks to a
 * single circle holding the last tab that was not Search. Closing runs the same
 * progress back, then calls [onClosed] so the caller can swap [GlassNavBar] back
 * in. [open] is the target; the caller keeps this composed until [onClosed].
 */
@Composable
fun GlassSearchBar(
    tabs: List<BottomTab>,
    lastTab: BottomTab,
    open: Boolean,
    onLastTabClick: () -> Unit,
    onClose: () -> Unit,
    onClosed: () -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    focusRequester: FocusRequester,
    focusRequested: Boolean,
    onFocusHandled: () -> Unit,
    onFocusChange: (Boolean) -> Unit,
    placeholder: String,
    onSourceClick: () -> Unit,
    /** true = the bar opens out of the collapsed row: two 48dp circles with the mini player between. */
    startInline: Boolean = false,
    /** Reports the open fraction each frame so the mini player above can rise on the same spring. */
    onOpenFraction: (Float) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // Starts at rest and springs open on the frame it arrives.
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    // Opening is softer with a little overshoot (liquid feel); closing is
    // firmer and nearly critically damped so the bar swap is not delayed.
    val openFraction by animateFloatAsState(
        targetValue = if (open && entered) 1f else 0f,
        animationSpec = spring(dampingRatio = 1f, stiffness = 340f),
        label = "searchBarOpen",
    )
    val currentOnClosed by rememberUpdatedState(onClosed)
    val inlineStart = remember { startInline }
    SideEffect { onOpenFraction(openFraction) }
    // The resting row is as tall as GlassNavBar's own tab row: glyph + gap + label,
    // padded. That is whatever the label measures at this font scale, not a fixed
    // 62dp, so it is measured the same way here and the mini player above does not
    // shift when the nav bar and this bar swap places.
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = tabLabelStyle()
    val restHeight = remember(labelStyle, density) {
        val labelPx = textMeasurer.measure(text = "Ag", style = labelStyle, maxLines = 1).size.height
        PILL_INSET * 2 + TAB_VERTICAL_PADDING * 2 + TAB_ICON_SIZE + TAB_ICON_LABEL_GAP +
            with(density) { labelPx.toDp() }
    }
    LaunchedEffect(open) {
        if (!open) {
            snapshotFlow { openFraction }.first { it <= 0.01f }
            currentOnClosed()
        }
    }

    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    LaunchedEffect(focusRequested) {
        if (focusRequested) {
            runCatching { focusRequester.requestFocus() }
            keyboard?.show()
            onFocusHandled()
        }
    }
    DisposableEffect(Unit) {
        onDispose { onFocusChange(false) }
    }

    val shape = remember { RoundedCornerShape(percent = 50) }
    val contentColor = glassContentColor()
    val tint = contentColor.copy(alpha = 0.88f)
    val selectedColor = if (contentColor == Color.White) {
        lerp(MaterialTheme.colorScheme.primary, Color.White, 0.06f)
    } else {
        MaterialTheme.colorScheme.primary
    }
    // Controls only answer once the bar has opened, not while it is folding.
    val interactive = open && openFraction > 0.9f
    // The field's contents fade in after the capsule has started to widen,
    // and out before it folds back, so text never squeezes mid-morph.
    val contentAlpha = ((openFraction - 0.3f) / 0.6f).coerceIn(0f, 1f)

    BoxWithConstraints(
        modifier = modifier
            .windowInsetsPadding(floatingBarInsets)
            .padding(horizontal = NAV_BAR_GUTTER)
            .padding(bottom = NAV_BAR_LIFT)
            // Fixed at the resting height: the 48dp pieces centre on the same
            // line, so the mini player above does not move while this folds.
            .height(restHeight),
    ) {
        val p = openFraction.coerceAtLeast(0f)
        // From the collapsed row the sides start 9dp further in and ease out to
        // the open bar's margin as the field widens.
        val inset = if (inlineStart) {
            lerpDp(BAR_INLINE_INSET, 0.dp, p).coerceAtLeast(0.dp)
        } else {
            0.dp
        }
        val fullWidth = maxWidth - inset * 2
        // The field's width once fully open; fixed so nothing reflows while it springs.
        val fieldWidth = maxWidth - BAR_FIELD_CIRCLE - BAR_FIELD_GAP
        // The pill's width at rest: whatever the Search circle leaves.
        val restLeft = fullWidth - restHeight - BAR_SPACING
        val gap = if (inlineStart) BAR_FIELD_GAP else lerpDp(BAR_SPACING, BAR_FIELD_GAP, p)
        // The spring no longer overshoots, so the last-tab circle keeps its width
        // and the mini player's rise follows the same smooth progress.
        val leftWidth = if (inlineStart) {
            BAR_FIELD_CIRCLE
        } else {
            lerpDp(restLeft, BAR_FIELD_CIRCLE, p)
        }
        // Collapsed start: a 48dp circle that grows leftward into the field.
        val rightWidth = if (inlineStart) {
            minOf(
                maxOf(BAR_FIELD_CIRCLE, lerpDp(BAR_FIELD_CIRCLE, fieldWidth, p)),
                fullWidth - leftWidth - gap,
            )
        } else {
            fullWidth - leftWidth - gap
        }
        val rowHeight = if (inlineStart) {
            BAR_FIELD_HEIGHT
        } else {
            lerpDp(restHeight, BAR_FIELD_HEIGHT, p)
        }

        // Left: the tab pill, folding down to the last tab's circle.
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = inset)
                .width(leftWidth)
                .height(rowHeight)
                .clip(shape)
                .then(Modifier.liquidGlass(shape = shape))
                .clickable(
                    enabled = interactive,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    onQueryChange("")
                    focusManager.clearFocus()
                    keyboard?.hide()
                    onLastTabClick()
                },
        ) {
            Row(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .requiredWidth(restLeft)
                    .padding(PILL_INSET)
                    .graphicsLayer {
                        alpha = if (inlineStart) 0f else (1f - openFraction * 2.2f).coerceIn(0f, 1f)
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tabs.forEach { tab ->
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                            tint = tint,
                            modifier = Modifier.size(TAB_ICON_SIZE),
                        )
                        Text(
                            text = tab.label,
                            style = tabLabelStyle(),
                            color = tint,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = TAB_ICON_LABEL_GAP),
                        )
                    }
                }
            }
            Icon(
                imageVector = lastTab.icon,
                contentDescription = lastTab.label,
                tint = tint,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(TAB_ICON_SIZE)
                    .graphicsLayer {
                        alpha = if (inlineStart) 1f else ((openFraction - 0.45f) * 2.2f).coerceIn(0f, 1f)
                    },
            )
        }

        // Right: the Search circle, growing leftward into the field.
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = inset)
                .width(rightWidth)
                .height(rowHeight)
                .clip(shape)
                .then(Modifier.liquidGlass(shape = shape)),
            contentAlignment = Alignment.CenterStart,
        ) {
            // One search glyph for both states: it rests centred in the Search circle
            // and, as the capsule opens, slides left with the capsule's edge into the
            // field's start slot. It is never swapped for a second icon or faded.
            val restRight = if (inlineStart) BAR_FIELD_CIRCLE else BAR_CIRCLE
            val glyphStart = lerpDp(
                (restRight - TAB_ICON_SIZE) / 2,
                (BAR_FIELD_CONTROL - TAB_ICON_SIZE) / 2,
                p,
            ).coerceAtLeast(0.dp)
            Icon(
                imageVector = SfIcons.Search,
                contentDescription = null,
                tint = lerp(selectedColor, tint, p.coerceIn(0f, 1f)),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = glyphStart)
                    .size(TAB_ICON_SIZE)
                    .clickable(
                        enabled = interactive && query.isNotBlank(),
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        onSubmit()
                        focusManager.clearFocus()
                    },
            )

            // Everything below keeps its full width the whole time, so nothing
            // reflows while the capsule springs; only its reveal changes.
            CompositionLocalProvider(LocalSearchFieldOnSurface provides true) {
                // The search glyph is the one drawn above, sliding in from the circle.

                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = BAR_FIELD_CONTROL)
                        .requiredWidth(fieldWidth - BAR_FIELD_CONTROL * 2)
                        .fillMaxHeight()
                        .graphicsLayer { alpha = contentAlpha },
                    contentAlignment = Alignment.CenterStart,
                ) {
                    MaterialTheme(
                        colorScheme = MaterialTheme.colorScheme.copy(
                            onBackground = contentColor,
                            onSurfaceVariant = contentColor.copy(alpha = 0.6f),
                            primary = contentColor,
                        ),
                        typography = MaterialTheme.typography,
                        shapes = MaterialTheme.shapes,
                    ) {
                        SearchField(
                            query = query,
                            onQueryChange = onQueryChange,
                            onSubmit = onSubmit,
                            focusRequester = focusRequester,
                            placeholder = placeholder,
                            onFocusChange = onFocusChange,
                            showSearchIcon = false,
                        )
                    }
                }

                // Source switch: YouTube, Library, Addons in turn.
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(BAR_FIELD_CONTROL)
                        .graphicsLayer { alpha = contentAlpha }
                        .clickable(
                            enabled = interactive,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onSourceClick,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Public,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(TAB_ICON_SIZE),
                    )
                }
            }
        }
    }
}