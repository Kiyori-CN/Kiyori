"""xlsx_write / xlsx_format / xlsx_sheet / xlsx_table / xlsx_export。"""

from __future__ import annotations

import re
from pathlib import Path
from typing import Any, Dict, List

from ..paths import artifact, resolve_output_path, resolve_path, resolve_task_id, task_dir
from ..protocol import OfficeError, engine_version, register
from ..readers.xlsx_reader import is_xlsm, require_openpyxl
from .formula import normalize_formula

def _open_workbook(source: Path):
    """打开可写工作簿。

    保存路径上禁止 ``data_only=True``：openpyxl 以缓存值载入后保存会永久丢掉
    全部公式。这里显式提供 ``data_only`` 形参，让调用方看见并触发拦截。
    """

    return open_workbook_for_write(source)


def open_workbook_for_write(source: Path, *, data_only: bool = False):
    if data_only:
        raise OfficeError(
            "E_INPUT_SCHEMA",
            "禁止用 data_only=True 载入后保存工作簿",
            detail="openpyxl data_only 载入只保留缓存值，保存会永久丢失全部公式",
            remedy="读取缓存值请用 xlsx_read；写入请用默认（data_only=False）载入",
        )
    import openpyxl  # type: ignore

    require_openpyxl()
    if is_xlsm(source):
        # .xlsm 必须 keep_vba=True，否则宏永久丢失
        return openpyxl.load_workbook(str(source), keep_vba=True)
    try:
        return openpyxl.load_workbook(str(source))
    except FileNotFoundError:
        return openpyxl.Workbook()
    except Exception as exc:
        raise OfficeError(
            "E_DOC_CORRUPT",
            "XLSX 打开失败",
            detail="%s: %s" % (type(exc).__name__, exc),
        ) from exc


def _prepare(args: Dict[str, Any]):
    task_id = resolve_task_id(args)
    directory = task_dir(task_id)
    raw_path = args.get("path")
    source = resolve_path(raw_path, args=args, field="path", must_exist=True) if raw_path else None
    default_name = source.name if source else str(args.get("file_name") or "workbook.xlsx")
    in_place = bool(args.get("in_place"))
    output = resolve_output_path(
        args.get("output_path") or (str(source) if in_place and source else None),
        args=args,
        field="output_path",
        default_dir=directory / "out",
        default_name=default_name,
        overwrite=bool(args.get("overwrite")),
        in_place=in_place,
    )
    if output.suffix.lower() not in (".xlsx", ".xlsm"):
        raise OfficeError(
            "E_INPUT_SCHEMA",
            "output_path 必须是 .xlsx 或 .xlsm",
            detail=str(output),
        )
    return task_id, directory, source, output


def _resolve_sheet(workbook, name):
    if not name:
        return workbook.active
    if name not in workbook.sheetnames:
        raise OfficeError(
            "E_INPUT_SCHEMA",
            "工作表不存在: %s" % name,
            detail="available=%s" % ", ".join(workbook.sheetnames),
        )
    return workbook[name]


@register(
    "xlsx_write",
    schema="xlsx_write",
    engine="openpyxl",
    next_actions=["xlsx_recalc", "xlsx_format", "office_validate"],
)
def xlsx_write(args: Dict[str, Any]) -> Dict[str, Any]:
    require_openpyxl()
    task_id, directory, source, output = _prepare(args)
    workbook = _open_workbook(source) if source else __import__("openpyxl").Workbook()
    sheet = _resolve_sheet(workbook, args.get("sheet_name"))
    if not sheet.title or sheet.title == "Sheet":
        if args.get("sheet_name"):
            sheet.title = str(args["sheet_name"])

    cells = args.get("cells")
    rows = args.get("rows")
    if cells is None and rows is None:
        raise OfficeError(
            "E_INPUT_SCHEMA",
            "xlsx_write 需要 cells 或 rows",
            remedy="cells=[{cell, value|formula}]，rows=[[...]]（可用 start_cell 指定起点）",
        )
    formula_count = 0
    written = 0
    if isinstance(cells, list):
        for index, item in enumerate(cells):
            if not isinstance(item, dict):
                raise OfficeError("E_INPUT_SCHEMA", "cells[%d] 必须是对象" % index)
            ref = item.get("cell")
            if not isinstance(ref, str) or not ref:
                raise OfficeError("E_INPUT_SCHEMA", "cells[%d].cell 必须是非空字符串" % index)
            if "formula" in item:
                sheet[ref] = normalize_formula(str(item["formula"]))
                formula_count += 1
            elif "value" in item:
                sheet[ref] = item["value"]
            else:
                raise OfficeError(
                    "E_INPUT_SCHEMA", "cells[%d] 需要 value 或 formula" % index
                )
            written += 1
    if isinstance(rows, list):
        start_cell = str(args.get("start_cell") or "A1")
        match = re.fullmatch(r"([A-Za-z]+)(\d+)", start_cell.strip())
        if not match:
            raise OfficeError("E_INPUT_SCHEMA", "start_cell 非法: %s" % start_cell)
        start_row = int(match.group(2))
        start_column = _column_index(start_cell)
        for row_offset, row in enumerate(rows):
            if not isinstance(row, list):
                raise OfficeError("E_INPUT_SCHEMA", "rows[%d] 必须是数组" % row_offset)
            for column_offset, value in enumerate(row):
                target = sheet.cell(
                    row=start_row + row_offset,
                    column=start_column + column_offset,
                )
                if isinstance(value, str) and value.startswith("="):
                    target.value = normalize_formula(value)
                    formula_count += 1
                else:
                    target.value = value
                written += 1

    output.parent.mkdir(parents=True, exist_ok=True)
    workbook.save(str(output))
    return {
        "artifacts": [artifact(output, role="in_place" if args.get("in_place") else "output")],
        "data": {
            "cells_written": written,
            "formula_cells": formula_count,
            "work_dir": str(directory),
            "sheet": sheet.title,
        },
        "engine_version": engine_version("openpyxl"),
        "next_actions": (
            ["xlsx_recalc", "office_validate"]
            if formula_count
            else ["xlsx_format", "office_validate"]
        ),
    }


def _column_index(ref: str) -> int:
    from openpyxl.utils import column_index_from_string  # type: ignore

    match = re.match(r"([A-Z]+)", ref.upper())
    if not match:
        raise OfficeError("E_INPUT_SCHEMA", "start_cell 非法: %s" % ref)
    return column_index_from_string(match.group(1))


@register(
    "xlsx_format",
    schema="xlsx_format",
    engine="openpyxl",
    next_actions=["xlsx_recalc", "office_validate"],
)
def xlsx_format(args: Dict[str, Any]) -> Dict[str, Any]:
    require_openpyxl()
    from openpyxl.styles import Alignment, Border, Font, PatternFill, Side  # type: ignore
    from openpyxl.utils import get_column_letter  # type: ignore

    task_id, directory, source, output = _prepare(args)
    if source is None:
        raise OfficeError("E_INPUT_SCHEMA", "xlsx_format 需要 path")
    workbook = _open_workbook(source)
    sheet = _resolve_sheet(workbook, args.get("sheet_name"))
    applied: List[str] = []

    number_format = args.get("number_format")
    if number_format:
        for row in sheet.iter_rows(min_row=1, max_row=max(1, sheet.max_row), max_col=max(1, sheet.max_column)):
            for cell in row:
                cell.number_format = str(number_format)
        applied.append("number_format")

    font = args.get("font")
    if isinstance(font, dict):
        style = Font(
            name=font.get("name"),
            size=font.get("size"),
            bold=font.get("bold"),
            italic=font.get("italic"),
            color=font.get("color"),
        )
        for row in sheet.iter_rows(min_row=1, max_row=max(1, sheet.max_row), max_col=max(1, sheet.max_column)):
            for cell in row:
                cell.font = style
        applied.append("font")

    fill = args.get("fill")
    if isinstance(fill, dict) and fill.get("color"):
        pattern = PatternFill(fill_type=fill.get("type") or "solid", fgColor=str(fill["color"]))
        for row in sheet.iter_rows(min_row=1, max_row=max(1, sheet.max_row), max_col=max(1, sheet.max_column)):
            for cell in row:
                cell.fill = pattern
        applied.append("fill")

    widths = args.get("column_widths")
    if isinstance(widths, dict):
        for key, value in widths.items():
            sheet.column_dimensions[str(key).upper()].width = float(value)
        applied.append("column_widths")

    heights = args.get("row_heights")
    if isinstance(heights, dict):
        for key, value in heights.items():
            sheet.row_dimensions[int(key)].height = float(value)
        applied.append("row_heights")

    freeze = args.get("freeze_panes")
    if freeze:
        sheet.freeze_panes = str(freeze)
        applied.append("freeze_panes")

    autofilter = args.get("auto_filter")
    if autofilter:
        sheet.auto_filter.ref = str(autofilter)
        applied.append("auto_filter")

    output.parent.mkdir(parents=True, exist_ok=True)
    workbook.save(str(output))
    return {
        "artifacts": [artifact(output, role="in_place" if args.get("in_place") else "output")],
        "data": {"applied": applied, "work_dir": str(directory)},
        "engine_version": engine_version("openpyxl"),
    }


@register(
    "xlsx_sheet",
    schema="xlsx_sheet",
    engine="openpyxl",
    next_actions=["xlsx_write", "office_validate"],
)
def xlsx_sheet(args: Dict[str, Any]) -> Dict[str, Any]:
    require_openpyxl()
    task_id, directory, source, output = _prepare(args)
    if source is None:
        raise OfficeError("E_INPUT_SCHEMA", "xlsx_sheet 需要 path")
    workbook = _open_workbook(source)
    operation = str(args.get("operation") or "")
    name = str(args.get("name") or "")
    if operation not in ("create", "delete", "rename", "move", "copy"):
        raise OfficeError(
            "E_INPUT_SCHEMA",
            "operation 必须是 create/delete/rename/move/copy",
        )
    if operation == "create":
        if not name:
            raise OfficeError("E_INPUT_SCHEMA", "create 需要 name")
        if name in workbook.sheetnames:
            raise OfficeError("E_INPUT_SCHEMA", "工作表已存在: %s" % name)
        workbook.create_sheet(name, index=args.get("index"))
    else:
        sheet = _resolve_sheet(workbook, name)
        if operation == "delete":
            if len(workbook.sheetnames) == 1:
                raise OfficeError("E_INPUT_SCHEMA", "不能删除唯一的工作表")
            workbook.remove(sheet)
        elif operation == "rename":
            new_name = str(args.get("new_name") or "")
            if not new_name:
                raise OfficeError("E_INPUT_SCHEMA", "rename 需要 new_name")
            sheet.title = new_name
        elif operation == "move":
            index = args.get("index")
            if not isinstance(index, int):
                raise OfficeError("E_INPUT_SCHEMA", "move 需要整数 index")
            current = workbook.sheetnames.index(sheet.title)
            workbook.move_sheet(sheet, offset=index - current)
        else:
            target_name = str(args.get("new_name") or ("%s-copy" % sheet.title))
            if target_name in workbook.sheetnames:
                raise OfficeError("E_INPUT_SCHEMA", "目标工作表已存在: %s" % target_name)
            workbook.copy_worksheet(sheet).title = target_name
    output.parent.mkdir(parents=True, exist_ok=True)
    workbook.save(str(output))
    return {
        "artifacts": [artifact(output, role="in_place" if args.get("in_place") else "output")],
        "data": {
            "operation": operation,
            "sheets": list(workbook.sheetnames),
            "work_dir": str(directory),
        },
        "engine_version": engine_version("openpyxl"),
    }


@register(
    "xlsx_table",
    schema="xlsx_table",
    engine="openpyxl",
    next_actions=["xlsx_recalc", "office_validate"],
)
def xlsx_table(args: Dict[str, Any]) -> Dict[str, Any]:
    require_openpyxl()
    from openpyxl.utils import get_column_letter  # type: ignore
    from openpyxl.worksheet.table import Table, TableStyleInfo  # type: ignore

    task_id, directory, source, output = _prepare(args)
    if source is None:
        raise OfficeError("E_INPUT_SCHEMA", "xlsx_table 需要 path")
    workbook = _open_workbook(source)
    sheet = _resolve_sheet(workbook, args.get("sheet_name"))
    ref = str(args.get("range") or "")
    if not ref:
        ref = "A1:%s%d" % (get_column_letter(sheet.max_column), sheet.max_row)
    name = str(args.get("table_name") or "Table1")
    table = Table(displayName=name, ref=ref)
    table.tableStyleInfo = TableStyleInfo(
        name=str(args.get("style") or "TableStyleMedium2"),
        showFirstColumn=False,
        showLastColumn=False,
        showRowStripes=True,
        showColumnStripes=False,
    )
    sheet.add_table(table)
    output.parent.mkdir(parents=True, exist_ok=True)
    workbook.save(str(output))
    return {
        "artifacts": [artifact(output, role="in_place" if args.get("in_place") else "output")],
        "data": {"table": name, "range": ref, "work_dir": str(directory)},
        "engine_version": engine_version("openpyxl"),
    }
