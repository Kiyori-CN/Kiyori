"""路径安全、原子写入与输出预算。"""

from __future__ import annotations

import os
from pathlib import Path

import pytest

from kiyori_office import paths
from kiyori_office.budget import bounded_text
from kiyori_office.protocol import OfficeError


def test_path_escape_is_rejected():
    with pytest.raises(OfficeError) as excinfo:
        paths.resolve_path("/etc/passwd", args={}, field="path", must_exist=True)
    assert excinfo.value.code == "E_PATH_INVALID"


def test_parent_traversal_is_rejected():
    with pytest.raises(OfficeError) as excinfo:
        paths.resolve_path("../outside.txt", args={}, field="path")
    assert excinfo.value.code == "E_PATH_INVALID"


def test_symlink_is_rejected(tmp_path, monkeypatch):
    root = paths.work_root()
    real = root / "real.txt"
    real.write_text("data", encoding="utf-8")
    link = root / "link.txt"
    try:
        os.symlink(real, link)
    except (OSError, NotImplementedError):
        pytest.skip("当前平台不允许创建符号链接")
    with pytest.raises(OfficeError) as excinfo:
        paths.resolve_path(str(link), args={}, field="path", must_exist=True)
    assert excinfo.value.code == "E_PATH_INVALID"


def test_allow_roots_extends_scope(tmp_path):
    outside = tmp_path.parent / (tmp_path.name + "-outside")
    outside.mkdir(exist_ok=True)
    target = outside / "file.txt"
    target.write_text("ok", encoding="utf-8")
    with pytest.raises(OfficeError):
        paths.resolve_path(str(target), args={}, field="path", must_exist=True)
    resolved = paths.resolve_path(
        str(target),
        args={"allow_roots": [str(outside)]},
        field="path",
        must_exist=True,
    )
    assert resolved == target.resolve()


def test_unicode_and_emoji_filenames_survive():
    name = paths.sanitize_filename("季度 报告 📊.docx")
    assert "季度" in name and "📊" in name
    assert "/" not in name


def test_atomic_write_and_sha256(tmp_path):
    target = tmp_path / "out.bin"
    paths.atomic_write_bytes(target, b"payload")
    assert target.read_bytes() == b"payload"
    assert paths.sha256_of(target) == paths.sha256_of(target)
    assert not list(tmp_path.glob(".tmp-*"))


def test_bounded_text_writes_full_output(tmp_path):
    text = "x" * 100
    result = bounded_text(text, 10, full_output_dir=tmp_path, full_output_name="full.txt")
    assert result["truncated"] is True
    assert result["value"] == "x" * 10
    assert Path(result["full_output_path"]).read_text(encoding="utf-8") == text


def test_bounded_text_passthrough():
    result = bounded_text("short", 100)
    assert result["truncated"] is False
    assert result["full_output_path"] is None


def test_output_path_exists_without_overwrite(tmp_path):
    target = tmp_path / "out" / "a.docx"
    target.parent.mkdir(parents=True)
    target.write_bytes(b"x")
    with pytest.raises(OfficeError) as excinfo:
        paths.resolve_output_path(
            str(target),
            args={},
            field="output_path",
            default_dir=tmp_path,
            default_name="a.docx",
            overwrite=False,
        )
    assert excinfo.value.code == "E_PATH_EXISTS"
