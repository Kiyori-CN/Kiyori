from __future__ import annotations

import sys
import subprocess
import tempfile
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO_ROOT / "ci" / "script"))

from check_markdown_links import (  # noqa: E402
    GitTree, SubmoduleTreeUnavailable, check_file, directory_paths, inline_targets, snapshot_issues,
)


class MarkdownLinkParserTest(unittest.TestCase):
    def test_parentheses_in_destination_are_preserved(self) -> None:
        self.assertEqual(inline_targets("[English](README.en.md)"), ["README.en.md"])

    def test_inline_code_is_ignored(self) -> None:
        self.assertEqual(inline_targets("Use `[label](missing.md)` here."), [])

    def test_non_http_uri_scheme_is_ignored(self) -> None:
        issues = check_file("README.md", "[Open](vscode://settings)", {"README.md"})

        self.assertEqual(issues, [])

    def test_malformed_external_url_is_ignored(self) -> None:
        issues = check_file("README.md", "[Open](https://[)", {"README.md"})

        self.assertEqual(issues, [])

    def test_repository_root_is_a_valid_target(self) -> None:
        existing_paths = {"docs/README.md"} | directory_paths({"docs/README.md"})

        self.assertEqual(check_file("docs/README.md", "[root](../)", existing_paths), [])

    def test_duplicate_broken_links_remain_distinct_occurrences(self) -> None:
        issues = check_file(
            "README.md",
            "[one](missing.md) and [two](missing.md)",
            {"README.md"},
        )

        self.assertEqual(len(issues), 2)

    def test_other_fence_marker_does_not_close_code_block(self) -> None:
        text = "```text\n~~~\n```\n[outside](missing.md)\n"

        issues = check_file("README.md", text, {"README.md"})

        self.assertEqual([issue.target for issue in issues], ["missing.md"])


class MarkdownSubmoduleLinkTest(unittest.TestCase):
    def setUp(self) -> None:
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.git(self.root, "init", "-q")
        self.child = self.root / "module"
        self.child.mkdir()
        self.git(self.child, "init", "-q")
        self.write(self.child, "README.md", "# Module\n")
        self.write(self.child, "docs/reference.md", "# Reference\n")
        self.child_commit = self.commit(self.child)

    def git(self, repository: Path, *arguments: str) -> str:
        return subprocess.run(
            ["git", "-C", str(repository), *arguments], check=True,
            capture_output=True, text=True, encoding="utf-8",
        ).stdout.strip()

    def write(self, repository: Path, path: str, text: str) -> None:
        target = repository / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(text, encoding="utf-8")

    def commit(self, repository: Path) -> str:
        self.git(repository, "add", "--all")
        self.git(repository, "-c", "user.name=Fixture", "-c", "user.email=fixture@example.invalid", "commit", "-qm", "fixture")
        return self.git(repository, "rev-parse", "HEAD")

    def parent_commit(self, text: str, child_commit: str | None = None) -> str:
        self.write(self.root, "README.md", text)
        self.git(self.root, "add", "--", "README.md")
        self.git(self.root, "update-index", "--add", "--cacheinfo", f"160000,{child_commit or self.child_commit},module")
        self.git(self.root, "-c", "user.name=Fixture", "-c", "user.email=fixture@example.invalid", "commit", "-qm", "parent fixture")
        return self.git(self.root, "rev-parse", "HEAD")

    def test_submodule_file_and_directory_links_are_verified(self) -> None:
        commit = self.parent_commit("[module](module/README.md) [docs](module/docs/) [root](module/)\n")
        self.assertEqual(snapshot_issues(commit, self.root), [])

    def test_missing_submodule_target_is_not_exempted(self) -> None:
        commit = self.parent_commit("[missing](module/docs/missing.md)\n")
        issues = snapshot_issues(commit, self.root)
        self.assertEqual([issue.target for issue in issues], ["module/docs/missing.md"])

    def test_target_only_in_working_tree_head_does_not_satisfy_old_gitlink(self) -> None:
        commit = self.parent_commit("[later](module/later.md)\n")
        self.write(self.child, "later.md", "# Added later\n")
        new_child_commit = self.commit(self.child)
        self.assertNotEqual(self.child_commit, new_child_commit)
        self.assertEqual([issue.target for issue in snapshot_issues(commit, self.root)], ["module/later.md"])
        candidate = self.parent_commit("[later](module/later.md)\n", new_child_commit)
        self.assertEqual(snapshot_issues(candidate, self.root), [])

    def test_deleted_in_working_tree_head_still_exists_at_pinned_gitlink(self) -> None:
        commit = self.parent_commit("[module](module/README.md)\n")
        self.git(self.child, "rm", "README.md")
        self.commit(self.child)
        self.assertEqual(snapshot_issues(commit, self.root), [])

    def test_parent_scan_does_not_claim_ownership_of_submodule_documents(self) -> None:
        self.write(self.child, "broken.md", "[internal missing](missing.md)\n")
        child_commit = self.commit(self.child)
        commit = self.parent_commit("[module](module/README.md)\n", child_commit)
        self.assertEqual(snapshot_issues(commit, self.root), [])

    def test_uninitialized_unreferenced_submodule_is_not_required(self) -> None:
        commit = self.parent_commit("# Parent\n")
        self.child.rename(self.root / "local-object-backup")
        self.child.mkdir()
        self.assertEqual(snapshot_issues(commit, self.root), [])

    def test_uninitialized_referenced_submodule_fails_explicitly(self) -> None:
        commit = self.parent_commit("[module](module/README.md)\n")
        self.child.rename(self.root / "local-object-backup")
        self.child.mkdir()
        with self.assertRaisesRegex(SubmoduleTreeUnavailable, self.child_commit):
            snapshot_issues(commit, self.root)

    def test_unavailable_gitlink_commit_fails_explicitly(self) -> None:
        missing_commit = "1" * 40
        commit = self.parent_commit("[module](module/README.md)\n", missing_commit)
        with self.assertRaisesRegex(SubmoduleTreeUnavailable, missing_commit):
            snapshot_issues(commit, self.root)

    def test_nested_submodule_links_follow_each_pinned_commit_and_cache_trees(self) -> None:
        nested = self.child / "nested"
        nested.mkdir()
        self.git(nested, "init", "-q")
        self.write(nested, "README.md", "# Nested\n")
        self.commit(nested)
        child_commit = self.commit(self.child)
        parent = self.parent_commit("[nested](module/nested/README.md)\n", child_commit)
        tree = GitTree(parent, self.root)
        self.assertIn("module/nested/README.md", tree)
        child_tree = tree.children["module"]
        self.assertIn("module/nested/README.md", tree)
        self.assertIs(tree.children["module"], child_tree)
        self.assertEqual(len(child_tree.children), 1)
        self.assertNotIn("module/nested/missing.md", tree)


if __name__ == "__main__":
    unittest.main()
