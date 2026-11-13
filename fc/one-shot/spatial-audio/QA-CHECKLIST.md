# Spatial Audio QA

- [ ] Upstream main/stable and target-version rechecked.
- [ ] Unit tests pass.
- [ ] Package ID remains com.spotify.music.
- [ ] Signing SHA-256 remains b94469c80f38f83d3f866c7f4dd82bf44b106b70b403c8aad5239f19f2430a83.
- [ ] Exact APK Music-v15.7.0.13.apk is attached and visible to Obtainium.
- [ ] Install over the existing signed app without uninstall/relogin.
- [ ] Stereo PCM16 44.1/48/96 kHz and PCM float.
- [ ] Mono and multichannel files remain playable and bypass widening.
- [ ] Atmos bypasses; phone speaker bypasses by default.
- [ ] Headphones, Bluetooth and USB DAC routes tested; Bit-perfect DSP bypass verified.
- [ ] Crossfade outgoing/incoming tracks use independent processor contexts.
- [ ] Per-track Auto→On→Off→Auto persists after force-stop.
- [ ] Global Off and safety gates override per-track choices.
- [ ] Auto defaults On; four similar Off labels can teach Auto Off.
- [ ] Learning Off stops profiling but does not disable the effect.
- [ ] Confirm no audio, credentials or listening history are uploaded.
