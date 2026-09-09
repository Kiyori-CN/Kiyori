"""XLSX 操作命令集合。"""

from __future__ import annotations

from typing import Any, Dict

from ..paths import resolve_path
from ..protocol import register
from ..readers.xlsx_reader import require_openpyxl, xlsx_info, xlsx_read


def register_commands() -> None:
    from . import formula as _formula  # noqa: F401
    from . import recalc as _recalc  # noqa: F401
    from . import write as _write  # noqa: F401
    from . import chart as _chart  # noqa: F401


@register("xlsx_info", schema="xlsx_info", next_actions=["xlsx_read"])
def _xlsx_info_command(args: Dict[str, Any]) -> Dict[str, Any]:
    source = resolve_path(args.get("path"), args=args, field="path", must_exist=True)
    info = xlsx_info(source)
    return {
        "data": {
            **info,
            "navigation": [
                {"sheet": sheet["name"], "rows": sheet["max_row"]}
                for sheet in info["sheets"]
            ],
        }
    }


@register("xlsx_read", schema="xlsx_read", next_actions=["xlsx_write", "xlsx_recalc"])
def _xlsx_read_command(args: Dict[str, Any]) -> Dict[str, Any]:
    source = resolve_path(args.get("path"), args=args, field="path", must_exist=True)
    return {
        "data": xlsx_read(
            source,
            sheet_name=args.get("sheet_name"),
            cell_range=args.get("range"),
            max_rows=int(args.get("max_rows") or 500),
        )
    }


__all__ = ["register_commands", "require_openpyxl"]
