"""office_read / office_validate / office_diff / 工作区命令。"""

from __future__ import annotations

from pathlib import Path

import pytest

from kiyori_office import protocol


def test_office_read_text_and_truncation(tmp_path):
    path = tmp_path / "note.txt"
    path.write_text("中文内容 " * 50, encoding="utf-8")
    result = protocol.run("office_read", {"path": str(path), "max_chars": 20})
    assert result["ok"] is True, result
    assert result["truncated"] is True
    assert Path(result["full_output_path"]).is_file()


def test_office_read_unsupported_format(tmp_path):
    path = tmp_path / "binary.bin"
    path.write_bytes(b"\x00\x01")
    result = protocol.run("office_read", {"path": str(path)})
    assert result["ok"] is False
    assert result["error"]["code"] == "E_FORMAT_UNSUPPORTED"


def test_office_read_docx_outline_mode(tmp_path):
    docx = pytest.importorskip("docx")
    path = tmp_path / "outline.docx"
    document = docx.Document()
    document.add_heading("一级标题", level=1)
    document.add_paragraph("正文")
    document.save(str(path))
    result = protocol.run("office_read", {"path": str(path), "mode": "outline"})
    assert result["ok"] is True, result
    assert result["data"]["navigation"][0]["text"] == "一级标题"


def test_office_diff(tmp_path):
    left = tmp_path / "left.txt"
    right = tmp_path / "right.txt"
    left.write_text("line1\nline2\n", encoding="utf-8")
    right.write_text("line1\nline3\n", encoding="utf-8")
    result = protocol.run("office_diff", {"left": str(left), "right": str(right)})
    assert result["ok"] is True, result
    assert result["data"]["identical"] is False
    assert "-line2" in result["data"]["diff"]


def test_workspace_init_and_clean(tmp_path):
    target = tmp_path / "workspace"
    result = protocol.run("office_workspace_init", {"dir": str(target)})
    assert result["ok"] is True, result
    for name in ("source", "output", "templates", "assets"):
        assert (target / name).is_dir()
    assert (target / "AGENTS.md").is_file()

    cleaned = protocol.run("office_workspace_clean", {"task_id": "demo"})
    assert cleaned["ok"] is True


def test_office_validate_docx_detects_toc_and_revision(tmp_path):
    docx = pytest.importorskip("docx")
    path = tmp_path / "toc.docx"
    document = docx.Document()
    document.add_paragraph("正文")
    document.save(str(path))
    result = protocol.run("office_validate", {"path": str(path)})
    assert result["ok"] is True, result
    assert result["data"]["format"] == "docx"
    assert "issue_count" in result["data"]


def test_office_validate_strict_raises_on_xlsx_missing_cache(tmp_path):
    openpyxl = pytest.importorskip("openpyxl")
    path = tmp_path / "nocache.xlsx"
    workbook = openpyxl.Workbook()
    workbook.active["A1"] = "=1+1"
    workbook.save(str(path))
    loose = protocol.run("office_validate", {"path": str(path)})
    assert loose["ok"] is True, loose
    assert loose["data"]["issue_count"] >= 1
    strict = protocol.run("office_validate", {"path": str(path), "strict": True})
    assert strict["ok"] is False
    assert strict["error"]["code"] == "E_VALIDATION_FAILED"


def test_office_env_check_reports_tiers():
    result = protocol.run("office_env_check", {"verbose": False})
    assert result["ok"] is True, result
    tiers = result["data"]["tiers"]
    assert set(tiers) == {"tier1", "tier2", "tier3", "tier4"}
    assert "fonts" in result["data"]


def test_office_env_setup_returns_plan_without_executing():
    result = protocol.run("office_env_setup", {"tier": 1, "confirm": True})
    assert result["ok"] is True, result
    assert result["data"]["executed"] is False
    assert result["data"]["execution"] == "visible_pty"
    assert result["data"]["commands"]


def test_office_convert_requires_explicit_engine(tmp_path):
    path = tmp_path / "a.md"
    path.write_text("# t", encoding="utf-8")
    result = protocol.run("office_convert", {"from_path": str(path), "to_format": "docx"})
    assert result["ok"] is False
    assert result["error"]["code"] == "E_INPUT_SCHEMA"
