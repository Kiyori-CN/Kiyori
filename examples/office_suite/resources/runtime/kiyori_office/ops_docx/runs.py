"""DOCX run 预处理。

Word 会把同一句话拆进多个 ``<w:r>``（拼写检查、修订 id、输入法分段）。如果不做
run 合并，``docx_find_replace`` 会在真实文档上大面积失效：Demo 能跑，真文件不行。
本模块提供「不改渲染结果」的合并与跨 run 文本替换。
"""

from __future__ import annotations

import copy
import re
from typing import Any, Dict, List, Optional, Tuple

from ..protocol import OfficeError


def _run_properties_xml(run) -> Optional[str]:
    rpr = run._element.find(
        "{http://schemas.openxmlformats.org/wordprocessingml/2006/main}rPr"
    )
    if rpr is None:
        return None
    import lxml.etree as etree

    return etree.tostring(rpr, encoding="unicode")


def merge_runs(paragraph) -> int:
    """合并相邻同格式 run，返回合并掉的 run 数量。

    只合并 rPr 完全一致的相邻 run；带 ``<w:br>``/``<w:tab>``/图片等非文本子元素的
    run 一律跳过，避免改变渲染结果。
    """

    merged = 0
    runs = list(paragraph.runs)
    index = 0
    while index < len(runs) - 1:
        current = runs[index]
        following = runs[index + 1]
        if (current._element.getnext() is not following._element
                or not _is_pure_text_run(current) or not _is_pure_text_run(following)):
            index += 1
            continue
        if _run_properties_xml(current) != _run_properties_xml(following):
            index += 1
            continue
        current.text = (current.text or "") + (following.text or "")
        following._element.getparent().remove(following._element)
        runs.pop(index + 1)
        merged += 1
    return merged


def _is_pure_text_run(run) -> bool:
    element = run._element
    for child in element:
        tag = child.tag.split("}")[-1]
        if tag in ("rPr", "t"):
            continue
        return False
    return True


def merge_document_runs(document) -> Dict[str, int]:
    """对整个文档做 run 合并，返回统计信息。"""

    from ..readers.docx_reader import _iter_block_items  # noqa: PLC2701
    from docx.table import Table  # type: ignore
    from docx.text.paragraph import Paragraph  # type: ignore

    paragraphs = 0
    merged_runs = 0
    for block in _iter_block_items(document):
        if isinstance(block, Paragraph):
            before = len(block.runs)
            merged_runs += merge_runs(block)
            paragraphs += 1 if len(block.runs) != before else 0
        elif isinstance(block, Table):
            for row in block.rows:
                for cell in row.cells:
                    for paragraph in cell.paragraphs:
                        before = len(paragraph.runs)
                        merged_runs += merge_runs(paragraph)
                        paragraphs += 1 if len(paragraph.runs) != before else 0
    return {"paragraphs_changed": paragraphs, "runs_merged": merged_runs}


def replace_in_paragraph(paragraph, pattern: "re.Pattern[str]", replacement: str) -> int:
    """在段落内做跨 run 查找替换，保留首个命中 run 的格式。

    先把所有 run 文本拼成整段文本定位匹配，再把替换结果写回首个 run 并清空其余
    命中 run 的文本。这样即使一句话被拆成多个不同格式的 run 也能整体替换。
    """

    # 超链接、域、图片和书签是结构边界。不能拼接边界两侧的文字后清空整个 run，
    # 否则一次普通替换可能删除图片或移动书签；只处理连续的纯文本 run。
    return sum(_replace_runs(runs, pattern, replacement) for runs in text_run_groups(paragraph))


def text_run_groups(paragraph, field_state=None):
    """返回可编辑的连续文字；field_state 跨段落保留复杂域深度。

    域的显示缓存常是普通 w:r/w:t，不能只跳过 fldChar 所在 run，
    否则 REF/目录结果会被当正文修改。所有结构边界都终止当前组。
    """
    from docx.oxml.ns import qn
    from docx.text.run import Run

    state = field_state if field_state is not None else [0]
    groups = []
    current = []
    for child in paragraph._p:
        if child.tag == qn('w:pPr'):
            continue
        run = Run(child, paragraph) if child.tag == qn('w:r') else None
        if run is not None and state[0] == 0 and _is_pure_text_run(run):
            current.append(run)
        else:
            if current:
                groups.append(current)
                current = []
        for marker in child.iter(qn('w:fldChar')):
            kind = marker.get(qn('w:fldCharType'))
            if kind == 'begin':
                state[0] += 1
            elif kind == 'end':
                state[0] = max(0, state[0] - 1)
    if current:
        groups.append(current)
    return groups


def document_text_stories(document, include_headers=False):
    """遍历已有正文/页眉页脚，不通过访问器隐式创建空的 story。

    XML 段落遍历自然覆盖嵌套表格与合并单元格，已有共享 header/footer
    part 只出现一次，避免重复填充以及把替换结果再作为输入。
    """
    from docx.oxml.ns import qn
    from docx.text.paragraph import Paragraph

    roots = [document.element.body]
    if include_headers:
        seen = set()
        for rel in document.part.rels.values():
            if rel.is_external or rel.reltype.rsplit('/', 1)[-1] not in ('header', 'footer'):
                continue
            if rel.target_part not in seen:
                seen.add(rel.target_part)
                roots.append(rel.target_part.element)
    for root in roots:
        yield (Paragraph(element, document) for element in root.iter(qn('w:p')))


def _replace_runs(runs, pattern, replacement) -> int:
    if not runs:
        return 0
    texts = [run.text or "" for run in runs]
    combined = "".join(texts)
    if not combined:
        return 0
    matches = list(pattern.finditer(combined))
    if not matches:
        return 0

    # 计算每个 run 在整段文本中的 [start, end) 区间
    spans: List[Tuple[int, int]] = []
    cursor = 0
    for text in texts:
        spans.append((cursor, cursor + len(text)))
        cursor += len(text)

    replaced_count = 0
    touched = set()
    # 从后往前替换，避免偏移失效。callable 基于原始 match 解析模板值，
    # 单次扫描支持多个变量，不会重扫已插入文字。
    for match in reversed(matches):
        value = replacement(match) if callable(replacement) else replacement
        if value is None:
            continue
        start, end = match.start(), match.end()
        replaced = False
        for run_index, (span_start, span_end) in enumerate(spans):
            if span_start <= start < span_end or (start == end == span_start):
                head = texts[run_index][: start - span_start]
                tail_inside = texts[run_index][end - span_start :] if end <= span_end else ""
                texts[run_index] = head + value + tail_inside
                touched.add(run_index)
                # 清空后续被命中的 run 文本
                for other in range(run_index + 1, len(spans)):
                    other_start, other_end = spans[other]
                    if other_start >= end:
                        break
                    if other_end > start:
                        # 最后一个命中 run 可能还有未命中的尾部，必须保留其文字和格式。
                        texts[other] = texts[other][max(0, end - other_start):]
                        touched.add(other)
                replaced = True
                replaced_count += 1
                break
        if not replaced:
            continue
    # 一次模板调用可能在同一个 run 中命中数百次。先完成文本投影，
    # 每个命中 run 最后只写一次 XML，避免反复拆建同一段的 w:t/a:t。
    for index in sorted(touched):
        runs[index].text = texts[index]
    return replaced_count


def merge_text_group(runs):
    """只合并即将编辑的连续文字组，保留未命中段落和结构边界。"""
    merged = 0
    result = []
    for run in runs:
        if result and _run_properties_xml(result[-1]) == _run_properties_xml(run):
            result[-1].text += run.text
            run._element.getparent().remove(run._element)
            merged += 1
        else:
            result.append(run)
    return result, merged


def compile_pattern(
    find: str,
    *,
    use_regex: bool = False,
    ignore_case: bool = False,
) -> "re.Pattern[str]":
    if not isinstance(find, str) or not find:
        raise OfficeError("E_INPUT_SCHEMA", "find 必须是非空字符串")
    flags = re.IGNORECASE if ignore_case else 0
    try:
        return re.compile(find if use_regex else re.escape(find), flags)
    except re.error as exc:
        raise OfficeError(
            "E_INPUT_SCHEMA", "正则表达式非法", detail=str(exc), remedy="修正 find 或改用 use_regex=false"
        ) from exc
