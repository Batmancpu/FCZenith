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
        get() = sampleRateHz in 8_000..192_000 &&
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

data class SpatialAudioExample(
    val trackId: String,
    val artist: String,
    val feedback: SpatialAudioFeedback,
    val profile: SpatialAudioProfile,
)

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
)

object SpatialAudioLearner {
    private const val MIN_NEIGHBOURS = 3
    private const val MAX_NEIGHBOURS = 7
    private const val MAX_DISTANCE = 2.6
    private const val CONFIDENCE_THRESHOLD = 0.78
    private const val WEIGHT_FLOOR = 0.18
    private const val SAME_ARTIST_BONUS = 1.12

    /**
     * Hard safety/output gates always win. Automatic learning is conservative:
     * a cold start, sparse labels or mixed evidence leaves processing OFF.
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

    /** Exposed separately so the settings page can explain a current track's decision. */
    fun recommend(
        target: SpatialAudioProfile,
        artist: String,
        examples: List<SpatialAudioExample>,
    ): SpatialAudioDecision {
        if (!target.isUsable) {
            return SpatialAudioDecision(false, SpatialAudioDecisionReason.AUTO_NO_PROFILE)
        }

        val nearest = examples.asSequence()
            .filter { it.feedback != SpatialAudioFeedback.NONE && it.profile.isUsable }
            .distinctBy { it.trackId }
            .map { example ->
                val sameArtist = artist.isNotBlank() &&
                    example.artist.trim().equals(artist.trim(), ignoreCase = true)
                Triple(example, distance(target, example.profile), if (sameArtist) SAME_ARTIST_BONUS else 1.0)
            }
            .filter { it.second <= MAX_DISTANCE }
            .sortedBy { it.second }
            .take(MAX_NEIGHBOURS)
            .toList()

        if (nearest.size < MIN_NEIGHBOURS) {
            return SpatialAudioDecision(
                enabled = false,
                reason = SpatialAudioDecisionReason.AUTO_INSUFFICIENT_EVIDENCE,
                similarTrackCount = nearest.size,
                onVotes = nearest.count { it.first.feedback == SpatialAudioFeedback.ON },
                offVotes = nearest.count { it.first.feedback == SpatialAudioFeedback.OFF },
                similarTrackIds = nearest.take(3).map { it.first.trackId },
            )
        }

        var onWeight = 0.0
        var totalWeight = 0.0
        var onVotes = 0
        var offVotes = 0
        nearest.forEach { (example, distance, artistBoost) ->
            val weight = artistBoost / (WEIGHT_FLOOR + distance * distance)
            totalWeight += weight
            if (example.feedback == SpatialAudioFeedback.ON) {
                onWeight += weight
                onVotes++
            } else {
                offVotes++
            }
        }
        if (totalWeight <= 0.0) {
            return SpatialAudioDecision(false, SpatialAudioDecisionReason.AUTO_UNCERTAIN)
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
            similarTrackIds = nearest.take(3).map { it.first.trackId },
        )
    }

    private fun distance(a: SpatialAudioProfile, b: SpatialAudioProfile): Double {
        val width = (ln(1.0 + a.stereoWidth) - ln(1.0 + b.stereoWidth)) / 0.55
        val correlation = (a.channelCorrelation - b.channelCorrelation) / 0.55
        val lowSide = (ln(1.0 + a.lowFrequencySideRatio) - ln(1.0 + b.lowFrequencySideRatio)) / 0.45
        val high = (a.highFrequencyRatio - b.highFrequencyRatio) / 0.35
        val crest = (a.crestFactor - b.crestFactor) / 3.5
        return sqrt(width * width + correlation * correlation + lowSide * lowSide + high * high + crest * crest)
    }
}
