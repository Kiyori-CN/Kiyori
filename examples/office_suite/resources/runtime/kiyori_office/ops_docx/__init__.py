"""DOCX 操作命令集合。"""

from __future__ import annotations

from typing import Any, Dict, List, Optional

from ..protocol import OfficeError, register
from ..readers.docx_reader import docx_outline, docx_read_text, require_docx


def register_commands() -> None:
    # 各模块通过 @register 装饰器在 import 时完成注册。
    from . import create as _create  # noqa: F401
    from . import edit as _edit  # noqa: F401
    from . import media as _media  # noqa: F401
    from . import template as _template  # noqa: F401

    register(
        "docx_outline",
        schema="docx_outline",
        next_actions=["docx_edit", "docx_find_replace"],
    )(_docx_outline_command)


def _docx_outline_command(args: Dict[str, Any]) -> Dict[str, Any]:
    from ..paths import resolve_path

    source = resolve_path(args.get("path"), args=args, field="path", must_exist=True)
    outline = docx_outline(source, max_items=int(args.get("max_items") or 0))
    return {
        "data": {
            **outline,
            "navigation": outline["headings"],
        }
    }


__all__ = ["docx_outline", "docx_read_text", "require_docx", "register_commands"]
