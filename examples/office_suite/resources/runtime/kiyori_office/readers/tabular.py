"""纯文本类输入（csv/tsv/md/html/txt）读取。"""

from __future__ import annotations

import csv
import io
from pathlib import Path
from typing import Any, Dict, List

from ..protocol import OfficeError

TEXT_SUFFIXES = {".txt", ".md", ".markdown", ".csv", ".tsv", ".json", ".html", ".htm"}


def read_text(path: Path) -> str:
    try:
        return path.read_text(encoding="utf-8-sig")
    except UnicodeDecodeError:
        return path.read_text(encoding="utf-8", errors="replace")


def read_tabular(path: Path, *, max_rows: int = 500) -> Dict[str, Any]:
    suffix = path.suffix.lower()
    if suffix not in (".csv", ".tsv"):
        raise OfficeError(
            "E_FORMAT_UNSUPPORTED",
            "read_tabular 只支持 .csv/.tsv",
            detail="suffix=%s" % suffix,
        )
    delimiter = "\t" if suffix == ".tsv" else ","
    text = read_text(path)
    try:
        reader = csv.reader(io.StringIO(text), delimiter=delimiter)
        rows: List[List[str]] = []
        for index, row in enumerate(reader):
            if index >= max_rows:
                break
            rows.append(row)
    except csv.Error as exc:
        raise OfficeError("E_DOC_CORRUPT", "CSV 解析失败", detail=str(exc)) from exc
    total_rows = 0
    for _ in csv.reader(io.StringIO(text), delimiter=delimiter):
        total_rows += 1
    header = rows[0] if rows else []
    return {
        "delimiter": delimiter,
        "header": header,
        "rows": rows,
        "row_count": len(rows),
        "total_rows": total_rows,
        "truncated": total_rows > len(rows),
        "columns": len(header),
    }


def html_to_text(raw: str) -> str:
    """极简 HTML 去标签：只做读取归一化，不做排版。"""

    import re

    without_script = re.sub(r"(?is)<(script|style)[^>]*>.*?</\1>", " ", raw)
    with_breaks = re.sub(r"(?i)<br\s*/?>", "\n", without_script)
    with_blocks = re.sub(r"(?i)</(p|div|li|tr|h[1-6])>", "\n", with_breaks)
    stripped = re.sub(r"(?s)<[^>]+>", "", with_blocks)
    import html as html_module

    return html_module.unescape(stripped)
