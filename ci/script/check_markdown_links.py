#!/usr/bin/env python3
"""Report local Markdown links newly broken by a merge candidate."""

from __future__ import annotations

import argparse
from collections import Counter
from collections.abc import Container
import os
from pathlib import Path
import posixpath
import re
import subprocess
import urllib.parse
from dataclasses import dataclass

from check_output import Diagnostic, report

DEFINITION_RE = re.compile(r"^\s{0,3}\[[^\]]+\]:\s*(.*)$")
URI_SCHEME_RE = re.compile(r"^[A-Za-z][A-Za-z0-9+.-]*:")


@dataclass(frozen=True)
class LinkIssue:
    path: str
    line: int
    target: str

    @property
    def identity(self) -> tuple[str, str]:
        return self.path, self.target


class SubmoduleTreeUnavailable(RuntimeError):
    """A referenced gitlink cannot be verified using the local object database."""


class GitTree:
    def __init__(self, commit: str, repository: Path) -> None:
        self.repository = repository
        result = subprocess.run(
            ["git", "-C", str(repository), "ls-tree", "-r", "-z", commit],
            check=True,
            capture_output=True,
        )
        self.files: set[str] = set()
        self.gitlinks: dict[str, str] = {}
        self.children: dict[str, GitTree] = {}
        for entry in result.stdout.split(b"\0"):
            if not entry:
                continue
            metadata, raw_path = entry.split(b"\t", 1)
            mode, kind, object_id = metadata.decode("ascii").split()
            path = os.fsdecode(raw_path)
            if mode == "160000" and kind == "commit":
                self.gitlinks[path] = object_id
            elif kind == "blob":
                self.files.add(path)
        self.paths = self.files | set(self.gitlinks)
        self.paths |= directory_paths(self.paths)

    def __contains__(self, target: str) -> bool:
        if target in self.paths:
            return True
        for path, commit in self.gitlinks.items():
            if not target.startswith(path + "/"):
                continue
            if path not in self.children:
                repository = self.repository / path
                # An uninitialized submodule directory otherwise makes git inspect its parent.
                if not (repository / ".git").exists():
                    raise SubmoduleTreeUnavailable(
                        f"Cannot verify link inside submodule {repository.as_posix()} at {commit}: "
                        "initialize the submodule at the recorded gitlink first."
                    )
                try:
                    self.children[path] = GitTree(commit, repository)
                except subprocess.CalledProcessError as error:
                    raise SubmoduleTreeUnavailable(
                        f"Cannot read submodule {repository.as_posix()} at {commit}: "
                        "the recorded gitlink commit must be available locally."
                    ) from error
            return target[len(path) + 1:] in self.children[path]
        return False


def read_blob(commit: str, path: str, repository: Path) -> str:
    result = subprocess.run(
        ["git", "-C", str(repository), "show", f"{commit}:{path}"],
        check=True,
        capture_output=True,
    )
    return result.stdout.decode("utf-8")


def directory_paths(paths: set[str]) -> set[str]:
    directories = {"."}
    for path in paths:
        parent = posixpath.dirname(path)
        while parent:
            directories.add(parent)
            parent = posixpath.dirname(parent)
    return directories

def should_skip_target(target: str) -> bool:
    return (
        not target
        or target.startswith("#")
        or target.startswith("//")
        or bool(URI_SCHEME_RE.match(target))
    )


def resolved_target(source_path: str, target: str) -> str | None:
    parsed = urllib.parse.urlsplit(target)
    link_path = urllib.parse.unquote(parsed.path)
    if not link_path:
        return None
    if link_path.startswith("/"):
        return posixpath.normpath(link_path.lstrip("/"))
    return posixpath.normpath(posixpath.join(posixpath.dirname(source_path), link_path))


def mask_code_spans(line: str) -> str:
    characters = list(line)
    index = 0
    while index < len(line):
        if line[index] != "`":
            index += 1
            continue
        run_end = index
        while run_end < len(line) and line[run_end] == "`":
            run_end += 1
        delimiter = line[index:run_end]
        close = line.find(delimiter, run_end)
        if close == -1:
            index = run_end
            continue
        for masked_index in range(index, close + len(delimiter)):
            characters[masked_index] = " "
        index = close + len(delimiter)
    return "".join(characters)


def fence_marker(line: str) -> tuple[str, int, str] | None:
    indentation = len(line) - len(line.lstrip(" "))
    if indentation > 3:
        return None
    content = line[indentation:]
    if not content or content[0] not in {"`", "~"}:
        return None
    marker = content[0]
    run_length = len(content) - len(content.lstrip(marker))
    if run_length < 3:
        return None
    return marker, run_length, content[run_length:]


def parse_destination(value: str, start: int = 0) -> tuple[str | None, int]:
    index = start
    while index < len(value) and value[index].isspace():
        index += 1
    if index >= len(value):
        return None, index
    if value[index] == "<":
        close = value.find(">", index + 1)
        if close == -1:
            return None, index
        return value[index + 1 : close], close + 1

    destination: list[str] = []
    depth = 0
    while index < len(value):
        character = value[index]
        if character == "\\" and index + 1 < len(value):
            destination.append(value[index + 1])
            index += 2
            continue
        if character == "(":
            depth += 1
            destination.append(character)
            index += 1
            continue
        if character == ")":
            if depth == 0:
                break
            depth -= 1
            destination.append(character)
            index += 1
            continue
        if character.isspace() and depth == 0:
            break
        destination.append(character)
        index += 1
    return "".join(destination) or None, index


def inline_targets(line: str) -> list[str]:
    masked = mask_code_spans(line)
    targets: list[str] = []
    search_start = 0
    while True:
        marker = masked.find("](", search_start)
        if marker == -1:
            break
        target, end = parse_destination(masked, marker + 2)
        if target is not None:
            targets.append(target)
        search_start = max(end + 1, marker + 2)
    return targets


def check_file(path: str, text: str, existing_paths: Container[str]) -> list[LinkIssue]:
    issues: list[LinkIssue] = []
    open_fence: tuple[str, int] | None = None
    for line_number, line in enumerate(text.splitlines(), start=1):
        marker = fence_marker(line)
        if open_fence is not None:
            if (
                marker is not None
                and marker[0] == open_fence[0]
                and marker[1] >= open_fence[1]
                and not marker[2].strip()
            ):
                open_fence = None
            continue
        if marker is not None and not (marker[0] == "`" and "`" in marker[2]):
            open_fence = (marker[0], marker[1])
            continue

        targets = inline_targets(line)
        definition = DEFINITION_RE.match(mask_code_spans(line))
        if definition:
            target, _ = parse_destination(definition.group(1))
            if target is not None:
                targets.append(target)

        for target in targets:
            if should_skip_target(target):
                continue
            resolved = resolved_target(path, target)
            if resolved is None:
                continue
            if resolved == ".." or resolved.startswith("../") or resolved not in existing_paths:
                issues.append(LinkIssue(path=path, line=line_number, target=target))
    return issues


def snapshot_issues(commit: str, repository: Path = Path(".")) -> list[LinkIssue]:
    tree = GitTree(commit, repository)
    issues: list[LinkIssue] = []
    for path in sorted(value for value in tree.files if value.endswith((".md", ".mdx"))):
        try:
            text = read_blob(commit, path, repository)
        except UnicodeDecodeError:
            continue
        issues.extend(check_file(path, text, tree))
    return issues


def renamed_markdown_paths(base: str, candidate: str) -> dict[str, str]:
    result = subprocess.run(
        [
            "git",
            "diff",
            "--name-status",
            "-z",
            "-M",
            base,
            candidate,
            "--",
            "*.md",
            "*.mdx",
        ],
        check=True,
        capture_output=True,
    )
    values = [os.fsdecode(value) for value in result.stdout.split(b"\0") if value]
    renames: dict[str, str] = {}
    index = 0
    while index < len(values):
        status = values[index]
        index += 1
        if status.startswith(("R", "C")):
            old_path, new_path = values[index], values[index + 1]
            index += 2
            if status.startswith("R"):
                renames[old_path] = new_path
        else:
            index += 1
    return renames


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", required=True)
    parser.add_argument("--candidate", required=True)
    args = parser.parse_args()

    try:
        base_issues = snapshot_issues(args.base)
        candidate_issues = snapshot_issues(args.candidate)
    except SubmoduleTreeUnavailable as error:
        return report("Markdown links", [Diagnostic(code="markdown-submodule", message=str(error))])
    renames = renamed_markdown_paths(args.base, args.candidate)
    base_identities = Counter((renames.get(issue.path, issue.path), issue.target) for issue in base_issues)
    new_issues: list[LinkIssue] = []
    for issue in candidate_issues:
        if base_identities[issue.identity] > 0:
            base_identities[issue.identity] -= 1
        else:
            new_issues.append(issue)
    existing_count = len(candidate_issues) - len(new_issues)

    errors = [
        Diagnostic(
            code="markdown-link",
            path=issue.path,
            line=issue.line,
            message=f"missing local link target: {issue.target}",
        )
        for issue in new_issues
    ]
    return report(
        "Markdown links",
        errors,
        notes=[f"Ignored {existing_count} broken local link(s) already present in the base tree."],
    )


if __name__ == "__main__":
    raise SystemExit(main())
