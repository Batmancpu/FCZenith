#!/usr/bin/env python3
"""Apply the permanent FC-owned Spatial Audio integration to an upstream checkout."""
from pathlib import Path
import shutil
import subprocess

ROOT = Path.cwd()
OVERLAY = Path(__file__).resolve().parent
ASSETS = OVERLAY

def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")

def write(path: str, content: str) -> None:
    target = ROOT / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(content, encoding="utf-8")

def replace_once(path: str, old: str, new: str) -> None:
    source = read(path)
    count = source.count(old)
    if count != 1:
        raise SystemExit(f"Spatial Audio patch stopped: {path} anchor count={count}, expected 1")
    write(path, source.replace(old, new, 1))

def insert_after(path: str, anchor: str, addition: str) -> None:
    replace_once(path, anchor, anchor + addition)

def replace_between(path: str, start: str, end: str, replacement: str) -> None:
    source = read(path)
    if source.count(start) != 1 or source.count(end) != 1:
        raise SystemExit(f"Spatial Audio patch stopped: non-unique section anchors in {path}")
    a = source.index(start)
    b = source.index(end, a + len(start))
    if b <= a:
        raise SystemExit(f"Spatial Audio patch stopped: invalid section bounds in {path}")
    write(path, source[:a] + replacement + source[b:])

def copy_asset(name: str, destination: str) -> None:
    source = ASSETS / name
    if not source.is_file():
        raise SystemExit(f"Missing Spatial Audio asset: {source}")
    target = ROOT / destination
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(source, target)

# The reviewed base is an ancestor of the upstream tree being built.
source_sha = (OVERLAY / "source-commit").read_text(encoding="utf-8").strip()
if len(source_sha) != 40:
    raise SystemExit("source-commit must contain a full Git SHA")
subprocess.run(["git", "cat-file", "-e", f"{source_sha}^{{commit}}"], check=True)
subprocess.run(["git", "merge-base", "--is-ancestor", source_sha, "HEAD"], check=True)

# Never overwrite upstream changes to the processor without an explicit rebase.
processor_path = "app/src/main/java/com/music/zenith/playback/SpatialAudioProcessor.kt"
baseline_processor = subprocess.run(
    ["git", "show", f"{source_sha}:{processor_path}"],
    check=True,
    capture_output=True,
    text=True,
).stdout
if read(processor_path) != baseline_processor:
    raise SystemExit(
        "Upstream SpatialAudioProcessor.kt changed after the reviewed FC baseline; "
        "reconcile the FC adapter before building."
    )

# Use the reviewed stereo sampler and learner as complete source assets.
copy_asset("SpatialAudioLearning.kt", "app/src/main/java/com/music/zenith/playback/SpatialAudioLearning.kt")
copy_asset("SpatialAudioProcessor.kt", "app/src/main/java/com/music/zenith/playback/SpatialAudioProcessor.kt")
copy_asset("SpatialAudioLearningTest.kt", "app/src/test/java/com/music/zenith/playback/SpatialAudioLearningTest.kt")

APP = "app/src/main/java/com/music/zenith/data/settings/AppSettings.kt"
insert_after(
    APP,
    "import com.music.zenith.playback.EqualizerPreset\n",
    "import com.music.zenith.playback.SpatialAudioDecision\n"
    "import com.music.zenith.playback.SpatialAudioExample\n"
    "import com.music.zenith.playback.SpatialAudioFeedback\n"
    "import com.music.zenith.playback.SpatialAudioMode\n"
    "import com.music.zenith.playback.SpatialAudioPreference\n"
    "import com.music.zenith.playback.SpatialAudioProfile\n"
    "import com.music.zenith.playback.SpatialAudioTrackRecord\n"
    "import java.util.concurrent.Executors\n",
)
insert_after(
    APP,
    "object AppSettings {\n",
    "\n"
    "    // A serial worker keeps JSON/index work off Main and the real-time audio callback.\n"
    "    private val spatialAudioIoExecutor = Executors.newSingleThreadExecutor { task ->\n"
    "        Thread(task, \"SpatialAudioPrefs\").apply { isDaemon = true }\n"
    "    }\n",
)
insert_after(
    APP,
    "    val spatialAudioByTrack = MutableStateFlow<Map<String, Boolean>>(emptyMap())\n",
    "\n"
    "    val spatialAudioMode = MutableStateFlow(SpatialAudioMode.AUTO)\n"
    "    val spatialAudioTrackPreferences = MutableStateFlow<Map<String, SpatialAudioPreference>>(emptyMap())\n"
    "    val spatialAudioRecords = MutableStateFlow<Map<String, SpatialAudioTrackRecord>>(emptyMap())\n"
    "    val spatialAudioTrainingExamples = MutableStateFlow<List<SpatialAudioExample>>(emptyList())\n"
    "    val spatialAudioOnPhoneSpeaker = MutableStateFlow(false)\n"
    "    val spatialAudioLearningEnabled = MutableStateFlow(true)\n"
    "    val spatialAudioCurrentDecision = MutableStateFlow<SpatialAudioDecision?>(null)\n"
    "    val spatialAudioCurrentTrackId = MutableStateFlow(\"\")\n"
    "    val spatialAudioCurrentTrackTitle = MutableStateFlow(\"\")\n"
    "    val spatialAudioCurrentTrackArtist = MutableStateFlow(\"\")\n",
)
replace_once(
    APP,
    '            .putBoolean(KEY_SPATIAL_AUDIO, true)\n',
    '            .putBoolean(KEY_SPATIAL_AUDIO, true)\n'
    '            .putString(KEY_SPATIAL_AUDIO_MODE, SpatialAudioMode.AUTO.name)\n'
    '            .putBoolean(KEY_SPATIAL_AUDIO_ON_PHONE_SPEAKER, false)\n'
    '            .putBoolean(KEY_SPATIAL_AUDIO_LEARNING_ENABLED, true)\n',
)
replace_once(
    APP,
    "        spatialAudio.value = prefs.getBoolean(KEY_SPATIAL_AUDIO, false)\n"
    "        spatialAudioByTrack.value = readSpatialAudioByTrack()\n",
    "        spatialAudioMode.value = if (prefs.contains(KEY_SPATIAL_AUDIO_MODE)) {\n"
    "            runCatching {\n"
    "                SpatialAudioMode.valueOf(\n"
    "                    prefs.getString(KEY_SPATIAL_AUDIO_MODE, SpatialAudioMode.AUTO.name) ?: SpatialAudioMode.AUTO.name,\n"
    "                )\n"
    "            }.getOrDefault(SpatialAudioMode.AUTO)\n"
    "        } else if (prefs.getBoolean(KEY_SPATIAL_AUDIO, false)) {\n"
    "            SpatialAudioMode.AUTO\n"
    "        } else {\n"
    "            SpatialAudioMode.OFF\n"
    "        }\n"
    "        if (spatialAudioMode.value == SpatialAudioMode.ON) {\n"
    "            spatialAudioMode.value = SpatialAudioMode.AUTO\n"
    "            prefs.edit().putString(KEY_SPATIAL_AUDIO_MODE, SpatialAudioMode.AUTO.name).apply()\n"
    "        }\n"
    "        spatialAudio.value = spatialAudioMode.value != SpatialAudioMode.OFF\n"
    "        spatialAudioByTrack.value = readSpatialAudioByTrack()\n"
    "        spatialAudioRecords.value = readSpatialAudioRecords()\n"
    "        spatialAudioTrackPreferences.value = readSpatialAudioTrackPreferences()\n"
    "        val migratedSpatialRecords = LinkedHashMap(spatialAudioRecords.value)\n"
    "        spatialAudioByTrack.value.forEach { (id, enabled) ->\n"
    "            if (!migratedSpatialRecords.containsKey(id)) {\n"
    "                migratedSpatialRecords[id] = SpatialAudioTrackRecord(\n"
    "                    trackId = id, title = \"\", artist = \"\",\n"
    "                    preference = if (enabled) SpatialAudioPreference.FORCE_ON else SpatialAudioPreference.FORCE_OFF,\n"
    "                    feedback = if (enabled) SpatialAudioFeedback.ON else SpatialAudioFeedback.OFF,\n"
    "                    updatedAtMs = System.currentTimeMillis(),\n"
    "                )\n"
    "            }\n"
    "        }\n"
    "        if (migratedSpatialRecords.size != spatialAudioRecords.value.size) persistSpatialAudioRecords(migratedSpatialRecords)\n"
    "        spatialAudioTrackPreferences.value = readSpatialAudioTrackPreferences()\n"
    "        val initialSpatialRecords = spatialAudioRecords.value\n"
    "        spatialAudioIoExecutor.execute {\n"
    "            spatialAudioTrainingExamples.value = buildSpatialAudioExamples(initialSpatialRecords)\n"
    "        }\n"
    "        spatialAudioOnPhoneSpeaker.value = prefs.getBoolean(KEY_SPATIAL_AUDIO_ON_PHONE_SPEAKER, false)\n"
    "        spatialAudioLearningEnabled.value = prefs.getBoolean(KEY_SPATIAL_AUDIO_LEARNING_ENABLED, true)\n",
)
SPATIAL_METHODS = r'''
    /** Master switch: On selects the Auto policy; Off disables Spatial Audio. */
    fun setSpatialAudio(value: Boolean) {
        setSpatialAudioMode(if (value) SpatialAudioMode.AUTO else SpatialAudioMode.OFF)
    }

    fun setSpatialAudioMode(value: SpatialAudioMode) {
        spatialAudioMode.value = value
        spatialAudio.value = value != SpatialAudioMode.OFF
        prefs.edit()
            .putString(KEY_SPATIAL_AUDIO_MODE, value.name)
            .putBoolean(KEY_SPATIAL_AUDIO, spatialAudio.value)
            .apply()
    }

    fun setSpatialAudioOnPhoneSpeaker(value: Boolean) {
        spatialAudioOnPhoneSpeaker.value = value
        prefs.edit().putBoolean(KEY_SPATIAL_AUDIO_ON_PHONE_SPEAKER, value).apply()
    }

    /** Stops personal profiling/training but does not silently disable the Auto effect. */
    fun setSpatialAudioLearningEnabled(value: Boolean) {
        spatialAudioLearningEnabled.value = value
        prefs.edit().putBoolean(KEY_SPATIAL_AUDIO_LEARNING_ENABLED, value).apply()
    }

    fun spatialAudioPreference(trackId: String?): SpatialAudioPreference =
        trackId?.takeIf { it.isNotBlank() }?.let { spatialAudioTrackPreferences.value[it] }
            ?: SpatialAudioPreference.AUTO

    /** Compatibility view for existing Boolean callers. Auto starts On for eligible stereo. */
    fun spatialAudioFor(trackId: String?): Boolean {
        if (spatialAudioMode.value == SpatialAudioMode.OFF) return false
        return when (spatialAudioPreference(trackId)) {
            SpatialAudioPreference.FORCE_OFF -> false
            SpatialAudioPreference.FORCE_ON, SpatialAudioPreference.AUTO -> true
        }
    }

    fun setSpatialAudioForTrack(trackId: String, value: Boolean) {
        setSpatialAudioPreference(
            trackId = trackId,
            title = spatialAudioRecords.value[trackId]?.title.orEmpty(),
            artist = spatialAudioRecords.value[trackId]?.artist.orEmpty(),
            preference = if (value) SpatialAudioPreference.FORCE_ON else SpatialAudioPreference.FORCE_OFF,
        )
    }

    /** Only explicit per-track On/Off choices become labels. Auto clears an override, not feedback. */
    @Synchronized
    fun setSpatialAudioPreference(
        trackId: String,
        title: String,
        artist: String,
        preference: SpatialAudioPreference,
    ) {
        if (trackId.isBlank()) return
        val previous = spatialAudioRecords.value[trackId] ?: SpatialAudioTrackRecord(trackId, title, artist)
        val feedback = when (preference) {
            SpatialAudioPreference.FORCE_ON -> SpatialAudioFeedback.ON
            SpatialAudioPreference.FORCE_OFF -> SpatialAudioFeedback.OFF
            SpatialAudioPreference.AUTO -> previous.feedback
        }
        val records = LinkedHashMap(spatialAudioRecords.value)
        records[trackId] = previous.copy(
            title = title.ifBlank { previous.title },
            artist = artist.ifBlank { previous.artist },
            preference = preference,
            feedback = feedback,
            updatedAtMs = System.currentTimeMillis(),
        )
        val preferences = LinkedHashMap(spatialAudioTrackPreferences.value)
        if (preference == SpatialAudioPreference.AUTO) preferences.remove(trackId)
        else preferences[trackId] = preference
        spatialAudioTrackPreferences.value = preferences

        // Keep the old Boolean map readable by earlier app versions.
        val legacy = LinkedHashMap(spatialAudioByTrack.value)
        when (preference) {
            SpatialAudioPreference.AUTO -> legacy.remove(trackId)
            SpatialAudioPreference.FORCE_ON -> legacy[trackId] = true
            SpatialAudioPreference.FORCE_OFF -> legacy[trackId] = false
        }
        while (legacy.size > MAX_SPATIAL_TRACKS) legacy.remove(legacy.keys.first())
        spatialAudioByTrack.value = legacy
        persistSpatialAudioRecords(records)
        val legacySnapshot = legacy.toMap()
        spatialAudioIoExecutor.execute {
            val json = org.json.JSONObject()
            legacySnapshot.forEach { (id, enabled) -> json.put(id, enabled) }
            prefs.edit().putString(KEY_SPATIAL_AUDIO_BY_TRACK, json.toString()).apply()
        }
    }

    /** Stores scalar mix statistics only; decoded audio itself is never retained. */
    @Synchronized
    fun setSpatialAudioProfile(trackId: String, title: String, artist: String, profile: SpatialAudioProfile) {
        if (trackId.isBlank() || !profile.isUsable) return
        val previous = spatialAudioRecords.value[trackId] ?: SpatialAudioTrackRecord(trackId, title, artist)
        val next = LinkedHashMap(spatialAudioRecords.value)
        next[trackId] = previous.copy(
            title = title.ifBlank { previous.title },
            artist = artist.ifBlank { previous.artist },
            profile = profile,
            updatedAtMs = System.currentTimeMillis(),
        )
        persistSpatialAudioRecords(next)
    }

    fun spatialAudioExamples(): List<SpatialAudioExample> = spatialAudioTrainingExamples.value

    private fun buildSpatialAudioExamples(records: Map<String, SpatialAudioTrackRecord>): List<SpatialAudioExample> =
        records.values.mapNotNull { record ->
            val profile = record.profile?.takeIf { it.isUsable } ?: return@mapNotNull null
            if (record.feedback == SpatialAudioFeedback.NONE) return@mapNotNull null
            SpatialAudioExample(record.trackId, record.artist, record.feedback, profile)
        }

    private fun persistSpatialAudioRecords(records: Map<String, SpatialAudioTrackRecord>) {
        val bounded = LinkedHashMap<String, SpatialAudioTrackRecord>()
        records.values.sortedByDescending { it.updatedAtMs }.take(MAX_SPATIAL_AUDIO_RECORDS)
            .forEach { bounded[it.trackId] = it }
        spatialAudioRecords.value = bounded
        spatialAudioIoExecutor.execute {
            spatialAudioTrainingExamples.value = buildSpatialAudioExamples(bounded)
            val root = org.json.JSONObject()
            bounded.forEach { (id, record) ->
                val item = org.json.JSONObject()
                    .put("title", record.title)
                    .put("artist", record.artist)
                    .put("preference", record.preference.name)
                    .put("feedback", record.feedback.name)
                    .put("updatedAt", record.updatedAtMs)
                record.profile?.let { p ->
                    item.put("profile", org.json.JSONObject()
                        .put("sampleRateHz", p.sampleRateHz)
                        .put("frameCount", p.frameCount)
                        .put("stereoWidth", p.stereoWidth.toDouble())
                        .put("channelCorrelation", p.channelCorrelation.toDouble())
                        .put("lowFrequencySideRatio", p.lowFrequencySideRatio.toDouble())
                        .put("highFrequencyRatio", p.highFrequencyRatio.toDouble())
                        .put("crestFactor", p.crestFactor.toDouble())
                        .put("rms", p.rms.toDouble()))
                }
                root.put(id, item)
            }
            prefs.edit().putString(KEY_SPATIAL_AUDIO_RECORDS, root.toString()).apply()
        }
    }

    private fun readSpatialAudioRecords(): Map<String, SpatialAudioTrackRecord> {
        val raw = prefs.getString(KEY_SPATIAL_AUDIO_RECORDS, null) ?: return emptyMap()
        return runCatching {
            val root = org.json.JSONObject(raw)
            val records = LinkedHashMap<String, SpatialAudioTrackRecord>()
            val keys = root.keys()
            while (keys.hasNext()) {
                val id = keys.next()
                val item = root.optJSONObject(id) ?: continue
                val pref = runCatching {
                    SpatialAudioPreference.valueOf(item.optString("preference", "AUTO"))
                }.getOrDefault(SpatialAudioPreference.AUTO)
                val feedback = runCatching {
                    SpatialAudioFeedback.valueOf(item.optString("feedback", "NONE"))
                }.getOrDefault(SpatialAudioFeedback.NONE)
                val profileJson = item.optJSONObject("profile")
                val profile = profileJson?.let { p ->
                    SpatialAudioProfile(
                        sampleRateHz = p.optInt("sampleRateHz", 0),
                        frameCount = p.optLong("frameCount", 0L),
                        stereoWidth = p.optDouble("stereoWidth", Double.NaN).toFloat(),
                        channelCorrelation = p.optDouble("channelCorrelation", Double.NaN).toFloat(),
                        lowFrequencySideRatio = p.optDouble("lowFrequencySideRatio", Double.NaN).toFloat(),
                        highFrequencyRatio = p.optDouble("highFrequencyRatio", Double.NaN).toFloat(),
                        crestFactor = p.optDouble("crestFactor", Double.NaN).toFloat(),
                        rms = p.optDouble("rms", Double.NaN).toFloat(),
                    ).takeIf { it.isUsable }
                }
                records[id] = SpatialAudioTrackRecord(
                    trackId = id,
                    title = item.optString("title", ""),
                    artist = item.optString("artist", ""),
                    preference = pref,
                    feedback = feedback,
                    profile = profile,
                    updatedAtMs = item.optLong("updatedAt", 0L),
                )
            }
            records
        }.getOrDefault(emptyMap())
    }

    private fun readSpatialAudioTrackPreferences(): Map<String, SpatialAudioPreference> {
        val result = LinkedHashMap<String, SpatialAudioPreference>()
        spatialAudioRecords.value.forEach { (id, record) ->
            if (record.preference != SpatialAudioPreference.AUTO) result[id] = record.preference
        }
        spatialAudioByTrack.value.forEach { (id, enabled) ->
            if (!result.containsKey(id)) {
                result[id] = if (enabled) SpatialAudioPreference.FORCE_ON else SpatialAudioPreference.FORCE_OFF
            }
        }
        return result
    }

    private fun readSpatialAudioByTrack(): Map<String, Boolean> {
        val raw = prefs.getString(KEY_SPATIAL_AUDIO_BY_TRACK, null) ?: return emptyMap()
        return runCatching {
            val json = org.json.JSONObject(raw)
            val result = LinkedHashMap<String, Boolean>()
            val keys = json.keys()
            while (keys.hasNext()) {
                val id = keys.next()
                result[id] = json.optBoolean(id, false)
            }
            result
        }.getOrDefault(emptyMap())
    }

'''
replace_between(APP, "    fun setSpatialAudio(value: Boolean) {\n", "    fun setEqualizerEnabled(value: Boolean) {\n", SPATIAL_METHODS)
insert_after(
    APP,
    '    private const val KEY_SPATIAL_AUDIO_BY_TRACK = "spatial_audio_by_track"\n',
    '    private const val KEY_SPATIAL_AUDIO_MODE = "spatial_audio_mode"\n'
    '    private const val KEY_SPATIAL_AUDIO_ON_PHONE_SPEAKER = "spatial_audio_on_phone_speaker"\n'
    '    private const val KEY_SPATIAL_AUDIO_LEARNING_ENABLED = "spatial_audio_learning_enabled"\n'
    '    private const val KEY_SPATIAL_AUDIO_RECORDS = "spatial_audio_records_v2"\n'
    '    private const val MAX_SPATIAL_AUDIO_RECORDS = 400\n',
)
replace_once(
    APP,
    '        KEY_ONBOARDING_COMPLETE,\n    )',
    '        KEY_ONBOARDING_COMPLETE,\n'
    '        KEY_SPATIAL_AUDIO_RECORDS,\n'
    '    )',
)

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
    "        spatial.onProfileReady = { trackId, title, artist, profile ->\n"
    "            scope.launch(Dispatchers.IO) {\n"
    "                AppSettings.setSpatialAudioProfile(trackId, title, artist, profile)\n"
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
    "                val song = mediaItem?.toSong()\n"
    "                val trackId = mediaItem?.mediaId?.takeIf { it.isNotBlank() }\n"
    "                    ?: song?.videoId?.takeIf { it.isNotBlank() }\n"
    "                val atmos = trackId?.let { NerdStats.declaredFormat(it)?.isDolbyAtmos == true } == true\n"
    "                val profiled = trackId?.let { AppSettings.spatialAudioRecords.value[it]?.profile?.isUsable == true } == true\n"
    "                spatial.setTrackContext(trackId, song?.title, song?.artist, allowProfile = !atmos && !profiled)\n"
    "                applySpatialAudioEnabled(trackId)\n"
    "            }\n"
    "        })\n"
    "        return built\n"
    "    }\n"
)
replace_once(PLAYBACK, old_builder, new_builder)
replace_once(
    PLAYBACK,
    "        scope.launch {\n"
    "            combine(\n"
    "                AppSettings.spatialAudio,\n"
    "                AppSettings.spatialAudioByTrack,\n"
    "            ) { global, perTrack -> global to perTrack }\n"
    "                .collect { applySpatialAudioEnabled() }\n"
    "        }\n",
    "        scope.launch {\n"
    "            combine(\n"
    "                AppSettings.spatialAudioMode,\n"
    "                AppSettings.spatialAudioTrackPreferences,\n"
    "                AppSettings.spatialAudioRecords,\n"
    "                AppSettings.spatialAudioTrainingExamples,\n"
    "            ) { _, _, _, _ -> Unit }\n"
    "                .combine(AppSettings.spatialAudioOnPhoneSpeaker) { _, _ -> Unit }\n"
    "                .combine(AppSettings.spatialAudioLearningEnabled) { _, _ -> Unit }\n"
    "                .combine(AudioOutputStatus.current.map { it.routeKind to it.dspAvailable }.distinctUntilChanged()) { _, _ -> Unit }\n"
    "                .collect { applySpatialAudioEnabled() }\n"
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
    "    /** Resolve each player's track, listener feedback, local mix similarity and output safety gates. */\n"
    "    private fun applySpatialAudioEnabled(mediaId: String? = player?.currentMediaItem?.mediaId) {\n"
    "        val output = AudioOutputStatus.current.value\n"
    "        val mode = AppSettings.spatialAudioMode.value\n"
    "        val examples = AppSettings.spatialAudioExamples()\n"
    "        val allowPhoneSpeaker = AppSettings.spatialAudioOnPhoneSpeaker.value\n"
    "        val learningEnabled = AppSettings.spatialAudioLearningEnabled.value\n"
    "        fun resolve(processor: SpatialAudioProcessor): SpatialAudioDecision? {\n"
    "            if (processor.learningEnabled != learningEnabled) processor.learningEnabled = learningEnabled\n"
    "            val trackId = processor.currentTrackId ?: return null\n"
    "            val preference = AppSettings.spatialAudioTrackPreferences.value[trackId] ?: SpatialAudioPreference.AUTO\n"
    "            val profile = AppSettings.spatialAudioRecords.value[trackId]?.profile\n"
    "            val atmos = NerdStats.declaredFormat(trackId)?.isDolbyAtmos == true ||\n"
    "                (trackId == player?.currentMediaItem?.mediaId && activeTrackIsDolbyAtmos)\n"
    "            if (atmos) processor.setTrackContext(trackId, processor.currentTitle, processor.currentArtist, allowProfile = false)\n"
    "            return SpatialAudioLearner.decide(\n"
    "                mode = mode, preference = preference, profile = profile, artist = processor.currentArtist,\n"
    "                examples = examples, learningEnabled = learningEnabled,\n"
    "                isPhoneSpeaker = output.routeKind == AudioRouting.Kind.PHONE,\n"
    "                allowOnPhoneSpeaker = allowPhoneSpeaker, isDolbyAtmos = atmos, dspAvailable = output.dspAvailable,\n"
    "            )\n"
    "        }\n"
    "        val decisionA = resolve(spatialAudioProcessorA)\n"
    "        val decisionB = resolve(spatialAudioProcessorB)\n"
    "        spatialAudioProcessorA.enabled = decisionA?.enabled == true\n"
    "        spatialAudioProcessorB.enabled = decisionB?.enabled == true\n"
    "        val activeId = player?.currentMediaItem?.mediaId ?: mediaId\n"
    "        val activeProcessor = when (activeId) {\n"
    "            spatialAudioProcessorA.currentTrackId -> spatialAudioProcessorA\n"
    "            spatialAudioProcessorB.currentTrackId -> spatialAudioProcessorB\n"
    "            else -> null\n"
    "        }\n"
    "        AppSettings.spatialAudioCurrentTrackId.value = activeProcessor?.currentTrackId.orEmpty()\n"
    "        AppSettings.spatialAudioCurrentTrackTitle.value = activeProcessor?.currentTitle.orEmpty()\n"
    "        AppSettings.spatialAudioCurrentTrackArtist.value = activeProcessor?.currentArtist.orEmpty()\n"
    "        AppSettings.spatialAudioCurrentDecision.value = when (activeProcessor) {\n"
    "            spatialAudioProcessorA -> decisionA\n"
    "            spatialAudioProcessorB -> decisionB\n"
    "            else -> null\n"
    "        }\n"
    "    }\n"
)
replace_once(PLAYBACK, old_apply, new_apply)

MENU = "app/src/main/java/com/music/zenith/ui/components/SongActionsMenu.kt"
insert_after(MENU, "import com.music.zenith.playback.SleepTimer\n",
    "import com.music.zenith.playback.SpatialAudioDecision\n"
    "import com.music.zenith.playback.SpatialAudioDecisionReason\n"
    "import com.music.zenith.playback.SpatialAudioPreference\n")
replace_once(MENU,
    "    onToggleSpatialAudio: (() -> Unit)? = null,\n"
    "    /** The track's effective spatial-audio state, so the row offers the opposite. */\n"
    "    spatialAudioOn: Boolean = false,",
    "    onSetSpatialAudioPreference: ((SpatialAudioPreference) -> Unit)? = null,\n"
    "    spatialAudioPreference: SpatialAudioPreference = SpatialAudioPreference.AUTO,\n"
    "    spatialAudioDecision: SpatialAudioDecision? = null,")
insert_after(MENU, "    val isOffline = song.localUri != null\n",
    "\n    var spatialPreference by remember(song.videoId) { mutableStateOf(spatialAudioPreference) }\n")
replace_once(MENU,
    "            // Stays open after a tap: the label flips in place and the change\n"
    "            // is audible at once, so the two settings can be compared by ear.\n"
    "            onToggleSpatialAudio?.let {\n"
    "                ActionRow(\n"
    "                    icon = SfIcons.SurroundSound,\n"
    "                    label = stringResource(\n"
    "                        if (spatialAudioOn) R.string.spatial_audio_prefer_off else R.string.spatial_audio_prefer_on,\n"
    "                    ),\n"
    "                    accent = palette.accent,\n"
    "                    onClick = it,\n"
    "                )\n"
    "            }\n",
    "            // One binary per-track control. Auto is internal policy, never a third toggle.\n"
    "            onSetSpatialAudioPreference?.let { setPreference ->\n"
    "                val isAutomatic = spatialPreference == SpatialAudioPreference.AUTO &&\n"
    "                    (spatialAudioDecision == null || spatialAudioDecision.reason in setOf(\n"
    "                        SpatialAudioDecisionReason.AUTO_DEFAULT_ON,\n"
    "                        SpatialAudioDecisionReason.AUTO_NO_PROFILE,\n"
    "                        SpatialAudioDecisionReason.AUTO_INSUFFICIENT_EVIDENCE,\n"
    "                        SpatialAudioDecisionReason.AUTO_LOW_INDEPENDENT_EVIDENCE,\n"
    "                        SpatialAudioDecisionReason.AUTO_UNCERTAIN,\n"
    "                        SpatialAudioDecisionReason.AUTO_LEARNED_ON,\n"
    "                        SpatialAudioDecisionReason.AUTO_LEARNED_OFF,\n"
    "                    ))\n"
    "                val effectiveOn = when {\n"
    "                    spatialPreference == SpatialAudioPreference.FORCE_OFF -> false\n"
    "                    spatialAudioDecision != null -> spatialAudioDecision.enabled\n"
    "                    else -> true\n"
    "                }\n"
    "                val greenAutoOn = isAutomatic && effectiveOn\n"
    "                val status = when {\n"
    "                    isAutomatic && effectiveOn -> R.string.spatial_audio_auto_on\n"
    "                    isAutomatic -> R.string.spatial_audio_auto_off\n"
    "                    effectiveOn -> R.string.spatial_audio_mode_on\n"
    "                    else -> R.string.spatial_audio_mode_off\n"
    "                }\n"
    "                ActionRow(\n"
    "                    icon = SfIcons.SurroundSound,\n"
    "                    label = stringResource(R.string.spatial_audio),\n"
    "                    value = stringResource(status),\n"
    "                    tint = if (greenAutoOn) Color(0xFF34C759) else null,\n"
    "                    accent = if (greenAutoOn) Color(0xFF34C759) else palette.accent,\n"
    "                    onClick = {\n"
    "                        val nextPreference = if (effectiveOn) SpatialAudioPreference.FORCE_OFF else SpatialAudioPreference.FORCE_ON\n"
    "                        setPreference(nextPreference)\n"
    "                        spatialPreference = nextPreference\n"
    "                    },\n"
    "                )\n"
    "            }\n",
)
replace_once(MENU, "onToggleSpatialAudio != null", "onSetSpatialAudioPreference != null")
MAIN = "app/src/main/java/com/music/zenith/MainActivity.kt"
insert_after(MAIN, "import com.music.zenith.data.settings.AppSettings\n", "import com.music.zenith.playback.SpatialAudioPreference\n")
replace_once(MAIN,
    "            val trackSpatialOverrides by AppSettings.spatialAudioByTrack.collectAsStateWithLifecycle()\n"
    "            val spatialAudioGlobal by AppSettings.spatialAudio.collectAsStateWithLifecycle()\n"
    "            val spatialAudioOn = trackSpatialOverrides[song.videoId] ?: spatialAudioGlobal\n",
    "            val trackSpatialPreferences by AppSettings.spatialAudioTrackPreferences.collectAsStateWithLifecycle()\n"
    "            val spatialAudioPreference = trackSpatialPreferences[song.videoId] ?: SpatialAudioPreference.AUTO\n"
    "            val currentSpatialAudioTrackId by AppSettings.spatialAudioCurrentTrackId.collectAsStateWithLifecycle()\n"
    "            val currentSpatialAudioDecision by AppSettings.spatialAudioCurrentDecision.collectAsStateWithLifecycle()\n"
    "            val spatialAudioDecision = if (fromPlayer && currentSpatialAudioTrackId == song.videoId) currentSpatialAudioDecision else null\n")
replace_once(MAIN,
    "                onToggleSpatialAudio = if (fromPlayer && song.videoId.isNotBlank()) {\n"
    "                    { AppSettings.setSpatialAudioForTrack(song.videoId, !spatialAudioOn) }\n"
    "                } else {\n"
    "                    null\n"
    "                },\n"
    "                spatialAudioOn = spatialAudioOn,\n",
    "                onSetSpatialAudioPreference = if (fromPlayer && song.videoId.isNotBlank()) {\n"
    "                    { preference -> AppSettings.setSpatialAudioPreference(song.videoId, song.title, song.artist, preference) }\n"
    "                } else {\n"
    "                    null\n"
    "                },\n"
    "                spatialAudioPreference = spatialAudioPreference,\n"
    "                spatialAudioDecision = spatialAudioDecision,\n")

UI = "app/src/main/java/com/music/zenith/ui/screens/SettingsSheet.kt"

insert_after(UI, "    val spatialAudio by AppSettings.spatialAudio.collectAsStateWithLifecycle()\n",

    "    val spatialAudioOnPhoneSpeaker by AppSettings.spatialAudioOnPhoneSpeaker.collectAsStateWithLifecycle()\n"
    "    val spatialAudioLearningEnabled by AppSettings.spatialAudioLearningEnabled.collectAsStateWithLifecycle()\n")
# Keep the upstream two-state IosSwitch; true maps to Auto and false to Off.
insert_after(UI,
    "            }\n            // The system panel is not listed here as well. A device with a\n",
    "\n"
    "            val spatialSpeakerTitle = stringResource(R.string.spatial_audio_phone_speaker)\n"
    "            row(spatialSpeakerTitle, \"spatial audio\", \"speaker\", \"output\") {\n"
    "                SettingsRow(\n"
    "                    title = spatialSpeakerTitle,\n"
    "                    subtitle = stringResource(R.string.spatial_audio_phone_speaker_subtitle),\n"
    "                    trailing = { IosSwitch(checked = spatialAudioOnPhoneSpeaker, onCheckedChange = AppSettings::setSpatialAudioOnPhoneSpeaker) },\n"
    "                    onClick = { AppSettings.setSpatialAudioOnPhoneSpeaker(!spatialAudioOnPhoneSpeaker) },\n"
    "                )\n"
    "            }\n"
    "            val spatialLearningTitle = stringResource(R.string.spatial_audio_learning_enabled)\n"
    "            row(spatialLearningTitle, \"spatial audio\", \"learning\", \"automatic\") {\n"
    "                SettingsRow(\n"
    "                    title = spatialLearningTitle,\n"
    "                    subtitle = stringResource(R.string.spatial_audio_learning_enabled_subtitle),\n"
    "                    trailing = { IosSwitch(checked = spatialAudioLearningEnabled, onCheckedChange = AppSettings::setSpatialAudioLearningEnabled) },\n"
    "                    onClick = { AppSettings.setSpatialAudioLearningEnabled(!spatialAudioLearningEnabled) },\n"
    "                )\n"
    "            }\n")
STRINGS = "app/src/main/res/values/strings.xml"
insert_after(STRINGS,
    '    <string name="spatial_audio_subtitle">Widens stereo tracks for a more immersive feel</string>\n',
    '    <string name="spatial_audio_mode_auto">Auto</string>\n'
    '    <string name="spatial_audio_auto_on">Auto · On</string>\n'
    '    <string name="spatial_audio_auto_off">Auto · Off</string>\n'
    '    <string name="spatial_audio_mode_on">On</string>\n'
    '    <string name="spatial_audio_mode_off">Off</string>\n'
    '    <string name="spatial_audio_phone_speaker">Allow on phone speaker</string>\n'
    '    <string name="spatial_audio_phone_speaker_subtitle">Off by default. Headphones and external audio outputs remain eligible.</string>\n'
    '    <string name="spatial_audio_learning_enabled">Personal learning</string>\n'
    '    <string name="spatial_audio_learning_enabled_subtitle">Learn from explicit per-track On or Off choices. Audio is never stored.</string>\n',
)

print("FC Spatial Audio integration applied; processor baseline and all patch anchors matched.")
