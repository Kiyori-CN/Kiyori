#!/usr/bin/env python3
"""Verify that the current commit can be cloned and initialize its terminal submodule."""

from __future__ import annotations

import argparse
import subprocess
import tempfile
from pathlib import Path


def run(cwd: Path, *args: str, check: bool = True) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        list(args),
        cwd=cwd,
        check=check,
        capture_output=True,
        text=True,
    )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repository", type=Path, default=Path.cwd())
    args = parser.parse_args()

    source = args.repository.resolve()
    head = run(source, "git", "rev-parse", "HEAD").stdout.strip()
    with tempfile.TemporaryDirectory(prefix="kiyori-fresh-clone-") as directory:
        clone = Path(directory) / "Kiyori"
        run(source, "git", "clone", "--no-local", "--no-checkout", str(source), str(clone))
        if run(clone, "git", "cat-file", "-e", f"{head}^{{commit}}", check=False).returncode != 0:
            run(clone, "git", "fetch", "--depth", "1", str(source), head)
        run(clone, "git", "checkout", "--detach", head)
        run(clone, "git", "submodule", "sync", "--", "terminal")
        run(clone, "git", "submodule", "update", "--init", "--recursive", "--depth", "1", "terminal")
        status = run(clone, "git", "submodule", "status", "--", "terminal").stdout.strip()
        if not status or status[0] in "-+U":
            raise RuntimeError(f"terminal submodule is not initialized at the pinned commit: {status or 'missing'}")
        if run(clone, "git", "status", "--short", "--ignore-submodules=none").stdout.strip():
            raise RuntimeError("fresh clone is dirty after submodule initialization")

    print(f"Fresh clone: PASS ({head})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
