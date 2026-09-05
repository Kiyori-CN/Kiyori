from __future__ import annotations

import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO_ROOT / "ci" / "script"))
from check_architecture_boundaries import (  # noqa: E402
    MAIN_MANIFEST_PATH,
    M05A1_BROWSER_THEME_PATH,
    M05A1_SETTINGS_THEME_PATH,
    M05A3_DESIGN_THEME_PATH,
    M05A3_FLOATING_THEME_PATH,
    M05A3_THEME_RESOURCE_PATHS,
    M05A3_UTILITY_THEME_PATH,
    check_design_theme_contracts,
)


ADAPTERS = (
    M05A1_BROWSER_THEME_PATH, M05A1_SETTINGS_THEME_PATH, M05A3_DESIGN_THEME_PATH,
    M05A3_UTILITY_THEME_PATH, M05A3_FLOATING_THEME_PATH,
)


class DesignThemeContractsTest(unittest.TestCase):
    def check(self, changes: dict[str, str] | None = None) -> list[str]:
        paths = (*ADAPTERS, *M05A3_THEME_RESOURCE_PATHS, MAIN_MANIFEST_PATH)
        sources = {path: (REPO_ROOT / path).read_text(encoding="utf-8") for path in paths}
        sources.update(changes or {})
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for relative, text in sources.items():
                path = root / relative
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(text, encoding="utf-8")
            errors: list[str] = []
            check_design_theme_contracts(root, errors)
            return errors

    def mutated(self, path: str, before: str, after: str) -> list[str]:
        source = (REPO_ROOT / path).read_text(encoding="utf-8")
        self.assertIn(before, source)
        return self.check({path: source.replace(before, after)})

    def test_actual_theme_contracts_pass(self) -> None:
        self.assertEqual(self.check(), [])

    def test_source_comments_spacing_and_xml_order_are_not_frozen(self) -> None:
        changes = {}
        for path in ADAPTERS:
            source = (REPO_ROOT / path).read_text(encoding="utf-8")
            changes[path] = "// Theme documentation\n" + source.replace("    ", "  ")
        for path in M05A3_THEME_RESOURCE_PATHS:
            root = ET.parse(REPO_ROOT / path).getroot()
            for node in root.iter():
                node.attrib = dict(reversed(list(node.attrib.items())))
                node[:] = reversed(node[:])
            changes[path] = ET.tostring(root, encoding="unicode")
        self.assertEqual(self.check(changes), [])

    def test_every_adapter_must_use_shared_shapes(self) -> None:
        for path in ADAPTERS:
            with self.subTest(path=path):
                errors = self.mutated(path, "shapes = KiyoriMaterialShapes,", "")
                self.assertTrue(any("shared shapes" in error for error in errors))

    def test_comments_cannot_supply_a_missing_shape_binding(self) -> None:
        errors = self.mutated(M05A1_BROWSER_THEME_PATH, "shapes = KiyoriMaterialShapes,", "// shapes = KiyoriMaterialShapes,")
        self.assertTrue(any("shared shapes" in error for error in errors))

    def test_browser_and_settings_must_inherit_parent_typography(self) -> None:
        for path in (M05A1_BROWSER_THEME_PATH, M05A1_SETTINGS_THEME_PATH):
            with self.subTest(path=path):
                errors = self.mutated(path, "val parentTypography = MaterialTheme.typography", "val parentTypography = KiyoriTypography")
                self.assertTrue(any("inherit typography" in error for error in errors))

    def test_child_theme_luminance_boundary_cannot_change(self) -> None:
        for path in (M05A1_BROWSER_THEME_PATH, M05A1_SETTINGS_THEME_PATH):
            with self.subTest(path=path):
                errors = self.mutated(path, "background.luminance() < 0.5f", "background.luminance() > 0.5f")
                self.assertTrue(any("parent luminance" in error for error in errors))

    def test_browser_dark_palette_cannot_be_replaced_with_light(self) -> None:
        errors = self.mutated(M05A1_BROWSER_THEME_PATH, "KiyoriBrowserDarkColorScheme", "KiyoriBrowserLightColorScheme")
        self.assertTrue(any("neutral dark and light" in error for error in errors))

    def test_settings_palette_and_provider_must_feed_the_content(self) -> None:
        for before, after in (
            ("resolveKiyoriSettingsColors(isDark)", "resolveKiyoriSettingsColors(false)"),
            ("background = settingsColors.pageBackground", "background = settingsColors.cardBackground"),
            ("LocalKiyoriSettingsColors provides settingsColors", "LocalKiyoriSettingsColors provides otherColors"),
            ("content = content,", "content = {},"),
            ("shapes = KiyoriMaterialShapes,\n    ) {", "shapes = KiyoriMaterialShapes,\n    ) {}\n    run {"),
        ):
            with self.subTest(change=before):
                errors = self.mutated(M05A1_SETTINGS_THEME_PATH, before, after)
                self.assertTrue(any("provide colors to content" in error for error in errors))

    def test_root_theme_cannot_drop_the_content(self) -> None:
        errors = self.mutated(M05A3_DESIGN_THEME_PATH, "content()", "Unit")
        self.assertTrue(any("render content inside" in error for error in errors))

    def test_settings_colors_must_require_provider_and_match_dark_mode(self) -> None:
        for before, after in (
            ('error("KiyoriSettingsTheme is not available")', "LightSettingsColors"),
            ("if (isDark) DarkSettingsColors else LightSettingsColors", "if (isDark) LightSettingsColors else DarkSettingsColors"),
        ):
            with self.subTest(change=before):
                errors = self.mutated(M05A1_SETTINGS_THEME_PATH, before, after)
                self.assertTrue(any("require a provider" in error for error in errors))

    def test_utility_theme_must_use_shared_preferences_and_honor_system_mode(self) -> None:
        for before, after in (
            ("UserPreferencesManager.getInstance(context)", "UserPreferencesManager(context)"),
            ("if (useSystemTheme)", "if (true)"),
            ("themeMode == UserPreferencesManager.THEME_MODE_DARK", "false"),
        ):
            with self.subTest(change=before):
                errors = self.mutated(M05A3_UTILITY_THEME_PATH, before, after)
                self.assertTrue(any("existing user and system preferences" in error for error in errors))

    def test_floating_theme_must_remain_independent_of_activity(self) -> None:
        source = (REPO_ROOT / M05A3_FLOATING_THEME_PATH).read_text(encoding="utf-8")
        errors = self.check({M05A3_FLOATING_THEME_PATH: source + "\nval context = LocalContext.current\n"})
        self.assertTrue(any("independent of Activity" in error for error in errors))

    def test_floating_theme_keeps_supplied_values_and_small_typography(self) -> None:
        for before, after, diagnostic in (
            ("colorScheme ?: KiyoriLightColorScheme", "KiyoriLightColorScheme", "static defaults"),
            ("typography ?: defaultSmallTypography", "defaultSmallTypography", "static defaults"),
            ("fontSize = 14.sp, lineHeight = 18.sp", "fontSize = 20.sp, lineHeight = 18.sp", "floating typography"),
        ):
            with self.subTest(change=before):
                self.assertTrue(any(diagnostic in error for error in self.mutated(M05A3_FLOATING_THEME_PATH, before, after)))

    def test_every_android_qualifier_preserves_parent_and_bar_contract(self) -> None:
        for path in M05A3_THEME_RESOURCE_PATHS:
            with self.subTest(path=path):
                errors = self.mutated(path, 'parent="@style/KiyoriThemeBase"', 'parent="Theme.MaterialComponents.DayNight.NoActionBar"')
                self.assertTrue(any("style contract differs" in error for error in errors))

    def test_night_status_bar_and_splash_contract_cannot_drift(self) -> None:
        path = "app/src/main/res/values-night/themes.xml"
        for before, after in (
            ('name="android:windowLightStatusBar">false', 'name="android:windowLightStatusBar">true'),
            ("@drawable/ic_kiyori_splash_transparent", "@drawable/other"),
            ('tools:targetApi="s"', 'tools:targetApi="q"'),
        ):
            with self.subTest(change=before):
                self.assertTrue(any("style contract differs" in error for error in self.mutated(path, before, after)))

    def test_cropper_cancel_and_confirm_require_action_bar_in_both_themes(self) -> None:
        for path in M05A3_THEME_RESOURCE_PATHS[:2]:
            with self.subTest(path=path):
                errors = self.mutated(path, "Theme.MaterialComponents.DayNight.DarkActionBar", "Theme.MaterialComponents.DayNight.NoActionBar")
                self.assertTrue(any("style contract differs" in error for error in errors))

    def test_cropper_manifest_cannot_lose_override_or_gain_exposure(self) -> None:
        for before, after in (
            ('android:theme="@style/Theme.Kiyori.Cropper"', 'android:theme="@style/Theme.Kiyori"'),
            ('tools:replace="android:theme" />', 'tools:replace="android:theme" android:exported="true" />'),
            ('tools:replace="android:theme" />', '/>'),
        ):
            with self.subTest(change=before):
                errors = self.mutated(MAIN_MANIFEST_PATH, before, after)
                self.assertTrue(any("one explicit ActionBar" in error for error in errors))

    def test_missing_theme_owner_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            errors: list[str] = []
            check_design_theme_contracts(Path(directory), errors)
            self.assertEqual(errors, ["ARCH050 theme contract owners are missing"])


if __name__ == "__main__":
    unittest.main()
