#!/usr/bin/env python3
"""Normalize environment-specific paths in the Android lint baseline."""

from __future__ import annotations

import argparse
from collections import Counter
import hashlib
import re
from xml.etree import ElementTree
from pathlib import Path


DEFAULT_BASELINE = Path("app/lint-baseline.xml")
HOME_VERSION_CATALOG_PATH = re.compile(
    r'file="\$HOME/[^\"]*/gradle/libs\.versions\.toml"'
)
ISSUE_BLOCK = re.compile(
    r"^    <issue\r?\n.*?^    </issue>\r?\n(?:\r?\n)?",
    re.MULTILINE | re.DOTALL,
)
NORMALIZED_VERSION_CATALOG_PATH = 'file="../gradle/libs.versions.toml"'
EXPECTED_SHA256 = "240ba1c6941485004d362ed8b74a464773b23b0fe62815e469488f3d3c64efa7"


def normalize(text: str) -> str:
    return HOME_VERSION_CATALOG_PATH.sub(NORMALIZED_VERSION_CATALOG_PATH, text)


def _issue_signature(issue: ElementTree.Element) -> tuple[str, str, tuple[str, ...]]:
    locations = tuple(
        location.attrib.get("file", "").replace("\\", "/")
        for location in issue.findall("location")
    )
    return (
        issue.attrib.get("id", ""),
        issue.attrib.get("message", ""),
        locations,
    )


def _verified_issue_blocks(
    text: str,
    root: ElementTree.Element,
) -> tuple[str, list[tuple[ElementTree.Element, str]], str]:
    matches = list(ISSUE_BLOCK.finditer(text))
    parsed_issues = root.findall("issue")
    if len(matches) != len(parsed_issues):
        raise ValueError(
            "lint baseline does not use the expected generated issue block format"
        )

    blocks: list[tuple[ElementTree.Element, str]] = []
    for parsed_issue, match in zip(parsed_issues, matches, strict=True):
        block = match.group(0)
        block_issue = ElementTree.fromstring(block.strip())
        if _issue_signature(block_issue) != _issue_signature(parsed_issue):
            raise ValueError("lint baseline issue block order does not match parsed XML")
        blocks.append((parsed_issue, block))

    prefix = text[: matches[0].start()] if matches else text
    suffix = text[matches[-1].end() :] if matches else ""
    return prefix, blocks, suffix


def prune_stale_issues(
    baseline_text: str,
    current_text: str,
) -> tuple[str, int, int, int]:
    """Keep only reviewed baseline issues that are still reported now."""

    baseline_root = ElementTree.fromstring(normalize(baseline_text))
    current_root = ElementTree.fromstring(normalize(current_text))
    prefix, reviewed_blocks, suffix = _verified_issue_blocks(
        normalize(baseline_text),
        baseline_root,
    )
    current = Counter(_issue_signature(issue) for issue in current_root.findall("issue"))

    retained_blocks: list[str] = []
    stale = 0
    for issue, block in reviewed_blocks:
        signature = _issue_signature(issue)
        if current[signature] > 0:
            current[signature] -= 1
            retained_blocks.append(block)
        else:
            stale += 1

    current_only = sum(current.values())
    return (
        prefix + "".join(retained_blocks) + suffix,
        stale,
        current_only,
        len(retained_blocks),
    )


def _write_lf(path: Path, text: str) -> None:
    with path.open("w", encoding="utf-8", newline="\n") as stream:
        stream.write(text)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    parser.add_argument(
        "--prune-against",
        type=Path,
        help="full temporary baseline generated from the current source tree",
    )
    parser.add_argument(
        "--output",
        type=Path,
        help="write the normalized intersection to a different baseline path",
    )
    parser.add_argument("path", nargs="?", type=Path, default=DEFAULT_BASELINE)
    args = parser.parse_args()

    original = args.path.read_text(encoding="utf-8")
    normalized = normalize(original)
    candidate = normalized
    if args.prune_against is not None:
        current = args.prune_against.read_text(encoding="utf-8")
        candidate, stale, current_only, retained = prune_stale_issues(
            normalized,
            current,
        )
        print(
            "Lint baseline intersection: "
            f"retained={retained}, stale={stale}, current-only={current_only}"
        )
        if stale == 0:
            candidate = normalized

    target = args.output or args.path

    if args.check:
        if args.output is not None:
            parser.error("--output cannot be combined with --check")
        if original != candidate:
            if original != normalized:
                reason = "environment-specific paths found"
            else:
                reason = "stale records found"
            print(
                f"{args.path}: {reason}; "
                "run ci/script/normalize_lint_baseline.py"
            )
            return 1
        actual_sha256 = hashlib.sha256(candidate.encode("utf-8")).hexdigest()
        if actual_sha256 != EXPECTED_SHA256:
            print(
                f"{args.path}: checksum changed from {EXPECTED_SHA256} "
                f"to {actual_sha256}; review the baseline diff and update its recorded checksum"
            )
            return 1
        print(f"Lint baseline paths are normalized: {target}")
        return 0

    if args.output is not None or original != candidate:
        _write_lf(target, candidate)
        print(f"Normalized lint baseline: {target}")
    else:
        print(f"Lint baseline paths already normalized: {target}")
    actual_sha256 = hashlib.sha256(candidate.encode("utf-8")).hexdigest()
    print(f"Lint baseline SHA-256: {actual_sha256}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
