#!/usr/bin/env python3
"""Apply FC Zenith's production identity only inside the CI build workspace."""
from __future__ import annotations

import base64
import os
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"FC overlay aborted: expected exactly one {label}; found {count}")
    return text.replace(old, new, 1)


def replace_pattern_once(text: str, pattern: str, replacement: str, label: str) -> str:
    updated, count = re.subn(pattern, lambda _match: replacement, text, count=1)
    if count != 1:
        raise SystemExit(f"FC overlay aborted: expected exactly one {label}; found {count}")
    return updated


def main() -> None:
    version_code = os.environ.get("FC_VERSION_CODE", "").strip()
    version_name = os.environ.get("FC_VERSION_NAME", "").strip()
    repository = os.environ.get("GITHUB_REPOSITORY", "").strip()

    if not version_code.isdigit() or int(version_code) < 1:
        raise SystemExit("FC overlay requires a positive FC_VERSION_CODE")
    if not re.fullmatch(r"\d+(?:\.\d+)+", version_name):
        raise SystemExit("FC overlay requires a numeric FC_VERSION_NAME")
    if not repository or "/" not in repository:
        raise SystemExit("FC overlay requires GITHUB_REPOSITORY")

    app_gradle = APP / "build.gradle.kts"
    app_text = app_gradle.read_text(encoding="utf-8")
    app_text = replace_once(
        app_text,
        'applicationId = "com.zeux.zenith"',
        'applicationId = "com.spotify.music"',
        "production applicationId",
    )
    app_text = replace_once(
        app_text,
        'buildConfigField("String", "UPDATE_APK_PREFIX", "\\"Zenith-\\"")',
        'buildConfigField("String", "UPDATE_APK_PREFIX", "\\"Music-\\"")',
        "in-app updater asset prefix",
    )

    prod_block = '''        create("prod") {
            dimension = "env"
            // Matches defaultConfig — this is the package already shipped/installed.
        }'''
    prod_block_fc = '''        create("prod") {
            dimension = "env"
            resValue("string", "app_name", "Music")
            // FC production label.
        }'''
    app_text = replace_once(app_text, prod_block, prod_block_fc, "production app label")
    app_gradle.write_text(app_text, encoding="utf-8")

    root_gradle = ROOT / "build.gradle.kts"
    root_text = root_gradle.read_text(encoding="utf-8")
    root_text = replace_pattern_once(
        root_text,
        r'(?m)^extra\["zenithVersionCode"\][ \t]*=[ \t]*\d+[ \t]*$',
        '''extra["zenithVersionCode"] = System.getenv("FC_VERSION_CODE")?.toIntOrNull()
    ?: error("FC_VERSION_CODE must be set for FC production builds")''',
        "upstream versionCode",
    )
    root_text = replace_pattern_once(
        root_text,
        r'(?m)^extra\["zenithVersionName"\][ \t]*=[ \t]*"\d+(?:\.\d+)+"[ \t]*$',
        '''extra["zenithVersionName"] = System.getenv("FC_VERSION_NAME")
    ?: error("FC_VERSION_NAME must be set for FC production builds")''',
        "upstream versionName",
    )
    root_gradle.write_text(root_text, encoding="utf-8")

    icon_b64 = ROOT / "fc" / "icon" / "music.webp.b64"
    if not icon_b64.is_file():
        raise SystemExit(f"FC launcher icon data is missing: {icon_b64}")
    icon_bytes = base64.b64decode(
        icon_b64.read_text(encoding="ascii").strip(),
        validate=True,
    )
    nodpi = APP / "src/main/res/drawable-nodpi"
    nodpi.mkdir(parents=True, exist_ok=True)
    icon_path = nodpi / "fc_music_icon.webp"
    icon_path.write_bytes(icon_bytes)

    # Zenith uses an adaptive icon: keep its white background and place the
    # supplied Music artwork in both the foreground and monochrome layers.
    (APP / "src/main/res/drawable/ic_launcher_background.xml").write_text(
        '''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="1024"
    android:viewportHeight="1024">
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="M0,0h1024v1024h-1024z" />
</vector>
''',
        encoding="utf-8",
    )
    (APP / "src/main/res/drawable/ic_launcher_foreground.xml").write_text(
        '''<?xml version="1.0" encoding="utf-8"?>
<bitmap xmlns:android="http://schemas.android.com/apk/res/android"
    android:src="@drawable/fc_music_icon"
    android:gravity="fill" />
''',
        encoding="utf-8",
    )

    from PIL import Image

    sizes = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
    with Image.open(icon_path) as source:
        artwork = source.convert("RGBA")
        for density, size in sizes.items():
            folder = APP / f"src/main/res/mipmap-{density}"
            folder.mkdir(parents=True, exist_ok=True)
            resized = artwork.resize((size, size), Image.Resampling.LANCZOS)
            resized.save(folder / "ic_launcher.webp", "WEBP", quality=94, method=6)
            resized.save(folder / "ic_launcher_round.webp", "WEBP", quality=94, method=6)

    # Android notification small icons are monochrome alpha masks. Use a
    # Music-note silhouette, not the upstream Zenith Z mark or the full-color
    # launcher artwork. Every notification builder shares this one resource.
    notification_logo = APP / "src/main/res/drawable/ic_notification_logo.xml"
    if not notification_logo.is_file():
        raise SystemExit("FC overlay requires the shared notification icon resource")
    notification_logo.write_text(
        '''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="M12 3v10.55c-.59-.34-1.27-.55-2-.55-2.21 0-4 1.79-4 4s1.79 4 4 4 4-1.79 4-4V7h4V3h-6z" />
</vector>
''',
        encoding="utf-8",
    )

    print(
        f"FC overlay applied: com.spotify.music / Music / "
        f"{version_name} / {repository}"
    )


if __name__ == "__main__":
    main()
