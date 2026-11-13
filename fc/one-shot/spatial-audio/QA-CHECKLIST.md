# Spatial Audio QA

- [ ] Upstream main/stable and target-version rechecked.
- [ ] Unit tests pass.
- [ ] Release APK package ID is com.spotify.music (verified by aapt before signing).
- [ ] Signing SHA-256 remains b94469c80f38f83d3f866c7f4dd82bf44b106b70b403c8aad5239f19f2430a83.
- [ ] Exact APK Music-v15.7.0.14.apk is attached and visible to Obtainium.
- [ ] Install over the existing signed app without uninstall/relogin.
- [ ] Stereo PCM16 44.1/48/96 kHz and PCM float.
- [ ] Mono and multichannel files remain playable and bypass widening.
- [ ] Atmos bypasses; phone speaker bypasses by default.
- [ ] Headphones, Bluetooth and USB DAC routes tested; Bit-perfect DSP bypass verified.
- [ ] Crossfade outgoing/incoming tracks use independent processor contexts.
- [ ] Player menu exposes only binary On/Off; Auto is not a third toggle.
- [ ] Automatic On appears as green “Auto · On”; automatic Off appears as neutral “Auto · Off”.
- [ ] Global Off and safety gates override per-track choices.
- [ ] Auto defaults On; four similar explicit Off labels can teach Auto Off.
- [ ] Four similar explicit On labels can teach Auto On.
- [ ] Learning Off stops profiling but does not disable the effect.
- [ ] Confirm no audio, credentials or listening history are uploaded.
