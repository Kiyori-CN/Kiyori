"""PPTX 结构读取、sldIdLst 一致性与溢出检查。"""

from __future__ import annotations

import pytest

from kiyori_office import protocol

pptx = pytest.importorskip("pptx")


def _make_pptx(path, titles):
    from pptx import Presentation

    presentation = Presentation()
    layout = presentation.slide_layouts[1]
    for title in titles:
        slide = presentation.slides.add_slide(layout)
        slide.shapes.title.text = title
    presentation.save(str(path))
    return path


def test_pptx_outline(tmp_path):
    path = _make_pptx(tmp_path / "deck.pptx", ["第一页", "第二页"])
    result = protocol.run("pptx_outline", {"path": str(path)})
    assert result["ok"] is True, result
    assert result["data"]["slide_count"] == 2
    assert len(result["data"]["navigation"]) == 2


def test_pptx_create(tmp_path):
    output = tmp_path / "created.pptx"
    result = protocol.run(
        "pptx_create",
        {
            "slides": [
                {"title": "季度汇报", "bullets": ["营收增长", "成本下降"]},
                {"title": "下一步", "bullets": ["扩产"]},
            ],
            "output_path": str(output),
        },
    )
    assert result["ok"] is True, result
    assert result["data"]["slide_count"] == 2


def test_pptx_slide_duplicate_keeps_relations_and_sld_id_lst(tmp_path):
    path = _make_pptx(tmp_path / "dup.pptx", ["A", "B"])
    output = tmp_path / "dup-out.pptx"
    result = protocol.run(
        "pptx_slide",
        {"path": str(path), "operation": "duplicate", "index": 0, "output_path": str(output)},
    )
    assert result["ok"] is True, result
    assert result["data"]["slide_count"] == 3

    from pptx import Presentation

    presentation = Presentation(str(output))
    assert len(presentation.slides._sldIdLst) == 3  # noqa: SLF001
    assert len(presentation.slides) == 3


def test_pptx_slide_move_reorders(tmp_path):
    path = _make_pptx(tmp_path / "move.pptx", ["A", "B", "C"])
    output = tmp_path / "move-out.pptx"
    result = protocol.run(
        "pptx_slide",
        {
            "path": str(path),
            "operation": "move",
            "index": 2,
            "target_index": 0,
            "output_path": str(output),
        },
    )
    assert result["ok"] is True, result
    from pptx import Presentation

    titles = [
        slide.shapes.title.text for slide in Presentation(str(output)).slides
    ]
    assert titles == ["C", "A", "B"]


def test_pptx_template_fill(tmp_path):
    path = _make_pptx(tmp_path / "template.pptx", ["{{project}} 汇报"])
    output = tmp_path / "filled.pptx"
    result = protocol.run(
        "pptx_template_fill",
        {
            "path": str(path),
            "variables": {"project": "宇树科技"},
            "output_path": str(output),
        },
    )
    assert result["ok"] is True, result
    from pptx import Presentation

    assert Presentation(str(output)).slides[0].shapes.title.text == "宇树科技 汇报"


def test_pptx_clean_reports_slide_counts(tmp_path):
    path = _make_pptx(tmp_path / "clean.pptx", ["A"])
    output = tmp_path / "clean-out.pptx"
    result = protocol.run("pptx_clean", {"path": str(path), "output_path": str(output)})
    assert result["ok"] is True, result
    assert result["data"]["slides_before"] == result["data"]["slides_after"] == 1
