from __future__ import annotations

import hashlib
import io
import json
import stat
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest import mock


REPO_ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO_ROOT / "ci" / "script"))
sys.path.insert(0, str(REPO_ROOT / "tools" / "player_native_build"))

import prepare_mpv_player_dependency as player_dependency  # noqa: E402
import build_ffmpegkit_native_closure as ffmpegkit_builder  # noqa: E402
from audit_player_native_closure import (  # noqa: E402
    SOURCE_DIRECTORY_MEMBERS,
    expected_member_names,
    load_manifest as load_player_manifest,
    parse_dynamic,
    parse_load_alignments,
    parse_version_info,
)
from audit_ffmpegkit_native_closure import (  # noqa: E402
    expected_member_names as expected_ffmpegkit_member_names,
    load_manifest as load_ffmpegkit_manifest,
    load_source_lock as load_ffmpegkit_source_lock,
    source_identity_markers as ffmpegkit_source_identity_markers,
)
from build_ffmpegkit_native_closure import (  # noqa: E402
    require_absolute_wsl_path,
    resolve_native_readelf,
    validate_overlay,
    validate_source_patches,
)
from build_player_native_closure import (  # noqa: E402
    ensure_windows_sdk_junctions,
    patch_unused_source_downloads,
    patch_windows_ffmpeg_source,
    require_host_toolchain,
)
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
    NATIVE_ZIP_ALIGNMENT,
    build_ffmpeg_player_aar,
    build_source_closure_candidate,
    build_thin_aar,
    load_ffmpeg_output_contract,
    materialize_legacy_mpv_baseline_candidate,
    namespace_mpv_native_payload,
    native_zip_data_offsets,
    normalize_sha256,
    promote_m9_ffmpegkit_patch_candidate,
    promote_m9_closure_pair,
    remove_retired_player_native_owners,
    validate_ffmpeg_player_aar,
    validate_thin_aar,
)
from remove_meson_android_rpath import remove_validated_libmpv_rpath  # noqa: E402


def write_synthetic_mpv_source(path: Path) -> None:
    classes_payload = io.BytesIO()
    with zipfile.ZipFile(classes_payload, "w") as classes:
        for name in sorted(MPV_REQUIRED_CLASS_MEMBERS):
            classes.writestr(name, b"class")
    with zipfile.ZipFile(path, "w") as stream:
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


def write_ffmpegkit_output_contract(
    repository: Path,
    sha256: str,
    size: int,
) -> None:
    manifest_path = (
        repository
        / player_dependency.FFMPEGKIT_CLOSURE_MANIFEST_RELATIVE_PATH
    )
    manifest_path.parent.mkdir(parents=True, exist_ok=True)
    manifest_path.write_text(
        json.dumps(
            {
                "schema": 1,
                "qualified_artifacts": {
                    "thin_candidate": {
                        "sha256": sha256,
                        "size": size,
                        "native_count": len(FFMPEG_NATIVE_LIBRARY_NAMES),
                    }
                },
                "selected_product": {
                    "path": str(
                        player_dependency.FFMPEG_OUTPUT_RELATIVE_PATH
                    ).replace("\\", "/"),
                    "sha256": sha256,
                    "size": size,
                    "native_count": len(FFMPEG_NATIVE_LIBRARY_NAMES),
                },
            }
        ),
        encoding="utf-8",
    )


class AndroidDependencyArchiveTest(unittest.TestCase):
    def test_sha256_normalization_accepts_only_full_hex_digest(self) -> None:
        digest = "A1" * 32
        self.assertEqual(normalize_sha256(f"  {digest}\n"), digest.lower())
        for invalid in ("", "0" * 63, "0" * 65, "g" * 64):
            with self.subTest(invalid=invalid):
                with self.assertRaisesRegex(ValueError, "64-character SHA-256"):
                    normalize_sha256(invalid)

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
            write_synthetic_mpv_source(source)

            build_thin_aar(source, first)
            build_thin_aar(source, second)
            validate_thin_aar(first)

            with zipfile.ZipFile(first) as stream:
                self.assertEqual(tuple(stream.namelist()), MPV_THIN_MEMBERS)
            self.assertTrue(
                all(
                    offset % NATIVE_ZIP_ALIGNMENT == 0
                    for offset in native_zip_data_offsets(first).values()
                )
            )
            self.assertEqual(
                hashlib.sha256(first.read_bytes()).hexdigest(),
                hashlib.sha256(second.read_bytes()).hexdigest(),
            )

    def test_source_closure_candidate_does_not_modify_selected_products(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            repository = Path(directory)
            source = repository / "source.aar"
            readelf = repository / "llvm-readelf.exe"
            mpv_product = repository / player_dependency.MPV_OUTPUT_RELATIVE_PATH
            ffmpeg_product = repository / player_dependency.FFMPEG_OUTPUT_RELATIVE_PATH
            mpv_product.parent.mkdir(parents=True)
            mpv_product.write_bytes(b"selected-mpv")
            ffmpeg_product.write_bytes(b"selected-ffmpeg-kit")
            readelf.write_bytes(b"readelf")
            write_synthetic_mpv_source(source)

            with mock.patch.object(
                player_dependency,
                "audit_source_closure",
            ) as audit:
                candidate = build_source_closure_candidate(
                    repository,
                    source,
                    "m8_security_refresh",
                    readelf,
                )

            self.assertTrue(candidate.is_file())
            self.assertEqual(mpv_product.read_bytes(), b"selected-mpv")
            self.assertEqual(ffmpeg_product.read_bytes(), b"selected-ffmpeg-kit")
            self.assertEqual(
                [call.args[3] for call in audit.call_args_list],
                ["source", "thin"],
            )

    def test_legacy_mpv_baseline_is_written_only_under_work(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            repository = Path(directory)
            source = repository / "legacy-source.aar"
            expected = repository / "expected.aar"
            product = repository / player_dependency.MPV_OUTPUT_RELATIVE_PATH
            product.parent.mkdir(parents=True)
            product.write_bytes(b"selected-product")
            write_synthetic_mpv_source(source)
            build_thin_aar(source, expected)

            with (
                mock.patch.object(
                    player_dependency,
                    "MPV_LEGACY_INPUT_SHA256",
                    hashlib.sha256(source.read_bytes()).hexdigest(),
                ),
                mock.patch.object(
                    player_dependency,
                    "MPV_LEGACY_OUTPUT_SHA256",
                    hashlib.sha256(expected.read_bytes()).hexdigest(),
                ),
            ):
                baseline = materialize_legacy_mpv_baseline_candidate(
                    repository,
                    source,
                )

            self.assertEqual(baseline.read_bytes(), expected.read_bytes())
            self.assertTrue(baseline.is_relative_to(repository / "work"))
            self.assertEqual(product.read_bytes(), b"selected-product")

    def test_paired_promotion_rejects_wrong_or_non_m9_identity(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            repository = Path(directory)
            mpv_candidate = repository / "mpv-candidate.aar"
            ffmpegkit_candidate = repository / "ffmpegkit-candidate.aar"
            readelf = repository / "llvm-readelf.exe"
            mpv_candidate.write_bytes(b"mpv-candidate")
            ffmpegkit_candidate.write_bytes(b"ffmpegkit-candidate")
            readelf.write_bytes(b"readelf")

            with self.assertRaisesRegex(
                ValueError,
                "only accepts the selected M9",
            ):
                promote_m9_closure_pair(
                    repository,
                    mpv_candidate,
                    ffmpegkit_candidate,
                    "m8_security_refresh",
                    readelf,
                    player_dependency.MPV_OUTPUT_SHA256,
                    "0" * 64,
                )
            with self.assertRaisesRegex(
                ValueError,
                "mpv promotion SHA-256 differs",
            ):
                promote_m9_closure_pair(
                    repository,
                    mpv_candidate,
                    ffmpegkit_candidate,
                    "m9_ffmpeg_major_candidate",
                    readelf,
                    "0" * 64,
                    "0" * 64,
                )

    def test_ffmpeg_output_contract_rejects_qualified_selected_drift(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            repository = Path(directory)
            write_ffmpegkit_output_contract(repository, "1" * 64, 123)
            manifest_path = (
                repository
                / player_dependency.FFMPEGKIT_CLOSURE_MANIFEST_RELATIVE_PATH
            )
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
            manifest["qualified_artifacts"]["thin_candidate"]["size"] = 122
            manifest_path.write_text(json.dumps(manifest), encoding="utf-8")

            with self.assertRaisesRegex(
                ValueError,
                "must exactly match the qualified thin candidate",
            ):
                load_ffmpeg_output_contract(repository)

    def test_paired_promotion_copies_both_exact_candidates(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            repository = Path(directory)
            mpv_candidate = repository / "mpv-candidate.aar"
            ffmpegkit_candidate = repository / "ffmpegkit-candidate.aar"
            readelf = repository / "llvm-readelf.exe"
            mpv_product = repository / player_dependency.MPV_OUTPUT_RELATIVE_PATH
            ffmpeg_product = repository / player_dependency.FFMPEG_OUTPUT_RELATIVE_PATH
            ffmpeg_product.parent.mkdir(parents=True)
            mpv_candidate.write_bytes(b"audited-mpv-m9-candidate")
            ffmpegkit_candidate.write_bytes(
                b"audited-ffmpegkit-m9-candidate"
            )
            readelf.write_bytes(b"readelf")
            mpv_product.write_bytes(b"selected-m8-mpv")
            ffmpeg_product.write_bytes(b"selected-ffmpeg-kit")
            mpv_sha256 = hashlib.sha256(mpv_candidate.read_bytes()).hexdigest()
            ffmpegkit_sha256 = hashlib.sha256(
                ffmpegkit_candidate.read_bytes()
            ).hexdigest()
            write_ffmpegkit_output_contract(
                repository,
                ffmpegkit_sha256,
                ffmpegkit_candidate.stat().st_size,
            )

            with (
                mock.patch.object(
                    player_dependency,
                    "MPV_OUTPUT_SHA256",
                    mpv_sha256,
                ),
                mock.patch.object(
                    player_dependency,
                    "audit_source_closure",
                ) as mpv_audit,
                mock.patch.object(
                    player_dependency,
                    "audit_ffmpegkit_closure",
                ) as ffmpegkit_audit,
            ):
                promoted_mpv, promoted_ffmpegkit = promote_m9_closure_pair(
                    repository,
                    mpv_candidate,
                    ffmpegkit_candidate,
                    "m9_ffmpeg_major_candidate",
                    readelf,
                    mpv_sha256,
                    ffmpegkit_sha256,
                )

            self.assertEqual(
                promoted_mpv.read_bytes(),
                mpv_candidate.read_bytes(),
            )
            self.assertEqual(
                promoted_ffmpegkit.read_bytes(),
                ffmpegkit_candidate.read_bytes(),
            )
            self.assertEqual(mpv_audit.call_count, 3)
            self.assertEqual(ffmpegkit_audit.call_count, 3)

    def test_ffmpegkit_patch_promotion_preserves_selected_mpv(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            repository = Path(directory)
            ffmpegkit_candidate = repository / "ffmpegkit-candidate.aar"
            readelf = repository / "llvm-readelf.exe"
            mpv_product = repository / player_dependency.MPV_OUTPUT_RELATIVE_PATH
            ffmpeg_product = repository / player_dependency.FFMPEG_OUTPUT_RELATIVE_PATH
            ffmpeg_product.parent.mkdir(parents=True)
            mpv_product.write_bytes(b"selected-mpv-m9")
            ffmpeg_product.write_bytes(b"selected-ffmpeg-kit")
            ffmpegkit_candidate.write_bytes(b"audited-ffmpegkit-patch-candidate")
            readelf.write_bytes(b"readelf")
            mpv_sha256 = hashlib.sha256(mpv_product.read_bytes()).hexdigest()
            ffmpegkit_sha256 = hashlib.sha256(
                ffmpegkit_candidate.read_bytes()
            ).hexdigest()
            original_mpv = mpv_product.read_bytes()
            write_ffmpegkit_output_contract(
                repository,
                ffmpegkit_sha256,
                ffmpegkit_candidate.stat().st_size,
            )

            with (
                mock.patch.object(
                    player_dependency,
                    "MPV_OUTPUT_SHA256",
                    mpv_sha256,
                ),
                mock.patch.object(
                    player_dependency,
                    "audit_source_closure",
                ) as mpv_audit,
                mock.patch.object(
                    player_dependency,
                    "audit_ffmpegkit_closure",
                ) as ffmpegkit_audit,
            ):
                promoted_ffmpegkit = promote_m9_ffmpegkit_patch_candidate(
                    repository,
                    ffmpegkit_candidate,
                    "m9_ffmpeg_major_candidate",
                    readelf,
                    ffmpegkit_sha256,
                )

            self.assertEqual(mpv_product.read_bytes(), original_mpv)
            self.assertEqual(
                promoted_ffmpegkit.read_bytes(),
                ffmpegkit_candidate.read_bytes(),
            )
            self.assertEqual(mpv_audit.call_count, 2)
            self.assertEqual(ffmpegkit_audit.call_count, 3)

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
            self.assertTrue(
                all(
                    offset % NATIVE_ZIP_ALIGNMENT == 0
                    for offset in native_zip_data_offsets(first).values()
                )
            )
            self.assertEqual(
                hashlib.sha256(first.read_bytes()).hexdigest(),
                hashlib.sha256(second.read_bytes()).hexdigest(),
            )

    def test_meson_android_rpath_removal_is_exact_and_idempotent(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            build_ninja = Path(directory) / "build.ninja"
            build_ninja.write_text(
                "build libmpv.so: c_LINKER object.o\n"
                ' LINK_ARGS = before "-Wl,-rpath,D:/ndk/lib:D:/closure/lib" after\n'
                "\n",
                encoding="utf-8",
            )

            self.assertTrue(
                remove_validated_libmpv_rpath(
                    build_ninja,
                    ("D:/ndk/lib", "D:/closure/lib"),
                )
            )
            self.assertNotIn("-Wl,-rpath", build_ninja.read_text(encoding="utf-8"))
            self.assertFalse(
                remove_validated_libmpv_rpath(
                    build_ninja,
                    ("D:/ndk/lib", "D:/closure/lib"),
                )
            )

    def test_meson_android_rpath_removal_rejects_unknown_ownership(self) -> None:
        cases = {
            "path mismatch": (
                "build libmpv.so: c_LINKER object.o\n"
                ' LINK_ARGS = "-Wl,-rpath,D:/other/lib:D:/closure/lib"\n\n'
            ),
            "multiple tokens": (
                "build libmpv.so: c_LINKER object.o\n"
                ' LINK_ARGS = "-Wl,-rpath,D:/ndk/lib:D:/closure/lib" '
                '"-Wl,-rpath,D:/other/lib:D:/extra/lib"\n\n'
            ),
            "other target": (
                "build libmpv.so: c_LINKER object.o\n"
                " LINK_ARGS = before after\n\n"
                "build other.so: c_LINKER other.o\n"
                ' LINK_ARGS = "-Wl,-rpath,D:/ndk/lib:D:/closure/lib"\n\n'
            ),
        }
        for name, source in cases.items():
            with self.subTest(name=name), tempfile.TemporaryDirectory() as directory:
                build_ninja = Path(directory) / "build.ninja"
                build_ninja.write_text(source, encoding="utf-8")
                with self.assertRaises(RuntimeError):
                    remove_validated_libmpv_rpath(
                        build_ninja,
                        ("D:/ndk/lib", "D:/closure/lib"),
                    )

    def test_windows_ffmpeg_source_rules_are_applied_after_checkout(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            workspace = Path(directory)
            with self.assertRaisesRegex(
                FileNotFoundError,
                "fixed FFmpeg checkout must exist",
            ):
                patch_windows_ffmpeg_source(workspace)

            library_mak = (
                workspace
                / "buildscripts"
                / "deps"
                / "ffmpeg"
                / "ffbuild"
                / "library.mak"
            )
            library_mak.parent.mkdir(parents=True)
            library_mak.write_text(
                "\t$(Q)echo $^ > $@.objs\n"
                "\t$(Q)echo $$(filter %.o,$$^) > $$@.objs\n"
                '\t$$(INSTALL) -m 644 $$^ "$(INCINSTDIR)"\n',
                encoding="utf-8",
            )

            patch_windows_ffmpeg_source(workspace)
            first = library_mak.read_text(encoding="utf-8")
            patch_windows_ffmpeg_source(workspace)
            second = library_mak.read_text(encoding="utf-8")

            self.assertEqual(first, second)
            self.assertIn("\t$(file >$@.objs,$(filter %.o,$^))\n", first)
            self.assertIn("\t$$(file >$$@.objs,$$(filter %.o,$$^))\n", first)
            self.assertIn('"$$(lastword $$(INSTALL))" -m 644', first)
            self.assertNotIn("$(Q)echo", first)

    def test_windows_host_toolchain_requires_standard_headers(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            mingw_root = root / "mingw64"
            bin_dir = mingw_root / "bin"
            bin_dir.mkdir(parents=True)
            for name in ("gcc.exe", "g++.exe", "mingw32-make.exe"):
                (bin_dir / name).write_bytes(b"tool")
            cc1plus = (
                mingw_root
                / "libexec"
                / "gcc"
                / "x86_64-w64-mingw32"
                / "16.2.0"
                / "cc1plus.exe"
            )
            cc1plus.parent.mkdir(parents=True)
            cc1plus.write_bytes(b"tool")

            with self.assertRaisesRegex(
                FileNotFoundError,
                "assert.h",
            ):
                require_host_toolchain(root)

    def test_windows_host_toolchain_accepts_complete_layout(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            mingw_root = root / "mingw64"
            bin_dir = mingw_root / "bin"
            bin_dir.mkdir(parents=True)
            for name in ("gcc.exe", "g++.exe", "mingw32-make.exe"):
                (bin_dir / name).write_bytes(b"tool")
            include_dir = mingw_root / "x86_64-w64-mingw32" / "include"
            include_dir.mkdir(parents=True)
            for name in ("assert.h", "stdint.h", "stdio.h"):
                (include_dir / name).write_text("/* header */\n", encoding="ascii")
            cc1plus = (
                mingw_root
                / "libexec"
                / "gcc"
                / "x86_64-w64-mingw32"
                / "16.2.0"
                / "cc1plus.exe"
            )
            cc1plus.parent.mkdir(parents=True)
            cc1plus.write_bytes(b"tool")

            self.assertEqual(require_host_toolchain(root), bin_dir)

    def test_unused_openssl_download_is_removed_from_source_graph(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            workspace = Path(directory)
            download_script = (
                workspace
                / "buildscripts"
                / "include"
                / "download-deps.sh"
            )
            download_script.parent.mkdir(parents=True)
            download_script.write_text(
                "# openssl\n"
                "if [ ! -d openssl ]; then\n"
                "\tmkdir openssl\n"
                "\t$WGET https://github.com/openssl/openssl/releases/download/"
                "openssl-$v_openssl/openssl-$v_openssl.tar.gz -O - | \\\n"
                "\t\ttar -xz -C openssl --strip-components=1\n"
                "fi\n",
                encoding="utf-8",
            )

            patch_unused_source_downloads(workspace)
            first = download_script.read_text(encoding="utf-8")
            patch_unused_source_downloads(workspace)
            second = download_script.read_text(encoding="utf-8")

            self.assertEqual(first, second)
            self.assertIn("OpenSSL is outside the selected source closure", first)
            self.assertIn("FFmpeg uses Mbed TLS, curl is disabled", first)
            self.assertNotIn("openssl-$v_openssl", first)

    def test_windows_sdk_junctions_are_exact_and_idempotent(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            workspace = root / "workspace"
            android_ndk = root / "android-ndk"
            android_sdk = root / "android-sdk"
            android_ndk.mkdir()
            android_sdk.mkdir()

            ensure_windows_sdk_junctions(workspace, android_ndk, android_sdk)
            ensure_windows_sdk_junctions(workspace, android_ndk, android_sdk)

            sdk_root = workspace / "buildscripts" / "sdk"
            self.assertTrue(
                (sdk_root / "android-ndk-r29").samefile(android_ndk)
            )
            self.assertTrue(
                (sdk_root / "android-sdk-linux").samefile(android_sdk)
            )

            wrong_workspace = root / "wrong-workspace"
            wrong_ndk_link = (
                wrong_workspace
                / "buildscripts"
                / "sdk"
                / "android-ndk-r29"
            )
            wrong_ndk_link.mkdir(parents=True)
            with self.assertRaisesRegex(
                RuntimeError,
                "unexpected target",
            ):
                ensure_windows_sdk_junctions(
                    wrong_workspace,
                    android_ndk,
                    android_sdk,
                )

    def test_player_closure_audit_parsers_cover_dynamic_load_and_versions(self) -> None:
        soname, needed, rpath_tags = parse_dynamic(
            " 0x000000000000000e (SONAME) Library soname: [libmpv.so]\n"
            " 0x0000000000000001 (NEEDED) Shared library: [libmpcodec.so]\n"
        )
        self.assertEqual(soname, "libmpv.so")
        self.assertEqual(needed, {"libmpcodec.so"})
        self.assertEqual(rpath_tags, set())
        self.assertEqual(
            parse_load_alignments(
                "  LOAD 0x000000 0x0 0x0 0x10 0x10 R E 0x4000\n"
                "  LOAD 0x004000 0x4000 0x4000 0x20 0x20 RW 0x10000\n"
            ),
            [0x4000, 0x10000],
        )
        definitions, requirements = parse_version_info(
            "Version definition section '.gnu.version_d' contains 1 entry:\n"
            "  0x001c: Rev: 1  Flags: none  Index: 2  Cnt: 1  Name: LIBAVCODEC_62\n"
            "Version needs section '.gnu.version_r' contains 1 entry:\n"
            "  0x0010: Version: 1  File: libavutil.so  Cnt: 1\n"
            "  0x0020:   Name: LIBAVUTIL_60  Flags: none  Version: 2\n"
        )
        self.assertEqual(definitions, {"LIBAVCODEC_62"})
        self.assertEqual(requirements, {"libavutil.so": {"LIBAVUTIL_60"}})

    def test_player_closure_member_contracts_are_exact(self) -> None:
        manifest = load_player_manifest("m9_ffmpeg_major_candidate")
        self.assertEqual(manifest["status"], "dual_m9_selected_locally")
        self.assertEqual(
            manifest["selected_profile"],
            "m9_ffmpeg_major_candidate",
        )
        self.assertEqual(
            manifest["selected_artifacts"]["product_aar_sha256"],
            player_dependency.MPV_OUTPUT_SHA256,
        )
        self.assertEqual(
            manifest["selected_artifacts"]["native_zip_alignment"],
            "0x4000",
        )
        self.assertEqual(
            expected_member_names("source"),
            (*SOURCE_DIRECTORY_MEMBERS, *player_dependency.MPV_SOURCE_MEMBERS),
        )
        self.assertEqual(expected_member_names("thin"), MPV_THIN_MEMBERS)

    def test_ffmpegkit_source_lock_overlay_and_member_contracts_are_exact(
        self,
    ) -> None:
        manifest = load_ffmpegkit_manifest()
        overlay = validate_overlay(REPO_ROOT, manifest)
        self.assertEqual(
            overlay,
            REPO_ROOT / "tools" / "ffmpegkit_native_build" / "overlay",
        )
        self.assertEqual(
            manifest["framework"]["commit"],
            "62b07bf097baf26b416c815aea514e05c9ad6d63",
        )
        self.assertEqual(
            manifest["framework"]["wrapper_version"],
            "8.1.7-kiyori-n9.0.1-r6",
        )
        self.assertEqual(manifest["ffmpeg"]["tag"], "n9.0.1")
        self.assertEqual(
            manifest["ffmpeg"]["commit"],
            "bf1b838f2ab88b4f8fd83443325c782ea0e0f7fa",
        )
        self.assertEqual(
            (
                manifest["overlay"]["file_count"],
                manifest["overlay"]["size"],
                manifest["overlay"]["tree_sha256"],
            ),
            (
                33,
                1_092_823,
                "c550972c8e5e9a1937958a81bd4361519584315a082902d030fc6ca15b33b098",
            ),
        )
        validated_patches = validate_source_patches(REPO_ROOT, manifest)
        self.assertEqual(
            [
                (
                    source,
                    patch.relative_to(REPO_ROOT).as_posix(),
                    sha256,
                )
                for source, patch, sha256 in validated_patches
            ],
            [
                (
                    "framework",
                    "tools/ffmpegkit_native_build/patches/framework/"
                    "0001-respect-explicit-build-job-count.patch",
                    "657a4597a83f310a51c0a8e52f88f4cb9fa81525c3ff9c1e4708497e62a88294",
                ),
                (
                    "framework",
                    "tools/ffmpegkit_native_build/patches/framework/"
                    "0002-reapply-openh264-patch-after-android-reset.patch",
                    "31caacd61531bbcbb9d133cc282089fa04f8ed64a3a13825f715311cd5bb1e5e",
                ),
                (
                    "ffmpeg",
                    "tools/ffmpegkit_native_build/patches/ffmpeg/"
                    "0001-libopenh264-map-constrained-baseline-profile.patch",
                    "03ec5a6c770c7acb5303a996a1f824cde4105677be6a6fdefd497ef1d746a8ee",
                ),
                (
                    "ffmpeg",
                    "tools/ffmpegkit_native_build/patches/ffmpeg/"
                    "0002-android-binder-preserve-started-threadpool.patch",
                    "6144819ab0aeb9fd2bb512c8b7dce10abee8182c1531c0fd1a48cd1af1745956",
                ),
                (
                    "openh264",
                    "tools/ffmpegkit_native_build/patches/openh264/"
                    "0001-BsFlush-skip-empty-word.patch",
                    "cda83194b33e17b42a46286d1ebed302d4a6aa93c39563935c6005c74b9ccbb8",
                ),
            ],
        )
        self.assertEqual(
            manifest["build_contract"]["build_arguments"][:3],
            [
                "--api-level=24",
                "--rebuild-openh264",
                "--rebuild-ffmpeg",
            ],
        )
        self.assertIn(
            "--enable-harfbuzz",
            manifest["build_contract"]["build_arguments"],
        )
        self.assertIn(
            "--enable-libharfbuzz",
            manifest["build_contract"]["required_configure_flags"],
        )
        self.assertIn(
            "--enable-gpl",
            manifest["build_contract"]["required_configure_flags"],
        )
        self.assertNotIn(
            "--enable-gpl",
            manifest["build_contract"]["forbidden_configure_flags"],
        )
        self.assertIn(
            "--enable-filter=eq",
            manifest["build_contract"]["required_configure_flags"],
        )
        self.assertIn(
            "--enable-filter=boxblur",
            manifest["build_contract"]["required_configure_flags"],
        )
        self.assertNotIn(
            "--enable-filter=eq",
            manifest["build_contract"]["build_arguments"],
        )
        self.assertNotIn(
            "--enable-filter=boxblur",
            manifest["build_contract"]["build_arguments"],
        )
        self.assertEqual(
            {"libavfilter.so": ["drawtext", "eq", "boxblur"]},
            manifest["build_contract"]["required_binary_markers"],
        )
        self.assertEqual(
            manifest["build_contract"]["gpl_license_resource"]["resource_path"],
            "res/raw/license_gplv3.txt",
        )
        self.assertEqual(
            manifest["build_contract"]["gpl_license_resource"]["source_path"],
            "LICENSE",
        )
        self.assertEqual(
            manifest["build_contract"]["gpl_license_resource"]["sha256"],
            "71ebd932ffa82ef1ed27738e7236cfc290925b20a0a4970cbc61b11c939d0569",
        )
        source_lock = load_ffmpegkit_source_lock()
        openh264 = next(
            source
            for source in source_lock["sources"]
            if source["name"] == "openh264"
        )
        self.assertEqual(openh264["tag"], "v2.6.0")
        self.assertEqual(
            openh264["commit"],
            "652bdb7719f30b52b08e506645a7322ff1b2cc6f",
        )
        source_text = (
            overlay
            / "android-8.1-lts"
            / "tools"
            / "source"
            / "SOURCE"
        ).read_text(encoding="utf-8")
        for marker in ffmpegkit_source_identity_markers(
            manifest,
            source_lock,
        ):
            self.assertIn(marker, source_text)
        self.assertEqual(
            (
                manifest["qualified_artifacts"]["source_aar"]["sha256"],
                manifest["qualified_artifacts"]["source_aar"]["size"],
            ),
            (
                "0bd7addae2d17960db940a17a3e2450acb83eecb46be0d3800d051bb2075c1ce",
                39_632_341,
            ),
        )
        self.assertEqual(
            (
                manifest["qualified_artifacts"]["thin_candidate"]["sha256"],
                manifest["qualified_artifacts"]["thin_candidate"]["size"],
            ),
            (
                "7e6b4c20a93dfb3b90bc7f3c5d724cf657b70e2469ea4f2b1110396a8d345394",
                30_486_441,
            ),
        )
        self.assertEqual(
            manifest["status"],
            "ffmpeg_n9_0_1_android_ffprobe_drawtext_capability_output_r6_selected_locally",
        )
        self.assertEqual(
            manifest["qualified_artifacts"]["thin_candidate"]["sha256"],
            load_ffmpeg_output_contract(REPO_ROOT)[1],
        )
        self.assertEqual(
            manifest["selected_product"]["sha256"],
            load_ffmpeg_output_contract(REPO_ROOT)[1],
        )
        self.assertEqual(
            manifest["selected_product"]["native_zip_alignment"],
            "0x4000",
        )
        thin_members = expected_ffmpegkit_member_names(manifest, "thin")
        source_members = expected_ffmpegkit_member_names(manifest, "source")
        self.assertEqual(
            source_members - thin_members,
            {
                "res/",
                "res/raw/",
                "jni/",
                "jni/arm64-v8a/",
                "jni/arm64-v8a/libc++_shared.so",
            },
        )

    def test_ffmpegkit_wsl_paths_reject_relative_and_root_targets(self) -> None:
        self.assertEqual(
            require_absolute_wsl_path(
                "/home/kiyori/build/ffmpegkit",
                "test path",
            ),
            "/home/kiyori/build/ffmpegkit",
        )
        for invalid in ("relative/path", "/", "/home/../tmp"):
            with self.subTest(invalid=invalid):
                with self.assertRaises(ValueError):
                    require_absolute_wsl_path(invalid, "test path")

    def test_ffmpegkit_native_readelf_requires_windows_host_executable(
        self,
    ) -> None:
        with self.assertRaisesRegex(
            ValueError,
            "must be a Windows-host executable path",
        ):
            resolve_native_readelf(
                (
                    "/home/kiyori/android-ndk/toolchains/llvm/prebuilt/"
                    "linux-x86_64/bin/llvm-readelf"
                )
            )
        with tempfile.TemporaryDirectory() as directory:
            readelf = Path(directory) / "llvm-readelf.exe"
            readelf.write_bytes(b"readelf")
            self.assertEqual(
                resolve_native_readelf(readelf),
                readelf.resolve(),
            )

    def test_ffmpegkit_windows_path_conversion_preserves_separators(self) -> None:
        patch = (
            REPO_ROOT
            / "tools"
            / "ffmpegkit_native_build"
            / "patches"
            / "ffmpeg"
            / "0001-libopenh264-map-constrained-baseline-profile.patch"
        )
        with mock.patch.object(
            ffmpegkit_builder,
            "wsl_output",
            return_value="/mnt/d/10_Project/Kiyori/patch.diff",
        ) as wsl_output:
            converted = ffmpegkit_builder.windows_path_to_wsl(
                "Ubuntu-22.04",
                "kiyori",
                patch,
            )

        self.assertEqual(converted, "/mnt/d/10_Project/Kiyori/patch.diff")
        command = wsl_output.call_args.args[2]
        self.assertEqual(command[:3], ["wslpath", "-a", "-u"])
        self.assertEqual(command[3], patch.resolve().as_posix())
        self.assertNotIn("\\", command[3])

    def test_ffmpegkit_build_environment_reapplies_locked_openh264_patch(
        self,
    ) -> None:
        source_patches = validate_source_patches(
            REPO_ROOT,
            load_ffmpegkit_manifest(),
        )
        with mock.patch.object(
            ffmpegkit_builder,
            "windows_path_to_wsl",
            return_value="/mnt/d/10_Project/Kiyori/openh264.patch",
        ) as windows_path_to_wsl:
            environment = ffmpegkit_builder.build_environment_arguments(
                "Ubuntu-22.04",
                "kiyori",
                "/home/kiyori/android-ndk",
                "/home/kiyori/android-sdk",
                12,
                source_patches,
            )

        self.assertEqual(environment[0], "/usr/bin/env")
        self.assertIn("FFMPEG_KIT_BUILD_JOBS=12", environment)
        self.assertIn(
            "FFMPEG_KIT_OPENH264_PATCH="
            "/mnt/d/10_Project/Kiyori/openh264.patch",
            environment,
        )
        openh264_patch = next(
            patch
            for source, patch, _ in source_patches
            if source == "openh264"
        )
        windows_path_to_wsl.assert_called_once_with(
            "Ubuntu-22.04",
            "kiyori",
            openh264_patch,
        )

    def test_ffmpegkit_host_openh264_sanitizer_matrix_is_strict(self) -> None:
        source = (
            REPO_ROOT
            / "tools"
            / "ffmpegkit_native_build"
            / "run_host_openh264_sanitizer_matrix.sh"
        ).read_text(encoding="utf-8")

        self.assertIn("set -euo pipefail", source)
        self.assertIn("output directory already exists", source)
        self.assertIn("for thread_count in default 1 2 4", source)
        self.assertIn('run_case "320x240"', source)
        self.assertIn('run_case "1280x720"', source)
        self.assertIn('-profile:v constrained_baseline', source)
        self.assertIn('-map 0:v:0', source)
        self.assertIn('-map 0:a:0', source)
        self.assertIn("OpenH264 host sanitizer matrix: 20/20 PASS", source)

    def test_ffmpegkit_framework_patch_targets_generated_android_tree(
        self,
    ) -> None:
        command = ffmpegkit_builder.git_apply_command(
            "/home/kiyori/build/framework",
            "/mnt/d/10_Project/Kiyori/framework.patch",
            directory="android-8.1-lts",
            reverse=True,
            check=True,
        )

        self.assertEqual(
            command,
            [
                "git",
                "-C",
                "/home/kiyori/build/framework",
                "apply",
                "--directory=android-8.1-lts",
                "--reverse",
                "--check",
                "/mnt/d/10_Project/Kiyori/framework.patch",
            ],
        )


if __name__ == "__main__":
    unittest.main()
