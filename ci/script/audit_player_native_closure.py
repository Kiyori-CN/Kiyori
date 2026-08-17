#!/usr/bin/env python3
"""Audit one source or namespaced thin mpv Android native closure."""

from __future__ import annotations

import argparse
import hashlib
import io
import json
import re
import subprocess
import tempfile
import zipfile
from pathlib import Path
from typing import Any

from prepare_mpv_player_dependency import (
    MPV_FFMPEG_NAMESPACE_RENAMES,
    MPV_PLAYER_DIRECT_DEPENDENCIES,
    MPV_REQUIRED_CLASS_MEMBERS,
    MPV_REQUIRED_TLS_MARKERS,
    MPV_SOURCE_MEMBERS,
    MPV_THIN_MEMBERS,
    NATIVE_ZIP_ALIGNMENT,
    native_zip_data_offsets,
)


REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
MANIFEST_PATH = (
    REPOSITORY_ROOT / "tools" / "player_native_build" / "closure_manifest.json"
)
REQUIRED_LIBCXX_SYMBOLS = {
    "_ZNSt6__ndk127__from_chars_floating_pointIfEENS_19__from_chars_resultIT_EEPKcS5_NS_12chars_formatE",
    "_ZNSt6__ndk127__from_chars_floating_pointIdEENS_19__from_chars_resultIT_EEPKcS5_NS_12chars_formatE",
}
FFMPEG_VERSION_PREFIXES = {
    "libavcodec.so": "LIBAVCODEC_",
    "libavdevice.so": "LIBAVDEVICE_",
    "libavfilter.so": "LIBAVFILTER_",
    "libavformat.so": "LIBAVFORMAT_",
    "libavutil.so": "LIBAVUTIL_",
    "libswresample.so": "LIBSWRESAMPLE_",
    "libswscale.so": "LIBSWSCALE_",
}
MPV_FFMPEG_NAMESPACE_REVERSE = {
    namespaced_name: source_name
    for source_name, namespaced_name in MPV_FFMPEG_NAMESPACE_RENAMES.items()
}
SOURCE_DIRECTORY_MEMBERS = (
    "assets/",
    "jni/",
    "jni/arm64-v8a/",
)
LIBCXX_IMPORT_PREFIXES = (
    "_ZNSt6__ndk1",
    "_ZSt",
    "_ZTVNSt6__ndk1",
    "_ZTTNSt6__ndk1",
    "_Zn",
    "_Zd",
    "__cxa_guard_",
    "__cxa_pure_virtual",
)


def sha256_bytes(payload: bytes) -> str:
    return hashlib.sha256(payload).hexdigest()


def run_readelf(readelf: Path, *arguments: str, elf: Path) -> str:
    completed = subprocess.run(
        [str(readelf), *arguments, str(elf)],
        check=True,
        capture_output=True,
        text=True,
        errors="replace",
    )
    return completed.stdout


def parse_dynamic(output: str) -> tuple[str, set[str], set[str]]:
    sonames = re.findall(r"\(SONAME\).*\[([^\]]+)\]", output)
    if len(sonames) != 1:
        raise ValueError(f"expected one ELF SONAME, found {sonames}")
    needed = set(re.findall(r"\(NEEDED\).*\[([^\]]+)\]", output))
    rpath_tags = set(re.findall(r"\((RPATH|RUNPATH)\)", output))
    return sonames[0], needed, rpath_tags


def parse_load_alignments(output: str) -> list[int]:
    alignments: list[int] = []
    for line in output.splitlines():
        if not line.lstrip().startswith("LOAD "):
            continue
        fields = line.split()
        if not fields:
            continue
        alignments.append(int(fields[-1], 16))
    if not alignments:
        raise ValueError("ELF contains no PT_LOAD program header")
    return alignments


def parse_dynamic_symbols(output: str) -> tuple[set[str], set[str]]:
    imported: set[str] = set()
    exported: set[str] = set()
    for line in output.splitlines():
        fields = line.split()
        if len(fields) < 8 or not fields[0].rstrip(":").isdigit():
            continue
        symbol = fields[-1]
        base_symbol = symbol.split("@", 1)[0]
        if fields[6] == "UND":
            imported.add(base_symbol)
        else:
            exported.add(base_symbol)
    return imported, exported


def parse_version_info(output: str) -> tuple[set[str], dict[str, set[str]]]:
    definitions: set[str] = set()
    requirements: dict[str, set[str]] = {}
    mode: str | None = None
    current_file: str | None = None
    for line in output.splitlines():
        if line.startswith("Version definition section"):
            mode = "definitions"
            current_file = None
            continue
        if line.startswith("Version needs section"):
            mode = "requirements"
            current_file = None
            continue
        if mode == "definitions":
            match = re.search(r"\bName: (\S+)", line)
            if match:
                definitions.add(match.group(1))
            continue
        if mode != "requirements":
            continue
        file_match = re.search(r"\bFile: (\S+)", line)
        if file_match:
            current_file = file_match.group(1)
            requirements.setdefault(current_file, set())
            continue
        name_match = re.search(r"\bName: (\S+)", line)
        if name_match and current_file is not None:
            requirements[current_file].add(name_match.group(1))
    return definitions, requirements


def require_markers(payload: bytes, markers: set[bytes], owner: str) -> None:
    missing = sorted(marker.decode("ascii") for marker in markers if marker not in payload)
    if missing:
        raise ValueError(f"{owner} is missing required markers: {missing}")


def closure_library_name(source_name: str, kind: str) -> str:
    if kind == "thin":
        return MPV_FFMPEG_NAMESPACE_RENAMES.get(source_name, source_name)
    return source_name


def closure_dependency_name(dependency_name: str, kind: str) -> str:
    if kind == "source":
        return MPV_FFMPEG_NAMESPACE_REVERSE.get(
            dependency_name,
            dependency_name,
        )
    return dependency_name


def expected_member_names(kind: str) -> tuple[str, ...]:
    if kind == "source":
        return (*SOURCE_DIRECTORY_MEMBERS, *MPV_SOURCE_MEMBERS)
    if kind == "thin":
        return MPV_THIN_MEMBERS
    raise ValueError(f"unsupported closure kind: {kind}")


def load_manifest(profile: str) -> dict[str, Any]:
    with MANIFEST_PATH.open(encoding="utf-8") as stream:
        manifest = json.load(stream)
    if profile not in manifest["profiles"]:
        raise ValueError(f"unknown player closure profile: {profile}")
    return manifest


def audit_closure(
    aar: Path,
    readelf: Path,
    profile: str,
    kind: str,
) -> dict[str, Any]:
    if not aar.is_file():
        raise FileNotFoundError(f"player closure AAR is missing: {aar}")
    if not readelf.is_file():
        raise FileNotFoundError(f"llvm-readelf is missing: {readelf}")

    manifest = load_manifest(profile)
    common = manifest["common"]
    profile_info = manifest["profiles"][profile]
    minimum_alignment = int(common["build_contract"]["elf_pt_load_minimum"], 16)
    expected_members = expected_member_names(kind)
    expected_member_set = set(expected_members)

    aar_payload = aar.read_bytes()
    result: dict[str, Any] = {
        "aar": str(aar.resolve()),
        "sha256": sha256_bytes(aar_payload),
        "size": len(aar_payload),
        "profile": profile,
        "kind": kind,
        "native": {},
    }
    if kind == "thin":
        offsets = native_zip_data_offsets(aar)
        misaligned = {
            name: offset
            for name, offset in offsets.items()
            if offset % NATIVE_ZIP_ALIGNMENT != 0
        }
        if misaligned:
            raise ValueError(
                "player thin AAR native ZIP offsets are not aligned: "
                f"{misaligned}"
            )
        result["zip_offsets"] = {
            name: hex(offset) for name, offset in sorted(offsets.items())
        }

    with zipfile.ZipFile(io.BytesIO(aar_payload)) as archive:
        members = archive.namelist()
        if len(members) != len(set(members)):
            raise ValueError("player closure AAR contains duplicate members")
        if set(members) != expected_member_set:
            raise ValueError(
                "player closure AAR member list differs: "
                f"missing={sorted(expected_member_set - set(members))}, "
                f"extra={sorted(set(members) - expected_member_set)}"
            )
        for member in members:
            if (
                member.startswith("jni/")
                and not member.endswith("/")
                and not member.startswith("jni/arm64-v8a/")
            ):
                raise ValueError(f"player closure AAR contains another ABI: {member}")

        classes_payload = archive.read("classes.jar")
        with zipfile.ZipFile(io.BytesIO(classes_payload)) as classes:
            class_members = set(classes.namelist())
        missing_classes = MPV_REQUIRED_CLASS_MEMBERS - class_members
        if missing_classes:
            raise ValueError(
                "player closure AAR is missing runtime classes: "
                f"{sorted(missing_classes)}"
            )

        native_members = sorted(
            member
            for member in members
            if member.startswith("jni/arm64-v8a/") and member.endswith(".so")
        )
        payloads = {Path(member).name: archive.read(member) for member in native_members}

    if kind == "thin":
        normal_names = set(MPV_FFMPEG_NAMESPACE_RENAMES)
        for owner, payload in payloads.items():
            remaining = sorted(
                name for name in normal_names if name.encode("ascii") in payload
            )
            if remaining:
                raise ValueError(
                    f"namespaced closure member {owner} still references {remaining}"
                )

    expected_ffmpeg_names = {
        closure_library_name(name, kind) for name in FFMPEG_VERSION_PREFIXES
    }
    expected_native_names = {
        "libc++_shared.so",
        "libmpv.so",
        "libplayer.so",
        *expected_ffmpeg_names,
    }
    if set(payloads) != expected_native_names:
        raise ValueError(
            "player closure native member list differs: "
            f"expected={sorted(expected_native_names)}, got={sorted(payloads)}"
        )

    elf_metadata: dict[str, dict[str, Any]] = {}
    with tempfile.TemporaryDirectory(prefix="kiyori-player-native-audit-") as directory:
        temporary_root = Path(directory)
        for name, payload in payloads.items():
            elf = temporary_root / name
            elf.write_bytes(payload)

            header = run_readelf(readelf, "-h", elf=elf)
            if "Class:                             ELF64" not in header:
                raise ValueError(f"{name} is not ELF64")
            if "Data:                              2's complement, little endian" not in header:
                raise ValueError(f"{name} is not little-endian ELF")
            if "Machine:                           AArch64" not in header:
                raise ValueError(f"{name} is not AArch64")

            dynamic_output = run_readelf(readelf, "-d", "-W", elf=elf)
            soname, needed, rpath_tags = parse_dynamic(dynamic_output)
            if soname != name:
                raise ValueError(f"{name} has SONAME {soname}")
            if rpath_tags:
                raise ValueError(f"{name} contains forbidden {sorted(rpath_tags)}")

            load_output = run_readelf(readelf, "-l", "-W", elf=elf)
            alignments = parse_load_alignments(load_output)
            bad_alignments = [
                alignment
                for alignment in alignments
                if alignment < minimum_alignment
            ]
            if bad_alignments:
                raise ValueError(
                    f"{name} has PT_LOAD below {minimum_alignment:#x}: "
                    f"{[hex(value) for value in bad_alignments]}"
                )

            symbol_output = run_readelf(readelf, "--dyn-syms", "-W", elf=elf)
            imported, exported = parse_dynamic_symbols(symbol_output)
            version_output = run_readelf(readelf, "--version-info", "-W", elf=elf)
            version_definitions, version_requirements = parse_version_info(
                version_output
            )
            elf_metadata[name] = {
                "needed": needed,
                "pt_load_alignments": alignments,
                "imports": imported,
                "exports": exported,
                "version_definitions": version_definitions,
                "version_requirements": version_requirements,
            }
            result["native"][name] = {
                "sha256": sha256_bytes(payload),
                "size": len(payload),
                "soname": soname,
                "needed": sorted(needed),
                "pt_load_alignments": [hex(value) for value in alignments],
                "version_definitions": sorted(version_definitions),
                "version_requirements": {
                    owner: sorted(versions)
                    for owner, versions in sorted(version_requirements.items())
                },
            }

    direct_dependencies = {
        owner: {
            closure_dependency_name(dependency, kind)
            for dependency in dependencies
        }
        for owner, dependencies in MPV_PLAYER_DIRECT_DEPENDENCIES.items()
    }
    for owner, dependencies in direct_dependencies.items():
        missing = dependencies - elf_metadata[owner]["needed"]
        if missing:
            raise ValueError(
                f"{owner} is missing direct FFmpeg dependencies: {sorted(missing)}"
            )

    provider_versions: dict[str, set[str]] = {}
    for source_name, version_prefix in FFMPEG_VERSION_PREFIXES.items():
        provider_name = closure_library_name(source_name, kind)
        definitions = elf_metadata[provider_name]["version_definitions"]
        matching_definitions = {
            version for version in definitions if version.startswith(version_prefix)
        }
        if not matching_definitions:
            raise ValueError(
                f"{provider_name} exports no {version_prefix} version namespace"
            )
        provider_versions[provider_name] = definitions

    for consumer_name, metadata in elf_metadata.items():
        for provider_name, required_versions in metadata[
            "version_requirements"
        ].items():
            if provider_name not in provider_versions:
                continue
            missing_versions = required_versions - provider_versions[provider_name]
            if missing_versions:
                raise ValueError(
                    f"{consumer_name} requires unavailable versions from "
                    f"{provider_name}: {sorted(missing_versions)}"
                )

    libcxx_exports = elf_metadata["libc++_shared.so"]["exports"]
    for owner in ("libmpv.so", "libplayer.so"):
        imports = elf_metadata[owner]["imports"]
        required_libcxx = {
            symbol
            for symbol in imports
            if symbol.startswith(LIBCXX_IMPORT_PREFIXES)
        }
        missing_libcxx = required_libcxx - libcxx_exports
        if missing_libcxx:
            raise ValueError(
                f"{owner} imports C++ symbols absent from libc++_shared.so: "
                f"{sorted(missing_libcxx)}"
            )
    if not REQUIRED_LIBCXX_SYMBOLS <= elf_metadata["libmpv.so"]["imports"]:
        raise ValueError("libmpv.so lacks the required float/double from_chars imports")
    if not REQUIRED_LIBCXX_SYMBOLS <= libcxx_exports:
        raise ValueError(
            "libc++_shared.so lacks the required float/double from_chars exports"
        )

    avformat_name = closure_library_name("libavformat.so", kind)
    avformat_payload = payloads[avformat_name]
    require_markers(
        avformat_payload,
        {
            *MPV_REQUIRED_TLS_MARKERS,
            f"Mbed TLS {common['mbedtls']['version']}".encode("ascii"),
            b"RSA-PSS",
            b"https",
            profile_info["ffmpeg"]["version"].encode("ascii"),
        },
        avformat_name,
    )
    forbidden_curl_markers = {
        b"--enable-libcurl",
        b"--enable-curl",
        b"libcurl.so",
        b"curl_easy_perform",
    }
    present_curl_markers = sorted(
        marker.decode("ascii")
        for marker in forbidden_curl_markers
        if marker in avformat_payload
    )
    if present_curl_markers:
        raise ValueError(
            f"{avformat_name} contains forbidden curl markers: {present_curl_markers}"
        )

    mpv_payload = payloads["libmpv.so"]
    require_markers(
        mpv_payload,
        {
            common["mpv"]["commit"][:9].encode("ascii"),
            profile_info["ffmpeg"]["version"].encode("ascii"),
            b"libplacebo",
            b"shaderc",
        },
        "libmpv.so",
    )

    result["members"] = members
    result["classes"] = sorted(MPV_REQUIRED_CLASS_MEMBERS)
    result["contract"] = {
        "abi": common["build_contract"]["abi"],
        "android_platform": common["build_contract"]["android_platform"],
        "minimum_pt_load": hex(minimum_alignment),
        "native_zip_alignment": hex(NATIVE_ZIP_ALIGNMENT),
        "mpv": common["mpv"]["version"],
        "ffmpeg": profile_info["ffmpeg"]["version"],
        "mbedtls": common["mbedtls"]["version"],
        "rsa_pss": common["mbedtls"]["rsa_pss"],
        "curl": common["build_contract"]["curl"],
    }
    return result


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--aar", type=Path, required=True)
    parser.add_argument("--readelf", type=Path, required=True)
    parser.add_argument(
        "--profile",
        choices=("m8_security_refresh", "m9_ffmpeg_major_candidate"),
        required=True,
    )
    parser.add_argument("--kind", choices=("source", "thin"), required=True)
    parser.add_argument("--json", action="store_true")
    args = parser.parse_args()

    result = audit_closure(
        args.aar.resolve(),
        args.readelf.resolve(),
        args.profile,
        args.kind,
    )
    if args.json:
        print(json.dumps(result, indent=2, sort_keys=True))
    else:
        print(
            "Player native closure audit passed: "
            f"profile={result['profile']} kind={result['kind']} "
            f"sha256={result['sha256']} size={result['size']} "
            f"native={len(result['native'])}"
        )
        for name, metadata in sorted(result["native"].items()):
            print(
                f"  {name}: sha256={metadata['sha256']} "
                f"needed={len(metadata['needed'])} "
                f"pt_load={metadata['pt_load_alignments']}"
            )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
