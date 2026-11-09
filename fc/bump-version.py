#!/usr/bin/env python3
"""Create a monotonically increasing FC release version from a stable parent tag."""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
VERSION_FILE = ROOT / "fc" / "version.properties"
VERSION_PATTERN = re.compile(r"\d+(?:\.\d+)+")


def parse_version(value: str) -> tuple[int, ...]:
    if not VERSION_PATTERN.fullmatch(value):
        raise SystemExit(f"Invalid numeric version: {value}")
    return tuple(int(part) for part in value.split("."))


def read_properties() -> dict[str, str]:
    values: dict[str, str] = {}
    for raw in VERSION_FILE.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if line and not line.startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            values[key.strip()] = value.strip()
    return values


def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit("Usage: bump-version.py <published-upstream-version>")

    upstream_version = sys.argv[1].removeprefix("v")
    upstream_parts = parse_version(upstream_version)
    props = read_properties()

    current_upstream = props.get("upstreamVersion", "")
    current_parts = parse_version(current_upstream) if current_upstream else ()
    if current_parts and upstream_parts < current_parts:
        raise SystemExit(
            f"Refusing upstream downgrade: {current_upstream} -> {upstream_version}"
        )

    current_revision = int(props.get("revision", "0"))
    revision = current_revision + 1 if upstream_parts == current_parts else 1
    version_code = int(props["versionCode"]) + 1
    version_name = f"{upstream_version}.{revision}"

    VERSION_FILE.write_text(
        "# FC release metadata. Keep versionCode strictly increasing.\n"
        f"upstreamVersion={upstream_version}\n"
        f"revision={revision}\n"
        f"versionCode={version_code}\n"
        f"versionName={version_name}\n",
        encoding="utf-8",
    )
    print(
        f"FC version: {props.get('versionName', '(unset)')} "
        f"-> {version_name} (code {version_code})"
    )


if __name__ == "__main__":
    main()
