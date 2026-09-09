"""真实 CLI 入口：`python -m kiyori_office <cmd> --args-file args.json`。"""

from __future__ import annotations

import json
import os
import subprocess
import sys
from pathlib import Path

from kiyori_office import protocol

RUNTIME_PARENT = Path(__file__).resolve().parents[2]


def _run_cli(tmp_path, command, args):
    args_file = tmp_path / "args.json"
    args_file.write_text(json.dumps(args, ensure_ascii=False), encoding="utf-8")
    env = dict(os.environ)
    env["PYTHONPATH"] = str(RUNTIME_PARENT)
    env["KIYORI_OFFICE_WORK_ROOT"] = str(tmp_path)
    completed = subprocess.run(
        [sys.executable, "-m", "kiyori_office", command, "--args-file", str(args_file)],
        capture_output=True,
        text=True,
        encoding="utf-8",
        env=env,
        timeout=120,
        check=False,
    )
    return completed


def test_cli_emits_parseable_envelope_with_unicode(tmp_path):
    source = tmp_path / "季度 报告 📊.txt"
    source.write_text("营收 100", encoding="utf-8")
    completed = _run_cli(tmp_path, "office_read", {"path": str(source)})
    assert completed.returncode == 0, completed.stderr
    payload = protocol.parse_sentinel(completed.stdout)
    assert payload["ok"] is True, payload
    assert "营收 100" in payload["data"]["text"]


def test_cli_returns_failure_envelope_for_bad_args_file(tmp_path):
    args_file = tmp_path / "bad.json"
    args_file.write_text("{not json", encoding="utf-8")
    env = dict(os.environ)
    env["PYTHONPATH"] = str(RUNTIME_PARENT)
    completed = subprocess.run(
        [sys.executable, "-m", "kiyori_office", "office_env_check", "--args-file", str(args_file)],
        capture_output=True,
        text=True,
        encoding="utf-8",
        env=env,
        timeout=120,
        check=False,
    )
    assert completed.returncode == 0
    payload = protocol.parse_sentinel(completed.stdout)
    assert payload["ok"] is False
    assert payload["error"]["code"] == "E_INPUT_SCHEMA"


def test_resource_zip_layout_matches_bootstrap_expectation(tmp_path):
    """宿主把目录资源打成以该目录父目录为根的 zip，运行时解压后应能 import。"""

    import zipfile

    runtime_dir = tmp_path / "runtime"
    runtime_dir.mkdir()
    source_root = RUNTIME_PARENT / "kiyori_office"
    archive_path = tmp_path / "kiyori_office_runtime.zip"
    with zipfile.ZipFile(archive_path, "w") as archive:
        for path in sorted(source_root.rglob("*")):
            if path.is_dir() or "__pycache__" in path.parts:
                continue
            archive.write(path, path.relative_to(source_root.parent).as_posix())
    with zipfile.ZipFile(archive_path) as archive:
        first = archive.namelist()[0]
        assert first.startswith("kiyori_office/")
        archive.extractall(runtime_dir)
    args_file = tmp_path / "args.json"
    args_file.write_text("{}", encoding="utf-8")
    env = dict(os.environ)
    env["PYTHONPATH"] = str(runtime_dir)
    completed = subprocess.run(
        [sys.executable, "-m", "kiyori_office", "office_env_check", "--args-file", str(args_file)],
        capture_output=True,
        text=True,
        encoding="utf-8",
        env=env,
        timeout=120,
        check=False,
    )
    assert completed.returncode == 0, completed.stderr
    payload = protocol.parse_sentinel(completed.stdout)
    assert payload["ok"] is True, payload
