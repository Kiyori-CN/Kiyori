#!/usr/bin/env python3
"""Validate Kiyori package ownership, stable contracts, and M-01 purity."""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
import tomllib
import xml.etree.ElementTree as ET
from pathlib import Path


ANDROID_NAME = "{http://schemas.android.com/apk/res/android}name"
VALID_SYNC_ZONES = {"A", "B", "C", "D"}
MANAGED_SOURCE_SUFFIXES = {".java", ".kt"}
PROJECT_IMPORT_ROOTS = (
    "com.ai.assistance.operit",
    "com.kiyori",
)
TEXT_SUFFIXES = {
    ".aidl",
    ".cpp",
    ".h",
    ".java",
    ".js",
    ".json",
    ".kt",
    ".kts",
    ".pro",
    ".properties",
    ".toml",
    ".ts",
    ".xml",
}
M01_OLD_PATH = "app/src/main/java/com/ai/assistance/operit/core/application/OperitApplication.kt"
M01_NEW_PATH = "app/src/main/java/com/ai/assistance/operit/core/application/KiyoriApplication.kt"
M01_CONSUMERS = {
    "app/src/main/java/com/ai/assistance/operit/api/chat/AIForegroundService.kt",
    "app/src/main/java/com/ai/assistance/operit/core/config/SystemPromptConfig.kt",
    "app/src/main/java/com/ai/assistance/operit/data/api/MarketStatsApiService.kt",
    "app/src/main/java/com/ai/assistance/operit/data/converter/GenericJsonConverter.kt",
    "app/src/main/java/com/ai/assistance/operit/data/preferences/WakeWordPreferences.kt",
    "app/src/main/java/com/ai/assistance/operit/plugins/toolbox/ToolboxPlugin.kt",
    "app/src/main/java/com/ai/assistance/operit/plugins/toolpkg/ToolPkgHookBridgeSupport.kt",
    "app/src/main/java/com/ai/assistance/operit/plugins/toolpkg/ToolPkgToolLifecycleBridge.kt",
    "app/src/main/java/com/ai/assistance/operit/services/FloatingChatService.kt",
    "app/src/main/java/com/ai/assistance/operit/ui/common/markdown/MarkdownCodeTypeface.kt",
    "app/src/main/java/com/ai/assistance/operit/ui/features/token/model/UrlConfig.kt",
    "app/src/main/java/com/ai/assistance/operit/ui/main/MainActivity.kt",
    "app/src/main/java/com/ai/assistance/operit/util/AppLogger.kt",
}
M01_EXPECTED_CANDIDATE_PATHS = M01_CONSUMERS | {
    M01_NEW_PATH,
    "app/src/main/AndroidManifest.xml",
    "app/lint-baseline.xml",
}
PROHIBITED_TRACKED_PARTS = {
    ".gradle",
    ".gradle-local",
    ".venv",
    "node_modules",
    "work",
}


def git(root: Path, *args: str, check: bool = True) -> str:
    result = subprocess.run(
        ["git", *args],
        cwd=root,
        check=check,
        capture_output=True,
        text=True,
    )
    return result.stdout


def path_matches(path: str, pattern: str) -> bool:
    normalized = path.replace("\\", "/")
    normalized_pattern = pattern.replace("\\", "/")
    if normalized_pattern.endswith("/**"):
        prefix = normalized_pattern[:-3]
        return normalized == prefix or normalized.startswith(f"{prefix}/")
    return normalized == normalized_pattern


def import_matches_root(imported: str, root: str) -> bool:
    return imported == root or imported.startswith(f"{root}.")


def source_imports(path: Path) -> list[tuple[int, str]]:
    imports: list[tuple[int, str]] = []
    for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        match = re.match(r"^\s*import\s+([A-Za-z_][A-Za-z0-9_.]*)", line)
        if match:
            imports.append((line_number, match.group(1)))
    return imports


def source_package(path: Path) -> str | None:
    for line in path.read_text(encoding="utf-8").splitlines():
        match = re.match(r"^\s*package\s+([A-Za-z_][A-Za-z0-9_.]*)", line)
        if match:
            return match.group(1)
    return None


def dependency_rule(identifier: str, imported: str) -> str:
    if identifier.startswith("operit-"):
        return "ARCH001" if import_matches_root(imported, "com.kiyori.app") else "ARCH002"
    if identifier == "kiyori-capability":
        return "ARCH003"
    if identifier in {"kiyori-app", "kiyori-feature", "kiyori-integration"}:
        return "ARCH004"
    return "ARCH005"


def read_snapshot(path: Path) -> list[str]:
    return [
        line.strip()
        for line in path.read_text(encoding="utf-8").splitlines()
        if line.strip() and not line.lstrip().startswith("#")
    ]


def read_count_snapshot(path: Path) -> list[tuple[int, str]]:
    entries: list[tuple[int, str]] = []
    for line in read_snapshot(path):
        fields = line.split("\t", maxsplit=1)
        if len(fields) != 2:
            raise ValueError(f"invalid counted snapshot line: {line}")
        try:
            expected_count = int(fields[0])
        except ValueError as error:
            raise ValueError(f"invalid snapshot count: {line}") from error
        if expected_count < 1 or not fields[1]:
            raise ValueError(f"invalid counted snapshot line: {line}")
        entries.append((expected_count, fields[1]))
    return entries


def repository_text(root: Path) -> str:
    chunks: list[str] = []
    relative_paths = sorted(
        Path(value)
        for value in git(
            root,
            "ls-files",
            "--cached",
            "--others",
            "--exclude-standard",
            "-z",
            "--",
            "app",
            "examples",
            "tools",
        ).split("\0")
        if value
    )
    for relative_path in relative_paths:
        path = root / relative_path
        excluded_parts = PROHIBITED_TRACKED_PARTS | {".cxx", "build"}
        if (
            path.is_file()
            and path.suffix.lower() in TEXT_SUFFIXES
            and not set(relative_path.parts) & excluded_parts
        ):
            chunks.append(path.read_text(encoding="utf-8", errors="replace"))
    return "\n".join(chunks)


def check_ownership(root: Path, ownership_path: Path, errors: list[str]) -> None:
    payload = tomllib.loads(ownership_path.read_text(encoding="utf-8"))
    if payload.get("schema_version") != 1:
        errors.append(
            f"ARCH013 unsupported ownership schema_version: {payload.get('schema_version')!r}"
        )
    records = payload.get("ownership", [])
    exception_records = payload.get("exception", [])
    if not isinstance(records, list) or not isinstance(exception_records, list):
        errors.append("ARCH013 ownership and exception entries must be arrays")
        return
    exceptions: dict[tuple[str, str], dict[str, object]] = {}
    used_exceptions: set[tuple[str, str]] = set()
    identifiers: set[str] = set()
    ownership_paths: set[str] = set()
    valid_records: list[dict[str, object]] = []

    for exception in exception_records:
        if not isinstance(exception, dict):
            errors.append(f"ARCH013 invalid architecture exception: {exception!r}")
            continue
        rule = exception.get("rule")
        path = exception.get("path")
        key = (str(rule), str(path))
        if (
            not isinstance(rule, str)
            or not rule.startswith("ARCH")
            or not isinstance(path, str)
            or not path
            or any(character in path for character in "*?[]")
            or not isinstance(exception.get("reason"), str)
            or not exception.get("reason")
            or not isinstance(exception.get("expires_after"), str)
            or not str(exception.get("expires_after")).startswith("M-")
            or not isinstance(exception.get("owner"), str)
            or not exception.get("owner")
        ):
            errors.append(f"ARCH013 invalid architecture exception: {exception!r}")
            continue
        if key in exceptions:
            errors.append(f"ARCH013 duplicate architecture exception: {rule} {path}")
            continue
        exceptions[key] = exception

    def add_violation(rule: str, path: str, message: str) -> None:
        key = (rule, path)
        if key in exceptions:
            used_exceptions.add(key)
            return
        errors.append(f"{rule} {message}")

    for record in records:
        if not isinstance(record, dict):
            errors.append(f"ARCH013 invalid ownership record: {record!r}")
            continue
        identifier = record.get("id")
        pattern = record.get("path")
        if not isinstance(identifier, str) or not identifier:
            errors.append("ARCH013 ownership record has no stable id")
            continue
        if identifier in identifiers:
            errors.append(f"ARCH013 duplicate ownership id: {identifier}")
        identifiers.add(identifier)
        if record.get("sync_zone") not in VALID_SYNC_ZONES:
            errors.append(f"ARCH013 {identifier} has invalid sync_zone")
        if not isinstance(pattern, str) or not pattern:
            errors.append(f"ARCH013 {identifier} has no path")
        elif pattern in ownership_paths:
            errors.append(f"ARCH013 duplicate ownership path: {pattern}")
        else:
            ownership_paths.add(pattern)
            valid_records.append(record)
        if not isinstance(record.get("owner"), str) or not record.get("owner"):
            errors.append(f"ARCH013 {identifier} has no owner")
        if not isinstance(record.get("phase"), str) or not record.get("phase"):
            errors.append(f"ARCH013 {identifier} has no phase")
        planned = record.get("planned")
        if planned is not None and planned is not True:
            errors.append(f"ARCH013 {identifier} has invalid planned flag")
        for field in ("allowed_import_roots", "forbidden_import_roots"):
            values = record.get(field, [])
            if not isinstance(values, list) or not all(
                isinstance(value, str) and value for value in values
            ):
                errors.append(f"ARCH013 {identifier} has invalid {field}")
        raw_allowed = record.get("allowed_import_roots", [])
        raw_forbidden = record.get("forbidden_import_roots", [])
        allowed_values = (
            {value for value in raw_allowed if isinstance(value, str)}
            if isinstance(raw_allowed, list)
            else set()
        )
        forbidden_values = (
            {value for value in raw_forbidden if isinstance(value, str)}
            if isinstance(raw_forbidden, list)
            else set()
        )
        overlap = allowed_values & forbidden_values
        if overlap:
            errors.append(
                f"ARCH013 {identifier} has imports both allowed and forbidden: "
                + ", ".join(sorted(overlap))
            )

    managed = sorted(
        path.relative_to(root).as_posix()
        for path in (root / "app/src/main/java").rglob("*")
        if path.is_file() and path.suffix in MANAGED_SOURCE_SUFFIXES
    )
    matched_counts = {record.get("id"): 0 for record in valid_records}
    for path in managed:
        relative_source = Path(path).relative_to("app/src/main/java")
        expected_package = ".".join(relative_source.parent.parts)
        declared_package = source_package(root / path)
        if declared_package != expected_package:
            add_violation(
                "ARCH012",
                path,
                f"source/package mismatch: {path} declares "
                f"{declared_package or 'no package'}, expected {expected_package}",
            )

        matches = [
            record
            for record in valid_records
            if path_matches(path, str(record.get("path", "")))
        ]
        if not matches:
            errors.append(f"ARCH013 unmanaged source file: {path}")
            continue
        if len(matches) > 1:
            errors.append(
                f"ARCH013 source file has multiple owners: {path} -> "
                + ", ".join(str(record.get("id")) for record in matches)
            )
        for record in matches:
            matched_counts[record.get("id")] += 1
        if len(matches) != 1:
            continue

        record = matches[0]
        identifier = str(record.get("id"))
        raw_allowed = record.get("allowed_import_roots", [])
        raw_forbidden = record.get("forbidden_import_roots", [])
        allowed = tuple(value for value in raw_allowed if isinstance(value, str))
        forbidden = tuple(value for value in raw_forbidden if isinstance(value, str))
        for line_number, imported in source_imports(root / path):
            forbidden_match = next(
                (value for value in forbidden if import_matches_root(imported, value)),
                None,
            )
            if forbidden_match:
                rule = dependency_rule(identifier, imported)
                add_violation(
                    rule,
                    path,
                    f"forbidden import: {path}:{line_number} imports {imported} from {identifier}",
                )
                continue
            if allowed and imported.startswith(PROJECT_IMPORT_ROOTS) and not any(
                import_matches_root(imported, value) for value in allowed
            ):
                rule = dependency_rule(identifier, imported)
                add_violation(
                    rule,
                    path,
                    f"import outside allowed roots: {path}:{line_number} "
                    f"imports {imported} from {identifier}",
                )

    for record in valid_records:
        if not record.get("planned") and matched_counts.get(record.get("id"), 0) == 0:
            errors.append(f"ARCH013 ownership path matches no source: {record.get('id')}")
    for rule, path in sorted(set(exceptions) - used_exceptions):
        errors.append(f"ARCH013 unused architecture exception: {rule} {path}")


def expected_manifest_components(snapshot_path: Path, phase: str) -> set[tuple[str, str]]:
    expected: set[tuple[str, str]] = set()
    for line in read_snapshot(snapshot_path):
        fields = line.split("\t")
        if len(fields) not in {2, 3}:
            raise ValueError(f"invalid manifest snapshot line: {line}")
        name = fields[2] if phase in {"m01", "post-m01"} and len(fields) == 3 else fields[1]
        expected.add((fields[0], name))
    return expected


def actual_manifest_components(manifest_path: Path) -> set[tuple[str, str]]:
    root = ET.parse(manifest_path).getroot()
    components: set[tuple[str, str]] = set()
    for tag in ("application", "activity", "activity-alias", "service", "receiver", "provider"):
        for node in root.iter(tag):
            name = node.get(ANDROID_NAME)
            if name:
                components.add((tag, name))
    return components


def check_manifest(root: Path, snapshot_path: Path, phase: str, errors: list[str]) -> None:
    expected = expected_manifest_components(snapshot_path, phase)
    actual = actual_manifest_components(root / "app/src/main/AndroidManifest.xml")
    for item in sorted(expected - actual):
        errors.append(f"ARCH008 missing manifest component: {item[0]} {item[1]}")
    for item in sorted(actual - expected):
        errors.append(f"ARCH008 unexpected manifest component: {item[0]} {item[1]}")


def check_literals(root: Path, snapshot_paths: tuple[Path, ...], errors: list[str]) -> None:
    text = repository_text(root)
    for snapshot_path in snapshot_paths:
        for expected_count, literal in read_count_snapshot(snapshot_path):
            actual_count = text.count(literal)
            if actual_count != expected_count:
                errors.append(
                    "ARCH009/ARCH010 stable literal count changed: "
                    f"{literal!r} expected {expected_count}, found {actual_count}"
                )


def check_tracked_artifacts(root: Path, errors: list[str]) -> None:
    for path in (value for value in git(root, "ls-files", "-z").split("\0") if value):
        normalized = path.replace("\\", "/")
        parts = set(Path(normalized).parts)
        if parts & PROHIBITED_TRACKED_PARTS:
            errors.append(f"ARCH015 tracked runtime/private path: {normalized}")
        if normalized.endswith((".apk", ".aab", ".bundle")) and not normalized.startswith(
            "app/src/main/assets/"
        ):
            errors.append(f"ARCH015 tracked build/backup artifact: {normalized}")
        if normalized in {"local.properties", ".env"} or normalized.endswith("/.env"):
            errors.append(f"ARCH015 tracked private config: {normalized}")


def check_terminal_unchanged(root: Path, base: str | None, errors: list[str]) -> None:
    if not base:
        return
    changed = {
        line.strip()
        for line in git(root, "diff", "--name-only", base, "--", "terminal").splitlines()
        if line.strip()
    }
    if changed:
        errors.append("ARCH014 terminal changed: " + ", ".join(sorted(changed)))


def normalize_m01_text(text: str) -> str:
    return (
        text.replace("\r\n", "\n")
        .replace("KiyoriApplication", "OperitApplication")
        .replace("kiyoriApplication", "operitApplication")
        .replace("Application class for Kiyori", "Application class for Operit")
    )


def candidate_text(root: Path, path: str) -> str:
    return (root / path).read_text(encoding="utf-8")


def base_text(root: Path, base: str, path: str) -> str:
    return git(root, "show", f"{base}:{path}")


def working_tree_app_paths(root: Path, base: str) -> set[str]:
    changed = {
        line.strip()
        for line in git(
            root,
            "diff",
            "--name-only",
            "--find-renames=90%",
            base,
            "--",
            "app",
        ).splitlines()
        if line.strip()
    }
    changed.update(
        line.strip()
        for line in git(
            root,
            "ls-files",
            "--others",
            "--exclude-standard",
            "--",
            "app",
        ).splitlines()
        if line.strip()
    )
    if M01_OLD_PATH in changed and M01_NEW_PATH in changed and not (root / M01_OLD_PATH).exists():
        changed.remove(M01_OLD_PATH)
    return changed


def check_m01(root: Path, base: str, errors: list[str]) -> None:
    changed = working_tree_app_paths(root, base)
    if changed != M01_EXPECTED_CANDIDATE_PATHS:
        errors.append(
            "ARCH016 M-01 implementation paths differ: expected "
            f"{sorted(M01_EXPECTED_CANDIDATE_PATHS)}, found {sorted(changed)}"
        )
        return

    for path in sorted(changed):
        source_path = M01_OLD_PATH if path == M01_NEW_PATH else path
        before = base_text(root, base, source_path)
        after = candidate_text(root, path)
        if normalize_m01_text(after) != normalize_m01_text(before):
            errors.append(f"ARCH016 non-name change in {path}")

    kotlin_files = list((root / "app/src/main/java").rglob("*.kt"))
    old_count = sum(path.read_text(encoding="utf-8").count("OperitApplication") for path in kotlin_files)
    old_count += (root / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8").count(
        "OperitApplication"
    )
    old_count += (root / "app/lint-baseline.xml").read_text(encoding="utf-8").count(
        "OperitApplication"
    )
    new_count = sum(path.read_text(encoding="utf-8").count("KiyoriApplication") for path in kotlin_files)
    new_count += (root / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8").count(
        "KiyoriApplication"
    )
    new_count += (root / "app/lint-baseline.xml").read_text(encoding="utf-8").count(
        "KiyoriApplication"
    )
    if old_count != 0 or new_count != 49:
        errors.append(f"ARCH016 M-01 symbol counts must be old=0/new=49, found {old_count}/{new_count}")

    package_line = next(
        (
            line.strip()
            for line in (root / M01_NEW_PATH).read_text(encoding="utf-8").splitlines()
            if line.startswith("package ")
        ),
        "",
    )
    if package_line != "package com.ai.assistance.operit.core.application":
        errors.append(f"ARCH016 M-01 package changed: {package_line or 'missing'}")


def git_path_exists(root: Path, revision: str, path: str) -> bool:
    result = subprocess.run(
        ["git", "cat-file", "-e", f"{revision}:{path}"],
        cwd=root,
        check=False,
        capture_output=True,
    )
    return result.returncode == 0


def resolve_phase(root: Path, requested: str, base: str | None) -> str:
    if requested != "auto":
        return requested
    if not (root / M01_NEW_PATH).is_file():
        return "baseline"
    if (
        base
        and git_path_exists(root, base, M01_OLD_PATH)
        and not git_path_exists(root, base, M01_NEW_PATH)
    ):
        return "m01"
    return "post-m01"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repository", type=Path, default=Path.cwd())
    parser.add_argument(
        "--ownership",
        type=Path,
        default=Path("config/architecture/package-ownership.toml"),
    )
    parser.add_argument(
        "--phase",
        choices=("auto", "baseline", "m01", "post-m01"),
        default="auto",
    )
    parser.add_argument("--base")
    parser.add_argument("--require-main", action="store_true")
    parser.add_argument("--json", action="store_true")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    root = args.repository.resolve()
    phase = resolve_phase(root, args.phase, args.base)
    errors: list[str] = []

    if args.require_main:
        branch = git(root, "branch", "--show-current").strip()
        if branch != "main":
            errors.append(f"ARCH000 current branch must be main, found {branch or 'detached HEAD'}")

    ownership_path = args.ownership
    if not ownership_path.is_absolute():
        ownership_path = root / ownership_path
    architecture_root = root / "config/architecture"
    checks = (
        lambda: check_ownership(root, ownership_path, errors),
        lambda: check_manifest(
            root,
            architecture_root / "manifest-components.txt",
            phase,
            errors,
        ),
        lambda: check_literals(
            root,
            (
                architecture_root / "stable-identifiers.txt",
                architecture_root / "persistence-names.txt",
                architecture_root / "native-ipc-identifiers.txt",
            ),
            errors,
        ),
        lambda: check_tracked_artifacts(root, errors),
        lambda: check_terminal_unchanged(root, args.base, errors),
    )
    for check in checks:
        try:
            check()
        except (
            OSError,
            TypeError,
            ValueError,
            ET.ParseError,
            tomllib.TOMLDecodeError,
            subprocess.CalledProcessError,
        ) as error:
            errors.append(f"ARCH000 checker input error: {error}")
    if phase == "m01":
        if not args.base:
            errors.append("ARCH016 --base is required for M-01")
        else:
            try:
                check_m01(root, args.base, errors)
            except (OSError, ValueError, subprocess.CalledProcessError) as error:
                errors.append(f"ARCH016 M-01 check failed: {error}")

    if args.json:
        print(json.dumps({"phase": phase, "errors": errors}, ensure_ascii=False, indent=2))
    elif errors:
        print("Architecture boundaries: FAIL", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
    else:
        print(f"Architecture boundaries: PASS (phase={phase})")
        print("- package ownership coverage")
        print("- manifest component snapshot")
        print("- stable persistence/native/protocol literals")
        print("- tracked artifact and terminal boundaries")
        if phase == "m01":
            print("- M-01 exact paths, normalized diff, package, and symbol counts")
    return 1 if errors else 0


if __name__ == "__main__":
    raise SystemExit(main())
