"""docx_edit / docx_find_replace / docx_merge。"""

from __future__ import annotations

from pathlib import Path
from typing import Any, Dict, List

from ..paths import artifact, resolve_output_path, resolve_path, resolve_task_id, task_dir
from ..protocol import OfficeError, engine_version, register
from ..readers.docx_reader import docx_outline, require_docx
from .runs import compile_pattern, merge_document_runs, merge_runs, replace_in_paragraph

def _open_document(source: Path):
    import docx  # type: ignore

    require_docx()
    try:
        return docx.Document(str(source))
    except Exception as exc:
        raise OfficeError(
            "E_DOC_CORRUPT",
            "DOCX 打开失败",
            detail="%s: %s" % (type(exc).__name__, exc),
        ) from exc


def _prepare(args: Dict[str, Any]):
    task_id = resolve_task_id(args)
    directory = task_dir(task_id)
    source = resolve_path(args.get("path"), args=args, field="path", must_exist=True)
    in_place = bool(args.get("in_place"))
    output = resolve_output_path(
        args.get("output_path") or (str(source) if in_place else None),
        args=args,
        field="output_path",
        default_dir=directory / "out",
        default_name=source.name,
        overwrite=bool(args.get("overwrite")),
        in_place=in_place,
    )
    return task_id, directory, source, output


def _resolve_paragraph(document, anchor: Dict[str, Any]):
    index = anchor.get("index")
    if index is not None:
        if not isinstance(index, int) or isinstance(index, bool):
            raise OfficeError("E_INPUT_SCHEMA", "anchor.index 必须是整数")
        if index < 0 or index >= len(document.paragraphs):
            raise OfficeError(
                "E_ANCHOR_NOT_FOUND",
                "段落索引越界: %s" % index,
                detail="paragraph_count=%d" % len(document.paragraphs),
                remedy="先调用 docx_outline 重新获取锚点",
            )
        return document.paragraphs[index]
    text = anchor.get("text")
    if not isinstance(text, str) or not text:
        raise OfficeError("E_INPUT_SCHEMA", "anchor 必须提供 index 或非空 text")
    matches = [p for p in document.paragraphs if text in (p.text or "")]
    if not matches:
        raise OfficeError(
            "E_ANCHOR_NOT_FOUND",
            "未命中锚点文本",
            detail=text,
            remedy="先调用 docx_outline 重新获取锚点",
        )
    if len(matches) > 1 and not anchor.get("allow_multiple"):
        raise OfficeError(
            "E_ANCHOR_NOT_FOUND",
            "锚点文本命中多处，拒绝模糊编辑",
            detail="matches=%d text=%s" % (len(matches), text),
            remedy="改用 anchor.index，或显式设置 allow_multiple=true",
        )
    return matches[0]


@register(
    "docx_edit",
    schema="docx_edit",
    engine="python-docx",
    next_actions=["docx_outline", "office_validate"],
)
def docx_edit(args: Dict[str, Any]) -> Dict[str, Any]:
    require_docx()
    task_id, directory, source, output = _prepare(args)
    anchor = args.get("anchor")
    if not isinstance(anchor, dict):
        raise OfficeError("E_INPUT_SCHEMA", "anchor 必须是对象")
    operation = str(args.get("operation") or "")
    if operation not in ("replace", "insert_before", "insert_after", "delete"):
        raise OfficeError(
            "E_INPUT_SCHEMA",
            "operation 必须是 replace/insert_before/insert_after/delete",
            detail="operation=%r" % args.get("operation"),
        )

    document = _open_document(source)
    paragraph = _resolve_paragraph(document, anchor)
    if operation == "replace":
        if "text" not in args:
            raise OfficeError("E_INPUT_SCHEMA", "replace 需要 text")
        merge_runs(paragraph)
        if paragraph.runs:
            paragraph.runs[0].text = str(args["text"])
            for run in paragraph.runs[1:]:
                run.text = ""
        else:
            paragraph.add_run(str(args["text"]))
    elif operation in ("insert_before", "insert_after"):
        new_paragraph = _copy_paragraph(paragraph)
        new_paragraph.text = str(args.get("text") or "")
        if operation == "insert_before":
            paragraph._element.addprevious(new_paragraph._element)
        else:
            paragraph._element.addnext(new_paragraph._element)
    else:
        paragraph._element.getparent().remove(paragraph._element)

    output.parent.mkdir(parents=True, exist_ok=True)
    document.save(str(output))
    return {
        "artifacts": [
            artifact(output, role="in_place" if args.get("in_place") else "output")
        ],
        "data": {"operation": operation, "changed": 1, "work_dir": str(directory)},
        "engine_version": engine_version("python-docx"),
    }


def _copy_paragraph(paragraph):
    import copy as copy_module

    from docx.text.paragraph import Paragraph  # type: ignore

    return Paragraph(copy_module.deepcopy(paragraph._element), paragraph._parent)


@register(
    "docx_find_replace",
    schema="docx_find_replace",
    engine="python-docx",
    next_actions=["docx_outline", "office_validate", "office_render_preview"],
)
def docx_find_replace(args: Dict[str, Any]) -> Dict[str, Any]:
    """跨 run 合并后查找替换，保留格式。"""

    require_docx()
    task_id, directory, source, output = _prepare(args)
    pattern = compile_pattern(
        str(args.get("find") or ""),
        use_regex=bool(args.get("use_regex")),
        ignore_case=bool(args.get("ignore_case")),
    )
    replacement = str(args.get("replace") or "")
    scope = str(args.get("scope") or "all")
    if scope not in ("all", "paragraphs", "tables"):
        raise OfficeError("E_INPUT_SCHEMA", "scope 必须是 all/paragraphs/tables")

    document = _open_document(source)
    merge_stats = merge_document_runs(document)
    replacements = 0
    paragraphs_touched = 0

    def apply_to(paragraph) -> None:
        nonlocal replacements, paragraphs_touched
        hits = replace_in_paragraph(paragraph, pattern, replacement)
        if hits:
            replacements += hits
            paragraphs_touched += 1

    if scope in ("all", "paragraphs"):
        for paragraph in document.paragraphs:
            apply_to(paragraph)
    if scope in ("all", "tables"):
        for table in document.tables:
            for row in table.rows:
                for cell in row.cells:
                    for paragraph in cell.paragraphs:
                        apply_to(paragraph)
    if replacements == 0:
        raise OfficeError(
            "E_ANCHOR_NOT_FOUND",
            "未找到可替换的内容",
            detail="find=%s scope=%s" % (args.get("find"), scope),
            remedy="先用 docx_outline/office_read 确认原文，再调整 find 或 use_regex",
        )

    output.parent.mkdir(parents=True, exist_ok=True)
    document.save(str(output))
    return {
        "artifacts": [
            artifact(output, role="in_place" if args.get("in_place") else "output")
        ],
        "data": {
            "replacements": replacements,
            "paragraphs_touched": paragraphs_touched,
            "runs_merged": merge_stats["runs_merged"],
            "work_dir": str(directory),
        },
        "engine_version": engine_version("python-docx"),
    }


@register(
    "docx_merge",
    schema="docx_merge",
    engine="python-docx",
    next_actions=["office_validate", "office_render_preview"],
)
def docx_merge(args: Dict[str, Any]) -> Dict[str, Any]:
    require_docx()
    import copy as copy_module

    import docx  # type: ignore

    task_id = resolve_task_id(args)
    directory = task_dir(task_id)
    paths = args.get("paths")
    if not isinstance(paths, list) or len(paths) < 2:
        raise OfficeError("E_INPUT_SCHEMA", "paths 至少需要两个输入文件")
    sources = [
        resolve_path(item, args=args, field="paths[%d]" % index, must_exist=True)
        for index, item in enumerate(paths)
    ]
    output = resolve_output_path(
        args.get("output_path"),
        args=args,
        field="output_path",
        default_dir=directory / "out",
        default_name=str(args.get("file_name") or "merged.docx"),
        overwrite=bool(args.get("overwrite")),
    )
    style_mode = str(args.get("style_mode") or "preserve")
    if style_mode not in ("preserve", "unified"):
        raise OfficeError("E_INPUT_SCHEMA", "style_mode 必须是 preserve/unified")

    base = docx.Document(str(sources[0]))
    merged = 1
    for source in sources[1:]:
        incoming = docx.Document(str(source))
        for element in incoming.element.body.iterchildren():
            if element.tag.split("}")[-1] == "sectPr":
                continue
            base.element.body.append(copy_module.deepcopy(element))
        merged += 1
    output.parent.mkdir(parents=True, exist_ok=True)
    base.save(str(output))
    outline = docx_outline(output)
    return {
        "artifacts": [artifact(output)],
        "data": {
            "merged": merged,
            "paragraph_count": outline["paragraph_count"],
            "style_mode": style_mode,
            "work_dir": str(directory),
        },
        "engine_version": engine_version("python-docx"),
    }
