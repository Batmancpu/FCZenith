package com.music.bitchord.ui.components

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.ThemeMode
import com.music.bitchord.ui.components.backdrop.backdrops.emptyBackdrop
import com.music.bitchord.ui.components.backdrop.backdrops.layerBackdrop
import com.music.bitchord.ui.components.backdrop.backdrops.rememberLayerBackdrop
import com.music.bitchord.ui.components.backdrop.drawBackdrop
import com.music.bitchord.ui.components.backdrop.effects.blur
import com.music.bitchord.ui.components.backdrop.effects.colorControls
import com.music.bitchord.ui.components.backdrop.effects.lens
import com.music.bitchord.ui.components.backdrop.highlight.Highlight
import com.music.bitchord.ui.components.backdrop.shadow.InnerShadow
import com.music.bitchord.ui.components.backdrop.shadow.Shadow
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import kotlin.math.sin

/**
 * Pemilih tema System / Light / Dark: kapsul kaca dengan satu pill kaca mengambang.
 *
 * State sepenuhnya dikontrol parent lewat [selectedTheme]; komponen ini tidak
 * menyimpan pilihan sendiri. Pill berpindah dengan spring yang sedikit
 * overshoot, melebar mengikuti kecepatan lalu kembali ("liquid"), dan
 * membiaskan track beserta label di bawahnya lewat lens.
 *
 * Mengikuti aturan glass app: tanpa blur/lens bila [isGlassSupported] false
 * atau "Reduce dynamic blur" aktif (fill solid + hairline), snap() bila
 * "Reduce animation" aktif. Track direkam ke backdrop lokal, bukan
 * LocalAppBackdrop, supaya tidak terjadi feedback loop di dalam halaman Settings.
 */
@Composable
fun LiquidThemeSelector(
    selectedTheme: ThemeMode,
    onThemeSelected: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val modes = remember { ThemeMode.entries.toList() }
    val selectedIndex = modes.indexOf(selectedTheme).coerceAtLeast(0)
    val currentSelected by rememberUpdatedState(selectedTheme)
    val currentOnSelected by rememberUpdatedState(onThemeSelected)

    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    val reduceAnimation by AppSettings.reduceAnimation.collectAsStateWithLifecycle()
    val glassOn = isGlassSupported() && !reduceDynamicBlur

    val light = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    val labelColor = glassContentColor()
    val dimColor = labelColor.copy(alpha = 0.55f)
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val labelStyle = MaterialTheme.typography.labelMedium.copy(fontSize = 13.5.sp)

    val shape = remember { RoundedCornerShape(percent = 50) }
    val trackBackdrop = rememberLayerBackdrop()

    // Posisi pill dalam satuan indeks segmen; spring dengan sedikit overshoot.
    val position = remember { Animatable(selectedIndex.toFloat()) }
    LaunchedEffect(selectedIndex, reduceAnimation) {
        val target = selectedIndex.toFloat()
        if (reduceAnimation) {
            position.snapTo(target)
        } else {
            position.animateTo(
                target,
                spring(
                    dampingRatio = 0.62f,
                    stiffness = Spring.StiffnessMediumLow,
                    visibilityThreshold = 0.001f,
                ),
            )
        }
    }

    // State drag: isDragging mengangkat pill (seperti ditekan), dragIndex = segmen
    // di bawah pill selama drag (-1 bila tidak drag) supaya label tajam ikut pill.
    val scope = rememberCoroutineScope()
    var isDragging by remember { mutableStateOf(false) }
    var dragIndex by remember { mutableIntStateOf(-1) }
    LaunchedEffect(selectedIndex) { dragIndex = -1 }

    val sources = remember { List(modes.size) { MutableInteractionSource() } }
    val pressedStates = sources.map { it.collectIsPressedAsState() }
    val anyPressed = pressedStates.any { it.value }
    val pressProgress = animateFloatAsState(
        targetValue = if ((anyPressed || isDragging) && !reduceAnimation) 1f else 0f,
        animationSpec = if (reduceAnimation) snap() else spring(0.7f, 500f),
        label = "themePillPress",
    )
    // 0 diam .. 1 bergerak cepat / ditekan. Dibaca saat draw, bukan saat komposisi.
    val energy = {
        max(pressProgress.value, (abs(position.velocity) / 6f).coerceIn(0f, 1f))
    }

    val trackBrush = Brush.verticalGradient(
        listOf(
            TRACK_FILL.copy(alpha = if (light) 0.20f else 0.34f),
            TRACK_FILL.copy(alpha = if (light) 0.09f else 0.18f),
        ),
    )
    val pillTint = Color.White
    val pillRestAlpha = if (light) 0.72f else 0.18f

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(SELECTOR_HEIGHT),
    ) {
        val slot = (maxWidth - SELECTOR_INSET * 2) / modes.size
        val slotPx = with(density) { slot.toPx() }

        // 1) Track + label redup: inilah yang dilihat dan dibiaskan pill.
        Box(
            Modifier
                .matchParentSize()
                .clip(shape)
                .layerBackdrop(trackBackdrop)
                .background(trackBrush)
                .padding(SELECTOR_INSET),
        ) {
            Row(Modifier.fillMaxWidth().fillMaxHeight()) {
                modes.forEach { mode ->
                    SegmentBox(Modifier.weight(1f)) {
                        SegmentContent(mode, dimColor, labelStyle)
                    }
                }
            }
        }

        // 2) Pill kaca yang bergerak.
        val pillBase = Modifier
            .align(Alignment.CenterStart)
            .padding(start = SELECTOR_INSET)
            .graphicsLayer {
                translationX = (if (isLtr) 1f else -1f) * position.value * slotPx
            }
        val pill = if (glassOn) {
            pillBase.drawBackdrop(
                backdrop = trackBackdrop,
                shape = { shape },
                effects = {
                    val e = energy()
                    val g = max(e, REST_GLASS)
                    colorControls(saturation = 1f + 0.35f * g)
                    blur(1.dp.toPx() * e)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        lens(
                            refractionHeight = 8.dp.toPx() * g,
                            refractionAmount = 14.dp.toPx() * g,
                            depthEffect = true,
                            chromaticAberration = e > 0.5f,
                        )
                    }
                },
                highlight = { Highlight.Ambient.copy(alpha = 0.55f + 0.45f * energy()) },
                shadow = {
                    Shadow(
                        radius = 8.dp,
                        offset = DpOffset(0.dp, 2.dp),
                        color = Color.Black.copy(alpha = if (light) 0.12f else 0.30f),
                    )
                },
                innerShadow = {
                    InnerShadow(
                        radius = 4.dp,
                        offset = DpOffset(0.dp, 1.dp),
                        color = Color.Black.copy(alpha = if (light) 0.10f else 0.25f),
                        alpha = 0.6f,
                    )
                },
                layerBlock = {
                    // Liquid: meregang searah gerak mengikuti kecepatan, lalu kembali via spring.
                    val stretch = (abs(position.velocity) / 8f).coerceIn(0f, 1f)
                    val pop = 1f + 0.05f * pressProgress.value
                    scaleX = pop * (1f + 0.16f * stretch)
                    scaleY = pop * (1f - 0.07f * stretch)
                },
                onDrawSurface = {
                    val e = energy()
                    drawRect(pillTint.copy(alpha = pillRestAlpha * (1f - 0.55f * e)))
                    val w = 1.dp.toPx()
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.45f * e),
                        topLeft = Offset(w / 2f, w / 2f),
                        size = Size(size.width - w, size.height - w),
                        cornerRadius = CornerRadius(size.height / 2f),
                        style = Stroke(w),
                    )
                },
            )
        } else {
            pillBase
                .graphicsLayer {
                    val pop = 1f + 0.05f * pressProgress.value
                    scaleX = pop
                    scaleY = pop
                }
                .background(
                    if (light) Color.White else FALLBACK_DARK_PILL,
                    shape,
                )
        }
        Box(pill.size(slot, SELECTOR_HEIGHT - SELECTOR_INSET * 2))

        // 3) Tepi kaca: rim ambient, bayangan luar, inner shadow tipis, border putih transparan.
        //    Hanya dekorasi (tanpa input); konten yang terbaca selalu digambar paling akhir.
        if (glassOn) {
            Box(
                Modifier
                    .drawBackdrop(
                        backdrop = emptyBackdrop(),
                        shape = { shape },
                        effects = {},
                        highlight = { Highlight.Ambient },
                        shadow = {
                            Shadow(
                                radius = 10.dp,
                                color = Color.Black.copy(alpha = if (light) 0.06f else 0.25f),
                            )
                        },
                        innerShadow = {
                            InnerShadow(
                                radius = 6.dp,
                                offset = DpOffset(0.dp, 2.dp),
                                color = Color.Black.copy(alpha = if (light) 0.14f else 0.35f),
                                alpha = 0.6f,
                            )
                        },
                        onDrawFront = {
                            val w = 0.75f.dp.toPx()
                            drawRoundRect(
                                color = Color.White.copy(alpha = if (light) 0.55f else 0.16f),
                                topLeft = Offset(w / 2f, w / 2f),
                                size = Size(size.width - w, size.height - w),
                                cornerRadius = CornerRadius((size.height - w) / 2f),
                                style = Stroke(w),
                            )
                        },
                    )
                    .matchParentSize(),
            )
        } else {
            Box(
                Modifier
                    .matchParentSize()
                    .border(GLASS_EDGE_WIDTH, GLASS_EDGE_COLOR, shape),
            )
        }

        // 4) Hanya label terpilih yang digambar tajam di atas pill (yang lain sudah ada di track);
        //    memudar sebagian selagi kaca menyala supaya refraksi label di bawah pill terlihat.
        Row(
            Modifier
                .matchParentSize()
                .pointerInput(slotPx, isLtr, reduceAnimation) {
                    val dir = if (isLtr) 1f else -1f
                    val maxIndex = (modes.size - 1).toFloat()
                    val tracker = VelocityTracker()
                    var origin = 0f
                    var travelled = 0f
                    var lastIndex = 0

                    fun settleTo(idx: Int) {
                        scope.launch {
                            if (reduceAnimation) {
                                position.snapTo(idx.toFloat())
                            } else {
                                // Velocity dari drag ikut terbawa, jadi pill
                                // "dilempar" lalu overshoot sebelum diam.
                                position.animateTo(
                                    idx.toFloat(),
                                    spring(
                                        dampingRatio = 0.62f,
                                        stiffness = Spring.StiffnessMediumLow,
                                        visibilityThreshold = 0.001f,
                                    ),
                                )
                            }
                        }
                    }

                    detectHorizontalDragGestures(
                        onDragStart = {
                            tracker.resetTracking()
                            origin = position.value
                            travelled = 0f
                            lastIndex = position.value.roundToInt().coerceIn(0, modes.lastIndex)
                            dragIndex = lastIndex
                            isDragging = true
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            tracker.addPosition(change.uptimeMillis, change.position)
                            travelled += dragAmount * dir / slotPx
                            val target = rubberBand(origin + travelled, maxIndex)
                            val idx = target.roundToInt().coerceIn(0, modes.lastIndex)
                            if (idx != lastIndex) {
                                lastIndex = idx
                                dragIndex = idx
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            scope.launch {
                                if (reduceAnimation) {
                                    position.snapTo(target)
                                } else {
                                    // Spring kaku mengejar jari: pill sedikit tertinggal
                                    // (liquid) dan velocity-nya memicu regangan.
                                    position.animateTo(
                                        target,
                                        spring(
                                            dampingRatio = 0.9f,
                                            stiffness = 1000f,
                                            visibilityThreshold = 0.001f,
                                        ),
                                    )
                                }
                            }
                        },
                        onDragEnd = {
                            val v = tracker.calculateVelocity().x * dir / slotPx
                            val idx = (position.value + v * 0.12f)
                                .roundToInt()
                                .coerceIn(0, modes.lastIndex)
                            isDragging = false
                            if (idx != modes.indexOf(currentSelected)) {
                                dragIndex = idx
                                currentOnSelected(modes[idx])
                            } else {
                                dragIndex = -1
                            }
                            settleTo(idx)
                        },
                        onDragCancel = {
                            isDragging = false
                            dragIndex = -1
                            settleTo(modes.indexOf(currentSelected).coerceAtLeast(0))
                        },
                    )
                }
                .padding(SELECTOR_INSET)
                .graphicsLayer { alpha = 1f - 0.55f * energy() },
        ) {
            modes.forEachIndexed { index, mode ->
                val selected = index == (if (dragIndex >= 0) dragIndex else selectedIndex)
                val color by animateColorAsState(
                    targetValue = if (selected) labelColor else labelColor.copy(alpha = 0f),
                    animationSpec = if (reduceAnimation) snap() else tween(180),
                    label = "themeLabelColor",
                )
                val pressed by pressedStates[index]
                val labelScale = animateFloatAsState(
                    targetValue = if (pressed && !reduceAnimation) 0.94f else 1f,
                    animationSpec = if (reduceAnimation) snap() else spring(0.75f, 700f),
                    label = "themeLabelScale",
                )
                SegmentBox(
                    Modifier
                        .weight(1f)
                        .clip(shape)
                        .selectable(
                            selected = selected,
                            interactionSource = sources[index],
                            indication = null,
                            role = Role.RadioButton,
                            onClick = {
                                if (mode != currentSelected) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    currentOnSelected(mode)
                                }
                            },
                        ),
                ) {
                    Box(
                        Modifier.graphicsLayer {
                            val s = labelScale.value
                            scaleX = s
                            scaleY = s
                        },
                    ) {
                        SegmentContent(mode, color, labelStyle)
                    }
                }
            }
        }

    }
}

@Composable
private fun SegmentBox(modifier: Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxHeight(), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun SegmentContent(
    mode: ThemeMode,
    color: Color,
    style: androidx.compose.ui.text.TextStyle,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ThemeGlyph(mode, color)
        Text(
            text = stringResource(
                when (mode) {
                    ThemeMode.SYSTEM -> R.string.system
                    ThemeMode.LIGHT -> R.string.light
                    ThemeMode.DARK -> R.string.dark
                },
            ),
            style = style,
            color = color,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
        )
    }
}

/** Ikon garis minimalis monitor / sun / moon pada grid 24 unit (~18dp), tanpa dependency ikon. */
@Composable
private fun ThemeGlyph(mode: ThemeMode, color: Color) {
    Canvas(Modifier.size(18.dp)) {
        val u = size.minDimension / 24f
        val sw = 1.8f * u
        val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (mode) {
            ThemeMode.SYSTEM -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(2f * u, 3f * u),
                    size = Size(20f * u, 14f * u),
                    cornerRadius = CornerRadius(2f * u),
                    style = stroke,
                )
                drawLine(color, Offset(12f * u, 17f * u), Offset(12f * u, 21f * u), sw, StrokeCap.Round)
                drawLine(color, Offset(8f * u, 21f * u), Offset(16f * u, 21f * u), sw, StrokeCap.Round)
            }

            ThemeMode.LIGHT -> {
                val c = Offset(12f * u, 12f * u)
                drawCircle(color, radius = 4f * u, center = c, style = stroke)
                for (i in 0 until 8) {
                    val a = i * Math.PI / 4.0
                    val dx = cos(a).toFloat()
                    val dy = sin(a).toFloat()
                    drawLine(
                        color,
                        Offset(c.x + dx * 7.6f * u, c.y + dy * 7.6f * u),
                        Offset(c.x + dx * 9.8f * u, c.y + dy * 9.8f * u),
                        sw,
                        StrokeCap.Round,
                    )
                }
            }

            ThemeMode.DARK -> {
                val outer = Path().apply {
                    addOval(Rect(Offset(4f * u, 4f * u), Size(16f * u, 16f * u)))
                }
                val cutter = Path().apply {
                    addOval(Rect(Offset(9.7f * u, 1.7f * u), Size(13.6f * u, 13.6f * u)))
                }
                drawPath(Path.combine(PathOperation.Difference, outer, cutter), color, style = stroke)
            }
        }
    }
}

/** Tarikan karet di tepi: melewati batas hanya bergeser sedikit (maks ~0.125 segmen). */
private fun rubberBand(x: Float, maxIndex: Float): Float {
    val over = when {
        x < 0f -> -x
        x > maxIndex -> x - maxIndex
        else -> return x
    }
    val eased = over * 0.25f / (1f + 2f * over)
    return if (x < 0f) -eased else maxIndex + eased
}

private val SELECTOR_HEIGHT = 50.dp
private val SELECTOR_INSET = 4.dp
private val TRACK_FILL = Color(0xFF767680)
private val FALLBACK_DARK_PILL = Color(0xFF636366)
private const val REST_GLASS = 0.35f