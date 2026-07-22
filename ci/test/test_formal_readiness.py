from __future__ import annotations

import sys
import tempfile
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO_ROOT / "ci" / "script"))

from check_formal_readiness import check_package_metadata, check_runtime_urls, check_visible_branding  # noqa: E402


class FormalReadinessTest(unittest.TestCase):
    def test_private_tooling_metadata_is_accepted(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "package.json").write_text(
                '{"name": "kiyori-tooling", "private": true}\n', encoding="utf-8"
            )
            (root / "package-lock.json").write_text(
                '{"name": "kiyori-tooling", "lockfileVersion": 3}\n', encoding="utf-8"
            )
            errors: list[str] = []

            check_package_metadata(root, errors)

            self.assertEqual(errors, [])

    def test_visible_legacy_terminal_brand_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            strings = root / "app/src/main/res/values/strings.xml"
            strings.parent.mkdir(parents=True)
            strings.write_text(
                '<resources><string name="terminal">Operit Terminal</string></resources>\n',
                encoding="utf-8",
            )
            errors: list[str] = []

            check_visible_branding(root, errors)

            self.assertEqual(len(errors), 1)
            self.assertIn("visible legacy brand", errors[0])

    def test_upstream_runtime_url_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "app/src/main/java/Example.kt"
            source.parent.mkdir(parents=True)
            source.write_text(
                'const val source = "https://github.com/AAswordman/Operit"\n', encoding="utf-8"
            )
            errors: list[str] = []

            check_runtime_urls(root, errors)

            self.assertEqual(len(errors), 1)
            self.assertIn("upstream runtime URL", errors[0])


if __name__ == "__main__":
    unittest.main()
