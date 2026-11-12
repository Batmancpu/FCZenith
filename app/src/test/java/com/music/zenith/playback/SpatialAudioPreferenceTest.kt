package com.music.zenith.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SpatialAudioPreferenceTest {
    @Test
    fun legacyFalseRemainsAnExplicitOffChoice() {
        assertEquals(
            SpatialAudioPreference.FORCE_OFF,
            SpatialAudioPreference.fromLegacyBoolean(false),
        )
        assertEquals(false, SpatialAudioPreference.FORCE_OFF.toLegacyBoolean())
    }

    @Test
    fun legacyTrueRemainsAnExplicitOnChoice() {
        assertEquals(
            SpatialAudioPreference.FORCE_ON,
            SpatialAudioPreference.fromLegacyBoolean(true),
        )
        assertEquals(true, SpatialAudioPreference.FORCE_ON.toLegacyBoolean())
    }

    @Test
    fun autoHasNoLegacyBooleanRepresentation() {
        assertNull(SpatialAudioPreference.AUTO.toLegacyBoolean())
    }

    @Test
    fun automaticDecisionRequiresValidConfidence() {
        val decision = SpatialAudioDecision(
            enabled = true,
            source = SpatialAudioDecisionSource.AUTOMATIC,
            confidence = 0.82f,
        )

        assertTrue(decision.enabled)
        assertEquals(0.82f, decision.confidence)
        assertNull(decision.bypassReason)
    }

    @Test
    fun automaticConfidenceMustBeFiniteAndInRange() {
        listOf(Float.NaN, Float.NEGATIVE_INFINITY, -0.01f, 1.01f).forEach { confidence ->
            assertThrows(IllegalArgumentException::class.java) {
                SpatialAudioDecision(
                    enabled = true,
                    source = SpatialAudioDecisionSource.AUTOMATIC,
                    confidence = confidence,
                )
            }
        }
    }

    @Test
    fun nonAutomaticDecisionCannotCarryModelConfidence() {
        assertThrows(IllegalArgumentException::class.java) {
            SpatialAudioDecision(
                enabled = true,
                source = SpatialAudioDecisionSource.MANUAL_TRACK,
                confidence = 0.9f,
            )
        }
    }

    @Test
    fun safetyBypassMustBeDisabledAndExplainWhy() {
        val decision = SpatialAudioDecision(
            enabled = false,
            source = SpatialAudioDecisionSource.SAFETY_BYPASS,
            bypassReason = SpatialAudioBypassReason.PHONE_SPEAKER,
        )

        assertFalse(decision.enabled)
        assertEquals(SpatialAudioBypassReason.PHONE_SPEAKER, decision.bypassReason)
    }

    @Test
    fun safetyBypassCannotClaimProcessingIsEnabled() {
        assertThrows(IllegalArgumentException::class.java) {
            SpatialAudioDecision(
                enabled = true,
                source = SpatialAudioDecisionSource.SAFETY_BYPASS,
                bypassReason = SpatialAudioBypassReason.DOLBY_ATMOS,
            )
        }
    }

    @Test
    fun safetyBypassMustAlwaysNameItsReason() {
        assertThrows(IllegalArgumentException::class.java) {
            SpatialAudioDecision(
                enabled = false,
                source = SpatialAudioDecisionSource.SAFETY_BYPASS,
            )
        }
    }

    @Test
    fun ordinaryDecisionCannotCarryABypassReason() {
        assertThrows(IllegalArgumentException::class.java) {
            SpatialAudioDecision(
                enabled = false,
                source = SpatialAudioDecisionSource.FALLBACK,
                bypassReason = SpatialAudioBypassReason.PROCESSOR_UNAVAILABLE,
            )
        }
    }
}
