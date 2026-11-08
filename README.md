<div align="center">

<br/>
<br/>

<img src="app/src/main/assets/Logo.svg" alt="Zenith app icon" width="200" />

# Zenith

### Aesthetic YouTube Music Client — Zeuxxi Edition

<br/>

[

![Latest release](https://img.shields.io/github/v/release/USERNAME/Zeuxxi?style=for-the-badge&labelColor=0d1117)

](https://github.com/USERNAME/Zeuxxi/releases)
[

![License](https://img.shields.io/github/license/USERNAME/Zeuxxi?style=for-the-badge&labelColor=0d1117)

](LICENSE)
[

![Downloads](https://img.shields.io/github/downloads/USERNAME/Zeuxxi/total?style=for-the-badge&labelColor=0d1117)

](https://github.com/USERNAME/Zeuxxi/releases)

<br/>

[**Download**](#download) · [**Features**](#features) · [**What's different**](#whats-different) · [**Credits**](#credits) · [**Disclaimer**](#disclaimer)

</div>

> [!IMPORTANT]
> Zenith is not affiliated with, endorsed by, or connected to YouTube or Google in any way. Use it at your own discretion.

---

<div align="center">

<h1><a id="features"></a>Features</h1>

<table>
  <tr>
    <td width="50%" valign="top">

#### Playback
- **Search, browse and play** anything available on YouTube Music.
- **Hi-Res lossless audio** — FLAC/ALAC from a configured module source, with YouTube Music as fallback.
- **Gapless playback with true crossfade**, adjustable 0–12s.
- **Automix [Beta]** — DJ-style transitions with beat-matching and tempo-stretching.
- **Offline downloads** — save tracks with embedded metadata.
- **Local music library** integration.
- **Background playback** via a proper foreground media session.
- **Apple-like lyrics animation** — credit to [binimum](https://github.com/binimum/am-lyrics).

#### Connectivity & Accounts
- **Sign in with your Google account** for personalized content.
- **Spotify integration** — connect your account to play your playlists and Liked Songs.
- **Discord Rich Presence** — in-app login, live track/artist/album and progress.
- **Scrobbling** to Last.fm and ListenBrainz.
- **Pluggable sources** — add, edit, test and health-check module sources.

    </td>
    <td width="50%" valign="top">

#### Experience
- **Animated album canvas** — motion artwork on the now-playing screen.
- **Word-synced lyrics** — word/syllable-level highlighting from multiple sources.
- **Lyrics providers** — credit to [lrc.red](https://lrc.red), [BiniLyrics](https://github.com/binimum), [BetterLyrics](https://github.com/better-lyrics/better-lyrics), [PaxSenix](https://lyrics.paxsenix.org), [LyricsPlus](https://github.com/ibratabian17/YouLyPlus), [SimpMusic](https://github.com/maxrave-dev/SimpMusic), [Unison](https://unison.boidu.dev), [Megalobiz](https://www.megalobiz.com), [KuGou](https://www.kugou.com), [LRCLIB](https://lrclib.net), [Musixmatch](https://www.musixmatch.com) and [Genius](https://genius.com).
- **Dynamic, artwork-driven theming** — Material palette extracted from album art.
- **Frosted-glass UI** — Telegram-style translucent bars via Haze, Material 3 theming.

#### Controls & Tweaks
- **Per-network audio quality** — separate quality ceilings for Wi-Fi and mobile data.
- **Playback speed control** (0.5×–2.0×) and **skip silence**.
- **Sleep timer** — fixed presets or "stop after this track".
- **System equalizer** integration.
- **Bit-perfect USB exclusive output** — sends lossless tracks to a USB DAC unchanged, at each track's own rate and bit depth. Thanks to [Mornye](https://github.com/mornyeapp/Mornye) for the reference.
- **Stats for nerds** — codec, bit depth, sample rate, and more on the now-playing screen.

    </td>
  </tr>
</table>

</div>

---

<div align="center">

<h1><a id="whats-different"></a>What's different from BitChord</h1>

</div>

Zeuxxi is my personal fork of [BitChord](https://github.com/kushagrasinghx/BitChord), reshaped into an Android-only build with its own identity and release pipeline.

- **Own identity** — new "Z" launcher icon and in-app logo, package `com.zeux.zenith`, credit line "Made by Zeuxxi" in the About screen.
- **Android only** — the project is trimmed to the `shared`, `sharedUi` and `app` modules, and the APK ships for `arm64-v8a` only.
- **Side-by-side dev build** — a `dev` flavor (`com.zeux.zenith.dev`) installs next to the `prod` app instead of overwriting it.
- **Automated releases** — GitHub Actions (`build-apk.yml`) builds a signed release APK with ccache, retries flaky dependency downloads, and publishes `Zenith-<version>.apk` to Releases whenever a `v*` tag is pushed.
- **In-app updater** — checks this repository's Releases for new versions.
- **Optimized release build** — R8 minification and resource shrinking enabled for a faster UI.

---

<div align="center">

<h1><a id="download"></a>Download</h1>

<a href="https://github.com/USERNAME/Zeuxxi/releases" target="_blank" rel="noopener noreferrer"><img src="https://cdn.jsdelivr.net/gh/JMcrafter26/badges@main/src/assets/supported/android/cozy.svg" alt="Download for Android" height="55"/></a>

Grab the latest signed APK from the [Releases](https://github.com/USERNAME/Zeuxxi/releases) page. Sideloading requires enabling "Install unknown apps" for whichever app you download it with.

</div>

---

<div align="center">

<h1><a id="credits"></a>Credits</h1>

Zenith is built on top of [BitChord](https://github.com/kushagrasinghx/BitChord) by [kushagrasinghx](https://github.com/kushagrasinghx) and its contributors. Huge thanks for the original work.

Lyrics providers, Bit-perfect USB reference and the lyrics animation are credited in [Features](#features).

[**Contributing Guide**](CONTRIBUTING.md) · [**Code of Conduct**](CODE_OF_CONDUCT.md) · [**Maintainers**](MAINTAINERS.md) · [**Additional Docs**](ADDITIONAL.md)

</div>

---

<div align="center">

<h1><a id="disclaimer"></a>Disclaimer & Legal Notice</h1>

Zenith is an independent, community-driven third-party audio player and client. It is **not** associated with Google LLC, YouTube Music, Deezer, Telegram, or any of their parent companies.

* **No Media Hosting:** Zenith does not host, upload, or store copyrighted music files. It operates strictly as an interface to scan local device storage or stream media directly from public, public-facing, or user-authenticated APIs.
* **Fair Use & API Usage:** This software is created solely for personal research, educational, and fair-use purposes. The user is entirely responsible for ensuring their usage aligns with their local copyright laws and YouTube Terms of Service.
* **No Ad-Blocking Guarantee:** While Zenith focuses on providing a clean listening environment, it does not guarantee permanent bypasses or modifications to commercial third-party platform conditions.
* **Copyleft:** Zenith is free software under the GPLv3. The license does not let anyone forbid others from selling or redistributing copies, but any distribution must come with the Corresponding Source under the same license.

</div>

---

<div align="center">

<h1><a id="license"></a>License</h1>

This project is licensed under the **GNU General Public License v3.0 (GPLv3)**. See the [LICENSE](LICENSE) file for details.

</div>
