# FCZenith/brrMusic recovery manifest — 2026-11-15

**Purpose:** restore the custom app and Spatial Audio from upstream without
guessing what was changed, including the last verified sound engine and the
new optional refinement. This recovery record lives on a separately named
backup branch; it does NOT modify or trigger the normal release branch.

## Immutable release checkpoints

- Last published APK before the second spatial refinement:
  [brrMusic v15.8.0.10](https://github.com/Batmancpu/FCZenith/releases/tag/v15.8.0.10).
  Git tag `v15.8.0.10` points to exact pre-refinement release source
  commit `b1906ab1860be619d4a7f54262cf9143a6f2aa46`.
- Dedicated baseline branch:
  `backup/brrmusic-v15.8.0.10-pre-refinement`.
- Source snapshot with the refined engine and its tests:
  `backup/fc-spatial-refinement-source-20261115`; the PR #5 merge commit
  (before this manifest) is
  `1f6ec55f6afb6363313fd5329629b42a80b5b903`.
- Full code review, changes and CI history:
  https://github.com/Batmancpu/FCZenith/pull/5
- Upstream project: https://github.com/rezapranata089-bit/Zenith
- Our fork: https://github.com/Batmancpu/FCZenith

## Source-of-truth files

- `fc/spatial-audio/SpatialAudioProcessor.kt`: complete FC stereo engine.
- `fc/spatial-audio/SpatialAudioLearning.kt`: complete local explicit-feedback
  preference learner (Auto on/off; confidence limits).
- `fc/spatial-audio/SpatialAudioLearningTest.kt`: baseline behavior tests.
- `fc/spatial-audio/apply.py`: integrates FC into upstream Android sources
  in the disposable build workspace; fails on source-anchor drift.
- `fc/spatial-audio/source-commit`: reviewed upstream base required by adapter.
- `fc/spatial-audio/experimental/{apply.py,config.properties,SpatialAudioQualityTest.kt,README.md}`:
  first optional experiment. Build-only: width 2.5→2.0,
  fixed gain 0.82→0.86, crossfeed 0.20→0.16, delay 15→12 ms,
  1.6-kHz sample-rate-dependent crossfeed lowpass, stereo-linked sample-peak
  protection, lower profiling allocations, bounded background callback and
  conservative Auto learning thresholds.
- `fc/spatial-audio/refinement/{apply.py,config.properties,SpatialAudioRefinementTest.kt,README.md}`:
  second **independently switchable** build-only experiment: centered signals
  unchanged, low-frequency stereo-side widening reduced below ~140 Hz, and
  normal Float32 DspChain delegates its peak protection to the existing final
  LoudnessProcessor. Direct/PCM16 standalone processor keeps its own limiter.
- `fc/branding/{apply.py,config.properties}`: reversible visible brrMusic label.
- `fc/apply-overlay.py`: permanent FC app identity and launcher resources.
- `fc/version.properties`: series, monotonically increasing Android
  `versionCode`, last released experimentalAudio/audioRefinement flags.
- `fc/signing-key.p12`: existing encrypted signing key (do not regenerate).
- `fc/signing-cert-sha256.txt`: certificate fingerprint
  `b94469c80f38f83d3f866c7f4dd82bf44b106b70b403c8aad5239f19f2430a83`.
- `.github/workflows/fc-sync-release.yml`: tests, builds, checks certificate,
  signs and publishes releases; retains `workflow_dispatch` and push triggers;
  **no cron polling**.
- `.github/workflows/fc-spatial-quality-verify.yml`: isolated PR tests.

The custom DSP is generated into `app/src/` only during the release
workflow and intentionally restored before committing upstream source. Do not
interpret the upstream `app/src/.../SpatialAudioProcessor.kt` on GitHub as the
effective APK engine; use the overlays above.

## Recovery procedure (for a future AI or maintainer)

1. **Do not uninstall the existing APK. Do not change package ID, signing
   certificate, storage or preference schema.** Record its versionCode.
2. Fetch exact baseline from the `v15.8.0.10` tag, and preserve both backup
   branches and this document. Use the current upstream repo only after
   establishing reviewed compatibility; do not blindly overwrite FC files.
3. Preserve `fc/` source overlays, signing-key file and certificate fingerprint,
   both relevant GitHub workflows, branding config and release metadata.
4. Restore these identities: Android applicationId `com.spotify.music`,
   visible app label `brrMusic` and the original signing certificate.
   Never expose the `FC_SIGNING_KEY_PASSWORD` GitHub secret in logs/source.
5. Baseline sound: enable `fc/spatial-audio/experimental/config.properties`
   and disable `fc/spatial-audio/refinement/config.properties`. The latter
   exists only in refined revisions; absent in older tag by design.
   If the prior experiment is also undesirable, disable that too to return
   to the preserved stable FC spatial processor.
6. Refined sound: enable both layer configs. Apply in order:
   `python3 fc/apply-overlay.py`,
   `python3 fc/spatial-audio/apply.py`,
   `python3 fc/spatial-audio/experimental/apply.py`,
   `python3 fc/spatial-audio/refinement/apply.py`,
   `python3 fc/branding/apply.py`.
   Follow the CI workflow for complete required environment and locale fallbacks.
7. Fail closed on any missing source anchors, drifting upstream audio files,
   Kotlin errors, regressions, changed signing certificate, or APK identity.
8. Run `:app:testProdReleaseUnitTest` and `:app:assembleProdRelease`.
   Check 44.1/48/96-kHz stereo, mono/multichannel bypass, Atmos/DSP route
   gating, per-track Auto decisions, crossfade handoffs, Float32/PCM16,
   disable/ramp safety and output peaks. Production unit tests alone cannot
   establish true-peak compliance or listening quality.
9. Release with a **higher Android versionCode** than any installed release,
   with the **same certificate**. Changing only the flags should still create
   a new version and release. Never try to install a lower versionCode as a
   rollback over an existing installation.
10. Confirm signed APK and exact GitHub Release URL; keep the known-good APK
    from v15.8.0.10. Prefer physical-device, volume-matched A/B before claiming
    the new engine sounds better.

## Reproducibility caveats

GitHub recovery branches and releases are separate recovery points, not
independent offline backups of the repo. For disaster recovery after account
deletion, download a full repository mirror plus the signed baseline APK
to storage you control. The signing key is encrypted: the signing password
must be preserved securely separately, or in GitHub repository secrets.
