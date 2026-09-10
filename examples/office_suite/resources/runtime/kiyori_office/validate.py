"""office_validate：OOXML/PDF 产物结构校验。

覆盖高频崩溃点，不要求完整 ECMA-376 XSD：

- ZIP 完整性与 ``[Content_Types].xml`` 覆盖；
- ``.rels`` 引用目标存在、无孤儿 media；
- docx 修订标记完整性、目录域刷新提示；
- xlsx 公式缓存值缺失与错误单元格清单；
- pptx ``<p:sldIdLst>`` 一致性、图表轴成对；
- pdf 头、加密与文本层。
"""

from __future__ import annotations

import posixpath
import re
import zipfile
from pathlib import Path
from typing import Any, Dict, List, Optional
from xml.etree import ElementTree

from .protocol import OfficeError

CT_NS = "http://schemas.openxmlformats.org/package/2006/content-types"
REL_NS = "http://schemas.openxmlformats.org/package/2006/relationships"


def validate_document(
    path: Path,
    *,
    original_path: Optional[Path] = None,
    strict: bool = False,
) -> Dict[str, Any]:
    suffix = path.suffix.lower()
    if suffix in (".docx", ".docm"):
        issues, summary = _validate_ooxml(path, family="docx")
    elif suffix in (".xlsx", ".xlsm"):
        issues, summary = _validate_ooxml(path, family="xlsx")
    elif suffix in (".pptx", ".pptm"):
        issues, summary = _validate_ooxml(path, family="pptx")
    elif suffix == ".pdf":
        issues, summary = _validate_pdf(path)
    else:
        raise OfficeError(
            "E_FORMAT_UNSUPPORTED",
            "office_validate 不支持该格式: %s" % (suffix or "(无扩展名)"),
        )

    baseline: List[Dict[str, Any]] = []
    if original_path is not None:
        baseline_issues, _baseline_summary = _validate_any(original_path)
        baseline = baseline_issues
        issues = _subtract_baseline(issues, baseline)

    report: Dict[str, Any] = {
        "path": str(path),
        "format": suffix.lstrip("."),
        "strict": strict,
        "issues": issues,
        "issue_count": len(issues),
        "baseline_issue_count": len(baseline),
        "summary": summary,
    }
    if issues and strict:
        # 宿主的失败气泡可能只显示 message；完整清单仍由 data.issues 持有。
        diagnostic = "; ".join(
            "%s [%s]: %s" % (item["code"], item["target"], item["message"])
            for item in issues[:5]
        )
        raise OfficeError(
            "E_VALIDATION_FAILED",
            "产物校验未通过（strict=true，%d 项）：%s" % (len(issues), diagnostic),
            detail="; ".join(issue["message"] for issue in issues[:10]),
            data=report,
            remedy="按 data.issues 修正后重新生成；不要用 strict=false 掩盖问题交付",
        )
    return report


def _validate_any(path: Path) -> Any:
    suffix = path.suffix.lower()
    if suffix in (".docx", ".docm", ".xlsx", ".xlsm", ".pptx", ".pptm"):
        return _validate_ooxml(path, family=suffix.lstrip("."))
    if suffix == ".pdf":
        return _validate_pdf(path)
    return [], {}


def _subtract_baseline(
    issues: List[Dict[str, Any]], baseline: List[Dict[str, Any]]
) -> List[Dict[str, Any]]:
    baseline_keys = {(issue.get("code"), issue.get("target")) for issue in baseline}
    return [
        issue
        for issue in issues
        if (issue.get("code"), issue.get("target")) not in baseline_keys
    ]


def _issue(code: str, message: str, target: str = "", severity: str = "error") -> Dict[str, Any]:
    return {"code": code, "message": message, "target": target, "severity": severity}


def _validate_ooxml(path: Path, *, family: str) -> Any:
    issues: List[Dict[str, Any]] = []
    summary: Dict[str, Any] = {}
    if not path.is_file():
        raise OfficeError("E_PATH_INVALID", "文件不存在", detail=str(path))
    try:
        archive = zipfile.ZipFile(path)
    except zipfile.BadZipFile as exc:
        raise OfficeError(
            "E_DOC_CORRUPT", "OOXML 不是有效的 ZIP 容器", detail=str(exc)
        ) from exc
    with archive:
        names = set(archive.namelist())
        summary["entry_count"] = len(names)
        summary["bytes"] = path.stat().st_size

        content_types = "[Content_Types].xml"
        if content_types not in names:
            issues.append(_issue("MISSING_CONTENT_TYPES", "缺少 [Content_Types].xml", content_types))
        else:
            issues.extend(_check_content_types(archive, names, content_types))

        rel_issues, rel_summary = _check_relationships(archive, names)
        issues.extend(rel_issues)
        summary.update(rel_summary)

        media = sorted(
            name for name in names if "/media/" in name and not name.endswith("/")
        )
        orphan_media = _find_orphan_media(archive, media)
        summary["media_count"] = len(media)
        summary["orphan_media"] = orphan_media
        for item in orphan_media:
            issues.append(
                _issue("ORPHAN_MEDIA", "media 未被任何关系引用", item, severity="warning")
            )

        if family in ("docx", "docm"):
            issues.extend(_check_docx(archive, names, summary))
        elif family in ("xlsx", "xlsm"):
            issues.extend(_check_xlsx(archive, names, summary))
        else:
            issues.extend(_check_pptx(archive, names, summary))
    return issues, summary


def _check_content_types(archive: zipfile.ZipFile, names: set, entry: str) -> List[Dict[str, Any]]:
    issues: List[Dict[str, Any]] = []
    try:
        root = ElementTree.fromstring(archive.read(entry))
    except ElementTree.ParseError as exc:
        return [_issue("CONTENT_TYPES_INVALID", "无法解析 [Content_Types].xml", str(exc))]
    defaults = {
        element.get("Extension", "").lower()
        for element in root.findall("{%s}Default" % CT_NS)
    }
    overrides = {
        element.get("PartName", "")
        for element in root.findall("{%s}Override" % CT_NS)
    }
    for name in sorted(names):
        if name == entry or name.endswith("/") or name.endswith(".rels"):
            continue
        extension = name.rsplit(".", 1)[-1].lower() if "." in name else ""
        if "/" + name in overrides or extension in defaults:
            continue
        issues.append(
            _issue(
                "CONTENT_TYPE_MISSING",
                "part 未被 [Content_Types].xml 覆盖",
                name,
                severity="warning",
            )
        )
    return issues


def _check_relationships(
    archive: zipfile.ZipFile, names: set
) -> Any:
    issues: List[Dict[str, Any]] = []
    rel_count = 0
    external_count = 0
    for name in sorted(item for item in names if item.endswith(".rels")):
        try:
            root = ElementTree.fromstring(archive.read(name))
        except ElementTree.ParseError as exc:
            issues.append(_issue("RELS_INVALID", "无法解析关系文件", "%s: %s" % (name, exc)))
            continue
        base = posixpath.dirname(posixpath.dirname(name))
        for relationship in root.findall("{%s}Relationship" % REL_NS):
            rel_count += 1
            if relationship.get("TargetMode") == "External":
                external_count += 1
                continue
            target = relationship.get("Target") or ""
            resolved = posixpath.normpath(posixpath.join(base, target))
            resolved = resolved.lstrip("/")
            if resolved not in names:
                issues.append(
                    _issue(
                        "RELS_TARGET_MISSING",
                        "关系引用的目标不存在",
                        "%s -> %s" % (name, resolved),
                    )
                )
    return issues, {"relationship_count": rel_count, "external_relationship_count": external_count}


def _find_orphan_media(archive: zipfile.ZipFile, media: List[str]) -> List[str]:
    referenced: set = set()
    for name in archive.namelist():
        if not name.endswith(".rels"):
            continue
        try:
            root = ElementTree.fromstring(archive.read(name))
        except ElementTree.ParseError:
            continue
        base = posixpath.dirname(posixpath.dirname(name))
        for relationship in root.findall("{%s}Relationship" % REL_NS):
            target = relationship.get("Target") or ""
            if relationship.get("TargetMode") == "External":
                continue
            referenced.add(posixpath.normpath(posixpath.join(base, target)).lstrip("/"))
    return [item for item in media if item not in referenced]


def _check_docx(archive: zipfile.ZipFile, names: set, summary: Dict[str, Any]) -> List[Dict[str, Any]]:
    issues: List[Dict[str, Any]] = []
    document_entry = "word/document.xml"
    if document_entry not in names:
        issues.append(_issue("MISSING_DOCUMENT", "缺少 word/document.xml", document_entry))
        return issues
    try:
        root = ElementTree.fromstring(archive.read(document_entry))
    except ElementTree.ParseError as exc:
        return [_issue("DOCUMENT_XML_INVALID", "word/document.xml 无法解析", str(exc))]
    namespace = "{http://schemas.openxmlformats.org/wordprocessingml/2006/main}"
    insertions = root.iter(namespace + "ins")
    deletions = root.iter(namespace + "del")
    insertion_count = sum(1 for _ in insertions)
    deletion_count = 0
    missing_del_text = 0
    for deletion in deletions:
        deletion_count += 1
        if not any(True for _ in deletion.iter(namespace + "delText")):
            missing_del_text += 1
    summary["revision_insertions"] = insertion_count
    summary["revision_deletions"] = deletion_count
    if missing_del_text:
        issues.append(
            _issue(
                "REVISION_DEL_TEXT_MISSING",
                "存在 <w:del> 但没有 <w:delText>，修订内容可能不完整",
                document_entry,
            )
        )
    field_codes = []
    for element in root.iter(namespace + "instrText"):
        if element.text:
            field_codes.append(element.text.strip())
    if any("TOC" in code.upper() for code in field_codes):
        issues.append(
            _issue(
                "TOC_NEEDS_UPDATE",
                "文档含目录域，交付前需要确认目录已刷新",
                document_entry,
                severity="warning",
            )
        )
    summary["field_codes"] = field_codes[:20]
    issues.extend(_check_docx_bookmarks(root, document_entry, summary))
    return issues


def _check_docx_bookmarks(root, entry, summary):
    namespace = '{http://schemas.openxmlformats.org/wordprocessingml/2006/main}'
    issues = []
    names = set()
    ids = set()
    active = {}
    anchors = []
    def own_nodes(node):
        yield node
        for child in node:
            if child.tag != namespace + 'p':
                yield from own_nodes(child)

    # 书签允许跨段落和交错范围；按 id 配对，不能用 XML 父子关系或栈判断。
    for paragraph_index, paragraph in enumerate(root.iter(namespace + 'p')):
        location = '%s#paragraph[%d]' % (entry, paragraph_index)
        # 文本框中的嵌套段落由自己的 paragraph 项处理，避免重复计数。
        for node in own_nodes(paragraph):
            if node.tag == namespace + 'bookmarkStart':
                identity, name = node.get(namespace + 'id'), node.get(namespace + 'name')
                if identity in ids or name in names:
                    issues.append(_issue('BOOKMARK_DUPLICATE', '重复书签 id 或名称：%s / %s' % (identity, name), location))
                if identity is None or not name:
                    issues.append(_issue('BOOKMARK_INVALID', '书签缺少 id 或名称', location))
                ids.add(identity)
                names.add(name)
                active.setdefault(identity, []).append(location)
            elif node.tag == namespace + 'bookmarkEnd':
                identity = node.get(namespace + 'id')
                if not active.get(identity):
                    issues.append(_issue('BOOKMARK_UNPAIRED', '书签结束没有对应起点：%s' % identity, location))
                else:
                    active[identity].pop()
            elif node.tag == namespace + 'hyperlink':
                anchor = node.get(namespace + 'anchor')
                # 带 r:id 的链接可指向外部文档中的书签，不能按本文件目标判断。
                external_id = node.get('{http://schemas.openxmlformats.org/officeDocument/2006/relationships}id')
                if anchor and not external_id:
                    anchors.append((anchor, location))
    for identity, locations in active.items():
        for location in locations:
            issues.append(_issue('BOOKMARK_UNPAIRED', '书签起点没有对应结束：%s' % identity, location))
    for anchor, location in anchors:
        if anchor not in names:
            issues.append(_issue('HYPERLINK_ANCHOR_MISSING', '内部链接书签不存在：%s' % anchor, location))
    summary['bookmark_count'] = len(ids)
    summary['internal_hyperlink_count'] = len(anchors)
    return issues


def _check_xlsx(archive: zipfile.ZipFile, names: set, summary: Dict[str, Any]) -> List[Dict[str, Any]]:
    issues: List[Dict[str, Any]] = []
    sheet_entries = sorted(
        name for name in names if name.startswith("xl/worksheets/sheet") and name.endswith(".xml")
    )
    summary["sheet_parts"] = len(sheet_entries)
    formula_count = 0
    error_cells: List[str] = []
    for entry in sheet_entries:
        try:
            root = ElementTree.fromstring(archive.read(entry))
        except ElementTree.ParseError as exc:
            issues.append(_issue("SHEET_XML_INVALID", "工作表 XML 无法解析", "%s: %s" % (entry, exc)))
            continue
        for cell in root.iter("{http://schemas.openxmlformats.org/spreadsheetml/2006/main}c"):
            formula = cell.find("{http://schemas.openxmlformats.org/spreadsheetml/2006/main}f")
            if formula is None:
                continue
            formula_count += 1
            value = cell.find("{http://schemas.openxmlformats.org/spreadsheetml/2006/main}v")
            text = value.text if value is not None else None
            if text is None:
                error_cells.append("%s!%s" % (entry, cell.get("r")))
            elif str(text).startswith("#"):
                error_cells.append("%s!%s=%s" % (entry, cell.get("r"), text))
    summary["formula_count"] = formula_count
    summary["formula_issue_count"] = len(error_cells)
    if error_cells:
        issues.append(
            _issue(
                "FORMULA_NOT_RECALCULATED",
                "公式单元格缺少缓存值或存在错误值，必须执行 xlsx_recalc",
                ", ".join(error_cells[:10]),
            )
        )
    return issues


def _check_pptx(archive: zipfile.ZipFile, names: set, summary: Dict[str, Any]) -> List[Dict[str, Any]]:
    issues: List[Dict[str, Any]] = []
    presentation = "ppt/presentation.xml"
    if presentation not in names:
        issues.append(_issue("MISSING_PRESENTATION", "缺少 ppt/presentation.xml", presentation))
        return issues
    try:
        root = ElementTree.fromstring(archive.read(presentation))
    except ElementTree.ParseError as exc:
        return [_issue("PRESENTATION_XML_INVALID", "presentation.xml 无法解析", str(exc))]
    namespace = "{http://schemas.openxmlformats.org/presentationml/2006/main}"
    rel_ns = "{http://schemas.openxmlformats.org/officeDocument/2006/relationships}"
    slide_ids = [
        element.get(rel_ns + "id")
        for element in root.iter(namespace + "sldId")
    ]
    slide_parts = sorted(
        name for name in names if re.fullmatch(r"ppt/slides/slide\d+\.xml", name)
    )
    summary["slide_count"] = len(slide_parts)
    summary["sld_id_count"] = len(slide_ids)
    if len(slide_parts) != len(slide_ids):
        issues.append(
            _issue(
                "SLDIDLST_MISMATCH",
                "ppt/slides 数量与 <p:sldIdLst> 条目不一致",
                "slides=%d sldIds=%d" % (len(slide_parts), len(slide_ids)),
            )
        )
    # 数量相同不代表 sldId 真正映射到不同有效页面；按 relationship 身份核验。
    presentation_rels = 'ppt/_rels/presentation.xml.rels'
    if presentation_rels in names:
        try:
            relations = ElementTree.fromstring(archive.read(presentation_rels))
        except ElementTree.ParseError:
            relations = []  # 通用关系检查已经报告解析错误。
        targets = {}
        for relation in relations:
            if relation.get('Type', '').endswith('/slide') and relation.get('TargetMode') != 'External':
                targets[relation.get('Id')] = posixpath.normpath(posixpath.join('ppt', relation.get('Target', ''))).lstrip('/')
        seen_targets = set()
        for index, identity in enumerate(slide_ids):
            target = targets.get(identity)
            if target not in names or target in seen_targets:
                issues.append(_issue('SLIDE_RELATION_INVALID', '页面关系缺失或重复：%s -> %s' % (identity, target),
                                     '%s#slide[%d]' % (presentation, index)))
            seen_targets.add(target)
    for entry in slide_parts:
        try:
            slide = ElementTree.fromstring(archive.read(entry))
        except ElementTree.ParseError as exc:
            issues.append(_issue('SLIDE_XML_INVALID', '页面 XML 无法解析', '%s: %s' % (entry, exc)))
            continue
        ids = [node.get('id') for node in slide.iter(namespace + 'cNvPr')]
        if len(ids) != len(set(ids)):
            issues.append(_issue('SHAPE_ID_DUPLICATE', '页内对象 ID 重复，增量编辑或动画定位不可靠', entry))
        time_ids = [node.get('id') for node in slide.iter(namespace + 'cTn')]
        if len(time_ids) != len(set(time_ids)):
            issues.append(_issue('ANIMATION_TIME_ID_DUPLICATE', '动画时间节点 ID 重复', entry))
        for kind in ('spTgt', 'bldP'):
            for node in slide.iter(namespace + kind):
                if node.get('spid') not in ids:
                    issues.append(_issue('ANIMATION_TARGET_MISSING', '动画引用不存在的对象', '%s#%s' % (entry,node.get('spid'))))
        for node in slide.iter(namespace + 'tn'):
            if node.get('val') not in time_ids:
                issues.append(_issue('ANIMATION_TIME_TARGET_MISSING', '动画引用不存在的时间节点', '%s#%s' % (entry,node.get('val'))))
    for entry in sorted(name for name in names if name.startswith("ppt/charts/chart")):
        if not entry.endswith(".xml"):
            continue
        try:
            chart_root = ElementTree.fromstring(archive.read(entry))
        except ElementTree.ParseError as exc:
            issues.append(_issue("CHART_XML_INVALID", "图表 XML 无法解析", "%s: %s" % (entry, exc)))
            continue
        chart_ns = "{http://schemas.openxmlformats.org/drawingml/2006/chart}"
        value_axes = len(list(chart_root.iter(chart_ns + "valAx")))
        category_axes = len(list(chart_root.iter(chart_ns + "catAx")))
        # 散点/气泡图使用两条数值轴，不能按普通分类图的 valAx/catAx 配对拒绝。
        numeric_xy = any(list(chart_root.iter(chart_ns + kind)) for kind in ("scatterChart", "bubbleChart"))
        if (numeric_xy and (value_axes < 2 or category_axes != 0)) or (not numeric_xy and (value_axes == 0) != (category_axes == 0)):
            issues.append(
                _issue(
                    "CHART_AXIS_PAIR_MISMATCH",
                    "图表 valAx/catAxes 未成对",
                    "%s valAx=%d catAx=%d" % (entry, value_axes, category_axes),
                )
            )
    return issues


def _validate_pdf(path: Path) -> Any:
    issues: List[Dict[str, Any]] = []
    summary: Dict[str, Any] = {"bytes": path.stat().st_size}
    try:
        from pypdf import PdfReader  # type: ignore
    except Exception as exc:
        raise OfficeError(
            "E_ENV_MISSING",
            "PDF 校验需要 pypdf（Tier1）",
            detail=str(exc),
            remedy="调用 office_env_setup 安装 Tier1 组件",
        ) from exc
    with path.open("rb") as stream:
        header = stream.read(5)
    if header != b"%PDF-":
        issues.append(_issue("PDF_HEADER_INVALID", "缺少 %PDF- 文件头", str(path)))
        return issues, summary
    try:
        reader = PdfReader(str(path))
        summary["encrypted"] = bool(reader.is_encrypted)
        if reader.is_encrypted:
            issues.append(_issue("PDF_ENCRYPTED", "PDF 仍处于加密状态，请先解密后校验页面", str(path), severity="warning"))
            return issues, summary
        summary["pages"] = len(reader.pages)
    except Exception as exc:
        issues.append(_issue("PDF_PARSE_FAILED", "PDF 解析失败", str(exc)))
        return issues, summary
    empty_pages = []
    for index, page in enumerate(reader.pages):
        try:
            if not (page.extract_text() or "").strip():
                empty_pages.append(index + 1)
        except Exception:
            empty_pages.append(index + 1)
    summary["pages_without_text"] = empty_pages
    if empty_pages:
        issues.append(
            _issue(
                "PDF_PAGE_WITHOUT_TEXT_LAYER",
                "部分页面没有文本层（可能是扫描件或字体缺字）",
                "pages=%s" % empty_pages[:20],
                severity="warning",
            )
        )
    return issues, summary
