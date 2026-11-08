## Zenith by Zeuxxi

Fork of [BitChord](https://github.com/kushagrasinghx/BitChord) with a new identity, an Apple Music–inspired interface and a reworked player.

### Identity & build
- Renamed from BitChord to Zenith; package is now `com.zeux.zenith`.
- New "Z" logo for the launcher and inside the app, with Default (monochrome) and red Liquid Glass launcher icons.
- Downloads go to `Music/Zenith`.
- Android only: TV, web, backend, docs, desktop and Wear OS removed; `arm64-v8a` build only.
- GitHub Actions builds a signed release APK (ccache, SDK/NDK cache, retry on failed downloads) and the build stops if the keystore is missing, so updates install without uninstalling.
- Settings footer shows the Zenith version and creator credit.

### Look & feel
- Apple Music style: full color scheme, squircle shapes, no Material ripple, 10 accent colors to choose from.
- Liquid Glass toggles and sliders, glass segmented controls (scrollable, used in Search filters), glass nav bar whose icon colors follow the pill.
- New Frosted Glass setting, lighter than Liquid Glass; glass effects switch off cleanly when Liquid Glass is off.
- Jelly spring animation on popups and menus; iOS-style confirm dialogs and toasts.
- Floating Settings headers and a redesigned Edit profile.

### Player
- Apple Music layout; mini player and big player are one morphing panel with a flying cover.
- Animated canvas stays alive during cover flights, with a full-screen mode and a switch to turn video covers off.
- Cover transitions between songs: slide, wipe, dissolve and a Fluid (AGSL) effect, with a setting to keep them light.
- Song title slides in the direction of Next/Previous.
- Double-tap the cover to Like, with a star effect and animated like button.
- Playback Speed & Tempo popup, AirPlay-style output icon, Dolby Atmos icon under the progress bar.
- Crossfade also on Next/Previous (can be switched off in Settings).

### Lyrics
- New MOTION lyrics engine with the LiquidFlow effect, Apple Music–style glow, swell and particles.
- Back-to-active-line button, spring scrolling, smoother stagger, read mode without sync.
- Smooth crossfade between the Queue and Lyrics panels.

### Queue & playlists
- Apple Music–style queue with shuffle, repeat and autoplay buttons under the song.
- "Add Songs to Queue" popup with live search, All/Songs/Videos filters and recommendations.
- Swipe to add to queue from search suggestions.
- Redesigned New Playlist, Edit playlist and Add to playlist pages, with gallery cover pick and an Add songs button.

### Home, Library & Explore
- Home in Apple Music style: Top Picks cards, Made for You Mix, Most Played, Listen again, playlist cards with a 3-song preview.
- Liked Music hero with animated album collage; Most Played card with fluid-blob background.
- Library redesigned with a Replay card and a 2-column Artists grid; up to 6 pinned playlists.
- Covers fly from cards to album, artist and mix pages.
- New "Together" tab for Listen Together, switchable in Settings.
- Local profile name and photo editing.

### Automix
- Closed-loop beat-phase correction, least-squares tempo, cue on downbeat, longer overlaps.
- Speed-up of the next song capped at 10%.
- Autoplay suggestions ordered to suit Automix; analysis retries when a download fails.
- Shine effect on the Mixing bar; blend details in Copy log.

### Offline, cache & speed
- Disk cache for browse, lyrics, artist lookups and search suggestions, with offline fallback.
- Animated covers cached and replayed offline; adjustable cache size with a confirmation before shrinking.
- Cached lossless copy plays even when quality is set low.
- First-run presets with a low-RAM fallback; mobile audio quality defaults to Lossless.
- Lighter hero animations (30 fps, paused when hidden) and many fixes for jank, flicker and blur glitches.