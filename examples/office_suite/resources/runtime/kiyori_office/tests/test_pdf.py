"""PDF 信息、提取、页级操作与校验。"""

from __future__ import annotations

import pytest

from kiyori_office import protocol

pytest.importorskip("pypdf")


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
