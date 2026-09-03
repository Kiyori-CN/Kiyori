#!/usr/bin/env python3
"""Check the generated Ubuntu installation function and exercise its safe paths.

The runtime script is embedded in a Kotlin raw string.  This check extracts that one function,
performs the same fixed manifest substitutions as ``TerminalManager``, runs a shell parser, and
executes a small first-install/upgrade/failure harness in an isolated WSL temporary directory.
It deliberately does not touch an Android device or any repository runtime directory.
"""

from __future__ import annotations

import argparse
import json
import re
import shutil
import subprocess
import sys
import textwrap
from pathlib import Path


REPOSITORY = Path(__file__).resolve().parents[2]
SOURCE = REPOSITORY / "terminal" / "src" / "main" / "java" / "com" / "ai" / "assistance" / "operit" / "terminal" / "TerminalManager.kt"
MANIFEST = REPOSITORY / "terminal" / "src" / "main" / "assets" / "ubuntu-rootfs-manifest.json"


def read_asset_sha256(repository: Path) -> str:
    manifest_path = repository / MANIFEST.relative_to(REPOSITORY)
    document = json.loads(manifest_path.read_text(encoding="utf-8"))
    asset = document["asset"]
    if asset.get("hardlinkMembers") != 0:
        raise SystemExit("Ubuntu install contract requires a hardlink-free rootfs manifest")
    return asset["sha256"]


def extract_install_function(source: str, asset_sha256: str) -> str:
    match = re.search(
        r"val installUbuntu = \"\"\"\r?\n(.*?)\r?\n\s*\"\"\"\.trimIndent\(\)",
        source,
        re.DOTALL,
    )
    if match is None:
        raise SystemExit("could not locate installUbuntu raw string")

    shell = match.group(1).replace("\r\n", "\n").replace("\r", "\n")
    shell = re.sub(r"\$\{'\$'\}", "$", shell)
    replacements = {
        "release": "26.04.1",
        "codename": "resolute",
        "architecture": "arm64",
        "assetSha256": asset_sha256,
        "installedMarkerFilename": ".kiyori_installed_ok",
        "manifestFilename": ".kiyori_rootfs_manifest",
        "legacyInstalledMarkerFilename": ".operit_installed_ok",
    }
    for name, value in replacements.items():
        shell = shell.replace("${manifest." + name + "}", value)
    return textwrap.dedent(shell).strip() + "\n"


def run_shell(shell_command: list[str], script: str, *, timeout: float = 90.0) -> subprocess.CompletedProcess[bytes]:
    return subprocess.run(
        shell_command,
        input=script.replace("\r\n", "\n").replace("\r", "\n").encode("utf-8"),
        capture_output=True,
        timeout=timeout,
        check=False,
    )


def output_text(result: subprocess.CompletedProcess[bytes]) -> str:
    return (result.stdout + result.stderr).decode("utf-8", errors="replace")


def choose_shell() -> list[str] | None:
    bash = shutil.which("bash")
    if bash:
        return [bash]
    wsl = shutil.which("wsl.exe")
    if wsl:
        return [wsl, "-d", "Ubuntu-22.04", "--", "bash"]
    return None


def harness(install_function: str) -> str:
    # The wrapper intentionally uses matching host commands as deterministic test doubles for the
    # Android BusyBox applets used by extraction and staging diagnostics.  The real arm64 BusyBox
    # binary is separately exercised against the checked-in archive by the rootfs verifier.
    return textwrap.dedent(
        f"""
        set -eu
        root=$(mktemp -d /tmp/kiyori-install-contract.XXXXXX)
        trap 'rm -rf "$root"' EXIT
        mkdir -p "$root/home" "$root/tmp" "$root/source/usr/lib" "$root/source/usr/bin" "$root/source/etc" "$root/source/root/.code_runner/py" "$root/source/root/.code_runner/data"
        printf '%s\\n' 'NAME="Ubuntu"' 'VERSION_ID="26.04"' 'VERSION_CODENAME=resolute' > "$root/source/usr/lib/os-release"
        printf '%s\\n' 'Ubuntu 26.04.1 LTS' > "$root/source/etc/issue.net"
        ln -s ../usr/lib/os-release "$root/source/etc/os-release"
        ln -s usr/bin "$root/source/bin"
        cp /bin/sh "$root/source/usr/bin/bash"
        cp /usr/bin/env "$root/source/usr/bin/gnuenv"
        ln -s gnuenv "$root/source/usr/bin/env"
        chmod +x "$root/source/usr/bin/bash" "$root/source/usr/bin/gnuenv"
        printf old-project > "$root/source/root/project.txt"
        printf old-python-venv > "$root/source/root/.code_runner/py/old.txt"
        printf retained-runner-data > "$root/source/root/.code_runner/data/state.txt"
        printf '%s\\n' '#!/bin/sh' 'applet=$1' 'shift' 'exec "$applet" "$@"' > "$root/busybox"
        chmod +x "$root/busybox"
        export PATH="$root:$PATH"
        export BIN="$root"
        export TMPDIR="$root/tmp"
        export HOME="$root/home"
        export UBUNTU_PATH="$root/installed-rootfs/ubuntu"
        export UBUNTU="rootfs.tar.xz"
        export L_NOT_INSTALLED='not installed'
        export L_INSTALLING='installing'
        export L_INSTALLED='installed'
        progress_echo() {{ printf '%s\\n' "$*" > "$TMPDIR/progress_des"; }}
        write_default_dns() {{ printf '%s\\n' nameserver 1.1.1.1 > "$1"; }}
        {install_function}

        # An already-current rootfs carrying the historical marker is migrated in place.  The
        # active directory must end with one canonical marker and no historical marker.
        mkdir -p "$UBUNTU_PATH"
        cp -a "$root/source"/. "$UBUNTU_PATH"/
        printf '%s\\n' ok schema=kiyori.rootfs.manifest.v1 distribution=ubuntu release=26.04.1 codename=resolute architecture=arm64 asset-sha256={read_asset_sha256(REPOSITORY)} > "$UBUNTU_PATH/.operit_installed_ok"
        printf '%s\\n' ok schema=kiyori.rootfs.manifest.v1 distribution=ubuntu release=26.04.1 codename=resolute architecture=arm64 asset-sha256={read_asset_sha256(REPOSITORY)} > "$UBUNTU_PATH/.kiyori_rootfs_manifest"
        install_ubuntu
        test -f "$UBUNTU_PATH/.kiyori_installed_ok"
        test ! -e "$UBUNTU_PATH/.operit_installed_ok"
        test -f "$UBUNTU_PATH/root/project.txt"

        # Prepare an old/incomplete installation for a staged upgrade.  The old tree must stay
        # recoverable in a backup, while only the explicit root-data allowlist crosses into the new tree.
        rm -rf "$root/source/root/.code_runner" "$root/source/root/project.txt"
        rm -f "$UBUNTU_PATH/.kiyori_installed_ok"
        printf '%s\\n' legacy > "$UBUNTU_PATH/.operit_installed_ok"
        printf '%s\\n' legacy > "$UBUNTU_PATH/.kiyori_rootfs_manifest"
        tar -cJf "$HOME/$UBUNTU" -C "$root/source" .
        install_ubuntu
        test -f "$UBUNTU_PATH/root/project.txt"
        test -f "$UBUNTU_PATH/root/.code_runner/data/state.txt"
        test ! -e "$UBUNTU_PATH/root/.code_runner/py/old.txt"
        test -f "$UBUNTU_PATH/.kiyori_installed_ok"
        test ! -e "$UBUNTU_PATH/.operit_installed_ok"
        test -f "$UBUNTU_PATH/.kiyori_rootfs_manifest"
        test "$(stat -c '%a' "$UBUNTU_PATH/bin/bash")" = 755
        test "$(stat -c '%a' "$UBUNTU_PATH/usr/lib/os-release")" = 644
        test ! -e "$HOME/$UBUNTU"
        test ! -e "$UBUNTU_PATH.install.lock"
        test ! -e "$UBUNTU_PATH.install.tmp"
        backup_count=$(find "$(dirname "$UBUNTU_PATH")" -maxdepth 1 -type d -name 'ubuntu.backup.*' | wc -l)
        test "$backup_count" -eq 1
        backup_path=$(find "$(dirname "$UBUNTU_PATH")" -maxdepth 1 -type d -name 'ubuntu.backup.*' | head -n 1)
        test -f "$backup_path/root/.code_runner/py/old.txt"

        # The Android host can report false for test -x on an executable rootfs path.  Validate the
        # explicit archive-mode contract, including a symlink target and a negative owner-bit case.
        has_owner_execute_bit "$UBUNTU_PATH/bin/bash"
        has_owner_execute_bit "$UBUNTU_PATH/usr/bin/env"
        chmod 0655 "$UBUNTU_PATH/usr/bin/bash"
        if has_owner_execute_bit "$UBUNTU_PATH/bin/bash"; then exit 22; fi
        chmod 0755 "$UBUNTU_PATH/usr/bin/bash"

        # A current installation is idempotent and does not require the archive again.
        install_ubuntu

        # A damaged archive is rejected before the old rootfs is moved; the failure is observable.
        rm -f "$UBUNTU_PATH/.kiyori_rootfs_manifest"
        printf not-an-archive > "$HOME/$UBUNTU"
        if install_ubuntu; then exit 20; fi
        test -d "$UBUNTU_PATH"
        grep -F 'Ubuntu rootfs extraction failed' "$TMPDIR/progress_des"
        test ! -e "$UBUNTU_PATH.install.lock"
        test ! -e "$UBUNTU_PATH.install.tmp"

        # A tar-valid but unhealthy staging tree must restore the old rootfs after activation.
        printf '%s\\n' legacy-health-check > "$UBUNTU_PATH/.kiyori_rootfs_manifest"
        printf '%s\\n' legacy-health-check > "$UBUNTU_PATH/.operit_installed_ok"
        printf retained-old-root > "$UBUNTU_PATH/legacy-sentinel"
        rm -rf "$root/source/usr/lib" "$root/source/etc"
        mkdir -p "$root/source/usr/lib" "$root/source/etc"
        tar -cJf "$HOME/$UBUNTU" -C "$root/source" .
        if install_ubuntu; then exit 21; fi
        test -f "$UBUNTU_PATH/.kiyori_rootfs_manifest"
        grep -F legacy-health-check "$UBUNTU_PATH/.kiyori_rootfs_manifest"
        test -f "$UBUNTU_PATH/.operit_installed_ok"
        test -f "$UBUNTU_PATH/legacy-sentinel"
        grep -F 'Ubuntu rootfs staging health check failed' "$TMPDIR/progress_des"
        test ! -e "$UBUNTU_PATH.install.lock"
        test ! -e "$UBUNTU_PATH.install.tmp"

        echo UBUNTU_INSTALL_CONTRACT_PASS
        """
    )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repository", type=Path, default=REPOSITORY)
    parser.add_argument("--skip-execution", action="store_true")
    args = parser.parse_args()

    repository = args.repository.resolve()
    source_path = repository / SOURCE.relative_to(REPOSITORY)
    install_function = extract_install_function(
        source_path.read_text(encoding="utf-8"),
        read_asset_sha256(repository),
    )
    shell = choose_shell()
    if shell is None:
        raise SystemExit("no bash or WSL bash available for Ubuntu install contract")

    required_markers = (
        'if [ -n "$lock_pid" ] && ! kill -0 "$lock_pid" 2>/dev/null; then',
        'current_lock_pid=$(cat "$LOCK_PID_FILE" 2>/dev/null)',
        'progress_echo "Ubuntu rootfs extraction failed (exit=$extraction_status)"',
        '( umask 022; busybox tar xf "$HOME/$UBUNTU" -C "$TMP_DIR"/ )',
        'has_owner_execute_bit(){',
        '"$BIN/busybox" stat -c \'%a\' "$1"',
        'elif ! has_owner_execute_bit "$TMP_DIR/bin/bash"; then',
        'elif ! has_owner_execute_bit "$TMP_DIR/usr/bin/env"; then',
        'progress_echo "Ubuntu rootfs staging health check failed"',
        'printf \'Ubuntu rootfs staging failure(s): %s\\n\' "$staging_failure"',
        'report_staging_path \'etc/os-release\' "$TMP_DIR/etc/os-release"',
        'report_staging_path \'usr/bin/gnuenv\' "$TMP_DIR/usr/bin/gnuenv"',
        'progress_echo "Ubuntu rootfs health check failed after activation"',
        'if [ -f "$LOCK_PID_FILE" ] && [ "$(cat "$LOCK_PID_FILE" 2>/dev/null)" = "$$" ]; then',
        'migrate_legacy_marker(){',
        'write_rootfs_identity(){',
        'OK_FILE="$UBUNTU_PATH/.kiyori_installed_ok"',
        'LEGACY_OK_FILE="$UBUNTU_PATH/.operit_installed_ok"',
    )
    for marker in required_markers:
        if marker not in install_function:
            raise SystemExit(f"generated installUbuntu function is missing contract marker: {marker}")

    forbidden_markers = (
        '[ ! -x "$TMP_DIR/bin/bash" ]',
        '[ ! -x "$TMP_DIR/usr/bin/env" ]',
        '[ -x "$UBUNTU_PATH/bin/bash" ]',
        '[ -x "$UBUNTU_PATH/usr/bin/env" ]',
    )
    for marker in forbidden_markers:
        if marker in install_function:
            raise SystemExit(f"generated installUbuntu function uses Android-incompatible executable check: {marker}")

    syntax = run_shell(shell + ["-n"], install_function)
    if syntax.returncode != 0:
        sys.stderr.write(output_text(syntax))
        raise SystemExit("generated installUbuntu function failed shell syntax validation")
    if not args.skip_execution:
        result = run_shell(shell, harness(install_function))
        if result.returncode != 0:
            sys.stderr.write(output_text(result))
            raise SystemExit("Ubuntu install contract harness failed")
        execution_output = output_text(result)
        if "Ubuntu rootfs staging failure(s): missing etc/os-release" not in execution_output:
            raise SystemExit("Ubuntu install contract did not expose the staging failure reason")
        if "Ubuntu staging usr/bin/gnuenv:" not in execution_output:
            raise SystemExit("Ubuntu install contract did not expose the key staging path diagnostics")

    print("Ubuntu install contract: PASS")
    print("- generated installUbuntu shell syntax: valid")
    print("- legacy marker migration, staged upgrade, idempotent reuse, corrupt archive and health-failure restore: passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
