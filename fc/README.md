# FC Zenith / Music

Personal fork: **Batmancpu/FCZenith**  
Direct upstream: **rezapranata089-bit/Zenith**

## Only installed-app changes

- Android application ID: `com.spotify.music`
- Installed app label: `Music`
- Launcher icon: the supplied artwork stored in `fc/icon/music.webp.b64`
- Existing Zenith features and behavior remain upstream-controlled.

The identity overlay runs only in the GitHub Actions build workspace. It does
not permanently rewrite upstream app source files.

## Permanent Spatial Audio integration

FC Zenith keeps its Spatial Audio engine as an FC-owned, removable build
integration under `fc/spatial-audio/`. The upstream application source remains
clean in Git: the release workflow applies the integration to the build
workspace, runs the normal production tests, builds and signs the APK, then
restores generated app-source edits before committing the upstream sync.

The integration is version-independent but fail-closed. It checks every source
patch anchor, requires its reviewed upstream baseline to be in the merged
history, and stops if upstream changes the processor implementation that FC
adapts. A changed upstream interface must be reconciled before publishing.

To remove Spatial Audio cleanly, remove `fc/spatial-audio/`, remove the
`Apply permanent FC Spatial Audio integration` workflow step, and remove the
Spatial Audio release-note line. No permanent edits to upstream application
source are required.

The feature is stereo widening/cross-feed virtualization, not object-based
Dolby Atmos or HRTF binaural rendering. Personal learning uses only explicit
per-track On/Off feedback and keeps profiles on-device.

## Obtainium and in-app updates

Use this repository URL as the Obtainium source:

https://github.com/Batmancpu/FCZenith

Releases publish one universal APK named
`Music-v<version>.apk`. The app's built-in updater is also configured to check
this repository and select the Music APK asset.

## Automated upstream main releases

FC Zenith checks `rezapranata089-bit/Zenith` main on a 15-minute GitHub Actions
schedule. New upstream commits are merged into the FC fork's build workspace
while FC-owned workflows, branding, signing and the Spatial Audio integration
remain separate. Each successful upstream source update triggers a production
unit-test run, APK build, certificate verification and a new release in this
repository. A failed merge, compatibility check, test, build or signing check
does **not** publish a broken APK or commit a partial sync.

The upstream author's latest **published stable release** sets the FC version
series; the code itself follows upstream `main`, including post-release fixes
and features. The first successful FC release on stable version `15.8.0` is
`15.8.0.1`; subsequent upstream main updates become `15.8.0.2`,
`15.8.0.3`, etc. Once a newer stable version is published, revisions
restart at `.1`. Android `versionCode` always increases.

## Run an upstream check on demand

In GitHub's **Actions** tab, select **FC Zenith — upstream sync and Music
release** and choose **Run workflow**. Alternatively, ask ChatGPT to trigger
an update: ChatGPT can change `fc/sync-request.txt` in the repository through
the GitHub connection, producing a `main` push that starts the same workflow.
An on-demand run checks upstream immediately, but an APK release appears
**only** when a new source revision exists (or the previous release asset is
missing) and all checks pass. GitHub Actions build and signing take time.

The schedule is a polling interval, not a guaranteed release deadline:
GitHub may delay scheduled workflows, and a build may take longer than the
interval.

## Signing

The first successful release generates a permanent PKCS#12 signing key in
GitHub Actions and stores it at `fc/signing-key.p12`. Its protection password
is the sole signing secret: `FC_SIGNING_KEY_PASSWORD`. Keep that password
in Proton Pass and never replace the keystore or change the password after
the first release. Every release verifies the certificate fingerprint before
publishing.
