#!/usr/bin/env python3
"""Materialize Kiyori's deterministic arm64 player native AARs."""

from __future__ import annotations

import argparse
import hashlib
import io
import shutil
import tempfile
import urllib.request
import zipfile
from pathlib import Path


MPV_RELEASE_URL = (
    "https://github.com/Riteshp2001/mpvlibAndroid/releases/download/"
    "2026-06-25/mpv-android-lib-2026-06-25.aar"
)
MPV_INPUT_SHA256 = "ca0d1c60ddfe5bad46c369d0d0bf6c2d1a8bb5ec5a3ab916c33f892d0263a75e"
MPV_OUTPUT_SHA256 = "ecdc87102e7b4a9bb9c9d46af863f7c32b25b9aab7a161614af6646ffd603f70"
MPV_OUTPUT_RELATIVE_PATH = Path("app/libs/mpv-player-arm64.aar")
FFMPEG_RELEASE_URL = (
    "https://repo.maven.apache.org/maven2/dev/ffmpegkit-maintained/"
    "ffmpeg-kit-full/8.1.7/ffmpeg-kit-full-8.1.7.aar"
)
FFMPEG_INPUT_SHA256 = "c3cbc81d498175fd2aa69ee2dfe7dafbf519052a96283c2568fe5b3b16618456"
FFMPEG_OUTPUT_SHA256 = "1a30a94226bf2157927ec6edbb20154f9a1c1c53580f59cf55efe46db87a5ab3"
FFMPEG_OUTPUT_RELATIVE_PATH = Path("app/libs/ffmpeg-kit-player-arm64.aar")
MPV_THIN_MEMBERS = (
    "R.txt",
    "AndroidManifest.xml",
    "classes.jar",
    "assets/cacert.pem",
    "assets/subfont.ttf",
    "META-INF/com/android/build/gradle/aar-metadata.properties",
    "jni/arm64-v8a/libc++_shared.so",
    "jni/arm64-v8a/libmpv.so",
    "jni/arm64-v8a/libplayer.so",
)
MPV_REQUIRED_NON_EMPTY_MEMBERS = tuple(
    name for name in MPV_THIN_MEMBERS if name != "R.txt"
)
MPV_REQUIRED_CLASS_MEMBERS = {
    "is/xyz/mpv/MPVLib.class",
    "is/xyz/mpv/MPVLib$EventObserver.class",
    "is/xyz/mpv/MPVNode.class",
}
MPV_NATIVE_LIBRARY_NAMES = {
    "libc++_shared.so",
    "libmpv.so",
    "libplayer.so",
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


def deterministic_zip_info(name: str) -> zipfile.ZipInfo:
    info = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
    # Stored members make the transformed hash independent of host zlib versions.
    info.compress_type = zipfile.ZIP_STORED
    info.create_system = 3
    info.external_attr = 0o100644 << 16
    return info


def build_thin_aar(source_aar: Path, output_aar: Path) -> None:
    output_aar.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(source_aar) as source:
        names = set(source.namelist())
        missing = [name for name in MPV_THIN_MEMBERS if name not in names]
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
                for name in MPV_THIN_MEMBERS:
                    target.writestr(deterministic_zip_info(name), source.read(name))
            validate_thin_aar(temporary_path)
            temporary_path.replace(output_aar)
        finally:
            temporary_path.unlink(missing_ok=True)


def validate_thin_aar(path: Path) -> None:
    if not path.is_file():
        raise FileNotFoundError(f"thin mpv AAR is missing: {path}")
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
                    target.writestr(deterministic_zip_info(name), source.read(name))
            validate_ffmpeg_player_aar(temporary_path)
            temporary_path.replace(output_aar)
        finally:
            temporary_path.unlink(missing_ok=True)


def validate_ffmpeg_player_aar(path: Path) -> None:
    if not path.is_file():
        raise FileNotFoundError(f"player FFmpegKit AAR is missing: {path}")
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


def materialize_mpv_player_dependency(
    repository: Path,
    input_aar: Path | None = None,
) -> Path:
    repository = repository.resolve()
    remove_retired_player_native_owners(repository)
    output_aar = repository / MPV_OUTPUT_RELATIVE_PATH
    if input_aar is not None:
        source_aar = input_aar.resolve()
        require_sha256(source_aar, MPV_INPUT_SHA256)
        build_thin_aar(source_aar, output_aar)
    else:
        if output_aar.is_file():
            validate_thin_aar(output_aar)
            require_sha256(output_aar, MPV_OUTPUT_SHA256)
            return output_aar
        with tempfile.TemporaryDirectory(prefix="kiyori-mpv-input-") as directory:
            source_aar = Path(directory) / "mpv-android-lib-2026-06-25.aar"
            download_verified_input(MPV_RELEASE_URL, MPV_INPUT_SHA256, source_aar)
            build_thin_aar(source_aar, output_aar)
    validate_thin_aar(output_aar)
    require_sha256(output_aar, MPV_OUTPUT_SHA256)
    return output_aar


def materialize_ffmpeg_player_dependency(
    repository: Path,
    input_aar: Path | None = None,
) -> Path:
    repository = repository.resolve()
    output_aar = repository / FFMPEG_OUTPUT_RELATIVE_PATH
    if input_aar is not None:
        source_aar = input_aar.resolve()
        require_sha256(source_aar, FFMPEG_INPUT_SHA256)
        build_ffmpeg_player_aar(source_aar, output_aar)
    else:
        if output_aar.is_file():
            validate_ffmpeg_player_aar(output_aar)
            require_sha256(output_aar, FFMPEG_OUTPUT_SHA256)
            return output_aar
        with tempfile.TemporaryDirectory(prefix="kiyori-ffmpeg-input-") as directory:
            source_aar = Path(directory) / "ffmpeg-kit-full-8.1.7.aar"
            download_verified_input(FFMPEG_RELEASE_URL, FFMPEG_INPUT_SHA256, source_aar)
            build_ffmpeg_player_aar(source_aar, output_aar)
    validate_ffmpeg_player_aar(output_aar)
    require_sha256(output_aar, FFMPEG_OUTPUT_SHA256)
    return output_aar


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repository", type=Path, required=True)
    parser.add_argument("--mpv-input-aar", "--input-aar", dest="mpv_input_aar", type=Path)
    parser.add_argument("--ffmpeg-input-aar", type=Path)
    args = parser.parse_args()

    mpv_output = materialize_mpv_player_dependency(args.repository, args.mpv_input_aar)
    ffmpeg_output = materialize_ffmpeg_player_dependency(
        args.repository,
        args.ffmpeg_input_aar,
    )
    print(
        f"Prepared {mpv_output} from input sha256={MPV_INPUT_SHA256}; "
        f"output sha256={sha256_file(mpv_output)}"
    )
    print(
        f"Prepared {ffmpeg_output} from input sha256={FFMPEG_INPUT_SHA256}; "
        f"output sha256={sha256_file(ffmpeg_output)}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
