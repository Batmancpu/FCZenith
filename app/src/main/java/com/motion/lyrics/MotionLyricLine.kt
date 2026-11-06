package com.motion.lyrics

import android.os.Build
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

internal class MotionRuntime {
    var layout: MotionLayout? = null
    var stepLayout: MotionLayout? = null
    var measuredW = -1
    var fontPx = 0f
    var rise = FloatArray(0); var riseVel = FloatArray(0)
    var swell = FloatArray(0); var swellVel = FloatArray(0)
    val bloomA = FloatArray(1); val bloomV = FloatArray(1)
    var head = 0f
    var maxSwell = 0f
    var timeSec = 0f
    var headX = 0f; var headY = 0f; var headRtl = false
    var lastPos = Long.MIN_VALUE
    var idle = -1
    var tick by mutableLongStateOf(0L)
    private val out = HeadOut()

    val bloom: Float get() = MotionTuning.BloomStrength * bloomA[0] * (1f + 0.5f * maxSwell)

    fun step(lay: MotionLayout, words: List<MotionWord>, pos: Long, dt: Float, snapIn: Boolean) {
        var snap = snapIn
        val n = lay.count
        if (rise.size != n) {
            rise = FloatArray(n); riseVel = FloatArray(n)
            swell = FloatArray(n); swellVel = FloatArray(n)
            snap = true
        }
        computeHead(words, pos, out)
        val h = out.head
        head = h
        val step = dt.coerceIn(0.001f, 0.05f)
        timeSec += step
        var maxS = 0f
        for (l in 0 until n) {
            val wi = lay.wordIndex[l]
            var riseTarget = 1f
            var swellTarget = 0f
            if (wi >= 0) {
                val w = words[wi]
                val c = lay.charIndex[l].toFloat()
                val through = (h - w.charStart) / (w.charEnd - w.charStart).coerceAtLeast(1).toFloat()
                val wordRise = smoothstep(through / MotionTuning.RiseCompleteAt)
                val letterRise = if (h >= w.charEnd) 1f else
                    smoothstep((h - c + MotionTuning.LetterRiseLeadChars) / MotionTuning.LetterRiseSpanChars)
                riseTarget = wordRise + (letterRise - wordRise) * MotionTuning.LetterRiseShare
                val hold = smoothstep(((w.endMs - w.startMs) - MotionTuning.HoldMinMs) / MotionTuning.HoldRangeMs)
                val tail = if (pos > w.endMs) (pos - w.endMs) / 1000f * MotionTuning.SwellTailCharsPerSec else 0f
                val sw = (h - c + MotionTuning.LetterSwellLeadChars + tail) / MotionTuning.LetterSwellSpanChars
                swellTarget = hold *
                    smoothstep(sw / MotionTuning.SwellAttackFraction) *
                    smoothstep((1f - sw) / MotionTuning.SwellReleaseFraction)
            }
            if (snap) {
                rise[l] = riseTarget; riseVel[l] = 0f
                swell[l] = swellTarget; swellVel[l] = 0f
            } else {
                dampedStep(rise, riseVel, l, riseTarget, MotionTuning.RiseFollowSec, step)
                dampedStep(swell, swellVel, l, swellTarget, MotionTuning.SwellFollowSec, step)
            }
            if (swell[l] > maxS) maxS = swell[l]
        }
        maxSwell = maxS
        if (snap) { bloomA[0] = out.sung; bloomV[0] = 0f }
        else dampedStep(bloomA, bloomV, 0, out.sung, MotionTuning.BloomFollowSec, step)

        // Posisi head dari geometri glyph nyata (kontinu melewati spasi).
        val res = lay.result
        val len = res.layoutInput.text.text.length
        if (len > 0) {
            val ci = floor(h).toInt().coerceIn(0, len - 1)
            val frac = (h - ci).coerceIn(0f, 1f)
            val box = res.getBoundingBox(ci)
            headRtl = res.getBidiRunDirection(ci) == ResolvedTextDirection.Rtl
            headX = if (!headRtl) box.left + frac * box.width else box.right - frac * box.width
            headY = box.center.y
        }
    }
}

@Composable
fun MotionLyricLine(
    text: String,
    words: List<MotionWord>,
    positionProvider: () -> Long,
    style: TextStyle,
    inactiveColor: Color,
    activeColor: Color,
    modifier: Modifier = Modifier
) {
    if (words.isEmpty() || text.isEmpty()) {
        BasicText(text = text, modifier = modifier, style = style.copy(color = inactiveColor))
        return
    }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val motionStyle = remember(style) {
        style.copy(fontFeatureSettings = MotionTuning.NoLigatures, color = Color.Unspecified)
    }
    val rt = remember(text, words, motionStyle) { MotionRuntime() }
    // Bloom kini digambar langsung per huruf (tanpa RenderEffect) agar tidak ada ghosting.

    LaunchedEffect(rt, words) {
        var last = -1L
        val firstMs = words.first().startMs
        val lastMs = words.last().endMs
        while (true) {
            val lay = rt.layout
            if (lay == null) { delay(50); continue }
            val pos = positionProvider()
            val inRange = pos >= firstMs - 600 && pos <= lastMs + 1500
            if (!inRange) {
                val s = if (pos < firstMs) 1 else 2
                if (rt.idle != s || rt.stepLayout !== lay) {
                    rt.step(lay, words, pos, 0f, true)
                    rt.idle = s; rt.stepLayout = lay; rt.tick++
                }
                last = -1L; rt.lastPos = Long.MIN_VALUE
                delay(100)
                continue
            }
            rt.idle = 0
            withFrameNanos { now ->
                val p = positionProvider()
                val dt = if (last < 0) 0.016f else (now - last) / 1_000_000_000f
                last = now
                val jump = rt.lastPos != Long.MIN_VALUE && abs(p - rt.lastPos) > 1200
                rt.step(lay, words, p, dt, jump || rt.stepLayout !== lay)
                rt.stepLayout = lay
                rt.lastPos = p
                rt.tick++
            }
        }
    }

    val bleedX = with(density) { MotionTuning.BleedXDp.dp.roundToPx() }
    val bleedY = with(density) { MotionTuning.BleedYDp.dp.roundToPx() }

    Spacer(
        modifier
            .layout { measurable, c ->
                val maxW = if (c.hasBoundedWidth) c.maxWidth else Constraints.Infinity
                val res = measurer.measure(
                    AnnotatedString(text), style = motionStyle,
                    constraints = Constraints(maxWidth = maxW)
                )
                if (rt.layout == null || rt.measuredW != maxW) {
                    rt.measuredW = maxW
                    rt.fontPx = if (motionStyle.fontSize.isSpecified) motionStyle.fontSize.toPx() else 18.sp.toPx()
                    rt.layout = buildMotionLayout(res, measurer, motionStyle, words)
                }
                val w = res.size.width
                val h = res.size.height
                val p = measurable.measure(Constraints.fixed(w + 2 * bleedX, h + 2 * bleedY))
                layout(w, h) { p.place(-bleedX, -bleedY) }
            }
            .graphicsLayer { clip = false }
            .drawBehind {
                val lay = rt.layout ?: return@drawBehind
                if (rt.tick < 0) return@drawBehind
                translate(bleedX.toFloat(), bleedY.toFloat()) {
                    drawMotion(lay, rt, inactiveColor, activeColor)
                }
            }
    )
}

private fun hash01(n: Int): Float {
    var x = n * 374761393 + 668265263
    x = (x xor (x ushr 13)) * 1274126177
    x = x xor (x ushr 16)
    return (x and 0xFFFF) / 65535f
}

private fun DrawScope.drawParticles(
    i: Int,
    cx: Float,
    top: Float,
    w: Float,
    rt: MotionRuntime,
    color: Color,
    sw: Float
) {
    val fp = rt.fontPx
    for (k in 0 until MotionTuning.ParticleCount) {
        val seed = i * 31 + k * 17
        val phase = (rt.timeSec * MotionTuning.ParticleSpeed + hash01(seed)) % 1f
        val px = cx + (hash01(seed + 7) - 0.5f) * (w + fp * 0.5f)
        val py = top + fp * 0.2f - phase * fp * MotionTuning.ParticleRiseEm
        val a = sin(PI * phase).toFloat() * sw
        val rad = fp * 0.028f * (0.6f + hash01(seed + 13))
        drawCircle(color, rad * 2.6f, Offset(px, py), alpha = (a * 0.12f).coerceIn(0f, 1f))
        drawCircle(color, rad, Offset(px, py), alpha = (a * 0.75f).coerceIn(0f, 1f))
    }
}

private fun DrawScope.drawMotion(
    lay: MotionLayout,
    rt: MotionRuntime,
    inactive: Color,
    active: Color
) {
    val riseDist = rt.fontPx * MotionTuning.RiseDistanceEm
    val glowBase = rt.fontPx * MotionTuning.GlowRadiusEm
    for (i in 0 until lay.count) {
        if (i >= rt.rise.size) break
        val l = lay.left[i]; val r = lay.right[i]
        val w = r - l
        val sw = rt.swell[i]
        val dy = (1f - rt.rise[i]) * riseDist
        val s = 1f + MotionTuning.SwellScale * sw
        val pivot = Offset(l + w / 2f, (lay.top[i] + lay.bottom[i]) / 2f)
        val g = lay.glyphs[i]
        val tl = Offset(l, lay.glyphY[i])
        val dist = rt.head - lay.charIndex[i]
        val prox = 1f - smoothstep(abs(dist - 1f) / MotionTuning.GlowSpanChars)
        val glow = (rt.bloomA[0] * prox * (0.4f + 0.6f * sw)).coerceIn(0f, 1f)
        val shadow = if (glow > 0.03f) {
            Shadow(active.copy(alpha = 0.85f * glow), Offset.Zero, glowBase * (0.6f + 0.8f * sw))
        } else null
        withTransform({
            translate(0f, dy)
            scale(s, s, pivot)
        }) {
            drawText(g, color = inactive, topLeft = tl)
            val frac = dist.coerceIn(0f, 1f)
            if (frac >= 1f) {
                drawText(g, color = active, topLeft = tl, shadow = shadow)
            } else if (frac > 0f) {
                val cl = if (lay.rtl[i]) r - frac * w else l
                val cr = if (lay.rtl[i]) r else l + frac * w
                clipRect(cl, lay.top[i] - 60f, cr, lay.bottom[i] + 60f) {
                    drawText(g, color = active, topLeft = tl, shadow = shadow)
                }
            }
        }
        if (sw > 0.12f) drawParticles(i, l + w / 2f, lay.top[i] + dy, w, rt, active, sw)
    }
}

/** Transisi baris aktif: pakai pada item lirik yang sudah ada (tanpa mengubah auto-scroll). */
@Composable
fun Modifier.motionLineTransition(distanceFromActive: Int): Modifier {
    val d = abs(distanceFromActive)
    val soft = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow)
    val alpha by animateFloatAsState(
        when (d) { 0 -> 1f; 1 -> 0.55f; 2 -> 0.38f; else -> 0.28f }, soft, label = "mAlpha"
    )
    val scale by animateFloatAsState(if (d == 0) 1f else 0.965f, soft, label = "mScale")
    val ty by animateFloatAsState(
        when { distanceFromActive < 0 -> -6f; distanceFromActive > 0 -> 6f; else -> 0f }, soft, label = "mTy"
    )
    val blur by animateFloatAsState(min(d, 3) * 1.1f, soft, label = "mBlur")
    val dens = LocalDensity.current.density
    return this.graphicsLayer {
        this.alpha = alpha
        scaleX = scale; scaleY = scale
        transformOrigin = TransformOrigin(0f, 0.5f)
        translationY = ty * dens
        if (Build.VERSION.SDK_INT >= 31 && blur > 0.1f) {
            renderEffect = BlurEffect(blur * dens, blur * dens, TileMode.Decal)
        }
    }
}