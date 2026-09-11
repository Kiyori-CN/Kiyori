"""docx_edit / docx_find_replace / docx_merge。"""

from __future__ import annotations

from pathlib import Path
from typing import Any, Dict, List

from ..paths import atomic_save, artifact, resolve_output_path, resolve_path, resolve_task_id, task_dir
from ..protocol import OfficeError, engine_version, register
from ..readers.docx_reader import docx_outline, require_docx
from .runs import compile_pattern, merge_runs, merge_text_group, text_run_groups, document_text_stories, _replace_runs

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
    if operation not in ("replace", "insert_before", "insert_after", "delete", "insert_blocks_before", "insert_blocks_after", "set_formula", "replace_image", "set_chart"):
        raise OfficeError(
            "E_INPUT_SCHEMA",
            "operation 必须是 replace/insert_before/insert_after/delete",
            detail="operation=%r" % args.get("operation"),
        )

    document = _open_document(source)
    paragraph = _resolve_paragraph(document, anchor)
    if operation in {"set_formula", "replace_image", "set_chart"}:
        from docx.oxml.ns import qn
        index=args.get("object_index",0)
        if type(index) is not int or index<0:
            raise OfficeError("E_INPUT_SCHEMA", "object_index 必须为非负整数")
        if operation=="set_formula":
            objects=paragraph._p.xpath('.//m:oMath')
        elif operation=="replace_image":
            objects=paragraph._p.xpath('.//a:blip')
        else:
            objects=list(paragraph._p.iter('{http://schemas.openxmlformats.org/drawingml/2006/chart}chart'))
        if index>=len(objects):
            raise OfficeError("E_ANCHOR_NOT_FOUND", "指定段落中不存在该对象索引")
        target=objects[index]
        if operation=="set_formula":
            from ..math import parse_omml
            target.getparent().replace(target,parse_omml(args.get('omml')))
        elif operation=="replace_image":
            if target.get(qn('r:link')):
                raise OfficeError('E_FORMAT_UNSUPPORTED','不替换外部链接图片')
            image=resolve_path(args.get('image_path'),args=args,field='image_path',must_exist=True)
            old=target.get(qn('r:embed'))
            rid,_=document.part.get_or_add_image(str(image))
            target.set(qn('r:embed'),rid)
            if old and not any(node.get(qn('r:embed'))==old for node in document.element.iter()):
                document.part.drop_rel(old)
        else:
            from .chart import edit_chart
            if not set(args)&{'chart_data','chart_style'}:
                raise OfficeError('E_INPUT_SCHEMA','set_chart 需要 chart_data/chart_style')
            edit_chart(document,target,args)
    else:
        _check_plain_paragraph(document, paragraph, operation.replace("_blocks", ""))
    if operation in ("insert_blocks_before", "insert_blocks_after"):
        if "text" in args:
            raise OfficeError("E_INPUT_SCHEMA", "insert_blocks 使用 blocks，不能同时提供 text")
        from .create import _append_spec
        from docx.shared import Pt
        from docx.section import Section
        from docx.oxml.ns import qn
        # 分节属性定义它之前的内容；插入图片必须按锚点所在节计算宽高。
        after = operation == "insert_blocks_after"
        cursor = paragraph._p.getnext() if after else paragraph._p
        section = None
        while cursor is not None:
            properties = cursor.find(".//" + qn("w:sectPr")) if cursor.tag != qn("w:sectPr") else cursor
            if properties is not None:
                section = Section(properties, document.part)
                break
            cursor = cursor.getnext()
        before = set(document.element.body)
        _append_spec(document, {"blocks": args.get("blocks")}, Pt, args=args, section=section)
        added = [node for node in document.element.body if node not in before]
        reference = paragraph._p
        for node in added:
            if after:
                reference.addnext(node)
                reference = node
            else:
                paragraph._p.addprevious(node)
    elif operation == "replace":
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
        new_paragraph = _copy_paragraph(paragraph, str(args.get("text") or ""))
        if operation == "insert_before":
            paragraph._element.addprevious(new_paragraph._element)
        else:
            paragraph._element.addnext(new_paragraph._element)
    elif operation == "delete":
        paragraph._element.getparent().remove(paragraph._element)

    output.parent.mkdir(parents=True, exist_ok=True)
    atomic_save(document, output)
    return {
        "artifacts": [
            artifact(output, role="in_place" if args.get("in_place") else "output")
        ],
        "data": {"operation": operation, "changed": 1, "work_dir": str(directory)},
        "engine_version": engine_version("python-docx"),
    }


def _check_plain_paragraph(document, paragraph, operation):
    from docx.oxml.ns import qn

    # 整段替换/删除没有对象或引用语义，不得静默损坏公式、域和跨段书签。
    allowed = {qn('w:p'), qn('w:pPr'), qn('w:r'), qn('w:rPr'), qn('w:t'), qn('w:br'), qn('w:tab'), qn('w:cr')}
    structures = set()
    for child in paragraph._p if operation in ('replace', 'delete') else []:
        if child.tag == qn('w:pPr'):
            if operation == 'delete' and child.find(qn('w:sectPr')) is not None:
                structures.add('sectPr')
            continue
        if child.tag == qn('w:r'):
            structures.update(node.tag.rsplit('}', 1)[-1] for node in child if node.tag not in allowed)
        else:
            structures.add(child.tag.rsplit('}', 1)[-1])
    state = [0]
    for story in document_text_stories(document):
        for candidate in story:
            if candidate._p is paragraph._p:
                if operation == 'insert_after':
                    text_run_groups(candidate, state)
                if state[0]:
                    structures.add('field_continuation')
                break
            text_run_groups(candidate, state)
    if structures:
        index = next(i for i, item in enumerate(document.paragraphs) if item._p is paragraph._p)
        raise OfficeError('E_FORMAT_UNSUPPORTED', '段落编辑涉及受保护结构，未写入文件',
                          detail='paragraph_index=%d operation=%s structures=%s' % (index, operation, ', '.join(sorted(structures))),
                          remedy='普通文字使用 docx_find_replace；域、公式、书签或分节结构请在支持它们的文档编辑器中修改。')


def _copy_paragraph(paragraph, text):
    import copy as copy_module
    from docx.oxml import OxmlElement
    from docx.oxml.ns import qn
    from docx.text.paragraph import Paragraph  # type: ignore

    # 插入继承视觉格式，不复制原段的分节、书签、对象身份或修订历史。
    element = OxmlElement('w:p')
    if paragraph._p.pPr is not None:
        properties = copy_module.deepcopy(paragraph._p.pPr)
        for child in list(properties):
            if child.tag in (qn('w:sectPr'), qn('w:pPrChange')):
                properties.remove(child)
        element.append(properties)
    result = Paragraph(element, paragraph._parent)
    run = result.add_run(text)
    if paragraph.runs and paragraph.runs[0]._r.rPr is not None:
        run._r.insert(0, copy_module.deepcopy(paragraph.runs[0]._r.rPr))
    return result


@register(
    "docx_find_replace",
    schema="docx_find_replace",
    engine="python-docx",
    next_actions=["docx_outline", "office_validate", "office_render_preview"],
)
def docx_find_replace(args: Dict[str, Any]) -> Dict[str, Any]:
    """仅合并命中文字组后替换，保留未命中结构和格式。"""

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
    replacements = 0
    paragraphs_touched = 0
    runs_merged = 0
    from docx.oxml.ns import qn
    for story in document_text_stories(document):
        state = [0]
        for paragraph in story:
            groups = text_run_groups(paragraph, state)
            in_table = any(parent.tag == qn('w:tc') for parent in paragraph._p.iterancestors())
            if (scope == 'paragraphs' and in_table) or (scope == 'tables' and not in_table):
                continue
            hits = 0
            for group in groups:
                if pattern.search(''.join(run.text for run in group)) is not None:
                    group, merged = merge_text_group(group)
                    runs_merged += merged
                    hits += _replace_runs(group, pattern, replacement)
            replacements += hits
            paragraphs_touched += bool(hits)
    if replacements == 0:
        raise OfficeError(
            "E_ANCHOR_NOT_FOUND",
            "未找到可替换的内容",
            detail="find=%s scope=%s" % (args.get("find"), scope),
            remedy="先用 docx_outline/office_read 确认原文，再调整 find 或 use_regex",
        )

    output.parent.mkdir(parents=True, exist_ok=True)
    atomic_save(document, output)
    return {
        "artifacts": [
            artifact(output, role="in_place" if args.get("in_place") else "output")
        ],
        "data": {
            "replacements": replacements,
            "paragraphs_touched": paragraphs_touched,
            "runs_merged": runs_merged,
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
        from io import BytesIO
        from docx.oxml.ns import qn
        from lxml import etree

        # Body XML 中的 rId/样式/编号属于源包，不能直接拼接，否则图片和链接会
        # 指向另一份文档的关系。对无法保持语义的高级对象明确拒绝，不生成坏文件。
        relation_map = {}
        copied_chart_parts = {}
        drawing_id = base.part.next_id
        for element in incoming.element.body.iterchildren():
            if element.tag.split("}")[-1] == "sectPr":
                continue
            cloned = copy_module.deepcopy(element)
            for node in cloned.iter():
                if node.tag == qn('wp:docPr'):
                    node.set('id', str(drawing_id))
                    drawing_id += 1
                if node.tag in (qn('w:footnoteReference'), qn('w:endnoteReference'), qn('w:commentReference'), qn('w:numPr'), qn('w:sectPr')):
                    raise OfficeError("E_FORMAT_UNSUPPORTED", "合并包含编号、批注、脚注或分节，当前无法保证保真", remedy="保留原文件；需要保留这些结构时请在 Word/WPS 合并")
                if node.tag in (qn('w:pStyle'), qn('w:rStyle'), qn('w:tblStyle')):
                    style_id = node.get(qn('w:val'))
                    incoming_style = next((s for s in incoming.styles if s.style_id == style_id), None)
                    existing_style = next((s for s in base.styles if s.style_id == style_id), None)
                    if incoming_style is not None:
                        if existing_style is None:
                            base.styles.element.append(copy_module.deepcopy(incoming_style.element))
                        elif style_mode == 'preserve' and etree.tostring(existing_style.element) != etree.tostring(incoming_style.element):
                            raise OfficeError("E_FORMAT_UNSUPPORTED", "合并文档存在同名异义样式", detail=style_id, remedy="确认可统一样式后显式指定 style_mode=unified")
                for key, value in list(node.attrib.items()):
                    if not key.startswith('{http://schemas.openxmlformats.org/officeDocument/2006/relationships}'):
                        continue
                    if value not in relation_map:
                        rel = incoming.part.rels[value]
                        if rel.is_external:
                            relation_map[value] = base.part.relate_to(rel.target_ref, rel.reltype, is_external=True)
                        elif rel.reltype.endswith('/image'):
                            relation_map[value] = base.part.get_or_add_image(BytesIO(rel.target_part.blob))[0]
                        elif rel.reltype.endswith('/chart'):
                            from .chart import copy_chart_for_merge
                            chart = copy_chart_for_merge(rel.target_part, base.part.package, copied_chart_parts)
                            relation_map[value] = base.part.relate_to(chart, rel.reltype)
                        else:
                            raise OfficeError("E_FORMAT_UNSUPPORTED", "合并包含不支持的嵌入对象", detail=rel.reltype)
                    node.set(key, relation_map[value])
            base.element.body.insert_element_before(cloned, 'w:sectPr')
        merged += 1
    output.parent.mkdir(parents=True, exist_ok=True)
    atomic_save(base, output)
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
