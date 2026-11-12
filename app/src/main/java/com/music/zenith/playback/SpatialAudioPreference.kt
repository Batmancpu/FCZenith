package com.music.zenith.playback

/**
 * The user's standing choice for Spatial Audio.
 *
 * AUTO means the engine may decide; FORCE_ON and FORCE_OFF are explicit
 * preferences. Legacy Boolean settings map to the corresponding forced
 * choice, never AUTO, so migration cannot silently reinterpret an old choice.
 */
enum class SpatialAudioPreference {
    AUTO,
    FORCE_ON,
    FORCE_OFF;

    /** The legacy Boolean representation, or null when this is AUTO. */
    fun toLegacyBoolean(): Boolean? = when (this) {
        AUTO -> null
        FORCE_ON -> true
        FORCE_OFF -> false
    }

    companion object {
        /** Preserves the exact meaning of an existing global/per-track Boolean. */
        fun fromLegacyBoolean(enabled: Boolean): SpatialAudioPreference =
            if (enabled) FORCE_ON else FORCE_OFF
    }
}

/** The authority that selected the effective state for the current track. */
enum class SpatialAudioDecisionSource {
    MANUAL_TRACK,
    MANUAL_GLOBAL,
    AUTOMATIC,
    FALLBACK,
    SAFETY_BYPASS,
}

/** A technical condition that prevents processing regardless of preference. */
enum class SpatialAudioBypassReason {
    PROCESSOR_UNAVAILABLE,
    UNSUPPORTED_FORMAT,
    DOLBY_ATMOS,
    PHONE_SPEAKER,
    NON_STEREO_INPUT,
}

/**
 * A resolved, truthful playback state.
 *
 * [enabled] is the effective processor state, not merely the user's requested
 * preference. A safety bypass is always disabled and names its exact reason.
 * [confidence] is reserved for a model decision; it is not a generic quality
 * score and must not be inferred from manual or fallback decisions.
 */
data class SpatialAudioDecision(
    val enabled: Boolean,
    val source: SpatialAudioDecisionSource,
    val confidence: Float? = null,
    val bypassReason: SpatialAudioBypassReason? = null,
) {
    init {
        require(confidence == null || (confidence.isFinite() && confidence in 0f..1f)) {
            "Automatic confidence must be finite and between 0 and 1."
        }
        require((source == SpatialAudioDecisionSource.AUTOMATIC) == (confidence != null)) {
            "Only automatic decisions carry model confidence, and every automatic decision must carry it."
        }
        require((source == SpatialAudioDecisionSource.SAFETY_BYPASS) == (bypassReason != null)) {
            "Safety bypass decisions must name a reason; other decisions must not."
        }
        require(source != SpatialAudioDecisionSource.SAFETY_BYPASS || !enabled) {
            "A safety bypass cannot leave Spatial Audio enabled."
        }
    }
}
