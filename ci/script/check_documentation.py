#!/usr/bin/env python3
"""检查父仓库 Markdown 工作区，并维护确定性的文档目录。"""

from __future__ import annotations

import argparse
from html.parser import HTMLParser
import posixpath
import re
import subprocess
from pathlib import Path

from check_markdown_links import check_file, fence_marker, mask_code_spans

# 这些文件是协议夹具或第三方/运行时资源，链接仍检查，正文不作为开发文档改写。
CONTENT_FIXTURES = {
    "docs/doc-src/test-example/chat_import_markdown_example.md",
}
CATALOGS = {"docs/CATALOG.md", "docs/TODO/catalog.md"}


def documents(root: Path) -> list[str]:
    result = subprocess.run(
        ["git", "-C", str(root), "ls-files", "-z", "--cached", "--others", "--exclude-standard"],
        check=True, capture_output=True,
    )
    return sorted({
        path for path in result.stdout.decode("utf-8").split("\0")
        if path.endswith(".md") and (root / path).is_file()
    })


def prose_lines(text: str) -> list[tuple[int, str]]:
    """围栏长度必须匹配；代码样例中的标题、空格和反引号保持原义。"""
    output = []
    fence = None
    frontmatter = text.startswith("---\n")
    for number, line in enumerate(text.splitlines(), 1):
        if frontmatter:
            if number > 1 and line == "---":
                frontmatter = False
            continue
        marker = fence_marker(line)
        if fence:
            if marker and marker[0] == fence[0] and marker[1] >= fence[1] and not marker[2].strip():
                fence = None
            continue
        if marker and not (marker[0] == "`" and "`" in marker[2]):
            fence = marker[:2]
            continue
        output.append((number, line))
    return output


def headings(text: str) -> list[tuple[int, int, str]]:
    result = []
    for number, line in prose_lines(text):
        match = re.match(r"^(#{1,6})\s+(.+?)(?:\s+#+)?$", line)
        if match:
            result.append((number, len(match[1]), match[2]))
    return result


class HtmlTitles(HTMLParser):
    def __init__(self, text: str) -> None:
        super().__init__()
        self.values: list[str] = []
        self.active = False
        self.feed("\n".join(mask_code_spans(line) for _, line in prose_lines(text)))

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        if tag == "h1":
            self.values.append("")
            self.active = True

    def handle_endtag(self, tag: str) -> None:
        if tag == "h1":
            self.active = False

    def handle_data(self, data: str) -> None:
        if self.active:
            self.values[-1] += data


def title(text: str, path: str) -> str:
    values = [value for _, level, value in headings(text) if level == 1]
    values.extend(value.strip() for value in HtmlTitles(text).values)
    return values[0] if values else Path(path).stem


def style_managed(path: str) -> bool:
    return path not in CONTENT_FIXTURES and not path.startswith((
        "app/src/main/assets/", "docs/legal/", "examples/template_try/resources/",
    ))


def style_issues(path: str, text: str) -> list[str]:
    if not style_managed(path):
        return []
    issues = []
    hs = headings(text)
    h1 = sum(level == 1 for _, level, _ in hs) + len(HtmlTitles(text).values)
    if h1 != 1:
        issues.append(f"{path}:1 一级标题数量应为 1，实际 {h1}")
    previous = 1
    for number, level, _ in hs:
        if level > previous + 1:
            issues.append(f"{path}:{number} 标题层级从 {previous} 跳到 {level}")
        previous = level
    if "\ufffd" in text:
        issues.append(f"{path}:1 包含 Unicode replacement character")
    lines = text.splitlines()
    for number, line in prose_lines(text):
        if (line and not line.strip()) or line.endswith("\t") or (line.endswith(" ") and not line.endswith("  ")):
            issues.append(f"{path}:{number} 无意义尾随空白")
        if re.match(r"^#{1,6} ", line):
            if number > 1 and lines[number - 2].strip() and lines[number - 2] != "---":
                issues.append(f"{path}:{number} 标题前缺少空行")
            if number < len(lines) and lines[number].strip():
                issues.append(f"{path}:{number} 标题后缺少空行")
    return issues


def catalog_content(paths: list[str], texts: dict[str, str]) -> dict[str, str]:
    all_docs = ["# 文档完整目录", "", "本页由 `ci/script/check_documentation.py --write-catalogs` 生成。按主题查阅请先进入 [文档中心](README.md)。只收录父仓库维护的文档；子模块文档由各自仓库维护。", ""]
    grouped: dict[str, list[str]] = {}
    for path in paths:
        if path in CATALOGS or path.startswith("docs/TODO/history/"):
            continue
        parent = posixpath.dirname(path) or "根目录"
        grouped.setdefault(parent, []).append(path)
    for group, members in sorted(grouped.items()):
        all_docs.extend([f"## {group}", "", "| 文档 | 用途或标题 |", "| --- | --- |"])
        for path in members:
            name = title(texts[path], path).replace("|", "\\|")
            link = posixpath.relpath(path, "docs")
            all_docs.append(f"| [{Path(path).name}]({link}) | {name} |")
        all_docs.append("")
    all_docs += ["## 历史证据", "", "旧 TODO 总索引的逐项证据集中在 [历史目录](TODO/history/README.md)，不与当前计划重复列出。", ""]
    tasks = ["# 开发专项目录", "", "本页由 `ci/script/check_documentation.py --write-catalogs` 生成，收录每个专项的唯一 index。状态摘要由各专项维护，本目录不推断完成状态。日常入口见 [开发任务索引](README.md)。", "", "| 专项 | 目录 |", "| --- | --- |"]
    for path in paths:
        if re.fullmatch(r"docs/TODO/[^/]+/index\.md", path):
            label = title(texts[path], path).replace("|", "\\|")
            tasks.append(f"| [{label}]({posixpath.relpath(path, 'docs/TODO')}) | `{Path(path).parent.name}` |")
    return {"docs/CATALOG.md": "\n".join(all_docs), "docs/TODO/catalog.md": "\n".join(tasks) + "\n"}


class ExistingPaths:
    def __init__(self, root: Path) -> None:
        self.root = root

    def __contains__(self, path: str) -> bool:
        return (self.root / path).exists()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repository", type=Path, default=Path.cwd())
    parser.add_argument("--write-catalogs", action="store_true", help="只更新两个派生目录，不修改正文")
    args = parser.parse_args()
    root = args.repository.resolve()
    paths = documents(root)
    issues = []
    texts: dict[str, str] = {}
    existing = ExistingPaths(root)
    for path in paths:
        raw = (root/path).read_bytes()
        try:
            text = raw.decode("utf-8-sig")
        except UnicodeDecodeError as error:
            issues.append(f"{path}:1 非 UTF-8：{error}")
            continue
        texts[path] = text
        if style_managed(path) and raw.startswith(b"\xef\xbb\xbf"):
            issues.append(f"{path}:1 不应包含 UTF-8 BOM")
        if style_managed(path) and b"\r" in raw:
            issues.append(f"{path}:1 换行应使用 LF")
        issues.extend(style_issues(path, text))
        issues.extend(f"{i.path}:{i.line} 断链：{i.target}" for i in check_file(path, text, existing))
    # 无法解码时保留原目录，避免把损坏文档从目录静默移除或在生成时崩溃。
    if len(texts) == len(paths):
        for path, expected in catalog_content(paths, texts).items():
            if args.write_catalogs:
                (root/path).parent.mkdir(parents=True, exist_ok=True)
                (root/path).write_text(expected, encoding="utf-8", newline="\n")
            elif texts.get(path) != expected:
                issues.append(f"{path}:1 目录已过期，请运行 --write-catalogs")
    for issue in issues:
        print(issue)
    print(f"文档检查：{len(paths)} 个文件，{len(issues)} 个问题。工作区检查不替代 Git 候选树检查或外网可用性验证。")
    return 1 if issues else 0


if __name__ == "__main__":
    raise SystemExit(main())
