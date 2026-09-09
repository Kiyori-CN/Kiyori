"""docx_from_template：{{变量}} 模板填充。

模板段落先做 run 合并，避免变量被 Word 拆开后在 XML 里匹配不到。
"""

from __future__ import annotations

import re
from typing import Any, Dict, List

from ..paths import artifact, resolve_output_path, resolve_path, resolve_task_id, task_dir
from ..protocol import OfficeError, engine_version, register
from ..readers.docx_reader import require_docx
from .runs import merge_document_runs, replace_in_paragraph

VARIABLE_PATTERN = re.compile(r"\{\{\s*([A-Za-z0-9_.\-\u4e00-\u9fff]+)\s*\}\}")

@register(
    "docx_from_template",
    schema="docx_from_template",
    engine="python-docx",
    next_actions=["office_validate", "office_render_preview"],
)
def docx_from_template(args: Dict[str, Any]) -> Dict[str, Any]:
    require_docx()
    import docx  # type: ignore

    task_id = resolve_task_id(args)
    directory = task_dir(task_id)
    source = resolve_path(args.get("path"), args=args, field="path", must_exist=True)
    variables = args.get("variables")
    if not isinstance(variables, dict):
        raise OfficeError("E_INPUT_SCHEMA", "variables 必须是对象")
    strict = bool(args.get("strict", True))
    output = resolve_output_path(
        args.get("output_path"),
        args=args,
        field="output_path",
        default_dir=directory / "out",
        default_name=source.name,
        overwrite=bool(args.get("overwrite")),
    )

    document = docx.Document(str(source))
    merge_document_runs(document)

    used: List[str] = []
    missing: List[str] = []

    def fill(paragraph) -> None:
        combined = "".join(run.text or "" for run in paragraph.runs)
        if not combined:
            return
        for name in VARIABLE_PATTERN.findall(combined):
            if name not in variables:
                if name not in missing:
                    missing.append(name)
                continue
            if name not in used:
                used.append(name)
            replace_in_paragraph(
                paragraph,
                re.compile(r"\{\{\s*%s\s*\}\}" % re.escape(name)),
                str(variables[name]),
            )

    for paragraph in document.paragraphs:
        fill(paragraph)
    for table in document.tables:
        for row in table.rows:
            for cell in row.cells:
                for paragraph in cell.paragraphs:
                    fill(paragraph)
    for section in document.sections:
        for paragraph in list(section.header.paragraphs) + list(section.footer.paragraphs):
            fill(paragraph)

    if missing and strict:
        raise OfficeError(
            "E_ANCHOR_NOT_FOUND",
            "模板变量缺少取值",
            detail="missing=%s" % ", ".join(missing),
            remedy="补齐 variables，或显式设置 strict=false 保留原样",
        )

    output.parent.mkdir(parents=True, exist_ok=True)
    document.save(str(output))
    return {
        "artifacts": [artifact(output)],
        "data": {
            "used_variables": used,
            "missing_variables": missing,
            "work_dir": str(directory),
        },
        "engine_version": engine_version("python-docx"),
    }
