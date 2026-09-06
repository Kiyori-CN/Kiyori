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


class DocumentationTest(unittest.TestCase):
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
