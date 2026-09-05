from __future__ import annotations

import sys
import tempfile
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO_ROOT / "ci" / "script"))
from check_architecture_boundaries import (  # noqa: E402
    M05A3_APP_THEME_PATH,
    M05A3_SYSTEM_BARS_PATH,
    STATUS_BAR_APPEARANCE_PATH,
    check_status_bar_appearance,
)


class StatusBarAppearanceTest(unittest.TestCase):
    def check(self, changes: dict[str, str] | None = None) -> list[str]:
        paths = (STATUS_BAR_APPEARANCE_PATH, M05A3_SYSTEM_BARS_PATH, M05A3_APP_THEME_PATH)
        sources = {path: (REPO_ROOT / path).read_text(encoding="utf-8") for path in paths}
        sources.update(changes or {})
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for relative, text in sources.items():
                path = root / relative
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(text, encoding="utf-8")
            errors: list[str] = []
            check_status_bar_appearance(root, errors)
            return errors

    def mutated(self, path: str, before: str, after: str) -> list[str]:
        source = (REPO_ROOT / path).read_text(encoding="utf-8")
        self.assertIn(before, source)
        return self.check({path: source.replace(before, after)})

    def test_actual_window_and_declaration_owners_pass(self) -> None:
        self.assertEqual(self.check(), [])

    def test_global_or_unremembered_state_is_rejected(self) -> None:
        changes = (
            ("remember { KiyoriStatusBarAppearanceState() }", "KiyoriStatusBarAppearanceState()"),
            ("private var requests by mutableStateOf", "private var requests by stateOf"),
            ("internal class KiyoriStatusBarAppearanceState", "internal object KiyoriStatusBarAppearanceState"),
        )
        for before, after in changes:
            with self.subTest(change=before):
                self.assertTrue(any("each root scope" in error for error in self.mutated(
                    STATUS_BAR_APPEARANCE_PATH, before, after,
                )))

    def test_identity_and_disposal_cannot_be_replaced_by_value_equality(self) -> None:
        for before, after in (
            ("appearance.remove(owner)", "appearance.remove(darkIcons)"),
            ("remember { Any() }", "Any()"),
            ("it.owner === owner", "it.owner == owner"),
        ):
            with self.subTest(change=before):
                self.assertTrue(any("stable identity" in error for error in self.mutated(
                    STATUS_BAR_APPEARANCE_PATH, before, after,
                )))

    def test_reading_state_only_in_side_effect_is_rejected(self) -> None:
        source = (REPO_ROOT / M05A3_SYSTEM_BARS_PATH).read_text(encoding="utf-8")
        declaration = "val darkStatusBarIcons = LocalKiyoriStatusBarAppearance.current.darkIcons ?: !darkTheme"
        source = source.replace(declaration, "").replace("SideEffect {", "SideEffect {\n" + declaration)
        self.assertTrue(any("during composition" in error for error in self.check({M05A3_SYSTEM_BARS_PATH: source})))

    def test_application_content_and_window_owner_require_the_same_scope(self) -> None:
        self.assertTrue(any("enclose both" in error for error in self.mutated(
            M05A3_APP_THEME_PATH, "KiyoriStatusBarAppearanceScope {", "run {",
        )))
        self.assertTrue(any("enclose both" in error for error in self.mutated(
            M05A3_APP_THEME_PATH, "        CompositionLocalProvider(", "    }\n    run {\n        CompositionLocalProvider(",
        )))

    def test_extra_window_writes_and_duplicate_state_owners_are_rejected(self) -> None:
        source = (REPO_ROOT / STATUS_BAR_APPEARANCE_PATH).read_text(encoding="utf-8")
        self.assertTrue(any("must not write" in error for error in self.check({
            STATUS_BAR_APPEARANCE_PATH: source + "\nfun writeWindow() { activity.enableEdgeToEdge(style, style) }\n",
        })))
        self.assertTrue(any("duplicate" in error for error in self.check({
            "app/src/main/java/ExtraWindow.kt": "val extra = KiyoriStatusBarAppearanceState()",
        })))

    def test_missing_owner_and_comment_spoofing_are_rejected(self) -> None:
        self.assertTrue(any("during composition" in error for error in self.mutated(
            M05A3_SYSTEM_BARS_PATH,
            "val darkStatusBarIcons = LocalKiyoriStatusBarAppearance.current.darkIcons ?: !darkTheme",
            "// val darkStatusBarIcons = LocalKiyoriStatusBarAppearance.current.darkIcons ?: !darkTheme",
        )))
        with tempfile.TemporaryDirectory() as directory:
            errors: list[str] = []
            check_status_bar_appearance(Path(directory), errors)
            self.assertEqual(errors, ["ARCH048 status-bar appearance owners are missing"])


if __name__ == "__main__":
    unittest.main()
