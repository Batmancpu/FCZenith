package com.music.zenith.playback

import kotlin.math.ln
import kotlin.math.sqrt

/** Global policy: Auto learns from explicit track choices; On and Off are deterministic. */
enum class SpatialAudioMode { AUTO, ON, OFF }

/** Per-track policy; Auto inherits the global policy or its learned decision. */
enum class SpatialAudioPreference { AUTO, FORCE_ON, FORCE_OFF }

/** Only deliberate per-track On/Off choices are learning labels. */
enum class SpatialAudioFeedback { NONE, ON, OFF }

/**
 * A compact fingerprint of a short stereo PCM sample. No audio is retained:
 * only these scalar features are persisted locally.
 *
 * [sampleRateHz] is the effective analysis rate. New profiles are sampled at
 * one frame in eight; old full-rate profiles remain valid and readable.
 */
data class SpatialAudioProfile(
    val sampleRateHz: Int,
    val frameCount: Long,
    val stereoWidth: Float,
    val channelCorrelation: Float,
    val lowFrequencySideRatio: Float,
    val highFrequencyRatio: Float,
    val crestFactor: Float,
    val rms: Float,
) {
    val isUsable: Boolean
        get() = sampleRateHz in 1_000..192_000 &&
            frameCount >= sampleRateHz.toLong() * MIN_PROFILE_SECONDS &&
            stereoWidth.isFinite() && stereoWidth in 0f..20f &&
            channelCorrelation.isFinite() && channelCorrelation in -1f..1f &&
            lowFrequencySideRatio.isFinite() && lowFrequencySideRatio in 0f..20f &&
            highFrequencyRatio.isFinite() && highFrequencyRatio in 0f..1f &&
            crestFactor.isFinite() && crestFactor in 0f..100f &&
            rms.isFinite() && rms >= MIN_RMS

    companion object {
        const val MIN_PROFILE_SECONDS = 4L
        const val MIN_RMS = 0.001f
    }
}

/** Persisted local record. Feedback is retained when preference returns to Auto. */
data class SpatialAudioTrackRecord(
    val trackId: String,
    val title: String,
    val artist: String,
    val preference: SpatialAudioPreference = SpatialAudioPreference.AUTO,
    val feedback: SpatialAudioFeedback = SpatialAudioFeedback.NONE,
    val profile: SpatialAudioProfile? = null,
    val updatedAtMs: Long = 0L,
)

/**
 * Distances are calculated in this pre-transformed space. The training set is
 * converted once when records change, not every time a track changes.
 */
data class SpatialAudioFeatures(
    val logStereoWidth: Double,
    val channelCorrelation: Double,
    val logLowFrequencySide: Double,
    val highFrequencyRatio: Double,
    val crestFactor: Double,
) {
    companion object {
        fun from(profile: SpatialAudioProfile): SpatialAudioFeatures = SpatialAudioFeatures(
            logStereoWidth = ln(1.0 + profile.stereoWidth),
            channelCorrelation = profile.channelCorrelation.toDouble(),
            logLowFrequencySide = ln(1.0 + profile.lowFrequencySideRatio),
            highFrequencyRatio = profile.highFrequencyRatio.toDouble(),
            crestFactor = profile.crestFactor.toDouble(),
        )
    }
}

data class SpatialAudioExample(
    val trackId: String,
    val artist: String,
    val feedback: SpatialAudioFeedback,
    val profile: SpatialAudioProfile,
) {
    /** Rebuilt by the constructor, including data-class copy(profile = ...). */
    val features: SpatialAudioFeatures = SpatialAudioFeatures.from(profile)
}

enum class SpatialAudioDecisionReason {
    GLOBAL_OFF,
    GLOBAL_ON,
    TRACK_ON,
    TRACK_OFF,
    PHONE_SPEAKER_BLOCKED,
    DOLBY_ATMOS_BYPASS,
    DSP_UNAVAILABLE,
    AUTO_DISABLED,
    AUTO_NO_PROFILE,
    AUTO_INSUFFICIENT_EVIDENCE,
    AUTO_LOW_INDEPENDENT_EVIDENCE,
    AUTO_UNCERTAIN,
    AUTO_LEARNED_ON,
    AUTO_LEARNED_OFF,
}

data class SpatialAudioDecision(
    val enabled: Boolean,
    val reason: SpatialAudioDecisionReason,
    val confidence: Float? = null,
    val similarTrackCount: Int = 0,
    val onVotes: Int = 0,
    val offVotes: Int = 0,
    val similarTrackIds: List<String> = emptyList(),
    val effectiveSampleCount: Float = 0f,
)

object SpatialAudioLearner {
    private const val MIN_NEIGHBOURS = 4
    private const val MAX_NEIGHBOURS = 9
    private const val MAX_DISTANCE = 2.5
    private const val CONFIDENCE_THRESHOLD = 0.82
    private const val MIN_EFFECTIVE_NEIGHBOURS = 3.0
    private const val WEIGHT_FLOOR = 0.18
    private const val SAME_ARTIST_BONUS = 1.08

    private data class Neighbour(
        val example: SpatialAudioExample,
        val distanceSquared: Double,
        val artistBoost: Double,
    )

    /**
     * Hard safety/output gates always win. Auto needs four distinct labelled
     * tracks, at least three effective neighbours, and 82% weighted agreement.
     * Cold starts and ambiguous mixes remain off.
     */
    fun decide(
        mode: SpatialAudioMode,
        preference: SpatialAudioPreference,
        profile: SpatialAudioProfile?,
        artist: String,
        examples: List<SpatialAudioExample>,
        learningEnabled: Boolean,
        isPhoneSpeaker: Boolean,
        allowOnPhoneSpeaker: Boolean,
        isDolbyAtmos: Boolean,
        dspAvailable: Boolean,
    ): SpatialAudioDecision {
        if (isPhoneSpeaker && !allowOnPhoneSpeaker) {
            return SpatialAudioDecision(false, SpatialAudioDecisionReason.PHONE_SPEAKER_BLOCKED)
        }
        if (isDolbyAtmos) {
            return SpatialAudioDecision(false, SpatialAudioDecisionReason.DOLBY_ATMOS_BYPASS)
        }
        if (!dspAvailable) {
            return SpatialAudioDecision(false, SpatialAudioDecisionReason.DSP_UNAVAILABLE)
        }
        if (mode == SpatialAudioMode.OFF) {
            return SpatialAudioDecision(false, SpatialAudioDecisionReason.GLOBAL_OFF)
        }
        if (preference == SpatialAudioPreference.FORCE_OFF) {
            return SpatialAudioDecision(false, SpatialAudioDecisionReason.TRACK_OFF)
        }
        if (preference == SpatialAudioPreference.FORCE_ON) {
            return SpatialAudioDecision(true, SpatialAudioDecisionReason.TRACK_ON)
        }
        if (mode == SpatialAudioMode.ON) {
            return SpatialAudioDecision(true, SpatialAudioDecisionReason.GLOBAL_ON)
        }
        if (!learningEnabled) {
            return SpatialAudioDecision(false, SpatialAudioDecisionReason.AUTO_DISABLED)
        }
        if (profile == null || !profile.isUsable) {
            return SpatialAudioDecision(false, SpatialAudioDecisionReason.AUTO_NO_PROFILE)
        }
        return recommend(profile, artist, examples)
    }

    /**
     * Bounded top-k search: O(number of examples × 9), no full-list sorting,
     * and no temporary stream pipeline. This runs on track transitions/settings
     * changes, never for each audio frame.
     */
    fun recommend(
        target: SpatialAudioProfile,
        artist: String,
        examples: List<SpatialAudioExample>,
    ): SpatialAudioDecision {
        if (!target.isUsable) {
            return SpatialAudioDecision(false, SpatialAudioDecisionReason.AUTO_NO_PROFILE)
        }

        val targetFeatures = SpatialAudioFeatures.from(target)
        val targetArtist = artist.trim()
        // AppSettings builds examples from a map keyed by track id, so ids
        // are already unique and no per-transition de-duplication set is needed.
        val nearest = ArrayList<Neighbour>(MAX_NEIGHBOURS)
        val maxDistanceSquared = MAX_DISTANCE * MAX_DISTANCE

        for (example in examples) {
            if (example.feedback == SpatialAudioFeedback.NONE || !example.profile.isUsable) continue

            val distanceSquared = distanceSquared(targetFeatures, example.features)
            if (distanceSquared > maxDistanceSquared) continue
            var insertion = 0
            while (insertion < nearest.size &&
                nearest[insertion].distanceSquared <= distanceSquared
            ) insertion++

            // Allocate only when this candidate enters the bounded top-k list.
            if (insertion < MAX_NEIGHBOURS) {
                val sameArtist = targetArtist.isNotEmpty() &&
                    example.artist.trim().equals(targetArtist, ignoreCase = true)
                nearest.add(
                    insertion,
                    Neighbour(
                        example = example,
                        distanceSquared = distanceSquared,
                        artistBoost = if (sameArtist) SAME_ARTIST_BONUS else 1.0,
                    ),
                )
                if (nearest.size > MAX_NEIGHBOURS) nearest.removeAt(nearest.lastIndex)
            }
        }

        if (nearest.size < MIN_NEIGHBOURS) {
            return insufficientEvidence(nearest)
        }

        // Several tracks by one artist should not outweigh independent artists
        // simply because the listener has a large catalogue from that artist.
        val artistCounts = HashMap<String, Int>()
        nearest.forEach { neighbour ->
            val key = neighbour.example.artist.trim().lowercase()
                .ifBlank { "track:${neighbour.example.trackId}" }
            artistCounts[key] = (artistCounts[key] ?: 0) + 1
        }

        var onWeight = 0.0
        var totalWeight = 0.0
        var squaredWeightSum = 0.0
        var onVotes = 0
        var offVotes = 0
        nearest.forEach { neighbour ->
            val artistKey = neighbour.example.artist.trim().lowercase()
                .ifBlank { "track:${neighbour.example.trackId}" }
            val artistCount = (artistCounts[artistKey] ?: 1).toDouble()
            val weight = neighbour.artistBoost /
                (WEIGHT_FLOOR + neighbour.distanceSquared) /
                sqrt(artistCount)
            totalWeight += weight
            squaredWeightSum += weight * weight
            if (neighbour.example.feedback == SpatialAudioFeedback.ON) {
                onWeight += weight
                onVotes++
            } else {
                offVotes++
            }
        }

        if (totalWeight <= 0.0 || squaredWeightSum <= 0.0) {
            return SpatialAudioDecision(false, SpatialAudioDecisionReason.AUTO_UNCERTAIN)
        }

        val effectiveSamples = (totalWeight * totalWeight / squaredWeightSum).toFloat()
        if (effectiveSamples < MIN_EFFECTIVE_NEIGHBOURS) {
            return SpatialAudioDecision(
                enabled = false,
                reason = SpatialAudioDecisionReason.AUTO_LOW_INDEPENDENT_EVIDENCE,
                similarTrackCount = nearest.size,
                onVotes = onVotes,
                offVotes = offVotes,
                similarTrackIds = nearest.take(5).map { it.example.trackId },
                effectiveSampleCount = effectiveSamples,
            )
        }

        val onShare = (onWeight / totalWeight).coerceIn(0.0, 1.0)
        val confidence = maxOf(onShare, 1.0 - onShare).toFloat()
        val reason = when {
            onShare >= CONFIDENCE_THRESHOLD -> SpatialAudioDecisionReason.AUTO_LEARNED_ON
            onShare <= 1.0 - CONFIDENCE_THRESHOLD -> SpatialAudioDecisionReason.AUTO_LEARNED_OFF
            else -> SpatialAudioDecisionReason.AUTO_UNCERTAIN
        }
        return SpatialAudioDecision(
            enabled = reason == SpatialAudioDecisionReason.AUTO_LEARNED_ON,
            reason = reason,
            confidence = confidence,
            similarTrackCount = nearest.size,
            onVotes = onVotes,
            offVotes = offVotes,
            similarTrackIds = nearest.take(5).map { it.example.trackId },
            effectiveSampleCount = effectiveSamples,
        )
    }

    private fun insufficientEvidence(
        nearest: List<Neighbour>,
        onVotes: Int = nearest.count { it.example.feedback == SpatialAudioFeedback.ON },
        offVotes: Int = nearest.count { it.example.feedback == SpatialAudioFeedback.OFF },
        effectiveSamples: Float = 0f,
    ) = SpatialAudioDecision(
        enabled = false,
        reason = SpatialAudioDecisionReason.AUTO_INSUFFICIENT_EVIDENCE,
        similarTrackCount = nearest.size,
        onVotes = onVotes,
        offVotes = offVotes,
        similarTrackIds = nearest.take(5).map { it.example.trackId },
        effectiveSampleCount = effectiveSamples,
    )

    private fun distanceSquared(a: SpatialAudioFeatures, b: SpatialAudioFeatures): Double {
        val width = (a.logStereoWidth - b.logStereoWidth) / 0.55
        val correlation = (a.channelCorrelation - b.channelCorrelation) / 0.55
        val lowSide = (a.logLowFrequencySide - b.logLowFrequencySide) / 0.45
        val high = (a.highFrequencyRatio - b.highFrequencyRatio) / 0.35
        val crest = (a.crestFactor - b.crestFactor) / 3.5
        return width * width + correlation * correlation + lowSide * lowSide +
            high * high + crest * crest
    }
}
