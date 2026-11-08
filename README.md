<div align="center">

<br/>

<img src=".github/assets/logo.svg" alt="Logo Zenith" width="128" height="128" />

<h1>Zenith</h1>

<p><b>Pemutar YouTube Music untuk Android dengan tampilan ala Apple Music</b><br/>Edisi Zeuxxi</p>

<p>
<a href="https://github.com/rezapranata089-bit/Zenith/releases"><img src="https://img.shields.io/github/v/release/rezapranata089-bit/Zenith?style=flat-square&amp;labelColor=0d1117&amp;color=ff4d6d" alt="Rilis terbaru" /></a>
<a href="https://github.com/rezapranata089-bit/Zenith/releases"><img src="https://img.shields.io/github/downloads/rezapranata089-bit/Zenith/total?style=flat-square&amp;labelColor=0d1117&amp;color=ff4d6d" alt="Unduhan" /></a>
<a href="LICENSE"><img src="https://img.shields.io/github/license/rezapranata089-bit/Zenith?style=flat-square&amp;labelColor=0d1117&amp;color=ff4d6d" alt="Lisensi" /></a>
</p>

<p>
<a href="#download">Download</a> ·
<a href="#fitur">Fitur</a> ·
<a href="#bedanya-dengan-bitchord">Bedanya dengan BitChord</a> ·
<a href="#kredit">Kredit</a> ·
<a href="#disclaimer">Disclaimer</a>
</p>

</div>

> [!IMPORTANT]
> Zenith tidak berafiliasi, tidak didukung, dan tidak terhubung dengan YouTube maupun Google. Gunakan dengan bijak.

---

## Download

Ambil APK terbaru di halaman [Releases](https://github.com/rezapranata089-bit/Zenith/releases). Aplikasi ini hanya untuk Android (arm64). Saat memasang dari file, aktifkan izin "Install unknown apps" untuk aplikasi yang kamu pakai membuka APK.

Catatan perubahan lengkap ada di [RELEASE_NOTES.md](RELEASE_NOTES.md).

---

## Fitur

### Pemutaran
- Cari, jelajahi, dan putar apa saja dari YouTube Music.
- Audio Hi-Res lossless (FLAC/ALAC) dari sumber modul, YouTube Music sebagai cadangan.
- Gapless dengan crossfade asli, atur 0–12 detik.
- Automix (Beta): transisi ala DJ dengan beat-matching dan tempo-stretching.
- Unduh lagu untuk offline, lengkap dengan metadata.
- Pustaka musik lokal, pemutaran latar belakang lewat media session.
- Output USB bit-perfect ke USB DAC pada sample rate dan bit depth aslinya. Terima kasih kepada [Mornye](https://github.com/mornyeapp/Mornye) untuk referensinya.

### Tampilan
- Gaya Apple Music: squircle, aksen warna pilihan, kaca cair (Liquid Glass) dan mode Frosted Glass yang lebih ringan.
- Mini player dan big player menyatu dalam satu panel, cover terbang saat berpindah halaman.
- Animated album canvas di layar pemutar.
- Tema dinamis dari warna cover album.

### Lirik
- Lirik sinkron per kata dengan mesin MOTION dan efek LiquidFlow.
- Animasi lirik ala Apple Music, kredit ke [binimum](https://github.com/binimum/am-lyrics).
- Penyedia lirik: [lrc.red](https://lrc.red), [BiniLyrics](https://github.com/binimum), [BetterLyrics](https://github.com/better-lyrics/better-lyrics), [PaxSenix](https://lyrics.paxsenix.org), [LyricsPlus](https://github.com/ibratabian17/YouLyPlus), [SimpMusic](https://github.com/maxrave-dev/SimpMusic), [Unison](https://unison.boidu.dev), [Megalobiz](https://www.megalobiz.com), [KuGou](https://www.kugou.com), [LRCLIB](https://lrclib.net), [Musixmatch](https://www.musixmatch.com), dan [Genius](https://genius.com).

### Akun dan koneksi
- Masuk dengan akun Google untuk konten personal.
- Integrasi Spotify untuk playlist dan Liked Songs.
- Discord Rich Presence dan scrobbling ke Last.fm serta ListenBrainz.
- Listen Together untuk mendengarkan bareng.
- Sumber musik modular yang bisa ditambah, diedit, dan dicek.

### Kontrol
- Kualitas audio terpisah untuk Wi-Fi dan data seluler.
- Kecepatan putar 0,5×–2,0×, skip silence, sleep timer, dan equalizer sistem.
- Stats for nerds: codec, bit depth, dan sample rate di layar pemutar.

---

## Bedanya dengan BitChord

Zenith adalah fork pribadiku dari [BitChord](https://github.com/kushagrasinghx/BitChord). Yang berubah:

- **Identitas sendiri**: nama Zenith, logo Z baru, package `com.zeux.zenith`, unduhan masuk ke `Music/Zenith`.
- **Android saja**: kode TV, web, backend, desktop, dan Wear OS dibuang, APK hanya untuk arm64-v8a.
- **Tampilan ala Apple Music**: skema warna baru, 10 pilihan warna aksen, toggle, slider, dan segmented control berbahan kaca.
- **Pemutar baru**: satu panel yang berubah bentuk dari mini ke penuh, canvas layar penuh, transisi cover antar lagu.
- **Home dan Library baru**: kartu Top Picks, Made for You Mix, Most Played, hero Liked Music.
- **Automix lebih presisi**: koreksi fase beat, deteksi tempo dan cue yang lebih baik.
- **Cache dan kecepatan**: cache disk untuk browse, lirik, dan pencarian, preset awal dengan cadangan untuk HP RAM kecil.
- **Rilis otomatis**: GitHub Actions membuat APK bertanda tangan setiap tag `v*` di-push, dan updater di dalam app mengecek repo ini.

---

## Kredit

Zenith dibangun di atas [BitChord](https://github.com/kushagrasinghx/BitChord) karya [kushagrasinghx](https://github.com/kushagrasinghx) dan para kontributornya. Terima kasih untuk fondasinya.

---

## Disclaimer

Zenith adalah pemutar audio pihak ketiga yang independen. Aplikasi ini **tidak** terkait dengan Google LLC, YouTube Music, Deezer, Telegram, atau perusahaan induk mereka.

- **Tidak menyimpan media**: Zenith tidak meng-host atau mengunggah musik berhak cipta. Aplikasi hanya menjadi antarmuka untuk membaca penyimpanan lokal atau memutar dari API publik atau API yang sudah kamu login.
- **Penggunaan wajar**: dibuat untuk keperluan pribadi, riset, dan edukasi. Kamu bertanggung jawab memastikan pemakaianmu sesuai hukum hak cipta setempat dan Ketentuan Layanan YouTube.
- **Tanpa jaminan**: tidak ada jaminan fitur akan terus berjalan bila platform pihak ketiga mengubah aturannya.

## Lisensi

Proyek ini berlisensi **GNU GPL v3.0**. Lihat berkas [LICENSE](LICENSE). Siapa pun yang mendistribusikan salinannya wajib menyertakan kode sumber dengan lisensi yang sama.
