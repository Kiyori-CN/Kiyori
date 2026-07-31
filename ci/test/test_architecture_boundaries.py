from __future__ import annotations

import hashlib
import sys
import subprocess
import tempfile
import unittest
from collections import Counter
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO_ROOT / "ci" / "script"))

from check_architecture_boundaries import (  # noqa: E402
    M01_NEW_PATH,
    M01_OLD_PATH,
    actual_manifest_components,
    check_file_hashes,
    check_literals,
    check_manifest,
    check_ownership,
    check_persistence_api_calls,
    check_terminal_unchanged,
    check_tracked_artifacts,
    expected_manifest_components,
    import_matches_root,
    is_project_import,
    manifest_semantic_hash,
    normalize_m01_text,
    path_matches,
    persistence_api_records,
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
        self.assertTrue(is_project_import("com.kiyori.feature.browser.BrowserState"))
        self.assertFalse(is_project_import("com.kiyorix.feature.browser.BrowserState"))

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
                '<activity android:name=".MainActivity" android:process=":ui" '
                'android:permission="com.example.ACTIVITY">'
                '<intent-filter>'
                '<action android:name="android.intent.action.VIEW" />'
                '<category android:name="android.intent.category.DEFAULT" />'
                '<data android:scheme="operit" android:host="callback" '
                'android:mimeType="text/plain" />'
                '</intent-filter>'
                '</activity>'
                '<provider android:name=".Provider" '
                'android:authorities="${applicationId}.provider" '
                'android:permission="com.example.PROVIDER" />'
                "</application></manifest>",
                encoding="utf-8",
            )
            self.assertEqual(
                actual_manifest_components(manifest),
                Counter(
                    {
                        ("application", ".App"): 1,
                        ("activity", ".MainActivity"): 1,
                        ("provider", ".Provider"): 1,
                        ("process", ":ui"): 1,
                        ("permission", "com.example.ACTIVITY"): 1,
                        ("permission", "com.example.PROVIDER"): 1,
                        ("action", "android.intent.action.VIEW"): 1,
                        ("category", "android.intent.category.DEFAULT"): 1,
                        ("authority", "${applicationId}.provider"): 1,
                        ("data-scheme", "operit"): 1,
                        ("data-host", "callback"): 1,
                        ("mime-type", "text/plain"): 1,
                    }
                ),
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

    def test_duplicate_manifest_component_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            manifest = root / "app/src/main/AndroidManifest.xml"
            manifest.parent.mkdir(parents=True)
            manifest.write_text(
                '<manifest xmlns:android="http://schemas.android.com/apk/res/android">'
                '<application android:name=".App">'
                '<service android:name=".DuplicateService" />'
                '<service android:name=".DuplicateService" />'
                "</application></manifest>",
                encoding="utf-8",
            )
            snapshot = root / "manifest-components.txt"
            snapshot.write_text(
                "application\t.App\nservice\t.DuplicateService\n",
                encoding="utf-8",
            )
            errors: list[str] = []
            check_manifest(root, snapshot, "baseline", errors)
            self.assertEqual(
                errors,
                ["ARCH008 unexpected manifest component: service .DuplicateService"],
            )

    def test_manifest_semantic_hash_ignores_formatting_attribute_and_sibling_order(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            first = root / "first.xml"
            second = root / "second.xml"
            first.write_text(
                '<manifest xmlns:android="http://schemas.android.com/apk/res/android">'
                '<uses-permission android:name="android.permission.INTERNET"/>'
                '<application android:name=".App" android:allowBackup="true">'
                '<activity android:name=".Main" android:exported="true"/>'
                '<service android:name=".Sync" android:exported="false"/>'
                "</application></manifest>",
                encoding="utf-8",
            )
            second.write_text(
                '<manifest xmlns:android="http://schemas.android.com/apk/res/android">\n'
                '  <application android:allowBackup="true" android:name=".App">\n'
                '    <service android:exported="false" android:name=".Sync" />\n'
                '    <activity android:exported="true" android:name=".Main" />\n'
                "  </application>\n"
                '  <uses-permission android:name="android.permission.INTERNET" />\n'
                "</manifest>\n",
                encoding="utf-8",
            )
            self.assertEqual(manifest_semantic_hash(first), manifest_semantic_hash(second))

    def test_manifest_semantic_hash_detects_non_component_contract_drift(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            manifest = root / "AndroidManifest.xml"
            manifest.write_text(
                '<manifest xmlns:android="http://schemas.android.com/apk/res/android">'
                '<uses-permission android:name="android.permission.INTERNET"/>'
                '<application android:name=".App">'
                '<activity android:name=".Main" android:exported="true"/>'
                "</application></manifest>",
                encoding="utf-8",
            )
            original = manifest_semantic_hash(manifest)
            manifest.write_text(
                '<manifest xmlns:android="http://schemas.android.com/apk/res/android">'
                '<application android:name=".App">'
                '<activity android:name=".Main" android:exported="false"/>'
                "</application></manifest>",
                encoding="utf-8",
            )
            self.assertNotEqual(original, manifest_semantic_hash(manifest))

    def test_manifest_semantic_snapshot_rejects_drift_missed_by_component_multiset(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            manifest = root / "app/src/main/AndroidManifest.xml"
            manifest.parent.mkdir(parents=True)
            manifest.write_text(
                '<manifest xmlns:android="http://schemas.android.com/apk/res/android">'
                '<uses-permission android:name="android.permission.INTERNET"/>'
                '<application android:name=".App">'
                '<activity android:name=".Main" android:exported="true"/>'
                "</application></manifest>",
                encoding="utf-8",
            )
            snapshot = root / "manifest-components.txt"
            snapshot.write_text(
                "application\t.App\nactivity\t.Main\n",
                encoding="utf-8",
            )
            semantic_snapshot = root / "manifest-structure-hashes.txt"
            digest = manifest_semantic_hash(manifest)
            semantic_snapshot.write_text(
                f"baseline\t{digest}\nm01\t{digest}\npost-m01\t{digest}\n",
                encoding="utf-8",
            )
            manifest.write_text(
                '<manifest xmlns:android="http://schemas.android.com/apk/res/android">'
                '<application android:name=".App">'
                '<activity android:name=".Main" android:exported="false"/>'
                "</application></manifest>",
                encoding="utf-8",
            )
            errors: list[str] = []
            check_manifest(
                root,
                snapshot,
                "post-m01",
                errors,
                semantic_snapshot,
            )
            self.assertEqual(len(errors), 1)
            self.assertTrue(errors[0].startswith("ARCH008 manifest semantic structure changed:"))

    def test_critical_file_hash_drift_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            contract = root / "app/src/main/aidl/com/example/Contract.aidl"
            contract.parent.mkdir(parents=True)
            contract.write_text("package com.example;\n", encoding="utf-8")
            normalized = contract.read_bytes().replace(b"\r\n", b"\n")
            digest = hashlib.sha256(normalized).hexdigest()
            snapshot = root / "critical-file-hashes.txt"
            snapshot.write_text(
                f"{digest}\tapp/src/main/aidl/com/example/Contract.aidl\n",
                encoding="utf-8",
            )
            errors: list[str] = []
            check_file_hashes(root, snapshot, errors)
            self.assertEqual(errors, [])

            contract.write_bytes(b"package com.example;\r\n")
            check_file_hashes(root, snapshot, errors)
            self.assertEqual(errors, [])

            contract.write_text("package com.changed;\n", encoding="utf-8")
            check_file_hashes(root, snapshot, errors)
            self.assertTrue(any("critical contract file changed" in error for error in errors))

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

    def test_native_symbol_replacement_is_rejected_when_prefix_count_is_unchanged(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            git(root, "init", "-b", "main")
            source = root / "app/src/main/cpp/native_bridge.cpp"
            source.parent.mkdir(parents=True)
            source.write_text(
                "Java_com_ai_assistance_operit_NativeBridge_open\n",
                encoding="utf-8",
            )
            snapshot = root / "native-ipc-identifiers.txt"
            snapshot.write_text(
                "1\tJava_com_ai_assistance_operit_\n"
                "1\tJava_com_ai_assistance_operit_NativeBridge_open\n",
                encoding="utf-8",
            )
            source.write_text(
                "Java_com_ai_assistance_operit_NativeBridge_close\n",
                encoding="utf-8",
            )
            errors: list[str] = []
            check_literals(root, (snapshot,), errors)
            self.assertEqual(
                errors,
                [
                    "ARCH009/ARCH010 stable literal count changed: "
                    "'Java_com_ai_assistance_operit_NativeBridge_open' expected 1, found 0"
                ],
            )

    def test_new_persistence_api_call_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            git(root, "init", "-b", "main")
            source = root / "app/src/main/java/com/example/Store.kt"
            source.parent.mkdir(parents=True)
            source.write_text(
                'val Context.old by preferencesDataStore(name = "old_store")\n',
                encoding="utf-8",
            )
            git(root, "add", "app/src/main/java/com/example/Store.kt")
            snapshot = root / "persistence-api-calls.txt"
            snapshot.write_text(
                "\n".join(persistence_api_records(root).elements()) + "\n",
                encoding="utf-8",
            )
            source.write_text(
                'val Context.old by preferencesDataStore(name = "old_store")\n'
                'val Context.new by preferencesDataStore(name = "new_store")\n',
                encoding="utf-8",
            )
            errors: list[str] = []
            check_persistence_api_calls(root, snapshot, errors)
            self.assertEqual(len(errors), 1)
            self.assertIn("unexpected persistence API contract", errors[0])
            self.assertIn('"new_store"', errors[0])

    def test_persistence_api_call_scanner_ignores_comments_strings_and_formatting(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            git(root, "init", "-b", "main")
            source = root / "app/src/main/java/com/example/Store.kt"
            source.parent.mkdir(parents=True)
            source.write_text(
                'val ignored = "getSharedPreferences(\\"fake\\", 0)"\n'
                "// getSharedPreferences(\"comment\", 0)\n"
                "val Context.store by preferencesDataStore(\n"
                "    /* reviewed */ name = \"stable_store\",\n"
                ")\n",
                encoding="utf-8",
            )
            git(root, "add", "app/src/main/java/com/example/Store.kt")
            records = persistence_api_records(root)
            self.assertEqual(len(records), 1)
            self.assertEqual(
                next(iter(records)),
                "app/src/main/java/com/example/Store.kt"
                '\tpreferencesDataStore\t"stable_store"',
            )

    def test_persistence_api_import_alias_cannot_bypass_scanner(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            git(root, "init", "-b", "main")
            source = root / "app/src/main/java/com/example/Store.kt"
            source.parent.mkdir(parents=True)
            source.write_text(
                "import androidx.datastore.preferences.preferencesDataStore as privateStore\n"
                'val Context.store by privateStore(name = "hidden_store")\n',
                encoding="utf-8",
            )
            git(root, "add", "app/src/main/java/com/example/Store.kt")
            with self.assertRaisesRegex(ValueError, "persistence API import bypass"):
                persistence_api_records(root)

    def test_unreviewed_persistence_api_requires_an_extractor(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            git(root, "init", "-b", "main")
            source = root / "app/src/main/java/com/example/Store.kt"
            source.parent.mkdir(parents=True)
            source.write_text(
                "val store = PreferenceDataStoreFactory.create(\n"
                '    produceFile = { context.dataStoreFile("hidden_store") },\n'
                ")\n",
                encoding="utf-8",
            )
            git(root, "add", "app/src/main/java/com/example/Store.kt")
            with self.assertRaisesRegex(
                ValueError,
                "unreviewed persistence API requires a contract extractor",
            ):
                persistence_api_records(root)

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

    def test_forbidden_java_static_import_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "app/src/main/java/com/legacy/Feature.java"
            source.parent.mkdir(parents=True)
            source.write_text(
                "package com.legacy;\n\n"
                "import static com.kiyori.app.KiyoriApplication.INSTANCE;\n",
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
                    and "Feature.java:3" in error
                    for error in errors
                )
            )

    def test_operit_owner_allows_only_kiyori_contract_and_platform_roots(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "app/src/main/java/com/legacy/Feature.kt"
            source.parent.mkdir(parents=True)
            source.write_text(
                "package com.legacy\n\n"
                "import com.kiyori.capability.browser.BrowserCapability\n"
                "import com.kiyori.platform.logging.KiyoriLogger\n"
                "import com.kiyori.design.theme.KiyoriTheme\n"
                "import com.kiyori.integration.operit.Adapter\n",
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
                'allowed_import_roots = [\n'
                '  "com.ai.assistance.operit",\n'
                '  "com.kiyori.capability",\n'
                '  "com.kiyori.platform",\n'
                ']\n',
                encoding="utf-8",
            )
            errors: list[str] = []
            check_ownership(root, ownership, errors)
            violations = [
                error for error in errors if error.startswith("ARCH002 import outside allowed roots:")
            ]
            self.assertEqual(len(violations), 2)

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

    def test_feature_owner_rejects_cross_feature_and_legacy_imports(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "app/src/main/java/com/kiyori/feature/browser/BrowserFeature.kt"
            source.parent.mkdir(parents=True)
            source.write_text(
                "package com.kiyori.feature.browser\n\n"
                "import com.kiyori.feature.player.PlayerState\n"
                "import com.ai.assistance.operit.data.model.ChatEntity\n",
                encoding="utf-8",
            )
            ownership = root / "ownership.toml"
            ownership.write_text(
                'schema_version = 1\n'
                '[[ownership]]\n'
                'id = "kiyori-feature-browser"\n'
                'path = "app/src/main/java/com/kiyori/feature/browser/**"\n'
                'owner = "kiyori-browser"\n'
                'sync_zone = "C"\n'
                'phase = "browser"\n'
                'allowed_import_roots = [\n'
                '  "com.kiyori.capability",\n'
                '  "com.kiyori.design",\n'
                '  "com.kiyori.feature.browser",\n'
                '  "com.kiyori.platform",\n'
                ']\n',
                encoding="utf-8",
            )
            errors: list[str] = []
            check_ownership(root, ownership, errors)
            violations = [
                error for error in errors if error.startswith("ARCH004 import outside allowed roots:")
            ]
            self.assertEqual(len(violations), 2)

    def test_vendored_source_rejects_product_imports(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "app/src/main/java/com/vendor/Binding.kt"
            source.parent.mkdir(parents=True)
            source.write_text(
                "package com.vendor\n\n"
                "import com.ai.assistance.operit.data.model.ChatEntity\n"
                "import com.kiyori.platform.logging.KiyoriLogger\n",
                encoding="utf-8",
            )
            ownership = root / "ownership.toml"
            ownership.write_text(
                'schema_version = 1\n'
                '[[ownership]]\n'
                'id = "bundled-vendor"\n'
                'path = "app/src/main/java/com/vendor/**"\n'
                'owner = "vendored"\n'
                'sync_zone = "D"\n'
                'phase = "current"\n'
                'allowed_import_roots = ["com.vendor"]\n',
                encoding="utf-8",
            )
            errors: list[str] = []
            check_ownership(root, ownership, errors)
            violations = [
                error for error in errors if error.startswith("ARCH005 import outside allowed roots:")
            ]
            self.assertEqual(len(violations), 2)

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
