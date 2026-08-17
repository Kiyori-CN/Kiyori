#!/usr/bin/env python3
"""Minimal pkg-config implementation for the isolated Android source build."""

from __future__ import annotations

import os
import shlex
import sys
from pathlib import Path


def split_paths(value: str) -> list[Path]:
    paths: list[Path] = []
    for item in value.replace(";", os.pathsep).split(os.pathsep):
        if not item:
            continue
        if len(item) >= 3 and item[0] == "/" and item[2] == "/":
            item = f"{item[1].upper()}:{item[2:]}"
        paths.append(Path(item))
    return paths


def parse_pc(path: Path) -> tuple[dict[str, str], dict[str, str]]:
    variables: dict[str, str] = {}
    fields: dict[str, str] = {}
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        if "=" in line and ":" not in line.split("=", 1)[0]:
            name, value = line.split("=", 1)
            variables[name.strip()] = value.strip()
        elif ":" in line:
            name, value = line.split(":", 1)
            fields[name.strip()] = value.strip()
    return variables, fields


def expand(value: str, variables: dict[str, str]) -> str:
    for _ in range(32):
        changed = False
        for name, replacement in variables.items():
            token = "${" + name + "}"
            if token in value:
                value = value.replace(token, replacement)
                changed = True
        if not changed:
            return value
    raise ValueError("pkg-config variable expansion did not converge")


def find_pc(name: str) -> Path:
    package = name.removesuffix(".pc") + ".pc"
    search_paths = split_paths(os.environ.get("PKG_CONFIG_LIBDIR", ""))
    search_paths += split_paths(os.environ.get("PKG_CONFIG_PATH", ""))
    for directory in search_paths:
        candidate = directory / package
        if candidate.is_file():
            return candidate
    raise FileNotFoundError(f"package metadata not found: {name}")


def read_package(name: str, cache: dict[str, tuple[dict[str, str], dict[str, str]]]):
    path = find_pc(name)
    key = str(path.resolve())
    if key not in cache:
        cache[key] = parse_pc(path)
    return cache[key]


def package_names(arguments: list[str]) -> list[str]:
    return [
        argument
        for argument in arguments
        if not argument.startswith("-") and "=" not in argument
    ]


def apply_sysroot(tokens: list[str]) -> list[str]:
    sysroot = os.environ.get("PKG_CONFIG_SYSROOT_DIR", "")
    if not sysroot:
        return tokens
    normalized = sysroot.replace("\\", "/")
    translated: list[str] = []
    for token in tokens:
        if token.startswith("-I/usr/local"):
            translated.append("-I" + normalized + token[len("-I/usr/local") :])
        elif token.startswith("-L/usr/local"):
            translated.append("-L" + normalized + token[len("-L/usr/local") :])
        else:
            translated.append(token)
    return translated


def main() -> int:
    arguments = sys.argv[1:]
    if "--version" in arguments:
        print("pkgconf-compatible Kiyori source-build helper 1")
        return 0
    names = package_names(arguments)
    if not names:
        return 0

    cache: dict[str, tuple[dict[str, str], dict[str, str]]] = {}
    packages = [read_package(name, cache) for name in names]
    variables: dict[str, str] = {}
    fields: dict[str, str] = {}
    for package_variables, package_fields in packages:
        variables.update(package_variables)
        fields.update(package_fields)
    variables.setdefault("pcfiledir", str(find_pc(names[0]).parent))
    expanded_fields = {
        name: expand(value, variables) for name, value in fields.items()
    }

    if "--exists" in arguments:
        return 0
    if "--modversion" in arguments:
        print(expanded_fields.get("Version", ""))
        return 0
    variable_index = next(
        (index for index, argument in enumerate(arguments) if argument == "--variable"),
        None,
    )
    if variable_index is not None:
        variable_name = arguments[variable_index + 1]
        print(expand(variables.get(variable_name, ""), variables))
        return 0

    output: list[str] = []
    if "--cflags" in arguments:
        output.extend(
            apply_sysroot(shlex.split(expanded_fields.get("Cflags", ""), posix=True))
        )
    if "--libs" in arguments:
        output.extend(
            apply_sysroot(shlex.split(expanded_fields.get("Libs", ""), posix=True))
        )
    print(" ".join(output))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (FileNotFoundError, IndexError, ValueError) as error:
        print(str(error), file=sys.stderr)
        raise SystemExit(1)
