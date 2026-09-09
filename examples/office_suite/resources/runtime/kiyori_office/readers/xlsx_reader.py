"""XLSX 读取。

读取公式与缓存值时必须分两次 ``load_workbook``：``data_only=True`` 的载入
只用于取缓存值，绝不能在保存路径上使用（见 ops_xlsx）。
"""

from __future__ import annotations

from pathlib import Path
from datetime import date, datetime, time
from typing import Any, Dict, List, Optional

from ..protocol import OfficeError

try:
    import openpyxl  # type: ignore
    from openpyxl.utils import get_column_letter  # type: ignore
    from openpyxl.utils.cell import range_boundaries

    OPENPYXL_AVAILABLE = True
except Exception:  # pragma: no cover
    OPENPYXL_AVAILABLE = False


def require_openpyxl() -> None:
    if not OPENPYXL_AVAILABLE:
        raise OfficeError(
            "E_ENV_MISSING",
            "XLSX 处理需要 openpyxl（Tier1）",
            detail="openpyxl is not importable",
            remedy="调用 office_env_setup 安装 Tier1 组件，或先安装 openpyxl",
        )


def _load(path: Path, *, data_only: bool = False, keep_vba: bool = False):
    try:
        return openpyxl.load_workbook(
            str(path), data_only=data_only, keep_vba=keep_vba, read_only=False
        )
    except Exception as exc:
        raise OfficeError(
            "E_DOC_CORRUPT",
            "XLSX 打开失败",
            detail="%s: %s" % (type(exc).__name__, exc),
            remedy="确认文件是未加密的 .xlsx/.xlsm；.xls 旧格式需先 office_convert",
        ) from exc


def is_xlsm(path: Path) -> bool:
    return path.suffix.lower() == ".xlsm"


def xlsx_info(path: Path) -> Dict[str, Any]:
    require_openpyxl()
    workbook = _load(path, keep_vba=is_xlsm(path))
    sheets: List[Dict[str, Any]] = []
    for sheet in workbook.worksheets:
        sheets.append(
            {
                "name": sheet.title,
                "index": workbook.worksheets.index(sheet),
                "max_row": sheet.max_row,
                "max_column": sheet.max_column,
                "dimensions": sheet.dimensions,
                "state": sheet.sheet_state,
            }
        )
    named_ranges = []
    try:
        for name, definition in workbook.defined_names.items():
            named_ranges.append({"name": name, "definition": str(definition.value)})
    except Exception:
        named_ranges = []
    external_links = [str(link) for link in getattr(workbook, "_external_links", [])]
    formula_count = 0
    for sheet in workbook.worksheets:
        # openpyxl 普通模式已经载入实际单元格；iter_rows 会把远端格式单元格之间的
        # 整个矩形物化，稀疏表可能因此分配数十亿个空 Cell。
        for cell in sheet._cells.values():
            if cell.data_type == "f":
                formula_count += 1
    workbook.close()
    return {
        "sheets": sheets,
        "sheet_count": len(sheets),
        "named_ranges": named_ranges,
        "external_links": external_links,
        "has_macros": is_xlsm(path),
        "formula_cells": formula_count,
    }


def _normalize_range(sheet, cell_range: Optional[str]) -> str:
    if not cell_range:
        return "A1:%s%d" % (get_column_letter(max(1, sheet.max_column)), max(1, sheet.max_row))
    return cell_range


def xlsx_read(
    path: Path,
    *,
    sheet_name: Optional[str] = None,
    cell_range: Optional[str] = None,
    max_rows: int = 500,
    with_formulas: bool = True,
) -> Dict[str, Any]:
    """按范围读，同时返回公式与缓存值。"""

    require_openpyxl()
    workbook = _load(path, keep_vba=is_xlsm(path))
    cached = _load(path, data_only=True, keep_vba=is_xlsm(path)) if with_formulas else None

    names = [sheet.title for sheet in workbook.worksheets]
    if sheet_name:
        if sheet_name not in names:
            raise OfficeError(
                "E_INPUT_SCHEMA",
                "工作表不存在: %s" % sheet_name,
                detail="available sheets: %s" % ", ".join(names),
            )
        targets = [workbook[sheet_name]]
    else:
        targets = list(workbook.worksheets)

    result_sheets: List[Dict[str, Any]] = []
    for sheet in targets:
        resolved_range = _normalize_range(sheet, cell_range)
        try:
            min_col, min_row, max_col, max_row = range_boundaries(resolved_range)
            min_col, min_row = min_col or 1, min_row or 1
            max_col, max_row = max_col or sheet.max_column, max_row or sheet.max_row
            if min_col > max_col or min_row > max_row or min_row < 1 or min_col < 1:
                raise ValueError("range is reversed or zero-based")
        except (ValueError, TypeError) as exc:
            raise OfficeError("E_INPUT_SCHEMA", "无效单元格范围", detail=resolved_range) from exc
        # 先限制迭代范围，不能先 sheet[range] 物化百万行再截断。
        last_row = min(max_row, min_row + max_rows - 1)
        if max_col > 16384 or max_row > 1048576 or (last_row - min_row + 1) * (max_col - min_col + 1) > 100000:
            raise OfficeError("E_BUDGET_EXCEEDED", "读取范围过大，请缩小 range 或 max_rows")
        rows: List[List[Dict[str, Any]]] = []
        for row in sheet.iter_rows(min_row=min_row, max_row=last_row, min_col=min_col, max_col=max_col):
            rendered: List[Dict[str, Any]] = []
            for cell in row:
                value = cell.value
                entry: Dict[str, Any] = {"ref": cell.coordinate, "value": _json_value(value)}
                if isinstance(value, str) and value.startswith("="):
                    entry["formula"] = value
                    if cached is not None:
                        cached_sheet = cached[sheet.title]
                        entry["cached_value"] = _json_value(cached_sheet[cell.coordinate].value)
                rendered.append(entry)
            rows.append(rendered)
        result_sheets.append(
            {
                "name": sheet.title,
                "range": resolved_range,
                "rows": rows,
                "row_count": len(rows),
                "truncated": max_row > last_row,
            }
        )
    workbook.close()
    if cached is not None:
        cached.close()
    return {"sheets": result_sheets, "sheet_names": names}


def _json_value(value):
    # Excel 日期单元格必须能通过 CLI JSON 信封，保留 ISO 日期/时间语义。
    return value.isoformat() if isinstance(value, (datetime, date, time)) else value
