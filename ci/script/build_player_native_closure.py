#!/usr/bin/env python3
"""Build one auditable arm64 mpv/libplayer native closure from source."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import shutil
import subprocess
import sys
from pathlib import Path
from typing import Any


REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
MANIFEST_PATH = REPOSITORY_ROOT / "tools" / "player_native_build" / "closure_manifest.json"
FETCH_SCRIPT = REPOSITORY_ROOT / "ci" / "script" / "fetch_player_native_source.py"
AUDIT_SCRIPT = (
    REPOSITORY_ROOT / "ci" / "script" / "audit_player_native_closure.py"
)
PKG_CONFIG_SCRIPT = REPOSITORY_ROOT / "tools" / "player_native_build" / "pkg_config.py"
MESON_RPATH_TOOL = (
    REPOSITORY_ROOT
    / "tools"
    / "player_native_build"
    / "remove_meson_android_rpath.py"
)
UPSTREAM_BINDING = "https://github.com/Riteshp2001/mpvlibAndroid.git"


def run(command: list[str], *, cwd: Path, env: dict[str, str] | None = None) -> None:
    print("$", " ".join(command))
    subprocess.run(command, cwd=cwd, env=env, check=True)


def bash_command(bash_executable: Path, script: str) -> list[str]:
    return [str(bash_executable), "-lc", script]


def configure_bash_environment(
    env: dict[str, str],
    bash_executable: Path,
) -> None:
    # MSYS2 login shells initialize the core Unix tools required by upstream
    # scripts, but normally replace cwd with the user's home. Preserve the
    # caller-owned workspace and inherit the closure's explicit host/NDK PATH.
    env["CHERE_INVOKING"] = "1"
    if (bash_executable.parent / "pacman.exe").is_file():
        env["MSYSTEM"] = "MSYS"
        env["MSYS2_PATH_TYPE"] = "inherit"


def git_output(repository: Path, *arguments: str) -> str:
    return subprocess.check_output(
        ["git", "-C", str(repository), *arguments],
        text=True,
    ).strip()


def directory_sha256(root: Path) -> str:
    digest = hashlib.sha256()
    for path in sorted(root.rglob("*"), key=lambda item: item.relative_to(root).as_posix()):
        if path.is_symlink():
            raise RuntimeError(f"source tree contains an unsupported symlink: {path}")
        if not path.is_file():
            continue
        digest.update(path.relative_to(root).as_posix().encode("utf-8"))
        digest.update(b"\0")
        with path.open("rb") as stream:
            for chunk in iter(lambda: stream.read(1024 * 1024), b""):
                digest.update(chunk)
        digest.update(b"\0")
    return digest.hexdigest()


def load_manifest() -> dict[str, Any]:
    with MANIFEST_PATH.open(encoding="utf-8") as stream:
        manifest = json.load(stream)
    common = manifest["common"]
    contract = common["build_contract"]
    if contract["curl"] != "disabled":
        raise ValueError("player closure must keep curl disabled")
    if common["mbedtls"]["rsa_pss"] != "enabled":
        raise ValueError("player closure must keep RSA-PSS enabled")
    if contract["selected_closure_count"] != 1:
        raise ValueError("the product must select exactly one native closure")
    return manifest


def require_android_ndk_sources(
    android_ndk: Path,
    manifest: dict[str, Any],
) -> tuple[str, Path, str]:
    source_properties = android_ndk / "source.properties"
    if not source_properties.is_file():
        raise FileNotFoundError(f"Android NDK source.properties is missing: {source_properties}")
    properties: dict[str, str] = {}
    for line in source_properties.read_text(encoding="utf-8").splitlines():
        if "=" not in line:
            continue
        key, value = line.split("=", 1)
        properties[key.strip()] = value.strip()
    revision = properties.get("Pkg.Revision", "")
    expected_revision = manifest["common"]["build_contract"]["android_ndk_revision"]
    if revision != expected_revision:
        raise RuntimeError(
            f"Android NDK revision mismatch: expected {expected_revision}, got {revision or '<missing>'}"
        )

    shaderc_contract = manifest["common"]["shaderc"]
    shaderc_source = android_ndk / shaderc_contract["ndk_source_path"]
    if not (shaderc_source / "Android.mk").is_file():
        raise FileNotFoundError(f"NDK shaderc Android.mk is missing: {shaderc_source}")
    actual_tree_sha256 = directory_sha256(shaderc_source)
    expected_tree_sha256 = shaderc_contract["tree_sha256"]
    if actual_tree_sha256 != expected_tree_sha256:
        raise RuntimeError(
            "NDK shaderc source tree mismatch: "
            f"expected {expected_tree_sha256}, got {actual_tree_sha256}"
        )
    return revision, shaderc_source, actual_tree_sha256


def windows_shaderc_stage_path(
    generated: Path,
    manifest: dict[str, Any],
) -> Path:
    shaderc_contract = manifest["common"]["shaderc"]
    version = shaderc_contract["version"].removeprefix("v")
    tree_sha256 = shaderc_contract["tree_sha256"]
    work_root = generated.parent.parent
    return work_root / "_toolchain-sources" / f"shaderc-{version}-{tree_sha256[:12]}"


def patch_windows_shaderc_stage(destination: Path) -> None:
    android_mk = destination / "Android.mk"
    source = android_mk.read_text(encoding="utf-8")
    old_mri_rules = """\t@echo "create libshaderc_combined.a" > $(1)/combine.ar
\t$(foreach lib,$(ALL_LIBS),
\t\t@echo "addlib $(lib)" >> $(1)/combine.ar
\t)
\t@echo "save" >> $(1)/combine.ar
\t@echo "end" >> $(1)/combine.ar
"""
    new_mri_rules = """\t@echo create libshaderc_combined.a > $(1)/combine.ar
\t$(foreach lib,$(ALL_LIBS),
\t\t@echo addlib $(lib) >> $(1)/combine.ar
\t)
\t@echo save >> $(1)/combine.ar
\t@echo end >> $(1)/combine.ar
"""
    if new_mri_rules in source:
        return
    if old_mri_rules not in source:
        raise RuntimeError("NDK shaderc Android.mk MRI rules changed; inspect before building")
    android_mk.write_text(
        source.replace(old_mri_rules, new_mri_rules),
        encoding="utf-8",
        newline="\n",
    )


def stage_windows_shaderc_source(
    generated: Path,
    source: Path,
    manifest: dict[str, Any],
) -> Path | None:
    if os.name != "nt":
        return None
    destination = windows_shaderc_stage_path(generated, manifest)
    shaderc_contract = manifest["common"]["shaderc"]
    source_tree_sha256 = shaderc_contract["tree_sha256"]
    staged_tree_sha256 = shaderc_contract["windows_staged_tree_sha256"]
    if destination.exists():
        actual_tree_sha256 = directory_sha256(destination)
        if actual_tree_sha256 == staged_tree_sha256:
            return destination
        if actual_tree_sha256 != source_tree_sha256:
            raise RuntimeError(
                "staged Windows shaderc source tree mismatch: "
                f"expected source {source_tree_sha256} or staged {staged_tree_sha256}, "
                f"got {actual_tree_sha256}: {destination}"
            )
    else:
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copytree(source, destination)
        actual_tree_sha256 = directory_sha256(destination)
        if actual_tree_sha256 != source_tree_sha256:
            raise RuntimeError(
                "copied Windows shaderc source tree mismatch: "
                f"expected {source_tree_sha256}, got {actual_tree_sha256}: {destination}"
            )

    patch_windows_shaderc_stage(destination)
    actual_tree_sha256 = directory_sha256(destination)
    if actual_tree_sha256 != staged_tree_sha256:
        raise RuntimeError(
            "patched Windows shaderc source tree mismatch: "
            f"expected {staged_tree_sha256}, got {actual_tree_sha256}: {destination}"
        )
    return destination


def clone_exact(url: str, commit: str, destination: Path) -> None:
    if destination.exists():
        actual = git_output(destination, "rev-parse", "HEAD")
        if actual == commit:
            return
        raise RuntimeError(
            f"existing source checkout has the wrong commit: {destination} "
            f"expected {commit}, got {actual}"
        )
    destination.parent.mkdir(parents=True, exist_ok=True)
    run(
        ["git", "clone", "--filter=blob:none", "--no-checkout", url, str(destination)],
        cwd=destination.parent,
    )
    try:
        run(["git", "checkout", "--detach", commit], cwd=destination)
    except subprocess.CalledProcessError:
        run(["git", "fetch", "--depth=1", "origin", commit], cwd=destination)
        run(["git", "checkout", "--detach", commit], cwd=destination)
    actual = git_output(destination, "rev-parse", "HEAD")
    if actual != commit:
        raise RuntimeError(f"source checkout mismatch: expected {commit}, got {actual}")


def write_depinfo(workspace: Path, manifest: dict[str, Any], profile: str) -> None:
    common = manifest["common"]
    ffmpeg = manifest["profiles"][profile]["ffmpeg"]
    depinfo = workspace / "buildscripts" / "include" / "depinfo.sh"
    depinfo.write_text(
        "\n".join(
            [
                "# Generated from Kiyori tools/player_native_build/closure_manifest.json.",
                "v_sdk=14742923_latest",
                "v_ndk=r29",
                "v_ndk_n=29.0.14206865",
                "v_sdk_platform=36",
                "v_sdk_build_tools=36.0.0",
                "v_lua=5.2.4",
                "v_unibreak=7.0",
                "v_harfbuzz=14.2.1",
                "v_fribidi=1.0.16",
                "v_freetype=2.14.3",
                f"v_mbedtls={common['mbedtls']['version']}",
                "v_mujs=1.3.9",
                "dep_mbedtls=()",
                "dep_dav1d=()",
                "dep_ffmpeg=(mbedtls dav1d)",
                "dep_freetype2=()",
                "dep_fribidi=()",
                "dep_harfbuzz=()",
                "dep_unibreak=()",
                "dep_libass=(freetype2 fribidi harfbuzz unibreak)",
                "dep_lua=()",
                "dep_mujs=()",
                "dep_shaderc=()",
                "dep_libplacebo=(shaderc)",
                "dep_mpv=(ffmpeg libass lua libplacebo mujs)",
                "dep_mpv_android=(mpv)",
                f"v_ci_ffmpeg={ffmpeg['version']}",
                "ci_tarball=closure-generated-by-Kiyori",
                "",
            ]
        ),
        encoding="utf-8",
        newline="\n",
    )


def write_wget_wrapper(workspace: Path) -> None:
    wrapper = workspace / "buildscripts" / "wget.cmd"
    python_executable = Path(sys.executable).resolve()
    wrapper.write_text(
        "@echo off\r\n"
        f'"{python_executable}" "{FETCH_SCRIPT}" %*\r\n',
        encoding="ascii",
        newline="",
    )
    pkg_config_wrapper = workspace / "build-tools" / "pkg-config.cmd"
    pkg_config_wrapper.parent.mkdir(parents=True, exist_ok=True)
    pkg_config_wrapper.write_text(
        "@echo off\r\n"
        f'"{python_executable}" "{PKG_CONFIG_SCRIPT}" %*\r\n',
        encoding="ascii",
        newline="",
    )
    pkg_config_shell_wrapper = pkg_config_wrapper.with_name("pkg-config")
    pkg_config_shell_wrapper.write_text(
        "#!/usr/bin/env bash\n"
        f'exec "{python_executable.as_posix()}" "{PKG_CONFIG_SCRIPT.as_posix()}" "$@"\n',
        encoding="utf-8",
        newline="\n",
    )
    pkg_config_shell_wrapper.chmod(0o755)


def patch_unused_source_downloads(workspace: Path) -> None:
    download_script = workspace / "buildscripts" / "include" / "download-deps.sh"
    source = download_script.read_text(encoding="utf-8")
    old_openssl_download = """# openssl
if [ ! -d openssl ]; then
\tmkdir openssl
\t$WGET https://github.com/openssl/openssl/releases/download/openssl-$v_openssl/openssl-$v_openssl.tar.gz -O - | \\
\t\ttar -xz -C openssl --strip-components=1
fi
"""
    new_openssl_contract = """# OpenSSL is outside the selected source closure.
# FFmpeg uses Mbed TLS, curl is disabled, and no build target depends on OpenSSL.
"""
    if new_openssl_contract in source:
        return
    if old_openssl_download not in source:
        raise RuntimeError(
            "upstream OpenSSL download block changed; inspect the fixed closure "
            "dependency graph before building"
        )
    download_script.write_text(
        source.replace(old_openssl_download, new_openssl_contract),
        encoding="utf-8",
        newline="\n",
    )


def require_host_toolchain(host_toolchain: Path) -> Path:
    mingw_root = host_toolchain.resolve() / "mingw64"
    bin_dir = mingw_root / "bin"
    required = (
        bin_dir / "gcc.exe",
        bin_dir / "mingw32-make.exe",
        # FFmpeg 9 builds AArch64 assembly generators with HOSTCC. Checking a
        # representative standard-header set here prevents a partial WinLibs
        # extraction from failing only after the Android objects are compiled.
        mingw_root / "x86_64-w64-mingw32" / "include" / "assert.h",
        mingw_root / "x86_64-w64-mingw32" / "include" / "stdint.h",
        mingw_root / "x86_64-w64-mingw32" / "include" / "stdio.h",
    )
    missing = [path for path in required if not path.is_file()]
    cxx_drivers = tuple(
        path
        for path in (bin_dir / "g++.exe", bin_dir / "c++.exe")
        if path.is_file()
    )
    cc1plus = tuple(
        (
            host_toolchain.resolve()
            / "mingw64"
            / "libexec"
            / "gcc"
            / "x86_64-w64-mingw32"
        ).glob("*/cc1plus.exe")
    )
    if missing:
        raise FileNotFoundError(
            "Windows native source closure requires WinLibs-style host tooling; "
            f"missing: {', '.join(str(path) for path in missing)}"
        )
    if not cxx_drivers:
        raise FileNotFoundError(
            "Windows native source closure requires the WinLibs C++ driver at "
            f"{bin_dir / 'g++.exe'} or {bin_dir / 'c++.exe'}"
        )
    if len(cc1plus) != 1:
        raise FileNotFoundError(
            "Windows native source closure requires exactly one WinLibs "
            f"cc1plus.exe, found {len(cc1plus)}: {cc1plus}"
        )
    return bin_dir


def find_ndk_llvm_tool(android_ndk: Path, tool_name: str) -> Path:
    executable_name = f"{tool_name}.exe" if os.name == "nt" else tool_name
    candidates = sorted(
        (
            android_ndk
            / "toolchains"
            / "llvm"
            / "prebuilt"
        ).glob(f"*/bin/{executable_name}")
    )
    if len(candidates) != 1:
        raise FileNotFoundError(
            f"expected one NDK {tool_name}, found {len(candidates)}: {candidates}"
        )
    return candidates[0]


def write_make_wrapper(workspace: Path, host_bin: Path) -> None:
    make_path = host_bin / "mingw32-make.exe"
    shell_wrapper = workspace / "build-tools" / "make"
    shell_wrapper.write_text(
        "#!/usr/bin/env bash\n"
        f'exec "{make_path.as_posix()}" "$@"\n',
        encoding="utf-8",
        newline="\n",
    )
    shell_wrapper.chmod(0o755)
    (workspace / "build-tools" / "make.cmd").write_text(
        "@echo off\r\n"
        f'"{make_path}" %*\r\n',
        encoding="ascii",
        newline="",
    )


def write_cmake_wrapper(workspace: Path, android_sdk: Path) -> None:
    cmake_root = android_sdk.resolve() / "cmake" / "3.22.1"
    cmake_executable = cmake_root / "bin" / "cmake.exe"
    cmake_module = cmake_root / "share" / "cmake-3.22" / "Modules" / "CMake.cmake"
    if not cmake_executable.is_file() or not cmake_module.is_file():
        raise FileNotFoundError(
            "Android SDK CMake 3.22.1 is incomplete; expected "
            f"{cmake_executable} and {cmake_module}"
        )
    shell_wrapper = workspace / "build-tools" / "cmake"
    shell_wrapper.write_text(
        "#!/usr/bin/env bash\n"
        f'exec "{cmake_executable.as_posix()}" "$@"\n',
        encoding="utf-8",
        newline="\n",
    )
    shell_wrapper.chmod(0o755)
    (workspace / "build-tools" / "cmake.cmd").write_text(
        "@echo off\r\n"
        f'"{cmake_executable}" %*\r\n',
        encoding="ascii",
        newline="",
    )


def ensure_windows_prefix_junctions(generated: Path) -> None:
    prefix_root = generated / "buildscripts" / "prefix" / "arm64"
    install_root = prefix_root / "usr" / "local"
    prefix_root.mkdir(parents=True, exist_ok=True)
    install_root.mkdir(parents=True, exist_ok=True)
    for directory in ("include", "lib", "bin", "share"):
        target = install_root / directory
        view = prefix_root / directory
        target.mkdir(parents=True, exist_ok=True)
        if view.exists():
            if not os.path.samefile(view, target):
                raise RuntimeError(
                    "Windows prefix view points to an unexpected directory: "
                    f"{view} -> {target}"
                )
            continue
        subprocess.run(
            ["cmd.exe", "/d", "/c", "mklink", "/J", str(view), str(target)],
            check=True,
        )
        if not os.path.samefile(view, target):
            raise RuntimeError(f"failed to create Windows prefix junction: {view}")


def ensure_windows_sdk_junctions(
    generated: Path,
    android_ndk: Path,
    android_sdk: Path,
) -> None:
    if os.name != "nt":
        return
    sdk_root = generated / "buildscripts" / "sdk"
    sdk_root.mkdir(parents=True, exist_ok=True)
    targets = {
        sdk_root / "android-ndk-r29": android_ndk.resolve(),
        sdk_root / "android-sdk-linux": android_sdk.resolve(),
    }
    for junction, target in targets.items():
        if not target.is_dir():
            raise FileNotFoundError(
                f"Android toolchain junction target is missing: {target}"
            )
        if junction.exists():
            if not junction.is_dir() or not os.path.samefile(junction, target):
                raise RuntimeError(
                    "Android toolchain junction points to an unexpected target: "
                    f"{junction} -> expected {target}"
                )
            continue
        subprocess.run(
            ["cmd.exe", "/d", "/c", "mklink", "/J", str(junction), str(target)],
            check=True,
        )
        if not junction.is_dir() or not os.path.samefile(junction, target):
            raise RuntimeError(
                f"failed to create Android toolchain junction: {junction} -> {target}"
            )


def patch_windows_prefix_layout(workspace: Path) -> None:
    if os.name != "nt":
        return
    path_script = workspace / "buildscripts" / "include" / "path.sh"
    path_source = path_script.read_text(encoding="utf-8")
    old_pkg_config_paths = """if [ -n "$ndk_triple" ]; then
\texport PKG_CONFIG_SYSROOT_DIR="$prefix_dir"
\texport PKG_CONFIG_LIBDIR="$PKG_CONFIG_SYSROOT_DIR/lib/pkgconfig"
\tunset PKG_CONFIG_PATH
fi
"""
    previous_pkg_config_paths = """if [ -n "$ndk_triple" ]; then
\tpkg_config_sysroot="$prefix_dir"
\tif command -v cygpath >/dev/null 2>&1; then
\t\t# Meson launches pkg-config as a native Windows process. Give that process
\t\t# a native path while retaining the same isolated closure prefix.
\t\tpkg_config_sysroot=$(cygpath -w "$prefix_dir")
\tfi
\texport PKG_CONFIG_SYSROOT_DIR="$pkg_config_sysroot"
\texport PKG_CONFIG_LIBDIR="$PKG_CONFIG_SYSROOT_DIR/lib/pkgconfig"
\tunset PKG_CONFIG_PATH
fi
"""
    new_pkg_config_paths = """if [ -n "$ndk_triple" ]; then
\tpkg_config_sysroot="$prefix_dir"
\tpkg_config_libdir="$prefix_dir/lib/pkgconfig"
\tif command -v cygpath >/dev/null 2>&1; then
\t\t# Meson launches pkg-config as a native Windows process, so its search
\t\t# directory must be native. Keep the sysroot in mixed form so the emitted
\t\t# compiler flags retain forward slashes for Autotools and MinGW make.
\t\tpkg_config_sysroot=$(cygpath -m "$prefix_dir")
\t\tpkg_config_libdir=$(cygpath -w "$prefix_dir/lib/pkgconfig")
\tfi
\texport PKG_CONFIG_SYSROOT_DIR="$pkg_config_sysroot"
\texport PKG_CONFIG_LIBDIR="$pkg_config_libdir"
\tunset PKG_CONFIG_PATH
fi
"""
    if new_pkg_config_paths not in path_source:
        if previous_pkg_config_paths in path_source:
            path_source = path_source.replace(
                previous_pkg_config_paths,
                new_pkg_config_paths,
            )
        elif old_pkg_config_paths in path_source:
            path_source = path_source.replace(
                old_pkg_config_paths,
                new_pkg_config_paths,
            )
        else:
            raise RuntimeError("upstream path.sh pkg-config paths changed; inspect before building")
        path_script.write_text(
            path_source,
            encoding="utf-8",
            newline="\n",
        )

    buildall = workspace / "buildscripts" / "buildall.sh"
    source = buildall.read_text(encoding="utf-8")
    old = """\t\t# enforce flat structure (/usr/local -> /)
\t\tln -s . "$prefix_dir/usr"
\t\tln -s . "$prefix_dir/local"
"""
    old_previous = """\t\t# Windows Git Bash cannot represent usr -> . without resolving a directory loop.
\t\t# Keep /usr/local semantics by linking only that child path back to the prefix root.
\t\tmkdir -p "$prefix_dir/usr"
\t\tln -s "$prefix_dir" "$prefix_dir/usr/local"
"""
    old_current = """\t\t# Windows Git Bash cannot represent usr -> . or usr/local -> .. without a directory loop.
\t\t# Keep /usr/local as a real install root and expose only one-way root views.
\t\tmkdir -p "$prefix_dir/usr/local"
\t\tfor directory in include lib bin share; do
\t\t\tmkdir -p "$prefix_dir/usr/local/$directory"
\t\t\tln -s "$prefix_dir/usr/local/$directory" "$prefix_dir/$directory"
\t\tdone
"""
    new = """\t\t# Windows Git Bash cannot represent usr -> . or usr/local -> .. without a directory loop.
\t\t# Keep /usr/local as a real install root and expose only one-way root views.
\t\tmkdir -p "$prefix_dir/usr/local"
\t\tfor directory in include lib bin share; do
\t\t\tmkdir -p "$prefix_dir/usr/local/$directory"
\t\t\tln -s "$prefix_dir/usr/local/$directory" "$prefix_dir/$directory"
\t\tdone
\tif [ ! -d "$prefix_dir/usr/local" ]; then
\t\techo "Windows prefix install root is missing: $prefix_dir/usr/local" >&2
\t\treturn 1
\tfi
\tfor directory in include lib bin share; do
\t\tif [ ! -L "$prefix_dir/$directory" ]; then
\t\t\techo "Windows prefix view is not a symlink: $prefix_dir/$directory" >&2
\t\t\treturn 1
\t\tfi
\tdone
"""
    if new not in source:
        if old_previous in source:
            source = source.replace(old_previous, new)
        elif old in source:
            source = source.replace(old, new)
        elif old_current in source:
            source = source.replace(old_current, new)
        else:
            raise RuntimeError("upstream buildall.sh prefix layout changed; inspect before building")
        buildall.write_text(source, encoding="utf-8", newline="\n")

    app_build = workspace / "app" / "build.gradle"
    app_build_source = app_build.read_text(encoding="utf-8")
    old_android_source_sets = """    buildFeatures {
        buildConfig = true
    }

    // https://youtrack.jetbrains.com/issue/KT-55947
"""
    new_android_source_sets = """    buildFeatures {
        buildConfig = true
    }

    // Git materializes the tracked jniLibs -> libs symlink as a regular file
    // on Windows. Point AGP at the real NDK output directory; otherwise its
    // JNI merger walks beyond the source root and fails in AssetItem.computePath.
    sourceSets {
        main {
            jniLibs.srcDirs = ['src/main/libs']
        }
    }

    // https://youtrack.jetbrains.com/issue/KT-55947
"""
    if new_android_source_sets not in app_build_source:
        jni_libs = workspace / "app" / "src" / "main" / "jniLibs"
        if jni_libs.is_symlink():
            jni_libs_target = os.readlink(jni_libs).replace("\\", "/")
        elif jni_libs.is_file():
            jni_libs_target = jni_libs.read_text(encoding="utf-8").strip()
        else:
            raise RuntimeError("upstream app/src/main/jniLibs link changed; inspect before building")
        if jni_libs_target != "libs/":
            raise RuntimeError(
                "upstream app/src/main/jniLibs target changed: "
                f"expected libs/, got {jni_libs_target}"
            )
        if old_android_source_sets not in app_build_source:
            raise RuntimeError("upstream app/build.gradle Android block changed; inspect before building")
        app_build.write_text(
            app_build_source.replace(
                old_android_source_sets,
                new_android_source_sets,
            ),
            encoding="utf-8",
            newline="\n",
        )

    mbedtls = workspace / "buildscripts" / "scripts" / "mbedtls.sh"
    mbedtls_source = mbedtls.read_text(encoding="utf-8")
    old_install = '-DCMAKE_INSTALL_PREFIX="$prefix_dir" \\\n'
    new_install = '-DCMAKE_INSTALL_PREFIX="$prefix_dir/usr/local" \\\n'
    if new_install not in mbedtls_source:
        if old_install not in mbedtls_source:
            raise RuntimeError("upstream mbedtls.sh install prefix changed; inspect before building")
        mbedtls.write_text(
            mbedtls_source.replace(old_install, new_install),
            encoding="utf-8",
            newline="\n",
        )

    for target in ("unibreak", "libass"):
        script = workspace / "buildscripts" / "scripts" / f"{target}.sh"
        script_source = script.read_text(encoding="utf-8")
        old_configure = """../configure \\
\t--host=$ndk_triple --with-pic \\
"""
        new_configure = """../configure \\
\t--prefix=/usr/local \\
\t--host=$ndk_triple --with-pic \\
"""
        if new_configure not in script_source:
            if old_configure not in script_source:
                raise RuntimeError(
                    f"upstream {target}.sh configure prefix changed; inspect before building"
                )
            script.write_text(
                script_source.replace(old_configure, new_configure),
                encoding="utf-8",
                newline="\n",
            )

    lua = workspace / "buildscripts" / "scripts" / "lua.sh"
    lua_source = lua.read_text(encoding="utf-8")
    old_lua_install = (
        'make INSTALL=${INSTALL:-install} INSTALL_TOP="$prefix_dir" '
        "TO_BIN=/dev/null install\n"
    )
    new_lua_install = (
        "# Native MinGW make otherwise lets MSYS2 translate TO_BIN=/dev/null to "
        "TO_BIN=nul.\n"
        "# Exclude only this variable assignment so the POSIX install recipe "
        "still receives /dev/null.\n"
        'MSYS2_ARG_CONV_EXCL="TO_BIN=" make INSTALL=${INSTALL:-install} '
        'INSTALL_TOP="$prefix_dir" TO_BIN=/dev/null install\n'
    )
    if new_lua_install not in lua_source:
        if old_lua_install not in lua_source:
            raise RuntimeError("upstream lua.sh install arguments changed; inspect before building")
        lua.write_text(
            lua_source.replace(old_lua_install, new_lua_install),
            encoding="utf-8",
            newline="\n",
        )

    shaderc = workspace / "buildscripts" / "scripts" / "shaderc.sh"
    shaderc_source = shaderc.read_text(encoding="utf-8")
    old_shaderc_ndk_build = """# build using the NDK's scripts, but keep object files in our build dir
cd "$(dirname "$(which ndk-build)")/sources/third_party/shaderc"
ndk-build -j$cores \\
"""
    previous_shaderc_ndk_build = """# Windows NDK packages expose the build entrypoint as ndk-build.cmd.
# Resolve that real entrypoint so the bundled, version-matched shaderc source
# remains owned by the fixed NDK instead of an unrelated host installation.
ndk_build=$(command -v ndk-build.cmd)
shaderc_source="$(dirname "$ndk_build")/sources/third_party/shaderc"
if [ ! -f "$shaderc_source/Android.mk" ]; then
	echo "NDK shaderc source is missing: $shaderc_source/Android.mk" >&2
	exit 1
fi
cd "$shaderc_source"
"$ndk_build" -j$cores \\
"""
    new_shaderc_ndk_build = """# Native Windows make cannot stat the deepest bundled shaderc prerequisite when
# the fixed NDK path reaches MAX_PATH. The builder verifies and stages the exact
# NDK-owned v2022.3 tree at a shorter physical path before this script runs.
if [ -z "$KIYORI_SHADERC_SOURCE" ]; then
	echo "KIYORI_SHADERC_SOURCE is required for the Windows shaderc build" >&2
	exit 1
fi
ndk_build=$(command -v ndk-build.cmd)
shaderc_source=$(cygpath -u "$KIYORI_SHADERC_SOURCE")
if [ ! -f "$shaderc_source/Android.mk" ]; then
	echo "staged NDK shaderc source is missing: $shaderc_source/Android.mk" >&2
	exit 1
fi
cd "$shaderc_source"
"$ndk_build" -j$cores \\
"""
    if new_shaderc_ndk_build not in shaderc_source:
        if previous_shaderc_ndk_build in shaderc_source:
            shaderc_source = shaderc_source.replace(
                previous_shaderc_ndk_build,
                new_shaderc_ndk_build,
            )
        elif old_shaderc_ndk_build in shaderc_source:
            shaderc_source = shaderc_source.replace(
                old_shaderc_ndk_build,
                new_shaderc_ndk_build,
            )
        else:
            raise RuntimeError("upstream shaderc.sh NDK entrypoint changed; inspect before building")
        shaderc.write_text(
            shaderc_source,
            encoding="utf-8",
            newline="\n",
        )

    shaderc_source = shaderc.read_text(encoding="utf-8")
    old_shaderc_pkg_config = """mkdir -p "$prefix_dir"/lib/pkgconfig
cat >"$prefix_dir"/lib/pkgconfig/shaderc_combined.pc <<"END"
Name: shaderc_combined
Description:
Version: 2022.3-unknown
Libs: -L/usr/local/lib -lshaderc_combined
Cflags: -I/usr/local/include
END

if [ -z "$(pkg-config --cflags shaderc_combined)" ]; then
\techo >&2 "shaderc pkg-config sanity check failed"
\texit 1
fi
"""
    new_shaderc_pkg_config = """mkdir -p "$prefix_dir"/lib/pkgconfig
rm -f "$prefix_dir"/lib/pkgconfig/shaderc_combined.pc
cat >"$prefix_dir"/lib/pkgconfig/shaderc.pc <<"END"
Name: shaderc
Description:
Version: 2022.3-unknown
Libs: -L/usr/local/lib -lshaderc_combined
Cflags: -I/usr/local/include
END

if [ -z "$(pkg-config --cflags shaderc)" ]; then
\techo >&2 "shaderc pkg-config sanity check failed"
\texit 1
fi
"""
    if new_shaderc_pkg_config not in shaderc_source:
        if old_shaderc_pkg_config not in shaderc_source:
            raise RuntimeError("upstream shaderc.sh pkg-config contract changed; inspect before building")
        shaderc.write_text(
            shaderc_source.replace(
                old_shaderc_pkg_config,
                new_shaderc_pkg_config,
            ),
            encoding="utf-8",
            newline="\n",
        )

    libplacebo = workspace / "buildscripts" / "scripts" / "libplacebo.sh"
    libplacebo_source = libplacebo.read_text(encoding="utf-8")
    old_libplacebo_setup = """meson setup $build --cross-file "$prefix_dir"/crossfile.txt \\
\t-Dvk-proc-addr=enabled -Ddemos=false
"""
    new_libplacebo_setup = """meson setup $build --cross-file "$prefix_dir"/crossfile.txt \\
\t-Dvk-proc-addr=enabled -Dshaderc=enabled -Ddemos=false
"""
    if new_libplacebo_setup not in libplacebo_source:
        if old_libplacebo_setup not in libplacebo_source:
            raise RuntimeError("upstream libplacebo.sh setup options changed; inspect before building")
        libplacebo.write_text(
            libplacebo_source.replace(
                old_libplacebo_setup,
                new_libplacebo_setup,
            ),
            encoding="utf-8",
            newline="\n",
        )

    mpv = workspace / "buildscripts" / "scripts" / "mpv.sh"
    mpv_source = mpv.read_text(encoding="utf-8")
    old_mpv_build = """meson setup $build --cross-file "$prefix_dir"/crossfile.txt \\
\t--default-library shared \\
\t-Diconv=disabled \\
\t-Dlua=enabled \\
\t-Djavascript=enabled \\
\t-Dvulkan=enabled \\
\t-Dlibmpv=true \\
\t-Dcplayer=false \\
\t-Dmanpage-build=disabled

ninja -C $build -j$cores
"""
    new_mpv_build = """meson setup $build --cross-file "$prefix_dir"/crossfile.txt \\
\t--default-library shared \\
\t-Diconv=disabled \\
\t-Dlua=enabled \\
\t-Djavascript=enabled \\
\t-Dvulkan=enabled \\
\t-Dlibmpv=true \\
\t-Dcplayer=false \\
\t-Dmanpage-build=disabled

if command -v cygpath >/dev/null 2>&1 && [[ "$OSTYPE" == msys* || "$OSTYPE" == cygwin* ]]; then
\t# The Android target never executes on the Windows build host. Meson adds a
\t# build-only RPATH for absolute shared-library inputs, but its install-time
\t# ELF cleanup splits on ':' and cannot distinguish Windows drive letters.
\t# Remove only the exact current NDK + closure token before libmpv is linked.
\tndk_rpath=$(cygpath -m "$ANDROID_NDK_HOME")/toolchains/llvm/prebuilt/windows-x86_64/sysroot/usr/lib/$ndk_triple/24
\tclosure_rpath=$(cygpath -m "$prefix_dir/usr/local/lib")
\t"$KIYORI_BUILD_PYTHON" "$KIYORI_MESON_RPATH_TOOL" \\
\t\t--build-ninja "$build/build.ninja" \\
\t\t--expected-rpath-dir "$ndk_rpath" \\
\t\t--expected-rpath-dir "$closure_rpath"
fi

ninja -C $build -j$cores
"""
    if new_mpv_build not in mpv_source:
        if old_mpv_build not in mpv_source:
            raise RuntimeError("upstream mpv.sh Meson build block changed; inspect before building")
        mpv.write_text(
            mpv_source.replace(old_mpv_build, new_mpv_build),
            encoding="utf-8",
            newline="\n",
        )

    mpv_android = workspace / "buildscripts" / "scripts" / "mpv-android.sh"
    mpv_android_source = mpv_android.read_text(encoding="utf-8")
    old_mpv_android_ndk_build = """PREFIX32=$prefix32 PREFIX64=$prefix64 PREFIX_X64=$prefix_x64 PREFIX_X86=$prefix_x86 \\
ndk-build -C app/src/main -j$cores
"""
    new_mpv_android_ndk_build = """ndk_build=$(command -v ndk-build.cmd)
PREFIX32=$prefix32 PREFIX64=$prefix64 PREFIX_X64=$prefix_x64 PREFIX_X86=$prefix_x86 \\
"$ndk_build" -C app/src/main -j$cores
"""
    if new_mpv_android_ndk_build not in mpv_android_source:
        if old_mpv_android_ndk_build not in mpv_android_source:
            raise RuntimeError("upstream mpv-android.sh NDK entrypoint changed; inspect before building")
        mpv_android.write_text(
            mpv_android_source.replace(
                old_mpv_android_ndk_build,
                new_mpv_android_ndk_build,
            ),
            encoding="utf-8",
            newline="\n",
        )

    compiler_source = buildall.read_text(encoding="utf-8")
    compiler_source = compiler_source.replace(
        "export CC=$cc_triple-clang\n",
        "export CC=$cc_triple-clang.cmd\n",
    )
    compiler_source = compiler_source.replace(
        "export CXX=$cc_triple-clang++\n",
        "export CXX=$cc_triple-clang++.cmd\n",
    )
    buildall.write_text(compiler_source, encoding="utf-8", newline="\n")

    ffmpeg = workspace / "buildscripts" / "scripts" / "ffmpeg.sh"
    ffmpeg_source = ffmpeg.read_text(encoding="utf-8")
    upstream_host_compiler = (
        "\t--cross-prefix=$ndk_triple- --cc=$CC --pkg-config=pkg-config "
        "--nm=llvm-nm\n"
    )
    experimental_host_compiler = (
        "\t--cross-prefix=$ndk_triple- --cc=$CC --host-cc=$CC --host-ld=$CC "
        "--pkg-config=pkg-config --nm=llvm-nm\n"
    )
    new_host_compiler = (
        "\t--cross-prefix=$ndk_triple- --cc=$CC --host-cc=gcc --host-ld=gcc "
        "--pkg-config=pkg-config --nm=llvm-nm\n"
    )
    if new_host_compiler not in ffmpeg_source:
        if upstream_host_compiler in ffmpeg_source:
            ffmpeg_source = ffmpeg_source.replace(
                upstream_host_compiler,
                new_host_compiler,
            )
        elif experimental_host_compiler in ffmpeg_source:
            ffmpeg_source = ffmpeg_source.replace(
                experimental_host_compiler,
                new_host_compiler,
            )
        else:
            raise RuntimeError("upstream ffmpeg.sh host compiler arguments changed; inspect before building")
    old_configure_tail = '../configure "${args[@]}"\n\nmake -j$cores\n'
    previous_configure_tail = """../configure "${args[@]}"

if command -v cygpath >/dev/null 2>&1 && [[ "$OSTYPE" == msys* ]]; then
	source_posix=$(cd .. && pwd)
	source_windows=$(cygpath -w "$source_posix")
	while IFS= read -r -d '' makefile; do
		sed -i "s|$source_posix|$source_windows|g" "$makefile"
	done < <(find . -type f \\( -name Makefile -o -name '*.mak' \\) -print0)
fi

make -j$cores
"""
    previous_configure_tail_cygwin_w = """../configure "${args[@]}"

if command -v cygpath >/dev/null 2>&1 && [[ "$OSTYPE" == msys* || "$OSTYPE" == cygwin* ]]; then
	source_posix=$(cd .. && pwd)
	source_windows=$(cygpath -w "$source_posix")
	while IFS= read -r -d '' makefile; do
		sed -i "s|$source_posix|$source_windows|g" "$makefile"
	done < <(find . -type f \\( -name Makefile -o -name '*.mak' \\) -print0)
fi

make -j$cores
"""
    previous_configure_tail_cygwin_m = """../configure "${args[@]}"

if command -v cygpath >/dev/null 2>&1 && [[ "$OSTYPE" == msys* || "$OSTYPE" == cygwin* ]]; then
	source_posix=$(cd .. && pwd)
	source_windows=$(cygpath -m "$source_posix")
	while IFS= read -r -d '' makefile; do
		sed -i "s|$source_posix|$source_windows|g" "$makefile"
	done < <(find . -type f \\( -name Makefile -o -name '*.mak' \\) -print0)
fi

make -j$cores
"""
    previous_configure_tail_windows_asm = """../configure "${args[@]}"

if command -v cygpath >/dev/null 2>&1 && [[ "$OSTYPE" == msys* || "$OSTYPE" == cygwin* ]]; then
	source_posix=$(cd .. && pwd)
	source_windows=$(cygpath -m "$source_posix")
	while IFS= read -r -d '' makefile; do
		sed -i "s|$source_posix|$source_windows|g" "$makefile"
	done < <(find . -type f \\( -name Makefile -o -name '*.mak' \\) -print0)

	# MinGW make normalizes the prerequisite in "%.o: %.S" to ".s" on Windows.
	# Keep every real uppercase-assembly source addressable by an explicit rule;
	# otherwise an existing .S file is reported as a missing .s file and the
	# build fails before the Android assembler can run.
	asm_rules=ffbuild/kiyori-windows-asm.mak
	{
		printf '%s\\n' '# Generated by Kiyori: preserve real .S prerequisites for Windows host make.'
		while IFS= read -r -d '' source_file; do
			source_rel=${source_file#"$source_posix"/}
			object_rel=${source_rel%.S}.o
			printf '%s: %s/%s\\n\\t$(COMPILE_S)\\n' "$object_rel" "$source_windows" "$source_rel"
		done < <(find "$source_posix" -type f -name '*.S' -print0)
	} > "$asm_rules"
	if ! grep -Fqx -- "-include $asm_rules" Makefile; then
		printf '%s\\n' "-include $asm_rules" >> Makefile
	fi
fi

make -j$cores
"""
    new_configure_tail = """../configure "${args[@]}"

if command -v cygpath >/dev/null 2>&1 && [[ "$OSTYPE" == msys* || "$OSTYPE" == cygwin* ]]; then
	source_posix=$(cd .. && pwd)
	source_windows=$(cygpath -m "$source_posix")
	while IFS= read -r -d '' makefile; do
		sed -i "s|$source_posix|$source_windows|g" "$makefile"
	done < <(find . -type f \\( -name Makefile -o -name '*.mak' \\) -print0)

	# MinGW make normalizes the prerequisite in "%.o: %.S" to ".s" on Windows.
	# Keep every real uppercase-assembly source addressable by an explicit rule;
	# otherwise an existing .S file is reported as a missing .s file and the
	# build fails before the Android assembler can run.
	asm_rules=ffbuild/kiyori-windows-asm.mak
	{
		printf '%s\\n' '# Generated by Kiyori: preserve real .S prerequisites for Windows host make.'
		while IFS= read -r -d '' source_file; do
			source_rel=${source_file#"$source_posix"/}
			object_rel=${source_rel%.S}.o
			printf '%s: %s/%s\\n\\t$(COMPILE_S)\\n' "$object_rel" "$source_windows" "$source_rel"
		done < <(find "$source_posix" -type f -name '*.S' -print0)
	} > "$asm_rules"
	if ! grep -Fqx -- "-include $asm_rules" Makefile; then
		printf '%s\\n' "-include $asm_rules" >> Makefile
	fi
	if grep -Fqx 'RESPONSE_FILES=no' ffbuild/config.mak; then
		# The Android Clang linker accepts response files, while the configure
		# probe only tests the cross-ar tool and can disable this link-safe path.
		sed -i 's/^RESPONSE_FILES=no$/RESPONSE_FILES=yes/' ffbuild/config.mak
	fi
fi

make -j$cores
"""
    if new_configure_tail not in ffmpeg_source:
        if old_configure_tail not in ffmpeg_source:
            if previous_configure_tail not in ffmpeg_source:
                if previous_configure_tail_windows_asm in ffmpeg_source:
                    ffmpeg_source = ffmpeg_source.replace(
                        previous_configure_tail_windows_asm,
                        new_configure_tail,
                    )
                elif previous_configure_tail_cygwin_m in ffmpeg_source:
                    ffmpeg_source = ffmpeg_source.replace(
                        previous_configure_tail_cygwin_m,
                        new_configure_tail,
                    )
                elif previous_configure_tail_cygwin_w in ffmpeg_source:
                    ffmpeg_source = ffmpeg_source.replace(
                        previous_configure_tail_cygwin_w,
                        new_configure_tail,
                    )
                else:
                    raise RuntimeError("upstream ffmpeg.sh configure tail changed; inspect before building")
            else:
                ffmpeg_source = ffmpeg_source.replace(
                    previous_configure_tail,
                    new_configure_tail,
                )
        else:
            ffmpeg_source = ffmpeg_source.replace(old_configure_tail, new_configure_tail)
    ffmpeg.write_text(ffmpeg_source, encoding="utf-8", newline="\n")

def patch_windows_ffmpeg_source(workspace: Path) -> None:
    if os.name != "nt":
        return
    library_mak = (
        workspace
        / "buildscripts"
        / "deps"
        / "ffmpeg"
        / "ffbuild"
        / "library.mak"
    )
    if not library_mak.is_file():
        raise FileNotFoundError(
            "fixed FFmpeg checkout must exist before applying Windows host "
            f"rules: {library_mak}"
        )
    library_source = library_mak.read_text(encoding="utf-8")
    old_static_response_rule = "\t$(Q)echo $^ > $@.objs\n"
    new_static_response_rule = "\t$(file >$@.objs,$(filter %.o,$^))\n"
    old_shared_response_rule = "\t$(Q)echo $$(filter %.o,$$^) > $$@.objs\n"
    previous_shared_response_rule = "\t$(file >$$@.objs,$$(filter %.o,$$^))\n"
    new_shared_response_rule = "\t$$(file >$$@.objs,$$(filter %.o,$$^))\n"
    old_header_install_rule = '\t$$(INSTALL) -m 644 $$^ "$(INCINSTDIR)"\n'
    # libavutil's absolute public-header list exceeds the Windows process
    # command-line limit. Keep FFmpeg's exact prerequisite set, but materialize
    # it with GNU Make before invoking one bounded install command per header.
    previous_header_install_rule = (
        "\t$$(file >$$@.headers)\n"
        "\t$$(foreach F,$$^,$$(file >>$$@.headers,$$(F)))\n"
        "\t$(Q)tr -d '\\r' < \"$$@.headers\" | while IFS= read -r header; do "
        '$$(INSTALL) -m 644 "$$$$header" "$(INCINSTDIR)" || exit $$$$?; done\n'
        '\t$(Q)$$(RM) "$$@.headers"\n'
    )
    new_header_install_rule = (
        "\t$$(file >$$@.headers)\n"
        "\t$$(foreach F,$$^,$$(file >>$$@.headers,$$(F)))\n"
        "\t$(Q)tr -d '\\r' < \"$$@.headers\" | while IFS= read -r header; do "
        '"$$(lastword $$(INSTALL))" -m 644 "$$$$header" "$(INCINSTDIR)" '
        "|| exit $$$$?; done\n"
        '\t$(Q)$$(RM) "$$@.headers"\n'
    )
    if new_static_response_rule not in library_source:
        if old_static_response_rule not in library_source:
            raise RuntimeError("upstream FFmpeg static response-file rule changed; inspect before building")
        library_source = library_source.replace(
            old_static_response_rule,
            new_static_response_rule,
        )
    if new_shared_response_rule not in library_source:
        if old_shared_response_rule in library_source:
            library_source = library_source.replace(
                old_shared_response_rule,
                new_shared_response_rule,
            )
        elif previous_shared_response_rule in library_source:
            library_source = library_source.replace(
                previous_shared_response_rule,
                new_shared_response_rule,
            )
        else:
            raise RuntimeError("upstream FFmpeg shared response-file rule changed; inspect before building")
    if new_header_install_rule not in library_source:
        if old_header_install_rule in library_source:
            library_source = library_source.replace(
                old_header_install_rule,
                new_header_install_rule,
            )
        elif previous_header_install_rule in library_source:
            library_source = library_source.replace(
                previous_header_install_rule,
                new_header_install_rule,
            )
        else:
            raise RuntimeError("upstream FFmpeg header install rule changed; inspect before building")
    library_mak.write_text(library_source, encoding="utf-8", newline="\n")


def source_lock(
    workspace: Path,
    ndk_revision: str,
    shaderc_tree_sha256: str,
    shaderc_windows_tree_sha256: str | None,
) -> dict[str, str]:
    deps = workspace / "buildscripts" / "deps"
    locked: dict[str, str] = {
        "android-ndk": ndk_revision,
        "shaderc-ndk-tree": shaderc_tree_sha256,
    }
    if shaderc_windows_tree_sha256 is not None:
        locked["shaderc-windows-tree"] = shaderc_windows_tree_sha256
    for name in ("dav1d", "ffmpeg", "freetype2", "fribidi", "harfbuzz", "libass", "libplacebo", "mpv"):
        path = deps / name
        if (path / ".git").exists():
            locked[name] = git_output(path, "rev-parse", "HEAD")
    mbedtls = deps / "mbedtls"
    if (mbedtls / ".git").exists():
        locked["mbedtls"] = git_output(mbedtls, "rev-parse", "HEAD")
        framework = mbedtls / "framework"
        if (framework / ".git").exists():
            locked["mbedtls-framework"] = git_output(framework, "rev-parse", "HEAD")
    return locked


def prepare_workspace(
    repository: Path,
    work_root: Path,
    manifest: dict[str, Any],
    profile: str,
    bash_executable: Path,
    android_ndk: Path,
) -> Path:
    workspace = work_root / profile
    workspace.mkdir(parents=True, exist_ok=True)
    binding = manifest["binding"]
    clone_exact(UPSTREAM_BINDING, binding["commit"], workspace / "mpvlibAndroid")
    generated = workspace / "mpvlibAndroid"
    write_depinfo(generated, manifest, profile)
    write_wget_wrapper(generated)
    patch_unused_source_downloads(generated)
    patch_windows_prefix_layout(generated)
    deps = generated / "buildscripts" / "deps"
    common = manifest["common"]
    profile_info = manifest["profiles"][profile]
    ndk_revision, shaderc_source, shaderc_tree_sha256 = require_android_ndk_sources(
        android_ndk,
        manifest,
    )
    shaderc_stage = stage_windows_shaderc_source(generated, shaderc_source, manifest)
    clone_exact(
        common["mbedtls"]["repository"],
        common["mbedtls"]["commit"],
        deps / "mbedtls",
    )
    run(["git", "-C", str(deps / "mbedtls"), "submodule", "update", "--init", "--recursive"], cwd=generated)
    framework_commit = git_output(deps / "mbedtls" / "framework", "rev-parse", "HEAD")
    expected_framework_commit = common["mbedtls"]["framework_submodule_commit"]
    if framework_commit != expected_framework_commit:
        raise RuntimeError(
            "Mbed TLS framework submodule mismatch: "
            f"expected {expected_framework_commit}, got {framework_commit}"
        )
    clone_exact(
        profile_info["ffmpeg"]["repository"],
        profile_info["ffmpeg"]["commit"],
        deps / "ffmpeg",
    )
    patch_windows_ffmpeg_source(generated)
    clone_exact(common["mpv"]["repository"], common["mpv"]["commit"], deps / "mpv")
    env = os.environ.copy()
    env["IN_CI"] = "1"
    env["WGET"] = "../wget.cmd"
    configure_bash_environment(env, bash_executable)
    run(
        bash_command(bash_executable, "./include/download-deps.sh"),
        cwd=generated / "buildscripts",
        env=env,
    )
    lock_path = workspace / "source-lock.json"
    lock_path.write_text(
        json.dumps(
            {
                "profile": profile,
                "manifest": str(MANIFEST_PATH.relative_to(repository)),
                "sources": source_lock(
                    generated,
                    ndk_revision,
                    shaderc_tree_sha256,
                    (
                        manifest["common"]["shaderc"]["windows_staged_tree_sha256"]
                        if shaderc_stage is not None
                        else None
                    ),
                ),
            },
            indent=2,
        )
        + "\n",
        encoding="utf-8",
        newline="\n",
    )
    return generated


def build_workspace(
    generated: Path,
    repository: Path,
    manifest: dict[str, Any],
    profile: str,
    android_ndk: Path,
    android_sdk: Path,
    jobs: int,
    bash_executable: Path,
    build_tools: Path | None,
    host_toolchain: Path | None,
) -> Path:
    if os.name == "nt":
        if host_toolchain is None:
            raise RuntimeError(
                "Windows source closure build requires --host-toolchain <WinLibs-root>"
            )
        host_bin = require_host_toolchain(host_toolchain)
        write_make_wrapper(generated, host_bin)
        write_cmake_wrapper(generated, android_sdk)
        ensure_windows_prefix_junctions(generated)
        ensure_windows_sdk_junctions(generated, android_ndk, android_sdk)
    else:
        host_bin = None

    sdk_root = generated / "buildscripts" / "sdk"
    sdk_root.mkdir(parents=True, exist_ok=True)
    ndk_link = sdk_root / "android-ndk-r29"
    sdk_link = sdk_root / "android-sdk-linux"
    if not ndk_link.exists():
        raise RuntimeError(
            f"missing {ndk_link}; stage the canonical NDK r29 at that exact path "
            "or create a directory link before building"
        )
    if not os.path.samefile(ndk_link, android_ndk):
        raise RuntimeError(
            f"{ndk_link} does not resolve to the selected NDK: {android_ndk}"
        )
    if not sdk_link.exists():
        raise RuntimeError(
            f"missing {sdk_link}; stage the Android SDK at that exact path "
            "or create a directory link before building"
        )
    if not os.path.samefile(sdk_link, android_sdk):
        raise RuntimeError(
            f"{sdk_link} does not resolve to the selected Android SDK: {android_sdk}"
        )
    env = os.environ.copy()
    env["IN_CI"] = "1"
    env["WGET"] = "../wget.cmd"
    env["cores"] = str(jobs)
    env["ANDROID_HOME"] = str(android_sdk)
    env["ANDROID_NDK_HOME"] = str(android_ndk)
    if os.name == "nt":
        shaderc_stage = windows_shaderc_stage_path(generated, manifest)
        expected_shaderc_sha256 = manifest["common"]["shaderc"]["windows_staged_tree_sha256"]
        actual_shaderc_sha256 = directory_sha256(shaderc_stage)
        if actual_shaderc_sha256 != expected_shaderc_sha256:
            raise RuntimeError(
                "staged Windows shaderc source changed after preparation: "
                f"expected {expected_shaderc_sha256}, got {actual_shaderc_sha256}"
            )
        env["KIYORI_SHADERC_SOURCE"] = str(shaderc_stage)
        env["KIYORI_BUILD_PYTHON"] = str(Path(sys.executable).resolve())
        env["KIYORI_MESON_RPATH_TOOL"] = str(MESON_RPATH_TOOL)
    env["PATH"] = os.pathsep.join(
        [
            str(bash_executable.parent),
            str(generated / "build-tools"),
            str(host_bin) if host_bin is not None else "",
            str(android_ndk / "toolchains" / "llvm" / "prebuilt" / "windows-x86_64" / "bin"),
            str(android_ndk / "prebuilt" / "windows-x86_64" / "bin"),
            str(Path(os.environ.get("ANDROID_HOME", android_sdk)) / "platform-tools"),
            str(build_tools) if build_tools is not None else "",
            str(android_sdk / "cmake" / "3.22.1" / "bin"),
            env["PATH"],
        ]
    )
    configure_bash_environment(env, bash_executable)
    buildscripts = generated / "buildscripts"
    for target in ("mbedtls", "dav1d", "ffmpeg", "freetype2", "fribidi", "harfbuzz", "unibreak", "libass", "lua", "mujs", "shaderc", "libplacebo", "mpv"):
        target_env = env.copy()
        if os.name == "nt" and target == "libass":
            # A fresh MSYS2/libtool process fan-out can stall before any NDK
            # compiler child starts. Serializing this small target avoids the
            # host-process race without changing configure flags or payload.
            target_env["cores"] = "1"
        command = bash_command(
            bash_executable,
            f"./buildall.sh --arch arm64 -n {target}",
        )
        run(command, cwd=buildscripts, env=target_env)
    run(
        bash_command(bash_executable, "./buildall.sh --arch arm64 -n mpv-android"),
        cwd=buildscripts,
        env=env,
    )
    source_aar = generated / "app" / "build" / "outputs" / "aar" / "app-release.aar"
    if not source_aar.is_file():
        raise FileNotFoundError(f"native source build did not produce {source_aar}")
    readelf = find_ndk_llvm_tool(android_ndk, "llvm-readelf")
    run(
        [
            sys.executable,
            str(AUDIT_SCRIPT),
            "--aar",
            str(source_aar),
            "--readelf",
            str(readelf),
            "--profile",
            profile,
            "--kind",
            "source",
        ],
        cwd=repository,
    )
    output_dir = repository / "work" / "player-native-outputs" / profile
    output_dir.mkdir(parents=True, exist_ok=True)
    output = output_dir / "mpv-player-source.aar"
    shutil.copy2(source_aar, output)
    digest = hashlib.sha256(output.read_bytes()).hexdigest()
    (output_dir / "sha256.txt").write_text(f"{digest}  {output.name}\n", encoding="utf-8", newline="\n")
    return output


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repository", type=Path, default=REPOSITORY_ROOT)
    parser.add_argument("--profile", choices=("m8_security_refresh", "m9_ffmpeg_major_candidate"), required=True)
    parser.add_argument("--work-root", type=Path, default=REPOSITORY_ROOT / "work" / "player-native-build")
    parser.add_argument("--android-ndk", type=Path, required=True)
    parser.add_argument("--android-sdk", type=Path, required=True)
    parser.add_argument("--bash", dest="bash_executable", type=Path)
    parser.add_argument("--build-tools", type=Path)
    parser.add_argument(
        "--host-toolchain",
        type=Path,
        help=(
            "Windows WinLibs root containing gcc.exe, g++.exe or c++.exe, "
            "cc1plus.exe, and mingw32-make.exe"
        ),
    )
    parser.add_argument("--jobs", type=int, default=4)
    parser.add_argument("--prepare-only", action="store_true")
    args = parser.parse_args()

    repository = args.repository.resolve()
    manifest = load_manifest()
    bash_executable = args.bash_executable
    if bash_executable is None:
        bash_from_path = shutil.which("bash")
        if bash_from_path is None:
            raise RuntimeError("Git Bash is not on PATH; pass --bash <absolute-path-to-bash.exe>")
        bash_executable = Path(bash_from_path)
    generated = prepare_workspace(
        repository,
        args.work_root.resolve(),
        manifest,
        args.profile,
        bash_executable.resolve(),
        args.android_ndk.resolve(),
    )
    if args.prepare_only:
        print(f"Prepared source workspace: {generated}")
        return 0
    output = build_workspace(
        generated,
        repository,
        manifest,
        args.profile,
        args.android_ndk.resolve(),
        args.android_sdk.resolve(),
        args.jobs,
        bash_executable.resolve(),
        args.build_tools.resolve() if args.build_tools is not None else None,
        args.host_toolchain.resolve() if args.host_toolchain is not None else None,
    )
    print(f"Built source closure: {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
