package com.music.zenith.playback

import com.music.zenith.playback.audio.AudioBlock
import com.music.zenith.playback.audio.DspChain
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests the second, optional quality layer applied AFTER the original FC experiment. */
class SpatialAudioRefinementTest {
    private fun spatial(rate: Int): SpatialAudioProcessor = SpatialAudioProcessor().apply {
        learningEnabled = false
        configure(rate, 2)
        enabled = true
    }

    private fun block(left: Float, right: Float, frames: Int = 4096): AudioBlock =
        AudioBlock(channelCount = 2, capacityFrames = frames).apply {
            repeat(frames) { i ->
                samples[2 * i] = left
                samples[2 * i + 1] = right
            }
            setFrameCount(frames)
        }

    private fun peak(block: AudioBlock) =
        (0 until block.sampleCount).maxOf { abs(block.samples[it]) }

    @Test fun monoContentRemainsExactlyCenteredWithoutLevelOrTonalShift() {
        for (rate in listOf(44_100, 48_000, 96_000)) {
            val dsp = spatial(rate)
            for (value in listOf(0f, 0.15f, -0.42f, 0.8f)) {
                val audio = block(value, value)
                val before = audio.samples.copyOf()
                dsp.process(audio)
                assertArrayEquals("Center changed at " + rate + " Hz", before, audio.samples, 0.000001f)
            }
        }
    }

    /** A low-frequency side signal receives much less widening than ambience. */
    private fun antiPhaseGain(frequency: Double, rate: Int = 48_000): Double {
        val dsp = spatial(rate)
        var inEnergy = 0.0
        var outEnergy = 0.0
        val framesPerBlock = 4096
        for (batch in 0 until 10) {
            val audio = block(0f, 0f, framesPerBlock)
            for (frame in 0 until framesPerBlock) {
                val sample = (0.10 * sin(2.0 * PI * frequency * (batch * framesPerBlock + frame) / rate)).toFloat()
                audio.samples[2 * frame] = sample
                audio.samples[2 * frame + 1] = -sample
                if (batch >= 6) inEnergy += sample.toDouble() * sample
            }
            dsp.process(audio)
            if (batch >= 6) {
                for (frame in 0 until framesPerBlock) {
                    val left = audio.samples[2 * frame].toDouble()
                    outEnergy += left * left
                }
            }
        }
        return sqrt(outEnergy / inEnergy)
    }

    @Test fun lowBassIsNotWidenedAsMuchAsHighStereoDetail() {
        val bass = antiPhaseGain(40.0)
        val high = antiPhaseGain(2_000.0)
        assertTrue("Expected bass to remain close to original level; gain=" + bass, bass in 0.65..1.35)
        assertTrue("High-frequency side should remain wider; bass=" + bass + " high=" + high, high > bass + 0.25)
    }

    @Test fun finalLimiterCatchesSpatialPeaksWithNoEqOrLoudnessBoost() {
        for (rate in listOf(44_100, 48_000, 96_000)) {
            val chain = DspChain()
            chain.spatial.learningEnabled = false
            chain.spatial.enabled = true
            chain.configure(rate, 2)
            assertTrue(chain.spatial.delegatePeakProtection)
            repeat(3) {
                val audio = block(0.98f, -0.98f)
                chain.process(audio)
                assertTrue("Final peak overshoot at " + rate + " Hz: " + peak(audio), peak(audio) <= 0.986f)
                assertTrue(audio.samples.all { it.isFinite() })
            }
        }
    }

    @Test fun spatialOffRampKeepsFinalLimiterArmedUntilCompletelyDry() {
        val chain = DspChain()
        chain.spatial.learningEnabled = false
        chain.spatial.enabled = true
        chain.configure(48_000, 2)
        chain.process(block(0.88f, -0.88f))
        chain.spatial.enabled = false
        repeat(8) {
            val audio = block(0.88f, -0.88f)
            chain.process(audio)
            assertTrue("Off-ramp exceeded sample peak: " + peak(audio), peak(audio) <= 0.986f)
        }
    }

    @Test fun completelyDisabledPipelinePreservesFloatSamplesExactly() {
        for (rate in listOf(44_100, 48_000, 96_000)) {
            val chain = DspChain()
            chain.spatial.learningEnabled = false
            chain.configure(rate, 2)
            val audio = block(0.389f, -0.715f)
            val before = audio.samples.copyOf()
            chain.process(audio)
            assertArrayEquals(before, audio.samples, 0f)
        }
    }

    @Test fun legacyPcm16OutputRetainsItsIndependentPeakSafetyAndCenter() {
        val dsp = spatial(48_000)
        val input = ByteBuffer.allocateDirect(4096 * 4).order(ByteOrder.nativeOrder())
        repeat(4096) {
            input.putShort(12_000.toShort())
            input.putShort(12_000.toShort())
        }
        input.flip()
        dsp.queueInput(input)
        val out = dsp.getOutput().order(ByteOrder.nativeOrder())
        assertEquals(4096 * 4, out.remaining())
        repeat(4096) {
            assertTrue(abs(out.short.toInt() - 12_000) <= 1)
            assertTrue(abs(out.short.toInt() - 12_000) <= 1)
        }
    }
}
