package com.music.zenith.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/** The circle the bar folds down to, and the Search circle the field grows out of. */
private val MORPH_CIRCLE = 48.dp

/** Gap between the last tab's circle and the search field. */
private val MORPH_GAP = 14.dp

/**
 * The bar while the separate Search tab is open on liquid glass.
 *
 * The bar folds down to one circle, the last tab that was not Search, and the
 * Search circle beside it springs open into the input field. Used only where
 * [GlassNavBar] would draw the separate Search circle, in place of that bar and
 * the mini player above it.
 */
@Composable
fun GlassSearchMorph(
    lastTab: BottomTab,
    onLastTabClick: () -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    focusRequester: FocusRequester,
    focusRequested: Boolean,
    onFocusHandled: () -> Unit,
    onFocusChange: (Boolean) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    // Starts as the Search circle alone and springs wide on the frame it
    // arrives, so the tab's own circle is what becomes the input.
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val openFraction by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 320f),
        label = "searchMorphOpen",
    )

    val keyboard = LocalSoftwareKeyboardController.current
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

    val ime = WindowInsets.ime
    val shape = remember { RoundedCornerShape(percent = 50) }
    val contentColor = glassContentColor()
    val glass = Modifier.liquidGlass(shape = shape)

    BoxWithConstraints(
        modifier = modifier
            .windowInsetsPadding(floatingBarInsets)
            .padding(horizontal = NAV_BAR_GUTTER)
            .padding(bottom = NAV_BAR_LIFT)
            .offset { IntOffset(0, -ime.getBottom(this)) },
    ) {
        val fullWidth = maxWidth
        // The field's width once open: whatever the last tab's circle leaves.
        val fieldWidth = fullWidth - MORPH_CIRCLE - MORPH_GAP
        val capsuleWidth = MORPH_CIRCLE + (fieldWidth - MORPH_CIRCLE) * openFraction

        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(MORPH_CIRCLE)
                .clip(shape)
                .then(glass)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onLastTabClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = lastTab.icon,
                contentDescription = lastTab.label,
                tint = contentColor.copy(alpha = 0.88f),
                modifier = Modifier.size(TAB_ICON_SIZE),
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(capsuleWidth)
                .height(MORPH_CIRCLE)
                .clip(shape)
                .then(glass),
            contentAlignment = Alignment.CenterStart,
        ) {
            // The magnifier the Search circle keeps while it is still a circle.
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = contentColor.copy(alpha = 0.88f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(TAB_ICON_SIZE)
                    .graphicsLayer { alpha = 1f - openFraction },
            )

            // The field keeps its full width the whole time, so its text does
            // not reflow while the capsule springs; only its reveal changes.
            CompositionLocalProvider(LocalSearchFieldOnSurface provides true) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .requiredWidth(fieldWidth)
                        .fillMaxHeight()
                        .graphicsLayer { alpha = openFraction },
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
            }
        }
    }
}