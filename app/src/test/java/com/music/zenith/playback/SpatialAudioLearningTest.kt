package com.music.zenith.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpatialAudioLearningTest {
    private fun profile(
        width: Float = 0.7f,
        correlation: Float = 0.2f,
        lowSide: Float = 0.25f,
        high: Float = 0.42f,
        crest: Float = 3.2f,
    ) = SpatialAudioProfile(
        sampleRateHz = 48_000,
        frameCount = 48_000L * 8L,
        stereoWidth = width,
        channelCorrelation = correlation,
        lowFrequencySideRatio = lowSide,
        highFrequencyRatio = high,
        crestFactor = crest,
        rms = 0.16f,
    )

    private fun example(id: String, feedback: SpatialAudioFeedback, artist: String = "Artist A") =
        SpatialAudioExample(id, artist, feedback, profile())

    private fun decide(
        preference: SpatialAudioPreference = SpatialAudioPreference.AUTO,
        mode: SpatialAudioMode = SpatialAudioMode.AUTO,
        examples: List<SpatialAudioExample> = emptyList(),
        target: SpatialAudioProfile? = profile(),
        artist: String = "Artist A",
        learning: Boolean = true,
        speaker: Boolean = false,
        allowSpeaker: Boolean = false,
        atmos: Boolean = false,
        dsp: Boolean = true,
    ) = SpatialAudioLearner.decide(
        mode, preference, target, artist, examples, learning, speaker, allowSpeaker, atmos, dsp,
    )

    @Test
    fun coldStartNeverWidensWithoutEvidence() {
        val result = decide()
        assertFalse(result.enabled)
        assertEquals(SpatialAudioDecisionReason.AUTO_INSUFFICIENT_EVIDENCE, result.reason)
    }

    @Test
    fun threeSimilarExplicitOnChoicesCanTeachAuto() {
        val result = decide(examples = listOf(
            example("one", SpatialAudioFeedback.ON),
            example("two", SpatialAudioFeedback.ON),
            example("three", SpatialAudioFeedback.ON),
        ))
        assertTrue(result.enabled)
        assertEquals(SpatialAudioDecisionReason.AUTO_LEARNED_ON, result.reason)
        assertEquals(3, result.onVotes)
    }

    @Test
    fun threeSimilarExplicitOffChoicesTeachAutoToStayOff() {
        val result = decide(examples = listOf(
            example("one", SpatialAudioFeedback.OFF),
            example("two", SpatialAudioFeedback.OFF),
            example("three", SpatialAudioFeedback.OFF),
        ))
        assertFalse(result.enabled)
        assertEquals(SpatialAudioDecisionReason.AUTO_LEARNED_OFF, result.reason)
    }

    @Test
    fun mixedEvidenceDefaultsOffRatherThanGuessing() {
        val result = decide(examples = listOf(
            example("one", SpatialAudioFeedback.ON),
            example("two", SpatialAudioFeedback.ON),
            example("three", SpatialAudioFeedback.OFF),
            example("four", SpatialAudioFeedback.OFF),
        ))
        assertFalse(result.enabled)
        assertEquals(SpatialAudioDecisionReason.AUTO_UNCERTAIN, result.reason)
    }

    @Test
    fun distantMixesDoNotCountEvenWhenArtistMatches() {
        val distant = profile(width = 12f, correlation = -0.95f, lowSide = 9f, high = 0.98f, crest = 20f)
        val result = decide(examples = listOf(
            example("one", SpatialAudioFeedback.ON).copy(profile = distant),
            example("two", SpatialAudioFeedback.ON).copy(profile = distant),
            example("three", SpatialAudioFeedback.ON).copy(profile = distant),
        ))
        assertFalse(result.enabled)
        assertEquals(SpatialAudioDecisionReason.AUTO_INSUFFICIENT_EVIDENCE, result.reason)
    }

    @Test
    fun invalidOrSilentProfilesAreRejected() {
        val invalid = profile().copy(rms = 0f)
        assertFalse(invalid.isUsable)
        val result = decide(target = invalid)
        assertFalse(result.enabled)
        assertEquals(SpatialAudioDecisionReason.AUTO_NO_PROFILE, result.reason)
    }

    @Test
    fun globalAndTrackOverridesAreDeterministic() {
        assertTrue(decide(mode = SpatialAudioMode.ON).enabled)
        assertFalse(decide(mode = SpatialAudioMode.OFF, preference = SpatialAudioPreference.FORCE_ON).enabled)
        assertTrue(decide(preference = SpatialAudioPreference.FORCE_ON).enabled)
        assertFalse(decide(preference = SpatialAudioPreference.FORCE_OFF, examples = listOf(
            example("one", SpatialAudioFeedback.ON),
            example("two", SpatialAudioFeedback.ON),
            example("three", SpatialAudioFeedback.ON),
        )).enabled)
    }

    @Test
    fun phoneSpeakerRequiresItsOwnExplicitPermission() {
        val denied = decide(mode = SpatialAudioMode.ON, speaker = true)
        assertFalse(denied.enabled)
        assertEquals(SpatialAudioDecisionReason.PHONE_SPEAKER_BLOCKED, denied.reason)
        assertTrue(decide(mode = SpatialAudioMode.ON, speaker = true, allowSpeaker = true).enabled)
    }

    @Test
    fun atmosAndUnavailableDspAlwaysBypass() {
        assertEquals(SpatialAudioDecisionReason.DOLBY_ATMOS_BYPASS, decide(mode = SpatialAudioMode.ON, atmos = true).reason)
        assertEquals(SpatialAudioDecisionReason.DSP_UNAVAILABLE, decide(mode = SpatialAudioMode.ON, dsp = false).reason)
    }

    @Test
    fun learningCanBeDisabledWithoutChangingManualModes() {
        assertFalse(decide(learning = false).enabled)
        assertTrue(decide(mode = SpatialAudioMode.ON, learning = false).enabled)
        assertTrue(decide(preference = SpatialAudioPreference.FORCE_ON, learning = false).enabled)
    }
}
