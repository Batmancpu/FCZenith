from pathlib import Path

ROOT = Path.cwd()

def replace_once(path_text: str, old: str, new: str) -> None:
    path = ROOT / path_text
    source = path.read_text(encoding="utf-8")
    count = source.count(old)
    if count != 1:
        raise SystemExit(f"Expected one patch anchor in {path_text}, found {count}")
    path.write_text(source.replace(old, new, 1), encoding="utf-8")

LEARNER = "app/src/main/java/com/music/zenith/playback/SpatialAudioLearning.kt"
replace_once(
    LEARNER,
    "    AUTO_DISABLED,\n    AUTO_NO_PROFILE,\n",
    "    AUTO_DISABLED,\n    AUTO_DEFAULT_ON,\n    AUTO_NO_PROFILE,\n",
)
replace_once(
    LEARNER,
    "    private const val MIN_NEIGHBOURS = 4\n"
    "    private const val MAX_NEIGHBOURS = 9\n"
    "    private const val MAX_DISTANCE = 2.5\n"
    "    private const val CONFIDENCE_THRESHOLD = 0.82\n"
    "    private const val MIN_EFFECTIVE_NEIGHBOURS = 3.0\n",
    "    private const val MIN_NEIGHBOURS = 3\n"
    "    private const val MAX_NEIGHBOURS = 9\n"
    "    private const val MAX_DISTANCE = 2.5\n"
    "    private const val CONFIDENCE_THRESHOLD = 0.66\n"
    "    private const val MIN_EFFECTIVE_NEIGHBOURS = 2.0\n",
)
replace_once(
    LEARNER,
    "     * Hard safety/output gates always win. Auto needs four distinct labelled\n"
    "     * tracks, at least three effective neighbours, and 82% weighted agreement.\n"
    "     * Cold starts and ambiguous mixes remain off.\n",
    "     * Hard safety/output gates always win. Auto favours On for eligible\n"
    "     * stereo tracks until local evidence is strong enough to override it.\n"
    "     * At least three similar labelled tracks and two effective neighbours\n"
    "     * are required; 66% weighted agreement is needed to learn On or Off.\n",
)
replace_once(
    LEARNER,
    "        if (profile == null || !profile.isUsable) {\n"
    "            return SpatialAudioDecision(false, SpatialAudioDecisionReason.AUTO_NO_PROFILE)\n"
    "        }\n",
    "        if (profile == null || !profile.isUsable) {\n"
    "            return SpatialAudioDecision(true, SpatialAudioDecisionReason.AUTO_DEFAULT_ON)\n"
    "        }\n",
)
replace_once(
    LEARNER,
    "            else -> SpatialAudioDecisionReason.AUTO_UNCERTAIN\n"
    "        }\n"
    "        return SpatialAudioDecision(\n"
    "            enabled = reason == SpatialAudioDecisionReason.AUTO_LEARNED_ON,\n",
    "            else -> SpatialAudioDecisionReason.AUTO_DEFAULT_ON\n"
    "        }\n"
    "        return SpatialAudioDecision(\n"
    "            enabled = reason != SpatialAudioDecisionReason.AUTO_LEARNED_OFF,\n",
)
replace_once(
    LEARNER,
    "    ) = SpatialAudioDecision(\n"
    "        enabled = false,\n"
    "        reason = SpatialAudioDecisionReason.AUTO_INSUFFICIENT_EVIDENCE,\n",
    "    ) = SpatialAudioDecision(\n"
    "        enabled = true,\n"
    "        reason = SpatialAudioDecisionReason.AUTO_DEFAULT_ON,\n",
)

SETTINGS = "app/src/main/java/com/music/zenith/ui/screens/SettingsSheet.kt"
replace_once(
    SETTINGS,
    "        SpatialAudioDecisionReason.AUTO_DISABLED -> stringResource(R.string.spatial_audio_decision_learning_disabled)\n",
    "        SpatialAudioDecisionReason.AUTO_DISABLED -> stringResource(R.string.spatial_audio_decision_learning_disabled)\n"
    "        SpatialAudioDecisionReason.AUTO_DEFAULT_ON -> stringResource(R.string.spatial_audio_decision_default_on)\n",
)

STRINGS = "app/src/main/res/values/strings.xml"
replace_once(
    STRINGS,
    '    <string name="spatial_audio_learning_body">Auto measures the first eight seconds of eligible stereo audio. It stores mix statistics, not audio. At least four similar tracks and 82% agreement are needed before Auto turns widening on. Uncertain tracks stay off. No genre or population data is guessed.</string>\n',
    '    <string name="spatial_audio_learning_body">Auto favours On for eligible stereo tracks. Three similar labelled tracks and at least two effective independent neighbours are required before strong 66% weighted agreement can override the default. Sparse or mixed evidence stays On; explicit track choices and output safety gates take priority. Only local mix statistics are stored, never audio.</string>\n',
)
replace_once(
    STRINGS,
    '    <string name="spatial_audio_decision_learning_disabled">Personal learning is paused, so Auto stays off.</string>\n',
    '    <string name="spatial_audio_decision_learning_disabled">Personal learning is paused, so Auto stays off.</string>\n'
    '    <string name="spatial_audio_decision_default_on">Auto keeps this eligible track On by default while similar-track evidence is sparse or mixed. Strong Off feedback can teach Auto to disable it.</string>\n',
)

TESTS = "app/src/test/java/com/music/zenith/playback/SpatialAudioLearningTest.kt"
replace_once(
    TESTS,
    "    fun coldStartNeverWidensWithoutEvidence() {\n"
    "        val result = decide()\n"
    "        assertFalse(result.enabled)\n"
    "        assertEquals(SpatialAudioDecisionReason.AUTO_INSUFFICIENT_EVIDENCE, result.reason)\n"
    "    }\n",
    "    fun coldStartFavoursOnUntilPersonalEvidenceExists() {\n"
    "        val result = decide()\n"
    "        assertTrue(result.enabled)\n"
    "        assertEquals(SpatialAudioDecisionReason.AUTO_DEFAULT_ON, result.reason)\n"
    "    }\n",
)
replace_once(
    TESTS,
    "    fun mixedEvidenceDefaultsOffRatherThanGuessing() {\n"
    "        val result = decide(examples = listOf(\n"
    "            example(\"one\", SpatialAudioFeedback.ON),\n"
    "            example(\"two\", SpatialAudioFeedback.ON),\n"
    "            example(\"three\", SpatialAudioFeedback.OFF),\n"
    "            example(\"four\", SpatialAudioFeedback.OFF),\n"
    "        ))\n"
    "        assertFalse(result.enabled)\n"
    "        assertEquals(SpatialAudioDecisionReason.AUTO_UNCERTAIN, result.reason)\n"
    "    }\n",
    "    fun mixedEvidenceKeepsTheOnBiasedDefault() {\n"
    "        val result = decide(examples = listOf(\n"
    "            example(\"one\", SpatialAudioFeedback.ON),\n"
    "            example(\"two\", SpatialAudioFeedback.ON),\n"
    "            example(\"three\", SpatialAudioFeedback.OFF),\n"
    "            example(\"four\", SpatialAudioFeedback.OFF),\n"
    "        ))\n"
    "        assertTrue(result.enabled)\n"
    "        assertEquals(SpatialAudioDecisionReason.AUTO_DEFAULT_ON, result.reason)\n"
    "    }\n",
)
replace_once(
    TESTS,
    "    fun fourOfFiveChoicesDoNotCrossTheEightyTwoPercentConfidenceGate() {\n"
    "        val result = decide(examples = listOf(\n"
    "            example(\"one\", SpatialAudioFeedback.ON),\n"
    "            example(\"two\", SpatialAudioFeedback.ON),\n"
    "            example(\"three\", SpatialAudioFeedback.ON),\n"
    "            example(\"four\", SpatialAudioFeedback.ON),\n"
    "            example(\"five\", SpatialAudioFeedback.OFF),\n"
    "        ))\n"
    "        assertFalse(result.enabled)\n"
    "        assertEquals(SpatialAudioDecisionReason.AUTO_UNCERTAIN, result.reason)\n"
    "    }\n",
    "    fun fourOfFiveChoicesTeachAutoToPreferOnAtTheNewThreshold() {\n"
    "        val result = decide(examples = listOf(\n"
    "            example(\"one\", SpatialAudioFeedback.ON),\n"
    "            example(\"two\", SpatialAudioFeedback.ON),\n"
    "            example(\"three\", SpatialAudioFeedback.ON),\n"
    "            example(\"four\", SpatialAudioFeedback.ON),\n"
    "            example(\"five\", SpatialAudioFeedback.OFF),\n"
    "        ))\n"
    "        assertTrue(result.enabled)\n"
    "        assertEquals(SpatialAudioDecisionReason.AUTO_LEARNED_ON, result.reason)\n"
    "    }\n",
)
replace_once(
    TESTS,
    "    fun distantMixesDoNotCountEvenWhenArtistMatches() {\n"
    "        val distant = profile(width = 12f, correlation = -0.95f, lowSide = 9f, high = 0.98f, crest = 20f)\n"
    "        val result = decide(examples = listOf(\n"
    "            example(\"one\", SpatialAudioFeedback.ON).copy(profile = distant),\n"
    "            example(\"two\", SpatialAudioFeedback.ON).copy(profile = distant),\n"
    "            example(\"three\", SpatialAudioFeedback.ON).copy(profile = distant),\n"
    "        ))\n"
    "        assertFalse(result.enabled)\n"
    "        assertEquals(SpatialAudioDecisionReason.AUTO_INSUFFICIENT_EVIDENCE, result.reason)\n"
    "    }\n",
    "    fun distantMixesDoNotCountAndKeepTheOnBiasedDefault() {\n"
    "        val distant = profile(width = 12f, correlation = -0.95f, lowSide = 9f, high = 0.98f, crest = 20f)\n"
    "        val result = decide(examples = listOf(\n"
    "            example(\"one\", SpatialAudioFeedback.ON).copy(profile = distant),\n"
    "            example(\"two\", SpatialAudioFeedback.ON).copy(profile = distant),\n"
    "            example(\"three\", SpatialAudioFeedback.ON).copy(profile = distant),\n"
    "        ))\n"
    "        assertTrue(result.enabled)\n"
    "        assertEquals(SpatialAudioDecisionReason.AUTO_DEFAULT_ON, result.reason)\n"
    "    }\n",
)
replace_once(
    TESTS,
    "        assertFalse(result.enabled)\n"
    "        assertEquals(SpatialAudioDecisionReason.AUTO_NO_PROFILE, result.reason)\n",
    "        assertTrue(result.enabled)\n"
    "        assertEquals(SpatialAudioDecisionReason.AUTO_DEFAULT_ON, result.reason)\n",
)
replace_once(
    TESTS,
    "    @Test\n"
    "    fun mixedEvidenceKeepsTheOnBiasedDefault() {\n",
    "    @Test\n"
    "    fun threeSimilarExplicitOffChoicesCanOverrideTheDefault() {\n"
    "        val result = decide(examples = listOf(\n"
    "            example(\"one\", SpatialAudioFeedback.OFF),\n"
    "            example(\"two\", SpatialAudioFeedback.OFF),\n"
    "            example(\"three\", SpatialAudioFeedback.OFF),\n"
    "        ))\n"
    "        assertFalse(result.enabled)\n"
    "        assertEquals(SpatialAudioDecisionReason.AUTO_LEARNED_OFF, result.reason)\n"
    "    }\n"
    "\n"
    "    @Test\n"
    "    fun mixedEvidenceKeepsTheOnBiasedDefault() {\n",
)

print("Applied the one-release On-biased Spatial Audio policy.")
