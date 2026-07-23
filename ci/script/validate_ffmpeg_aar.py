#!/usr/bin/env python3
"""Validate the API, ABI, and 16 KB ELF contract of a local ffmpeg-kit AAR."""

from __future__ import annotations

import argparse
import hashlib
import io
import struct
import zipfile
from dataclasses import dataclass
from pathlib import Path, PurePosixPath


ARM64_ABI = "arm64-v8a"
MIN_LOAD_ALIGNMENT = 16 * 1024
EXPECTED_NATIVE_LIBRARIES = frozenset(
    {
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
)
REQUIRED_JAVA_CLASSES = frozenset(
    {
        "com/arthenica/ffmpegkit/FFmpegKit.class",
        "com/arthenica/ffmpegkit/FFmpegKitConfig.class",
        "com/arthenica/ffmpegkit/FFprobeKit.class",
        "com/arthenica/ffmpegkit/MediaInformation.class",
        "com/arthenica/ffmpegkit/ReturnCode.class",
    }
)
ELF_MAGIC = b"\x7fELF"
ELFCLASS64 = 2
ELFDATA2LSB = 1
EM_AARCH64 = 183
PT_LOAD = 1
ELF64_HEADER_SIZE = 64
ELF64_PROGRAM_HEADER_SIZE = 56


@dataclass(frozen=True)
class ElfAudit:
    name: str
    load_alignments: tuple[int, ...]


def _validate_archive_names(names: list[str]) -> None:
    seen: set[PurePosixPath] = set()
    for name in names:
        if "\\" in name:
            raise ValueError(f"AAR member uses a backslash path: {name}")
        member = PurePosixPath(name)
        if member.is_absolute() or ".." in member.parts:
            raise ValueError(f"AAR member escapes the archive root: {name}")
        if member in seen:
            raise ValueError(f"AAR contains a duplicate member: {name}")
        seen.add(member)


def _validate_java_api(archive: zipfile.ZipFile) -> None:
    classes_entries = [name for name in archive.namelist() if name == "classes.jar"]
    if len(classes_entries) != 1:
        raise ValueError("AAR must contain exactly one classes.jar")
    try:
        with zipfile.ZipFile(io.BytesIO(archive.read("classes.jar"))) as classes:
            class_names = set(classes.namelist())
    except zipfile.BadZipFile as error:
        raise ValueError("AAR classes.jar is not a valid ZIP archive") from error
    missing = REQUIRED_JAVA_CLASSES - class_names
    if missing:
        raise ValueError(
            "AAR is missing required ffmpeg-kit Java APIs: " + ", ".join(sorted(missing))
        )


def audit_arm64_elf(name: str, data: bytes) -> ElfAudit:
    if len(data) < ELF64_HEADER_SIZE or data[:4] != ELF_MAGIC:
        raise ValueError(f"native library is not an ELF file: {name}")
    if data[4] != ELFCLASS64 or data[5] != ELFDATA2LSB:
        raise ValueError(f"native library is not 64-bit little-endian ELF: {name}")
    machine = struct.unpack_from("<H", data, 18)[0]
    if machine != EM_AARCH64:
        raise ValueError(f"native library is not AArch64 ELF: {name} (machine={machine})")

    program_offset = struct.unpack_from("<Q", data, 32)[0]
    entry_size = struct.unpack_from("<H", data, 54)[0]
    entry_count = struct.unpack_from("<H", data, 56)[0]
    if entry_size < ELF64_PROGRAM_HEADER_SIZE or entry_count == 0:
        raise ValueError(f"native library has no valid program headers: {name}")
    table_end = program_offset + entry_size * entry_count
    if program_offset < ELF64_HEADER_SIZE or table_end > len(data):
        raise ValueError(f"native library has a truncated program header table: {name}")

    load_alignments: list[int] = []
    for index in range(entry_count):
        offset = program_offset + index * entry_size
        program_type = struct.unpack_from("<I", data, offset)[0]
        if program_type != PT_LOAD:
            continue
        file_offset = struct.unpack_from("<Q", data, offset + 8)[0]
        virtual_address = struct.unpack_from("<Q", data, offset + 16)[0]
        alignment = struct.unpack_from("<Q", data, offset + 48)[0]
        if alignment < MIN_LOAD_ALIGNMENT:
            raise ValueError(
                f"native library PT_LOAD alignment is below 16 KB: "
                f"{name} ({alignment:#x})"
            )
        if file_offset % alignment != virtual_address % alignment:
            raise ValueError(f"native library has an invalid PT_LOAD congruence: {name}")
        load_alignments.append(alignment)
    if not load_alignments:
        raise ValueError(f"native library has no PT_LOAD segments: {name}")
    return ElfAudit(name=name, load_alignments=tuple(load_alignments))


def validate_ffmpeg_aar(path: Path) -> tuple[ElfAudit, ...]:
    try:
        with zipfile.ZipFile(path) as archive:
            names = archive.namelist()
            _validate_archive_names(names)
            corrupt_member = archive.testzip()
            if corrupt_member is not None:
                raise ValueError(f"AAR member failed its CRC check: {corrupt_member}")
            _validate_java_api(archive)

            native_entries = [
                name
                for name in names
                if name.startswith("jni/") and name.endswith(".so")
            ]
            abi_names = {PurePosixPath(name).parts[1] for name in native_entries}
            if abi_names != {ARM64_ABI}:
                raise ValueError(
                    "AAR native ABI set must be exactly arm64-v8a: "
                    + ", ".join(sorted(abi_names))
                )
            expected_entries = {
                f"jni/{ARM64_ABI}/{library}" for library in EXPECTED_NATIVE_LIBRARIES
            }
            if set(native_entries) != expected_entries:
                missing = expected_entries - set(native_entries)
                unexpected = set(native_entries) - expected_entries
                details = []
                if missing:
                    details.append("missing=" + ",".join(sorted(missing)))
                if unexpected:
                    details.append("unexpected=" + ",".join(sorted(unexpected)))
                raise ValueError("AAR native library set does not match: " + "; ".join(details))

            return tuple(
                audit_arm64_elf(name, archive.read(name))
                for name in sorted(expected_entries)
            )
    except zipfile.BadZipFile as error:
        raise ValueError(f"ffmpeg-kit AAR is not a valid ZIP archive: {path}") from error


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        while chunk := stream.read(1024 * 1024):
            digest.update(chunk)
    return digest.hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--aar", type=Path, required=True)
    args = parser.parse_args()

    audits = validate_ffmpeg_aar(args.aar)
    minimum_alignment = min(
        alignment for audit in audits for alignment in audit.load_alignments
    )
    print(
        f"Validated ffmpeg-kit AAR: {args.aar} "
        f"sha256={sha256_file(args.aar)} abi={ARM64_ABI} "
        f"libraries={len(audits)} min_load_alignment={minimum_alignment:#x}"
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError) as error:
        raise SystemExit(f"ffmpeg-kit AAR validation failed: {error}") from error
