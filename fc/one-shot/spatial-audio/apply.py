from pathlib import Path
import copy
import xml.etree.ElementTree as ET

ROOT = Path.cwd()

def fill_missing_locale_keys(base_path_text: str, locale_path_text: str) -> None:
    """Mirror Android's English fallback for untranslated keys in this build only."""
    base_root = ET.parse(ROOT / base_path_text).getroot()
    locale_path = ROOT / locale_path_text
    locale_root = ET.parse(locale_path).getroot()
    supported_tags = {"string", "plurals", "string-array"}
    existing = {
        element.attrib.get("name")
        for element in list(locale_root)
        if element.tag in supported_tags
        and element.attrib.get("name")
        and element.attrib.get("translatable") != "false"
    }
    missing = [
        ET.tostring(copy.deepcopy(element), encoding="unicode")
        for element in list(base_root)
        if element.tag in supported_tags
        and element.attrib.get("name")
        and element.attrib.get("translatable") != "false"
        and element.attrib.get("name") not in existing
    ]
    if not missing:
        return
    original = locale_path.read_text(encoding="utf-8")
    closing = original.rfind("</resources>")
    if closing < 0:
        raise SystemExit(f"Missing resources closing tag in {locale_path_text}")
    addition = "\n    " + "\n    ".join(missing) + "\n"
    locale_path.write_text(original[:closing].rstrip() + addition + original[closing:], encoding="utf-8")
    ET.parse(locale_path)  # Fail immediately if the generated Android XML is malformed.
    print(f"Added {len(missing)} English fallback resource entries to {locale_path_text} for this build.")


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
    "    val spatialAudioByTrack = MutableStateFlow<Map<String, Boolean>>(emptyMap())\n",
    "\n"
    "    /** Refresh automatic decisions after the listener gives explicit feedback. */\n"
    "    val spatialAudioLearningRevision = MutableStateFlow(0)\n"
    "    /** Effective processor state for the active Now Playing track. */\n"
    "    val spatialAudioEffective = MutableStateFlow(false)\n",
)
insert_after(
    APP_SETTINGS,
    "    fun setSpatialAudio(value: Boolean) {\n"
    "        spatialAudio.value = value\n"
    "        prefs.edit().putBoolean(KEY_SPATIAL_AUDIO, value).apply()\n"
    "    }\n",
    "\n"
    "    /** Explicit per-track preference; old Boolean entries remain valid during migration. */\n"
    "    fun spatialAudioPreference(trackId: String?): SpatialAudioPreference {\n"
    "        val id = trackId?.takeIf { it.isNotBlank() } ?: return SpatialAudioPreference.AUTO\n"
    "        val record = runCatching { readSpatialAudioLearning().optJSONObject(id) }.getOrNull()\n"
    "        val saved = runCatching {\n"
    "            SpatialAudioPreference.valueOf(record?.optString(\"override\", \"AUTO\") ?: \"AUTO\")\n"
    "        }.getOrDefault(SpatialAudioPreference.AUTO)\n"
    "        if (saved != SpatialAudioPreference.AUTO) return saved\n"
    "        return when (spatialAudioByTrack.value[id]) {\n"
    "            true -> SpatialAudioPreference.FORCE_ON\n"
    "            false -> SpatialAudioPreference.FORCE_OFF\n"
    "            null -> SpatialAudioPreference.AUTO\n"
    "        }\n"
    "    }\n"
    "\n"
    "    fun spatialAudioProfile(trackId: String?): SpatialAudioProfile? {\n"
    "        val id = trackId?.takeIf { it.isNotBlank() } ?: return null\n"
    "        val record = runCatching { readSpatialAudioLearning().optJSONObject(id) }.getOrNull() ?: return null\n"
    "        return spatialAudioProfileFrom(record)\n"
    "    }\n"
    "\n"
    "    /** Only explicit listener choices become training labels. */\n"
    "    fun spatialAudioExamples(): List<SpatialAudioExample> {\n"
    "        val root = readSpatialAudioLearning()\n"
    "        val examples = ArrayList<SpatialAudioExample>()\n"
    "        val keys = root.keys()\n"
    "        while (keys.hasNext()) {\n"
    "            val id = keys.next()\n"
    "            val record = root.optJSONObject(id) ?: continue\n"
    "            val preference = when (record.optString(\"feedback\", \"\")) {\n"
    "                SpatialAudioPreference.FORCE_ON.name -> true\n"
    "                SpatialAudioPreference.FORCE_OFF.name -> false\n"
    "                else -> null\n"
    "            } ?: continue\n"
    "            val profile = spatialAudioProfileFrom(record) ?: continue\n"
    "            examples += SpatialAudioExample(id, record.optString(\"artist\", \"\"), preference, profile)\n"
    "        }\n"
    "        return examples\n"
    "    }\n"
    "\n"
    "    /** Auto -> On -> Off -> Auto; feedback survives returning a track to Auto. */\n"
    "    fun setSpatialAudioPreference(trackId: String, artist: String, preference: SpatialAudioPreference) {\n"
    "        if (trackId.isBlank()) return\n"
    "        val root = readSpatialAudioLearning()\n"
    "        val record = root.optJSONObject(trackId) ?: org.json.JSONObject()\n"
    "        val legacy = spatialAudioByTrack.value[trackId]\n"
    "        if (!record.has(\"feedback\") && legacy != null) {\n"
    "            record.put(\"feedback\", if (legacy) SpatialAudioPreference.FORCE_ON.name else SpatialAudioPreference.FORCE_OFF.name)\n"
    "        }\n"
    "        record.put(\"artist\", artist)\n"
    "        if (preference == SpatialAudioPreference.AUTO) {\n"
    "            record.remove(\"override\")\n"
    "        } else {\n"
    "            record.put(\"override\", preference.name)\n"
    "            record.put(\"feedback\", preference.name)\n"
    "        }\n"
    "        record.put(\"updatedAt\", System.currentTimeMillis())\n"
    "        root.put(trackId, record)\n"
    "\n"
    "        // Remove a migrated Boolean so it cannot override a later return to Auto.\n"
    "        val legacyMap = LinkedHashMap(spatialAudioByTrack.value)\n"
    "        if (legacyMap.remove(trackId) != null) {\n"
    "            spatialAudioByTrack.value = legacyMap\n"
    "            val legacyJson = org.json.JSONObject()\n"
    "            legacyMap.forEach { (id, enabled) -> legacyJson.put(id, enabled) }\n"
    "            prefs.edit().putString(KEY_SPATIAL_AUDIO_BY_TRACK, legacyJson.toString()).apply()\n"
    "        }\n"
    "        writeSpatialAudioLearning(root)\n"
    "        spatialAudioLearningRevision.value += 1\n"
    "    }\n"
    "\n"
    "    /** Store a measured profile after eight seconds; it does not change the playing track mid-song. */\n"
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
    "        if (!record.has(\"feedback\")) {\n"
    "            when (spatialAudioByTrack.value[trackId]) {\n"
    "                true -> record.put(\"feedback\", SpatialAudioPreference.FORCE_ON.name)\n"
    "                false -> record.put(\"feedback\", SpatialAudioPreference.FORCE_OFF.name)\n"
    "                null -> Unit\n"
    "            }\n"
    "        }\n"
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
    "        if (!width.isFinite() || width < 0.0 || !correlation.isFinite() || correlation !in -1.0..1.0 ||\n"
    "            !lowSide.isFinite() || lowSide < 0.0 || !highFrequency.isFinite() || highFrequency !in 0.0..1.0 ||\n"
    "            !crestFactor.isFinite() || crestFactor < 0.0 || !rms.isFinite() || rms < 0.001 ||\n"
    "            samples < 48_000L * 4L\n"
    "        ) return null\n"
    "        return SpatialAudioProfile(width.toFloat(), correlation.toFloat(), lowSide.toFloat(),\n"
    "            highFrequency.toFloat(), crestFactor.toFloat(), samples, rms.toFloat())\n"
    "    }\n"
    "\n"
    "    private fun writeSpatialAudioLearning(root: org.json.JSONObject) {\n"
    "        val ids = mutableListOf<String>()\n"
    "        val iterator = root.keys()\n"
    "        while (iterator.hasNext()) ids += iterator.next()\n"
    "        val excess = ids.size - MAX_SPATIAL_AUDIO_RECORDS\n"
    "        if (excess > 0) {\n"
    "            ids.map { id -> id to (root.optJSONObject(id)?.optLong(\"updatedAt\", 0L) ?: 0L) }\n"
    "                .sortedBy { it.second }.take(excess).forEach { (id, _) -> root.remove(id) }\n"
    "        }\n"
    "        prefs.edit().putString(KEY_SPATIAL_AUDIO_LEARNING, root.toString()).apply()\n"
    "    }\n",
)
insert_after(
    APP_SETTINGS,
    '    private const val KEY_SPATIAL_AUDIO_BY_TRACK = "spatial_audio_by_track"\n',
    '    private const val KEY_SPATIAL_AUDIO_LEARNING = "spatial_audio_learning_v1"\n'
    "    private const val MAX_SPATIAL_AUDIO_RECORDS = 400\n",
)

ENGINE = ROOT / "app/src/main/java/com/music/zenith/playback/SpatialAudioDecisionEngine.kt"
ENGINE.write_text(r'''package com.music.zenith.playback

import kotlin.math.ln
import kotlin.math.sqrt

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

private data class SpatialAudioRecommendation(
    val enabled: Boolean,
    val confidence: Float,
)

/**
 * Small, local and explainable personalisation. It uses only explicit choices
 * made on this device and measured stereo-mix features. It does not claim to
 * contain a population-trained model or any cloud-collected listener data.
 */
object SpatialAudioDecisionEngine {
    fun resolve(
        preference: SpatialAudioPreference,
        profile: SpatialAudioProfile?,
        artist: String?,
        examples: List<SpatialAudioExample>,
        bypassReason: SpatialAudioBypassReason? = null,
    ): SpatialAudioDecision {
        if (bypassReason != null) {
            return SpatialAudioDecision(
                enabled = false,
                source = SpatialAudioDecisionSource.SAFETY_BYPASS,
                bypassReason = bypassReason,
            )
        }
        return when (preference) {
            SpatialAudioPreference.FORCE_ON ->
                SpatialAudioDecision(true, SpatialAudioDecisionSource.MANUAL_TRACK)
            SpatialAudioPreference.FORCE_OFF ->
                SpatialAudioDecision(false, SpatialAudioDecisionSource.MANUAL_TRACK)
            SpatialAudioPreference.AUTO -> {
                val recommendation = recommendation(profile, artist, examples)
                SpatialAudioDecision(
                    enabled = recommendation.enabled,
                    source = SpatialAudioDecisionSource.AUTOMATIC,
                    confidence = recommendation.confidence,
                )
            }
        }
    }

    fun recommendsOn(
        profile: SpatialAudioProfile?,
        artist: String?,
        examples: List<SpatialAudioExample>,
    ): Boolean = recommendation(profile, artist, examples).enabled

    private fun recommendation(
        profile: SpatialAudioProfile?,
        artist: String?,
        examples: List<SpatialAudioExample>,
    ): SpatialAudioRecommendation {
        if (!usable(profile)) return SpatialAudioRecommendation(false, 0f)
        val target = requireNotNull(profile)
        val labelled = examples.asSequence()
            .filter { usable(it.profile) }
            .distinctBy { it.trackId }
            .toList()
        if (labelled.size < MIN_NEIGHBOURS) return SpatialAudioRecommendation(false, 0f)

        val normalizedArtist = artist?.trim()?.lowercase().orEmpty()
        val neighbours = labelled.map { example ->
            val rawDistance = distance(target, example.profile)
            val sameArtist = normalizedArtist.isNotEmpty() &&
                example.artist.trim().lowercase() == normalizedArtist
            // Artist is only a weak tie-breaker; mix similarity dominates.
            example to (rawDistance * if (sameArtist) SAME_ARTIST_BONUS else 1.0)
        }.filter { it.second <= MAX_NEIGHBOUR_DISTANCE }
            .sortedBy { it.second }
            .take(NEIGHBOUR_COUNT)

        if (neighbours.size < MIN_NEIGHBOURS) return SpatialAudioRecommendation(false, 0f)
        val onWeight = neighbours.filter { it.first.preference }
            .sumOf { 1.0 / (WEIGHT_FLOOR + it.second * it.second) }
        val totalWeight = neighbours.sumOf { 1.0 / (WEIGHT_FLOOR + it.second * it.second) }
        if (totalWeight <= 0.0) return SpatialAudioRecommendation(false, 0f)

        val onProbability = (onWeight / totalWeight).coerceIn(0.0, 1.0)
        val confidence = maxOf(onProbability, 1.0 - onProbability).toFloat()
        // Conservative activation: uncertain or cold-start tracks stay untouched.
        return SpatialAudioRecommendation(
            enabled = onProbability >= ON_CONFIDENCE_THRESHOLD,
            confidence = confidence.coerceIn(0f, 1f),
        )
    }

    private fun usable(profile: SpatialAudioProfile?): Boolean =
        profile != null &&
            profile.sampleCount >= MIN_PROFILE_SAMPLES &&
            profile.rms.isFinite() && profile.rms >= MIN_RMS &&
            profile.stereoWidth.isFinite() && profile.stereoWidth >= 0f &&
            profile.channelCorrelation.isFinite() && profile.channelCorrelation in -1f..1f &&
            profile.lowFrequencySideRatio.isFinite() && profile.lowFrequencySideRatio >= 0f &&
            profile.highFrequencyRatio.isFinite() && profile.highFrequencyRatio in 0f..1f &&
            profile.crestFactor.isFinite() && profile.crestFactor >= 0f

    private fun distance(a: SpatialAudioProfile, b: SpatialAudioProfile): Double {
        val width = (
            ln(1.0 + a.stereoWidth.toDouble()) -
                ln(1.0 + b.stereoWidth.toDouble())
            ) / WIDTH_SCALE
        val correlation = (a.channelCorrelation - b.channelCorrelation) / CORRELATION_SCALE
        val lowSide = (
            ln(1.0 + a.lowFrequencySideRatio.toDouble()) -
                ln(1.0 + b.lowFrequencySideRatio.toDouble())
            ) / LOW_SIDE_SCALE
        val highFrequency = (a.highFrequencyRatio - b.highFrequencyRatio) / HIGH_FREQUENCY_SCALE
        val crestFactor = (a.crestFactor - b.crestFactor) / CREST_FACTOR_SCALE
        return sqrt(
            width * width + correlation * correlation + lowSide * lowSide +
                highFrequency * highFrequency + crestFactor * crestFactor,
        )
    }

    private const val MIN_PROFILE_SAMPLES = 48_000L * 4L
    private const val MIN_RMS = 0.001f
    private const val MIN_NEIGHBOURS = 3
    private const val NEIGHBOUR_COUNT = 7
    private const val MAX_NEIGHBOUR_DISTANCE = 2.4
    private const val ON_CONFIDENCE_THRESHOLD = 0.75
    private const val WEIGHT_FLOOR = 0.20
    private const val SAME_ARTIST_BONUS = 0.90
    private const val WIDTH_SCALE = 0.45
    private const val CORRELATION_SCALE = 0.45
    private const val LOW_SIDE_SCALE = 0.35
    private const val HIGH_FREQUENCY_SCALE = 0.30
    private const val CREST_FACTOR_SCALE = 3.0
}
''', encoding="utf-8")

PROCESSOR = "app/src/main/java/com/music/zenith/playback/SpatialAudioProcessor.kt"
insert_after(PROCESSOR, "import kotlin.math.roundToInt\n", "import kotlin.math.sqrt\n")
insert_after(
    PROCESSOR,
    "    @Volatile\n    var enabled: Boolean = false\n",
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
    "    /** Begin a fresh eight-second profile for this player's own track. */\n"
    "    fun beginTrack(trackId: String?, artist: String?) {\n"
    "        val id = trackId?.takeIf { it.isNotBlank() }\n"
    "        currentTrackId = id\n"
    "        currentArtist = artist.orEmpty()\n"
    "        val rate = sampleRate.takeIf { it > 0 } ?: 48_000\n"
    "        profileAccumulator = id?.let { ProfileAccumulator(it, currentArtist, rate.toLong() * PROFILE_SECONDS) }\n"
    "    }\n",
)
insert_after(
    PROCESSOR,
    "        this.sampleRate = sampleRate\n        this.channelCount = channelCount\n",
    "        profileAccumulator?.takeIf { it.frames == 0L }?.targetFrames = sampleRate.toLong() * PROFILE_SECONDS\n",
)
insert_after(
    PROCESSOR,
    "        if (block.channelCount != 2) return // Spatial widening only applies to stereo\n",
    "        observeFloatSamples(block.samples, block.frameCount)\n",
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
    "        channelCount = 0\n"
    "        sampleRate = 0\n"
    "    }\n",
    "\n"
    "    private fun observeFloatSamples(samples: FloatArray, frameCount: Int) {\n"
    "        val acc = profileAccumulator ?: return\n"
    "        if (acc.completed) return\n"
    "        val remaining = (acc.targetFrames - acc.frames).coerceAtLeast(0L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()\n"
    "        val count = minOf(frameCount, samples.size / 2, remaining)\n"
    "        var index = 0\n"
    "        repeat(count) {\n"
    "            observeFrame(acc, samples[index].toDouble(), samples[index + 1].toDouble())\n"
    "            index += 2\n"
    "        }\n"
    "        finishProfile(acc)\n"
    "    }\n"
    "\n"
    "    private fun observePcm16Samples(input: java.nio.ByteBuffer) {\n"
    "        val acc = profileAccumulator ?: return\n"
    "        if (acc.completed) return\n"
    "        val view = input.duplicate().order(ByteOrder.nativeOrder())\n"
    "        val available = view.remaining() / BYTES_PER_FRAME\n"
    "        val remaining = (acc.targetFrames - acc.frames).coerceAtLeast(0L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()\n"
    "        val count = minOf(available, remaining)\n"
    "        val scale = 1.0 / 32768.0\n"
    "        repeat(count) {\n"
    "            observeFrame(acc, view.short.toDouble() * scale, view.short.toDouble() * scale)\n"
    "        }\n"
    "        finishProfile(acc)\n"
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
    "        // One-pole low-pass used for feature measurement only; audio is unchanged.\n"
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
    "        if (!rms.isFinite() || rms < 0.001) return\n"
    "        val width = sqrt(acc.sideSquared / (acc.midSquared + 1.0e-12)).coerceIn(0.0, 50.0)\n"
    "        val correlation = (acc.cross / sqrt(acc.leftSquared * acc.rightSquared + 1.0e-12)).coerceIn(-1.0, 1.0)\n"
    "        val lowSide = sqrt(acc.lowSideSquared / (acc.lowMidSquared + 1.0e-12)).coerceIn(0.0, 50.0)\n"
    "        val highFrequency = (acc.highFrequencySquared / (acc.totalEnergy + 1.0e-12)).coerceIn(0.0, 1.0)\n"
    "        val crestFactor = (acc.peakAmplitude / rms.coerceAtLeast(1.0e-9)).coerceIn(0.0, 100.0)\n"
    "        onProfileReady?.invoke(\n"
    "            acc.trackId,\n"
    "            acc.artist,\n"
    "            SpatialAudioProfile(width.toFloat(), correlation.toFloat(), lowSide.toFloat(),\n"
    "                highFrequency.toFloat(), crestFactor.toFloat(), acc.frames, rms.toFloat()),\n"
    "        )\n"
    "    }\n",
)
insert_after(PROCESSOR, "        const val DELAY_MS = 15\n", "        const val PROFILE_SECONDS = 8L\n")

PLAYBACK = "app/src/main/java/com/music/zenith/playback/PlaybackService.kt"
old_builder = (
    "    ): ExoPlayer = ExoPlayer.Builder(this)\n"
    "        .setRenderersFactory(silenceSkippingRenderers(spatial, equalizer, filter, loudness))\n"
    "        .setMediaSourceFactory(requireNotNull(mediaSourceFactory))\n"
    "        .setLoadControl(farBufferingLoadControl())\n"
    "        .setAudioAttributes(AUDIO_ATTRIBUTES, /* handleAudioFocus = */ ownsSession)\n"
    "        .setHandleAudioBecomingNoisy(ownsSession)\n"
    "        // Back restarts the track once you're this far into it; only a\n"
    "        // press before that steps to the previous one.\n"
    "        .setMaxSeekToPreviousPositionMs(BACK_RESTARTS_AFTER_MS)\n"
    "        .build()\n"
)
new_builder = (
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
    "    }\n"
)
replace_once(PLAYBACK, old_builder, new_builder)

flow_anchor = (
    "        scope.launch {\n"
    "            combine(\n"
    "                AppSettings.spatialAudio,\n"
    "                AppSettings.spatialAudioByTrack,\n"
    "            ) { global, perTrack -> global to perTrack }\n"
    "                .collect { applySpatialAudioEnabled() }\n"
    "        }\n"
)
insert_after(
    PLAYBACK,
    flow_anchor,
    "\n"
    "        scope.launch {\n"
    "            combine(\n"
    "                AppSettings.spatialAudioLearningRevision,\n"
    "                AudioOutputStatus.current.map { it.routeKind == AudioRouting.Kind.PHONE }.distinctUntilChanged(),\n"
    "            ) { _, _ -> Unit }.collect { applySpatialAudioEnabled() }\n"
    "        }\n",
)

old_apply = (
    "    /**\n"
    "     * The effective spatial-audio state: the user's toggle, unless the\n"
    "     * active player's decoder is currently on a Dolby Atmos (E-AC-3 JOC)\n"
    "     * stream, in which case it's forced off. [activeTrackIsDolbyAtmos] only\n"
    "     * ever describes the active player — see [formatListener] — so both\n"
    "     * processors are kept in lockstep rather than tracking a role swap.\n"
    "     */\n"
    "    private fun applySpatialAudioEnabled(\n"
    "        mediaId: String? = player?.currentMediaItem?.mediaId,\n"
    "    ) {\n"
    "        val enabled = AppSettings.spatialAudioFor(mediaId) && !activeTrackIsDolbyAtmos\n"
    "        spatialAudioProcessorA.enabled = enabled\n"
    "        spatialAudioProcessorB.enabled = enabled\n"
    "    }\n"
)
new_apply = (
    "    /** Resolve per-player choices, learned mix similarity and hard output/format safety gates. */\n"
    "    private fun applySpatialAudioEnabled(\n"
    "        mediaId: String? = player?.currentMediaItem?.mediaId,\n"
    "    ) {\n"
    "        val masterEnabled = AppSettings.spatialAudio.value\n"
    "        val phoneSpeaker = AudioOutputStatus.current.value.routeKind == AudioRouting.Kind.PHONE\n"
    "        val examples = if (masterEnabled && !phoneSpeaker) AppSettings.spatialAudioExamples() else emptyList()\n"
    "\n"
    "        fun resolve(processor: SpatialAudioProcessor): SpatialAudioDecision {\n"
    "            val trackId = processor.currentTrackId\n"
    "                ?: return SpatialAudioDecision(false, SpatialAudioDecisionSource.FALLBACK)\n"
    "            val atmos = NerdStats.declaredFormat(trackId)?.isDolbyAtmos == true ||\n"
    "                (trackId == mediaId && activeTrackIsDolbyAtmos)\n"
    "            val bypass = when {\n"
    "                phoneSpeaker -> SpatialAudioBypassReason.PHONE_SPEAKER\n"
    "                atmos -> SpatialAudioBypassReason.DOLBY_ATMOS\n"
    "                else -> null\n"
    "            }\n"
    "            if (bypass != null) {\n"
    "                return SpatialAudioDecisionEngine.resolve(\n"
    "                    AppSettings.spatialAudioPreference(trackId),\n"
    "                    AppSettings.spatialAudioProfile(trackId),\n"
    "                    processor.currentArtist,\n"
    "                    examples,\n"
    "                    bypass,\n"
    "                )\n"
    "            }\n"
    "            if (!masterEnabled) return SpatialAudioDecision(false, SpatialAudioDecisionSource.MANUAL_GLOBAL)\n"
    "            return SpatialAudioDecisionEngine.resolve(\n"
    "                AppSettings.spatialAudioPreference(trackId),\n"
    "                AppSettings.spatialAudioProfile(trackId),\n"
    "                processor.currentArtist,\n"
    "                examples,\n"
    "            )\n"
    "        }\n"
    "\n"
    "        val decisionA = resolve(spatialAudioProcessorA)\n"
    "        val decisionB = resolve(spatialAudioProcessorB)\n"
    "        spatialAudioProcessorA.enabled = decisionA.enabled\n"
    "        spatialAudioProcessorB.enabled = decisionB.enabled\n"
    "        val activeId = mediaId ?: player?.currentMediaItem?.mediaId\n"
    "        val activeDecision = when (activeId) {\n"
    "            spatialAudioProcessorA.currentTrackId -> decisionA\n"
    "            spatialAudioProcessorB.currentTrackId -> decisionB\n"
    "            else -> null\n"
    "        }\n"
    "        AppSettings.spatialAudioEffective.value = activeDecision?.enabled == true\n"
    "    }\n"
)
replace_once(PLAYBACK, old_apply, new_apply)

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
old_menu_row = (
    "            onToggleSpatialAudio?.let {\n"
    "                ActionRow(\n"
    "                    icon = SfIcons.SurroundSound,\n"
    "                    label = stringResource(\n"
    "                        if (spatialAudioOn) R.string.spatial_audio_prefer_off else R.string.spatial_audio_prefer_on,\n"
    "                    ),\n"
    "                    accent = palette.accent,\n"
    "                    onClick = it,\n"
    "                )\n"
    "            }\n"
)
new_menu_row = (
    "            onToggleSpatialAudio?.let { _ ->\n"
    "                ActionRow(\n"
    "                    icon = SfIcons.SurroundSound,\n"
    "                    label = stringResource(R.string.spatial_audio),\n"
    "                    value = when (spatialPreference) {\n"
    "                        SpatialAudioPreference.AUTO -> stringResource(\n"
    "                            if (spatialAudioMaster && spatialAudioEffective) R.string.spatial_audio_auto_on\n"
    "                            else R.string.spatial_audio_auto_off,\n"
    "                        )\n"
    "                        SpatialAudioPreference.FORCE_ON -> stringResource(\n"
    "                            if (spatialAudioMaster && spatialAudioEffective) R.string.spatial_audio_manual_on\n"
    "                            else R.string.spatial_audio_paused,\n"
    "                        )\n"
    "                        SpatialAudioPreference.FORCE_OFF -> stringResource(R.string.spatial_audio_manual_off)\n"
    "                    },\n"
    "                    accent = palette.accent,\n"
    "                    onClick = {\n"
    "                        val next = when (spatialPreference) {\n"
    "                            SpatialAudioPreference.AUTO -> SpatialAudioPreference.FORCE_ON\n"
    "                            SpatialAudioPreference.FORCE_ON -> SpatialAudioPreference.FORCE_OFF\n"
    "                            SpatialAudioPreference.FORCE_OFF -> SpatialAudioPreference.AUTO\n"
    "                        }\n"
    "                        AppSettings.setSpatialAudioPreference(song.videoId, song.artist, next)\n"
    "                        spatialPreference = next\n"
    "                        if (next == SpatialAudioPreference.FORCE_ON && !spatialAudioMaster) {\n"
    "                            AppSettings.setSpatialAudio(true)\n"
    "                        }\n"
    "                    },\n"
    "                )\n"
    "            }\n"
)
replace_once(MENU, old_menu_row, new_menu_row)

STRINGS = "app/src/main/res/values/strings.xml"
insert_after(
    STRINGS,
    '    <string name="spatial_audio_prefer_on">Prefer Spatial Audio On</string>\n',
    '    <string name="spatial_audio_auto_on">Auto · On</string>\n'
    '    <string name="spatial_audio_auto_off">Auto · Off</string>\n'
    '    <string name="spatial_audio_manual_on">On</string>\n'
    '    <string name="spatial_audio_manual_off">Off</string>\n'
    '    <string name="spatial_audio_paused">On · paused for output or format</string>\n',
)

# Keep the locale-completeness test honest without changing upstream translations:
# missing entries use the same English fallback Android would otherwise resolve.
# These edits exist only in the build checkout and are restored before main is pushed.
for locale_path in (
    "app/src/main/res/values-in/strings.xml",
    "app/src/main/res/values-ko/strings.xml",
):
    fill_missing_locale_keys(STRINGS, locale_path)

TEST = ROOT / "app/src/test/java/com/music/zenith/playback/SpatialAudioDecisionEngineTest.kt"
TEST.write_text(r'''package com.music.zenith.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpatialAudioDecisionEngineTest {
    private val profile = SpatialAudioProfile(
        stereoWidth = 0.35f,
        channelCorrelation = 0.72f,
        lowFrequencySideRatio = 0.08f,
        highFrequencyRatio = 0.42f,
        crestFactor = 5.2f,
        sampleCount = 384_000L,
        rms = 0.15f,
    )

    private fun example(id: String, on: Boolean, mix: SpatialAudioProfile = profile) =
        SpatialAudioExample(id, "Artist", on, mix)

    @Test
    fun coldStartStaysOffWithoutListenerFeedback() {
        val result = SpatialAudioDecisionEngine.resolve(
            SpatialAudioPreference.AUTO, profile, "Artist", emptyList(),
        )
        assertFalse(result.enabled)
        assertEquals(SpatialAudioDecisionSource.AUTOMATIC, result.source)
        assertEquals(0f, result.confidence)
    }

    @Test
    fun similarMixesFollowUnanimousPositiveFeedback() {
        val examples = listOf(example("a", true), example("b", true), example("c", true))
        assertTrue(SpatialAudioDecisionEngine.recommendsOn(profile, "Artist", examples))
    }

    @Test
    fun mixedOrNegativeFeedbackDoesNotActivateProcessing() {
        assertFalse(
            SpatialAudioDecisionEngine.recommendsOn(
                profile, "Artist",
                listOf(example("a", true), example("b", true), example("c", false)),
            ),
        )
        assertFalse(
            SpatialAudioDecisionEngine.recommendsOn(
                profile, "Artist",
                listOf(example("d", false), example("e", false), example("f", false)),
            ),
        )
    }

    @Test
    fun sameArtistCannotOverrideDissimilarMixes() {
        val dissimilar = profile.copy(
            stereoWidth = 4.0f,
            channelCorrelation = -0.95f,
            lowFrequencySideRatio = 3.0f,
            highFrequencyRatio = 0.95f,
            crestFactor = 18.0f,
        )
        assertFalse(
            SpatialAudioDecisionEngine.recommendsOn(
                profile, "Artist",
                listOf(example("a", true, dissimilar), example("b", true, dissimilar), example("c", true, dissimilar)),
            ),
        )
    }

    @Test
    fun incompleteOrSilentProfilesAreIgnored() {
        assertFalse(
            SpatialAudioDecisionEngine.recommendsOn(
                profile.copy(sampleCount = 1_000L), "Artist",
                listOf(example("a", true), example("b", true), example("c", true)),
            ),
        )
        assertFalse(
            SpatialAudioDecisionEngine.recommendsOn(
                profile.copy(rms = 0.0001f), "Artist",
                listOf(example("a", true), example("b", true), example("c", true)),
            ),
        )
    }

    @Test
    fun explicitTrackChoiceBeatsTheModel() {
        assertTrue(
            SpatialAudioDecisionEngine.resolve(
                SpatialAudioPreference.FORCE_ON, profile, "Artist",
                listOf(example("a", false), example("b", false), example("c", false)),
            ).enabled,
        )
        assertFalse(
            SpatialAudioDecisionEngine.resolve(
                SpatialAudioPreference.FORCE_OFF, profile, "Artist",
                listOf(example("a", true), example("b", true), example("c", true)),
            ).enabled,
        )
    }

    @Test
    fun phoneSpeakerSafetyBypassBeatsManualOn() {
        val decision = SpatialAudioDecisionEngine.resolve(
            SpatialAudioPreference.FORCE_ON, profile, "Artist", emptyList(),
            SpatialAudioBypassReason.PHONE_SPEAKER,
        )
        assertFalse(decision.enabled)
        assertEquals(SpatialAudioDecisionSource.SAFETY_BYPASS, decision.source)
        assertEquals(SpatialAudioBypassReason.PHONE_SPEAKER, decision.bypassReason)
    }
}
''', encoding="utf-8")

print("One-shot Spatial Audio learning overlay applied.")
