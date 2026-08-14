#!/usr/bin/env python3
"""Check the repository contracts required before continuous development."""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


EXPECTED_TERMINAL_URL = "https://github.com/Kiyori-CN/KiyoriTerminalCore.git"
EXPECTED_NATIVE_SOURCE_PINS = {
    Path("llama/CMakeLists.txt"): (
        "https://github.com/ggml-org/llama.cpp.git",
        "885c5bbe8e04dc78db25beb911a2715312ad7b54",
    ),
    Path("mnn/CMakeLists.txt"): (
        "https://github.com/alibaba/MNN.git",
        "ea44a3ebd5dd6348eea501047b17c43aa3ecccb6",
    ),
}
FORBIDDEN_RUNTIME_URLS = (
    "https://github.com/AAswordman/Operit",
    "https://github.com/AAswordman/OperitTerminalCore",
)
RUNTIME_ARTIFACT_PATTERNS = (
    ".gradle/",
    "app/build/",
    "build/",
    "node_modules/",
    ".venv/",
    "local.properties",
)
LEGACY_TERMINAL_BANNER_MARKER = "| |_| | |_) |  __/ |   | | |_"
EXPECTED_TERMINAL_BANNER_TEXT = "Kiyori Ubuntu environment on Android"


def git(root: Path, *args: str) -> str:
    result = subprocess.run(
        ["git", *args],
        cwd=root,
        check=True,
        capture_output=True,
        text=True,
    )
    return result.stdout


def line_number(text: str, index: int) -> int:
    return text.count("\n", 0, index) + 1


def check_git_identity(root: Path, errors: list[str], require_main: bool) -> None:
    if require_main:
        branch = git(root, "branch", "--show-current").strip()
        if branch != "main":
            errors.append(f"current branch must be main, found {branch or 'detached HEAD'}")

    settings = (root / "settings.gradle.kts").read_text(encoding="utf-8")
    if not re.search(r'rootProject\.name\s*=\s*"Kiyori"', settings):
        errors.append("settings.gradle.kts must define rootProject.name = Kiyori")

    app_build_path = root / "app" / "build.gradle.kts"
    app_build = app_build_path.read_text(encoding="utf-8")
    if not re.search(r'applicationId\s*=\s*"com\.kiyori"', app_build):
        errors.append("app/build.gradle.kts must use applicationId com.kiyori")
    version_code = re.search(r"versionCode\s*=\s*(\d+)", app_build)
    if version_code is None or int(version_code.group(1)) <= 0:
        errors.append("app/build.gradle.kts must define a positive versionCode")
    version_name = re.search(r'versionName\s*=\s*"([^"]+)"', app_build)
    if version_name is None or not re.fullmatch(r"\d+\.\d+\.\d+(?:[-+][0-9A-Za-z.-]+)?", version_name.group(1)):
        errors.append("app/build.gradle.kts must define a semantic versionName")


def check_submodule(root: Path, errors: list[str]) -> None:
    gitmodules = root / ".gitmodules"
    configured_url = git(root, "config", "--file", str(gitmodules), "--get", "submodule.terminal.url").strip()
    if configured_url != EXPECTED_TERMINAL_URL:
        errors.append(f"terminal submodule URL must be {EXPECTED_TERMINAL_URL}, found {configured_url or 'missing'}")

    tree_entry = git(root, "ls-tree", "HEAD", "terminal").strip()
    if not tree_entry.startswith("160000 "):
        errors.append("HEAD must contain a gitlink for terminal")


def check_package_metadata(root: Path, errors: list[str]) -> None:
    package_path = root / "package.json"
    package = package_path.read_text(encoding="utf-8")
    if not re.search(r'"name"\s*:\s*"kiyori-tooling"', package):
        errors.append("package.json must identify the private Kiyori tooling package")
    if not re.search(r'"private"\s*:\s*true', package):
        errors.append("package.json must be private to prevent accidental npm publication")

    lock = (root / "package-lock.json").read_text(encoding="utf-8")
    if '"name": "kiyori-tooling"' not in lock:
        errors.append("package-lock.json root metadata must match package.json")


def check_visible_branding(root: Path, errors: list[str]) -> None:
    visible_tokens = ("OperitTerminal", "Operit Terminal", "Operit终端")
    resource_root = root / "app" / "src" / "main" / "res"
    for path in sorted(resource_root.glob("values*/strings.xml")):
        try:
            document = ET.parse(path)
        except ET.ParseError as error:
            errors.append(f"{path.relative_to(root)} is not valid XML: {error}")
            continue
        for element in document.getroot().iter("string"):
            value = element.text or ""
            for token in visible_tokens:
                if token in value:
                    errors.append(
                        f"{path.relative_to(root)}:{getattr(element, 'sourceline', '?')} contains visible legacy brand {token}"
                    )

    source_root = root / "app" / "src" / "main" / "java"
    for path in sorted(source_root.rglob("*.kt")):
        text = path.read_text(encoding="utf-8")
        for token in visible_tokens:
            index = text.find(token)
            if index >= 0 and '"' in text[max(0, text.rfind("\n", 0, index) + 1):index]:
                errors.append(f"{path.relative_to(root)}:{line_number(text, index)} contains a visible legacy brand {token}")

    terminal_output = (
        root
        / "terminal"
        / "src"
        / "main"
        / "java"
        / "com"
        / "ai"
        / "assistance"
        / "operit"
        / "terminal"
        / "view"
        / "domain"
        / "OutputProcessor.kt"
    )
    if terminal_output.is_file():
        text = terminal_output.read_text(encoding="utf-8")
        legacy_index = text.find(LEGACY_TERMINAL_BANNER_MARKER)
        if legacy_index >= 0:
            errors.append(
                f"{terminal_output.relative_to(root)}:{line_number(text, legacy_index)} "
                "contains the visible legacy Operit ASCII banner"
            )
        if EXPECTED_TERMINAL_BANNER_TEXT not in text:
            errors.append(
                f"{terminal_output.relative_to(root)} must identify the visible Ubuntu environment as Kiyori"
            )


def check_runtime_urls(root: Path, errors: list[str]) -> None:
    runtime_root = root / "app" / "src" / "main"
    for path in sorted(runtime_root.rglob("*")):
        if not path.is_file() or path.suffix.lower() not in {".kt", ".java", ".xml", ".json"}:
            continue
        text = path.read_text(encoding="utf-8", errors="replace")
        normalized_text = text.casefold()
        for forbidden in FORBIDDEN_RUNTIME_URLS:
            index = normalized_text.find(forbidden.casefold())
            if index >= 0:
                errors.append(f"{path.relative_to(root)}:{line_number(text, index)} contains upstream runtime URL {forbidden}")


def check_tracked_artifacts(root: Path, errors: list[str]) -> None:
    tracked = git(root, "ls-files", "-z").split("\0")
    for path in (value for value in tracked if value):
        normalized = path.replace("\\", "/")
        if normalized.endswith(".aab") or (normalized.endswith(".apk") and not normalized.startswith("app/src/main/assets/")):
            errors.append(f"tracked build artifact: {path}")
        if normalized.startswith("work/"):
            errors.append(f"tracked workspace checkpoint: {path}")
        for pattern in RUNTIME_ARTIFACT_PATTERNS:
            if pattern.endswith("/") and normalized.startswith(pattern):
                errors.append(f"tracked runtime or private path: {path}")
            elif not pattern.endswith("/") and normalized == pattern:
                errors.append(f"tracked runtime or private path: {path}")


def check_generated_native_inputs(root: Path, errors: list[str]) -> None:
    forbidden_prebuilt_inputs = (
        root / "app/src/main/assets/operit_shell_exec",
        root / "terminal/src/main/jniLibs/arm64-v8a/libsudo.so",
    )
    for path in forbidden_prebuilt_inputs:
        if path.exists():
            errors.append(
                f"{path.relative_to(root)} must not exist; native runtime inputs are generated from reviewed source"
            )

    required_markers = {
        root / "app/build.gradle.kts": (
            "abstract class BuildShellIdentityLauncherTask",
            '"-nostdlib++"',
            '"-Wl,-z,max-page-size=16384"',
            '"libc++_shared.so"',
            'layout.buildDirectory.dir("generated/shellIdentityLauncherAssets")',
            "assets.addGeneratedSourceDirectory(buildShellIdentityLauncher)",
            'ndkVersion.set(providers.gradleProperty("kiyori.android.ndkVersion"))',
        ),
        root / "tools/shell_identity_launcher/native-lib.cpp": (
            "setgroups(",
            "setgid(2000)",
            "setuid(2000)",
            'const char *target_ctx = "u:r:shell:s0"',
            "execvp(",
        ),
        root / "tools/shell_identity_launcher/CMakeLists.txt": (
            "-nostdlib++",
            "-Wl,-z,max-page-size=16384",
            "-Wl,--strip-all",
        ),
        root / "tools/shell_identity_launcher/build_android.bat": (
            "kiyori.android.ndkVersion",
            "-DANDROID_ABI=arm64-v8a",
            "-DANDROID_PLATFORM=android-26",
        ),
        root / "terminal/src/main/java/com/ai/assistance/operit/terminal/TerminalManager.kt": (
            "installSudoShim",
            "Files.deleteIfExists(sudoFile.toPath())",
            "#!/system/bin/sh",
        ),
    }
    for path, markers in required_markers.items():
        if not path.is_file():
            errors.append(f"required generated-native input is missing: {path.relative_to(root)}")
            continue
        text = path.read_text(encoding="utf-8")
        for marker in markers:
            if marker not in text:
                errors.append(
                    f"{path.relative_to(root)} is missing generated-native contract marker: {marker}"
                )


def check_native_source_pins(root: Path, errors: list[str]) -> None:
    for relative_path, (repository, expected_sha) in EXPECTED_NATIVE_SOURCE_PINS.items():
        path = root / relative_path
        if not path.is_file():
            errors.append(f"required native source definition is missing: {relative_path}")
            continue

        text = path.read_text(encoding="utf-8")
        match = re.search(
            rf'"{re.escape(repository)}"\s*"{re.escape(expected_sha)}"',
            text,
        )
        if match is None:
            errors.append(
                f"{relative_path} must pin {repository} to exact commit {expected_sha}"
            )


def check_ssh_secret_transport(root: Path, errors: list[str]) -> None:
    path = (
        root
        / "terminal"
        / "src"
        / "main"
        / "java"
        / "com"
        / "ai"
        / "assistance"
        / "operit"
        / "terminal"
        / "utils"
        / "SSHFileConnectionManager.kt"
    )
    if not path.is_file():
        errors.append(f"required SSH transport owner is missing: {path.relative_to(root)}")
        return

    text = path.read_text(encoding="utf-8")
    required_markers = (
        "IFS= read -r SSHPASS",
        "channel.outputStream.use",
        "config.localSshPassword.toByteArray(Charsets.UTF_8)",
        "sshpass -e sshfs",
        "StrictHostKeyChecking=accept-new",
    )
    for marker in required_markers:
        if marker not in text:
            errors.append(
                f"{path.relative_to(root)} is missing SSH secret transport marker: {marker}"
            )

    forbidden_markers = (
        'channel.setEnv("SSHPASS"',
        "sshpass -p",
        "StrictHostKeyChecking=no",
        '<<< "${config.localSshPassword}"',
    )
    for marker in forbidden_markers:
        if marker in text:
            errors.append(
                f"{path.relative_to(root)} contains forbidden SSH secret transport marker: {marker}"
            )


def check_ci_android_toolchain(root: Path, errors: list[str]) -> None:
    app_build_path = root / "app/build.gradle.kts"
    if not app_build_path.is_file():
        errors.append("app/build.gradle.kts is missing")
        return
    app_build = app_build_path.read_text(encoding="utf-8")
    compile_sdk_match = re.search(r"\bcompileSdk\s*=\s*(\d+)", app_build)
    if compile_sdk_match is None:
        errors.append("app/build.gradle.kts must define compileSdk")
        return

    expected_platform = f"platforms;android-{compile_sdk_match.group(1)}"
    for workflow_relative_path in (
        ".github/workflows/android-build.yml",
        ".github/workflows/pr-check.yml",
    ):
        workflow_path = root / workflow_relative_path
        if not workflow_path.is_file():
            errors.append(f"required Android workflow is missing: {workflow_relative_path}")
            continue
        workflow = workflow_path.read_text(encoding="utf-8")
        if expected_platform not in workflow:
            errors.append(
                f"{workflow_relative_path} must install {expected_platform} to match app compileSdk"
            )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repository", type=Path, default=Path.cwd())
    parser.add_argument("--require-main", action="store_true")
    args = parser.parse_args()

    root = args.repository.resolve()
    errors: list[str] = []
    check_git_identity(root, errors, args.require_main)
    check_submodule(root, errors)
    check_package_metadata(root, errors)
    check_visible_branding(root, errors)
    check_runtime_urls(root, errors)
    check_tracked_artifacts(root, errors)
    check_generated_native_inputs(root, errors)
    check_native_source_pins(root, errors)
    check_ssh_secret_transport(root, errors)
    check_ci_android_toolchain(root, errors)

    if errors:
        print("Formal development readiness: FAIL", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1

    print("Formal development readiness: PASS")
    print("- Kiyori identity and version metadata")
    print("- KiyoriTerminalCore submodule URL and gitlink")
    print("- user-visible terminal branding")
    print("- runtime upstream URL exclusion")
    print("- tracked secret/runtime artifact hygiene")
    print("- generated shell launcher and terminal shim source contracts")
    print("- exact llama.cpp and MNN native source commits")
    print("- SSH password transport avoids command text and server AcceptEnv dependency")
    print("- CI Android platform matches app compileSdk")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
