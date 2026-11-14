# Temporary FC Spatial Audio quality experiment

This layer is **optional and removable**. It is applied only to the generated
Android sources in the GitHub Actions build workspace, *after*
`fc/spatial-audio/apply.py`. The existing tested permanent FC Spatial Audio
engine is preserved byte-for-byte in `fc/spatial-audio/SpatialAudioProcessor.kt`
and `SpatialAudioLearning.kt`.

## What changes when enabled

- Reduce mid/side gain from 2.5 to 2.0 and crossfeed from 0.20 to 0.16; shorten
  crossfeed delay to 12 ms to reduce aggressive image expansion.
- Calculate the one-pole crossfeed low-pass from a 1.6-kHz cutoff and the actual
  sample rate instead of reusing a fixed coefficient at 44.1/48/96 kHz.
- Add a common-L/R, fast-attack sample-peak limiter with 0.98 ceiling and
  250-ms release. It protects the DSP path while preserving a bit-exact bypass
  when disabled. This is **not a true-peak/oversampling limiter**; real-device
  listening and intersample-peak verification remain necessary.
- Remove per-block ByteBuffer duplication during PCM16 analysis and replace
  delay ring modulo with a wrap comparison. Publish completed audio-profile
  fingerprints on one bounded pre-started worker, not on the audio callback.
- Increase the effective-independent-evidence requirement from 3.0 to 3.5 and
  raise the Auto On/Off confidence gates to 80% / 85%, respectively. Existing
  explicit On/Off choices and all safety bypasses remain authoritative.
- Add `SpatialAudioQualityTest.kt` to the production unit-test suite covering
  sample peaks and numeric safety across 44.1/48/96 kHz, PCM16 bypass and
  limiting, bit-exact disabled Float32, mono bypass, background completion,
  toggling and conservative Auto learning.

## Release and rollback

Set `enabled=true` in `config.properties` to opt in. Set `enabled=false`
to return to the original FC Spatial Audio implementation. Committing a flag
change to main triggers a new numbered release, with strictly increasing
Android versionCode and unchanged signing certificate, app package, brrMusic
branding, Fluid Cloud compatibility and existing preferences.

The workflow restores every generated Kotlin file and test to the upstream
checkout before committing to main. The stable integration assets themselves
remain unchanged. Missing exact patch anchors abort the release.

## QA evidence and limitations

CI unit tests and an APK build can establish compilation, the algorithmic
sample-peak ceiling, deterministic bypass and release signing. They cannot
certify subjective sound quality, perceptual spaciousness, end-to-end true
peaks or device-specific output behavior.

Before declaring this engine the preferred permanent version, perform A/B
listening against the baseline signed APK on wired headphones, Bluetooth and
USB DAC, with low and high volumes. Verify phone-speaker and Dolby Atmos
bypasses, Fluid Cloud and crossfade behavior on physical devices. Look for
vocal coloration, pumping, fatigue, clipping and dropouts. If regressions
appear, switch the flag off and issue a new release without changing
upstream application code.
