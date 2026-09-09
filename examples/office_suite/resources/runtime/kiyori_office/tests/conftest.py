"""pytest 公共夹具：把工作区固定在临时目录，避免污染真实 HOME。"""

from __future__ import annotations

import os
import sys
from pathlib import Path

import pytest

RUNTIME_ROOT = Path(__file__).resolve().parents[1]
if str(RUNTIME_ROOT.parent) not in sys.path:
    sys.path.insert(0, str(RUNTIME_ROOT.parent))


@pytest.fixture(autouse=True)
def isolated_work_root(tmp_path, monkeypatch):
    # 用 pytest 的临时目录作为允许根：测试文件与被测工作区同根，既不污染真实
    # HOME，也符合「路径必须落在允许根内」的生产约束。
    monkeypatch.setenv("KIYORI_OFFICE_WORK_ROOT", str(tmp_path))
    yield tmp_path


@pytest.fixture
def samples(tmp_path):
    directory = tmp_path / "samples"
    directory.mkdir()
    return directory
