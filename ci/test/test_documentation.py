from __future__ import annotations

import contextlib
import io
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch


sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "ci" / "script"))

import check_documentation as docs
from markdown_structure import heading_anchors, local_reference_issues, table_cells


class DocumentationTest(unittest.TestCase):
    def test_portable_names_reject_case_collisions_and_unpadded_steps(self) -> None:
        self.assertEqual(docs.naming_issues(["docs/TODO/example/01_readme.md", "docs/doc-src/dev-core/BUILDING.md"]), [])
        issues = docs.naming_issues(["docs/Guide/a.md", "docs/guide/b.md", "docs/TODO/example/1_Readme.md"])
        self.assertTrue(any("碰撞" in issue for issue in issues))
        self.assertTrue(any("两位编号" in issue for issue in issues))

    def test_toc_marker_examples_inside_fences_are_not_generated(self) -> None:
        text = "# Guide\n\n```markdown\n<!-- doc-toc:start -->\n<!-- doc-toc:end -->\n```\n\n## Section\n"
        self.assertEqual(docs.refresh_toc(text), text)

    def test_toc_refresh_preserves_body_and_duplicate_heading_links(self) -> None:
        text = "# Guide\n\nIntroduction.\n\n<!-- doc-toc:start -->\nold\n<!-- doc-toc:end -->\n\n## 使用\n\nA.\n\n## 使用\n\nB.\n"
        updated = docs.refresh_toc(text)
        self.assertIn("(#使用-1)", updated)
        self.assertTrue(updated.endswith("## 使用\n\nA.\n\n## 使用\n\nB.\n"))
        self.assertEqual(docs.refresh_toc(updated), updated)

    def test_table_columns_and_escaped_code_pipes(self) -> None:
        self.assertEqual(table_cells(r"| `a\|b` | value |"), [r"`a\|b`", "value"])
        valid = "# Table\n\n| A | B |\n| --- | --- |\n| `a\\|b` | value |\n"
        self.assertEqual(docs.style_issues("guide.md", valid), [])
        invalid = valid.replace(r"a\|b", "a|b")
        self.assertTrue(any("实际 3" in issue for issue in docs.style_issues("guide.md", invalid)))

    def test_unclosed_fence_is_not_silently_accepted(self) -> None:
        self.assertTrue(any("未闭合" in issue for issue in docs.style_issues("guide.md", "# Guide\n\n```python\nx = 1\n")))
        self.assertTrue(any("缺少语言" in issue for issue in docs.style_issues("guide.md", "# Guide\n\n```\noutput\n```\n")))

    def test_chinese_duplicate_heading_anchors_and_encoded_destinations(self) -> None:
        hs = [(1, 1, "Guide"), (3, 2, "数据、隐私与权限"), (5, 2, "数据、隐私与权限")]
        anchors = {"guide.md": {anchor for _, _, _, anchor in heading_anchors(hs)}}
        self.assertIn("数据隐私与权限-1", anchors["guide.md"])
        prose = [(7, "[正确](#%E6%95%B0%E6%8D%AE%E9%9A%90%E7%A7%81%E4%B8%8E%E6%9D%83%E9%99%90-1)"),
                 (8, "[错误](#missing)")]
        issues = local_reference_issues("guide.md", prose, anchors, {"guide.md"})
        self.assertEqual(len(issues), 1)
        self.assertIn("guide.md:8", issues[0])

    def test_cross_document_html_and_reference_links_are_checked(self) -> None:
        anchors = {"docs/guide.md": {"usage"}, "README.md": {"project"}}
        prose = [(1, '<a href="docs/guide.md#usage">Use</a>'),
                 (2, '<img src="missing.svg" alt="Diagram">'),
                 (3, '[guide]: docs/guide.md#no-such-section'),
                 (4, '`<img src="ignored.svg">`'),
                 (5, '<!-- <a href="ignored.md"> -->')]
        issues = local_reference_issues("README.md", prose, anchors, set(anchors))
        self.assertEqual(len(issues), 2)
        self.assertTrue(any("missing.svg" in issue for issue in issues))
        self.assertTrue(any("no-such-section" in issue for issue in issues))

    def test_list_spacing_ignores_nested_items_and_code(self) -> None:
        text = "# Guide\n\nText.\n- First\n  continuation\n- Second\n  - Nested\n\n```text\nNo space\n- example\n```\n"
        issues = docs.style_issues("guide.md", text)
        self.assertEqual(len(issues), 1)
        self.assertIn("guide.md:4", issues[0])

    def test_protocol_fixture_is_not_reformatted(self) -> None:
        self.assertEqual(docs.style_issues(next(iter(docs.CONTENT_FIXTURES)), "```\n# fixture"), [])

    def test_readme_review_requires_new_evidence_not_old_notes(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            def git(*args):
                return subprocess.run(["git", "-C", directory, *args], check=True, capture_output=True)
            git("init", "--quiet")
            git("config", "user.name", "Test")
            git("config", "user.email", "test@example.invalid")
            (root / "README.md").write_text("# Project\n", encoding="utf-8")
            note = root / "docs/TODO/example/index.md"
            note.parent.mkdir(parents=True)
            old = "# Work\n\n" + "\n".join(f"- {field}：旧记录" for field in docs.README_REVIEW_FIELDS) + "\n"
            note.write_text(old, encoding="utf-8")
            git("add", ".")
            git("commit", "--quiet", "-m", "baseline")
            self.assertEqual(docs.readme_review_issues(root, "HEAD"), [])
            (root / "README.md").write_text("# Project\n\nNew user guidance.\n", encoding="utf-8")
            self.assertTrue(docs.readme_review_issues(root, "HEAD"))
            note.write_text(old + "\n" + "\n".join(f"- {field}：本轮已核对说明" for field in docs.README_REVIEW_FIELDS) + "\n", encoding="utf-8")
            self.assertEqual(docs.readme_review_issues(root, "HEAD"), [])

    def test_nested_fence_and_frontmatter_do_not_leak_into_headings(self) -> None:
        text = "---\n# metadata\n---\n# Document\n\n````markdown\n```\n# Example\n<h1>Example</h1>\n````\n\n## Body\n\nText.\n"
        self.assertEqual([value for _, _, value in docs.headings(text)], ["Document", "Body"])
        self.assertEqual(docs.style_issues("docs/guide.md", text), [])

    def test_html_title_precedes_first_markdown_section(self) -> None:
        text = "<h1><strong>Kiyori</strong> &amp; AI</h1>\n\n## Introduction\n\nText.\n"
        self.assertEqual(docs.title(text, "README.md"), "Kiyori & AI")
        self.assertEqual(docs.style_issues("README.md", text), [])

    def test_inline_code_html_is_not_a_second_document_title(self) -> None:
        text = "# Guide\n\nUse `<h1>` only for the title.\n"
        self.assertEqual(docs.style_issues("docs/guide.md", text), [])

    def test_invalid_backtick_fence_does_not_hide_a_second_title(self) -> None:
        text = "# Guide\n\n```invalid`info\n# Another\n\nText.\n"
        self.assertTrue(any("实际 2" in issue for issue in docs.style_issues("guide.md", text)))

    def test_blank_whitespace_is_rejected_but_hard_break_is_preserved(self) -> None:
        issues = docs.style_issues("guide.md", "# Guide\n\nLine  \nnext\n  \n")
        self.assertEqual(len(issues), 1)
        self.assertIn("guide.md:5", issues[0])

    def test_heading_jumps_and_duplicate_titles_are_rejected(self) -> None:
        issues = docs.style_issues("guide.md", "# Guide\n\n### Jump\n\n# Duplicate\n")
        self.assertTrue(any("层级" in issue for issue in issues))
        self.assertTrue(any("实际 2" in issue for issue in issues))

    def test_catalog_groups_tasks_and_omits_history_details(self) -> None:
        texts = {
            "README.md": "<h1>Kiyori</h1>\n\n## Intro\n",
            "docs/TODO/example/index.md": "# Example | Task\n",
            "docs/TODO/history/2026-09/record.md": "# Old\n",
        }
        catalogs = docs.catalog_content(sorted(texts), texts)
        self.assertIn("| Kiyori |", catalogs["docs/CATALOG.md"])
        self.assertIn("Example \\| Task", catalogs["docs/TODO/catalog.md"])
        self.assertNotIn("record.md", catalogs["docs/CATALOG.md"])

    def test_worktree_inventory_includes_new_files_but_excludes_ignored_files(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            subprocess.run(["git", "init", "--quiet", str(root)], check=True)
            (root / ".gitignore").write_text("ignored.md\n", encoding="utf-8")
            (root / "new.md").write_text("# New\n", encoding="utf-8")
            (root / "ignored.md").write_text("# Ignored\n", encoding="utf-8")
            self.assertEqual(docs.documents(root), ["new.md"])

    def test_invalid_encoding_reports_failure_without_replacing_catalogs(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "bad.md").write_bytes(b"\xff")
            (root / "docs").mkdir()
            catalog = root / "docs/CATALOG.md"
            catalog.write_text("# Existing\n", encoding="utf-8")
            output = io.StringIO()
            with patch.object(sys, "argv", ["check_documentation", "--repository", directory, "--write-catalogs"]), patch.object(docs, "documents", return_value=["bad.md"]), contextlib.redirect_stdout(output):
                self.assertEqual(docs.main(), 1)
            self.assertIn("非 UTF-8", output.getvalue())
            self.assertEqual(catalog.read_text(encoding="utf-8"), "# Existing\n")


if __name__ == "__main__":
    unittest.main()
