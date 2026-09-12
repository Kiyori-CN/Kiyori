from __future__ import annotations

import sys
import tempfile
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO_ROOT / "ci" / "script"))
from check_architecture_boundaries import (  # noqa: E402
    M04B_AI_DRAWER_PATH,
    M04B_PRIMARY_NAVIGATION_PATH,
    M04B_SOFTWARE_HOME_PATH,
    check_shell_presentation_contracts,
)


class ShellPresentationContractsTest(unittest.TestCase):
    def check(self, changes: dict[str, str] | None = None) -> list[str]:
        paths = (M04B_AI_DRAWER_PATH, M04B_PRIMARY_NAVIGATION_PATH, M04B_SOFTWARE_HOME_PATH)
        sources = {path: (REPO_ROOT / path).read_text(encoding="utf-8") for path in paths}
        sources.update(changes or {})
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for relative, text in sources.items():
                path = root / relative
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(text, encoding="utf-8")
            errors: list[str] = []
            check_shell_presentation_contracts(root, errors)
            return errors

    def mutated(self, path: str, before: str, after: str) -> list[str]:
        source = (REPO_ROOT / path).read_text(encoding="utf-8")
        self.assertIn(before, source)
        return self.check({path: source.replace(before, after)})

    def test_actual_shell_presentation_contracts_pass(self) -> None:
        self.assertEqual(self.check(), [])

    def test_visual_changes_do_not_freeze_source(self) -> None:
        self.assertEqual(self.mutated(M04B_AI_DRAWER_PATH, "0.32f * visibilityFraction", "0.3f * visibilityFraction"), [])
        self.assertEqual(self.mutated(M04B_SOFTWARE_HOME_PATH, "120\n", "124\n"), [])

    def test_drawer_back_cannot_capture_hidden_pages_or_skip_dismiss(self) -> None:
        for before, after in (
            ("BackHandler(enabled = isVisible)", "BackHandler(enabled = true)"),
            ("onDismiss()", "Unit"),
            ("if (!isVisible)", "if (isVisible)"),
        ):
            with self.subTest(change=before):
                self.assertTrue(any("consume Back" in error for error in self.mutated(M04B_AI_DRAWER_PATH, before, after)))

    def test_drawer_registry_and_selection_cannot_be_replaced_with_local_state(self) -> None:
        for before, after in (
            ("navigationEntries.filter", "localEntries.filter"),
            ("onEntrySelected(entry)", "selectedEntryId = entry.entryId"),
            ("navigationModel.navigationEntries", "emptyList()"),
        ):
            with self.subTest(change=before):
                self.assertTrue(any("supplied navigation registry" in error for error in self.mutated(M04B_AI_DRAWER_PATH, before, after)))

    def test_bottom_selection_and_click_must_belong_to_caller(self) -> None:
        for before, after in (
            ("selectedDestination == item.destination", "localSelection == item.destination"),
            ("onDestinationSelected(item.destination)", "localSelection = item.destination"),
        ):
            with self.subTest(change=before):
                self.assertTrue(any("render caller selection" in error for error in self.mutated(M04B_PRIMARY_NAVIGATION_PATH, before, after)))

    def test_file_entry_cannot_bypass_shell_callback(self) -> None:
        self.assertTrue(any("dispatch file and settings" in error for error in self.mutated(
            M04B_PRIMARY_NAVIGATION_PATH,
            "onOpenFileManagerLocation = onOpenFileManagerLocation",
            "onOpenFileManagerLocation = { _, _ -> openOtherPage() }",
        )))

    def test_home_cannot_drop_search_ai_or_window_dispatch(self) -> None:
        for before in ("onSearchClick()", "onAiClick()", "onAiQuickAction(action)", "onWindowsClick = onWindowsClick"):
            with self.subTest(change=before):
                self.assertTrue(any("dispatch search, AI and windows" in error for error in self.mutated(
                    M04B_SOFTWARE_HOME_PATH, before, "Unit",
                )))

    def test_weather_must_use_shared_repository_and_its_state(self) -> None:
        for before, after in (
            ("KiyoriWeatherRepository.getInstance(context.applicationContext)", "KiyoriWeatherRepository(context.applicationContext)"),
            ("weatherRepository.state.collectAsState()", "mutableStateOf<KiyoriWeatherState>(KiyoriWeatherState.Loading)"),
        ):
            with self.subTest(change=before):
                self.assertTrue(any("shared weather state" in error for error in self.mutated(M04B_SOFTWARE_HOME_PATH, before, after)))

    def test_refresh_must_be_nested_inside_started_lifecycle(self) -> None:
        for before, after in (
            ("repeatOnLifecycle(Lifecycle.State.STARTED)", "repeatOnLifecycle(Lifecycle.State.CREATED)"),
            ("repeatOnLifecycle(Lifecycle.State.STARTED) {", "repeatOnLifecycle(Lifecycle.State.STARTED) {}\n        run {"),
            ("delay(KiyoriWeatherRepository.REFRESH_INTERVAL_MILLIS)", "Unit"),
        ):
            with self.subTest(change=before):
                self.assertTrue(any("STARTED lifecycle" in error for error in self.mutated(M04B_SOFTWARE_HOME_PATH, before, after)))

    def test_comments_and_strings_cannot_supply_a_missing_callback(self) -> None:
        for replacement in ("// onSearchClick()\nUnit", 'log("onSearchClick()")'):
            with self.subTest(replacement=replacement):
                self.assertTrue(any("dispatch search, AI and windows" in error for error in self.mutated(
                    M04B_SOFTWARE_HOME_PATH, "onSearchClick()", replacement,
                )))

    def test_missing_owners_are_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            errors: list[str] = []
            check_shell_presentation_contracts(Path(directory), errors)
            self.assertEqual(errors, ["ARCH049 shell presentation owners are missing"])


if __name__ == "__main__":
    unittest.main()
