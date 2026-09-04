from __future__ import annotations

import sys
import tempfile
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO_ROOT / "ci" / "script"))
from check_architecture_boundaries import check_browser_runtime_owner  # noqa: E402


RUNTIME_PATH = (
    "app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/standard/"
    "StandardBrowserSessionTools.kt"
)
SHARED_RUNTIME = """
class StandardBrowserSessionTools private constructor(context: Application) {
    companion object {
        @Volatile private var sharedInstance: StandardBrowserSessionTools? = null
        fun getSharedInstance(context: Context): StandardBrowserSessionTools =
            sharedInstance ?: synchronized(this) {
                sharedInstance ?: StandardBrowserSessionTools(context.applicationContext as Application)
                    .also { instance -> sharedInstance = instance }
            }
    }
}
"""


class BrowserRuntimeOwnershipTest(unittest.TestCase):
    def check(self, runtime: str, consumer: str = "") -> list[str]:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            path = root / RUNTIME_PATH
            path.parent.mkdir(parents=True)
            path.write_text(runtime, encoding="utf-8")
            (root / "app/src/main/java/DownloadDrawer.kt").write_text(
                consumer, encoding="utf-8",
            )
            errors: list[str] = []
            check_browser_runtime_owner(root, errors)
            return errors

    def test_shared_factory_and_ui_consumer_are_allowed(self) -> None:
        self.assertEqual(self.check(
            SHARED_RUNTIME,
            "val runtime = StandardBrowserSessionTools.getSharedInstance(context)",
        ), [])

    def test_non_shared_factory_is_rejected_even_when_constructor_is_private(self) -> None:
        runtime = SHARED_RUNTIME.replace(
            "companion object {",
            "companion object {\n"
            "internal fun create(context: Context): StandardBrowserSessionTools =\n"
            "    StandardBrowserSessionTools(context.applicationContext as Application)\n",
        )
        errors = self.check(runtime, "val runtime = StandardBrowserSessionTools.create(context)")
        self.assertTrue(any("only getSharedInstance" in error for error in errors))
        self.assertTrue(any("one construction site" in error for error in errors))
        self.assertTrue(any("non-shared" in error for error in errors))

    def test_constructor_visibility_and_unsafe_publication_are_rejected(self) -> None:
        for before, after, diagnostic in (
            ("private constructor", "constructor", "constructor must be private"),
            ("@Volatile", "", "synchronized volatile"),
            ("synchronized(this)", "run", "synchronized volatile"),
            ("sharedInstance = instance", "", "synchronized volatile"),
        ):
            with self.subTest(change=before):
                self.assertTrue(any(
                    diagnostic in error
                    for error in self.check(SHARED_RUNTIME.replace(before, after))
                ))

    def test_comments_and_strings_cannot_spoof_construction_sites(self) -> None:
        self.assertEqual(self.check(
            SHARED_RUNTIME + '\n// StandardBrowserSessionTools(context)\n'
            'val message = "StandardBrowserSessionTools(context)"',
            "// StandardBrowserSessionTools.create(context)",
        ), [])

    def test_missing_owner_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            errors: list[str] = []
            check_browser_runtime_owner(Path(directory), errors)
            self.assertEqual(errors, ["ARCH047 Browser Runtime owner is missing"])


if __name__ == "__main__":
    unittest.main()
