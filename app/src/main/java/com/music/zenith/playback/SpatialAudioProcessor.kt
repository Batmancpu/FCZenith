package com.music.zenith.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.music.zenith.playback.audio.AudioBlock
import com.music.zenith.playback.audio.FloatAudioProcessor
import com.music.zenith.playback.audio.PcmBoundary
import java.nio.ByteOrder
import kotlin.math.roundToInt

/**
 * Cheap stand-in for "spatial audio": widens the mid/side image and mixes in
 * a short, low-passed cross-feed between channels — the same trick most
 * consumer virtual-surround plugins use. O(1) per sample, no FFT or
 * convolution, so it costs nothing worth measuring on a phone CPU.
 *
 * Exists because the platform [android.media.audiofx.Virtualizer] produced no
 * audible difference on the reference device — likely swallowed by the OEM's
 * own audio effect chain — so this runs inside ExoPlayer's own audio
 * processor pipeline instead, where nothing else can intercept it.
 */
@UnstableApi
class SpatialAudioProcessor : BaseAudioProcessor() {

    @Volatile
    var enabled: Boolean = false

    @Volatile
    var currentTrackId: String? = null
        private set

    @Volatile
    var currentTitle: String = ""
        private set

    @Volatile
    var currentArtist: String = ""
        private set

    @Volatile
    var onProfileReady: ((String, String, String, SpatialAudioProfile) -> Unit)? = null

    private class ProfileAccumulator(
        val trackId: String,
        var title: String,
        var artist: String,
        var sampleRateHz: Int,
        var lowpassAlpha: Double,
        var targetFrames: Long,
    ) {
        var frames = 0L
        var leftSquared = 0.0
        var rightSquared = 0.0
        var cross = 0.0
        var midSquared = 0.0
        var sideSquared = 0.0
        var lowMidSquared = 0.0
        var lowSideSquared = 0.0
        var highFrequencySquared = 0.0
        var totalEnergy = 0.0
        var peakAmplitude = 0.0
        var lowLeft = 0.0
        var lowRight = 0.0
        var completed = false
    }

    @Volatile
    private var profileAccumulator: ProfileAccumulator? = null

    @Volatile
    private var profileLearningAllowed: Boolean = true

    @Volatile
    var learningEnabled: Boolean = true
        set(value) {
            field = value
            if (!value) {
                profileAccumulator = null
            } else if (profileAccumulator == null && profileLearningAllowed) {
                val id = currentTrackId
                if (id != null) {
                    val rate = sampleRate.takeIf { it > 0 } ?: FALLBACK_SAMPLE_RATE
                    profileAccumulator = ProfileAccumulator(
                        id, currentTitle, currentArtist, rate, rate.toLong() * PROFILE_SECONDS,
                    )
                }
            }
        }

    /** Context belongs to this processor/player, not the session player. */
    fun setTrackContext(
        trackId: String?,
        title: String?,
        artist: String?,
        allowProfile: Boolean = true,
    ) {
        val id = trackId?.takeIf { it.isNotBlank() }
        profileLearningAllowed = allowProfile
        if (id == currentTrackId) {
            currentTitle = title.orEmpty()
            currentArtist = artist.orEmpty()
            if (!allowProfile || !learningEnabled) {
                profileAccumulator = null
            } else if (profileAccumulator == null && id != null) {
                profileAccumulator = newProfileAccumulator(id, currentTitle, currentArtist)
            } else {
                profileAccumulator?.takeIf { !it.completed }?.let {
                    it.title = currentTitle
                    it.artist = currentArtist
                }
            }
            return
        }
        currentTrackId = id
        currentTitle = title.orEmpty()
        currentArtist = artist.orEmpty()
        profileAccumulator = if (id == null || !learningEnabled || !allowProfile) {
            null
        } else {
            newProfileAccumulator(id, currentTitle, currentArtist)
        }
    }

    private fun newProfileAccumulator(id: String, title: String, artist: String): ProfileAccumulator {
        val rate = sampleRate.takeIf { it > 0 } ?: FALLBACK_SAMPLE_RATE
        return ProfileAccumulator(
            id, title, artist, rate, lowpassAlpha(rate), rate.toLong() * PROFILE_SECONDS,
        )
    }

    private fun lowpassAlpha(rate: Int): Double =
        1.0 - kotlin.math.exp(-2.0 * kotlin.math.PI * LOWPASS_HZ / rate.toDouble())

    private fun observeFloatSamples(block: AudioBlock) {
        if (!learningEnabled || !profileLearningAllowed) return
        val accumulator = profileAccumulator ?: return
        if (accumulator.completed || accumulator.frames >= accumulator.targetFrames) return
        val count = minOf(
            block.frameCount,
            block.samples.size / 2,
            (accumulator.targetFrames - accumulator.frames).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
        )
        var index = 0
        repeat(count) {
            observeFrame(accumulator, block.samples[index].toDouble(), block.samples[index + 1].toDouble())
            index += 2
        }
        finishProfile(accumulator)
    }

    private fun observePcm16Samples(input: java.nio.ByteBuffer) {
        if (!learningEnabled || !profileLearningAllowed) return
        val accumulator = profileAccumulator ?: return
        if (accumulator.completed || accumulator.frames >= accumulator.targetFrames) return
        val view = input.duplicate().order(ByteOrder.nativeOrder())
        val count = minOf(
            view.remaining() / BYTES_PER_FRAME,
            (accumulator.targetFrames - accumulator.frames).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
        )
        val scale = 1.0 / 32768.0
        repeat(count) {
            observeFrame(accumulator, view.short.toDouble() * scale, view.short.toDouble() * scale)
        }
        finishProfile(accumulator)
    }

    private fun observeFrame(accumulator: ProfileAccumulator, left: Double, right: Double) {
        val mid = (left + right) * 0.5
        val side = (left - right) * 0.5
        accumulator.leftSquared += left * left
        accumulator.rightSquared += right * right
        accumulator.cross += left * right
        accumulator.midSquared += mid * mid
        accumulator.sideSquared += side * side

        // One-pole low-pass is used only for the fingerprint; samples are untouched.
        accumulator.lowLeft += accumulator.lowpassAlpha * (left - accumulator.lowLeft)
        accumulator.lowRight += accumulator.lowpassAlpha * (right - accumulator.lowRight)
        val lowMid = (accumulator.lowLeft + accumulator.lowRight) * 0.5
        val lowSide = (accumulator.lowLeft - accumulator.lowRight) * 0.5
        accumulator.lowMidSquared += lowMid * lowMid
        accumulator.lowSideSquared += lowSide * lowSide
        val highLeft = left - accumulator.lowLeft
        val highRight = right - accumulator.lowRight
        accumulator.highFrequencySquared += (highLeft * highLeft + highRight * highRight) * 0.5
        accumulator.totalEnergy += (left * left + right * right) * 0.5
        accumulator.peakAmplitude = maxOf(accumulator.peakAmplitude, kotlin.math.abs(left), kotlin.math.abs(right))
        accumulator.frames++
    }

    private fun finishProfile(accumulator: ProfileAccumulator) {
        if (accumulator.completed || accumulator.frames < accumulator.targetFrames) return
        accumulator.completed = true
        if (profileAccumulator === accumulator) profileAccumulator = null

        val frames = accumulator.frames.toDouble()
        val rms = kotlin.math.sqrt((accumulator.leftSquared + accumulator.rightSquared) / (2.0 * frames))
        if (!rms.isFinite() || rms < SpatialAudioProfile.MIN_RMS) return
        val width = kotlin.math.sqrt(accumulator.sideSquared / (accumulator.midSquared + EPSILON)).coerceIn(0.0, 20.0)
        val correlation = (accumulator.cross /
            kotlin.math.sqrt(accumulator.leftSquared * accumulator.rightSquared + EPSILON)).coerceIn(-1.0, 1.0)
        val lowSide = kotlin.math.sqrt(accumulator.lowSideSquared /
            (accumulator.lowMidSquared + EPSILON)).coerceIn(0.0, 20.0)
        val highFrequency = (accumulator.highFrequencySquared /
            (accumulator.totalEnergy + EPSILON)).coerceIn(0.0, 1.0)
        val crestFactor = (accumulator.peakAmplitude / rms.coerceAtLeast(EPSILON)).coerceIn(0.0, 100.0)
        val profile = SpatialAudioProfile(
            sampleRateHz = accumulator.sampleRateHz,
            frameCount = accumulator.frames,
            stereoWidth = width.toFloat(),
            channelCorrelation = correlation.toFloat(),
            lowFrequencySideRatio = lowSide.toFloat(),
            highFrequencyRatio = highFrequency.toFloat(),
            crestFactor = crestFactor.toFloat(),
            rms = rms.toFloat(),
        )
        if (profile.isUsable) {
            onProfileReady?.invoke(accumulator.trackId, accumulator.title, accumulator.artist, profile)
        }
    }

    /**
     * Wet/dry blend: 0 = untouched, 1 = fully widened. Ramps toward [enabled]
     * over [RAMP_MS] so a mid-song toggle doesn't click. Audio thread only.
     */
    private var mix = 0f

    /** After a flush the blend jumps straight to its target instead of ramping. */
    private var snapMix = true

    /** How much wider the stereo image gets. 1.0 = untouched. */
    private val widthGain = 2.5f

    /** Makeup attenuation after widening, so the wider side energy doesn't clip. */
    private val outputGain = 0.82f

    /** How much of the delayed, low-passed opposite channel gets mixed back in. */
    private val crossfeedGain = 0.2f

    /** One-pole lowpass factor applied to the cross-fed signal — dulls it, like a far ear would. */
    private val lowpassCoeff = 0.3f

    private var sampleRate: Int = 0
    private var channelCount: Int = 0

    private var delayLeft = FloatArray(0)
    private var delayRight = FloatArray(0)
    private var delayIndex = 0
    private var lowpassLeft = 0f
    private var lowpassRight = 0f

    /**
     * Configures the Float32 DSP engine for [sampleRate] and [channelCount].
     */
    fun configure(sampleRate: Int, channelCount: Int) {
        this.sampleRate = sampleRate
        this.channelCount = channelCount
        if (sampleRate > 0) {
            profileAccumulator?.takeIf { it.frames == 0L }?.let {
                it.sampleRateHz = sampleRate
                it.lowpassAlpha = lowpassAlpha(sampleRate)
                it.targetFrames = sampleRate.toLong() * PROFILE_SECONDS
            }
        }
        if (channelCount != 2 || sampleRate <= 0) {
            delayLeft = FloatArray(0)
            delayRight = FloatArray(0)
            delayIndex = 0
            lowpassLeft = 0f
            lowpassRight = 0f
            return
        }
        val delaySamples = (sampleRate * DELAY_MS / 1000f)
            .roundToInt()
            .coerceAtLeast(1)
        if (delayLeft.size != delaySamples) {
            delayLeft = FloatArray(delaySamples)
            delayRight = FloatArray(delaySamples)
        }
        onFlush()
    }

    /**
     * Processes interleaved Float32 audio samples in [block] in-place.
     * Preserves dynamic headroom without clamping to [-1.0f, +1.0f].
     */
    fun process(block: AudioBlock) {
        if (block.frameCount == 0) return
        if (block.channelCount != 2) return // Spatial widening only applies to stereo
        observeFloatSamples(block)

        val delaySize = delayLeft.size
        if (delaySize == 0) return

        val target = if (enabled) 1f else 0f
        if (snapMix) {
            mix = target
            snapMix = false
        }
        if (mix == 0f && target == 0f) return
        val step = rampStep()

        val totalSamples = block.frameCount * 2
        var idx = 0
        var dIdx = delayIndex
        var lpL = lowpassLeft
        var lpR = lowpassRight

        while (idx < totalSamples) {
            val left = block.samples[idx]
            val right = block.samples[idx + 1]

            val mid = (left + right) * 0.5f
            val side = (left - right) * 0.5f * widthGain
            var widenedLeft = mid + side
            var widenedRight = mid - side

            val delayedRight = delayRight[dIdx]
            val delayedLeft = delayLeft[dIdx]
            lpL += lowpassCoeff * (delayedRight - lpL)
            lpR += lowpassCoeff * (delayedLeft - lpR)
            widenedLeft += lpL * crossfeedGain
            widenedRight += lpR * crossfeedGain

            delayLeft[dIdx] = left
            delayRight[dIdx] = right
            dIdx = (dIdx + 1) % delaySize

            if (mix < target) mix = minOf(target, mix + step)
            else if (mix > target) mix = maxOf(target, mix - step)
            val wetLeft = widenedLeft * outputGain
            val wetRight = widenedRight * outputGain

            // Headroom is preserved: no clamping to [-1.0f, +1.0f]
            block.samples[idx] = if (mix >= 1f) wetLeft else left + (wetLeft - left) * mix
            block.samples[idx + 1] = if (mix >= 1f) wetRight else right + (wetRight - right) * mix
            idx += 2
        }

        delayIndex = dIdx
        lowpassLeft = lpL
        lowpassRight = lpR
    }

    /**
     * Stereo 16-bit only: the widening is written in terms of a left and a
     * right sample, and there is no mid/side of a mono voice note or of a 5.1
     * mix to widen.
     *
     * Bowing out with [AudioProcessor.AudioFormat.NOT_SET] rather than an
     * [AudioProcessor.UnhandledAudioFormatException] is what keeps those
     * tracks playable at all.
     * [DefaultAudioSink][androidx.media3.exoplayer.audio.DefaultAudioSink]
     * configures every processor in its chain whether or not the effect is
     * switched on, and a throw from any
     * of them fails the whole sink — the renderer dies with
     * "MediaCodecAudioRenderer error" before a sample is written. NOT_SET
     * means "inactive for this format" and the chain routes around this
     * processor instead.
     *
     * Nothing from YouTube is anything but stereo, so this only ever showed
     * itself on files from the device: every mono or multichannel track in the
     * local library failed to play while downloads were fine.
     */
    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount != 2) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        configure(inputAudioFormat.sampleRate, inputAudioFormat.channelCount)
        return inputAudioFormat
    }

    override fun onFlush() {
        snapMix = true
        delayLeft.fill(0f)
        delayRight.fill(0f)
        delayIndex = 0
        lowpassLeft = 0f
        lowpassRight = 0f
    }

    override fun onReset() {
        delayLeft = FloatArray(0)
        delayRight = FloatArray(0)
        delayIndex = 0
        lowpassLeft = 0f
        lowpassRight = 0f
        channelCount = 0
        sampleRate = 0
    }

    override fun queueInput(inputBuffer: java.nio.ByteBuffer) {
        val frameCount = inputBuffer.remaining() / BYTES_PER_FRAME
        if (frameCount == 0) return
        observePcm16Samples(inputBuffer)
        val outputBuffer = replaceOutputBuffer(frameCount * BYTES_PER_FRAME)

        val target = if (enabled) 1f else 0f
        if (snapMix) {
            mix = target
            snapMix = false
        }
        if (mix == 0f && target == 0f) {
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }
        val step = rampStep()

        inputBuffer.order(ByteOrder.nativeOrder())
        outputBuffer.order(ByteOrder.nativeOrder())

        val delaySize = delayLeft.size
        if (delaySize == 0) {
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        var dIdx = delayIndex
        var lpL = lowpassLeft
        var lpR = lowpassRight
        val invScale = 1.0f / 32768.0f

        repeat(frameCount) {
            val left = inputBuffer.short.toFloat() * invScale
            val right = inputBuffer.short.toFloat() * invScale

            val mid = (left + right) * 0.5f
            val side = (left - right) * 0.5f * widthGain
            var widenedLeft = mid + side
            var widenedRight = mid - side

            val delayedRight = delayRight[dIdx]
            val delayedLeft = delayLeft[dIdx]
            lpL += lowpassCoeff * (delayedRight - lpL)
            lpR += lowpassCoeff * (delayedLeft - lpR)
            widenedLeft += lpL * crossfeedGain
            widenedRight += lpR * crossfeedGain

            delayLeft[dIdx] = left
            delayRight[dIdx] = right
            dIdx = (dIdx + 1) % delaySize

            if (mix < target) mix = minOf(target, mix + step)
            else if (mix > target) mix = maxOf(target, mix - step)
            val wetLeft = widenedLeft * outputGain
            val wetRight = widenedRight * outputGain
            val outLeft = if (mix >= 1f) wetLeft else left + (wetLeft - left) * mix
            val outRight = if (mix >= 1f) wetRight else right + (wetRight - right) * mix
            outputBuffer.putShort(PcmBoundary.clamp16FromFloat(outLeft))
            outputBuffer.putShort(PcmBoundary.clamp16FromFloat(outRight))
        }
        delayIndex = dIdx
        lowpassLeft = lpL
        lowpassRight = lpR

        outputBuffer.flip()
    }

    /** Per-sample [mix] increment: a full 0 to 1 swing takes [RAMP_MS]. */
    private fun rampStep(): Float =
        if (sampleRate > 0) 1000f / (sampleRate * RAMP_MS) else 1f

    private companion object {
        const val BYTES_PER_FRAME = 4 // stereo, 16-bit
        const val DELAY_MS = 15
        const val RAMP_MS = 300f
        const val PROFILE_SECONDS = 8L
        const val FALLBACK_SAMPLE_RATE = 48_000
        const val LOWPASS_HZ = 120.0
        const val EPSILON = 1.0e-12
    }
}
