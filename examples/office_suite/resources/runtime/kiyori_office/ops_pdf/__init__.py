"""PDF 操作命令集合。"""

from __future__ import annotations

from ..readers.pdf_reader import parse_page_range, pdf_info, require_pypdf


def register_commands() -> None:
    from . import ops as _ops  # noqa: F401


__all__ = ["register_commands", "parse_page_range", "pdf_info", "require_pypdf"]
