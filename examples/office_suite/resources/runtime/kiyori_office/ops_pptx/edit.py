"""pptx_edit / pptx_notes / pptx_media / pptx_outline 命令注册。"""

from __future__ import annotations

from pathlib import Path
from typing import Any, Dict, List

from ..paths import atomic_save, artifact, resolve_output_path, resolve_path, resolve_task_id, task_dir
from ..protocol import OfficeError, engine_version, register
from ..readers.pptx_reader import pptx_outline, require_pptx
from .slides import _open, _prepare

@register("pptx_outline", schema="pptx_outline", next_actions=["pptx_edit", "pptx_slide"])
def pptx_outline_command(args: Dict[str, Any]) -> Dict[str, Any]:
    source = resolve_path(args.get("path"), args=args, field="path", must_exist=True)
    outline = pptx_outline(source, max_slides=int(args.get("max_slides") or 0))
    return {
        "data": {
            **outline,
            "navigation": [
                {"slide": slide["index"] + 1, "layout": slide["layout"]}
                for slide in outline["slides"]
            ],
        }
    }


def _find_shape(slide, shape_index=None, shape_name=None):
    shapes = list(slide.shapes)
    if shape_index is not None:
        if not isinstance(shape_index, int) or isinstance(shape_index, bool):
            raise OfficeError("E_INPUT_SCHEMA", "shape_index 必须是整数")
        if shape_index < 0 or shape_index >= len(shapes):
            raise OfficeError(
                "E_ANCHOR_NOT_FOUND",
                "shape_index 越界: %s" % shape_index,
                detail="shape_count=%d" % len(shapes),
                remedy="先调用 pptx_outline 获取形状列表",
            )
        return shapes[shape_index]
    if shape_name:
        matches = [(index, shape) for index, shape in enumerate(shapes) if shape.name == shape_name]
        if len(matches) > 1:
            raise OfficeError('E_ANCHOR_NOT_FOUND', '形状名命中多处，拒绝模糊编辑',
                              detail='shape_name=%s indices=%s' % (shape_name, [index for index, _ in matches]),
                              remedy='使用 pptx_outline 返回的 shape_index 精确定位。')
        if matches:
            return matches[0][1]
        raise OfficeError(
            "E_ANCHOR_NOT_FOUND",
            "未找到形状: %s" % shape_name,
            remedy="先调用 pptx_outline 获取形状名",
        )
    raise OfficeError("E_INPUT_SCHEMA", "需要 shape_index 或 shape_name")


def _resolve_slide(prs, index: Any):
    if not isinstance(index, int) or isinstance(index, bool):
        raise OfficeError("E_INPUT_SCHEMA", "slide_index 必须是 0-based 整数")
    slides = list(prs.slides)
    if index < 0 or index >= len(slides):
        raise OfficeError(
            "E_ANCHOR_NOT_FOUND",
            "slide_index 越界: %s" % index,
            detail="slide_count=%d" % len(slides),
            remedy="先调用 pptx_outline 获取幻灯片列表",
        )
    return slides[index]


@register(
    "pptx_edit",
    schema="pptx_edit",
    engine="python-pptx",
    next_actions=["office_validate", "office_render_preview"],
)
def pptx_edit(args: Dict[str, Any]) -> Dict[str, Any]:
    require_pptx()
    from pptx.util import Emu, Pt  # type: ignore

    task_id, directory, source, output = _prepare(args)
    prs = _open(source)
    slide = _resolve_slide(prs, args.get("slide_index"))
    shape = _find_shape(slide, args.get("shape_index"), args.get("shape_name"))
    operation = str(args.get("operation") or "set_text")
    if operation not in ("set_text", "append_text", "set_position", "set_size", "set_font"):
        raise OfficeError(
            "E_INPUT_SCHEMA",
            "operation 必须是 set_text/append_text/set_position/set_size/set_font",
        )
    changed: List[str] = []
    if operation in ("set_text", "append_text"):
        if not shape.has_text_frame:
            raise OfficeError(
                "E_INPUT_SCHEMA", "目标形状没有文本框", detail=shape.name
            )
        text = str(args.get("text") or "")
        if operation == "set_text":
            # 替换文本保留首段/首 run 的外观；clear() 会移除 run 格式，导致
            # 设计好的字号、字体和颜色突然回退到母版。
            from copy import deepcopy
            first = shape.text_frame.paragraphs[0]
            paragraph_properties = deepcopy(first._p.pPr) if first._p.pPr is not None else None
            run_properties = deepcopy(first.runs[0]._r.rPr) if first.runs and first.runs[0]._r.rPr is not None else None
            shape.text_frame.clear()
            for index, line in enumerate(text.split("\n")):
                paragraph = shape.text_frame.paragraphs[0] if index == 0 else shape.text_frame.add_paragraph()
                paragraph.text = line
                if paragraph_properties is not None:
                    existing = paragraph._p.pPr
                    if existing is not None:
                        paragraph._p.remove(existing)
                    paragraph._p.insert(0, deepcopy(paragraph_properties))
                if paragraph.runs and run_properties is not None:
                    paragraph.runs[0]._r.insert(0, deepcopy(run_properties))
        else:
            paragraph = shape.text_frame.add_paragraph()
            paragraph.text = text
        changed.append(operation)
        if args.get("size_pt"):
            for paragraph in shape.text_frame.paragraphs:
                for run in paragraph.runs:
                    run.font.size = Pt(float(args["size_pt"]))
            changed.append("size_pt")
    elif operation == "set_position":
        if args.get("left_emu") is not None:
            shape.left = Emu(int(args["left_emu"]))
            changed.append("left")
        if args.get("top_emu") is not None:
            shape.top = Emu(int(args["top_emu"]))
            changed.append("top")
    elif operation == "set_size":
        if args.get("width_emu") is not None:
            shape.width = Emu(int(args["width_emu"]))
            changed.append("width")
        if args.get("height_emu") is not None:
            shape.height = Emu(int(args["height_emu"]))
            changed.append("height")
    else:
        if not shape.has_text_frame:
            raise OfficeError("E_INPUT_SCHEMA", "目标形状没有文本框", detail=shape.name)
        for paragraph in shape.text_frame.paragraphs:
            for run in paragraph.runs:
                if args.get("size_pt"):
                    run.font.size = Pt(float(args["size_pt"]))
                if args.get("bold") is not None:
                    run.font.bold = bool(args["bold"])
                if args.get("color_rgb"):
                    from pptx.dml.color import RGBColor  # type: ignore

                    run.font.color.rgb = RGBColor.from_string(str(args["color_rgb"]))
        changed.append("font")
    output.parent.mkdir(parents=True, exist_ok=True)
    atomic_save(prs, output)
    return {
        "artifacts": [artifact(output, role="in_place" if args.get("in_place") else "output")],
        "data": {"operation": operation, "changed": changed, "work_dir": str(directory)},
        "engine_version": engine_version("python-pptx"),
    }


@register(
    "pptx_notes",
    schema="pptx_notes",
    engine="python-pptx",
    next_actions=["office_validate"],
)
def pptx_notes(args: Dict[str, Any]) -> Dict[str, Any]:
    require_pptx()
    task_id, directory, source, output = _prepare(args)
    prs = _open(source)
    operation = str(args.get("operation") or "read")
    if operation not in ("read", "write"):
        raise OfficeError("E_INPUT_SCHEMA", "operation 必须是 read/write")
    if operation == "read":
        notes = []
        for index, slide in enumerate(prs.slides):
            text = ""
            if slide.has_notes_slide:
                text = slide.notes_slide.notes_text_frame.text
            notes.append({"slide": index, "notes": text})
        return {"data": {"notes": notes, "work_dir": str(directory)}}
    slide = _resolve_slide(prs, args.get("slide_index"))
    slide.notes_slide.notes_text_frame.text = str(args.get("text") or "")
    output.parent.mkdir(parents=True, exist_ok=True)
    atomic_save(prs, output)
    return {
        "artifacts": [artifact(output, role="in_place" if args.get("in_place") else "output")],
        "data": {"slide_index": args.get("slide_index"), "work_dir": str(directory)},
        "engine_version": engine_version("python-pptx"),
    }


@register(
    "pptx_media",
    schema="pptx_media",
    engine="python-pptx",
    next_actions=["office_validate", "office_render_preview"],
)
def pptx_media(args: Dict[str, Any]) -> Dict[str, Any]:
    require_pptx()
    task_id, directory, source, output = _prepare(args)
    prs = _open(source)
    slide = _resolve_slide(prs, args.get("slide_index"))
    image = resolve_path(args.get("image_path"), args=args, field="image_path", must_exist=True)
    left = int(args.get("left_emu") or 0)
    top = int(args.get("top_emu") or 0)
    width = args.get("width_emu")
    height = args.get("height_emu")
    from PIL import Image
    with Image.open(image) as handle:
        ratio = handle.height / handle.width
    if left < 0 or top < 0 or left >= prs.slide_width or top >= prs.slide_height:
        raise OfficeError("E_INPUT_SCHEMA", "图片起点必须位于幻灯片内部")
    if width is None and height is None:
        width = min(int(prs.slide_width * 0.8), prs.slide_width - left,
                    int((prs.slide_height - top) / ratio))
        height = int(width * ratio)
    elif width is None:
        width = int(height / ratio)
    elif height is None:
        height = int(width * ratio)
    if width <= 0 or height <= 0 or left + width > prs.slide_width or top + height > prs.slide_height:
        raise OfficeError("E_INPUT_SCHEMA", "图片显式尺寸超出幻灯片，请调整位置或尺寸")
    slide.shapes.add_picture(str(image), left, top, width=width, height=height)
    output.parent.mkdir(parents=True, exist_ok=True)
    atomic_save(prs, output)
    return {
        "artifacts": [artifact(output, role="in_place" if args.get("in_place") else "output")],
        "data": {"image": str(image), "width_emu": width, "height_emu": height, "work_dir": str(directory)},
        "engine_version": engine_version("python-pptx"),
    }
