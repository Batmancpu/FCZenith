# FC Zenith Spatial Audio integration

This is a permanent FC-owned build integration, not a one-release patch. Its
source, tests, compatibility baseline and QA checklist live in this directory.
The upstream app source is not rewritten in Git: GitHub Actions applies the
adapter in its temporary build workspace, tests and packages it, then restores
generated application edits before committing the upstream sync.

## Design

- Reuses ExoPlayer's existing stereo widening/cross-feed processor; independent
  contexts are retained for both sides of crossfade playback.
- Samples an eight-second stereo PCM profile at 1/8 rate and persists scalar
  statistics only. Decoded audio is never retained.
- Uses bounded top-9 similarity, four explicit per-track labels, three
  effective neighbours and a 75% weighted-agreement gate before Auto changes
  the default On decision.
- The global control is binary On/Off; On enables the internal Auto policy.
  The player menu is binary per-track On/Off. Auto is displayed as status
  (“Auto · On” or “Auto · Off”), not as a third toggle.
- Phone speaker is blocked by default. Dolby Atmos and unavailable DSP are hard
  bypasses. Profiles are capped at 400 records and excluded from settings export.
- Only explicit per-track On/Off choices train the learner. Metadata, likes,
  scrobbles and playlists are not treated as preference evidence.

This is stereo widening/soundstage virtualization, not object-based Dolby
Atmos or HRTF binaural rendering. Existing sound-shaping constants are left
unchanged.

## Mergeability and safety

The patcher is deliberately strict. Every edit uses an exact single-match
anchor; missing or duplicated anchors fail the build before signing/publishing.
The reviewed upstream baseline must be an ancestor of the checked-out source.
The underlying upstream `SpatialAudioProcessor.kt` is compared byte-for-byte
against that baseline before FC's adapted processor is copied in. If upstream
changes it, the release stops until the adapter is reviewed and rebased.

Other upstream UI and service changes are preserved by applying the FC adapter
to the newly merged checkout at build time, rather than carrying FC edits in
those app files on the permanent Git branch. The normal production unit-test
task runs after the integration is applied. The workflow then restores the
generated app-source paths to the exact upstream commit before saving the sync.

## Removal

To remove the feature, delete `fc/spatial-audio/`, remove the workflow step
named `Apply permanent FC Spatial Audio integration`, and remove the
Spatial Audio line from the release description. No permanent upstream app
source changes need reverting; the workflow has already restored them before
each sync commit.
