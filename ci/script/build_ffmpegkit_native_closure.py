#!/usr/bin/env python3
"""Build Kiyori's fixed arm64 FFmpegKit/FFmpeg 9.0.1 closure in WSL."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import shutil
import subprocess
import sys
from pathlib import Path, PurePosixPath
from typing import Any

from audit_ffmpegkit_native_closure import audit_closure
from prepare_mpv_player_dependency import build_ffmpeg_player_aar


REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
TOOL_ROOT = REPOSITORY_ROOT / "tools" / "ffmpegkit_native_build"
MANIFEST_PATH = TOOL_ROOT / "closure_manifest.json"
SOURCE_LOCK_PATH = TOOL_ROOT / "source_lock.json"


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def directory_sha256(root: Path) -> tuple[str, int, int]:
    digest = hashlib.sha256()
    file_count = 0
    total_size = 0
    for path in sorted(
        root.rglob("*"),
        key=lambda item: item.relative_to(root).as_posix(),
    ):
        if path.is_symlink():
            raise RuntimeError(
                f"FFmpegKit overlay contains an unsupported symlink: {path}"
            )
        if not path.is_file():
            continue
        payload = path.read_bytes()
        digest.update(path.relative_to(root).as_posix().encode("utf-8"))
        digest.update(b"\0")
        digest.update(payload)
        digest.update(b"\0")
        file_count += 1
        total_size += len(payload)
    return digest.hexdigest(), file_count, total_size


def load_json(path: Path) -> dict[str, Any]:
    with path.open(encoding="utf-8") as stream:
        return json.load(stream)


def normalize_git_url(value: str) -> str:
    return value.strip().removesuffix("/").removesuffix(".git").lower()


def require_absolute_wsl_path(value: str, label: str) -> str:
    path = PurePosixPath(value)
    if not path.is_absolute() or ".." in path.parts:
        raise ValueError(f"{label} must be an absolute WSL path: {value}")
    if path == PurePosixPath("/"):
        raise ValueError(f"{label} cannot be the WSL filesystem root")
    return path.as_posix()


def wsl_unc_path(distribution: str, posix_path: str) -> Path:
    path = PurePosixPath(require_absolute_wsl_path(posix_path, "WSL path"))
    return Path(f"\\\\wsl.localhost\\{distribution}").joinpath(*path.parts[1:])


def resolve_native_readelf(
    value: str | os.PathLike[str],
) -> Path:
    raw_value = os.fspath(value)
    # Native closure auditing is a Windows-host process. A Linux llvm-readelf
    # cannot be executed by that process, and pathlib would otherwise silently
    # reinterpret /home/... as <current-drive>:\home\....
    if raw_value.startswith("/"):
        raise ValueError(
            "--native-readelf must be a Windows-host executable path, "
            f"not a WSL path: {raw_value}"
        )
    readelf = Path(raw_value).resolve()
    if not readelf.is_file():
        raise FileNotFoundError(f"llvm-readelf is missing: {readelf}")
    if os.name == "nt" and readelf.suffix.lower() != ".exe":
        raise ValueError(
            "--native-readelf must point to a Windows executable: "
            f"{readelf}"
        )
    return readelf


def run_wsl(
    distribution: str,
    linux_user: str,
    command: list[str],
    *,
    cwd: str | None = None,
    capture_output: bool = False,
    check: bool = True,
) -> subprocess.CompletedProcess[str]:
    invocation = [
        "wsl.exe",
        "-d",
        distribution,
        "-u",
        linux_user,
    ]
    if cwd is not None:
        invocation.extend(["--cd", require_absolute_wsl_path(cwd, "WSL cwd")])
    invocation.extend(["--", *command])
    print("$", subprocess.list2cmdline(invocation))
    return subprocess.run(
        invocation,
        check=check,
        text=True,
        errors="replace",
        stdout=subprocess.PIPE if capture_output else None,
        stderr=subprocess.PIPE if capture_output else None,
    )


def wsl_output(
    distribution: str,
    linux_user: str,
    command: list[str],
    *,
    cwd: str | None = None,
) -> str:
    return run_wsl(
        distribution,
        linux_user,
        command,
        cwd=cwd,
        capture_output=True,
    ).stdout.strip()


def validate_overlay(
    repository: Path,
    manifest: dict[str, Any],
) -> Path:
    overlay = repository / manifest["overlay"]["path"]
    if not overlay.is_dir():
        raise FileNotFoundError(f"FFmpegKit overlay is missing: {overlay}")
    digest, file_count, total_size = directory_sha256(overlay)
    expected = manifest["overlay"]
    if (
        digest != expected["tree_sha256"]
        or file_count != expected["file_count"]
        or total_size != expected["size"]
    ):
        raise RuntimeError(
            "FFmpegKit overlay identity mismatch: "
            f"expected sha256={expected['tree_sha256']} "
            f"files={expected['file_count']} size={expected['size']}, "
            f"got sha256={digest} files={file_count} size={total_size}"
        )
    return overlay


def validate_source_patches(
    repository: Path,
    manifest: dict[str, Any],
) -> list[tuple[str, Path, str]]:
    repository = repository.resolve()
    validated: list[tuple[str, Path, str]] = []
    seen_paths: set[Path] = set()
    for entry in manifest.get("source_patches", []):
        source_name = str(entry["source"]).strip()
        patch_path = (repository / entry["path"]).resolve()
        expected_sha256 = str(entry["sha256"]).lower()
        if not source_name:
            raise ValueError("FFmpegKit source patch has an empty source name")
        if not patch_path.is_relative_to(repository):
            raise RuntimeError(
                f"FFmpegKit source patch escapes repository root: {patch_path}"
            )
        if patch_path in seen_paths:
            raise RuntimeError(f"duplicate FFmpegKit source patch: {patch_path}")
        if not patch_path.is_file():
            raise FileNotFoundError(f"FFmpegKit source patch is missing: {patch_path}")
        actual_sha256 = sha256_file(patch_path)
        if actual_sha256 != expected_sha256:
            raise RuntimeError(
                "FFmpegKit source patch identity mismatch: "
                f"path={patch_path} expected={expected_sha256} "
                f"got={actual_sha256}"
            )
        seen_paths.add(patch_path)
        validated.append((source_name, patch_path, expected_sha256))
    if not validated:
        raise RuntimeError("FFmpegKit source patch allowlist is empty")
    return validated


def validate_ndk(
    distribution: str,
    android_ndk: str,
    manifest: dict[str, Any],
) -> None:
    properties_path = (
        wsl_unc_path(distribution, android_ndk) / "source.properties"
    )
    if not properties_path.is_file():
        raise FileNotFoundError(
            f"Android NDK source.properties is missing: {properties_path}"
        )
    properties: dict[str, str] = {}
    for line in properties_path.read_text(encoding="utf-8").splitlines():
        if "=" not in line:
            continue
        key, value = line.split("=", 1)
        properties[key.strip()] = value.strip()
    revision = properties.get("Pkg.Revision", "")
    expected = manifest["build_contract"]["android_ndk_revision"]
    if revision != expected:
        raise RuntimeError(
            f"Android NDK revision mismatch: expected {expected}, "
            f"got {revision or '<missing>'}"
        )


def require_git_checkout(
    distribution: str,
    linux_user: str,
    path: str,
    repository: str,
    commit: str,
    *,
    create: bool,
) -> None:
    git_dir = wsl_unc_path(distribution, path) / ".git"
    if create:
        if git_dir.exists():
            raise RuntimeError(
                f"refusing to clone over an existing Git checkout: {path}"
            )
        run_wsl(
            distribution,
            linux_user,
            ["git", "clone", "--no-checkout", repository, path],
        )
        run_wsl(
            distribution,
            linux_user,
            ["git", "-C", path, "checkout", "--detach", commit],
        )
    elif not git_dir.exists():
        raise FileNotFoundError(f"expected Git checkout is missing: {path}")

    actual_commit = wsl_output(
        distribution,
        linux_user,
        ["git", "-C", path, "rev-parse", "HEAD"],
    )
    if actual_commit != commit:
        raise RuntimeError(
            f"Git checkout mismatch at {path}: "
            f"expected {commit}, got {actual_commit}"
        )
    actual_remote = wsl_output(
        distribution,
        linux_user,
        ["git", "-C", path, "remote", "get-url", "origin"],
    )
    if normalize_git_url(actual_remote) != normalize_git_url(repository):
        raise RuntimeError(
            f"Git remote mismatch at {path}: "
            f"expected {repository}, got {actual_remote}"
        )


def require_exact_tag(
    distribution: str,
    linux_user: str,
    path: str,
    expected_tag: str | None,
) -> None:
    if expected_tag is None:
        return
    actual_tag = wsl_output(
        distribution,
        linux_user,
        ["git", "-C", path, "describe", "--tags", "--exact-match"],
    )
    if actual_tag != expected_tag:
        raise RuntimeError(
            f"Git tag mismatch at {path}: expected {expected_tag}, got {actual_tag}"
        )


def windows_path_to_wsl(
    distribution: str,
    linux_user: str,
    path: Path,
) -> str:
    # wsl.exe consumes backslashes in argv before wslpath sees them. Passing the
    # resolved Windows path with forward slashes preserves every path separator.
    windows_path = path.resolve().as_posix()
    return wsl_output(
        distribution,
        linux_user,
        ["wslpath", "-a", "-u", windows_path],
    )


def unique_source_patch_path(
    source_patches: list[tuple[str, Path, str]],
    source_name: str,
) -> Path | None:
    matches = [
        patch
        for patch_source, patch, _ in source_patches
        if patch_source == source_name
    ]
    if len(matches) > 1:
        raise RuntimeError(
            "FFmpegKit build environment requires at most one patch per "
            f"environment-owned source: source={source_name} count={len(matches)}"
        )
    return matches[0] if matches else None


def build_environment_arguments(
    distribution: str,
    linux_user: str,
    android_ndk: str,
    android_sdk: str,
    jobs: int,
    source_patches: list[tuple[str, Path, str]],
) -> list[str]:
    if jobs < 1:
        raise ValueError(f"FFmpegKit build jobs must be positive: {jobs}")
    environment = [
        "/usr/bin/env",
        f"ANDROID_NDK_ROOT={android_ndk}",
        f"ANDROID_SDK_ROOT={android_sdk}",
        f"ANDROID_HOME={android_sdk}",
        f"FFMPEG_KIT_BUILD_JOBS={jobs}",
    ]
    openh264_patch = unique_source_patch_path(source_patches, "openh264")
    if openh264_patch is not None:
        environment.append(
            "FFMPEG_KIT_OPENH264_PATCH="
            + windows_path_to_wsl(
                distribution,
                linux_user,
                openh264_patch,
            )
        )
    return environment


def git_apply_command(
    source_path: str,
    patch_path: str,
    *,
    directory: str | None = None,
    reverse: bool = False,
    check: bool = False,
) -> list[str]:
    command = ["git", "-C", source_path, "apply"]
    if directory is not None:
        command.append(f"--directory={directory}")
    if reverse:
        command.append("--reverse")
    if check:
        command.append("--check")
    command.append(patch_path)
    return command


def apply_source_patch(
    distribution: str,
    linux_user: str,
    source_path: str,
    patch_path: str,
    *,
    directory: str | None = None,
) -> None:
    check_result = run_wsl(
        distribution,
        linux_user,
        git_apply_command(
            source_path,
            patch_path,
            directory=directory,
            check=True,
        ),
        capture_output=True,
        check=False,
    )
    if check_result.returncode == 0:
        run_wsl(
            distribution,
            linux_user,
            git_apply_command(
                source_path,
                patch_path,
                directory=directory,
            ),
        )
        return

    reverse_check = run_wsl(
        distribution,
        linux_user,
        git_apply_command(
            source_path,
            patch_path,
            directory=directory,
            reverse=True,
            check=True,
        ),
        capture_output=True,
        check=False,
    )
    if reverse_check.returncode == 0:
        return
    raise RuntimeError(
        "FFmpegKit source patch cannot be applied or verified as already applied: "
        f"source={source_path} patch={patch_path}\n"
        f"apply-check={check_result.stderr.strip()}\n"
        f"reverse-check={reverse_check.stderr.strip()}"
    )


def prepare_source_patches(
    repository: Path,
    distribution: str,
    linux_user: str,
    framework_path: str,
    source_lock: dict[str, Any],
    source_patches: list[tuple[str, Path, str]],
) -> None:
    android_lts = f"{framework_path}/android-8.1-lts"
    sources_by_name = {
        source["name"]: source for source in source_lock["sources"]
    }
    prepared_sources: set[str] = set()
    for source_name, patch, _ in source_patches:
        if source_name == "framework":
            source_path = framework_path
            patch_directory = "android-8.1-lts"
        else:
            patch_directory = None
            source = sources_by_name.get(source_name)
            if source is None:
                raise RuntimeError(
                    f"FFmpegKit source patch targets unlocked source {source_name}"
                )
            source_path = f"{android_lts}/src/{source_name}"
            if source_name not in prepared_sources:
                source_unc = wsl_unc_path(distribution, source_path)
                require_git_checkout(
                    distribution,
                    linux_user,
                    source_path,
                    source["repository"],
                    source["commit"],
                    create=not (source_unc / ".git").exists(),
                )
                require_exact_tag(
                    distribution,
                    linux_user,
                    source_path,
                    source.get("tag"),
                )
                prepared_sources.add(source_name)
        apply_source_patch(
            distribution,
            linux_user,
            source_path,
            windows_path_to_wsl(distribution, linux_user, patch),
            directory=patch_directory,
        )
        run_wsl(
            distribution,
            linux_user,
            ["git", "-C", source_path, "diff", "--check"],
        )


def validate_source_patches_applied(
    distribution: str,
    linux_user: str,
    framework_path: str,
    source_patches: list[tuple[str, Path, str]],
) -> None:
    android_lts = f"{framework_path}/android-8.1-lts"
    for source_name, patch, _ in source_patches:
        if source_name == "framework":
            source_path = framework_path
            patch_directory = "android-8.1-lts"
        else:
            source_path = f"{android_lts}/src/{source_name}"
            patch_directory = None
        patch_path = windows_path_to_wsl(distribution, linux_user, patch)
        result = run_wsl(
            distribution,
            linux_user,
            git_apply_command(
                source_path,
                patch_path,
                directory=patch_directory,
                reverse=True,
                check=True,
            ),
            capture_output=True,
            check=False,
        )
        if result.returncode != 0:
            raise RuntimeError(
                "FFmpegKit source patch is not present after build: "
                f"source={source_name} patch={patch}\n{result.stderr.strip()}"
            )


def safe_replace_fftools_tree(
    framework_root: Path,
    ffmpeg_source: Path,
) -> Path:
    android_lts = framework_root / "android-8.1-lts"
    source = ffmpeg_source / "fftools"
    target = (
        android_lts
        / "android"
        / "ffmpeg-kit-android-lib"
        / "src"
        / "main"
        / "cpp"
        / "fftools9"
    )
    framework_resolved = framework_root.resolve()
    target_resolved = target.resolve(strict=False)
    if (
        target_resolved.name != "fftools9"
        or not target_resolved.is_relative_to(framework_resolved)
    ):
        raise RuntimeError(
            f"refusing to replace unexpected fftools target: {target_resolved}"
        )
    if not source.is_dir():
        raise FileNotFoundError(f"fixed FFmpeg fftools source is missing: {source}")
    if target.exists():
        shutil.rmtree(target)
    shutil.copytree(source, target)
    return target


def apply_overlay(
    overlay: Path,
    framework_root: Path,
) -> None:
    framework_resolved = framework_root.resolve()
    for source in sorted(
        (path for path in overlay.rglob("*") if path.is_file()),
        key=lambda path: path.relative_to(overlay).as_posix(),
    ):
        relative = source.relative_to(overlay)
        destination = framework_root / relative
        destination_resolved = destination.resolve(strict=False)
        if not destination_resolved.is_relative_to(framework_resolved):
            raise RuntimeError(
                f"overlay destination escapes framework root: {destination}"
            )
        destination.parent.mkdir(parents=True, exist_ok=True)
        payload = source.read_bytes()
        if destination.exists():
            destination.write_bytes(payload)
        else:
            destination.write_bytes(payload)
        if destination.read_bytes() != payload:
            raise RuntimeError(f"overlay copy verification failed: {destination}")


def prepare_workspace(
    repository: Path,
    distribution: str,
    linux_user: str,
    work_root: str,
    manifest: dict[str, Any],
    source_lock: dict[str, Any],
    overlay: Path,
    source_patches: list[tuple[str, Path, str]],
    reuse_workspace: bool,
) -> tuple[str, Path]:
    work_root = require_absolute_wsl_path(work_root, "WSL work root")
    framework_path = f"{work_root.rstrip('/')}/framework"
    framework_unc = wsl_unc_path(distribution, framework_path)
    framework_exists = (framework_unc / ".git").exists()
    if framework_exists and not reuse_workspace:
        raise RuntimeError(
            f"WSL framework workspace already exists: {framework_path}; "
            "pass --reuse-workspace only when continuing this exact closure"
        )
    run_wsl(
        distribution,
        linux_user,
        ["mkdir", "-p", work_root],
    )
    require_git_checkout(
        distribution,
        linux_user,
        framework_path,
        manifest["framework"]["repository"],
        manifest["framework"]["commit"],
        create=not framework_exists,
    )

    android_lts_path = f"{framework_path}/android-8.1-lts"
    ffmpeg_path = f"{android_lts_path}/src/ffmpeg"
    ffmpeg_unc = wsl_unc_path(distribution, ffmpeg_path)
    ffmpeg_exists = (ffmpeg_unc / ".git").exists()
    require_git_checkout(
        distribution,
        linux_user,
        ffmpeg_path,
        manifest["ffmpeg"]["build_repository"],
        manifest["ffmpeg"]["commit"],
        create=not ffmpeg_exists,
    )
    tag = wsl_output(
        distribution,
        linux_user,
        ["git", "-C", ffmpeg_path, "describe", "--tags", "--exact-match"],
    )
    if tag != manifest["ffmpeg"]["tag"]:
        raise RuntimeError(
            f"FFmpeg tag mismatch: expected {manifest['ffmpeg']['tag']}, "
            f"got {tag}"
        )

    prepare_source_patches(
        repository,
        distribution,
        linux_user,
        framework_path,
        source_lock,
        source_patches,
    )
    target = safe_replace_fftools_tree(framework_unc, ffmpeg_unc)
    apply_overlay(overlay, framework_unc)
    if not (target / "ffmpegkit_bridge.c").is_file():
        raise RuntimeError("FFmpegKit overlay did not provide fftools9 bridge")
    return framework_path, framework_unc


def validate_source_lock(
    distribution: str,
    linux_user: str,
    framework_path: str,
    source_lock: dict[str, Any],
) -> None:
    android_lts = f"{framework_path}/android-8.1-lts"
    for source in source_lock["sources"]:
        path = f"{android_lts}/src/{source['name']}"
        actual_commit = wsl_output(
            distribution,
            linux_user,
            ["git", "-C", path, "rev-parse", "HEAD"],
        )
        if actual_commit != source["commit"]:
            raise RuntimeError(
                f"source lock mismatch for {source['name']}: "
                f"expected {source['commit']}, got {actual_commit}"
            )
        actual_tag = wsl_output(
            distribution,
            linux_user,
            ["git", "-C", path, "describe", "--tags", "--exact-match"],
        )
        if actual_tag != source["tag"]:
            raise RuntimeError(
                f"source tag mismatch for {source['name']}: "
                f"expected {source['tag']}, got {actual_tag}"
            )
    config = source_lock["gnu_config"]
    config_path = f"{android_lts}/.tmp/source/config"
    actual_config_commit = wsl_output(
        distribution,
        linux_user,
        ["git", "-C", config_path, "rev-parse", "HEAD"],
    )
    if actual_config_commit != config["commit"]:
        raise RuntimeError(
            "GNU config source lock mismatch: "
            f"expected {config['commit']}, got {actual_config_commit}"
        )


def build_closure(
    repository: Path,
    distribution: str,
    linux_user: str,
    framework_path: str,
    framework_unc: Path,
    android_ndk: str,
    android_sdk: str,
    native_readelf: Path,
    libcxx_provider_aar: Path,
    jobs: int,
    manifest: dict[str, Any],
    source_lock: dict[str, Any],
    source_patches: list[tuple[str, Path, str]],
    require_qualified_hash: bool,
) -> tuple[Path, Path]:
    android_lts_path = f"{framework_path}/android-8.1-lts"
    environment = build_environment_arguments(
        distribution,
        linux_user,
        android_ndk,
        android_sdk,
        jobs,
        source_patches,
    )
    run_wsl(
        distribution,
        linux_user,
        [
            *environment,
            "./android.sh",
            *manifest["build_contract"]["build_arguments"],
        ],
        cwd=android_lts_path,
    )
    validate_source_lock(
        distribution,
        linux_user,
        framework_path,
        source_lock,
    )
    validate_source_patches_applied(
        distribution,
        linux_user,
        framework_path,
        source_patches,
    )

    built_aar = (
        framework_unc
        / "android-8.1-lts"
        / "android"
        / "ffmpeg-kit-android-lib"
        / "build"
        / "outputs"
        / "aar"
        / "ffmpeg-kit-release.aar"
    )
    if not built_aar.is_file():
        raise FileNotFoundError(
            f"FFmpegKit source build did not produce {built_aar}"
        )

    source_contract = manifest["qualified_artifacts"]["source_aar"]
    source_output = repository / source_contract["path"]
    source_output.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(built_aar, source_output)
    audit_closure(
        source_output,
        native_readelf,
        libcxx_provider_aar,
        "source",
        require_qualified_hash,
    )

    thin_contract = manifest["qualified_artifacts"]["thin_candidate"]
    thin_output = repository / thin_contract["path"]
    build_ffmpeg_player_aar(source_output, thin_output)
    audit_closure(
        thin_output,
        native_readelf,
        libcxx_provider_aar,
        "thin",
        require_qualified_hash,
    )
    return source_output, thin_output


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--repository",
        type=Path,
        default=REPOSITORY_ROOT,
    )
    parser.add_argument("--distribution", default="Ubuntu-22.04")
    parser.add_argument("--linux-user", default="kiyori")
    parser.add_argument(
        "--work-root",
        default="/home/kiyori/build/kiyori-ffmpegkit9-repro",
    )
    parser.add_argument(
        "--android-ndk",
        default=(
            "/home/kiyori/.local/share/kiyori-player-native/"
            "android-ndk-r29"
        ),
    )
    parser.add_argument(
        "--android-sdk",
        default="/home/kiyori/.local/share/android-sdk",
    )
    parser.add_argument(
        "--native-readelf",
        help=(
            "Windows-host NDK llvm-readelf executable used by the "
            "host-side closure audit"
        ),
    )
    parser.add_argument(
        "--libcxx-provider-aar",
        type=Path,
        default=(
            REPOSITORY_ROOT
            / "work"
            / "player-native-outputs"
            / "m9_ffmpeg_major_candidate"
            / "mpv-player-thin-candidate.aar"
        ),
    )
    parser.add_argument(
        "--jobs",
        type=int,
        default=max(1, os.cpu_count() or 1),
    )
    parser.add_argument("--reuse-workspace", action="store_true")
    parser.add_argument("--prepare-only", action="store_true")
    parser.add_argument(
        "--candidate-output",
        action="store_true",
        help=(
            "Audit structural closure contracts without requiring the "
            "pre-existing qualified hashes; final promotion still requires "
            "updating the manifest and rerunning the qualified audits."
        ),
    )
    args = parser.parse_args()

    repository = args.repository.resolve()
    manifest = load_json(MANIFEST_PATH)
    source_lock = load_json(SOURCE_LOCK_PATH)
    overlay = validate_overlay(repository, manifest)
    source_patches = validate_source_patches(repository, manifest)
    android_ndk = require_absolute_wsl_path(
        args.android_ndk,
        "Android NDK",
    )
    android_sdk = require_absolute_wsl_path(
        args.android_sdk,
        "Android SDK",
    )
    native_readelf: Path | None = None
    if not args.prepare_only:
        if args.native_readelf is None:
            parser.error(
                "--native-readelf is required unless --prepare-only is used"
            )
        try:
            native_readelf = resolve_native_readelf(args.native_readelf)
        except (FileNotFoundError, ValueError) as error:
            parser.error(str(error))
    validate_ndk(args.distribution, android_ndk, manifest)
    framework_path, framework_unc = prepare_workspace(
        repository,
        args.distribution,
        args.linux_user,
        args.work_root,
        manifest,
        source_lock,
        overlay,
        source_patches,
        args.reuse_workspace,
    )
    if args.prepare_only:
        print(f"Prepared FFmpegKit 9 workspace: {framework_path}")
        return 0

    if native_readelf is None:
        raise AssertionError("native readelf validation was skipped")
    libcxx_provider_aar = args.libcxx_provider_aar.resolve()
    source_output, thin_output = build_closure(
        repository,
        args.distribution,
        args.linux_user,
        framework_path,
        framework_unc,
        android_ndk,
        android_sdk,
        native_readelf,
        libcxx_provider_aar,
        args.jobs,
        manifest,
        source_lock,
        source_patches,
        not args.candidate_output,
    )
    print(
        "Built FFmpegKit 9 source closure: "
        f"{source_output} sha256={sha256_file(source_output)}"
    )
    print(
        "Built FFmpegKit 9 thin candidate: "
        f"{thin_output} sha256={sha256_file(thin_output)}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
