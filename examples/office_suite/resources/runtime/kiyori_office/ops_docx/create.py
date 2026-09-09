"""docx_create / docx_table / docx_style。"""

from __future__ import annotations

import re
from pathlib import Path
from typing import Any, Dict, List, Optional

from ..budget import bounded_text
from ..paths import atomic_save, artifact, atomic_write_text, resolve_output_path, resolve_path, resolve_task_id, task_dir
from ..protocol import OfficeError, engine_version, register
from ..readers.docx_reader import docx_outline, require_docx
from .layout import apply_layout, checked, font_style, paragraph_style, format_table, table_rows, layout_warnings, FONT_KEYS, PARAGRAPH_KEYS

@register(
    "docx_create",
    schema="docx_create",
    engine="python-docx",
    next_actions=["docx_outline", "office_validate", "office_render_preview"],
)
def docx_create(args: Dict[str, Any]) -> Dict[str, Any]:
    require_docx()
    import docx  # type: ignore
    from docx.shared import Pt  # type: ignore

    task_id = resolve_task_id(args)
    directory = task_dir(task_id)
    output = resolve_output_path(
        args.get("output_path"),
        args=args,
        field="output_path",
        default_dir=directory / "out",
        default_name=str(args.get("file_name") or "document.docx"),
        overwrite=bool(args.get("overwrite")),
    )

    if output.suffix.lower() != ".docx":
        raise OfficeError("E_INPUT_SCHEMA", "output_path 必须是 .docx")
    spec = args.get("spec")
    markdown = args.get("markdown")
    if spec is not None and markdown is not None:
        raise OfficeError("E_INPUT_SCHEMA", "spec 与 markdown 只能提供一个")
    if not spec and not markdown:
        raise OfficeError(
            "E_INPUT_SCHEMA",
            "docx_create 需要 spec 或 markdown 之一",
            remedy="提供结构化 spec（blocks 数组）或 markdown 文本",
        )

    document = docx.Document()
    if isinstance(spec, dict) and "layout" in spec:
        apply_layout(document, spec["layout"])
    if markdown:
        _append_markdown(document, str(markdown), Pt)
    else:
        _append_spec(document, spec, Pt)

    output.parent.mkdir(parents=True, exist_ok=True)
    atomic_save(document, output)
    outline = docx_outline(output)
    return {
        "artifacts": [artifact(output)],
        "data": {
            "work_dir": str(directory),
            "paragraph_count": outline["paragraph_count"],
            "table_count": outline["table_count"],
            "headings": outline["headings"],
        },
        "engine_version": engine_version("python-docx"),
        "warnings": layout_warnings(spec.get("layout", {})) if isinstance(spec, dict) else [],
    }


def _append_spec(document, spec: Any, pt) -> None:
    if not isinstance(spec, dict) or not isinstance(spec.get("blocks"), list):
        raise OfficeError(
            "E_INPUT_SCHEMA",
            "spec 必须是含 blocks 数组的对象",
            detail="spec=%r" % (type(spec).__name__,),
        )
    for index, block in enumerate(spec["blocks"]):
        if not isinstance(block, dict):
            raise OfficeError("E_INPUT_SCHEMA", "spec.blocks[%d] 必须是对象" % index)
        kind = str(block.get("type") or "paragraph")
        content = {"type", "text", "runs", "format"}
        allowed = {
            "heading": content | {"level"}, "title": content | {"level"},
            "paragraph": content | {"style"},
            "bullet": content | {"items"}, "number": content | {"items"},
            "table": {"type", "rows", "style", "header", "column_widths_cm"},
            "page_break": {"type"},
        }
        if kind in allowed:
            # 内容字段拼错不能被当作空段落发布；错误精确到块索引。
            checked(block, allowed[kind], "spec.blocks[%d]" % index)
        paragraphs = []
        if kind in ("heading", "title"):
            level = block.get("level", 0 if kind == "title" else 1)
            if isinstance(level, bool) or not isinstance(level, int) or not 0 <= level <= 9:
                raise OfficeError("E_INPUT_SCHEMA", "标题 level 必须为 0 到 9 的整数")
            text = str(block.get("text") or "")
            if level <= 0:
                paragraph = document.add_heading(text, level=0)
            else:
                paragraph = document.add_heading(text, level=level)
        elif kind == "paragraph":
            paragraph = document.add_paragraph(str(block.get("text") or ""))
            style = block.get("style")
            if style:
                try:
                    paragraph.style = document.styles[str(style)]
                except KeyError as exc:
                    raise OfficeError(
                        "E_INPUT_SCHEMA",
                        "未知段落样式: %s" % style,
                        detail=str(exc),
                    ) from exc
        elif kind in ("bullet", "number"):
            style = "List Bullet" if kind == "bullet" else "List Number"
            if "items" in block:
                items = block["items"]
                if "text" in block or "runs" in block:
                    raise OfficeError("E_INPUT_SCHEMA", "spec.blocks[%d]: items 不能与 text/runs 混用" % index)
                if not isinstance(items, list) or not items or not all(isinstance(item, str) for item in items):
                    raise OfficeError("E_INPUT_SCHEMA", "spec.blocks[%d].items 必须是非空字符串数组" % index)
                # 每项必须是真实列表段落，不能拼接成段内换行或只消费首项。
                paragraphs = [document.add_paragraph(item, style=style) for item in items]
            else:
                if "text" not in block and not block.get("runs"):
                    raise OfficeError("E_INPUT_SCHEMA", "spec.blocks[%d]: 列表需要 text、非空 runs 或 items" % index)
                if "text" in block and not isinstance(block["text"], str):
                    raise OfficeError("E_INPUT_SCHEMA", "spec.blocks[%d].text 必须是字符串" % index)
                paragraph = document.add_paragraph(block.get("text", ""), style=style)
        elif kind == "table":
            rows = block.get("rows")
            columns = table_rows(rows)
            table = document.add_table(rows=len(rows), cols=columns)
            table.style = str(block.get("style") or "Table Grid")
            for row_index, row in enumerate(rows):
                if not isinstance(row, list):
                    raise OfficeError("E_INPUT_SCHEMA", "table.rows[%d] 必须是数组" % row_index)
                for column_index, value in enumerate(row):
                    table.cell(row_index, column_index).text = "" if value is None else str(value)
            format_table(table, rows, header=block.get("header", True), widths=block.get("column_widths_cm"))
        elif kind == "page_break":
            document.add_page_break()
        else:
            raise OfficeError(
                "E_INPUT_SCHEMA",
                "不支持的 spec 块类型: %s" % kind,
                remedy="使用 heading/title/paragraph/bullet/number/table/page_break",
            )

        for paragraph in (paragraphs or [paragraph]) if kind in ("heading", "title", "paragraph", "bullet", "number") else []:
            if "runs" in block:
                runs = block["runs"]
                if "text" in block or not isinstance(runs, list):
                    raise OfficeError("E_INPUT_SCHEMA", "runs 必须是数组，且不能与 text 同时提供")
                for item in runs:
                    checked(item, FONT_KEYS | {"text"}, "runs[]")
                    if not isinstance(item.get("text"), str):
                        raise OfficeError("E_INPUT_SCHEMA", "runs[].text 必须是字符串")
                    run = paragraph.add_run(item["text"])
                    font_style(run.font, item)
            if "format" in block:
                config = checked(block["format"], PARAGRAPH_KEYS, "block.format")
                paragraph_style(paragraph.paragraph_format, config)


_MD_HEADING = re.compile(r"^(#{1,6})\s+(.*)$")
_MD_BULLET = re.compile(r"^[-*+]\s+(.*)$")
_MD_NUMBER = re.compile(r"^\d+[.)]\s+(.*)$")


def _append_markdown(document, markdown: str, pt) -> None:
    lines = markdown.replace("\r\n", "\n").split("\n")
    table_buffer: List[List[str]] = []

    def flush_table() -> None:
        nonlocal table_buffer
        if not table_buffer:
            return
        columns = max(len(row) for row in table_buffer)
        table = document.add_table(rows=len(table_buffer), cols=columns)
        table.style = "Table Grid"
        for row_index, row in enumerate(table_buffer):
            for column_index, value in enumerate(row):
                table.cell(row_index, column_index).text = value
        table_buffer = []

    for raw_line in lines:
        line = raw_line.rstrip()
        if line.strip().startswith("|") and line.strip().endswith("|"):
            cells = [cell.strip() for cell in line.strip().strip("|").split("|")]
            if all(re.fullmatch(r":?-{3,}:?", cell) for cell in cells):
                continue
            table_buffer.append(cells)
            continue
        flush_table()
        if not line.strip():
            continue
        heading = _MD_HEADING.match(line)
        if heading:
            document.add_heading(heading.group(2).strip(), level=len(heading.group(1)))
            continue
        bullet = _MD_BULLET.match(line)
        if bullet:
            document.add_paragraph(bullet.group(1).strip(), style="List Bullet")
            continue
        numbered = _MD_NUMBER.match(line)
        if numbered:
            document.add_paragraph(numbered.group(1).strip(), style="List Number")
            continue
        document.add_paragraph(line)
    flush_table()


@register(
    "docx_table",
    schema="docx_table",
    engine="python-docx",
    next_actions=["docx_outline", "office_validate"],
)
def docx_table(args: Dict[str, Any]) -> Dict[str, Any]:
    require_docx()
    import docx  # type: ignore
    from docx.shared import Cm  # type: ignore

    task_id = resolve_task_id(args)
    directory = task_dir(task_id)
    source = resolve_path(args.get("path"), args=args, field="path", must_exist=True)
    output = resolve_output_path(
        args.get("output_path") or (str(source) if args.get("in_place") else None),
        args=args,
        field="output_path",
        default_dir=directory / "out",
        default_name=source.name,
        overwrite=bool(args.get("overwrite")),
        in_place=bool(args.get("in_place")),
    )
    document = docx.Document(str(source))

    rows = args.get("rows")
    columns = table_rows(rows)
    header = bool(args.get("header", True))
    table = document.add_table(rows=len(rows), cols=columns)
    table.style = str(args.get("style") or "Table Grid")
    for row_index, row in enumerate(rows):
        for column_index, value in enumerate(row):
            table.cell(row_index, column_index).text = "" if value is None else str(value)
    format_table(table, rows, header=header, widths=args.get("column_widths_cm"))
    atomic_save(document, output)
    return {
        "artifacts": [artifact(output)],
        "data": {
            "rows": len(rows),
            "columns": columns,
            "header": header,
            "work_dir": str(directory),
        },
        "engine_version": engine_version("python-docx"),
    }


@register(
    "docx_style",
    schema="docx_style",
    engine="python-docx",
    next_actions=["office_validate", "office_render_preview"],
)
def docx_style(args: Dict[str, Any]) -> Dict[str, Any]:
    require_docx()
    import docx  # type: ignore
    from docx.enum.text import WD_ALIGN_PARAGRAPH  # type: ignore
    from docx.shared import Cm, Pt  # type: ignore

    task_id = resolve_task_id(args)
    directory = task_dir(task_id)
    source = resolve_path(args.get("path"), args=args, field="path", must_exist=True)
    output = resolve_output_path(
        args.get("output_path") or (str(source) if args.get("in_place") else None),
        args=args,
        field="output_path",
        default_dir=directory / "out",
        default_name=source.name,
        overwrite=bool(args.get("overwrite")),
        in_place=bool(args.get("in_place")),
    )
    document = docx.Document(str(source))
    layout = {key: args[key] for key in
        ("margins_cm", "default_font", "paragraph_styles", "page_setup", "header_footer") if key in args}
    applied = apply_layout(document, layout)

    atomic_save(document, output)
    return {
        "artifacts": [artifact(output)],
        "data": {"applied": applied, "work_dir": str(directory)},
        "warnings": layout_warnings(layout),
        "engine_version": engine_version("python-docx"),
    }
