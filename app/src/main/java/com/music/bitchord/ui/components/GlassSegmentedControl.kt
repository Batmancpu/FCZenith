package com.music.bitchord.ui.components

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
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
    val pillColor = if (light) Color.White else GLASS_DARK_PILL
    val labelColor = if (light) Color.Black else Color.White
    val reduceAnimation by AppSettings.reduceAnimation.collectAsStateWithLifecycle()
    val selected = selectedIndex.takeIf { it in options.indices }
    val backdrop = rememberLayerBackdrop()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clip(GlassTrackShape),
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
        val pillStart by animateFloatAsState(targetStart, motion, label = "glassSegmentStart")
        // Kaca menyala penuh selama pill masih jauh dari tujuan, padam saat mendarat.
        // Dibaca saat draw/layout, jadi satu frame animasi bukan rekomposisi.
        val glass = {
            if (selected == null) 0f else (abs(pillStart - targetStart) / (slot * 0.5f)).coerceIn(0f, 1f)
        }

        Box(Modifier.fillMaxWidth()) {
            // Yang dilihat kaca: track beserta label-labelnya.
            Box(
                Modifier
                    .layerBackdrop(backdrop)
                    .fillMaxWidth()
                    .background(trackColor)
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
                                    placeable.place(pillStart.roundToInt(), 0)
                                }
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
                    .graphicsLayer { alpha = 1f - glass() },
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
private val GLASS_TRACK_FILL = Color(0xFF767680)
private val GLASS_DARK_PILL = Color(0xFF636366)
private val GLASS_TRACK_PADDING = 4.dp
private val GLASS_LABEL_PADDING = 18.dp
private val GLASS_LABEL_VERTICAL_PADDING = 9.dp
private const val GLASS_PILL_DAMPING = 0.8f