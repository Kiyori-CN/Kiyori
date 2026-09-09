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

    from kiyori_office.paths import task_dir
    task = task_dir("demo")
    cleaned = protocol.run("office_workspace_clean", {"task_id": "demo", "scope": "task"})
    assert cleaned["ok"] is True, cleaned
    assert cleaned["data"]["dry_run"] and task.exists()
    confirmed = protocol.run("office_workspace_clean", {"task_id": "demo", "scope": "task",
        "confirm": True, "plan_token": cleaned["data"]["plan_token"]})
    assert confirmed["ok"] and not task.exists()


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


def test_libreoffice_detection_requires_component_libraries(tmp_path, monkeypatch):
    """只装 libreoffice-writer 时 soffice 存在，但 tier3 不能报 complete。

    真机复测中 `office_env_check` 曾把「仅 Writer」误报为 tier3 complete=true，
    随后 xlsx_recalc 与 xlsx/pptx 预览全部失败在引擎层。
    """

    from kiyori_office import env

    program = tmp_path / "libreoffice" / "program"
    program.mkdir(parents=True)
    (program / "libswlo.so").write_bytes(b"")
    fake_soffice = program / "soffice"
    fake_soffice.write_bytes(b"")
    monkeypatch.setattr(env.shutil, "which", lambda name: str(fake_soffice) if name == "soffice" else None)

    state = env.detect_libreoffice()
    assert state["available"] is True
    assert state["components"] == {"writer": True, "calc": False, "impress": False}
    assert state["missing_components"] == ["calc", "impress"]


def test_env_check_marks_incomplete_libreoffice(tmp_path, monkeypatch):
    from kiyori_office import env

    program = tmp_path / "libreoffice" / "program"
    program.mkdir(parents=True)
    (program / "libswlo.so").write_bytes(b"")
    fake_soffice = program / "soffice"
    fake_soffice.write_bytes(b"")
    monkeypatch.setattr(env.shutil, "which", lambda name: str(fake_soffice) if name == "soffice" else None)

    tier3 = env.detect(verbose=False)["tiers"]["tier3"]
    assert tier3["complete"] is False
    assert tier3["components"]["libreoffice"]["components"]["calc"] is False


def test_require_libreoffice_reports_missing_component(tmp_path, monkeypatch):
    from kiyori_office import env
    from kiyori_office.protocol import OfficeError

    program = tmp_path / "libreoffice" / "program"
    program.mkdir(parents=True)
    (program / "libswlo.so").write_bytes(b"")
    fake_soffice = program / "soffice"
    fake_soffice.write_bytes(b"")
    monkeypatch.setattr(env.shutil, "which", lambda name: str(fake_soffice) if name == "soffice" else None)

    with pytest.raises(OfficeError) as excinfo:
        env.require_libreoffice(purpose="xlsx 公式重算", component="calc")
    assert excinfo.value.code == "E_ENV_MISSING"
    assert "calc" in str(excinfo.value.message)
    assert excinfo.value.remedy


def test_require_libreoffice_for_source_maps_suffix(tmp_path, monkeypatch):
    from kiyori_office import env
    from kiyori_office.protocol import OfficeError

    program = tmp_path / "libreoffice" / "program"
    program.mkdir(parents=True)
    (program / "libswlo.so").write_bytes(b"")
    fake_soffice = program / "soffice"
    fake_soffice.write_bytes(b"")
    monkeypatch.setattr(env.shutil, "which", lambda name: str(fake_soffice) if name == "soffice" else None)

    # writer 可用
    assert env.require_libreoffice_for_source(".docx", purpose="preview")["available"] is True
    # calc/impress 不可用，必须显式失败而不是落到 E_ENGINE_FAILED
    for suffix in (".xlsx", ".pptx"):
        with pytest.raises(OfficeError) as excinfo:
            env.require_libreoffice_for_source(suffix, purpose="preview")
        assert excinfo.value.code == "E_ENV_MISSING"


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


def test_pandoc_whitelist_matches_dotless_to_format():
    """真机报告：pandoc 目标格式 md/markdown/html/txt/pdf 全被拒。

    根因是白名单存的是带点后缀（".md"），而 to_format 已 lstrip(".")，
    两边永远不相等，pandoc 路线实际不可用。
    """

    from kiyori_office import convert

    assert ".md" in convert.PANDOC_INPUTS
    for fmt in ("md", "markdown", "html", "txt", "pdf", "docx", "rst", "epub"):
        assert fmt in convert.PANDOC_OUTPUTS, fmt
        # 产物后缀必须可用于文件名，不能写成 document.plain
        assert not convert.PANDOC_OUTPUT_EXTENSIONS[fmt].startswith(".")


def test_office_render_preview_requires_poppler_data(tmp_path, monkeypatch):
    """缺 poppler-data 时 pdftoppm 退出码为 0 但产出空白图，必须前置拦截。"""

    from kiyori_office import env

    monkeypatch.setattr(env, "detect_poppler_data", lambda: {"available": False, "files": [], "detail": "missing"})
    source = tmp_path / "a.pdf"
    try:
        from reportlab.pdfgen import canvas
    except Exception:
        pytest.skip("reportlab 不可用")
    pdf = canvas.Canvas(str(source))
    pdf.drawString(72, 720, "hi")
    pdf.showPage()
    pdf.save()

    result = protocol.run("office_render_preview", {"path": str(source)})
    assert result["ok"] is False
    assert result["error"]["code"] == "E_ENV_MISSING"
    assert "poppler-data" in result["error"]["message"]
    assert result["error"]["remedy"]
