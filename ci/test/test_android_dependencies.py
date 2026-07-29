from __future__ import annotations

import stat
import sys
import tempfile
import unittest
import zipfile
import hashlib
import io
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO_ROOT / "ci" / "script"))

from prepare_android_dependencies import (  # noqa: E402
    extract_archive,
    synchronize_native_runtime,
    validate_member,
    verify_outputs,
)
from prepare_mpv_player_dependency import (  # noqa: E402
    FFMPEG_NATIVE_LIBRARY_NAMES,
    FFMPEG_REQUIRED_MEMBERS,
    MPV_FFMPEG_NAMESPACE_RENAMES,
    MPV_REQUIRED_CLASS_MEMBERS,
    MPV_REQUIRED_TLS_MARKERS,
    MPV_THIN_MEMBER_SOURCES,
    MPV_THIN_MEMBERS,
    build_ffmpeg_player_aar,
    build_thin_aar,
    namespace_mpv_native_payload,
    remove_retired_player_native_owners,
    validate_ffmpeg_player_aar,
    validate_thin_aar,
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
            android_ndk.mkdir()
            manual_libcxx = jni_root / "arm64-v8a" / "libc++_shared.so"
            manual_libcxx.write_bytes(b"manual-libcxx")

            extracted = {ffmpeg_aar, stale_gif, manual_libcxx}
            for removed in remove_retired_player_native_owners(repository):
                extracted.discard(removed)
            synchronize_native_runtime(repository, android_ndk, extracted)

            self.assertFalse(stale_gif.exists())
            self.assertFalse(manual_libcxx.exists())
            self.assertFalse(ffmpeg_aar.exists())
            self.assertNotIn(ffmpeg_aar, extracted)

    def test_thin_mpv_aar_has_exact_deterministic_members(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "source.aar"
            first = root / "first.aar"
            second = root / "second.aar"
            classes_payload = io.BytesIO()
            with zipfile.ZipFile(classes_payload, "w") as classes:
                for name in sorted(MPV_REQUIRED_CLASS_MEMBERS):
                    classes.writestr(name, b"class")
            with zipfile.ZipFile(source, "w") as stream:
                for source_name, _ in MPV_THIN_MEMBER_SOURCES:
                    if source_name == "R.txt":
                        payload = b""
                    elif source_name == "classes.jar":
                        payload = classes_payload.getvalue()
                    else:
                        payload = f"payload:{source_name}".encode()
                        if source_name.endswith("/libmpv.so"):
                            payload += b"".join(
                                name.encode("ascii")
                                for name in MPV_FFMPEG_NAMESPACE_RENAMES
                            )
                        elif source_name.endswith("/libplayer.so"):
                            payload += b"".join(
                                name.encode("ascii")
                                for name in (
                                    "libavcodec.so",
                                    "libavformat.so",
                                    "libavutil.so",
                                    "libswscale.so",
                                )
                            )
                        elif source_name.endswith("/libavformat.so"):
                            payload += b"".join(MPV_REQUIRED_TLS_MARKERS)
                    stream.writestr(source_name, payload)
                stream.writestr("jni/x86_64/libc++_shared.so", b"other-abi-libcxx")

            build_thin_aar(source, first)
            build_thin_aar(source, second)
            validate_thin_aar(first)

            with zipfile.ZipFile(first) as stream:
                self.assertEqual(tuple(stream.namelist()), MPV_THIN_MEMBERS)
            self.assertEqual(
                hashlib.sha256(first.read_bytes()).hexdigest(),
                hashlib.sha256(second.read_bytes()).hexdigest(),
            )

    def test_mpv_namespace_rewrite_preserves_payload_size(self) -> None:
        source_payload = b"|".join(
            name.encode("ascii")
            for name in MPV_FFMPEG_NAMESPACE_RENAMES
        )
        namespaced_payload = namespace_mpv_native_payload(source_payload)

        self.assertEqual(len(source_payload), len(namespaced_payload))
        for source_name, namespaced_name in MPV_FFMPEG_NAMESPACE_RENAMES.items():
            self.assertNotIn(source_name.encode("ascii"), namespaced_payload)
            self.assertIn(namespaced_name.encode("ascii"), namespaced_payload)

    def test_ffmpeg_player_aar_has_only_owned_arm64_native_members(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "source.aar"
            first = root / "first.aar"
            second = root / "second.aar"
            with zipfile.ZipFile(source, "w") as stream:
                for name in sorted(FFMPEG_REQUIRED_MEMBERS):
                    stream.writestr(name, f"payload:{name}".encode())
                stream.writestr("res/raw/license_component.txt", b"license")
                for library_name in sorted(FFMPEG_NATIVE_LIBRARY_NAMES):
                    stream.writestr(
                        f"jni/arm64-v8a/{library_name}",
                        f"arm64:{library_name}".encode(),
                    )
                    stream.writestr(
                        f"jni/x86_64/{library_name}",
                        f"x86_64:{library_name}".encode(),
                    )
                stream.writestr(
                    "jni/arm64-v8a/libc++_shared.so",
                    b"ffmpeg-libcxx",
                )
                stream.writestr(
                    "jni/x86_64/libc++_shared.so",
                    b"x86_64-libcxx",
                )

            build_ffmpeg_player_aar(source, first)
            build_ffmpeg_player_aar(source, second)
            validate_ffmpeg_player_aar(first)

            with zipfile.ZipFile(first) as stream:
                native_members = {
                    name for name in stream.namelist() if name.startswith("jni/")
                }
                self.assertEqual(
                    {
                        f"jni/arm64-v8a/{name}"
                        for name in FFMPEG_NATIVE_LIBRARY_NAMES
                    },
                    native_members,
                )
                self.assertIn("res/raw/license_component.txt", stream.namelist())
            self.assertEqual(
                hashlib.sha256(first.read_bytes()).hexdigest(),
                hashlib.sha256(second.read_bytes()).hexdigest(),
            )


if __name__ == "__main__":
    unittest.main()
