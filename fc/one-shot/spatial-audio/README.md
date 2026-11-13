FC Zenith Spatial Audio one-release implementation

This isolated overlay is applied only to the pinned test release. The release workflow builds, tests and signs the APK, then restores app source and retires the overlay after confirming the exact asset is published.

Pinned upstream: rezapranata089-bit/Zenith @ c74d5784b3d55d1517d636972ec17f95df073086
Reserved FC target if parent remains 15.7.0: 15.7.0.13

Architecture: reuse the existing ExoPlayer stereo widening/cross-feed DSP; sample an eight-second stereo PCM profile at 1/8 rate; persist scalar statistics only; use bounded top-9 similarity with four explicit labels, three effective neighbours and 75% weighted agreement before Auto can change the default On decision. Each ExoPlayer/crossfade side owns its own track context. The global setting is a binary On/Off master switch: On enables the internal Auto policy, Off disables the effect. The per-track menu is also binary On/Off; Auto is not a third toggle. When Auto selects On the menu shows green “Auto · On”; when Auto selects Off it shows neutral “Auto · Off”. Phone speaker is bypassed by default; Atmos and unavailable DSP are hard bypasses. Local records are capped at 400 and excluded from settings exports.

Audio type caveat: this is stereo virtualization/soundstage widening, not object-based Atmos or an HRTF binaural renderer. Existing sound-shaping constants are intentionally unchanged for the first learning integration test.

Metadata audit: Spotify import exposes title/artist/album but no reliable genre signal; Canvas is artwork/video; ListenBrainz integration submits listens but does not read history. Likes, scrobbles and playlist membership are not valid feedback for whether widening sounds better. Only explicit per-track Spatial Audio On/Off controls train the local learner. No account credentials or raw audio are uploaded.

Release target: Music-v15.7.0.13.apk (versionCode 52). The workflow pins upstream main once, validates the pin against current upstream main and stable, runs production tests, signs with the existing certificate, publishes the exact APK and removes this overlay only after verifying the release asset. Future scheduled runs return to upstream stable releases.

## Privacy and model limits

Only explicit per-track On/Off choices train the learner. Spotify metadata does not provide a dependable genre signal, and likes, scrobbles or playlist membership are not treated as evidence that widening sounded better. No raw audio or account credentials leave the device. This processor performs stereo widening/virtualization, not object-based Atmos or HRTF binaural rendering.
