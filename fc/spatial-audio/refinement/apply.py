#!/usr/bin/env python3
"""Second, independently reversible refinement over the existing FC quality experiment.

No tracked upstream app sources are modified: production CI applies this only to
its disposable build checkout. All anchors are strict; drift aborts the build.
"""
from pathlib import Path
import re
import shutil

ROOT = Path(__file__).resolve().parents[3]
DIR = Path(__file__).resolve().parent
SPATIAL = ROOT / "app/src/main/java/com/music/zenith/playback/SpatialAudioProcessor.kt"
CHAIN = ROOT / "app/src/main/java/com/music/zenith/playback/audio/DspChain.kt"
TEST = ROOT / "app/src/test/java/com/music/zenith/playback/SpatialAudioRefinementTest.kt"


def replace(text: str, before: str, after: str, count: int = 1) -> str:
    actual = text.count(before)
    if actual != count:
        raise SystemExit(f"FC refinement anchor failed (expected {count}, got {actual}): {before[:95]!r}")
    return text.replace(before, after)


def refine_processor(src: str) -> str:
    # Existing experimental engine and its fallback PCM16 limiter remain intact.
    # Base stereo (including centered vocals and low bass) is preserved. Only
    # higher-frequency stereo difference receives extra width.
    src = replace(src,
        "    private var lowpassRight = 0f\n",
        "    private var lowpassRight = 0f\n"
        "    private var bassSideLow = 0f\n"
        "    private var bassSideAlpha = 0f\n",
    )
    src = replace(src,
        "            lowpassCoeff = (1.0 - kotlin.math.exp(\n"
        "                -2.0 * kotlin.math.PI * 1600.0 / sampleRate.toDouble()\n"
        "            )).toFloat()\n",
        "            lowpassCoeff = (1.0 - kotlin.math.exp(\n"
        "                -2.0 * kotlin.math.PI * 1600.0 / sampleRate.toDouble()\n"
        "            )).toFloat()\n"
        "            bassSideAlpha = (1.0 - kotlin.math.exp(\n"
        "                -2.0 * kotlin.math.PI * 140.0 / sampleRate.toDouble()\n"
        "            )).toFloat()\n",
    )
    src = replace(src, "        lowpassRight = 0f\n",
        "        lowpassRight = 0f\n        bassSideLow = 0f\n", count=3)
    src = replace(src, "        var lpR = lowpassRight\n",
        "        var lpR = lowpassRight\n        var sideLow = bassSideLow\n", count=2)
    old = (
        "            val mid = (left + right) * 0.5f\n"
        "            val side = (left - right) * 0.5f * widthGain\n"
        "            var widenedLeft = mid + side\n"
        "            var widenedRight = mid - side\n"
        "\n"
        "            val delayedRight = delayRight[dIdx]\n"
        "            val delayedLeft = delayLeft[dIdx]\n"
        "            lpL += lowpassCoeff * (delayedRight - lpL)\n"
        "            lpR += lowpassCoeff * (delayedLeft - lpR)\n"
        "            widenedLeft += lpL * crossfeedGain\n"
        "            widenedRight += lpR * crossfeedGain\n"
    )
    new = (
        "            val mid = (left + right) * 0.5f\n"
        "            val side = (left - right) * 0.5f\n"
        "            sideLow += bassSideAlpha * (side - sideLow)\n"
        "            // Unity base side, widen only above the low-frequency side shelf.\n"
        "            val widenedSide = side + (side - sideLow) * (widthGain * outputGain - 1f)\n"
        "\n"
        "            val delayedRight = delayRight[dIdx]\n"
        "            val delayedLeft = delayLeft[dIdx]\n"
        "            lpL += lowpassCoeff * (delayedRight - lpL)\n"
        "            lpR += lowpassCoeff * (delayedLeft - lpR)\n"
        "            // Remove the common-mode contribution: mono/center is not colored.\n"
        "            val crossSide = (lpL - lpR) * (0.5f * crossfeedGain)\n"
        "            val widenedLeft = mid + widenedSide + crossSide\n"
        "            val widenedRight = mid - widenedSide - crossSide\n"
    )
    src = replace(src, old, new, count=2)
    src = replace(src,
        "            val wetLeft = widenedLeft * outputGain\n"
        "            val wetRight = widenedRight * outputGain\n",
        "            val wetLeft = widenedLeft\n"
        "            val wetRight = widenedRight\n", count=2)
    src = replace(src, "        lowpassRight = lpR\n",
        "        lowpassRight = lpR\n        bassSideLow = sideLow\n", count=2)

    # The independent/direct processor keeps its own 0.98 peak limiter for
    # safety. Within DspChain, delegate limiting to the existing last stage
    # and avoid two gain envelopes acting on the same audio.
    src = replace(src,
        "    private val peakCeiling = 0.98f\n",
        "    private val peakCeiling = 0.98f\n"
        "    @Volatile var delegatePeakProtection = false\n"
        "    /** Only read on the audio thread, including during the 300ms off ramp. */\n"
        "    val isRenderingWet: Boolean get() = enabled || mix > 0f\n",
    )
    src = replace(src,
        "            val peak = maxOf(kotlin.math.abs(mixedLeft), kotlin.math.abs(mixedRight))\n"
        "            val required = if (mix > 0f && peak > peakCeiling) peakCeiling / peak else 1f\n"
        "            limiterGain = if (required < limiterGain) required else minOf(1f, limiterGain + releaseStep)\n"
        "            block.samples[idx] = mixedLeft * limiterGain\n"
        "            block.samples[idx + 1] = mixedRight * limiterGain",
        "            if (delegatePeakProtection) {\n"
        "                // LoudnessProcessor catches the fully shaped Float32 result.\n"
        "                block.samples[idx] = mixedLeft\n"
        "                block.samples[idx + 1] = mixedRight\n"
        "            } else {\n"
        "                val peak = maxOf(kotlin.math.abs(mixedLeft), kotlin.math.abs(mixedRight))\n"
        "                val required = if (mix > 0f && peak > peakCeiling) peakCeiling / peak else 1f\n"
        "                limiterGain = if (required < limiterGain) required else minOf(1f, limiterGain + releaseStep)\n"
        "                block.samples[idx] = mixedLeft * limiterGain\n"
        "                block.samples[idx + 1] = mixedRight * limiterGain\n"
        "            }",
    )
    return src


def refine_chain(src: str) -> str:
    src = replace(src,
        "    private var currentSampleRate: Int = 0\n",
        "    init { spatial.delegatePeakProtection = true }\n\n"
        "    private var currentSampleRate: Int = 0\n",
    )
    src = replace(src,
        "        loudness.holdLimiter = equalizer.isShaping\n",
        "        // Keep final protection active for Spatial Audio and its off ramp,\n"
        "        // even with EQ and normalization disabled.\n"
        "        loudness.holdLimiter = equalizer.isShaping || spatial.isRenderingWet\n",
    )
    return src


def main() -> None:
    flags = re.findall(r"(?m)^enabled=(true|false)$", (DIR / "config.properties").read_text())
    if len(flags) != 1:
        raise SystemExit("FC refinement must have exactly one enabled=true/false flag")
    if flags[0] == "false":
        print("FC refinement disabled: original experimental engine stays unchanged.")
        return
    base_experiment = re.findall(
        r"(?m)^enabled=(true|false)$",
        (DIR.parent / "experimental/config.properties").read_text(),
    )
    if base_experiment != ["true"]:
        raise SystemExit("FC refinement requires the original experiment enabled")
    if not SPATIAL.is_file() or not CHAIN.is_file():
        raise SystemExit("Apply stable FC Spatial Audio and quality experiment first")
    p = refine_processor(SPATIAL.read_text(encoding="utf-8"))
    c = refine_chain(CHAIN.read_text(encoding="utf-8"))
    # All anchors and inputs checked before writing anything.
    SPATIAL.write_text(p, encoding="utf-8")
    CHAIN.write_text(c, encoding="utf-8")
    TEST.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(DIR / "SpatialAudioRefinementTest.kt", TEST)
    print("FC refined engine: stable center, high-band stereo widening, unified DSP peak safety.")


if __name__ == "__main__":
    main()
