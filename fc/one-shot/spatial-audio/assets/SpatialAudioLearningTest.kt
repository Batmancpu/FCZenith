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
    fun coldStartDefaultsOnForEligibleStereo() {
        val result = decide()
        assertTrue(result.enabled)
        assertEquals(SpatialAudioDecisionReason.AUTO_DEFAULT_ON, result.reason)
    }

    @Test
    fun fourSimilarExplicitOnChoicesTeachAutoOn() {
        val result = decide(examples = listOf(
            example("one", SpatialAudioFeedback.ON),
            example("two", SpatialAudioFeedback.ON),
            example("three", SpatialAudioFeedback.ON),
            example("four", SpatialAudioFeedback.ON),
        ))
        assertTrue(result.enabled)
        assertEquals(SpatialAudioDecisionReason.AUTO_LEARNED_ON, result.reason)
        assertEquals(4, result.onVotes)
    }

    @Test
    fun fourSimilarExplicitOffChoicesTeachAutoOff() {
        val result = decide(examples = listOf(
            example("one", SpatialAudioFeedback.OFF),
            example("two", SpatialAudioFeedback.OFF),
            example("three", SpatialAudioFeedback.OFF),
            example("four", SpatialAudioFeedback.OFF),
        ))
        assertFalse(result.enabled)
        assertEquals(SpatialAudioDecisionReason.AUTO_LEARNED_OFF, result.reason)
    }

    @Test
    fun mixedFeedbackDoesNotDisableTheOnBiasedDefault() {
        val result = decide(examples = listOf(
            example("one", SpatialAudioFeedback.ON),
            example("two", SpatialAudioFeedback.ON),
            example("three", SpatialAudioFeedback.OFF),
            example("four", SpatialAudioFeedback.OFF),
        ))
        assertTrue(result.enabled)
        assertEquals(SpatialAudioDecisionReason.AUTO_UNCERTAIN, result.reason)
    }

    @Test
    fun fourOfFivePositiveChoicesCrossTheSeventyFivePercentGate() {
        val result = decide(examples = listOf(
            example("one", SpatialAudioFeedback.ON),
            example("two", SpatialAudioFeedback.ON),
            example("three", SpatialAudioFeedback.ON),
            example("four", SpatialAudioFeedback.ON),
            example("five", SpatialAudioFeedback.OFF),
        ))
        assertTrue(result.enabled)
        assertEquals(SpatialAudioDecisionReason.AUTO_LEARNED_ON, result.reason)
    }

    @Test
    fun aSingleCloseTrackCannotOutvoteWeakIndependentEvidence() {
        val farther = profile(width = 1.7f, correlation = 0.6f, lowSide = 0.75f, high = 0.5f, crest = 4.5f)
        val result = decide(examples = listOf(
            example("near", SpatialAudioFeedback.OFF),
            example("far-one", SpatialAudioFeedback.ON, "Other A").copy(profile = farther),
            example("far-two", SpatialAudioFeedback.ON, "Other B").copy(profile = farther),
            example("far-three", SpatialAudioFeedback.ON, "Other C").copy(profile = farther),
        ))
        assertTrue(result.enabled)
        assertEquals(SpatialAudioDecisionReason.AUTO_LOW_INDEPENDENT_EVIDENCE, result.reason)
    }

    @Test
    fun decimatedAndLegacyFullRateProfilesAreUsable() {
        val decimated = profile().copy(sampleRateHz = 6_000, frameCount = 6_000L * 8L)
        assertTrue(decimated.isUsable)
        assertFalse(decimated.copy(frameCount = 6_000L * 3L).isUsable)
        assertTrue(profile().isUsable)
    }

    @Test
    fun distantMixesDoNotCountEvenWhenArtistMatches() {
        val distant = profile(width = 12f, correlation = -0.95f, lowSide = 9f, high = 0.98f, crest = 20f)
        val result = decide(examples = listOf(
            example("one", SpatialAudioFeedback.OFF).copy(profile = distant),
            example("two", SpatialAudioFeedback.OFF).copy(profile = distant),
            example("three", SpatialAudioFeedback.OFF).copy(profile = distant),
            example("four", SpatialAudioFeedback.OFF).copy(profile = distant),
        ))
        assertTrue(result.enabled)
        assertEquals(SpatialAudioDecisionReason.AUTO_INSUFFICIENT_EVIDENCE, result.reason)
    }

    @Test
    fun invalidOrSilentProfilesUseDefaultOnWithoutTraining() {
        val invalid = profile().copy(rms = 0f)
        assertFalse(invalid.isUsable)
        val result = decide(target = invalid)
        assertTrue(result.enabled)
        assertEquals(SpatialAudioDecisionReason.AUTO_DEFAULT_ON, result.reason)
    }

    @Test
    fun globalAndTrackOverridesAreDeterministic() {
        assertTrue(decide(mode = SpatialAudioMode.ON).enabled)
        assertFalse(decide(mode = SpatialAudioMode.OFF, preference = SpatialAudioPreference.FORCE_ON).enabled)
        assertTrue(decide(preference = SpatialAudioPreference.FORCE_ON).enabled)
        assertFalse(decide(preference = SpatialAudioPreference.FORCE_OFF).enabled)
    }

    @Test
    fun phoneSpeakerBlocksManualOnUnlessAllowed() {
        val denied = decide(preference = SpatialAudioPreference.FORCE_ON, speaker = true)
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
    fun disablingLearningStopsProfilingButKeepsEffectOn() {
        assertTrue(decide(learning = false).enabled)
        assertTrue(decide(mode = SpatialAudioMode.ON, learning = false).enabled)
        assertTrue(decide(preference = SpatialAudioPreference.FORCE_ON, learning = false).enabled)
        assertFalse(decide(preference = SpatialAudioPreference.FORCE_OFF, learning = false).enabled)
    }
}
