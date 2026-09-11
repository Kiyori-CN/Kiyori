"""子包 office 的通用命令：读取、工作区、差异。"""

from __future__ import annotations

import difflib
from pathlib import Path
from typing import Any, Dict, List, Optional

from .budget import bounded_text
from .paths import (
    artifact,
    atomic_write_text,
    resolve_path,
    resolve_task_id,
    sanitize_filename,
    task_dir,
)
from .protocol import OfficeError, register
from .readers import docx_outline, docx_read_text, pdf_extract_text, pdf_info
from .readers import pptx_outline, pptx_read_text, read_tabular, xlsx_info, xlsx_read
from .readers.tabular import TEXT_SUFFIXES, html_to_text, read_text

FORMAT_BY_SUFFIX = {
    ".docx": "docx",
    ".xlsx": "xlsx",
    ".xlsm": "xlsx",
    ".pptx": "pptx",
    ".pdf": "pdf",
    ".csv": "tabular",
    ".tsv": "tabular",
    ".md": "text",
    ".markdown": "text",
    ".txt": "text",
    ".html": "text",
    ".htm": "text",
    ".json": "text",
}


def detect_format(path: Path) -> str:
    suffix = path.suffix.lower()
    if suffix not in FORMAT_BY_SUFFIX:
        raise OfficeError(
            "E_FORMAT_UNSUPPORTED",
            "不支持的文件格式: %s" % (suffix or "(无扩展名)"),
            detail=str(path),
            remedy="先用 office_convert 转换到支持的格式；旧 .doc/.xls/.ppt 必须先转换",
        )
    return FORMAT_BY_SUFFIX[suffix]


@register("office_read", schema="office_read", next_actions=["office_validate"])
def office_read(args: Dict[str, Any]) -> Dict[str, Any]:
    task_id = resolve_task_id(args)
    directory = task_dir(task_id)
    source = resolve_path(args.get("path"), args=args, field="path", must_exist=True)
    mode = str(args.get("mode") or "full")
    if mode not in ("outline", "full", "range"):
        raise OfficeError("E_INPUT_SCHEMA", "mode 必须是 outline/full/range")
    max_chars = int(args.get("max_chars") or 20000)
    with_anchors = bool(args.get("with_anchors", True))
    file_format = detect_format(source)
    full_dir = directory / "out"

    if file_format == "docx":
        if mode == "outline":
            outline = docx_outline(source)
            text = "\n".join(
                "%d\t%s\t%s" % (item["index"], item["style"], item["text"])
                for item in outline["paragraphs"]
            )
            budget = bounded_text(
                text,
                max_chars,
                full_output_dir=full_dir,
                full_output_name="docx-outline.txt",
                navigation=outline["headings"],
            )
            return {
                "data": {
                    "format": file_format,
                    "mode": mode,
                    "outline": outline,
                    "text": budget["value"],
                    "navigation": outline["headings"],
                    "work_dir": str(directory),
                },
                "truncated": budget["truncated"],
                "full_output_path": budget["full_output_path"],
            }
        text = docx_read_text(source)
    elif file_format == "xlsx":
        if mode == "outline":
            info = xlsx_info(source)
            return {
                "data": {
                    "format": file_format,
                    "mode": mode,
                    "info": info,
                    "navigation": [
                        {"sheet": sheet["name"], "rows": sheet["max_row"]}
                        for sheet in info["sheets"]
                    ],
                    "work_dir": str(directory),
                }
            }
        read = xlsx_read(
            source,
            sheet_name=args.get("sheet_name"),
            cell_range=args.get("range"),
            max_rows=int(args.get("max_rows") or 500),
        )
        text = _xlsx_to_text(read)
    elif file_format == "pptx":
        if mode == "outline":
            outline = pptx_outline(source)
            return {
                "data": {
                    "format": file_format,
                    "mode": mode,
                    "outline": outline,
                    "navigation": [
                        {"slide": slide["index"] + 1, "layout": slide["layout"]}
                        for slide in outline["slides"]
                    ],
                    "work_dir": str(directory),
                }
            }
        text = pptx_read_text(source)
    elif file_format == "pdf":
        if mode == "outline":
            info = pdf_info(source)
            return {
                "data": {
                    "format": file_format,
                    "mode": mode,
                    "info": info,
                    "navigation": [
                        {"page": page, "label": "page %d" % page}
                        for page in range(1, info["pages"] + 1)
                    ],
                    "work_dir": str(directory),
                }
            }
        from .readers.pdf_reader import parse_page_range

        info = pdf_info(source)
        pages = parse_page_range(args.get("range"), info["pages"])
        extracted = pdf_extract_text(source, pages=pages, layout=bool(args.get("layout")))
        text = extracted["text"]
    elif file_format == "tabular":
        table = read_tabular(source, max_rows=int(args.get("max_rows") or 500))
        text = _tabular_to_text(table)
    else:
        raw = read_text(source)
        text = html_to_text(raw) if source.suffix.lower() in (".html", ".htm") else raw

    budget = bounded_text(
        text,
        max_chars,
        full_output_dir=full_dir,
        full_output_name="%s-full.txt" % source.stem,
    )
    data: Dict[str, Any] = {
        "format": file_format,
        "mode": mode,
        "text": budget["value"],
        "total_chars": budget["total_chars"],
        "work_dir": str(directory),
    }
    if with_anchors and file_format in ("docx", "pdf", "pptx"):
        data["navigation"] = _navigation_for(source, file_format)
    return {
        "data": data,
        "truncated": budget["truncated"],
        "full_output_path": budget["full_output_path"],
    }


def _navigation_for(path: Path, file_format: str) -> List[Dict[str, Any]]:
    if file_format == "docx":
        return docx_outline(path)["headings"]
    if file_format == "pdf":
        info = pdf_info(path)
        return [{"page": page} for page in range(1, info["pages"] + 1)]
    if file_format == "pptx":
        outline = pptx_outline(path)
        return [{"slide": slide["index"] + 1, "layout": slide["layout"]} for slide in outline["slides"]]
    return []


def _xlsx_to_text(read: Dict[str, Any]) -> str:
    blocks: List[str] = []
    for sheet in read["sheets"]:
        blocks.append("## %s (%s)" % (sheet["name"], sheet["range"]))
        for row in sheet["rows"]:
            cells = []
            for cell in row:
                if "formula" in cell:
                    cached = cell.get("cached_value")
                    cells.append("%s → %s" % (
                        cell["formula"], cached if cached is not None else "[缓存未提供；请先 xlsx_recalc]"
                    ))
                else:
                    cells.append("" if cell["value"] is None else str(cell["value"]))
            blocks.append("\t".join(cells))
    return "\n".join(blocks)


def _tabular_to_text(table: Dict[str, Any]) -> str:
    lines = ["\t".join(table["header"])]
    for row in table["rows"][1:]:
        lines.append("\t".join(row))
    return "\n".join(lines)


@register("office_validate", schema="office_validate", next_actions=["office_render_preview"])
def office_validate(args: Dict[str, Any]) -> Dict[str, Any]:
    from .validate import validate_document

    source = resolve_path(args.get("path"), args=args, field="path", must_exist=True)
    original = None
    if args.get("original_path"):
        original = resolve_path(
            args.get("original_path"), args=args, field="original_path", must_exist=True
        )
    report = validate_document(source, original_path=original, strict=bool(args.get("strict")))
    return {"data": report}


@register("office_convert", schema="office_convert", engine="external", next_actions=["office_validate"])
def office_convert(args: Dict[str, Any]) -> Dict[str, Any]:
    from .convert import convert

    return convert(args)


@register(
    "office_render_preview",
    schema="office_render_preview",
    engine="libreoffice+pdftoppm",
    next_actions=["office_validate"],
)
def office_render_preview(args: Dict[str, Any]) -> Dict[str, Any]:
    from .render import render_preview

    return render_preview(args)


@register("office_diff", schema="office_diff", next_actions=["office_read"])
def office_diff(args: Dict[str, Any]) -> Dict[str, Any]:
    left = resolve_path(args.get("left"), args=args, field="left", must_exist=True)
    right = resolve_path(args.get("right"), args=args, field="right", must_exist=True)
    left_text = _read_any_text(left)
    right_text = _read_any_text(right)
    diff = list(
        difflib.unified_diff(
            left_text.splitlines(),
            right_text.splitlines(),
            fromfile=str(left),
            tofile=str(right),
            lineterm="",
        )
    )
    text = "\n".join(diff)
    task_id = resolve_task_id(args)
    directory = task_dir(task_id)
    budget = bounded_text(
        text,
        int(args.get("max_chars") or 20000),
        full_output_dir=directory / "out",
        full_output_name="office-diff.txt",
    )
    return {
        "data": {
            "diff": budget["value"],
            "changed_lines": sum(1 for line in diff if line[:1] in "+-" and line[:3] not in ("+++", "---")),
            "identical": not diff,
            "work_dir": str(directory),
        },
        "truncated": budget["truncated"],
        "full_output_path": budget["full_output_path"],
    }


def _read_any_text(path: Path) -> str:
    file_format = detect_format(path)
    if file_format == "docx":
        return docx_read_text(path)
    if file_format == "xlsx":
        return _xlsx_to_text(xlsx_read(path))
    if file_format == "pptx":
        return pptx_read_text(path)
    if file_format == "pdf":
        return pdf_extract_text(path)["text"]
    if file_format == "tabular":
        return _tabular_to_text(read_tabular(path))
    raw = read_text(path)
    return html_to_text(raw) if path.suffix.lower() in (".html", ".htm") else raw


WORKSPACE_DIRS = ("source", "output", "templates", "assets")
WORKSPACE_AGENTS = """# 办公文档工作区规则

- 先 `office_env_check`，再读对应格式 Skill；输入先 outline/read 再编辑。
- 产物一律写入 `output/`，不要原地覆盖 `source/` 里的用户文件。
- 含公式的 xlsx 交付前必须 `xlsx_recalc`，错误数为 0 才可交付。
- 交付前执行 `office_validate` 与 `office_render_preview`，并用 direct_image 看图。
- 模板与素材放 `templates/` 与 `assets/`，不要把中间文件混进 `output/`。
"""


@register("office_workspace_init", schema="office_workspace_init", next_actions=["office_env_check"])
def office_workspace_init(args: Dict[str, Any]) -> Dict[str, Any]:
    target = resolve_path(
        args.get("dir"), args=args, field="dir", create_parent=True
    )
    target.mkdir(parents=True, exist_ok=True)
    created: List[str] = []
    for name in WORKSPACE_DIRS:
        directory = target / name
        if not directory.exists():
            directory.mkdir(parents=True)
            created.append(str(directory))
    agents_path = target / "AGENTS.md"
    if not agents_path.exists() or args.get("overwrite"):
        atomic_write_text(agents_path, WORKSPACE_AGENTS)
        created.append(str(agents_path))
    return {
        "data": {
            "dir": str(target),
            "created": created,
            "directories": list(WORKSPACE_DIRS),
            "agents_markdown": WORKSPACE_AGENTS,
        }
    }


@register("office_workspace_clean", schema="office_workspace_clean")
def office_workspace_clean(args: Dict[str, Any]) -> Dict[str, Any]:
    from .storage import clean
    return {"data": clean(args.get("task_id"), scope=args.get("scope", "temporary"),
                          confirm=args.get("confirm", False), plan_token=args.get("plan_token"))}


@register("office_workspace_status", schema="office_workspace_status", next_actions=["office_workspace_clean"])
def office_workspace_status(args: Dict[str, Any]) -> Dict[str, Any]:
    from .storage import list_tasks
    return {"data": list_tasks(offset=args.get("offset", 0), limit=args.get("limit", 20))}
