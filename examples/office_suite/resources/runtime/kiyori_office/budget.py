"""输出预算：防止一次读爆上下文。"""

from __future__ import annotations

from pathlib import Path
from typing import Any, Dict, Iterable, List, Optional

from .paths import atomic_write_text, sanitize_filename, task_dir

DEFAULT_MAX_CHARS = 20000


def bounded_text(
    text: str,
    max_chars: int = DEFAULT_MAX_CHARS,
    *,
    full_output_dir: Optional[Path] = None,
    full_output_name: str = "full-output.txt",
    navigation: Optional[Iterable[Dict[str, Any]]] = None,
) -> Dict[str, Any]:
    """截断长文本并把完整结果落盘，返回统一预算结构。"""

    if max_chars <= 0:
        max_chars = DEFAULT_MAX_CHARS
    total = len(text)
    if total <= max_chars:
        payload: Dict[str, Any] = {
            "value": text,
            "truncated": False,
            "full_output_path": None,
            "total_chars": total,
        }
    else:
        full_path: Optional[str] = None
        if full_output_dir is not None:
            target = full_output_dir / sanitize_filename(
                full_output_name, fallback="full-output.txt"
            )
            full_path = str(atomic_write_text(target, text))
        payload = {
            "value": text[:max_chars],
            "truncated": True,
            "full_output_path": full_path,
            "total_chars": total,
        }
    if navigation is not None:
        payload["navigation"] = list(navigation)
    return payload


def bounded_items(items: List[Any], max_items: int) -> Dict[str, Any]:
    if max_items <= 0 or len(items) <= max_items:
        return {"items": items, "truncated": False, "total_items": len(items)}
    return {
        "items": items[:max_items],
        "truncated": True,
        "total_items": len(items),
    }


def write_full_output(task_id: str, name: str, text: str) -> Path:
    return atomic_write_text(task_dir(task_id) / "out" / sanitize_filename(name), text)
