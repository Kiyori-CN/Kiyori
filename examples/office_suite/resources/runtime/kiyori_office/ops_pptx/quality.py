"""版面报告与创建前文本估算；估算和渲染证据明确分开。"""

import math
import unicodedata

from ..protocol import OfficeError, register
from .layout import measure


def estimate_text(text, width_cm, size_pt=18, margin_cm=0.1, line_spacing=1.2):
    if not isinstance(text, str):
        raise OfficeError("E_INPUT_SCHEMA", "text 必须是字符串")
    width = measure(width_cm, "width_cm", 0.01, 200) * 72 / 2.54
    size = measure(size_pt, "size_pt", 1, 400)
    margin = measure(margin_cm, "margin_cm", 0, 10) * 72 / 2.54
    spacing = measure(line_spacing, "line_spacing", 0.5, 10)
    if width <= margin * 2:
        raise OfficeError("E_INPUT_SCHEMA", "width_cm 必须大于两侧 margin_cm")
    lines = 0
    for line in text.replace("\v", "\n").split("\n"):
        # 字族与真实换行规则未知时，只输出保守估算，不宣称实际渲染高度。
        units = sum(0 if unicodedata.combining(c) else 1 if unicodedata.east_asian_width(c) in "WF" else 0.6 for c in line)
        lines += max(1, math.ceil(units * size / (width - 2 * margin)))
    height = (lines * size * spacing + margin * 2) * 2.54 / 72
    return {"estimated_lines": lines, "estimated_height_cm": round(height, 3),
            "measurement_kind": "heuristic_not_font_shaping", "visual_verification_required": True}


@register("pptx_measure_text", schema="pptx_measure_text", next_actions=["pptx_edit", "office_render_preview"])
def measure_text(args):
    data = estimate_text(args.get("text"), args.get("width_cm"), args.get("size_pt", 18), args.get("margin_cm", 0.1), args.get("line_spacing", 1.2))
    if "height_cm" in args:
        data["estimated_overflow"] = data["estimated_height_cm"] > measure(args["height_cm"], "height_cm", 0.01, 200)
    return {"data": data}


def slide_report(slide, presentation):
    items = []
    issues = []
    def visit(shapes, parent=None, transform=(1, 1, 0, 0)):
        sx, sy, dx, dy = transform
        for index, shape in enumerate(shapes):
            left, top = shape.left * sx + dx, shape.top * sy + dy
            width, height = shape.width * sx, shape.height * sy
            entry = {"shape_id": shape.shape_id, "name": shape.name, "parent_id": parent, "z_index": index,
                     "bounds_cm": {k: round(v / 360000, 3) for k, v in zip(("left", "top", "width", "height"), (left, top, width, height))},
                     "rotation": shape.rotation, "text": shape.text if shape.has_text_frame else ""}
            if hasattr(shape, "shapes"):
                x = shape._element.grpSpPr.xfrm
                if x.chExt.cx and x.chExt.cy:
                    child_sx, child_sy = sx * x.ext.cx / x.chExt.cx, sy * x.ext.cy / x.chExt.cy
                    visit(shape.shapes, shape.shape_id, (child_sx, child_sy, dx + sx * x.off.x - child_sx*x.chOff.x, dy + sy*x.off.y - child_sy*x.chOff.y))
                entry["is_group"] = True
            if left < 0 or top < 0 or left + width > presentation.slide_width + 1 or top + height > presentation.slide_height + 1:
                issues.append({"code": "OUT_OF_BOUNDS", "shape_id": shape.shape_id})
            if shape.rotation:
                issues.append({"code": "ROTATED_BOUNDS_REQUIRE_VISUAL_REVIEW", "shape_id": shape.shape_id})
            if shape.has_text_frame and shape.text:
                sizes = [r.font.size.pt for p in shape.text_frame.paragraphs for r in p.runs if r.font.size]
                entry["font_sizes_pt"] = sorted(set(sizes))
                entry["font_inheritance_unresolved"] = any(r.font.size is None for p in shape.text_frame.paragraphs for r in p.runs)
                if sizes and width - shape.text_frame.margin_left - shape.text_frame.margin_right >= 3600:
                    # 框内部边距必须计入可用空间；段间距、字体字形仍属于估算限制。
                    measured = estimate_text(shape.text, (width - shape.text_frame.margin_left - shape.text_frame.margin_right) / 360000, max(sizes), 0)
                    measured["estimated_height_cm"] += (shape.text_frame.margin_top + shape.text_frame.margin_bottom) / 360000
                    entry.update(measured)
                    if measured["estimated_height_cm"] > height / 360000:
                        issues.append({"code": "POSSIBLE_TEXT_OVERFLOW", "shape_id": shape.shape_id, "evidence": "estimate"})
            if shape.has_table:
                entry["table"] = [[cell.text for cell in row.cells] for row in shape.table.rows]
            items.append(entry)
    visit(slide.shapes)
    overlaps = []
    leaves = [x for x in items if not x.get("is_group")]
    for i, a in enumerate(leaves):
        for b in leaves[i+1:]:
            if a["parent_id"] != b["parent_id"]:
                continue
            aa, bb = a["bounds_cm"], b["bounds_cm"]
            w = min(aa["left"]+aa["width"], bb["left"]+bb["width"]) - max(aa["left"], bb["left"])
            h = min(aa["top"]+aa["height"], bb["top"]+bb["height"]) - max(aa["top"], bb["top"])
            if w > 0.01 and h > 0.01:
                contains = (w*h >= min(aa["width"]*aa["height"], bb["width"]*bb["height"]) - 0.01)
                overlaps.append({"shape_ids": [a["shape_id"], b["shape_id"]], "kind": "containment" if contains else "intersection",
                                 "review_required": True})
    return {"elements": items, "issues": issues, "overlaps": overlaps,
            "limitations": ["overlap may be intentional", "font shaping and rotated geometry require rendered review"],
            "visual_verification_required": True}
