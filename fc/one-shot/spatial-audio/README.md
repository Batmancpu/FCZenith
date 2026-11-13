FC Zenith Spatial Audio preparation

Preparation-only branch; no production source, main, workflow, signing configuration or release has been changed.

Pinned upstream: rezapranata089-bit/Zenith @ 49c9da8e8bf3098806e49e85f5aaa58619c97982
Reserved FC target if parent remains 15.7.0: 15.7.0.13

Architecture: reuse the existing ExoPlayer stereo widening/cross-feed DSP; carry forward the eight-second 1/8-rate PCM profile sampler; persist scalar stereo statistics only; bounded top-9 similarity with four explicit labels, three effective neighbours and 75% agreement before Auto may change the default On decision. Each ExoPlayer/crossfade side owns its own track context. Global Auto/On/Off is independent of the per-track Auto→On→Off→Auto control. Phone speaker is bypassed by default; Atmos and unavailable DSP are hard bypasses. Local records capped at 400 and excluded from settings exports.

Audio type caveat: this is stereo virtualization/soundstage widening, not object-based Atmos or an HRTF binaural renderer. Existing sound-shaping constants are intentionally unchanged for the first learning integration test.

Metadata audit: Spotify import exposes title/artist/album but no reliable genre signal; Canvas is artwork/video; ListenBrainz integration submits listens but does not read history. Likes, scrobbles and playlist membership are not valid feedback for whether widening sounds better. Only explicit per-track Spatial Audio On/Off controls train the local learner. No account credentials or raw audio are uploaded.

Next attempt: recheck upstream; merge this prep branch; set upstreamTrack=main-once and upstreamCommit to the reviewed full upstream main SHA if still current; execute the existing fc-sync-release.yml workflow; verify tests, package ID, permanent signing fingerprint, Music-v15.7.0.13.apk and Obtainium update. Workflow already applies fc/one-shot/spatial-audio/apply.py and only removes the overlay after exact APK asset verification.

## Version bump guard for the next attempt

At preparation time, fork main is `15.7.0.12` and the `v15.7.0.12` APK is already published. The current workflow increments the FC version automatically only when the upstream stable version changes. With stable still `15.7.0`, it would otherwise keep selecting `.12` and skip a new build because that asset already exists. After merging this preparation branch, run:

`python3 fc/bump-version.py 15.7.0`

That helper increments the same-parent revision to `15.7.0.13` and version code `52`. Then pin `upstreamTrack=main-once` and `upstreamCommit=49c9da8e8bf3098806e49e85f5aaa58619c97982` if upstream main is still at the reviewed SHA. Recalculate both target pins if the stable release or main head changes before implementation.
