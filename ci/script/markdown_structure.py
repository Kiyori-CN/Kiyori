"""自有文档的 GFM 常用结构检查；不执行示例或访问外部 URL。"""

from __future__ import annotations

from collections.abc import Container
from html import unescape
from html.parser import HTMLParser
import posixpath
import re
import unicodedata
from urllib.parse import unquote, urlsplit

from check_markdown_links import inline_targets, mask_code_spans, parse_destination, DEFINITION_RE


class HtmlReferences(HTMLParser):
    def __init__(self, text: str) -> None:
        super().__init__()
        self.ids: set[str] = set()
        self.targets: list[tuple[int, str]] = []
        self.feed(text)

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        values = dict(attrs)
        for key in ("id", "name" if tag == "a" else "id"):
            if values.get(key):
                self.ids.add(values[key])
        for key in ("href", "src"):
            if values.get(key):
                self.targets.append((self.getpos()[0], values[key]))


def heading_slug(value: str) -> str:
    """保留中文、下划线和连字符，移除 GFM 标题中的格式与标点。"""
    value = re.sub(r"<[^>]*>", "", value)
    value = re.sub(r"!?\[([^\]]+)\]\([^)]*\)", r"\1", value)
    value = unescape(value).lower()
    return "".join(
        "-" if character == " " else character
        for character in value
        if character in " _-" or unicodedata.category(character)[0] not in "PSCZ"
    )


def heading_anchors(headings: list[tuple[int, int, str]]) -> list[tuple[int, int, str, str]]:
    used: set[str] = set()
    output = []
    for number, level, label in headings:
        base = heading_slug(label)
        anchor = base
        suffix = 0
        while anchor in used:
            suffix += 1
            anchor = f"{base}-{suffix}"
        used.add(anchor)
        output.append((number, level, label, anchor))
    return output


def table_cells(line: str) -> list[str]:
    # GFM 要求代码跨度中的管道也转义；不能先 mask_code_spans 后把畸形表格放行。
    cells: list[str] = []
    start = 0
    for index, character in enumerate(line):
        if character != "|":
            continue
        backslashes = len(line[:index]) - len(line[:index].rstrip("\\"))
        if backslashes % 2 == 0:
            cells.append(line[start:index].strip())
            start = index + 1
    cells.append(line[start:].strip())
    if cells and not cells[0]:
        cells.pop(0)
    if cells and not cells[-1]:
        cells.pop()
    return cells


def block_issues(path: str, text: str, prose: list[tuple[int, str]]) -> list[str]:
    issues = []
    lines = text.splitlines()
    visible = dict(prose)
    for number, line in prose:
        if re.match(r"^\s*\|?\s*:?-{3,}:?\s*\|", line):
            count = len(table_cells(line))
            if number > 1 and number - 1 in visible:
                if len(table_cells(lines[number - 2])) != count:
                    issues.append(f"{path}:{number - 1} 表头与分隔行列数不一致")
            index = number
            while index < len(lines) and index + 1 in visible and lines[index].lstrip().startswith("|"):
                actual = len(table_cells(lines[index]))
                if actual != count:
                    issues.append(f"{path}:{index + 1} 表格应有 {count} 列，实际 {actual}；单元内管道须转义")
                index += 1
        if re.match(r"^(?:[-+*]|\d+[.)]) ", line) and number > 1:
            previous = lines[number - 2]
            if (number - 1 in visible and previous.strip()
                    and not re.match(r"^(?:\s|[-+*] |\d+[.)] |<!--)", previous)):
                issues.append(f"{path}:{number} 列表前缺少空行")
    if text and not text.endswith("\n"):
        issues.append(f"{path}:{len(lines)} 文件末尾缺少换行")
    return issues


def local_reference_issues(
    path: str, prose: list[tuple[int, str]], anchors: dict[str, set[str]], existing: Container[str],
) -> list[str]:
    # 保持原行号，HTML 注释不会被 HTMLParser 当作链接。
    visible = dict(prose)
    html_text = "\n".join(visible.get(i, "") for i in range(1, max(visible, default=0) + 1))
    html = HtmlReferences("\n".join(mask_code_spans(line) for line in html_text.splitlines()))
    targets = list(html.targets)
    for number, line in prose:
        targets.extend((number, target) for target in inline_targets(line))
        definition = DEFINITION_RE.match(mask_code_spans(line))
        if definition:
            target, _ = parse_destination(definition[1])
            if target:
                targets.append((number, target))
    issues = []
    for number, target in targets:
        try:
            parsed = urlsplit(target)
        except ValueError:
            issues.append(f"{path}:{number} URL 语法无效：{target}")
            continue
        if parsed.scheme or parsed.netloc or not target:
            continue
        destination = unquote(parsed.path)
        resolved = (posixpath.normpath(destination.lstrip("/")) if destination.startswith("/") else
                    posixpath.normpath(posixpath.join(posixpath.dirname(path), destination))) if destination else path
        if resolved == ".." or resolved.startswith("../") or resolved not in existing:
            issues.append(f"{path}:{number} 本地资源不存在：{target}")
        elif parsed.fragment and resolved in anchors and unquote(parsed.fragment) not in anchors[resolved]:
            issues.append(f"{path}:{number} 本地锚点不存在：{target}")
    return sorted(set(issues))
