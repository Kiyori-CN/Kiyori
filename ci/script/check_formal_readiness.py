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
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
