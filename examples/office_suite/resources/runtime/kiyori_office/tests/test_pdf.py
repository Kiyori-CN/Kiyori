"""PDF 信息、提取、页级操作与校验。"""

from __future__ import annotations


def test_tables_default_range_reads_every_page(tmp_path):
    import pytest
    pytest.importorskip('pdfplumber')
    from reportlab.pdfgen.canvas import Canvas
    from kiyori_office import protocol
    path = tmp_path / 'tables.pdf'
    canvas = Canvas(str(path))
    for page in range(2):
        for x in (50, 150, 250):
            canvas.line(x, 650, x, 750)
        for y in (650, 700, 750):
            canvas.line(50, y, 250, y)
        canvas.drawString(60, 720, 'Header')
        canvas.drawString(160, 670, str(page + 1))
        canvas.showPage()
    canvas.showPage()  # 无表格页也属于默认扫描范围。
    canvas.save()
    all_pages = protocol.run('pdf_extract', {'path': str(path), 'mode': 'tables'})
    assert all_pages['ok'], all_pages
    assert all_pages['data']['pages'] == [1, 2, 3]
    assert all_pages['data']['table_count'] == 2
    selected = protocol.run('pdf_extract', {'path': str(path), 'mode': 'tables', 'range': '2'})
    assert selected['ok'], selected
    assert selected['data']['pages'] == [2] and selected['data']['table_count'] == 1
    empty = protocol.run('pdf_extract', {'path': str(path), 'mode': 'tables', 'range': '3'})
    assert empty['ok'] and empty['data']['tables'] == []

from pathlib import Path

import pytest

from kiyori_office import protocol

pytest.importorskip("pypdf")


def test_acroform_fill_roundtrip_and_unknown_field_protection(tmp_path):
    from reportlab.pdfgen.canvas import Canvas
    from pypdf import PdfReader
    source, output = tmp_path / 'form.pdf', tmp_path / 'filled.pdf'
    canvas = Canvas(str(source))
    canvas.drawString(40, 760, 'Office form fixture')
    canvas.acroForm.textfield(name='author', x=40, y=700, width=240, height=24)
    canvas.acroForm.checkbox(name='reviewed', x=40, y=650, checked=False)
    canvas.showPage()
    canvas.save()
    listed = protocol.run('pdf_form_list', {'path': str(source)})
    assert listed['ok'] and listed['data']['field_count'] == 2
    result = protocol.run('pdf_form_fill', {'path': str(source), 'output_path': str(output),
                                           'values': {'author': 'Kiyori test', 'reviewed': '/Yes'}})
    assert result['ok'], result
    fields = PdfReader(output).get_fields()
    assert fields['author']['/V'] == 'Kiyori test'
    assert fields['reviewed']['/V'] == '/Yes'
    assert PdfReader(source).get_fields()['author'].get('/V', '') == ''
    original = output.read_bytes()
    rejected = protocol.run('pdf_form_fill', {'path': str(output), 'output_path': str(output),
                                             'in_place': True, 'values': {'missing': 'x'}})
    assert not rejected['ok'] and rejected['error']['code'] == 'E_ANCHOR_NOT_FOUND'
    assert output.read_bytes() == original


def _make_pdf(path, pages=2, text="Hello Kiyori"):
    from reportlab.pdfgen import canvas

    pdf_canvas = canvas.Canvas(str(path))
    for index in range(pages):
        pdf_canvas.drawString(72, 720, "%s %d" % (text, index + 1))
        pdf_canvas.showPage()
    pdf_canvas.save()
    return path


def test_pdf_info_and_extract(tmp_path):
    path = _make_pdf(tmp_path / "a.pdf", pages=3)
    info = protocol.run("pdf_info", {"path": str(path)})
    assert info["ok"] is True, info
    assert info["data"]["pages"] == 3
    assert info["data"]["has_text_layer"] is True

    extracted = protocol.run("pdf_extract", {"path": str(path), "range": "2-3"})
    assert extracted["ok"] is True, extracted
    assert extracted["data"]["pages"] == [1, 2]
    assert "Hello Kiyori 2" in extracted["data"]["text"]


def test_pdf_page_range_out_of_bounds(tmp_path):
    path = _make_pdf(tmp_path / "b.pdf", pages=1)
    result = protocol.run("pdf_extract", {"path": str(path), "range": "5"})
    assert result["ok"] is False
    assert result["error"]["code"] == "E_INPUT_SCHEMA"


def test_pdf_create_supports_bullet_blocks(tmp_path):
    """真机报告：blocks type=bullet 报「不支持的 blocks 类型」。"""

    result = protocol.run(
        "pdf_create",
        {
            "engine": "reportlab",
            "blocks": [
                {"type": "heading", "text": "标题"},
                {"type": "bullet", "items": ["第一条", "第二条"]},
                {"type": "paragraph", "text": "正文"},
            ],
        },
    )
    assert result["ok"] is True, result
    assert result["artifacts"][0]["bytes"] > 0


def test_pdf_create_detects_cjk_in_bullet_items(tmp_path):
    """只有 bullet 列表的中文文档也必须走 CJK 字体，否则渲染成方框。"""

    result = protocol.run(
        "pdf_create",
        {
            "engine": "reportlab",
            "blocks": [{"type": "bullet", "items": ["中文条目一", "中文条目二"]}],
        },
    )
    assert result["ok"] is True, result
    assert result["data"]["font"] != "Helvetica"


def test_pdf_split_honours_output_path(tmp_path):
    path = _make_pdf(tmp_path / "c.pdf", pages=2)
    target = tmp_path / "custom-split"
    result = protocol.run(
        "pdf_split",
        {
            "path": str(path),
            "range": "1-2",
            "output_path": str(target),
            "allow_roots": [str(tmp_path)],
        },
    )
    assert result["ok"] is True, result
    assert result["data"]["target_dir"] == str(target)
    assert all(Path(item["path"]).parent == target for item in result["artifacts"])


def test_pdf_merge_and_split(tmp_path):
    first = _make_pdf(tmp_path / "one.pdf", pages=1, text="first")
    second = _make_pdf(tmp_path / "two.pdf", pages=1, text="second")
    merged = tmp_path / "merged.pdf"
    result = protocol.run(
        "pdf_merge", {"paths": [str(first), str(second)], "output_path": str(merged)}
    )
    assert result["ok"] is True, result
    assert result["data"]["pages"] == 2

    result = protocol.run("pdf_split", {"path": str(merged), "range": "2"})
    assert result["ok"] is True, result
    assert len(result["artifacts"]) == 1


def test_pdf_reorder_validates_permutation(tmp_path):
    path = _make_pdf(tmp_path / "order.pdf", pages=2)
    bad = protocol.run("pdf_reorder", {"path": str(path), "order": [1, 1]})
    assert bad["ok"] is False
    assert bad["error"]["code"] == "E_INPUT_SCHEMA"
    output = tmp_path / "reordered.pdf"
    good = protocol.run(
        "pdf_reorder", {"path": str(path), "order": [2, 1], "output_path": str(output)}
    )
    assert good["ok"] is True, good


def test_pdf_delete_pages_refuses_to_remove_all(tmp_path):
    path = _make_pdf(tmp_path / "del.pdf", pages=2)
    result = protocol.run("pdf_delete_pages", {"path": str(path), "range": "1-2"})
    assert result["ok"] is False
    assert result["error"]["code"] == "E_INPUT_SCHEMA"


def test_pdf_encrypt_and_decrypt_roundtrip(tmp_path):
    path = _make_pdf(tmp_path / "plain.pdf", pages=1)
    encrypted = tmp_path / "encrypted.pdf"
    result = protocol.run(
        "pdf_encrypt",
        {"path": str(path), "password": "secret", "output_path": str(encrypted)},
    )
    assert result["ok"] is True, result

    wrong = protocol.run(
        "pdf_decrypt",
        {"path": str(encrypted), "password": "nope", "output_path": str(tmp_path / "x.pdf")},
    )
    assert wrong["ok"] is False
    assert wrong["error"]["code"] == "E_DOC_CORRUPT"

    decrypted = tmp_path / "decrypted.pdf"
    good = protocol.run(
        "pdf_decrypt",
        {"path": str(encrypted), "password": "secret", "output_path": str(decrypted)},
    )
    assert good["ok"] is True, good


def test_pdf_create_requires_explicit_engine(tmp_path):
    result = protocol.run("pdf_create", {"blocks": [{"type": "paragraph", "text": "x"}]})
    assert result["ok"] is False
    assert result["error"]["code"] == "E_INPUT_SCHEMA"


def test_pdf_create_reportlab_with_cjk(tmp_path):
    output = tmp_path / "cjk.pdf"
    result = protocol.run(
        "pdf_create",
        {
            "engine": "reportlab",
            "blocks": [
                {"type": "heading", "text": "季度报告"},
                {"type": "paragraph", "text": "中文内容"},
            ],
            "output_path": str(output),
        },
    )
    assert result["ok"] is True, result
    assert result["data"]["pages"] >= 1


def test_pdf_validate_reports_structure(tmp_path):
    path = _make_pdf(tmp_path / "valid.pdf", pages=1)
    result = protocol.run("office_validate", {"path": str(path)})
    assert result["ok"] is True, result
    assert result["data"]["summary"]["pages"] == 1


def test_corrupt_pdf_reports_format_error(tmp_path):
    path = tmp_path / "broken.pdf"
    path.write_bytes(b"not a pdf")
    result = protocol.run("pdf_info", {"path": str(path)})
    assert result["ok"] is False
    assert result["error"]["code"] == "E_FORMAT_UNSUPPORTED"
