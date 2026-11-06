package com.motion.lyrics

import kotlin.math.exp

internal object MotionTuning {
    const val RiseCompleteAt = 0.85f
    const val LetterRiseShare = 0.55f
    const val LetterRiseLeadChars = 0.5f
    const val LetterRiseSpanChars = 2.5f
    const val LetterSwellLeadChars = 0.35f
    const val LetterSwellSpanChars = 1.4f
    const val SwellAttackFraction = 0.2f
    const val SwellReleaseFraction = 0.12f
    const val RiseFollowSec = 0.12f
    const val SwellFollowSec = 0.10f
    const val BloomFollowSec = 0.18f
    const val RiseDistanceEm = 0.14f
    const val SwellScale = 0.06f
    const val BloomStrength = 1.6f
    const val BloomRadiusEm = 0.45f
    const val BloomLagChars = 1.2f
    const val BloomSpanChars = 7f
    const val CharWidthEm = 0.55f
    const val NearGapMs = 80L
    const val SpaceShare = 0.15f
    const val MaxSpaceShareMs = 150f
    const val MaxTravelMs = 450L
    const val HoldMinMs = 600f
    const val HoldRangeMs = 1000f
    const val BleedXDp = 14
    const val BleedYDp = 20
    const val NoLigatures = "liga 0, clig 0"
}

internal fun smoothstep(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

/** Critically damped follow (solusi eksak, stabil untuk dt apa pun). */
internal fun dampedStep(
    values: FloatArray,
    vels: FloatArray,
    i: Int,
    target: Float,
    followSec: Float,
    dt: Float
) {
    val w = 4f / followSec.coerceAtLeast(0.02f)
    val x0 = values[i] - target
    val v0 = vels[i]
    val e = exp(-w * dt)
    val k = v0 + w * x0
    values[i] = target + (x0 + k * dt) * e
    vels[i] = (v0 - w * k * dt) * e
}