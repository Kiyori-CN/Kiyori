#!/usr/bin/env python3
"""Validate the checked-in Ubuntu rootfs manifest and its binary asset."""

from __future__ import annotations

import argparse
import hashlib
import json
import tarfile
from pathlib import Path


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repository", type=Path, default=Path(__file__).resolve().parents[2])
    args = parser.parse_args()

    repository = args.repository.resolve()
    manifest_path = repository / "terminal" / "src" / "main" / "assets" / "ubuntu-rootfs-manifest.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    if manifest["schema"] != "kiyori.rootfs.manifest.v1":
        raise SystemExit(f"unexpected rootfs manifest schema: {manifest['schema']}")
    if (manifest["distribution"], manifest["release"], manifest["codename"], manifest["architecture"]) != (
        "ubuntu",
        "26.04.1",
        "resolute",
        "arm64",
    ):
        raise SystemExit("manifest does not describe Ubuntu 26.04.1 Resolute arm64")

    asset = manifest["asset"]
    asset_path = manifest_path.parent / asset["filename"]
    if asset_path.stat().st_size != asset["compressedBytes"]:
        raise SystemExit("rootfs asset size does not match manifest")
    actual_hash = sha256(asset_path)
    if actual_hash != asset["sha256"]:
        raise SystemExit(f"rootfs asset SHA-256 mismatch: {actual_hash}")

    with tarfile.open(asset_path, mode="r:xz") as archive_stream:
        archive_members = list(archive_stream)
    actual_member_count = len(archive_members)
    if actual_member_count != asset["archiveMembers"]:
        raise SystemExit(
            f"rootfs archive member count mismatch: expected={asset['archiveMembers']} actual={actual_member_count}"
        )
    hardlink_members = [member.name for member in archive_members if member.islnk()]
    if hardlink_members:
        raise SystemExit(
            "rootfs archive contains hard-link members that Android cannot extract: "
            + ", ".join(hardlink_members[:5])
        )
    if asset.get("hardlinkMembers") != 0:
        raise SystemExit("rootfs manifest must declare hardlinkMembers=0 for Android extraction")

    expected_markers = {
        "installed": ".kiyori_installed_ok",
        "manifest": ".kiyori_rootfs_manifest",
        "legacyInstalled": ".operit_installed_ok",
    }
    if manifest.get("markers") != expected_markers:
        raise SystemExit(
            f"rootfs marker contract mismatch: expected={expected_markers} actual={manifest.get('markers')}"
        )
    migration = manifest.get("migration")
    if not isinstance(migration, dict):
        raise SystemExit("rootfs manifest migration section is missing")
    if "legacyCompatibilityMarker" in migration:
        raise SystemExit(
            "rootfs manifest must not duplicate the historical marker outside markers.legacyInstalled"
        )
    if migration.get("legacyAsset") != "terminal/tools/rootfs/legacy/ubuntu-noble-aarch64-pd-v4.18.0.tar.xz":
        raise SystemExit("rootfs manifest legacy asset path is incorrect")

    package_lock = repository / "terminal" / "tools" / "rootfs" / "ubuntu-26.04.1" / manifest["packageLock"]["filename"]
    if package_lock.stat().st_size <= 0:
        raise SystemExit("rootfs package lock is empty")
    lock_hash = sha256(package_lock)
    if lock_hash != manifest["packageLock"]["sha256"]:
        raise SystemExit(f"rootfs package lock SHA-256 mismatch: {lock_hash}")
    package_count = len([line for line in package_lock.read_text(encoding="utf-8").splitlines() if line.strip()])
    if package_count != manifest["packageLock"]["packageCount"]:
        raise SystemExit("rootfs package lock count does not match manifest")

    print("Ubuntu rootfs asset: PASS")
    print(f"- release={manifest['release']} codename={manifest['codename']} architecture={manifest['architecture']}")
    print(f"- asset={asset_path.name} bytes={asset['compressedBytes']} sha256={actual_hash}")
    print(f"- archive_members={actual_member_count} hardlinks=0")
    print(f"- packages={package_count} lock_sha256={lock_hash}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
