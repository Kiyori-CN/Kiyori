"""PPTX 操作命令集合。"""

from __future__ import annotations

from ..readers.pptx_reader import pptx_outline, require_pptx


def register_commands() -> None:
    from . import edit as _edit  # noqa: F401
    from . import slides as _slides  # noqa: F401


__all__ = ["register_commands", "pptx_outline", "require_pptx"]
