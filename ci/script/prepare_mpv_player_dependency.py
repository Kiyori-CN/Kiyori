#!/usr/bin/env python3
"""Materialize Kiyori's deterministic arm64 player native AARs."""

from __future__ import annotations

import argparse
import hashlib
import io
import shutil
import struct
import subprocess
import sys
import tempfile
import urllib.request
import zipfile
from pathlib import Path


MPV_LEGACY_RELEASE_URL = (
    "https://github.com/Riteshp2001/mpvlibAndroid/releases/download/"
    "2026-06-25/mpv-android-lib-2026-06-25.aar"
)
MPV_LEGACY_INPUT_SHA256 = (
    "ca0d1c60ddfe5bad46c369d0d0bf6c2d1a8bb5ec5a3ab916c33f892d0263a75e"
)
MPV_LEGACY_OUTPUT_SHA256 = (
    "fc983b7ed0c8b8be1938283fe94108dfdc593aa31608d55dd1ce119ae201c32c"
)
MPV_OUTPUT_SHA256 = "f52aca6f35c651be7aab55f2efe6b5f40180d1ebaeb1404cc446470bf8deb6a4"
MPV_OUTPUT_RELATIVE_PATH = Path("app/libs/mpv-player-arm64.aar")
MPV_LEGACY_CANDIDATE_RELATIVE_PATH = Path(
    "work/player-native-outputs/legacy_2026_06_25/mpv-player-thin-candidate.aar"
)
MPV_SELECTED_SOURCE_CLOSURE_PROFILE = "m9_ffmpeg_major_candidate"
PLAYER_CLOSURE_AUDIT_SCRIPT = (
    Path(__file__).resolve().with_name("audit_player_native_closure.py")
)
FFMPEGKIT_CLOSURE_AUDIT_SCRIPT = (
    Path(__file__).resolve().with_name("audit_ffmpegkit_native_closure.py")
)
PLAYER_CLOSURE_PROFILES = (
    "m8_security_refresh",
    "m9_ffmpeg_major_candidate",
)
FFMPEG_OUTPUT_SHA256 = "86d97cc0174ff44a8057899bef7b8e66bd976e5cfa7bba7d2a9fc819cb8efca7"
FFMPEG_OUTPUT_RELATIVE_PATH = Path("app/libs/ffmpeg-kit-player-arm64.aar")
MPV_PASSTHROUGH_MEMBERS = (
    "R.txt",
    "AndroidManifest.xml",
    "classes.jar",
    "assets/cacert.pem",
    "assets/subfont.ttf",
    "META-INF/com/android/build/gradle/aar-metadata.properties",
)
MPV_FFMPEG_NAMESPACE_RENAMES = {
    "libavcodec.so": "libmpcodec.so",
    "libavdevice.so": "libmpdevice.so",
    "libavfilter.so": "libmpfilter.so",
    "libavformat.so": "libmpformat.so",
    "libavutil.so": "libmputil.so",
    "libswresample.so": "libmpresample.so",
    "libswscale.so": "libmpscale.so",
}
MPV_THIN_MEMBER_SOURCES = (
    *((name, name) for name in MPV_PASSTHROUGH_MEMBERS),
    (
        "jni/arm64-v8a/libc++_shared.so",
        "jni/arm64-v8a/libc++_shared.so",
    ),
    *(
        (
            f"jni/arm64-v8a/{source_name}",
            f"jni/arm64-v8a/{namespaced_name}",
        )
        for source_name, namespaced_name in MPV_FFMPEG_NAMESPACE_RENAMES.items()
    ),
    (
        "jni/arm64-v8a/libmpv.so",
        "jni/arm64-v8a/libmpv.so",
    ),
    (
        "jni/arm64-v8a/libplayer.so",
        "jni/arm64-v8a/libplayer.so",
    ),
)
MPV_THIN_MEMBERS = tuple(
    output_name for _, output_name in MPV_THIN_MEMBER_SOURCES
)
MPV_SOURCE_MEMBERS = tuple(
    source_name for source_name, _ in MPV_THIN_MEMBER_SOURCES
)
MPV_REQUIRED_NON_EMPTY_MEMBERS = tuple(
    name for name in MPV_THIN_MEMBERS if name != "R.txt"
)
MPV_REQUIRED_CLASS_MEMBERS = {
    "is/xyz/mpv/MPVLib.class",
    "is/xyz/mpv/MPVLib$EventObserver.class",
    "is/xyz/mpv/MPVLib$LogObserver.class",
    "is/xyz/mpv/MPVNode.class",
    "is/xyz/mpv/Utils.class",
}
MPV_NATIVE_LIBRARY_NAMES = {
    "libc++_shared.so",
    *MPV_FFMPEG_NAMESPACE_RENAMES.values(),
    "libmpv.so",
    "libplayer.so",
}
MPV_REQUIRED_TLS_MARKERS = (
    b"--enable-mbedtls",
    b"mbedtls_ssl_handshake",
)
MPV_PLAYER_DIRECT_DEPENDENCIES = {
    "libmpv.so": set(MPV_FFMPEG_NAMESPACE_RENAMES.values()),
    "libplayer.so": {
        "libmpcodec.so",
        "libmpformat.so",
        "libmputil.so",
        "libmpscale.so",
    },
}
FFMPEG_NATIVE_LIBRARY_NAMES = {
    "libavcodec.so",
    "libavdevice.so",
    "libavfilter.so",
    "libavformat.so",
    "libavutil.so",
    "libffmpegkit.so",
    "libffmpegkit_abidetect.so",
    "libswresample.so",
    "libswscale.so",
}
FFMPEG_REQUIRED_MEMBERS = {
    "AndroidManifest.xml",
    "classes.jar",
    "proguard.txt",
    "res/raw/license.txt",
    "res/raw/source.txt",
    "META-INF/com/android/build/gradle/aar-metadata.properties",
}
RETIRED_PLAYER_NATIVE_PATH = Path("app/libs/ffmpeg-kit-local.aar")
NATIVE_ZIP_ALIGNMENT = 16 * 1024
NATIVE_ALIGNMENT_EXTRA_FIELD_ID = 0xA11E


def remove_retired_player_native_owners(repository: Path) -> set[Path]:
    repository = repository.resolve()
    candidates = {repository / RETIRED_PLAYER_NATIVE_PATH}
    candidates.update(
        (repository / "app" / "src" / "main" / "jniLibs").glob(
            "*/libc++_shared.so"
        )
    )
    removed: set[Path] = set()
    for path in candidates:
        if path.is_file():
            path.unlink()
            removed.add(path)
    return removed


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        while chunk := stream.read(1024 * 1024):
            digest.update(chunk)
    return digest.hexdigest()


def require_sha256(path: Path, expected_sha256: str) -> None:
    actual_sha256 = sha256_file(path)
    if actual_sha256 != expected_sha256:
        raise ValueError(
            f"unexpected SHA-256 for {path}: expected {expected_sha256}, got {actual_sha256}"
        )


def normalize_sha256(value: str) -> str:
    normalized = value.strip().lower()
    if len(normalized) != 64 or any(
        character not in "0123456789abcdef" for character in normalized
    ):
        raise ValueError(f"expected a 64-character SHA-256, got {value!r}")
    return normalized


def deterministic_zip_info(name: str) -> zipfile.ZipInfo:
    info = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
    # Stored members make the transformed hash independent of host zlib versions.
    info.compress_type = zipfile.ZIP_STORED
    info.create_system = 3
    info.external_attr = 0o100644 << 16
    return info


def write_deterministic_member(
    target: zipfile.ZipFile,
    name: str,
    payload: bytes,
) -> None:
    info = deterministic_zip_info(name)
    if name.startswith("jni/") and name.endswith(".so"):
        if target.fp is None:
            raise RuntimeError("deterministic AAR output stream is closed")
        local_header_offset = target.fp.tell()
        encoded_name = name.encode("ascii")
        unpadded_data_offset = local_header_offset + 30 + len(encoded_name)
        padding = (-unpadded_data_offset) % NATIVE_ZIP_ALIGNMENT
        if 0 < padding < 4:
            padding += NATIVE_ZIP_ALIGNMENT
        if padding:
            extra_payload_size = padding - 4
            info.extra = struct.pack(
                "<HH",
                NATIVE_ALIGNMENT_EXTRA_FIELD_ID,
                extra_payload_size,
            ) + bytes(extra_payload_size)
    target.writestr(info, payload)


def native_zip_data_offsets(path: Path) -> dict[str, int]:
    offsets: dict[str, int] = {}
    with path.open("rb") as raw, zipfile.ZipFile(raw) as archive:
        for entry in archive.infolist():
            if not (
                entry.filename.startswith("jni/")
                and entry.filename.endswith(".so")
            ):
                continue
            if entry.compress_type != zipfile.ZIP_STORED:
                raise ValueError(
                    f"native AAR member must be stored: {entry.filename}"
                )
            raw.seek(entry.header_offset)
            local_header = raw.read(30)
            if len(local_header) != 30:
                raise ValueError(
                    f"truncated ZIP local header for {entry.filename}"
                )
            (
                signature,
                _version,
                _flags,
                _compression,
                _time,
                _date,
                _crc,
                _compressed_size,
                _uncompressed_size,
                name_length,
                extra_length,
            ) = struct.unpack("<IHHHHHIIIHH", local_header)
            if signature != 0x04034B50:
                raise ValueError(
                    f"invalid ZIP local header for {entry.filename}"
                )
            offsets[entry.filename] = (
                entry.header_offset + 30 + name_length + extra_length
            )
    return offsets


def validate_native_zip_alignment(path: Path) -> None:
    offsets = native_zip_data_offsets(path)
    misaligned = {
        name: offset
        for name, offset in offsets.items()
        if offset % NATIVE_ZIP_ALIGNMENT != 0
    }
    if misaligned:
        details = ", ".join(
            f"{name}={offset:#x}" for name, offset in sorted(misaligned.items())
        )
        raise ValueError(
            f"native AAR members are not {NATIVE_ZIP_ALIGNMENT}-byte aligned: "
            f"{details}"
        )


def namespace_mpv_native_payload(payload: bytes) -> bytes:
    namespaced_payload = payload
    for source_name, namespaced_name in MPV_FFMPEG_NAMESPACE_RENAMES.items():
        source_bytes = source_name.encode("ascii")
        namespaced_bytes = namespaced_name.encode("ascii")
        if len(source_bytes) != len(namespaced_bytes):
            raise ValueError(
                "mpv FFmpeg namespace rewrite must preserve ELF string length: "
                f"{source_name} -> {namespaced_name}"
            )
        namespaced_payload = namespaced_payload.replace(
            source_bytes,
            namespaced_bytes,
        )
    return namespaced_payload


def build_thin_aar(source_aar: Path, output_aar: Path) -> None:
    output_aar.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(source_aar) as source:
        names = set(source.namelist())
        missing = [name for name in MPV_SOURCE_MEMBERS if name not in names]
        if missing:
            raise ValueError(f"mpv source AAR is missing required members: {missing}")

        with tempfile.NamedTemporaryFile(
            prefix=f".{output_aar.name}.",
            suffix=".tmp",
            dir=output_aar.parent,
            delete=False,
        ) as temporary:
            temporary_path = Path(temporary.name)
        try:
            with zipfile.ZipFile(
                temporary_path,
                mode="w",
                compression=zipfile.ZIP_STORED,
            ) as target:
                for source_name, output_name in MPV_THIN_MEMBER_SOURCES:
                    payload = source.read(source_name)
                    if source_name.startswith("jni/") and source_name.endswith(".so"):
                        payload = namespace_mpv_native_payload(payload)
                    write_deterministic_member(
                        target,
                        output_name,
                        payload,
                    )
            validate_thin_aar(temporary_path)
            temporary_path.replace(output_aar)
        finally:
            temporary_path.unlink(missing_ok=True)


def audit_source_closure(
    repository: Path,
    aar: Path,
    profile: str,
    kind: str,
    readelf: Path,
) -> None:
    subprocess.run(
        [
            sys.executable,
            str(PLAYER_CLOSURE_AUDIT_SCRIPT),
            "--aar",
            str(aar),
            "--readelf",
            str(readelf),
            "--profile",
            profile,
            "--kind",
            kind,
        ],
        cwd=repository,
        check=True,
    )


def audit_ffmpegkit_closure(
    repository: Path,
    aar: Path,
    libcxx_provider_aar: Path,
    readelf: Path,
) -> None:
    subprocess.run(
        [
            sys.executable,
            str(FFMPEGKIT_CLOSURE_AUDIT_SCRIPT),
            "--aar",
            str(aar),
            "--readelf",
            str(readelf),
            "--libcxx-provider-aar",
            str(libcxx_provider_aar),
            "--kind",
            "thin",
            "--require-qualified-hash",
        ],
        cwd=repository,
        check=True,
    )


def source_closure_candidate_path(repository: Path, profile: str) -> Path:
    return (
        repository
        / "work"
        / "player-native-outputs"
        / profile
        / "mpv-player-thin-candidate.aar"
    )


def build_source_closure_candidate(
    repository: Path,
    source_aar: Path,
    profile: str,
    readelf: Path,
    output_aar: Path | None = None,
) -> Path:
    repository = repository.resolve()
    source_aar = source_aar.resolve()
    readelf = readelf.resolve()
    output = (
        output_aar.resolve()
        if output_aar is not None
        else source_closure_candidate_path(repository, profile)
    )
    audit_source_closure(repository, source_aar, profile, "source", readelf)
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(
        prefix=f".{output.name}.",
        suffix=".candidate",
        dir=output.parent,
        delete=False,
    ) as temporary:
        temporary_path = Path(temporary.name)
    try:
        build_thin_aar(source_aar, temporary_path)
        audit_source_closure(
            repository,
            temporary_path,
            profile,
            "thin",
            readelf,
        )
        temporary_path.replace(output)
    finally:
        temporary_path.unlink(missing_ok=True)
    return output


def promote_m9_closure_pair(
    repository: Path,
    mpv_candidate_aar: Path,
    ffmpegkit_candidate_aar: Path,
    profile: str,
    readelf: Path,
    expected_mpv_sha256: str,
    expected_ffmpegkit_sha256: str,
) -> tuple[Path, Path]:
    repository = repository.resolve()
    mpv_candidate_aar = mpv_candidate_aar.resolve()
    ffmpegkit_candidate_aar = ffmpegkit_candidate_aar.resolve()
    readelf = readelf.resolve()
    expected_mpv = normalize_sha256(expected_mpv_sha256)
    expected_ffmpegkit = normalize_sha256(expected_ffmpegkit_sha256)
    if profile != MPV_SELECTED_SOURCE_CLOSURE_PROFILE:
        raise ValueError(
            "paired promotion only accepts the selected M9 source closure; "
            f"got {profile}"
        )
    if expected_mpv != MPV_OUTPUT_SHA256:
        raise ValueError(
            "mpv promotion SHA-256 differs from the selected M9 contract: "
            f"expected {MPV_OUTPUT_SHA256}, got {expected_mpv}"
        )
    if expected_ffmpegkit != FFMPEG_OUTPUT_SHA256:
        raise ValueError(
            "FFmpegKit promotion SHA-256 differs from the selected M9 contract: "
            f"expected {FFMPEG_OUTPUT_SHA256}, got {expected_ffmpegkit}"
        )
    require_sha256(mpv_candidate_aar, expected_mpv)
    require_sha256(ffmpegkit_candidate_aar, expected_ffmpegkit)
    audit_source_closure(
        repository,
        mpv_candidate_aar,
        profile,
        "thin",
        readelf,
    )
    audit_ffmpegkit_closure(
        repository,
        ffmpegkit_candidate_aar,
        mpv_candidate_aar,
        readelf,
    )

    remove_retired_player_native_owners(repository)
    mpv_output = repository / MPV_OUTPUT_RELATIVE_PATH
    ffmpegkit_output = repository / FFMPEG_OUTPUT_RELATIVE_PATH
    mpv_output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(
        prefix=f".{mpv_output.name}.",
        suffix=".promotion",
        dir=mpv_output.parent,
        delete=False,
    ) as mpv_temporary:
        mpv_temporary_path = Path(mpv_temporary.name)
    with tempfile.NamedTemporaryFile(
        prefix=f".{ffmpegkit_output.name}.",
        suffix=".promotion",
        dir=ffmpegkit_output.parent,
        delete=False,
    ) as ffmpegkit_temporary:
        ffmpegkit_temporary_path = Path(ffmpegkit_temporary.name)
    try:
        shutil.copyfile(mpv_candidate_aar, mpv_temporary_path)
        shutil.copyfile(
            ffmpegkit_candidate_aar,
            ffmpegkit_temporary_path,
        )
        require_sha256(mpv_temporary_path, expected_mpv)
        require_sha256(ffmpegkit_temporary_path, expected_ffmpegkit)
        audit_source_closure(
            repository,
            mpv_temporary_path,
            profile,
            "thin",
            readelf,
        )
        audit_ffmpegkit_closure(
            repository,
            ffmpegkit_temporary_path,
            mpv_temporary_path,
            readelf,
        )
        ffmpegkit_temporary_path.replace(ffmpegkit_output)
        mpv_temporary_path.replace(mpv_output)
    finally:
        mpv_temporary_path.unlink(missing_ok=True)
        ffmpegkit_temporary_path.unlink(missing_ok=True)

    require_sha256(mpv_output, expected_mpv)
    require_sha256(ffmpegkit_output, expected_ffmpegkit)
    audit_source_closure(
        repository,
        mpv_output,
        profile,
        "thin",
        readelf,
    )
    audit_ffmpegkit_closure(
        repository,
        ffmpegkit_output,
        mpv_output,
        readelf,
    )
    return mpv_output, ffmpegkit_output


def validate_thin_aar(path: Path) -> None:
    if not path.is_file():
        raise FileNotFoundError(f"thin mpv AAR is missing: {path}")
    validate_native_zip_alignment(path)
    with zipfile.ZipFile(path) as stream:
        names = stream.namelist()
        if tuple(names) != MPV_THIN_MEMBERS:
            raise ValueError(f"thin mpv AAR member list differs: {names}")
        for name in names:
            if name.startswith("jni/") and not name.startswith("jni/arm64-v8a/"):
                raise ValueError(f"thin mpv AAR contains another ABI: {name}")
            if name.startswith("jni/") and Path(name).name not in MPV_NATIVE_LIBRARY_NAMES:
                raise ValueError(f"thin mpv AAR contains an unowned native library: {name}")
        for name in MPV_REQUIRED_NON_EMPTY_MEMBERS:
            if not stream.read(name):
                raise ValueError(f"thin mpv AAR member is empty: {name}")
        with zipfile.ZipFile(io.BytesIO(stream.read("classes.jar"))) as classes:
            packaged_classes = set(classes.namelist())
        missing_classes = MPV_REQUIRED_CLASS_MEMBERS - packaged_classes
        if missing_classes:
            raise ValueError(
                f"thin mpv AAR is missing runtime classes: {sorted(missing_classes)}"
            )
        native_members = tuple(name for name in names if name.startswith("jni/"))
        old_names = tuple(
            name.encode("ascii") for name in MPV_FFMPEG_NAMESPACE_RENAMES
        )
        for member in native_members:
            payload = stream.read(member)
            remaining_old_names = [
                name.decode("ascii") for name in old_names if name in payload
            ]
            if remaining_old_names:
                raise ValueError(
                    f"thin mpv AAR member {member} still references "
                    f"non-namespaced FFmpeg libraries: {remaining_old_names}"
                )

        for namespaced_name in MPV_FFMPEG_NAMESPACE_RENAMES.values():
            member = f"jni/arm64-v8a/{namespaced_name}"
            payload = stream.read(member)
            if namespaced_name.encode("ascii") not in payload:
                raise ValueError(
                    f"namespaced mpv FFmpeg member has no matching SONAME: {member}"
                )

        for owner_name, dependency_names in MPV_PLAYER_DIRECT_DEPENDENCIES.items():
            owner = f"jni/arm64-v8a/{owner_name}"
            payload = stream.read(owner)
            missing_dependencies = [
                name
                for name in sorted(dependency_names)
                if name.encode("ascii") not in payload
            ]
            if missing_dependencies:
                raise ValueError(
                    f"{owner} is missing namespaced FFmpeg dependencies: "
                    f"{missing_dependencies}"
                )

        mpv_avformat = stream.read("jni/arm64-v8a/libmpformat.so")
        missing_tls_markers = [
            marker.decode("ascii")
            for marker in MPV_REQUIRED_TLS_MARKERS
            if marker not in mpv_avformat
        ]
        if missing_tls_markers:
            raise ValueError(
                "namespaced mpv libavformat lacks the fixed mbedTLS build markers: "
                f"{missing_tls_markers}"
            )


def build_ffmpeg_player_aar(source_aar: Path, output_aar: Path) -> None:
    output_aar.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(source_aar) as source:
        selected_members = []
        for entry in source.infolist():
            if entry.is_dir():
                continue
            name = entry.filename
            if name.startswith("jni/"):
                if not name.startswith("jni/arm64-v8a/"):
                    continue
                if Path(name).name not in FFMPEG_NATIVE_LIBRARY_NAMES:
                    continue
            selected_members.append(name)

        with tempfile.NamedTemporaryFile(
            prefix=f".{output_aar.name}.",
            suffix=".tmp",
            dir=output_aar.parent,
            delete=False,
        ) as temporary:
            temporary_path = Path(temporary.name)
        try:
            with zipfile.ZipFile(
                temporary_path,
                mode="w",
                compression=zipfile.ZIP_STORED,
            ) as target:
                for name in selected_members:
                    write_deterministic_member(
                        target,
                        name,
                        source.read(name),
                    )
            validate_ffmpeg_player_aar(temporary_path)
            temporary_path.replace(output_aar)
        finally:
            temporary_path.unlink(missing_ok=True)


def validate_ffmpeg_player_aar(path: Path) -> None:
    if not path.is_file():
        raise FileNotFoundError(f"player FFmpegKit AAR is missing: {path}")
    validate_native_zip_alignment(path)
    with zipfile.ZipFile(path) as stream:
        names = tuple(stream.namelist())
        missing_members = FFMPEG_REQUIRED_MEMBERS - set(names)
        if missing_members:
            raise ValueError(
                "player FFmpegKit AAR is missing required members: "
                f"{sorted(missing_members)}"
            )
        native_members = tuple(name for name in names if name.startswith("jni/"))
        expected_native_members = {
            f"jni/arm64-v8a/{name}" for name in FFMPEG_NATIVE_LIBRARY_NAMES
        }
        if set(native_members) != expected_native_members:
            raise ValueError(
                "player FFmpegKit AAR native member list differs: "
                f"{native_members}"
            )
        for name in names:
            if name.startswith("jni/") and not name.startswith("jni/arm64-v8a/"):
                raise ValueError(f"player FFmpegKit AAR contains another ABI: {name}")
        for name in FFMPEG_REQUIRED_MEMBERS | expected_native_members:
            if not stream.read(name):
                raise ValueError(f"player FFmpegKit AAR member is empty: {name}")


def download_verified_input(
    source_url: str,
    expected_sha256: str,
    destination: Path,
) -> None:
    request = urllib.request.Request(
        source_url,
        headers={"User-Agent": "Kiyori-player-dependency-preparer/1"},
    )
    with urllib.request.urlopen(request, timeout=120) as response, destination.open("wb") as target:
        shutil.copyfileobj(response, target, length=1024 * 1024)
    require_sha256(destination, expected_sha256)


def materialize_legacy_mpv_baseline_candidate(
    repository: Path,
    input_aar: Path | None = None,
) -> Path:
    repository = repository.resolve()
    output_aar = repository / MPV_LEGACY_CANDIDATE_RELATIVE_PATH
    if input_aar is not None:
        source_aar = input_aar.resolve()
        require_sha256(source_aar, MPV_LEGACY_INPUT_SHA256)
        build_thin_aar(source_aar, output_aar)
    else:
        if output_aar.is_file():
            validate_thin_aar(output_aar)
            require_sha256(output_aar, MPV_LEGACY_OUTPUT_SHA256)
            return output_aar
        with tempfile.TemporaryDirectory(prefix="kiyori-mpv-input-") as directory:
            source_aar = Path(directory) / "mpv-android-lib-2026-06-25.aar"
            download_verified_input(
                MPV_LEGACY_RELEASE_URL,
                MPV_LEGACY_INPUT_SHA256,
                source_aar,
            )
            build_thin_aar(source_aar, output_aar)
    validate_thin_aar(output_aar)
    require_sha256(output_aar, MPV_LEGACY_OUTPUT_SHA256)
    return output_aar


def materialize_mpv_player_dependency(repository: Path) -> Path:
    repository = repository.resolve()
    remove_retired_player_native_owners(repository)
    output_aar = repository / MPV_OUTPUT_RELATIVE_PATH
    if not output_aar.is_file():
        raise FileNotFoundError(
            "selected mpv source closure AAR is missing: "
            f"{output_aar}; promote the audited "
            f"{MPV_SELECTED_SOURCE_CLOSURE_PROFILE} candidate with "
            "--promote-m9-mpv-candidate and "
            "--promote-m9-ffmpegkit-candidate"
        )
    validate_thin_aar(output_aar)
    require_sha256(output_aar, MPV_OUTPUT_SHA256)
    return output_aar


def materialize_ffmpeg_player_dependency(repository: Path) -> Path:
    repository = repository.resolve()
    output_aar = repository / FFMPEG_OUTPUT_RELATIVE_PATH
    if not output_aar.is_file():
        raise FileNotFoundError(
            "selected FFmpegKit M9 closure AAR is missing: "
            f"{output_aar}; promote the audited M9 pair"
        )
    validate_ffmpeg_player_aar(output_aar)
    require_sha256(output_aar, FFMPEG_OUTPUT_SHA256)
    return output_aar


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repository", type=Path, required=True)
    parser.add_argument(
        "--mpv-input-aar",
        "--input-aar",
        dest="mpv_input_aar",
        type=Path,
        help=(
            "Fixed 2026-06-25 official AAR used only to reproduce the historical "
            "thin baseline under work/; never writes the selected product AAR."
        ),
    )
    parser.add_argument("--source-closure-aar", type=Path)
    parser.add_argument("--promote-m9-mpv-candidate", type=Path)
    parser.add_argument("--promote-m9-ffmpegkit-candidate", type=Path)
    parser.add_argument(
        "--source-closure-profile",
        choices=PLAYER_CLOSURE_PROFILES,
    )
    parser.add_argument("--native-readelf", type=Path)
    parser.add_argument("--candidate-output", type=Path)
    parser.add_argument("--expected-mpv-sha256")
    parser.add_argument("--expected-ffmpegkit-sha256")
    args = parser.parse_args()

    promotion_requested = (
        args.promote_m9_mpv_candidate is not None
        or args.promote_m9_ffmpegkit_candidate is not None
    )
    if args.source_closure_aar is not None or promotion_requested:
        if args.mpv_input_aar is not None:
            parser.error(
                "source closure modes cannot be combined with legacy fixed AAR inputs"
            )
        if args.source_closure_profile is None or args.native_readelf is None:
            parser.error(
                "source closure modes require --source-closure-profile and "
                "--native-readelf"
            )
        if args.source_closure_aar is not None:
            if promotion_requested:
                parser.error(
                    "candidate generation cannot be combined with paired promotion"
                )
            if (
                args.expected_mpv_sha256 is not None
                or args.expected_ffmpegkit_sha256 is not None
            ):
                parser.error("expected promotion hashes require paired promotion")
            candidate = build_source_closure_candidate(
                args.repository,
                args.source_closure_aar,
                args.source_closure_profile,
                args.native_readelf,
                args.candidate_output,
            )
            print(
                f"Prepared source closure candidate {candidate}; "
                f"sha256={sha256_file(candidate)}; product AAR unchanged"
            )
            return 0
        if args.candidate_output is not None:
            parser.error("--candidate-output is only valid when building a candidate")
        if (
            args.promote_m9_mpv_candidate is None
            or args.promote_m9_ffmpegkit_candidate is None
            or args.expected_mpv_sha256 is None
            or args.expected_ffmpegkit_sha256 is None
        ):
            parser.error(
                "paired M9 promotion requires both candidate paths and "
                "both expected SHA-256 values"
            )
        mpv_output, ffmpegkit_output = promote_m9_closure_pair(
            args.repository,
            args.promote_m9_mpv_candidate,
            args.promote_m9_ffmpegkit_candidate,
            args.source_closure_profile,
            args.native_readelf,
            args.expected_mpv_sha256,
            args.expected_ffmpegkit_sha256,
        )
        print(
            f"Promoted paired M9 mpv closure to {mpv_output}; "
            f"sha256={sha256_file(mpv_output)}"
        )
        print(
            f"Promoted paired M9 FFmpegKit closure to {ffmpegkit_output}; "
            f"sha256={sha256_file(ffmpegkit_output)}"
        )
        return 0

    if (
        args.source_closure_profile is not None
        or args.native_readelf is not None
        or args.candidate_output is not None
        or args.expected_mpv_sha256 is not None
        or args.expected_ffmpegkit_sha256 is not None
    ):
        parser.error(
            "source closure options require candidate generation or paired promotion"
        )

    if args.mpv_input_aar is not None:
        mpv_output = materialize_legacy_mpv_baseline_candidate(
            args.repository,
            args.mpv_input_aar,
        )
        mpv_message = (
            f"Prepared historical mpv baseline {mpv_output} from input "
            f"sha256={MPV_LEGACY_INPUT_SHA256}; "
            f"output sha256={sha256_file(mpv_output)}; product AAR unchanged"
        )
    else:
        mpv_output = materialize_mpv_player_dependency(args.repository)
        mpv_message = (
            f"Validated selected mpv source closure {mpv_output}; "
            f"sha256={sha256_file(mpv_output)}"
        )
    ffmpeg_output = materialize_ffmpeg_player_dependency(args.repository)
    print(mpv_message)
    print(
        f"Validated selected FFmpegKit M9 closure {ffmpeg_output}; "
        f"sha256={sha256_file(ffmpeg_output)}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
