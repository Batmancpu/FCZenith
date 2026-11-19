#!/usr/bin/env python3
"""Send the release announcement to Telegram.

Version comes from build.gradle.kts, notes from RELEASE_NOTES.md.
Secrets live in ~/.zenith_release.env (never in the repo):

    TELEGRAM_BOT_TOKEN=...
    TELEGRAM_CHAT_ID=...
    TELEGRAM_THREAD_ID=...
    TELEGRAM_TEST_CHAT_ID=...
    TELEGRAM_TEST_THREAD_ID=...

Usage:
    python scripts/release.py --dry-run
    python scripts/release.py --test --images /storage/emulated/0/CodeOnTheGoProjects/png
    python scripts/release.py --images /storage/emulated/0/CodeOnTheGoProjects/png
"""
import argparse
import html
import json
import os
import re
import shutil
import subprocess
import sys
import urllib.error
import urllib.request
from pathlib import Path

ENV_FILE = Path.home() / ".zenith_release.env"
IMAGE_TYPES = {".jpg", ".jpeg", ".png"}
TEXT_LIMIT = 4000
ALBUM_MAX = 10


def esc(text):
    return html.escape(text, quote=False)


def load_config():
    config = {}
    if ENV_FILE.exists():
        for line in ENV_FILE.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                key, value = line.split("=", 1)
                config[key.strip()] = value.strip().strip("\"'")
    for key, value in os.environ.items():
        if key.startswith("TELEGRAM_"):
            config[key] = value
    return config


def read_version(gradle):
    path = Path(gradle)
    if not path.exists():
        sys.exit(f"{gradle} not found; run from the repo root or pass --version")
    match = re.search(r'zenithVersionName"\]\s*=\s*"([^"]+)"', path.read_text(encoding="utf-8"))
    if not match:
        sys.exit(f"Version not found in {gradle}; pass --version")
    return match.group(1)


def inline(text):
    text = esc(text)
    text = re.sub(r"\*\*(.+?)\*\*", r"<b>\1</b>", text)
    text = re.sub(r"`(.+?)`", r"<code>\1</code>", text)
    return text


def parse_notes(text):
    sections = []
    current = None
    for raw in text.splitlines():
        line = raw.strip()
        if not line:
            continue
        if line.startswith("###"):
            current = (line.lstrip("#").strip(), [])
            sections.append(current)
        elif line.startswith("#"):
            continue
        elif line[0] in "-*" and line[1:2] == " ":
            if current is None:
                current = ("What's new", [])
                sections.append(current)
            current[1].append(line[2:].strip())
        elif current and current[1]:
            current[1][-1] += " " + line
    return [(title, items) for title, items in sections if items]


def render(title, items):
    tag = "<blockquote expandable>" if "fix" in title.lower() else "<blockquote>"
    body = "\n".join("• " + inline(item) for item in items)
    return f"<b>{esc(title)}</b>\n{tag}{body}</blockquote>"


def chunk(head, blocks):
    messages = []
    current = head
    for block in blocks:
        candidate = f"{current}\n\n{block}" if current else block
        if len(candidate) > TEXT_LIMIT and current:
            messages.append(current)
            current = block
        else:
            current = candidate
    if current:
        messages.append(current)
    return messages


def call(token, method, payload):
    request = urllib.request.Request(
        f"https://api.telegram.org/bot{token}/{method}",
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"},
    )
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            return json.load(response)
    except urllib.error.HTTPError as error:
        try:
            return json.load(error)
        except ValueError:
            return {"ok": False, "description": f"HTTP {error.code}"}
    except urllib.error.URLError as error:
        return {"ok": False, "description": str(error.reason)}


def send_text(token, chat, thread, text):
    payload = {
        "chat_id": chat,
        "text": text,
        "parse_mode": "HTML",
        "link_preview_options": {"is_disabled": True},
    }
    if thread:
        payload["message_thread_id"] = int(thread)
    return call(token, "sendMessage", payload)


def send_images(token, chat, thread, images, caption):
    base = ["curl", "-s", "--form-string", f"chat_id={chat}"]
    if thread:
        base += ["--form-string", f"message_thread_id={thread}"]
    if len(images) == 1:
        method = "sendPhoto"
        cmd = base + [
            "-F", f"photo=@{images[0]}",
            "--form-string", f"caption={caption}",
            "--form-string", "parse_mode=HTML",
        ]
    else:
        method = "sendMediaGroup"
        media = []
        cmd = list(base)
        for index, path in enumerate(images):
            item = {"type": "photo", "media": f"attach://f{index}"}
            if index == 0:
                item.update(caption=caption, parse_mode="HTML")
            media.append(item)
            cmd += ["-F", f"f{index}=@{path}"]
        cmd += ["--form-string", "media=" + json.dumps(media)]
    cmd.append(f"https://api.telegram.org/bot{token}/{method}")
    result = subprocess.run(cmd, capture_output=True, text=True)
    try:
        return json.loads(result.stdout)
    except ValueError:
        return {"ok": False, "description": result.stderr.strip() or "no response"}


def check(result, what):
    if not result.get("ok"):
        sys.exit(f"{what} failed: {result.get('description', 'unknown error')}")
    print(f"Sent: {what}")


def main():
    parser = argparse.ArgumentParser(description="Send the release announcement to Telegram.")
    parser.add_argument("--app", default="BitChord", help="app name in the title")
    parser.add_argument("--version", help="override the version read from build.gradle.kts")
    parser.add_argument("--tagline", default="", help="one line under the title")
    parser.add_argument("--notes", default="RELEASE_NOTES.md")
    parser.add_argument("--gradle", default="build.gradle.kts")
    parser.add_argument("--images", default=".github/assets/release", help="folder with release images")
    parser.add_argument("--no-images", action="store_true")
    parser.add_argument("--test", action="store_true", help="send to the test chat")
    parser.add_argument("--dry-run", action="store_true", help="print the message, send nothing")
    parser.add_argument("--yes", action="store_true", help="skip the confirmation")
    args = parser.parse_args()

    version = args.version or read_version(args.gradle)
    notes = Path(args.notes)
    if not notes.exists():
        sys.exit(f"{args.notes} not found")
    sections = parse_notes(notes.read_text(encoding="utf-8"))
    if not sections:
        sys.exit(f"No '### Section' with '- item' lines found in {args.notes}")

    images = []
    folder = Path(args.images).expanduser()
    if not args.no_images and folder.is_dir():
        images = sorted(p for p in folder.iterdir() if p.suffix.lower() in IMAGE_TYPES)[:ALBUM_MAX]

    caption = f"<b>{esc(args.app)} {esc(version)}</b>"
    if args.tagline:
        caption += f"\n<i>{esc(args.tagline)}</i>"
    head = f"<b>What's new in {esc(version)}</b>" if images else caption
    messages = chunk(head, [render(title, items) for title, items in sections])

    if args.dry_run:
        print(f"Images ({len(images)}):")
        for path in images:
            print(f"  {path}")
        if images:
            print("\n--- album caption ---\n" + caption)
        for number, message in enumerate(messages, 1):
            print(f"\n--- message {number}/{len(messages)} ({len(message)} chars) ---\n{message}")
        return

    config = load_config()
    prefix = "TELEGRAM_TEST_" if args.test else "TELEGRAM_"
    token = config.get("TELEGRAM_BOT_TOKEN")
    chat = config.get(prefix + "CHAT_ID")
    thread = config.get(prefix + "THREAD_ID")
    if not token or not chat:
        sys.exit(f"Set TELEGRAM_BOT_TOKEN and {prefix}CHAT_ID in {ENV_FILE}")
    if images and not shutil.which("curl"):
        sys.exit("curl is required to upload images")

    if not args.test and not args.yes:
        answer = input(
            f"Send {args.app} {version} ({len(images)} image(s), {len(messages)} message(s)) "
            "to the release chat? [y/N] "
        )
        if answer.strip().lower() != "y":
            sys.exit("Cancelled")

    if images:
        check(send_images(token, chat, thread, images, caption), f"{len(images)} image(s)")
    for number, message in enumerate(messages, 1):
        check(send_text(token, chat, thread, message), f"message {number}/{len(messages)}")


if __name__ == "__main__":
    main()