from __future__ import annotations

import stat
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO_ROOT / "ci" / "script"))

from prepare_android_dependencies import (  # noqa: E402
    extract_archive,
    synchronize_native_runtime,
    validate_member,
    verify_outputs,
)


class AndroidDependencyArchiveTest(unittest.TestCase):
    def test_regular_member_is_accepted(self) -> None:
        validate_member(zipfile.ZipInfo("app/libs/library.aar"), "app/libs")

    def test_root_ancestor_directory_is_accepted(self) -> None:
        validate_member(zipfile.ZipInfo("app/"), "app/libs")

    def test_root_ancestor_file_is_rejected(self) -> None:
        with self.assertRaisesRegex(ValueError, "outside the allowed root"):
            validate_member(zipfile.ZipInfo("app"), "app/libs")

    def test_parent_traversal_is_rejected(self) -> None:
        with self.assertRaisesRegex(ValueError, "escapes the repository"):
            validate_member(zipfile.ZipInfo("../outside.txt"), "app/libs")

    def test_absolute_path_is_rejected(self) -> None:
        with self.assertRaisesRegex(ValueError, "escapes the repository"):
            validate_member(zipfile.ZipInfo("/tmp/outside.txt"), "app/libs")

    def test_other_repository_root_is_rejected(self) -> None:
        with self.assertRaisesRegex(ValueError, "outside the allowed root"):
            validate_member(zipfile.ZipInfo("app/build.gradle.kts"), "app/libs")

    def test_symbolic_link_member_is_rejected(self) -> None:
        info = zipfile.ZipInfo("app/libs/library.aar")
        info.external_attr = (stat.S_IFLNK | 0o777) << 16

        with self.assertRaisesRegex(ValueError, "unsafe file type"):
            validate_member(info, "app/libs")

    def test_special_file_member_is_rejected(self) -> None:
        info = zipfile.ZipInfo("app/libs/library.aar")
        info.external_attr = (stat.S_IFIFO | 0o644) << 16

        with self.assertRaisesRegex(ValueError, "unsafe file type"):
            validate_member(info, "app/libs")

    def test_existing_symlink_in_destination_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            repository = root / "repository"
            outside = root / "outside"
            repository.mkdir()
            outside.mkdir()
            (repository / "app").symlink_to(outside, target_is_directory=True)
            archive = root / "libs.zip"
            with zipfile.ZipFile(archive, "w") as stream:
                stream.writestr("app/libs/library.aar", b"library")

            with self.assertRaisesRegex(ValueError, "contains a symbolic link"):
                extract_archive(archive, repository)

    def test_library_verification_uses_current_regular_files(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            repository = root / "repository"
            repository.mkdir()
            archive = root / "libs.zip"
            with zipfile.ZipFile(archive, "w") as stream:
                stream.mkdir("app/libs/fake.jar/")
                stream.writestr("app/libs/readme.txt", b"not a library")

            extracted = extract_archive(archive, repository)

            with self.assertRaisesRegex(ValueError, "did not provide"):
                verify_outputs("jvm", repository, extracted)

    def test_native_runtime_is_owned_by_declared_dependencies(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            repository = root / "repository"
            ffmpeg_aar = repository / "app" / "libs" / "ffmpeg-kit-local.aar"
            ffmpeg_aar.parent.mkdir(parents=True)
            with zipfile.ZipFile(ffmpeg_aar, "w") as stream:
                stream.writestr("classes.jar", b"classes")
                stream.writestr("jni/arm64-v8a/libc++_shared.so", b"old-aar-libcxx")

            jni_root = repository / "app" / "src" / "main" / "jniLibs"
            stale_gif = jni_root / "arm64-v8a" / "libpl_droidsonroids_gif.so"
            stale_gif.parent.mkdir(parents=True)
            stale_gif.write_bytes(b"old-gif")

            android_ndk = root / "android-ndk"
            ndk_libcxx = (
                android_ndk
                / "toolchains"
                / "llvm"
                / "prebuilt"
                / "linux-x86_64"
                / "sysroot"
                / "usr"
                / "lib"
                / "aarch64-linux-android"
                / "libc++_shared.so"
            )
            ndk_libcxx.parent.mkdir(parents=True)
            ndk_libcxx.write_bytes(b"ndk-libcxx")

            extracted = {ffmpeg_aar, stale_gif}
            synchronize_native_runtime(repository, android_ndk, extracted)

            self.assertFalse(stale_gif.exists())
            app_libcxx = jni_root / "arm64-v8a" / "libc++_shared.so"
            self.assertEqual(app_libcxx.read_bytes(), b"ndk-libcxx")
            self.assertIn(app_libcxx, extracted)
            with zipfile.ZipFile(ffmpeg_aar) as stream:
                self.assertEqual(stream.read("classes.jar"), b"classes")
                self.assertNotIn("jni/arm64-v8a/libc++_shared.so", stream.namelist())


class FFmpegBuildScriptTest(unittest.TestCase):
    def test_ffmpeg_source_commit_is_pinned(self) -> None:
        script = (REPO_ROOT / "tools" / "ffmpeg" / "build_ffmpeg_kit_wsl.sh").read_text(
            encoding="utf-8"
        )

        self.assertIn(
            'FFMPEG_KIT_COMMIT="d6be56d7aec286eb3c292d6b23ff07a6b70d8693"',
            script,
        )
        self.assertIn('rev-parse --verify HEAD', script)
        self.assertIn('diff --cached --quiet', script)

    def test_ffmpeg_import_targets_current_repository(self) -> None:
        script = (
            REPO_ROOT / "tools" / "ffmpeg" / "import_local_ffmpeg_kit.ps1"
        ).read_text(encoding="utf-8")

        self.assertNotIn("/mnt/d/Code/prog/assistance", script)
        self.assertIn("wslpath -a $stagedAar", script)
        self.assertIn("$targetWslPath", script)
        self.assertIn("validate_ffmpeg_aar.py", script)
        self.assertLess(script.index("--aar $stagedAar"), script.index("Move-Item"))


if __name__ == "__main__":
    unittest.main()
