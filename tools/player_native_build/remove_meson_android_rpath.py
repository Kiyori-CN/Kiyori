#!/usr/bin/env python3
"""Remove the validated Windows-host build RPATH from Meson's libmpv link rule."""

from __future__ import annotations

import argparse
import re
from pathlib import Path


TARGET_PREFIX = "build libmpv.so:"
LINK_ARGS_PREFIX = " LINK_ARGS = "
RPATH_TOKEN_PATTERN = re.compile(r'"-Wl,-rpath,[^"]*"')


def normalize_rpath_dir(value: str) -> str:
    normalized = value.replace("\\", "/").rstrip("/")
    if not re.fullmatch(r"[A-Za-z]:/.+", normalized):
        raise ValueError(f"expected an absolute Windows mixed path, got {value!r}")
    return normalized


def remove_validated_libmpv_rpath(
    build_ninja: Path,
    expected_rpath_dirs: tuple[str, str],
) -> bool:
    if not build_ninja.is_file():
        raise FileNotFoundError(f"Meson build.ninja is missing: {build_ninja}")

    normalized_dirs = tuple(normalize_rpath_dir(value) for value in expected_rpath_dirs)
    if normalized_dirs[0] == normalized_dirs[1]:
        raise ValueError("the expected NDK and closure RPATH directories must differ")
    expected_token = f'"-Wl,-rpath,{":".join(normalized_dirs)}"'

    source = build_ninja.read_text(encoding="utf-8")
    lines = source.splitlines(keepends=True)
    target_lines = [
        index for index, line in enumerate(lines) if line.startswith(TARGET_PREFIX)
    ]
    if len(target_lines) != 1:
        raise RuntimeError(
            "expected exactly one Meson libmpv.so target, "
            f"found {len(target_lines)} in {build_ninja}"
        )

    target_start = target_lines[0]
    target_end = len(lines)
    for index in range(target_start + 1, len(lines)):
        if not lines[index].strip():
            target_end = index
            break
    link_args_lines = [
        index
        for index in range(target_start + 1, target_end)
        if lines[index].startswith(LINK_ARGS_PREFIX)
    ]
    if len(link_args_lines) != 1:
        raise RuntimeError(
            "expected exactly one LINK_ARGS line for Meson's libmpv.so target, "
            f"found {len(link_args_lines)} in {build_ninja}"
        )

    link_args_index = link_args_lines[0]
    target_rpath_tokens = RPATH_TOKEN_PATTERN.findall(lines[link_args_index])
    all_rpath_tokens = RPATH_TOKEN_PATTERN.findall(source)
    if not target_rpath_tokens:
        if all_rpath_tokens:
            raise RuntimeError(
                "Meson build contains an RPATH outside the libmpv.so target: "
                f"{all_rpath_tokens}"
            )
        return False
    if len(target_rpath_tokens) != 1 or len(all_rpath_tokens) != 1:
        raise RuntimeError(
            "expected one build RPATH owned only by libmpv.so, "
            f"found target={target_rpath_tokens}, all={all_rpath_tokens}"
        )
    if target_rpath_tokens[0] != expected_token:
        raise RuntimeError(
            "Meson's libmpv.so build RPATH differs from the fixed Android closure: "
            f"expected {expected_token}, got {target_rpath_tokens[0]}"
        )

    removable = f" {expected_token}"
    if lines[link_args_index].count(removable) != 1:
        raise RuntimeError(
            "Meson's libmpv.so RPATH token is not a unique LINK_ARGS element: "
            f"{lines[link_args_index].rstrip()}"
        )
    lines[link_args_index] = lines[link_args_index].replace(removable, "", 1)
    updated = "".join(lines)
    if RPATH_TOKEN_PATTERN.search(updated):
        raise RuntimeError("Meson build RPATH remained after the validated removal")

    with build_ninja.open("w", encoding="utf-8", newline="") as stream:
        stream.write(updated)
    return True


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--build-ninja", type=Path, required=True)
    parser.add_argument(
        "--expected-rpath-dir",
        action="append",
        required=True,
        help="Expected directory in Meson's ordered build RPATH; pass exactly twice.",
    )
    args = parser.parse_args()
    if len(args.expected_rpath_dir) != 2:
        parser.error("--expected-rpath-dir must be passed exactly twice")

    changed = remove_validated_libmpv_rpath(
        args.build_ninja,
        (args.expected_rpath_dir[0], args.expected_rpath_dir[1]),
    )
    state = "removed" if changed else "already absent"
    print(f"Validated libmpv.so Android build RPATH: {state}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
