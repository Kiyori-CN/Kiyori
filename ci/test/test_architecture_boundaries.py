from __future__ import annotations

import sys
import subprocess
import tempfile
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO_ROOT / "ci" / "script"))

from check_architecture_boundaries import (  # noqa: E402
    M01_NEW_PATH,
    M01_OLD_PATH,
    actual_manifest_components,
    check_literals,
    check_manifest,
    check_ownership,
    check_terminal_unchanged,
    check_tracked_artifacts,
    expected_manifest_components,
    import_matches_root,
    normalize_m01_text,
    path_matches,
    repository_text,
    resolve_phase,
    working_tree_app_paths,
)


def git(repository: Path, *args: str) -> str:
    result = subprocess.run(
        ["git", *args],
        cwd=repository,
        check=True,
        capture_output=True,
        text=True,
    )
    return result.stdout.strip()


class ArchitectureBoundaryTest(unittest.TestCase):
    def test_m01_normalization_accepts_only_the_approved_names(self) -> None:
        before = (
            "class OperitApplication\n"
            "val operitApplication = application as OperitApplication\n"
            "val tag = \"OperitApplication\"\n"
            "/** Application class for Operit */\n"
        )
        after = (
            "class KiyoriApplication\n"
            "val kiyoriApplication = application as KiyoriApplication\n"
            "val tag = \"KiyoriApplication\"\n"
            "/** Application class for Kiyori */\n"
        )
        self.assertEqual(normalize_m01_text(after), before)

    def test_m01_normalization_preserves_logic_changes(self) -> None:
        before = "class OperitApplication { fun value() = 1 }\n"
        after = "class KiyoriApplication { fun value() = 2 }\n"
        self.assertNotEqual(normalize_m01_text(after), before)

    def test_cross_platform_path_and_import_prefix_matching(self) -> None:
        self.assertTrue(
            path_matches(
                r"app\src\main\java\com\kiyori\app\App.kt",
                "app/src/main/java/com/kiyori/app/**",
            )
        )
        self.assertTrue(import_matches_root("com.kiyori.app.shell.Root", "com.kiyori.app"))
        self.assertFalse(import_matches_root("com.kiyori.application.Root", "com.kiyori.app"))

    def test_manifest_snapshot_switches_only_the_application_for_m01(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            snapshot = Path(directory) / "manifest-components.txt"
            snapshot.write_text(
                "application\t.core.application.OperitApplication"
                "\t.core.application.KiyoriApplication\n"
                "activity\t.ui.main.MainActivity\n",
                encoding="utf-8",
            )
            self.assertIn(
                ("application", ".core.application.OperitApplication"),
                expected_manifest_components(snapshot, "baseline"),
            )
            self.assertIn(
                ("application", ".core.application.KiyoriApplication"),
                expected_manifest_components(snapshot, "m01"),
            )
            self.assertIn(
                ("application", ".core.application.KiyoriApplication"),
                expected_manifest_components(snapshot, "post-m01"),
            )

    def test_manifest_components_are_extracted_exactly(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            manifest = Path(directory) / "AndroidManifest.xml"
            manifest.write_text(
                '<manifest xmlns:android="http://schemas.android.com/apk/res/android">'
                '<application android:name=".App">'
                '<activity android:name=".MainActivity" />'
                "</application></manifest>",
                encoding="utf-8",
            )
            self.assertEqual(
                actual_manifest_components(manifest),
                {("application", ".App"), ("activity", ".MainActivity")},
            )

    def test_manifest_drift_is_rejected_in_both_directions(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            manifest = root / "app/src/main/AndroidManifest.xml"
            manifest.parent.mkdir(parents=True)
            manifest.write_text(
                '<manifest xmlns:android="http://schemas.android.com/apk/res/android">'
                '<application android:name=".ActualApp">'
                '<activity android:name=".UnexpectedActivity" />'
                "</application></manifest>",
                encoding="utf-8",
            )
            snapshot = root / "manifest-components.txt"
            snapshot.write_text(
                "application\t.ExpectedApp\nactivity\t.MissingActivity\n",
                encoding="utf-8",
            )
            errors: list[str] = []
            check_manifest(root, snapshot, "baseline", errors)
            self.assertTrue(any("ARCH008 missing manifest component" in error for error in errors))
            self.assertTrue(any("ARCH008 unexpected manifest component" in error for error in errors))

    def test_missing_stable_literal_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            git(root, "init", "-b", "main")
            source = root / "app/src/main/java/com/example/Contract.kt"
            source.parent.mkdir(parents=True)
            source.write_text('const val CONTRACT = "present"\n', encoding="utf-8")
            snapshot = root / "stable-identifiers.txt"
            snapshot.write_text("1\tpresent\n1\tmissing\n", encoding="utf-8")
            errors: list[str] = []
            check_literals(root, (snapshot,), errors)
            self.assertEqual(
                errors,
                [
                    "ARCH009/ARCH010 stable literal count changed: "
                    "'missing' expected 1, found 0"
                ],
            )

    def test_stable_literal_addition_is_rejected_by_count(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            git(root, "init", "-b", "main")
            source = root / "app/src/main/java/com/example/Contract.kt"
            source.parent.mkdir(parents=True)
            source.write_text('"stable" + "stable"\n', encoding="utf-8")
            snapshot = root / "stable-identifiers.txt"
            snapshot.write_text("1\tstable\n", encoding="utf-8")
            errors: list[str] = []
            check_literals(root, (snapshot,), errors)
            self.assertEqual(
                errors,
                [
                    "ARCH009/ARCH010 stable literal count changed: "
                    "'stable' expected 1, found 2"
                ],
            )

    def test_repository_text_excludes_git_ignored_dependency_trees(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            git(root, "init", "-b", "main")
            tracked = root / "app/src/main/java/com/example/Contract.kt"
            tracked.parent.mkdir(parents=True)
            tracked.write_text('const val CONTRACT = "stable"\n', encoding="utf-8")
            ignored = root / "examples/demo/node_modules/library/index.js"
            ignored.parent.mkdir(parents=True)
            ignored.write_text('"stable" + "stable"\n', encoding="utf-8")
            untracked = root / "tools/local-check.ts"
            untracked.parent.mkdir(parents=True)
            untracked.write_text('"untracked-contract"\n', encoding="utf-8")
            (root / ".gitignore").write_text("node_modules/\n", encoding="utf-8")
            git(root, "add", ".gitignore", "app/src/main/java/com/example/Contract.kt")

            text = repository_text(root)

            self.assertEqual(text.count('"stable"'), 1)
            self.assertIn('"untracked-contract"', text)

    def test_unmanaged_source_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "app/src/main/java/com/example/Unowned.kt"
            source.parent.mkdir(parents=True)
            source.write_text("package com.example\n", encoding="utf-8")
            ownership = root / "ownership.toml"
            ownership.write_text(
                'schema_version = 1\n'
                '[[ownership]]\n'
                'id = "other"\n'
                'path = "app/src/main/java/com/other/**"\n'
                'owner = "other"\n'
                'sync_zone = "A"\n'
                'phase = "current"\n',
                encoding="utf-8",
            )
            errors: list[str] = []
            check_ownership(root, ownership, errors)
            self.assertTrue(any("unmanaged source file" in error for error in errors))

    def test_source_package_must_match_its_directory(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "app/src/main/java/com/example/Mismatch.kt"
            source.parent.mkdir(parents=True)
            source.write_text("package com.other\n", encoding="utf-8")
            ownership = root / "ownership.toml"
            ownership.write_text(
                'schema_version = 1\n'
                '[[ownership]]\n'
                'id = "example"\n'
                'path = "app/src/main/java/com/example/**"\n'
                'owner = "example"\n'
                'sync_zone = "C"\n'
                'phase = "current"\n',
                encoding="utf-8",
            )
            errors: list[str] = []
            check_ownership(root, ownership, errors)
            self.assertTrue(any(error.startswith("ARCH012 source/package mismatch:") for error in errors))

    def test_exact_exception_suppresses_only_its_active_violation(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "app/src/main/java/com/example/Mismatch.kt"
            source.parent.mkdir(parents=True)
            source.write_text("package com.other\n", encoding="utf-8")
            ownership = root / "ownership.toml"
            ownership.write_text(
                'schema_version = 1\n'
                '[[ownership]]\n'
                'id = "example"\n'
                'path = "app/src/main/java/com/example/**"\n'
                'owner = "example"\n'
                'sync_zone = "C"\n'
                'phase = "current"\n'
                '[[exception]]\n'
                'rule = "ARCH012"\n'
                'path = "app/src/main/java/com/example/Mismatch.kt"\n'
                'reason = "Historical mismatch."\n'
                'expires_after = "M-05-example"\n'
                'owner = "example"\n',
                encoding="utf-8",
            )
            errors: list[str] = []
            check_ownership(root, ownership, errors)
            self.assertEqual(errors, [])

    def test_unused_or_broad_exception_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "app/src/main/java/com/example/Owned.kt"
            source.parent.mkdir(parents=True)
            source.write_text("package com.example\n", encoding="utf-8")
            ownership = root / "ownership.toml"
            ownership.write_text(
                'schema_version = 1\n'
                '[[ownership]]\n'
                'id = "example"\n'
                'path = "app/src/main/java/com/example/**"\n'
                'owner = "example"\n'
                'sync_zone = "C"\n'
                'phase = "current"\n'
                '[[exception]]\n'
                'rule = "ARCH012"\n'
                'path = "app/src/main/java/com/example/Owned.kt"\n'
                'reason = "No longer needed."\n'
                'expires_after = "M-05-example"\n'
                'owner = "example"\n'
                '[[exception]]\n'
                'rule = "ARCH012"\n'
                'path = "app/src/main/java/com/example/**"\n'
                'reason = "Too broad."\n'
                'expires_after = "M-05-example"\n'
                'owner = "example"\n',
                encoding="utf-8",
            )
            errors: list[str] = []
            check_ownership(root, ownership, errors)
            self.assertTrue(any("unused architecture exception" in error for error in errors))
            self.assertTrue(any("invalid architecture exception" in error for error in errors))

    def test_ownership_schema_fields_are_validated(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "app/src/main/java/com/example/Owned.kt"
            source.parent.mkdir(parents=True)
            source.write_text("package com.example\n", encoding="utf-8")
            ownership = root / "ownership.toml"
            ownership.write_text(
                'schema_version = 2\n'
                '[[ownership]]\n'
                'id = "example"\n'
                'path = "app/src/main/java/com/example/**"\n'
                'sync_zone = "C"\n'
                'phase = "current"\n'
                'allowed_import_roots = ["com.kiyori.app"]\n'
                'forbidden_import_roots = ["com.kiyori.app"]\n',
                encoding="utf-8",
            )
            errors: list[str] = []
            check_ownership(root, ownership, errors)
            self.assertTrue(any("unsupported ownership schema_version" in error for error in errors))
            self.assertTrue(any("has no owner" in error for error in errors))
            self.assertTrue(any("both allowed and forbidden" in error for error in errors))

    def test_forbidden_dependency_is_rejected_with_source_line(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "app/src/main/java/com/legacy/Feature.kt"
            source.parent.mkdir(parents=True)
            source.write_text(
                "package com.legacy\n\nimport com.kiyori.app.KiyoriApplication\n",
                encoding="utf-8",
            )
            ownership = root / "ownership.toml"
            ownership.write_text(
                'schema_version = 1\n'
                '[[ownership]]\n'
                'id = "operit-legacy"\n'
                'path = "app/src/main/java/com/legacy/**"\n'
                'owner = "legacy"\n'
                'sync_zone = "A"\n'
                'phase = "current"\n'
                'forbidden_import_roots = ["com.kiyori.app"]\n',
                encoding="utf-8",
            )
            errors: list[str] = []
            check_ownership(root, ownership, errors)
            self.assertTrue(
                any(
                    error.startswith("ARCH001 forbidden import:")
                    and "Feature.kt:3" in error
                    for error in errors
                )
            )

    def test_allowed_dependency_roots_reject_project_layer_escape(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "app/src/main/java/com/kiyori/capability/Contract.kt"
            source.parent.mkdir(parents=True)
            source.write_text(
                "package com.kiyori.capability\n\nimport com.kiyori.feature.browser.BrowserState\n",
                encoding="utf-8",
            )
            ownership = root / "ownership.toml"
            ownership.write_text(
                'schema_version = 1\n'
                '[[ownership]]\n'
                'id = "kiyori-capability"\n'
                'path = "app/src/main/java/com/kiyori/capability/**"\n'
                'owner = "contracts"\n'
                'sync_zone = "C"\n'
                'phase = "current"\n'
                'allowed_import_roots = ["com.kiyori.capability"]\n',
                encoding="utf-8",
            )
            errors: list[str] = []
            check_ownership(root, ownership, errors)
            self.assertTrue(
                any(error.startswith("ARCH003 import outside allowed roots:") for error in errors)
            )

    def test_tracked_private_and_build_artifacts_are_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            git(root, "init", "-b", "main")
            for relative_path in ("local.properties", "artifacts/app-debug.apk"):
                path = root / relative_path
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text("test\n", encoding="utf-8")
                git(root, "add", relative_path)
            errors: list[str] = []
            check_tracked_artifacts(root, errors)
            self.assertTrue(any("tracked private config" in error for error in errors))
            self.assertTrue(any("tracked build/backup artifact" in error for error in errors))

    def test_terminal_change_is_rejected_against_explicit_base(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            git(root, "init", "-b", "main")
            git(root, "config", "user.name", "Architecture Test")
            git(root, "config", "user.email", "architecture@example.com")
            terminal_file = root / "terminal/version.txt"
            terminal_file.parent.mkdir(parents=True)
            terminal_file.write_text("before\n", encoding="utf-8")
            git(root, "add", "terminal/version.txt")
            git(root, "commit", "-m", "baseline")
            base = git(root, "rev-parse", "HEAD")
            terminal_file.write_text("after\n", encoding="utf-8")
            errors: list[str] = []
            check_terminal_unchanged(root, base, errors)
            self.assertEqual(errors, ["ARCH014 terminal changed: terminal/version.txt"])

    def test_unstaged_application_move_is_counted_as_the_destination_path(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            git(root, "init", "-b", "main")
            git(root, "config", "user.name", "Architecture Test")
            git(root, "config", "user.email", "architecture@example.com")
            old_path = root / M01_OLD_PATH
            old_path.parent.mkdir(parents=True)
            old_path.write_text("class OperitApplication\n", encoding="utf-8")
            git(root, "add", M01_OLD_PATH)
            git(root, "commit", "-m", "baseline")
            base = git(root, "rev-parse", "HEAD")

            old_path.unlink()
            new_path = root / M01_NEW_PATH
            new_path.write_text("class KiyoriApplication\n", encoding="utf-8")

            self.assertEqual(working_tree_app_paths(root, base), {M01_NEW_PATH})

    def test_phase_detection_does_not_keep_future_changes_in_m01(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            git(root, "init", "-b", "main")
            git(root, "config", "user.name", "Architecture Test")
            git(root, "config", "user.email", "architecture@example.com")
            old_path = root / M01_OLD_PATH
            old_path.parent.mkdir(parents=True)
            old_path.write_text("class OperitApplication\n", encoding="utf-8")
            git(root, "add", M01_OLD_PATH)
            git(root, "commit", "-m", "baseline")
            baseline = git(root, "rev-parse", "HEAD")

            old_path.unlink()
            new_path = root / M01_NEW_PATH
            new_path.write_text("class KiyoriApplication\n", encoding="utf-8")
            self.assertEqual(resolve_phase(root, "auto", baseline), "m01")

            git(root, "add", "-A")
            git(root, "commit", "-m", "m01")
            m01_commit = git(root, "rev-parse", "HEAD")
            self.assertEqual(resolve_phase(root, "auto", None), "post-m01")
            self.assertEqual(resolve_phase(root, "auto", m01_commit), "post-m01")


if __name__ == "__main__":
    unittest.main()
