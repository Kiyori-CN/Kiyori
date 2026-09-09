"""DOCX 结构读取与跨 run 编辑。"""

from __future__ import annotations

import zipfile
from pathlib import Path

import pytest

from kiyori_office import protocol

docx = pytest.importorskip("docx")

W_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"


def _make_docx(path: Path, paragraphs):
    document = docx.Document()
    for item in paragraphs:
        document.add_paragraph(item)
    document.save(str(path))
    return path


def test_docx_outline_merges_text_across_runs(tmp_path):
    path = tmp_path / "runs.docx"
    document = docx.Document()
    paragraph = document.add_paragraph()
    for piece in ("季度", "营收", "同比", "增长"):
        paragraph.add_run(piece)
    document.save(str(path))

    result = protocol.run("docx_outline", {"path": str(path)})
    assert result["ok"] is True
    texts = [item["text"] for item in result["data"]["paragraphs"]]
    assert "季度营收同比增长" in texts


def test_docx_find_replace_across_runs_preserves_other_text(tmp_path):
    path = tmp_path / "find.docx"
    document = docx.Document()
    paragraph = document.add_paragraph()
    for piece in ("季度", "营收", "同比", "增长"):
        paragraph.add_run(piece)
    document.add_paragraph("未命中的段落")
    document.save(str(path))

    output = tmp_path / "replaced.docx"
    result = protocol.run(
        "docx_find_replace",
        {
            "path": str(path),
            "find": "营收同比",
            "replace": "利润环比",
            "output_path": str(output),
        },
    )
    assert result["ok"] is True, result
    assert result["data"]["replacements"] == 1
    assert result["data"]["runs_merged"] >= 1
    merged = docx.Document(str(output))
    assert merged.paragraphs[0].text == "季度利润环比增长"
    assert merged.paragraphs[1].text == "未命中的段落"


def test_docx_find_replace_miss_reports_anchor_not_found(tmp_path):
    path = _make_docx(tmp_path / "miss.docx", ["只有一句话"])
    result = protocol.run(
        "docx_find_replace",
        {"path": str(path), "find": "不存在的词", "replace": "x"},
    )
    assert result["ok"] is False
    assert result["error"]["code"] == "E_ANCHOR_NOT_FOUND"


def test_docx_find_replace_rejects_ambiguous_anchor(tmp_path):
    path = _make_docx(tmp_path / "ambiguous.docx", ["目标行", "目标行"])
    result = protocol.run(
        "docx_edit",
        {"path": str(path), "anchor": {"text": "目标行"}, "operation": "replace", "text": "新"},
    )
    assert result["ok"] is False
    assert result["error"]["code"] == "E_ANCHOR_NOT_FOUND"


def test_docx_edit_by_index(tmp_path):
    path = _make_docx(tmp_path / "edit.docx", ["第一段", "第二段"])
    output = tmp_path / "edited.docx"
    result = protocol.run(
        "docx_edit",
        {
            "path": str(path),
            "anchor": {"index": 1},
            "operation": "replace",
            "text": "改过的第二段",
            "output_path": str(output),
        },
    )
    assert result["ok"] is True, result
    assert docx.Document(str(output)).paragraphs[1].text == "改过的第二段"


def test_docx_create_from_spec_and_markdown(tmp_path):
    spec_output = tmp_path / "spec.docx"
    result = protocol.run(
        "docx_create",
        {
            "spec": {
                "blocks": [
                    {"type": "title", "text": "季度报告"},
                    {"type": "heading", "level": 1, "text": "营收"},
                    {"type": "paragraph", "text": "同比增长 20%"},
                    {"type": "table", "rows": [["季度", "营收"], ["Q3", "120"]]},
                ]
            },
            "output_path": str(spec_output),
        },
    )
    assert result["ok"] is True, result
    assert result["data"]["paragraph_count"] >= 3
    assert result["data"]["table_count"] == 1

    markdown_output = tmp_path / "md.docx"
    markdown = "# 标题\n\n- 项目一\n- 项目二\n\n| A | B |\n| --- | --- |\n| 1 | 2 |\n"
    result = protocol.run(
        "docx_create", {"markdown": markdown, "output_path": str(markdown_output)}
    )
    assert result["ok"] is True, result
    assert result["data"]["table_count"] == 1


def test_docx_from_template_fills_and_reports_missing(tmp_path):
    path = _make_docx(tmp_path / "template.docx", ["客户：{{customer}}", "金额：{{amount}}"])
    output = tmp_path / "filled.docx"
    result = protocol.run(
        "docx_from_template",
        {
            "path": str(path),
            "variables": {"customer": "宇树科技", "amount": "100"},
            "output_path": str(output),
        },
    )
    assert result["ok"] is True, result
    assert docx.Document(str(output)).paragraphs[0].text == "客户：宇树科技"

    strict = protocol.run(
        "docx_from_template",
        {"path": str(path), "variables": {"customer": "甲"}, "output_path": str(tmp_path / "b.docx")},
    )
    assert strict["ok"] is False
    assert strict["error"]["code"] == "E_ANCHOR_NOT_FOUND"


def test_docx_merge_keeps_all_paragraphs(tmp_path):
    first = _make_docx(tmp_path / "a.docx", ["A1", "A2"])
    second = _make_docx(tmp_path / "b.docx", ["B1"])
    output = tmp_path / "merged.docx"
    result = protocol.run(
        "docx_merge", {"paths": [str(first), str(second)], "output_path": str(output)}
    )
    assert result["ok"] is True, result
    texts = [p.text for p in docx.Document(str(output)).paragraphs]
    assert texts[:3] == ["A1", "A2", "B1"]


def test_docx_extract_media(tmp_path):
    from PIL import Image

    image = tmp_path / "pixel.png"
    Image.new("RGB", (4, 4), (255, 0, 0)).save(image)
    path = tmp_path / "with-image.docx"
    document = docx.Document()
    document.add_picture(str(image))
    document.save(str(path))
    result = protocol.run("docx_extract_media", {"path": str(path)})
    assert result["ok"] is True, result
    assert result["data"]["count"] == 1


def test_corrupt_docx_reports_doc_corrupt(tmp_path):
    path = tmp_path / "broken.docx"
    path.write_bytes(b"not a zip")
    result = protocol.run("docx_outline", {"path": str(path)})
    assert result["ok"] is False
    assert result["error"]["code"] == "E_DOC_CORRUPT"
