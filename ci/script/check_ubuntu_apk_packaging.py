#!/usr/bin/env python3
"""Audit Ubuntu rootfs members in a built Kiyori APK.

The terminal runtime selects the current archive through its manifest.  This check keeps the
retired Noble archive out of Android delivery while proving that the Resolute member is present
exactly once and is byte-for-byte equal to the checked-in source asset.
"""

from __future__ import annotations

import argparse
import hashlib
import zipfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CURRENT_NAME = "ubuntu-resolute-arm64-kiyori-v1.tar.xz"
LEGACY_NAME = "ubuntu-noble-aarch64-pd-v4.18.0.tar.xz"
MANIFEST_NAME = "ubuntu-rootfs-manifest.json"


def sha256_bytes(payload: bytes) -> str:
    return hashlib.sha256(payload).hexdigest()


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--repository",
        type=Path,
        default=ROOT,
        help="Kiyori repository root",
    )
    parser.add_argument(
        "--apk",
        type=Path,
        default=None,
        help="APK to inspect (defaults to app/build/outputs/apk/debug/app-debug.apk)",
    )
    args = parser.parse_args()

    repository = args.repository.resolve()
    apk = (args.apk or repository / "app" / "build" / "outputs" / "apk" / "debug" / "app-debug.apk").resolve()
    source_asset = repository / "terminal" / "src" / "main" / "assets" / CURRENT_NAME
    manifest = repository / "terminal" / "src" / "main" / "assets" / MANIFEST_NAME

    if not apk.is_file():
        raise SystemExit(f"Debug APK is missing: {apk}")
    if not source_asset.is_file():
        raise SystemExit(f"Resolute source asset is missing: {source_asset}")
    if not manifest.is_file():
        raise SystemExit(f"Rootfs manifest is missing: {manifest}")

    with zipfile.ZipFile(apk) as archive:
        names = archive.namelist()
        current_members = [name for name in names if name == f"assets/{CURRENT_NAME}"]
        legacy_members = [name for name in names if name == f"assets/{LEGACY_NAME}"]
        manifest_members = [name for name in names if name == f"assets/{MANIFEST_NAME}"]
        if current_members != [f"assets/{CURRENT_NAME}"]:
            raise SystemExit(
                f"APK must contain exactly one Resolute rootfs member, found {len(current_members)}"
            )
        if legacy_members:
            raise SystemExit("APK still contains the retired Noble rootfs member")
        if manifest_members != [f"assets/{MANIFEST_NAME}"]:
            raise SystemExit(
                f"APK must contain exactly one rootfs manifest, found {len(manifest_members)}"
            )
        payload = archive.read(current_members[0])

    source_hash = sha256_file(source_asset)
    packaged_hash = sha256_bytes(payload)
    if packaged_hash != source_hash:
        raise SystemExit(
            f"Packaged Resolute asset hash mismatch: source={source_hash} apk={packaged_hash}"
        )

    print("Ubuntu APK packaging: PASS")
    print(f"- apk_bytes={apk.stat().st_size}")
    print(f"- resolute_members={len(current_members)} bytes={len(payload)} sha256={packaged_hash}")
    print("- noble_members=0")
    print(f"- manifest_members={len(manifest_members)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
