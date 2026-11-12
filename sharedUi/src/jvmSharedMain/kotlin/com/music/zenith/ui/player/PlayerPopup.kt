package com.music.zenith.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.zenith.ui.components.MENU_FROST_ALPHA
import com.music.zenith.ui.components.MenuFrostDark
import com.music.zenith.ui.components.liveMenuFrost
import com.music.zenith.ui.utils.containSheetGestures
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay

internal val POPUP_SHAPE = RoundedCornerShape(30.dp)
internal val POPUP_MAX_WIDTH = 420.dp

/** How long the popup takes to leave, and so how long [PlayerPopup] waits before telling its caller. */
private const val POPUP_EXIT_MS = 200

/** Where the jelly starts: narrower and flatter than it ends up, so it swells into place. */
private const val POPUP_ENTER_SCALE_X = 0.88f
private const val POPUP_ENTER_SCALE_Y = 0.82f

/** Where it shrinks to on the way out. */
private const val POPUP_EXIT_SCALE_X = 0.93f
private const val POPUP_EXIT_SCALE_Y = 0.88f

/**
 * A floating card over a dimmed player, the popup counterpart of [PlayerDrawer].
 *
 * It opens like a jelly: width and height spring on their own, loosely, so the
 * card overshoots, squashes and settles rather than sliding in. Both ways it
 * also moves like a macOS popover: it grows out of, and shrinks back toward, the
 * low edge where the controls are, fading as it goes. Tapping outside
 * or pressing back closes it; the exit is played before [onDismiss] is called,
 * because the caller removes the popup from the tree the moment it runs.
 */
@Composable
internal fun PlayerPopup(
    hazeState: HazeState,
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    /** Air between the title and whatever the popup opens with. */
    titleGap: Dp = 14.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val reduceDynamicBlur by PlayerSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    val frost = LocalPlayerFrost.current

    // Flipped on the first composition so the card swells in instead of
    // appearing over the player fully formed.
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }

    var closing by remember { mutableStateOf(false) }
    LaunchedEffect(closing) {
        if (closing) {
            delay(POPUP_EXIT_MS.toLong())
            onDismiss()
        }
    }
    val close: () -> Unit = { closing = true }
    PlayerBackHandler(enabled = !closing) { close() }

    val popScaleX by animateFloatAsState(
        targetValue = when {
            closing -> POPUP_EXIT_SCALE_X
            shown -> 1f
            else -> POPUP_ENTER_SCALE_X
        },
        animationSpec = if (closing) {
            spring(dampingRatio = 0.55f, stiffness = 800f)
        } else {
            spring(dampingRatio = 0.46f, stiffness = 420f)
        },
        label = "playerPopupScaleX",
    )
    // A different spring from the width's, so the two axes never agree and the
    // card wobbles instead of just scaling.
    val popScaleY by animateFloatAsState(
        targetValue = when {
            closing -> POPUP_EXIT_SCALE_Y
            shown -> 1f
            else -> POPUP_ENTER_SCALE_Y
        },
        animationSpec = if (closing) {
            spring(dampingRatio = 0.50f, stiffness = 650f)
        } else {
            spring(dampingRatio = 0.40f, stiffness = 300f)
        },
        label = "playerPopupScaleY",
    )
    val popAlpha by animateFloatAsState(
        targetValue = if (shown && !closing) 1f else 0f,
        animationSpec = tween(if (closing) 120 else 140),
        label = "playerPopupAlpha",
    )
    val scrimAlpha by animateFloatAsState(
        targetValue = if (shown && !closing) 1f else 0f,
        animationSpec = tween(200),
        label = "playerPopupScrim",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .containSheetGestures()
            .background(SCRIM_COLOR.copy(alpha = SCRIM_COLOR.alpha * scrimAlpha))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = close,
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = POPUP_MAX_WIDTH)
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .heightIn(max = 560.dp)
                .graphicsLayer {
                    scaleX = popScaleX
                    scaleY = popScaleY
                    alpha = popAlpha
                    transformOrigin = TransformOrigin(0.5f, 0.9f)
                }
                .clip(POPUP_SHAPE)
                .then(
                    // The same frost the drawers use, so the popup and the ⋯ menu
                    // read as one material over the player.
                    when {
                        reduceDynamicBlur -> Modifier.background(MenuFrostDark)
                        frost != null -> with(frost) {
                            Modifier.frostPanel(POPUP_SHAPE, MenuFrostDark.copy(alpha = MENU_FROST_ALPHA))
                        }
                        else -> Modifier.liveMenuFrost(hazeState, MenuFrostDark)
                    },
                )
                .border(0.5.dp, Color.White.copy(alpha = 0.10f), POPUP_SHAPE)
                // Taps on the card are the card's, not the scrim's.
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = {},
                )
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = Color.White,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, bottom = titleGap),
            )
            content()
        }
    }
}