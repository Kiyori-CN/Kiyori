from __future__ import annotations

import io
import struct
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO_ROOT / "ci" / "script"))

from validate_ffmpeg_aar import (  # noqa: E402
    EXPECTED_NATIVE_LIBRARIES,
    REQUIRED_JAVA_CLASSES,
    audit_arm64_elf,
    validate_ffmpeg_aar,
)


def elf_with_load_alignment(alignment: int) -> bytes:
    data = bytearray(64 + 56)
    data[:6] = b"\x7fELF\x02\x01"
    struct.pack_into("<H", data, 18, 183)
    struct.pack_into("<Q", data, 32, 64)
    struct.pack_into("<H", data, 54, 56)
    struct.pack_into("<H", data, 56, 1)
    struct.pack_into("<I", data, 64, 1)
    struct.pack_into("<Q", data, 64 + 48, alignment)
    return bytes(data)


def classes_jar() -> bytes:
    output = io.BytesIO()
    with zipfile.ZipFile(output, "w") as archive:
        for class_name in REQUIRED_JAVA_CLASSES:
            archive.writestr(class_name, b"class")
    return output.getvalue()


def write_aar(path: Path, alignment: int) -> None:
    with zipfile.ZipFile(path, "w") as archive:
        archive.writestr("classes.jar", classes_jar())
        for library in EXPECTED_NATIVE_LIBRARIES:
            archive.writestr(
                f"jni/arm64-v8a/{library}",
                elf_with_load_alignment(alignment),
            )


class ValidateFfmpegAarTest(unittest.TestCase):
    def test_complete_16kb_arm64_aar_is_accepted(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            aar = Path(directory) / "ffmpeg-kit.aar"
            write_aar(aar, 0x4000)

            audits = validate_ffmpeg_aar(aar)

            self.assertEqual(len(audits), len(EXPECTED_NATIVE_LIBRARIES))

    def test_4kb_load_alignment_is_rejected(self) -> None:
        with self.assertRaisesRegex(ValueError, "below 16 KB"):
            audit_arm64_elf("libavcodec.so", elf_with_load_alignment(0x1000))

    def test_missing_native_library_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            aar = Path(directory) / "ffmpeg-kit.aar"
            write_aar(aar, 0x4000)
            with zipfile.ZipFile(aar, "a") as archive:
                archive.writestr("jni/arm64-v8a/unexpected.so", elf_with_load_alignment(0x4000))

            with self.assertRaisesRegex(ValueError, "native library set does not match"):
                validate_ffmpeg_aar(aar)


if __name__ == "__main__":
    unittest.main()
