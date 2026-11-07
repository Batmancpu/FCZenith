# Panduan Pengembangan Fitur Baru

Aturan wajib untuk setiap fitur, perubahan, atau halaman baru di app ini.
Berlaku untuk manusia maupun AI yang mengubah kode.

## 1. Pakai komponen yang sudah ada dulu

Sebelum menulis composable baru, cari dulu di `ui/components/`, `ui/screens/`
dan `sharedUi/`. Jangan membuat versi kedua dari sesuatu yang sudah ada.

| Kebutuhan | Pakai yang sudah ada |
|---|---|
| Switch / slider | `IosSwitch`, `IosSlider` |
| Menu | `IosMenu`, `SongActionsMenu`, `BrowseActionsMenu` |
| Segmented control | `GlassSegmentedControl`, `PillSegmentedControl` |
| Baris & grup setelan | `SettingsGroup`, `SettingsRow`, `SettingsSubRow`, `RowDivider`, `ChoiceGroup` |
| Judul halaman setelan | `SettingsPageTitle` (judul tampil di pil melayang `FrostedTopBar.floatingTitle`) |
| Kolom pencarian | `SearchField` |
| Top bar & tombol back | `FrostedTopBar` (jangan buat top bar sendiri) |
| Permukaan kaca | `Modifier.liquidGlass(shape)`, `lightweightLiquidGlass`, `liquidGlassRim` |
| Dialog / alert | komponen di `ui/components/*Dialog.kt` dan `AccountAlerts.kt` |
| Ikon | `SfIcons` (bukan ikon acak dari library lain) |
| Border thumbnail | `thumbnailBorder` |
| Fade tepi layar | `BottomFadeScrim`, `TopFadeBlur` |

Kalau komponen yang ada hampir cocok, **perluas dengan parameter opsional**
(default = perilaku lama) daripada menyalin lalu mengubahnya.

## 2. Kalau memang tidak ada, buat komponen baru dengan tema yang sama

- Letakkan di `ui/components/` (atau `sharedUi` bila dipakai lintas platform).
- Ambil warna dari `MaterialTheme.colorScheme`, tipografi dari
  `MaterialTheme.typography`. **Dilarang** hardcode `Color(0xFF...)`, `fontSize`
  mentah, atau font sendiri. Pengecualian hanya untuk konstanta kaca yang sudah
  ada di `LiquidGlass.kt`.
- Ukuran dan jarak memakai konstanta yang sudah ada, bukan angka baru:
  `BAR_GUTTER`, `PAGE_GUTTER`, `TopBarContentHeight`, `topBarContentPadding()`,
  `topBarHeight()`. Padding konten halaman mengikuti `listPadding`.
- Siapkan tampilan **gelap dan terang**, dan semua ukuran dalam `dp`/`sp`.
- Bentuk dan material meniru komponen lain: sudut squircle/pil, hairline
  `GLASS_EDGE_*`, dan pilihan material mengikuti pola `artworkPageSurface`
  (liquid glass, haze blur, atau solid).
- Sertakan `@Preview` bila memungkinkan (lihat `BarPreviews.kt`).

## 3. Hormati setelan efek visual

Setiap efek berat harus tunduk pada setelan yang sudah ada:

- `reduceDynamicBlur`: tidak ada blur, isi solid.
- `reduceAnimation`: animasi dikurangi atau dimatikan.
- `liquidGlass` + `isGlassSupported()` + `LocalLiquidGlassEnabled`: kaca hanya
  bila aktif dan didukung (Android 12+), selain itu fallback ke haze atau solid.
- `frostedGlass`: kaca versi ringan tanpa lensa.
- `highPerformanceMode` dan `lowRam`: jangan menambah beban di jalur ini.

Efek baru yang berat wajib punya jalur fallback ringan.

## 4. Semua perilaku harus punya setelan, jangan hardcode

Fitur baru yang bisa dipilih pengguna (nyala/mati, nilai, mode, intensitas)
**harus** dibuat sebagai opsi di Pengaturan, bukan konstanta di kode.

Langkah menambah setelan:

1. **`AppSettings.kt`**: tambah `MutableStateFlow` bertipe tepat, `KEY_...`,
   pemuatan nilai, setter `setXxx(...)`, dan nilai default (pertimbangkan
   default berbeda untuk `lowRam`).
2. **`SettingsSheet.kt`**: tambah baris lewat `row(title, kata kunci...)` agar
   ikut ke pencarian setelan. Gunakan `SettingsRow` + `IosSwitch`; setelan yang
   hanya melengkapi setelan lain pakai `SettingsSubRow` dan tampilkan bersyarat.
   Pilihan dengan banyak opsi: tambah entri `SettingsChoiceKind`.
3. **Kategori**: tempatkan di `SettingsCategory` yang paling relevan
   (Playback, Appearance, Performance, dst.).
4. **Pembaca setelan**: baca dengan `collectAsStateWithLifecycle()`. Di jalur
   draw/scroll baca lewat lambda agar tidak merekomposisi tiap frame.
5. Setelan ikut tersimpan, ekspor, dan impor otomatis lewat `AppSettings`.

Nilai yang dilarang di-hardcode: durasi animasi yang terasa subjektif,
intensitas blur/glow, ambang batas perilaku, URL/server, dan teks apa pun.

## 5. Teks selalu lewat resource string

- Tidak ada string literal di UI. Tambahkan ke `res/values/strings.xml`
  (Inggris) **dan** `res/values-id/strings.xml` (Indonesia) bersamaan.
- Teks harus singkat dan langsung ke inti. Subjudul satu kalimat, jelaskan hasil
  untuk pengguna, bukan cara kerja internalnya.
- Judul di bar atas melayang harus pendek; bila judul asli panjang, buat string
  khusus `bar_title_*` (lihat `settingsCategoryBarTitle`).
- Pakai `stringResource(...)`; penamaan key snake_case dan deskriptif.

## 6. Konsistensi halaman

- Halaman yang di-push memakai `FrostedTopBar` dengan tombol back melayang.
- Judul halaman tampil di pil kaca kanan tombol back dan memudar halus saat
  halaman di-scroll; jangan menambah judul besar di dalam konten.
- Konten halaman memakai `contentPadding = listPadding` supaya tidak tertutup bar
  dan mini player.
- Gesture back, state scroll, dan penyimpanan state halaman mengikuti pola
  `SaveableStateProvider` di `MainActivity`.

## 7. Performa

- Jangan membaca state yang berubah cepat (scroll, posisi lagu, animasi) di
  komposisi; baca di `graphicsLayer {}`, `drawWithContent {}`, atau lambda.
- Hindari `listOf(...)` baru tiap rekomposisi sebagai parameter; pakai `remember`.
- Efek GPU berat (lens, blur besar, shader) hanya bila setelannya aktif.
- Uji di perangkat spek rendah atau dengan setelan `lowRam` saat memungkinkan.

## 8. Format perubahan kode

Semua patch memakai format Find & Replace dalam **satu blok kode berformat
text**. Semua perubahan dari semua file digabung dalam blok yang sama.

- Baris pertama berisi direktif judul singkat (`:title`).
- Edit file memakai `:file`, lalu `:find` ... `:end_find`, lalu `:replace` ...
  `:end_replace`.
- `:find` harus cocok persis dengan isi file, tanpa placeholder atau kode palsu.
- Find terlalu panjang: pakai 5 baris awal, `:skip` di barisnya sendiri, lalu
  5 baris akhir.
- File baru memakai `:new_file`, folder baru `:new_folder`, hapus file `:hapus`.
- Jangan kirim seluruh file; hanya bagian yang berubah. Pertahankan indentasi.

## 9. Checklist sebelum selesai

- [ ] Memakai komponen yang sudah ada; komponen baru hanya bila benar-benar perlu
- [ ] Warna, tipografi, dan jarak dari tema dan konstanta app
- [ ] Gelap dan terang tampil benar
- [ ] Perilaku opsional punya setelan di Pengaturan (bukan hardcode)
- [ ] Setelan terdaftar di `AppSettings` dan ikut pencarian setelan
- [ ] String ada di `values` dan `values-id`, teks singkat
- [ ] Menghormati `reduceDynamicBlur`, `reduceAnimation`, dan fallback kaca
- [ ] Tidak ada rekomposisi per frame yang tidak perlu
- [ ] Patch dalam satu blok format Find & Replace