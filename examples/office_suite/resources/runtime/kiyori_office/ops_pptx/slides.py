"""pptx_create / pptx_slide / pptx_clean / pptx_template_fill。

时序约束（设计文档 §6.4）：结构性操作（增删复制重排）必须在内容编辑之前完成。
先编辑再复制会克隆已编辑内容；``pptx_clean`` 会删除不在 ``<p:sldIdLst>`` 中的
slide，包括刚写的那张。工具描述与返回的 next_actions 都会强调这条约束。
"""

from __future__ import annotations

import copy as copy_module
import re
from pathlib import Path
from typing import Any, Dict, List

from ..paths import artifact, resolve_output_path, resolve_path, resolve_task_id, task_dir
from ..protocol import OfficeError, engine_version, register
from ..readers.pptx_reader import pptx_outline, require_pptx

def _open(source: Path):
    from pptx import Presentation  # type: ignore

    require_pptx()
    try:
        return Presentation(str(source))
    except Exception as exc:
        raise OfficeError(
            "E_DOC_CORRUPT",
            "PPTX 打开失败",
            detail="%s: %s" % (type(exc).__name__, exc),
        ) from exc


def _prepare(args: Dict[str, Any], *, need_source: bool = True):
    task_id = resolve_task_id(args)
    directory = task_dir(task_id)
    source = None
    if args.get("path"):
        source = resolve_path(args.get("path"), args=args, field="path", must_exist=True)
    elif need_source:
        raise OfficeError("E_INPUT_SCHEMA", "缺少 path")
    default_name = source.name if source else str(args.get("file_name") or "presentation.pptx")
    in_place = bool(args.get("in_place"))
    output = resolve_output_path(
        args.get("output_path") or (str(source) if in_place and source else None),
        args=args,
        field="output_path",
        default_dir=directory / "out",
        default_name=default_name,
        overwrite=bool(args.get("overwrite")),
        in_place=in_place,
    )
    if output.suffix.lower() != ".pptx":
        raise OfficeError("E_INPUT_SCHEMA", "output_path 必须是 .pptx", detail=str(output))
    return task_id, directory, source, output


def _set_text_frame(text_frame, lines: List[str], size_pt=None) -> None:
    from pptx.util import Pt  # type: ignore

    text_frame.clear()
    for index, line in enumerate(lines):
        paragraph = text_frame.paragraphs[0] if index == 0 else text_frame.add_paragraph()
        paragraph.text = str(line)
        if size_pt:
            for run in paragraph.runs:
                run.font.size = Pt(float(size_pt))


def _apply_slide_spec(prs, spec: Dict[str, Any], layout_index: int) -> None:
    layouts = prs.slide_layouts
    if layout_index >= len(layouts):
        raise OfficeError(
            "E_INPUT_SCHEMA",
            "layout_index 越界: %s" % layout_index,
            detail="layout_count=%d" % len(layouts),
        )
    slide = prs.slides.add_slide(layouts[layout_index])
    title = str(spec.get("title") or "")
    bullets = spec.get("bullets") or []
    if not isinstance(bullets, list):
        raise OfficeError("E_INPUT_SCHEMA", "bullets 必须是字符串数组")
    if slide.shapes.title is not None and title:
        _set_text_frame(slide.shapes.title.text_frame, [title], spec.get("title_size_pt") or 36)
    body = None
    for placeholder in slide.placeholders:
        if placeholder.placeholder_format.idx == 1:
            body = placeholder
            break
    if body is not None and bullets:
        _set_text_frame(body.text_frame, [str(item) for item in bullets], spec.get("body_size_pt") or 16)
    elif bullets and title:
        # 没有内容占位符时显式加一个文本框，避免文字落空
        textbox = slide.shapes.add_textbox(
            prs.slide_width * 0.08,
            prs.slide_height * 0.25,
            prs.slide_width * 0.84,
            prs.slide_height * 0.65,
        )
        _set_text_frame(textbox.text_frame, [str(item) for item in bullets], spec.get("body_size_pt") or 16)


@register(
    "pptx_create",
    schema="pptx_create",
    engine="python-pptx",
    next_actions=["office_validate", "office_render_preview"],
)
def pptx_create(args: Dict[str, Any]) -> Dict[str, Any]:
    from pptx import Presentation  # type: ignore

    require_pptx()
    task_id, directory, _source, output = _prepare(args, need_source=False)
    slides = args.get("slides")
    if not isinstance(slides, list) or not slides:
        raise OfficeError("E_INPUT_SCHEMA", "slides 必须是非空数组")
    template = args.get("template_path")
    if template:
        prs = _open(resolve_path(template, args=args, field="template_path", must_exist=True))
    else:
        prs = Presentation()
    default_layout = int(args.get("layout_index") or 1)
    for index, spec in enumerate(slides):
        if not isinstance(spec, dict):
            raise OfficeError("E_INPUT_SCHEMA", "slides[%d] 必须是对象" % index)
        _apply_slide_spec(prs, spec, int(spec.get("layout_index", default_layout)))
    output.parent.mkdir(parents=True, exist_ok=True)
    prs.save(str(output))
    outline = pptx_outline(output)
    return {
        "artifacts": [artifact(output)],
        "data": {
            "slide_count": outline["slide_count"],
            "layouts": [slide["layout"] for slide in outline["slides"]],
            "work_dir": str(directory),
        },
        "engine_version": engine_version("python-pptx"),
    }


@register(
    "pptx_slide",
    schema="pptx_slide",
    engine="python-pptx",
    next_actions=["pptx_clean", "pptx_edit", "office_validate"],
)
def pptx_slide(args: Dict[str, Any]) -> Dict[str, Any]:
    """增删复制重排幻灯片，并维护关系与 <p:sldIdLst>。"""

    require_pptx()
    task_id, directory, source, output = _prepare(args)
    prs = _open(source)
    operation = str(args.get("operation") or "")
    if operation not in ("add", "delete", "duplicate", "move"):
        raise OfficeError("E_INPUT_SCHEMA", "operation 必须是 add/delete/duplicate/move")

    if operation == "add":
        layout_index = int(args.get("layout_index") or 1)
        layouts = prs.slide_layouts
        if layout_index >= len(layouts):
            raise OfficeError(
                "E_INPUT_SCHEMA",
                "layout_index 越界: %s" % layout_index,
                detail="layout_count=%d" % len(layouts),
            )
        prs.slides.add_slide(layouts[layout_index])
    else:
        index = args.get("index")
        if not isinstance(index, int) or isinstance(index, bool):
            raise OfficeError("E_INPUT_SCHEMA", "index 必须是 0-based 整数")
        slide_list = list(prs.slides)
        if index < 0 or index >= len(slide_list):
            raise OfficeError(
                "E_INPUT_SCHEMA",
                "index 越界: %s" % index,
                detail="slide_count=%d" % len(slide_list),
            )
        if operation == "delete":
            if len(slide_list) == 1:
                raise OfficeError("E_INPUT_SCHEMA", "不能删除唯一的一张幻灯片")
            _delete_slide(prs, index)
        elif operation == "duplicate":
            _duplicate_slide(prs, index)
        else:
            target = args.get("target_index")
            if not isinstance(target, int) or isinstance(target, bool):
                raise OfficeError("E_INPUT_SCHEMA", "move 需要整数 target_index")
            if target < 0 or target >= len(slide_list):
                raise OfficeError("E_INPUT_SCHEMA", "target_index 越界: %s" % target)
            _move_slide(prs, index, target)

    output.parent.mkdir(parents=True, exist_ok=True)
    prs.save(str(output))
    outline = pptx_outline(output)
    return {
        "artifacts": [artifact(output, role="in_place" if args.get("in_place") else "output")],
        "data": {
            "operation": operation,
            "slide_count": outline["slide_count"],
            "work_dir": str(directory),
            "ordering_note": "结构性操作请先完成，再编辑内容；随后调用 pptx_clean。",
        },
        "engine_version": engine_version("python-pptx"),
    }


def _sld_id_lst(prs):
    return prs.slides._sldIdLst  # noqa: SLF001


def _slide_part_rId(prs, index: int) -> str:
    slide_part = list(prs.slides)[index].part
    for rId, rel in prs.part.rels.items():
        if rel.target_part is slide_part:
            return rId
    raise OfficeError(
        "E_ENGINE_FAILED",
        "未找到幻灯片在 presentation 关系中的 rId",
        detail="index=%d" % index,
    )


def _delete_slide(prs, index: int) -> None:
    """按关系与 <p:sldIdLst> 双向下线，避免留下孤儿 slide part。"""

    slide_list = list(prs.slides)
    slide_id = slide_list[index].slide_id
    sld_id_lst = _sld_id_lst(prs)
    for element in list(sld_id_lst):
        if element.get("id") == str(slide_id):
            sld_id_lst.remove(element)
            break
    r_id = _slide_part_rId(prs, index)
    prs.part.drop_rel(r_id)


def _duplicate_slide(prs, index: int):
    """逐字节复制 slide XML 并重建关系，避免先编辑再复制导致内容被克隆。"""

    slide_list = list(prs.slides)
    source_slide = slide_list[index]
    layout = source_slide.slide_layout
    new_slide = prs.slides.add_slide(layout)
    _clear_slide_shapes(new_slide)

    r_id_map = {}
    for r_id, rel in source_slide.part.rels.items():
        if rel.reltype.endswith("/slideLayout"):
            continue
        if rel.is_external:
            new_r_id = new_slide.part.rels.get_or_add_ext_rel(rel.reltype, rel.target_ref)
        else:
            new_r_id = new_slide.part.relate_to(rel.target_part, rel.reltype)
        r_id_map[r_id] = new_r_id

    namespace = "{http://schemas.openxmlformats.org/officeDocument/2006/relationships}"
    for shape in source_slide.shapes:
        element = copy_module.deepcopy(shape._element)
        for node in element.iter():
            for attribute in list(node.attrib):
                if attribute.startswith(namespace):
                    old = node.attrib[attribute]
                    if old in r_id_map:
                        node.attrib[attribute] = r_id_map[old]
        new_slide.shapes._spTree.append(element)  # noqa: SLF001

    sld_id_lst = _sld_id_lst(prs)
    new_element = list(sld_id_lst)[-1]
    sld_id_lst.remove(new_element)
    sld_id_lst.insert(index + 1, new_element)
    return new_slide


def _clear_slide_shapes(slide) -> None:
    sp_tree = slide.shapes._spTree  # noqa: SLF001
    for child in list(sp_tree):
        tag = child.tag.split("}")[-1]
        if tag in ("sp", "pic", "graphicFrame", "grpSp", "cxnSp"):
            sp_tree.remove(child)


def _move_slide(prs, index: int, target: int) -> None:
    sld_id_lst = _sld_id_lst(prs)
    elements = list(sld_id_lst)
    element = elements[index]
    sld_id_lst.remove(element)
    sld_id_lst.insert(target, element)


@register(
    "pptx_clean",
    schema="pptx_clean",
    engine="python-pptx",
    next_actions=["office_validate", "office_render_preview"],
)
def pptx_clean(args: Dict[str, Any]) -> Dict[str, Any]:
    require_pptx()
    task_id, directory, source, output = _prepare(args)
    prs = _open(source)
    before = len(prs.slides._sldIdLst)  # noqa: SLF001
    output.parent.mkdir(parents=True, exist_ok=True)
    prs.save(str(output))
    after = len(_open(output).slides._sldIdLst)  # noqa: SLF001
    return {
        "artifacts": [artifact(output, role="in_place" if args.get("in_place") else "output")],
        "data": {
            "slides_before": before,
            "slides_after": after,
            "work_dir": str(directory),
            "note": "pptx_clean 会丢弃不在 <p:sldIdLst> 中的 slide；请先完成结构性操作。",
        },
        "engine_version": engine_version("python-pptx"),
    }


VARIABLE_PATTERN = re.compile(r"\{\{\s*([A-Za-z0-9_.\-\u4e00-\u9fff]+)\s*\}\}")


@register(
    "pptx_template_fill",
    schema="pptx_template_fill",
    engine="python-pptx",
    next_actions=["office_validate", "office_render_preview"],
)
def pptx_template_fill(args: Dict[str, Any]) -> Dict[str, Any]:
    require_pptx()
    task_id, directory, source, output = _prepare(args)
    variables = args.get("variables")
    if not isinstance(variables, dict):
        raise OfficeError("E_INPUT_SCHEMA", "variables 必须是对象")
    prs = _open(source)
    used: List[str] = []
    missing: List[str] = []

    def replace_in_frame(text_frame) -> None:
        for paragraph in text_frame.paragraphs:
            for run in paragraph.runs:
                if "{{" not in (run.text or ""):
                    continue
                for name in VARIABLE_PATTERN.findall(run.text):
                    if name in variables:
                        run.text = VARIABLE_PATTERN.sub(
                            lambda match: str(variables[match.group(1)])
                            if match.group(1) in variables
                            else match.group(0),
                            run.text,
                        )
                        if name not in used:
                            used.append(name)
                    elif name not in missing:
                        missing.append(name)

    for slide in prs.slides:
        for shape in slide.shapes:
            if shape.has_text_frame:
                replace_in_frame(shape.text_frame)
            if shape.has_table:
                for row in shape.table.rows:
                    for cell in row.cells:
                        replace_in_frame(cell.text_frame)
    if missing and bool(args.get("strict", True)):
        raise OfficeError(
            "E_ANCHOR_NOT_FOUND",
            "模板变量缺少取值",
            detail="missing=%s" % ", ".join(missing),
            remedy="补齐 variables，或显式设置 strict=false",
        )
    output.parent.mkdir(parents=True, exist_ok=True)
    prs.save(str(output))
    return {
        "artifacts": [artifact(output)],
        "data": {
            "used_variables": used,
            "missing_variables": missing,
            "work_dir": str(directory),
        },
        "engine_version": engine_version("python-pptx"),
    }
