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

## Stable upstream policy

Regular production builds follow the upstream developer's latest **published stable
GitHub Release**. The explicitly requested v15.7.0.4 snapshot is a one-time exception:
it includes upstream `main` through commit `677d992a0c6569e0ad34bcb2f2ddcfcde3d438d7`.
After that snapshot, the automated pipeline returns to published stable releases.

Version format is:

`<upstream-release-version>.<FC-revision>`

For example, parent release `15.6.5` becomes `15.6.5.1`. Further FC builds
from the same parent version become `15.6.5.2`, `15.6.5.3`, and so on. When
the parent publishes `15.6.6`, the FC sequence restarts at `15.6.6.1`.
Android `versionCode` always increases independently and never goes backward.

## Signing

The first successful release generates a permanent PKCS#12 signing key in
GitHub Actions and stores it at `fc/signing-key.p12`. Its protection password
is the sole signing secret: `FC_SIGNING_KEY_PASSWORD`. Keep that password
in Proton Pass and never replace the keystore or change the password after
the first release. Every release verifies the certificate fingerprint before
publishing.
