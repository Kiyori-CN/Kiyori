from __future__ import annotations

import hashlib
import sys
import subprocess
import tempfile
import unittest
import zipfile
from collections import Counter
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO_ROOT / "ci" / "script"))

from check_architecture_boundaries import (  # noqa: E402
    M01_NEW_PATH,
    M01_OLD_PATH,
    M03_APPLICATION_PATH,
    M03_LINT_NEW_PATH,
    M03_LINT_OLD_PATH,
    M03_LINT_PATH_COUNT,
    M04_HOST_LOCALS_PATH,
    M04_MAIN_ACTIVITY_PATH,
    M04_OLD_ROOT_PATH,
    M04_ROOT_PATH,
    M04_OLD_SHELL_STATE_PATH,
    M04_SHELL_STATE_PATH,
    M04B_BROWSER_EXIT_CONTRACT_PATH,
    M04B_APP_SHELL_PATH,
    M04B_AI_DRAWER_PATH,
    M04B_BROWSER_SEARCH_PATH,
    M04B_BROWSER_SEARCH_CAPABILITY_IMPORT,
    M04B_PRIMARY_NAVIGATION_PATH,
    M04B_SOFTWARE_HOME_PATH,
    M04C_NAVIGATION_INTEGRATION_PATH,
    M04C_ROUTE_CATALOG_PATH,
    M04C_SETTINGS_TEST_PATH,
    M04D_MAIN_PENDING_REQUESTS_PATH,
    M04D_MAIN_PENDING_REQUESTS_TEST_PATH,
    M04D_MAIN_INTENT_DECODER_PATH,
    M04D_MAIN_INTENT_DECODER_TEST_PATH,
    M04D_MAIN_DISPLAY_COORDINATOR_PATH,
    M04D_MAIN_DISPLAY_COORDINATOR_TEST_PATH,
    M04D_MAIN_SHARED_CONTENT_COORDINATOR_PATH,
    M04D_MAIN_SHARED_CONTENT_COORDINATOR_TEST_PATH,
    M04D_MAIN_TASK_VISIBILITY_COORDINATOR_PATH,
    M04D_MAIN_TASK_VISIBILITY_COORDINATOR_TEST_PATH,
    M04D_MAIN_ORIENTATION_COORDINATOR_PATH,
    M04D_MAIN_ORIENTATION_COORDINATOR_TEST_PATH,
    M04D_MAIN_NOTIFICATION_PERMISSION_COORDINATOR_PATH,
    M04D_MAIN_NOTIFICATION_PERMISSION_COORDINATOR_TEST_PATH,
    M04D_MAIN_STARTUP_GATE_COORDINATOR_PATH,
    M04D_MAIN_STARTUP_GATE_COORDINATOR_TEST_PATH,
    M04D_MAIN_CONTENT_HOST_PATH,
    M04D_MAIN_CONTENT_HOST_TEST_PATH,
    M04E_MAIN_ACTIVITY_IMPORT_SNAPSHOT,
    M04E_MAIN_ACTIVITY_OWNERSHIP_ID,
    M05A1_BROWSER_THEME_PATH,
    M05A1_COLOR_SCHEMES_PATH,
    M05A1_DESIGN_TEST_PATH,
    M05A1_OLD_BROWSER_THEME_PATH,
    M05A1_OLD_COLOR_RESOLVER_PATH,
    M05A1_OLD_SETTINGS_THEME_PATH,
    M05A1_OLD_THEME_TEST_PATH,
    M05A1_SETTINGS_THEME_PATH,
    M05A2_CONSUMER_IMPORT_COUNT,
    M05A2_CONSUMER_IMPORT_SNAPSHOT,
    M05A2_DESIGN_PACKAGE,
    M05A2_ARCHITECTURE_TEST_PATH,
    M05A2_EXTERNAL_TEST_CONSUMER_COUNT,
    M05A2_EXPECTED_QUALIFIED_REFERENCES,
    M05A2_M04B_SEMANTIC_SNAPSHOT_REPLACEMENTS,
    M05A2_PRODUCTION_CONSUMER_COUNT,
    M05A2_SEMANTIC_COLORS_HASH_SNAPSHOT,
    M05A2_SEMANTIC_COLORS_PATH,
    M05A2_SEMANTIC_THEME_HASH_SNAPSHOT,
    M05A2_SEMANTIC_THEME_PATH,
    M05A3_APP_THEME_EXCEPTION_EXPIRY,
    M05A3_APP_THEME_EXCEPTION_REASON,
    M05A3_APP_THEME_EXCEPTION_RULE,
    M05A3_APP_THEME_PATH,
    M05A3_ARCHITECTURE_TEST_PATH,
    M05A3_CONSUMER_IMPORT_SNAPSHOT,
    M05A3_DESIGN_THEME_PATH,
    M05A3_FLOATING_THEME_PATH,
    M05A3_HASHED_PATHS,
    M05A3_LIQUID_GLASS_PATH,
    M05A3_NEW_STYLE,
    M05A3_OLD_THEME_PATH,
    M05A3_PLAYER_ACTIVITY_PATH,
    M05A3_ROOT_HASH_SNAPSHOT,
    M05A3_SYSTEM_BARS_PATH,
    M05A3_THEME_RESOURCE_PATHS,
    M05A3_TYPOGRAPHY_PATH,
    M05A3_TYPE_PATH,
    M05A3_UTILITY_THEME_PATH,
    M05A3_WATER_GLASS_PATH,
    M05A3_WIDGET_EXCEPTION_EXPIRY,
    M05A3_WIDGET_EXCEPTION_REASON,
    M05A3_WIDGET_EXCEPTION_RULE,
    M05A3_WIDGET_THEME_HOST_PATH,
    M05B_ARCHITECTURE_TEST_PATH,
    M05B_CRASH_REPORT_STORE_PATH,
    M05B_DISPLAY_EXCEPTION_PATH,
    M05B_FORMATTER_PATH,
    M05B_FORMATTER_TEST_PATH,
    M05B_HASHED_PATHS,
    M05B_HASH_SNAPSHOT,
    M05B_KIYORI_CONSUMER_SNAPSHOT,
    M05B_LOG_MIGRATION_PATH,
    M05B_LEGACY_FACADE_PATH,
    M05B_LOGGER_PATH,
    M05B_MEMORY_PROVIDER_PATH,
    M05B_NEW_FORMATTER_IMPORT,
    M05B_NEW_LOGGER_IMPORT,
    M05B_OLD_FORMATTER_PATH,
    M05B_OLD_LOGGER_IMPORT,
    M05C_ARCHITECTURE_TEST_PATH,
    M05C_FACTS_TEST_PATH,
    M05C_HASHED_PATHS,
    M05C_HASH_SNAPSHOT,
    M05C_LEGACY_CONSUMER_PATHS,
    M05C_LEGACY_CONSUMER_SNAPSHOT,
    M05C_LEGACY_FACADE_PATH,
    M05C_LEGACY_IMPORT,
    M05C_OBSERVER_IMPORT,
    M05C_OPERIT_INTEGRATION_PATH,
    M05C_PLATFORM_IMPORT,
    M05C_PLATFORM_LIFECYCLE_PATH,
    M05D_ARCHITECTURE_TEST_PATH,
    M05D_COORDINATOR_PATH,
    M05D_DIRECT_CONSUMER_PATHS,
    M05D_DIRECT_CONSUMER_SNAPSHOT,
    M05D_HASHED_PATHS,
    M05D_HASH_SNAPSHOT,
    M05D_OLD_PERMISSION_TEST_PATH,
    M05D_OPERIT_RESOURCE_BRIDGE_IMPORT,
    M05D_OPERIT_RESOURCE_BRIDGE_PATH,
    M05D_PERMISSION_TEST_PATH,
    M05D_PLATFORM_ACTION_IMPORT,
    M05D_PLATFORM_CAPABILITY_IMPORT,
    M05D_PLATFORM_PERMISSION_PATH,
    M05D_PREFERENCES_PATH,
    KIYORI_ACCESSIBILITY_SUPPORT_APK_PATH,
    KIYORI_ACCESSIBILITY_SUPPORT_HASH_PATH,
    KIYORI_ACCESSIBILITY_SUPPORT_VERSION_PATH,
    KIYORI_LAUNCHER_FOREGROUND_PATH,
    KIYORI_FIRST_RUN_AGREEMENT_PATH,
    KIYORI_FIRST_RUN_AGREEMENT_TEST_PATH,
    KIYORI_FIRST_RUN_CONTRACT_PATH,
    KIYORI_FIRST_RUN_CONTRACT_TEST_PATH,
    KIYORI_FIRST_RUN_PERMISSIONS_PATH,
    KIYORI_FIRST_RUN_PREFERENCES_PATH,
    KIYORI_FIRST_RUN_SCREEN_PATH,
    M05E_ARCHITECTURE_TEST_PATH,
    M05E_DIRECT_BACKUP_CONSUMER_PATHS,
    M05E_DIRECT_BACKUP_CONSUMER_SNAPSHOT,
    M05E_DIRECT_PATH_CONSUMER_PATHS,
    M05E_DIRECT_PATH_CONSUMER_SNAPSHOT,
    M05E_HASHED_PATHS,
    M05E_HASH_SNAPSHOT,
    M05E_KIYORI_BACKUP_PATHS_IMPORT,
    M05E_KIYORI_BACKUP_PATHS_PATH,
    M05E_KIYORI_PATHS_IMPORT,
    M05E_KIYORI_PATHS_PATH,
    M05E_LEGACY_OPERIT_CONSUMER_PATHS,
    M05E_LEGACY_OPERIT_CONSUMER_SNAPSHOT,
    M05E_OPERIT_BACKUP_DIRS_IMPORT,
    M05E_OPERIT_BACKUP_DIRS_PATH,
    M05E_OPERIT_PATHS_IMPORT,
    M05E_OPERIT_PATHS_PATH,
    M05E_PATHS_TEST_PATH,
    M04B_OPERIT_NAVIGATION_POLICY_PATH,
    actual_manifest_components,
    check_file_hashes,
    check_debug_manifest,
    check_literals,
    check_manifest,
    check_m02_application_access,
    check_m03_application_move,
    check_m04_root_composition,
    check_m04b_browser_exit_contract,
    check_m04b_app_shell_owner,
    check_m04b_ai_drawer_owner,
    check_m04b_browser_search_owner,
    check_m04b_primary_navigation_owner,
    check_m04b_software_home_owner,
    check_m04c_navigation_integration,
    check_m04d_main_pending_requests,
    check_m04d_main_intent_decoder,
    check_m04d_main_display_coordinator,
    check_m04d_main_shared_content_coordinator,
    check_m04d_main_task_visibility_coordinator,
    check_m04d_main_orientation_coordinator,
    check_m04d_main_notification_permission_coordinator,
    check_m04d_main_startup_gate_coordinator,
    check_kiyori_first_run_flow,
    check_m04d_main_content_host,
    check_m04e_finalization,
    check_m05a1_design_theme,
    check_m05a2_semantic_design,
    check_m05a3_root_theme,
    check_m05b_platform_logging,
    check_m05c_platform_lifecycle,
    check_m05d_android_permission_capability,
    check_m05e_storage_paths,
    check_m04b_operit_navigation_policy,
    check_m04b_shell_state_owner,
    check_ownership,
    check_persistence_api_calls,
    check_terminal_unchanged,
    check_tracked_artifacts,
    expected_manifest_components,
    import_matches_root,
    is_project_import,
    manifest_semantic_hash,
    normalize_m01_text,
    normalize_m03_application_text,
    normalize_m04b_app_shell_text,
    path_matches,
    persistence_api_records,
    repository_text,
    resolve_phase,
    source_dependency_edges,
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
    def write_m02_contract_layout(self, root: Path) -> None:
        files = {
            "app/src/main/java/com/kiyori/platform/android/ApplicationContextAccess.kt": (
                "package com.kiyori.platform.android\n"
                "object ApplicationContextAccess\n"
            ),
            "app/src/main/java/com/kiyori/platform/lifecycle/ApplicationStartupTime.kt": (
                "package com.kiyori.platform.lifecycle\n"
                "object ApplicationStartupTime\n"
            ),
            "app/src/main/java/com/kiyori/platform/lifecycle/MainApplicationInitialization.kt": (
                "package com.kiyori.platform.lifecycle\n"
                "interface MainApplicationInitialization\n"
            ),
            "app/src/main/java/com/kiyori/platform/serialization/ApplicationJson.kt": (
                "package com.kiyori.platform.serialization\n"
                "object ApplicationJson\n"
            ),
            M01_NEW_PATH: (
                "package com.ai.assistance.operit.core.application\n"
                "class KiyoriApplication : MainApplicationInitialization {\n"
                "    fun onCreate() {\n"
                "        ApplicationStartupTime.recordForProcess(1L)\n"
                "        ApplicationContextAccess.installForProcess(this)\n"
                "        ApplicationJson.installForProcess(Unit)\n"
                "    }\n"
                "    override fun initializeMainUiPrerequisites() = Unit\n"
                "    override fun initializeMainApplication() = Unit\n"
                "}\n"
            ),
        }
        for relative_path, content in files.items():
            path = root / relative_path
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content, encoding="utf-8")

    def write_m03_move_layout(self, root: Path) -> Path:
        application = root / M03_APPLICATION_PATH
        application.parent.mkdir(parents=True, exist_ok=True)
        application.write_text(
            "package com.kiyori.app\n\n"
            "import com.ai.assistance.operit.BuildConfig\n"
            "import com.ai.assistance.operit.util.AppLogger\n\n"
            "class KiyoriApplication\n",
            encoding="utf-8",
        )
        architecture_root = root / "config/architecture"
        architecture_root.mkdir(parents=True)
        normalized = normalize_m03_application_text(
            application.read_text(encoding="utf-8")
        ).encode("utf-8")
        (architecture_root / "m03-application-normalized-sha256.txt").write_text(
            f"{hashlib.sha256(normalized).hexdigest()}\n",
            encoding="utf-8",
        )
        (architecture_root / "m03-application-operit-imports.txt").write_text(
            "com.ai.assistance.operit.BuildConfig\n"
            "com.ai.assistance.operit.util.AppLogger\n",
            encoding="utf-8",
        )
        lint_baseline = root / "app/lint-baseline.xml"
        lint_baseline.parent.mkdir(parents=True, exist_ok=True)
        lint_baseline.write_text(
            "\n".join(
                f'<location file="{M03_LINT_NEW_PATH}"/>'
                for _ in range(M03_LINT_PATH_COUNT)
            ),
            encoding="utf-8",
        )
        return architecture_root

    def write_m04_root_move_layout(self, root: Path) -> Path:
        host_locals = root / M04_HOST_LOCALS_PATH
        host_locals.parent.mkdir(parents=True)
        host_locals.write_text(
            "package com.ai.assistance.operit.ui.main.navigation\n\n"
            "val LocalTopBarActions = compositionLocalOf<Any> { Unit }\n"
            "val LocalOpenBrowser = compositionLocalOf<Any> { Unit }\n"
            "class TopBarTitleContent\n"
            "val LocalTopBarTitleContent = compositionLocalOf<Any> { Unit }\n"
            "val LocalAppNavigationModel = compositionLocalOf<Any> { Unit }\n",
            encoding="utf-8",
        )
        root_composable = root / M04_ROOT_PATH
        root_composable.parent.mkdir(parents=True, exist_ok=True)
        root_composable.write_text(
            "package com.kiyori.app\n\n"
            "import com.ai.assistance.operit.R\n"
            "import com.ai.assistance.operit.ui.main.AiHomeQuickAction\n"
            "import com.ai.assistance.operit.ui.main.PendingAiHomeActionHandler\n\n"
            "fun KiyoriApp() = AiHomeQuickAction to PendingAiHomeActionHandler\n",
            encoding="utf-8",
        )
        architecture_root = root / "config/architecture"
        architecture_root.mkdir(parents=True)
        normalized = root_composable.read_bytes().replace(b"\r\n", b"\n")
        (architecture_root / "m04-root-sha256.txt").write_text(
            f"{hashlib.sha256(normalized).hexdigest()}\n",
            encoding="utf-8",
        )
        (architecture_root / "m04-root-operit-imports.txt").write_text(
            "com.ai.assistance.operit.R\n"
            "com.ai.assistance.operit.ui.main.AiHomeQuickAction\n"
            "com.ai.assistance.operit.ui.main.PendingAiHomeActionHandler\n",
            encoding="utf-8",
        )
        content_host = root / M04D_MAIN_CONTENT_HOST_PATH
        content_host.parent.mkdir(parents=True, exist_ok=True)
        content_host.write_text(
            "package com.kiyori.app.startup\n\n"
            "import com.kiyori.app.KiyoriApp\n\n"
            "fun KiyoriMainContentHost() = KiyoriApp()\n",
            encoding="utf-8",
        )
        main_activity = root / M04_MAIN_ACTIVITY_PATH
        main_activity.parent.mkdir(parents=True, exist_ok=True)
        main_activity.write_text(
            "package com.ai.assistance.operit.ui.main\n\n"
            "import com.kiyori.app.startup.KiyoriMainContentHost\n\n"
            "fun hostRoot() = KiyoriMainContentHost()\n",
            encoding="utf-8",
        )
        return root_composable

    def write_m04b_policy_layout(self, root: Path) -> tuple[Path, Path]:
        policy = root / M04B_OPERIT_NAVIGATION_POLICY_PATH
        policy.parent.mkdir(parents=True, exist_ok=True)
        policy.write_text(
            "package com.kiyori.integration.operit.navigation\n\n"
            "import com.ai.assistance.operit.ui.main.navigation.NavigationEntryKind\n"
            "import com.ai.assistance.operit.ui.main.navigation.NavigationEntrySpec\n"
            "import com.ai.assistance.operit.ui.main.navigation.RouteEntry\n"
            "import com.ai.assistance.operit.ui.main.navigation.RouteEntrySource\n"
            "import com.ai.assistance.operit.ui.main.navigation.RouteSpec\n\n"
            "enum class AiDrawerSelectionEffect { CLOSE_ONLY, REPLACE_PRIMARY }\n"
            "enum class AiTopBarMode { DRAWER, BACK }\n"
            "fun resolveAiDrawerSelection() = Unit\n"
            "fun resolveAiTopBarMode() = Unit\n"
            "fun hasSameAiSettingsSourceFamily() = Unit\n"
            "fun NavigationEntrySpec.toAiPrimaryRouteEntry() = Unit\n"
            "fun NavigationEntrySpec.preservesAiPrimaryStack() = Unit\n"
            "fun buildAiPrimaryStack() = Unit\n",
            encoding="utf-8",
        )
        shell_state = root / M04_SHELL_STATE_PATH
        shell_state.parent.mkdir(parents=True, exist_ok=True)
        shell_state.write_text(
            "package com.kiyori.app.shell\n"
            "data class KiyoriShellState(val page: Int = 1)\n",
            encoding="utf-8",
        )
        root_composable = root / M04_ROOT_PATH
        root_composable.parent.mkdir(parents=True, exist_ok=True)
        integration_package = "com.kiyori.integration.operit.navigation"
        root_composable.write_text(
            "package com.kiyori.app\n\n"
            + "\n".join(
                f"import {integration_package}.{symbol}"
                for symbol in (
                    "AiDrawerSelectionEffect",
                    "AiTopBarMode",
                    "buildAiPrimaryStack",
                    "hasSameAiSettingsSourceFamily",
                    "preservesAiPrimaryStack",
                    "resolveAiDrawerSelection",
                    "resolveAiTopBarMode",
                    "toAiPrimaryRouteEntry",
                )
            )
            + "\n",
            encoding="utf-8",
        )
        architecture_root = root / "config/architecture"
        architecture_root.mkdir(parents=True)
        (architecture_root / "m04b-operit-navigation-policy-sha256.txt").write_text(
            hashlib.sha256(
                policy.read_bytes().replace(b"\r\n", b"\n")
            ).hexdigest()
            + "\n",
            encoding="utf-8",
        )
        (architecture_root / "m04b-operit-navigation-policy-imports.txt").write_text(
            "com.ai.assistance.operit.ui.main.navigation.NavigationEntryKind\n"
            "com.ai.assistance.operit.ui.main.navigation.NavigationEntrySpec\n"
            "com.ai.assistance.operit.ui.main.navigation.RouteEntry\n"
            "com.ai.assistance.operit.ui.main.navigation.RouteEntrySource\n"
            "com.ai.assistance.operit.ui.main.navigation.RouteSpec\n",
            encoding="utf-8",
        )
        (architecture_root / "m04b-shell-state-sha256.txt").write_text(
            hashlib.sha256(
                shell_state.read_bytes().replace(b"\r\n", b"\n")
            ).hexdigest()
            + "\n",
            encoding="utf-8",
        )
        return policy, shell_state

    def write_m04b_shell_state_layout(self, root: Path) -> tuple[Path, Path]:
        contract = root / M04B_BROWSER_EXIT_CONTRACT_PATH
        contract.parent.mkdir(parents=True, exist_ok=True)
        contract.write_text(
            "package com.kiyori.capability.browser.presentation\n"
            "enum class KiyoriBrowserExitPresentation { CLOSE, MINIMIZED_INDICATOR }\n",
            encoding="utf-8",
        )
        shell_state = root / M04_SHELL_STATE_PATH
        shell_state.parent.mkdir(parents=True, exist_ok=True)
        shell_state.write_text(
            "package com.kiyori.app.shell\n"
            "import com.kiyori.capability.browser.presentation.KiyoriBrowserExitPresentation\n"
            "import com.kiyori.capability.settings.navigation.KiyoriSettingsRoute\n"
            "enum class PrimaryDestination { SOFTWARE_HOME }\n"
            "enum class KiyoriBrowserReturnTarget { SOFTWARE_HOME }\n"
            "enum class SoftwareHomePage { HOME }\n"
            "enum class KiyoriShellChild { BROWSER_SETTINGS }\n"
            "enum class KiyoriShellExternalDestination { BROWSER_HOME }\n"
            "enum class KiyoriShellBackResult { CONSUMED }\n"
            "data class KiyoriShellBackTransition(val state: KiyoriShellState)\n"
            "data class KiyoriShellState(val exit: KiyoriBrowserExitPresentation)\n"
            "val KiyoriShellStateSaver = Unit\n",
            encoding="utf-8",
        )
        architecture_root = root / "config/architecture"
        architecture_root.mkdir(parents=True)
        (architecture_root / "m04b-browser-exit-presentation-sha256.txt").write_text(
            hashlib.sha256(
                contract.read_bytes().replace(b"\r\n", b"\n")
            ).hexdigest()
            + "\n",
            encoding="utf-8",
        )
        files = {
            M04_ROOT_PATH: (
                "package com.kiyori.app\n"
                "import com.kiyori.app.shell.BrowserWorkspaceReturnToken\n"
                "import com.kiyori.app.shell.KiyoriBrowserReturnTarget\n"
                "import com.kiyori.app.shell.KiyoriShellChild\n"
                "import com.kiyori.app.shell.KiyoriShellExternalDestination\n"
                "import com.kiyori.app.shell.KiyoriShellState\n"
                "import com.kiyori.app.shell.KiyoriShellStateSaver\n"
                "import com.kiyori.app.shell.KiyoriSettingsOrigin\n"
                "import com.kiyori.app.shell.KiyoriSettingsPresentation\n"
                "import com.kiyori.app.shell.PrimaryDestination\n"
                "import com.kiyori.app.shell.SoftwareHomePage\n"
                "import com.kiyori.app.shell.openExternalDestination\n"
                "import com.kiyori.app.shell.shouldPresentKiyoriPluginLoading\n"
                "import com.kiyori.capability.browser.presentation.KiyoriBrowserExitPresentation\n"
            ),
            (
                "app/src/main/java/com/ai/assistance/operit/ui/main/"
                "MainActivity.kt"
            ): (
                "package com.ai.assistance.operit.ui.main\n"
                "import com.kiyori.app.shell.KiyoriShellExternalDestination\n"
            ),
            (
                "app/src/main/java/com/ai/assistance/operit/ui/main/shell/"
                "KiyoriAiDrawer.kt"
            ): (
                "package com.ai.assistance.operit.ui.main.shell\n"
                "import com.kiyori.app.shell.calculateKiyoriAiDrawerWidthDp\n"
            ),
            (
                "app/src/main/java/com/ai/assistance/operit/ui/main/shell/"
                "KiyoriAppShell.kt"
            ): (
                "package com.ai.assistance.operit.ui.main.shell\n"
                "import com.kiyori.app.shell.KiyoriBrowserReturnTarget\n"
                "import com.kiyori.app.shell.KiyoriShellBackResult\n"
                "import com.kiyori.app.shell.KiyoriShellChild\n"
                "import com.kiyori.app.shell.KiyoriShellState\n"
                "import com.kiyori.app.shell.PrimaryDestination\n"
                "import com.kiyori.app.shell.SoftwareHomePage\n"
            ),
            (
                "app/src/main/java/com/ai/assistance/operit/ui/main/shell/"
                "KiyoriShellPages.kt"
            ): (
                "package com.ai.assistance.operit.ui.main.shell\n"
                "import com.kiyori.app.shell.PrimaryDestination\n"
            ),
            (
                "app/src/main/java/com/ai/assistance/operit/ui/features/browser/"
                "appshell/KiyoriBrowserHome.kt"
            ): (
                "package com.ai.assistance.operit.ui.features.browser.appshell\n"
                "import com.kiyori.capability.browser.presentation.KiyoriBrowserExitPresentation\n"
            ),
        }
        for relative_path, content in files.items():
            path = root / relative_path
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content, encoding="utf-8")
        return contract, shell_state

    def write_m04b_app_shell_layout(self, root: Path) -> Path:
        app_shell = root / M04B_APP_SHELL_PATH
        app_shell.parent.mkdir(parents=True, exist_ok=True)
        project_imports = (
            "com.ai.assistance.operit.ui.main.AiHomeQuickAction",
            "com.ai.assistance.operit.ui.main.navigation.NavigationEntrySpec",
            "com.ai.assistance.operit.ui.main.shell.KiyoriBookmarkDrawerHost",
            "com.ai.assistance.operit.ui.main.shell.KiyoriBottomNavigation",
            "com.ai.assistance.operit.ui.main.shell.KiyoriBrowserSettingsPage",
            "com.ai.assistance.operit.ui.main.shell.KiyoriDownloadDrawerHost",
            "com.ai.assistance.operit.ui.main.shell.KiyoriDownloadSettingsPage",
            "com.ai.assistance.operit.ui.main.shell.KiyoriFullScreenWebSearchPage",
            "com.ai.assistance.operit.ui.main.shell.KiyoriHistoryDrawerHost",
            "com.ai.assistance.operit.ui.main.shell.KiyoriMinusOnePage",
            "com.ai.assistance.operit.ui.main.shell.KiyoriModalAiDrawer",
            "com.ai.assistance.operit.ui.main.shell.KiyoriPlayerSettingsPage",
            "com.ai.assistance.operit.ui.main.shell.KiyoriPrimaryRootPage",
            "com.ai.assistance.operit.ui.main.shell.KiyoriSoftwareHomePage",
            "com.ai.assistance.operit.ui.main.shell.KiyoriWebSearchRequest",
            "com.kiyori.capability.browser.presentation.KiyoriBrowserWorkspaceRoute",
            "com.kiyori.capability.settings.navigation.KiyoriSettingsRoute",
            "com.kiyori.design.theme.KiyoriBrowserTheme",
            "com.kiyori.design.theme.KiyoriSettingsTheme",
        )
        app_shell.write_text(
            "package com.kiyori.app.shell\n\n"
            + "\n".join(f"import {imported}" for imported in project_imports)
            + "\n\n"
            + "fun KiyoriAppShell() = Unit\n"
            + "fun kiyoriStartupBeyondViewportPageCount() = Unit\n"
            + "fun shouldComposeKiyoriAiHost() = Unit\n"
            + "fun shouldPresentKiyoriPluginLoading() = Unit\n"
            + "fun shouldNotifyKiyoriAiHomeSettledForInitialPage() = Unit\n"
            + "fun shouldProvideKiyoriSettingsTheme() = Unit\n"
            + "fun calculateKiyoriAiHostTranslation() = Unit\n"
            + "fun shouldReverseKiyoriPagerDrag() = Unit\n"
            + "fun shouldPresentKiyoriDownloadDrawer() = Unit\n"
            + "fun shouldPresentKiyoriBookmarkDrawer() = Unit\n"
            + "fun shouldPresentKiyoriHistoryDrawer() = Unit\n"
            + "fun shouldEnableKiyoriShellBackHandler() = Unit\n"
            + "fun calculateKiyoriPagerPageOffset() = Unit\n",
            encoding="utf-8",
        )
        architecture_root = root / "config/architecture"
        architecture_root.mkdir(parents=True)
        normalized = normalize_m04b_app_shell_text(
            app_shell.read_text(encoding="utf-8")
        ).encode("utf-8")
        (
            architecture_root / "m04b-app-shell-normalized-sha256.txt"
        ).write_text(
            hashlib.sha256(normalized).hexdigest() + "\n",
            encoding="utf-8",
        )
        (architecture_root / "m04b-app-shell-operit-imports.txt").write_text(
            "\n".join(project_imports) + "\n",
            encoding="utf-8",
        )
        root_composable = root / M04_ROOT_PATH
        root_composable.parent.mkdir(parents=True, exist_ok=True)
        root_composable.write_text(
            "package com.kiyori.app\n"
            "import com.kiyori.app.shell.KiyoriAppShell\n"
            "fun KiyoriApp() { KiyoriAppShell() }\n",
            encoding="utf-8",
        )
        helper_symbols = (
            "calculateKiyoriAiHostTranslation",
            "calculateKiyoriPagerPageOffset",
            "kiyoriStartupBeyondViewportPageCount",
            "shouldComposeKiyoriAiHost",
            "shouldEnableKiyoriShellBackHandler",
            "shouldNotifyKiyoriAiHomeSettledForInitialPage",
            "shouldPresentKiyoriPluginLoading",
            "shouldPresentKiyoriBookmarkDrawer",
            "shouldPresentKiyoriDownloadDrawer",
            "shouldPresentKiyoriHistoryDrawer",
            "shouldProvideKiyoriSettingsTheme",
            "shouldReverseKiyoriPagerDrag",
        )
        test_path = (
            root
            / "app/src/test/java/com/ai/assistance/operit/ui/main/shell/"
            "KiyoriShellStateTest.kt"
        )
        test_path.parent.mkdir(parents=True, exist_ok=True)
        test_path.write_text(
            "package com.ai.assistance.operit.ui.main.shell\n"
            + "\n".join(
                f"import com.kiyori.app.shell.{symbol}"
                for symbol in helper_symbols
            )
            + "\n",
            encoding="utf-8",
        )
        return app_shell

    def write_m04b_ai_drawer_layout(self, root: Path) -> Path:
        ai_drawer = root / M04B_AI_DRAWER_PATH
        ai_drawer.parent.mkdir(parents=True, exist_ok=True)
        project_imports = (
            "com.ai.assistance.operit.R",
            "com.ai.assistance.operit.core.tools.AIToolHandler",
            "com.ai.assistance.operit.core.tools.packTool.PackageManager",
            "com.ai.assistance.operit.core.tools.system.AndroidPermissionLevel",
            "com.ai.assistance.operit.core.tools.system.ShizukuAuthorizer",
            "com.ai.assistance.operit.core.tools.system.action.ActionListenerFactory",
            "com.ai.assistance.operit.data.preferences.androidPermissionPreferences",
            "com.ai.assistance.operit.data.repository.WorkflowRepository",
            "com.ai.assistance.operit.ui.components.KiyoriSemanticIconBadge",
            "com.ai.assistance.operit.ui.main.navigation.NavigationEntrySpec",
            "com.ai.assistance.operit.ui.main.navigation.NavigationSurface",
            "com.kiyori.design.theme.KiyoriSemanticTone",
            "com.kiyori.design.theme.resolveColors",
        )
        ai_drawer.write_text(
            "package com.kiyori.app.shell\n\n"
            + "\n".join(f"import {imported}" for imported in project_imports)
            + "\n\n"
            + "fun KiyoriModalAiDrawer() = Unit\n"
            + "fun resolveKiyoriAiDrawerTone() = Unit\n",
            encoding="utf-8",
        )
        architecture_root = root / "config/architecture"
        architecture_root.mkdir(parents=True)
        (architecture_root / "m04b-ai-drawer-operit-imports.txt").write_text(
            "\n".join(project_imports) + "\n",
            encoding="utf-8",
        )
        app_shell = root / M04B_APP_SHELL_PATH
        app_shell.parent.mkdir(parents=True, exist_ok=True)
        app_shell.write_text(
            "package com.kiyori.app.shell\n"
            "fun hostDrawer() { KiyoriModalAiDrawer() }\n",
            encoding="utf-8",
        )
        test_path = (
            root
            / "app/src/test/java/com/ai/assistance/operit/ui/main/shell/"
            "KiyoriShellStateTest.kt"
        )
        test_path.parent.mkdir(parents=True, exist_ok=True)
        test_path.write_text(
            "package com.ai.assistance.operit.ui.main.shell\n"
            "import com.kiyori.app.shell.resolveKiyoriAiDrawerTone\n",
            encoding="utf-8",
        )
        return ai_drawer

    def write_m04b_primary_navigation_layout(self, root: Path) -> Path:
        navigation = root / M04B_PRIMARY_NAVIGATION_PATH
        navigation.parent.mkdir(parents=True, exist_ok=True)
        project_imports = (
            "com.ai.assistance.operit.R",
            "com.ai.assistance.operit.ui.features.websession.browser.chrome.WEB_SESSION_BROWSER_BOTTOM_ACTION_SIZE_DP",
            "com.ai.assistance.operit.ui.features.websession.browser.chrome.WEB_SESSION_BROWSER_BOTTOM_BOTTOM_PADDING_DP",
            "com.ai.assistance.operit.ui.features.websession.browser.chrome.WEB_SESSION_BROWSER_BOTTOM_CENTER_ICON_SIZE_DP",
            "com.ai.assistance.operit.ui.features.websession.browser.chrome.WEB_SESSION_BROWSER_BOTTOM_HORIZONTAL_PADDING_DP",
            "com.ai.assistance.operit.ui.features.websession.browser.chrome.WEB_SESSION_BROWSER_BOTTOM_ICON_SIZE_DP",
            "com.ai.assistance.operit.ui.features.websession.browser.chrome.WEB_SESSION_BROWSER_BOTTOM_TOP_PADDING_DP",
            "com.ai.assistance.operit.ui.main.shell.KiyoriFileManagementPage",
            "com.ai.assistance.operit.ui.main.shell.KiyoriSettingsHomePage",
            "com.kiyori.design.theme.KiyoriBottomNavigationSelectedFillColor",
        )
        navigation.write_text(
            "package com.kiyori.app.shell\n\n"
            + "\n".join(f"import {imported}" for imported in project_imports)
            + "\n\n"
            + "data class PrimaryDestinationVisual(val destination: PrimaryDestination)\n"
            + "val primaryDestinationVisuals = emptyList<PrimaryDestinationVisual>()\n"
            + "fun resolveKiyoriBottomNavigationSelectedStartScale() = Unit\n"
            + "fun resolveKiyoriBottomNavigationSelectedFinalScale() = Unit\n"
            + "fun resolveKiyoriBottomNavigationSelectedSpringDampingRatio() = Unit\n"
            + "fun KiyoriPrimaryRootPage() = Unit\n"
            + "fun KiyoriBottomNavigation() = Unit\n"
            + "fun KiyoriBottomNavigationIcon() = Unit\n",
            encoding="utf-8",
        )
        architecture_root = root / "config/architecture"
        architecture_root.mkdir(parents=True)
        (
            architecture_root / "m04b-primary-navigation-operit-imports.txt"
        ).write_text(
            "\n".join(project_imports) + "\n",
            encoding="utf-8",
        )
        app_shell = root / M04B_APP_SHELL_PATH
        app_shell.parent.mkdir(parents=True, exist_ok=True)
        app_shell.write_text(
            "package com.kiyori.app.shell\n"
            "fun hostPrimaryNavigation() {\n"
            "    KiyoriPrimaryRootPage()\n"
            "    KiyoriBottomNavigation()\n"
            "}\n",
            encoding="utf-8",
        )
        test_path = (
            root
            / "app/src/test/java/com/ai/assistance/operit/ui/main/shell/"
            "KiyoriShellStateTest.kt"
        )
        test_path.parent.mkdir(parents=True, exist_ok=True)
        test_path.write_text(
            "package com.ai.assistance.operit.ui.main.shell\n"
            "import com.kiyori.app.shell.resolveKiyoriBottomNavigationSelectedFinalScale\n"
            "import com.kiyori.app.shell.resolveKiyoriBottomNavigationSelectedSpringDampingRatio\n"
            "import com.kiyori.app.shell.resolveKiyoriBottomNavigationSelectedStartScale\n",
            encoding="utf-8",
        )
        return navigation

    def write_m04b_software_home_layout(self, root: Path) -> Path:
        software_home = root / M04B_SOFTWARE_HOME_PATH
        software_home.parent.mkdir(parents=True, exist_ok=True)
        project_imports = (
            "com.ai.assistance.operit.R",
            "com.ai.assistance.operit.ui.features.websession.browser.chrome.WebSessionBrowserWindowCountIcon",
            "com.ai.assistance.operit.ui.main.AiHomeQuickAction",
            "com.ai.assistance.operit.ui.main.weather.KiyoriWeatherRepository",
            "com.ai.assistance.operit.ui.main.weather.KiyoriWeatherState",
            "com.ai.assistance.operit.ui.main.weather.KiyoriWeatherVisual",
            "com.kiyori.design.theme.KiyoriSemanticTone",
            "com.kiyori.design.theme.kiyoriWeatherSunColor",
            "com.kiyori.design.theme.resolveColors",
        )
        software_home.write_text(
            "package com.kiyori.app.shell\n\n"
            + "\n".join(f"import {imported}" for imported in project_imports)
            + "\n\n"
            + "fun KiyoriSoftwareHomePage() = Unit\n"
            + "enum class KiyoriSoftwareHomeLayout { COMPACT }\n"
            + "enum class KiyoriSoftwareHomeHeightLayout { SHORT }\n"
            + "enum class KiyoriSoftwareHomeMode { SEARCH }\n"
            + "enum class KiyoriSoftwareHomePrimaryTarget { WEB_SEARCH }\n"
            + "const val KIYORI_SOFTWARE_HOME_GOLDEN_TOP_FRACTION = 0.382f\n"
            + "const val KIYORI_HOME_MODE_SEGMENT_WIDTH_DP = 120\n"
            + "const val KIYORI_HOME_MODE_SEGMENT_HEIGHT_DP = 32\n"
            + "const val KIYORI_HOME_BRAND_ICON_SIZE_DP = 18\n"
            + "const val KIYORI_HOME_REGULAR_SEARCH_FRAME_HEIGHT_DP = 114\n"
            + "const val KIYORI_HOME_SHORT_SEARCH_FRAME_HEIGHT_DP = 96\n"
            + "const val KIYORI_HOME_COMPACT_HORIZONTAL_PADDING_DP = 24\n"
            + "const val KIYORI_HOME_MEDIUM_HORIZONTAL_PADDING_DP = 48\n"
            + "const val KIYORI_HOME_EXPANDED_HORIZONTAL_PADDING_DP = 72\n"
            + "const val KIYORI_HOME_COMPACT_MAX_WIDTH_DP = 544\n"
            + "const val KIYORI_HOME_MEDIUM_MAX_WIDTH_DP = 584\n"
            + "const val KIYORI_HOME_EXPANDED_MAX_WIDTH_DP = 624\n"
            + "const val KIYORI_HOME_SEARCH_FRAME_STROKE_WIDTH_DP = 1\n"
            + "const val KIYORI_HOME_TOOL_ICON_SIZE_DP = 20\n"
            + "const val KIYORI_HOME_ATTACHMENT_ICON_SIZE_DP = 24\n"
            + "const val KIYORI_HOME_ATTACHMENT_ICON_ALPHA = 0.9f\n"
            + "val KIYORI_HOME_SEARCH_FRAME_GRADIENT_COLORS = emptyList<Unit>()\n"
            + "val KIYORI_HOME_SELECTED_LIGHT_COLOR = Unit\n"
            + "val KIYORI_HOME_SELECTED_DARK_COLOR = Unit\n"
            + "fun resolveKiyoriSoftwareHomeLayout() = Unit\n"
            + "fun resolveKiyoriSoftwareHomeHeightLayout() = Unit\n"
            + "fun resolveKiyoriSoftwareHomeSearchTopY() = Unit\n"
            + "fun resolveKiyoriSoftwareHomePrimaryTarget() = Unit\n"
            + "fun KiyoriHomeTopActions() = Unit\n"
            + "fun KiyoriHomeBrandTitle() = Unit\n"
            + "fun KiyoriHomeSearchFrame() = Unit\n"
            + "fun KiyoriSearchAiSegment() = Unit\n"
            + "fun KiyoriSearchAiSegmentOption() = Unit\n"
            + "fun KiyoriHomeToolButton() = Unit\n"
            + "fun KiyoriWeatherButton() = Unit\n"
            + "fun KiyoriBrowserWindowsButton() = Unit\n"
            + "fun kiyoriWeatherIcon() = Unit\n"
            + "fun Modifier.kiyoriGradientSearchFrame() = Unit\n",
            encoding="utf-8",
        )
        architecture_root = root / "config/architecture"
        architecture_root.mkdir(parents=True)
        (
            architecture_root / "m04b-software-home-operit-imports.txt"
        ).write_text(
            "\n".join(project_imports) + "\n",
            encoding="utf-8",
        )
        app_shell = root / M04B_APP_SHELL_PATH
        app_shell.parent.mkdir(parents=True, exist_ok=True)
        app_shell.write_text(
            "package com.kiyori.app.shell\n"
            "fun hostSoftwareHome() { KiyoriSoftwareHomePage() }\n",
            encoding="utf-8",
        )
        tested_symbols = (
            "KiyoriSoftwareHomeLayout",
            "KiyoriSoftwareHomeHeightLayout",
            "KiyoriSoftwareHomeMode",
            "KiyoriSoftwareHomePrimaryTarget",
            "KIYORI_SOFTWARE_HOME_GOLDEN_TOP_FRACTION",
            "KIYORI_HOME_MODE_SEGMENT_WIDTH_DP",
            "KIYORI_HOME_MODE_SEGMENT_HEIGHT_DP",
            "KIYORI_HOME_BRAND_ICON_SIZE_DP",
            "KIYORI_HOME_REGULAR_SEARCH_FRAME_HEIGHT_DP",
            "KIYORI_HOME_SHORT_SEARCH_FRAME_HEIGHT_DP",
            "KIYORI_HOME_COMPACT_HORIZONTAL_PADDING_DP",
            "KIYORI_HOME_MEDIUM_HORIZONTAL_PADDING_DP",
            "KIYORI_HOME_EXPANDED_HORIZONTAL_PADDING_DP",
            "KIYORI_HOME_COMPACT_MAX_WIDTH_DP",
            "KIYORI_HOME_MEDIUM_MAX_WIDTH_DP",
            "KIYORI_HOME_EXPANDED_MAX_WIDTH_DP",
            "KIYORI_HOME_SEARCH_FRAME_STROKE_WIDTH_DP",
            "KIYORI_HOME_TOOL_ICON_SIZE_DP",
            "KIYORI_HOME_ATTACHMENT_ICON_SIZE_DP",
            "KIYORI_HOME_ATTACHMENT_ICON_ALPHA",
            "KIYORI_HOME_SEARCH_FRAME_GRADIENT_COLORS",
            "KIYORI_HOME_SELECTED_LIGHT_COLOR",
            "KIYORI_HOME_SELECTED_DARK_COLOR",
            "resolveKiyoriSoftwareHomeLayout",
            "resolveKiyoriSoftwareHomeHeightLayout",
            "resolveKiyoriSoftwareHomeSearchTopY",
            "resolveKiyoriSoftwareHomePrimaryTarget",
        )
        test_path = root / (
            "app/src/test/java/com/ai/assistance/operit/ui/main/shell/"
            "KiyoriSoftwareHomeSearchTest.kt"
        )
        test_path.parent.mkdir(parents=True, exist_ok=True)
        test_path.write_text(
            "package com.ai.assistance.operit.ui.main.shell\n\n"
            + "\n".join(
                f"import com.kiyori.app.shell.{symbol}"
                for symbol in tested_symbols
            )
            + "\n",
            encoding="utf-8",
        )
        return software_home

    def write_m04b_browser_search_layout(self, root: Path) -> Path:
        browser_search = root / M04B_BROWSER_SEARCH_PATH
        browser_search.parent.mkdir(parents=True, exist_ok=True)
        operit_imports = (
            "com.ai.assistance.operit.R",
            "com.ai.assistance.operit.core.browser.navigation.BrowserAddressResolver",
            "com.ai.assistance.operit.core.browser.presentation.BrowserPresentationCoordinator",
            "com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionHistoryStore",
            "com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionProfile",
            "com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionSearchEngine",
            "com.ai.assistance.operit.core.tools.defaultTool.websession.browser.opposite",
            "com.ai.assistance.operit.ui.features.websession.browser.WebSessionBrowserSearchScreen",
            M04B_BROWSER_SEARCH_CAPABILITY_IMPORT,
        )
        browser_search.write_text(
            "package com.kiyori.app.shell\n\n"
            + "\n".join(f"import {imported}" for imported in operit_imports)
            + "\n\n"
            + "fun KiyoriFullScreenWebSearchPage() = Unit\n"
            + "data class KiyoriWebSearchRequest(val query: String)\n"
            + "fun resolveKiyoriWebSearchRequest() = Unit\n",
            encoding="utf-8",
        )
        architecture_root = root / "config/architecture"
        architecture_root.mkdir(parents=True)
        normalized = browser_search.read_bytes().replace(b"\r\n", b"\n")
        (architecture_root / "m04b-browser-search-sha256.txt").write_text(
            hashlib.sha256(normalized).hexdigest() + "\n",
            encoding="utf-8",
        )
        (
            architecture_root / "m04b-browser-search-operit-imports.txt"
        ).write_text(
            "\n".join(operit_imports) + "\n",
            encoding="utf-8",
        )
        app_shell = root / M04B_APP_SHELL_PATH
        app_shell.parent.mkdir(parents=True, exist_ok=True)
        app_shell.write_text(
            "package com.kiyori.app.shell\n"
            "fun hostBrowserSearch(request: KiyoriWebSearchRequest) {\n"
            "    KiyoriFullScreenWebSearchPage()\n"
            "}\n",
            encoding="utf-8",
        )
        root_path = root / M04_ROOT_PATH
        root_path.parent.mkdir(parents=True, exist_ok=True)
        root_path.write_text(
            "package com.kiyori.app\n"
            "import com.kiyori.app.shell.KiyoriWebSearchRequest\n"
            "import com.kiyori.app.shell.resolveKiyoriWebSearchRequest\n",
            encoding="utf-8",
        )
        test_path = root / (
            "app/src/test/java/com/ai/assistance/operit/ui/main/shell/"
            "KiyoriSoftwareHomeSearchTest.kt"
        )
        test_path.parent.mkdir(parents=True, exist_ok=True)
        test_path.write_text(
            "package com.ai.assistance.operit.ui.main.shell\n"
            "import com.kiyori.app.shell.KiyoriWebSearchRequest\n"
            "import com.kiyori.app.shell.resolveKiyoriWebSearchRequest\n",
            encoding="utf-8",
        )
        return browser_search

    def write_m04c_navigation_integration_layout(self, root: Path) -> Path:
        catalog = root / M04C_ROUTE_CATALOG_PATH
        catalog.parent.mkdir(parents=True, exist_ok=True)
        catalog_imports = (
            "com.ai.assistance.operit.core.tools.packTool.PackageManager",
            "com.ai.assistance.operit.ui.common.NavItem",
            "com.ai.assistance.operit.ui.main.navigation.AppNavigationModel",
            "com.ai.assistance.operit.ui.main.navigation.NavigationEntryActionSpec",
            "com.ai.assistance.operit.ui.main.navigation.NavigationEntryKind",
            "com.ai.assistance.operit.ui.main.navigation.NavigationEntrySpec",
            "com.ai.assistance.operit.ui.main.navigation.NavigationSurface",
            "com.ai.assistance.operit.ui.main.navigation.RouteEntry",
            "com.ai.assistance.operit.ui.main.navigation.RouteEntrySource",
            "com.ai.assistance.operit.ui.main.navigation.RouteRuntime",
            "com.ai.assistance.operit.ui.main.navigation.RouteSpec",
            "com.ai.assistance.operit.ui.main.screens.Screen",
            "com.ai.assistance.operit.ui.main.screens.ScreenRouteRegistry",
        )
        catalog.write_text(
            "package com.kiyori.integration.operit.navigation\n\n"
            + "\n".join(f"import {imported}" for imported in catalog_imports)
            + "\n\n"
            + "object AppRouteCatalog {\n"
            + "    fun build(context: Context, packageManager: PackageManager) = Unit\n"
            + "}\n",
            encoding="utf-8",
        )
        integration = root / M04C_NAVIGATION_INTEGRATION_PATH
        integration_imports = (
            "com.ai.assistance.operit.core.tools.AIToolHandler",
            "com.ai.assistance.operit.core.tools.packTool.PackageManager",
            "com.ai.assistance.operit.ui.common.NavItem",
            "com.ai.assistance.operit.ui.main.navigation.AppNavigationModel",
            "com.ai.assistance.operit.ui.main.navigation.AppRouteDiscoveryGateway",
            "com.ai.assistance.operit.ui.main.navigation.AppRouterGateway",
            "com.ai.assistance.operit.ui.main.navigation.AppRouterState",
            "com.ai.assistance.operit.ui.main.navigation.NavigationEntrySpec",
            "com.ai.assistance.operit.ui.main.navigation.RouteEntry",
            "com.ai.assistance.operit.ui.main.navigation.RouteEntrySource",
            "com.ai.assistance.operit.ui.main.screens.Screen",
        )
        integration.write_text(
            "package com.kiyori.integration.operit.navigation\n\n"
            + "\n".join(f"import {imported}" for imported in integration_imports)
            + "\n\n"
            + "class OperitNavigationIntegration\n"
            + "fun rememberOperitNavigationIntegration() {\n"
            + "    PackageManager.getInstance(context, AIToolHandler.getInstance(context))\n"
            + "}\n"
            + "fun OperitNavigationIntegrationEffects() {\n"
            + "    packageManager.addToolPkgRuntimeChangeListener(listener)\n"
            + "    packageManager.removeToolPkgRuntimeChangeListener(listener)\n"
            + "    AppRouterGateway.install(handler, reset)\n"
            + "    AppRouterGateway.clear()\n"
            + "    AppRouteDiscoveryGateway.install(provider)\n"
            + "    AppRouteDiscoveryGateway.clear()\n"
            + "}\n"
            + "fun List<NavigationEntrySpec>.findOperitNavigationRoot() = first()\n"
            + "fun List<NavigationEntrySpec>.toOperitExternalRouteEntry() = RouteEntry(routeId = \"x\")\n",
            encoding="utf-8",
        )
        architecture_root = root / "config/architecture"
        architecture_root.mkdir(parents=True)
        for path, hash_name, import_name, imports in (
            (
                catalog,
                "m04c-route-catalog-sha256.txt",
                "m04c-route-catalog-operit-imports.txt",
                catalog_imports,
            ),
            (
                integration,
                "m04c-navigation-integration-sha256.txt",
                "m04c-navigation-integration-operit-imports.txt",
                integration_imports,
            ),
        ):
            normalized = path.read_bytes().replace(b"\r\n", b"\n")
            (architecture_root / hash_name).write_text(
                hashlib.sha256(normalized).hexdigest() + "\n",
                encoding="utf-8",
            )
            (architecture_root / import_name).write_text(
                "\n".join(imports) + "\n",
                encoding="utf-8",
            )
        root_path = root / M04_ROOT_PATH
        root_path.parent.mkdir(parents=True, exist_ok=True)
        root_path.write_text(
            "package com.kiyori.app\n"
            "import com.kiyori.integration.operit.navigation.OperitNavigationIntegrationEffects\n"
            "import com.kiyori.integration.operit.navigation.rememberOperitNavigationIntegration\n"
            "import com.kiyori.integration.operit.navigation.toOperitExternalRouteEntry\n"
            "fun KiyoriApp() {\n"
            "    rememberOperitNavigationIntegration()\n"
            "    OperitNavigationIntegrationEffects()\n"
            "}\n",
            encoding="utf-8",
        )
        settings_test = root / M04C_SETTINGS_TEST_PATH
        settings_test.parent.mkdir(parents=True, exist_ok=True)
        settings_test.write_text(
            "package com.ai.assistance.operit.ui.main.shell\n"
            "import com.kiyori.integration.operit.navigation.AppRouteCatalog\n",
            encoding="utf-8",
        )
        return integration

    def write_m04d_main_pending_requests_layout(self, root: Path) -> Path:
        owner = root / M04D_MAIN_PENDING_REQUESTS_PATH
        owner.parent.mkdir(parents=True, exist_ok=True)
        owner_imports = (
            "com.ai.assistance.operit.ui.common.NavItem",
            "com.kiyori.app.shell.KiyoriShellExternalDestination",
        )
        owner.write_text(
            "package com.kiyori.app.startup\n\n"
            + "\n".join(f"import {imported}" for imported in owner_imports)
            + "\n\n"
            + "class KiyoriMainPendingRequests {\n"
            + "    fun recordShellDestination() = Unit\n"
            + "    fun recordShortcut() = Unit\n"
            + "    fun recordRoute() = Unit\n"
            + "    fun recordGitHubAuth() = Unit\n"
            + "    fun recordBrowser() = Unit\n"
            + "    fun recordSharedFiles() = Unit\n"
            + "    fun recordSharedText() = Unit\n"
            + "    fun takeGitHubAuthUri() = Unit\n"
            + "    fun clearSharedText() = Unit\n"
            + "    fun clearSharedFiles() = Unit\n"
            + "    fun clearSharedFilesAndText() = Unit\n"
            + "    fun consumeShortcut() = Unit\n"
            + "    fun updateCurrentMainNavItem() = Unit\n"
            + "    fun consumeRoute() = Unit\n"
            + "    fun consumeBrowser() = Unit\n"
            + "    fun consumeShell() = Unit\n"
            + "}\n",
            encoding="utf-8",
        )
        architecture_root = root / "config/architecture"
        architecture_root.mkdir(parents=True)
        normalized = owner.read_bytes().replace(b"\r\n", b"\n")
        (
            architecture_root / "m04d-main-pending-requests-sha256.txt"
        ).write_text(
            hashlib.sha256(normalized).hexdigest() + "\n",
            encoding="utf-8",
        )
        (
            architecture_root
            / "m04d-main-pending-requests-project-imports.txt"
        ).write_text(
            "\n".join(owner_imports) + "\n",
            encoding="utf-8",
        )

        main_activity = root / M04_MAIN_ACTIVITY_PATH
        main_activity.parent.mkdir(parents=True, exist_ok=True)
        main_activity.write_text(
            "package com.ai.assistance.operit.ui.main\n"
            "import com.kiyori.app.startup.KiyoriMainPendingRequests\n"
            "class MainActivity {\n"
            "    private val pendingRequests = KiyoriMainPendingRequests()\n"
            "    fun host() {\n"
            "        pendingRequests.recordShellDestination()\n"
            "        pendingRequests.recordShortcut()\n"
            "        pendingRequests.recordRoute()\n"
            "        pendingRequests.recordGitHubAuth()\n"
            "        pendingRequests.recordBrowser()\n"
            "        pendingRequests.recordSharedFiles()\n"
            "        pendingRequests.recordSharedText()\n"
            "        pendingRequests.takeGitHubAuthUri()\n"
            "        pendingRequests.clearSharedText()\n"
            "        pendingRequests.clearSharedFiles()\n"
            "        pendingRequests.clearSharedFilesAndText()\n"
            "        pendingRequests.consumeShortcut()\n"
            "        pendingRequests.updateCurrentMainNavItem()\n"
            "        pendingRequests.consumeRoute()\n"
            "        pendingRequests.consumeBrowser()\n"
            "        pendingRequests.consumeShell()\n"
            "    }\n"
            "}\n",
            encoding="utf-8",
        )
        owner_test = root / M04D_MAIN_PENDING_REQUESTS_TEST_PATH
        owner_test.parent.mkdir(parents=True, exist_ok=True)
        owner_test.write_text(
            "package com.kiyori.app.startup\n"
            "class KiyoriMainPendingRequestsTest {\n"
            "    private val requests = KiyoriMainPendingRequests()\n"
            "}\n",
            encoding="utf-8",
        )
        return owner

    def write_m04d_main_intent_decoder_layout(self, root: Path) -> Path:
        pending_requests = root / M04D_MAIN_PENDING_REQUESTS_PATH
        pending_requests.parent.mkdir(parents=True, exist_ok=True)
        pending_requests.write_text(
            "package com.kiyori.app.startup\n"
            "class KiyoriMainPendingRequests\n",
            encoding="utf-8",
        )

        decoder = root / M04D_MAIN_INTENT_DECODER_PATH
        decoder_imports = (
            "com.ai.assistance.operit.data.preferences.GitHubAuthPreferences",
            "com.ai.assistance.operit.widget.ToolPkgDesktopWidgetHost",
            "com.kiyori.app.shell.KiyoriShellExternalDestination",
        )
        constants = (
            "ACTION_OPEN_SETTINGS_SHORTCUT",
            "ACTION_OPEN_KIYORI_BROWSER",
            "ACTION_RESTORE_KIYORI_BROWSER_FROM_INDICATOR",
            "ACTION_OPEN_KIYORI_DOWNLOADS",
            "ACTION_OPEN_KIYORI_DOWNLOAD_TASK",
            "EXTRA_KIYORI_DOWNLOAD_TASK_ID",
            "ACTION_OPEN_KIYORI_BROWSER_SETTINGS",
            "ACTION_OPEN_KIYORI_DOWNLOAD_SETTINGS",
            "ACTION_RESTART_PLAYER_AFTER_CRASH",
            "EXTRA_PLAYER_RUNTIME_GENERATION",
        )
        decoder.write_text(
            "package com.kiyori.app.startup\n\n"
            + "\n".join(f"import {imported}" for imported in decoder_imports)
            + "\n\n"
            + "object KiyoriMainIntentContract {\n"
            + "\n".join(f"    const val {name} = \"{name}\"" for name in constants)
            + "\n}\n"
            + "sealed interface KiyoriMainIntentCommand\n"
            + "data class KiyoriMainIntentDecoding(val command: KiyoriMainIntentCommand)\n"
            + "fun resolveKiyoriShellExternalDestination() = Unit\n"
            + "fun resolveKiyoriDownloadTaskId() = Unit\n"
            + "fun decodeKiyoriMainIntent() {\n"
            + "    intent.getLongExtra(key, 0L)\n"
            + "    intent.getStringExtra(key)\n"
            + "    intent.getParcelableExtra(key)\n"
            + "    intent.getParcelableArrayListExtra(key)\n"
            + "    GitHubAuthPreferences.isOAuthRedirectUri(uri)\n"
            + "}\n",
            encoding="utf-8",
        )
        architecture_root = root / "config/architecture"
        architecture_root.mkdir(parents=True)
        normalized = decoder.read_bytes().replace(b"\r\n", b"\n")
        (
            architecture_root / "m04d-main-intent-decoder-sha256.txt"
        ).write_text(
            hashlib.sha256(normalized).hexdigest() + "\n",
            encoding="utf-8",
        )
        (
            architecture_root / "m04d-main-intent-decoder-project-imports.txt"
        ).write_text(
            "\n".join(decoder_imports) + "\n",
            encoding="utf-8",
        )

        main_activity = root / M04_MAIN_ACTIVITY_PATH
        main_activity.parent.mkdir(parents=True, exist_ok=True)
        main_activity.write_text(
            "package com.ai.assistance.operit.ui.main\n"
            "import com.kiyori.app.startup.KiyoriMainIntentCommand\n"
            "import com.kiyori.app.startup.KiyoriMainIntentContract\n"
            "import com.kiyori.app.startup.decodeKiyoriMainIntent\n"
            "class MainActivity {\n"
            "    companion object {\n"
            + "\n".join(
                f"        const val {name} = KiyoriMainIntentContract.{name}"
                for name in constants
            )
            + "\n    }\n"
            + "    fun handle() = decodeKiyoriMainIntent()\n"
            + "    fun consume(command: KiyoriMainIntentCommand) = Unit\n"
            + "}\n",
            encoding="utf-8",
        )
        decoder_test = root / M04D_MAIN_INTENT_DECODER_TEST_PATH
        decoder_test.parent.mkdir(parents=True, exist_ok=True)
        decoder_test.write_text(
            "package com.kiyori.app.startup\n"
            "class KiyoriMainIntentDecoderTest {\n"
            "    fun test() {\n"
            "        val command: KiyoriMainIntentCommand? = null\n"
            "        decodeKiyoriMainIntent()\n"
            "    }\n"
            "}\n",
            encoding="utf-8",
        )
        return decoder

    def write_m04d_main_display_coordinator_layout(self, root: Path) -> Path:
        decoder = root / M04D_MAIN_INTENT_DECODER_PATH
        decoder.parent.mkdir(parents=True, exist_ok=True)
        decoder.write_text(
            "package com.kiyori.app.startup\n"
            "fun decodeKiyoriMainIntent() = Unit\n",
            encoding="utf-8",
        )

        coordinator = root / M04D_MAIN_DISPLAY_COORDINATOR_PATH
        coordinator_imports = ("com.ai.assistance.operit.util.AppLogger",)
        coordinator.write_text(
            "package com.kiyori.app.startup\n\n"
            + "\n".join(f"import {imported}" for imported in coordinator_imports)
            + "\n\n"
            + "data class KiyoriDisplayModeCandidate(val id: Int)\n"
            + "data class KiyoriDisplayModeSelection(val id: Int)\n"
            + "fun selectHighestRefreshRateMode() = Unit\n"
            + "fun selectHighestRefreshRate() = Unit\n"
            + "object KiyoriMainDisplayCoordinator {\n"
            + "    fun configure() {\n"
            + "        window.setSustainedPerformanceMode(true)\n"
            + "        window.attributes.preferredDisplayModeId = mode\n"
            + "        window.attributes.preferredRefreshRate = rate\n"
            + "        window.setFlags(\n"
            + "            FLAG_HARDWARE_ACCELERATED,\n"
            + "            FLAG_HARDWARE_ACCELERATED,\n"
            + "        )\n"
            + "        context.getSystemService(service)\n"
            + "    }\n"
            + "}\n",
            encoding="utf-8",
        )
        architecture_root = root / "config/architecture"
        architecture_root.mkdir(parents=True)
        normalized = coordinator.read_bytes().replace(b"\r\n", b"\n")
        (
            architecture_root / "m04d-main-display-coordinator-sha256.txt"
        ).write_text(
            hashlib.sha256(normalized).hexdigest() + "\n",
            encoding="utf-8",
        )
        (
            architecture_root / "m04d-main-display-coordinator-project-imports.txt"
        ).write_text(
            "\n".join(coordinator_imports) + "\n",
            encoding="utf-8",
        )

        main_activity = root / M04_MAIN_ACTIVITY_PATH
        main_activity.parent.mkdir(parents=True, exist_ok=True)
        main_activity.write_text(
            "package com.ai.assistance.operit.ui.main\n"
            "import com.kiyori.app.startup.KiyoriMainDisplayCoordinator\n"
            "class MainActivity {\n"
            "    fun onCreate() {\n"
            "        KiyoriMainDisplayCoordinator.configure(this)\n"
            "    }\n"
            "}\n",
            encoding="utf-8",
        )
        coordinator_test = root / M04D_MAIN_DISPLAY_COORDINATOR_TEST_PATH
        coordinator_test.parent.mkdir(parents=True, exist_ok=True)
        coordinator_test.write_text(
            "package com.kiyori.app.startup\n"
            "class KiyoriMainDisplayCoordinatorTest {\n"
            "    fun test() {\n"
            "        selectHighestRefreshRateMode()\n"
            "        selectHighestRefreshRate()\n"
            "    }\n"
            "}\n",
            encoding="utf-8",
        )
        return coordinator

    def write_m04d_main_shared_content_coordinator_layout(
        self,
        root: Path,
    ) -> Path:
        display = root / M04D_MAIN_DISPLAY_COORDINATOR_PATH
        display.parent.mkdir(parents=True, exist_ok=True)
        display.write_text(
            "package com.kiyori.app.startup\n"
            "object KiyoriMainDisplayCoordinator\n",
            encoding="utf-8",
        )

        coordinator = root / M04D_MAIN_SHARED_CONTENT_COORDINATOR_PATH
        coordinator_imports = (
            "com.ai.assistance.operit.R",
            "com.ai.assistance.operit.ui.main.SharedFileHandler",
            "com.ai.assistance.operit.util.AppLogger",
        )
        coordinator.write_text(
            "package com.kiyori.app.startup\n\n"
            + "\n".join(f"import {imported}" for imported in coordinator_imports)
            + "\n\n"
            + "data class KiyoriPendingSharedFiles(val value: Unit)\n"
            + "fun resolvePendingSharedText() = Unit\n"
            + "fun resolvePendingSharedFiles() = Unit\n"
            + "class KiyoriMainSharedContentCoordinator {\n"
            + "    fun process() {\n"
            + "        SharedFileHandler.setSharedText(text)\n"
            + "        SharedFileHandler.setSharedFiles(uris, text)\n"
            + "        pendingRequests.clearSharedText()\n"
            + "        pendingRequests.clearSharedFilesAndText()\n"
            + "        pendingRequests.clearSharedFiles()\n"
            + "        lifecycleScope.launch { Unit }\n"
            + "        Toast.makeText(context, message, duration)\n"
            + "    }\n"
            + "}\n",
            encoding="utf-8",
        )
        architecture_root = root / "config/architecture"
        architecture_root.mkdir(parents=True)
        normalized = coordinator.read_bytes().replace(b"\r\n", b"\n")
        (
            architecture_root
            / "m04d-main-shared-content-coordinator-sha256.txt"
        ).write_text(
            hashlib.sha256(normalized).hexdigest() + "\n",
            encoding="utf-8",
        )
        (
            architecture_root
            / "m04d-main-shared-content-coordinator-project-imports.txt"
        ).write_text(
            "\n".join(coordinator_imports) + "\n",
            encoding="utf-8",
        )

        main_activity = root / M04_MAIN_ACTIVITY_PATH
        main_activity.parent.mkdir(parents=True, exist_ok=True)
        main_activity.write_text(
            "package com.ai.assistance.operit.ui.main\n"
            "import com.kiyori.app.startup.KiyoriMainSharedContentCoordinator\n"
            "class MainActivity {\n"
            "    private val sharedContentCoordinator by lazy {\n"
            "        KiyoriMainSharedContentCoordinator()\n"
            "    }\n"
            "    fun first() {\n"
            "        sharedContentCoordinator.processPendingSharedFiles()\n"
            "        sharedContentCoordinator.processPendingSharedText()\n"
            "    }\n"
            "}\n",
            encoding="utf-8",
        )
        content_host = root / M04D_MAIN_CONTENT_HOST_PATH
        content_host.parent.mkdir(parents=True, exist_ok=True)
        content_host.write_text(
            "package com.kiyori.app.startup\n"
            "fun KiyoriMainContentHost(\n"
            "    sharedContentCoordinator: KiyoriMainSharedContentCoordinator,\n"
            ") {\n"
            "    sharedContentCoordinator.processPendingSharedFiles()\n"
            "    sharedContentCoordinator.processPendingSharedText()\n"
            "}\n",
            encoding="utf-8",
        )
        coordinator_test = root / M04D_MAIN_SHARED_CONTENT_COORDINATOR_TEST_PATH
        coordinator_test.parent.mkdir(parents=True, exist_ok=True)
        coordinator_test.write_text(
            "package com.kiyori.app.startup\n"
            "class KiyoriMainSharedContentCoordinatorTest {\n"
            "    fun test() {\n"
            "        resolvePendingSharedText()\n"
            "        resolvePendingSharedFiles()\n"
            "    }\n"
            "}\n",
            encoding="utf-8",
        )
        return coordinator

    def write_m04d_main_task_visibility_coordinator_layout(
        self,
        root: Path,
    ) -> Path:
        shared_content = root / M04D_MAIN_SHARED_CONTENT_COORDINATOR_PATH
        shared_content.parent.mkdir(parents=True, exist_ok=True)
        shared_content.write_text(
            "package com.kiyori.app.startup\n"
            "class KiyoriMainSharedContentCoordinator\n",
            encoding="utf-8",
        )

        coordinator = root / M04D_MAIN_TASK_VISIBILITY_COORDINATOR_PATH
        coordinator_imports = (
            "com.ai.assistance.operit.api.chat.AIForegroundService",
            "com.ai.assistance.operit.util.AppLogger",
        )
        coordinator.write_text(
            "package com.kiyori.app.startup\n\n"
            + "\n".join(f"import {imported}" for imported in coordinator_imports)
            + "\n\n"
            + "fun shouldRestoreRuntimeTaskVisibility() = Unit\n"
            + "object KiyoriMainTaskVisibilityCoordinator {\n"
            + "    fun restoreIfNeeded() {\n"
            + "        Build.VERSION.SDK_INT\n"
            + "        Build.VERSION_CODES.LOLLIPOP\n"
            + "        AIForegroundService.isRunning.get()\n"
            + "        activity.getSystemService(service)\n"
            + "        activityManager.appTasks\n"
            + "        task.setExcludeFromRecents(false)\n"
            + "    }\n"
            + "}\n",
            encoding="utf-8",
        )
        architecture_root = root / "config/architecture"
        architecture_root.mkdir(parents=True)
        normalized = coordinator.read_bytes().replace(b"\r\n", b"\n")
        (
            architecture_root
            / "m04d-main-task-visibility-coordinator-sha256.txt"
        ).write_text(
            hashlib.sha256(normalized).hexdigest() + "\n",
            encoding="utf-8",
        )
        (
            architecture_root
            / "m04d-main-task-visibility-coordinator-project-imports.txt"
        ).write_text(
            "\n".join(coordinator_imports) + "\n",
            encoding="utf-8",
        )

        main_activity = root / M04_MAIN_ACTIVITY_PATH
        main_activity.parent.mkdir(parents=True, exist_ok=True)
        main_activity.write_text(
            "package com.ai.assistance.operit.ui.main\n"
            "import com.kiyori.app.startup.KiyoriMainTaskVisibilityCoordinator\n"
            "class MainActivity {\n"
            "    fun first() {\n"
            "        KiyoriMainTaskVisibilityCoordinator.restoreIfNeeded(this)\n"
            "    }\n"
            "    fun second() {\n"
            "        KiyoriMainTaskVisibilityCoordinator.restoreIfNeeded(this)\n"
            "    }\n"
            "}\n",
            encoding="utf-8",
        )
        coordinator_test = root / M04D_MAIN_TASK_VISIBILITY_COORDINATOR_TEST_PATH
        coordinator_test.parent.mkdir(parents=True, exist_ok=True)
        coordinator_test.write_text(
            "package com.kiyori.app.startup\n"
            "class KiyoriMainTaskVisibilityCoordinatorTest {\n"
            "    fun test() {\n"
            "        shouldRestoreRuntimeTaskVisibility()\n"
            "    }\n"
            "}\n",
            encoding="utf-8",
        )
        return coordinator

    def write_m04d_main_orientation_coordinator_layout(
        self,
        root: Path,
    ) -> Path:
        task_visibility = root / M04D_MAIN_TASK_VISIBILITY_COORDINATOR_PATH
        task_visibility.parent.mkdir(parents=True, exist_ok=True)
        task_visibility.write_text(
            "package com.kiyori.app.startup\n"
            "object KiyoriMainTaskVisibilityCoordinator\n",
            encoding="utf-8",
        )

        coordinator = root / M04D_MAIN_ORIENTATION_COORDINATOR_PATH
        coordinator_imports = (
            "com.ai.assistance.operit.R",
            "com.kiyori.platform.logging.KiyoriLogger",
        )
        coordinator.write_text(
            "package com.kiyori.app.startup\n\n"
            + "\n".join(f"import {imported}" for imported in coordinator_imports)
            + "\n\n"
            + "data class KiyoriMainOrientationState(val value: Int)\n"
            + "fun resolveKiyoriMainOrientationChange() = Unit\n"
            + "class KiyoriMainOrientationCoordinator {\n"
            + "    private var state by mutableStateOf(Unit)\n"
            + "    val showChangeDialog = false\n"
            + "    fun initialize(value: Int) = Unit\n"
            + "    fun onConfigurationChanged(value: Int) {\n"
            + "        resolveKiyoriMainOrientationChange()\n"
            + "        KiyoriLogger.d(tag, message)\n"
            + "    }\n"
            + "    fun dismissChangeDialog() = Unit\n"
            + "}\n"
            + "@Composable\n"
            + "fun KiyoriMainOrientationDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {\n"
            + "    AlertDialog(\n"
            + "        onDismissRequest = onDismiss,\n"
            + "        title = { stringResource(R.string.dialog_title_orientation_change) },\n"
            + "        text = { stringResource(R.string.dialog_message_orientation_change) },\n"
            + "        confirmButton = { stringResource(R.string.dialog_button_confirm) },\n"
            + "        dismissButton = { stringResource(R.string.dialog_button_dismiss) },\n"
            + "    )\n"
            + "}\n",
            encoding="utf-8",
        )
        architecture_root = root / "config/architecture"
        architecture_root.mkdir(parents=True)
        normalized = coordinator.read_bytes().replace(b"\r\n", b"\n")
        (
            architecture_root
            / "m04d-main-orientation-coordinator-sha256.txt"
        ).write_text(
            hashlib.sha256(normalized).hexdigest() + "\n",
            encoding="utf-8",
        )
        (
            architecture_root
            / "m04d-main-orientation-coordinator-project-imports.txt"
        ).write_text(
            "\n".join(coordinator_imports) + "\n",
            encoding="utf-8",
        )

        main_activity = root / M04_MAIN_ACTIVITY_PATH
        main_activity.parent.mkdir(parents=True, exist_ok=True)
        main_activity.write_text(
            "package com.ai.assistance.operit.ui.main\n"
            "import com.kiyori.app.startup.KiyoriMainOrientationCoordinator\n"
            "import com.kiyori.app.startup.KiyoriMainOrientationDialog\n"
            "class MainActivity {\n"
            "    private val orientationCoordinator = KiyoriMainOrientationCoordinator()\n"
            "    fun onCreate() {\n"
            "        orientationCoordinator.initialize(resources.configuration.orientation)\n"
            "    }\n"
            "    override fun onConfigurationChanged(newConfig: Configuration) {\n"
            "        super.onConfigurationChanged(newConfig)\n"
            "        pluginLoadingState.hide()\n"
            "        orientationCoordinator.onConfigurationChanged(newConfig.orientation)\n"
            "    }\n"
            "    fun content() {\n"
            "        if (orientationCoordinator.showChangeDialog) {\n"
            "            KiyoriMainOrientationDialog(\n"
            "                onConfirm = {\n"
            "                    orientationCoordinator.dismissChangeDialog()\n"
            "                    recreate()\n"
            "                },\n"
            "                onDismiss = {\n"
            "                    orientationCoordinator.dismissChangeDialog()\n"
            "                },\n"
            "            )\n"
            "        }\n"
            "    }\n"
            "}\n",
            encoding="utf-8",
        )
        coordinator_test = root / M04D_MAIN_ORIENTATION_COORDINATOR_TEST_PATH
        coordinator_test.parent.mkdir(parents=True, exist_ok=True)
        coordinator_test.write_text(
            "package com.kiyori.app.startup\n"
            "class KiyoriMainOrientationCoordinatorTest {\n"
            "    fun test() {\n"
            "        resolveKiyoriMainOrientationChange()\n"
            "        KiyoriMainOrientationCoordinator()\n"
            "    }\n"
            "}\n",
            encoding="utf-8",
        )
        return coordinator

    def write_m04d_main_notification_permission_coordinator_layout(
        self,
        root: Path,
    ) -> Path:
        orientation = root / M04D_MAIN_ORIENTATION_COORDINATOR_PATH
        orientation.parent.mkdir(parents=True, exist_ok=True)
        orientation.write_text(
            "package com.kiyori.app.startup\n"
            "class KiyoriMainOrientationCoordinator\n",
            encoding="utf-8",
        )

        coordinator = root / M04D_MAIN_NOTIFICATION_PERMISSION_COORDINATOR_PATH
        coordinator_imports = (
            M05D_OPERIT_RESOURCE_BRIDGE_IMPORT,
            "com.kiyori.platform.logging.KiyoriLogger",
            M05D_PLATFORM_ACTION_IMPORT,
            M05D_PLATFORM_CAPABILITY_IMPORT,
        )
        coordinator.write_text(
            "package com.kiyori.app.startup\n\n"
            + "\n".join(f"import {imported}" for imported in coordinator_imports)
            + "\n\n"
            + "class KiyoriMainNotificationPermissionCoordinator {\n"
            + "    private val notificationPermissionCapability = "
            + "KiyoriNotificationPermissionCapability(\n"
            + "        activity,\n"
            + "        onPermissionResult = { isGranted -> isGranted },\n"
            + "    )\n"
            + "    fun checkAndRequest() {\n"
            + "        notificationPermissionCapability.checkAndRequest { action ->\n"
            + "        KiyoriNotificationPermissionAction.NOT_REQUIRED\n"
            + "        KiyoriNotificationPermissionAction.ALREADY_GRANTED\n"
            + "        KiyoriNotificationPermissionAction."
            + "SHOW_RATIONALE_AND_REQUEST\n"
            + "        KiyoriNotificationPermissionAction.REQUEST\n"
            + "        Toast.makeText(\n"
            + "            activity,\n"
            + "            activity.getString("
            + "OperitNotificationPermissionResources."
            + "notificationPermissionDenied),\n"
            + "            duration,\n"
            + "        )\n"
            + "        Toast.makeText(\n"
            + "            activity,\n"
            + "            activity.getString("
            + "OperitNotificationPermissionResources."
            + "notificationPermissionRationale),\n"
            + "            duration,\n"
            + "        )\n"
            + "        KiyoriLogger.d(tag, message)\n"
            + "        KiyoriLogger.d(tag, message)\n"
            + "        KiyoriLogger.d(tag, message)\n"
            + "        KiyoriLogger.d(tag, message)\n"
            + "        KiyoriLogger.d(tag, message)\n"
            + "        KiyoriLogger.d(tag, message)\n"
            + "        }\n"
            + "    }\n"
            + "}\n",
            encoding="utf-8",
        )
        architecture_root = root / "config/architecture"
        architecture_root.mkdir(parents=True)
        normalized = coordinator.read_bytes().replace(b"\r\n", b"\n")
        (
            architecture_root
            / "m04d-main-notification-permission-coordinator-sha256.txt"
        ).write_text(
            hashlib.sha256(normalized).hexdigest() + "\n",
            encoding="utf-8",
        )
        (
            architecture_root
            / "m04d-main-notification-permission-coordinator-project-imports.txt"
        ).write_text(
            "\n".join(coordinator_imports) + "\n",
            encoding="utf-8",
        )

        main_activity = root / M04_MAIN_ACTIVITY_PATH
        main_activity.parent.mkdir(parents=True, exist_ok=True)
        main_activity.write_text(
            "package com.ai.assistance.operit.ui.main\n"
            "import com.kiyori.app.startup.KiyoriMainNotificationPermissionCoordinator\n"
            "class MainActivity {\n"
            "    private val notificationPermissionCoordinator = "
            "KiyoriMainNotificationPermissionCoordinator(this)\n"
            "    fun performInitialChecks() {\n"
            "        notificationPermissionCoordinator.checkAndRequest()\n"
            "    }\n"
            "}\n",
            encoding="utf-8",
        )
        coordinator_test = root / M05D_PERMISSION_TEST_PATH
        coordinator_test.parent.mkdir(parents=True, exist_ok=True)
        coordinator_test.write_text(
            "package com.kiyori.platform.permission\n"
            "class KiyoriNotificationPermissionCapabilityTest {\n"
            "    fun test() {\n"
            "        val action: KiyoriNotificationPermissionAction? = null\n"
            "        resolveKiyoriNotificationPermissionAction()\n"
            "    }\n"
            "}\n",
            encoding="utf-8",
        )
        return coordinator

    def write_m04d_main_startup_gate_coordinator_layout(
        self,
        root: Path,
    ) -> Path:
        notification = root / M04D_MAIN_NOTIFICATION_PERMISSION_COORDINATOR_PATH
        notification.parent.mkdir(parents=True, exist_ok=True)
        notification.write_text(
            "package com.kiyori.app.startup\n"
            "class KiyoriMainNotificationPermissionCoordinator\n",
            encoding="utf-8",
        )

        coordinator = root / M04D_MAIN_STARTUP_GATE_COORDINATOR_PATH
        coordinator_imports = (
            "com.ai.assistance.operit.core.tools.system.AndroidPermissionLevel",
            "com.ai.assistance.operit.data.preferences.AgreementPreferences",
            "com.ai.assistance.operit.data.preferences.androidPermissionPreferences",
            "com.ai.assistance.operit.ui.features.agreement.screens.AgreementScreen",
            "com.ai.assistance.operit.ui.features.permission.screens.PermissionGuideScreen",
            "com.kiyori.platform.logging.KiyoriLogger",
        )
        coordinator.write_text(
            "package com.kiyori.app.startup\n\n"
            + "\n".join(f"import {imported}" for imported in coordinator_imports)
            + "\n\n"
            + "enum class KiyoriMainStartupDestination { "
            + "AGREEMENT, PERMISSION_GUIDE, CONTENT }\n"
            + "fun resolveKiyoriMainStartupDestination() = "
            + "KiyoriMainStartupDestination.CONTENT\n"
            + "data class Dependencies(\n"
            + "    val isAgreementAccepted: () -> Boolean,\n"
            + "    val acceptCurrentAgreement: () -> Unit,\n"
            + "    val readPermissionLevel: () -> AndroidPermissionLevel?,\n"
            + ")\n"
            + "fun createDependencies(context: Context): Dependencies {\n"
            + "    val agreementPreferences = AgreementPreferences(context)\n"
            + "    return Dependencies(\n"
            + "        agreementPreferences::isAgreementAccepted,\n"
            + "        agreementPreferences::acceptCurrentAgreement,\n"
            + "        androidPermissionPreferences::getPreferredPermissionLevel,\n"
            + "    )\n"
            + "}\n"
            + "class KiyoriMainStartupGateCoordinator {\n"
            + "    private var showPermissionGuide by mutableStateOf(false)\n"
            + "    val destination = resolveKiyoriMainStartupDestination()\n"
            + "    val isReadyForContent = true\n"
            + "    fun refreshPermissionLevel() {\n"
            + "        val permissionLevel: AndroidPermissionLevel? = null\n"
            + "        showPermissionGuide = permissionLevel == null\n"
            + "        KiyoriLogger.d(\n"
            + "            \"MainActivity\",\n"
            + "            \"当前权限级别: $permissionLevel; "
            + "权限级别检查: 已设置=${!showPermissionGuide}\",\n"
            + "        )\n"
            + "    }\n"
            + "    fun acceptCurrentAgreement() = Unit\n"
            + "    fun completePermissionGuide() { showPermissionGuide = false }\n"
            + "}\n"
            + "@Composable\n"
            + "fun KiyoriMainStartupGate(\n"
            + "    destination: KiyoriMainStartupDestination,\n"
            + "    onAgreementAccepted: () -> Unit,\n"
            + "    onPermissionGuideComplete: () -> Unit,\n"
            + "    content: @Composable () -> Unit,\n"
            + ") {\n"
            + "    when (destination) {\n"
            + "        KiyoriMainStartupDestination.AGREEMENT -> "
            + "AgreementScreen(onAgreementAccepted)\n"
            + "        KiyoriMainStartupDestination.PERMISSION_GUIDE -> "
            + "PermissionGuideScreen(onComplete = onPermissionGuideComplete)\n"
            + "        KiyoriMainStartupDestination.CONTENT -> content()\n"
            + "    }\n"
            + "}\n",
            encoding="utf-8",
        )
        architecture_root = root / "config/architecture"
        architecture_root.mkdir(parents=True)
        normalized = coordinator.read_bytes().replace(b"\r\n", b"\n")
        (
            architecture_root / "m04d-main-startup-gate-coordinator-sha256.txt"
        ).write_text(
            hashlib.sha256(normalized).hexdigest() + "\n",
            encoding="utf-8",
        )
        (
            architecture_root
            / "m04d-main-startup-gate-coordinator-project-imports.txt"
        ).write_text(
            "\n".join(coordinator_imports) + "\n",
            encoding="utf-8",
        )

        main_activity = root / M04_MAIN_ACTIVITY_PATH
        main_activity.parent.mkdir(parents=True, exist_ok=True)
        main_activity.write_text(
            "package com.ai.assistance.operit.ui.main\n"
            "import com.kiyori.app.startup.KiyoriMainStartupGate\n"
            "import com.kiyori.app.startup.KiyoriMainStartupGateCoordinator\n"
            "class MainActivity {\n"
            "    private lateinit var startupGateCoordinator: "
            "KiyoriMainStartupGateCoordinator\n"
            "    fun initializeComponents() {\n"
            "        startupGateCoordinator = KiyoriMainStartupGateCoordinator(this)\n"
            "    }\n"
            "    fun performInitialChecks() {\n"
            "        notificationPermissionCoordinator.checkAndRequest()\n"
            "        startupGateCoordinator.refreshPermissionLevel()\n"
            "        prepareStartupChatIfNeeded()\n"
            "        if (startupGateCoordinator.isReadyForContent) {\n"
            "            startPluginLoading()\n"
            "        }\n"
            "    }\n"
            "    fun content() {\n"
            "        KiyoriMainStartupGate(\n"
            "            destination = startupGateCoordinator.destination,\n"
            "            onAgreementAccepted = {\n"
            "                startupGateCoordinator.acceptCurrentAgreement()\n"
            "                lifecycleScope.launch {\n"
            "                    delay(300)\n"
            "                    startupGateCoordinator.refreshPermissionLevel()\n"
            "                    if (startupGateCoordinator.isReadyForContent) {\n"
            "                        startPluginLoading()\n"
            "                    }\n"
            "                    setAppContent()\n"
            "                }\n"
            "            },\n"
            "            onPermissionGuideComplete = {\n"
            "                startupGateCoordinator.completePermissionGuide()\n"
            "                startPluginLoading()\n"
            "                setAppContent()\n"
            "            },\n"
            "        ) { Unit }\n"
            "    }\n"
            "}\n",
            encoding="utf-8",
        )
        coordinator_test = root / M04D_MAIN_STARTUP_GATE_COORDINATOR_TEST_PATH
        coordinator_test.parent.mkdir(parents=True, exist_ok=True)
        coordinator_test.write_text(
            "package com.kiyori.app.startup\n"
            "class KiyoriMainStartupGateCoordinatorTest {\n"
            "    fun test() {\n"
            "        val destination: KiyoriMainStartupDestination? = null\n"
            "        resolveKiyoriMainStartupDestination()\n"
            "        KiyoriMainStartupGateCoordinator()\n"
            "    }\n"
            "}\n",
            encoding="utf-8",
        )
        return coordinator

    def write_m04d_main_content_host_layout(
        self,
        root: Path,
    ) -> Path:
        self.write_m04d_main_startup_gate_coordinator_layout(root)

        host = root / M04D_MAIN_CONTENT_HOST_PATH
        host_imports = (
            "com.ai.assistance.operit.ui.common.NavItem",
            "com.ai.assistance.operit.ui.features.startup.screens.LocalPluginLoadingState",
            "com.ai.assistance.operit.ui.features.startup.screens.PluginLoadingState",
            "com.kiyori.app.KiyoriApp",
            "com.kiyori.app.shell.KiyoriShellExternalDestination",
        )
        host.write_text(
            "package com.kiyori.app.startup\n\n"
            + "\n".join(f"import {imported}" for imported in host_imports)
            + "\n\n"
            + "data class KiyoriMainContentRequestProjection(\n"
            + "    val initialNavItem: NavItem,\n"
            + "    val shortcutNavRequest: NavItem?,\n"
            + "    val shortcutNavRequestId: Long,\n"
            + "    val routeNavRequest: String?,\n"
            + "    val routeNavArgs: Map<String, Any?>,\n"
            + "    val routeNavRequestId: Long,\n"
            + "    val browserOpenRequest: String?,\n"
            + "    val browserOpenRequestId: Long,\n"
            + "    val kiyoriShellDestinationRequest: "
            + "KiyoriShellExternalDestination?,\n"
            + "    val kiyoriShellRequestId: Long,\n"
            + ")\n"
            + "class KiyoriMainPendingRequests {\n"
            + "    val shortcutNavItem: NavItem? = null\n"
            + "    val currentMainNavItem: NavItem = NavItem.AiChat\n"
            + "    val shortcutRequestId: Long = 0\n"
            + "    val routeId: String? = null\n"
            + "    val routeArgs: Map<String, Any?> = emptyMap()\n"
            + "    val routeRequestId: Long = 0\n"
            + "    val browserUrl: String? = null\n"
            + "    val browserRequestId: Long = 0\n"
            + "    val shellDestination: KiyoriShellExternalDestination? = null\n"
            + "    val shellRequestId: Long = 0\n"
            + "    fun consumeShortcut(handledRequestId: Long) = Unit\n"
            + "    fun updateCurrentMainNavItem(navItem: NavItem) = Unit\n"
            + "    fun consumeRoute(handledRequestId: Long) = Unit\n"
            + "    fun consumeBrowser(handledRequestId: Long) = Unit\n"
            + "    fun consumeShell(handledRequestId: Long) = Unit\n"
            + "}\n"
            + "class KiyoriMainSharedContentCoordinator {\n"
            + "    fun processPendingSharedFiles() = Unit\n"
            + "    fun processPendingSharedText() = Unit\n"
            + "}\n"
            + "fun projectKiyoriMainContentRequests(\n"
            + "    pendingRequests: KiyoriMainPendingRequests,\n"
            + ") = KiyoriMainContentRequestProjection(\n"
            + "    initialNavItem = pendingRequests.shortcutNavItem "
            + "?: pendingRequests.currentMainNavItem,\n"
            + "    shortcutNavRequest = pendingRequests.shortcutNavItem,\n"
            + "    shortcutNavRequestId = pendingRequests.shortcutRequestId,\n"
            + "    routeNavRequest = pendingRequests.routeId,\n"
            + "    routeNavArgs = pendingRequests.routeArgs,\n"
            + "    routeNavRequestId = pendingRequests.routeRequestId,\n"
            + "    browserOpenRequest = pendingRequests.browserUrl,\n"
            + "    browserOpenRequestId = pendingRequests.browserRequestId,\n"
            + "    kiyoriShellDestinationRequest = "
            + "pendingRequests.shellDestination,\n"
            + "    kiyoriShellRequestId = pendingRequests.shellRequestId,\n"
            + ")\n"
            + "@Composable\n"
            + "fun KiyoriMainContentHost(\n"
            + "    pendingRequests: KiyoriMainPendingRequests,\n"
            + "    sharedContentCoordinator: KiyoriMainSharedContentCoordinator,\n"
            + "    pluginLoadingState: PluginLoadingState,\n"
            + ") {\n"
            + "    sharedContentCoordinator.processPendingSharedFiles()\n"
            + "    sharedContentCoordinator.processPendingSharedText()\n"
            + "    val contentRequests = "
            + "projectKiyoriMainContentRequests(pendingRequests)\n"
            + "    CompositionLocalProvider(\n"
            + "        LocalPluginLoadingState provides pluginLoadingState,\n"
            + "    ) {\n"
            + "        KiyoriApp(\n"
            + "            initialNavItem = contentRequests.initialNavItem,\n"
            + "            shortcutNavRequest = contentRequests.shortcutNavRequest,\n"
            + "            shortcutNavRequestId = "
            + "contentRequests.shortcutNavRequestId,\n"
            + "            routeNavRequest = contentRequests.routeNavRequest,\n"
            + "            routeNavArgs = contentRequests.routeNavArgs,\n"
            + "            routeNavRequestId = contentRequests.routeNavRequestId,\n"
            + "            browserOpenRequest = contentRequests.browserOpenRequest,\n"
            + "            browserOpenRequestId = "
            + "contentRequests.browserOpenRequestId,\n"
            + "            kiyoriShellDestinationRequest = "
            + "contentRequests.kiyoriShellDestinationRequest,\n"
            + "            kiyoriShellRequestId = "
            + "contentRequests.kiyoriShellRequestId,\n"
            + "            onShortcutNavHandled = { handledRequestId ->\n"
            + "                pendingRequests.consumeShortcut(handledRequestId)\n"
            + "            },\n"
            + "            onCurrentNavItemChanged = { navItem ->\n"
            + "                pendingRequests.updateCurrentMainNavItem(navItem)\n"
            + "            },\n"
            + "            onRouteNavHandled = { handledRequestId ->\n"
            + "                pendingRequests.consumeRoute(handledRequestId)\n"
            + "            },\n"
            + "            onBrowserOpenHandled = { handledRequestId ->\n"
            + "                pendingRequests.consumeBrowser(handledRequestId)\n"
            + "            },\n"
            + "            onKiyoriShellRequestHandled = { handledRequestId ->\n"
            + "                pendingRequests.consumeShell(handledRequestId)\n"
            + "            },\n"
            + "        )\n"
            + "    }\n"
            + "}\n",
            encoding="utf-8",
        )

        architecture_root = root / "config/architecture"
        architecture_root.mkdir(parents=True, exist_ok=True)
        normalized = host.read_bytes().replace(b"\r\n", b"\n")
        (
            architecture_root / "m04d-main-content-host-sha256.txt"
        ).write_text(
            hashlib.sha256(normalized).hexdigest() + "\n",
            encoding="utf-8",
        )
        (
            architecture_root / "m04d-main-content-host-project-imports.txt"
        ).write_text(
            "\n".join(host_imports) + "\n",
            encoding="utf-8",
        )

        main_activity = root / M04_MAIN_ACTIVITY_PATH
        main_activity.write_text(
            "package com.ai.assistance.operit.ui.main\n"
            "import com.kiyori.app.startup.KiyoriMainContentHost\n"
            "class MainActivity {\n"
            "    fun onNewIntent() {\n"
            "        sharedContentCoordinator.processPendingSharedFiles()\n"
            "        sharedContentCoordinator.processPendingSharedText()\n"
            "    }\n"
            "    fun content() {\n"
            "        KiyoriMainContentHost(\n"
            "            pendingRequests = pendingRequests,\n"
            "            sharedContentCoordinator = sharedContentCoordinator,\n"
            "            pluginLoadingState = pluginLoadingState,\n"
            "        )\n"
            "    }\n"
            "}\n",
            encoding="utf-8",
        )

        host_test = root / M04D_MAIN_CONTENT_HOST_TEST_PATH
        host_test.parent.mkdir(parents=True, exist_ok=True)
        host_test.write_text(
            "package com.kiyori.app.startup\n"
            "class KiyoriMainContentHostTest {\n"
            "    fun test() {\n"
            "        val projection: KiyoriMainContentRequestProjection? = null\n"
            "        projectKiyoriMainContentRequests(KiyoriMainPendingRequests())\n"
            "    }\n"
            "}\n",
            encoding="utf-8",
        )
        return host

    def write_m04e_finalization_layout(
        self,
        root: Path,
        *,
        include_stale_exception: bool = False,
    ) -> Path:
        self.write_m04d_main_content_host_layout(root)
        main_activity = root / M04_MAIN_ACTIVITY_PATH
        main_activity.write_text(
            "package com.ai.assistance.operit.ui.main\n"
            "import com.kiyori.app.startup.KiyoriMainContentHost\n"
            "class MainActivity : ComponentActivity() {\n"
            "    fun content() {\n"
            "        KiyoriMainContentHost(\n"
            "            pendingRequests = pendingRequests,\n"
            "            sharedContentCoordinator = sharedContentCoordinator,\n"
            "            pluginLoadingState = pluginLoadingState,\n"
            "        )\n"
            "    }\n"
            "}\n",
            encoding="utf-8",
        )

        ownership_text = (
            "schema_version = 1\n"
            "[[ownership]]\n"
            'id = "operit-ui"\n'
            'path = "app/src/main/java/com/ai/assistance/operit/ui/**"\n'
            'owner = "mixed-current"\n'
            'sync_zone = "B"\n'
            'phase = "current"\n'
            'allowed_import_roots = [\n'
            '  "com.ai.assistance.operit",\n'
            '  "com.kiyori.capability",\n'
            '  "com.kiyori.platform",\n'
            "]\n"
            'forbidden_import_roots = [\n'
            '  "com.kiyori.app",\n'
            '  "com.kiyori.feature",\n'
            "]\n"
            "[[ownership]]\n"
            f'id = "{M04E_MAIN_ACTIVITY_OWNERSHIP_ID}"\n'
            f'path = "{M04_MAIN_ACTIVITY_PATH}"\n'
            'owner = "kiyori-app-entry-compatibility"\n'
            'sync_zone = "D"\n'
            'phase = "m04e"\n'
            'allowed_import_roots = [\n'
            '  "com.ai.assistance.operit",\n'
            '  "com.kiyori.app",\n'
            '  "com.kiyori.platform",\n'
            "]\n"
            'forbidden_import_roots = [\n'
            '  "com.kiyori.feature",\n'
            "]\n"
        )
        if include_stale_exception:
            ownership_text += (
                "[[exception]]\n"
                'rule = "ARCH001"\n'
                f'path = "{M04_MAIN_ACTIVITY_PATH}"\n'
                'reason = "Expired M-04 compatibility exception."\n'
                'expires_after = "M-04D-main-activity-host"\n'
                'owner = "operit-ui"\n'
            )
        ownership = root / "config/architecture/package-ownership.toml"
        ownership.write_text(ownership_text, encoding="utf-8")
        (
            root
            / "config/architecture"
            / M04E_MAIN_ACTIVITY_IMPORT_SNAPSHOT
        ).write_text(
            "com.kiyori.app.startup.KiyoriMainContentHost\n",
            encoding="utf-8",
        )

        manifest = root / "app/src/main/AndroidManifest.xml"
        manifest.parent.mkdir(parents=True, exist_ok=True)
        manifest.write_text(
            '<?xml version="1.0" encoding="utf-8"?>\n'
            '<manifest xmlns:android="http://schemas.android.com/apk/res/android">\n'
            "  <application>\n"
            '    <activity android:name=".ui.main.MainActivity">\n'
            "      <intent-filter>\n"
            '        <action android:name="android.intent.action.MAIN" />\n'
            '        <category android:name="android.intent.category.LAUNCHER" />\n'
            "      </intent-filter>\n"
            "    </activity>\n"
            "  </application>\n"
            "</manifest>\n",
            encoding="utf-8",
        )

        architecture_test = root / "ci/test/test_architecture_boundaries.py"
        architecture_test.parent.mkdir(parents=True, exist_ok=True)
        architecture_test.write_text(
            "def test_exact_ownership_overrides_broad_glob():\n"
            "    pass\n"
            "def test_overlapping_broad_ownership_is_rejected():\n"
            "    pass\n"
            "def test_m04e_finalization_accepts_compatibility_owner():\n"
            "    pass\n"
            "def test_m04e_finalization_rejects_stale_exception():\n"
            "    pass\n",
            encoding="utf-8",
        )
        return ownership

    def write_m05a1_design_theme_layout(self, root: Path) -> Path:
        design_sources = {
            M05A1_COLOR_SCHEMES_PATH: (
                "package com.kiyori.design.theme\n"
                "val KiyoriBrowserLightColorScheme = false\n"
                "val KiyoriBrowserDarkColorScheme = true\n"
                "val KiyoriLightColorScheme = false\n"
                "val KiyoriDarkColorScheme = true\n"
                "fun resolveKiyoriColorScheme(darkTheme: Boolean) =\n"
                "    if (darkTheme) KiyoriDarkColorScheme else KiyoriLightColorScheme\n"
            ),
            M05A1_BROWSER_THEME_PATH: (
                "package com.kiyori.design.theme\n"
                "fun KiyoriBrowserTheme() {\n"
                "    KiyoriBrowserLightColorScheme\n"
                "    KiyoriBrowserDarkColorScheme\n"
                "}\n"
            ),
            M05A1_SETTINGS_THEME_PATH: (
                "package com.kiyori.design.theme\n"
                "data class KiyoriSettingsColors(val isDark: Boolean)\n"
                "val LocalKiyoriSettingsColors = KiyoriSettingsColors(false)\n"
                "fun KiyoriSettingsTheme() {\n"
                "    resolveKiyoriSettingsColors(false)\n"
                "}\n"
                "fun resolveKiyoriSettingsColors(isDark: Boolean) =\n"
                "    KiyoriSettingsColors(isDark)\n"
            ),
        }
        hash_snapshot_names = {
            M05A1_COLOR_SCHEMES_PATH: "m05a1-color-schemes-sha256.txt",
        }
        architecture_root = root / "config/architecture"
        architecture_root.mkdir(parents=True, exist_ok=True)
        for relative_path, source in design_sources.items():
            source_path = root / relative_path
            source_path.parent.mkdir(parents=True, exist_ok=True)
            source_path.write_text(source, encoding="utf-8")
            if relative_path not in hash_snapshot_names:
                continue
            normalized = source_path.read_bytes().replace(b"\r\n", b"\n")
            digest = hashlib.sha256(normalized).hexdigest().upper()
            (architecture_root / hash_snapshot_names[relative_path]).write_text(
                f"{digest}\t{relative_path}\n",
                encoding="utf-8",
            )

        old_resolver = root / M05A1_OLD_COLOR_RESOLVER_PATH
        old_resolver.parent.mkdir(parents=True, exist_ok=True)
        old_resolver.write_text(
            "package com.ai.assistance.operit.ui.theme\n"
            "import com.kiyori.design.theme.resolveKiyoriColorScheme\n"
            "fun resolveThemeColorScheme(\n"
            "    context: Context,\n"
            "    snapshot: ThemePreferenceSnapshot,\n"
            "): ColorScheme =\n"
            "    resolveThemeColorScheme(darkTheme = resolveDarkTheme(context, snapshot))\n"
            "fun resolveThemeColorScheme(darkTheme: Boolean): ColorScheme =\n"
            "    resolveKiyoriColorScheme(darkTheme)\n"
            "private fun resolveDarkTheme(\n"
            "    context: Context,\n"
            "    snapshot: ThemePreferenceSnapshot,\n"
            "): Boolean {\n"
            "    if (!snapshot.useSystemTheme) {\n"
            "        return snapshot.themeMode == UserPreferencesManager.THEME_MODE_DARK\n"
            "    }\n"
            "    return (context.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==\n"
            "        Configuration.UI_MODE_NIGHT_YES\n"
            "}\n",
            encoding="utf-8",
        )

        imports_by_consumer: dict[str, set[str]] = {}
        expected_import_consumers = {
            "KiyoriBrowserTheme": {
                "app/src/main/java/com/ai/assistance/operit/ui/features/toolbox/screens/filemanager/components/FileContextMenu.kt",
                "app/src/main/java/com/ai/assistance/operit/ui/features/websession/browser/chrome/KiyoriToolboxDrawer.kt",
                "app/src/main/java/com/ai/assistance/operit/ui/features/toolbox/screens/filemanager/components/FileManagerChrome.kt",
                "app/src/main/java/com/ai/assistance/operit/ui/features/toolbox/screens/filemanager/components/KiyoriFileManagerMinimizedIndicator.kt",
                "app/src/main/java/com/kiyori/app/shell/KiyoriAppShell.kt",
                "app/src/main/java/com/ai/assistance/operit/ui/features/player/"
                "PlayerActivity.kt",
                "app/src/main/java/com/ai/assistance/operit/ui/features/websession/"
                "browser/WebSessionBrowserScreen.kt",
            },
            "KiyoriSettingsTheme": {
                "app/src/main/java/com/ai/assistance/operit/ui/features/toolbox/screens/filemanager/FileManagerScreen.kt",
                "app/src/main/java/com/ai/assistance/operit/ui/features/chat/details/"
                "ConversationDetailsScreen.kt",
                "app/src/main/java/com/kiyori/app/shell/KiyoriAppShell.kt",
                "app/src/main/java/com/ai/assistance/operit/ui/main/components/"
                "AppContent.kt",
                "app/src/main/java/com/ai/assistance/operit/ui/main/shell/"
                "KiyoriCollapsingSettingsPage.kt",
                "app/src/main/java/com/ai/assistance/operit/ui/main/shell/"
                "KiyoriSettingsHomePage.kt",
                "app/src/main/java/com/ai/assistance/operit/ui/main/shell/"
                "KiyoriSettingsUi.kt",
                "app/src/main/java/com/ai/assistance/operit/ui/main/shell/"
                "KiyoriSettingsWorkspacePage.kt",
            },
            "LocalKiyoriSettingsColors": {
                "app/src/main/java/com/ai/assistance/operit/ui/features/chat/details/"
                "ConversationDetailsScreen.kt",
                "app/src/main/java/com/ai/assistance/operit/ui/features/assistant/"
                "components/AvatarPreviewSection.kt",
                "app/src/main/java/com/ai/assistance/operit/ui/features/assistant/"
                "components/VoiceAutoAttachComponents.kt",
                "app/src/main/java/com/ai/assistance/operit/ui/features/settings/"
                "screens/ContextSummarySettingsScreen.kt",
                "app/src/main/java/com/ai/assistance/operit/ui/features/settings/"
                "screens/CustomEmojiManagementScreen.kt",
                "app/src/main/java/com/ai/assistance/operit/ui/features/settings/"
                "screens/ExternalHttpChatSettingsScreen.kt",
                "app/src/main/java/com/ai/assistance/operit/ui/features/settings/"
                "screens/FunctionalConfigScreen.kt",
                "app/src/main/java/com/ai/assistance/operit/ui/features/settings/"
                "screens/MnnModelDownloadScreen.kt",
                "app/src/main/java/com/ai/assistance/operit/ui/features/settings/"
                "screens/ModelConfigScreen.kt",
                "app/src/main/java/com/ai/assistance/operit/ui/features/settings/"
                "screens/TagMarketScreen.kt",
                "app/src/main/java/com/ai/assistance/operit/ui/features/settings/"
                "screens/TokenUsageStatisticsComponents.kt",
                "app/src/main/java/com/ai/assistance/operit/ui/features/settings/"
                "screens/TokenUsageStatisticsScreen.kt",
                "app/src/main/java/com/ai/assistance/operit/ui/features/settings/"
                "screens/ToolPermissionSettingsScreen.kt",
                "app/src/main/java/com/ai/assistance/operit/ui/features/settings/"
                "screens/UserPreferencesSettingsScreen.kt",
                "app/src/main/java/com/ai/assistance/operit/ui/main/shell/"
                "KiyoriAdBlockSettingsPage.kt",
                "app/src/main/java/com/ai/assistance/operit/ui/main/shell/"
                "KiyoriNetworkProxySettingsPage.kt",
                "app/src/main/java/com/ai/assistance/operit/ui/main/shell/"
                "KiyoriBrowserPasswordManagerPage.kt",
                "app/src/main/java/com/ai/assistance/operit/ui/main/shell/"
                "KiyoriBrowserTextSizePage.kt",
                "app/src/main/java/com/ai/assistance/operit/ui/main/shell/"
                "KiyoriCollapsingSettingsPage.kt",
                "app/src/main/java/com/ai/assistance/operit/ui/main/shell/"
                "KiyoriSettingsHomePage.kt",
                "app/src/main/java/com/ai/assistance/operit/ui/main/shell/"
                "KiyoriSettingsUi.kt",
                "app/src/main/java/com/ai/assistance/operit/ui/main/shell/"
                "KiyoriSettingsWorkspacePage.kt",
                "app/src/main/java/com/ai/assistance/operit/ui/features/settings/"
                "screens/theme/ThemeSettingsContentEditor.kt",
                "app/src/main/java/com/ai/assistance/operit/ui/features/settings/"
                "screens/theme/ThemeSettingsTabs.kt",
                "app/src/main/java/com/kiyori/integration/operit/onboarding/"
                "KiyoriPermissionsSettingsPage.kt",
            },
            "KiyoriLightColorScheme": {
                "app/src/main/java/com/ai/assistance/operit/ui/floating/"
                "FloatingWindowTheme.kt",
            },
        }
        for symbol, consumers in expected_import_consumers.items():
            for relative_path in consumers:
                imports_by_consumer.setdefault(relative_path, set()).add(symbol)
        for relative_path, symbols in imports_by_consumer.items():
            source_path = root / relative_path
            source_path.parent.mkdir(parents=True, exist_ok=True)
            package_name = ".".join(
                Path(relative_path)
                .relative_to("app/src/main/java")
                .parent
                .parts
            )
            source_path.write_text(
                f"package {package_name}\n"
                + "".join(
                    f"import com.kiyori.design.theme.{symbol}\n"
                    for symbol in sorted(symbols)
                ),
                encoding="utf-8",
            )

        ownership = architecture_root / "package-ownership.toml"
        ownership.write_text(
            "schema_version = 1\n"
            "[[ownership]]\n"
            'id = "operit-ui"\n'
            'path = "app/src/main/java/com/ai/assistance/operit/ui/**"\n'
            'owner = "mixed-current"\n'
            'sync_zone = "B"\n'
            'phase = "current"\n'
            'allowed_import_roots = [\n'
            '  "com.ai.assistance.operit",\n'
            '  "com.kiyori.capability",\n'
            '  "com.kiyori.design",\n'
            '  "com.kiyori.platform",\n'
            "]\n"
            'forbidden_import_roots = [\n'
            '  "com.kiyori.app",\n'
            '  "com.kiyori.feature",\n'
            "]\n"
            "[[ownership]]\n"
            'id = "operit-core"\n'
            'path = "app/src/main/java/com/ai/assistance/operit/core/**"\n'
            'owner = "mixed-current"\n'
            'sync_zone = "B"\n'
            'phase = "current"\n'
            'allowed_import_roots = [\n'
            '  "com.ai.assistance.operit",\n'
            '  "com.kiyori.capability",\n'
            '  "com.kiyori.platform",\n'
            "]\n"
            "[[ownership]]\n"
            'id = "kiyori-design"\n'
            'path = "app/src/main/java/com/kiyori/design/**"\n'
            'owner = "kiyori-design"\n'
            'sync_zone = "C"\n'
            'phase = "m05a1"\n'
            'allowed_import_roots = [\n'
            '  "com.kiyori.capability",\n'
            '  "com.kiyori.design",\n'
            '  "com.kiyori.platform",\n'
            "]\n",
            encoding="utf-8",
        )

        design_test = root / M05A1_DESIGN_TEST_PATH
        design_test.parent.mkdir(parents=True, exist_ok=True)
        design_test.write_text(
            "package com.kiyori.design.theme\n"
            "class KiyoriDesignThemeTest {\n"
            "    fun `light palette uses the accessible Kiyori accent contract`() {}\n"
            "    fun `dark palette keeps blue actions on layered neutral surfaces`() {}\n"
            "    fun `browser palettes retain the neutral chrome contract`() {}\n"
            "    fun `theme resolver exposes only the fixed Kiyori light and dark palettes`() {\n"
            "        resolveKiyoriColorScheme(false)\n"
            "    }\n"
            "    fun `settings palette keeps a neutral hierarchy in both application themes`() {\n"
            "        resolveKiyoriSettingsColors(false)\n"
            "    }\n"
            "    fun `default text pairs exceed WCAG normal text contrast`() {\n"
            "        contrastRatio()\n"
            "    }\n"
            "    fun `light primary remains a visible non text accent on white`() {}\n"
            "    fun contrastRatio() = 4.5\n"
            "}\n",
            encoding="utf-8",
        )

        old_theme_test = root / M05A1_OLD_THEME_TEST_PATH
        old_theme_test.parent.mkdir(parents=True, exist_ok=True)
        old_theme_test.write_text(
            "package com.ai.assistance.operit.ui.theme\n"
            "class KiyoriThemeTest {\n"
            "    fun `application semantic icon tones stay colorful and theme aware`() {}\n"
            "    fun `bottom navigation yellow is exact while weather sun remains independent`() {}\n"
            "    fun `stable entry ids keep deterministic semantic tones`() {}\n"
            "    fun `all typography roles use neutral tracking`() {}\n"
            "}\n",
            encoding="utf-8",
        )

        architecture_test = root / "ci/test/test_architecture_boundaries.py"
        architecture_test.parent.mkdir(parents=True, exist_ok=True)
        architecture_test.write_text(
            "def test_m05a1_design_theme_accepts_pure_owner():\n"
            "    pass\n"
            "def test_m05a1_design_theme_rejects_owner_or_dependency_drift():\n"
            "    pass\n",
            encoding="utf-8",
        )
        return ownership

    def write_m05a2_semantic_design_layout(self, root: Path) -> Path:
        semantic_colors = root / M05A2_SEMANTIC_COLORS_PATH
        semantic_colors.parent.mkdir(parents=True, exist_ok=True)
        semantic_colors.write_text(
            "package com.kiyori.design.theme\n\n"
            "import androidx.compose.runtime.Immutable\n"
            "import androidx.compose.ui.graphics.Color\n\n"
            "enum class KiyoriSemanticTone {\n"
            "    BLUE,\n"
            "    GREEN,\n"
            "    PURPLE,\n"
            "    ORANGE,\n"
            "    RED,\n"
            "    CYAN,\n"
            "    PINK,\n"
            "}\n\n"
            "internal fun kiyoriSemanticToneForStableId("
            "stableId: String): KiyoriSemanticTone =\n"
            "    KiyoriSemanticTone.entries[\n"
            "        Math.floorMod("
            "stableId.hashCode(), KiyoriSemanticTone.entries.size)\n"
            "    ]\n\n"
            "@Immutable\n"
            "data class KiyoriSemanticColors(\n"
            "    val icon: Color,\n"
            "    val container: Color,\n"
            ")\n\n"
            "internal fun resolveKiyoriSemanticColors(\n"
            "    tone: KiyoriSemanticTone,\n"
            "    isDark: Boolean,\n"
            "): KiyoriSemanticColors =\n"
            "    if (isDark) {\n"
            "        when (tone) {\n"
            "            KiyoriSemanticTone.BLUE ->\n"
            "                KiyoriSemanticColors("
            "Color(0xFF90CAF9), Color(0xFF163044))\n"
            "            KiyoriSemanticTone.GREEN ->\n"
            "                KiyoriSemanticColors("
            "Color(0xFF67D7A5), Color(0xFF17382D))\n"
            "            KiyoriSemanticTone.PURPLE ->\n"
            "                KiyoriSemanticColors("
            "Color(0xFFB7A7FF), Color(0xFF2D254A))\n"
            "            KiyoriSemanticTone.ORANGE ->\n"
            "                KiyoriSemanticColors("
            "Color(0xFFFFB76A), Color(0xFF432B16))\n"
            "            KiyoriSemanticTone.RED ->\n"
            "                KiyoriSemanticColors("
            "Color(0xFFFF8A8A), Color(0xFF472323))\n"
            "            KiyoriSemanticTone.CYAN ->\n"
            "                KiyoriSemanticColors("
            "Color(0xFF65D3E8), Color(0xFF173942))\n"
            "            KiyoriSemanticTone.PINK ->\n"
            "                KiyoriSemanticColors("
            "Color(0xFFF49AC0), Color(0xFF452336))\n"
            "        }\n"
            "    } else {\n"
            "        when (tone) {\n"
            "            KiyoriSemanticTone.BLUE ->\n"
            "                KiyoriSemanticColors("
            "Color(0xFF1E88E5), Color(0xFFE8F3FE))\n"
            "            KiyoriSemanticTone.GREEN ->\n"
            "                KiyoriSemanticColors("
            "Color(0xFF1B8D5F), Color(0xFFE6F5EE))\n"
            "            KiyoriSemanticTone.PURPLE ->\n"
            "                KiyoriSemanticColors("
            "Color(0xFF7056D9), Color(0xFFF0ECFF))\n"
            "            KiyoriSemanticTone.ORANGE ->\n"
            "                KiyoriSemanticColors("
            "Color(0xFFC66A13), Color(0xFFFFF0DF))\n"
            "            KiyoriSemanticTone.RED ->\n"
            "                KiyoriSemanticColors("
            "Color(0xFFD64545), Color(0xFFFDEAEA))\n"
            "            KiyoriSemanticTone.CYAN ->\n"
            "                KiyoriSemanticColors("
            "Color(0xFF168CA7), Color(0xFFE4F5F8))\n"
            "            KiyoriSemanticTone.PINK ->\n"
            "                KiyoriSemanticColors("
            "Color(0xFFC8467D), Color(0xFFFCEAF2))\n"
            "        }\n"
            "    }\n\n"
            "internal val KiyoriBottomNavigationSelectedFillColor = "
            "Color(0xFFFFC153)\n\n"
            "internal fun resolveKiyoriWeatherSunColor("
            "isDark: Boolean): Color =\n"
            "    if (isDark) {\n"
            "        Color(0xFFFFD166)\n"
            "    } else {\n"
            "        Color(0xFFC57C00)\n"
            "    }\n",
            encoding="utf-8",
        )

        semantic_theme = root / M05A2_SEMANTIC_THEME_PATH
        semantic_theme.write_text(
            "package com.kiyori.design.theme\n\n"
            "import androidx.compose.material3.MaterialTheme\n"
            "import androidx.compose.runtime.Composable\n"
            "import androidx.compose.ui.graphics.Color\n"
            "import androidx.compose.ui.graphics.luminance\n\n"
            "@Composable\n"
            "fun KiyoriSemanticTone.resolveColors(): KiyoriSemanticColors {\n"
            "    val isDark = "
            "MaterialTheme.colorScheme.background.luminance() < 0.5f\n"
            "    return resolveKiyoriSemanticColors(this, isDark)\n"
            "}\n\n"
            "@Composable\n"
            "internal fun kiyoriWeatherSunColor(): Color {\n"
            "    val isDark = "
            "MaterialTheme.colorScheme.background.luminance() < 0.5f\n"
            "    return resolveKiyoriWeatherSunColor(isDark)\n"
            "}\n",
            encoding="utf-8",
        )

        architecture_root = root / "config/architecture"
        architecture_root.mkdir(parents=True, exist_ok=True)
        for source_path, snapshot_name, relative_path in (
            (
                semantic_colors,
                M05A2_SEMANTIC_COLORS_HASH_SNAPSHOT,
                M05A2_SEMANTIC_COLORS_PATH,
            ),
            (
                semantic_theme,
                M05A2_SEMANTIC_THEME_HASH_SNAPSHOT,
                M05A2_SEMANTIC_THEME_PATH,
            ),
        ):
            normalized = source_path.read_bytes().replace(b"\r\n", b"\n")
            digest = hashlib.sha256(normalized).hexdigest().upper()
            (architecture_root / snapshot_name).write_text(
                f"{digest}\t{relative_path}\n",
                encoding="utf-8",
            )

        player_path = next(
            iter(M05A2_EXPECTED_QUALIFIED_REFERENCES["KiyoriSemanticColors"])
        )
        # Five file-manager pages each consume only the existing semantic tone.
        file_manager_paths = [
            "app/src/main/java/com/ai/assistance/operit/ui/features/toolbox/screens/filemanager/components/" + name
            for name in ("FileContextMenu.kt", "FileManagerChrome.kt", "FileManagerCopyUi.kt", "FileManagerDualPane.kt", "SearchDialogs.kt")
        ]
        legacy_production_consumer_count = M05A2_PRODUCTION_CONSUMER_COUNT - 4 - len(file_manager_paths)
        assistant_experience_path = (
            "app/src/main/java/com/ai/assistance/operit/ui/features/"
            "semantic/AssistantExperienceSettingsPages.kt"
        )
        permission_presentation_path = (
            "app/src/main/java/com/kiyori/integration/operit/onboarding/"
            "KiyoriPermissionPresentation.kt"
        )
        network_proxy_path = (
            "app/src/main/java/com/ai/assistance/operit/ui/main/shell/"
            "KiyoriNetworkProxySettingsPage.kt"
        )
        more_features_path = (
            "app/src/main/java/com/ai/assistance/operit/ui/main/shell/"
            "KiyoriMoreFeaturesSettingsPage.kt"
        )
        production_paths = [player_path] + [
            "app/src/main/java/com/ai/assistance/operit/ui/features/"
            f"semantic/Consumer{index:02d}.kt"
            for index in range(legacy_production_consumer_count - 1)
        ] + [
            assistant_experience_path,
            permission_presentation_path,
            network_proxy_path,
            more_features_path,
        ] + file_manager_paths
        test_paths = [
            "app/src/test/java/com/ai/assistance/operit/ui/semantic/"
            f"SemanticConsumer{index}.kt"
            for index in range(M05A2_EXTERNAL_TEST_CONSUMER_COUNT)
        ]
        imports_by_path: dict[str, list[str]] = {}
        for index, relative_path in enumerate(production_paths):
            symbols: list[str] = []
            page_source_consumer_index = legacy_production_consumer_count - 1
            stable_id_consumer_indices = (
                legacy_production_consumer_count - 4,
                legacy_production_consumer_count - 3,
            )
            navigation_consumer_index = legacy_production_consumer_count - 2
            # The final synthetic consumer models WebSessionPageSourceEditor, which uses both
            # the stable semantic tone and its Compose color resolver.
            # The current tree adds WebSessionHistoryDialogs as a two-import consumer and
            # KiyoriAdBlockSettingsPage, the assistant-experience Settings page, the
            # shared onboarding/Settings permission presentation, network proxy, and
            # More Features as tone-only consumers.
            if relative_path in {
                *file_manager_paths,
                assistant_experience_path,
                permission_presentation_path,
                network_proxy_path,
                more_features_path,
            }:
                imports_by_path[relative_path] = ["KiyoriSemanticTone"]
                continue
            if index < stable_id_consumer_indices[0] or index == page_source_consumer_index:
                symbols.append("KiyoriSemanticTone")
            if index < stable_id_consumer_indices[0] - 6 or index == page_source_consumer_index:
                symbols.append("resolveColors")
            if index in stable_id_consumer_indices:
                symbols.append("kiyoriSemanticToneForStableId")
            if index == navigation_consumer_index:
                symbols.append("KiyoriBottomNavigationSelectedFillColor")
            if index == navigation_consumer_index:
                symbols.append("kiyoriWeatherSunColor")
            imports_by_path[relative_path] = symbols
        for relative_path in test_paths:
            imports_by_path[relative_path] = ["KiyoriSemanticTone"]

        snapshot_entries: list[str] = []
        for relative_path, symbols in imports_by_path.items():
            source_path = root / relative_path
            source_path.parent.mkdir(parents=True, exist_ok=True)
            source_root = (
                "app/src/main/java"
                if relative_path.startswith("app/src/main/")
                else "app/src/test/java"
            )
            package_name = ".".join(
                Path(relative_path)
                .relative_to(source_root)
                .parent
                .parts
            )
            imports = [
                f"{M05A2_DESIGN_PACKAGE}.{symbol}"
                for symbol in symbols
            ]
            body = ""
            if relative_path == player_path:
                body = (
                    "val direct = "
                    "com.kiyori.design.theme.KiyoriSemanticColors::class\n"
                )
            source_path.write_text(
                f"package {package_name}\n"
                + "".join(f"import {imported}\n" for imported in imports)
                + body,
                encoding="utf-8",
            )
            snapshot_entries.extend(
                f"{relative_path}\t{imported}"
                for imported in imports
            )
        self.assertEqual(
            len(snapshot_entries),
            M05A2_CONSUMER_IMPORT_COUNT,
        )
        (architecture_root / M05A2_CONSUMER_IMPORT_SNAPSHOT).write_text(
            "\n".join(sorted(snapshot_entries)) + "\n",
            encoding="utf-8",
        )

        ownership = architecture_root / "package-ownership.toml"
        ownership.write_text(
            "schema_version = 1\n"
            "[[ownership]]\n"
            'id = "operit-ui"\n'
            'path = "app/src/main/java/com/ai/assistance/operit/ui/**"\n'
            'owner = "mixed-current"\n'
            'sync_zone = "B"\n'
            'phase = "current"\n'
            'allowed_import_roots = [\n'
            '  "com.ai.assistance.operit",\n'
            '  "com.kiyori.capability",\n'
            '  "com.kiyori.design",\n'
            '  "com.kiyori.platform",\n'
            "]\n"
            "[[ownership]]\n"
            'id = "operit-core"\n'
            'path = "app/src/main/java/com/ai/assistance/operit/core/**"\n'
            'owner = "mixed-current"\n'
            'sync_zone = "B"\n'
            'phase = "current"\n'
            'allowed_import_roots = [\n'
            '  "com.ai.assistance.operit",\n'
            '  "com.kiyori.capability",\n'
            '  "com.kiyori.platform",\n'
            "]\n"
            "[[ownership]]\n"
            'id = "kiyori-design"\n'
            'path = "app/src/main/java/com/kiyori/design/**"\n'
            'owner = "kiyori-design"\n'
            'sync_zone = "C"\n'
            'phase = "m05a1"\n'
            'allowed_import_roots = [\n'
            '  "com.kiyori.capability",\n'
            '  "com.kiyori.design",\n'
            '  "com.kiyori.platform",\n'
            "]\n",
            encoding="utf-8",
        )

        design_test = root / M05A1_DESIGN_TEST_PATH
        design_test.parent.mkdir(parents=True, exist_ok=True)
        design_test.write_text(
            "package com.kiyori.design.theme\n"
            "class KiyoriDesignThemeTest {\n"
            "    fun `application semantic icon tones stay colorful and theme aware`() {\n"
            "        KiyoriSemanticTone.entries\n"
            "        resolveKiyoriSemanticColors("
            "KiyoriSemanticTone.BLUE, false)\n"
            "        contrastRatio()\n"
            "    }\n"
            "    fun `bottom navigation yellow is exact while weather sun remains independent`() {\n"
            "        KiyoriBottomNavigationSelectedFillColor\n"
            "        resolveKiyoriWeatherSunColor(false)\n"
            "    }\n"
            "    fun `stable entry ids keep deterministic semantic tones`() {\n"
            "        kiyoriSemanticToneForStableId(\"id\")\n"
            "    }\n"
            "    fun contrastRatio() = 3.0\n"
            "}\n",
            encoding="utf-8",
        )

        old_theme_test = root / M05A1_OLD_THEME_TEST_PATH
        old_theme_test.parent.mkdir(parents=True, exist_ok=True)
        old_theme_test.write_text(
            "package com.ai.assistance.operit.ui.theme\n"
            "class KiyoriThemeTest {\n"
            "    fun `all typography roles use neutral tracking`() {}\n"
            "}\n",
            encoding="utf-8",
        )

        for snapshot_name, replacements in (
            M05A2_M04B_SEMANTIC_SNAPSHOT_REPLACEMENTS.items()
        ):
            (architecture_root / snapshot_name).write_text(
                "\n".join(sorted(replacements.values())) + "\n",
                encoding="utf-8",
            )

        architecture_test = root / M05A2_ARCHITECTURE_TEST_PATH
        architecture_test.parent.mkdir(parents=True, exist_ok=True)
        architecture_test.write_text(
            "def test_m05a2_semantic_design_accepts_split_owner():\n"
            "    pass\n"
            "def test_m05a2_semantic_design_rejects_contract_or_consumer_drift():\n"
            "    pass\n",
            encoding="utf-8",
        )
        return ownership

    def write_m05a3_root_theme_layout(self, root: Path) -> Path:
        def write(relative_path: str, text: str) -> Path:
            path = root / relative_path
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(text, encoding="utf-8")
            return path

        design_theme = write(
            M05A3_DESIGN_THEME_PATH,
            "package com.kiyori.design.theme\n\n"
            "import androidx.compose.foundation.background\n"
            "import androidx.compose.foundation.layout.Box\n"
            "import androidx.compose.foundation.layout.fillMaxSize\n"
            "import androidx.compose.material3.ColorScheme\n"
            "import androidx.compose.material3.MaterialTheme\n"
            "import androidx.compose.material3.Typography\n"
            "import androidx.compose.runtime.Composable\n"
            "import androidx.compose.ui.Modifier\n\n"
            "@Composable\n"
            "fun KiyoriTheme(\n"
            "    colorScheme: ColorScheme,\n"
            "    modifier: Modifier = Modifier,\n"
            "    typography: Typography = KiyoriTypography,\n"
            "    content: @Composable () -> Unit,\n"
            ") {\n"
            "    MaterialTheme(\n"
            "        colorScheme = colorScheme,\n"
            "        typography = typography,\n"
            "    ) {\n"
            "        Box(\n"
            "            modifier = Modifier\n"
            "                .fillMaxSize()\n"
            "                .background(colorScheme.background)\n"
            "                .then(modifier),\n"
            "        ) {\n"
            "            content()\n"
            "        }\n"
            "    }\n"
            "}\n",
        )

        style_names = (
            "displayLarge",
            "displayMedium",
            "displaySmall",
            "headlineLarge",
            "headlineMedium",
            "headlineSmall",
            "titleLarge",
            "titleMedium",
            "titleSmall",
            "bodyLarge",
            "bodyMedium",
            "bodySmall",
            "labelLarge",
            "labelMedium",
            "labelSmall",
        )
        typography_assignments = "".join(
            f"        {name} = Material3Typography.{name}.copy("
            "letterSpacing = 0.sp),\n"
            for name in style_names
        )
        typography_copies = "".join(
            f"        {name} = baseTypography.{name}.copy("
            "fontFamily = fontFamily),\n"
            for name in style_names
        )
        typography = write(
            M05A3_TYPOGRAPHY_PATH,
            "package com.kiyori.design.theme\n\n"
            "import androidx.compose.material3.Typography\n"
            "import androidx.compose.ui.text.font.FontFamily\n"
            "import androidx.compose.ui.unit.sp\n\n"
            "private val Material3Typography = Typography()\n\n"
            "val KiyoriTypography =\n"
            "    Typography(\n"
            f"{typography_assignments}"
            "    )\n\n"
            "fun applyFontFamilyToTypography(\n"
            "    baseTypography: Typography,\n"
            "    fontFamily: FontFamily?,\n"
            "): Typography {\n"
            "    if (fontFamily == null) {\n"
            "        return baseTypography\n"
            "    }\n"
            "    return Typography(\n"
            f"{typography_copies}"
            "    )\n"
            "}\n",
        )

        app_theme = write(
            M05A3_APP_THEME_PATH,
            "package com.kiyori.app.theme\n\n"
            "import androidx.compose.foundation.isSystemInDarkTheme\n"
            "import androidx.compose.runtime.Composable\n"
            "import androidx.compose.runtime.CompositionLocalProvider\n"
            "import androidx.compose.runtime.collectAsState\n"
            "import androidx.compose.runtime.getValue\n"
            "import androidx.compose.runtime.remember\n"
            "import androidx.compose.ui.Modifier\n"
            "import androidx.compose.ui.platform.LocalContext\n"
            "import com.ai.assistance.operit.data.preferences.UserPreferencesManager\n"
            "import com.ai.assistance.operit.ui.theme.LocalLiquidGlassBackdrop\n"
            "import com.ai.assistance.operit.ui.theme.LocalWaterGlassState\n"
            "import com.ai.assistance.operit.ui.theme.createCustomTypography\n"
            "import com.ai.assistance.operit.ui.theme.isWaterGlassSupported\n"
            "import com.kiyori.design.theme.resolveKiyoriColorScheme\n"
            "import com.kiyori.platform.window.KiyoriApplicationSystemBars\n"
            "import com.kiyori.platform.window.KiyoriStatusBarAppearanceScope\n"
            "import com.kyant.backdrop.backdrops.layerBackdrop\n"
            "import com.kyant.backdrop.backdrops.rememberLayerBackdrop\n"
            "import io.github.fletchmckee.liquid.liquefiable\n"
            "import io.github.fletchmckee.liquid.rememberLiquidState\n\n"
            "@Composable\n"
            "fun KiyoriTheme(content: @Composable () -> Unit) {\n"
            "    val context = LocalContext.current\n"
            "    val preferencesManager = remember(context) {\n"
            "        UserPreferencesManager.getInstance(context)\n"
            "    }\n"
            "    val useSystemTheme by "
            "preferencesManager.useSystemTheme.collectAsState(initial = false)\n"
            "    val themeMode by preferencesManager.themeMode.collectAsState(\n"
            "        initial = UserPreferencesManager.THEME_MODE_LIGHT,\n"
            "    )\n"
            "    val statusBarHidden by "
            "preferencesManager.statusBarHidden.collectAsState(initial = false)\n"
            "    val useCustomFont by "
            "preferencesManager.useCustomFont.collectAsState(initial = false)\n"
            "    val fontType by preferencesManager.fontType.collectAsState(\n"
            "        initial = UserPreferencesManager.FONT_TYPE_SYSTEM,\n"
            "    )\n"
            "    val systemFontName by "
            "preferencesManager.systemFontName.collectAsState(\n"
            "        initial = UserPreferencesManager.SYSTEM_FONT_DEFAULT,\n"
            "    )\n"
            "    val customFontPath by "
            "preferencesManager.customFontPath.collectAsState(initial = null)\n"
            "    val fontScale by "
            "preferencesManager.fontScale.collectAsState(initial = 1.0f)\n"
            "    val customTypography =\n"
            "        remember(useCustomFont, fontType, systemFontName, "
            "customFontPath, fontScale) {\n"
            "            createCustomTypography(\n"
            "                context = context,\n"
            "                useCustomFont = useCustomFont,\n"
            "                fontType = fontType,\n"
            "                systemFontName = systemFontName,\n"
            "                customFontPath = customFontPath,\n"
            "                fontScale = fontScale,\n"
            "            )\n"
            "        }\n"
            "    val systemDarkTheme = isSystemInDarkTheme()\n"
            "    val darkTheme =\n"
            "        if (useSystemTheme) {\n"
            "            systemDarkTheme\n"
            "        } else {\n"
            "            themeMode == UserPreferencesManager.THEME_MODE_DARK\n"
            "        }\n"
            "    val colorScheme = resolveKiyoriColorScheme(darkTheme)\n"
            "    KiyoriApplicationSystemBars(\n"
            "        darkTheme = darkTheme,\n"
            "        navigationBarColor = colorScheme.background,\n"
            "        statusBarHidden = statusBarHidden,\n"
            "    )\n"
            "    val liquidGlassBackdrop = rememberLayerBackdrop()\n"
            "    val waterGlassState = "
            "if (isWaterGlassSupported()) rememberLiquidState() else null\n"
            "    CompositionLocalProvider(\n"
            "        LocalLiquidGlassBackdrop provides liquidGlassBackdrop,\n"
            "        LocalWaterGlassState provides waterGlassState,\n"
            "    ) {\n"
            "        com.kiyori.design.theme.KiyoriTheme(\n"
            "            colorScheme = colorScheme,\n"
            "            modifier = Modifier\n"
            "                .layerBackdrop(liquidGlassBackdrop)\n"
            "                .then(\n"
            "                    if (waterGlassState != null) {\n"
            "                        Modifier.liquefiable(waterGlassState)\n"
            "                    } else {\n"
            "                        Modifier\n"
            "                    },\n"
            "                ),\n"
            "            typography = customTypography,\n"
            "            content = content,\n"
            "        )\n"
            "    }\n"
            "}\n",
        )

        system_bars = write(
            M05A3_SYSTEM_BARS_PATH,
            "package com.kiyori.platform.window\n\n"
            "import android.os.Build\n"
            "import androidx.activity.ComponentActivity\n"
            "import androidx.activity.SystemBarStyle\n"
            "import androidx.activity.enableEdgeToEdge\n"
            "import androidx.compose.runtime.Composable\n"
            "import androidx.compose.runtime.SideEffect\n"
            "import androidx.compose.ui.graphics.Color\n"
            "import androidx.compose.ui.graphics.toArgb\n"
            "import androidx.compose.ui.platform.LocalView\n"
            "import androidx.core.view.WindowCompat\n"
            "import androidx.core.view.WindowInsetsCompat\n"
            "import androidx.core.view.WindowInsetsControllerCompat\n\n"
            "@Composable\n"
            "fun KiyoriApplicationSystemBars(\n"
            "    darkTheme: Boolean,\n"
            "    navigationBarColor: Color,\n"
            "    statusBarHidden: Boolean,\n"
            ") {\n"
            "    val view = LocalView.current\n"
            "    if (!view.isInEditMode) {\n"
            "        SideEffect {\n"
            "            val activity = view.context as ComponentActivity\n"
            "            val window = activity.window\n"
            "            val transparentBarColor = "
            "android.graphics.Color.TRANSPARENT\n"
            "            val statusBarStyle = if (darkTheme) {\n"
            "                SystemBarStyle.dark(transparentBarColor)\n"
            "            } else {\n"
            "                SystemBarStyle.light("
            "transparentBarColor, transparentBarColor)\n"
            "            }\n"
            "            val navigationBarArgb = navigationBarColor.toArgb()\n"
            "            val navigationBarStyle = if (darkTheme) {\n"
            "                SystemBarStyle.dark(navigationBarArgb)\n"
            "            } else {\n"
            "                SystemBarStyle.light("
            "navigationBarArgb, navigationBarArgb)\n"
            "            }\n"
            "            activity.enableEdgeToEdge("
            "statusBarStyle, navigationBarStyle)\n"
            "            val insetsController = "
            "WindowCompat.getInsetsController(window, window.decorView)\n"
            "            if (statusBarHidden) {\n"
            "                insetsController.hide("
            "WindowInsetsCompat.Type.statusBars())\n"
            "                insetsController.systemBarsBehavior = "
            "WindowInsetsControllerCompat."
            "BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE\n"
            "            } else {\n"
            "                insetsController.show("
            "WindowInsetsCompat.Type.statusBars())\n"
            "            }\n"
            "            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {\n"
            "                window.isNavigationBarContrastEnforced = true\n"
            "            }\n"
            "        }\n"
            "    }\n"
            "}\n",
        )

        type_adapter = write(
            M05A3_TYPE_PATH,
            "package com.ai.assistance.operit.ui.theme\n\n"
            "import android.content.Context\n"
            "import androidx.compose.material3.Typography\n"
            "import androidx.compose.ui.text.font.FontFamily\n"
            "import com.ai.assistance.operit.data.preferences.UserPreferencesManager\n"
            "import com.ai.assistance.operit.util.AppLogger\n"
            "import com.kiyori.design.theme.KiyoriTypography\n"
            "import com.kiyori.design.theme.applyFontFamilyToTypography\n\n"
            "fun getSystemFontFamily(systemFontName: String): FontFamily = "
            "FontFamily.Default\n"
            "fun loadCustomFontFamily("
            "context: Context, fontPath: String): FontFamily? = null\n"
            "fun resolveConfiguredFontFamily(\n"
            "    context: Context,\n"
            "    useCustomFont: Boolean,\n"
            "    fontType: String,\n"
            "    systemFontName: String,\n"
            "    customFontPath: String?,\n"
            "): FontFamily? = null\n"
            "fun createCustomTypography(\n"
            "    context: Context,\n"
            "    useCustomFont: Boolean,\n"
            "    fontType: String,\n"
            "    systemFontName: String,\n"
            "    customFontPath: String?,\n"
            "    fontScale: Float,\n"
            "): Typography {\n"
            "    if (!useCustomFont && fontScale == 1.0f) {\n"
            "        return KiyoriTypography\n"
            "    }\n"
            "    val fontFamily = resolveConfiguredFontFamily(\n"
            "        context, useCustomFont, fontType, "
            "systemFontName, customFontPath,\n"
            "    )\n"
            "    return applyFontFamilyToTypography("
            "KiyoriTypography, fontFamily)\n"
            "}\n",
        )

        utility_theme = write(
            M05A3_UTILITY_THEME_PATH,
            "package com.ai.assistance.operit.ui.common\n"
            "import com.ai.assistance.operit.data.preferences.UserPreferencesManager\n"
            "import com.ai.assistance.operit.ui.theme.resolveThemeColorScheme\n"
            "import com.kiyori.design.theme.KiyoriTypography\n"
            "val utilityTypography = KiyoriTypography\n",
        )
        floating_theme = write(
            M05A3_FLOATING_THEME_PATH,
            "package com.ai.assistance.operit.ui.floating\n"
            "import com.kiyori.design.theme.KiyoriTypography\n"
            "val floatingTypography = KiyoriTypography\n",
        )
        main_activity = write(
            M04_MAIN_ACTIVITY_PATH,
            "package com.ai.assistance.operit.ui.main\n"
            "import com.kiyori.app.theme.KiyoriTheme\n"
            "class MainActivity {\n"
            "    fun setAppContent() { KiyoriTheme { } }\n"
            "}\n",
        )
        widget_host = write(
            M05A3_WIDGET_THEME_HOST_PATH,
            "package com.ai.assistance.operit.widget\n"
            "import com.kiyori.app.theme.KiyoriTheme\n"
            "class ToolPkgDesktopWidgetConfigActivity {\n"
            "    fun setAppContent() { KiyoriTheme { } }\n"
            "}\n",
        )
        consumer_paths = (
            "app/src/main/java/com/ai/assistance/operit/ui/features/"
            "settings/ThemeConsumer.kt",
            "app/src/main/java/com/ai/assistance/operit/ui/features/"
            "chat/AiTypographyConsumer.kt",
            "app/src/main/java/com/ai/assistance/operit/ui/features/"
            "chat/UserTypographyConsumer.kt",
        )
        for index, relative_path in enumerate(consumer_paths):
            write(
                relative_path,
                "package com.ai.assistance.operit.ui.features.test\n"
                "import com.ai.assistance.operit.ui.theme."
                "resolveConfiguredFontFamily\n"
                "import com.kiyori.design.theme."
                "applyFontFamilyToTypography\n"
                f"val consumer{index} = ::applyFontFamilyToTypography\n",
            )

        liquid_glass = write(
            M05A3_LIQUID_GLASS_PATH,
            "package com.ai.assistance.operit.ui.theme\n"
            "val LocalLiquidGlassBackdrop = Any()\n"
            "fun isLiquidGlassSupported(): Boolean = true\n",
        )
        water_glass = write(
            M05A3_WATER_GLASS_PATH,
            "package com.ai.assistance.operit.ui.theme\n"
            "val LocalWaterGlassState = Any()\n"
            "fun isWaterGlassSupported(): Boolean = true\n",
        )
        player = root / M05A3_PLAYER_ACTIVITY_PATH
        player.parent.mkdir(parents=True, exist_ok=True)
        player.write_bytes(
            (REPO_ROOT / M05A3_PLAYER_ACTIVITY_PATH).read_bytes()
        )

        for relative_path in M05A3_THEME_RESOURCE_PATHS:
            write(
                relative_path,
                "<resources>\n"
                f"    <style name=\"{M05A3_NEW_STYLE}\" "
                "parent=\"@style/KiyoriThemeBase\" />\n"
                "</resources>\n",
            )

        manifest = write(
            "app/src/main/AndroidManifest.xml",
            '<manifest xmlns:android="http://schemas.android.com/apk/res/android">\n'
            "    <application\n"
            '        android:name="com.kiyori.app.KiyoriApplication"\n'
            f'        android:theme="@style/{M05A3_NEW_STYLE}">\n'
            f'        <activity android:name=".ui.features.player.PlayerActivity" '
            f'android:theme="@style/{M05A3_NEW_STYLE}" />\n'
            f'        <activity android:name=".ui.main.MainActivity" '
            f'android:theme="@style/{M05A3_NEW_STYLE}" />\n'
            f'        <activity android:name=".ui.error.CrashReportActivity" '
            f'android:theme="@style/{M05A3_NEW_STYLE}" />\n'
            f'        <activity android:name=".ui.recovery.DataRecoveryActivity" '
            f'android:theme="@style/{M05A3_NEW_STYLE}" />\n'
            f'        <activity android:name="'
            f'.widget.ToolPkgDesktopWidgetConfigActivity" '
            f'android:theme="@style/{M05A3_NEW_STYLE}" />\n'
            "    </application>\n"
            "</manifest>\n",
        )

        design_test = write(
            M05A1_DESIGN_TEST_PATH,
            "package com.kiyori.design.theme\n"
            "import androidx.compose.ui.unit.sp\n"
            "import org.junit.Assert.assertEquals\n"
            "class KiyoriDesignThemeTest {\n"
            "    fun `all typography roles use neutral tracking`() {\n"
            "        val styles = listOf(\n"
            "            KiyoriTypography.displayLarge,\n"
            "            KiyoriTypography.labelSmall,\n"
            "        )\n"
            "        styles.forEach { style -> "
            "assertEquals(0.sp, style.letterSpacing) }\n"
            "    }\n"
            "}\n",
        )

        architecture_root = root / "config/architecture"
        architecture_root.mkdir(parents=True, exist_ok=True)
        root_hash_entries = []
        for relative_path in M05A3_HASHED_PATHS:
            source_path = root / relative_path
            normalized = source_path.read_bytes().replace(b"\r\n", b"\n")
            digest = hashlib.sha256(normalized).hexdigest().upper()
            root_hash_entries.append(f"{digest}\t{relative_path}")
        (architecture_root / M05A3_ROOT_HASH_SNAPSHOT).write_text(
            "\n".join(root_hash_entries) + "\n",
            encoding="utf-8",
        )

        consumer_entries = [
            (
                M04_MAIN_ACTIVITY_PATH,
                "com.kiyori.app.theme.KiyoriTheme",
            ),
            (
                M05A3_WIDGET_THEME_HOST_PATH,
                "com.kiyori.app.theme.KiyoriTheme",
            ),
            (
                M05A3_UTILITY_THEME_PATH,
                "com.kiyori.design.theme.KiyoriTypography",
            ),
            (
                M05A3_FLOATING_THEME_PATH,
                "com.kiyori.design.theme.KiyoriTypography",
            ),
        ]
        consumer_entries.extend(
            (
                relative_path,
                "com.kiyori.design.theme.applyFontFamilyToTypography",
            )
            for relative_path in consumer_paths
        )
        (architecture_root / M05A3_CONSUMER_IMPORT_SNAPSHOT).write_text(
            "\n".join(
                f"{relative_path}\t{imported}"
                for relative_path, imported in sorted(consumer_entries)
            )
            + "\n",
            encoding="utf-8",
        )
        (architecture_root / M04E_MAIN_ACTIVITY_IMPORT_SNAPSHOT).write_text(
            "com.kiyori.app.theme.KiyoriTheme\n",
            encoding="utf-8",
        )
        manifest_hash = manifest_semantic_hash(manifest)
        (architecture_root / "manifest-structure-hashes.txt").write_text(
            f"baseline\t{manifest_hash}\n"
            f"m01\t{manifest_hash}\n"
            f"post-m01\t{manifest_hash}\n"
            f"m03\t{manifest_hash}\n",
            encoding="utf-8",
        )

        ownership = architecture_root / "package-ownership.toml"
        ownership.write_text(
            "schema_version = 1\n"
            "[[ownership]]\n"
            'id = "operit-ui"\n'
            'path = "app/src/main/java/com/ai/assistance/operit/ui/**"\n'
            'owner = "mixed-current"\n'
            'sync_zone = "B"\n'
            'phase = "current"\n'
            'allowed_import_roots = [\n'
            '  "com.ai.assistance.operit",\n'
            '  "com.kiyori.design",\n'
            "]\n"
            "[[ownership]]\n"
            'id = "operit-widget"\n'
            'path = "app/src/main/java/com/ai/assistance/operit/widget/**"\n'
            'owner = "compatibility"\n'
            'sync_zone = "D"\n'
            'phase = "current"\n'
            'allowed_import_roots = [\n'
            '  "com.ai.assistance.operit",\n'
            '  "com.kiyori.capability",\n'
            '  "com.kiyori.platform",\n'
            "]\n"
            'forbidden_import_roots = [\n'
            '  "com.kiyori.app",\n'
            '  "com.kiyori.feature",\n'
            "]\n"
            "[[ownership]]\n"
            'id = "kiyori-app"\n'
            'path = "app/src/main/java/com/kiyori/app/**"\n'
            'owner = "kiyori-app"\n'
            'sync_zone = "C"\n'
            'phase = "m03"\n'
            'allowed_import_roots = [\n'
            '  "com.kiyori.app",\n'
            '  "com.kiyori.capability",\n'
            '  "com.kiyori.design",\n'
            '  "com.kiyori.feature",\n'
            '  "com.kiyori.integration",\n'
            '  "com.kiyori.platform",\n'
            "]\n"
            "[[ownership]]\n"
            'id = "kiyori-design"\n'
            'path = "app/src/main/java/com/kiyori/design/**"\n'
            'owner = "kiyori-design"\n'
            'sync_zone = "C"\n'
            'phase = "m05a1"\n'
            'allowed_import_roots = [\n'
            '  "com.kiyori.capability",\n'
            '  "com.kiyori.design",\n'
            '  "com.kiyori.platform",\n'
            "]\n"
            "[[ownership]]\n"
            'id = "kiyori-platform"\n'
            'path = "app/src/main/java/com/kiyori/platform/**"\n'
            'owner = "kiyori-platform"\n'
            'sync_zone = "C"\n'
            'phase = "m05a3"\n'
            'allowed_import_roots = [\n'
            '  "com.kiyori.capability",\n'
            '  "com.kiyori.platform",\n'
            "]\n"
            "[[exception]]\n"
            f'rule = "{M05A3_APP_THEME_EXCEPTION_RULE}"\n'
            f'path = "{M05A3_APP_THEME_PATH}"\n'
            f'reason = "{M05A3_APP_THEME_EXCEPTION_REASON}"\n'
            f'expires_after = "{M05A3_APP_THEME_EXCEPTION_EXPIRY}"\n'
            'owner = "kiyori-app"\n'
            "[[exception]]\n"
            f'rule = "{M05A3_WIDGET_EXCEPTION_RULE}"\n'
            f'path = "{M05A3_WIDGET_THEME_HOST_PATH}"\n'
            f'reason = "{M05A3_WIDGET_EXCEPTION_REASON}"\n'
            f'expires_after = "{M05A3_WIDGET_EXCEPTION_EXPIRY}"\n'
            'owner = "compatibility"\n',
            encoding="utf-8",
        )

        write(
            M05A3_ARCHITECTURE_TEST_PATH,
            "def test_m05a3_root_theme_accepts_split_owners():\n"
            "    pass\n"
            "def test_m05a3_root_theme_rejects_owner_style_or_player_drift():\n"
            "    pass\n",
        )
        self.assertTrue(design_theme.is_file())
        self.assertTrue(typography.is_file())
        self.assertTrue(app_theme.is_file())
        self.assertTrue(system_bars.is_file())
        self.assertTrue(type_adapter.is_file())
        self.assertTrue(utility_theme.is_file())
        self.assertTrue(floating_theme.is_file())
        self.assertTrue(main_activity.is_file())
        self.assertTrue(widget_host.is_file())
        self.assertTrue(liquid_glass.is_file())
        self.assertTrue(water_glass.is_file())
        self.assertTrue(player.is_file())
        self.assertTrue(design_test.is_file())
        return ownership

    def write_m05b_platform_logging_layout(self, root: Path) -> Path:
        def write(relative_path: str, text: str) -> Path:
            path = root / relative_path
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(text, encoding="utf-8")
            return path

        write(
            M05A3_DESIGN_THEME_PATH,
            "package com.kiyori.design.theme\n"
            "fun KiyoriTheme() = Unit\n",
        )
        write(
            M05B_LOGGER_PATH,
            "package com.kiyori.platform.logging\n\n"
            "import android.content.Context\n"
            "import android.util.Log\n"
            "import com.kiyori.platform.android.ApplicationContextAccess\n"
            "import com.kiyori.platform.lifecycle.ApplicationStartupTime\n"
            "import java.io.File\n"
            "import java.io.FileWriter\n"
            "import java.text.SimpleDateFormat\n"
            "import java.util.Locale\n"
            "import java.util.concurrent.Executors\n"
            "import java.util.regex.Pattern\n\n"
            "object KiyoriLogger {\n"
            "    const val VERBOSE: Int = Log.VERBOSE\n"
            "    const val DEBUG: Int = Log.DEBUG\n"
            "    const val INFO: Int = Log.INFO\n"
            "    const val WARN: Int = Log.WARN\n"
            "    const val ERROR: Int = Log.ERROR\n"
            "    const val ASSERT: Int = Log.ASSERT\n"
            '    private const val LOG_DIR_NAME = "logs"\n'
            '    private const val LOG_FILE_NAME = KiyoriLogFileMigration.ACTIVE_FILE_NAME\n'
            '    private const val LEGACY_LOG_FILE_NAME = KiyoriLogFileMigration.LEGACY_FILE_NAME\n'
            '    private const val PACKAGE_LOG_DIR_NAME = "packageLogs"\n'
            '    private const val TOOLPKG_LOG_TAG = "ToolPkg"\n'
            "    private const val MAX_LOG_MESSAGE_CHARS = 12_000\n"
            "    private const val MAX_LOG_THROWABLE_CHARS = 24_000\n"
            '    private val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)\n'
            '    private val startup = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)\n'
            "    private val regex = Pattern.compile(\"toolPkgId\")\n"
            "    @Volatile var enableFileLogging: Boolean = true\n"
            "    @Volatile private var logFile: File? = null\n"
            "    @Volatile private var packageLogFile: File? = null\n"
            "    @Volatile private var boundFilesDir: File? = null\n"
            "    @Volatile private var packageLogRootProvider: (() -> File)? = null\n"
            "    private val fileLogExecutor = Executors.newSingleThreadExecutor { runnable ->\n"
            '        Thread(runnable, "KiyoriAppLogger")\n'
            "    }\n"
            "    fun bindContext(context: Context, packageLogRootProvider: () -> File) {\n"
            "        boundFilesDir = context.applicationContext.filesDir\n"
            "        this.packageLogRootProvider = packageLogRootProvider\n"
            "    }\n"
            "    private fun resolve(): File {\n"
            "        val appContext = ApplicationContextAccess.current\n"
            "        val filesDir = appContext.filesDir\n"
            "        val startupMs = ApplicationStartupTime.epochMillis\n"
            "        System.currentTimeMillis()\n"
            "        File(filesDir, LOG_DIR_NAME)\n"
            "        val rootProvider = packageLogRootProvider ?: error(\"missing\")\n"
            "        File(rootProvider(), PACKAGE_LOG_DIR_NAME)\n"
            "        KiyoriLogFileMigration.resolve(File(filesDir, LOG_DIR_NAME))\n"
            "        KiyoriLogFileMigration.migrate(File(filesDir, LOG_DIR_NAME))\n"
            "        return File(LOG_FILE_NAME)\n"
            "    }\n"
            "    private fun resolveFilesDir(): File? = ApplicationContextAccess.current.filesDir\n"
            "    private fun writeToFile(priority: Int, tag: String, msg: String, tr: Throwable?) {\n"
            "        fileLogExecutor.execute {\n"
            "            writeToFileSync(priority, tag, msg, tr)\n"
            "        }\n"
            "    }\n"
            "    private fun writeToFileSync(priority: Int, tag: String, msg: String, tr: Throwable?) {\n"
            "        val file = resolve()\n"
            "        FileWriter(file, true).use { it.write(msg) }\n"
            "        KiyoriLogTextFormatter.format(tr, MAX_LOG_THROWABLE_CHARS)\n"
            "        KiyoriLogTextFormatter.truncateText(msg, MAX_LOG_MESSAGE_CHARS)\n"
            "        writeToPackageLogIfNeeded(tag, msg)\n"
            "    }\n"
            "    private fun writeToPackageLogIfNeeded(tag: String, msg: String) = Unit\n"
            "    fun v(tag: String, msg: String): Int = Log.v(tag, msg)\n"
            "    fun d(tag: String, msg: String): Int = Log.d(tag, msg)\n"
            "    fun i(tag: String, msg: String): Int = Log.i(tag, msg)\n"
            "    fun w(tag: String, msg: String): Int = Log.w(tag, msg)\n"
            "    fun e(tag: String, msg: String): Int = Log.e(tag, msg)\n"
            "    fun wtf(tag: String, msg: String): Int = Log.wtf(tag, msg)\n"
            "    fun isLoggable(tag: String, level: Int): Boolean = Log.isLoggable(tag, level)\n"
            "    fun println(priority: Int, tag: String, msg: String): Int = Log.println(priority, tag, msg)\n"
            "    fun getStackTraceString(tr: Throwable): String = KiyoriLogTextFormatter.format(tr)\n"
            "    fun getLogFile(): File? = resolveLogFile()\n"
            "    private fun resolveLogFile(): File? = logFile\n"
            "    fun resetLogFile() { logFile = null; packageLogFile = null }\n"
            "}\n",
        )
        write(
            M05B_LOG_MIGRATION_PATH,
            "package com.kiyori.platform.logging\n\n"
            "import java.io.File\n"
            "import java.io.FileOutputStream\n"
            "import java.nio.file.Files\n"
            "import java.nio.file.StandardCopyOption\n\n"
            "internal object KiyoriLogFileMigration {\n"
            '    const val ACTIVE_FILE_NAME = "kiyori.log"\n'
            '    const val LEGACY_FILE_NAME = "operit.log"\n'
            "    fun resolve(logDirectory: File): File = File(logDirectory, ACTIVE_FILE_NAME)\n"
            "    fun migrate(logDirectory: File) {\n"
            "        val legacyFile = File(logDirectory, LEGACY_FILE_NAME)\n"
            "        val activeFile = File(logDirectory, ACTIVE_FILE_NAME)\n"
            "        val temporaryFile = File(logDirectory, \"$ACTIVE_FILE_NAME.tmp\")\n"
            "        FileOutputStream(temporaryFile).use { fileOutput ->\n"
            "            fileOutput.write(legacyFile.readBytes())\n"
            "            fileOutput.fd.sync()\n"
            "        }\n"
            "        Files.move(temporaryFile.toPath(), activeFile.toPath(), StandardCopyOption.ATOMIC_MOVE)\n"
            "    }\n"
            "}\n",
        )
        write(
            M05B_FORMATTER_PATH,
            "package com.kiyori.platform.logging\n\n"
            "import java.util.Collections\n"
            "import java.util.IdentityHashMap\n\n"
            "internal object KiyoriLogTextFormatter {\n"
            "    private const val MAX_FRAMES_PER_THROWABLE = 64\n"
            "    private const val MAX_CAUSE_DEPTH = 8\n"
            "    fun format(throwable: Throwable?, maxChars: Int = 24000): String {\n"
            "        Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())\n"
            "        return throwable?.toString().orEmpty().take(maxChars)\n"
            "    }\n"
            "    fun truncateText(text: String?, maxChars: Int = 24000): String =\n"
            "        text.orEmpty().take(maxChars)\n"
            "}\n",
        )
        write(
            M05B_LEGACY_FACADE_PATH,
            "package com.ai.assistance.operit.util\n\n"
            "import android.content.Context\n"
            "import com.kiyori.platform.logging.KiyoriLogger\n"
            "import java.io.File\n\n"
            "object AppLogger {\n"
            "    const val VERBOSE: Int = KiyoriLogger.VERBOSE\n"
            "    const val DEBUG: Int = KiyoriLogger.DEBUG\n"
            "    const val INFO: Int = KiyoriLogger.INFO\n"
            "    const val WARN: Int = KiyoriLogger.WARN\n"
            "    const val ERROR: Int = KiyoriLogger.ERROR\n"
            "    const val ASSERT: Int = KiyoriLogger.ASSERT\n"
            "    var enableFileLogging: Boolean\n"
            "        get() = KiyoriLogger.enableFileLogging\n"
            "        set(value) { KiyoriLogger.enableFileLogging = value }\n"
            "    fun bindContext(context: Context) =\n"
            "        KiyoriLogger.bindContext(context, OperitPaths::kiyoriRootDir)\n"
            "    fun v(tag: String, msg: String) = KiyoriLogger.v(tag, msg)\n"
            "    fun d(tag: String, msg: String) = KiyoriLogger.d(tag, msg)\n"
            "    fun i(tag: String, msg: String) = KiyoriLogger.i(tag, msg)\n"
            "    fun w(tag: String, msg: String) = KiyoriLogger.w(tag, msg)\n"
            "    fun e(tag: String, msg: String) = KiyoriLogger.e(tag, msg)\n"
            "    fun wtf(tag: String, msg: String) = KiyoriLogger.wtf(tag, msg)\n"
            "    fun isLoggable(tag: String, level: Int) = KiyoriLogger.isLoggable(tag, level)\n"
            "    fun println(priority: Int, tag: String, msg: String) =\n"
            "        KiyoriLogger.println(priority, tag, msg)\n"
            "    fun getStackTraceString(tr: Throwable) = KiyoriLogger.getStackTraceString(tr)\n"
            "    fun getLogFile(): File? = KiyoriLogger.getLogFile()\n"
            "    fun resetLogFile() = KiyoriLogger.resetLogFile()\n"
            "}\n",
        )
        write(
            M05B_CRASH_REPORT_STORE_PATH,
            "package com.ai.assistance.operit.util.crash\n\n"
            f"import {M05B_NEW_FORMATTER_IMPORT}\n"
            "val text = KiyoriLogTextFormatter.truncateText(\"crash\")\n",
        )
        write(
            M05B_MEMORY_PROVIDER_PATH,
            "package com.ai.assistance.operit.provider\n\n"
            "import android.content.Context\n"
            f"import {M05B_OLD_LOGGER_IMPORT}\n"
            "fun bind(context: Context) {\n"
            "    AppLogger.bindContext(context)\n"
            "}\n",
        )

        consumer_paths = (
            M03_APPLICATION_PATH,
            M04_ROOT_PATH,
            M04D_MAIN_DISPLAY_COORDINATOR_PATH,
            M04D_MAIN_SHARED_CONTENT_COORDINATOR_PATH,
            M04D_MAIN_TASK_VISIBILITY_COORDINATOR_PATH,
            M04D_MAIN_ORIENTATION_COORDINATOR_PATH,
            M04C_NAVIGATION_INTEGRATION_PATH,
            KIYORI_FIRST_RUN_SCREEN_PATH,
            KIYORI_FIRST_RUN_PERMISSIONS_PATH,
            "app/src/main/java/com/kiyori/integration/operit/onboarding/"
            "KiyoriPermissionsSettingsPage.kt",
            "app/src/main/java/com/kiyori/platform/network/"
            "KiyoriMihomoRuntime.kt",
            "app/src/main/java/com/kiyori/platform/network/"
            "KiyoriNetworkProxyConfigStore.kt",
        )
        for relative_path in consumer_paths:
            extra_import = (
                "import com.ai.assistance.operit.util.OperitPaths\n"
                if relative_path == M03_APPLICATION_PATH
                else ""
            )
            body = (
                "fun install() {\n"
                "    KiyoriLogger.bindContext(this, OperitPaths::kiyoriRootDir)\n"
                "}\n"
                if relative_path == M03_APPLICATION_PATH
                else "fun log() = KiyoriLogger.d(\"test\", \"message\")\n"
            )
            write(
                relative_path,
                (
                    "package com.kiyori.integration.operit.navigation\n\n"
                    if relative_path == M04C_NAVIGATION_INTEGRATION_PATH
                    else (
                        "package com.kiyori.integration.operit.onboarding\n\n"
                        if relative_path.startswith(
                            "app/src/main/java/com/kiyori/integration/operit/onboarding/"
                        )
                        else "package com.kiyori.app\n\n"
                    )
                )
                + f"import {M05B_NEW_LOGGER_IMPORT}\n"
                f"{extra_import}\n"
                f"{body}",
            )

        write(
            "app/src/test/java/com/ai/assistance/operit/core/tools/condition/"
            "ConditionEvaluatorTest.kt",
            "package com.ai.assistance.operit.core.tools.condition\n"
            f"import {M05B_OLD_LOGGER_IMPORT}\n"
            "fun test() = Mockito.mockStatic(AppLogger::class.java)\n",
        )
        write(
            "app/src/test/java/com/ai/assistance/operit/core/tools/condition/"
            "ConditionEvaluatorParseFailureTest.kt",
            "package com.ai.assistance.operit.core.tools.condition\n"
            f"import {M05B_OLD_LOGGER_IMPORT}\n"
            "fun test() = Mockito.mockStatic(AppLogger::class.java)\n",
        )
        write(
            "app/src/test/java/com/ai/assistance/operit/api/chat/llmprovider/"
            "OpenAIResponsesSubmissionFaultInjectionTest.kt",
            "package com.ai.assistance.operit.api.chat.llmprovider\n"
            f"import {M05B_OLD_LOGGER_IMPORT}\n"
            "fun test() = Mockito.mockStatic(AppLogger::class.java)\n",
        )
        write(
            "app/src/test/java/com/ai/assistance/operit/api/chat/llmprovider/"
            "OpenAIResponsesImageHistoryRequestTest.kt",
            "package com.ai.assistance.operit.api.chat.llmprovider\n"
            f"import {M05B_OLD_LOGGER_IMPORT}\n"
            "fun test() = Mockito.mockStatic(AppLogger::class.java)\n",
        )
        write(
            "app/src/test/java/com/ai/assistance/operit/api/chat/llmprovider/"
            "ApiKeyProviderLogPrivacyTest.kt",
            "package com.ai.assistance.operit.api.chat.llmprovider\n"
            f"import {M05B_OLD_LOGGER_IMPORT}\n"
            "fun test() = Mockito.mockStatic(AppLogger::class.java)\n",
        )
        write(
            "app/src/test/java/com/ai/assistance/operit/api/chat/llmprovider/"
            "LlmTransportDiagnosticsTest.kt",
            "package com.ai.assistance.operit.api.chat.llmprovider\n"
            f"import {M05B_OLD_LOGGER_IMPORT}\n"
            "fun test() = Mockito.mockStatic(AppLogger::class.java)\n",
        )
        write(
            M05B_FORMATTER_TEST_PATH,
            "package com.kiyori.platform.logging\n"
            "fun unchangedTextRemainsUnchanged() = Unit\n"
            "fun longTextIsBoundedAndMarked() = Unit\n"
            "fun throwableFormattingKeepsCauseAndBound() = Unit\n",
        )
        write(
            M05B_ARCHITECTURE_TEST_PATH,
            "def test_m05b_platform_logging_accepts_single_owner_and_facade():\n"
            "    pass\n"
            "def test_m05b_platform_logging_rejects_state_or_compatibility_drift():\n"
            "    pass\n",
        )

        architecture_root = root / "config/architecture"
        architecture_root.mkdir(parents=True, exist_ok=True)
        hash_entries = []
        for relative_path in M05B_HASHED_PATHS:
            source_path = root / relative_path
            normalized = source_path.read_bytes().replace(b"\r\n", b"\n")
            digest = hashlib.sha256(normalized).hexdigest().upper()
            hash_entries.append(f"{digest}\t{relative_path}")
        (architecture_root / M05B_HASH_SNAPSHOT).write_text(
            "\n".join(hash_entries) + "\n",
            encoding="utf-8",
        )
        (architecture_root / M05B_KIYORI_CONSUMER_SNAPSHOT).write_text(
            "\n".join(sorted(consumer_paths)) + "\n",
            encoding="utf-8",
        )
        ownership = architecture_root / "package-ownership.toml"
        ownership.write_text(
            "schema_version = 1\n"
            "[[ownership]]\n"
            'id = "kiyori-platform"\n'
            'path = "app/src/main/java/com/kiyori/platform/**"\n'
            'owner = "kiyori-platform"\n'
            'sync_zone = "C"\n'
            'phase = "m05a3"\n'
            'allowed_import_roots = [\n'
            '  "com.kiyori.capability",\n'
            '  "com.kiyori.platform",\n'
            "]\n",
            encoding="utf-8",
        )
        self.assertFalse((root / M05B_OLD_FORMATTER_PATH).exists())
        self.assertNotIn(
            M05B_DISPLAY_EXCEPTION_PATH,
            ownership.read_text(encoding="utf-8"),
        )
        return ownership

    def write_m05c_platform_lifecycle_layout(self, root: Path) -> Path:
        def write(relative_path: str, text: str) -> Path:
            path = root / relative_path
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(text, encoding="utf-8")
            return path

        write(
            M05B_LOGGER_PATH,
            "package com.kiyori.platform.logging\n"
            "object KiyoriLogger\n",
        )
        write(
            M05C_PLATFORM_LIFECYCLE_PATH,
            "package com.kiyori.platform.lifecycle\n\n"
            "import android.app.Activity\n"
            "import android.app.Application\n"
            "import android.os.Bundle\n"
            "import java.lang.ref.WeakReference\n\n"
            "internal interface KiyoriActivityLifecycleObserver {\n"
            "    fun onActivityCreated(activity: Activity, activeActivityCount: Int)\n"
            "    fun onActivityStarted(activity: Activity, enteredForeground: Boolean)\n"
            "    fun onActivityResumed(activity: Activity)\n"
            "    fun onActivityPaused(activity: Activity)\n"
            "    fun onActivityStopped(activity: Activity, enteredBackground: Boolean)\n"
            "    fun onActivityDestroyed(activity: Activity, activeActivityCount: Int)\n"
            "}\n\n"
            "internal class KiyoriActivityLifecycleFacts {\n"
            "    private var currentActivity: WeakReference<Activity>? = null\n"
            "    private var activityCount = 0\n"
            "    private var startedActivityCount = 0\n"
            "    private var isAppInForeground = false\n"
            "    fun getCurrentActivity(): Activity? = currentActivity?.get()\n"
            "    fun isAppInForeground(): Boolean = isAppInForeground\n"
            "    fun onActivityCreated(): Int { activityCount += 1; return activityCount }\n"
            "    fun onActivityStarted(): Boolean {\n"
            "        startedActivityCount += 1\n"
            "        if (!isAppInForeground && startedActivityCount > 0) {\n"
            "            isAppInForeground = true\n"
            "            return true\n"
            "        }\n"
            "        return false\n"
            "    }\n"
            "    fun onActivityResumed(activity: Activity) {\n"
            "        currentActivity = WeakReference(activity)\n"
            "    }\n"
            "    fun onActivityPaused(activity: Activity) {\n"
            "        if (currentActivity?.get() == activity) currentActivity?.clear()\n"
            "    }\n"
            "    fun onActivityStopped(): Boolean {\n"
            "        startedActivityCount = (startedActivityCount - 1).coerceAtLeast(0)\n"
            "        if (isAppInForeground && startedActivityCount == 0) {\n"
            "            isAppInForeground = false\n"
            "            return true\n"
            "        }\n"
            "        return false\n"
            "    }\n"
            "    fun onActivityDestroyed(activity: Activity): Int {\n"
            "        if (currentActivity?.get() == activity) currentActivity?.clear()\n"
            "        activityCount -= 1\n"
            "        return activityCount\n"
            "    }\n"
            "}\n\n"
            "object KiyoriActivityLifecycle : Application.ActivityLifecycleCallbacks {\n"
            "    private val facts = KiyoriActivityLifecycleFacts()\n"
            "    private lateinit var observer: KiyoriActivityLifecycleObserver\n"
            "    internal fun initialize(\n"
            "        application: Application,\n"
            "        observer: KiyoriActivityLifecycleObserver,\n"
            "    ) {\n"
            "        this.observer = observer\n"
            "        application.registerActivityLifecycleCallbacks(this)\n"
            "    }\n"
            "    fun getCurrentActivity(): Activity? = facts.getCurrentActivity()\n"
            "    fun isAppInForeground(): Boolean = facts.isAppInForeground()\n"
            "    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {\n"
            "        observer.onActivityCreated(activity, facts.onActivityCreated())\n"
            "    }\n"
            "    override fun onActivityStarted(activity: Activity) {\n"
            "        observer.onActivityStarted(activity, facts.onActivityStarted())\n"
            "    }\n"
            "    override fun onActivityResumed(activity: Activity) {\n"
            "        facts.onActivityResumed(activity)\n"
            "        observer.onActivityResumed(activity)\n"
            "    }\n"
            "    override fun onActivityPaused(activity: Activity) {\n"
            "        facts.onActivityPaused(activity)\n"
            "        observer.onActivityPaused(activity)\n"
            "    }\n"
            "    override fun onActivityStopped(activity: Activity) {\n"
            "        observer.onActivityStopped(activity, facts.onActivityStopped())\n"
            "    }\n"
            "    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit\n"
            "    override fun onActivityDestroyed(activity: Activity) {\n"
            "        observer.onActivityDestroyed(activity, facts.onActivityDestroyed(activity))\n"
            "    }\n"
            "}\n",
        )
        write(
            M05C_OPERIT_INTEGRATION_PATH,
            "package com.ai.assistance.operit.core.application\n\n"
            "import android.app.Activity\n"
            "import android.app.Application\n"
            "import android.view.WindowManager\n"
            "import com.ai.assistance.operit.api.chat.AIForegroundService\n"
            "import com.ai.assistance.operit.core.tools.agent.ShowerController\n"
            "import com.ai.assistance.operit.data.preferences.ApiPreferences\n"
            "import com.ai.assistance.operit.integrations.http.ExternalChatHttpAutoStarter\n"
            "import com.ai.assistance.operit.plugins.lifecycle.AppLifecycleEvent\n"
            "import com.ai.assistance.operit.plugins.lifecycle.AppLifecycleHookParams\n"
            "import com.ai.assistance.operit.plugins.lifecycle.AppLifecycleHookPluginRegistry\n"
            "import com.ai.assistance.operit.ui.common.displays.VirtualDisplayOverlay\n"
            "import com.ai.assistance.operit.util.AppLogger\n"
            "import com.ai.assistance.operit.util.crash.PlayerCrashCoordinator\n"
            f"import {M05C_PLATFORM_IMPORT}\n"
            f"import {M05C_OBSERVER_IMPORT}\n"
            "import kotlinx.coroutines.CoroutineScope\n"
            "import kotlinx.coroutines.Dispatchers\n"
            "import kotlinx.coroutines.SupervisorJob\n"
            "import kotlinx.coroutines.flow.first\n"
            "import kotlinx.coroutines.launch\n\n"
            "internal object OperitActivityLifecycleIntegration : KiyoriActivityLifecycleObserver {\n"
            "    private const val TAG = \"ActivityLifecycleManager\"\n"
            "    private lateinit var apiPreferences: ApiPreferences\n"
            "    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())\n"
            "    private var keepScreenOnPreferenceRequestCount = 0\n"
            "    private var keepScreenOnForcedRequestCount = 0\n"
            "    @Volatile private var lastMicEnsureAtMs: Long = 0L\n"
            "    fun initialize(application: Application) {\n"
            "        apiPreferences = ApiPreferences.getInstance(application.applicationContext)\n"
            "    }\n"
            "    fun checkAndApplyKeepScreenOn(enable: Boolean) = applyKeepScreenOnRequest(enable, true)\n"
            "    fun forceKeepScreenOn(enable: Boolean) = applyKeepScreenOnRequest(enable, false)\n"
            "    private fun applyKeepScreenOnRequest(enable: Boolean, respectUserPreference: Boolean) {\n"
            "        scope.launch {\n"
            "            if (enable && respectUserPreference && !apiPreferences.keepScreenOnFlow.first()) return@launch\n"
            "            keepScreenOnPreferenceRequestCount += 1\n"
            "            keepScreenOnForcedRequestCount += 1\n"
            "            KiyoriActivityLifecycle.getCurrentActivity()?.runOnUiThread {\n"
            "                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON\n"
            "            }\n"
            "            AppLogger.d(TAG, enable.toString())\n"
            "        }\n"
            "    }\n"
            "    override fun onActivityCreated(activity: Activity, activeActivityCount: Int) {\n"
            "        AppLifecycleHookPluginRegistry.dispatchAsync(\n"
            "            AppLifecycleEvent.ACTIVITY_CREATE,\n"
            "            AppLifecycleHookParams(activity.applicationContext),\n"
            "        )\n"
            "    }\n"
            "    override fun onActivityStarted(activity: Activity, enteredForeground: Boolean) {\n"
            "        AppLifecycleEvent.ACTIVITY_START\n"
            "        if (enteredForeground) {\n"
            "            ExternalChatHttpAutoStarter.ensureRunningIfEnabled(\n"
            "                context = activity.applicationContext,\n"
            "                reason = \"application_foreground\",\n"
            "            )\n"
            "            AppLifecycleEvent.APPLICATION_FOREGROUND\n"
            "        }\n"
            "    }\n"
            "    override fun onActivityResumed(activity: Activity) {\n"
            "        PlayerCrashCoordinator.onActivityResumed(activity)\n"
            "        val now = System.currentTimeMillis()\n"
            "        if (now - lastMicEnsureAtMs >= 2500L) {\n"
            "            lastMicEnsureAtMs = now\n"
            "            AIForegroundService.ensureMicrophoneForeground(activity.applicationContext)\n"
            "        }\n"
            "        AppLifecycleEvent.ACTIVITY_RESUME\n"
            "    }\n"
            "    override fun onActivityPaused(activity: Activity) {\n"
            "        AppLifecycleEvent.ACTIVITY_PAUSE\n"
            "    }\n"
            "    override fun onActivityStopped(activity: Activity, enteredBackground: Boolean) {\n"
            "        AppLifecycleEvent.ACTIVITY_STOP\n"
            "        if (enteredBackground) AppLifecycleEvent.APPLICATION_BACKGROUND\n"
            "    }\n"
            "    override fun onActivityDestroyed(activity: Activity, activeActivityCount: Int) {\n"
            "        AppLifecycleEvent.ACTIVITY_DESTROY\n"
            "        if (activeActivityCount <= 0) {\n"
            "            VirtualDisplayOverlay.hideAll()\n"
            "            ShowerController.shutdown()\n"
            "        }\n"
            "    }\n"
            "}\n",
        )
        write(
            M05C_LEGACY_FACADE_PATH,
            "package com.ai.assistance.operit.core.application\n\n"
            "import android.app.Activity\n"
            "import android.app.Application\n"
            "import android.os.Bundle\n"
            f"import {M05C_PLATFORM_IMPORT}\n\n"
            "object ActivityLifecycleManager : Application.ActivityLifecycleCallbacks {\n"
            "    fun initialize(application: Application) {\n"
            "        OperitActivityLifecycleIntegration.initialize(application)\n"
            "        KiyoriActivityLifecycle.initialize(\n"
            "            application = application,\n"
            "            observer = OperitActivityLifecycleIntegration,\n"
            "        )\n"
            "    }\n"
            "    fun getCurrentActivity(): Activity? =\n"
            "        KiyoriActivityLifecycle.getCurrentActivity()\n"
            "    fun isAppInForeground(): Boolean = KiyoriActivityLifecycle.isAppInForeground()\n"
            "    fun checkAndApplyKeepScreenOn(enable: Boolean) =\n"
            "        OperitActivityLifecycleIntegration.checkAndApplyKeepScreenOn(enable)\n"
            "    fun forceKeepScreenOn(enable: Boolean) =\n"
            "        OperitActivityLifecycleIntegration.forceKeepScreenOn(enable)\n"
            "    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) =\n"
            "        KiyoriActivityLifecycle.onActivityCreated(activity, savedInstanceState)\n"
            "    override fun onActivityStarted(activity: Activity) =\n"
            "        KiyoriActivityLifecycle.onActivityStarted(activity)\n"
            "    override fun onActivityResumed(activity: Activity) =\n"
            "        KiyoriActivityLifecycle.onActivityResumed(activity)\n"
            "    override fun onActivityPaused(activity: Activity) =\n"
            "        KiyoriActivityLifecycle.onActivityPaused(activity)\n"
            "    override fun onActivityStopped(activity: Activity) =\n"
            "        KiyoriActivityLifecycle.onActivityStopped(activity)\n"
            "    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) =\n"
            "        KiyoriActivityLifecycle.onActivitySaveInstanceState(activity, outState)\n"
            "    override fun onActivityDestroyed(activity: Activity) =\n"
            "        KiyoriActivityLifecycle.onActivityDestroyed(activity)\n"
            "}\n",
        )
        write(
            M05C_FACTS_TEST_PATH,
            "package com.kiyori.platform.lifecycle\n"
            "fun currentActivityTracksResumePauseAndDestroyIdentity() = Unit\n"
            "fun foregroundTransitionsOnlyAtZeroOneBoundaries() = Unit\n"
            "fun activityCountPreservesLastActivityCleanupBoundary() = Unit\n"
            "fun platformRegistrationUsesThePlatformCallbackOwner() = Unit\n",
        )
        write(
            M05C_ARCHITECTURE_TEST_PATH,
            "def test_m05c_platform_lifecycle_accepts_fact_and_side_effect_split():\n"
            "    pass\n"
            "def test_m05c_platform_lifecycle_rejects_state_or_compatibility_drift():\n"
            "    pass\n",
        )

        for relative_path in M05C_LEGACY_CONSUMER_PATHS:
            if relative_path == M03_APPLICATION_PATH:
                write(
                    relative_path,
                    "package com.kiyori.app\n"
                    f"import {M05C_LEGACY_IMPORT}\n"
                    "fun install(application: android.app.Application) {\n"
                    "    ActivityLifecycleManager.initialize(this)\n"
                    "}\n",
                )
            elif relative_path.endswith(".kt"):
                write(
                    relative_path,
                    "package com.ai.assistance.operit.placeholder\n"
                    f"import {M05C_LEGACY_IMPORT}\n"
                    "val lifecycleManager = ActivityLifecycleManager\n",
                )
            else:
                write(
                    relative_path,
                    "const ActivityLifecycleManager = "
                    "Java.com.ai.assistance.operit.core.application."
                    "ActivityLifecycleManager;\n",
                )

        architecture_root = root / "config/architecture"
        architecture_root.mkdir(parents=True, exist_ok=True)
        hash_entries = []
        for relative_path in M05C_HASHED_PATHS:
            source_path = root / relative_path
            normalized = source_path.read_bytes().replace(b"\r\n", b"\n")
            digest = hashlib.sha256(normalized).hexdigest().upper()
            hash_entries.append(f"{digest}\t{relative_path}")
        (architecture_root / M05C_HASH_SNAPSHOT).write_text(
            "\n".join(hash_entries) + "\n",
            encoding="utf-8",
        )
        (architecture_root / M05C_LEGACY_CONSUMER_SNAPSHOT).write_text(
            "\n".join(sorted(M05C_LEGACY_CONSUMER_PATHS)) + "\n",
            encoding="utf-8",
        )
        ownership = architecture_root / "package-ownership.toml"
        ownership.write_text(
            "schema_version = 1\n"
            "[[ownership]]\n"
            'id = "kiyori-platform"\n'
            'path = "app/src/main/java/com/kiyori/platform/**"\n'
            'owner = "kiyori-platform"\n'
            'sync_zone = "C"\n'
            'phase = "m05a3"\n'
            'allowed_import_roots = [\n'
            '  "com.kiyori.capability",\n'
            '  "com.kiyori.platform",\n'
            "]\n",
            encoding="utf-8",
        )
        return ownership

    def write_m05d_notification_permission_layout(self, root: Path) -> Path:
        def write(relative_path: str, text: str) -> Path:
            path = root / relative_path
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(text, encoding="utf-8")
            return path

        write(
            M05C_PLATFORM_LIFECYCLE_PATH,
            "package com.kiyori.platform.lifecycle\n"
            "object KiyoriActivityLifecycle\n",
        )
        write(
            M05D_PLATFORM_PERMISSION_PATH,
            "package com.kiyori.platform.permission\n\n"
            "import android.Manifest\n"
            "import android.content.pm.PackageManager\n"
            "import android.os.Build\n"
            "import androidx.activity.ComponentActivity\n"
            "import androidx.activity.result.contract.ActivityResultContracts\n"
            "import androidx.annotation.RequiresApi\n"
            "import androidx.core.content.ContextCompat\n\n"
            "internal enum class KiyoriNotificationPermissionAction {\n"
            "    NOT_REQUIRED,\n"
            "    ALREADY_GRANTED,\n"
            "    SHOW_RATIONALE_AND_REQUEST,\n"
            "    REQUEST,\n"
            "}\n\n"
            "internal fun resolveKiyoriNotificationPermissionAction(\n"
            "    sdkInt: Int,\n"
            "    isGranted: Boolean,\n"
            "    shouldShowRationale: Boolean,\n"
            "): KiyoriNotificationPermissionAction = when {\n"
            "    sdkInt < Build.VERSION_CODES.TIRAMISU ->\n"
            "        KiyoriNotificationPermissionAction.NOT_REQUIRED\n"
            "    isGranted -> KiyoriNotificationPermissionAction.ALREADY_GRANTED\n"
            "    shouldShowRationale ->\n"
            "        KiyoriNotificationPermissionAction.SHOW_RATIONALE_AND_REQUEST\n"
            "    else -> KiyoriNotificationPermissionAction.REQUEST\n"
            "}\n\n"
            "internal class KiyoriNotificationPermissionCapability(\n"
            "    private val activity: ComponentActivity,\n"
            "    onPermissionResult: (Boolean) -> Unit,\n"
            ") {\n"
            "    private val permissionLauncher = activity.registerForActivityResult(\n"
            "        ActivityResultContracts.RequestPermission(),\n"
            "        onPermissionResult,\n"
            "    )\n"
            "    fun checkAndRequest(\n"
            "        onPermissionAction: (KiyoriNotificationPermissionAction) -> Unit,\n"
            "    ) {\n"
            "        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {\n"
            "            onPermissionAction(KiyoriNotificationPermissionAction.NOT_REQUIRED)\n"
            "            return\n"
            "        }\n"
            "        checkAndRequestAtLeastTiramisu(onPermissionAction)\n"
            "    }\n"
            "    @RequiresApi(Build.VERSION_CODES.TIRAMISU)\n"
            "    private fun checkAndRequestAtLeastTiramisu(\n"
            "        onPermissionAction: (KiyoriNotificationPermissionAction) -> Unit,\n"
            "    ) {\n"
            "        val permission = Manifest.permission.POST_NOTIFICATIONS\n"
            "        val isGranted = ContextCompat.checkSelfPermission(activity, permission) ==\n"
            "            PackageManager.PERMISSION_GRANTED\n"
            "        val shouldShowRationale =\n"
            "            activity.shouldShowRequestPermissionRationale(permission)\n"
            "        val action = resolveKiyoriNotificationPermissionAction(\n"
            "            Build.VERSION.SDK_INT,\n"
            "            isGranted,\n"
            "            shouldShowRationale,\n"
            "        )\n"
            "        onPermissionAction(action)\n"
            "        when (action) {\n"
            "            KiyoriNotificationPermissionAction.SHOW_RATIONALE_AND_REQUEST,\n"
            "            KiyoriNotificationPermissionAction.REQUEST ->\n"
            "                permissionLauncher.launch(permission)\n"
            "            KiyoriNotificationPermissionAction.NOT_REQUIRED,\n"
            "            KiyoriNotificationPermissionAction.ALREADY_GRANTED -> Unit\n"
            "        }\n"
            "    }\n"
            "}\n",
        )
        write(
            M05D_OPERIT_RESOURCE_BRIDGE_PATH,
            "package com.kiyori.integration.operit.permission\n\n"
            "import androidx.annotation.StringRes\n"
            "import com.ai.assistance.operit.R\n\n"
            "internal object OperitNotificationPermissionResources {\n"
            "    @StringRes\n"
            "    val notificationPermissionDenied: Int =\n"
            "        R.string.notification_permission_denied\n"
            "    @StringRes\n"
            "    val notificationPermissionRationale: Int =\n"
            "        R.string.notification_permission_rationale\n"
            "}\n",
        )
        write(
            M05D_COORDINATOR_PATH,
            "package com.kiyori.app.startup\n\n"
            "import android.widget.Toast\n"
            "import androidx.activity.ComponentActivity\n"
            f"import {M05D_OPERIT_RESOURCE_BRIDGE_IMPORT}\n"
            f"import {M05B_NEW_LOGGER_IMPORT}\n"
            f"import {M05D_PLATFORM_ACTION_IMPORT}\n"
            f"import {M05D_PLATFORM_CAPABILITY_IMPORT}\n\n"
            "internal class KiyoriMainNotificationPermissionCoordinator(\n"
            "    private val activity: ComponentActivity,\n"
            ") {\n"
            "    private companion object { const val TAG = \"MainActivity\" }\n"
            "    private val notificationPermissionCapability =\n"
            "        KiyoriNotificationPermissionCapability(\n"
            "            activity = activity,\n"
            "            onPermissionResult = { isGranted -> isGranted },\n"
            "        )\n"
            "    fun checkAndRequest() {\n"
            "        notificationPermissionCapability.checkAndRequest { action ->\n"
            "    }\n"
            "    private fun handlePermissionResult(isGranted: Boolean) {\n"
            "        if (isGranted) {\n"
            "            KiyoriLogger.d(TAG, \"granted\")\n"
            "        } else {\n"
            "            KiyoriLogger.d(TAG, \"denied\")\n"
            "            Toast.makeText(\n"
            "                activity,\n"
            "                activity.getString(\n"
            "                    OperitNotificationPermissionResources."
            "notificationPermissionDenied,\n"
            "                ),\n"
            "                Toast.LENGTH_LONG,\n"
            "            ).show()\n"
            "        }\n"
            "    }\n"
            "    private fun handlePermissionAction(\n"
            "        action: KiyoriNotificationPermissionAction,\n"
            "    ) {\n"
            "        when (action) {\n"
            "            KiyoriNotificationPermissionAction.NOT_REQUIRED ->\n"
            "                KiyoriLogger.d(TAG, \"not required\")\n"
            "            KiyoriNotificationPermissionAction.ALREADY_GRANTED ->\n"
            "                KiyoriLogger.d(TAG, \"already granted\")\n"
            "            KiyoriNotificationPermissionAction.SHOW_RATIONALE_AND_REQUEST -> {\n"
            "                KiyoriLogger.d(TAG, \"rationale\")\n"
            "                Toast.makeText(\n"
            "                    activity,\n"
            "                    activity.getString(\n"
            "                        OperitNotificationPermissionResources."
            "notificationPermissionRationale,\n"
            "                    ),\n"
            "                    Toast.LENGTH_LONG,\n"
            "                ).show()\n"
            "            }\n"
            "            KiyoriNotificationPermissionAction.REQUEST ->\n"
            "                KiyoriLogger.d(TAG, \"request\")\n"
            "        }\n"
            "    }\n"
            "}\n",
        )
        write(
            M05D_PREFERENCES_PATH,
            "package com.ai.assistance.operit.data.preferences\n"
            "class AndroidPermissionPreferences\n",
        )
        write(
            M04_MAIN_ACTIVITY_PATH,
            "package com.ai.assistance.operit.ui.main\n"
            "import com.kiyori.app.startup.KiyoriMainNotificationPermissionCoordinator\n"
            "class MainActivity {\n"
            "    private val notificationPermissionCoordinator =\n"
            "        KiyoriMainNotificationPermissionCoordinator(this)\n"
            "    fun performInitialChecks() {\n"
            "        notificationPermissionCoordinator.checkAndRequest()\n"
            "    }\n"
            "}\n",
        )
        for relative_path in M05D_DIRECT_CONSUMER_PATHS:
            if relative_path == M05D_PLATFORM_PERMISSION_PATH:
                continue
            write(
                relative_path,
                "package com.ai.assistance.operit.placeholder\n"
                "import android.Manifest\n"
                "val notificationPermission = "
                "Manifest.permission.POST_NOTIFICATIONS\n",
            )
        write(
            "app/src/main/AndroidManifest.xml",
            "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
            "    <uses-permission "
            "android:name=\"android.permission.POST_NOTIFICATIONS\" />\n"
            "</manifest>\n",
        )
        write(
            M05D_PERMISSION_TEST_PATH,
            "package com.kiyori.platform.permission\n"
            "class KiyoriNotificationPermissionCapabilityTest {\n"
            "    fun `platforms below android thirteen do not request notification permission`() {\n"
            "        resolveKiyoriNotificationPermissionAction(32, false, false)\n"
            "        KiyoriNotificationPermissionAction.NOT_REQUIRED\n"
            "    }\n"
            "    fun `granted permission takes precedence over rationale`() {\n"
            "        KiyoriNotificationPermissionAction.ALREADY_GRANTED\n"
            "    }\n"
            "    fun `denied permission with rationale shows explanation before request`() {\n"
            "        KiyoriNotificationPermissionAction.SHOW_RATIONALE_AND_REQUEST\n"
            "    }\n"
            "    fun `denied permission without rationale requests directly`() {\n"
            "        KiyoriNotificationPermissionAction.REQUEST\n"
            "    }\n"
            "}\n",
        )
        write(
            M05D_ARCHITECTURE_TEST_PATH,
            "def test_m05d_notification_permission_accepts_platform_and_resource_split():\n"
            "    pass\n"
            "def test_m05d_notification_permission_rejects_owner_scope_or_resource_drift():\n"
            "    pass\n",
        )

        architecture_root = root / "config/architecture"
        architecture_root.mkdir(parents=True, exist_ok=True)
        hash_entries = []
        for relative_path in M05D_HASHED_PATHS:
            source_path = root / relative_path
            normalized = source_path.read_bytes().replace(b"\r\n", b"\n")
            digest = hashlib.sha256(normalized).hexdigest().upper()
            hash_entries.append(f"{digest}\t{relative_path}")
        (architecture_root / M05D_HASH_SNAPSHOT).write_text(
            "\n".join(hash_entries) + "\n",
            encoding="utf-8",
        )
        (architecture_root / M05D_DIRECT_CONSUMER_SNAPSHOT).write_text(
            "\n".join(sorted(M05D_DIRECT_CONSUMER_PATHS)) + "\n",
            encoding="utf-8",
        )
        ownership = architecture_root / "package-ownership.toml"
        ownership.write_text(
            "schema_version = 1\n"
            "[[ownership]]\n"
            'id = "kiyori-platform"\n'
            'path = "app/src/main/java/com/kiyori/platform/**"\n'
            'owner = "kiyori-platform"\n'
            'sync_zone = "C"\n'
            'phase = "m05a3"\n'
            'allowed_import_roots = ["com.kiyori.platform"]\n'
            "[[ownership]]\n"
            'id = "kiyori-integration-operit"\n'
            'path = "app/src/main/java/com/kiyori/integration/operit/**"\n'
            'owner = "kiyori-operit-integration"\n'
            'sync_zone = "C"\n'
            'phase = "m04b"\n'
            'allowed_import_roots = [\n'
            '  "com.ai.assistance.operit",\n'
            '  "com.kiyori.integration.operit",\n'
            '  "com.kiyori.platform",\n'
            "]\n",
            encoding="utf-8",
        )
        return ownership

    def write_m05e_storage_paths_layout(self, root: Path) -> Path:
        def write(relative_path: str, text: str) -> Path:
            path = root / relative_path
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(text, encoding="utf-8")
            return path

        write(
            M05D_PLATFORM_PERMISSION_PATH,
            "package com.kiyori.platform.permission\n"
            "internal class KiyoriNotificationPermissionCapability\n",
        )
        write(
            M05E_KIYORI_PATHS_PATH,
            """package com.kiyori.platform.storage

import android.content.Context
import android.os.Environment
import java.io.File

object KiyoriPaths {
    private const val KIYORI_DIR_NAME = "Kiyori"
    private const val DOWNLOADS_COLLECTION_DIR_NAME = "Download"
    private const val PICTURES_COLLECTION_DIR_NAME = "Pictures"
    private const val CLEAN_ON_EXIT_DIR_NAME = "cleanOnExit"
    private const val PLUGINS_DIR_NAME = "plugins"
    private const val MCP_PLUGINS_DIR_NAME = "mcp_plugins"
    private const val BRIDGE_DIR_NAME = "bridge"
    private const val EXPORTS_DIR_NAME = "exports"
    private const val EXPORT_BROWSER_DIR_NAME = "browser"
    private const val EXPORT_USERSCRIPTS_DIR_NAME = "userscripts"
    private const val EXPORT_PLAYER_DIR_NAME = "player"
    private const val EXPORT_TOOLBOX_DIR_NAME = "toolbox"
    private const val EXPORT_AI_CONFIG_DIR_NAME = "ai-config"
    private const val EXPORT_CONVERSATION_AUDIT_DIR_NAME = "conversation-audit"
    private const val EXPORT_BACKUPS_DIR_NAME = "backups"
    private const val EXPORT_TOOLPKG_DIR_NAME = "toolpkg"
    private const val TOOLPKG_PUBLIC_DIR_NAME = "toolpkg"
    private const val TOOLPKG_PUBLIC_WORKSPACE_DIR_NAME = "public"
    private const val WORKSPACE_DIR_NAME = "workspace"
    private const val WORKFLOW_DIR_NAME = "workflow"
    private const val MODELS_DIR_NAME = "models"
    private const val MNN_MODELS_DIR_NAME = "mnn"
    private const val LLAMA_MODELS_DIR_NAME = "llama"
    private const val OUTPUT_IMAGES_DIR_NAME = "output images"
    private const val ERROR_DIR_NAME = "error"
    private const val TEST_DIR_NAME = "test"
    private const val WEBSESSION_DIR_NAME = "websession"
    private const val USERSCRIPTS_DIR_NAME = "userscripts"
    private const val SKILLS_DIR_NAME = "skills"
    private const val BROWSER_DIR_NAME = "browser"
    private const val BROWSER_DOWNLOADS_DIR_NAME = "downloads"
    private const val BACKUP_DIR_NAME = "backup"
    private const val RAW_SNAPSHOT_DIR_NAME = "raw_snapshot"
    private const val ROOM_DB_DIR_NAME = "room_db"
    private const val CHAT_DIR_NAME = "chat"
    private const val MEMORY_DIR_NAME = "memory"
    private const val MODEL_CONFIG_DIR_NAME = "model_config"
    private const val CHARACTER_CARDS_DIR_NAME = "character_cards"
    private const val PICTURES_MARKDOWN_DIR_NAME = "Markdown"
    private const val PICTURES_SHARED_DIR_NAME = "Shared"
    private const val PICTURES_AI_DIR_NAME = "AI"
    private const val INTERNAL_TOOLPKG_DIR_NAME = "toolpkg"
    private const val INTERNAL_TOOLPKG_VERSION_DIR_NAME = "v1"
    private const val INTERNAL_TOOLPKG_DATA_DIR_NAME = "data"
    private const val INTERNAL_TOOLPKG_GENERATIONS_DIR_NAME = "generations"
    private const val INTERNAL_TOOLPKG_MIGRATION_AUDIT_DIR_NAME = "migration-audit"
    private const val INTERNAL_TOOLPKG_ACTIVE_GENERATION_FILE_NAME = "active-generation.json"
    private const val INTERNAL_TOOLPKG_GENERATION_METADATA_FILE_NAME = "generation.json"
    private const val TOOLPKG_RUNTIME_DIR_NAME = "toolpkg-runtime"
    private const val TOOLPKG_RUNTIME_VERSION_DIR_NAME = "v1"
    private const val TOOLPKG_ARTIFACTS_DIR_NAME = "artifacts"
    private const val TOOLPKG_EXTRACTED_DIR_NAME = "extracted"
    private const val TOOLPKG_ACTIVE_DIR_NAME = "active"
    private const val TOOLPKG_AUDIT_DIR_NAME = "audit"
    private const val TOOLPKG_MARKET_DIR_NAME = "market"
    private const val TOOLPKG_BUILD_DIR_NAME = "toolpkg-build"
    private const val INTERNAL_LOGS_DIR_NAME = "logs"
    private const val INTERNAL_ERROR_DIR_NAME = "errors"
    private const val INTERNAL_BACKUP_STAGING_DIR_NAME = "backup-staging"
    private const val INTERNAL_CONVERSATION_AUDIT_DIR_NAME = "conversation-audit"
    private const val INTERNAL_CONVERSATION_AUDIT_VERSION_DIR_NAME = "v1"
    private const val INTERNAL_CONVERSATION_AUDIT_PAYLOADS_DIR_NAME = "payloads"
    private const val INTERNAL_CONVERSATION_AUDIT_STAGING_DIR_NAME = "staging"

    const val SHERPA_NCNN_MODELS_DIR_NAME = ".sherpa_ncnn_models"
    const val VECTOR_INDEX_DIR_NAME = ".vector_index"
    const val IMAGE_POOL_DIR_NAME = "image_pool"
    const val MEDIA_POOL_DIR_NAME = "media_pool"
    const val SKILL_REPO_ZIP_POOL_DIR_NAME = "skill_repo_zip_pool"

    fun downloadsDir(): File =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    fun kiyoriRootDir(): File = ensureDir(File(downloadsDir(), KIYORI_DIR_NAME))
    fun cleanOnExitDir(): File = ensureDir(File(kiyoriRootDir(), CLEAN_ON_EXIT_DIR_NAME))
    fun pluginsDir(): File = ensureDir(File(kiyoriRootDir(), PLUGINS_DIR_NAME))
    internal fun pluginConfigDirectoryName(pluginId: String): String {
        val trimmed = pluginId.trim()
        val safeBaseName =
            trimmed.replace(Regex("[invalid]"), "_").ifBlank { "plugin" }
        return if (safeBaseName == trimmed) {
            safeBaseName
        } else {
            "$safeBaseName-${Integer.toHexString(trimmed.hashCode())}"
        }
    }
    fun pluginConfigDir(pluginId: String): File =
        ensureDir(File(pluginsDir(), pluginConfigDirectoryName(pluginId)))
    fun cleanOnExitInternalDir(context: Context): File =
        ensureDir(File(ensureDir(File(context.cacheDir, KIYORI_DIR_NAME)), CLEAN_ON_EXIT_DIR_NAME))
    fun mcpPluginsDir(): File = ensureDir(File(kiyoriRootDir(), MCP_PLUGINS_DIR_NAME))
    fun bridgeDir(): File = ensureDir(File(kiyoriRootDir(), BRIDGE_DIR_NAME))
    fun exportsDir(): File = ensureDir(File(kiyoriRootDir(), EXPORTS_DIR_NAME))
    fun conversationAuditExportsDir(): File =
        ensureDir(File(exportsDir(), EXPORT_CONVERSATION_AUDIT_DIR_NAME))
    fun conversationAuditRootDir(context: Context): File =
        ensureDir(
            File(
                File(File(context.filesDir, KIYORI_DIR_NAME), INTERNAL_CONVERSATION_AUDIT_DIR_NAME),
                INTERNAL_CONVERSATION_AUDIT_VERSION_DIR_NAME,
            ),
        )
    fun conversationAuditPayloadsDir(context: Context): File =
        ensureDir(
            File(
                conversationAuditRootDir(context),
                INTERNAL_CONVERSATION_AUDIT_PAYLOADS_DIR_NAME,
            ),
        )
    fun conversationAuditStagingDir(context: Context): File =
        ensureDir(
            File(
                conversationAuditRootDir(context),
                INTERNAL_CONVERSATION_AUDIT_STAGING_DIR_NAME,
            ),
        )
    fun exportDir(location: KiyoriPublicLocation): File {
        val projection = publicProjection(location)
        require(projection.collection == KiyoriPublicCollection.DOWNLOADS)
        return ensureDir(File(downloadsDir(), projection.relativePath.removePrefix("Download/")))
    }
    fun workspaceDir(): File = ensureDir(File(kiyoriRootDir(), WORKSPACE_DIR_NAME))
    fun workflowDir(): File = ensureDir(File(kiyoriRootDir(), WORKFLOW_DIR_NAME))
    fun mnnModelsDir(): File =
        ensureDir(File(File(kiyoriRootDir(), MODELS_DIR_NAME), MNN_MODELS_DIR_NAME))
    fun llamaModelsDir(): File =
        ensureDir(File(File(kiyoriRootDir(), MODELS_DIR_NAME), LLAMA_MODELS_DIR_NAME))
    fun outputImagesDir(): File = ensureDir(File(kiyoriRootDir(), OUTPUT_IMAGES_DIR_NAME))
    fun errorDir(): File = ensureDir(File(kiyoriRootDir(), ERROR_DIR_NAME))
    fun testDir(): File = ensureDir(File(kiyoriRootDir(), TEST_DIR_NAME))
    fun webSessionDir(): File = ensureDir(File(kiyoriRootDir(), WEBSESSION_DIR_NAME))
    fun skillsDir(): File = ensureDir(File(kiyoriRootDir(), SKILLS_DIR_NAME))
    fun webSessionUserscriptsDir(): File =
        ensureDir(File(webSessionDir(), USERSCRIPTS_DIR_NAME))
    fun privateWebSessionUserscriptsDir(context: Context): File =
        ensureDir(File(File(context.filesDir, WEBSESSION_DIR_NAME), USERSCRIPTS_DIR_NAME))
    fun browserDownloadsDir(): File =
        ensureDir(File(File(kiyoriRootDir(), BROWSER_DIR_NAME), BROWSER_DOWNLOADS_DIR_NAME))
    fun browserApplicationDownloadsDir(context: Context): File {
        val externalDownloads =
            requireNotNull(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS))
        return ensureDir(File(externalDownloads, BROWSER_DIR_NAME))
    }
    fun publicProjection(location: KiyoriPublicLocation): KiyoriPublicPathProjection {
        return when (location) {
            KiyoriPublicLocation.BROWSER_DOWNLOADS ->
                downloadProjection(BROWSER_DIR_NAME, BROWSER_DOWNLOADS_DIR_NAME)
            KiyoriPublicLocation.EXPORT_BROWSER ->
                downloadProjection(EXPORTS_DIR_NAME, EXPORT_BROWSER_DIR_NAME)
            KiyoriPublicLocation.EXPORT_USERSCRIPTS ->
                downloadProjection(EXPORTS_DIR_NAME, EXPORT_USERSCRIPTS_DIR_NAME)
            KiyoriPublicLocation.EXPORT_PLAYER ->
                downloadProjection(EXPORTS_DIR_NAME, EXPORT_PLAYER_DIR_NAME)
            KiyoriPublicLocation.EXPORT_TOOLBOX ->
                downloadProjection(EXPORTS_DIR_NAME, EXPORT_TOOLBOX_DIR_NAME)
            KiyoriPublicLocation.EXPORT_AI_CONFIG ->
                downloadProjection(EXPORTS_DIR_NAME, EXPORT_AI_CONFIG_DIR_NAME)
            KiyoriPublicLocation.EXPORT_BACKUPS ->
                downloadProjection(EXPORTS_DIR_NAME, EXPORT_BACKUPS_DIR_NAME)
            KiyoriPublicLocation.PICTURE_MARKDOWN ->
                pictureProjection(PICTURES_MARKDOWN_DIR_NAME)
            KiyoriPublicLocation.PICTURE_SHARED ->
                pictureProjection(PICTURES_SHARED_DIR_NAME)
            KiyoriPublicLocation.PICTURE_AI ->
                pictureProjection(PICTURES_AI_DIR_NAME)
        }
    }
    fun toolPkgPublicWorkspaceDir(packageKey: String): File {
        requirePackageKey(packageKey)
        return ensureDir(
            File(
                File(File(kiyoriRootDir(), TOOLPKG_PUBLIC_DIR_NAME), packageKey),
                TOOLPKG_PUBLIC_WORKSPACE_DIR_NAME,
            ),
        )
    }
    fun toolPkgExportDir(packageKey: String): File {
        requirePackageKey(packageKey)
        return ensureDir(File(File(exportsDir(), EXPORT_TOOLPKG_DIR_NAME), packageKey))
    }
    fun toolPkgPrivateRootDir(context: Context, packageKey: String): File {
        requirePackageKey(packageKey)
        return ensureDir(
            File(
                File(
                    File(context.noBackupFilesDir, KIYORI_DIR_NAME),
                    INTERNAL_TOOLPKG_DIR_NAME,
                ),
                "$INTERNAL_TOOLPKG_VERSION_DIR_NAME${File.separator}$packageKey",
            ),
        )
    }
    fun toolPkgPrivateDataDir(context: Context, packageKey: String): File =
        ensureDir(
            File(
                toolPkgPrivateRootDir(context, packageKey),
                INTERNAL_TOOLPKG_DATA_DIR_NAME,
            ),
        )
    fun toolPkgGenerationsDir(context: Context, packageKey: String): File =
        ensureDir(
            File(
                toolPkgPrivateRootDir(context, packageKey),
                INTERNAL_TOOLPKG_GENERATIONS_DIR_NAME,
            ),
        )
    fun toolPkgGenerationDir(
        context: Context,
        packageKey: String,
        generationId: String,
    ): File {
        requireSafePathSegment(generationId, "generationId")
        return File(toolPkgGenerationsDir(context, packageKey), generationId)
    }
    fun toolPkgGenerationDataDir(
        context: Context,
        packageKey: String,
        generationId: String,
    ): File =
        File(
            toolPkgGenerationDir(context, packageKey, generationId),
            INTERNAL_TOOLPKG_DATA_DIR_NAME,
        )
    fun toolPkgGenerationMetadataFile(
        context: Context,
        packageKey: String,
        generationId: String,
    ): File =
        File(
            toolPkgGenerationDir(context, packageKey, generationId),
            INTERNAL_TOOLPKG_GENERATION_METADATA_FILE_NAME,
        )
    fun toolPkgMigrationAuditDir(context: Context, packageKey: String): File =
        ensureDir(
            File(
                toolPkgPrivateRootDir(context, packageKey),
                INTERNAL_TOOLPKG_MIGRATION_AUDIT_DIR_NAME,
            ),
        )
    fun toolPkgMigrationAuditFile(
        context: Context,
        packageKey: String,
        generationId: String,
    ): File {
        requireSafePathSegment(generationId, "generationId")
        return File(toolPkgMigrationAuditDir(context, packageKey), "$generationId.json")
    }
    fun toolPkgActiveGenerationFile(context: Context, packageKey: String): File =
        File(
            toolPkgPrivateRootDir(context, packageKey),
            INTERNAL_TOOLPKG_ACTIVE_GENERATION_FILE_NAME,
        )
    fun toolPkgCacheDir(context: Context, packageKey: String): File {
        requirePackageKey(packageKey)
        return ensureDir(
            File(
                File(File(context.cacheDir, KIYORI_DIR_NAME), INTERNAL_TOOLPKG_DIR_NAME),
                "$INTERNAL_TOOLPKG_VERSION_DIR_NAME${File.separator}$packageKey",
            ),
        )
    }
    fun toolPkgBuildTransactionDir(context: Context, transactionId: String): File {
        requireSafePathSegment(transactionId, "transactionId")
        return ensureDir(
            File(
                File(File(context.cacheDir, KIYORI_DIR_NAME), TOOLPKG_BUILD_DIR_NAME),
                transactionId,
            ),
        )
    }
    fun toolPkgRuntimeRootDir(context: Context): File =
        ensureDir(
            File(
                File(File(context.filesDir, KIYORI_DIR_NAME), TOOLPKG_RUNTIME_DIR_NAME),
                TOOLPKG_RUNTIME_VERSION_DIR_NAME,
            ),
        )
    fun toolPkgArtifactsDir(context: Context): File =
        ensureDir(File(toolPkgRuntimeRootDir(context), TOOLPKG_ARTIFACTS_DIR_NAME))
    fun toolPkgExtractedDir(context: Context): File =
        ensureDir(File(toolPkgRuntimeRootDir(context), TOOLPKG_EXTRACTED_DIR_NAME))
    fun toolPkgActiveDir(context: Context): File =
        ensureDir(File(toolPkgRuntimeRootDir(context), TOOLPKG_ACTIVE_DIR_NAME))
    fun toolPkgAuditDir(context: Context): File =
        ensureDir(File(toolPkgRuntimeRootDir(context), TOOLPKG_AUDIT_DIR_NAME))
    fun toolPkgMarketDir(context: Context, packageKey: String): File {
        requirePackageKey(packageKey)
        return ensureDir(
            File(File(toolPkgRuntimeRootDir(context), TOOLPKG_MARKET_DIR_NAME), packageKey),
        )
    }
    fun internalLogsDir(context: Context): File =
        ensureDir(File(File(context.filesDir, KIYORI_DIR_NAME), INTERNAL_LOGS_DIR_NAME))
    fun internalErrorsDir(context: Context): File =
        ensureDir(File(File(context.filesDir, KIYORI_DIR_NAME), INTERNAL_ERROR_DIR_NAME))
    fun internalBackupStagingDir(context: Context): File =
        ensureDir(
            File(
                File(context.filesDir, KIYORI_DIR_NAME),
                INTERNAL_BACKUP_STAGING_DIR_NAME,
            ),
        )
    fun sherpaNcnnModelsDir(context: Context): File =
        ensureDir(File(context.filesDir, SHERPA_NCNN_MODELS_DIR_NAME))
    fun vectorIndexDir(context: Context): File =
        ensureDir(File(context.filesDir, VECTOR_INDEX_DIR_NAME))
    fun imagePoolDir(baseDir: File): File = ensureDir(File(baseDir, IMAGE_POOL_DIR_NAME))
    fun mediaPoolDir(baseDir: File): File = ensureDir(File(baseDir, MEDIA_POOL_DIR_NAME))
    fun skillRepoZipPoolDir(baseDir: File): File =
        ensureDir(File(baseDir, SKILL_REPO_ZIP_POOL_DIR_NAME))
    fun rawSnapshotExcludedFilesTopLevelDirNames(): Set<String> =
        setOf(
            SHERPA_NCNN_MODELS_DIR_NAME,
            VECTOR_INDEX_DIR_NAME,
            IMAGE_POOL_DIR_NAME,
            MEDIA_POOL_DIR_NAME,
            SKILL_REPO_ZIP_POOL_DIR_NAME,
        )
    fun kiyoriRootPathSdcard(): String {
        return "/sdcard/Download/$KIYORI_DIR_NAME"
    }
    fun cleanOnExitPathSdcard(): String =
        "${kiyoriRootPathSdcard()}/$CLEAN_ON_EXIT_DIR_NAME"
    fun pluginsPathSdcard(): String =
        "${kiyoriRootPathSdcard()}/$PLUGINS_DIR_NAME"
    fun bridgePathSdcard(): String =
        "${kiyoriRootPathSdcard()}/$BRIDGE_DIR_NAME"
    fun exportsPathSdcard(): String =
        "${kiyoriRootPathSdcard()}/$EXPORTS_DIR_NAME"
    fun workspacePathSdcard(chatId: String): String =
        "${kiyoriRootPathSdcard()}/$WORKSPACE_DIR_NAME/$chatId"
    fun testPathSdcard(): String =
        "${kiyoriRootPathSdcard()}/$TEST_DIR_NAME"
    fun webSessionUserscriptsPathSdcard(): String =
        "${kiyoriRootPathSdcard()}/$WEBSESSION_DIR_NAME/$USERSCRIPTS_DIR_NAME"
    fun browserDownloadsRelativePath(): String =
        publicProjection(KiyoriPublicLocation.BROWSER_DOWNLOADS).relativePath
    fun backupRootDir(): File = backupRootDir(kiyoriRootDir())
    internal fun backupRootDir(rootDir: File): File =
        ensureDir(File(rootDir, BACKUP_DIR_NAME))
    fun rawSnapshotDir(): File = ensureDir(File(backupRootDir(), RAW_SNAPSHOT_DIR_NAME))
    fun roomDbDir(): File = ensureDir(File(backupRootDir(), ROOM_DB_DIR_NAME))
    fun chatDir(): File = ensureDir(File(backupRootDir(), CHAT_DIR_NAME))
    fun memoryDir(): File = ensureDir(File(backupRootDir(), MEMORY_DIR_NAME))
    fun modelConfigDir(): File = ensureDir(File(backupRootDir(), MODEL_CONFIG_DIR_NAME))
    fun characterCardsDir(): File =
        ensureDir(File(backupRootDir(), CHARACTER_CARDS_DIR_NAME))
    private fun downloadProjection(vararg segments: String): KiyoriPublicPathProjection =
        KiyoriPublicPathProjection(
            collection = KiyoriPublicCollection.DOWNLOADS,
            relativePath =
                listOf(DOWNLOADS_COLLECTION_DIR_NAME, KIYORI_DIR_NAME, *segments)
                    .joinToString("/"),
        )
    private fun pictureProjection(vararg segments: String): KiyoriPublicPathProjection =
        KiyoriPublicPathProjection(
            collection = KiyoriPublicCollection.PICTURES,
            relativePath =
                listOf(PICTURES_COLLECTION_DIR_NAME, KIYORI_DIR_NAME, *segments)
                    .joinToString("/"),
        )
    private fun requirePackageKey(packageKey: String) {
        requireSafePathSegment(packageKey, "packageKey")
    }
    private fun requireSafePathSegment(value: String, label: String) {
        require(value.isNotBlank())
        require(value == value.trim())
        require(value != "." && value != "..")
        require(value.none { it == '/' || it == '\\' || it.code < 32 })
    }
    private fun ensureDir(dir: File): File {
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }
}

enum class KiyoriPublicCollection {
    DOWNLOADS,
    PICTURES,
}

enum class KiyoriPublicLocation {
    BROWSER_DOWNLOADS,
    EXPORT_BROWSER,
    EXPORT_USERSCRIPTS,
    EXPORT_PLAYER,
    EXPORT_TOOLBOX,
    EXPORT_AI_CONFIG,
    EXPORT_BACKUPS,
    PICTURE_MARKDOWN,
    PICTURE_SHARED,
    PICTURE_AI,
}

data class KiyoriPublicPathProjection(
    val collection: KiyoriPublicCollection,
    val relativePath: String,
)
""",
        )
        write(
            M05E_KIYORI_BACKUP_PATHS_PATH,
            """package com.kiyori.platform.storage

import java.io.File

object KiyoriBackupPaths {
    fun kiyoriRootDir(): File = KiyoriPaths.kiyoriRootDir()
    fun backupRootDir(): File = KiyoriPaths.backupRootDir()
    fun rawSnapshotDir(): File = KiyoriPaths.rawSnapshotDir()
    fun roomDbDir(): File = KiyoriPaths.roomDbDir()
    fun chatDir(): File = KiyoriPaths.chatDir()
    fun memoryDir(): File = KiyoriPaths.memoryDir()
    fun modelConfigDir(): File = KiyoriPaths.modelConfigDir()
    fun characterCardsDir(): File = KiyoriPaths.characterCardsDir()
}
""",
        )
        write(
            M05E_OPERIT_PATHS_PATH,
            f"""package com.ai.assistance.operit.util

import android.content.Context
import {M05E_KIYORI_PATHS_IMPORT}
import java.io.File

object OperitPaths {{
    const val SHERPA_NCNN_MODELS_DIR_NAME = KiyoriPaths.SHERPA_NCNN_MODELS_DIR_NAME
    const val VECTOR_INDEX_DIR_NAME = KiyoriPaths.VECTOR_INDEX_DIR_NAME
    const val IMAGE_POOL_DIR_NAME = KiyoriPaths.IMAGE_POOL_DIR_NAME
    const val MEDIA_POOL_DIR_NAME = KiyoriPaths.MEDIA_POOL_DIR_NAME
    const val SKILL_REPO_ZIP_POOL_DIR_NAME = KiyoriPaths.SKILL_REPO_ZIP_POOL_DIR_NAME
    fun downloadsDir(): File = KiyoriPaths.downloadsDir()
    fun kiyoriRootDir(): File = KiyoriPaths.kiyoriRootDir()
    fun cleanOnExitDir(): File = KiyoriPaths.cleanOnExitDir()
    fun pluginsDir(): File = KiyoriPaths.pluginsDir()
    fun pluginConfigDir(pluginId: String): File = KiyoriPaths.pluginConfigDir(pluginId)
    fun cleanOnExitInternalDir(context: Context): File =
        KiyoriPaths.cleanOnExitInternalDir(context)
    fun mcpPluginsDir(): File = KiyoriPaths.mcpPluginsDir()
    fun bridgeDir(): File = KiyoriPaths.bridgeDir()
    fun exportsDir(): File = KiyoriPaths.exportsDir()
    fun workspaceDir(): File = KiyoriPaths.workspaceDir()
    fun workflowDir(): File = KiyoriPaths.workflowDir()
    fun mnnModelsDir(): File = KiyoriPaths.mnnModelsDir()
    fun llamaModelsDir(): File = KiyoriPaths.llamaModelsDir()
    fun outputImagesDir(): File = KiyoriPaths.outputImagesDir()
    fun errorDir(): File = KiyoriPaths.errorDir()
    fun testDir(): File = KiyoriPaths.testDir()
    fun webSessionDir(): File = KiyoriPaths.webSessionDir()
    fun skillsDir(): File = KiyoriPaths.skillsDir()
    fun webSessionUserscriptsDir(): File = KiyoriPaths.webSessionUserscriptsDir()
    fun privateWebSessionUserscriptsDir(context: Context): File =
        KiyoriPaths.privateWebSessionUserscriptsDir(context)
    fun browserDownloadsDir(): File = KiyoriPaths.browserDownloadsDir()
    fun sherpaNcnnModelsDir(context: Context): File =
        KiyoriPaths.sherpaNcnnModelsDir(context)
    fun vectorIndexDir(context: Context): File = KiyoriPaths.vectorIndexDir(context)
    fun imagePoolDir(baseDir: File): File = KiyoriPaths.imagePoolDir(baseDir)
    fun mediaPoolDir(baseDir: File): File = KiyoriPaths.mediaPoolDir(baseDir)
    fun skillRepoZipPoolDir(baseDir: File): File =
        KiyoriPaths.skillRepoZipPoolDir(baseDir)
    fun rawSnapshotExcludedFilesTopLevelDirNames(): Set<String> =
        KiyoriPaths.rawSnapshotExcludedFilesTopLevelDirNames()
    fun kiyoriRootPathSdcard(): String = KiyoriPaths.kiyoriRootPathSdcard()
    fun cleanOnExitPathSdcard(): String = KiyoriPaths.cleanOnExitPathSdcard()
    fun pluginsPathSdcard(): String = KiyoriPaths.pluginsPathSdcard()
    fun bridgePathSdcard(): String = KiyoriPaths.bridgePathSdcard()
    fun exportsPathSdcard(): String = KiyoriPaths.exportsPathSdcard()
    fun workspacePathSdcard(chatId: String): String =
        KiyoriPaths.workspacePathSdcard(chatId)
    fun testPathSdcard(): String = KiyoriPaths.testPathSdcard()
    fun webSessionUserscriptsPathSdcard(): String =
        KiyoriPaths.webSessionUserscriptsPathSdcard()
}}
""",
        )
        write(
            M05E_OPERIT_BACKUP_DIRS_PATH,
            f"""package com.ai.assistance.operit.data.backup

import {M05E_KIYORI_BACKUP_PATHS_IMPORT}
import java.io.File

object OperitBackupDirs {{
    fun kiyoriRootDir(): File = KiyoriBackupPaths.kiyoriRootDir()
    fun backupRootDir(): File = KiyoriBackupPaths.backupRootDir()
    fun rawSnapshotDir(): File = KiyoriBackupPaths.rawSnapshotDir()
    fun roomDbDir(): File = KiyoriBackupPaths.roomDbDir()
    fun chatDir(): File = KiyoriBackupPaths.chatDir()
    fun memoryDir(): File = KiyoriBackupPaths.memoryDir()
    fun modelConfigDir(): File = KiyoriBackupPaths.modelConfigDir()
    fun characterCardsDir(): File = KiyoriBackupPaths.characterCardsDir()
}}
""",
        )

        owner_paths = {
            M05E_KIYORI_PATHS_PATH,
            M05E_KIYORI_BACKUP_PATHS_PATH,
            M05E_OPERIT_PATHS_PATH,
            M05E_OPERIT_BACKUP_DIRS_PATH,
        }
        direct_path_paths = set(M05E_DIRECT_PATH_CONSUMER_PATHS)
        direct_backup_paths = set(M05E_DIRECT_BACKUP_CONSUMER_PATHS)
        legacy_path_paths = set(M05E_LEGACY_OPERIT_CONSUMER_PATHS)
        for relative_path in sorted(
            (
                direct_path_paths
                | direct_backup_paths
                | legacy_path_paths
            )
            - owner_paths
        ):
            imports = []
            body = []
            if relative_path in direct_path_paths:
                imports.append(f"import {M05E_KIYORI_PATHS_IMPORT}")
                body.append(
                    "val kiyoriPath = KiyoriPaths.kiyoriRootPathSdcard()"
                )
            if relative_path in direct_backup_paths:
                imports.append(f"import {M05E_KIYORI_BACKUP_PATHS_IMPORT}")
                body.append(
                    "val backupPath = KiyoriBackupPaths.backupRootDir()"
                )
            if relative_path in legacy_path_paths:
                imports.append(f"import {M05E_OPERIT_PATHS_IMPORT}")
                body.append(
                    "val legacyPath = OperitPaths.kiyoriRootPathSdcard()"
                )
            write(
                relative_path,
                "package com.ai.assistance.operit.placeholder\n"
                + "\n".join(imports)
                + "\n"
                + "\n".join(body)
                + "\n",
            )

        write(
            M03_APPLICATION_PATH,
            """package com.kiyori.app

import com.ai.assistance.showerclient.ShowerEnvironment
import com.kiyori.platform.storage.KiyoriPaths

class KiyoriApplication {
    fun configureShowerEnvironment() {
        ShowerEnvironment.stagingDirectoryProvider = KiyoriPaths::kiyoriRootDir
    }
}
""",
        )

        write(
            M05E_PATHS_TEST_PATH,
            """package com.kiyori.platform.storage

class KiyoriPathsTest {
    fun `public sdcard paths keep exact Kiyori layout`() {
        KiyoriPaths.kiyoriRootPathSdcard()
    }
    fun `raw snapshot exclusions keep exact names`() {
        KiyoriPaths.rawSnapshotExcludedFilesTopLevelDirNames()
    }
    fun `plugin directory names keep existing sanitization`() {
        KiyoriPaths.pluginConfigDirectoryName("plugin")
    }
    fun `backup directories keep exact hierarchy`() {
        KiyoriPaths.backupRootDir(java.io.File("root"))
    }
    fun `base directory pools keep exact names`() {
        KiyoriPaths.imagePoolDir(java.io.File("root"))
    }
}
""",
        )
        write(
            M05E_ARCHITECTURE_TEST_PATH,
            "def test_m05e_storage_paths_accept_unique_owner_and_facades():\n"
            "    pass\n"
            "def test_m05e_storage_paths_reject_duplicate_calculation_or_consumer_drift():\n"
            "    pass\n",
        )

        architecture_root = root / "config/architecture"
        architecture_root.mkdir(parents=True, exist_ok=True)
        hash_entries = []
        for relative_path in M05E_HASHED_PATHS:
            source_path = root / relative_path
            normalized = source_path.read_bytes().replace(b"\r\n", b"\n")
            digest = hashlib.sha256(normalized).hexdigest().upper()
            hash_entries.append(f"{digest}\t{relative_path}")
        (architecture_root / M05E_HASH_SNAPSHOT).write_text(
            "\n".join(hash_entries) + "\n",
            encoding="utf-8",
        )
        for snapshot_name, paths in (
            (
                M05E_DIRECT_PATH_CONSUMER_SNAPSHOT,
                M05E_DIRECT_PATH_CONSUMER_PATHS,
            ),
            (
                M05E_DIRECT_BACKUP_CONSUMER_SNAPSHOT,
                M05E_DIRECT_BACKUP_CONSUMER_PATHS,
            ),
            (
                M05E_LEGACY_OPERIT_CONSUMER_SNAPSHOT,
                M05E_LEGACY_OPERIT_CONSUMER_PATHS,
            ),
        ):
            (architecture_root / snapshot_name).write_text(
                "\n".join(sorted(paths)) + "\n",
                encoding="utf-8",
            )
        ownership = architecture_root / "package-ownership.toml"
        ownership.write_text(
            "schema_version = 1\n"
            "[[ownership]]\n"
            'id = "kiyori-platform"\n'
            'path = "app/src/main/java/com/kiyori/platform/**"\n'
            'owner = "kiyori-platform"\n'
            'sync_zone = "C"\n'
            'phase = "m05a3"\n'
            'allowed_import_roots = [\n'
            '  "com.kiyori.capability",\n'
            '  "com.kiyori.platform",\n'
            "]\n",
            encoding="utf-8",
        )
        return ownership

    def test_m02_gate_is_inactive_before_platform_contracts_exist(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            errors: list[str] = []
            check_m02_application_access(Path(directory), errors)
            self.assertEqual(errors, [])

    def test_m02_gate_accepts_closed_narrow_contract_layout(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write_m02_contract_layout(root)
            errors: list[str] = []
            check_m02_application_access(root, errors)
            self.assertEqual(errors, [])

    def test_m02_gate_rejects_missing_contract_and_concrete_consumer(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write_m02_contract_layout(root)
            (
                root
                / "app/src/main/java/com/kiyori/platform/serialization/ApplicationJson.kt"
            ).unlink()
            consumer = (
                root
                / "app/src/main/java/com/ai/assistance/operit/data/Consumer.kt"
            )
            consumer.parent.mkdir(parents=True, exist_ok=True)
            consumer.write_text(
                "package com.ai.assistance.operit.data\n"
                "import com.ai.assistance.operit.core.application.KiyoriApplication\n"
                "val app = KiyoriApplication.instance\n",
                encoding="utf-8",
            )
            errors: list[str] = []
            check_m02_application_access(root, errors)
            self.assertTrue(any("platform contract files missing" in error for error in errors))
            self.assertTrue(any("concrete Application dependency" in error for error in errors))

    def test_m02_gate_rejects_second_owner_and_service_locator(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write_m02_contract_layout(root)
            extra_owner = (
                root
                / "app/src/main/java/com/ai/assistance/operit/data/SecondOwner.kt"
            )
            extra_owner.parent.mkdir(parents=True, exist_ok=True)
            extra_owner.write_text(
                "package com.ai.assistance.operit.data\n"
                "import com.kiyori.platform.android.ApplicationContextAccess.installForProcess "
                "as installContext\n"
                "fun bind() = installContext(Unit)\n",
                encoding="utf-8",
            )
            locator = (
                root
                / "app/src/main/java/com/kiyori/platform/android/Unsafe.kt"
            )
            locator.write_text(
                "package com.kiyori.platform.android\n"
                "object ServiceLocator\n",
                encoding="utf-8",
            )
            errors: list[str] = []
            check_m02_application_access(root, errors)
            self.assertTrue(any("process installer import outside" in error for error in errors))
            self.assertTrue(any("resembles a Service Locator" in error for error in errors))

    def test_m02_gate_rejects_deprecated_application_globals(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write_m02_contract_layout(root)
            application = root / M01_NEW_PATH
            application.write_text(
                application.read_text(encoding="utf-8").replace(
                    "class KiyoriApplication : MainApplicationInitialization {\n",
                    "class KiyoriApplication : MainApplicationInitialization {\n"
                    "    lateinit var instance: KiyoriApplication\n",
                ),
                encoding="utf-8",
            )
            errors: list[str] = []
            check_m02_application_access(root, errors)
            self.assertTrue(any("deprecated global member: instance" in error for error in errors))

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

    def test_manifest_snapshot_switches_only_the_application_for_m01_and_m03(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            snapshot = Path(directory) / "manifest-components.txt"
            snapshot.write_text(
                "application\t.core.application.OperitApplication"
                "\t.core.application.KiyoriApplication"
                "\tcom.kiyori.app.KiyoriApplication\n"
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
            self.assertIn(
                ("application", "com.kiyori.app.KiyoriApplication"),
                expected_manifest_components(snapshot, "m03"),
            )

    def test_manifest_components_are_extracted_exactly(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            manifest = Path(directory) / "AndroidManifest.xml"
            manifest.write_text(
                '<manifest xmlns:android="http://schemas.android.com/apk/res/android" '
                'xmlns:tools="http://schemas.android.com/tools">'
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
                '<activity android:name=".RemovedLauncher" tools:node="remove" />'
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

    def test_debug_manifest_contract_is_checked_separately_from_main(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            architecture_root = root / "config/architecture"
            architecture_root.mkdir(parents=True)
            manifest = root / "app/src/debug/AndroidManifest.xml"
            manifest.parent.mkdir(parents=True)
            manifest.write_text(
                '<manifest xmlns:android="http://schemas.android.com/apk/res/android">'
                "<application>"
                '<receiver android:name=".DebugReceiver" android:exported="true" '
                'android:permission="android.permission.DUMP">'
                "<intent-filter>"
                '<action android:name="com.example.DEBUG" />'
                "</intent-filter>"
                "</receiver>"
                "</application></manifest>",
                encoding="utf-8",
            )
            (architecture_root / "debug-manifest-components.txt").write_text(
                "receiver\t.DebugReceiver\n"
                "action\tcom.example.DEBUG\n"
                "permission\tandroid.permission.DUMP\n",
                encoding="utf-8",
            )
            digest = manifest_semantic_hash(manifest)
            (architecture_root / "debug-manifest-structure-hashes.txt").write_text(
                f"debug\t{digest}\n",
                encoding="utf-8",
            )

            errors: list[str] = []
            check_debug_manifest(root, architecture_root, errors)
            self.assertEqual(errors, [])

            manifest.write_text(
                manifest.read_text(encoding="utf-8").replace(
                    'android:exported="true"',
                    'android:exported="false"',
                ),
                encoding="utf-8",
            )
            check_debug_manifest(root, architecture_root, errors)
            self.assertEqual(len(errors), 1)
            self.assertTrue(
                errors[0].startswith(
                    "ARCH008 debug manifest semantic structure changed:"
                )
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
                f"baseline\t{digest}\nm01\t{digest}\n"
                f"post-m01\t{digest}\nm03\t{digest}\n",
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

    def test_m03_gate_accepts_a_package_only_application_move(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            architecture_root = self.write_m03_move_layout(root)
            generated_source = (
                root
                / "app/src/main/cpp/.cxx/operit_deps/generated/StaleReference.cpp"
            )
            generated_source.parent.mkdir(parents=True, exist_ok=True)
            generated_source.write_text(
                "const char* generated = "
                '"com.ai.assistance.operit.core.application.KiyoriApplication";\n',
                encoding="utf-8",
            )
            errors: list[str] = []
            check_m03_application_move(root, architecture_root, errors)
            self.assertEqual(errors, [])

    def test_m03_gate_rejects_source_import_lint_and_fqcn_drift(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            architecture_root = self.write_m03_move_layout(root)
            application = root / M03_APPLICATION_PATH
            application.write_text(
                application.read_text(encoding="utf-8").replace(
                    "import com.ai.assistance.operit.util.AppLogger\n",
                    "import com.ai.assistance.operit.util.AppLogger\n"
                    "import com.ai.assistance.operit.util.LocaleUtils\n",
                )
                + "val implementationDrift = true\n",
                encoding="utf-8",
            )
            lint_baseline = root / "app/lint-baseline.xml"
            lint_baseline.write_text(
                lint_baseline.read_text(encoding="utf-8").replace(
                    M03_LINT_NEW_PATH,
                    M03_LINT_OLD_PATH,
                    1,
                ),
                encoding="utf-8",
            )
            stale_runtime = root / "app/src/main/java/com/example/StaleReference.kt"
            stale_runtime.parent.mkdir(parents=True, exist_ok=True)
            stale_runtime.write_text(
                "package com.example\n"
                "val applicationClass = "
                "com.ai.assistance.operit.core.application.KiyoriApplication::class\n",
                encoding="utf-8",
            )
            errors: list[str] = []
            check_m03_application_move(root, architecture_root, errors)
            self.assertTrue(any("non-package change" in error for error in errors))
            self.assertTrue(
                any("unexpected transitional Application import" in error for error in errors)
            )
            self.assertTrue(any("lint baseline paths" in error for error in errors))
            self.assertTrue(any("old Application FQCN remains" in error for error in errors))

    def test_m03_gate_rejects_reintroduced_pruned_application_lint_record(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            architecture_root = self.write_m03_move_layout(root)
            lint_baseline = root / "app/lint-baseline.xml"
            lint_baseline.write_text(
                lint_baseline.read_text(encoding="utf-8")
                + f'\n<location file="{M03_LINT_NEW_PATH}"/>\n',
                encoding="utf-8",
            )
            errors: list[str] = []
            check_m03_application_move(root, architecture_root, errors)
            self.assertEqual(
                errors,
                [
                    "ARCH018 current lint baseline paths must be old=0/new="
                    f"{M03_LINT_PATH_COUNT}, found 0/{M03_LINT_PATH_COUNT + 1}"
                ],
            )

    def test_m04_gate_accepts_one_operit_host_composition_local_owner(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            host_locals = root / M04_HOST_LOCALS_PATH
            host_locals.parent.mkdir(parents=True)
            host_locals.write_text(
                "package com.ai.assistance.operit.ui.main.navigation\n\n"
                "val LocalTopBarActions = compositionLocalOf<Any> { Unit }\n"
                "val LocalOpenBrowser = compositionLocalOf<Any> { Unit }\n"
                "class TopBarTitleContent\n"
                "val LocalTopBarTitleContent = compositionLocalOf<Any> { Unit }\n"
                "val LocalAppNavigationModel = compositionLocalOf<Any> { Unit }\n",
                encoding="utf-8",
            )
            root_composable = root / M04_OLD_ROOT_PATH
            root_composable.parent.mkdir(parents=True, exist_ok=True)
            root_composable.write_text(
                "package com.ai.assistance.operit.ui.main\n"
                "import com.ai.assistance.operit.ui.main.navigation.LocalTopBarActions\n"
                "fun OperitApp() = LocalTopBarActions\n",
                encoding="utf-8",
            )
            errors: list[str] = []
            check_m04_root_composition(root, errors)
            self.assertEqual(errors, [])

    def test_m04_gate_rejects_duplicate_owner_and_stale_root_import(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            host_locals = root / M04_HOST_LOCALS_PATH
            host_locals.parent.mkdir(parents=True)
            host_locals.write_text(
                "package com.ai.assistance.operit.ui.main.navigation\n\n"
                "val LocalTopBarActions = compositionLocalOf<Any> { Unit }\n"
                "val LocalOpenBrowser = compositionLocalOf<Any> { Unit }\n"
                "class TopBarTitleContent\n"
                "val LocalTopBarTitleContent = compositionLocalOf<Any> { Unit }\n"
                "val LocalAppNavigationModel = compositionLocalOf<Any> { Unit }\n",
                encoding="utf-8",
            )
            root_composable = root / M04_OLD_ROOT_PATH
            root_composable.parent.mkdir(parents=True, exist_ok=True)
            root_composable.write_text(
                "package com.ai.assistance.operit.ui.main\n"
                "val LocalOpenBrowser = compositionLocalOf<Any> { Unit }\n"
                "fun OperitApp() = Unit\n",
                encoding="utf-8",
            )
            consumer = root / "app/src/main/java/com/example/Consumer.kt"
            consumer.parent.mkdir(parents=True)
            consumer.write_text(
                "package com.example\n"
                "import com.ai.assistance.operit.ui.main.LocalTopBarActions\n",
                encoding="utf-8",
            )
            errors: list[str] = []
            check_m04_root_composition(root, errors)
            self.assertTrue(any("must have one owner" in error for error in errors))
            self.assertTrue(any("stale root CompositionLocal import" in error for error in errors))
            self.assertTrue(any("still declares a CompositionLocal" in error for error in errors))

    def test_m04_gate_accepts_exact_kiyori_root_move(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write_m04_root_move_layout(root)
            errors: list[str] = []
            check_m04_root_composition(root, errors)
            self.assertEqual(errors, [])

    def test_m04_gate_rejects_root_move_drift_and_old_runtime_symbol(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            root_composable = self.write_m04_root_move_layout(root)
            root_composable.write_text(
                root_composable.read_text(encoding="utf-8")
                + "import com.ai.assistance.operit.util.AppLogger\n",
                encoding="utf-8",
            )
            old_root = root / M04_OLD_ROOT_PATH
            old_root.parent.mkdir(parents=True, exist_ok=True)
            old_root.write_text(
                "package com.ai.assistance.operit.ui.main\n"
                "fun OperitApp() = Unit\n",
                encoding="utf-8",
            )
            content_host = root / M04D_MAIN_CONTENT_HOST_PATH
            content_host.write_text(
                content_host.read_text(encoding="utf-8").replace(
                    "fun KiyoriMainContentHost() = KiyoriApp()\n",
                    "fun KiyoriMainContentHost() = Unit\n",
                ),
                encoding="utf-8",
            )
            errors: list[str] = []
            check_m04_root_composition(root, errors)
            self.assertTrue(any("old root Composable path remains" in error for error in errors))
            self.assertTrue(any("changed outside the approved move" in error for error in errors))
            self.assertTrue(
                any("unexpected transitional root Composable import" in error for error in errors)
            )
            self.assertTrue(any("host exactly one KiyoriApp call" in error for error in errors))
            self.assertTrue(any("old OperitApp runtime symbol remains" in error for error in errors))

    def test_m04b_gate_accepts_one_integration_policy_owner(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write_m04b_policy_layout(root)
            errors: list[str] = []
            check_m04b_operit_navigation_policy(root, errors)
            self.assertEqual(errors, [])

    def test_m04b_gate_rejects_policy_drift_and_shell_dependency(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            policy, shell_state = self.write_m04b_policy_layout(root)
            policy.write_text(
                policy.read_text(encoding="utf-8")
                + "fun unexpectedPolicy() = Unit\n",
                encoding="utf-8",
            )
            shell_state.write_text(
                shell_state.read_text(encoding="utf-8")
                + "import com.ai.assistance.operit.ui.main.navigation.RouteEntry\n"
                + "fun resolveAiTopBarMode() = Unit\n",
                encoding="utf-8",
            )
            root_composable = root / M04_ROOT_PATH
            root_composable.write_text(
                root_composable.read_text(encoding="utf-8").replace(
                    "import com.kiyori.integration.operit.navigation.resolveAiTopBarMode\n",
                    "import com.ai.assistance.operit.ui.main.shell.resolveAiTopBarMode\n",
                ),
                encoding="utf-8",
            )
            errors: list[str] = []
            check_m04b_operit_navigation_policy(root, errors)
            self.assertTrue(any("policy changed outside" in error for error in errors))
            self.assertTrue(any("Shell state changed outside" in error for error in errors))
            self.assertTrue(any("still imports Operit implementation" in error for error in errors))
            self.assertTrue(any("symbol must have one owner" in error for error in errors))
            self.assertTrue(any("must import the integration policy" in error for error in errors))
            self.assertTrue(any("still imports the old Shell policy owner" in error for error in errors))

    def test_m04b_gate_accepts_browser_contract_and_shell_state_owner(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write_m04b_shell_state_layout(root)
            errors: list[str] = []
            check_m04b_browser_exit_contract(root, errors)
            check_m04b_shell_state_owner(root, errors)
            self.assertEqual(errors, [])

    def test_m04b_gate_rejects_old_shell_state_and_bridge_drift(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            contract, _ = self.write_m04b_shell_state_layout(root)
            old_state = root / M04_OLD_SHELL_STATE_PATH
            old_state.parent.mkdir(parents=True, exist_ok=True)
            old_state.write_text(
                "package com.ai.assistance.operit.ui.main.shell\n"
                "data class KiyoriShellState(val old: Boolean)\n",
                encoding="utf-8",
            )
            contract.write_text(
                contract.read_text(encoding="utf-8")
                + "import com.example.Unexpected\n",
                encoding="utf-8",
            )
            app_shell = (
                root
                / "app/src/main/java/com/ai/assistance/operit/ui/main/shell/"
                "KiyoriAppShell.kt"
            )
            app_shell.write_text(
                app_shell.read_text(encoding="utf-8").replace(
                    "import com.kiyori.app.shell.SoftwareHomePage\n",
                    "",
                ),
                encoding="utf-8",
            )
            errors: list[str] = []
            check_m04b_browser_exit_contract(root, errors)
            check_m04b_shell_state_owner(root, errors)
            self.assertTrue(any("contract changed" in error for error in errors))
            self.assertTrue(any("must not import implementation" in error for error in errors))
            self.assertTrue(any("old Shell state path remains" in error for error in errors))
            self.assertTrue(any("symbol must have one owner" in error for error in errors))
            self.assertTrue(any("bridge imports differ" in error for error in errors))

    def test_m04b_gate_accepts_app_shell_package_only_move(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write_m04b_app_shell_layout(root)
            errors: list[str] = []
            check_m04b_app_shell_owner(root, errors)
            self.assertEqual(errors, [])

    def test_m04b_gate_rejects_app_shell_drift_and_old_owner(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            app_shell = self.write_m04b_app_shell_layout(root)
            app_shell.write_text(
                app_shell.read_text(encoding="utf-8")
                + "import com.kiyori.platform.lifecycle.ApplicationStartupTime\n",
                encoding="utf-8",
            )
            old_shell = (
                root
                / "app/src/main/java/com/ai/assistance/operit/ui/main/shell/"
                "KiyoriAppShell.kt"
            )
            old_shell.parent.mkdir(parents=True, exist_ok=True)
            old_shell.write_text(
                "package com.ai.assistance.operit.ui.main.shell\n"
                "fun KiyoriAppShell() = Unit\n",
                encoding="utf-8",
            )
            root_composable = root / M04_ROOT_PATH
            root_composable.write_text(
                root_composable.read_text(encoding="utf-8").replace(
                    "import com.kiyori.app.shell.KiyoriAppShell",
                    "import com.ai.assistance.operit.ui.main.shell.KiyoriAppShell",
                ),
                encoding="utf-8",
            )
            test_path = (
                root
                / "app/src/test/java/com/ai/assistance/operit/ui/main/shell/"
                "KiyoriShellStateTest.kt"
            )
            test_path.write_text(
                test_path.read_text(encoding="utf-8").replace(
                    "import com.kiyori.app.shell.shouldReverseKiyoriPagerDrag\n",
                    "",
                ),
                encoding="utf-8",
            )
            errors: list[str] = []
            check_m04b_app_shell_owner(root, errors)
            self.assertTrue(any("old App Shell path remains" in error for error in errors))
            self.assertTrue(any("changed outside" in error for error in errors))
            self.assertTrue(any("project imports differ" in error for error in errors))
            self.assertTrue(any("symbol must have one owner" in error for error in errors))
            self.assertTrue(any("must import KiyoriAppShell" in error for error in errors))
            self.assertTrue(any("still imports the old App Shell" in error for error in errors))
            self.assertTrue(any("test must import moved helper" in error for error in errors))

    def test_m04b_gate_accepts_ai_drawer_owner(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write_m04b_ai_drawer_layout(root)
            errors: list[str] = []
            check_m04b_ai_drawer_owner(root, errors)
            self.assertEqual(errors, [])

    def test_m04b_gate_rejects_ai_drawer_drift_and_old_owner(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            ai_drawer = self.write_m04b_ai_drawer_layout(root)
            ai_drawer.write_text(
                ai_drawer.read_text(encoding="utf-8")
                + "import com.kiyori.platform.lifecycle.ApplicationStartupTime\n",
                encoding="utf-8",
            )
            old_drawer = (
                root
                / "app/src/main/java/com/ai/assistance/operit/ui/main/shell/"
                "KiyoriAiDrawer.kt"
            )
            old_drawer.parent.mkdir(parents=True, exist_ok=True)
            old_drawer.write_text(
                "package com.ai.assistance.operit.ui.main.shell\n"
                "fun KiyoriModalAiDrawer() = Unit\n",
                encoding="utf-8",
            )
            app_shell = root / M04B_APP_SHELL_PATH
            app_shell.write_text(
                "package com.kiyori.app.shell\n"
                "import com.ai.assistance.operit.ui.main.shell.KiyoriModalAiDrawer\n"
                "fun hostDrawer() = Unit\n",
                encoding="utf-8",
            )
            test_path = (
                root
                / "app/src/test/java/com/ai/assistance/operit/ui/main/shell/"
                "KiyoriShellStateTest.kt"
            )
            test_path.write_text(
                "package com.ai.assistance.operit.ui.main.shell\n",
                encoding="utf-8",
            )
            errors: list[str] = []
            check_m04b_ai_drawer_owner(root, errors)
            self.assertTrue(any("old AI Drawer path remains" in error for error in errors))
            self.assertTrue(any("project imports differ" in error for error in errors))
            self.assertTrue(any("symbol must have one owner" in error for error in errors))
            self.assertTrue(any("still imports the old AI Drawer" in error for error in errors))
            self.assertTrue(any("must host exactly one" in error for error in errors))
            self.assertTrue(any("must import moved AI Drawer" in error for error in errors))

    def test_m04b_gate_accepts_primary_navigation_extraction(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write_m04b_primary_navigation_layout(root)
            errors: list[str] = []
            check_m04b_primary_navigation_owner(root, errors)
            self.assertEqual(errors, [])

    def test_m04b_gate_rejects_primary_navigation_drift_and_duplicate_owner(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            navigation = self.write_m04b_primary_navigation_layout(root)
            navigation.write_text(
                navigation.read_text(encoding="utf-8")
                + "import com.kiyori.platform.lifecycle.ApplicationStartupTime\n",
                encoding="utf-8",
            )
            old_pages = root / (
                "app/src/main/java/com/ai/assistance/operit/ui/main/shell/"
                "KiyoriShellPages.kt"
            )
            old_pages.parent.mkdir(parents=True, exist_ok=True)
            old_pages.write_text(
                "package com.ai.assistance.operit.ui.main.shell\n"
                "fun KiyoriBottomNavigation() = Unit\n",
                encoding="utf-8",
            )
            app_shell = root / M04B_APP_SHELL_PATH
            app_shell.write_text(
                "package com.kiyori.app.shell\n"
                "import com.ai.assistance.operit.ui.main.shell.KiyoriPrimaryRootPage\n"
                "fun hostPrimaryNavigation() { KiyoriPrimaryRootPage() }\n",
                encoding="utf-8",
            )
            test_path = (
                root
                / "app/src/test/java/com/ai/assistance/operit/ui/main/shell/"
                "KiyoriShellStateTest.kt"
            )
            test_path.write_text(
                "package com.ai.assistance.operit.ui.main.shell\n",
                encoding="utf-8",
            )
            errors: list[str] = []
            check_m04b_primary_navigation_owner(root, errors)
            self.assertTrue(any("project imports differ" in error for error in errors))
            self.assertTrue(any("symbol must have one owner" in error for error in errors))
            self.assertTrue(any("still imports the old primary" in error for error in errors))
            self.assertTrue(any("must host exactly one" in error for error in errors))
            self.assertTrue(any("must import primary navigation" in error for error in errors))

    def test_m04b_gate_accepts_software_home_extraction(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write_m04b_software_home_layout(root)
            errors: list[str] = []
            check_m04b_software_home_owner(root, errors)
            self.assertEqual(errors, [])

    def test_m04b_gate_rejects_software_home_drift_and_duplicate_owner(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            software_home = self.write_m04b_software_home_layout(root)
            software_home.write_text(
                software_home.read_text(encoding="utf-8")
                + "import com.kiyori.platform.lifecycle.ApplicationStartupTime\n",
                encoding="utf-8",
            )
            old_pages = root / (
                "app/src/main/java/com/ai/assistance/operit/ui/main/shell/"
                "KiyoriShellPages.kt"
            )
            old_pages.parent.mkdir(parents=True, exist_ok=True)
            old_pages.write_text(
                "package com.ai.assistance.operit.ui.main.shell\n"
                "fun KiyoriSoftwareHomePage() = Unit\n",
                encoding="utf-8",
            )
            app_shell = root / M04B_APP_SHELL_PATH
            app_shell.write_text(
                "package com.kiyori.app.shell\n"
                "import com.ai.assistance.operit.ui.main.shell.KiyoriSoftwareHomePage\n"
                "fun hostSoftwareHome() {\n"
                "    KiyoriSoftwareHomePage()\n"
                "    KiyoriSoftwareHomePage()\n"
                "}\n",
                encoding="utf-8",
            )
            test_path = root / (
                "app/src/test/java/com/ai/assistance/operit/ui/main/shell/"
                "KiyoriSoftwareHomeSearchTest.kt"
            )
            test_path.write_text(
                "package com.ai.assistance.operit.ui.main.shell\n",
                encoding="utf-8",
            )
            errors: list[str] = []
            check_m04b_software_home_owner(root, errors)
            self.assertTrue(any("project imports differ" in error for error in errors))
            self.assertTrue(any("symbol must have one owner" in error for error in errors))
            self.assertTrue(any("still imports the old Software Home" in error for error in errors))
            self.assertTrue(any("must host exactly one" in error for error in errors))
            self.assertTrue(any("must import moved symbol" in error for error in errors))

    def test_m04b_gate_accepts_browser_search_extraction(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write_m04b_browser_search_layout(root)
            errors: list[str] = []
            check_m04b_browser_search_owner(root, errors)
            self.assertEqual(errors, [])

    def test_m04b_gate_rejects_browser_search_drift_and_old_owner(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            browser_search = self.write_m04b_browser_search_layout(root)
            browser_search.write_text(
                browser_search.read_text(encoding="utf-8")
                + "import com.ai.assistance.operit.util.AppLogger\n",
                encoding="utf-8",
            )
            old_pages = root / (
                "app/src/main/java/com/ai/assistance/operit/ui/main/shell/"
                "KiyoriShellPages.kt"
            )
            old_pages.parent.mkdir(parents=True, exist_ok=True)
            old_pages.write_text(
                "package com.ai.assistance.operit.ui.main.shell\n"
                "data class KiyoriWebSearchRequest(val query: String)\n",
                encoding="utf-8",
            )
            app_shell = root / M04B_APP_SHELL_PATH
            app_shell.write_text(
                "package com.kiyori.app.shell\n"
                "import com.ai.assistance.operit.ui.main.shell.KiyoriFullScreenWebSearchPage\n"
                "import com.ai.assistance.operit.ui.main.shell.KiyoriWebSearchRequest\n",
                encoding="utf-8",
            )
            root_path = root / M04_ROOT_PATH
            root_path.write_text(
                "package com.kiyori.app\n"
                "import com.ai.assistance.operit.ui.main.shell.KiyoriWebSearchRequest\n"
                "import com.ai.assistance.operit.ui.main.shell.resolveKiyoriWebSearchRequest\n",
                encoding="utf-8",
            )
            test_path = root / (
                "app/src/test/java/com/ai/assistance/operit/ui/main/shell/"
                "KiyoriSoftwareHomeSearchTest.kt"
            )
            test_path.write_text(
                "package com.ai.assistance.operit.ui.main.shell\n",
                encoding="utf-8",
            )
            errors: list[str] = []
            check_m04b_browser_search_owner(root, errors)
            self.assertTrue(any("old path remains" in error for error in errors))
            self.assertTrue(any("source changed" in error for error in errors))
            self.assertTrue(any("project imports differ" in error for error in errors))
            self.assertTrue(any("symbol must have one owner" in error for error in errors))
            self.assertTrue(any("App Shell still imports the old" in error for error in errors))
            self.assertTrue(any("must host exactly one" in error for error in errors))
            self.assertTrue(any("KiyoriApp must import" in error for error in errors))
            self.assertTrue(any("KiyoriApp still imports old" in error for error in errors))
            self.assertTrue(any("test must import moved symbol" in error for error in errors))

    def test_m04c_gate_accepts_navigation_integration_extraction(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write_m04c_navigation_integration_layout(root)
            errors: list[str] = []
            check_m04c_navigation_integration(root, errors)
            self.assertEqual(errors, [])

    def test_m04c_gate_rejects_navigation_integration_drift_and_old_owner(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            integration = self.write_m04c_navigation_integration_layout(root)
            integration.write_text(
                integration.read_text(encoding="utf-8")
                + "import com.ai.assistance.operit.util.AppLogger\n",
                encoding="utf-8",
            )
            old_catalog = root / (
                "app/src/main/java/com/ai/assistance/operit/ui/main/navigation/"
                "AppRouteCatalog.kt"
            )
            old_catalog.parent.mkdir(parents=True, exist_ok=True)
            old_catalog.write_text(
                "package com.ai.assistance.operit.ui.main.navigation\n"
                "object AppRouteCatalog\n",
                encoding="utf-8",
            )
            catalog = root / M04C_ROUTE_CATALOG_PATH
            catalog.write_text(
                catalog.read_text(encoding="utf-8").replace(
                    "fun build(context: Context, packageManager: PackageManager) = Unit",
                    "fun build(context: Context) = PackageManager.getInstance(context, handler)",
                ),
                encoding="utf-8",
            )
            root_path = root / M04_ROOT_PATH
            root_path.write_text(
                "package com.kiyori.app\n"
                "import androidx.compose.runtime.DisposableEffect\n"
                "import com.ai.assistance.operit.core.tools.packTool.PackageManager\n"
                "import com.ai.assistance.operit.ui.main.navigation.AppRouteCatalog\n"
                "import com.ai.assistance.operit.ui.main.navigation.AppRouterGateway\n"
                "fun KiyoriApp() { AppRouterGateway.install(handler, reset) }\n",
                encoding="utf-8",
            )
            settings_test = root / M04C_SETTINGS_TEST_PATH
            settings_test.write_text(
                "package com.ai.assistance.operit.ui.main.shell\n"
                "import com.ai.assistance.operit.ui.main.navigation.AppRouteCatalog\n",
                encoding="utf-8",
            )
            errors: list[str] = []
            check_m04c_navigation_integration(root, errors)
            self.assertTrue(any("old route catalog path remains" in error for error in errors))
            self.assertTrue(any("source changed" in error for error in errors))
            self.assertTrue(any("project imports differ" in error for error in errors))
            self.assertTrue(any("symbol must have one owner" in error for error in errors))
            self.assertTrue(any("must receive the unique PackageManager" in error for error in errors))
            self.assertTrue(any("second PackageManager" in error for error in errors))
            self.assertTrue(any("still imports extracted" in error for error in errors))
            self.assertTrue(any("must import navigation integration" in error for error in errors))
            self.assertTrue(any("still owns navigation integration" in error for error in errors))
            self.assertTrue(any("must host navigation integration" in error for error in errors))
            self.assertTrue(any("settings test must import moved" in error for error in errors))
            self.assertTrue(any("settings test still imports old" in error for error in errors))

    def test_m04d_gate_accepts_unique_pending_request_owner(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write_m04d_main_pending_requests_layout(root)
            errors: list[str] = []
            check_m04d_main_pending_requests(root, errors)
            self.assertEqual(errors, [])

    def test_m04d_gate_rejects_owner_drift_and_second_state_owner(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            owner = self.write_m04d_main_pending_requests_layout(root)
            owner.write_text(
                owner.read_text(encoding="utf-8")
                + "import com.ai.assistance.operit.util.AppLogger\n"
                + "fun absorbHostSideEffect() = System.currentTimeMillis()\n",
                encoding="utf-8",
            )
            duplicate = (
                root
                / "app/src/main/java/com/ai/assistance/operit/ui/main/"
                "SecondPendingOwner.kt"
            )
            duplicate.parent.mkdir(parents=True, exist_ok=True)
            duplicate.write_text(
                "package com.ai.assistance.operit.ui.main\n"
                "class KiyoriMainPendingRequests\n",
                encoding="utf-8",
            )
            main_activity = root / M04_MAIN_ACTIVITY_PATH
            main_activity.write_text(
                main_activity.read_text(encoding="utf-8")
                .replace(
                    "import com.kiyori.app.startup.KiyoriMainPendingRequests\n",
                    "",
                )
                .replace(
                    "    private val pendingRequests = KiyoriMainPendingRequests()\n",
                    "    private var pendingBrowserUrl: String? = null\n",
                ),
                encoding="utf-8",
            )
            (root / M04D_MAIN_PENDING_REQUESTS_TEST_PATH).unlink()
            errors: list[str] = []
            check_m04d_main_pending_requests(root, errors)
            self.assertTrue(any("source changed" in error for error in errors))
            self.assertTrue(any("project imports differ" in error for error in errors))
            self.assertTrue(any("must have one owner" in error for error in errors))
            self.assertTrue(any("absorbed host side effects" in error for error in errors))
            self.assertTrue(any("must import" in error for error in errors))
            self.assertTrue(any("must hold exactly one" in error for error in errors))
            self.assertTrue(any("still declares" in error for error in errors))
            self.assertTrue(any("contract test missing" in error for error in errors))

    def test_m04d_intent_gate_accepts_side_effect_free_decoder(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write_m04d_main_intent_decoder_layout(root)
            errors: list[str] = []
            check_m04d_main_intent_decoder(root, errors)
            self.assertEqual(errors, [])

    def test_m04d_intent_gate_rejects_decoder_drift_and_host_parsing(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            decoder = self.write_m04d_main_intent_decoder_layout(root)
            decoder.write_text(
                decoder.read_text(encoding="utf-8")
                + "import com.ai.assistance.operit.util.AppLogger\n"
                + "fun hostSideEffect() = System.currentTimeMillis()\n",
                encoding="utf-8",
            )
            duplicate = (
                root
                / "app/src/main/java/com/ai/assistance/operit/ui/main/"
                "SecondIntentDecoder.kt"
            )
            duplicate.parent.mkdir(parents=True, exist_ok=True)
            duplicate.write_text(
                "package com.ai.assistance.operit.ui.main\n"
                "fun decodeKiyoriMainIntent() = Unit\n",
                encoding="utf-8",
            )
            main_activity = root / M04_MAIN_ACTIVITY_PATH
            main_activity.write_text(
                main_activity.read_text(encoding="utf-8")
                .replace(
                    "import com.kiyori.app.startup.decodeKiyoriMainIntent\n",
                    "",
                )
                .replace(
                    "    fun handle() = decodeKiyoriMainIntent()\n",
                    "    fun handle(intent: Intent) = intent.getStringExtra(\"route\")\n",
                ),
                encoding="utf-8",
            )
            (root / M04D_MAIN_INTENT_DECODER_TEST_PATH).unlink()
            errors: list[str] = []
            check_m04d_main_intent_decoder(root, errors)
            self.assertTrue(any("source changed" in error for error in errors))
            self.assertTrue(any("project imports differ" in error for error in errors))
            self.assertTrue(any("must have one owner" in error for error in errors))
            self.assertTrue(any("absorbed host side effects" in error for error in errors))
            self.assertTrue(any("must import" in error for error in errors))
            self.assertTrue(any("one decoder call" in error for error in errors))
            self.assertTrue(any("directly decodes" in error for error in errors))
            self.assertTrue(any("contract test missing" in error for error in errors))

    def test_m04d_display_gate_accepts_unique_display_coordinator(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write_m04d_main_display_coordinator_layout(root)
            errors: list[str] = []
            check_m04d_main_display_coordinator(root, errors)
            self.assertEqual(errors, [])

    def test_m04d_display_gate_rejects_drift_and_activity_display_owner(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            coordinator = self.write_m04d_main_display_coordinator_layout(root)
            coordinator.write_text(
                coordinator.read_text(encoding="utf-8")
                + "import com.ai.assistance.operit.data.SecondOwner\n"
                + "fun unrelated() = PlayerSession.getInstance(context)\n",
                encoding="utf-8",
            )
            duplicate = (
                root
                / "app/src/main/java/com/ai/assistance/operit/ui/main/"
                "SecondDisplayCoordinator.kt"
            )
            duplicate.parent.mkdir(parents=True, exist_ok=True)
            duplicate.write_text(
                "package com.ai.assistance.operit.ui.main\n"
                "object KiyoriMainDisplayCoordinator\n",
                encoding="utf-8",
            )
            main_activity = root / M04_MAIN_ACTIVITY_PATH
            main_activity.write_text(
                "package com.ai.assistance.operit.ui.main\n"
                "class MainActivity {\n"
                "    fun configureDisplaySettings() {\n"
                "        window.setSustainedPerformanceMode(true)\n"
                "    }\n"
                "}\n",
                encoding="utf-8",
            )
            (root / M04D_MAIN_DISPLAY_COORDINATOR_TEST_PATH).unlink()
            errors: list[str] = []
            check_m04d_main_display_coordinator(root, errors)
            self.assertTrue(any("source changed" in error for error in errors))
            self.assertTrue(any("project imports differ" in error for error in errors))
            self.assertTrue(any("must have one owner" in error for error in errors))
            self.assertTrue(any("unrelated host responsibility" in error for error in errors))
            self.assertTrue(any("must import" in error for error in errors))
            self.assertTrue(any("one coordinator call" in error for error in errors))
            self.assertTrue(any("still owns display" in error for error in errors))
            self.assertTrue(any("contract test missing" in error for error in errors))

    def test_m04d_shared_content_gate_accepts_unique_transfer_coordinator(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write_m04d_main_shared_content_coordinator_layout(root)
            errors: list[str] = []
            check_m04d_main_shared_content_coordinator(root, errors)
            self.assertEqual(errors, [])

    def test_m04d_shared_content_gate_rejects_drift_and_second_transfer_owner(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            coordinator = self.write_m04d_main_shared_content_coordinator_layout(root)
            coordinator.write_text(
                coordinator.read_text(encoding="utf-8")
                + "import com.ai.assistance.operit.data.SecondOwner\n"
                + "val state = MutableStateFlow<Unit?>(null)\n",
                encoding="utf-8",
            )
            duplicate = (
                root
                / "app/src/main/java/com/ai/assistance/operit/ui/main/"
                "SecondSharedCoordinator.kt"
            )
            duplicate.parent.mkdir(parents=True, exist_ok=True)
            duplicate.write_text(
                "package com.ai.assistance.operit.ui.main\n"
                "class KiyoriMainSharedContentCoordinator\n",
                encoding="utf-8",
            )
            main_activity = root / M04_MAIN_ACTIVITY_PATH
            main_activity.write_text(
                "package com.ai.assistance.operit.ui.main\n"
                "class MainActivity {\n"
                "    fun processPendingSharedFiles() {\n"
                "        SharedFileHandler.setSharedFiles(uris, text)\n"
                "    }\n"
                "}\n",
                encoding="utf-8",
            )
            (root / M04D_MAIN_SHARED_CONTENT_COORDINATOR_TEST_PATH).unlink()
            errors: list[str] = []
            check_m04d_main_shared_content_coordinator(root, errors)
            self.assertTrue(any("source changed" in error for error in errors))
            self.assertTrue(any("project imports differ" in error for error in errors))
            self.assertTrue(any("must have one owner" in error for error in errors))
            self.assertTrue(any("unrelated state" in error for error in errors))
            self.assertTrue(any("must import" in error for error in errors))
            self.assertTrue(any("must hold one" in error for error in errors))
            self.assertTrue(any("call count differs" in error for error in errors))
            self.assertTrue(any("still owns shared-content" in error for error in errors))
            self.assertTrue(any("contract test missing" in error for error in errors))

    def test_m04d_task_visibility_gate_accepts_unique_coordinator(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write_m04d_main_task_visibility_coordinator_layout(root)
            errors: list[str] = []
            check_m04d_main_task_visibility_coordinator(root, errors)
            self.assertEqual(errors, [])

    def test_m04d_task_visibility_gate_rejects_drift_and_activity_owner(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            coordinator = self.write_m04d_main_task_visibility_coordinator_layout(root)
            coordinator.write_text(
                coordinator.read_text(encoding="utf-8")
                + "import com.ai.assistance.operit.data.SecondOwner\n"
                + "val state = mutableStateOf(false)\n",
                encoding="utf-8",
            )
            duplicate = (
                root
                / "app/src/main/java/com/ai/assistance/operit/ui/main/"
                "SecondTaskVisibilityCoordinator.kt"
            )
            duplicate.parent.mkdir(parents=True, exist_ok=True)
            duplicate.write_text(
                "package com.ai.assistance.operit.ui.main\n"
                "object KiyoriMainTaskVisibilityCoordinator\n",
                encoding="utf-8",
            )
            main_activity = root / M04_MAIN_ACTIVITY_PATH
            main_activity.write_text(
                "package com.ai.assistance.operit.ui.main\n"
                "import android.app.ActivityManager\n"
                "class MainActivity {\n"
                "    fun restoreRuntimeTaskViewVisibilityIfNeeded() {\n"
                "        AIForegroundService.isRunning.get()\n"
                "        getSystemService(Context.ACTIVITY_SERVICE)\n"
                "        task.setExcludeFromRecents(false)\n"
                "    }\n"
                "}\n",
                encoding="utf-8",
            )
            (root / M04D_MAIN_TASK_VISIBILITY_COORDINATOR_TEST_PATH).unlink()
            errors: list[str] = []
            check_m04d_main_task_visibility_coordinator(root, errors)
            self.assertTrue(any("source changed" in error for error in errors))
            self.assertTrue(any("project imports differ" in error for error in errors))
            self.assertTrue(any("must have one owner" in error for error in errors))
            self.assertTrue(any("unrelated state" in error for error in errors))
            self.assertTrue(any("must import" in error for error in errors))
            self.assertTrue(any("call count differs" in error for error in errors))
            self.assertTrue(any("still owns runtime task" in error for error in errors))
            self.assertTrue(any("contract test missing" in error for error in errors))

    def test_m04d_orientation_gate_accepts_unique_state_and_presentation_owner(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write_m04d_main_orientation_coordinator_layout(root)
            errors: list[str] = []
            check_m04d_main_orientation_coordinator(root, errors)
            self.assertEqual(errors, [])

    def test_m04d_orientation_gate_rejects_drift_and_activity_owner(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            coordinator = self.write_m04d_main_orientation_coordinator_layout(root)
            coordinator.write_text(
                coordinator.read_text(encoding="utf-8")
                + "import com.ai.assistance.operit.data.SecondOwner\n"
                + "val flow = MutableStateFlow<Unit?>(null)\n",
                encoding="utf-8",
            )
            duplicate = (
                root
                / "app/src/main/java/com/ai/assistance/operit/ui/main/"
                "SecondOrientationCoordinator.kt"
            )
            duplicate.parent.mkdir(parents=True, exist_ok=True)
            duplicate.write_text(
                "package com.ai.assistance.operit.ui.main\n"
                "class KiyoriMainOrientationCoordinator\n",
                encoding="utf-8",
            )
            main_activity = root / M04_MAIN_ACTIVITY_PATH
            main_activity.write_text(
                "package com.ai.assistance.operit.ui.main\n"
                "import androidx.compose.material3.AlertDialog\n"
                "import androidx.compose.runtime.Composable\n"
                "class MainActivity {\n"
                "    private var showOrientationChangeDialog = false\n"
                "    private var lastOrientation: Int? = null\n"
                "    override fun onConfigurationChanged(newConfig: Configuration) {\n"
                "        lastOrientation = newConfig.orientation\n"
                "    }\n"
                "}\n"
                "@Composable\n"
                "fun OrientationChangeDialog() {\n"
                "    AlertDialog(onDismissRequest = {})\n"
                "}\n",
                encoding="utf-8",
            )
            (root / M04D_MAIN_ORIENTATION_COORDINATOR_TEST_PATH).unlink()
            errors: list[str] = []
            check_m04d_main_orientation_coordinator(root, errors)
            self.assertTrue(any("source changed" in error for error in errors))
            self.assertTrue(any("project imports differ" in error for error in errors))
            self.assertTrue(any("must have one owner" in error for error in errors))
            self.assertTrue(any("unrelated state" in error for error in errors))
            self.assertTrue(any("must import" in error for error in errors))
            self.assertTrue(any("still imports" in error for error in errors))
            self.assertTrue(any("call count differs" in error for error in errors))
            self.assertTrue(any("onConfigurationChanged" in error for error in errors))
            self.assertTrue(any("still owns orientation" in error for error in errors))
            self.assertTrue(any("contract test missing" in error for error in errors))

    def write_kiyori_first_run_layout(self, root: Path) -> None:
        files = {
            KIYORI_FIRST_RUN_CONTRACT_PATH: (
                "package com.kiyori.integration.operit.onboarding\n"
                "enum class KiyoriOnboardingStep { WELCOME, BROWSER_AND_MEDIA, "
                "AI_ASSISTANT, FILES_AND_TOOLS, AGREEMENT, PERMISSIONS }\n"
                "fun resolveInitialKiyoriOnboardingStep() = KiyoriOnboardingStep.AGREEMENT\n"
                "enum class KiyoriPermissionStatus { ON_DEMAND, NOT_APPLICABLE }\n"
                "class KiyoriPermissionSnapshot\n"
                "fun sanitizeKiyoriPermissionSelection() = emptySet<String>()\n"
            ),
            KIYORI_FIRST_RUN_PREFERENCES_PATH: (
                "package com.kiyori.integration.operit.onboarding\n"
                "class KiyoriOnboardingPreferences\n"
            ),
            KIYORI_FIRST_RUN_PERMISSIONS_PATH: (
                "package com.kiyori.integration.operit.onboarding\n"
                "Manifest.permission.POST_NOTIFICATIONS\n"
                "Manifest.permission.READ_MEDIA_AUDIO\n"
                "Manifest.permission.READ_MEDIA_VIDEO\n"
                "Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED\n"
                "Manifest.permission.CAMERA\n"
                "Manifest.permission.RECORD_AUDIO\n"
                "Manifest.permission.ACCESS_FINE_LOCATION\n"
                "Manifest.permission.ACCESS_COARSE_LOCATION\n"
                "Manifest.permission.BLUETOOTH_CONNECT\n"
                "Manifest.permission.BLUETOOTH_SCAN\n"
                "Manifest.permission.CALL_PHONE\n"
                "Manifest.permission.SEND_SMS\n"
                "Manifest.permission.READ_SMS\n"
                "Manifest.permission.RECEIVE_SMS\n"
                "Manifest.permission.READ_EXTERNAL_STORAGE\n"
                "Manifest.permission.WRITE_EXTERNAL_STORAGE\n"
                "Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION\n"
                "Settings.ACTION_MANAGE_OVERLAY_PERMISSION\n"
                "Settings.ACTION_MANAGE_WRITE_SETTINGS\n"
                "Settings.ACTION_USAGE_ACCESS_SETTINGS\n"
                "Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES\n"
                "Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS\n"
                "Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS\n"
                "Settings.ACTION_VOICE_INPUT_SETTINGS\n"
                "Settings.ACTION_ACCESSIBILITY_SETTINGS\n"
                "UIHierarchyManager.launchProviderInstall\n"
                "UIHierarchyManager.isUpdateNeeded\n"
                "ShizukuInstaller.installBundledShizuku\n"
                "ShizukuAuthorizer.requestShizukuPermission\n"
                "isKiyoriRuntimePermission\n"
            ),
            KIYORI_FIRST_RUN_SCREEN_PATH: (
                "package com.kiyori.integration.operit.onboarding\n"
                "ActivityResultContracts.RequestMultiplePermissions\n"
                "kiyoriRuntimePermissionsForSdk\n"
                "KiyoriOnboardingStep.WELCOME\n"
                "KiyoriOnboardingStep.BROWSER_AND_MEDIA\n"
                "KiyoriOnboardingStep.AI_ASSISTANT\n"
                "KiyoriOnboardingStep.FILES_AND_TOOLS\n"
                "KiyoriOnboardingStep.AGREEMENT\n"
                "KiyoriOnboardingStep.PERMISSIONS\n"
                "isKiyoriRuntimePermission\n"
                "sanitizeKiyoriPermissionSelection\n"
                "selectedPermissionIds\n"
                "authorizationActive\n"
                "KiyoriPermissionId.entries\n"
                "kiyoriPermissionGroups.forEach\n"
                "group.permissionIds.forEach\n"
                "R.string.onb_p6_summary\n"
                "RootAuthorizer.requestRootPermission\n"
                "KiyoriLegalDocument.USER_AGREEMENT\n"
                "KiyoriLegalDocument.PRIVACY_POLICY\n"
                "HorizontalPager(\n"
                "rememberPagerState(\n"
                "snapshotFlow { pagerState.settledPage }\n"
                "pagerState.animateScrollToPage\n"
                "PagerSnapDistance.atMost(1)\n"
                "userScrollEnabled = pagerInputEnabled\n"
                "shouldEnableKiyoriOnboardingPagerInput\n"
                "kiyoriOnboardingPageCount\n"
                "canNavigateKiyoriOnboarding\n"
                "onStopAuthorization\n"
                "authorizationNeedsContinue\n"
                "resolveKiyoriOnboardingTitleAlignment\n"
                "fun OnboardingProgressHeader() {\n"
                "R.string.kiyori_onboarding_progress\n"
                "step.ordinal + 1\n"
                "KiyoriOnboardingStep.entries.size\n"
                "KiyoriOnboardingStep.entries.forEach { item.ordinal <= step.ordinal }\n"
                "}\n"
                "onTogglePermission = { permissionId -> if (!authorizationActive) {\n"
                "permissionSnapshot.canSelect(permissionId)\n"
                "persistSelection(selectedPermissionIds)\n"
                "} }\n"
                "maxLines = 1\n"
            ),
            KIYORI_FIRST_RUN_AGREEMENT_PATH: (
                "package com.ai.assistance.operit.ui.features.agreement.screens\n"
                "class KiyoriAgreementConfirmationScreen\n"
                "KiyoriLegalDocument.USER_AGREEMENT\n"
                "KiyoriLegalDocument.PRIVACY_POLICY\n"
                "canAcceptKiyoriAgreement\n"
                "KiyoriLegalDocumentsScreen\n"
                "AgreementPreferences.CURRENT_AGREEMENT_VERSION\n"
                ".windowInsetsPadding(WindowInsets.safeDrawing)\n"
                "R.string.kiyori_onboarding_legal_full_text\n"
            ),
            KIYORI_FIRST_RUN_CONTRACT_TEST_PATH: (
                "KiyoriOnboardingStep resolveInitialKiyoriOnboardingStep "
                "KiyoriPermissionSnapshot sanitizeKiyoriPermissionSelection "
                "KiyoriPermissionStatus.ON_DEMAND "
                "KiyoriPermissionStatus.NOT_APPLICABLE "
                "kiyoriOnboardingPageCount canNavigateKiyoriOnboarding "
                "shouldEnableKiyoriOnboardingPagerInput "
                "resolveKiyoriOnboardingTitleAlignment\n"
            ),
            KIYORI_FIRST_RUN_AGREEMENT_TEST_PATH: (
                "canAcceptKiyoriAgreement\n"
                "checked = true\n"
                "assertTrue\n"
                "assertFalse\n"
            ),
            M04D_MAIN_STARTUP_GATE_COORDINATOR_PATH: (
                "package com.kiyori.app.startup\n"
                "KiyoriMainStartupDestination.ONBOARDING\n"
                "KiyoriMainStartupDestination.AGREEMENT\n"
                "KiyoriMainStartupDestination.CONTENT\n"
                "KiyoriOnboardingPreferences KiyoriOnboardingScreen "
                "KiyoriAgreementConfirmationScreen completeOnboarding\n"
            ),
            M04D_MAIN_STARTUP_GATE_COORDINATOR_TEST_PATH: (
                "KiyoriMainStartupDestination.ONBOARDING "
                "KiyoriMainStartupDestination.AGREEMENT "
                "KiyoriMainStartupDestination.CONTENT\n"
            ),
            M04_MAIN_ACTIVITY_PATH: (
                "KiyoriMainStartupGate(\n"
                "startupGateCoordinator.acceptCurrentAgreement()\n"
                "startupGateCoordinator.completeOnboarding()\n"
                "startPluginLoadingIfReady()\n"
                "!mainApplicationReady\n"
                "!startupGateCoordinator.isReadyForContent\n"
                "pluginLoadingStarted\n"
            ),
            "app/src/main/AndroidManifest.xml": (
                '<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />\n'
            ),
            "app/src/main/res/values/strings.xml": (
                "kiyori_onboarding_user_agreement_content\n"
                "kiyori_onboarding_privacy_policy_content\n"
                "kiyori_onboarding_welcome_title\n"
                "kiyori_onboarding_browser_title\n"
                "kiyori_onboarding_ai_title\n"
                "kiyori_onboarding_files_title\n"
                "网页浏览器\n"
                "文件下载器\n"
                "视频播放器\n"
                "广告拦截器\n"
                "模型配置\n"
                "AI 助手\n"
                "语音服务\n"
                "首页搜索\n"
                "工具箱\n"
                "文件管理器\n"
                "终端\n"
                "工作流\n"
                "数据备份\n"
                "Kiyori 无障碍支持\n"
                "Kiyori UI 自动化服务\n"
                "GPL-3.0-or-later\n"
                "集成 Operit AI\n"
                "kiyori_onboarding_permissions_authorize_and_enter\n"
                "日常运行时权限\n"
                "kiyori_onboarding_legal_full_text\n"
                "系统文件选择器按次选择\n"
                "Shizuku\nRoot\n"
            ),
        }
        for relative_path, text in files.items():
            path = root / relative_path
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(text, encoding="utf-8")

        launcher_foreground = root / KIYORI_LAUNCHER_FOREGROUND_PATH
        launcher_foreground.parent.mkdir(parents=True, exist_ok=True)
        launcher_foreground.write_bytes(b"kiyori-icon")

        support_apk = root / KIYORI_ACCESSIBILITY_SUPPORT_APK_PATH
        support_apk.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(support_apk, "w") as archive:
            archive.writestr(
                "resources.arsc",
                (
                    "Kiyori 无障碍支持\n"
                    "Kiyori UI 自动化服务\n"
                    "Theme.KiyoriAccessibilitySupport\n"
                ).encode("utf-8"),
            )
            archive.writestr(
                "res/drawable-nodpi/ic_kiyori_brand_foreground.png",
                b"kiyori-icon",
            )
            archive.writestr(
                "res/drawable-nodpi/ic_launcher_foreground.png",
                b"kiyori-icon",
            )
        support_version = root / KIYORI_ACCESSIBILITY_SUPPORT_VERSION_PATH
        support_version.write_text("1.7\n", encoding="utf-8")
        support_hash = root / KIYORI_ACCESSIBILITY_SUPPORT_HASH_PATH
        support_hash.parent.mkdir(parents=True, exist_ok=True)
        support_hash.write_text(
            hashlib.sha256(support_apk.read_bytes()).hexdigest().upper() + "\n",
            encoding="utf-8",
        )

    def test_kiyori_first_run_flow_accepts_centralized_owner(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write_kiyori_first_run_layout(root)
            errors: list[str] = []
            check_kiyori_first_run_flow(root, errors)
            self.assertEqual(errors, [])

    def test_kiyori_first_run_flow_requires_current_feature_copy(self) -> None:
        for feature in ("广告拦截器", "模型配置", "首页搜索", "工作流"):
            with self.subTest(feature=feature), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                self.write_kiyori_first_run_layout(root)
                strings = root / "app/src/main/res/values/strings.xml"
                strings.write_text(
                    strings.read_text(encoding="utf-8").replace(feature, ""),
                    encoding="utf-8",
                )
                errors: list[str] = []
                check_kiyori_first_run_flow(root, errors)
                self.assertTrue(any(f"fact contract missing: {feature}" in error for error in errors))

    def test_kiyori_first_run_flow_requires_permission_selection_summary(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write_kiyori_first_run_layout(root)
            screen = root / KIYORI_FIRST_RUN_SCREEN_PATH
            screen.write_text(
                screen.read_text(encoding="utf-8").replace(
                    "R.string.onb_p6_summary", "",
                ),
                encoding="utf-8",
            )
            errors: list[str] = []
            check_kiyori_first_run_flow(root, errors)
            self.assertTrue(any("onb_p6_summary" in error for error in errors))

    def test_kiyori_first_run_flow_rejects_second_launcher_and_legacy_owner(
        self,
    ) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write_kiyori_first_run_layout(root)
            screen = root / KIYORI_FIRST_RUN_SCREEN_PATH
            screen.write_text(
                screen.read_text(encoding="utf-8")
                + "ActivityResultContracts.RequestPermission\n",
                encoding="utf-8",
            )
            legacy = root / M04D_MAIN_NOTIFICATION_PERMISSION_COORDINATOR_PATH
            legacy.parent.mkdir(parents=True, exist_ok=True)
            legacy.write_text("class KiyoriMainNotificationPermissionCoordinator\n")
            errors: list[str] = []
            check_kiyori_first_run_flow(root, errors)
            self.assertTrue(any("legacy startup" in error for error in errors))
            self.assertTrue(any("second launcher" in error for error in errors))

    def test_kiyori_first_run_flow_rejects_removed_exact_alarm_contract(
        self,
    ) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write_kiyori_first_run_layout(root)
            permissions = root / KIYORI_FIRST_RUN_PERMISSIONS_PATH
            permissions.write_text(
                permissions.read_text(encoding="utf-8")
                + "KiyoriPermissionId.EXACT_ALARM\n"
                + "Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM\n"
                + "alarmManager.canScheduleExactAlarms()\n",
                encoding="utf-8",
            )
            manifest = root / "app/src/main/AndroidManifest.xml"
            manifest.write_text(
                manifest.read_text(encoding="utf-8")
                + '<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />\n',
                encoding="utf-8",
            )
            strings = root / "app/src/main/res/values/strings.xml"
            strings.write_text(
                strings.read_text(encoding="utf-8")
                + '<string name="kiyori_onboarding_permission_alarm_title">Exact alarm</string>\n',
                encoding="utf-8",
            )

            errors: list[str] = []
            check_kiyori_first_run_flow(root, errors)

            self.assertTrue(
                any(
                    "removed first-run exact-alarm contract remains" in error
                    for error in errors
                )
            )
            self.assertTrue(
                any(
                    "removed first-run exact-alarm manifest permission remains"
                    in error
                    for error in errors
                )
            )
            self.assertTrue(
                any(
                    "removed first-run exact-alarm resource remains" in error
                    for error in errors
                )
            )

    def test_kiyori_first_run_flow_rejects_combined_legal_ui_reference(
        self,
    ) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write_kiyori_first_run_layout(root)
            agreement = root / KIYORI_FIRST_RUN_AGREEMENT_PATH
            agreement.write_text(
                agreement.read_text(encoding="utf-8")
                + "R.string.kiyori_onboarding_agreement_document_content\n",
                encoding="utf-8",
            )
            errors: list[str] = []
            check_kiyori_first_run_flow(root, errors)
            self.assertTrue(
                any(
                    "legacy combined agreement UI reference" in error
                    for error in errors
                )
            )

    def test_kiyori_first_run_flow_rejects_obsolete_page_transition(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write_kiyori_first_run_layout(root)
            screen = root / KIYORI_FIRST_RUN_SCREEN_PATH
            screen.write_text(
                screen.read_text(encoding="utf-8")
                + "LinearProgressIndicator(\n",
                encoding="utf-8",
            )
            errors: list[str] = []
            check_kiyori_first_run_flow(root, errors)
            self.assertTrue(
                any(
                    "obsolete Kiyori onboarding UI contract remains" in error
                    for error in errors
                )
            )

    def test_kiyori_first_run_flow_rejects_global_permission_selection(
        self,
    ) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            self.write_kiyori_first_run_layout(root)
            screen = root / KIYORI_FIRST_RUN_SCREEN_PATH
            screen.write_text(
                screen.read_text(encoding="utf-8").replace(
                    "permissionSnapshot.canSelect(permissionId)", "KiyoriPermissionId.entries.toSet()"
                ),
                encoding="utf-8",
            )
            errors: list[str] = []
            check_kiyori_first_run_flow(root, errors)
            self.assertTrue(
                any(
                    "individual selection must persist only actionable permissions" in error
                    for error in errors
                )
            )

    def test_kiyori_first_run_flow_rejects_selection_during_authorization(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write_kiyori_first_run_layout(root)
            screen = root / KIYORI_FIRST_RUN_SCREEN_PATH
            screen.write_text(
                screen.read_text(encoding="utf-8").replace("if (!authorizationActive)", "if (true)"),
                encoding="utf-8",
            )
            errors: list[str] = []
            check_kiyori_first_run_flow(root, errors)
            self.assertTrue(any("while authorization is inactive" in error for error in errors))

    def test_kiyori_first_run_flow_rejects_progress_from_a_fixed_step(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write_kiyori_first_run_layout(root)
            screen = root / KIYORI_FIRST_RUN_SCREEN_PATH
            screen.write_text(
                screen.read_text(encoding="utf-8").replace("step.ordinal + 1", "1"),
                encoding="utf-8",
            )
            errors: list[str] = []
            check_kiyori_first_run_flow(root, errors)
            self.assertTrue(any("progress must use the current step" in error for error in errors))

    def test_kiyori_first_run_flow_rejects_legacy_accessibility_brand(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write_kiyori_first_run_layout(root)
            support_apk = root / KIYORI_ACCESSIBILITY_SUPPORT_APK_PATH
            with zipfile.ZipFile(support_apk, "a") as archive:
                archive.writestr(
                    "assets/legacy-brand.txt",
                    "Accessibility Operit Support",
                )
            (
                root / KIYORI_ACCESSIBILITY_SUPPORT_HASH_PATH
            ).write_text(
                hashlib.sha256(support_apk.read_bytes()).hexdigest().upper()
                + "\n",
                encoding="utf-8",
            )

            errors: list[str] = []
            check_kiyori_first_run_flow(root, errors)

            self.assertTrue(
                any(
                    "legacy accessibility support brand remains" in error
                    for error in errors
                )
            )

    def test_m04d_notification_permission_gate_accepts_early_registered_owner(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write_m04d_main_notification_permission_coordinator_layout(root)
            errors: list[str] = []
            check_m04d_main_notification_permission_coordinator(root, errors)
            self.assertEqual(errors, [])

    def test_m04d_notification_permission_gate_rejects_drift_and_activity_owner(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            coordinator = (
                self.write_m04d_main_notification_permission_coordinator_layout(root)
            )
            coordinator.write_text(
                coordinator.read_text(encoding="utf-8")
                + "import com.ai.assistance.operit.data.SecondOwner\n"
                + "val state = mutableStateOf(false)\n",
                encoding="utf-8",
            )
            duplicate = (
                root
                / "app/src/main/java/com/ai/assistance/operit/ui/main/"
                "SecondNotificationPermissionCoordinator.kt"
            )
            duplicate.parent.mkdir(parents=True, exist_ok=True)
            duplicate.write_text(
                "package com.ai.assistance.operit.ui.main\n"
                "class KiyoriMainNotificationPermissionCoordinator\n",
                encoding="utf-8",
            )
            main_activity = root / M04_MAIN_ACTIVITY_PATH
            main_activity.write_text(
                "package com.ai.assistance.operit.ui.main\n"
                "import android.Manifest\n"
                "import android.content.pm.PackageManager\n"
                "import androidx.activity.result.contract.ActivityResultContracts\n"
                "import androidx.core.content.ContextCompat\n"
                "class MainActivity {\n"
                "    private val notificationPermissionLauncher = "
                "registerForActivityResult(ActivityResultContracts.RequestPermission()) { Unit }\n"
                "    fun checkNotificationPermission() {\n"
                "        val permission = Manifest.permission.POST_NOTIFICATIONS\n"
                "        PackageManager.PERMISSION_GRANTED\n"
                "        shouldShowRequestPermissionRationale(permission)\n"
                "        R.string.notification_permission_denied\n"
                "        R.string.notification_permission_rationale\n"
                "    }\n"
                "}\n",
                encoding="utf-8",
            )
            (
                root / M05D_PERMISSION_TEST_PATH
            ).unlink()
            errors: list[str] = []
            check_m04d_main_notification_permission_coordinator(root, errors)
            self.assertTrue(any("source changed" in error for error in errors))
            self.assertTrue(any("project imports differ" in error for error in errors))
            self.assertTrue(any("must have one owner" in error for error in errors))
            self.assertTrue(any("unrelated state" in error for error in errors))
            self.assertTrue(any("must import" in error for error in errors))
            self.assertTrue(any("still imports" in error for error in errors))
            self.assertTrue(any("call count differs" in error for error in errors))
            self.assertTrue(any("still owns notification" in error for error in errors))
            self.assertTrue(any("contract test missing" in error for error in errors))

    def test_m04d_startup_gate_accepts_ui_projection_and_activity_side_effects(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write_m04d_main_startup_gate_coordinator_layout(root)
            errors: list[str] = []
            check_m04d_main_startup_gate_coordinator(root, errors)
            self.assertEqual(errors, [])

    def test_m04d_startup_gate_rejects_persistent_or_activity_owner_drift(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            coordinator = self.write_m04d_main_startup_gate_coordinator_layout(root)
            coordinator.write_text(
                coordinator.read_text(encoding="utf-8")
                + "import com.ai.assistance.operit.data.SecondOwner\n"
                + "val duplicateStore = MutableStateFlow(false)\n"
                + "fun startPluginLoading() = Unit\n",
                encoding="utf-8",
            )
            duplicate = (
                root
                / "app/src/main/java/com/ai/assistance/operit/ui/main/"
                "SecondStartupGateCoordinator.kt"
            )
            duplicate.parent.mkdir(parents=True, exist_ok=True)
            duplicate.write_text(
                "package com.ai.assistance.operit.ui.main\n"
                "class KiyoriMainStartupGateCoordinator\n",
                encoding="utf-8",
            )
            main_activity = root / M04_MAIN_ACTIVITY_PATH
            main_activity.write_text(
                "package com.ai.assistance.operit.ui.main\n"
                "import com.ai.assistance.operit.data.preferences.AgreementPreferences\n"
                "import com.ai.assistance.operit.data.preferences.androidPermissionPreferences\n"
                "import com.ai.assistance.operit.ui.features.agreement.screens.AgreementScreen\n"
                "import com.ai.assistance.operit.ui.features.permission.screens.PermissionGuideScreen\n"
                "class MainActivity {\n"
                "    private val agreementPreferences = AgreementPreferences(this)\n"
                "    private var showPermissionGuide = false\n"
                "    fun checkPermissionLevelSet() {\n"
                "        androidPermissionPreferences.getPreferredPermissionLevel()\n"
                "    }\n"
                "    fun content() {\n"
                "        AgreementScreen {}\n"
                "        PermissionGuideScreen(onComplete = {})\n"
                "    }\n"
                "}\n",
                encoding="utf-8",
            )
            (root / M04D_MAIN_STARTUP_GATE_COORDINATOR_TEST_PATH).unlink()
            errors: list[str] = []
            check_m04d_main_startup_gate_coordinator(root, errors)
            self.assertTrue(any("source changed" in error for error in errors))
            self.assertTrue(any("project imports differ" in error for error in errors))
            self.assertTrue(any("must have one owner" in error for error in errors))
            self.assertTrue(any("persistent state or host" in error for error in errors))
            self.assertTrue(any("must import" in error for error in errors))
            self.assertTrue(any("still imports" in error for error in errors))
            self.assertTrue(any("initialization count differs" in error for error in errors))
            self.assertTrue(any("call count differs" in error for error in errors))
            self.assertTrue(any("still owns startup-gate" in error for error in errors))
            self.assertTrue(any("ordering changed" in error for error in errors))
            self.assertTrue(any("contract test missing" in error for error in errors))

    def test_m04d_content_host_accepts_single_projection_and_activity_boundary(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write_m04d_main_content_host_layout(root)
            errors: list[str] = []
            check_m04d_main_content_host(root, errors)
            self.assertEqual(errors, [])

    def test_m04d_content_host_rejects_state_or_activity_owner_drift(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            host = self.write_m04d_main_content_host_layout(root)
            host_text = host.read_text(encoding="utf-8").replace(
                "    sharedContentCoordinator.processPendingSharedFiles()\n"
                "    sharedContentCoordinator.processPendingSharedText()\n",
                "    sharedContentCoordinator.processPendingSharedText()\n"
                "    sharedContentCoordinator.processPendingSharedFiles()\n",
            )
            host.write_text(
                host_text
                + "import com.ai.assistance.operit.data.SecondOwner\n"
                + "val duplicateStore = MutableStateFlow(false)\n"
                + "fun setContent() = Unit\n",
                encoding="utf-8",
            )
            duplicate = (
                root
                / "app/src/main/java/com/ai/assistance/operit/ui/main/"
                "SecondMainContentHost.kt"
            )
            duplicate.parent.mkdir(parents=True, exist_ok=True)
            duplicate.write_text(
                "package com.ai.assistance.operit.ui.main\n"
                "data class KiyoriMainContentRequestProjection(val value: String)\n"
                "fun projectKiyoriMainContentRequests() = Unit\n"
                "fun KiyoriMainContentHost() = Unit\n",
                encoding="utf-8",
            )
            main_activity = root / M04_MAIN_ACTIVITY_PATH
            main_activity.write_text(
                "package com.ai.assistance.operit.ui.main\n"
                "import androidx.compose.runtime.CompositionLocalProvider\n"
                "import com.ai.assistance.operit.ui.features.startup.screens."
                "LocalPluginLoadingState\n"
                "import com.kiyori.app.KiyoriApp\n"
                "class MainActivity {\n"
                "    fun content() {\n"
                "        sharedContentCoordinator.processPendingSharedText()\n"
                "        sharedContentCoordinator.processPendingSharedText()\n"
                "        sharedContentCoordinator.processPendingSharedFiles()\n"
                "        sharedContentCoordinator.processPendingSharedFiles()\n"
                "        val shortcutNavItem = pendingRequests.shortcutNavItem\n"
                "        val routeNavRequest = pendingRequests.routeId\n"
                "        val browserOpenRequest = pendingRequests.browserUrl\n"
                "        val kiyoriShellDestinationRequest = "
                "pendingRequests.shellDestination\n"
                "        CompositionLocalProvider(\n"
                "            LocalPluginLoadingState provides pluginLoadingState,\n"
                "        ) { KiyoriApp() }\n"
                "    }\n"
                "}\n",
                encoding="utf-8",
            )
            (root / M04D_MAIN_CONTENT_HOST_TEST_PATH).unlink()
            errors: list[str] = []
            check_m04d_main_content_host(root, errors)
            self.assertTrue(any("source changed" in error for error in errors))
            self.assertTrue(any("project imports differ" in error for error in errors))
            self.assertTrue(any("must have one owner" in error for error in errors))
            self.assertTrue(
                any(
                    "absorbed state or Activity/runtime responsibility" in error
                    for error in errors
                )
            )
            self.assertTrue(any("must import content host" in error for error in errors))
            self.assertTrue(any("still imports direct content" in error for error in errors))
            self.assertTrue(any("call count differs" in error for error in errors))
            self.assertTrue(any("still owns direct content" in error for error in errors))
            self.assertTrue(any("execution ordering changed" in error for error in errors))
            self.assertTrue(any("contract test missing" in error for error in errors))

    def test_m04e_finalization_accepts_compatibility_owner(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            ownership = self.write_m04e_finalization_layout(root)
            errors: list[str] = []
            check_m04e_finalization(root, ownership, errors)
            self.assertEqual(errors, [])

    def test_m04e_finalization_rejects_stale_exception(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            ownership = self.write_m04e_finalization_layout(
                root,
                include_stale_exception=True,
            )
            errors: list[str] = []
            check_m04e_finalization(root, ownership, errors)
            self.assertTrue(
                any(
                    "expired MainActivity ARCH001 exception remains" in error
                    for error in errors
                )
            )

    def test_m05a1_design_theme_accepts_pure_owner(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            ownership = self.write_m05a1_design_theme_layout(root)
            errors: list[str] = []
            check_m05a1_design_theme(root, ownership, errors)
            self.assertEqual(errors, [])

    def test_m05a1_design_theme_rejects_owner_or_dependency_drift(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            ownership = self.write_m05a1_design_theme_layout(root)

            color_schemes = root / M05A1_COLOR_SCHEMES_PATH
            color_schemes.write_text(
                color_schemes.read_text(encoding="utf-8")
                + "val preferences = UserPreferencesManager\n",
                encoding="utf-8",
            )

            old_browser_theme = root / M05A1_OLD_BROWSER_THEME_PATH
            old_browser_theme.parent.mkdir(parents=True, exist_ok=True)
            old_browser_theme.write_text(
                "package com.ai.assistance.operit.ui.theme\n"
                "fun KiyoriBrowserTheme() {}\n",
                encoding="utf-8",
            )

            ownership.write_text(
                ownership.read_text(encoding="utf-8").replace(
                    'id = "operit-core"\n'
                    'path = "app/src/main/java/com/ai/assistance/operit/core/**"\n'
                    'owner = "mixed-current"\n'
                    'sync_zone = "B"\n'
                    'phase = "current"\n'
                    'allowed_import_roots = [\n'
                    '  "com.ai.assistance.operit",\n'
                    '  "com.kiyori.capability",\n'
                    '  "com.kiyori.platform",\n',
                    'id = "operit-core"\n'
                    'path = "app/src/main/java/com/ai/assistance/operit/core/**"\n'
                    'owner = "mixed-current"\n'
                    'sync_zone = "B"\n'
                    'phase = "current"\n'
                    'allowed_import_roots = [\n'
                    '  "com.ai.assistance.operit",\n'
                    '  "com.kiyori.capability",\n'
                    '  "com.kiyori.design",\n'
                    '  "com.kiyori.platform",\n',
                ),
                encoding="utf-8",
            )

            player = (
                root
                / "app/src/main/java/com/ai/assistance/operit/ui/features/"
                "player/PlayerActivity.kt"
            )
            player.write_text(
                player.read_text(encoding="utf-8").replace(
                    "com.kiyori.design.theme.KiyoriBrowserTheme",
                    "com.ai.assistance.operit.ui.theme.KiyoriBrowserTheme",
                ),
                encoding="utf-8",
            )

            resolver = root / M05A1_OLD_COLOR_RESOLVER_PATH
            resolver.write_text(
                resolver.read_text(encoding="utf-8").replace(
                    "resolveKiyoriColorScheme(darkTheme)",
                    "if (darkTheme) KiyoriDarkColorScheme "
                    "else KiyoriLightColorScheme",
                ),
                encoding="utf-8",
            )

            design_test = root / M05A1_DESIGN_TEST_PATH
            design_test.write_text(
                design_test.read_text(encoding="utf-8").replace(
                    "fun `browser palettes retain the neutral chrome contract`() {}\n",
                    "",
                ),
                encoding="utf-8",
            )

            errors: list[str] = []
            check_m05a1_design_theme(root, ownership, errors)
            self.assertTrue(any("source changed" in error for error in errors))
            self.assertTrue(
                any("forbidden dependency or platform state" in error for error in errors)
            )
            self.assertTrue(
                any("design declaration owner differs" in error for error in errors)
            )
            self.assertTrue(any("old design owner remains" in error for error in errors))
            self.assertTrue(
                any("non-UI Operit owner gained design permission" in error for error in errors)
            )
            self.assertTrue(
                any("consumer still imports old design owner" in error for error in errors)
            )
            self.assertTrue(any("import consumers differ" in error for error in errors))
            self.assertTrue(
                any("bool preference adapter must delegate once" in error for error in errors)
            )
            self.assertTrue(
                any("fixed design assertion missing" in error for error in errors)
            )

    def test_m05a2_semantic_design_accepts_split_owner(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            ownership = self.write_m05a2_semantic_design_layout(root)
            errors: list[str] = []
            check_m05a2_semantic_design(root, ownership, errors)
            self.assertEqual(errors, [])

    def test_m05a2_semantic_design_rejects_contract_or_consumer_drift(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            ownership = self.write_m05a2_semantic_design_layout(root)

            semantic_colors = root / M05A2_SEMANTIC_COLORS_PATH
            semantic_colors.write_text(
                semantic_colors.read_text(encoding="utf-8").replace(
                    "Color(0xFF90CAF9)",
                    "Color(0xFF90CAFA)",
                )
                + "val preferences = MaterialTheme\n",
                encoding="utf-8",
            )

            semantic_theme = root / M05A2_SEMANTIC_THEME_PATH
            semantic_theme.write_text(
                semantic_theme.read_text(encoding="utf-8").replace(
                    "luminance() < 0.5f",
                    "luminance() <= 0.5f",
                    1,
                )
                + "val raw = Color(0xFFFFFFFF)\n"
                + "val state = remember { 0 }\n",
                encoding="utf-8",
            )

            old_owner = (
                root
                / "app/src/main/java/com/ai/assistance/operit/ui/theme/"
                "KiyoriSemanticTheme.kt"
            )
            old_owner.parent.mkdir(parents=True, exist_ok=True)
            old_owner.write_text(
                "package com.ai.assistance.operit.ui.theme\n"
                "enum class KiyoriSemanticTone { BLUE }\n",
                encoding="utf-8",
            )

            player_path = next(
                iter(
                    M05A2_EXPECTED_QUALIFIED_REFERENCES[
                        "KiyoriSemanticColors"
                    ]
                )
            )
            player = root / player_path
            player.write_text(
                player.read_text(encoding="utf-8")
                .replace(
                    "import com.kiyori.design.theme.KiyoriSemanticTone",
                    "import com.ai.assistance.operit.ui.theme.KiyoriSemanticTone",
                )
                .replace(
                    "com.kiyori.design.theme.KiyoriSemanticColors",
                    "com.ai.assistance.operit.ui.theme.KiyoriSemanticColors",
                ),
                encoding="utf-8",
            )

            ownership.write_text(
                ownership.read_text(encoding="utf-8").replace(
                    'id = "operit-core"\n'
                    'path = "app/src/main/java/com/ai/assistance/operit/core/**"\n'
                    'owner = "mixed-current"\n'
                    'sync_zone = "B"\n'
                    'phase = "current"\n'
                    'allowed_import_roots = [\n'
                    '  "com.ai.assistance.operit",\n'
                    '  "com.kiyori.capability",\n'
                    '  "com.kiyori.platform",\n',
                    'id = "operit-core"\n'
                    'path = "app/src/main/java/com/ai/assistance/operit/core/**"\n'
                    'owner = "mixed-current"\n'
                    'sync_zone = "B"\n'
                    'phase = "current"\n'
                    'allowed_import_roots = [\n'
                    '  "com.ai.assistance.operit",\n'
                    '  "com.kiyori.capability",\n'
                    '  "com.kiyori.design",\n'
                    '  "com.kiyori.platform",\n',
                ),
                encoding="utf-8",
            )

            design_test = root / M05A1_DESIGN_TEST_PATH
            design_test.write_text(
                design_test.read_text(encoding="utf-8").replace(
                    "fun `stable entry ids keep deterministic semantic tones`() {\n",
                    "fun removed_stable_entry_test() {\n",
                ),
                encoding="utf-8",
            )
            old_theme_test = root / M05A1_OLD_THEME_TEST_PATH
            old_theme_test.write_text(
                old_theme_test.read_text(encoding="utf-8")
                + "fun `application semantic icon tones stay colorful and theme aware`() {}\n",
                encoding="utf-8",
            )

            snapshot_name = "m04b-ai-drawer-operit-imports.txt"
            snapshot_path = root / "config/architecture" / snapshot_name
            replacements = M05A2_M04B_SEMANTIC_SNAPSHOT_REPLACEMENTS[
                snapshot_name
            ]
            old_import, new_import = next(iter(replacements.items()))
            snapshot_path.write_text(
                snapshot_path.read_text(encoding="utf-8").replace(
                    new_import,
                    old_import,
                ),
                encoding="utf-8",
            )

            errors: list[str] = []
            check_m05a2_semantic_design(root, ownership, errors)
            self.assertTrue(any("source changed" in error for error in errors))
            self.assertTrue(
                any("pure semantic source owns forbidden" in error for error in errors)
            )
            self.assertTrue(
                any("semantic color literal count differs" in error for error in errors)
            )
            self.assertTrue(
                any("semantic Compose adapter contract differs" in error for error in errors)
            )
            self.assertTrue(
                any("semantic Compose adapter owns forbidden" in error for error in errors)
            )
            self.assertTrue(
                any("old semantic design owner remains" in error for error in errors)
            )
            self.assertTrue(
                any("semantic declaration owner differs" in error for error in errors)
            )
            self.assertTrue(
                any("consumer still imports old semantic owner" in error for error in errors)
            )
            self.assertTrue(
                any("old qualified semantic reference remains" in error for error in errors)
            )
            self.assertTrue(any("consumer imports differ" in error for error in errors))
            self.assertTrue(
                any("qualified reference differs" in error for error in errors)
            )
            self.assertTrue(
                any("non-UI Operit owner gained design permission" in error for error in errors)
            )
            self.assertTrue(
                any("semantic design assertion missing" in error for error in errors)
            )
            self.assertTrue(
                any("old theme test still owns semantic assertion" in error for error in errors)
            )
            self.assertTrue(
                any("M-04B semantic snapshot differs" in error for error in errors)
            )

    def test_m05a3_root_theme_accepts_split_owners(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            ownership = self.write_m05a3_root_theme_layout(root)
            errors: list[str] = []
            check_m05a3_root_theme(root, ownership, errors)
            self.assertEqual(errors, [])

    def test_m05a3_root_theme_rejects_owner_style_or_player_drift(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            ownership = self.write_m05a3_root_theme_layout(root)

            design_theme = root / M05A3_DESIGN_THEME_PATH
            design_theme.write_text(
                design_theme.read_text(encoding="utf-8")
                + "val preferences = UserPreferencesManager\n",
                encoding="utf-8",
            )

            app_theme = root / M05A3_APP_THEME_PATH
            app_theme.write_text(
                app_theme.read_text(encoding="utf-8").replace(
                    "preferencesManager.fontScale.collectAsState(initial = 1.0f)",
                    "preferencesManager.fontScale.collectAsState(initial = 1.1f)",
                ),
                encoding="utf-8",
            )

            system_bars = root / M05A3_SYSTEM_BARS_PATH
            system_bars.write_text(
                system_bars.read_text(encoding="utf-8")
                + "val preferences = UserPreferencesManager\n",
                encoding="utf-8",
            )

            old_theme = root / M05A3_OLD_THEME_PATH
            old_theme.parent.mkdir(parents=True, exist_ok=True)
            old_theme.write_text(
                "package com.ai.assistance.operit.ui.theme\n"
                "fun OperitTheme() {}\n",
                encoding="utf-8",
            )

            type_adapter = root / M05A3_TYPE_PATH
            type_adapter.write_text(
                type_adapter.read_text(encoding="utf-8")
                + "val Typography = KiyoriTypography\n"
                + "fun applyFontFamilyToTypography() {}\n",
                encoding="utf-8",
            )

            theme_resource = root / M05A3_THEME_RESOURCE_PATHS[0]
            theme_resource.write_text(
                theme_resource.read_text(encoding="utf-8").replace(
                    M05A3_NEW_STYLE,
                    "Theme.Operit",
                ),
                encoding="utf-8",
            )

            ownership.write_text(
                ownership.read_text(encoding="utf-8")
                .replace(
                    '  "com.kiyori.capability",\n'
                    '  "com.kiyori.platform",\n'
                    "]\n"
                    "forbidden_import_roots = [\n",
                    '  "com.kiyori.app",\n'
                    '  "com.kiyori.capability",\n'
                    '  "com.kiyori.platform",\n'
                    "]\n"
                    "forbidden_import_roots = [\n",
                    1,
                )
                .replace(
                    f'reason = "{M05A3_APP_THEME_EXCEPTION_REASON}"',
                    'reason = "broadened theme bridge"',
                    1,
                ),
                encoding="utf-8",
            )

            player = root / M05A3_PLAYER_ACTIVITY_PATH
            player.write_text(
                player.read_text(encoding="utf-8") + "\n// drift\n",
                encoding="utf-8",
            )

            design_test = root / M05A1_DESIGN_TEST_PATH
            design_test.write_text(
                design_test.read_text(encoding="utf-8").replace(
                    "all typography roles use neutral tracking",
                    "removed typography assertion",
                ),
                encoding="utf-8",
            )

            errors: list[str] = []
            check_m05a3_root_theme(root, ownership, errors)
            self.assertTrue(any("source changed" in error for error in errors))
            self.assertTrue(
                any("pure root theme owns forbidden" in error for error in errors)
            )
            self.assertTrue(
                any("app theme host contract differs" in error for error in errors)
            )
            self.assertTrue(
                any("system-bar owner owns forbidden" in error for error in errors)
            )
            self.assertTrue(
                any("old root theme owner remains" in error for error in errors)
            )
            self.assertTrue(
                any(
                    "old or duplicate theme/Typography owner remains" in error
                    for error in errors
                )
            )
            self.assertTrue(
                any("theme resource declaration differs" in error for error in errors)
            )
            self.assertTrue(
                any(
                    "operit-widget gained broad app permission" in error
                    for error in errors
                )
            )
            self.assertTrue(any("PlayerActivity changed" in error for error in errors))
            self.assertTrue(
                any("Typography design assertion missing" in error for error in errors)
            )
            self.assertTrue(
                any("exact ownership exception differs" in error for error in errors)
            )

    def test_m05b_platform_logging_accepts_single_owner_and_facade(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            ownership = self.write_m05b_platform_logging_layout(root)
            errors: list[str] = []
            check_m05b_platform_logging(root, ownership, errors)
            self.assertEqual(errors, [])

    def test_m05b_platform_logging_rejects_state_or_compatibility_drift(
        self,
    ) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            ownership = self.write_m05b_platform_logging_layout(root)

            logger = root / M05B_LOGGER_PATH
            logger.write_text(
                logger.read_text(encoding="utf-8")
                .replace(
                    "import com.kiyori.platform.android.ApplicationContextAccess\n",
                    "import com.kiyori.platform.android.ApplicationContextAccess\n"
                    "import com.ai.assistance.operit.util.OperitPaths\n",
                )
                .replace(
                    "KiyoriLogFileMigration.ACTIVE_FILE_NAME",
                    "ChangedLogFile.ACTIVE_FILE_NAME",
                ),
                encoding="utf-8",
            )

            facade = root / M05B_LEGACY_FACADE_PATH
            facade.write_text(
                facade.read_text(encoding="utf-8")
                + "\n@Volatile private var logFile = File(\"second.log\")\n"
                + "val duplicate = Executors.newSingleThreadExecutor()\n",
                encoding="utf-8",
            )

            old_formatter = root / M05B_OLD_FORMATTER_PATH
            old_formatter.parent.mkdir(parents=True, exist_ok=True)
            old_formatter.write_text(
                "package com.ai.assistance.operit.util\n"
                "object ThrowableTextFormatter\n",
                encoding="utf-8",
            )

            consumer_snapshot = (
                root / "config/architecture" / M05B_KIYORI_CONSUMER_SNAPSHOT
            )
            stale_consumer_path = Path(
                consumer_snapshot.read_text(encoding="utf-8")
                .splitlines()[0]
            )
            stale_consumer = root / stale_consumer_path
            stale_consumer.write_text(
                stale_consumer.read_text(encoding="utf-8").replace(
                    M05B_NEW_LOGGER_IMPORT,
                    M05B_OLD_LOGGER_IMPORT,
                ),
                encoding="utf-8",
            )

            formatter_test = root / M05B_FORMATTER_TEST_PATH
            formatter_test.write_text(
                formatter_test.read_text(encoding="utf-8").replace(
                    "longTextIsBoundedAndMarked",
                    "removedLongTextAssertion",
                ),
                encoding="utf-8",
            )

            static_mock_test = (
                root
                / "app/src/test/java/com/ai/assistance/operit/core/tools/"
                "condition/ConditionEvaluatorParseFailureTest.kt"
            )
            static_mock_test.write_text(
                static_mock_test.read_text(encoding="utf-8").replace(
                    "Mockito.mockStatic(AppLogger::class.java)",
                    "Unit",
                ),
                encoding="utf-8",
            )

            ownership.write_text(
                ownership.read_text(encoding="utf-8")
                + "[[exception]]\n"
                'rule = "ARCH004"\n'
                f'path = "{M05B_DISPLAY_EXCEPTION_PATH}"\n'
                'reason = "stale logging exception"\n'
                'expires_after = "M-05-platform-migration"\n'
                'owner = "kiyori-app"\n',
                encoding="utf-8",
            )

            errors: list[str] = []
            check_m05b_platform_logging(root, ownership, errors)
            self.assertTrue(
                any("logging source changed" in error for error in errors)
            )
            self.assertTrue(
                any(
                    "platform logger project imports differ" in error
                    for error in errors
                )
            )
            self.assertTrue(
                any(
                    "platform logger owns forbidden dependency" in error
                    for error in errors
                )
            )
            self.assertTrue(
                any("platform logger contract differs" in error for error in errors)
            )
            self.assertTrue(
                any(
                    "legacy facade owns logger implementation state" in error
                    for error in errors
                )
            )
            self.assertTrue(
                any("old log formatter owner remains" in error for error in errors)
            )
            self.assertTrue(
                any("Kiyori logger consumers differ" in error for error in errors)
            )
            self.assertTrue(
                any(
                    "Kiyori source still uses legacy logger" in error
                    for error in errors
                )
            )
            self.assertTrue(
                any(
                    "formatter contract assertion missing" in error
                    for error in errors
                )
            )
            self.assertTrue(
                any(
                    "static-mock compatibility differs" in error
                    for error in errors
                )
            )
            self.assertTrue(
                any(
                    "display logger ownership exception remains" in error
                    for error in errors
                )
            )

    def test_m05c_platform_lifecycle_accepts_fact_and_side_effect_split(
        self,
    ) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            ownership = self.write_m05c_platform_lifecycle_layout(root)
            errors: list[str] = []
            check_m05c_platform_lifecycle(root, ownership, errors)
            self.assertEqual(errors, [])

    def test_m05c_platform_lifecycle_rejects_state_or_compatibility_drift(
        self,
    ) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            ownership = self.write_m05c_platform_lifecycle_layout(root)

            platform = root / M05C_PLATFORM_LIFECYCLE_PATH
            platform.write_text(
                platform.read_text(encoding="utf-8").replace(
                    "import android.os.Bundle\n",
                    "import android.os.Bundle\n"
                    "import com.ai.assistance.operit.data.preferences.ApiPreferences\n",
                ),
                encoding="utf-8",
            )

            integration = root / M05C_OPERIT_INTEGRATION_PATH
            integration.write_text(
                integration.read_text(encoding="utf-8")
                + "\nprivate lateinit var appContext: android.content.Context\n",
                encoding="utf-8",
            )

            facade = root / M05C_LEGACY_FACADE_PATH
            facade.write_text(
                facade.read_text(encoding="utf-8")
                + "\nprivate var activityCount = 0\n"
                + "fun duplicateRegistration(application: Application) = "
                "application.registerActivityLifecycleCallbacks("
                "ActivityLifecycleManager)\n",
                encoding="utf-8",
            )

            stale_consumer = (
                root
                / "app/src/main/java/com/ai/assistance/operit/api/chat/"
                "EnhancedAIService.kt"
            )
            stale_consumer.write_text(
                stale_consumer.read_text(encoding="utf-8").replace(
                    M05C_LEGACY_IMPORT,
                    "com.example.RemovedLifecycleManager",
                ),
                encoding="utf-8",
            )

            facts_test = root / M05C_FACTS_TEST_PATH
            facts_test.write_text(
                facts_test.read_text(encoding="utf-8").replace(
                    "foregroundTransitionsOnlyAtZeroOneBoundaries",
                    "removedForegroundBoundaryAssertion",
                ),
                encoding="utf-8",
            )

            errors: list[str] = []
            check_m05c_platform_lifecycle(root, ownership, errors)
            self.assertTrue(
                any("lifecycle source changed" in error for error in errors)
            )
            self.assertTrue(
                any(
                    "platform lifecycle owns project dependency" in error
                    for error in errors
                )
            )
            self.assertTrue(
                any(
                    "platform lifecycle owns Operit integration" in error
                    for error in errors
                )
            )
            self.assertTrue(
                any(
                    "Operit lifecycle integration owns platform fact or "
                    "Context state" in error
                    for error in errors
                )
            )
            self.assertTrue(
                any(
                    "legacy lifecycle facade owns implementation state"
                    in error
                    for error in errors
                )
            )
            self.assertTrue(
                any(
                    "lifecycle callback registration owner differs" in error
                    for error in errors
                )
            )
            self.assertTrue(
                any(
                    "legacy lifecycle consumers differ" in error
                    for error in errors
                )
            )
            self.assertTrue(
                any(
                    "lifecycle facts assertion missing" in error
                    for error in errors
                )
            )

    def test_m05d_notification_permission_accepts_platform_and_resource_split(
        self,
    ) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            ownership = self.write_m05d_notification_permission_layout(root)
            errors: list[str] = []
            check_m05d_android_permission_capability(
                root,
                ownership,
                errors,
            )
            self.assertEqual(errors, [])

    def test_m05d_notification_permission_rejects_owner_scope_or_resource_drift(
        self,
    ) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            ownership = self.write_m05d_notification_permission_layout(root)

            platform = root / M05D_PLATFORM_PERMISSION_PATH
            platform.write_text(
                platform.read_text(encoding="utf-8").replace(
                    "import androidx.core.content.ContextCompat\n",
                    "import androidx.core.content.ContextCompat\n"
                    "import com.ai.assistance.operit.data.preferences."
                    "AndroidPermissionPreferences\n",
                )
                + "\nprivate var isGranted = false\n"
                + "val unrelatedPermission = Manifest.permission.CAMERA\n",
                encoding="utf-8",
            )

            resource_bridge = root / M05D_OPERIT_RESOURCE_BRIDGE_PATH
            resource_bridge.write_text(
                resource_bridge.read_text(encoding="utf-8")
                + "\nval permissionResourceState = mutableStateOf(false)\n",
                encoding="utf-8",
            )

            coordinator = root / M05D_COORDINATOR_PATH
            coordinator.write_text(
                coordinator.read_text(encoding="utf-8")
                + "\nval duplicatePermission = "
                "Manifest.permission.POST_NOTIFICATIONS\n"
                + "val duplicateResource = "
                "R.string.notification_permission_denied\n",
                encoding="utf-8",
            )

            main_activity = root / M04_MAIN_ACTIVITY_PATH
            main_activity.write_text(
                main_activity.read_text(encoding="utf-8")
                + "\nval mainPermission = "
                "Manifest.permission.POST_NOTIFICATIONS\n",
                encoding="utf-8",
            )

            direct_consumer = (
                root
                / "app/src/main/java/com/ai/assistance/operit/core/tools/"
                "defaultTool/websession/browser/BrowserDownloadRuntime.kt"
            )
            direct_consumer.write_text(
                direct_consumer.read_text(encoding="utf-8").replace(
                    "Manifest.permission.POST_NOTIFICATIONS",
                    "Manifest.permission.CAMERA",
                ),
                encoding="utf-8",
            )

            preferences = root / M05D_PREFERENCES_PATH
            preferences.write_text(
                preferences.read_text(encoding="utf-8")
                + "\nval copiedPermissionFact = false\n",
                encoding="utf-8",
            )

            permission_test = root / M05D_PERMISSION_TEST_PATH
            permission_test.write_text(
                permission_test.read_text(encoding="utf-8").replace(
                    "denied permission without rationale requests directly",
                    "removed direct request assertion",
                ),
                encoding="utf-8",
            )

            with ownership.open("a", encoding="utf-8") as stream:
                stream.write(
                    "[[exception]]\n"
                    'rule = "ARCH004"\n'
                    f'path = "{M05D_COORDINATOR_PATH}"\n'
                    'reason = "stale resource bridge"\n'
                    'expires_after = "never"\n'
                    'owner = "kiyori-app"\n'
                )

            errors: list[str] = []
            check_m05d_android_permission_capability(
                root,
                ownership,
                errors,
            )
            self.assertTrue(
                any("permission source changed" in error for error in errors)
            )
            self.assertTrue(
                any(
                    "platform permission owns project dependency" in error
                    for error in errors
                )
            )
            self.assertTrue(
                any(
                    "platform permission absorbed forbidden" in error
                    for error in errors
                )
            )
            self.assertTrue(
                any(
                    "resource bridge owns runtime permission or state" in error
                    for error in errors
                )
            )
            self.assertTrue(
                any(
                    "startup coordinator still owns platform permission"
                    in error
                    for error in errors
                )
            )
            self.assertTrue(
                any(
                    "MainActivity absorbed permission implementation" in error
                    for error in errors
                )
            )
            self.assertTrue(
                any(
                    "direct notification permission consumers differ" in error
                    for error in errors
                )
            )
            self.assertTrue(
                any(
                    "permission policy assertion missing" in error
                    for error in errors
                )
            )
            self.assertTrue(
                any(
                    "resource ownership exception remains" in error
                    for error in errors
                )
            )

    def test_m05e_storage_paths_accept_unique_owner_and_facades(
        self,
    ) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            ownership = self.write_m05e_storage_paths_layout(root)
            errors: list[str] = []
            check_m05e_storage_paths(root, ownership, errors)
            self.assertEqual(errors, [])

    def test_m05e_storage_paths_reject_duplicate_calculation_or_consumer_drift(
        self,
    ) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            ownership = self.write_m05e_storage_paths_layout(root)

            kiyori_paths = root / M05E_KIYORI_PATHS_PATH
            kiyori_paths.write_text(
                kiyori_paths.read_text(encoding="utf-8").replace(
                    "import java.io.File\n",
                    "import java.io.File\n"
                    "import com.ai.assistance.operit.util.AppLogger\n",
                ),
                encoding="utf-8",
            )

            backup_projection = root / M05E_KIYORI_BACKUP_PATHS_PATH
            backup_projection.write_text(
                backup_projection.read_text(encoding="utf-8")
                + '\nval duplicateBackup = File(KiyoriPaths.kiyoriRootDir(), "backup")\n',
                encoding="utf-8",
            )

            operit_paths = root / M05E_OPERIT_PATHS_PATH
            operit_paths.write_text(
                operit_paths.read_text(encoding="utf-8")
                + '\nval duplicateRoot = File(Environment.getExternalStorageDirectory(), "Kiyori")\n',
                encoding="utf-8",
            )

            direct_consumer = root / M03_APPLICATION_PATH
            direct_consumer.write_text(
                direct_consumer.read_text(encoding="utf-8").replace(
                    "ShowerEnvironment.stagingDirectoryProvider = "
                    "KiyoriPaths::kiyoriRootDir",
                    "ShowerEnvironment.stagingDirectoryProvider = null",
                ),
                encoding="utf-8",
            )

            legacy_consumer = (
                root / M05E_LEGACY_OPERIT_CONSUMER_PATHS[0]
            )
            legacy_consumer.write_text(
                legacy_consumer.read_text(encoding="utf-8").replace(
                    M05E_OPERIT_PATHS_IMPORT,
                    M05E_KIYORI_PATHS_IMPORT,
                ).replace(
                    "OperitPaths.kiyoriRootPathSdcard()",
                    "KiyoriPaths.kiyoriRootPathSdcard()",
                ),
                encoding="utf-8",
            )

            write_path = (
                root
                / "app/src/main/java/com/ai/assistance/operit/data/"
                "backup/LegacyBackupConsumer.kt"
            )
            write_path.parent.mkdir(parents=True, exist_ok=True)
            write_path.write_text(
                "package com.ai.assistance.operit.data.backup\n"
                f"import {M05E_OPERIT_BACKUP_DIRS_IMPORT}\n"
                "val backup = OperitBackupDirs.backupRootDir()\n",
                encoding="utf-8",
            )

            paths_test = root / M05E_PATHS_TEST_PATH
            paths_test.write_text(
                paths_test.read_text(encoding="utf-8").replace(
                    "backup directories keep exact hierarchy",
                    "removed backup hierarchy assertion",
                ),
                encoding="utf-8",
            )

            errors: list[str] = []
            check_m05e_storage_paths(root, ownership, errors)
            self.assertTrue(
                any("storage source changed" in error for error in errors)
            )
            self.assertTrue(
                any(
                    "Kiyori paths owner imports project code" in error
                    for error in errors
                )
            )
            self.assertTrue(
                any(
                    "Kiyori backup projection owns path calculation"
                    in error
                    for error in errors
                )
            )
            self.assertTrue(
                any(
                    "Operit paths facade owns calculation" in error
                    for error in errors
                )
            )
            self.assertTrue(
                any(
                    "direct Kiyori path consumers differ" in error
                    for error in errors
                )
            )
            self.assertTrue(
                any(
                    "Shower staging path owner differs" in error
                    for error in errors
                )
            )
            self.assertTrue(
                any(
                    "legacy Operit path consumers differ" in error
                    for error in errors
                )
            )
            self.assertTrue(
                any(
                    "legacy Operit backup consumers remain" in error
                    for error in errors
                )
            )
            self.assertTrue(
                any(
                    "storage path assertion missing" in error
                    for error in errors
                )
            )

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

    def test_data_store_factory_contract_is_extracted(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            git(root, "init", "-b", "main")
            source = root / "app/src/main/java/com/example/Store.kt"
            source.parent.mkdir(parents=True)
            source.write_text(
                "val store = DataStoreFactory.create(\n"
                "    serializer = Serializer,\n"
                "    scope = scope,\n"
                "    produceFile = browserSessionRecoveryFileProducer,\n"
                ")\n",
                encoding="utf-8",
            )
            git(root, "add", "app/src/main/java/com/example/Store.kt")
            self.assertEqual(
                persistence_api_records(root),
                Counter(
                    {
                        "app/src/main/java/com/example/Store.kt"
                        "\tDataStoreFactory.create\tbrowserSessionRecoveryFileProducer": 1,
                    },
                ),
            )

    def test_unregistered_data_store_factory_contract_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            git(root, "init", "-b", "main")
            source = root / "app/src/main/java/com/example/Store.kt"
            source.parent.mkdir(parents=True)
            source.write_text(
                "val store = DataStoreFactory.create(\n"
                "    produceFile = browserSessionRecoveryFileProducer,\n"
                ")\n",
                encoding="utf-8",
            )
            git(root, "add", "app/src/main/java/com/example/Store.kt")
            snapshot = root / "persistence-api-calls.txt"
            snapshot.write_text("", encoding="utf-8")
            errors: list[str] = []
            check_persistence_api_calls(root, snapshot, errors)
            self.assertEqual(len(errors), 1)
            self.assertIn("unexpected persistence API contract", errors[0])
            self.assertIn("DataStoreFactory.create", errors[0])

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

    def test_exact_ownership_overrides_broad_glob(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            broad_source = (
                root
                / "app/src/main/java/com/ai/assistance/operit/ui/Other.kt"
            )
            broad_source.parent.mkdir(parents=True)
            broad_source.write_text(
                "package com.ai.assistance.operit.ui\n",
                encoding="utf-8",
            )
            exact_source = root / M04_MAIN_ACTIVITY_PATH
            exact_source.parent.mkdir(parents=True, exist_ok=True)
            exact_source.write_text(
                "package com.ai.assistance.operit.ui.main\n"
                "import com.kiyori.app.startup.KiyoriMainContentHost\n",
                encoding="utf-8",
            )
            ownership = root / "ownership.toml"
            ownership.write_text(
                "schema_version = 1\n"
                "[[ownership]]\n"
                'id = "operit-ui"\n'
                'path = "app/src/main/java/com/ai/assistance/operit/ui/**"\n'
                'owner = "operit-ui"\n'
                'sync_zone = "B"\n'
                'phase = "current"\n'
                'allowed_import_roots = ["com.ai.assistance.operit"]\n'
                'forbidden_import_roots = ["com.kiyori.app"]\n'
                "[[ownership]]\n"
                f'id = "{M04E_MAIN_ACTIVITY_OWNERSHIP_ID}"\n'
                f'path = "{M04_MAIN_ACTIVITY_PATH}"\n'
                'owner = "kiyori-app-entry-compatibility"\n'
                'sync_zone = "D"\n'
                'phase = "m04e"\n'
                'allowed_import_roots = [\n'
                '  "com.ai.assistance.operit",\n'
                '  "com.kiyori.app",\n'
                '  "com.kiyori.platform",\n'
                "]\n"
                'forbidden_import_roots = ["com.kiyori.feature"]\n',
                encoding="utf-8",
            )
            errors: list[str] = []
            check_ownership(root, ownership, errors)
            self.assertEqual(errors, [])

    def test_overlapping_broad_ownership_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "app/src/main/java/com/example/sub/Owned.kt"
            source.parent.mkdir(parents=True)
            source.write_text(
                "package com.example.sub\n",
                encoding="utf-8",
            )
            ownership = root / "ownership.toml"
            ownership.write_text(
                "schema_version = 1\n"
                "[[ownership]]\n"
                'id = "example"\n'
                'path = "app/src/main/java/com/example/**"\n'
                'owner = "example"\n'
                'sync_zone = "C"\n'
                'phase = "current"\n'
                "[[ownership]]\n"
                'id = "example-sub"\n'
                'path = "app/src/main/java/com/example/sub/**"\n'
                'owner = "example-sub"\n'
                'sync_zone = "C"\n'
                'phase = "current"\n',
                encoding="utf-8",
            )
            errors: list[str] = []
            check_ownership(root, ownership, errors)
            self.assertTrue(
                any("source file has multiple owners" in error for error in errors)
            )

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

    def test_forbidden_fully_qualified_dependency_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "app/src/main/java/com/legacy/Feature.kt"
            source.parent.mkdir(parents=True)
            source.write_text(
                "package com.legacy\n\n"
                "class Feature {\n"
                "    val app = com.kiyori.app.KiyoriApplication.instance\n"
                '    val literal = "com.kiyori.app.NotADependency"\n'
                "    // com.kiyori.app.CommentOnly\n"
                "}\n",
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
            violations = [
                error
                for error in errors
                if error.startswith("ARCH001 forbidden fully-qualified reference:")
            ]
            self.assertEqual(len(violations), 1)
            self.assertIn("Feature.kt:4", violations[0])

    def test_fully_qualified_dependency_respects_allowed_roots(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "app/src/main/java/com/kiyori/feature/browser/BrowserFeature.kt"
            source.parent.mkdir(parents=True)
            source.write_text(
                "package com.kiyori.feature.browser\n\n"
                "class BrowserFeature {\n"
                "    val player = com.kiyori.feature.player.PlayerState\n"
                "}\n",
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
                '  "com.kiyori.feature.browser",\n'
                ']\n',
                encoding="utf-8",
            )
            edges = source_dependency_edges(source)
            self.assertIn(
                (
                    4,
                    "com.kiyori.feature.player.PlayerState",
                    "fully-qualified reference",
                ),
                edges,
            )
            errors: list[str] = []
            check_ownership(root, ownership, errors)
            self.assertTrue(
                any(
                    error.startswith(
                        "ARCH004 fully-qualified reference outside allowed roots:"
                    )
                    and "BrowserFeature.kt:4" in error
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

            new_path.unlink()
            m03_path = root / M03_APPLICATION_PATH
            m03_path.parent.mkdir(parents=True, exist_ok=True)
            m03_path.write_text(
                "package com.kiyori.app\nclass KiyoriApplication\n",
                encoding="utf-8",
            )
            self.assertEqual(resolve_phase(root, "auto", m01_commit), "m03")


if __name__ == "__main__":
    unittest.main()
