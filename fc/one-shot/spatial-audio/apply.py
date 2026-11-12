from pathlib import Path

ROOT = Path.cwd()

def replace_once(path_text: str, old: str, new: str) -> None:
    path = ROOT / path_text
    source = path.read_text(encoding="utf-8")
    count = source.count(old)
    if count != 1:
        raise SystemExit(f"Expected one patch anchor in {path_text}, found {count}")
    path.write_text(source.replace(old, new, 1), encoding="utf-8")

def insert_after(path_text: str, anchor: str, addition: str) -> None:
    replace_once(path_text, anchor, anchor + addition)

# AppSettings: persistent per-track choices and route-safety preference.
APP_SETTINGS = "app/src/main/java/com/music/zenith/data/settings/AppSettings.kt"
insert_after(
    APP_SETTINGS,
    "import com.music.zenith.playback.EqualizerPreset\n",
    "import com.music.zenith.playback.SpatialAudioExample\n"
    "import com.music.zenith.playback.SpatialAudioPreference\n"
    "import com.music.zenith.playback.SpatialAudioProfile\n",
)
insert_after(
    APP_SETTINGS,
    "    val spatialAudio = MutableStateFlow(false)\n",
    "    /** Bypass Zenith's stereo widener on the built-in phone loudspeaker. */\n"
    "    val spatialAudioOffOnSpeaker = MutableStateFlow(true)\n"
    "    /** Re-evaluate the live decision after an explicit per-track choice. */\n"
    "    val spatialAudioLearningRevision = MutableStateFlow(0)\n"
    "    /** Actual effective state for the current Now Playing track. */\n"
    "    val spatialAudioEffective = MutableStateFlow(false)\n",
)
insert_after(
    APP_SETTINGS,
    "        spatialAudio.value = prefs.getBoolean(KEY_SPATIAL_AUDIO, false)\n",
    "        spatialAudioOffOnSpeaker.value = prefs.getBoolean(KEY_SPATIAL_AUDIO_OFF_ON_SPEAKER, true)\n",
)
insert_after(
    APP_SETTINGS,
    "    fun setSpatialAudio(value: Boolean) {\n"
    "        spatialAudio.value = value\n"
    "        prefs.edit().putBoolean(KEY_SPATIAL_AUDIO, value).apply()\n"
    "    }\n",
    "\n"
    "    fun setSpatialAudioOffOnSpeaker(value: Boolean) {\n"
    "        spatialAudioOffOnSpeaker.value = value\n"
    "        prefs.edit().putBoolean(KEY_SPATIAL_AUDIO_OFF_ON_SPEAKER, value).apply()\n"
    "    }\n"
    "\n"
    "    fun spatialAudioPreference(trackId: String?): SpatialAudioPreference {\n"
    "        val id = trackId?.takeIf { it.isNotBlank() } ?: return SpatialAudioPreference.AUTO\n"
    "        val record = runCatching {\n"
    "            org.json.JSONObject(prefs.getString(KEY_SPATIAL_AUDIO_LEARNING, null) ?: \"{}\")\n"
    "                .optJSONObject(id)\n"
    "        }.getOrNull()\n"
    "        return runCatching {\n"
    "            SpatialAudioPreference.valueOf(record?.optString(\"override\", \"AUTO\") ?: \"AUTO\")\n"
    "        }.getOrDefault(SpatialAudioPreference.AUTO)\n"
    "    }\n"
    "\n"
    "    fun spatialAudioProfile(trackId: String?): SpatialAudioProfile? {\n"
    "        val id = trackId?.takeIf { it.isNotBlank() } ?: return null\n"
    "        val record = runCatching {\n"
    "            org.json.JSONObject(prefs.getString(KEY_SPATIAL_AUDIO_LEARNING, null) ?: \"{}\")\n"
    "                .optJSONObject(id)\n"
    "        }.getOrNull() ?: return null\n"
    "        return spatialAudioProfileFrom(record)\n"
    "    }\n"
    "\n"
    "    fun spatialAudioExamples(): List<SpatialAudioExample> {\n"
    "        val root = readSpatialAudioLearning()\n"
    "        val examples = ArrayList<SpatialAudioExample>()\n"
    "        val keys = root.keys()\n"
    "        while (keys.hasNext()) {\n"
    "            val id = keys.next()\n"
    "            val record = root.optJSONObject(id) ?: continue\n"
    "            val preference = when (record.optString(\"feedback\", \"\")) {\n"
    "                \"ON\" -> true\n"
    "                \"OFF\" -> false\n"
    "                else -> null\n"
    "            } ?: continue\n"
    "            val profile = spatialAudioProfileFrom(record) ?: continue\n"
    "            examples += SpatialAudioExample(\n"
    "                trackId = id,\n"
    "                artist = record.optString(\"artist\", \"\"),\n"
    "                preference = preference,\n"
    "                profile = profile,\n"
    "            )\n"
    "        }\n"
    "        return examples\n"
    "    }\n"
    "\n"
    "    /**\n"
    "     * The player menu cycles Auto -> On -> Off -> Auto for this track.\n"
    "     * Explicit choices are retained as private on-device training examples\n"
    "     * even after the listener returns the track to Auto.\n"
    "     */\n"
    "    fun setSpatialAudioPreference(trackId: String, artist: String, preference: SpatialAudioPreference) {\n"
    "        if (trackId.isBlank()) return\n"
    "        val root = readSpatialAudioLearning()\n"
    "        val record = root.optJSONObject(trackId) ?: org.json.JSONObject()\n"
    "        record.put(\"artist\", artist)\n"
    "        if (preference == SpatialAudioPreference.AUTO) {\n"
    "            record.remove(\"override\")\n"
    "        } else {\n"
    "            record.put(\"override\", preference.name)\n"
    "            record.put(\"feedback\", preference.name)\n"
    "        }\n"
    "        record.put(\"updatedAt\", System.currentTimeMillis())\n"
    "        root.put(trackId, record)\n"
    "        writeSpatialAudioLearning(root)\n"
    "        spatialAudioLearningRevision.value += 1\n"
    "    }\n"
    "\n"
    "    /** Persist a profile for the next play; it never changes the current track mid-song. */\n"
    "    fun setSpatialAudioProfile(trackId: String, artist: String, profile: SpatialAudioProfile) {\n"
    "        if (trackId.isBlank()) return\n"
    "        val root = readSpatialAudioLearning()\n"
    "        val record = root.optJSONObject(trackId) ?: org.json.JSONObject()\n"
    "        record.put(\"artist\", artist)\n"
    "        record.put(\"width\", profile.stereoWidth.toDouble())\n"
    "        record.put(\"correlation\", profile.channelCorrelation.toDouble())\n"
    "        record.put(\"lowSide\", profile.lowFrequencySideRatio.toDouble())\n"
    "        record.put(\"highFrequency\", profile.highFrequencyRatio.toDouble())\n"
    "        record.put(\"crestFactor\", profile.crestFactor.toDouble())\n"
    "        record.put(\"samples\", profile.sampleCount)\n"
    "        record.put(\"rms\", profile.rms.toDouble())\n"
    "        record.put(\"updatedAt\", System.currentTimeMillis())\n"
    "        root.put(trackId, record)\n"
    "        writeSpatialAudioLearning(root)\n"
    "    }\n"
    "\n"
    "    private fun readSpatialAudioLearning(): org.json.JSONObject = runCatching {\n"
    "        org.json.JSONObject(prefs.getString(KEY_SPATIAL_AUDIO_LEARNING, null) ?: \"{}\")\n"
    "    }.getOrElse { org.json.JSONObject() }\n"
    "\n"
    "    private fun spatialAudioProfileFrom(record: org.json.JSONObject): SpatialAudioProfile? {\n"
    "        val width = record.optDouble(\"width\", Double.NaN)\n"
    "        val correlation = record.optDouble(\"correlation\", Double.NaN)\n"
    "        val lowSide = record.optDouble(\"lowSide\", Double.NaN)\n"
    "        val highFrequency = record.optDouble(\"highFrequency\", Double.NaN)\n"
    "        val crestFactor = record.optDouble(\"crestFactor\", Double.NaN)\n"
    "        val rms = record.optDouble(\"rms\", Double.NaN)\n"
    "        val samples = record.optLong(\"samples\", 0L)\n"
    "        if (!width.isFinite() || !correlation.isFinite() || !lowSide.isFinite() ||\n"
    "            !highFrequency.isFinite() || !crestFactor.isFinite() ||\n"
    "            !rms.isFinite() || samples < 1L\n"
    "        ) return null\n"
    "        return SpatialAudioProfile(\n"
    "            stereoWidth = width.toFloat(),\n"
    "            channelCorrelation = correlation.toFloat(),\n"
    "            lowFrequencySideRatio = lowSide.toFloat(),\n"
    "            highFrequencyRatio = highFrequency.toFloat(),\n"
    "            crestFactor = crestFactor.toFloat(),\n"
    "            sampleCount = samples,\n"
    "            rms = rms.toFloat(),\n"
    "        )\n"
    "    }\n"
    "\n"
    "    private fun writeSpatialAudioLearning(root: org.json.JSONObject) {\n"
    "        val keys = mutableListOf<String>()\n"
    "        val iterator = root.keys()\n"
    "        while (iterator.hasNext()) keys += iterator.next()\n"
    "        val excess = keys.size - MAX_SPATIAL_AUDIO_RECORDS\n"
    "        if (excess > 0) {\n"
    "            keys.map { id -> id to (root.optJSONObject(id)?.optLong(\"updatedAt\", 0L) ?: 0L) }\n"
    "                .sortedBy { it.second }\n"
    "                .take(excess)\n"
    "                .forEach { (id, _) -> root.remove(id) }\n"
    "        }\n"
    "        prefs.edit().putString(KEY_SPATIAL_AUDIO_LEARNING, root.toString()).apply()\n"
    "    }\n",
)
insert_after(
    APP_SETTINGS,
    '    private const val KEY_SPATIAL_AUDIO = "spatial_audio"\n',
    '    private const val KEY_SPATIAL_AUDIO_OFF_ON_SPEAKER = "spatial_audio_off_on_speaker"\n'
    '    private const val KEY_SPATIAL_AUDIO_LEARNING = "spatial_audio_learning_v1"\n'
    "    private const val MAX_SPATIAL_AUDIO_RECORDS = 400\n",
)

# One compact, private model: acoustic-neighbour learning and an artist prior.
ENGINE = ROOT / "app/src/main/java/com/music/zenith/playback/SpatialAudioDecisionEngine.kt"
ENGINE.parent.mkdir(parents=True, exist_ok=True)
ENGINE.write_text(
    """package com.music.zenith.playback

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt

enum class SpatialAudioPreference { AUTO, ON, OFF }

data class SpatialAudioProfile(
    val stereoWidth: Float,
    val channelCorrelation: Float,
    val lowFrequencySideRatio: Float,
    val highFrequencyRatio: Float,
    val crestFactor: Float,
    val sampleCount: Long,
    val rms: Float,
)

data class SpatialAudioExample(
    val trackId: String,
    val artist: String,
    val preference: Boolean,
    val profile: SpatialAudioProfile,
)

/**
 * Local, evidence-gated personalisation for Zenith's stereo widener.
 *
 * It does not pretend to have a crowd dataset. Explicit choices on this device
 * label measured stereo profiles; Auto uses a high-confidence artist prior or
 * nearest acoustic neighbours. Cold-start and ambiguous cases stay dry.
 */
object SpatialAudioDecisionEngine {
    fun recommendsOn(
        profile: SpatialAudioProfile?,
        artist: String?,
        examples: List<SpatialAudioExample>,
    ): Boolean {
        if (profile == null || profile.sampleCount < MIN_PROFILE_SAMPLES || profile.rms < MIN_RMS) {
            return false
        }
        val labelled = examples
            .asSequence()
            .filter { it.profile.sampleCount >= MIN_PROFILE_SAMPLES && it.profile.rms >= MIN_RMS }
            .distinctBy { it.trackId }
            .toList()
        if (labelled.isEmpty()) return false

        val normalizedArtist = artist?.trim()?.lowercase().orEmpty()
        // Artist identity is only a weak tie-breaker; the measured mix profile
        // remains the dominant similarity signal across styles and albums.
        val neighbours = labelled
            .map { example ->
                val baseDistance = distance(profile, example.profile)
                val sameArtist = normalizedArtist.isNotEmpty() &&
                    example.artist.trim().lowercase() == normalizedArtist
                example to (baseDistance * if (sameArtist) 0.90 else 1.0)
            }
            .filter { it.second <= MAX_NEIGHBOUR_DISTANCE }
            .sortedBy { it.second }
            .take(NEIGHBOUR_COUNT)
        if (neighbours.size < MIN_NEIGHBOURS) return false

        val onWeight = neighbours.filter { it.first.preference }
            .sumOf { 1.0 / (WEIGHT_FLOOR + it.second * it.second) }
        val totalWeight = neighbours.sumOf { 1.0 / (WEIGHT_FLOOR + it.second * it.second) }
        return totalWeight > 0.0 && onWeight / totalWeight >= NEIGHBOUR_CONFIDENCE
    }

    private fun distance(a: SpatialAudioProfile, b: SpatialAudioProfile): Double {
        // Log-width distances treat a change from narrow to moderate as more
        // meaningful than the same absolute change between already-wide mixes.
        val width = (ln(1.0 + a.stereoWidth.coerceAtLeast(0f)) -
            ln(1.0 + b.stereoWidth.coerceAtLeast(0f))) / WIDTH_SCALE
        val correlation = (a.channelCorrelation - b.channelCorrelation) / CORRELATION_SCALE
        val lowSide = (ln(1.0 + a.lowFrequencySideRatio.coerceAtLeast(0f)) -
            ln(1.0 + b.lowFrequencySideRatio.coerceAtLeast(0f))) / LOW_SIDE_SCALE
        val highFrequency = (a.highFrequencyRatio - b.highFrequencyRatio) / HIGH_FREQUENCY_SCALE
        val crestFactor = (a.crestFactor - b.crestFactor) / CREST_FACTOR_SCALE
        return sqrt(width * width + correlation * correlation + lowSide * lowSide +
            highFrequency * highFrequency + crestFactor * crestFactor)
    }

    private const val MIN_PROFILE_SAMPLES = 48_000L * 4L
    private const val MIN_RMS = 0.001f
    private const val MIN_NEIGHBOURS = 3
    private const val NEIGHBOUR_COUNT = 7
    private const val MAX_NEIGHBOUR_DISTANCE = 2.4
    private const val NEIGHBOUR_CONFIDENCE = 0.75
    private const val WEIGHT_FLOOR = 0.20
    private const val WIDTH_SCALE = 0.45
    private const val CORRELATION_SCALE = 0.45
    private const val LOW_SIDE_SCALE = 0.35
    private const val HIGH_FREQUENCY_SCALE = 0.30
    private const val CREST_FACTOR_SCALE = 3.0
}
""",
    encoding="utf-8",
)

# Measure the original stereo signal, whether the effect is currently on or off.
PROCESSOR = "app/src/main/java/com/music/zenith/playback/SpatialAudioProcessor.kt"
insert_after(
    PROCESSOR,
    "import java.nio.ByteOrder\n",
    "import kotlin.math.sqrt\n",
)
insert_after(
    PROCESSOR,
    "    var enabled: Boolean = false\n",
    "\n"
    "    @Volatile\n"
    "    var currentTrackId: String? = null\n"
    "        private set\n"
    "\n"
    "    @Volatile\n"
    "    var currentArtist: String = \"\"\n"
    "        private set\n"
    "\n"
    "    @Volatile\n"
    "    var onProfileReady: ((String, String, SpatialAudioProfile) -> Unit)? = null\n"
    "\n"
    "    private class ProfileAccumulator(\n"
    "        val trackId: String,\n"
    "        val artist: String,\n"
    "        var targetFrames: Long,\n"
    "    ) {\n"
    "        var frames = 0L\n"
    "        var leftSquared = 0.0\n"
    "        var rightSquared = 0.0\n"
    "        var cross = 0.0\n"
    "        var midSquared = 0.0\n"
    "        var sideSquared = 0.0\n"
    "        var lowMidSquared = 0.0\n"
    "        var lowSideSquared = 0.0\n"
    "        var highFrequencySquared = 0.0\n"
    "        var totalEnergy = 0.0\n"
    "        var peakAmplitude = 0.0\n"
    "        var lowLeft = 0.0\n"
    "        var lowRight = 0.0\n"
    "        var completed = false\n"
    "    }\n"
    "\n"
    "    @Volatile\n"
    "    private var profileAccumulator: ProfileAccumulator? = null\n"
    "\n"
    "    /** Starts a clean profile for this player's own incoming track. */\n"
    "    fun beginTrack(trackId: String?, artist: String?) {\n"
    "        val id = trackId?.takeIf { it.isNotBlank() }\n"
    "        currentTrackId = id\n"
    "        currentArtist = artist.orEmpty()\n"
    "        val rate = sampleRate.takeIf { it > 0 } ?: 48_000\n"
    "        profileAccumulator = id?.let { ProfileAccumulator(it, currentArtist, rate.toLong() * PROFILE_SECONDS) }\n"
    "    }\n",
)
replace_once(
    PROCESSOR,
    "    fun process(block: AudioBlock) {\n"
    "        if (!enabled || block.frameCount == 0) return\n"
    "        if (block.channelCount != 2) return // Spatial widening only applies to stereo\n",
    "    fun process(block: AudioBlock) {\n"
    "        if (block.frameCount == 0 || block.channelCount != 2) return\n"
    "        observeFloatSamples(block.samples, block.frameCount)\n"
    "        if (!enabled) return\n",
)
insert_after(
    PROCESSOR,
    "    override fun queueInput(inputBuffer: java.nio.ByteBuffer) {\n"
    "        val frameCount = inputBuffer.remaining() / BYTES_PER_FRAME\n"
    "        if (frameCount == 0) return\n",
    "        observePcm16Samples(inputBuffer)\n",
)
insert_after(
    PROCESSOR,
    "    override fun onReset() {\n"
    "        delayLeft = FloatArray(0)\n"
    "        delayRight = FloatArray(0)\n"
    "        delayIndex = 0\n"
    "        lowpassLeft = 0f\n"
    "        lowpassRight = 0f\n"
    "        channelCount = 0\n"
    "        sampleRate = 0\n"
    "    }\n",
    "\n"
    "    private fun observeFloatSamples(samples: FloatArray, frameCount: Int) {\n"
    "        val accumulator = profileAccumulator ?: return\n"
    "        if (accumulator.completed) return\n"
    "        val available = minOf(frameCount, samples.size / 2)\n"
    "        val count = minOf(available.toLong(), accumulator.targetFrames - accumulator.frames).toInt()\n"
    "        var index = 0\n"
    "        repeat(count) {\n"
    "            observeFrame(accumulator, samples[index].toDouble(), samples[index + 1].toDouble())\n"
    "            index += 2\n"
    "        }\n"
    "        finishProfile(accumulator)\n"
    "    }\n"
    "\n"
    "    private fun observePcm16Samples(input: java.nio.ByteBuffer) {\n"
    "        val accumulator = profileAccumulator ?: return\n"
    "        if (accumulator.completed) return\n"
    "        val view = input.duplicate().order(ByteOrder.nativeOrder())\n"
    "        val available = view.remaining() / BYTES_PER_FRAME\n"
    "        val count = minOf(available.toLong(), accumulator.targetFrames - accumulator.frames).toInt()\n"
    "        val scale = 1.0 / 32768.0\n"
    "        repeat(count) {\n"
    "            observeFrame(accumulator, view.short.toDouble() * scale, view.short.toDouble() * scale)\n"
    "        }\n"
    "        finishProfile(accumulator)\n"
    "    }\n"
    "\n"
    "    private fun observeFrame(acc: ProfileAccumulator, left: Double, right: Double) {\n"
    "        val mid = (left + right) * 0.5\n"
    "        val side = (left - right) * 0.5\n"
    "        acc.leftSquared += left * left\n"
    "        acc.rightSquared += right * right\n"
    "        acc.cross += left * right\n"
    "        acc.midSquared += mid * mid\n"
    "        acc.sideSquared += side * side\n"
    "        // A light one-pole low-pass around the bass region, used only for\n"
    "        // the profile. The playback samples themselves are never altered.\n"
    "        acc.lowLeft += 0.02 * (left - acc.lowLeft)\n"
    "        acc.lowRight += 0.02 * (right - acc.lowRight)\n"
    "        val lowMid = (acc.lowLeft + acc.lowRight) * 0.5\n"
    "        val lowSide = (acc.lowLeft - acc.lowRight) * 0.5\n"
    "        acc.lowMidSquared += lowMid * lowMid\n"
    "        acc.lowSideSquared += lowSide * lowSide\n"
    "        val highLeft = left - acc.lowLeft\n"
    "        val highRight = right - acc.lowRight\n"
    "        acc.highFrequencySquared += (highLeft * highLeft + highRight * highRight) * 0.5\n"
    "        acc.totalEnergy += (left * left + right * right) * 0.5\n"
    "        acc.peakAmplitude = maxOf(acc.peakAmplitude, kotlin.math.abs(left), kotlin.math.abs(right))\n"
    "        acc.frames++\n"
    "    }\n"
    "\n"
    "    private fun finishProfile(acc: ProfileAccumulator) {\n"
    "        if (acc.completed || acc.frames < acc.targetFrames) return\n"
    "        acc.completed = true\n"
    "        if (profileAccumulator === acc) profileAccumulator = null\n"
    "        val frames = acc.frames.toDouble()\n"
    "        val rms = sqrt((acc.leftSquared + acc.rightSquared) / (2.0 * frames))\n"
    "        if (rms < 0.001) return\n"
    "        val width = sqrt(acc.sideSquared / (acc.midSquared + 1.0e-12))\n"
    "        val correlation = (acc.cross / sqrt(acc.leftSquared * acc.rightSquared + 1.0e-12))\n"
    "            .coerceIn(-1.0, 1.0)\n"
    "        val lowSide = sqrt(acc.lowSideSquared / (acc.lowMidSquared + 1.0e-12))\n"
    "        val highFrequency = (acc.highFrequencySquared / (acc.totalEnergy + 1.0e-12)).coerceIn(0.0, 1.0)\n"
    "        val crestFactor = acc.peakAmplitude / rms.coerceAtLeast(1.0e-9)\n"
    "        onProfileReady?.invoke(\n"
    "            acc.trackId,\n"
    "            acc.artist,\n"
    "            SpatialAudioProfile(\n"
    "                stereoWidth = width.toFloat(),\n"
    "                channelCorrelation = correlation.toFloat(),\n"
    "                lowFrequencySideRatio = lowSide.toFloat(),\n"
    "                highFrequencyRatio = highFrequency.toFloat(),\n"
    "                crestFactor = crestFactor.toFloat(),\n"
    "                sampleCount = acc.frames,\n"
    "                rms = rms.toFloat(),\n"
    "            ),\n"
    "        )\n"
    "    }\n",
)
insert_after(
    PROCESSOR,
    "        const val DELAY_MS = 15\n",
    "        const val PROFILE_SECONDS = 8L\n",
)

replace_once(
    PROCESSOR,
    "        this.sampleRate = sampleRate\n        this.channelCount = channelCount\n",
    "        this.sampleRate = sampleRate\n        this.channelCount = channelCount\n"
    "        profileAccumulator?.takeIf { it.frames == 0L }?.targetFrames = sampleRate.toLong() * PROFILE_SECONDS\n",
)

# The processor belongs to its ExoPlayer, so crossfade standby tracks get their
# own profile and decision instead of inheriting the outgoing track's setting.
PLAYBACK = "app/src/main/java/com/music/zenith/playback/PlaybackService.kt"
replace_once(
    PLAYBACK,
    "    ): ExoPlayer = ExoPlayer.Builder(this)\n"
    "        .setRenderersFactory(silenceSkippingRenderers(spatial, equalizer, filter, loudness))\n"
    "        .setMediaSourceFactory(requireNotNull(mediaSourceFactory))\n"
    "        .setLoadControl(farBufferingLoadControl())\n"
    "        .setAudioAttributes(AUDIO_ATTRIBUTES, /* handleAudioFocus = */ ownsSession)\n"
    "        .setHandleAudioBecomingNoisy(ownsSession)\n"
    "        // Back restarts the track once you're this far into it; only a\n"
    "        // press before that steps to the previous one.\n"
    "        .setMaxSeekToPreviousPositionMs(BACK_RESTARTS_AFTER_MS)\n"
    "        .build()\n",
    "    ): ExoPlayer {\n"
    "        spatial.onProfileReady = { trackId, artist, profile ->\n"
    "            android.os.Handler(android.os.Looper.getMainLooper()).post {\n"
    "                AppSettings.setSpatialAudioProfile(trackId, artist, profile)\n"
    "            }\n"
    "        }\n"
    "        val built = ExoPlayer.Builder(this)\n"
    "            .setRenderersFactory(silenceSkippingRenderers(spatial, equalizer, filter, loudness))\n"
    "            .setMediaSourceFactory(requireNotNull(mediaSourceFactory))\n"
    "            .setLoadControl(farBufferingLoadControl())\n"
    "            .setAudioAttributes(AUDIO_ATTRIBUTES, /* handleAudioFocus = */ ownsSession)\n"
    "            .setHandleAudioBecomingNoisy(ownsSession)\n"
    "            .setMaxSeekToPreviousPositionMs(BACK_RESTARTS_AFTER_MS)\n"
    "            .build()\n"
    "        built.addListener(object : Player.Listener {\n"
    "            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {\n"
    "                spatial.beginTrack(mediaItem?.mediaId, mediaItem?.toSong()?.artist)\n"
    "                applySpatialAudioEnabled()\n"
    "            }\n"
    "        })\n"
    "        return built\n"
    "    }\n",
)
replace_once(
    PLAYBACK,
    "        scope.launch {\n"
    "            AppSettings.spatialAudio.collect { applySpatialAudioEnabled() }\n"
    "        }\n",
    "        scope.launch {\n"
    "            combine(\n"
    "                AppSettings.spatialAudio,\n"
    "                AppSettings.spatialAudioOffOnSpeaker,\n"
    "                AppSettings.spatialAudioLearningRevision,\n"
    "                AudioOutputStatus.current.map { it.routeKind == AudioRouting.Kind.PHONE }.distinctUntilChanged(),\n"
    "            ) { _, _, _, _ -> Unit }.collect { applySpatialAudioEnabled() }\n"
    "        }\n",
)
replace_once(
    PLAYBACK,
    "    /**\n"
    "     * The effective spatial-audio state: the user's toggle, unless the\n"
    "     * active player's decoder is currently on a Dolby Atmos (E-AC-3 JOC)\n"
    "     * stream, in which case it's forced off. [activeTrackIsDolbyAtmos] only\n"
    "     * ever describes the active player — see [formatListener] — so both\n"
    "     * processors are kept in lockstep rather than tracking a role swap.\n"
    "     */\n"
    "    private fun applySpatialAudioEnabled() {\n"
    "        val enabled = AppSettings.spatialAudio.value && !activeTrackIsDolbyAtmos\n"
    "        spatialAudioProcessorA.enabled = enabled\n"
    "        spatialAudioProcessorB.enabled = enabled\n"
    "    }\n",
    "    /**\n"
    "     * Resolve each player's own track independently. This matters during a\n"
    "     * crossfade: the incoming and outgoing processors must not inherit one\n"
    "     * another's learned preference. Route and Atmos are hard safety gates;\n"
    "     * an explicit per-track choice then wins over Auto.\n"
    "     */\n"
    "    private fun applySpatialAudioEnabled() {\n"
    "        val masterEnabled = AppSettings.spatialAudio.value\n"
    "        val phoneSpeaker = AppSettings.spatialAudioOffOnSpeaker.value &&\n"
    "            AudioOutputStatus.current.value.routeKind == AudioRouting.Kind.PHONE\n"
    "        val examples = if (masterEnabled && !phoneSpeaker) AppSettings.spatialAudioExamples() else emptyList()\n"
    "\n"
    "        fun enabledFor(processor: SpatialAudioProcessor): Boolean {\n"
    "            val trackId = processor.currentTrackId ?: return false\n"
    "            if (!masterEnabled || phoneSpeaker) return false\n"
    "            val atmosDeclared = NerdStats.declaredFormat(trackId)?.isDolbyAtmos == true\n"
    "            val activeAtmos = trackId == player?.currentMediaItem?.mediaId && activeTrackIsDolbyAtmos\n"
    "            if (atmosDeclared || activeAtmos) return false\n"
    "            return when (AppSettings.spatialAudioPreference(trackId)) {\n"
    "                SpatialAudioPreference.ON -> true\n"
    "                SpatialAudioPreference.OFF -> false\n"
    "                SpatialAudioPreference.AUTO -> SpatialAudioDecisionEngine.recommendsOn(\n"
    "                    profile = AppSettings.spatialAudioProfile(trackId),\n"
    "                    artist = processor.currentArtist,\n"
    "                    examples = examples,\n"
    "                )\n"
    "            }\n"
    "        }\n"
    "\n"
    "        spatialAudioProcessorA.enabled = enabledFor(spatialAudioProcessorA)\n"
    "        spatialAudioProcessorB.enabled = enabledFor(spatialAudioProcessorB)\n"
    "        val activeId = player?.currentMediaItem?.mediaId\n"
    "        val activeProcessor = when (activeId) {\n"
    "            spatialAudioProcessorA.currentTrackId -> spatialAudioProcessorA\n"
    "            spatialAudioProcessorB.currentTrackId -> spatialAudioProcessorB\n"
    "            else -> null\n"
    "        }\n"
    "        AppSettings.spatialAudioEffective.value = activeProcessor?.enabled == true\n"
    "    }\n",
)

# Only the Now Playing menu exposes this track-scoped three-state control.
MENU = "app/src/main/java/com/music/zenith/ui/components/SongActionsMenu.kt"
insert_after(
    MENU,
    "import com.music.zenith.playback.SleepTimer\n",
    "import com.music.zenith.playback.SpatialAudioPreference\n",
)
insert_after(
    MENU,
    "    val isOffline = song.localUri != null\n",
    "\n"
    "    var spatialPreference by remember(song.videoId) {\n"
    "        mutableStateOf(AppSettings.spatialAudioPreference(song.videoId))\n"
    "    }\n"
    "    val spatialAudioEffective by AppSettings.spatialAudioEffective.collectAsStateWithLifecycle()\n"
    "    val spatialAudioMaster by AppSettings.spatialAudio.collectAsStateWithLifecycle()\n",
)
insert_after(
    MENU,
    "            if (showSleepTimer) {\n"
    "                ActionRow(\n"
    "                    icon = SfIcons.Performance,\n"
    "                    label = stringResource(R.string.playback_speed_tempo),\n"
    "                    value = speedTempoStatus(),\n"
    "                    accent = palette.accent,\n"
    "                ) { pickingSpeed = true }\n"
    "            }\n",
    "            if (showSleepTimer) {\n"
    "                ActionRow(\n"
    "                    icon = SfIcons.SurroundSound,\n"
    "                    label = stringResource(R.string.spatial_audio),\n"
    "                    value = when (spatialPreference) {\n"
    "                        SpatialAudioPreference.AUTO -> stringResource(\n"
    "                            if (spatialAudioMaster && spatialAudioEffective) R.string.spatial_audio_auto_on\n"
    "                            else R.string.spatial_audio_auto_off,\n"
    "                        )\n"
    "                        SpatialAudioPreference.ON -> stringResource(\n"
    "                            if (spatialAudioEffective) R.string.spatial_audio_manual_on else R.string.spatial_audio_paused,\n"
    "                        )\n"
    "                        SpatialAudioPreference.OFF -> stringResource(R.string.spatial_audio_manual_off)\n"
    "                    },\n"
    "                    accent = palette.accent,\n"
    "                    description = stringResource(R.string.spatial_audio_preference_hint),\n"
    "                ) {\n"
    "                    val next = when (spatialPreference) {\n"
    "                        SpatialAudioPreference.AUTO -> SpatialAudioPreference.ON\n"
    "                        SpatialAudioPreference.ON -> SpatialAudioPreference.OFF\n"
    "                        SpatialAudioPreference.OFF -> SpatialAudioPreference.AUTO\n"
    "                    }\n"
    "                    AppSettings.setSpatialAudioPreference(song.videoId, song.artist, next)\n"
    "                    spatialPreference = next\n"
    "                    if (next == SpatialAudioPreference.ON && !spatialAudioMaster) {\n"
    "                        AppSettings.setSpatialAudio(true)\n"
    "                    }\n"
    "                }\n"
    "            }\n",
)

# The phone-speaker safety switch sits with the existing Spatial Audio setting.
SETTINGS = "app/src/main/java/com/music/zenith/ui/screens/SettingsSheet.kt"
insert_after(
    SETTINGS,
    "    val spatialAudio by AppSettings.spatialAudio.collectAsStateWithLifecycle()\n",
    "    val spatialAudioOffOnSpeaker by AppSettings.spatialAudioOffOnSpeaker.collectAsStateWithLifecycle()\n",
)
insert_after(
    SETTINGS,
    "            }\n"
    "            // The system panel is not listed here as well. A device with a\n"
    "            // Dolby or Dirac panel has something Zenith cannot reproduce and\n",
    "            val spatialSpeakerTitle = stringResource(R.string.spatial_audio_off_on_speaker)\n"
    "            row(spatialSpeakerTitle, \"spatial\", \"phone\", \"speaker\") {\n"
    "                BypassedByBitPerfect(bitPerfectReady) {\n"
    "                    SettingsSubRow(\n"
    "                        title = spatialSpeakerTitle,\n"
    "                        checked = spatialAudioOffOnSpeaker,\n"
    "                        onCheckedChange = AppSettings::setSpatialAudioOffOnSpeaker,\n"
    "                    )\n"
    "                }\n"
    "            }\n",
)

# Localised fallback strings; translations without these keys fall back to values.
STRINGS = "app/src/main/res/values/strings.xml"
insert_after(
    STRINGS,
    '    <string name="spatial_audio_atmos_subtitle">Paused for this track — Dolby Atmos is already spatial</string>\n',
    '    <string name="spatial_audio_auto_on">Auto · On</string>\n'
    '    <string name="spatial_audio_auto_off">Auto · Off</string>\n'
    '    <string name="spatial_audio_manual_on">On</string>\n'
    '    <string name="spatial_audio_manual_off">Off</string>\n'
    '    <string name="spatial_audio_paused">On · paused by route or format</string>\n'
    '    <string name="spatial_audio_preference_hint">Tap to cycle Auto, On and Off for this track. Auto learns from your choices and similar stereo mixes.</string>\n'
    '    <string name="spatial_audio_off_on_speaker">Disable on Phone Speaker</string>\n',
)


TEST = ROOT / "app/src/test/java/com/music/zenith/playback/SpatialAudioDecisionEngineTest.kt"
TEST.parent.mkdir(parents=True, exist_ok=True)
TEST.write_text("package com.music.zenith.playback\n\nimport org.junit.Assert.assertFalse\nimport org.junit.Assert.assertTrue\nimport org.junit.Test\n\nclass SpatialAudioDecisionEngineTest {\n    private val profile = SpatialAudioProfile(\n        stereoWidth = 0.35f,\n        channelCorrelation = 0.72f,\n        lowFrequencySideRatio = 0.08f,\n        highFrequencyRatio = 0.42f,\n        crestFactor = 5.2f,\n        sampleCount = 384_000L,\n        rms = 0.15f,\n    )\n\n    private fun example(id: String, preference: Boolean, profile: SpatialAudioProfile = this.profile) =\n        SpatialAudioExample(id, \"Artist\", preference, profile)\n\n    @Test\n    fun coldStartStaysNeutralWithoutListenerFeedback() {\n        assertFalse(SpatialAudioDecisionEngine.recommendsOn(profile, \"Artist\", emptyList()))\n    }\n\n    @Test\n    fun similarMixesFollowStrongPositiveFeedback() {\n        val examples = listOf(example(\"a\", true), example(\"b\", true), example(\"c\", true))\n        assertTrue(SpatialAudioDecisionEngine.recommendsOn(profile, \"Artist\", examples))\n    }\n\n    @Test\n    fun similarMixesDoNotTurnOnForMixedOrNegativeFeedback() {\n        val examples = listOf(example(\"a\", true), example(\"b\", true), example(\"c\", false))\n        assertFalse(SpatialAudioDecisionEngine.recommendsOn(profile, \"Artist\", examples))\n        assertFalse(\n            SpatialAudioDecisionEngine.recommendsOn(\n                profile,\n                \"Artist\",\n                listOf(example(\"d\", false), example(\"e\", false), example(\"f\", false)),\n            ),\n        )\n    }\n\n    @Test\n    fun artistNameAloneCannotOverrideAnAcousticallyDissimilarMix() {\n        val dissimilar = profile.copy(\n            stereoWidth = 4.0f,\n            channelCorrelation = -0.95f,\n            lowFrequencySideRatio = 3.0f,\n            highFrequencyRatio = 0.95f,\n            crestFactor = 18.0f,\n        )\n        val examples = listOf(\n            example(\"a\", true, dissimilar),\n            example(\"b\", true, dissimilar),\n            example(\"c\", true, dissimilar),\n        )\n        assertFalse(SpatialAudioDecisionEngine.recommendsOn(profile, \"Artist\", examples))\n    }\n\n    @Test\n    fun incompleteOrSilentProfilesAreNotUsed() {\n        assertFalse(\n            SpatialAudioDecisionEngine.recommendsOn(\n                profile.copy(sampleCount = 1_000L),\n                \"Artist\",\n                listOf(example(\"a\", true), example(\"b\", true), example(\"c\", true)),\n            ),\n        )\n        assertFalse(\n            SpatialAudioDecisionEngine.recommendsOn(\n                profile.copy(rms = 0.0001f),\n                \"Artist\",\n                listOf(example(\"a\", true), example(\"b\", true), example(\"c\", true)),\n            ),\n        )\n    }\n}", encoding="utf-8")

print("One-shot Spatial Audio overlay applied for the pinned FC release.")
