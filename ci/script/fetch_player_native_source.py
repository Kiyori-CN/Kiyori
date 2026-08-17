#!/usr/bin/env python3
"""Small wget-compatible stream fetcher for the isolated player source build."""

from __future__ import annotations

import argparse
import shutil
import sys
import urllib.request
from pathlib import Path


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("url")
    parser.add_argument("-O", "--output-document", dest="output", default="-")
    args, unknown = parser.parse_known_args()
    if unknown:
        raise SystemExit(f"unsupported fetch arguments: {unknown}")

    request = urllib.request.Request(
        args.url,
        headers={"User-Agent": "Kiyori-player-native-source-builder/1"},
    )
    with urllib.request.urlopen(request, timeout=180) as response:
        if args.output == "-":
            shutil.copyfileobj(response, sys.stdout.buffer, length=1024 * 1024)
        else:
            destination = Path(args.output)
            destination.parent.mkdir(parents=True, exist_ok=True)
            with destination.open("wb") as stream:
                shutil.copyfileobj(response, stream, length=1024 * 1024)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
