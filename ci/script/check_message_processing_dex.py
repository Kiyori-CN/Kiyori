#!/usr/bin/env python3
"""Audit the Android DEX continuation boundaries for ordinary message sending.

This check is intentionally APK-only. It does not inspect or change runtime state, call a model
endpoint, install an APK, or infer behavior from source size. The target device crash occurred in
ART while interpreting a generated coroutine state machine, so the final DEX is the authoritative
local artifact for this narrow compatibility gate.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
from pathlib import Path
from typing import Sequence


APKANALYZER_METHOD = "invokeSuspend(Ljava/lang/Object;)Ljava/lang/Object;"
DELEGATE_CLASS = "com.ai.assistance.operit.services.core.MessageProcessingDelegate"


def _command_for_executable(executable: Path, arguments: Sequence[str]) -> list[str]:
    command = [str(executable), *arguments]
    if os.name == "nt" and executable.suffix.lower() in {".bat", ".cmd"}:
        return ["cmd.exe", "/d", "/s", "/c", subprocess.list2cmdline(command)]
    return command


def _run_apkanalyzer(apkanalyzer: Path, arguments: Sequence[str], apk: Path) -> str:
    completed = subprocess.run(
        _command_for_executable(apkanalyzer, [*arguments, str(apk)]),
        check=False,
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
    )
    if completed.returncode != 0:
        raise RuntimeError(
            f"apkanalyzer failed with exit code {completed.returncode}: "
            f"{completed.stderr.strip() or completed.stdout.strip()}"
        )
    return completed.stdout


def _read_registers(
    apkanalyzer: Path,
    apk: Path,
    class_name: str,
) -> dict[str, object]:
    output = _run_apkanalyzer(
        apkanalyzer,
        ["dex", "code", "--class", class_name, "--method", APKANALYZER_METHOD],
        apk,
    )
    match = re.search(r"(?m)^\s*\.registers\s+(\d+)\s*$", output)
    if match is None:
        raise RuntimeError(
            f"invokeSuspend was not found or could not be decoded for {class_name}"
        )
    return {
        "class": class_name,
        "registers": int(match.group(1)),
        "smali_lines": len(output.splitlines()),
    }


def _read_method_size(
    packages_output: str,
    method_name: str,
) -> int:
    pattern = re.compile(
        rf"^M\s+\S+\s+\d+\s+\d+\s+(\d+)\s+"
        rf"{re.escape(DELEGATE_CLASS)}\s+java\.lang\.Object\s+"
        rf"{re.escape(method_name)}\(",
        re.MULTILINE,
    )
    match = pattern.search(packages_output)
    if match is None:
        raise RuntimeError(f"method {method_name} was not found in the APK DEX")
    return int(match.group(1))


def _discover_top_level_continuation(
    packages_output: str,
    function_name: str,
) -> str:
    pattern = re.compile(
        rf"^M\s+\S+\s+\d+\s+\d+\s+\d+\s+"
        rf"({re.escape(DELEGATE_CLASS)}\${re.escape(function_name)}\$\d+)\s+"
        rf"java\.lang\.Object\s+invokeSuspend\(java\.lang\.Object\)\s*$",
        re.MULTILINE,
    )
    matches = sorted(set(pattern.findall(packages_output)))
    if len(matches) != 1:
        raise RuntimeError(
            f"expected one top-level continuation for {function_name}, found {matches}"
        )
    return matches[0]


def audit(apk: Path, apkanalyzer: Path) -> dict[str, object]:
    if not apk.is_file():
        raise RuntimeError(f"APK does not exist: {apk}")
    if not apkanalyzer.is_file():
        raise RuntimeError(f"apkanalyzer does not exist: {apkanalyzer}")

    packages_output = _run_apkanalyzer(
        apkanalyzer,
        ["dex", "packages", "--defined-only"],
        apk,
    )
    collector_continuation = _discover_top_level_continuation(
        packages_output,
        "collectAssistantResponseStream",
    )
    continuation_limits = {
        f"{DELEGATE_CLASS}$sendUserMessage$sendJob$1": 64,
        f"{DELEGATE_CLASS}$executeSendUserMessageTurn$1": 128,
        collector_continuation: 128,
    }
    continuations = [
        {
            **_read_registers(apkanalyzer, apk, class_name),
            "max_registers": max_registers,
        }
        for class_name, max_registers in continuation_limits.items()
    ]
    main_method_size = _read_method_size(
        packages_output,
        "executeSendUserMessageTurn",
    )
    result = {
        "apk": str(apk),
        "main_method": {
            "class": DELEGATE_CLASS,
            "method": "executeSendUserMessageTurn",
            "size": main_method_size,
            "max_size": 100_000,
        },
        "continuations": continuations,
    }

    violations: list[str] = []
    if main_method_size > 100_000:
        violations.append(
            f"executeSendUserMessageTurn method size {main_method_size} exceeds 100000"
        )
    for continuation in continuations:
        registers = int(continuation["registers"])
        max_registers = int(continuation["max_registers"])
        if registers > max_registers:
            violations.append(
                f"{continuation['class']} uses {registers} registers, "
                f"limit is {max_registers}"
            )
    result["violations"] = violations
    return result


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Audit Kiyori message-sending DEX continuation boundaries."
    )
    parser.add_argument("--apk", required=True, type=Path)
    parser.add_argument("--apkanalyzer", required=True, type=Path)
    parser.add_argument("--json", action="store_true")
    args = parser.parse_args()

    try:
        result = audit(args.apk, args.apkanalyzer)
    except (OSError, RuntimeError) as error:
        print(f"DEX audit failed: {error}", file=sys.stderr)
        return 1

    violations = result["violations"]
    if args.json:
        print(json.dumps(result, ensure_ascii=False, indent=2))
    else:
        main_method = result["main_method"]
        print(
            "executeSendUserMessageTurn: "
            f"{main_method['size']} / {main_method['max_size']} method-size limit"
        )
        for continuation in result["continuations"]:
            print(
                f"{continuation['class']}: "
                f"{continuation['registers']} / {continuation['max_registers']} registers, "
                f"{continuation['smali_lines']} smali lines"
            )

    if violations:
        for violation in violations:
            print(f"VIOLATION: {violation}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
