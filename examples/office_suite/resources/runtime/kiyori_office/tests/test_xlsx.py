"""XLSX 读取、公式规则与重算前置检查。"""

from __future__ import annotations

import pytest

from kiyori_office import protocol

openpyxl = pytest.importorskip("openpyxl")


def _make_xlsx(path, rows, sheet_name="数据"):
    workbook = openpyxl.Workbook()
    sheet = workbook.active
    sheet.title = sheet_name
    for row in rows:
        sheet.append(row)
    workbook.save(str(path))
    return path


def test_xlsx_info_and_unicode_sheet_name(tmp_path):
    path = _make_xlsx(tmp_path / "a.xlsx", [["a", "b"], [1, 2]])
    result = protocol.run("xlsx_info", {"path": str(path)})
    assert result["ok"] is True, result
    assert result["data"]["sheets"][0]["name"] == "数据"
    assert result["data"]["sheet_count"] == 1


def test_xlsx_write_formula_and_next_actions(tmp_path):
    path = _make_xlsx(tmp_path / "calc.xlsx", [["收入", "成本"], [100, 40]])
    output = tmp_path / "calc-out.xlsx"
    result = protocol.run(
        "xlsx_write",
        {
            "path": str(path),
            "cells": [{"cell": "C2", "formula": "=A2-B2"}],
            "output_path": str(output),
        },
    )
    assert result["ok"] is True, result
    assert result["data"]["formula_cells"] == 1
    assert "xlsx_recalc" in result["next_actions"]


def test_xlsx_write_adds_xlfn_prefix(tmp_path):
    output = tmp_path / "textjoin.xlsx"
    result = protocol.run(
        "xlsx_write",
        {
            "file_name": str(output),
            "output_path": str(output),
            "cells": [{"cell": "A1", "formula": '=TEXTJOIN(",",TRUE,B1:B3)'}],
        },
    )
    assert result["ok"] is True, result
    value = openpyxl.load_workbook(str(output)).active["A1"].value
    assert value.startswith("=_xlfn.TEXTJOIN")


@pytest.mark.parametrize("function", ["XLOOKUP", "FILTER", "SORT", "UNIQUE", "SEQUENCE"])
def test_xlsx_write_rejects_spill_functions(tmp_path, function):
    output = tmp_path / "spill.xlsx"
    result = protocol.run(
        "xlsx_write",
        {
            "output_path": str(output),
            "cells": [{"cell": "A1", "formula": "=%s(B1:B3)" % function}],
        },
    )
    assert result["ok"] is False
    assert result["error"]["code"] == "E_INPUT_SCHEMA"
    assert "溢出数组函数" in result["error"]["message"]


def test_data_only_save_path_is_blocked():
    from kiyori_office.ops_xlsx.write import open_workbook_for_write

    with pytest.raises(Exception) as excinfo:
        open_workbook_for_write(None, data_only=True)  # type: ignore[arg-type]
    assert "data_only" in str(excinfo.value)


def test_xlsx_read_returns_formula_and_cached_value(tmp_path):
    path = tmp_path / "formula.xlsx"
    workbook = openpyxl.Workbook()
    sheet = workbook.active
    sheet["A1"] = 1
    sheet["A2"] = 2
    sheet["A3"] = "=A1+A2"
    workbook.save(str(path))
    result = protocol.run("xlsx_read", {"path": str(path), "range": "A3:A3"})
    assert result["ok"] is True, result
    cell = result["data"]["sheets"][0]["rows"][0][0]
    assert cell["formula"] == "=A1+A2"
    assert cell["cached_value"] is None


def test_xlsx_sheet_operations(tmp_path):
    path = _make_xlsx(tmp_path / "sheets.xlsx", [["a"]])
    output = tmp_path / "sheets-out.xlsx"
    result = protocol.run(
        "xlsx_sheet",
        {"path": str(path), "operation": "create", "name": "汇总", "output_path": str(output)},
    )
    assert result["ok"] is True, result
    assert "汇总" in result["data"]["sheets"]

    renamed = tmp_path / "renamed.xlsx"
    result = protocol.run(
        "xlsx_sheet",
        {
            "path": str(output),
            "operation": "rename",
            "name": "汇总",
            "new_name": "总计",
            "output_path": str(renamed),
        },
    )
    assert result["ok"] is True, result
    assert "总计" in result["data"]["sheets"]


def test_xlsx_recalc_requires_libreoffice(tmp_path, monkeypatch):
    monkeypatch.setattr("shutil.which", lambda name: None)
    path = _make_xlsx(tmp_path / "recalc.xlsx", [["a"], [1]])
    result = protocol.run("xlsx_recalc", {"path": str(path)})
    assert result["ok"] is False
    assert result["error"]["code"] == "E_ENV_MISSING"
    assert result["error"]["remedy"]


def test_xlsx_write_new_workbook_honours_sheet_name(tmp_path):
    """真机报告：新建工作簿时传 sheet_name 报「工作表不存在」。"""

    output = tmp_path / "budget.xlsx"
    result = protocol.run(
        "xlsx_write",
        {
            "rows": [["项目", "金额"], ["差旅", 1150]],
            "sheet_name": "预算表",
            "output_path": str(output),
            "allow_roots": [str(tmp_path)],
        },
    )
    assert result["ok"] is True, result
    assert result["data"]["sheet"] == "预算表"
    reopened = protocol.run("xlsx_info", {"path": str(output), "allow_roots": [str(tmp_path)]})
    assert reopened["ok"] is True, reopened
    assert [sheet["name"] for sheet in reopened["data"]["sheets"]] == ["预算表"]


def test_xlsx_write_rejects_illegal_sheet_name(tmp_path):
    result = protocol.run(
        "xlsx_write",
        {
            "rows": [[1]],
            "sheet_name": "非法/名称",
            "output_path": str(tmp_path / "a.xlsx"),
            "allow_roots": [str(tmp_path)],
        },
    )
    assert result["ok"] is False
    assert result["error"]["code"] == "E_INPUT_SCHEMA"
    assert result["error"]["remedy"]


def test_xlsx_write_missing_sheet_on_existing_workbook_still_fails(tmp_path):
    """已有工作簿上查找不存在的表仍应报错，不能被「新建」语义吞掉。"""

    path = _make_xlsx(tmp_path / "src.xlsx", [[1]])
    result = protocol.run(
        "xlsx_write",
        {
            "path": str(path),
            "rows": [[2]],
            "sheet_name": "不存在",
            "output_path": str(tmp_path / "out.xlsx"),
            "allow_roots": [str(tmp_path)],
        },
    )
    assert result["ok"] is False
    assert result["error"]["code"] == "E_INPUT_SCHEMA"
    assert "工作表不存在" in result["error"]["message"]
