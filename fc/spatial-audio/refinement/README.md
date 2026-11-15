# Experimental Spatial Audio refinement — second reversible layer

This layer applies after `fc/spatial-audio/apply.py` and
`fc/spatial-audio/experimental/apply.py`. The stable FC sources and the
previous experimental layer are deliberately unchanged in Git.

## Exact design

1. Retain existing 2.0x width setting, 12-ms low-passed crossfeed and per-track
   learning. Use a 140-Hz low-pass of the stereo side signal, widening primarily
   the remaining side signal; original mid and baseline side are not globally
   attenuated. This is a modest **spectral shelf**, not a full crossover, HRTF
   model or Dolby Atmos system.
2. Make the wet mid signal exactly the original mid signal. Remove delayed
   crossfeed's common-mode component: a dual-mono/centered signal remains
   unchanged. This does **not** isolate vocals from instruments.
3. Keep the original sample-peak limiter for independent SpatialAudioProcessor
   and PCM16 legacy processing. The normal Float32 DspChain delegates peak
   protection to its *existing* final LoudnessProcessor, with holdLimiter active
   when Spatial Audio is on or its 300-ms wet/dry transition is ongoing.
   The final limiter sees Spatial -> EQ -> Transition as one signal.
4. Preserve true bypass when all DSP is disabled, stereo-only routing, Dolby
   Atmos bypass, phone-speaker policy, USB/bit-perfect policy, source fallback,
   user profiles and learning, the package ID and signature.

## What this might and might not improve

- Possible: more stable centered voices, less exaggerated stereo deep bass,
  reduced double-limiter gain envelopes and computations in the normal
  Float32 chain.
- Not proven: better listener preference, lower battery drain, fewer underruns,
  true-peak compliance or device-specific behavior.
- Possible drawback: side-channel tone and spatial impression may change;
  conservative crossfeed adjustment can reduce apparent width for some mixes.
- The final limiter has a 0.985 sample ceiling (vs 0.980 previously at the
  Spatial Audio stage). This is not a true-peak limiter.

## Automatic verification

The isolated PR workflow runs ALL production Android unit tests, including:
- direct processor and legacy PCM16 bypass/limiting tests from the previous layer
- new center preservation, 40 Hz vs 2 kHz stereo-difference behavior
- final-chain clipping protection at 44.1/48/96 kHz
- off-ramp peak handling, exact disabled bypass and PCM16 center preservation

The production release verifies signing key, certificate, app package, Android
versionCode monotonicity, and APK publishing. Device listening, Bluetooth/USB
QA, energy profiling and loudness-matched blind listening remain required
before calling the refinement *better*.

## Rollback

Set `enabled=false` in this directory's `config.properties`, merge to main,
and allow the production workflow to publish a new higher-version signed APK.
`fc/version.properties` stores the released `audioRefinement` flag so the
rollback triggers a real version bump even when upstream hasn't changed.

Turning off this refinement leaves the previous optional experiment intact.
To go all the way back to the FC baseline, also disable
`fc/spatial-audio/experimental/config.properties`.
