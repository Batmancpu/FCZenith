package com.music.bitchord.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.components.backdrop.backdrops.layerBackdrop
import com.music.bitchord.ui.components.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Segmented control dengan pill kaca ala iOS 26.
 *
 * Diam, pill putih (gelap di tema gelap) seperti biasa. Saat berpindah segmen,
 * pill terangkat jadi kaca: hampir bening, membelokkan track dan label di
 * bawahnya, lalu kembali pekat begitu mendarat. Label di atas pill memudar
 * selagi kaca menyala, jadi yang terlihat adalah label hasil refraksi.
 *
 * Segmen berbagi lebar sama rata. Kalau label tidak muat, atau kaca tidak
 * didukung / "Reduce dynamic blur" menyala, memakai [PillSegmentedControl].
 */
@Composable
fun GlassSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** Mode Liquid Glass penuh: track kaca bergradasi dan pill yang selalu berupa lensa kaca. */
    liquid: Boolean = false,
) {
    if (options.isEmpty()) return
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    if (!isGlassSupported() || reduceDynamicBlur) {
        PillSegmentedControl(options, selectedIndex, onSelect, modifier, enabled)
        return
    }
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val style = MaterialTheme.typography.labelMedium
    val measurer = rememberTextMeasurer()
    val light = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    val trackColor = GLASS_TRACK_FILL.copy(alpha = if (light) 0.12f else 0.24f)
    // Mode liquid mengikuti toggle Liquid Glass aplikasi; bila mati, tampilan lama dipakai.
    val liquidOn = liquid && LocalLiquidGlassEnabled.current
    val trackShape: Shape = if (liquidOn) LiquidTrackShape else GlassTrackShape
    val trackBrush = Brush.verticalGradient(
        listOf(
            GLASS_TRACK_FILL.copy(alpha = if (light) 0.20f else 0.34f),
            GLASS_TRACK_FILL.copy(alpha = if (light) 0.09f else 0.18f),
        ),
    )
    val trackRim = if (liquidOn) Modifier.liquidGlassRim(LiquidTrackShape) else Modifier
    val pillColor = if (light) Color.White else GLASS_DARK_PILL
    val labelColor = if (light) Color.Black else Color.White
    val reduceAnimation by AppSettings.reduceAnimation.collectAsStateWithLifecycle()
    val selected = selectedIndex.takeIf { it in options.indices }
    val backdrop = rememberLayerBackdrop()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .then(if (liquidOn) Modifier else Modifier.clip(trackShape)),
    ) {
        val inset = with(density) { GLASS_TRACK_PADDING.toPx() }
        val labelPadding = with(density) { GLASS_LABEL_PADDING.toPx() }
        val room = constraints.maxWidth.toFloat() - inset * 2f
        val widest = remember(options, style, measurer, density) {
            options.maxOf { label ->
                measurer.measure(label, style, maxLines = 1, softWrap = false).size.width + labelPadding * 2f
            }
        }
        if (widest * options.size > room) {
            PillSegmentedControl(options, selectedIndex, onSelect, Modifier, enabled)
            return@BoxWithConstraints
        }

        val slot = room / options.size
        val targetStart = if (selected != null) inset + slot * selected else 0f
        val motion: AnimationSpec<Float> =
            if (reduceAnimation) snap() else spring(GLASS_PILL_DAMPING, Spring.StiffnessMediumLow)
        val scope = rememberCoroutineScope()
        val pillAnim = remember { Animatable(targetStart) }
        LaunchedEffect(targetStart) { pillAnim.animateTo(targetStart, motion) }
        var dragging by remember { mutableStateOf(false) }
        // 0 diam, 1 pill sedang dipegang/digeser: pill membesar dan kacanya menyala penuh.
        val lift by animateFloatAsState(if (dragging) 1f else 0f, motion, label = "glassSegmentLift")
        // Kaca menyala penuh selama pill jauh dari tujuan atau sedang dipegang, padam saat mendarat.
        // Dibaca saat draw/layout, jadi satu frame animasi bukan rekomposisi.
        val moving = {
            if (selected == null) {
                0f
            } else {
                maxOf((abs(pillAnim.value - targetStart) / (slot * 0.5f)).coerceIn(0f, 1f), lift)
            }
        }
        // Liquid: pill tetap kaca saat diam (lensa menyala), makin bening saat berpindah.
        val glass = { if (liquidOn) maxOf(moving(), LIQUID_REST_GLASS) else moving() }

        val maxStart = inset + slot * (options.size - 1)
        val dragPos = remember { floatArrayOf(0f) }
        val dragModifier = if (liquidOn && enabled && selected != null) {
            Modifier.pointerInput(options.size, slot, inset, selected, targetStart) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        dragging = true
                        dragPos[0] = pillAnim.value
                    },
                    onDragCancel = {
                        dragging = false
                        scope.launch { pillAnim.animateTo(targetStart, motion) }
                    },
                    onDragEnd = {
                        dragging = false
                        val index = ((dragPos[0] - inset) / slot).roundToInt().coerceIn(0, options.lastIndex)
                        if (index != selected) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(index)
                        }
                        scope.launch { pillAnim.animateTo(inset + slot * index, motion) }
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        dragPos[0] = (dragPos[0] + dragAmount).coerceIn(inset, maxStart)
                        scope.launch { pillAnim.snapTo(dragPos[0]) }
                    },
                )
            }
        } else {
            Modifier
        }

        Box(Modifier.fillMaxWidth().then(dragModifier)) {
            // Yang dilihat kaca: track beserta label-labelnya.
            Box(
                Modifier
                    .clip(trackShape)
                    .layerBackdrop(backdrop)
                    .fillMaxWidth()
                    .then(if (liquidOn) Modifier.background(trackBrush) else Modifier.background(trackColor))
                    .padding(GLASS_TRACK_PADDING),
            ) {
                GlassLabels(options, style, labelColor, Modifier.fillMaxWidth())
            }

            if (selected != null) {
                Box(Modifier.matchParentSize().padding(vertical = GLASS_TRACK_PADDING)) {
                    LiquidGlassThumb(
                        backdrop = backdrop,
                        glass = glass,
                        restColor = pillColor,
                        modifier = Modifier
                            .matchParentSize()
                            .layout { measurable, constraints ->
                                val placeable = measurable.measure(
                                    Constraints.fixed(slot.roundToInt(), constraints.maxHeight),
                                )
                                layout(constraints.maxWidth, constraints.maxHeight) {
                                    placeable.place(pillAnim.value.roundToInt(), 0)
                                }
                            }
                            .graphicsLayer {
                                // Pill membesar saat dipegang, meluber sedikit keluar track seperti iOS 26.
                                val s = if (liquidOn) lift else 0f
                                scaleX = 1f + 0.10f * s
                                scaleY = 1f + 0.16f * s
                            },
                    )
                }
            }

            // Label di atas pill; memudar selagi kaca menyala supaya tidak dobel.
            GlassLabels(
                options = options,
                style = style,
                color = labelColor,
                modifier = Modifier
                    .matchParentSize()
                    .padding(GLASS_TRACK_PADDING)
                    .graphicsLayer { alpha = 1f - if (liquidOn) 0.5f * moving() else moving() },
                onClick = if (enabled) {
                    { index ->
                        if (index != selected) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(index)
                        }
                    }
                } else {
                    null
                },
            )

            // Rim highlight kaca di tepi track, di atas semua lapisan dan tidak menangkap sentuhan.
            if (liquidOn) {
                Box(Modifier.matchParentSize().then(trackRim))
            }
        }
    }
}

@Composable
private fun GlassLabels(
    options: List<String>,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: ((Int) -> Unit)? = null,
) {
    Row(modifier) {
        options.forEachIndexed { index, label ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (onClick != null) {
                            Modifier.clickable(interactionSource = null, indication = null) { onClick(index) }
                        } else {
                            Modifier
                        },
                    )
                    .padding(vertical = GLASS_LABEL_VERTICAL_PADDING),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = style,
                    color = color,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

private val GlassTrackShape = SquirclePillShape()
private val LiquidTrackShape = RoundedCornerShape(percent = 50)
private val GLASS_TRACK_FILL = Color(0xFF767680)
private val GLASS_DARK_PILL = Color(0xFF636366)
private val GLASS_TRACK_PADDING = 4.dp
private val GLASS_LABEL_PADDING = 18.dp
private val GLASS_LABEL_VERTICAL_PADDING = 9.dp
private const val GLASS_PILL_DAMPING = 0.8f
private const val LIQUID_REST_GLASS = 0.8f