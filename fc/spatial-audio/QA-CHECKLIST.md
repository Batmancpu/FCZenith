# FC Zenith Spatial Audio QA

Run this checklist for every upstream baseline or adapter change before
publishing a production APK.

- [ ] Upstream stable tag and merged source commit are current.
- [ ] The FC integration script confirms its reviewed baseline is an ancestor.
- [ ] The upstream `SpatialAudioProcessor.kt` matches the reviewed baseline.
- [ ] Every patch anchor appears exactly once; drift fails the build before release.
- [ ] Production unit tests pass, including SpatialAudioLearningTest.
- [ ] APK package ID is `com.spotify.music`; version code/name match FC metadata.
- [ ] Existing signing certificate fingerprint is unchanged.
- [ ] Exact signed APK is attached to the FC release and visible to Obtainium.
- [ ] Install over the existing signed app without uninstall/relogin.
- [ ] Stereo PCM16 44.1/48/96 kHz and PCM float paths behave as expected.
- [ ] Mono and multichannel tracks remain playable and bypass widening.
- [ ] Dolby Atmos, phone speaker (default), unavailable DSP and bit-perfect output bypass correctly.
- [ ] Headphones, Bluetooth and USB DAC routes are checked.
- [ ] Crossfade outgoing/incoming tracks use independent processor contexts.
- [ ] Player menu exposes only binary On/Off; Auto remains an internal policy.
- [ ] Automatic On appears as green “Auto · On”; automatic Off appears as neutral “Auto · Off”.
- [ ] Global Off and safety gates override per-track choices.
- [ ] Four similar explicit Off labels can teach Auto Off; four similar explicit On labels can teach Auto On.
- [ ] Disabling learning stops profiling without disabling the effect.
- [ ] Profiles and feedback remain local; no raw audio, credentials or listening history are uploaded.

## Scope and removal

This is stereo widening/soundstage virtualization, not object-based Atmos or an
HRTF binaural renderer. Only explicit per-track On/Off actions are training
labels; likes, scrobbles and playlist membership are not assumed to mean a
listener prefers widening.

The entire feature is isolated under `fc/spatial-audio/` plus one build step.
Remove that directory, the workflow step named
`Apply permanent FC Spatial Audio integration`, and its release-note entry to
return the build to unmodified upstream audio behavior. The release workflow
restores temporary edits to upstream files before committing, so no app-source
cleanup commit or special merge is required.
