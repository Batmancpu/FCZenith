#!/usr/bin/env python3
"""Build-only, reversible DSP/learning experiment layered over FC's proven engine.

The stable FC spatial audio source is untouched. All edits target generated
app source AFTER fc/spatial-audio/apply.py and stop on upstream drift.
"""
from pathlib import Path
import re
import shutil

ROOT = Path(__file__).resolve().parents[3]
EXPERIMENT = Path(__file__).resolve().parent
PROCESSOR = ROOT / "app/src/main/java/com/music/zenith/playback/SpatialAudioProcessor.kt"
LEARNER = ROOT / "app/src/main/java/com/music/zenith/playback/SpatialAudioLearning.kt"
TEST = ROOT / "app/src/test/java/com/music/zenith/playback/SpatialAudioQualityTest.kt"


def active() -> bool:
    config = (EXPERIMENT / "config.properties").read_text(encoding="utf-8")
    modes = re.findall(r"(?m)^enabled=(true|false)$", config)
    if len(modes) != 1:
        raise SystemExit("Spatial audio experiment requires exactly one enabled=true/false")
    return modes[0] == "true"


def replace(text: str, before: str, after: str, count: int = 1) -> str:
    hits = text.count(before)
    if hits != count:
        raise SystemExit(
            f"Spatial quality overlay refused to patch: expected {count} anchors "
            f"but found {hits}: {before[:85]!r}"
        )
    return text.replace(before, after)


def upgrade_processor(text: str) -> str:
    # Modest widening and crossfeed changes, not a different DSP architecture.
    text = replace(text, "private val widthGain = 2.5f", "private val widthGain = 2.0f")
    text = replace(text, "private val outputGain = 0.82f", "private val outputGain = 0.86f")
    text = replace(text, "private val crossfeedGain = 0.2f", "private val crossfeedGain = 0.16f")
    text = replace(text, "private val lowpassCoeff = 0.3f", "private var lowpassCoeff = 0.3f")
    text = replace(text, "const val DELAY_MS = 15", "const val DELAY_MS = 12")
    text = replace(
        text,
        "        if (sampleRate > 0) {\n            profileAccumulator?",
        "        if (sampleRate > 0) {\n"
        "            // Stable cutoff in Hz across 44.1, 48 and 96 kHz outputs.\n"
        "            lowpassCoeff = (1.0 - kotlin.math.exp(\n"
        "                -2.0 * kotlin.math.PI * 1600.0 / sampleRate.toDouble()\n"
        "            )).toFloat()\n"
        "            profileAccumulator?",
    )

    # Hard sample-peak ceiling with common L/R gain and fast attack, slow recovery.
    # A shared gain avoids image wandering between channels.
    text = replace(
        text,
        "    private var mix = 0f\n",
        "    private var mix = 0f\n"
        "    private var limiterGain = 1f\n"
        "    private val peakCeiling = 0.98f\n",
    )
    text = replace(
        text, "        val step = rampStep()\n",
        "        val step = rampStep()\n        val releaseStep = limiterReleaseStep()\n",
        count=2,
    )
    text = replace(
        text, "        if (mix == 0f && target == 0f) {\n",
        "        if (mix == 0f && target == 0f) {\n            limiterGain = 1f\n",
        count=2,
    )
    text = replace(
        text,
        "            block.samples[idx] = if (mix >= 1f) wetLeft else left + (wetLeft - left) * mix\n"
        "            block.samples[idx + 1] = if (mix >= 1f) wetRight else right + (wetRight - right) * mix",
        "            val mixedLeft = if (mix >= 1f) wetLeft else left + (wetLeft - left) * mix\n"
        "            val mixedRight = if (mix >= 1f) wetRight else right + (wetRight - right) * mix\n"
        "            val peak = maxOf(kotlin.math.abs(mixedLeft), kotlin.math.abs(mixedRight))\n"
        "            val required = if (mix > 0f && peak > peakCeiling) peakCeiling / peak else 1f\n"
        "            limiterGain = if (required < limiterGain) required else minOf(1f, limiterGain + releaseStep)\n"
        "            block.samples[idx] = mixedLeft * limiterGain\n"
        "            block.samples[idx + 1] = mixedRight * limiterGain",
    )
    text = replace(
        text,
        "            val outLeft = if (mix >= 1f) wetLeft else left + (wetLeft - left) * mix\n"
        "            val outRight = if (mix >= 1f) wetRight else right + (wetRight - right) * mix\n"
        "            outputBuffer.putShort(PcmBoundary.clamp16FromFloat(outLeft))\n"
        "            outputBuffer.putShort(PcmBoundary.clamp16FromFloat(outRight))",
        "            val outLeft = if (mix >= 1f) wetLeft else left + (wetLeft - left) * mix\n"
        "            val outRight = if (mix >= 1f) wetRight else right + (wetRight - right) * mix\n"
        "            val peak = maxOf(kotlin.math.abs(outLeft), kotlin.math.abs(outRight))\n"
        "            val required = if (mix > 0f && peak > peakCeiling) peakCeiling / peak else 1f\n"
        "            limiterGain = if (required < limiterGain) required else minOf(1f, limiterGain + releaseStep)\n"
        "            outputBuffer.putShort(PcmBoundary.clamp16FromFloat(outLeft * limiterGain))\n"
        "            outputBuffer.putShort(PcmBoundary.clamp16FromFloat(outRight * limiterGain))",
    )
    text = replace(
        text,
        "            dIdx = (dIdx + 1) % delaySize",
        "            dIdx++\n            if (dIdx == delaySize) dIdx = 0",
        count=2,
    )
    text = replace(
        text,
        "    private fun rampStep(): Float =\n"
        "        if (sampleRate > 0) 1000f / (sampleRate * RAMP_MS) else 1f\n",
        "    private fun rampStep(): Float =\n"
        "        if (sampleRate > 0) 1000f / (sampleRate * RAMP_MS) else 1f\n"
        "\n"
        "    private fun limiterReleaseStep(): Float =\n"
        "        if (sampleRate > 0) 1000f / (sampleRate * 250f) else 1f\n",
    )
    text = replace(
        text,
        "    override fun onFlush() {\n        snapMix = true\n",
        "    override fun onFlush() {\n        snapMix = true\n        limiterGain = 1f\n",
    )
    text = replace(
        text,
        "    override fun onReset() {\n        delayLeft = FloatArray(0)\n",
        "    override fun onReset() {\n        limiterGain = 1f\n        delayLeft = FloatArray(0)\n",
    )

    # No ByteBuffer duplicate per dry audio block. Use absolute reads with no position changes.
    text = replace(
        text,
        "        val view = input.duplicate().order(ByteOrder.nativeOrder())\n"
        "        val start = view.position()\n"
        "        val availableFrames = view.remaining() / BYTES_PER_FRAME",
        "        val start = input.position()\n"
        "        val availableFrames = input.remaining() / BYTES_PER_FRAME",
    )
    text = replace(
        text,
        "            view.position(start + frame * BYTES_PER_FRAME)\n"
        "            observeFrame(accumulator, view.short.toDouble() * scale, view.short.toDouble() * scale)",
        "            val offset = start + frame * BYTES_PER_FRAME\n"
        "            observeFrame(accumulator, input.getShort(offset).toDouble() * scale,\n"
        "                input.getShort(offset + 2).toDouble() * scale)",
    )
    text = replace(
        text,
        "        val frameCount = inputBuffer.remaining() / BYTES_PER_FRAME\n",
        "        inputBuffer.order(ByteOrder.nativeOrder())\n"
        "        val frameCount = inputBuffer.remaining() / BYTES_PER_FRAME\n",
    )

    # One-shot callbacks are dispatched off the real-time thread on a shared
    # prestarted, bounded worker (no per-block callback allocations).
    text = replace(
        text,
        "        if (profile.isUsable) {\n"
        "            onProfileReady?.invoke(accumulator.trackId, accumulator.title, accumulator.artist, profile)\n"
        "        }\n",
        "        if (profile.isUsable) {\n"
        "            val callback = onProfileReady\n"
        "            if (callback != null) {\n"
        "                val trackId = accumulator.trackId\n"
        "                val title = accumulator.title\n"
        "                val artist = accumulator.artist\n"
        "                PROFILE_DISPATCH.execute { callback(trackId, title, artist, profile) }\n"
        "            }\n"
        "        }\n",
    )
    text = replace(
        text,
        "    private companion object {\n",
        "    private companion object {\n"
        "        // One background worker shared by both crossfade processors; tasks are bounded.\n"
        "        private val PROFILE_DISPATCH = java.util.concurrent.ThreadPoolExecutor(\n"
        "            1, 1, 0L, java.util.concurrent.TimeUnit.MILLISECONDS,\n"
        "            java.util.concurrent.ArrayBlockingQueue<Runnable>(32),\n"
        "            java.util.concurrent.ThreadFactory { task ->\n"
        "                Thread(task, \"FC-SpatialProfile\").apply { isDaemon = true }\n"
        "            },\n"
        "            java.util.concurrent.ThreadPoolExecutor.DiscardPolicy(),\n"
        "        ).apply { prestartAllCoreThreads() }\n"
    )
    return text


def upgrade_learner(text: str) -> str:
    # Be conservative about Auto Off; On remains the safe existing default.
    text = replace(
        text,
        "private const val CONFIDENCE_THRESHOLD = 0.75",
        "private const val CONFIDENCE_THRESHOLD = 0.80\n"
        "    private const val AUTO_OFF_THRESHOLD = 0.85"
    )
    text = replace(
        text,
        "private const val MIN_EFFECTIVE_NEIGHBOURS = 3.0",
        "private const val MIN_EFFECTIVE_NEIGHBOURS = 3.5"
    )
    text = replace(
        text,
        "            onShare <= 1.0 - CONFIDENCE_THRESHOLD -> SpatialAudioDecisionReason.AUTO_LEARNED_OFF",
        "            onShare <= 1.0 - AUTO_OFF_THRESHOLD -> SpatialAudioDecisionReason.AUTO_LEARNED_OFF"
    )
    return text


def main() -> None:
    if not active():
        print("Experimental Spatial Audio quality overlay disabled: original engine unchanged.")
        return
    if not PROCESSOR.is_file() or not LEARNER.is_file():
        raise SystemExit("FC's stable Spatial Audio overlay must run before the experimental overlay")
    processor = upgrade_processor(PROCESSOR.read_text(encoding="utf-8"))
    learner = upgrade_learner(LEARNER.read_text(encoding="utf-8"))
    # All compatibility anchors validated before any source write.
    PROCESSOR.write_text(processor, encoding="utf-8")
    LEARNER.write_text(learner, encoding="utf-8")
    TEST.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(EXPERIMENT / "SpatialAudioQualityTest.kt", TEST)
    print("Experimental Spatial Audio upgrade active: sample-peak safety, rate-aware crossfeed,")
    print("allocation-free PCM16 profiling, async profile callback, guarded Auto learning, DSP tests.")


if __name__ == "__main__":
    main()
