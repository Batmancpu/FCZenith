package com.music.zenith.playback

import com.music.zenith.playback.audio.AudioBlock
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Production-gated checks only for the removable FC quality experiment. */
class SpatialAudioQualityTest {
    private fun block(frames: Int = 4096, left: Float, right: Float): AudioBlock {
        val result = AudioBlock(channelCount = 2, capacityFrames = frames)
        for (i in 0 until frames) {
            result.samples[i * 2] = left
            result.samples[i * 2 + 1] = right
        }
        result.setFrameCount(frames)
        return result
    }

    private fun peak(block: AudioBlock): Float {
        var max = 0f
        for (i in 0 until block.sampleCount) max = maxOf(max, abs(block.samples[i]))
        return max
    }

    private fun processor(rate: Int, on: Boolean = true) = SpatialAudioProcessor().apply {
        learningEnabled = false
        configure(rate, 2)
        enabled = on
    }

    @Test fun aggressiveAntiphaseRemainsBelowSamplePeakCeiling() {
        for (rate in listOf(44_100, 48_000, 96_000)) {
            val dsp = processor(rate)
            val audio = block(left = 0.99f, right = -0.99f)
            repeat(3) {
                dsp.process(audio)
                assertTrue("sample peak at " + rate + " Hz: " + peak(audio), peak(audio) <= 0.981f)
                assertTrue(audio.samples.all { it.isFinite() })
            }
        }
    }

    @Test fun ordinaryStereoSignalsNeverProduceNonFiniteSamples() {
        for (rate in listOf(44_100, 48_000, 96_000)) {
            val dsp = processor(rate)
            val audio = AudioBlock(channelCount = 2, capacityFrames = 4096)
            repeat(4096) { frame ->
                audio.samples[frame * 2] =
                    (0.96 * sin(2.0 * PI * 440 * frame / rate)).toFloat()
                audio.samples[frame * 2 + 1] =
                    (0.93 * sin(2.0 * PI * 447 * frame / rate)).toFloat()
            }
            audio.setFrameCount(4096)
            repeat(4) {
                dsp.process(audio)
                assertTrue("bad output at $rate Hz", audio.samples.all { it.isFinite() })
                assertTrue("clipped output at $rate Hz", peak(audio) <= 0.981f)
            }
        }
    }

    @Test fun disabledProcessingIsBitExactForFloat32() {
        for (rate in listOf(44_100, 48_000, 96_000)) {
            val dsp = processor(rate, on = false)
            val audio = block(left = 0.751f, right = -0.623f)
            val original = audio.samples.copyOf()
            repeat(5) {
                dsp.process(audio)
                assertArrayEquals(original, audio.samples, 0f)
            }
        }
    }

    @Test fun offToOnAndBackCannotGenerateOutOfRangeFloatSamples() {
        val dsp = processor(48_000, on = false)
        var audio = block(left = 0.88f, right = -0.88f)
        dsp.process(audio)
        dsp.enabled = true
        repeat(6) {
            audio = block(left = 0.88f, right = -0.88f)
            dsp.process(audio)
            assertTrue(peak(audio) <= 0.981f)
        }
        dsp.enabled = false
        repeat(8) {
            audio = block(left = 0.88f, right = -0.88f)
            dsp.process(audio)
            assertTrue(peak(audio) <= 0.981f)
        }
    }

    @Test fun monoFloatPlaybackRemainsUntouched() {
        val dsp = SpatialAudioProcessor().apply {
            configure(48_000, 1)
            enabled = true
        }
        val mono = AudioBlock(channelCount = 1, capacityFrames = 128)
        for (i in mono.samples.indices) mono.samples[i] = (i - 64) / 128f
        mono.setFrameCount(128)
        val original = mono.samples.copyOf()
        dsp.process(mono)
        assertArrayEquals(original, mono.samples, 0f)
    }

    @Test fun pcm16BypassPreservesSamplesAndBufferPosition() {
        val dsp = processor(48_000, on = false)
        val input = ByteBuffer.allocateDirect(4096 * 4).order(ByteOrder.nativeOrder())
        repeat(4096) {
            input.putShort(12345.toShort())
            input.putShort((-12345).toShort())
        }
        input.flip()
        dsp.queueInput(input)
        assertEquals(input.limit(), input.position())
        val output = dsp.getOutput().order(ByteOrder.nativeOrder())
        assertEquals(4096 * 4, output.remaining())
        repeat(4096) {
            assertEquals(12345, output.short.toInt())
            assertEquals(-12345, output.short.toInt())
        }
    }

    @Test fun pcm16WideningNeverWrapsOrHardClips() {
        val dsp = processor(48_000)
        val input = ByteBuffer.allocateDirect(4096 * 4).order(ByteOrder.nativeOrder())
        repeat(4096) {
            input.putShort(32000.toShort())
            input.putShort((-32000).toShort())
        }
        input.flip()
        dsp.queueInput(input)
        val output = dsp.getOutput().order(ByteOrder.nativeOrder())
        assertEquals(4096 * 4, output.remaining())
        while (output.hasRemaining()) {
            assertTrue("PCM16 sample was clipped or wrapped", abs(output.short.toInt()) <= 32120)
        }
    }

    @Test fun profileCompletionCallbackRunsOffPlaybackThread() {
        val dsp = SpatialAudioProcessor()
        dsp.configure(48_000, 2)
        dsp.setTrackContext("test-track", "Track", "Artist")
        dsp.enabled = true
        val latch = CountDownLatch(1)
        val callbackThread = AtomicReference<String>()
        dsp.onProfileReady = { _, _, _, profile ->
            if (profile.isUsable) {
                callbackThread.set(Thread.currentThread().name)
                latch.countDown()
            }
        }
        val input = block(left = 0.30f, right = -0.25f)
        repeat(100) { dsp.process(input) }
        assertTrue("profile callback not delivered", latch.await(5, TimeUnit.SECONDS))
        assertEquals("FC-SpatialProfile", callbackThread.get())
    }

    private fun audioProfile() = SpatialAudioProfile(
        sampleRateHz = 6_000,
        frameCount = 6_000L * 8,
        stereoWidth = 0.72f,
        channelCorrelation = 0.25f,
        lowFrequencySideRatio = 0.31f,
        highFrequencyRatio = 0.39f,
        crestFactor = 3.4f,
        rms = 0.14f,
    )
    private fun example(i: Int, feedback: SpatialAudioFeedback) =
        SpatialAudioExample("song-$i", "Artist $i", feedback, audioProfile())

    private fun recommend(items: List<SpatialAudioExample>): SpatialAudioDecision =
        SpatialAudioLearner.decide(
            mode = SpatialAudioMode.AUTO,
            preference = SpatialAudioPreference.AUTO,
            profile = audioProfile(),
            artist = "Unknown",
            examples = items,
            learningEnabled = true,
            isPhoneSpeaker = false,
            allowOnPhoneSpeaker = false,
            isDolbyAtmos = false,
            dspAvailable = true,
        )

    @Test fun fourOfFiveOffLabelsAreNotYetConfidentEnoughToDisableAuto() {
        val decision = recommend(
            (1..4).map { example(it, SpatialAudioFeedback.OFF) } +
                example(5, SpatialAudioFeedback.ON)
        )
        assertTrue(decision.enabled)
        assertEquals(SpatialAudioDecisionReason.AUTO_UNCERTAIN, decision.reason)
    }

    @Test fun fourStrongOffLabelsCanStillTeachAutoOff() {
        val decision = recommend((1..4).map { example(it, SpatialAudioFeedback.OFF) })
        assertFalse(decision.enabled)
        assertEquals(SpatialAudioDecisionReason.AUTO_LEARNED_OFF, decision.reason)
    }

    @Test fun fourOfFivePositiveLabelsCanStillTeachAutoOn() {
        val decision = recommend(
            (1..4).map { example(it, SpatialAudioFeedback.ON) } +
                example(5, SpatialAudioFeedback.OFF)
        )
        assertTrue(decision.enabled)
        assertEquals(SpatialAudioDecisionReason.AUTO_LEARNED_ON, decision.reason)
    }
}
