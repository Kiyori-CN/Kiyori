"""PDF 读取。

优先使用 pypdf 的纯 Python 文本层；pdfplumber 仅作为版面提取的可选增强，
缺失时明确报 E_ENV_MISSING，不做静默降级。
"""

from __future__ import annotations

from pathlib import Path
from typing import Any, Dict, List, Optional

from ..protocol import OfficeError

try:
    from pypdf import PdfReader  # type: ignore
    from pypdf.errors import PdfReadError  # type: ignore

    PYPDF_AVAILABLE = True
except Exception:  # pragma: no cover
    PYPDF_AVAILABLE = False


def require_pypdf() -> None:
    if not PYPDF_AVAILABLE:
        raise OfficeError(
            "E_ENV_MISSING",
            "PDF 处理需要 pypdf（Tier1）",
            detail="pypdf is not importable",
            remedy="调用 office_env_setup 安装 Tier1 组件，或先安装 pypdf",
        )


def _open(path: Path) -> "PdfReader":
    require_pypdf()
    with path.open("rb") as header:
        is_pdf = header.read(5) == b"%PDF-"
    if not is_pdf:
        raise OfficeError(
            "E_FORMAT_UNSUPPORTED",
            "文件不是 PDF（缺少 %PDF- 头）",
            detail=str(path),
        )
    try:
        reader = PdfReader(str(path))
    except PdfReadError as exc:
        raise OfficeError("E_DOC_CORRUPT", "PDF 解析失败", detail=str(exc)) from exc
    except Exception as exc:
        raise OfficeError(
            "E_DOC_CORRUPT",
            "PDF 打开失败",
            detail="%s: %s" % (type(exc).__name__, exc),
        ) from exc
    if reader.is_encrypted:
        try:
            opened = reader.decrypt("")
        except Exception:
            opened = 0
        if not opened:
            raise OfficeError(
                "E_DOC_CORRUPT",
                "PDF 已加密，需要密码",
                remedy="先用 pdf_decrypt 提供密码，或让用户提供未加密副本",
            )
    return reader


def pdf_info(path: Path) -> Dict[str, Any]:
    reader = _open(path)
    metadata: Dict[str, Any] = {}
    try:
        for key, value in (reader.metadata or {}).items():
            metadata[str(key)] = str(value)
    except Exception:
        metadata = {}
    first_page_size = None
    if len(reader.pages) > 0:
        box = reader.pages[0].mediabox
        first_page_size = {"width": float(box.width), "height": float(box.height)}
    text_pages = 0
    for page in reader.pages[: min(len(reader.pages), 5)]:
        try:
            if (page.extract_text() or "").strip():
                text_pages += 1
        except Exception:
            continue
    form_fields: List[str] = []
    try:
        fields = reader.get_fields() or {}
        form_fields = sorted(str(name) for name in fields)
    except Exception:
        form_fields = []
    return {
        "pages": len(reader.pages),
        "metadata": metadata,
        "encrypted": bool(reader.is_encrypted),
        "first_page_size": first_page_size,
        "text_layer_sampled_pages": text_pages,
        "has_text_layer": text_pages > 0,
        "form_fields": form_fields,
        "form_field_count": len(form_fields),
        "bytes": path.stat().st_size,
    }


def pdf_extract_text(
    path: Path,
    *,
    pages: Optional[List[int]] = None,
    layout: bool = False,
) -> Dict[str, Any]:
    """提取文本。layout=True 需要 pdfplumber，缺失时报 E_ENV_MISSING。"""

    reader = _open(path)
    total = len(reader.pages)
    targets = _normalize_pages(pages, total)
    if layout:
        try:
            import pdfplumber  # type: ignore
        except Exception as exc:
            raise OfficeError(
                "E_ENV_MISSING",
                "保留版面提取需要 pdfplumber（Tier1）",
                detail=str(exc),
                remedy="调用 office_env_setup 安装 pdfplumber，或改用 layout=false",
            ) from exc
        with pdfplumber.open(str(path)) as pdf:
            chunks = []
            for index in targets:
                chunks.append(pdf.pages[index].extract_text(layout=True) or "")
        return {"pages": targets, "text": "\n\n".join(chunks), "page_count": total}

    chunks: List[Dict[str, Any]] = []
    for index in targets:
        try:
            text = reader.pages[index].extract_text() or ""
        except Exception as exc:
            raise OfficeError(
                "E_ENGINE_FAILED",
                "第 %d 页文本提取失败" % (index + 1),
                detail="%s: %s" % (type(exc).__name__, exc),
            ) from exc
        chunks.append({"page": index + 1, "text": text})
    return {
        "pages": targets,
        "page_count": total,
        "chunks": chunks,
        "text": "\n\n".join(chunk["text"] for chunk in chunks),
        "empty_pages": [chunk["page"] for chunk in chunks if not chunk["text"].strip()],
    }


def _normalize_pages(pages: Optional[List[int]], total: int) -> List[int]:
    if not pages:
        return list(range(total))
    normalized: List[int] = []
    for value in pages:
        if not isinstance(value, int) or isinstance(value, bool):
            raise OfficeError("E_INPUT_SCHEMA", "pages 必须是整数数组", detail=repr(value))
        if value < 1 or value > total:
            raise OfficeError(
                "E_INPUT_SCHEMA",
                "页码越界: %d（共 %d 页）" % (value, total),
                remedy="先用 pdf_info 确认页数，再按 1-based 页码请求",
            )
        if value - 1 not in normalized:
            normalized.append(value - 1)
    return normalized


def parse_page_range(text: Optional[str], total: int) -> Optional[List[int]]:
    """解析 ``1-3,7`` 形式的页范围，返回 1-based 页码列表。"""

    if not text:
        return None
    pages: List[int] = []
    for chunk in str(text).split(","):
        piece = chunk.strip()
        if not piece:
            continue
        if "-" in piece:
            start_text, _, end_text = piece.partition("-")
            try:
                start, end = int(start_text), int(end_text)
            except ValueError as exc:
                raise OfficeError(
                    "E_INPUT_SCHEMA", "页范围格式非法: %s" % piece, detail=str(exc)
                ) from exc
            if start < 1 or end < start:
                raise OfficeError("E_INPUT_SCHEMA", "页范围非法: %s" % piece)
            pages.extend(range(start, end + 1))
        else:
            try:
                pages.append(int(piece))
            except ValueError as exc:
                raise OfficeError(
                    "E_INPUT_SCHEMA", "页码格式非法: %s" % piece, detail=str(exc)
                ) from exc
    return _normalize_pages_1based(pages, total)


def _normalize_pages_1based(pages: List[int], total: int) -> List[int]:
    result: List[int] = []
    for value in pages:
        if value < 1 or value > total:
            raise OfficeError(
                "E_INPUT_SCHEMA",
                "页码越界: %d（共 %d 页）" % (value, total),
            )
        if value not in result:
            result.append(value)
    return result
