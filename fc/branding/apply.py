#!/usr/bin/env python3
"""Temporary, fail-closed UI rebrand applied only in the CI build workspace."""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
APP = ROOT / "app/src/main"
SHARED = ROOT / "sharedUi/src/commonMain/composeResources"

def config_enabled():
    settings = dict(line.split("=", 1) for line in
        (x.strip() for x in (ROOT / "fc/branding/config.properties").read_text().splitlines())
        if line and not line.startswith("#"))
    if settings.get("enabled") not in ("true", "false"):
        raise SystemExit("Brand overlay requires enabled=true or enabled=false")
    return settings["enabled"] == "true"

def replace_once(value, old, new, path):
    count = value.count(old)
    if count != 1:
        raise SystemExit(f"Brand overlay stopped: {path}: expected one {old[:65]!r}, found {count}")
    return value.replace(old, new, 1)

def edit(relative, transform):
    path = ROOT / relative
    old = path.read_text(encoding="utf-8")
    new = transform(old, path)
    if old == new:
        raise SystemExit(f"Brand overlay stopped: no changes to {relative}")
    path.write_text(new, encoding="utf-8")
    print(f"brrMusic temporary UI overlay: {relative}")

def xml_text(value, path):
    if "Zenith" not in value:
        raise SystemExit(f"Brand overlay stopped: no Zenith UI text in {path}")
    # Modify human-readable text nodes, never resource identifiers or third-party brands.
    return re.sub(r"(?<=>)[^<>]*(?=<)",
        lambda m: re.sub(r"\bZenith\b", "brrMusic", m.group(), flags=re.I),
        value)

def lyrics_card(value, path):
    # Remove the ENTIRE watermark block and its bottom gutter, not merely the word.
    start, end = "    // ── Brand ─", "\n    bitmap\n"
    if value.count(start) != 1 or value.count(end) != 1:
        raise SystemExit("Lyrics card footer changed; refusing unsafe patch")
    head, tail = value.split(start, 1)
    watermark, rest = tail.split(end, 1)
    if "drawLogo(" not in watermark or "canvas.drawText(BRAND" not in watermark:
        raise SystemExit("Lyrics card watermark changed; refusing unsafe patch")
    value = head + "    // No brand, icon or watermark in shared lyrics images.\n" + end + rest
    return replace_once(value, "private const val BRAND_BAND = 132f",
                        "private const val BRAND_BAND = 0f", path)

def home(value, path):
    old = """            Wordmark(
                logoHeight = logoHeight,
                fontSize = style.fontSize * MUSIC_TO_FONT,
                color = MaterialTheme.colorScheme.onBackground,
                gap = 2.dp,
                modifier = Modifier.padding(bottom = 14.dp),
            )"""
    new = """            Text(
                text = "brrMusic",
                style = style,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                modifier = Modifier.padding(bottom = 14.dp),
            )"""
    return replace_once(value, old, new, path)

def settings(value, path):
    value = replace_once(value, '"Support Zenith"', '"Support upstream developer"', path)
    return replace_once(value, '"Zenith $version"', '"brrMusic $version"', path)
    # Preserve actual legacy Music/Zenith storage paths to avoid orphaning downloads.

def replay_poster(value, path):
    value = replace_once(value,
        '    canvas.drawText("Zenith", w - MARGIN, 150f, brand)\n'
        '    drawLogo(canvas, context, w - MARGIN - brand.measureText("Zenith") - LOGO_GAP, 150f)',
        '    canvas.drawText("brrMusic", w - MARGIN, 150f, brand)', path)
    value = replace_once(value,
        '    canvas.drawText("Zenith", POSTER_W - MARGIN, 132f, brand)',
        '    canvas.drawText("brrMusic", POSTER_W - MARGIN, 132f, brand)', path)
    return replace_once(value,
        '    val wordWidth = brand.measureText("Zenith")\n'
        '    drawLogo(canvas, context, POSTER_W - MARGIN - wordWidth - LOGO_GAP, 132f)',
        '    // No upstream logo: brrMusic heading stands alone.', path)

def lyrics_share(value, path):
    return replace_once(value, 'LYRICS_FILE_PREFIX = "zenith-lyrics"',
                        'LYRICS_FILE_PREFIX = "lyrics"', path)

def replay_share(value, path):
    value = replace_once(value, 'prefix: String = "zenith-replay"',
                         'prefix: String = "brrmusic-replay"', path)
    old = '"' + '$' + '{Environment.DIRECTORY_PICTURES}/Zenith"'
    new = '"' + '$' + '{Environment.DIRECTORY_PICTURES}/brrMusic"'
    return replace_once(value, old, new, path)

def main():
    if not config_enabled():
        print("brrMusic branding disabled; upstream visuals remain unchanged.")
        return
    for rel in (
        "app/src/main/res/values/strings.xml",
        "app/src/main/res/values-in/strings.xml",
        "app/src/main/res/values-ko/strings.xml",
        "sharedUi/src/commonMain/composeResources/values/strings.xml",
        "sharedUi/src/commonMain/composeResources/values-id/strings.xml",
        "sharedUi/src/commonMain/composeResources/values-ko/strings.xml",
    ):
        edit(rel, xml_text)
    edit("app/src/main/java/com/music/zenith/ui/player/LyricsShareCard.kt", lyrics_card)
    edit("app/src/main/java/com/music/zenith/ui/player/AndroidLyricsShareSheet.kt", lyrics_share)
    edit("app/src/main/java/com/music/zenith/ui/replay/ReplayPoster.kt", replay_poster)
    edit("app/src/main/java/com/music/zenith/ui/replay/ReplayShareSheet.kt", replay_share)
    edit("app/src/main/java/com/music/zenith/ui/screens/SettingsSheet.kt", settings)
    edit("sharedUi/src/jvmSharedMain/kotlin/com/music/zenith/ui/screens/HomeScreen.kt", home)
    print("Brand-only overlay complete; Spatial Audio source unmodified.")

if __name__ == "__main__":
    main()
