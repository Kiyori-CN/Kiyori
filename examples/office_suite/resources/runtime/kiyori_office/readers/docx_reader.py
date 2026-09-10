"""DOCX 结构读取。

关键点：Word 会把同一句话拆进多个 ``<w:r>``，因此按 ``<w:p>`` 聚合 ``<w:t>``
文本，不能按 run 逐个取值，否则「文档里看得见的字符串」在 XML 里不连续。
"""

from __future__ import annotations

import zipfile
from pathlib import Path
from typing import Any, Dict, List, Optional

from ..protocol import OfficeError

W_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"

try:
    import docx  # type: ignore
    from docx.document import Document as DocxDocument  # type: ignore
    from docx.table import Table  # type: ignore
    from docx.text.paragraph import Paragraph  # type: ignore

    DOCX_AVAILABLE = True
except Exception:  # pragma: no cover - 环境缺失时给出 E_ENV_MISSING
    DOCX_AVAILABLE = False


def require_docx() -> None:
    if not DOCX_AVAILABLE:
        raise OfficeError(
            "E_ENV_MISSING",
            "DOCX 处理需要 python-docx（Tier1）",
            detail="python-docx is not importable",
            remedy="调用 office_env_setup 安装 Tier1 组件，或先安装 python-docx",
        )


def _style_name(paragraph: "Paragraph") -> str:
    try:
        return str(paragraph.style.name or "")
    except Exception:
        return ""


def _heading_level(style_name: str) -> Optional[int]:
    lowered = style_name.lower()
    if lowered.startswith("heading"):
        tail = lowered.replace("heading", "").strip()
        if tail.isdigit():
            return int(tail)
    if lowered in ("title", "subtitle"):
        return 0 if lowered == "title" else 1
    return None


def _iter_block_items(document: "DocxDocument"):
    """按文档顺序遍历段落与表格。"""

    body = document.element.body
    for child in body.iterchildren():
        tag = child.tag.split("}")[-1]
        if tag == "p":
            yield Paragraph(child, document)
        elif tag == "tbl":
            yield Table(child, document)


def _paragraph_objects(paragraph, document):
    from lxml import etree
    from docx.oxml.ns import qn
    result = []
    for index, formula in enumerate(paragraph._p.xpath('.//m:oMath')):
        xml = etree.tostring(formula, encoding='unicode')
        result.append({'type': 'formula', 'object_index': index, 'omml': xml[:12000], 'truncated': len(xml)>12000})
    for index, image in enumerate(paragraph._p.xpath('.//a:blip')):
        result.append({'type': 'image', 'object_index': index, 'external': image.get(qn('r:link')) is not None})
    namespace = '{http://schemas.openxmlformats.org/drawingml/2006/chart}'
    for index, chart in enumerate(paragraph._p.iter(namespace+'chart')):
        entry = {'type': 'chart', 'object_index': index}
        relation = document.part.rels.get(chart.get(qn('r:id')))
        if relation is not None and not relation.is_external:
            root = etree.fromstring(relation.target_part.blob)
            series = []
            for series_node in root.iter(namespace+'ser'):
                value = {}
                for field, tag in (('name','tx'),('categories','cat'),('values','val')):
                    parent = series_node.find(namespace+tag)
                    if parent is not None:
                        values = [node.text for node in parent.iter(namespace+'v')]
                        value[field] = values[:100]
                        if len(values)>100:
                            value['truncated'] = True
                series.append(value)
            entry['cached_series'] = series
        result.append(entry)
    return result


def docx_outline(path: Path, *, max_items: int = 0) -> Dict[str, Any]:
    """返回段落索引、样式、层级、表格坐标与章节信息。"""

    require_docx()
    try:
        document = docx.Document(str(path))
    except zipfile.BadZipFile as exc:
        raise OfficeError(
            "E_DOC_CORRUPT", "DOCX 不是有效的 ZIP 容器", detail=str(exc)
        ) from exc
    except Exception as exc:
        raise OfficeError(
            "E_DOC_CORRUPT",
            "DOCX 打开失败",
            detail="%s: %s" % (type(exc).__name__, exc),
        ) from exc

    paragraphs: List[Dict[str, Any]] = []
    tables: List[Dict[str, Any]] = []
    headings: List[Dict[str, Any]] = []
    index = 0
    table_index = 0
    for block in _iter_block_items(document):
        if isinstance(block, Paragraph):
            text = block.text
            style_name = _style_name(block)
            level = _heading_level(style_name)
            item = {
                "index": index,
                "text": text,
                "style": style_name,
                "heading_level": level,
                "runs": len(block.runs),
                "objects": _paragraph_objects(block, document),
            }
            paragraphs.append(item)
            if level is not None and text.strip():
                headings.append(
                    {"index": index, "level": level, "text": text, "style": style_name}
                )
            index += 1
        else:
            rows: List[List[str]] = []
            for row in block.rows:
                rows.append([cell.text for cell in row.cells])
            tables.append(
                {
                    "index": table_index,
                    "rows": len(block.rows),
                    "columns": len(block.columns),
                    "preview": rows[:5],
                }
            )
            table_index += 1

    sections: List[Dict[str, Any]] = []
    for section_index, section in enumerate(document.sections):
        sections.append(
            {
                "index": section_index,
                "orientation": str(section.orientation),
                "page_width_twips": section.page_width.twips if section.page_width else None,
                "page_height_twips": section.page_height.twips if section.page_height else None,
                "left_margin_twips": section.left_margin.twips if section.left_margin else None,
                "right_margin_twips": section.right_margin.twips if section.right_margin else None,
            }
        )

    images: List[Dict[str, Any]] = []
    for rel_index, relationship in enumerate(document.part.rels.values()):
        if "image" in relationship.reltype:
            images.append(
                {
                    "index": rel_index,
                    "target": relationship.target_ref,
                    "external": bool(relationship.is_external),
                }
            )

    payload: Dict[str, Any] = {
        "paragraphs": paragraphs,
        "paragraph_count": len(paragraphs),
        "tables": tables,
        "table_count": len(tables),
        "headings": headings,
        "sections": sections,
        "images": images,
    }
    if max_items > 0:
        payload["paragraphs"] = paragraphs[:max_items]
        payload["paragraphs_truncated"] = len(paragraphs) > max_items
    return payload


def docx_read_text(path: Path) -> str:
    """按文档顺序输出可读文本，表格以制表符分隔。"""

    require_docx()
    try:
        document = docx.Document(str(path))
    except zipfile.BadZipFile as exc:
        raise OfficeError(
            "E_DOC_CORRUPT", "DOCX 不是有效的 ZIP 容器", detail=str(exc)
        ) from exc
    except Exception as exc:
        raise OfficeError(
            "E_DOC_CORRUPT",
            "DOCX 打开失败",
            detail="%s: %s" % (type(exc).__name__, exc),
        ) from exc

    lines: List[str] = []
    for block in _iter_block_items(document):
        if isinstance(block, Paragraph):
            lines.append(block.text)
        else:
            for row in block.rows:
                lines.append("\t".join(cell.text for cell in row.cells))
    return "\n".join(lines)
