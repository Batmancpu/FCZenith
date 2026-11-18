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
import androidx.compose.ui.platform.LocalFocusManager
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
    modifier: Modifier = Modifier,
) {
    // Starts at rest and springs open on the frame it arrives.
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    // Opening is softer with a little overshoot (liquid feel); closing is
    // firmer and nearly critically damped so the bar swap is not delayed.
    val openFraction by animateFloatAsState(
        targetValue = if (open && entered) 1f else 0f,
        animationSpec = if (open) {
            spring(dampingRatio = 0.74f, stiffness = 240f)
        } else {
            spring(dampingRatio = 0.92f, stiffness = 380f)
        },
        label = "searchBarOpen",
    )
    val currentOnClosed by rememberUpdatedState(onClosed)
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
            .height(BAR_ROW_HEIGHT),
    ) {
        val fullWidth = maxWidth
        val p = openFraction.coerceAtLeast(0f)
        // The pill's width at rest: whatever the Search circle leaves.
        val restLeft = fullWidth - BAR_CIRCLE - BAR_SPACING
        val leftWidth = maxOf(BAR_FIELD_CIRCLE, lerpDp(restLeft, BAR_FIELD_CIRCLE, p))
        val gap = lerpDp(BAR_SPACING, BAR_FIELD_GAP, p)
        val rightWidth = fullWidth - leftWidth - gap
        val rowHeight = lerpDp(BAR_ROW_HEIGHT, BAR_FIELD_HEIGHT, p.coerceAtMost(1f))
        // The field's width once fully open.
        val fieldWidth = fullWidth - BAR_FIELD_CIRCLE - BAR_FIELD_GAP

        // Left: the tab pill, folding down to the last tab's circle.
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(leftWidth)
                .height(rowHeight)
                .clip(shape)
                .then(Modifier.liquidGlass(shape = shape))
                .clickable(
                    enabled = interactive,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
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
                    .graphicsLayer { alpha = (1f - openFraction * 2.2f).coerceIn(0f, 1f) },
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
                    .graphicsLayer { alpha = ((openFraction - 0.45f) * 2.2f).coerceIn(0f, 1f) },
            )
        }

        // Right: the Search circle, growing leftward into the field.
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(rightWidth)
                .height(rowHeight)
                .clip(shape)
                .then(Modifier.liquidGlass(shape = shape)),
            contentAlignment = Alignment.CenterStart,
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = lerp(selectedColor, tint, p.coerceIn(0f, 1f)),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(TAB_ICON_SIZE)
                    .graphicsLayer { alpha = (1f - openFraction * 1.6f).coerceIn(0f, 1f) },
            )

            // Everything below keeps its full width the whole time, so nothing
            // reflows while the capsule springs; only its reveal changes.
            CompositionLocalProvider(LocalSearchFieldOnSurface provides true) {
                // Back: clears the query and closes the field. The tab stays.
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .size(BAR_FIELD_CONTROL)
                        .graphicsLayer { alpha = contentAlpha }
                        .clickable(
                            enabled = interactive,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            onQueryChange("")
                            focusManager.clearFocus()
                            keyboard?.hide()
                            onClose()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = SfIcons.ArrowBack,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(TAB_ICON_SIZE),
                    )
                }

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