#!/usr/bin/env python3
"""Audit Kiyori's source or deterministic thin FFmpegKit Android closure."""

from __future__ import annotations

import argparse
import hashlib
import io
import json
import tempfile
import zipfile
from pathlib import Path
from typing import Any

from audit_player_native_closure import (
    parse_dynamic,
    parse_dynamic_symbols,
    parse_load_alignments,
    parse_version_info,
    run_readelf,
)
from prepare_mpv_player_dependency import (
    FFMPEG_NATIVE_LIBRARY_NAMES,
    native_zip_data_offsets,
)


REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
MANIFEST_PATH = (
    REPOSITORY_ROOT
    / "tools"
    / "ffmpegkit_native_build"
    / "closure_manifest.json"
)
SOURCE_LOCK_PATH = (
    REPOSITORY_ROOT
    / "tools"
    / "ffmpegkit_native_build"
    / "source_lock.json"
)
REQUIRED_CLASS_MEMBERS = {
    "com/arthenica/ffmpegkit/Abi.class",
    "com/arthenica/ffmpegkit/AbiDetect.class",
    "com/arthenica/ffmpegkit/AbstractSession.class",
    "com/arthenica/ffmpegkit/AsyncFFmpegExecuteTask.class",
    "com/arthenica/ffmpegkit/AsyncFFprobeExecuteTask.class",
    "com/arthenica/ffmpegkit/AsyncGetMediaInformationTask.class",
    "com/arthenica/ffmpegkit/CameraSupport.class",
    "com/arthenica/ffmpegkit/Chapter.class",
    "com/arthenica/ffmpegkit/DeepLTranslationProvider.class",
    "com/arthenica/ffmpegkit/FFmpegKit.class",
    "com/arthenica/ffmpegkit/FFmpegKitConfig$1.class",
    "com/arthenica/ffmpegkit/FFmpegKitConfig$2.class",
    "com/arthenica/ffmpegkit/FFmpegKitConfig$SAFProtocolUrl.class",
    "com/arthenica/ffmpegkit/FFmpegKitConfig.class",
    "com/arthenica/ffmpegkit/FFmpegSession.class",
    "com/arthenica/ffmpegkit/FFmpegSessionCompleteCallback.class",
    "com/arthenica/ffmpegkit/FFprobeKit.class",
    "com/arthenica/ffmpegkit/FFprobeSession.class",
    "com/arthenica/ffmpegkit/FFprobeSessionCompleteCallback.class",
    "com/arthenica/ffmpegkit/GoogleTranslateProvider.class",
    "com/arthenica/ffmpegkit/Level.class",
    "com/arthenica/ffmpegkit/LibreTranslateProvider.class",
    "com/arthenica/ffmpegkit/Log.class",
    "com/arthenica/ffmpegkit/LogCallback.class",
    "com/arthenica/ffmpegkit/LogRedirectionStrategy.class",
    "com/arthenica/ffmpegkit/MediaInformation.class",
    "com/arthenica/ffmpegkit/MediaInformationJsonParser.class",
    "com/arthenica/ffmpegkit/MediaInformationSession.class",
    "com/arthenica/ffmpegkit/MediaInformationSessionCompleteCallback.class",
    "com/arthenica/ffmpegkit/NativeLoader.class",
    "com/arthenica/ffmpegkit/Packages.class",
    "com/arthenica/ffmpegkit/ReturnCode.class",
    "com/arthenica/ffmpegkit/Session.class",
    "com/arthenica/ffmpegkit/SessionState.class",
    "com/arthenica/ffmpegkit/Signal.class",
    "com/arthenica/ffmpegkit/Statistics.class",
    "com/arthenica/ffmpegkit/StatisticsCallback.class",
    "com/arthenica/ffmpegkit/StreamInformation.class",
    "com/arthenica/ffmpegkit/TranslationProvider.class",
    "com/arthenica/ffmpegkit/WhisperKit.class",
}
REQUIRED_NON_NATIVE_MEMBERS = {
    "R.txt",
    "AndroidManifest.xml",
    "classes.jar",
    "proguard.txt",
    "res/raw/source.txt",
    "META-INF/com/android/build/gradle/aar-metadata.properties",
}
SOURCE_DIRECTORY_MEMBERS = {
    "res/",
    "res/raw/",
    "jni/",
    "jni/arm64-v8a/",
}
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
FORBIDDEN_OLD_MARKERS = {
    b"n8.1.2",
    b"LIBAVCODEC_62",
    b"LIBAVDEVICE_62",
    b"LIBAVFILTER_11",
    b"LIBAVFORMAT_62",
    b"LIBAVUTIL_60",
    b"LIBSWRESAMPLE_6",
    b"LIBSWSCALE_9",
}


def sha256_bytes(payload: bytes) -> str:
    return hashlib.sha256(payload).hexdigest()


def load_manifest() -> dict[str, Any]:
    with MANIFEST_PATH.open(encoding="utf-8") as stream:
        manifest = json.load(stream)
    if manifest["schema"] != 1:
        raise ValueError(
            f"unsupported FFmpegKit closure manifest schema: {manifest['schema']}"
        )
    return manifest


def load_source_lock() -> dict[str, Any]:
    with SOURCE_LOCK_PATH.open(encoding="utf-8") as stream:
        source_lock = json.load(stream)
    if source_lock["schema"] != 1:
        raise ValueError(
            "unsupported FFmpegKit source lock schema: "
            f"{source_lock['schema']}"
        )
    return source_lock


def source_identity_markers(
    manifest: dict[str, Any],
    source_lock: dict[str, Any],
) -> tuple[str, ...]:
    openh264 = next(
        (
            source
            for source in source_lock["sources"]
            if source["name"] == "openh264"
        ),
        None,
    )
    if openh264 is None:
        raise ValueError("FFmpegKit source lock is missing OpenH264")
    return (
        manifest["framework"]["commit"],
        manifest["framework"]["wrapper_version"],
        manifest["ffmpeg"]["tag"],
        manifest["ffmpeg"]["commit"],
        openh264["repository"],
        openh264["tag"],
        openh264["commit"],
        manifest["build_contract"]["android_ndk_revision"],
        *(
            patch["sha256"]
            for patch in manifest["source_patches"]
        ),
    )


def expected_member_names(
    manifest: dict[str, Any],
    kind: str,
) -> set[str]:
    non_native = REQUIRED_NON_NATIVE_MEMBERS | set(
        manifest["license_resources"]
    )
    native = {
        f"jni/arm64-v8a/{name}" for name in FFMPEG_NATIVE_LIBRARY_NAMES
    }
    if kind == "source":
        native.add("jni/arm64-v8a/libc++_shared.so")
        return non_native | native | SOURCE_DIRECTORY_MEMBERS
    if kind == "thin":
        return non_native | native
    raise ValueError(f"unsupported FFmpegKit closure kind: {kind}")


def extract_libcxx_provider(aar: Path) -> bytes:
    if not aar.is_file():
        raise FileNotFoundError(f"libc++ provider AAR is missing: {aar}")
    with zipfile.ZipFile(aar) as archive:
        member = "jni/arm64-v8a/libc++_shared.so"
        if member not in archive.namelist():
            raise ValueError(f"libc++ provider AAR is missing {member}")
        payload = archive.read(member)
    if not payload:
        raise ValueError("libc++ provider payload is empty")
    return payload


def audit_closure(
    aar: Path,
    readelf: Path,
    libcxx_provider_aar: Path,
    kind: str,
    require_qualified_hash: bool,
) -> dict[str, Any]:
    if not aar.is_file():
        raise FileNotFoundError(f"FFmpegKit closure AAR is missing: {aar}")
    if not readelf.is_file():
        raise FileNotFoundError(f"llvm-readelf is missing: {readelf}")

    manifest = load_manifest()
    source_lock = load_source_lock()
    minimum_alignment = int(
        manifest["build_contract"]["elf_pt_load_minimum"],
        16,
    )
    zip_alignment = int(
        manifest["build_contract"]["native_zip_alignment"],
        16,
    )
    expected_members = expected_member_names(manifest, kind)
    aar_payload = aar.read_bytes()
    aar_sha256 = sha256_bytes(aar_payload)

    if require_qualified_hash:
        artifact_key = "source_aar" if kind == "source" else "thin_candidate"
        expected = manifest["qualified_artifacts"][artifact_key]
        if aar_sha256 != expected["sha256"] or len(aar_payload) != expected["size"]:
            raise ValueError(
                f"{kind} AAR differs from the qualified artifact: "
                f"expected sha256={expected['sha256']} size={expected['size']}, "
                f"got sha256={aar_sha256} size={len(aar_payload)}"
            )

    with zipfile.ZipFile(io.BytesIO(aar_payload)) as archive:
        members = archive.namelist()
        if len(members) != len(set(members)):
            raise ValueError("FFmpegKit closure AAR contains duplicate members")
        if set(members) != expected_members:
            raise ValueError(
                "FFmpegKit closure AAR member list differs: "
                f"missing={sorted(expected_members - set(members))}, "
                f"extra={sorted(set(members) - expected_members)}"
            )

        classes_payload = archive.read("classes.jar")
        with zipfile.ZipFile(io.BytesIO(classes_payload)) as classes:
            class_members = {
                name for name in classes.namelist() if name.endswith(".class")
            }
        if class_members != REQUIRED_CLASS_MEMBERS:
            raise ValueError(
                "FFmpegKit classes.jar API owner set differs: "
                f"missing={sorted(REQUIRED_CLASS_MEMBERS - class_members)}, "
                f"extra={sorted(class_members - REQUIRED_CLASS_MEMBERS)}"
            )

        for member in manifest["license_resources"]:
            if not archive.read(member):
                raise ValueError(f"FFmpegKit license resource is empty: {member}")
        gpl_license = manifest["build_contract"]["gpl_license_resource"]
        gpl_payload = archive.read(gpl_license["resource_path"])
        if sha256_bytes(gpl_payload) != str(gpl_license["sha256"]).lower():
            raise ValueError(
                "FFmpegKit GPL license resource differs from the repository "
                f"contract: {gpl_license['resource_path']}"
            )
        source_text = archive.read("res/raw/source.txt").decode("utf-8")
        for marker in source_identity_markers(manifest, source_lock):
            if marker not in source_text:
                raise ValueError(
                    f"FFmpegKit source identity is missing marker {marker}"
                )

        native_members = sorted(
            member
            for member in members
            if member.startswith("jni/arm64-v8a/")
            and member.endswith(".so")
        )
        payloads = {
            Path(member).name: archive.read(member)
            for member in native_members
        }

    if kind == "thin":
        offsets = native_zip_data_offsets(aar)
        misaligned = {
            name: offset
            for name, offset in offsets.items()
            if offset % zip_alignment != 0
        }
        if misaligned:
            raise ValueError(
                "FFmpegKit thin AAR native ZIP offsets are not aligned: "
                f"{misaligned}"
            )
    else:
        offsets = {}

    for owner, payload in payloads.items():
        if any(marker in payload for marker in FORBIDDEN_OLD_MARKERS):
            present = sorted(
                marker.decode("ascii")
                for marker in FORBIDDEN_OLD_MARKERS
                if marker in payload
            )
            raise ValueError(
                f"{owner} contains forbidden FFmpeg 8 markers: {present}"
            )
        namespaced = sorted(
            marker.decode("ascii")
            for marker in (
                b"libmpcodec.so",
                b"libmpdevice.so",
                b"libmpfilter.so",
                b"libmpformat.so",
                b"libmputil.so",
                b"libmpresample.so",
                b"libmpscale.so",
            )
            if marker in payload
        )
        if namespaced:
            raise ValueError(
                f"normal-name FFmpegKit owner {owner} references {namespaced}"
            )

    libcxx_payload = extract_libcxx_provider(libcxx_provider_aar)
    elf_payloads = dict(payloads)
    elf_payloads["libc++_shared.so"] = libcxx_payload
    elf_metadata: dict[str, dict[str, Any]] = {}
    result: dict[str, Any] = {
        "aar": str(aar.resolve()),
        "sha256": aar_sha256,
        "size": len(aar_payload),
        "kind": kind,
        "native": {},
        "zip_offsets": {
            name: hex(offset) for name, offset in sorted(offsets.items())
        },
        "libcxx_provider": {
            "aar": str(libcxx_provider_aar.resolve()),
            "sha256": sha256_bytes(libcxx_payload),
            "size": len(libcxx_payload),
        },
    }

    with tempfile.TemporaryDirectory(
        prefix="kiyori-ffmpegkit-native-audit-"
    ) as directory:
        temporary_root = Path(directory)
        for name, payload in elf_payloads.items():
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

            symbol_output = run_readelf(
                readelf,
                "--dyn-syms",
                "-W",
                elf=elf,
            )
            imported, exported = parse_dynamic_symbols(symbol_output)
            version_output = run_readelf(
                readelf,
                "--version-info",
                "-W",
                elf=elf,
            )
            version_definitions, version_requirements = parse_version_info(
                version_output
            )
            elf_metadata[name] = {
                "needed": needed,
                "imports": imported,
                "exports": exported,
                "version_definitions": version_definitions,
                "version_requirements": version_requirements,
            }
            if name != "libc++_shared.so":
                result["native"][name] = {
                    "sha256": sha256_bytes(payload),
                    "size": len(payload),
                    "soname": soname,
                    "needed": sorted(needed),
                    "pt_load_alignments": [
                        hex(value) for value in alignments
                    ],
                    "version_definitions": sorted(version_definitions),
                    "version_requirements": {
                        provider: sorted(versions)
                        for provider, versions in sorted(
                            version_requirements.items()
                        )
                    },
                }

    internal_names = set(FFMPEG_NATIVE_LIBRARY_NAMES)
    system_allowlist = set(
        manifest["build_contract"]["system_needed_allowlist"]
    )
    for owner, metadata in elf_metadata.items():
        allowed = internal_names | system_allowlist
        if owner != "libc++_shared.so":
            allowed.add("libc++_shared.so")
        unexpected = metadata["needed"] - allowed
        if unexpected:
            raise ValueError(
                f"{owner} has unexpected DT_NEEDED owners: "
                f"{sorted(unexpected)}"
            )

    for library_name, version_name in manifest["ffmpeg_majors"].items():
        definitions = elf_metadata[library_name]["version_definitions"]
        if version_name not in definitions:
            raise ValueError(
                f"{library_name} does not export {version_name}: "
                f"{sorted(definitions)}"
            )

    for consumer, metadata in elf_metadata.items():
        for provider, versions in metadata["version_requirements"].items():
            if provider not in elf_metadata:
                continue
            missing = (
                versions
                - elf_metadata[provider]["version_definitions"]
            )
            if missing:
                raise ValueError(
                    f"{consumer} requires unavailable versions from "
                    f"{provider}: {sorted(missing)}"
                )

    for owner, required_exports in manifest[
        "required_native_exports"
    ].items():
        missing = set(required_exports) - elf_metadata[owner]["exports"]
        if missing:
            raise ValueError(
                f"{owner} is missing required exports: {sorted(missing)}"
            )

    forbidden_exit_imports = {
        "exit",
        "_exit",
        "quick_exit",
    } & elf_metadata["libffmpegkit.so"]["imports"]
    if forbidden_exit_imports:
        raise ValueError(
            "libffmpegkit.so imports forbidden process exits: "
            f"{sorted(forbidden_exit_imports)}"
        )

    libcxx_exports = elf_metadata["libc++_shared.so"]["exports"]
    for owner, metadata in elf_metadata.items():
        if owner == "libc++_shared.so":
            continue
        required_libcxx = {
            symbol
            for symbol in metadata["imports"]
            if symbol.startswith(LIBCXX_IMPORT_PREFIXES)
        }
        missing = required_libcxx - libcxx_exports
        if missing:
            raise ValueError(
                f"{owner} imports C++ symbols absent from the player-owned "
                f"libc++_shared.so: {sorted(missing)}"
            )

    avutil_payload = payloads["libavutil.so"]
    for flag in manifest["build_contract"]["required_configure_flags"]:
        if flag.encode("ascii") not in avutil_payload:
            raise ValueError(
                f"libavutil.so is missing required configure flag {flag}"
            )
    for flag in manifest["build_contract"]["forbidden_configure_flags"]:
        if flag.encode("ascii") in avutil_payload:
            raise ValueError(
                f"libavutil.so contains forbidden configure flag {flag}"
            )
    if manifest["ffmpeg"]["version"].encode("ascii") not in avutil_payload:
        raise ValueError(
            "libavutil.so does not contain the fixed FFmpeg version marker"
        )
    if (
        manifest["framework"]["wrapper_version"].encode("ascii")
        not in payloads["libffmpegkit.so"]
    ):
        raise ValueError(
            "libffmpegkit.so does not contain the fixed wrapper version"
        )

    qualified_profiles = manifest.get("qualified_conversion_profiles", [])
    for profile in qualified_profiles:
        profile_id = profile["id"]
        for library_name, markers in profile["required_binary_markers"].items():
            library_payload = payloads.get(library_name)
            if library_payload is None:
                raise ValueError(
                    f"Qualified FFmpeg profile {profile_id} references missing {library_name}"
                )
            for marker in markers:
                if marker.encode("ascii") not in library_payload:
                    raise ValueError(
                        f"Qualified FFmpeg profile {profile_id} is missing marker "
                        f"{marker!r} in {library_name}"
                    )

    for library_name, markers in manifest["build_contract"].get(
        "required_binary_markers",
        {},
    ).items():
        library_payload = payloads.get(library_name)
        if library_payload is None:
            raise ValueError(
                f"FFmpegKit closure marker contract references missing {library_name}"
            )
        for marker in markers:
            if marker.encode("ascii") not in library_payload:
                raise ValueError(
                    f"FFmpegKit closure is missing marker {marker!r} "
                    f"in {library_name}"
                )

    result["members"] = members
    result["classes"] = sorted(REQUIRED_CLASS_MEMBERS)
    result["contract"] = {
        "wrapper": manifest["framework"]["wrapper_version"],
        "ffmpeg": manifest["ffmpeg"]["version"],
        "abi": manifest["build_contract"]["abi"],
        "android_api": manifest["build_contract"]["android_api"],
        "minimum_pt_load": hex(minimum_alignment),
        "native_zip_alignment": hex(zip_alignment),
        "qualified_conversion_profiles": [
            profile["id"] for profile in qualified_profiles
        ],
    }
    return result


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--aar", type=Path, required=True)
    parser.add_argument("--readelf", type=Path, required=True)
    parser.add_argument("--libcxx-provider-aar", type=Path, required=True)
    parser.add_argument("--kind", choices=("source", "thin"), required=True)
    parser.add_argument("--require-qualified-hash", action="store_true")
    parser.add_argument("--json", action="store_true")
    args = parser.parse_args()

    result = audit_closure(
        args.aar.resolve(),
        args.readelf.resolve(),
        args.libcxx_provider_aar.resolve(),
        args.kind,
        args.require_qualified_hash,
    )
    if args.json:
        print(json.dumps(result, indent=2, sort_keys=True))
    else:
        print(
            "FFmpegKit native closure audit passed: "
            f"kind={result['kind']} sha256={result['sha256']} "
            f"size={result['size']} native={len(result['native'])}"
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
