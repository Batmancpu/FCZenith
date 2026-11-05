#!/usr/bin/env python3
"""
rename_zenith.py - ganti seluruh nama "bitchord" jadi "zenith" di project Android.

Dijalankan dari folder root project (yang ada settings.gradle.kts):
  python3 rename_zenith.py            # dry-run: hanya laporan, TIDAK mengubah apa pun
  python3 rename_zenith.py --apply    # terapkan perubahan

Opsi:
  --rename-data   ikut ganti nama file SharedPreferences dan folder Music/BitChord
                  (login/setting/download lama di HP tidak terbaca lagi)
  --force         lanjut walau git belum bersih atau project bukan repo git

Yang SENGAJA tidak diganti supaya APK tidak error:
  - semua URL / domain / repo GitHub (server listen-together, invite link, update, dll)
  - keystore.properties, local.properties, *.jks (alias signing)
  - file biner (png, so, onnx, jar, dst)
  - nama file SharedPreferences + folder Music/BitChord (kecuali pakai --rename-data)
"""
import argparse
import os
import re
import subprocess
import sys
from collections import Counter

OLD = "bitchord"

SKIP_DIRS = {".git", ".gradle", ".idea", ".cxx", ".kotlin", "node_modules",
             "captures", ".externalNativeBuild", "dist"}
SKIP_FILES = {"keystore.properties", "local.properties", "rename_zenith.py", "merge.txt"}
SKIP_EXT = {".jks", ".keystore"}
BINARY_EXT = {
    ".png", ".webp", ".jpg", ".jpeg", ".gif", ".ico", ".so", ".a", ".o", ".jar", ".aar",
    ".class", ".dex", ".apk", ".aab", ".onnx", ".otf", ".ttf", ".ttc", ".woff", ".woff2",
    ".zip", ".gz", ".tar", ".7z", ".mp3", ".mp4", ".ogg", ".wav", ".flac", ".bin",
    ".dat", ".pdf",
}

# Nama file SharedPreferences yang dipakai sebagai string literal / .xml.
DATA_NAMES = [
    "auth_plain", "auth", "sources_plain", "sources", "settings", "party_queue_stash",
    "last_played", "bounded_context", "offline_playlists", "local_playlists",
    "listen_together", "playlist_sync", "original_versions", "widget",
]

ALWAYS_KEEP = [
    r"https?://[^\s\"'<>)\]\\`}]+",
    r"[A-Za-z0-9.\-]*kushagrasingh\.in",
    r"bitchord-listen-together\.onrender\.com",
    r"kushagrasinghx/BitChord",
    r"github\.com/bitchord",
]
DATA_KEEP = [
    r"(?<![A-Za-z0-9_])bitchord_(?:%s)(?![A-Za-z0-9_])"
    % "|".join(sorted(DATA_NAMES, key=len, reverse=True)),
    r"Music/BitChord",
]

WORD_RE = re.compile(OLD, re.IGNORECASE)
TOKEN_RE = re.compile(r"[A-Za-z_][A-Za-z0-9_.]*")


def swap(m):
    s = m.group(0)
    if s.isupper():
        return "ZENITH"
    if s[0].isupper():
        return "Zenith"
    return "zenith"


def build_keep(rename_data):
    pats = list(ALWAYS_KEEP) + ([] if rename_data else DATA_KEEP)
    return re.compile("|".join("(?:%s)" % p for p in pats))


def convert(text, keep_re):
    """Ganti di luar bagian terlindungi. Return (teks_baru, jumlah_ganti)."""
    out, pos, total = [], 0, 0
    for m in keep_re.finditer(text):
        part, n = WORD_RE.subn(swap, text[pos:m.start()])
        out.append(part)
        out.append(m.group(0))
        total += n
        pos = m.end()
    part, n = WORD_RE.subn(swap, text[pos:])
    out.append(part)
    return "".join(out) + "", total + n


def read_text(path):
    base = os.path.basename(path)
    ext = os.path.splitext(base)[1].lower()
    if base in SKIP_FILES or ext in BINARY_EXT or ext in SKIP_EXT:
        return None
    try:
        with open(path, "rb") as f:
            data = f.read()
    except OSError:
        return None
    if b"\0" in data:
        return None
    try:
        return data.decode("utf-8")
    except UnicodeDecodeError:
        print("  [lewati, bukan UTF-8] " + path)
        return None


def walk_project(root):
    dirs_all, files_all = [], []
    for dirpath, dirs, files in os.walk(root):
        has_gradle = any(f in files for f in ("build.gradle", "build.gradle.kts"))
        dirs[:] = [d for d in dirs
                   if d not in SKIP_DIRS and not (d == "build" and has_gradle)]
        for d in dirs:
            dirs_all.append(os.path.join(dirpath, d))
        for f in files:
            files_all.append(os.path.join(dirpath, f))
    return dirs_all, files_all


def git_state(root):
    if not os.path.isdir(os.path.join(root, ".git")):
        return None
    try:
        r = subprocess.run(["git", "-C", root, "status", "--porcelain"],
                           capture_output=True, text=True)
    except OSError:
        return None
    if r.returncode != 0:
        return None
    return bool(r.stdout.strip())


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("root", nargs="?", default=".")
    ap.add_argument("--apply", action="store_true")
    ap.add_argument("--rename-data", action="store_true")
    ap.add_argument("--force", action="store_true")
    args = ap.parse_args()

    root = os.path.abspath(args.root)
    if not (os.path.exists(os.path.join(root, "settings.gradle.kts"))
            or os.path.exists(os.path.join(root, "settings.gradle"))):
        sys.exit("Bukan root project Android (settings.gradle[.kts] tidak ada): " + root)

    keep_re = build_keep(args.rename_data)
    dirs_all, files_all = walk_project(root)
    print("Memindai %d file, %d folder ..." % (len(files_all), len(dirs_all)))

    idents = set()
    matched = {}
    for path in files_all:
        text = read_text(path)
        if text is None:
            continue
        idents.update(t.rstrip(".") for t in TOKEN_RE.findall(text))
        if OLD in text.lower():
            matched[path] = text

    # 1. Cek tabrakan nama: BitChordX -> ZenithX tidak boleh sudah ada.
    collisions = []
    for path, text in matched.items():
        for t in set(TOKEN_RE.findall(text)):
            t = t.rstrip(".")
            if OLD not in t.lower() or t.lower() == OLD:
                continue
            new, _ = convert(t, keep_re)
            if new != t and new in idents:
                collisions.append((os.path.relpath(path, root), t, new))
    if collisions:
        print("\nTABRAKAN NAMA (sudah ada nama tujuan). Ubah manual dulu lalu ulangi:")
        for rel, old, new in collisions[:40]:
            print("  %s: %s -> %s" % (rel, old, new))
        sys.exit(1)

    # 2. Rencana rename file/folder (yang terdalam dulu).
    renames = []
    for p in dirs_all + [f for f in files_all if os.path.basename(f) not in SKIP_FILES]:
        base = os.path.basename(p)
        if OLD in base.lower():
            renames.append((p, os.path.join(os.path.dirname(p), WORD_RE.sub(swap, base))))
    renames.sort(key=lambda x: x[0].count(os.sep), reverse=True)
    conflicts = [(a, b) for a, b in renames if os.path.exists(b)]
    if conflicts:
        print("\nTUJUAN RENAME SUDAH ADA:")
        for a, b in conflicts[:20]:
            print("  %s -> %s" % (os.path.relpath(a, root), os.path.relpath(b, root)))
        sys.exit(1)

    # 3. Laporan.
    new_texts, total, kept = {}, 0, Counter()
    for path, text in matched.items():
        new, n = convert(text, keep_re)
        for m in keep_re.finditer(text):
            if OLD in m.group(0).lower():
                kept[m.group(0)] += 1
        if new != text:
            new_texts[path] = new
            total += n
    print("\nIsi file diubah : %d file, %d penggantian" % (len(new_texts), total))
    print("Rename path     : %d (folder + file)" % len(renames))
    print("Sengaja dibiarkan (URL/domain/data): %d tempat" % sum(kept.values()))
    for s, c in kept.most_common(12):
        print("    %3dx %s" % (c, s if len(s) < 70 else s[:67] + "..."))

    if not args.apply:
        print("\nDRY-RUN selesai, belum ada yang diubah. Jalankan lagi dengan --apply")
        return

    dirty = git_state(root)
    if dirty is not False and not args.force:
        sys.exit("\nGit belum bersih / bukan repo git. Commit dulu (git add -A && "
                 "git commit -m backup), atau pakai --force.")

    # 4. Terapkan.
    for path, new in new_texts.items():
        with open(path, "wb") as f:
            f.write(new.encode("utf-8"))
    for old, new in renames:
        os.makedirs(os.path.dirname(new), exist_ok=True)
        os.rename(old, new)

    # 5. Verifikasi: tidak boleh ada sisa di luar bagian terlindungi.
    left = 0
    for path in new_texts:
        final = path
        for old, new in renames:
            if final.startswith(old + os.sep) or final == old:
                final = new + final[len(old):]
        try:
            with open(final, "rb") as f:
                _, n = convert(f.read().decode("utf-8"), keep_re)
        except OSError:
            continue
        left += n
    print("\nSelesai. Sisa 'bitchord' di luar bagian terlindungi: %d" % left)
    print("Langkah berikut: periksa 'git status', lalu build (GitHub Actions / gradlew clean assembleDebug).")


if __name__ == "__main__":
    main()