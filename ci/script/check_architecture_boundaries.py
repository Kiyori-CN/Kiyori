#!/usr/bin/env python3
"""Validate Kiyori package ownership, stable contracts, and M-01 purity."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import subprocess
import sys
import time
import tomllib
import xml.etree.ElementTree as ET
import zipfile
from collections import Counter
from functools import lru_cache
from pathlib import Path, PurePosixPath


ANDROID_NAME = "{http://schemas.android.com/apk/res/android}name"
ANDROID_AUTHORITIES = "{http://schemas.android.com/apk/res/android}authorities"
ANDROID_HOST = "{http://schemas.android.com/apk/res/android}host"
ANDROID_MIME_TYPE = "{http://schemas.android.com/apk/res/android}mimeType"
ANDROID_PERMISSION = "{http://schemas.android.com/apk/res/android}permission"
ANDROID_PROCESS = "{http://schemas.android.com/apk/res/android}process"
ANDROID_SCHEME = "{http://schemas.android.com/apk/res/android}scheme"
TOOLS_NODE = "{http://schemas.android.com/tools}node"
VALID_SYNC_ZONES = {"A", "B", "C", "D"}
VALID_MANIFEST_PHASES = {"baseline", "m01", "post-m01", "m03"}
DEBUG_MANIFEST_PHASES = {"debug"}
MAIN_MANIFEST_PATH = "app/src/main/AndroidManifest.xml"
DEBUG_MANIFEST_PATH = "app/src/debug/AndroidManifest.xml"
DEBUG_MANIFEST_COMPONENT_SNAPSHOT = "debug-manifest-components.txt"
DEBUG_MANIFEST_HASH_SNAPSHOT = "debug-manifest-structure-hashes.txt"
MANAGED_SOURCE_SUFFIXES = {".java", ".kt"}
PROJECT_IMPORT_ROOTS = (
    "com.ai.assistance.operit",
    "com.kiyori",
)
PROJECT_REFERENCE_PATTERN = re.compile(
    r"(?<![A-Za-z0-9_])"
    r"((?:com\.ai\.assistance\.operit|com\.kiyori)"
    r"(?:\.[A-Za-z_][A-Za-z0-9_]*)+)"
)
TEXT_SUFFIXES = {
    ".aidl",
    ".cpp",
    ".h",
    ".java",
    ".js",
    ".json",
    ".kt",
    ".kts",
    ".pro",
    ".properties",
    ".toml",
    ".ts",
    ".xml",
}
M01_OLD_PATH = "app/src/main/java/com/ai/assistance/operit/core/application/OperitApplication.kt"
M01_NEW_PATH = "app/src/main/java/com/ai/assistance/operit/core/application/KiyoriApplication.kt"
M03_APPLICATION_PATH = "app/src/main/java/com/kiyori/app/KiyoriApplication.kt"
M03_APPLICATION_PACKAGE = "com.kiyori.app"
M03_APPLICATION_PACKAGE_PLACEHOLDER = "__M03_APPLICATION_PACKAGE__"
M03_LINT_OLD_PATH = "src/main/java/com/ai/assistance/operit/core/application/KiyoriApplication.kt"
M03_LINT_NEW_PATH = "src/main/java/com/kiyori/app/KiyoriApplication.kt"
# M-03 originally moved six baseline locations. Full lint regeneration first
# proved AppBundleLocaleChanges stale, then QD-07's minSdk cleanup removed one
# obsolete SDK branch. ARCH018 locks the four records that remain so neither
# stale issue can silently return.
M03_LINT_PATH_COUNT = 4
M04_OLD_ROOT_PATH = "app/src/main/java/com/ai/assistance/operit/ui/main/OperitApp.kt"
M04_ROOT_PATH = "app/src/main/java/com/kiyori/app/KiyoriApp.kt"
M04_ROOT_PACKAGE = "com.kiyori.app"
M04_MAIN_ACTIVITY_PATH = (
    "app/src/main/java/com/ai/assistance/operit/ui/main/MainActivity.kt"
)
M04_ROOT_HASH_SNAPSHOT = "m04-root-sha256.txt"
M04_ROOT_IMPORT_SNAPSHOT = "m04-root-operit-imports.txt"
M04_OLD_SHELL_STATE_PATH = (
    "app/src/main/java/com/ai/assistance/operit/ui/main/shell/KiyoriShellState.kt"
)
M04_SHELL_STATE_PATH = "app/src/main/java/com/kiyori/app/shell/KiyoriShellState.kt"
M04_SHELL_STATE_PACKAGE = "com.kiyori.app.shell"
M04B_OPERIT_NAVIGATION_POLICY_PATH = (
    "app/src/main/java/com/kiyori/integration/operit/navigation/"
    "OperitNavigationStackPolicy.kt"
)
M04B_OPERIT_NAVIGATION_POLICY_PACKAGE = "com.kiyori.integration.operit.navigation"
M04B_OPERIT_NAVIGATION_POLICY_HASH_SNAPSHOT = (
    "m04b-operit-navigation-policy-sha256.txt"
)
M04B_OPERIT_NAVIGATION_POLICY_IMPORT_SNAPSHOT = (
    "m04b-operit-navigation-policy-imports.txt"
)
M04B_SHELL_STATE_HASH_SNAPSHOT = "m04b-shell-state-sha256.txt"
M04B_BROWSER_EXIT_CONTRACT_PATH = (
    "app/src/main/java/com/kiyori/capability/browser/presentation/"
    "BrowserExitPresentation.kt"
)
M04B_BROWSER_EXIT_CONTRACT_PACKAGE = "com.kiyori.capability.browser.presentation"
M04B_BROWSER_EXIT_CONTRACT_HASH_SNAPSHOT = (
    "m04b-browser-exit-presentation-sha256.txt"
)
M04B_OLD_APP_SHELL_PATH = (
    "app/src/main/java/com/ai/assistance/operit/ui/main/shell/KiyoriAppShell.kt"
)
M04B_APP_SHELL_PATH = "app/src/main/java/com/kiyori/app/shell/KiyoriAppShell.kt"
M04B_APP_SHELL_PACKAGE = "com.kiyori.app.shell"
M04B_APP_SHELL_NORMALIZED_HASH_SNAPSHOT = (
    "m04b-app-shell-normalized-sha256.txt"
)
M04B_APP_SHELL_PROJECT_IMPORT_SNAPSHOT = (
    "m04b-app-shell-operit-imports.txt"
)
M04B_APP_SHELL_TEST_PATH = (
    "app/src/test/java/com/ai/assistance/operit/ui/main/shell/"
    "KiyoriShellStateTest.kt"
)
M04B_OLD_AI_DRAWER_PATH = (
    "app/src/main/java/com/ai/assistance/operit/ui/main/shell/KiyoriAiDrawer.kt"
)
M04B_AI_DRAWER_PATH = "app/src/main/java/com/kiyori/app/shell/KiyoriAiDrawer.kt"
M04B_AI_DRAWER_PACKAGE = "com.kiyori.app.shell"
M04B_AI_DRAWER_NORMALIZED_HASH_SNAPSHOT = (
    "m04b-ai-drawer-normalized-sha256.txt"
)
M04B_AI_DRAWER_PROJECT_IMPORT_SNAPSHOT = (
    "m04b-ai-drawer-operit-imports.txt"
)
M04B_PRIMARY_NAVIGATION_PATH = (
    "app/src/main/java/com/kiyori/app/shell/KiyoriPrimaryNavigation.kt"
)
M04B_PRIMARY_NAVIGATION_PACKAGE = "com.kiyori.app.shell"
M04B_PRIMARY_NAVIGATION_HASH_SNAPSHOT = (
    "m04b-primary-navigation-sha256.txt"
)
M04B_PRIMARY_NAVIGATION_PROJECT_IMPORT_SNAPSHOT = (
    "m04b-primary-navigation-operit-imports.txt"
)
M04B_SOFTWARE_HOME_PATH = (
    "app/src/main/java/com/kiyori/app/shell/KiyoriSoftwareHome.kt"
)
M04B_SOFTWARE_HOME_PACKAGE = "com.kiyori.app.shell"
M04B_SOFTWARE_HOME_HASH_SNAPSHOT = (
    "m04b-software-home-sha256.txt"
)
M04B_SOFTWARE_HOME_PROJECT_IMPORT_SNAPSHOT = (
    "m04b-software-home-operit-imports.txt"
)
M04B_SOFTWARE_HOME_TEST_PATH = (
    "app/src/test/java/com/ai/assistance/operit/ui/main/shell/"
    "KiyoriSoftwareHomeSearchTest.kt"
)
M04B_BROWSER_SEARCH_PATH = (
    "app/src/main/java/com/kiyori/app/shell/KiyoriBrowserSearch.kt"
)
M04B_BROWSER_SEARCH_PACKAGE = "com.kiyori.app.shell"
M04B_BROWSER_SEARCH_HASH_SNAPSHOT = (
    "m04b-browser-search-sha256.txt"
)
M04B_BROWSER_SEARCH_OPERIT_IMPORT_SNAPSHOT = (
    "m04b-browser-search-operit-imports.txt"
)
M04B_BROWSER_SEARCH_CAPABILITY_IMPORT = (
    "com.kiyori.capability.browser.presentation.KiyoriBrowserSearchSource"
)
M04C_OLD_ROUTE_CATALOG_PATH = (
    "app/src/main/java/com/ai/assistance/operit/ui/main/navigation/"
    "AppRouteCatalog.kt"
)
M04C_ROUTE_CATALOG_PATH = (
    "app/src/main/java/com/kiyori/integration/operit/navigation/"
    "AppRouteCatalog.kt"
)
M04C_NAVIGATION_INTEGRATION_PATH = (
    "app/src/main/java/com/kiyori/integration/operit/navigation/"
    "OperitNavigationIntegration.kt"
)
M04C_NAVIGATION_INTEGRATION_PACKAGE = (
    "com.kiyori.integration.operit.navigation"
)
M04C_ROUTE_CATALOG_HASH_SNAPSHOT = "m04c-route-catalog-sha256.txt"
M04C_ROUTE_CATALOG_OPERIT_IMPORT_SNAPSHOT = (
    "m04c-route-catalog-operit-imports.txt"
)
M04C_NAVIGATION_INTEGRATION_HASH_SNAPSHOT = (
    "m04c-navigation-integration-sha256.txt"
)
M04C_NAVIGATION_INTEGRATION_OPERIT_IMPORT_SNAPSHOT = (
    "m04c-navigation-integration-operit-imports.txt"
)
M04C_SETTINGS_TEST_PATH = (
    "app/src/test/java/com/ai/assistance/operit/ui/main/shell/"
    "KiyoriSettingsPagesTest.kt"
)
M04D_MAIN_PENDING_REQUESTS_PATH = (
    "app/src/main/java/com/kiyori/app/startup/"
    "KiyoriMainPendingRequests.kt"
)
M04D_MAIN_PENDING_REQUESTS_PACKAGE = "com.kiyori.app.startup"
M04D_MAIN_PENDING_REQUESTS_TEST_PATH = (
    "app/src/test/java/com/kiyori/app/startup/"
    "KiyoriMainPendingRequestsTest.kt"
)
M04D_MAIN_PENDING_REQUESTS_HASH_SNAPSHOT = (
    "m04d-main-pending-requests-sha256.txt"
)
M04D_MAIN_PENDING_REQUESTS_IMPORT_SNAPSHOT = (
    "m04d-main-pending-requests-project-imports.txt"
)
M04D_MAIN_INTENT_DECODER_PATH = (
    "app/src/main/java/com/kiyori/app/startup/"
    "KiyoriMainIntentDecoder.kt"
)
M04D_MAIN_INTENT_DECODER_PACKAGE = "com.kiyori.app.startup"
M04D_MAIN_INTENT_DECODER_TEST_PATH = (
    "app/src/test/java/com/kiyori/app/startup/"
    "KiyoriMainIntentDecoderTest.kt"
)
M04D_MAIN_INTENT_DECODER_HASH_SNAPSHOT = (
    "m04d-main-intent-decoder-sha256.txt"
)
M04D_MAIN_INTENT_DECODER_IMPORT_SNAPSHOT = (
    "m04d-main-intent-decoder-project-imports.txt"
)
M04D_MAIN_INTENT_CONTRACT_CONSTANTS = (
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
M04D_MAIN_DISPLAY_COORDINATOR_PATH = (
    "app/src/main/java/com/kiyori/app/startup/"
    "KiyoriMainDisplayCoordinator.kt"
)
M04D_MAIN_DISPLAY_COORDINATOR_PACKAGE = "com.kiyori.app.startup"
M04D_MAIN_DISPLAY_COORDINATOR_TEST_PATH = (
    "app/src/test/java/com/kiyori/app/startup/"
    "KiyoriMainDisplayCoordinatorTest.kt"
)
M04D_MAIN_DISPLAY_COORDINATOR_HASH_SNAPSHOT = (
    "m04d-main-display-coordinator-sha256.txt"
)
M04D_MAIN_DISPLAY_COORDINATOR_IMPORT_SNAPSHOT = (
    "m04d-main-display-coordinator-project-imports.txt"
)
M04D_MAIN_SHARED_CONTENT_COORDINATOR_PATH = (
    "app/src/main/java/com/kiyori/app/startup/"
    "KiyoriMainSharedContentCoordinator.kt"
)
M04D_MAIN_SHARED_CONTENT_COORDINATOR_PACKAGE = "com.kiyori.app.startup"
M04D_MAIN_SHARED_CONTENT_COORDINATOR_TEST_PATH = (
    "app/src/test/java/com/kiyori/app/startup/"
    "KiyoriMainSharedContentCoordinatorTest.kt"
)
M04D_MAIN_SHARED_CONTENT_COORDINATOR_HASH_SNAPSHOT = (
    "m04d-main-shared-content-coordinator-sha256.txt"
)
M04D_MAIN_SHARED_CONTENT_COORDINATOR_IMPORT_SNAPSHOT = (
    "m04d-main-shared-content-coordinator-project-imports.txt"
)
M04D_MAIN_TASK_VISIBILITY_COORDINATOR_PATH = (
    "app/src/main/java/com/kiyori/app/startup/"
    "KiyoriMainTaskVisibilityCoordinator.kt"
)
M04D_MAIN_TASK_VISIBILITY_COORDINATOR_PACKAGE = "com.kiyori.app.startup"
M04D_MAIN_TASK_VISIBILITY_COORDINATOR_TEST_PATH = (
    "app/src/test/java/com/kiyori/app/startup/"
    "KiyoriMainTaskVisibilityCoordinatorTest.kt"
)
M04D_MAIN_TASK_VISIBILITY_COORDINATOR_HASH_SNAPSHOT = (
    "m04d-main-task-visibility-coordinator-sha256.txt"
)
M04D_MAIN_TASK_VISIBILITY_COORDINATOR_IMPORT_SNAPSHOT = (
    "m04d-main-task-visibility-coordinator-project-imports.txt"
)
M04D_MAIN_ORIENTATION_COORDINATOR_PATH = (
    "app/src/main/java/com/kiyori/app/startup/"
    "KiyoriMainOrientationCoordinator.kt"
)
M04D_MAIN_ORIENTATION_COORDINATOR_PACKAGE = "com.kiyori.app.startup"
M04D_MAIN_ORIENTATION_COORDINATOR_TEST_PATH = (
    "app/src/test/java/com/kiyori/app/startup/"
    "KiyoriMainOrientationCoordinatorTest.kt"
)
M04D_MAIN_ORIENTATION_COORDINATOR_HASH_SNAPSHOT = (
    "m04d-main-orientation-coordinator-sha256.txt"
)
M04D_MAIN_ORIENTATION_COORDINATOR_IMPORT_SNAPSHOT = (
    "m04d-main-orientation-coordinator-project-imports.txt"
)
M04D_MAIN_NOTIFICATION_PERMISSION_COORDINATOR_PATH = (
    "app/src/main/java/com/kiyori/app/startup/"
    "KiyoriMainNotificationPermissionCoordinator.kt"
)
M04D_MAIN_NOTIFICATION_PERMISSION_COORDINATOR_PACKAGE = "com.kiyori.app.startup"
M04D_MAIN_NOTIFICATION_PERMISSION_COORDINATOR_TEST_PATH = (
    "app/src/test/java/com/kiyori/app/startup/"
    "KiyoriMainNotificationPermissionCoordinatorTest.kt"
)
M04D_MAIN_NOTIFICATION_PERMISSION_COORDINATOR_HASH_SNAPSHOT = (
    "m04d-main-notification-permission-coordinator-sha256.txt"
)
M04D_MAIN_NOTIFICATION_PERMISSION_COORDINATOR_IMPORT_SNAPSHOT = (
    "m04d-main-notification-permission-coordinator-project-imports.txt"
)
M04D_MAIN_STARTUP_GATE_COORDINATOR_PATH = (
    "app/src/main/java/com/kiyori/app/startup/"
    "KiyoriMainStartupGateCoordinator.kt"
)
M04D_MAIN_STARTUP_GATE_COORDINATOR_PACKAGE = "com.kiyori.app.startup"
M04D_MAIN_STARTUP_GATE_COORDINATOR_TEST_PATH = (
    "app/src/test/java/com/kiyori/app/startup/"
    "KiyoriMainStartupGateCoordinatorTest.kt"
)
M04D_MAIN_STARTUP_GATE_COORDINATOR_HASH_SNAPSHOT = (
    "m04d-main-startup-gate-coordinator-sha256.txt"
)
M04D_MAIN_STARTUP_GATE_COORDINATOR_IMPORT_SNAPSHOT = (
    "m04d-main-startup-gate-coordinator-project-imports.txt"
)
M04D_MAIN_CONTENT_HOST_PATH = (
    "app/src/main/java/com/kiyori/app/startup/KiyoriMainContentHost.kt"
)
M04D_MAIN_CONTENT_HOST_PACKAGE = "com.kiyori.app.startup"
M04D_MAIN_CONTENT_HOST_TEST_PATH = (
    "app/src/test/java/com/kiyori/app/startup/KiyoriMainContentHostTest.kt"
)
M04D_MAIN_CONTENT_HOST_HASH_SNAPSHOT = "m04d-main-content-host-sha256.txt"
M04D_MAIN_CONTENT_HOST_IMPORT_SNAPSHOT = (
    "m04d-main-content-host-project-imports.txt"
)
M04E_MAIN_ACTIVITY_OWNERSHIP_ID = "operit-main-activity-compatibility"
M04E_MAIN_ACTIVITY_OWNER = "kiyori-app-entry-compatibility"
M04E_MAIN_ACTIVITY_PHASE = "m04e"
M04E_MAIN_ACTIVITY_SYNC_ZONE = "D"
M04E_MAIN_ACTIVITY_PACKAGE = "com.ai.assistance.operit.ui.main"
M04E_MAIN_ACTIVITY_CLASS = "MainActivity"
M04E_MAIN_ACTIVITY_MANIFEST_NAME = ".ui.main.MainActivity"
M04E_MAIN_ACTIVITY_IMPORT_SNAPSHOT = (
    "m04e-main-activity-project-imports.txt"
)
M04E_MAIN_ACTIVITY_ALLOWED_IMPORT_ROOTS = (
    "com.ai.assistance.operit",
    "com.kiyori.app",
    "com.kiyori.platform",
)
M04E_MAIN_ACTIVITY_FORBIDDEN_IMPORT_ROOTS = (
    "com.kiyori.feature",
)
M04E_ARCHITECTURE_TEST_PATH = "ci/test/test_architecture_boundaries.py"
M05A1_DESIGN_PACKAGE = "com.kiyori.design.theme"
M05A1_COLOR_SCHEMES_PATH = (
    "app/src/main/java/com/kiyori/design/theme/KiyoriColorSchemes.kt"
)
M05A1_BROWSER_THEME_PATH = (
    "app/src/main/java/com/kiyori/design/theme/KiyoriBrowserTheme.kt"
)
M05A1_SETTINGS_THEME_PATH = (
    "app/src/main/java/com/kiyori/design/theme/KiyoriSettingsTheme.kt"
)
M05A1_OLD_COLOR_RESOLVER_PATH = (
    "app/src/main/java/com/ai/assistance/operit/ui/theme/"
    "ThemeColorSchemeResolver.kt"
)
M05A1_OLD_BROWSER_THEME_PATH = (
    "app/src/main/java/com/ai/assistance/operit/ui/theme/KiyoriBrowserTheme.kt"
)
M05A1_OLD_SETTINGS_THEME_PATH = (
    "app/src/main/java/com/ai/assistance/operit/ui/theme/KiyoriSettingsTheme.kt"
)
M05A1_DESIGN_TEST_PATH = (
    "app/src/test/java/com/kiyori/design/theme/KiyoriDesignThemeTest.kt"
)
M05A1_OLD_THEME_TEST_PATH = (
    "app/src/test/java/com/ai/assistance/operit/ui/theme/KiyoriThemeTest.kt"
)
M05A1_ARCHITECTURE_TEST_PATH = "ci/test/test_architecture_boundaries.py"
M05A1_DESIGN_FILES = (
    (
        "ColorScheme",
        M05A1_COLOR_SCHEMES_PATH,
        "m05a1-color-schemes-sha256.txt",
    ),
    (
        "Browser theme",
        M05A1_BROWSER_THEME_PATH,
        "m05a1-browser-theme-sha256.txt",
    ),
    (
        "Settings theme",
        M05A1_SETTINGS_THEME_PATH,
        "m05a1-settings-theme-sha256.txt",
    ),
)
M05A1_DECLARATION_OWNERS = {
    "KiyoriBrowserLightColorScheme": M05A1_COLOR_SCHEMES_PATH,
    "KiyoriBrowserDarkColorScheme": M05A1_COLOR_SCHEMES_PATH,
    "KiyoriLightColorScheme": M05A1_COLOR_SCHEMES_PATH,
    "KiyoriDarkColorScheme": M05A1_COLOR_SCHEMES_PATH,
    "resolveKiyoriColorScheme": M05A1_COLOR_SCHEMES_PATH,
    "KiyoriBrowserTheme": M05A1_BROWSER_THEME_PATH,
    "KiyoriSettingsColors": M05A1_SETTINGS_THEME_PATH,
    "LocalKiyoriSettingsColors": M05A1_SETTINGS_THEME_PATH,
    "KiyoriSettingsTheme": M05A1_SETTINGS_THEME_PATH,
    "resolveKiyoriSettingsColors": M05A1_SETTINGS_THEME_PATH,
}
M05A1_EXPECTED_IMPORT_CONSUMERS = {
    "KiyoriBrowserTheme": {
        "app/src/main/java/com/kiyori/app/shell/KiyoriAppShell.kt",
        "app/src/main/java/com/ai/assistance/operit/ui/features/player/"
        "PlayerActivity.kt",
        "app/src/main/java/com/ai/assistance/operit/ui/features/websession/"
        "browser/WebSessionBrowserScreen.kt",
    },
    "KiyoriSettingsTheme": {
        "app/src/main/java/com/kiyori/app/shell/KiyoriAppShell.kt",
        "app/src/main/java/com/ai/assistance/operit/ui/main/components/"
        "AppContent.kt",
        "app/src/main/java/com/ai/assistance/operit/ui/main/shell/"
        "KiyoriCollapsingSettingsPage.kt",
        "app/src/main/java/com/ai/assistance/operit/ui/main/shell/"
        "KiyoriSettingsHomePage.kt",
    },
    "LocalKiyoriSettingsColors": {
        "app/src/main/java/com/ai/assistance/operit/ui/main/shell/"
        "KiyoriAdBlockSettingsPage.kt",
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
        "app/src/main/java/com/ai/assistance/operit/ui/features/settings/"
        "screens/theme/ThemeSettingsContentEditor.kt",
        "app/src/main/java/com/ai/assistance/operit/ui/features/settings/"
        "screens/theme/ThemeSettingsTabs.kt",
    },
    "KiyoriLightColorScheme": {
        "app/src/main/java/com/ai/assistance/operit/ui/floating/"
        "FloatingWindowTheme.kt",
    },
    "resolveKiyoriColorScheme": {
        M05A1_OLD_COLOR_RESOLVER_PATH,
    },
}
M05A1_FORBIDDEN_DESIGN_TOKENS = (
    "com.ai.assistance.operit",
    "UserPreferencesManager",
    "ThemePreferenceSnapshot",
    "DataStore",
    "SharedPreferences",
    "Application",
    "ComponentActivity",
    "Activity",
    "Window",
    "enableEdgeToEdge",
    "WindowInsets",
    "Lifecycle",
    "Permission",
    "OperitPaths",
    "Repository",
    "ViewModel",
)
M05A2_SEMANTIC_COLORS_PATH = (
    "app/src/main/java/com/kiyori/design/theme/KiyoriSemanticColors.kt"
)
M05A2_SEMANTIC_THEME_PATH = (
    "app/src/main/java/com/kiyori/design/theme/KiyoriSemanticTheme.kt"
)
M05A2_OLD_SEMANTIC_THEME_PATH = (
    "app/src/main/java/com/ai/assistance/operit/ui/theme/"
    "KiyoriSemanticTheme.kt"
)
M05A2_SEMANTIC_COLORS_HASH_SNAPSHOT = (
    "m05a2-semantic-colors-sha256.txt"
)
M05A2_SEMANTIC_THEME_HASH_SNAPSHOT = (
    "m05a2-semantic-theme-sha256.txt"
)
M05A2_CONSUMER_IMPORT_SNAPSHOT = (
    "m05a2-semantic-consumer-imports.txt"
)
M05A2_DESIGN_PACKAGE = "com.kiyori.design.theme"
M05A2_PRODUCTION_CONSUMER_COUNT = 52
M05A2_EXTERNAL_TEST_CONSUMER_COUNT = 4
M05A2_CONSUMER_IMPORT_COUNT = 99
M05A2_MOVED_IMPORT_SYMBOLS = {
    "KiyoriSemanticTone",
    "kiyoriSemanticToneForStableId",
    "KiyoriBottomNavigationSelectedFillColor",
    "resolveColors",
    "kiyoriWeatherSunColor",
}
M05A2_DECLARATION_OWNERS = {
    "KiyoriSemanticTone": M05A2_SEMANTIC_COLORS_PATH,
    "kiyoriSemanticToneForStableId": M05A2_SEMANTIC_COLORS_PATH,
    "KiyoriSemanticColors": M05A2_SEMANTIC_COLORS_PATH,
    "resolveKiyoriSemanticColors": M05A2_SEMANTIC_COLORS_PATH,
    "KiyoriBottomNavigationSelectedFillColor": M05A2_SEMANTIC_COLORS_PATH,
    "resolveKiyoriWeatherSunColor": M05A2_SEMANTIC_COLORS_PATH,
    "resolveColors": M05A2_SEMANTIC_THEME_PATH,
    "kiyoriWeatherSunColor": M05A2_SEMANTIC_THEME_PATH,
}
M05A2_SEMANTIC_COLOR_LITERALS = (
    "FF90CAF9",
    "FF163044",
    "FF67D7A5",
    "FF17382D",
    "FFB7A7FF",
    "FF2D254A",
    "FFFFB76A",
    "FF432B16",
    "FFFF8A8A",
    "FF472323",
    "FF65D3E8",
    "FF173942",
    "FFF49AC0",
    "FF452336",
    "FF1E88E5",
    "FFE8F3FE",
    "FF1B8D5F",
    "FFE6F5EE",
    "FF7056D9",
    "FFF0ECFF",
    "FFC66A13",
    "FFFFF0DF",
    "FFD64545",
    "FFFDEAEA",
    "FF168CA7",
    "FFE4F5F8",
    "FFC8467D",
    "FFFCEAF2",
    "FFFFC153",
    "FFFFD166",
    "FFC57C00",
)
M05A2_EXPECTED_QUALIFIED_REFERENCES = {
    "KiyoriSemanticColors": {
        "app/src/main/java/com/ai/assistance/operit/ui/features/player/"
        "PlayerScreen.kt",
    },
}
M05A2_M04B_SEMANTIC_SNAPSHOT_REPLACEMENTS = {
    "m04b-ai-drawer-operit-imports.txt": {
        "com.ai.assistance.operit.ui.theme.KiyoriSemanticTone":
            "com.kiyori.design.theme.KiyoriSemanticTone",
        "com.ai.assistance.operit.ui.theme.resolveColors":
            "com.kiyori.design.theme.resolveColors",
    },
    "m04b-primary-navigation-operit-imports.txt": {
        "com.ai.assistance.operit.ui.theme.KiyoriBottomNavigationSelectedFillColor":
            "com.kiyori.design.theme.KiyoriBottomNavigationSelectedFillColor",
    },
    "m04b-software-home-operit-imports.txt": {
        "com.ai.assistance.operit.ui.theme.KiyoriSemanticTone":
            "com.kiyori.design.theme.KiyoriSemanticTone",
        "com.ai.assistance.operit.ui.theme.kiyoriWeatherSunColor":
            "com.kiyori.design.theme.kiyoriWeatherSunColor",
        "com.ai.assistance.operit.ui.theme.resolveColors":
            "com.kiyori.design.theme.resolveColors",
    },
}
M05A2_ARCHITECTURE_TEST_PATH = "ci/test/test_architecture_boundaries.py"
M05A3_DESIGN_THEME_PATH = (
    "app/src/main/java/com/kiyori/design/theme/KiyoriTheme.kt"
)
M05A3_TYPOGRAPHY_PATH = (
    "app/src/main/java/com/kiyori/design/theme/KiyoriTypography.kt"
)
M05A3_APP_THEME_PATH = (
    "app/src/main/java/com/kiyori/app/theme/KiyoriTheme.kt"
)
M05A3_SYSTEM_BARS_PATH = (
    "app/src/main/java/com/kiyori/platform/window/"
    "KiyoriApplicationSystemBars.kt"
)
M05A3_OLD_THEME_PATH = (
    "app/src/main/java/com/ai/assistance/operit/ui/theme/Theme.kt"
)
M05A3_TYPE_PATH = (
    "app/src/main/java/com/ai/assistance/operit/ui/theme/Type.kt"
)
M05A3_LIQUID_GLASS_PATH = (
    "app/src/main/java/com/ai/assistance/operit/ui/theme/LiquidGlass.kt"
)
M05A3_WATER_GLASS_PATH = (
    "app/src/main/java/com/ai/assistance/operit/ui/theme/WaterGlass.kt"
)
M05A3_PLAYER_ACTIVITY_PATH = (
    "app/src/main/java/com/ai/assistance/operit/ui/features/player/"
    "PlayerActivity.kt"
)
M05A3_UTILITY_THEME_PATH = (
    "app/src/main/java/com/ai/assistance/operit/ui/common/"
    "OperitUtilityTheme.kt"
)
M05A3_FLOATING_THEME_PATH = (
    "app/src/main/java/com/ai/assistance/operit/ui/floating/"
    "FloatingWindowTheme.kt"
)
M05A3_WIDGET_THEME_HOST_PATH = (
    "app/src/main/java/com/ai/assistance/operit/widget/"
    "ToolPkgDesktopWidgetConfigActivity.kt"
)
M05A3_ROOT_HASH_SNAPSHOT = "m05a3-root-theme-sha256.txt"
M05A3_RESOURCE_HASH_SNAPSHOT = "m05a3-theme-resources-sha256.txt"
M05A3_CONSUMER_IMPORT_SNAPSHOT = "m05a3-theme-consumer-imports.txt"
M05A3_THEME_RESOURCE_PATHS = (
    "app/src/main/res/values/themes.xml",
    "app/src/main/res/values-night/themes.xml",
    "app/src/main/res/values-v27/themes.xml",
    "app/src/main/res/values-night-v27/themes.xml",
    "app/src/main/res/values-v29/themes.xml",
    "app/src/main/res/values-night-v29/themes.xml",
)
M05A3_HASHED_PATHS = (
    M05A3_DESIGN_THEME_PATH,
    M05A3_TYPOGRAPHY_PATH,
    M05A3_APP_THEME_PATH,
    M05A3_SYSTEM_BARS_PATH,
    M05A3_TYPE_PATH,
    M05A3_LIQUID_GLASS_PATH,
    M05A3_WATER_GLASS_PATH,
    M05A3_PLAYER_ACTIVITY_PATH,
    M05A3_UTILITY_THEME_PATH,
    M05A3_FLOATING_THEME_PATH,
    M04_MAIN_ACTIVITY_PATH,
    M05A3_WIDGET_THEME_HOST_PATH,
)
M05A3_DESIGN_PACKAGE = "com.kiyori.design.theme"
M05A3_APP_THEME_PACKAGE = "com.kiyori.app.theme"
M05A3_SYSTEM_BARS_PACKAGE = "com.kiyori.platform.window"
M05A3_NEW_STYLE = "Theme.Kiyori"
M05A3_OLD_STYLE = "Theme.Operit"
M05A3_STYLE_DECLARATION_COUNT = 6
M05A3_MANIFEST_STYLE_REFERENCE_COUNT = 6
M05A3_CONSUMER_IMPORT_COUNT = 7
M05A3_PLAYER_BASELINE_HASH = (
    "0958C96D76C5C30E98EA84F08AC29CA576FA263C497AD1976BFEF9B4E326B7CA"
)
M05A3_EXPECTED_APP_HOST_PROJECT_IMPORTS = (
    "com.ai.assistance.operit.data.preferences.UserPreferencesManager",
    "com.ai.assistance.operit.ui.theme.LocalLiquidGlassBackdrop",
    "com.ai.assistance.operit.ui.theme.LocalWaterGlassState",
    "com.ai.assistance.operit.ui.theme.createCustomTypography",
    "com.ai.assistance.operit.ui.theme.isWaterGlassSupported",
    "com.kiyori.design.theme.resolveKiyoriColorScheme",
    "com.kiyori.platform.window.KiyoriApplicationSystemBars",
)
M05A3_APP_THEME_EXCEPTION_REASON = (
    "M-05A3 isolates the unique Kiyori app theme host while existing "
    "UserPreferences, configured typography, and Glass state owners remain "
    "in Operit UI; ARCH042 locks the exact bridge until settings and visual "
    "effect ownership migrate."
)
M05A3_WIDGET_EXCEPTION_REASON = (
    "M-05A3 keeps the stable Operit widget configuration Activity while it "
    "reuses the unique Kiyori app theme host; ARCH042 locks this one app "
    "dependency until widget integration ownership migrates."
)
M05A3_APP_THEME_EXCEPTION_EXPIRY = "M-06-settings-design-migration"
M05A3_WIDGET_EXCEPTION_EXPIRY = "M-07-widget-integration"
M05A3_APP_THEME_EXCEPTION_RULE = "ARCH004"
M05A3_WIDGET_EXCEPTION_RULE = "ARCH001"
M05A3_ARCHITECTURE_TEST_PATH = "ci/test/test_architecture_boundaries.py"
M05B_LOGGER_PATH = (
    "app/src/main/java/com/kiyori/platform/logging/KiyoriLogger.kt"
)
M05B_FORMATTER_PATH = (
    "app/src/main/java/com/kiyori/platform/logging/"
    "KiyoriLogTextFormatter.kt"
)
M05B_LEGACY_FACADE_PATH = (
    "app/src/main/java/com/ai/assistance/operit/util/AppLogger.kt"
)
M05B_OLD_FORMATTER_PATH = (
    "app/src/main/java/com/ai/assistance/operit/util/"
    "ThrowableTextFormatter.kt"
)
M05B_CRASH_REPORT_STORE_PATH = (
    "app/src/main/java/com/ai/assistance/operit/util/crash/"
    "CrashReportStore.kt"
)
M05B_MEMORY_PROVIDER_PATH = (
    "app/src/main/java/com/ai/assistance/operit/provider/"
    "MemoryDocumentsProvider.kt"
)
M05B_FORMATTER_TEST_PATH = (
    "app/src/test/java/com/kiyori/platform/logging/"
    "KiyoriLogTextFormatterTest.kt"
)
M05B_HASH_SNAPSHOT = "m05b-platform-logging-sha256.txt"
M05B_KIYORI_CONSUMER_SNAPSHOT = (
    "m05b-kiyori-logger-consumers.txt"
)
M05B_LOGGER_PACKAGE = "com.kiyori.platform.logging"
M05B_LEGACY_FACADE_PACKAGE = "com.ai.assistance.operit.util"
M05B_NEW_LOGGER_IMPORT = "com.kiyori.platform.logging.KiyoriLogger"
M05B_OLD_LOGGER_IMPORT = "com.ai.assistance.operit.util.AppLogger"
M05B_NEW_FORMATTER_IMPORT = (
    "com.kiyori.platform.logging.KiyoriLogTextFormatter"
)
M05B_OLD_FORMATTER_IMPORT = (
    "com.ai.assistance.operit.util.ThrowableTextFormatter"
)
M05B_KIYORI_CONSUMER_COUNT = 8
M05B_HASHED_PATHS = (
    M05B_LOGGER_PATH,
    M05B_FORMATTER_PATH,
    M05B_LEGACY_FACADE_PATH,
    M05B_CRASH_REPORT_STORE_PATH,
)
M05B_ARCHITECTURE_TEST_PATH = "ci/test/test_architecture_boundaries.py"
M05B_DISPLAY_EXCEPTION_PATH = M04D_MAIN_DISPLAY_COORDINATOR_PATH
M05C_PLATFORM_LIFECYCLE_PATH = (
    "app/src/main/java/com/kiyori/platform/lifecycle/"
    "KiyoriActivityLifecycle.kt"
)
M05C_OPERIT_INTEGRATION_PATH = (
    "app/src/main/java/com/ai/assistance/operit/core/application/"
    "OperitActivityLifecycleIntegration.kt"
)
M05C_LEGACY_FACADE_PATH = (
    "app/src/main/java/com/ai/assistance/operit/core/application/"
    "ActivityLifecycleManager.kt"
)
M05C_FACTS_TEST_PATH = (
    "app/src/test/java/com/kiyori/platform/lifecycle/"
    "KiyoriActivityLifecycleFactsTest.kt"
)
M05C_HASH_SNAPSHOT = "m05c-platform-lifecycle-sha256.txt"
M05C_LEGACY_CONSUMER_SNAPSHOT = (
    "m05c-legacy-lifecycle-consumers.txt"
)
M05C_PLATFORM_PACKAGE = "com.kiyori.platform.lifecycle"
M05C_OPERIT_PACKAGE = "com.ai.assistance.operit.core.application"
M05C_PLATFORM_IMPORT = (
    "com.kiyori.platform.lifecycle.KiyoriActivityLifecycle"
)
M05C_OBSERVER_IMPORT = (
    "com.kiyori.platform.lifecycle.KiyoriActivityLifecycleObserver"
)
M05C_LEGACY_IMPORT = (
    "com.ai.assistance.operit.core.application.ActivityLifecycleManager"
)
M05C_LEGACY_CONSUMER_COUNT = 14
M05C_HASHED_PATHS = (
    M05C_PLATFORM_LIFECYCLE_PATH,
    M05C_OPERIT_INTEGRATION_PATH,
    M05C_LEGACY_FACADE_PATH,
)
M05C_LEGACY_CONSUMER_PATHS = (
    M03_APPLICATION_PATH,
    "app/src/main/java/com/ai/assistance/operit/api/chat/"
    "AIForegroundService.kt",
    "app/src/main/java/com/ai/assistance/operit/api/chat/"
    "EnhancedAIService.kt",
    "app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/"
    "standard/StandardBrowserSessionTools.kt",
    "app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/"
    "websession/browser/BrowserDownloadSupport.kt",
    "app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/"
    "websession/browser/BrowserWebViewSupport.kt",
    "app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/"
    "websession/browser/WebSessionPermissionRequestActivity.kt",
    "app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/"
    "websession/userscript/install/UserscriptImportCoordinator.kt",
    "app/src/main/java/com/ai/assistance/operit/core/tools/javascript/"
    "JsEngine.kt",
    M04_MAIN_ACTIVITY_PATH,
    "app/src/main/java/com/ai/assistance/operit/util/crash/"
    "PlayerCrashCoordinator.kt",
    "app/src/main/java/com/ai/assistance/operit/core/tools/javascript/"
    "README.md",
    "examples/java_bridge.js",
    "examples/java_bridge.ts",
)
M05C_ARCHITECTURE_TEST_PATH = "ci/test/test_architecture_boundaries.py"
M05D_PLATFORM_PERMISSION_PATH = (
    "app/src/main/java/com/kiyori/platform/permission/"
    "KiyoriNotificationPermissionCapability.kt"
)
M05D_OPERIT_RESOURCE_BRIDGE_PATH = (
    "app/src/main/java/com/kiyori/integration/operit/permission/"
    "OperitNotificationPermissionResources.kt"
)
M05D_COORDINATOR_PATH = M04D_MAIN_NOTIFICATION_PERMISSION_COORDINATOR_PATH
M05D_PREFERENCES_PATH = (
    "app/src/main/java/com/ai/assistance/operit/data/preferences/"
    "AndroidPermissionPreferences.kt"
)
M05D_PERMISSION_TEST_PATH = (
    "app/src/test/java/com/kiyori/platform/permission/"
    "KiyoriNotificationPermissionCapabilityTest.kt"
)
M05D_OLD_PERMISSION_TEST_PATH = (
    M04D_MAIN_NOTIFICATION_PERMISSION_COORDINATOR_TEST_PATH
)
M05D_HASH_SNAPSHOT = "m05d-notification-permission-sha256.txt"
M05D_DIRECT_CONSUMER_SNAPSHOT = (
    "m05d-direct-notification-permission-consumers.txt"
)
M05D_PLATFORM_PACKAGE = "com.kiyori.platform.permission"
M05D_OPERIT_RESOURCE_BRIDGE_PACKAGE = (
    "com.kiyori.integration.operit.permission"
)
M05D_PLATFORM_CAPABILITY_IMPORT = (
    "com.kiyori.platform.permission.KiyoriNotificationPermissionCapability"
)
M05D_PLATFORM_ACTION_IMPORT = (
    "com.kiyori.platform.permission.KiyoriNotificationPermissionAction"
)
M05D_OPERIT_RESOURCE_BRIDGE_IMPORT = (
    "com.kiyori.integration.operit.permission."
    "OperitNotificationPermissionResources"
)
M05D_HASHED_PATHS = (
    M05D_PLATFORM_PERMISSION_PATH,
    M05D_OPERIT_RESOURCE_BRIDGE_PATH,
    M05D_COORDINATOR_PATH,
    M05D_PREFERENCES_PATH,
)
M05D_DIRECT_CONSUMER_PATHS = (
    "app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/"
    "websession/browser/BrowserDownloadRuntime.kt",
    "app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/"
    "websession/userscript/runtime/WebSessionUserscriptManager.kt",
    M05D_PLATFORM_PERMISSION_PATH,
)
KIYORI_FIRST_RUN_CONTRACT_PATH = (
    "app/src/main/java/com/kiyori/integration/operit/onboarding/"
    "KiyoriOnboardingContract.kt"
)
KIYORI_FIRST_RUN_PREFERENCES_PATH = (
    "app/src/main/java/com/kiyori/integration/operit/onboarding/"
    "KiyoriOnboardingPreferences.kt"
)
KIYORI_FIRST_RUN_PERMISSIONS_PATH = (
    "app/src/main/java/com/kiyori/integration/operit/onboarding/"
    "KiyoriOnboardingPermissions.kt"
)
KIYORI_FIRST_RUN_SCREEN_PATH = (
    "app/src/main/java/com/kiyori/integration/operit/onboarding/"
    "KiyoriOnboardingScreen.kt"
)
KIYORI_FIRST_RUN_AGREEMENT_PATH = (
    "app/src/main/java/com/ai/assistance/operit/ui/features/agreement/screens/"
    "KiyoriAgreementScreen.kt"
)
KIYORI_FIRST_RUN_CONTRACT_TEST_PATH = (
    "app/src/test/java/com/kiyori/integration/operit/onboarding/"
    "KiyoriOnboardingContractTest.kt"
)
KIYORI_FIRST_RUN_AGREEMENT_TEST_PATH = (
    "app/src/test/java/com/ai/assistance/operit/ui/features/agreement/screens/"
    "KiyoriAgreementReadinessTest.kt"
)
KIYORI_ACCESSIBILITY_SUPPORT_APK_PATH = (
    "app/src/main/assets/accessibility.apk"
)
KIYORI_ACCESSIBILITY_SUPPORT_VERSION_PATH = (
    "app/src/main/assets/accessibility_version.txt"
)
KIYORI_ACCESSIBILITY_SUPPORT_HASH_PATH = (
    "config/architecture/kiyori-accessibility-support-sha256.txt"
)
KIYORI_LAUNCHER_FOREGROUND_PATH = (
    "app/src/main/res/drawable-nodpi/ic_kiyori_launcher_foreground.png"
)
KIYORI_FIRST_RUN_LEGACY_PATHS = (
    M04D_MAIN_NOTIFICATION_PERMISSION_COORDINATOR_PATH,
    M05D_PLATFORM_PERMISSION_PATH,
    M05D_OPERIT_RESOURCE_BRIDGE_PATH,
    "app/src/main/java/com/ai/assistance/operit/ui/features/agreement/screens/"
    "AgreementScreen.kt",
    "app/src/main/java/com/ai/assistance/operit/ui/features/permission/screens/"
    "PermissionGuideScreen.kt",
    "app/src/main/java/com/ai/assistance/operit/ui/features/permission/viewmodel/"
    "PermissionGuideViewModel.kt",
)
M05D_ARCHITECTURE_TEST_PATH = "ci/test/test_architecture_boundaries.py"
M05E_KIYORI_PATHS_PATH = (
    "app/src/main/java/com/kiyori/platform/storage/KiyoriPaths.kt"
)
M05E_KIYORI_BACKUP_PATHS_PATH = (
    "app/src/main/java/com/kiyori/platform/storage/KiyoriBackupPaths.kt"
)
M05E_OPERIT_PATHS_PATH = (
    "app/src/main/java/com/ai/assistance/operit/util/OperitPaths.kt"
)
M05E_OPERIT_BACKUP_DIRS_PATH = (
    "app/src/main/java/com/ai/assistance/operit/data/backup/"
    "OperitBackupDirs.kt"
)
M05E_PATHS_TEST_PATH = (
    "app/src/test/java/com/kiyori/platform/storage/KiyoriPathsTest.kt"
)
M05E_HASH_SNAPSHOT = "m05e-storage-paths-sha256.txt"
M05E_DIRECT_PATH_CONSUMER_SNAPSHOT = (
    "m05e-direct-kiyori-path-consumers.txt"
)
M05E_DIRECT_BACKUP_CONSUMER_SNAPSHOT = (
    "m05e-direct-kiyori-backup-path-consumers.txt"
)
M05E_LEGACY_OPERIT_CONSUMER_SNAPSHOT = (
    "m05e-legacy-operit-path-consumers.txt"
)
M05E_KIYORI_PATHS_IMPORT = "com.kiyori.platform.storage.KiyoriPaths"
M05E_KIYORI_BACKUP_PATHS_IMPORT = (
    "com.kiyori.platform.storage.KiyoriBackupPaths"
)
M05E_OPERIT_PATHS_IMPORT = "com.ai.assistance.operit.util.OperitPaths"
M05E_OPERIT_BACKUP_DIRS_IMPORT = (
    "com.ai.assistance.operit.data.backup.OperitBackupDirs"
)
M05E_HASHED_PATHS = (
    M05E_KIYORI_PATHS_PATH,
    M05E_KIYORI_BACKUP_PATHS_PATH,
    M05E_OPERIT_PATHS_PATH,
    M05E_OPERIT_BACKUP_DIRS_PATH,
)
M05E_DIRECT_PATH_CONSUMER_PATHS = (
    M05E_OPERIT_PATHS_PATH,
    M05E_KIYORI_BACKUP_PATHS_PATH,
    M05B_LEGACY_FACADE_PATH,
    M03_APPLICATION_PATH,
    "app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/"
    "websession/browser/BrowserDownloadSupport.kt",
    "app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/"
    "websession/userscript/storage/UserscriptJsonStore.kt",
    "app/src/main/java/com/ai/assistance/operit/core/tools/packTool/"
    "PackageManager.kt",
    "app/src/main/java/com/ai/assistance/operit/core/tools/packTool/"
    "ToolPkgArtifactBuilder.kt",
    "app/src/main/java/com/ai/assistance/operit/core/tools/packTool/"
    "ToolPkgArtifactStore.kt",
    "app/src/main/java/com/ai/assistance/operit/data/audit/"
    "ConversationAuditExporter.kt",
    "app/src/main/java/com/ai/assistance/operit/data/audit/"
    "ConversationAuditPayloadStore.kt",
    "app/src/main/java/com/ai/assistance/operit/data/backup/"
    "RawSnapshotBackupManager.kt",
    "app/src/main/java/com/kiyori/platform/storage/KiyoriPublicStore.kt",
    "app/src/main/java/com/kiyori/platform/storage/"
    "ToolPkgLegacyImportCoordinator.kt",
    "app/src/main/java/com/kiyori/platform/storage/"
    "ToolPkgPrivateDataLayout.kt",
    "app/src/main/java/com/kiyori/platform/storage/"
    "ToolPkgStorageService.kt",
)
M05E_DIRECT_BACKUP_CONSUMER_PATHS = (
    M05E_OPERIT_BACKUP_DIRS_PATH,
    "app/src/main/java/com/ai/assistance/operit/data/backup/"
    "RawSnapshotBackupManager.kt",
    "app/src/main/java/com/ai/assistance/operit/data/backup/"
    "RoomDatabaseBackupManager.kt",
    "app/src/main/java/com/ai/assistance/operit/data/backup/"
    "RoomDatabaseRestoreManager.kt",
    "app/src/main/java/com/ai/assistance/operit/data/preferences/"
    "CharacterCardManager.kt",
    "app/src/main/java/com/ai/assistance/operit/data/repository/"
    "ChatHistoryManager.kt",
    "app/src/main/java/com/ai/assistance/operit/ui/features/settings/screens/"
    "ChatBackupSettingsScreen.kt",
)
M05E_LEGACY_OPERIT_CONSUMER_PATHS = (
    "app/src/main/java/com/ai/assistance/operit/api/chat/llmprovider/"
    "GeminiProvider.kt",
    "app/src/main/java/com/ai/assistance/operit/api/chat/llmprovider/"
    "LlamaProvider.kt",
    "app/src/main/java/com/ai/assistance/operit/api/chat/llmprovider/"
    "MNNProvider.kt",
    "app/src/main/java/com/ai/assistance/operit/api/chat/llmprovider/"
    "ModelListFetcher.kt",
    "app/src/main/java/com/ai/assistance/operit/api/chat/llmprovider/"
    "OpenAIProvider.kt",
    "app/src/main/java/com/ai/assistance/operit/api/speech/"
    "SherpaSpeechProvider.kt",
    "app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/"
    "accessbility/AccessibilityUITools.kt",
    "app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/"
    "debugger/DebuggerUITools.kt",
    "app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/"
    "standard/StandardSystemOperationTools.kt",
    "app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/"
    "standard/StandardUITools.kt",
    "app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/"
    "standard/StandardWebVisitTool.kt",
    "app/src/main/java/com/ai/assistance/operit/core/tools/javascript/"
    "JsEngine.kt",
    "app/src/main/java/com/ai/assistance/operit/core/tools/javascript/"
    "JsNativeInterfaceDelegates.kt",
    "app/src/main/java/com/ai/assistance/operit/core/tools/javascript/"
    "ScriptExecutionReceiver.kt",
    "app/src/main/java/com/ai/assistance/operit/core/tools/mcp/"
    "MCPToolExecutor.kt",
    "app/src/main/java/com/ai/assistance/operit/core/tools/packTool/"
    "PackageManager.kt",
    "app/src/main/java/com/ai/assistance/operit/core/tools/skill/"
    "SkillManager.kt",
    "app/src/main/java/com/ai/assistance/operit/data/mcp/"
    "MCPLocalServer.kt",
    "app/src/main/java/com/ai/assistance/operit/data/mcp/"
    "MCPRepository.kt",
    "app/src/main/java/com/ai/assistance/operit/data/mcp/plugins/"
    "MCPBridge.kt",
    "app/src/main/java/com/ai/assistance/operit/data/mnn/"
    "MnnModelDownloadManager.kt",
    "app/src/main/java/com/ai/assistance/operit/data/preferences/"
    "FreeUsagePreferences.kt",
    "app/src/main/java/com/ai/assistance/operit/data/repository/"
    "MemoryRepository.kt",
    "app/src/main/java/com/ai/assistance/operit/data/repository/"
    "WorkflowRepository.kt",
    "app/src/main/java/com/ai/assistance/operit/services/core/"
    "AttachmentDelegate.kt",
    "app/src/main/java/com/ai/assistance/operit/ui/common/composedsl/"
    "ToolPkgComposeDslScreen.kt",
    "app/src/main/java/com/ai/assistance/operit/ui/error/"
    "CrashReportActivity.kt",
    "app/src/main/java/com/ai/assistance/operit/ui/features/chat/components/"
    "ExportDialogs.kt",
    "app/src/main/java/com/ai/assistance/operit/ui/features/chat/webview/"
    "LocalWebServer.kt",
    "app/src/main/java/com/ai/assistance/operit/ui/features/chat/webview/"
    "WorkspaceUtils.kt",
    "app/src/main/java/com/ai/assistance/operit/ui/features/settings/screens/"
    "ChatHistorySettingsScreen.kt",
    "app/src/main/java/com/ai/assistance/operit/ui/features/toolbox/screens/"
    "tooltester/ToolTesterScreen.kt",
    "app/src/main/java/com/ai/assistance/operit/util/ImagePoolManager.kt",
    "app/src/main/java/com/ai/assistance/operit/util/MediaPoolManager.kt",
    "app/src/main/java/com/ai/assistance/operit/util/"
    "SkillRepoZipPoolManager.kt",
)
M05E_ARCHITECTURE_TEST_PATH = "ci/test/test_architecture_boundaries.py"
M04B_SHELL_PAGES_PATH = (
    "app/src/main/java/com/ai/assistance/operit/ui/main/shell/"
    "KiyoriShellPages.kt"
)
M04_HOST_LOCALS_PATH = (
    "app/src/main/java/com/ai/assistance/operit/ui/main/navigation/"
    "OperitHostCompositionLocals.kt"
)
M04_HOST_LOCAL_SYMBOLS = (
    "LocalTopBarActions",
    "LocalOpenBrowser",
    "TopBarTitleContent",
    "LocalTopBarTitleContent",
    "LocalAppNavigationModel",
)
M01_CONSUMERS = {
    "app/src/main/java/com/ai/assistance/operit/api/chat/AIForegroundService.kt",
    "app/src/main/java/com/ai/assistance/operit/core/config/SystemPromptConfig.kt",
    "app/src/main/java/com/ai/assistance/operit/data/api/MarketStatsApiService.kt",
    "app/src/main/java/com/ai/assistance/operit/data/converter/GenericJsonConverter.kt",
    "app/src/main/java/com/ai/assistance/operit/data/preferences/WakeWordPreferences.kt",
    "app/src/main/java/com/ai/assistance/operit/plugins/toolbox/ToolboxPlugin.kt",
    "app/src/main/java/com/ai/assistance/operit/plugins/toolpkg/ToolPkgHookBridgeSupport.kt",
    "app/src/main/java/com/ai/assistance/operit/plugins/toolpkg/ToolPkgToolLifecycleBridge.kt",
    "app/src/main/java/com/ai/assistance/operit/services/FloatingChatService.kt",
    "app/src/main/java/com/ai/assistance/operit/ui/common/markdown/MarkdownCodeTypeface.kt",
    "app/src/main/java/com/ai/assistance/operit/ui/features/token/model/UrlConfig.kt",
    "app/src/main/java/com/ai/assistance/operit/ui/main/MainActivity.kt",
    "app/src/main/java/com/ai/assistance/operit/util/AppLogger.kt",
}
M01_EXPECTED_CANDIDATE_PATHS = M01_CONSUMERS | {
    M01_NEW_PATH,
    "app/src/main/AndroidManifest.xml",
    "app/lint-baseline.xml",
}
M02_PLATFORM_CONTRACT_PATHS = {
    "app/src/main/java/com/kiyori/platform/android/ApplicationContextAccess.kt",
    "app/src/main/java/com/kiyori/platform/lifecycle/ApplicationStartupTime.kt",
    "app/src/main/java/com/kiyori/platform/lifecycle/MainApplicationInitialization.kt",
    "app/src/main/java/com/kiyori/platform/serialization/ApplicationJson.kt",
}
M02_REQUIRED_APPLICATION_CALLS = {
    "ApplicationContextAccess.installForProcess": "application context",
    "ApplicationJson.installForProcess": "application Json",
    "ApplicationStartupTime.recordForProcess": "application startup time",
}
M02_PROCESS_INSTALL_METHOD_NAMES = {
    "installForProcess",
    "recordForProcess",
}
M02_FORBIDDEN_PLATFORM_PATTERNS = {
    r"\bServiceLocator\b": "ServiceLocator",
    r"\bKiyoriPlatform\b": "KiyoriPlatform",
    r"\bApplicationServices\b": "ApplicationServices",
    r"\bApplicationRegistry\b": "ApplicationRegistry",
    r"\bgetService\s*\(": "getService",
    r"\bresolveService\s*\(": "resolveService",
    r"\bMap\s*<\s*KClass\b": "KClass service map",
    r"\bMap\s*<\s*Class\b": "Class service map",
}
M02_FORBIDDEN_APPLICATION_MEMBERS = {
    r"\blateinit\s+var\s+instance\s*:": "instance",
    r"\blateinit\s+var\s+json\s*:": "json",
    r"\bvar\s+appStartupTimeMs\s*:": "appStartupTimeMs",
    r"\bval\s+globalImageLoader\s*:": "globalImageLoader",
}
PROHIBITED_TRACKED_PARTS = {
    ".gradle",
    ".gradle-local",
    ".venv",
    "node_modules",
    "work",
}
GENERATED_SOURCE_DIRECTORY_NAMES = {".cxx", "build"}


def source_files(
    root: Path,
    suffixes: set[str],
) -> list[Path]:
    """Return source inputs without descending into generated build directories."""

    if not root.is_dir():
        return []
    paths: list[Path] = []
    for current_root, directory_names, file_names in os.walk(root):
        directory_names[:] = [
            name
            for name in directory_names
            if name not in GENERATED_SOURCE_DIRECTORY_NAMES
        ]
        current_path = Path(current_root)
        paths.extend(
            current_path / file_name
            for file_name in file_names
            if Path(file_name).suffix.lower() in suffixes
        )
    return sorted(paths)
PERSISTENCE_CALL_SPECS = {
    "DataStoreFactory.create": ("named", "produceFile"),
    "preferencesDataStore": ("named", "name"),
    "getSharedPreferences": ("index", 0),
    "Room.databaseBuilder": ("index", 2),
    "enqueueUniquePeriodicWork": ("index", 0),
    "enqueueUniqueWork": ("index", 0),
    "cancelUniqueWork": ("index", 0),
    "getWorkInfosForUniqueWork": ("index", 0),
}
PERSISTENCE_CALL_PATTERN = re.compile(
    r"(?<![A-Za-z0-9_])("
    + "|".join(
        re.escape(name)
        for name in sorted(PERSISTENCE_CALL_SPECS, key=len, reverse=True)
    )
    + r")\s*\("
)
PERSISTENCE_BYPASS_IMPORT_PATTERN = re.compile(
    r"^\s*import\s+(?:static\s+)?(?:"
    r"androidx\.room\.Room\.databaseBuilder"
    r"|androidx\.datastore\.preferences\.preferencesDataStore\s+as\s+\S+"
    r")"
)
UNREVIEWED_PERSISTENCE_CALL_PATTERN = re.compile(
    r"(?<![A-Za-z0-9_])(?:"
    r"PreferenceManager\.getDefaultSharedPreferences"
    r"|PreferenceDataStoreFactory\.create"
    r"|Room\.inMemoryDatabaseBuilder"
    r"|SQLiteDatabase\.openDatabase"
    r"|createDataStore"
    r"|getPreferences"
    r"|openOrCreateDatabase"
    r")\s*\("
)
UNREVIEWED_PERSISTENCE_IMPORT_PATTERN = re.compile(
    r"^\s*import\s+androidx\.datastore\.dataStore(?:\s+as\s+\S+)?"
)


def git(root: Path, *args: str, check: bool = True) -> str:
    result = subprocess.run(
        ["git", *args],
        cwd=root,
        check=check,
        capture_output=True,
        text=True,
    )
    return result.stdout


def path_matches(path: str, pattern: str) -> bool:
    normalized = path.replace("\\", "/")
    normalized_pattern = pattern.replace("\\", "/")
    if normalized_pattern.endswith("/**"):
        prefix = normalized_pattern[:-3]
        return normalized == prefix or normalized.startswith(f"{prefix}/")
    return normalized == normalized_pattern


def is_exact_ownership_path(pattern: str) -> bool:
    return not any(character in pattern for character in "*?[]")


def import_matches_root(imported: str, root: str) -> bool:
    return imported == root or imported.startswith(f"{root}.")


def is_project_import(imported: str) -> bool:
    return any(import_matches_root(imported, root) for root in PROJECT_IMPORT_ROOTS)


def source_imports(path: Path) -> list[tuple[int, str]]:
    imports: list[tuple[int, str]] = []
    for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        # Java static imports represent the same dependency edge as ordinary
        # imports. Ignoring "static" would let a forbidden package bypass the
        # ownership graph without changing the referenced implementation.
        match = re.match(
            r"^\s*import\s+(?:static\s+)?([A-Za-z_][A-Za-z0-9_.]*)",
            line,
        )
        if match:
            imports.append((line_number, match.group(1)))
    return imports


def source_dependency_edges(path: Path) -> list[tuple[int, str, str]]:
    text = path.read_text(encoding="utf-8")
    edges = [
        (line_number, imported, "import")
        for line_number, imported in source_imports(path)
    ]
    seen = set(edges)
    mask = source_code_mask(text)
    for line_number, line in enumerate(mask.splitlines(), start=1):
        # Imports already produce one dependency edge above. Package declarations
        # describe the current source rather than a dependency. Skipping both also
        # prevents a fully qualified matcher from duplicating their diagnostics.
        if re.match(r"^\s*(?:package|import)\b", line):
            continue
        for match in PROJECT_REFERENCE_PATTERN.finditer(line):
            edge = (line_number, match.group(1), "fully-qualified reference")
            if edge not in seen:
                edges.append(edge)
                seen.add(edge)
    return edges


def source_package(path: Path) -> str | None:
    for line in path.read_text(encoding="utf-8").splitlines():
        match = re.match(r"^\s*package\s+([A-Za-z_][A-Za-z0-9_.]*)", line)
        if match:
            return match.group(1)
    return None


def dependency_rule(identifier: str, imported: str) -> str:
    if identifier.startswith("operit-"):
        return "ARCH001" if import_matches_root(imported, "com.kiyori.app") else "ARCH002"
    if identifier == "kiyori-capability" or identifier.startswith("kiyori-capability-"):
        return "ARCH003"
    if (
        identifier == "kiyori-app"
        or identifier.startswith("kiyori-feature")
        or identifier.startswith("kiyori-integration")
    ):
        return "ARCH004"
    return "ARCH005"


def read_snapshot(path: Path) -> list[str]:
    return [
        line.strip()
        for line in path.read_text(encoding="utf-8").splitlines()
        if line.strip() and not line.lstrip().startswith("#")
    ]


def read_count_snapshot(path: Path) -> list[tuple[int, str]]:
    entries: list[tuple[int, str]] = []
    for line in read_snapshot(path):
        fields = line.split("\t", maxsplit=1)
        if len(fields) != 2:
            raise ValueError(f"invalid counted snapshot line: {line}")
        try:
            expected_count = int(fields[0])
        except ValueError as error:
            raise ValueError(f"invalid snapshot count: {line}") from error
        if expected_count < 1 or not fields[1]:
            raise ValueError(f"invalid counted snapshot line: {line}")
        entries.append((expected_count, fields[1]))
    return entries


def read_hash_snapshot(path: Path) -> list[tuple[str, str]]:
    entries: list[tuple[str, str]] = []
    seen_paths: set[str] = set()
    for line in read_snapshot(path):
        fields = line.split("\t", maxsplit=1)
        if len(fields) != 2 or not re.fullmatch(r"[0-9A-Fa-f]{64}", fields[0]):
            raise ValueError(f"invalid hash snapshot line: {line}")
        relative_path = PurePosixPath(fields[1])
        if relative_path.is_absolute() or ".." in relative_path.parts:
            raise ValueError(f"invalid hash snapshot path: {fields[1]}")
        normalized = relative_path.as_posix()
        if normalized in seen_paths:
            raise ValueError(f"duplicate hash snapshot path: {normalized}")
        seen_paths.add(normalized)
        entries.append((fields[0].upper(), normalized))
    return entries


def read_manifest_hash_snapshot(
    path: Path,
    valid_phases: set[str] = VALID_MANIFEST_PHASES,
) -> dict[str, str]:
    entries: dict[str, str] = {}
    for line in read_snapshot(path):
        fields = line.split("\t", maxsplit=1)
        if (
            len(fields) != 2
            or fields[0] not in valid_phases
            or not re.fullmatch(r"[0-9A-Fa-f]{64}", fields[1])
        ):
            raise ValueError(f"invalid manifest hash snapshot line: {line}")
        if fields[0] in entries:
            raise ValueError(f"duplicate manifest hash snapshot phase: {fields[0]}")
        entries[fields[0]] = fields[1].upper()
    missing = valid_phases - set(entries)
    unexpected = set(entries) - valid_phases
    if missing or unexpected:
        raise ValueError(
            "manifest hash snapshot phases differ: "
            f"missing={sorted(missing)}, unexpected={sorted(unexpected)}"
        )
    return entries


def repository_text(root: Path) -> str:
    chunks: list[str] = []
    relative_paths = sorted(
        Path(value)
        for value in git(
            root,
            "ls-files",
            "--cached",
            "--others",
            "--exclude-standard",
            "-z",
            "--",
            "app",
            "examples",
            "tools",
        ).split("\0")
        if value
    )
    for relative_path in relative_paths:
        path = root / relative_path
        excluded_parts = PROHIBITED_TRACKED_PARTS | {".cxx", "build"}
        if (
            path.is_file()
            and path.suffix.lower() in TEXT_SUFFIXES
            and not set(relative_path.parts) & excluded_parts
        ):
            chunks.append(path.read_text(encoding="utf-8", errors="replace"))
    return "\n".join(chunks)


@lru_cache(maxsize=None)
def source_code_mask(text: str) -> str:
    """Mask comments and literals once per distinct source snapshot.

    Most architecture checks search the same Kotlin sources for different
    ownership symbols. Recomputing the character-level mask for every symbol
    made the real repository gate scale with checks multiplied by source size.
    The input string is immutable, so reusing its mask preserves the exact
    matching semantics while keeping the gate proportional to unique source
    snapshots.
    """

    masked = list(text)
    index = 0
    state = "code"
    block_depth = 0
    while index < len(text):
        if state == "code":
            if text.startswith("//", index):
                masked[index : index + 2] = "  "
                index += 2
                state = "line-comment"
                continue
            if text.startswith("/*", index):
                masked[index : index + 2] = "  "
                index += 2
                state = "block-comment"
                block_depth = 1
                continue
            if text.startswith('"""', index):
                masked[index : index + 3] = "   "
                index += 3
                state = "triple-string"
                continue
            if text[index] == '"':
                masked[index] = " "
                index += 1
                state = "string"
                continue
            if text[index] == "'":
                masked[index] = " "
                index += 1
                state = "character"
                continue
            index += 1
            continue
        if state == "line-comment":
            masked[index] = "\n" if text[index] == "\n" else " "
            if text[index] == "\n":
                state = "code"
            index += 1
            continue
        if state == "block-comment":
            if text.startswith("/*", index):
                masked[index : index + 2] = "  "
                index += 2
                block_depth += 1
                continue
            if text.startswith("*/", index):
                masked[index : index + 2] = "  "
                index += 2
                block_depth -= 1
                if block_depth == 0:
                    state = "code"
                continue
            masked[index] = "\n" if text[index] == "\n" else " "
            index += 1
            continue
        if state == "triple-string":
            if text.startswith('"""', index):
                masked[index : index + 3] = "   "
                index += 3
                state = "code"
                continue
            masked[index] = "\n" if text[index] == "\n" else " "
            index += 1
            continue
        if text[index] == "\\":
            masked[index] = " "
            if index + 1 < len(text):
                masked[index + 1] = " "
            index += 2
            continue
        delimiter = '"' if state == "string" else "'"
        masked[index] = "\n" if text[index] == "\n" else " "
        if text[index] == delimiter:
            state = "code"
        index += 1
    return "".join(masked)


def compact_code_expression(expression: str) -> str:
    compacted: list[str] = []
    index = 0
    state = "code"
    block_depth = 0
    while index < len(expression):
        if state == "code":
            if expression.startswith("//", index):
                index += 2
                state = "line-comment"
                continue
            if expression.startswith("/*", index):
                index += 2
                state = "block-comment"
                block_depth = 1
                continue
            if expression.startswith('"""', index):
                compacted.append('"""')
                index += 3
                state = "triple-string"
                continue
            character = expression[index]
            if character == '"':
                compacted.append(character)
                index += 1
                state = "string"
                continue
            if character == "'":
                compacted.append(character)
                index += 1
                state = "character"
                continue
            if not character.isspace():
                compacted.append(character)
            index += 1
            continue
        if state == "line-comment":
            if expression[index] == "\n":
                state = "code"
            index += 1
            continue
        if state == "block-comment":
            if expression.startswith("/*", index):
                index += 2
                block_depth += 1
                continue
            if expression.startswith("*/", index):
                index += 2
                block_depth -= 1
                if block_depth == 0:
                    state = "code"
                continue
            index += 1
            continue
        if state == "triple-string":
            if expression.startswith('"""', index):
                compacted.append('"""')
                index += 3
                state = "code"
                continue
            compacted.append(expression[index])
            index += 1
            continue
        character = expression[index]
        compacted.append(character)
        if character == "\\" and index + 1 < len(expression):
            compacted.append(expression[index + 1])
            index += 2
            continue
        delimiter = '"' if state == "string" else "'"
        if character == delimiter:
            state = "code"
        index += 1
    return "".join(compacted)


def call_argument_ranges(mask: str, open_index: int) -> tuple[list[tuple[int, int]], int]:
    ranges: list[tuple[int, int]] = []
    start = open_index + 1
    round_depth = 0
    square_depth = 0
    brace_depth = 0
    index = start
    while index < len(mask):
        character = mask[index]
        if character == "(":
            round_depth += 1
        elif character == ")":
            if round_depth == 0 and square_depth == 0 and brace_depth == 0:
                if mask[start:index].strip() or ranges:
                    ranges.append((start, index))
                return ranges, index
            round_depth -= 1
        elif character == "[":
            square_depth += 1
        elif character == "]":
            square_depth -= 1
        elif character == "{":
            brace_depth += 1
        elif character == "}":
            brace_depth -= 1
        elif (
            character == ","
            and round_depth == 0
            and square_depth == 0
            and brace_depth == 0
        ):
            ranges.append((start, index))
            start = index + 1
        if min(round_depth, square_depth, brace_depth) < 0:
            raise ValueError("unbalanced persistence API call")
        index += 1
    raise ValueError("unterminated persistence API call")


def persistence_api_records(root: Path) -> Counter[str]:
    records: Counter[str] = Counter()
    relative_paths = sorted(
        Path(value)
        for value in git(
            root,
            "ls-files",
            "--cached",
            "--others",
            "--exclude-standard",
            "-z",
            "--",
            "app/src/main/java",
        ).split("\0")
        if value and Path(value).suffix in MANAGED_SOURCE_SUFFIXES
    )
    for relative_path in relative_paths:
        path = root / relative_path
        if not path.is_file():
            continue
        text = path.read_text(encoding="utf-8")
        mask = source_code_mask(text)
        for line_number, line in enumerate(mask.splitlines(), start=1):
            if PERSISTENCE_BYPASS_IMPORT_PATTERN.match(line):
                raise ValueError(
                    "persistence API import bypass is not allowed: "
                    f"{relative_path.as_posix()}:{line_number}"
                )
            if UNREVIEWED_PERSISTENCE_IMPORT_PATTERN.match(line):
                raise ValueError(
                    "unreviewed persistence API requires a contract extractor: "
                    f"{relative_path.as_posix()}:{line_number}"
                )
        unreviewed_match = UNREVIEWED_PERSISTENCE_CALL_PATTERN.search(mask)
        if unreviewed_match:
            line_number = mask.count("\n", 0, unreviewed_match.start()) + 1
            raise ValueError(
                "unreviewed persistence API requires a contract extractor: "
                f"{relative_path.as_posix()}:{line_number}"
            )
        for match in PERSISTENCE_CALL_PATTERN.finditer(mask):
            call_name = match.group(1)
            open_index = mask.find("(", match.start(), match.end())
            ranges, _ = call_argument_ranges(mask, open_index)
            selector_kind, selector = PERSISTENCE_CALL_SPECS[call_name]
            selected: str | None = None
            if selector_kind == "index":
                argument_index = int(selector)
                if argument_index < len(ranges):
                    start, end = ranges[argument_index]
                    selected = text[start:end]
            else:
                for start, end in ranges:
                    masked_argument = mask[start:end]
                    named_match = re.match(
                        rf"^\s*{re.escape(str(selector))}\s*=",
                        masked_argument,
                    )
                    if named_match:
                        selected = text[start + named_match.end() : end]
                        break
            if selected is None:
                raise ValueError(
                    f"cannot resolve {call_name} contract argument in {relative_path.as_posix()}"
                )
            expression = compact_code_expression(selected)
            if not expression:
                raise ValueError(
                    f"empty {call_name} contract argument in {relative_path.as_posix()}"
                )
            records[
                f"{relative_path.as_posix()}\t{call_name}\t{expression}"
            ] += 1
    return records


def check_persistence_api_calls(root: Path, snapshot_path: Path, errors: list[str]) -> None:
    expected = Counter(read_snapshot(snapshot_path))
    actual = persistence_api_records(root)
    for record in sorted((expected - actual).elements()):
        errors.append(f"ARCH009 missing persistence API contract: {record}")
    for record in sorted((actual - expected).elements()):
        errors.append(f"ARCH009 unexpected persistence API contract: {record}")


def check_ownership(root: Path, ownership_path: Path, errors: list[str]) -> None:
    payload = tomllib.loads(ownership_path.read_text(encoding="utf-8"))
    if payload.get("schema_version") != 1:
        errors.append(
            f"ARCH013 unsupported ownership schema_version: {payload.get('schema_version')!r}"
        )
    records = payload.get("ownership", [])
    exception_records = payload.get("exception", [])
    if not isinstance(records, list) or not isinstance(exception_records, list):
        errors.append("ARCH013 ownership and exception entries must be arrays")
        return
    exceptions: dict[tuple[str, str], dict[str, object]] = {}
    used_exceptions: set[tuple[str, str]] = set()
    identifiers: set[str] = set()
    ownership_paths: set[str] = set()
    valid_records: list[dict[str, object]] = []

    for exception in exception_records:
        if not isinstance(exception, dict):
            errors.append(f"ARCH013 invalid architecture exception: {exception!r}")
            continue
        rule = exception.get("rule")
        path = exception.get("path")
        key = (str(rule), str(path))
        if (
            not isinstance(rule, str)
            or not rule.startswith("ARCH")
            or not isinstance(path, str)
            or not path
            or any(character in path for character in "*?[]")
            or not isinstance(exception.get("reason"), str)
            or not exception.get("reason")
            or not isinstance(exception.get("expires_after"), str)
            or not str(exception.get("expires_after")).startswith("M-")
            or not isinstance(exception.get("owner"), str)
            or not exception.get("owner")
        ):
            errors.append(f"ARCH013 invalid architecture exception: {exception!r}")
            continue
        if key in exceptions:
            errors.append(f"ARCH013 duplicate architecture exception: {rule} {path}")
            continue
        exceptions[key] = exception

    def add_violation(rule: str, path: str, message: str) -> None:
        key = (rule, path)
        if key in exceptions:
            used_exceptions.add(key)
            return
        errors.append(f"{rule} {message}")

    for record in records:
        if not isinstance(record, dict):
            errors.append(f"ARCH013 invalid ownership record: {record!r}")
            continue
        identifier = record.get("id")
        pattern = record.get("path")
        if not isinstance(identifier, str) or not identifier:
            errors.append("ARCH013 ownership record has no stable id")
            continue
        if identifier in identifiers:
            errors.append(f"ARCH013 duplicate ownership id: {identifier}")
        identifiers.add(identifier)
        if record.get("sync_zone") not in VALID_SYNC_ZONES:
            errors.append(f"ARCH013 {identifier} has invalid sync_zone")
        if not isinstance(pattern, str) or not pattern:
            errors.append(f"ARCH013 {identifier} has no path")
        elif pattern in ownership_paths:
            errors.append(f"ARCH013 duplicate ownership path: {pattern}")
        else:
            ownership_paths.add(pattern)
            valid_records.append(record)
        if not isinstance(record.get("owner"), str) or not record.get("owner"):
            errors.append(f"ARCH013 {identifier} has no owner")
        if not isinstance(record.get("phase"), str) or not record.get("phase"):
            errors.append(f"ARCH013 {identifier} has no phase")
        planned = record.get("planned")
        if planned is not None and planned is not True:
            errors.append(f"ARCH013 {identifier} has invalid planned flag")
        for field in ("allowed_import_roots", "forbidden_import_roots"):
            values = record.get(field, [])
            if not isinstance(values, list) or not all(
                isinstance(value, str) and value for value in values
            ):
                errors.append(f"ARCH013 {identifier} has invalid {field}")
        raw_allowed = record.get("allowed_import_roots", [])
        raw_forbidden = record.get("forbidden_import_roots", [])
        allowed_values = (
            {value for value in raw_allowed if isinstance(value, str)}
            if isinstance(raw_allowed, list)
            else set()
        )
        forbidden_values = (
            {value for value in raw_forbidden if isinstance(value, str)}
            if isinstance(raw_forbidden, list)
            else set()
        )
        overlap = allowed_values & forbidden_values
        if overlap:
            errors.append(
                f"ARCH013 {identifier} has imports both allowed and forbidden: "
                + ", ".join(sorted(overlap))
            )

    managed = sorted(
        path.relative_to(root).as_posix()
        for path in (root / "app/src/main/java").rglob("*")
        if path.is_file() and path.suffix in MANAGED_SOURCE_SUFFIXES
    )
    matched_counts = {record.get("id"): 0 for record in valid_records}
    for path in managed:
        relative_source = Path(path).relative_to("app/src/main/java")
        expected_package = ".".join(relative_source.parent.parts)
        declared_package = source_package(root / path)
        if declared_package != expected_package:
            add_violation(
                "ARCH012",
                path,
                f"source/package mismatch: {path} declares "
                f"{declared_package or 'no package'}, expected {expected_package}",
            )

        raw_matches = [
            record
            for record in valid_records
            if path_matches(path, str(record.get("path", "")))
        ]
        exact_matches = [
            record
            for record in raw_matches
            if is_exact_ownership_path(str(record.get("path", "")))
        ]
        matches = exact_matches or raw_matches
        if not matches:
            errors.append(f"ARCH013 unmanaged source file: {path}")
            continue
        if len(matches) > 1:
            errors.append(
                f"ARCH013 source file has multiple owners: {path} -> "
                + ", ".join(str(record.get("id")) for record in matches)
            )
        for record in matches:
            matched_counts[record.get("id")] += 1
        if len(matches) != 1:
            continue

        record = matches[0]
        identifier = str(record.get("id"))
        raw_allowed = record.get("allowed_import_roots", [])
        raw_forbidden = record.get("forbidden_import_roots", [])
        allowed = tuple(value for value in raw_allowed if isinstance(value, str))
        forbidden = tuple(value for value in raw_forbidden if isinstance(value, str))
        for line_number, imported, reference_kind in source_dependency_edges(root / path):
            forbidden_match = next(
                (value for value in forbidden if import_matches_root(imported, value)),
                None,
            )
            if forbidden_match:
                rule = dependency_rule(identifier, imported)
                add_violation(
                    rule,
                    path,
                    f"forbidden {reference_kind}: {path}:{line_number} "
                    f"references {imported} from {identifier}",
                )
                continue
            if allowed and is_project_import(imported) and not any(
                import_matches_root(imported, value) for value in allowed
            ):
                rule = dependency_rule(identifier, imported)
                add_violation(
                    rule,
                    path,
                    f"{reference_kind} outside allowed roots: {path}:{line_number} "
                    f"references {imported} from {identifier}",
                )

    for record in valid_records:
        if not record.get("planned") and matched_counts.get(record.get("id"), 0) == 0:
            errors.append(f"ARCH013 ownership path matches no source: {record.get('id')}")
    for rule, path in sorted(set(exceptions) - used_exceptions):
        errors.append(f"ARCH013 unused architecture exception: {rule} {path}")


def expected_manifest_components(
    snapshot_path: Path,
    phase: str,
) -> Counter[tuple[str, str]]:
    expected: Counter[tuple[str, str]] = Counter()
    for line in read_snapshot(snapshot_path):
        fields = line.split("\t")
        if len(fields) not in {2, 3, 4}:
            raise ValueError(f"invalid manifest snapshot line: {line}")
        if phase == "m03" and len(fields) == 4:
            name = fields[3]
        elif phase in {"m01", "post-m01"} and len(fields) >= 3:
            name = fields[2]
        else:
            name = fields[1]
        expected[(fields[0], name)] += 1
    return expected


def actual_manifest_components(manifest_path: Path) -> Counter[tuple[str, str]]:
    root = ET.parse(manifest_path).getroot()
    components: Counter[tuple[str, str]] = Counter()
    for tag in ("application", "activity", "activity-alias", "service", "receiver", "provider"):
        for node in root.iter(tag):
            if node.get(TOOLS_NODE) == "remove":
                continue
            name = node.get(ANDROID_NAME)
            if name:
                components[(tag, name)] += 1
            process = node.get(ANDROID_PROCESS)
            if process:
                components[("process", process)] += 1
            permission = node.get(ANDROID_PERMISSION)
            if permission:
                components[("permission", permission)] += 1
    for node in root.iter("action"):
        name = node.get(ANDROID_NAME)
        if name:
            components[("action", name)] += 1
    for node in root.iter("category"):
        name = node.get(ANDROID_NAME)
        if name:
            components[("category", name)] += 1
    for node in root.iter("provider"):
        authorities = node.get(ANDROID_AUTHORITIES)
        if authorities:
            for authority in authorities.split(";"):
                if authority.strip():
                    components[("authority", authority.strip())] += 1
    for node in root.iter("data"):
        for kind, attribute in (
            ("data-host", ANDROID_HOST),
            ("data-scheme", ANDROID_SCHEME),
            ("mime-type", ANDROID_MIME_TYPE),
        ):
            value = node.get(attribute)
            if value:
                components[(kind, value)] += 1
    return components


def canonical_manifest_element(node: ET.Element) -> object:
    attributes = sorted((key, value) for key, value in node.attrib.items())
    children = sorted(
        (canonical_manifest_element(child) for child in node),
        key=lambda value: json.dumps(
            value,
            ensure_ascii=False,
            separators=(",", ":"),
        ),
    )
    text = (node.text or "").strip()
    return [node.tag, attributes, text, children]


def manifest_semantic_hash(manifest_path: Path) -> str:
    root = ET.parse(manifest_path).getroot()
    canonical = canonical_manifest_element(root)
    payload = json.dumps(
        canonical,
        ensure_ascii=False,
        separators=(",", ":"),
    ).encode("utf-8")
    return hashlib.sha256(payload).hexdigest().upper()


def check_manifest(
    root: Path,
    snapshot_path: Path,
    phase: str,
    errors: list[str],
    semantic_snapshot_path: Path | None = None,
) -> None:
    expected = expected_manifest_components(snapshot_path, phase)
    manifest_path = root / MAIN_MANIFEST_PATH
    actual = actual_manifest_components(manifest_path)
    for item in sorted((expected - actual).elements()):
        errors.append(f"ARCH008 missing manifest component: {item[0]} {item[1]}")
    for item in sorted((actual - expected).elements()):
        errors.append(f"ARCH008 unexpected manifest component: {item[0]} {item[1]}")
    if semantic_snapshot_path is not None:
        expected_hashes = read_manifest_hash_snapshot(semantic_snapshot_path)
        actual_hash = manifest_semantic_hash(manifest_path)
        expected_hash = expected_hashes[phase]
        if actual_hash != expected_hash:
            errors.append(
                "ARCH008 manifest semantic structure changed: "
                f"phase={phase} expected {expected_hash}, found {actual_hash}"
            )


def check_debug_manifest(
    root: Path,
    architecture_root: Path,
    errors: list[str],
) -> None:
    manifest_path = root / DEBUG_MANIFEST_PATH
    if not manifest_path.is_file():
        errors.append(f"ARCH008 debug Manifest missing: {DEBUG_MANIFEST_PATH}")
        return

    expected = expected_manifest_components(
        architecture_root / DEBUG_MANIFEST_COMPONENT_SNAPSHOT,
        "baseline",
    )
    actual = actual_manifest_components(manifest_path)
    for item in sorted((expected - actual).elements()):
        errors.append(
            f"ARCH008 missing debug manifest component: {item[0]} {item[1]}"
        )
    for item in sorted((actual - expected).elements()):
        errors.append(
            f"ARCH008 unexpected debug manifest component: {item[0]} {item[1]}"
        )

    expected_hashes = read_manifest_hash_snapshot(
        architecture_root / DEBUG_MANIFEST_HASH_SNAPSHOT,
        DEBUG_MANIFEST_PHASES,
    )
    actual_hash = manifest_semantic_hash(manifest_path)
    expected_hash = expected_hashes["debug"]
    if actual_hash != expected_hash:
        errors.append(
            "ARCH008 debug manifest semantic structure changed: "
            f"expected {expected_hash}, found {actual_hash}"
        )


def check_literals(root: Path, snapshot_paths: tuple[Path, ...], errors: list[str]) -> None:
    text = repository_text(root)
    for snapshot_path in snapshot_paths:
        for expected_count, literal in read_count_snapshot(snapshot_path):
            actual_count = text.count(literal)
            if actual_count != expected_count:
                errors.append(
                    "ARCH009/ARCH010 stable literal count changed: "
                    f"{literal!r} expected {expected_count}, found {actual_count}"
                )


def check_file_hashes(root: Path, snapshot_path: Path, errors: list[str]) -> None:
    for expected_hash, relative_path in read_hash_snapshot(snapshot_path):
        path = root.joinpath(*PurePosixPath(relative_path).parts)
        if not path.is_file():
            errors.append(f"ARCH009/ARCH010 critical contract file missing: {relative_path}")
            continue
        # Hash the current working tree, not the Git index, so unstaged contract
        # changes are visible. Normalizing CRLF keeps the same approved digest
        # across Windows and Linux checkouts without ignoring content changes.
        normalized = path.read_bytes().replace(b"\r\n", b"\n")
        actual_hash = hashlib.sha256(normalized).hexdigest().upper()
        if actual_hash != expected_hash:
            errors.append(
                "ARCH009/ARCH010 critical contract file changed: "
                f"{relative_path} expected {expected_hash}, found {actual_hash}"
            )


def check_tracked_artifacts(root: Path, errors: list[str]) -> None:
    for path in (value for value in git(root, "ls-files", "-z").split("\0") if value):
        normalized = path.replace("\\", "/")
        parts = set(Path(normalized).parts)
        if parts & PROHIBITED_TRACKED_PARTS:
            errors.append(f"ARCH015 tracked runtime/private path: {normalized}")
        if normalized.endswith((".apk", ".aab", ".bundle")) and not normalized.startswith(
            "app/src/main/assets/"
        ):
            errors.append(f"ARCH015 tracked build/backup artifact: {normalized}")
        if normalized in {"local.properties", ".env"} or normalized.endswith("/.env"):
            errors.append(f"ARCH015 tracked private config: {normalized}")


def check_terminal_unchanged(root: Path, base: str | None, errors: list[str]) -> None:
    if not base:
        return
    changed = {
        line.strip()
        for line in git(root, "diff", "--name-only", base, "--", "terminal").splitlines()
        if line.strip()
    }
    if changed:
        errors.append("ARCH014 terminal changed: " + ", ".join(sorted(changed)))


def current_application_path(root: Path) -> str:
    if (root / M03_APPLICATION_PATH).is_file():
        return M03_APPLICATION_PATH
    return M01_NEW_PATH


def check_m02_application_access(root: Path, errors: list[str]) -> None:
    contract_files = {
        relative_path: root / relative_path
        for relative_path in M02_PLATFORM_CONTRACT_PATHS
    }
    if not any(path.is_file() for path in contract_files.values()):
        return

    missing_contracts = sorted(
        relative_path
        for relative_path, path in contract_files.items()
        if not path.is_file()
    )
    if missing_contracts:
        errors.append(
            "ARCH017 M-02 platform contract files missing: "
            + ", ".join(missing_contracts)
        )

    application_relative_path = current_application_path(root)
    application_path = root / application_relative_path
    if not application_path.is_file():
        errors.append(
            "ARCH017 M-02 Application implementation missing: "
            f"{application_relative_path}"
        )
        return

    operit_root = root / "app/src/main/java/com/ai/assistance/operit"
    if operit_root.is_dir():
        for path in sorted(
            source
            for source in operit_root.rglob("*")
            if source.is_file() and source.suffix in MANAGED_SOURCE_SUFFIXES
        ):
            relative_path = path.relative_to(root).as_posix()
            if relative_path == application_relative_path:
                continue
            masked = source_code_mask(path.read_text(encoding="utf-8"))
            for line_number, line in enumerate(masked.splitlines(), start=1):
                if re.search(r"\bKiyoriApplication\b", line):
                    errors.append(
                        "ARCH017 concrete Application dependency: "
                        f"{relative_path}:{line_number}"
                    )

    application_masked = source_code_mask(application_path.read_text(encoding="utf-8"))
    for pattern, member in M02_FORBIDDEN_APPLICATION_MEMBERS.items():
        if re.search(pattern, application_masked):
            errors.append(
                f"ARCH017 KiyoriApplication still owns deprecated global member: {member}"
            )

    required_implementation_tokens = (
        "MainApplicationInitialization",
        "override fun initializeMainUiPrerequisites(",
        "override fun initializeMainApplication(",
    )
    for token in required_implementation_tokens:
        if token not in application_masked:
            errors.append(
                "ARCH017 KiyoriApplication does not implement the main initialization contract: "
                + token
            )

    main_source_root = root / "app/src/main/java"
    source_masks: dict[str, str] = {}
    if main_source_root.is_dir():
        for path in sorted(
            source
            for source in main_source_root.rglob("*")
            if source.is_file() and source.suffix in MANAGED_SOURCE_SUFFIXES
        ):
            source_masks[path.relative_to(root).as_posix()] = source_code_mask(
                path.read_text(encoding="utf-8")
            )

    allowed_install_paths = M02_PLATFORM_CONTRACT_PATHS | {application_relative_path}
    install_method_pattern = re.compile(
        r"\b(?:"
        + "|".join(sorted(M02_PROCESS_INSTALL_METHOD_NAMES))
        + r")\s*\("
    )
    for relative_path, masked in source_masks.items():
        if relative_path in allowed_install_paths:
            continue
        for line_number, imported in source_imports(root / relative_path):
            if imported.rsplit(".", maxsplit=1)[-1] in M02_PROCESS_INSTALL_METHOD_NAMES:
                errors.append(
                    "ARCH017 M-02 process installer import outside the unique owner: "
                    f"{relative_path}:{line_number}"
                )
        for line_number, line in enumerate(masked.splitlines(), start=1):
            if install_method_pattern.search(line):
                errors.append(
                    "ARCH017 M-02 process installer call outside the unique owner: "
                    f"{relative_path}:{line_number}"
                )

    for call, responsibility in M02_REQUIRED_APPLICATION_CALLS.items():
        call_pattern = re.compile(rf"\b{re.escape(call)}\s*\(")
        call_sites: list[str] = []
        for relative_path, masked in source_masks.items():
            for line_number, line in enumerate(masked.splitlines(), start=1):
                if call_pattern.search(line):
                    call_sites.append(f"{relative_path}:{line_number}")
        if len(call_sites) != 1 or not call_sites[0].startswith(
            f"{application_relative_path}:"
        ):
            errors.append(
                "ARCH017 M-02 process owner call must appear exactly once in "
                f"{application_relative_path} for {responsibility}; found {call_sites}"
            )

    platform_root = root / "app/src/main/java/com/kiyori/platform"
    if platform_root.is_dir():
        for path in sorted(
            source
            for source in platform_root.rglob("*")
            if source.is_file() and source.suffix in MANAGED_SOURCE_SUFFIXES
        ):
            relative_path = path.relative_to(root).as_posix()
            masked = source_code_mask(path.read_text(encoding="utf-8"))
            for pattern, description in M02_FORBIDDEN_PLATFORM_PATTERNS.items():
                for line_number, line in enumerate(masked.splitlines(), start=1):
                    if re.search(pattern, line):
                        errors.append(
                            "ARCH017 M-02 platform contract resembles a Service Locator: "
                            f"{relative_path}:{line_number} ({description})"
                        )


def normalize_m03_application_text(text: str) -> str:
    normalized = text.replace("\r\n", "\n")
    # Only normalize the declaration itself. Using \s here would also consume
    # the following blank line and make a package-only move look like source drift.
    package_pattern = re.compile(
        r"^package[ \t]+(?:com\.ai\.assistance\.operit\.core\.application|com\.kiyori\.app)[ \t]*$",
        re.MULTILINE,
    )
    normalized, replacements = package_pattern.subn(
        f"package {M03_APPLICATION_PACKAGE_PLACEHOLDER}",
        normalized,
        count=1,
    )
    if replacements != 1:
        raise ValueError("cannot normalize the M-03 Application package declaration")
    return normalized


def check_m03_application_move(
    root: Path,
    architecture_root: Path,
    errors: list[str],
) -> None:
    target_path = root / M03_APPLICATION_PATH
    if not target_path.is_file():
        return

    if (root / M01_NEW_PATH).exists():
        errors.append(f"ARCH018 old Application path still exists: {M01_NEW_PATH}")

    declared_package = source_package(target_path)
    if declared_package != M03_APPLICATION_PACKAGE:
        errors.append(
            "ARCH018 M-03 Application package differs: "
            f"{declared_package or 'missing'}"
        )

    hash_entries = read_snapshot(
        architecture_root / "m03-application-normalized-sha256.txt"
    )
    if len(hash_entries) != 1 or not re.fullmatch(r"[0-9A-Fa-f]{64}", hash_entries[0]):
        raise ValueError("invalid M-03 normalized Application hash snapshot")
    normalized = normalize_m03_application_text(
        target_path.read_text(encoding="utf-8")
    ).encode("utf-8")
    actual_hash = hashlib.sha256(normalized).hexdigest().upper()
    expected_hash = hash_entries[0].upper()
    if actual_hash != expected_hash:
        errors.append(
            "ARCH018 M-03 Application contains a non-package change: "
            f"expected {expected_hash}, found {actual_hash}"
        )

    expected_imports = Counter(
        read_snapshot(architecture_root / "m03-application-operit-imports.txt")
    )
    actual_imports = Counter(
        imported
        for _, imported in source_imports(target_path)
        if import_matches_root(imported, "com.ai.assistance.operit")
    )
    for imported in sorted((expected_imports - actual_imports).elements()):
        errors.append(f"ARCH018 missing transitional Application import: {imported}")
    for imported in sorted((actual_imports - expected_imports).elements()):
        errors.append(f"ARCH018 unexpected transitional Application import: {imported}")

    lint_text = (root / "app/lint-baseline.xml").read_text(encoding="utf-8")
    old_lint_count = lint_text.count(M03_LINT_OLD_PATH)
    new_lint_count = lint_text.count(M03_LINT_NEW_PATH)
    if old_lint_count != 0 or new_lint_count != M03_LINT_PATH_COUNT:
        errors.append(
            "ARCH018 current lint baseline paths must be old=0/new="
            f"{M03_LINT_PATH_COUNT}, found {old_lint_count}/{new_lint_count}"
        )

    old_fqcn = "com.ai.assistance.operit.core.application.KiyoriApplication"
    runtime_paths = source_files(root / "app/src/main", TEXT_SUFFIXES)
    runtime_paths.extend(
        path
        for path in (
            root / "app/build.gradle.kts",
            root / "app/proguard-rules.pro",
        )
        if path.is_file()
    )
    stale_paths = [
        path.relative_to(root).as_posix()
        for path in runtime_paths
        if old_fqcn in path.read_text(encoding="utf-8", errors="replace")
    ]
    if stale_paths:
        errors.append(
            "ARCH018 old Application FQCN remains in runtime text: "
            f"{old_fqcn} ({', '.join(sorted(stale_paths))})"
        )


def check_m04_root_composition(root: Path, errors: list[str]) -> None:
    host_locals_path = root / M04_HOST_LOCALS_PATH
    if not host_locals_path.is_file():
        return

    expected_package = "com.ai.assistance.operit.ui.main.navigation"
    declared_package = source_package(host_locals_path)
    if declared_package != expected_package:
        errors.append(
            "ARCH019 Operit host CompositionLocal package differs: "
            f"{declared_package or 'missing'}"
        )

    required_declarations = {
        "LocalTopBarActions": r"\bval\s+LocalTopBarActions\s*=\s*compositionLocalOf\b",
        "LocalOpenBrowser": r"\bval\s+LocalOpenBrowser\s*=\s*compositionLocalOf\b",
        "TopBarTitleContent": r"\bclass\s+TopBarTitleContent\b",
        "LocalTopBarTitleContent": (
            r"\bval\s+LocalTopBarTitleContent\s*=\s*compositionLocalOf\b"
        ),
        "LocalAppNavigationModel": (
            r"\bval\s+LocalAppNavigationModel\s*=\s*compositionLocalOf\b"
        ),
    }
    host_masked = source_code_mask(host_locals_path.read_text(encoding="utf-8"))
    for symbol, pattern in required_declarations.items():
        if not re.search(pattern, host_masked):
            errors.append(
                f"ARCH019 Operit host CompositionLocal declaration missing: {symbol}"
            )

    main_source_root = root / "app/src/main/java"
    source_paths = (
        sorted(
            path
            for path in main_source_root.rglob("*.kt")
            if path.is_file()
        )
        if main_source_root.is_dir()
        else []
    )
    for symbol in M04_HOST_LOCAL_SYMBOLS:
        declaration_pattern = re.compile(rf"\b(?:val|class)\s+{re.escape(symbol)}\b")
        declaration_sites = [
            path.relative_to(root).as_posix()
            for path in source_paths
            if declaration_pattern.search(
                source_code_mask(path.read_text(encoding="utf-8"))
            )
        ]
        if declaration_sites != [M04_HOST_LOCALS_PATH]:
            errors.append(
                "ARCH019 Operit host CompositionLocal must have one owner: "
                f"{symbol} found {declaration_sites}"
            )

    old_imports = {
        f"com.ai.assistance.operit.ui.main.{symbol}"
        for symbol in M04_HOST_LOCAL_SYMBOLS
    }
    for path in source_paths:
        relative_path = path.relative_to(root).as_posix()
        for line_number, imported in source_imports(path):
            if imported in old_imports:
                errors.append(
                    "ARCH019 stale root CompositionLocal import: "
                    f"{relative_path}:{line_number} imports {imported}"
                )

    root_relative_path = (
        M04_ROOT_PATH if (root / M04_ROOT_PATH).is_file() else M04_OLD_ROOT_PATH
    )
    root_path = root / root_relative_path
    if not root_path.is_file():
        errors.append(
            f"ARCH019 root Composable implementation missing: {root_relative_path}"
        )
        return
    root_masked = source_code_mask(root_path.read_text(encoding="utf-8"))
    if "compositionLocalOf" in root_masked:
        errors.append(
            "ARCH019 root Composable still declares a CompositionLocal: "
            f"{root_relative_path}"
        )

    target_path = root / M04_ROOT_PATH
    if not target_path.is_file():
        return

    old_root_path = root / M04_OLD_ROOT_PATH
    if old_root_path.exists():
        errors.append(
            f"ARCH020 old root Composable path remains: {M04_OLD_ROOT_PATH}"
        )

    declared_package = source_package(target_path)
    if declared_package != M04_ROOT_PACKAGE:
        errors.append(
            "ARCH020 Kiyori root Composable package differs: "
            f"{declared_package or 'missing'}"
        )

    architecture_root = root / "config/architecture"
    hash_entries = read_snapshot(architecture_root / M04_ROOT_HASH_SNAPSHOT)
    if len(hash_entries) != 1 or not re.fullmatch(r"[0-9A-Fa-f]{64}", hash_entries[0]):
        raise ValueError("invalid M-04 root Composable hash snapshot")
    normalized_root = target_path.read_bytes().replace(b"\r\n", b"\n")
    actual_hash = hashlib.sha256(normalized_root).hexdigest().upper()
    expected_hash = hash_entries[0].upper()
    if actual_hash != expected_hash:
        errors.append(
            "ARCH020 Kiyori root Composable changed outside the approved move: "
            f"expected {expected_hash}, found {actual_hash}"
        )

    expected_imports = Counter(
        read_snapshot(architecture_root / M04_ROOT_IMPORT_SNAPSHOT)
    )
    actual_imports = Counter(
        imported
        for _, imported in source_imports(target_path)
        if import_matches_root(imported, "com.ai.assistance.operit")
    )
    for imported in sorted((expected_imports - actual_imports).elements()):
        errors.append(
            "ARCH020 missing transitional root Composable import: "
            f"{imported}"
        )
    for imported in sorted((actual_imports - expected_imports).elements()):
        errors.append(
            "ARCH020 unexpected transitional root Composable import: "
            f"{imported}"
        )

    target_masked = source_code_mask(target_path.read_text(encoding="utf-8"))
    if len(re.findall(r"\bfun\s+KiyoriApp\s*\(", target_masked)) != 1:
        errors.append(
            "ARCH020 Kiyori root Composable must declare exactly one KiyoriApp"
        )
    if re.search(r"\bOperitApp\b", target_path.read_text(encoding="utf-8")):
        errors.append(
            "ARCH020 old OperitApp symbol remains in the Kiyori root Composable"
        )

    kiyori_app_declarations = [
        path.relative_to(root).as_posix()
        for path in source_paths
        if re.search(
            r"\bfun\s+KiyoriApp\s*\(",
            source_code_mask(path.read_text(encoding="utf-8")),
        )
    ]
    if kiyori_app_declarations != [M04_ROOT_PATH]:
        errors.append(
            "ARCH020 KiyoriApp must have one runtime owner: "
            f"found {kiyori_app_declarations}"
        )

    content_host_path = root / M04D_MAIN_CONTENT_HOST_PATH
    if not content_host_path.is_file():
        errors.append(
            f"ARCH020 KiyoriApp content host missing: {M04D_MAIN_CONTENT_HOST_PATH}"
        )
    else:
        content_host_imports = Counter(
            imported for _, imported in source_imports(content_host_path)
        )
        if content_host_imports["com.kiyori.app.KiyoriApp"] != 1:
            errors.append(
                "ARCH020 content host must import com.kiyori.app.KiyoriApp exactly once"
            )
        content_host_text = content_host_path.read_text(encoding="utf-8")
        content_host_masked = source_code_mask(content_host_text)
        if len(re.findall(r"\bKiyoriApp\s*\(", content_host_masked)) != 1:
            errors.append(
                "ARCH020 content host must host exactly one KiyoriApp call"
            )
        if re.search(r"\bOperitApp\b", content_host_text):
            errors.append(
                "ARCH020 content host still references OperitApp"
            )

    stale_operit_app_paths = [
        path.relative_to(root).as_posix()
        for path in source_paths
        if re.search(r"\bOperitApp\b", path.read_text(encoding="utf-8"))
    ]
    if stale_operit_app_paths:
        errors.append(
            "ARCH020 old OperitApp runtime symbol remains: "
            + ", ".join(stale_operit_app_paths)
        )


def check_m04b_operit_navigation_policy(root: Path, errors: list[str]) -> None:
    policy_path = root / M04B_OPERIT_NAVIGATION_POLICY_PATH
    if not policy_path.is_file():
        return

    declared_package = source_package(policy_path)
    if declared_package != M04B_OPERIT_NAVIGATION_POLICY_PACKAGE:
        errors.append(
            "ARCH021 Operit navigation policy package differs: "
            f"{declared_package or 'missing'}"
        )

    architecture_root = root / "config/architecture"
    policy_hash_entries = read_snapshot(
        architecture_root / M04B_OPERIT_NAVIGATION_POLICY_HASH_SNAPSHOT
    )
    if (
        len(policy_hash_entries) != 1
        or not re.fullmatch(r"[0-9A-Fa-f]{64}", policy_hash_entries[0])
    ):
        raise ValueError("invalid M-04B Operit navigation policy hash snapshot")
    normalized_policy = policy_path.read_bytes().replace(b"\r\n", b"\n")
    actual_policy_hash = hashlib.sha256(normalized_policy).hexdigest().upper()
    expected_policy_hash = policy_hash_entries[0].upper()
    if actual_policy_hash != expected_policy_hash:
        errors.append(
            "ARCH021 Operit navigation policy changed outside the approved split: "
            f"expected {expected_policy_hash}, found {actual_policy_hash}"
        )

    expected_imports = Counter(
        read_snapshot(
            architecture_root / M04B_OPERIT_NAVIGATION_POLICY_IMPORT_SNAPSHOT
        )
    )
    actual_imports = Counter(
        imported
        for _, imported in source_imports(policy_path)
        if import_matches_root(imported, "com.ai.assistance.operit")
    )
    for imported in sorted((expected_imports - actual_imports).elements()):
        errors.append(
            "ARCH021 missing Operit navigation policy import: "
            f"{imported}"
        )
    for imported in sorted((actual_imports - expected_imports).elements()):
        errors.append(
            "ARCH021 unexpected Operit navigation policy import: "
            f"{imported}"
        )

    shell_state_path = root / M04_SHELL_STATE_PATH
    if not shell_state_path.is_file():
        errors.append(
            f"ARCH021 Shell state implementation missing: {M04_SHELL_STATE_PATH}"
        )
        return
    state_hash_entries = read_snapshot(
        architecture_root / M04B_SHELL_STATE_HASH_SNAPSHOT
    )
    if (
        len(state_hash_entries) != 1
        or not re.fullmatch(r"[0-9A-Fa-f]{64}", state_hash_entries[0])
    ):
        raise ValueError("invalid M-04B Shell state hash snapshot")
    normalized_state = shell_state_path.read_bytes().replace(b"\r\n", b"\n")
    actual_state_hash = hashlib.sha256(normalized_state).hexdigest().upper()
    expected_state_hash = state_hash_entries[0].upper()
    if actual_state_hash != expected_state_hash:
        errors.append(
            "ARCH021 Shell state changed outside the approved policy split: "
            f"expected {expected_state_hash}, found {actual_state_hash}"
        )
    state_operit_imports = [
        imported
        for _, imported in source_imports(shell_state_path)
        if import_matches_root(imported, "com.ai.assistance.operit")
    ]
    if state_operit_imports:
        errors.append(
            "ARCH021 Shell state still imports Operit implementation: "
            + ", ".join(sorted(state_operit_imports))
        )

    moved_symbol_patterns = {
        "AiDrawerSelectionEffect": r"\benum\s+class\s+AiDrawerSelectionEffect\b",
        "AiTopBarMode": r"\benum\s+class\s+AiTopBarMode\b",
        "resolveAiDrawerSelection": r"\bfun\s+resolveAiDrawerSelection\s*\(",
        "resolveAiTopBarMode": r"\bfun\s+resolveAiTopBarMode\s*\(",
        "hasSameAiSettingsSourceFamily": (
            r"\bfun\s+hasSameAiSettingsSourceFamily\s*\("
        ),
        "toAiPrimaryRouteEntry": (
            r"\bfun\s+NavigationEntrySpec\.toAiPrimaryRouteEntry\s*\("
        ),
        "preservesAiPrimaryStack": (
            r"\bfun\s+NavigationEntrySpec\.preservesAiPrimaryStack\s*\("
        ),
        "buildAiPrimaryStack": r"\bfun\s+buildAiPrimaryStack\s*\(",
    }
    main_source_root = root / "app/src/main/java"
    source_paths = (
        sorted(
            path
            for path in main_source_root.rglob("*.kt")
            if path.is_file()
        )
        if main_source_root.is_dir()
        else []
    )
    for symbol, pattern in moved_symbol_patterns.items():
        declaration_sites = [
            path.relative_to(root).as_posix()
            for path in source_paths
            if re.search(
                pattern,
                source_code_mask(path.read_text(encoding="utf-8")),
            )
        ]
        if declaration_sites != [M04B_OPERIT_NAVIGATION_POLICY_PATH]:
            errors.append(
                "ARCH021 Operit navigation policy symbol must have one owner: "
                f"{symbol} found {declaration_sites}"
            )

    root_path = root / M04_ROOT_PATH
    if root_path.is_file():
        root_imports = Counter(imported for _, imported in source_imports(root_path))
        for symbol in moved_symbol_patterns:
            expected_import = (
                f"{M04B_OPERIT_NAVIGATION_POLICY_PACKAGE}.{symbol}"
            )
            if root_imports[expected_import] != 1:
                errors.append(
                    "ARCH021 KiyoriApp must import the integration policy exactly once: "
                    f"{expected_import}"
                )
            stale_import = (
                "com.ai.assistance.operit.ui.main.shell."
                f"{symbol}"
            )
            if root_imports[stale_import] != 0:
                errors.append(
                    "ARCH021 KiyoriApp still imports the old Shell policy owner: "
                    f"{stale_import}"
                )


def check_m04b_browser_exit_contract(root: Path, errors: list[str]) -> None:
    contract_path = root / M04B_BROWSER_EXIT_CONTRACT_PATH
    if not contract_path.is_file():
        return

    declared_package = source_package(contract_path)
    if declared_package != M04B_BROWSER_EXIT_CONTRACT_PACKAGE:
        errors.append(
            "ARCH022 Browser exit presentation package differs: "
            f"{declared_package or 'missing'}"
        )

    architecture_root = root / "config/architecture"
    hash_entries = read_snapshot(
        architecture_root / M04B_BROWSER_EXIT_CONTRACT_HASH_SNAPSHOT
    )
    if len(hash_entries) != 1 or not re.fullmatch(r"[0-9A-Fa-f]{64}", hash_entries[0]):
        raise ValueError("invalid M-04B Browser exit presentation hash snapshot")
    normalized = contract_path.read_bytes().replace(b"\r\n", b"\n")
    actual_hash = hashlib.sha256(normalized).hexdigest().upper()
    expected_hash = hash_entries[0].upper()
    if actual_hash != expected_hash:
        errors.append(
            "ARCH022 Browser exit presentation contract changed: "
            f"expected {expected_hash}, found {actual_hash}"
        )

    if source_imports(contract_path):
        errors.append(
            "ARCH022 Browser exit presentation contract must not import implementation code"
        )

    main_source_root = root / "app/src/main/java"
    source_paths = (
        sorted(
            path
            for path in main_source_root.rglob("*.kt")
            if path.is_file()
        )
        if main_source_root.is_dir()
        else []
    )
    declaration_pattern = r"\benum\s+class\s+KiyoriBrowserExitPresentation\b"
    declaration_sites = [
        path.relative_to(root).as_posix()
        for path in source_paths
        if re.search(
            declaration_pattern,
            source_code_mask(path.read_text(encoding="utf-8")),
        )
    ]
    if declaration_sites != [M04B_BROWSER_EXIT_CONTRACT_PATH]:
        errors.append(
            "ARCH022 Browser exit presentation must have one owner: "
            f"found {declaration_sites}"
        )

    expected_import = (
        f"{M04B_BROWSER_EXIT_CONTRACT_PACKAGE}.KiyoriBrowserExitPresentation"
    )
    expected_consumers = {
        M04_ROOT_PATH,
        M04_SHELL_STATE_PATH,
        (
            "app/src/main/java/com/ai/assistance/operit/ui/features/browser/"
            "appshell/KiyoriBrowserHome.kt"
        ),
    }
    actual_consumers = {
        path.relative_to(root).as_posix()
        for path in source_paths
        if any(imported == expected_import for _, imported in source_imports(path))
    }
    if actual_consumers != expected_consumers:
        errors.append(
            "ARCH022 Browser exit presentation consumer set differs: "
            f"expected {sorted(expected_consumers)}, found {sorted(actual_consumers)}"
        )

    stale_import = (
        "com.ai.assistance.operit.ui.main.shell.KiyoriBrowserExitPresentation"
    )
    stale_consumers = [
        path.relative_to(root).as_posix()
        for path in source_paths
        if any(imported == stale_import for _, imported in source_imports(path))
    ]
    if stale_consumers:
        errors.append(
            "ARCH022 old Browser exit presentation import remains: "
            + ", ".join(stale_consumers)
        )


def check_m04b_shell_state_owner(root: Path, errors: list[str]) -> None:
    target_path = root / M04_SHELL_STATE_PATH
    if not target_path.is_file():
        return

    old_path = root / M04_OLD_SHELL_STATE_PATH
    if old_path.exists():
        errors.append(
            f"ARCH023 old Shell state path remains: {M04_OLD_SHELL_STATE_PATH}"
        )

    declared_package = source_package(target_path)
    if declared_package != M04_SHELL_STATE_PACKAGE:
        errors.append(
            "ARCH023 Shell state package differs: "
            f"{declared_package or 'missing'}"
        )

    expected_state_imports = Counter(
        {
            (
                f"{M04B_BROWSER_EXIT_CONTRACT_PACKAGE}."
                "KiyoriBrowserExitPresentation"
            ): 1,
            "com.kiyori.capability.settings.navigation.KiyoriSettingsRoute": 1,
        }
    )
    actual_state_imports = Counter(
        imported
        for _, imported in source_imports(target_path)
        if is_project_import(imported)
    )
    if actual_state_imports != expected_state_imports:
        errors.append(
            "ARCH023 Shell state project imports differ: "
            f"expected {sorted(expected_state_imports.elements())}, "
            f"found {sorted(actual_state_imports.elements())}"
        )

    main_source_root = root / "app/src/main/java"
    source_paths = (
        sorted(
            path
            for path in main_source_root.rglob("*.kt")
            if path.is_file()
        )
        if main_source_root.is_dir()
        else []
    )
    moved_symbol_patterns = {
        "PrimaryDestination": r"\benum\s+class\s+PrimaryDestination\b",
        "KiyoriBrowserReturnTarget": (
            r"\benum\s+class\s+KiyoriBrowserReturnTarget\b"
        ),
        "SoftwareHomePage": r"\benum\s+class\s+SoftwareHomePage\b",
        "KiyoriShellChild": r"\benum\s+class\s+KiyoriShellChild\b",
        "KiyoriShellExternalDestination": (
            r"\benum\s+class\s+KiyoriShellExternalDestination\b"
        ),
        "KiyoriShellBackResult": r"\benum\s+class\s+KiyoriShellBackResult\b",
        "KiyoriShellBackTransition": (
            r"\bdata\s+class\s+KiyoriShellBackTransition\b"
        ),
        "KiyoriShellState": r"\bdata\s+class\s+KiyoriShellState\b",
        "KiyoriShellStateSaver": r"\bval\s+KiyoriShellStateSaver\b",
    }
    for symbol, pattern in moved_symbol_patterns.items():
        declaration_sites = [
            path.relative_to(root).as_posix()
            for path in source_paths
            if re.search(
                pattern,
                source_code_mask(path.read_text(encoding="utf-8")),
            )
        ]
        if declaration_sites != [M04_SHELL_STATE_PATH]:
            errors.append(
                "ARCH023 Shell state symbol must have one owner: "
                f"{symbol} found {declaration_sites}"
            )

    expected_operit_imports = {
        "app/src/main/java/com/ai/assistance/operit/ui/main/MainActivity.kt": {
            "com.kiyori.app.shell.KiyoriShellExternalDestination",
        },
    }
    if not (root / M04B_PRIMARY_NAVIGATION_PATH).is_file():
        expected_operit_imports[M04B_SHELL_PAGES_PATH] = {
            "com.kiyori.app.shell.PrimaryDestination",
        }
    if not (root / M04B_AI_DRAWER_PATH).is_file():
        expected_operit_imports[M04B_OLD_AI_DRAWER_PATH] = {
            "com.kiyori.app.shell.calculateKiyoriAiDrawerWidthDp",
        }
    if not (root / M04B_APP_SHELL_PATH).is_file():
        expected_operit_imports[M04B_OLD_APP_SHELL_PATH] = {
            "com.kiyori.app.shell.KiyoriBrowserReturnTarget",
            "com.kiyori.app.shell.KiyoriShellBackResult",
            "com.kiyori.app.shell.KiyoriShellChild",
            "com.kiyori.app.shell.KiyoriShellState",
            "com.kiyori.app.shell.PrimaryDestination",
            "com.kiyori.app.shell.SoftwareHomePage",
        }
    actual_operit_imports: dict[str, set[str]] = {}
    for path in source_paths:
        relative_path = path.relative_to(root).as_posix()
        imports = {
            imported
            for _, imported in source_imports(path)
            if import_matches_root(imported, M04_SHELL_STATE_PACKAGE)
        }
        if imports and relative_path.startswith(
            "app/src/main/java/com/ai/assistance/operit/"
        ):
            actual_operit_imports[relative_path] = imports
    if actual_operit_imports != expected_operit_imports:
        errors.append(
            "ARCH023 Operit-to-Shell-state bridge imports differ: "
            f"expected {expected_operit_imports}, found {actual_operit_imports}"
        )

    root_path = root / M04_ROOT_PATH
    if root_path.is_file():
        expected_root_imports = {
            "com.kiyori.app.shell.BrowserWorkspaceReturnToken",
            "com.kiyori.app.shell.KiyoriBrowserReturnTarget",
            "com.kiyori.app.shell.KiyoriSettingsOrigin",
            "com.kiyori.app.shell.KiyoriSettingsPresentation",
            "com.kiyori.app.shell.KiyoriShellChild",
            "com.kiyori.app.shell.KiyoriShellExternalDestination",
            "com.kiyori.app.shell.KiyoriShellState",
            "com.kiyori.app.shell.KiyoriShellStateSaver",
            "com.kiyori.app.shell.PrimaryDestination",
            "com.kiyori.app.shell.SoftwareHomePage",
            "com.kiyori.app.shell.openExternalDestination",
            "com.kiyori.app.shell.shouldPresentKiyoriPluginLoading",
        }
        if (root / M04B_APP_SHELL_PATH).is_file():
            expected_root_imports.add(
                "com.kiyori.app.shell.KiyoriAppShell"
            )
        if (root / M04B_BROWSER_SEARCH_PATH).is_file():
            expected_root_imports.update(
                {
                    "com.kiyori.app.shell.KiyoriWebSearchRequest",
                    "com.kiyori.app.shell.resolveKiyoriWebSearchRequest",
                }
            )
        actual_root_imports = {
            imported
            for _, imported in source_imports(root_path)
            if import_matches_root(imported, M04_SHELL_STATE_PACKAGE)
        }
        if actual_root_imports != expected_root_imports:
            errors.append(
                "ARCH023 KiyoriApp Shell-state imports differ: "
                f"expected {sorted(expected_root_imports)}, "
                f"found {sorted(actual_root_imports)}"
            )

    stale_prefix = "com.ai.assistance.operit.ui.main.shell."
    stale_symbols = set(moved_symbol_patterns)
    stale_imports = [
        f"{path.relative_to(root).as_posix()}:{line_number} imports {imported}"
        for path in source_paths
        for line_number, imported in source_imports(path)
        if imported.startswith(stale_prefix)
        and imported.removeprefix(stale_prefix) in stale_symbols
    ]
    if stale_imports:
        errors.append(
            "ARCH023 old Shell state import remains: "
            + ", ".join(stale_imports)
        )


M04B_APP_SHELL_MECHANICAL_IMPORTS = {
    "com.kiyori.app.shell.KiyoriBrowserReturnTarget",
    "com.kiyori.app.shell.KiyoriShellBackResult",
    "com.kiyori.app.shell.KiyoriShellChild",
    "com.kiyori.app.shell.KiyoriShellState",
    "com.kiyori.app.shell.PrimaryDestination",
    "com.kiyori.app.shell.SoftwareHomePage",
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
}


def normalize_m04b_app_shell_text(text: str) -> str:
    normalized = text.replace("\r\n", "\n")
    lines: list[str] = []
    for line in normalized.splitlines(keepends=True):
        if re.match(
            r"^package (?:com\.ai\.assistance\.operit\.ui\.main\.shell|"
            r"com\.kiyori\.app\.shell)\s*$",
            line.rstrip("\n"),
        ):
            lines.append("package __M04B_APP_SHELL_PACKAGE__\n")
            continue
        import_match = re.match(r"^import ([A-Za-z_][A-Za-z0-9_.]*)\s*$", line.rstrip("\n"))
        if (
            import_match
            and import_match.group(1) in M04B_APP_SHELL_MECHANICAL_IMPORTS
        ):
            continue
        lines.append(line)
    return "".join(lines)


def check_m04b_app_shell_owner(root: Path, errors: list[str]) -> None:
    target_path = root / M04B_APP_SHELL_PATH
    if not target_path.is_file():
        return

    if (root / M04B_OLD_APP_SHELL_PATH).exists():
        errors.append(
            f"ARCH024 old App Shell path remains: {M04B_OLD_APP_SHELL_PATH}"
        )

    declared_package = source_package(target_path)
    if declared_package != M04B_APP_SHELL_PACKAGE:
        errors.append(
            "ARCH024 App Shell package differs: "
            f"{declared_package or 'missing'}"
        )

    architecture_root = root / "config/architecture"
    hash_entries = read_snapshot(
        architecture_root / M04B_APP_SHELL_NORMALIZED_HASH_SNAPSHOT
    )
    if len(hash_entries) != 1 or not re.fullmatch(r"[0-9A-Fa-f]{64}", hash_entries[0]):
        raise ValueError("invalid M-04B App Shell normalized hash snapshot")
    normalized = normalize_m04b_app_shell_text(
        target_path.read_text(encoding="utf-8")
    ).encode("utf-8")
    actual_hash = hashlib.sha256(normalized).hexdigest().upper()
    expected_hash = hash_entries[0].upper()
    if actual_hash != expected_hash:
        errors.append(
            "ARCH024 App Shell changed outside the approved package-only move: "
            f"expected {expected_hash}, found {actual_hash}"
        )

    expected_import_entries = read_snapshot(
        architecture_root / M04B_APP_SHELL_PROJECT_IMPORT_SNAPSHOT
    )
    if len(expected_import_entries) != len(set(expected_import_entries)):
        raise ValueError("duplicate M-04B App Shell import snapshot entry")
    if any(not is_project_import(imported) for imported in expected_import_entries):
        raise ValueError("invalid non-project M-04B App Shell import snapshot entry")
    expected_project_imports = Counter(expected_import_entries)
    actual_project_imports = Counter(
        imported
        for _, imported in source_imports(target_path)
        if is_project_import(imported)
    )
    if actual_project_imports != expected_project_imports:
        errors.append(
            "ARCH024 App Shell project imports differ: "
            f"expected {sorted(expected_project_imports.elements())}, "
            f"found {sorted(actual_project_imports.elements())}"
        )

    moved_symbol_patterns = {
        "KiyoriAppShell": r"\bfun\s+KiyoriAppShell\s*\(",
        "kiyoriStartupBeyondViewportPageCount": (
            r"\bfun\s+kiyoriStartupBeyondViewportPageCount\s*\("
        ),
        "shouldComposeKiyoriAiHost": (
            r"\bfun\s+shouldComposeKiyoriAiHost\s*\("
        ),
        "shouldPresentKiyoriPluginLoading": (
            r"\bfun\s+shouldPresentKiyoriPluginLoading\s*\("
        ),
        "shouldNotifyKiyoriAiHomeSettledForInitialPage": (
            r"\bfun\s+shouldNotifyKiyoriAiHomeSettledForInitialPage\s*\("
        ),
        "shouldProvideKiyoriSettingsTheme": (
            r"\bfun\s+shouldProvideKiyoriSettingsTheme\s*\("
        ),
        "calculateKiyoriAiHostTranslation": (
            r"\bfun\s+calculateKiyoriAiHostTranslation\s*\("
        ),
        "shouldReverseKiyoriPagerDrag": (
            r"\bfun\s+shouldReverseKiyoriPagerDrag\s*\("
        ),
        "shouldPresentKiyoriDownloadDrawer": (
            r"\bfun\s+shouldPresentKiyoriDownloadDrawer\s*\("
        ),
        "shouldPresentKiyoriBookmarkDrawer": (
            r"\bfun\s+shouldPresentKiyoriBookmarkDrawer\s*\("
        ),
        "shouldPresentKiyoriHistoryDrawer": (
            r"\bfun\s+shouldPresentKiyoriHistoryDrawer\s*\("
        ),
        "shouldEnableKiyoriShellBackHandler": (
            r"\bfun\s+shouldEnableKiyoriShellBackHandler\s*\("
        ),
        "calculateKiyoriPagerPageOffset": (
            r"\bfun\s+calculateKiyoriPagerPageOffset\s*\("
        ),
    }
    main_source_root = root / "app/src/main/java"
    source_paths = (
        sorted(
            path
            for path in main_source_root.rglob("*.kt")
            if path.is_file()
        )
        if main_source_root.is_dir()
        else []
    )
    for symbol, pattern in moved_symbol_patterns.items():
        declaration_sites = [
            path.relative_to(root).as_posix()
            for path in source_paths
            if re.search(
                pattern,
                source_code_mask(path.read_text(encoding="utf-8")),
            )
        ]
        if declaration_sites != [M04B_APP_SHELL_PATH]:
            errors.append(
                "ARCH024 App Shell symbol must have one owner: "
                f"{symbol} found {declaration_sites}"
            )

    root_path = root / M04_ROOT_PATH
    if root_path.is_file():
        root_imports = Counter(imported for _, imported in source_imports(root_path))
        expected_import = f"{M04B_APP_SHELL_PACKAGE}.KiyoriAppShell"
        if root_imports[expected_import] != 1:
            errors.append(
                "ARCH024 KiyoriApp must import KiyoriAppShell exactly once"
            )
        stale_import = (
            "com.ai.assistance.operit.ui.main.shell.KiyoriAppShell"
        )
        if root_imports[stale_import] != 0:
            errors.append(
                "ARCH024 KiyoriApp still imports the old App Shell owner"
            )
        root_code = source_code_mask(root_path.read_text(encoding="utf-8"))
        if len(re.findall(r"\bKiyoriAppShell\s*\(", root_code)) != 1:
            errors.append(
                "ARCH024 KiyoriApp must host exactly one KiyoriAppShell call"
            )

    helper_symbols = set(moved_symbol_patterns) - {"KiyoriAppShell"}
    test_path = root / M04B_APP_SHELL_TEST_PATH
    if test_path.is_file():
        test_imports = Counter(imported for _, imported in source_imports(test_path))
        for symbol in helper_symbols:
            expected_import = f"{M04B_APP_SHELL_PACKAGE}.{symbol}"
            if test_imports[expected_import] != 1:
                errors.append(
                    "ARCH024 Shell host test must import moved helper exactly once: "
                    f"{expected_import}"
                )
            stale_import = (
                "com.ai.assistance.operit.ui.main.shell."
                f"{symbol}"
            )
            if test_imports[stale_import] != 0:
                errors.append(
                    "ARCH024 Shell host test still imports old helper owner: "
                    f"{stale_import}"
                )


def normalize_m04b_ai_drawer_text(text: str) -> str:
    normalized = text.replace("\r\n", "\n")
    lines: list[str] = []
    for line in normalized.splitlines(keepends=True):
        if re.match(
            r"^package (?:com\.ai\.assistance\.operit\.ui\.main\.shell|"
            r"com\.kiyori\.app\.shell)\s*$",
            line.rstrip("\n"),
        ):
            lines.append("package __M04B_AI_DRAWER_PACKAGE__\n")
            continue
        if (
            line.rstrip("\n")
            == "import com.kiyori.app.shell.calculateKiyoriAiDrawerWidthDp"
        ):
            continue
        lines.append(line)
    return "".join(lines)


def check_m04b_ai_drawer_owner(root: Path, errors: list[str]) -> None:
    target_path = root / M04B_AI_DRAWER_PATH
    if not target_path.is_file():
        return

    if (root / M04B_OLD_AI_DRAWER_PATH).exists():
        errors.append(
            f"ARCH025 old AI Drawer path remains: {M04B_OLD_AI_DRAWER_PATH}"
        )

    declared_package = source_package(target_path)
    if declared_package != M04B_AI_DRAWER_PACKAGE:
        errors.append(
            "ARCH025 AI Drawer package differs: "
            f"{declared_package or 'missing'}"
        )

    architecture_root = root / "config/architecture"
    hash_entries = read_snapshot(
        architecture_root / M04B_AI_DRAWER_NORMALIZED_HASH_SNAPSHOT
    )
    if len(hash_entries) != 1 or not re.fullmatch(r"[0-9A-Fa-f]{64}", hash_entries[0]):
        raise ValueError("invalid M-04B AI Drawer normalized hash snapshot")
    normalized = normalize_m04b_ai_drawer_text(
        target_path.read_text(encoding="utf-8")
    ).encode("utf-8")
    actual_hash = hashlib.sha256(normalized).hexdigest().upper()
    expected_hash = hash_entries[0].upper()
    if actual_hash != expected_hash:
        errors.append(
            "ARCH025 AI Drawer changed outside the approved package-only move: "
            f"expected {expected_hash}, found {actual_hash}"
        )

    expected_import_entries = read_snapshot(
        architecture_root / M04B_AI_DRAWER_PROJECT_IMPORT_SNAPSHOT
    )
    if len(expected_import_entries) != len(set(expected_import_entries)):
        raise ValueError("duplicate M-04B AI Drawer import snapshot entry")
    if any(not is_project_import(imported) for imported in expected_import_entries):
        raise ValueError("invalid non-project M-04B AI Drawer import snapshot entry")
    expected_project_imports = Counter(expected_import_entries)
    actual_project_imports = Counter(
        imported
        for _, imported in source_imports(target_path)
        if is_project_import(imported)
    )
    if actual_project_imports != expected_project_imports:
        errors.append(
            "ARCH025 AI Drawer project imports differ: "
            f"expected {sorted(expected_project_imports.elements())}, "
            f"found {sorted(actual_project_imports.elements())}"
        )

    moved_symbol_patterns = {
        "KiyoriModalAiDrawer": r"\bfun\s+KiyoriModalAiDrawer\s*\(",
        "resolveKiyoriAiDrawerTone": (
            r"\bfun\s+resolveKiyoriAiDrawerTone\s*\("
        ),
    }
    main_source_root = root / "app/src/main/java"
    source_paths = (
        sorted(
            path
            for path in main_source_root.rglob("*.kt")
            if path.is_file()
        )
        if main_source_root.is_dir()
        else []
    )
    for symbol, pattern in moved_symbol_patterns.items():
        declaration_sites = [
            path.relative_to(root).as_posix()
            for path in source_paths
            if re.search(
                pattern,
                source_code_mask(path.read_text(encoding="utf-8")),
            )
        ]
        if declaration_sites != [M04B_AI_DRAWER_PATH]:
            errors.append(
                "ARCH025 AI Drawer symbol must have one owner: "
                f"{symbol} found {declaration_sites}"
            )

    app_shell_path = root / M04B_APP_SHELL_PATH
    if app_shell_path.is_file():
        app_shell_imports = Counter(
            imported for _, imported in source_imports(app_shell_path)
        )
        stale_import = (
            "com.ai.assistance.operit.ui.main.shell.KiyoriModalAiDrawer"
        )
        if app_shell_imports[stale_import] != 0:
            errors.append(
                "ARCH025 App Shell still imports the old AI Drawer owner"
            )
        app_shell_code = source_code_mask(
            app_shell_path.read_text(encoding="utf-8")
        )
        if len(re.findall(r"\bKiyoriModalAiDrawer\s*\(", app_shell_code)) != 1:
            errors.append(
                "ARCH025 App Shell must host exactly one KiyoriModalAiDrawer call"
            )

    test_path = root / M04B_APP_SHELL_TEST_PATH
    if test_path.is_file():
        test_imports = Counter(imported for _, imported in source_imports(test_path))
        expected_import = (
            f"{M04B_AI_DRAWER_PACKAGE}.resolveKiyoriAiDrawerTone"
        )
        if test_imports[expected_import] != 1:
            errors.append(
                "ARCH025 Shell test must import moved AI Drawer tone helper exactly once"
            )
        stale_import = (
            "com.ai.assistance.operit.ui.main.shell.resolveKiyoriAiDrawerTone"
        )
        if test_imports[stale_import] != 0:
            errors.append(
                "ARCH025 Shell test still imports old AI Drawer tone helper"
            )


def check_m04b_primary_navigation_owner(root: Path, errors: list[str]) -> None:
    target_path = root / M04B_PRIMARY_NAVIGATION_PATH
    if not target_path.is_file():
        return

    declared_package = source_package(target_path)
    if declared_package != M04B_PRIMARY_NAVIGATION_PACKAGE:
        errors.append(
            "ARCH026 primary navigation package differs: "
            f"{declared_package or 'missing'}"
        )

    architecture_root = root / "config/architecture"
    hash_entries = read_snapshot(
        architecture_root / M04B_PRIMARY_NAVIGATION_HASH_SNAPSHOT
    )
    if len(hash_entries) != 1 or not re.fullmatch(r"[0-9A-Fa-f]{64}", hash_entries[0]):
        raise ValueError("invalid M-04B primary navigation hash snapshot")
    normalized = target_path.read_bytes().replace(b"\r\n", b"\n")
    actual_hash = hashlib.sha256(normalized).hexdigest().upper()
    expected_hash = hash_entries[0].upper()
    if actual_hash != expected_hash:
        errors.append(
            "ARCH026 primary navigation source changed: "
            f"expected {expected_hash}, found {actual_hash}"
        )

    expected_import_entries = read_snapshot(
        architecture_root / M04B_PRIMARY_NAVIGATION_PROJECT_IMPORT_SNAPSHOT
    )
    if len(expected_import_entries) != len(set(expected_import_entries)):
        raise ValueError("duplicate M-04B primary navigation import snapshot entry")
    if any(not is_project_import(imported) for imported in expected_import_entries):
        raise ValueError("invalid non-project primary navigation import snapshot entry")
    expected_project_imports = Counter(expected_import_entries)
    actual_project_imports = Counter(
        imported
        for _, imported in source_imports(target_path)
        if is_project_import(imported)
    )
    if actual_project_imports != expected_project_imports:
        errors.append(
            "ARCH026 primary navigation project imports differ: "
            f"expected {sorted(expected_project_imports.elements())}, "
            f"found {sorted(actual_project_imports.elements())}"
        )

    moved_symbol_patterns = {
        "PrimaryDestinationVisual": (
            r"\bdata\s+class\s+PrimaryDestinationVisual\b"
        ),
        "primaryDestinationVisuals": r"\bval\s+primaryDestinationVisuals\b",
        "resolveKiyoriBottomNavigationSelectedStartScale": (
            r"\bfun\s+resolveKiyoriBottomNavigationSelectedStartScale\s*\("
        ),
        "resolveKiyoriBottomNavigationSelectedFinalScale": (
            r"\bfun\s+resolveKiyoriBottomNavigationSelectedFinalScale\s*\("
        ),
        "resolveKiyoriBottomNavigationSelectedSpringDampingRatio": (
            r"\bfun\s+resolveKiyoriBottomNavigationSelectedSpringDampingRatio\s*\("
        ),
        "KiyoriPrimaryRootPage": r"\bfun\s+KiyoriPrimaryRootPage\s*\(",
        "KiyoriBottomNavigation": r"\bfun\s+KiyoriBottomNavigation\s*\(",
        "KiyoriBottomNavigationIcon": (
            r"\bfun\s+KiyoriBottomNavigationIcon\s*\("
        ),
    }
    main_source_root = root / "app/src/main/java"
    source_paths = (
        sorted(
            path
            for path in main_source_root.rglob("*.kt")
            if path.is_file()
        )
        if main_source_root.is_dir()
        else []
    )
    for symbol, pattern in moved_symbol_patterns.items():
        declaration_sites = [
            path.relative_to(root).as_posix()
            for path in source_paths
            if re.search(
                pattern,
                source_code_mask(path.read_text(encoding="utf-8")),
            )
        ]
        if declaration_sites != [M04B_PRIMARY_NAVIGATION_PATH]:
            errors.append(
                "ARCH026 primary navigation symbol must have one owner: "
                f"{symbol} found {declaration_sites}"
            )

    app_shell_path = root / M04B_APP_SHELL_PATH
    if app_shell_path.is_file():
        app_shell_imports = Counter(
            imported for _, imported in source_imports(app_shell_path)
        )
        for symbol in ("KiyoriPrimaryRootPage", "KiyoriBottomNavigation"):
            stale_import = (
                "com.ai.assistance.operit.ui.main.shell."
                f"{symbol}"
            )
            if app_shell_imports[stale_import] != 0:
                errors.append(
                    "ARCH026 App Shell still imports the old primary navigation owner: "
                    f"{stale_import}"
                )
        app_shell_code = source_code_mask(
            app_shell_path.read_text(encoding="utf-8")
        )
        for symbol in ("KiyoriPrimaryRootPage", "KiyoriBottomNavigation"):
            if len(re.findall(rf"\b{symbol}\s*\(", app_shell_code)) != 1:
                errors.append(
                    "ARCH026 App Shell must host exactly one primary navigation call: "
                    f"{symbol}"
                )

    test_path = root / M04B_APP_SHELL_TEST_PATH
    if test_path.is_file():
        test_imports = Counter(imported for _, imported in source_imports(test_path))
        for symbol in (
            "resolveKiyoriBottomNavigationSelectedStartScale",
            "resolveKiyoriBottomNavigationSelectedFinalScale",
            "resolveKiyoriBottomNavigationSelectedSpringDampingRatio",
        ):
            expected_import = f"{M04B_PRIMARY_NAVIGATION_PACKAGE}.{symbol}"
            if test_imports[expected_import] != 1:
                errors.append(
                    "ARCH026 Shell test must import primary navigation policy exactly once: "
                    f"{expected_import}"
                )
            stale_import = (
                "com.ai.assistance.operit.ui.main.shell."
                f"{symbol}"
            )
            if test_imports[stale_import] != 0:
                errors.append(
                    "ARCH026 Shell test still imports old primary navigation policy: "
                    f"{stale_import}"
                )


def check_m04b_software_home_owner(root: Path, errors: list[str]) -> None:
    target_path = root / M04B_SOFTWARE_HOME_PATH
    if not target_path.is_file():
        return

    declared_package = source_package(target_path)
    if declared_package != M04B_SOFTWARE_HOME_PACKAGE:
        errors.append(
            "ARCH027 Software Home package differs: "
            f"{declared_package or 'missing'}"
        )

    architecture_root = root / "config/architecture"
    hash_entries = read_snapshot(
        architecture_root / M04B_SOFTWARE_HOME_HASH_SNAPSHOT
    )
    if len(hash_entries) != 1 or not re.fullmatch(r"[0-9A-Fa-f]{64}", hash_entries[0]):
        raise ValueError("invalid M-04B Software Home hash snapshot")
    normalized = target_path.read_bytes().replace(b"\r\n", b"\n")
    actual_hash = hashlib.sha256(normalized).hexdigest().upper()
    expected_hash = hash_entries[0].upper()
    if actual_hash != expected_hash:
        errors.append(
            "ARCH027 Software Home source changed: "
            f"expected {expected_hash}, found {actual_hash}"
        )

    expected_import_entries = read_snapshot(
        architecture_root / M04B_SOFTWARE_HOME_PROJECT_IMPORT_SNAPSHOT
    )
    if len(expected_import_entries) != len(set(expected_import_entries)):
        raise ValueError("duplicate M-04B Software Home import snapshot entry")
    if any(not is_project_import(imported) for imported in expected_import_entries):
        raise ValueError("invalid non-project Software Home import snapshot entry")
    expected_project_imports = Counter(expected_import_entries)
    actual_project_imports = Counter(
        imported
        for _, imported in source_imports(target_path)
        if is_project_import(imported)
    )
    if actual_project_imports != expected_project_imports:
        errors.append(
            "ARCH027 Software Home project imports differ: "
            f"expected {sorted(expected_project_imports.elements())}, "
            f"found {sorted(actual_project_imports.elements())}"
        )

    moved_symbol_patterns = {
        "KiyoriSoftwareHomePage": r"\bfun\s+KiyoriSoftwareHomePage\s*\(",
        "KiyoriSoftwareHomeLayout": (
            r"\benum\s+class\s+KiyoriSoftwareHomeLayout\b"
        ),
        "KiyoriSoftwareHomeHeightLayout": (
            r"\benum\s+class\s+KiyoriSoftwareHomeHeightLayout\b"
        ),
        "KiyoriSoftwareHomeMode": (
            r"\benum\s+class\s+KiyoriSoftwareHomeMode\b"
        ),
        "KiyoriSoftwareHomePrimaryTarget": (
            r"\benum\s+class\s+KiyoriSoftwareHomePrimaryTarget\b"
        ),
        "KIYORI_SOFTWARE_HOME_GOLDEN_TOP_FRACTION": (
            r"\bconst\s+val\s+KIYORI_SOFTWARE_HOME_GOLDEN_TOP_FRACTION\b"
        ),
        "KIYORI_HOME_MODE_SEGMENT_WIDTH_DP": (
            r"\bconst\s+val\s+KIYORI_HOME_MODE_SEGMENT_WIDTH_DP\b"
        ),
        "KIYORI_HOME_MODE_SEGMENT_HEIGHT_DP": (
            r"\bconst\s+val\s+KIYORI_HOME_MODE_SEGMENT_HEIGHT_DP\b"
        ),
        "KIYORI_HOME_BRAND_ICON_SIZE_DP": (
            r"\bconst\s+val\s+KIYORI_HOME_BRAND_ICON_SIZE_DP\b"
        ),
        "KIYORI_HOME_REGULAR_SEARCH_FRAME_HEIGHT_DP": (
            r"\bconst\s+val\s+KIYORI_HOME_REGULAR_SEARCH_FRAME_HEIGHT_DP\b"
        ),
        "KIYORI_HOME_SHORT_SEARCH_FRAME_HEIGHT_DP": (
            r"\bconst\s+val\s+KIYORI_HOME_SHORT_SEARCH_FRAME_HEIGHT_DP\b"
        ),
        "KIYORI_HOME_COMPACT_HORIZONTAL_PADDING_DP": (
            r"\bconst\s+val\s+KIYORI_HOME_COMPACT_HORIZONTAL_PADDING_DP\b"
        ),
        "KIYORI_HOME_MEDIUM_HORIZONTAL_PADDING_DP": (
            r"\bconst\s+val\s+KIYORI_HOME_MEDIUM_HORIZONTAL_PADDING_DP\b"
        ),
        "KIYORI_HOME_EXPANDED_HORIZONTAL_PADDING_DP": (
            r"\bconst\s+val\s+KIYORI_HOME_EXPANDED_HORIZONTAL_PADDING_DP\b"
        ),
        "KIYORI_HOME_COMPACT_MAX_WIDTH_DP": (
            r"\bconst\s+val\s+KIYORI_HOME_COMPACT_MAX_WIDTH_DP\b"
        ),
        "KIYORI_HOME_MEDIUM_MAX_WIDTH_DP": (
            r"\bconst\s+val\s+KIYORI_HOME_MEDIUM_MAX_WIDTH_DP\b"
        ),
        "KIYORI_HOME_EXPANDED_MAX_WIDTH_DP": (
            r"\bconst\s+val\s+KIYORI_HOME_EXPANDED_MAX_WIDTH_DP\b"
        ),
        "KIYORI_HOME_SEARCH_FRAME_STROKE_WIDTH_DP": (
            r"\bconst\s+val\s+KIYORI_HOME_SEARCH_FRAME_STROKE_WIDTH_DP\b"
        ),
        "KIYORI_HOME_TOOL_ICON_SIZE_DP": (
            r"\bconst\s+val\s+KIYORI_HOME_TOOL_ICON_SIZE_DP\b"
        ),
        "KIYORI_HOME_ATTACHMENT_ICON_SIZE_DP": (
            r"\bconst\s+val\s+KIYORI_HOME_ATTACHMENT_ICON_SIZE_DP\b"
        ),
        "KIYORI_HOME_ATTACHMENT_ICON_ALPHA": (
            r"\bconst\s+val\s+KIYORI_HOME_ATTACHMENT_ICON_ALPHA\b"
        ),
        "KIYORI_HOME_SEARCH_FRAME_GRADIENT_COLORS": (
            r"\bval\s+KIYORI_HOME_SEARCH_FRAME_GRADIENT_COLORS\b"
        ),
        "KIYORI_HOME_SELECTED_LIGHT_COLOR": (
            r"\bval\s+KIYORI_HOME_SELECTED_LIGHT_COLOR\b"
        ),
        "KIYORI_HOME_SELECTED_DARK_COLOR": (
            r"\bval\s+KIYORI_HOME_SELECTED_DARK_COLOR\b"
        ),
        "resolveKiyoriSoftwareHomeLayout": (
            r"\bfun\s+resolveKiyoriSoftwareHomeLayout\s*\("
        ),
        "resolveKiyoriSoftwareHomeHeightLayout": (
            r"\bfun\s+resolveKiyoriSoftwareHomeHeightLayout\s*\("
        ),
        "resolveKiyoriSoftwareHomeSearchTopY": (
            r"\bfun\s+resolveKiyoriSoftwareHomeSearchTopY\s*\("
        ),
        "resolveKiyoriSoftwareHomePrimaryTarget": (
            r"\bfun\s+resolveKiyoriSoftwareHomePrimaryTarget\s*\("
        ),
        "KiyoriHomeTopActions": r"\bfun\s+KiyoriHomeTopActions\s*\(",
        "KiyoriHomeBrandTitle": r"\bfun\s+KiyoriHomeBrandTitle\s*\(",
        "KiyoriHomeSearchFrame": r"\bfun\s+KiyoriHomeSearchFrame\s*\(",
        "KiyoriSearchAiSegment": r"\bfun\s+KiyoriSearchAiSegment\s*\(",
        "KiyoriSearchAiSegmentOption": (
            r"\bfun\s+KiyoriSearchAiSegmentOption\s*\("
        ),
        "KiyoriHomeToolButton": r"\bfun\s+KiyoriHomeToolButton\s*\(",
        "KiyoriWeatherButton": r"\bfun\s+KiyoriWeatherButton\s*\(",
        "KiyoriBrowserWindowsButton": (
            r"\bfun\s+KiyoriBrowserWindowsButton\s*\("
        ),
        "kiyoriWeatherIcon": r"\bfun\s+kiyoriWeatherIcon\s*\(",
        "kiyoriGradientSearchFrame": (
            r"\bfun\s+Modifier\.kiyoriGradientSearchFrame\s*\("
        ),
    }
    main_source_root = root / "app/src/main/java"
    source_paths = (
        sorted(
            path
            for path in main_source_root.rglob("*.kt")
            if path.is_file()
        )
        if main_source_root.is_dir()
        else []
    )
    for symbol, pattern in moved_symbol_patterns.items():
        declaration_sites = [
            path.relative_to(root).as_posix()
            for path in source_paths
            if re.search(
                pattern,
                source_code_mask(path.read_text(encoding="utf-8")),
            )
        ]
        if declaration_sites != [M04B_SOFTWARE_HOME_PATH]:
            errors.append(
                "ARCH027 Software Home symbol must have one owner: "
                f"{symbol} found {declaration_sites}"
            )

    app_shell_path = root / M04B_APP_SHELL_PATH
    if app_shell_path.is_file():
        app_shell_imports = Counter(
            imported for _, imported in source_imports(app_shell_path)
        )
        stale_import = (
            "com.ai.assistance.operit.ui.main.shell.KiyoriSoftwareHomePage"
        )
        if app_shell_imports[stale_import] != 0:
            errors.append(
                "ARCH027 App Shell still imports the old Software Home owner"
            )
        app_shell_code = source_code_mask(
            app_shell_path.read_text(encoding="utf-8")
        )
        if len(re.findall(r"\bKiyoriSoftwareHomePage\s*\(", app_shell_code)) != 1:
            errors.append(
                "ARCH027 App Shell must host exactly one Software Home call"
            )

    test_path = root / M04B_SOFTWARE_HOME_TEST_PATH
    if test_path.is_file():
        test_imports = Counter(imported for _, imported in source_imports(test_path))
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
        for symbol in tested_symbols:
            expected_import = f"{M04B_SOFTWARE_HOME_PACKAGE}.{symbol}"
            if test_imports[expected_import] != 1:
                errors.append(
                    "ARCH027 Software Home test must import moved symbol exactly once: "
                    f"{expected_import}"
                )
            stale_import = (
                "com.ai.assistance.operit.ui.main.shell."
                f"{symbol}"
            )
            if test_imports[stale_import] != 0:
                errors.append(
                    "ARCH027 Software Home test still imports old symbol: "
                    f"{stale_import}"
                )


def check_m04b_browser_search_owner(root: Path, errors: list[str]) -> None:
    target_path = root / M04B_BROWSER_SEARCH_PATH
    if not target_path.is_file():
        return

    old_path = root / M04B_SHELL_PAGES_PATH
    if old_path.exists():
        errors.append(
            f"ARCH028 residual Browser Search old path remains: {M04B_SHELL_PAGES_PATH}"
        )

    declared_package = source_package(target_path)
    if declared_package != M04B_BROWSER_SEARCH_PACKAGE:
        errors.append(
            "ARCH028 Browser Search package differs: "
            f"{declared_package or 'missing'}"
        )

    architecture_root = root / "config/architecture"
    hash_entries = read_snapshot(
        architecture_root / M04B_BROWSER_SEARCH_HASH_SNAPSHOT
    )
    if len(hash_entries) != 1 or not re.fullmatch(r"[0-9A-Fa-f]{64}", hash_entries[0]):
        raise ValueError("invalid M-04B Browser Search hash snapshot")
    normalized = target_path.read_bytes().replace(b"\r\n", b"\n")
    actual_hash = hashlib.sha256(normalized).hexdigest().upper()
    expected_hash = hash_entries[0].upper()
    if actual_hash != expected_hash:
        errors.append(
            "ARCH028 Browser Search source changed: "
            f"expected {expected_hash}, found {actual_hash}"
        )

    expected_import_entries = read_snapshot(
        architecture_root / M04B_BROWSER_SEARCH_OPERIT_IMPORT_SNAPSHOT
    )
    if len(expected_import_entries) != len(set(expected_import_entries)):
        raise ValueError("duplicate M-04B Browser Search import snapshot entry")
    if any(
        not (
            import_matches_root(imported, "com.ai.assistance.operit")
            or imported == M04B_BROWSER_SEARCH_CAPABILITY_IMPORT
        )
        for imported in expected_import_entries
    ):
        raise ValueError("invalid non-Operit Browser Search import snapshot entry")
    expected_project_imports = Counter(expected_import_entries)
    actual_project_imports = Counter(
        imported
        for _, imported in source_imports(target_path)
        if is_project_import(imported)
    )
    if actual_project_imports != expected_project_imports:
        errors.append(
            "ARCH028 Browser Search project imports differ: "
            f"expected {sorted(expected_project_imports.elements())}, "
            f"found {sorted(actual_project_imports.elements())}"
        )

    moved_symbol_patterns = {
        "KiyoriFullScreenWebSearchPage": (
            r"\bfun\s+KiyoriFullScreenWebSearchPage\s*\("
        ),
        "KiyoriWebSearchRequest": (
            r"\bdata\s+class\s+KiyoriWebSearchRequest\b"
        ),
        "resolveKiyoriWebSearchRequest": (
            r"\bfun\s+resolveKiyoriWebSearchRequest\s*\("
        ),
    }
    main_source_root = root / "app/src/main/java"
    source_paths = (
        sorted(
            path
            for path in main_source_root.rglob("*.kt")
            if path.is_file()
        )
        if main_source_root.is_dir()
        else []
    )
    for symbol, pattern in moved_symbol_patterns.items():
        declaration_sites = [
            path.relative_to(root).as_posix()
            for path in source_paths
            if re.search(
                pattern,
                source_code_mask(path.read_text(encoding="utf-8")),
            )
        ]
        if declaration_sites != [M04B_BROWSER_SEARCH_PATH]:
            errors.append(
                "ARCH028 Browser Search symbol must have one owner: "
                f"{symbol} found {declaration_sites}"
            )

    app_shell_path = root / M04B_APP_SHELL_PATH
    if app_shell_path.is_file():
        app_shell_imports = Counter(
            imported for _, imported in source_imports(app_shell_path)
        )
        for symbol in (
            "KiyoriFullScreenWebSearchPage",
            "KiyoriWebSearchRequest",
        ):
            stale_import = (
                "com.ai.assistance.operit.ui.main.shell."
                f"{symbol}"
            )
            if app_shell_imports[stale_import] != 0:
                errors.append(
                    "ARCH028 App Shell still imports the old Browser Search owner: "
                    f"{stale_import}"
                )
        app_shell_code = source_code_mask(
            app_shell_path.read_text(encoding="utf-8")
        )
        if len(
            re.findall(r"\bKiyoriFullScreenWebSearchPage\s*\(", app_shell_code)
        ) != 1:
            errors.append(
                "ARCH028 App Shell must host exactly one Browser Search call"
            )

    root_path = root / M04_ROOT_PATH
    if root_path.is_file():
        root_imports = Counter(imported for _, imported in source_imports(root_path))
        for symbol in (
            "KiyoriWebSearchRequest",
            "resolveKiyoriWebSearchRequest",
        ):
            expected_import = f"{M04B_BROWSER_SEARCH_PACKAGE}.{symbol}"
            if root_imports[expected_import] != 1:
                errors.append(
                    "ARCH028 KiyoriApp must import Browser Search symbol exactly once: "
                    f"{expected_import}"
                )
            stale_import = (
                "com.ai.assistance.operit.ui.main.shell."
                f"{symbol}"
            )
            if root_imports[stale_import] != 0:
                errors.append(
                    "ARCH028 KiyoriApp still imports old Browser Search symbol: "
                    f"{stale_import}"
                )

    test_path = root / M04B_SOFTWARE_HOME_TEST_PATH
    if test_path.is_file():
        test_imports = Counter(imported for _, imported in source_imports(test_path))
        for symbol in (
            "KiyoriWebSearchRequest",
            "resolveKiyoriWebSearchRequest",
        ):
            expected_import = f"{M04B_BROWSER_SEARCH_PACKAGE}.{symbol}"
            if test_imports[expected_import] != 1:
                errors.append(
                    "ARCH028 Browser Search test must import moved symbol exactly once: "
                    f"{expected_import}"
                )
            stale_import = (
                "com.ai.assistance.operit.ui.main.shell."
                f"{symbol}"
            )
            if test_imports[stale_import] != 0:
                errors.append(
                    "ARCH028 Browser Search test still imports old symbol: "
                    f"{stale_import}"
                )


def check_m04c_navigation_integration(root: Path, errors: list[str]) -> None:
    integration_path = root / M04C_NAVIGATION_INTEGRATION_PATH
    if not integration_path.is_file():
        return

    catalog_path = root / M04C_ROUTE_CATALOG_PATH
    if not catalog_path.is_file():
        errors.append(
            f"ARCH029 integration route catalog missing: {M04C_ROUTE_CATALOG_PATH}"
        )
        return
    old_catalog_path = root / M04C_OLD_ROUTE_CATALOG_PATH
    if old_catalog_path.exists():
        errors.append(
            f"ARCH029 old route catalog path remains: {M04C_OLD_ROUTE_CATALOG_PATH}"
        )

    for label, path in (
        ("route catalog", catalog_path),
        ("navigation integration", integration_path),
    ):
        declared_package = source_package(path)
        if declared_package != M04C_NAVIGATION_INTEGRATION_PACKAGE:
            errors.append(
                f"ARCH029 {label} package differs: "
                f"{declared_package or 'missing'}"
            )

    architecture_root = root / "config/architecture"
    source_contracts = (
        (
            "route catalog",
            catalog_path,
            M04C_ROUTE_CATALOG_HASH_SNAPSHOT,
            M04C_ROUTE_CATALOG_OPERIT_IMPORT_SNAPSHOT,
        ),
        (
            "navigation integration",
            integration_path,
            M04C_NAVIGATION_INTEGRATION_HASH_SNAPSHOT,
            M04C_NAVIGATION_INTEGRATION_OPERIT_IMPORT_SNAPSHOT,
        ),
    )
    for label, path, hash_snapshot, import_snapshot in source_contracts:
        hash_entries = read_snapshot(architecture_root / hash_snapshot)
        if (
            len(hash_entries) != 1
            or not re.fullmatch(r"[0-9A-Fa-f]{64}", hash_entries[0])
        ):
            raise ValueError(f"invalid M-04C {label} hash snapshot")
        normalized = path.read_bytes().replace(b"\r\n", b"\n")
        actual_hash = hashlib.sha256(normalized).hexdigest().upper()
        expected_hash = hash_entries[0].upper()
        if actual_hash != expected_hash:
            errors.append(
                f"ARCH029 {label} source changed: "
                f"expected {expected_hash}, found {actual_hash}"
            )

        expected_import_entries = read_snapshot(
            architecture_root / import_snapshot
        )
        if len(expected_import_entries) != len(set(expected_import_entries)):
            raise ValueError(f"duplicate M-04C {label} import snapshot entry")
        if any(
            not is_project_import(imported)
            for imported in expected_import_entries
        ):
            raise ValueError(f"invalid non-project M-04C {label} import snapshot entry")
        expected_project_imports = Counter(expected_import_entries)
        actual_project_imports = Counter(
            imported
            for _, imported in source_imports(path)
            if is_project_import(imported)
        )
        if actual_project_imports != expected_project_imports:
            errors.append(
                f"ARCH029 {label} project imports differ: "
                f"expected {sorted(expected_project_imports.elements())}, "
                f"found {sorted(actual_project_imports.elements())}"
            )

    moved_symbol_patterns = {
        "AppRouteCatalog": r"\bobject\s+AppRouteCatalog\b",
        "OperitNavigationIntegration": (
            r"\bclass\s+OperitNavigationIntegration\b"
        ),
        "rememberOperitNavigationIntegration": (
            r"\bfun\s+rememberOperitNavigationIntegration\s*\("
        ),
        "OperitNavigationIntegrationEffects": (
            r"\bfun\s+OperitNavigationIntegrationEffects\s*\("
        ),
        "findOperitNavigationRoot": (
            r"\bfun\s+List<NavigationEntrySpec>\.findOperitNavigationRoot\s*\("
        ),
        "toOperitExternalRouteEntry": (
            r"\bfun\s+List<NavigationEntrySpec>\.toOperitExternalRouteEntry\s*\("
        ),
    }
    main_source_root = root / "app/src/main/java"
    source_paths = (
        sorted(
            path
            for path in main_source_root.rglob("*.kt")
            if path.is_file()
        )
        if main_source_root.is_dir()
        else []
    )
    expected_symbol_paths = {
        "AppRouteCatalog": M04C_ROUTE_CATALOG_PATH,
        "OperitNavigationIntegration": M04C_NAVIGATION_INTEGRATION_PATH,
        "rememberOperitNavigationIntegration": M04C_NAVIGATION_INTEGRATION_PATH,
        "OperitNavigationIntegrationEffects": M04C_NAVIGATION_INTEGRATION_PATH,
        "findOperitNavigationRoot": M04C_NAVIGATION_INTEGRATION_PATH,
        "toOperitExternalRouteEntry": M04C_NAVIGATION_INTEGRATION_PATH,
    }
    for symbol, pattern in moved_symbol_patterns.items():
        declaration_sites = [
            path.relative_to(root).as_posix()
            for path in source_paths
            if re.search(
                pattern,
                source_code_mask(path.read_text(encoding="utf-8")),
            )
        ]
        expected_path = expected_symbol_paths[symbol]
        if declaration_sites != [expected_path]:
            errors.append(
                "ARCH029 navigation integration symbol must have one owner: "
                f"{symbol} found {declaration_sites}"
            )

    catalog_code = source_code_mask(catalog_path.read_text(encoding="utf-8"))
    if not re.search(
        r"\bfun\s+build\s*\(\s*context:\s*Context,\s*"
        r"packageManager:\s*PackageManager",
        catalog_code,
        re.DOTALL,
    ):
        errors.append(
            "ARCH029 route catalog must receive the unique PackageManager instance"
        )
    if "PackageManager.getInstance" in catalog_code:
        errors.append(
            "ARCH029 route catalog still resolves a second PackageManager reference"
        )

    integration_code = source_code_mask(
        integration_path.read_text(encoding="utf-8")
    )
    required_integration_counts = {
        "PackageManager.getInstance": 1,
        "addToolPkgRuntimeChangeListener": 1,
        "removeToolPkgRuntimeChangeListener": 1,
        "AppRouterGateway.install": 1,
        "AppRouterGateway.clear": 1,
        "AppRouteDiscoveryGateway.install": 1,
        "AppRouteDiscoveryGateway.clear": 1,
    }
    for token, expected_count in required_integration_counts.items():
        actual_count = integration_code.count(token)
        if actual_count != expected_count:
            errors.append(
                "ARCH029 navigation integration lifecycle count differs: "
                f"{token} expected {expected_count}, found {actual_count}"
            )

    root_path = root / M04_ROOT_PATH
    if root_path.is_file():
        root_imports = Counter(imported for _, imported in source_imports(root_path))
        forbidden_imports = (
            "androidx.compose.runtime.DisposableEffect",
            "com.ai.assistance.operit.core.tools.AIToolHandler",
            "com.ai.assistance.operit.core.tools.packTool.PackageManager",
            "com.ai.assistance.operit.ui.main.navigation.AppNavigationModel",
            "com.ai.assistance.operit.ui.main.navigation.AppRouteCatalog",
            "com.ai.assistance.operit.ui.main.navigation.AppRouteDiscoveryGateway",
            "com.ai.assistance.operit.ui.main.navigation.AppRouterGateway",
        )
        for imported in forbidden_imports:
            if root_imports[imported] != 0:
                errors.append(
                    "ARCH029 KiyoriApp still imports extracted navigation assembly: "
                    f"{imported}"
                )
        required_imports = (
            (
                f"{M04C_NAVIGATION_INTEGRATION_PACKAGE}."
                "OperitNavigationIntegrationEffects"
            ),
            (
                f"{M04C_NAVIGATION_INTEGRATION_PACKAGE}."
                "rememberOperitNavigationIntegration"
            ),
            (
                f"{M04C_NAVIGATION_INTEGRATION_PACKAGE}."
                "toOperitExternalRouteEntry"
            ),
        )
        for imported in required_imports:
            if root_imports[imported] != 1:
                errors.append(
                    "ARCH029 KiyoriApp must import navigation integration symbol exactly once: "
                    f"{imported}"
                )
        root_code = source_code_mask(root_path.read_text(encoding="utf-8"))
        forbidden_tokens = (
            "PackageManager.getInstance",
            "AppRouteCatalog.",
            "AppRouterGateway.install",
            "AppRouterGateway.clear",
            "AppRouteDiscoveryGateway.install",
            "AppRouteDiscoveryGateway.clear",
            "addToolPkgRuntimeChangeListener",
            "removeToolPkgRuntimeChangeListener",
        )
        for token in forbidden_tokens:
            if token in root_code:
                errors.append(
                    "ARCH029 KiyoriApp still owns navigation integration lifecycle: "
                    f"{token}"
                )
        for symbol in (
            "rememberOperitNavigationIntegration",
            "OperitNavigationIntegrationEffects",
        ):
            if len(re.findall(rf"\b{symbol}\s*\(", root_code)) != 1:
                errors.append(
                    "ARCH029 KiyoriApp must host navigation integration exactly once: "
                    f"{symbol}"
                )

    settings_test_path = root / M04C_SETTINGS_TEST_PATH
    if settings_test_path.is_file():
        test_imports = Counter(
            imported for _, imported in source_imports(settings_test_path)
        )
        expected_import = (
            f"{M04C_NAVIGATION_INTEGRATION_PACKAGE}.AppRouteCatalog"
        )
        if test_imports[expected_import] != 1:
            errors.append(
                "ARCH029 settings test must import moved route catalog exactly once"
            )
        stale_import = (
            "com.ai.assistance.operit.ui.main.navigation.AppRouteCatalog"
        )
        if test_imports[stale_import] != 0:
            errors.append(
                "ARCH029 settings test still imports old route catalog"
            )


def check_m04d_main_pending_requests(root: Path, errors: list[str]) -> None:
    main_activity_path = root / M04_MAIN_ACTIVITY_PATH
    if not main_activity_path.is_file():
        return

    owner_path = root / M04D_MAIN_PENDING_REQUESTS_PATH
    if not owner_path.is_file():
        errors.append(
            "ARCH030 MainActivity pending-request owner missing: "
            f"{M04D_MAIN_PENDING_REQUESTS_PATH}"
        )
        return

    declared_package = source_package(owner_path)
    if declared_package != M04D_MAIN_PENDING_REQUESTS_PACKAGE:
        errors.append(
            "ARCH030 pending-request owner package differs: "
            f"{declared_package or 'missing'}"
        )

    architecture_root = root / "config/architecture"
    hash_entries = read_snapshot(
        architecture_root / M04D_MAIN_PENDING_REQUESTS_HASH_SNAPSHOT
    )
    if (
        len(hash_entries) != 1
        or not re.fullmatch(r"[0-9A-Fa-f]{64}", hash_entries[0])
    ):
        raise ValueError("invalid M-04D pending-request owner hash snapshot")
    normalized = owner_path.read_bytes().replace(b"\r\n", b"\n")
    actual_hash = hashlib.sha256(normalized).hexdigest().upper()
    expected_hash = hash_entries[0].upper()
    if actual_hash != expected_hash:
        errors.append(
            "ARCH030 pending-request owner source changed: "
            f"expected {expected_hash}, found {actual_hash}"
        )

    expected_import_entries = read_snapshot(
        architecture_root / M04D_MAIN_PENDING_REQUESTS_IMPORT_SNAPSHOT
    )
    if len(expected_import_entries) != len(set(expected_import_entries)):
        raise ValueError(
            "duplicate M-04D pending-request owner import snapshot entry"
        )
    if any(not is_project_import(imported) for imported in expected_import_entries):
        raise ValueError(
            "invalid non-project M-04D pending-request owner import snapshot entry"
        )
    expected_project_imports = Counter(expected_import_entries)
    actual_project_imports = Counter(
        imported
        for _, imported in source_imports(owner_path)
        if is_project_import(imported)
    )
    if actual_project_imports != expected_project_imports:
        errors.append(
            "ARCH030 pending-request owner project imports differ: "
            f"expected {sorted(expected_project_imports.elements())}, "
            f"found {sorted(actual_project_imports.elements())}"
        )

    main_source_root = root / "app/src/main/java"
    source_paths = (
        sorted(path for path in main_source_root.rglob("*.kt") if path.is_file())
        if main_source_root.is_dir()
        else []
    )
    declaration_sites = [
        path.relative_to(root).as_posix()
        for path in source_paths
        if re.search(
            r"\bclass\s+KiyoriMainPendingRequests\b",
            source_code_mask(path.read_text(encoding="utf-8")),
        )
    ]
    if declaration_sites != [M04D_MAIN_PENDING_REQUESTS_PATH]:
        errors.append(
            "ARCH030 pending-request state must have one owner: "
            f"KiyoriMainPendingRequests found {declaration_sites}"
        )

    owner_code = source_code_mask(owner_path.read_text(encoding="utf-8"))
    required_owner_methods = (
        "recordShellDestination",
        "recordShortcut",
        "recordRoute",
        "recordGitHubAuth",
        "recordBrowser",
        "recordSharedFiles",
        "recordSharedText",
        "takeGitHubAuthUri",
        "clearSharedText",
        "clearSharedFiles",
        "clearSharedFilesAndText",
        "consumeShortcut",
        "updateCurrentMainNavItem",
        "consumeRoute",
        "consumeBrowser",
        "consumeShell",
    )
    for method in required_owner_methods:
        if len(re.findall(rf"\bfun\s+{method}\s*\(", owner_code)) != 1:
            errors.append(
                "ARCH030 pending-request owner method count differs: "
                f"{method}"
            )
    for forbidden_token in (
        "System.currentTimeMillis",
        "SharedFileHandler",
        "GitHubOAuthCoordinator",
        "BrowserDownloadManager",
        "android.content.Intent",
    ):
        if forbidden_token in owner_code:
            errors.append(
                "ARCH030 pending-request owner absorbed host side effects: "
                f"{forbidden_token}"
            )

    main_imports = Counter(
        imported for _, imported in source_imports(main_activity_path)
    )
    expected_owner_import = (
        f"{M04D_MAIN_PENDING_REQUESTS_PACKAGE}.KiyoriMainPendingRequests"
    )
    if main_imports[expected_owner_import] != 1:
        errors.append(
            "ARCH030 MainActivity must import pending-request owner exactly once"
        )

    main_code = source_code_mask(
        main_activity_path.read_text(encoding="utf-8")
    )
    if len(
        re.findall(
            r"\bprivate\s+val\s+pendingRequests\s*=\s*"
            r"KiyoriMainPendingRequests\s*\(\s*\)",
            main_code,
        )
    ) != 1:
        errors.append(
            "ARCH030 MainActivity must hold exactly one pending-request owner"
        )

    legacy_field_names = (
        "pendingSharedFileUris",
        "pendingSharedText",
        "pendingBrowserUrl",
        "pendingBrowserRequestId",
        "pendingGitHubAuthUri",
        "pendingShortcutNavItem",
        "pendingShortcutRequestId",
        "currentMainNavItem",
        "pendingRouteId",
        "pendingRouteArgs",
        "pendingRouteRequestId",
        "pendingKiyoriShellDestination",
        "pendingKiyoriShellRequestId",
    )
    for field_name in legacy_field_names:
        if re.search(
            rf"\bprivate\s+(?:var|val)\s+{field_name}\b",
            main_code,
        ):
            errors.append(
                "ARCH030 MainActivity still declares pending-request field: "
                f"{field_name}"
            )

    required_host_calls = (
        "recordShellDestination",
        "recordShortcut",
        "recordRoute",
        "recordGitHubAuth",
        "recordBrowser",
        "recordSharedFiles",
        "recordSharedText",
        "takeGitHubAuthUri",
        "clearSharedText",
        "clearSharedFiles",
        "clearSharedFilesAndText",
        "consumeShortcut",
        "updateCurrentMainNavItem",
        "consumeRoute",
        "consumeBrowser",
        "consumeShell",
    )
    shared_content_coordinator_path = (
        root / M04D_MAIN_SHARED_CONTENT_COORDINATOR_PATH
    )
    host_integration_code = main_code
    if shared_content_coordinator_path.is_file():
        host_integration_code += "\n" + source_code_mask(
            shared_content_coordinator_path.read_text(encoding="utf-8")
        )
    content_host_path = root / M04D_MAIN_CONTENT_HOST_PATH
    if content_host_path.is_file():
        host_integration_code += "\n" + source_code_mask(
            content_host_path.read_text(encoding="utf-8")
        )
    for method in required_host_calls:
        if not re.search(
            rf"\bpendingRequests\.{method}\s*\(",
            host_integration_code,
        ):
            errors.append(
                "ARCH030 MainActivity host pending-request integration missing: "
                f"{method}"
            )

    test_path = root / M04D_MAIN_PENDING_REQUESTS_TEST_PATH
    if not test_path.is_file():
        errors.append(
            "ARCH030 pending-request owner contract test missing: "
            f"{M04D_MAIN_PENDING_REQUESTS_TEST_PATH}"
        )
    else:
        test_code = source_code_mask(test_path.read_text(encoding="utf-8"))
        if "KiyoriMainPendingRequests" not in test_code:
            errors.append(
                "ARCH030 pending-request owner contract test does not use the owner"
            )


def check_m04d_main_intent_decoder(root: Path, errors: list[str]) -> None:
    pending_requests_path = root / M04D_MAIN_PENDING_REQUESTS_PATH
    if not pending_requests_path.is_file():
        return

    decoder_path = root / M04D_MAIN_INTENT_DECODER_PATH
    if not decoder_path.is_file():
        errors.append(
            "ARCH031 MainActivity intent decoder missing: "
            f"{M04D_MAIN_INTENT_DECODER_PATH}"
        )
        return

    declared_package = source_package(decoder_path)
    if declared_package != M04D_MAIN_INTENT_DECODER_PACKAGE:
        errors.append(
            "ARCH031 intent decoder package differs: "
            f"{declared_package or 'missing'}"
        )

    architecture_root = root / "config/architecture"
    hash_entries = read_snapshot(
        architecture_root / M04D_MAIN_INTENT_DECODER_HASH_SNAPSHOT
    )
    if (
        len(hash_entries) != 1
        or not re.fullmatch(r"[0-9A-Fa-f]{64}", hash_entries[0])
    ):
        raise ValueError("invalid M-04D intent decoder hash snapshot")
    normalized = decoder_path.read_bytes().replace(b"\r\n", b"\n")
    actual_hash = hashlib.sha256(normalized).hexdigest().upper()
    expected_hash = hash_entries[0].upper()
    if actual_hash != expected_hash:
        errors.append(
            "ARCH031 intent decoder source changed: "
            f"expected {expected_hash}, found {actual_hash}"
        )

    expected_import_entries = read_snapshot(
        architecture_root / M04D_MAIN_INTENT_DECODER_IMPORT_SNAPSHOT
    )
    if len(expected_import_entries) != len(set(expected_import_entries)):
        raise ValueError("duplicate M-04D intent decoder import snapshot entry")
    if any(not is_project_import(imported) for imported in expected_import_entries):
        raise ValueError(
            "invalid non-project M-04D intent decoder import snapshot entry"
        )
    expected_project_imports = Counter(expected_import_entries)
    actual_project_imports = Counter(
        imported
        for _, imported in source_imports(decoder_path)
        if is_project_import(imported)
    )
    if actual_project_imports != expected_project_imports:
        errors.append(
            "ARCH031 intent decoder project imports differ: "
            f"expected {sorted(expected_project_imports.elements())}, "
            f"found {sorted(actual_project_imports.elements())}"
        )

    main_source_root = root / "app/src/main/java"
    source_paths = (
        sorted(path for path in main_source_root.rglob("*.kt") if path.is_file())
        if main_source_root.is_dir()
        else []
    )
    symbol_patterns = {
        "KiyoriMainIntentContract": (
            r"\bobject\s+KiyoriMainIntentContract\b"
        ),
        "KiyoriMainIntentCommand": (
            r"\bsealed\s+interface\s+KiyoriMainIntentCommand\b"
        ),
        "KiyoriMainIntentDecoding": (
            r"\bdata\s+class\s+KiyoriMainIntentDecoding\b"
        ),
        "decodeKiyoriMainIntent": (
            r"\bfun\s+decodeKiyoriMainIntent\s*\("
        ),
        "resolveKiyoriShellExternalDestination": (
            r"\bfun\s+resolveKiyoriShellExternalDestination\s*\("
        ),
        "resolveKiyoriDownloadTaskId": (
            r"\bfun\s+resolveKiyoriDownloadTaskId\s*\("
        ),
    }
    for symbol, pattern in symbol_patterns.items():
        declaration_sites = [
            path.relative_to(root).as_posix()
            for path in source_paths
            if re.search(
                pattern,
                source_code_mask(path.read_text(encoding="utf-8")),
            )
        ]
        if declaration_sites != [M04D_MAIN_INTENT_DECODER_PATH]:
            errors.append(
                "ARCH031 intent decoder symbol must have one owner: "
                f"{symbol} found {declaration_sites}"
            )

    decoder_code = source_code_mask(decoder_path.read_text(encoding="utf-8"))
    required_decoder_tokens = (
        "getLongExtra",
        "getStringExtra",
        "getParcelableExtra",
        "getParcelableArrayListExtra",
        "GitHubAuthPreferences.isOAuthRedirectUri",
    )
    for token in required_decoder_tokens:
        if token not in decoder_code:
            errors.append(
                "ARCH031 intent decoder contract extraction missing: "
                f"{token}"
            )
    for forbidden_token in (
        "System.currentTimeMillis",
        "PlayerSession",
        "BrowserDownloadManager",
        "Toast",
        "AppLogger",
        "SharedFileHandler",
        "GitHubOAuthCoordinator",
        "lifecycleScope",
        "intent.action =",
    ):
        if forbidden_token in decoder_code:
            errors.append(
                "ARCH031 intent decoder absorbed host side effects: "
                f"{forbidden_token}"
            )

    main_activity_path = root / M04_MAIN_ACTIVITY_PATH
    if not main_activity_path.is_file():
        errors.append(
            f"ARCH031 MainActivity host missing: {M04_MAIN_ACTIVITY_PATH}"
        )
        return
    main_imports = Counter(
        imported for _, imported in source_imports(main_activity_path)
    )
    for symbol in (
        "KiyoriMainIntentCommand",
        "KiyoriMainIntentContract",
        "decodeKiyoriMainIntent",
    ):
        expected_import = f"{M04D_MAIN_INTENT_DECODER_PACKAGE}.{symbol}"
        if main_imports[expected_import] != 1:
            errors.append(
                "ARCH031 MainActivity must import intent decoder symbol exactly once: "
                f"{expected_import}"
            )

    main_code = source_code_mask(
        main_activity_path.read_text(encoding="utf-8")
    )
    if len(re.findall(r"\bdecodeKiyoriMainIntent\s*\(", main_code)) != 1:
        errors.append(
            "ARCH031 MainActivity must decode each Intent through one decoder call"
        )
    for constant in M04D_MAIN_INTENT_CONTRACT_CONSTANTS:
        if len(
            re.findall(
                rf"\bconst\s+val\s+{constant}\s*=\s*"
                rf"KiyoriMainIntentContract\.{constant}\b",
                main_code,
            )
        ) != 1:
            errors.append(
                "ARCH031 MainActivity stable intent constant bridge differs: "
                f"{constant}"
            )
    forbidden_main_tokens = (
        "intent?.getLongExtra",
        "intent.getLongExtra",
        "intent?.getStringExtra",
        "intent.getStringExtra",
        "intent?.getParcelableExtra",
        "intent.getParcelableExtra",
        "intent?.getParcelableArrayListExtra",
        "intent.getParcelableArrayListExtra",
        "val intentUri = intent?.data",
    )
    for token in forbidden_main_tokens:
        if token in main_code:
            errors.append(
                "ARCH031 MainActivity still directly decodes Intent payload: "
                f"{token}"
            )
    if re.search(
        r"\bIntent\.ACTION_VIEW\s*\|\||"
        r"\bIntent\.ACTION_SEND\s*\|\||"
        r"\bIntent\.ACTION_SEND_MULTIPLE\b",
        main_code,
    ):
        errors.append(
            "ARCH031 MainActivity still branches on shared-content Intent actions"
        )

    test_path = root / M04D_MAIN_INTENT_DECODER_TEST_PATH
    if not test_path.is_file():
        errors.append(
            "ARCH031 intent decoder contract test missing: "
            f"{M04D_MAIN_INTENT_DECODER_TEST_PATH}"
        )
    else:
        test_code = source_code_mask(test_path.read_text(encoding="utf-8"))
        for symbol in (
            "KiyoriMainIntentCommand",
            "decodeKiyoriMainIntent",
        ):
            if symbol not in test_code:
                errors.append(
                    "ARCH031 intent decoder contract test does not use symbol: "
                    f"{symbol}"
                )


def check_m04d_main_display_coordinator(root: Path, errors: list[str]) -> None:
    decoder_path = root / M04D_MAIN_INTENT_DECODER_PATH
    if not decoder_path.is_file():
        return

    coordinator_path = root / M04D_MAIN_DISPLAY_COORDINATOR_PATH
    if not coordinator_path.is_file():
        errors.append(
            "ARCH032 MainActivity display coordinator missing: "
            f"{M04D_MAIN_DISPLAY_COORDINATOR_PATH}"
        )
        return

    declared_package = source_package(coordinator_path)
    if declared_package != M04D_MAIN_DISPLAY_COORDINATOR_PACKAGE:
        errors.append(
            "ARCH032 display coordinator package differs: "
            f"{declared_package or 'missing'}"
        )

    architecture_root = root / "config/architecture"
    hash_entries = read_snapshot(
        architecture_root / M04D_MAIN_DISPLAY_COORDINATOR_HASH_SNAPSHOT
    )
    if (
        len(hash_entries) != 1
        or not re.fullmatch(r"[0-9A-Fa-f]{64}", hash_entries[0])
    ):
        raise ValueError("invalid M-04D display coordinator hash snapshot")
    normalized = coordinator_path.read_bytes().replace(b"\r\n", b"\n")
    actual_hash = hashlib.sha256(normalized).hexdigest().upper()
    expected_hash = hash_entries[0].upper()
    if actual_hash != expected_hash:
        errors.append(
            "ARCH032 display coordinator source changed: "
            f"expected {expected_hash}, found {actual_hash}"
        )

    expected_import_entries = read_snapshot(
        architecture_root / M04D_MAIN_DISPLAY_COORDINATOR_IMPORT_SNAPSHOT
    )
    if len(expected_import_entries) != len(set(expected_import_entries)):
        raise ValueError(
            "duplicate M-04D display coordinator import snapshot entry"
        )
    if any(not is_project_import(imported) for imported in expected_import_entries):
        raise ValueError(
            "invalid non-project M-04D display coordinator import snapshot entry"
        )
    expected_project_imports = Counter(expected_import_entries)
    actual_project_imports = Counter(
        imported
        for _, imported in source_imports(coordinator_path)
        if is_project_import(imported)
    )
    if actual_project_imports != expected_project_imports:
        errors.append(
            "ARCH032 display coordinator project imports differ: "
            f"expected {sorted(expected_project_imports.elements())}, "
            f"found {sorted(actual_project_imports.elements())}"
        )

    main_source_root = root / "app/src/main/java"
    source_paths = (
        sorted(path for path in main_source_root.rglob("*.kt") if path.is_file())
        if main_source_root.is_dir()
        else []
    )
    symbol_patterns = {
        "KiyoriDisplayModeCandidate": (
            r"\bdata\s+class\s+KiyoriDisplayModeCandidate\b"
        ),
        "KiyoriDisplayModeSelection": (
            r"\bdata\s+class\s+KiyoriDisplayModeSelection\b"
        ),
        "selectHighestRefreshRateMode": (
            r"\bfun\s+selectHighestRefreshRateMode\s*\("
        ),
        "selectHighestRefreshRate": (
            r"\bfun\s+selectHighestRefreshRate\s*\("
        ),
        "KiyoriMainDisplayCoordinator": (
            r"\bobject\s+KiyoriMainDisplayCoordinator\b"
        ),
    }
    for symbol, pattern in symbol_patterns.items():
        declaration_sites = [
            path.relative_to(root).as_posix()
            for path in source_paths
            if re.search(
                pattern,
                source_code_mask(path.read_text(encoding="utf-8")),
            )
        ]
        if declaration_sites != [M04D_MAIN_DISPLAY_COORDINATOR_PATH]:
            errors.append(
                "ARCH032 display coordinator symbol must have one owner: "
                f"{symbol} found {declaration_sites}"
            )

    coordinator_text = coordinator_path.read_text(encoding="utf-8")
    coordinator_code = source_code_mask(coordinator_text)
    required_token_counts = {
        "setSustainedPerformanceMode": 1,
        "preferredDisplayModeId": 1,
        "preferredRefreshRate": 1,
        "FLAG_HARDWARE_ACCELERATED": 2,
        "getSystemService": 1,
    }
    for token, expected_count in required_token_counts.items():
        actual_count = coordinator_code.count(token)
        if actual_count != expected_count:
            errors.append(
                "ARCH032 display coordinator API count differs: "
                f"{token} expected {expected_count}, found {actual_count}"
            )
    for forbidden_token in (
        "mutableStateOf",
        "KiyoriMainPendingRequests",
        "KiyoriMainIntentCommand",
        "PlayerSession",
        "BrowserDownloadManager",
        "GitHubOAuthCoordinator",
        "SharedFileHandler",
        "lifecycleScope",
    ):
        if forbidden_token in coordinator_code:
            errors.append(
                "ARCH032 display coordinator absorbed unrelated host responsibility: "
                f"{forbidden_token}"
            )

    main_activity_path = root / M04_MAIN_ACTIVITY_PATH
    if not main_activity_path.is_file():
        errors.append(
            f"ARCH032 MainActivity host missing: {M04_MAIN_ACTIVITY_PATH}"
        )
        return
    main_imports = Counter(
        imported for _, imported in source_imports(main_activity_path)
    )
    expected_import = (
        f"{M04D_MAIN_DISPLAY_COORDINATOR_PACKAGE}."
        "KiyoriMainDisplayCoordinator"
    )
    if main_imports[expected_import] != 1:
        errors.append(
            "ARCH032 MainActivity must import display coordinator exactly once"
        )
    main_code = source_code_mask(
        main_activity_path.read_text(encoding="utf-8")
    )
    if len(
        re.findall(
            r"\bKiyoriMainDisplayCoordinator\.configure\s*\(\s*this\s*\)",
            main_code,
        )
    ) != 1:
        errors.append(
            "ARCH032 MainActivity must configure display through one coordinator call"
        )
    forbidden_main_tokens = (
        "fun configureDisplaySettings",
        "fun getHighestRefreshRate",
        "fun getDeviceRefreshRate",
        "setSustainedPerformanceMode",
        "preferredDisplayModeId",
        "preferredRefreshRate",
        "FLAG_HARDWARE_ACCELERATED",
    )
    for token in forbidden_main_tokens:
        if token in main_code:
            errors.append(
                "ARCH032 MainActivity still owns display configuration: "
                f"{token}"
            )

    test_path = root / M04D_MAIN_DISPLAY_COORDINATOR_TEST_PATH
    if not test_path.is_file():
        errors.append(
            "ARCH032 display coordinator contract test missing: "
            f"{M04D_MAIN_DISPLAY_COORDINATOR_TEST_PATH}"
        )
    else:
        test_code = source_code_mask(test_path.read_text(encoding="utf-8"))
        for symbol in (
            "selectHighestRefreshRateMode",
            "selectHighestRefreshRate",
        ):
            if symbol not in test_code:
                errors.append(
                    "ARCH032 display coordinator contract test does not use symbol: "
                    f"{symbol}"
                )


def check_m04d_main_shared_content_coordinator(
    root: Path,
    errors: list[str],
) -> None:
    display_path = root / M04D_MAIN_DISPLAY_COORDINATOR_PATH
    if not display_path.is_file():
        return

    coordinator_path = root / M04D_MAIN_SHARED_CONTENT_COORDINATOR_PATH
    if not coordinator_path.is_file():
        errors.append(
            "ARCH033 MainActivity shared-content coordinator missing: "
            f"{M04D_MAIN_SHARED_CONTENT_COORDINATOR_PATH}"
        )
        return

    declared_package = source_package(coordinator_path)
    if declared_package != M04D_MAIN_SHARED_CONTENT_COORDINATOR_PACKAGE:
        errors.append(
            "ARCH033 shared-content coordinator package differs: "
            f"{declared_package or 'missing'}"
        )

    architecture_root = root / "config/architecture"
    hash_entries = read_snapshot(
        architecture_root / M04D_MAIN_SHARED_CONTENT_COORDINATOR_HASH_SNAPSHOT
    )
    if (
        len(hash_entries) != 1
        or not re.fullmatch(r"[0-9A-Fa-f]{64}", hash_entries[0])
    ):
        raise ValueError("invalid M-04D shared-content coordinator hash snapshot")
    normalized = coordinator_path.read_bytes().replace(b"\r\n", b"\n")
    actual_hash = hashlib.sha256(normalized).hexdigest().upper()
    expected_hash = hash_entries[0].upper()
    if actual_hash != expected_hash:
        errors.append(
            "ARCH033 shared-content coordinator source changed: "
            f"expected {expected_hash}, found {actual_hash}"
        )

    expected_import_entries = read_snapshot(
        architecture_root / M04D_MAIN_SHARED_CONTENT_COORDINATOR_IMPORT_SNAPSHOT
    )
    if len(expected_import_entries) != len(set(expected_import_entries)):
        raise ValueError(
            "duplicate M-04D shared-content coordinator import snapshot entry"
        )
    if any(not is_project_import(imported) for imported in expected_import_entries):
        raise ValueError(
            "invalid non-project M-04D shared-content coordinator import snapshot entry"
        )
    expected_project_imports = Counter(expected_import_entries)
    actual_project_imports = Counter(
        imported
        for _, imported in source_imports(coordinator_path)
        if is_project_import(imported)
    )
    if actual_project_imports != expected_project_imports:
        errors.append(
            "ARCH033 shared-content coordinator project imports differ: "
            f"expected {sorted(expected_project_imports.elements())}, "
            f"found {sorted(actual_project_imports.elements())}"
        )

    main_source_root = root / "app/src/main/java"
    source_paths = (
        sorted(path for path in main_source_root.rglob("*.kt") if path.is_file())
        if main_source_root.is_dir()
        else []
    )
    symbol_patterns = {
        "KiyoriPendingSharedFiles": (
            r"\bdata\s+class\s+KiyoriPendingSharedFiles\b"
        ),
        "resolvePendingSharedText": (
            r"\bfun\s+resolvePendingSharedText\s*\("
        ),
        "resolvePendingSharedFiles": (
            r"\bfun\s+resolvePendingSharedFiles\s*\("
        ),
        "KiyoriMainSharedContentCoordinator": (
            r"\bclass\s+KiyoriMainSharedContentCoordinator\b"
        ),
    }
    for symbol, pattern in symbol_patterns.items():
        declaration_sites = [
            path.relative_to(root).as_posix()
            for path in source_paths
            if re.search(
                pattern,
                source_code_mask(path.read_text(encoding="utf-8")),
            )
        ]
        if declaration_sites != [M04D_MAIN_SHARED_CONTENT_COORDINATOR_PATH]:
            errors.append(
                "ARCH033 shared-content coordinator symbol must have one owner: "
                f"{symbol} found {declaration_sites}"
            )

    coordinator_code = source_code_mask(
        coordinator_path.read_text(encoding="utf-8")
    )
    required_call_counts = {
        r"\bSharedFileHandler\.setSharedText\s*\(": (
            "SharedFileHandler.setSharedText",
            1,
        ),
        r"\bSharedFileHandler\.setSharedFiles\s*\(": (
            "SharedFileHandler.setSharedFiles",
            1,
        ),
        r"\bpendingRequests\.clearSharedText\s*\(": (
            "pendingRequests.clearSharedText",
            1,
        ),
        r"\bpendingRequests\.clearSharedFilesAndText\s*\(": (
            "pendingRequests.clearSharedFilesAndText",
            1,
        ),
        r"\bpendingRequests\.clearSharedFiles\s*\(": (
            "pendingRequests.clearSharedFiles",
            1,
        ),
        r"\blifecycleScope\.launch\s*\{": (
            "lifecycleScope.launch",
            1,
        ),
        r"\bToast\.makeText\s*\(": (
            "Toast.makeText",
            1,
        ),
    }
    for pattern, (label, expected_count) in required_call_counts.items():
        actual_count = len(re.findall(pattern, coordinator_code))
        if actual_count != expected_count:
            errors.append(
                "ARCH033 shared-content coordinator transfer count differs: "
                f"{label} expected {expected_count}, found {actual_count}"
            )
    for forbidden_token in (
        "MutableStateFlow",
        "mutableStateOf",
        "recordSharedFiles",
        "recordSharedText",
        "KiyoriMainIntentCommand",
        "PlayerSession",
        "BrowserDownloadManager",
        "GitHubOAuthCoordinator",
        "setContent",
    ):
        if forbidden_token in coordinator_code:
            errors.append(
                "ARCH033 shared-content coordinator absorbed unrelated state or host responsibility: "
                f"{forbidden_token}"
            )

    main_activity_path = root / M04_MAIN_ACTIVITY_PATH
    if not main_activity_path.is_file():
        errors.append(
            f"ARCH033 MainActivity host missing: {M04_MAIN_ACTIVITY_PATH}"
        )
        return
    main_imports = Counter(
        imported for _, imported in source_imports(main_activity_path)
    )
    expected_import = (
        f"{M04D_MAIN_SHARED_CONTENT_COORDINATOR_PACKAGE}."
        "KiyoriMainSharedContentCoordinator"
    )
    if main_imports[expected_import] != 1:
        errors.append(
            "ARCH033 MainActivity must import shared-content coordinator exactly once"
        )
    main_code = source_code_mask(
        main_activity_path.read_text(encoding="utf-8")
    )
    if len(
        re.findall(
            r"\bprivate\s+val\s+sharedContentCoordinator\s+by\s+lazy\b",
            main_code,
        )
    ) != 1:
        errors.append(
            "ARCH033 MainActivity must hold one lifecycle-bound shared-content coordinator"
        )
    main_expected_call_counts = {
        "sharedContentCoordinator.processPendingSharedFiles": 1,
        "sharedContentCoordinator.processPendingSharedText": 1,
    }
    for token, expected_count in main_expected_call_counts.items():
        actual_count = main_code.count(token)
        if actual_count != expected_count:
            errors.append(
                "ARCH033 MainActivity shared-content coordinator call count differs: "
                f"{token} expected {expected_count}, found {actual_count}"
            )
    for forbidden_token in (
        "fun processPendingSharedText",
        "fun processPendingSharedFiles",
        "SharedFileHandler.setSharedText",
        "SharedFileHandler.setSharedFiles",
    ):
        if forbidden_token in main_code:
            errors.append(
                "ARCH033 MainActivity still owns shared-content transfer: "
                f"{forbidden_token}"
            )

    content_host_path = root / M04D_MAIN_CONTENT_HOST_PATH
    if not content_host_path.is_file():
        errors.append(
            "ARCH033 shared-content content host missing: "
            f"{M04D_MAIN_CONTENT_HOST_PATH}"
        )
    else:
        content_host_code = source_code_mask(
            content_host_path.read_text(encoding="utf-8")
        )
        for token, expected_count in main_expected_call_counts.items():
            actual_count = content_host_code.count(token)
            if actual_count != expected_count:
                errors.append(
                    "ARCH033 content host shared-content coordinator call count differs: "
                    f"{token} expected {expected_count}, found {actual_count}"
                )
            total_count = main_code.count(token) + actual_count
            if total_count != 2:
                errors.append(
                    "ARCH033 shared-content coordinator total call count differs: "
                    f"{token} expected 2, found {total_count}"
                )

    test_path = root / M04D_MAIN_SHARED_CONTENT_COORDINATOR_TEST_PATH
    if not test_path.is_file():
        errors.append(
            "ARCH033 shared-content coordinator contract test missing: "
            f"{M04D_MAIN_SHARED_CONTENT_COORDINATOR_TEST_PATH}"
        )
    else:
        test_code = source_code_mask(test_path.read_text(encoding="utf-8"))
        for symbol in (
            "resolvePendingSharedText",
            "resolvePendingSharedFiles",
        ):
            if symbol not in test_code:
                errors.append(
                    "ARCH033 shared-content coordinator contract test does not use symbol: "
                    f"{symbol}"
                )


def check_m04d_main_task_visibility_coordinator(
    root: Path,
    errors: list[str],
) -> None:
    shared_content_path = root / M04D_MAIN_SHARED_CONTENT_COORDINATOR_PATH
    if not shared_content_path.is_file():
        return

    coordinator_path = root / M04D_MAIN_TASK_VISIBILITY_COORDINATOR_PATH
    if not coordinator_path.is_file():
        errors.append(
            "ARCH034 MainActivity task-visibility coordinator missing: "
            f"{M04D_MAIN_TASK_VISIBILITY_COORDINATOR_PATH}"
        )
        return

    declared_package = source_package(coordinator_path)
    if declared_package != M04D_MAIN_TASK_VISIBILITY_COORDINATOR_PACKAGE:
        errors.append(
            "ARCH034 task-visibility coordinator package differs: "
            f"{declared_package or 'missing'}"
        )

    architecture_root = root / "config/architecture"
    hash_entries = read_snapshot(
        architecture_root / M04D_MAIN_TASK_VISIBILITY_COORDINATOR_HASH_SNAPSHOT
    )
    if (
        len(hash_entries) != 1
        or not re.fullmatch(r"[0-9A-Fa-f]{64}", hash_entries[0])
    ):
        raise ValueError("invalid M-04D task-visibility coordinator hash snapshot")
    normalized = coordinator_path.read_bytes().replace(b"\r\n", b"\n")
    actual_hash = hashlib.sha256(normalized).hexdigest().upper()
    expected_hash = hash_entries[0].upper()
    if actual_hash != expected_hash:
        errors.append(
            "ARCH034 task-visibility coordinator source changed: "
            f"expected {expected_hash}, found {actual_hash}"
        )

    expected_import_entries = read_snapshot(
        architecture_root / M04D_MAIN_TASK_VISIBILITY_COORDINATOR_IMPORT_SNAPSHOT
    )
    if len(expected_import_entries) != len(set(expected_import_entries)):
        raise ValueError(
            "duplicate M-04D task-visibility coordinator import snapshot entry"
        )
    if any(not is_project_import(imported) for imported in expected_import_entries):
        raise ValueError(
            "invalid non-project M-04D task-visibility coordinator import snapshot entry"
        )
    expected_project_imports = Counter(expected_import_entries)
    actual_project_imports = Counter(
        imported
        for _, imported in source_imports(coordinator_path)
        if is_project_import(imported)
    )
    if actual_project_imports != expected_project_imports:
        errors.append(
            "ARCH034 task-visibility coordinator project imports differ: "
            f"expected {sorted(expected_project_imports.elements())}, "
            f"found {sorted(actual_project_imports.elements())}"
        )

    main_source_root = root / "app/src/main/java"
    source_paths = (
        sorted(path for path in main_source_root.rglob("*.kt") if path.is_file())
        if main_source_root.is_dir()
        else []
    )
    symbol_patterns = {
        "shouldRestoreRuntimeTaskVisibility": (
            r"\bfun\s+shouldRestoreRuntimeTaskVisibility\s*\("
        ),
        "KiyoriMainTaskVisibilityCoordinator": (
            r"\bobject\s+KiyoriMainTaskVisibilityCoordinator\b"
        ),
    }
    for symbol, pattern in symbol_patterns.items():
        declaration_sites = [
            path.relative_to(root).as_posix()
            for path in source_paths
            if re.search(
                pattern,
                source_code_mask(path.read_text(encoding="utf-8")),
            )
        ]
        if declaration_sites != [M04D_MAIN_TASK_VISIBILITY_COORDINATOR_PATH]:
            errors.append(
                "ARCH034 task-visibility coordinator symbol must have one owner: "
                f"{symbol} found {declaration_sites}"
            )

    coordinator_code = source_code_mask(
        coordinator_path.read_text(encoding="utf-8")
    )
    required_token_counts = {
        "Build.VERSION.SDK_INT": 1,
        "Build.VERSION_CODES.LOLLIPOP": 1,
        "AIForegroundService.isRunning.get()": 1,
        "getSystemService": 1,
        "appTasks": 1,
        "setExcludeFromRecents": 1,
    }
    for token, expected_count in required_token_counts.items():
        actual_count = coordinator_code.count(token)
        if actual_count != expected_count:
            errors.append(
                "ARCH034 task-visibility coordinator API count differs: "
                f"{token} expected {expected_count}, found {actual_count}"
            )
    for forbidden_token in (
        "mutableStateOf",
        "MutableStateFlow",
        "KiyoriMainPendingRequests",
        "KiyoriMainIntentCommand",
        "PlayerSession",
        "BrowserDownloadManager",
        "GitHubOAuthCoordinator",
        "SharedFileHandler",
        "lifecycleScope",
        "setContent",
    ):
        if forbidden_token in coordinator_code:
            errors.append(
                "ARCH034 task-visibility coordinator absorbed unrelated state or host responsibility: "
                f"{forbidden_token}"
            )

    main_activity_path = root / M04_MAIN_ACTIVITY_PATH
    if not main_activity_path.is_file():
        errors.append(
            f"ARCH034 MainActivity host missing: {M04_MAIN_ACTIVITY_PATH}"
        )
        return
    main_imports = Counter(
        imported for _, imported in source_imports(main_activity_path)
    )
    expected_import = (
        f"{M04D_MAIN_TASK_VISIBILITY_COORDINATOR_PACKAGE}."
        "KiyoriMainTaskVisibilityCoordinator"
    )
    if main_imports[expected_import] != 1:
        errors.append(
            "ARCH034 MainActivity must import task-visibility coordinator exactly once"
        )
    main_code = source_code_mask(
        main_activity_path.read_text(encoding="utf-8")
    )
    actual_call_count = len(
        re.findall(
            r"\bKiyoriMainTaskVisibilityCoordinator\.restoreIfNeeded\s*"
            r"\(\s*this(?:@MainActivity)?\s*\)",
            main_code,
        )
    )
    if actual_call_count != 2:
        errors.append(
            "ARCH034 MainActivity task-visibility coordinator call count differs: "
            f"expected 2, found {actual_call_count}"
        )
    for forbidden_token in (
        "fun restoreRuntimeTaskViewVisibilityIfNeeded",
        "AIForegroundService.isRunning",
        "Context.ACTIVITY_SERVICE",
        "setExcludeFromRecents",
        "ActivityManager",
    ):
        if forbidden_token in main_code:
            errors.append(
                "ARCH034 MainActivity still owns runtime task visibility: "
                f"{forbidden_token}"
            )

    test_path = root / M04D_MAIN_TASK_VISIBILITY_COORDINATOR_TEST_PATH
    if not test_path.is_file():
        errors.append(
            "ARCH034 task-visibility coordinator contract test missing: "
            f"{M04D_MAIN_TASK_VISIBILITY_COORDINATOR_TEST_PATH}"
        )
    else:
        test_code = source_code_mask(test_path.read_text(encoding="utf-8"))
        if "shouldRestoreRuntimeTaskVisibility" not in test_code:
            errors.append(
                "ARCH034 task-visibility coordinator contract test does not use symbol: "
                "shouldRestoreRuntimeTaskVisibility"
            )


def check_m04d_main_orientation_coordinator(
    root: Path,
    errors: list[str],
) -> None:
    task_visibility_path = root / M04D_MAIN_TASK_VISIBILITY_COORDINATOR_PATH
    if not task_visibility_path.is_file():
        return

    coordinator_path = root / M04D_MAIN_ORIENTATION_COORDINATOR_PATH
    if not coordinator_path.is_file():
        errors.append(
            "ARCH035 MainActivity orientation coordinator missing: "
            f"{M04D_MAIN_ORIENTATION_COORDINATOR_PATH}"
        )
        return

    declared_package = source_package(coordinator_path)
    if declared_package != M04D_MAIN_ORIENTATION_COORDINATOR_PACKAGE:
        errors.append(
            "ARCH035 orientation coordinator package differs: "
            f"{declared_package or 'missing'}"
        )

    architecture_root = root / "config/architecture"
    hash_entries = read_snapshot(
        architecture_root / M04D_MAIN_ORIENTATION_COORDINATOR_HASH_SNAPSHOT
    )
    if (
        len(hash_entries) != 1
        or not re.fullmatch(r"[0-9A-Fa-f]{64}", hash_entries[0])
    ):
        raise ValueError("invalid M-04D orientation coordinator hash snapshot")
    normalized = coordinator_path.read_bytes().replace(b"\r\n", b"\n")
    actual_hash = hashlib.sha256(normalized).hexdigest().upper()
    expected_hash = hash_entries[0].upper()
    if actual_hash != expected_hash:
        errors.append(
            "ARCH035 orientation coordinator source changed: "
            f"expected {expected_hash}, found {actual_hash}"
        )

    expected_import_entries = read_snapshot(
        architecture_root / M04D_MAIN_ORIENTATION_COORDINATOR_IMPORT_SNAPSHOT
    )
    if len(expected_import_entries) != len(set(expected_import_entries)):
        raise ValueError(
            "duplicate M-04D orientation coordinator import snapshot entry"
        )
    if any(not is_project_import(imported) for imported in expected_import_entries):
        raise ValueError(
            "invalid non-project M-04D orientation coordinator import snapshot entry"
        )
    expected_project_imports = Counter(expected_import_entries)
    actual_project_imports = Counter(
        imported
        for _, imported in source_imports(coordinator_path)
        if is_project_import(imported)
    )
    if actual_project_imports != expected_project_imports:
        errors.append(
            "ARCH035 orientation coordinator project imports differ: "
            f"expected {sorted(expected_project_imports.elements())}, "
            f"found {sorted(actual_project_imports.elements())}"
        )

    main_source_root = root / "app/src/main/java"
    source_paths = (
        sorted(path for path in main_source_root.rglob("*.kt") if path.is_file())
        if main_source_root.is_dir()
        else []
    )
    symbol_patterns = {
        "KiyoriMainOrientationState": (
            r"\bdata\s+class\s+KiyoriMainOrientationState\b"
        ),
        "resolveKiyoriMainOrientationChange": (
            r"\bfun\s+resolveKiyoriMainOrientationChange\s*\("
        ),
        "KiyoriMainOrientationCoordinator": (
            r"\bclass\s+KiyoriMainOrientationCoordinator\b"
        ),
        "KiyoriMainOrientationDialog": (
            r"\bfun\s+KiyoriMainOrientationDialog\s*\("
        ),
    }
    for symbol, pattern in symbol_patterns.items():
        declaration_sites = [
            path.relative_to(root).as_posix()
            for path in source_paths
            if re.search(
                pattern,
                source_code_mask(path.read_text(encoding="utf-8")),
            )
        ]
        if declaration_sites != [M04D_MAIN_ORIENTATION_COORDINATOR_PATH]:
            errors.append(
                "ARCH035 orientation symbol must have one owner: "
                f"{symbol} found {declaration_sites}"
            )

    coordinator_code = source_code_mask(
        coordinator_path.read_text(encoding="utf-8")
    )
    required_token_counts = {
        "KiyoriLogger.d": 1,
        "R.string.dialog_title_orientation_change": 1,
        "R.string.dialog_message_orientation_change": 1,
        "R.string.dialog_button_confirm": 1,
        "R.string.dialog_button_dismiss": 1,
    }
    for token, expected_count in required_token_counts.items():
        actual_count = coordinator_code.count(token)
        if actual_count != expected_count:
            errors.append(
                "ARCH035 orientation coordinator contract count differs: "
                f"{token} expected {expected_count}, found {actual_count}"
            )
    if len(re.findall(r"\bmutableStateOf\s*\(", coordinator_code)) != 1:
        errors.append(
            "ARCH035 orientation coordinator must own exactly one Compose state"
        )
    if len(re.findall(r"\bstringResource\s*\(", coordinator_code)) != 4:
        errors.append(
            "ARCH035 orientation dialog stringResource call count differs"
        )
    if len(re.findall(r"\bAlertDialog\s*\(", coordinator_code)) != 1:
        errors.append(
            "ARCH035 orientation dialog must render exactly one AlertDialog"
        )
    for forbidden_token in (
        "MutableStateFlow",
        "PluginLoadingState",
        "lifecycleScope",
        "KiyoriMainPendingRequests",
        "KiyoriMainIntentCommand",
        "PlayerSession",
        "BrowserDownloadManager",
        "GitHubOAuthCoordinator",
        "SharedFileHandler",
        "setContent",
        "recreate()",
    ):
        if forbidden_token in coordinator_code:
            errors.append(
                "ARCH035 orientation coordinator absorbed unrelated state or host responsibility: "
                f"{forbidden_token}"
            )

    main_activity_path = root / M04_MAIN_ACTIVITY_PATH
    if not main_activity_path.is_file():
        errors.append(
            f"ARCH035 MainActivity host missing: {M04_MAIN_ACTIVITY_PATH}"
        )
        return
    main_imports = Counter(
        imported for _, imported in source_imports(main_activity_path)
    )
    expected_imports = (
        f"{M04D_MAIN_ORIENTATION_COORDINATOR_PACKAGE}."
        "KiyoriMainOrientationCoordinator",
        f"{M04D_MAIN_ORIENTATION_COORDINATOR_PACKAGE}."
        "KiyoriMainOrientationDialog",
    )
    for expected_import in expected_imports:
        if main_imports[expected_import] != 1:
            errors.append(
                "ARCH035 MainActivity must import orientation owner exactly once: "
                f"{expected_import}"
            )
    forbidden_imports = (
        "androidx.compose.material3.AlertDialog",
        "androidx.compose.material3.Text",
        "androidx.compose.material3.TextButton",
        "androidx.compose.runtime.Composable",
        "androidx.compose.ui.res.stringResource",
    )
    for forbidden_import in forbidden_imports:
        if main_imports[forbidden_import] != 0:
            errors.append(
                "ARCH035 MainActivity still imports orientation presentation API: "
                f"{forbidden_import}"
            )

    main_code = source_code_mask(
        main_activity_path.read_text(encoding="utf-8")
    )
    expected_call_counts = {
        "private val orientationCoordinator = KiyoriMainOrientationCoordinator()": 1,
        "orientationCoordinator.initialize(resources.configuration.orientation)": 1,
        "orientationCoordinator.onConfigurationChanged(newConfig.orientation)": 1,
        "orientationCoordinator.showChangeDialog": 1,
        "KiyoriMainOrientationDialog(": 1,
        "orientationCoordinator.dismissChangeDialog()": 2,
        "recreate()": 1,
    }
    for token, expected_count in expected_call_counts.items():
        actual_count = main_code.count(token)
        if actual_count != expected_count:
            errors.append(
                "ARCH035 MainActivity orientation owner call count differs: "
                f"{token} expected {expected_count}, found {actual_count}"
            )

    configuration_match = re.search(
        r"override\s+fun\s+onConfigurationChanged\s*"
        r"\(\s*newConfig\s*:\s*Configuration\s*\)\s*\{"
        r"(?P<body>.*?)"
        r"\n\s*\}",
        main_code,
        flags=re.DOTALL,
    )
    if configuration_match is None:
        errors.append(
            "ARCH035 MainActivity onConfigurationChanged host missing"
        )
    else:
        configuration_body = configuration_match.group("body")
        hide_index = configuration_body.find("pluginLoadingState.hide()")
        orientation_index = configuration_body.find(
            "orientationCoordinator.onConfigurationChanged(newConfig.orientation)"
        )
        if (
            hide_index < 0
            or orientation_index < 0
            or hide_index >= orientation_index
        ):
            errors.append(
                "ARCH035 MainActivity must hide plugin loading before orientation dispatch"
            )

    for forbidden_token in (
        "showOrientationChangeDialog",
        "lastOrientation",
        "fun OrientationChangeDialog",
        "R.string.dialog_title_orientation_change",
        "R.string.dialog_message_orientation_change",
        "R.string.dialog_button_confirm",
        "R.string.dialog_button_dismiss",
    ):
        if forbidden_token in main_code:
            errors.append(
                "ARCH035 MainActivity still owns orientation state or presentation: "
                f"{forbidden_token}"
            )

    test_path = root / M04D_MAIN_ORIENTATION_COORDINATOR_TEST_PATH
    if not test_path.is_file():
        errors.append(
            "ARCH035 orientation coordinator contract test missing: "
            f"{M04D_MAIN_ORIENTATION_COORDINATOR_TEST_PATH}"
        )
    else:
        test_code = source_code_mask(test_path.read_text(encoding="utf-8"))
        for symbol in (
            "resolveKiyoriMainOrientationChange",
            "KiyoriMainOrientationCoordinator",
        ):
            if symbol not in test_code:
                errors.append(
                    "ARCH035 orientation coordinator contract test does not use symbol: "
                    f"{symbol}"
                )


def check_kiyori_first_run_flow(
    root: Path,
    errors: list[str],
) -> None:
    required_paths = (
        KIYORI_FIRST_RUN_CONTRACT_PATH,
        KIYORI_FIRST_RUN_PREFERENCES_PATH,
        KIYORI_FIRST_RUN_PERMISSIONS_PATH,
        KIYORI_FIRST_RUN_SCREEN_PATH,
        KIYORI_FIRST_RUN_AGREEMENT_PATH,
        KIYORI_FIRST_RUN_CONTRACT_TEST_PATH,
        KIYORI_FIRST_RUN_AGREEMENT_TEST_PATH,
        M04D_MAIN_STARTUP_GATE_COORDINATOR_PATH,
        M04D_MAIN_STARTUP_GATE_COORDINATOR_TEST_PATH,
        M04_MAIN_ACTIVITY_PATH,
        KIYORI_ACCESSIBILITY_SUPPORT_APK_PATH,
        KIYORI_ACCESSIBILITY_SUPPORT_VERSION_PATH,
        KIYORI_ACCESSIBILITY_SUPPORT_HASH_PATH,
        KIYORI_LAUNCHER_FOREGROUND_PATH,
        "app/src/main/AndroidManifest.xml",
        "app/src/main/res/values/strings.xml",
    )
    missing_paths = [
        relative_path
        for relative_path in required_paths
        if not (root / relative_path).is_file()
    ]
    for relative_path in missing_paths:
        errors.append(
            "ARCH036 Kiyori first-run contract path missing: "
            f"{relative_path}"
        )
    if missing_paths:
        return

    for legacy_path in KIYORI_FIRST_RUN_LEGACY_PATHS:
        if (root / legacy_path).exists():
            errors.append(
                "ARCH036 legacy startup or onboarding owner remains: "
                f"{legacy_path}"
            )

    support_version = (
        root / KIYORI_ACCESSIBILITY_SUPPORT_VERSION_PATH
    ).read_text(encoding="utf-8").strip()
    if support_version != "1.7":
        errors.append(
            "ARCH045 bundled Kiyori accessibility support version differs: "
            f"expected 1.7, found {support_version or '<empty>'}"
        )

    support_apk_path = root / KIYORI_ACCESSIBILITY_SUPPORT_APK_PATH
    support_apk_bytes = support_apk_path.read_bytes()
    expected_support_hash = (
        root / KIYORI_ACCESSIBILITY_SUPPORT_HASH_PATH
    ).read_text(encoding="utf-8").strip().upper()
    actual_support_hash = hashlib.sha256(support_apk_bytes).hexdigest().upper()
    if actual_support_hash != expected_support_hash:
        errors.append(
            "ARCH045 bundled Kiyori accessibility support hash differs: "
            f"expected {expected_support_hash}, found {actual_support_hash}"
        )

    for required_brand_text in (
        "Kiyori 无障碍支持",
        "Kiyori UI 自动化服务",
        "Theme.KiyoriAccessibilitySupport",
    ):
        if required_brand_text.encode("utf-8") not in support_apk_bytes:
            errors.append(
                "ARCH045 bundled Kiyori accessibility support brand missing: "
                f"{required_brand_text}"
            )
    for forbidden_brand_text in (
        "Accessibility Operit Support",
        "Theme.OperitAccessibilitySupport",
        "AI助手接口服务",
        "此服务为AI助手提供UI自动化能力",
    ):
        if forbidden_brand_text.encode("utf-8") in support_apk_bytes:
            errors.append(
                "ARCH045 legacy accessibility support brand remains: "
                f"{forbidden_brand_text}"
            )
    try:
        with zipfile.ZipFile(support_apk_path) as support_archive:
            support_entries = set(support_archive.namelist())
            expected_icon = (
                root / KIYORI_LAUNCHER_FOREGROUND_PATH
            ).read_bytes()
            for icon_entry in (
                "res/drawable-nodpi/ic_kiyori_brand_foreground.png",
                "res/drawable-nodpi/ic_launcher_foreground.png",
            ):
                if icon_entry not in support_entries:
                    errors.append(
                        "ARCH045 bundled Kiyori accessibility support icon missing: "
                        f"{icon_entry}"
                    )
                elif support_archive.read(icon_entry) != expected_icon:
                    errors.append(
                        "ARCH045 bundled Kiyori accessibility support icon differs "
                        f"from {KIYORI_LAUNCHER_FOREGROUND_PATH}: {icon_entry}"
                    )
            if "res/drawable/ic_launcher_foreground.xml" in support_entries:
                errors.append(
                    "ARCH045 legacy accessibility support launcher foreground XML remains"
                )
    except zipfile.BadZipFile:
        errors.append(
            "ARCH045 bundled Kiyori accessibility support is not a valid APK"
        )

    permissions_code = source_code_mask(
        (root / KIYORI_FIRST_RUN_PERMISSIONS_PATH).read_text(encoding="utf-8")
    )
    contract_code = source_code_mask(
        (root / KIYORI_FIRST_RUN_CONTRACT_PATH).read_text(encoding="utf-8")
    )
    required_runtime_permissions = (
        "Manifest.permission.POST_NOTIFICATIONS",
        "Manifest.permission.READ_MEDIA_AUDIO",
        "Manifest.permission.READ_MEDIA_VIDEO",
        "Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED",
        "Manifest.permission.CAMERA",
        "Manifest.permission.RECORD_AUDIO",
        "Manifest.permission.ACCESS_FINE_LOCATION",
        "Manifest.permission.ACCESS_COARSE_LOCATION",
        "Manifest.permission.BLUETOOTH_CONNECT",
        "Manifest.permission.BLUETOOTH_SCAN",
        "Manifest.permission.CALL_PHONE",
        "Manifest.permission.SEND_SMS",
        "Manifest.permission.READ_SMS",
        "Manifest.permission.RECEIVE_SMS",
        "Manifest.permission.READ_EXTERNAL_STORAGE",
        "Manifest.permission.WRITE_EXTERNAL_STORAGE",
    )
    for token in required_runtime_permissions:
        if token not in permissions_code:
            errors.append(
                "ARCH036 centralized runtime-permission coverage missing: "
                f"{token}"
            )

    required_special_access = (
        "Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION",
        "Settings.ACTION_MANAGE_OVERLAY_PERMISSION",
        "Settings.ACTION_MANAGE_WRITE_SETTINGS",
        "Settings.ACTION_USAGE_ACCESS_SETTINGS",
        "Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES",
        "Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS",
        "Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS",
        "Settings.ACTION_VOICE_INPUT_SETTINGS",
        "Settings.ACTION_ACCESSIBILITY_SETTINGS",
        "UIHierarchyManager.launchProviderInstall",
        "UIHierarchyManager.isUpdateNeeded",
        "ShizukuInstaller.installBundledShizuku",
        "ShizukuAuthorizer.requestShizukuPermission",
    )
    for token in required_special_access:
        if token not in permissions_code:
            errors.append(
                "ARCH045 first-run special-access coverage missing: "
                f"{token}"
            )

    screen_code = source_code_mask(
        (root / KIYORI_FIRST_RUN_SCREEN_PATH).read_text(encoding="utf-8")
    )
    first_run_code = "\n".join((contract_code, permissions_code, screen_code))
    for token in (
        "KiyoriPermissionId.EXACT_ALARM",
        "Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM",
        "canScheduleExactAlarms",
    ):
        if token in first_run_code:
            errors.append(
                "ARCH045 removed first-run exact-alarm contract remains: "
                f"{token}"
            )

    manifest_text = (
        root / "app/src/main/AndroidManifest.xml"
    ).read_text(encoding="utf-8")
    if "android.permission.SCHEDULE_EXACT_ALARM" in manifest_text:
        errors.append(
            "ARCH045 removed first-run exact-alarm manifest permission remains"
        )

    for strings_path in sorted(
        (root / "app/src/main/res").glob("values*/strings.xml")
    ):
        if (
            "kiyori_onboarding_permission_alarm_"
            in strings_path.read_text(encoding="utf-8")
        ):
            errors.append(
                "ARCH045 removed first-run exact-alarm resource remains: "
                f"{strings_path.relative_to(root).as_posix()}"
            )

    required_screen_tokens = (
        "ActivityResultContracts.RequestMultiplePermissions",
        "kiyoriRuntimePermissionsForSdk",
        "KiyoriOnboardingStep.WELCOME",
        "KiyoriOnboardingStep.BROWSER_AND_MEDIA",
        "KiyoriOnboardingStep.AI_ASSISTANT",
        "KiyoriOnboardingStep.FILES_AND_TOOLS",
        "KiyoriOnboardingStep.AGREEMENT",
        "KiyoriOnboardingStep.PERMISSIONS",
        "isKiyoriRuntimePermission",
        "sanitizeKiyoriPermissionSelection",
        "selectedPermissionIds",
        "authorizationActive",
        "KiyoriPermissionId.entries",
        "RootAuthorizer.requestRootPermission",
        "HorizontalPager(",
        "rememberPagerState(",
        "snapshotFlow { pagerState.settledPage }",
        "pagerState.animateScrollToPage",
        "PagerSnapDistance.atMost(1)",
        "userScrollEnabled = pagerInputEnabled",
        "shouldEnableKiyoriOnboardingPagerInput",
        "resolveKiyoriOnboardingSwipeTarget",
        "onboardingPreviousSwipe",
        "resolveKiyoriOnboardingTitleAlignment",
    )
    for token in required_screen_tokens:
        if token not in screen_code:
            errors.append(
                "ARCH036 first-run screen contract missing: "
                f"{token}"
            )
    if "ActivityResultContracts.RequestPermission" in screen_code:
        errors.append(
            "ARCH036 first-run runtime permissions split into a second launcher"
        )
    for obsolete_token in (
        "KiyoriOnboardingStep.VOICE_AUTOMATION",
        "KiyoriOnboardingStep.DEVELOPER_TOOLBOX",
        "KiyoriOnboardingStep.CREATIVE_WORKSPACE",
        "KiyoriOnboardingStep.READY",
        "KiyoriPermissionSection.RUNTIME",
        "KiyoriPermissionSection.SPECIAL_ACCESS",
        "KiyoriPermissionSection.ADVANCED",
        "AutomationLevelSelector",
        "KiyoriReadyPage",
        "onSkip =",
        "private sealed interface KiyoriOnboardingContent",
        "LinearProgressIndicator(",
        "AnimatedContent(",
    ):
        if obsolete_token in screen_code:
            errors.append(
                "ARCH037 obsolete Kiyori onboarding UI contract remains: "
                f"{obsolete_token}"
            )
    for token in (
        "KiyoriLegalDocument.USER_AGREEMENT",
        "KiyoriLegalDocument.PRIVACY_POLICY",
        "maxLines = 1",
    ):
        if token not in screen_code:
            errors.append(
                "ARCH037 first-run legal-navigation contract missing: "
                f"{token}"
            )
    progress_indicator_index = screen_code.find("OnboardingProgressBar(")
    progress_label_index = screen_code.find(
        "R.string.kiyori_onboarding_progress",
        progress_indicator_index + 1,
    )
    if progress_indicator_index < 0 or progress_label_index < 0:
        errors.append(
            "ARCH037 onboarding progress label must follow the compact progress bar"
        )

    agreement_code = source_code_mask(
        (root / KIYORI_FIRST_RUN_AGREEMENT_PATH).read_text(encoding="utf-8")
    )
    for token in (
        "KiyoriLegalDocument.USER_AGREEMENT",
        "KiyoriLegalDocument.PRIVACY_POLICY",
        "canAcceptKiyoriAgreement",
        "KiyoriLegalDocumentsScreen",
    ):
        if token not in agreement_code:
            errors.append(
                "ARCH037 split legal-document UI contract missing: "
                f"{token}"
            )
    if "Icons.Default.Policy" in agreement_code:
        errors.append("ARCH037 decorative agreement title icon remains")
    for obsolete_token in (
        "canAcceptKiyoriLegalDocuments",
        "canScrollVertically(1)",
        "onReadComplete",
        "alreadyRead",
        "kiyori_onboarding_agreement_read_and_return",
    ):
        if obsolete_token in agreement_code:
            errors.append(
                "ARCH037 agreement read-to-end contract remains: "
                f"{obsolete_token}"
            )
    for obsolete_token in (
        "R.string.kiyori_onboarding_agreement_document_content",
        "R.string.kiyori_onboarding_agreement_local_title",
        "R.string.kiyori_onboarding_agreement_network_title",
        "R.string.kiyori_onboarding_agreement_permission_title",
        "R.string.kiyori_onboarding_agreement_open_document",
    ):
        if obsolete_token in agreement_code:
            errors.append(
                "ARCH037 legacy combined agreement UI reference remains: "
                f"{obsolete_token}"
            )

    startup_code = source_code_mask(
        (root / M04D_MAIN_STARTUP_GATE_COORDINATOR_PATH).read_text(
            encoding="utf-8"
        )
    )
    for token in (
        "KiyoriMainStartupDestination.ONBOARDING",
        "KiyoriMainStartupDestination.AGREEMENT",
        "KiyoriMainStartupDestination.CONTENT",
        "KiyoriOnboardingPreferences",
        "KiyoriOnboardingScreen",
        "KiyoriAgreementConfirmationScreen",
        "completeOnboarding",
    ):
        if token not in startup_code:
            errors.append(
                "ARCH037 first-run startup-gate contract missing: "
                f"{token}"
            )
    for forbidden_token in (
        "PermissionGuideScreen",
        "PermissionGuideViewModel",
        "refreshPermissionLevel",
        "completePermissionGuide",
        "AndroidPermissionPreferences",
        "delay(300)",
    ):
        if forbidden_token in startup_code:
            errors.append(
                "ARCH037 legacy startup-gate contract remains: "
                f"{forbidden_token}"
            )

    main_activity_code = source_code_mask(
        (root / M04_MAIN_ACTIVITY_PATH).read_text(encoding="utf-8")
    )
    for token in (
        "KiyoriMainStartupGate(",
        "startupGateCoordinator.acceptCurrentAgreement()",
        "startupGateCoordinator.completeOnboarding()",
        "startPluginLoadingIfReady()",
        "!mainApplicationReady",
        "!startupGateCoordinator.isReadyForContent",
        "pluginLoadingStarted",
    ):
        if token not in main_activity_code:
            errors.append(
                "ARCH037 MainActivity first-run host contract missing: "
                f"{token}"
            )
    for forbidden_token in (
        "KiyoriMainNotificationPermissionCoordinator",
        "notificationPermissionCoordinator",
        "Manifest.permission.POST_NOTIFICATIONS",
        "ActivityResultContracts.RequestPermission",
        "PermissionGuideScreen",
        "AgreementScreen(",
        "refreshPermissionLevel",
        "delay(300)",
    ):
        if forbidden_token in main_activity_code:
            errors.append(
                "ARCH036 MainActivity still owns a first-run implementation detail: "
                f"{forbidden_token}"
            )

    manifest_text = (
        root / "app/src/main/AndroidManifest.xml"
    ).read_text(encoding="utf-8")
    notification_manifest_count = manifest_text.count(
        "android.permission.POST_NOTIFICATIONS"
    )
    if notification_manifest_count != 1:
        errors.append(
            "ARCH045 POST_NOTIFICATIONS Manifest declaration differs: "
            f"expected 1, found {notification_manifest_count}"
        )

    agreement_text = (
        root / "app/src/main/res/values/strings.xml"
    ).read_text(encoding="utf-8")
    for token in (
        "kiyori_onboarding_user_agreement_content",
        "kiyori_onboarding_privacy_policy_content",
        "kiyori_onboarding_welcome_title",
        "kiyori_onboarding_browser_title",
        "kiyori_onboarding_ai_title",
        "kiyori_onboarding_files_title",
        "以网页为入口的 AI 浏览器",
        "从发现到播放，内容自然流动",
        "让 AI 读懂上下文，也能继续行动",
        "文件、终端与扩展能力，一处展开",
        "网页浏览器",
        "文件下载器",
        "视频播放器",
        "搜索、标签、网页操作与广告拦截",
        "音乐播放与沉浸式小说阅读",
        "AI 助手",
        "语音服务",
        "账号与连接",
        "工具箱",
        "文件管理器",
        "终端",
        "小程序管理",
        "日志记录器",
        "Kiyori 无障碍支持",
        "Kiyori UI 自动化服务",
        "GPL-3.0-or-later",
        "Operit AI 是内嵌的 AI 子系统",
        "kiyori_onboarding_permissions_authorize_and_enter",
        "Android 运行时权限",
        "Shizuku",
        "Root",
    ):
        if token not in agreement_text:
            errors.append(
                "ARCH037 Kiyori agreement fact contract missing: "
                f"{token}"
            )
    if "AI 浏览器 · 内容工作台" in agreement_text:
        errors.append(
            "ARCH037 obsolete welcome eyebrow still present: AI 浏览器 · 内容工作台"
        )
    contract_test_code = source_code_mask(
        (root / KIYORI_FIRST_RUN_CONTRACT_TEST_PATH).read_text(
            encoding="utf-8"
        )
    )
    for token in (
        "KiyoriOnboardingStep",
        "resolveInitialKiyoriOnboardingStep",
        "KiyoriPermissionSnapshot",
        "sanitizeKiyoriPermissionSelection",
        "KiyoriPermissionStatus.ON_DEMAND",
        "KiyoriPermissionStatus.NOT_APPLICABLE",
        "KiyoriOnboardingSwipeDirection",
        "resolveKiyoriOnboardingSwipeTarget",
        "shouldEnableKiyoriOnboardingPagerInput",
        "resolveKiyoriOnboardingTitleAlignment",
    ):
        if token not in contract_test_code:
            errors.append(
                "ARCH045 first-run contract test assertion missing: "
                f"{token}"
            )
    agreement_test_code = source_code_mask(
        (root / KIYORI_FIRST_RUN_AGREEMENT_TEST_PATH).read_text(
            encoding="utf-8"
        )
    )
    for token in (
        "canAcceptKiyoriAgreement",
        "checked = true",
        "assertTrue",
        "assertFalse",
    ):
        if token not in agreement_test_code:
            errors.append(
                "ARCH037 Kiyori legal readiness test missing: "
                f"{token}"
            )


def check_m04d_main_notification_permission_coordinator(
    root: Path,
    errors: list[str],
) -> None:
    orientation_path = root / M04D_MAIN_ORIENTATION_COORDINATOR_PATH
    if not orientation_path.is_file():
        return

    coordinator_path = root / M04D_MAIN_NOTIFICATION_PERMISSION_COORDINATOR_PATH
    if not coordinator_path.is_file():
        errors.append(
            "ARCH036 MainActivity notification-permission coordinator missing: "
            f"{M04D_MAIN_NOTIFICATION_PERMISSION_COORDINATOR_PATH}"
        )
        return

    declared_package = source_package(coordinator_path)
    if declared_package != M04D_MAIN_NOTIFICATION_PERMISSION_COORDINATOR_PACKAGE:
        errors.append(
            "ARCH036 notification-permission coordinator package differs: "
            f"{declared_package or 'missing'}"
        )

    architecture_root = root / "config/architecture"
    hash_entries = read_snapshot(
        architecture_root
        / M04D_MAIN_NOTIFICATION_PERMISSION_COORDINATOR_HASH_SNAPSHOT
    )
    if (
        len(hash_entries) != 1
        or not re.fullmatch(r"[0-9A-Fa-f]{64}", hash_entries[0])
    ):
        raise ValueError(
            "invalid M-04D notification-permission coordinator hash snapshot"
        )
    normalized = coordinator_path.read_bytes().replace(b"\r\n", b"\n")
    actual_hash = hashlib.sha256(normalized).hexdigest().upper()
    expected_hash = hash_entries[0].upper()
    if actual_hash != expected_hash:
        errors.append(
            "ARCH036 notification-permission coordinator source changed: "
            f"expected {expected_hash}, found {actual_hash}"
        )

    expected_import_entries = read_snapshot(
        architecture_root
        / M04D_MAIN_NOTIFICATION_PERMISSION_COORDINATOR_IMPORT_SNAPSHOT
    )
    if len(expected_import_entries) != len(set(expected_import_entries)):
        raise ValueError(
            "duplicate M-04D notification-permission import snapshot entry"
        )
    if any(not is_project_import(imported) for imported in expected_import_entries):
        raise ValueError(
            "invalid non-project M-04D notification-permission import snapshot entry"
        )
    expected_project_imports = Counter(expected_import_entries)
    actual_project_imports = Counter(
        imported
        for _, imported in source_imports(coordinator_path)
        if is_project_import(imported)
    )
    if actual_project_imports != expected_project_imports:
        errors.append(
            "ARCH036 notification-permission coordinator project imports differ: "
            f"expected {sorted(expected_project_imports.elements())}, "
            f"found {sorted(actual_project_imports.elements())}"
        )

    main_source_root = root / "app/src/main/java"
    source_paths = (
        sorted(path for path in main_source_root.rglob("*.kt") if path.is_file())
        if main_source_root.is_dir()
        else []
    )
    symbol_patterns = {
        "KiyoriMainNotificationPermissionCoordinator": (
            r"\bclass\s+KiyoriMainNotificationPermissionCoordinator\b"
        ),
    }
    for symbol, pattern in symbol_patterns.items():
        declaration_sites = [
            path.relative_to(root).as_posix()
            for path in source_paths
            if re.search(
                pattern,
                source_code_mask(path.read_text(encoding="utf-8")),
            )
        ]
        if declaration_sites != [M04D_MAIN_NOTIFICATION_PERMISSION_COORDINATOR_PATH]:
            errors.append(
                "ARCH036 notification-permission symbol must have one owner: "
                f"{symbol} found {declaration_sites}"
            )

    coordinator_code = source_code_mask(
        coordinator_path.read_text(encoding="utf-8")
    )
    required_call_counts = {
        r"\bKiyoriNotificationPermissionCapability\s*\(": (
            "KiyoriNotificationPermissionCapability",
            1,
        ),
        r"\bnotificationPermissionCapability\.checkAndRequest\s*\{": (
            "notificationPermissionCapability.checkAndRequest",
            1,
        ),
        r"\bToast\.makeText\s*\(": (
            "Toast.makeText",
            2,
        ),
    }
    for pattern, (label, expected_count) in required_call_counts.items():
        actual_count = len(re.findall(pattern, coordinator_code))
        if actual_count != expected_count:
            errors.append(
                "ARCH036 notification-permission API count differs: "
                f"{label} expected {expected_count}, found {actual_count}"
            )
    required_token_counts = {
        "KiyoriLogger.d": 6,
        (
            "OperitNotificationPermissionResources."
            "notificationPermissionDenied"
        ): 1,
        (
            "OperitNotificationPermissionResources."
            "notificationPermissionRationale"
        ): 1,
    }
    for token, expected_count in required_token_counts.items():
        actual_count = coordinator_code.count(token)
        if actual_count != expected_count:
            errors.append(
                "ARCH036 notification-permission contract count differs: "
                f"{token} expected {expected_count}, found {actual_count}"
            )
    for forbidden_token in (
        "mutableStateOf",
        "MutableStateFlow",
        "PluginLoadingState",
        "lifecycleScope",
        "KiyoriMainPendingRequests",
        "KiyoriMainIntentCommand",
        "PlayerSession",
        "BrowserDownloadManager",
        "GitHubOAuthCoordinator",
        "SharedFileHandler",
        "setContent",
        "com.ai.assistance.operit.R",
        "R.string",
        "Manifest.permission.POST_NOTIFICATIONS",
        "PackageManager.PERMISSION_GRANTED",
        "Build.VERSION",
        "ActivityResultContracts.RequestPermission",
        "ContextCompat.checkSelfPermission",
        "shouldShowRequestPermissionRationale",
        "permissionLauncher",
        "KiyoriMainNotificationPermissionAction",
        "resolveKiyoriMainNotificationPermissionAction",
    ):
        if forbidden_token in coordinator_code:
            errors.append(
                "ARCH036 notification-permission coordinator absorbed unrelated state or host responsibility: "
                f"{forbidden_token}"
            )

    main_activity_path = root / M04_MAIN_ACTIVITY_PATH
    if not main_activity_path.is_file():
        errors.append(
            f"ARCH036 MainActivity host missing: {M04_MAIN_ACTIVITY_PATH}"
        )
        return
    main_imports = Counter(
        imported for _, imported in source_imports(main_activity_path)
    )
    expected_import = (
        f"{M04D_MAIN_NOTIFICATION_PERMISSION_COORDINATOR_PACKAGE}."
        "KiyoriMainNotificationPermissionCoordinator"
    )
    if main_imports[expected_import] != 1:
        errors.append(
            "ARCH036 MainActivity must import notification-permission coordinator exactly once"
        )
    forbidden_imports = (
        "android.Manifest",
        "android.content.pm.PackageManager",
        "androidx.activity.result.contract.ActivityResultContracts",
        "androidx.core.content.ContextCompat",
    )
    for forbidden_import in forbidden_imports:
        if main_imports[forbidden_import] != 0:
            errors.append(
                "ARCH036 MainActivity still imports notification-permission API: "
                f"{forbidden_import}"
            )

    main_code = source_code_mask(
        main_activity_path.read_text(encoding="utf-8")
    )
    field_count = len(
        re.findall(
            r"\bprivate\s+val\s+notificationPermissionCoordinator\s*=\s*"
            r"KiyoriMainNotificationPermissionCoordinator\s*\(\s*this\s*\)",
            main_code,
        )
    )
    if field_count != 1:
        errors.append(
            "ARCH036 MainActivity must hold one early-registered "
            "notification-permission coordinator"
        )
    check_call_count = main_code.count(
        "notificationPermissionCoordinator.checkAndRequest()"
    )
    if check_call_count != 1:
        errors.append(
            "ARCH036 MainActivity notification-permission call count differs: "
            f"expected 1, found {check_call_count}"
        )
    for forbidden_token in (
        "notificationPermissionLauncher",
        "fun checkNotificationPermission",
        "Manifest.permission.POST_NOTIFICATIONS",
        "PackageManager.PERMISSION_GRANTED",
        "shouldShowRequestPermissionRationale",
        "R.string.notification_permission_denied",
        "R.string.notification_permission_rationale",
    ):
        if forbidden_token in main_code:
            errors.append(
                "ARCH036 MainActivity still owns notification-permission request: "
                f"{forbidden_token}"
            )

    test_path = root / M05D_PERMISSION_TEST_PATH
    if not test_path.is_file():
        errors.append(
            "ARCH036 notification-permission platform contract test missing: "
            f"{M05D_PERMISSION_TEST_PATH}"
        )
    else:
        test_code = source_code_mask(test_path.read_text(encoding="utf-8"))
        for symbol in (
            "KiyoriNotificationPermissionAction",
            "resolveKiyoriNotificationPermissionAction",
        ):
            if symbol not in test_code:
                errors.append(
                    "ARCH036 notification-permission platform contract "
                    "test does not use symbol: "
                    f"{symbol}"
                )


def check_m04d_main_startup_gate_coordinator(
    root: Path,
    errors: list[str],
) -> None:
    notification_path = root / M04D_MAIN_NOTIFICATION_PERMISSION_COORDINATOR_PATH
    if not notification_path.is_file():
        return

    coordinator_path = root / M04D_MAIN_STARTUP_GATE_COORDINATOR_PATH
    if not coordinator_path.is_file():
        errors.append(
            "ARCH037 MainActivity startup-gate coordinator missing: "
            f"{M04D_MAIN_STARTUP_GATE_COORDINATOR_PATH}"
        )
        return

    declared_package = source_package(coordinator_path)
    if declared_package != M04D_MAIN_STARTUP_GATE_COORDINATOR_PACKAGE:
        errors.append(
            "ARCH037 startup-gate coordinator package differs: "
            f"{declared_package or 'missing'}"
        )

    architecture_root = root / "config/architecture"
    hash_entries = read_snapshot(
        architecture_root / M04D_MAIN_STARTUP_GATE_COORDINATOR_HASH_SNAPSHOT
    )
    if (
        len(hash_entries) != 1
        or not re.fullmatch(r"[0-9A-Fa-f]{64}", hash_entries[0])
    ):
        raise ValueError("invalid M-04D startup-gate coordinator hash snapshot")
    normalized = coordinator_path.read_bytes().replace(b"\r\n", b"\n")
    actual_hash = hashlib.sha256(normalized).hexdigest().upper()
    expected_hash = hash_entries[0].upper()
    if actual_hash != expected_hash:
        errors.append(
            "ARCH037 startup-gate coordinator source changed: "
            f"expected {expected_hash}, found {actual_hash}"
        )

    expected_import_entries = read_snapshot(
        architecture_root / M04D_MAIN_STARTUP_GATE_COORDINATOR_IMPORT_SNAPSHOT
    )
    if len(expected_import_entries) != len(set(expected_import_entries)):
        raise ValueError("duplicate M-04D startup-gate import snapshot entry")
    if any(not is_project_import(imported) for imported in expected_import_entries):
        raise ValueError(
            "invalid non-project M-04D startup-gate import snapshot entry"
        )
    expected_project_imports = Counter(expected_import_entries)
    actual_project_imports = Counter(
        imported
        for _, imported in source_imports(coordinator_path)
        if is_project_import(imported)
    )
    if actual_project_imports != expected_project_imports:
        errors.append(
            "ARCH037 startup-gate coordinator project imports differ: "
            f"expected {sorted(expected_project_imports.elements())}, "
            f"found {sorted(actual_project_imports.elements())}"
        )

    main_source_root = root / "app/src/main/java"
    source_paths = (
        sorted(path for path in main_source_root.rglob("*.kt") if path.is_file())
        if main_source_root.is_dir()
        else []
    )
    symbol_patterns = {
        "KiyoriMainStartupDestination": (
            r"\benum\s+class\s+KiyoriMainStartupDestination\b"
        ),
        "resolveKiyoriMainStartupDestination": (
            r"\bfun\s+resolveKiyoriMainStartupDestination\s*\("
        ),
        "KiyoriMainStartupGateCoordinator": (
            r"\bclass\s+KiyoriMainStartupGateCoordinator\b"
        ),
        "KiyoriMainStartupGate": (
            r"\bfun\s+KiyoriMainStartupGate\s*\("
        ),
    }
    for symbol, pattern in symbol_patterns.items():
        declaration_sites = [
            path.relative_to(root).as_posix()
            for path in source_paths
            if re.search(
                pattern,
                source_code_mask(path.read_text(encoding="utf-8")),
            )
        ]
        if declaration_sites != [M04D_MAIN_STARTUP_GATE_COORDINATOR_PATH]:
            errors.append(
                "ARCH037 startup-gate symbol must have one owner: "
                f"{symbol} found {declaration_sites}"
            )

    coordinator_text = coordinator_path.read_text(encoding="utf-8")
    coordinator_code = source_code_mask(coordinator_text)
    required_call_counts = {
        r"\bAgreementPreferences\s*\(\s*context\s*\)": (
            "AgreementPreferences(context)",
            1,
        ),
        r"\bagreementPreferences::isAgreementAccepted\b": (
            "AgreementPreferences.isAgreementAccepted reference",
            1,
        ),
        r"\bagreementPreferences::acceptCurrentAgreement\b": (
            "AgreementPreferences.acceptCurrentAgreement reference",
            1,
        ),
        r"\bandroidPermissionPreferences::getPreferredPermissionLevel\b": (
            "AndroidPermissionPreferences.getPreferredPermissionLevel reference",
            1,
        ),
        r"\bmutableStateOf\s*\(\s*false\s*\)": (
            "mutableStateOf(false)",
            1,
        ),
        r"\bAgreementScreen\s*\(": (
            "AgreementScreen",
            1,
        ),
        r"\bPermissionGuideScreen\s*\(": (
            "PermissionGuideScreen",
            1,
        ),
        r"\bfun\s+refreshPermissionLevel\s*\(": (
            "refreshPermissionLevel",
            1,
        ),
        r"\bfun\s+acceptCurrentAgreement\s*\(": (
            "acceptCurrentAgreement",
            1,
        ),
        r"\bfun\s+completePermissionGuide\s*\(": (
            "completePermissionGuide",
            1,
        ),
    }
    for pattern, (label, expected_count) in required_call_counts.items():
        actual_count = len(re.findall(pattern, coordinator_code))
        if actual_count != expected_count:
            errors.append(
                "ARCH037 startup-gate contract count differs: "
                f"{label} expected {expected_count}, found {actual_count}"
            )
    required_token_counts = {"KiyoriLogger.d": 1}
    for token, expected_count in required_token_counts.items():
        actual_count = coordinator_code.count(token)
        if actual_count != expected_count:
            errors.append(
                "ARCH037 startup-gate logging contract differs: "
                f"{token} expected {expected_count}, found {actual_count}"
            )
    required_message_counts = {
        "当前权限级别:": 1,
        "权限级别检查: 已设置=": 1,
    }
    for token, expected_count in required_message_counts.items():
        actual_count = coordinator_text.count(token)
        if actual_count != expected_count:
            errors.append(
                "ARCH037 startup-gate message contract differs: "
                f"{token} expected {expected_count}, found {actual_count}"
            )
    for forbidden_token in (
        "MutableStateFlow",
        "SharedPreferences",
        "DataStore",
        "agreementAcceptedFlow",
        "preferredPermissionLevelFlow",
        "savePreferredPermissionLevel",
        "PluginLoadingState",
        "startPluginLoading",
        "lifecycleScope",
        "delay(",
        "setContent",
        "setAppContent",
        "KiyoriApp",
        "KiyoriMainPendingRequests",
        "KiyoriMainIntentCommand",
        "PlayerSession",
        "BrowserDownloadManager",
        "GitHubOAuthCoordinator",
        "SharedFileHandler",
    ):
        if forbidden_token in coordinator_code:
            errors.append(
                "ARCH037 startup-gate coordinator absorbed persistent state or host responsibility: "
                f"{forbidden_token}"
            )

    main_activity_path = root / M04_MAIN_ACTIVITY_PATH
    if not main_activity_path.is_file():
        errors.append(f"ARCH037 MainActivity host missing: {M04_MAIN_ACTIVITY_PATH}")
        return
    main_imports = Counter(
        imported for _, imported in source_imports(main_activity_path)
    )
    expected_imports = (
        f"{M04D_MAIN_STARTUP_GATE_COORDINATOR_PACKAGE}."
        "KiyoriMainStartupGate",
        f"{M04D_MAIN_STARTUP_GATE_COORDINATOR_PACKAGE}."
        "KiyoriMainStartupGateCoordinator",
    )
    for expected_import in expected_imports:
        if main_imports[expected_import] != 1:
            errors.append(
                "ARCH037 MainActivity must import startup-gate symbol exactly once: "
                f"{expected_import}"
            )
    forbidden_imports = (
        "com.ai.assistance.operit.data.preferences.AgreementPreferences",
        "com.ai.assistance.operit.data.preferences.androidPermissionPreferences",
        "com.ai.assistance.operit.ui.features.agreement.screens.AgreementScreen",
        "com.ai.assistance.operit.ui.features.permission.screens.PermissionGuideScreen",
    )
    for forbidden_import in forbidden_imports:
        if main_imports[forbidden_import] != 0:
            errors.append(
                "ARCH037 MainActivity still imports startup-gate implementation detail: "
                f"{forbidden_import}"
            )

    main_code = source_code_mask(
        main_activity_path.read_text(encoding="utf-8")
    )
    if len(
        re.findall(
            r"\bprivate\s+lateinit\s+var\s+startupGateCoordinator\s*:\s*"
            r"KiyoriMainStartupGateCoordinator\b",
            main_code,
        )
    ) != 1:
        errors.append(
            "ARCH037 MainActivity must hold exactly one startup-gate coordinator"
        )
    initialization_count = len(
        re.findall(
            r"\bstartupGateCoordinator\s*=\s*"
            r"KiyoriMainStartupGateCoordinator\s*\(\s*this\s*\)",
            main_code,
        )
    )
    if initialization_count != 1:
        errors.append(
            "ARCH037 MainActivity startup-gate initialization count differs: "
            f"expected 1, found {initialization_count}"
        )
    required_host_counts = {
        "startupGateCoordinator.refreshPermissionLevel()": 2,
        "startupGateCoordinator.isReadyForContent": 2,
        "startupGateCoordinator.acceptCurrentAgreement()": 1,
        "startupGateCoordinator.completePermissionGuide()": 1,
        "KiyoriMainStartupGate(": 1,
        "destination = startupGateCoordinator.destination": 1,
        "delay(300)": 1,
    }
    for token, expected_count in required_host_counts.items():
        actual_count = main_code.count(token)
        if actual_count != expected_count:
            errors.append(
                "ARCH037 MainActivity startup-gate call count differs: "
                f"{token} expected {expected_count}, found {actual_count}"
            )
    for forbidden_token in (
        "agreementPreferences",
        "showPermissionGuide",
        "fun checkPermissionLevelSet",
        "AgreementScreen(",
        "PermissionGuideScreen(",
        "androidPermissionPreferences",
    ):
        if forbidden_token in main_code:
            errors.append(
                "ARCH037 MainActivity still owns startup-gate state or presentation: "
                f"{forbidden_token}"
            )

    initial_checks_pattern = re.compile(
        r"notificationPermissionCoordinator\.checkAndRequest\s*\(\s*\)"
        r".*startupGateCoordinator\.refreshPermissionLevel\s*\(\s*\)"
        r".*prepareStartupChatIfNeeded\s*\(\s*\)"
        r".*if\s*\(\s*startupGateCoordinator\.isReadyForContent\s*\)"
        r".*startPluginLoading\s*\(\s*\)",
        re.DOTALL,
    )
    if not initial_checks_pattern.search(main_code):
        errors.append(
            "ARCH037 MainActivity initial-check ordering changed"
        )
    agreement_callback_pattern = re.compile(
        r"startupGateCoordinator\.acceptCurrentAgreement\s*\(\s*\)"
        r".*delay\s*\(\s*300\s*\)"
        r".*startupGateCoordinator\.refreshPermissionLevel\s*\(\s*\)"
        r".*if\s*\(\s*startupGateCoordinator\.isReadyForContent\s*\)"
        r".*startPluginLoading\s*\(\s*\)"
        r".*setAppContent\s*\(\s*\)",
        re.DOTALL,
    )
    if not agreement_callback_pattern.search(main_code):
        errors.append(
            "ARCH037 MainActivity agreement callback ordering changed"
        )
    permission_callback_pattern = re.compile(
        r"startupGateCoordinator\.completePermissionGuide\s*\(\s*\)"
        r".*startPluginLoading\s*\(\s*\)"
        r".*setAppContent\s*\(\s*\)",
        re.DOTALL,
    )
    if not permission_callback_pattern.search(main_code):
        errors.append(
            "ARCH037 MainActivity permission-guide callback ordering changed"
        )

    test_path = root / M04D_MAIN_STARTUP_GATE_COORDINATOR_TEST_PATH
    if not test_path.is_file():
        errors.append(
            "ARCH037 startup-gate coordinator contract test missing: "
            f"{M04D_MAIN_STARTUP_GATE_COORDINATOR_TEST_PATH}"
        )
    else:
        test_code = source_code_mask(test_path.read_text(encoding="utf-8"))
        for symbol in (
            "KiyoriMainStartupDestination",
            "resolveKiyoriMainStartupDestination",
            "KiyoriMainStartupGateCoordinator",
        ):
            if symbol not in test_code:
                errors.append(
                    "ARCH037 startup-gate contract test does not use symbol: "
                    f"{symbol}"
                )


def check_m04d_main_content_host(
    root: Path,
    errors: list[str],
) -> None:
    startup_gate_path = root / M04D_MAIN_STARTUP_GATE_COORDINATOR_PATH
    if not startup_gate_path.is_file():
        return

    host_path = root / M04D_MAIN_CONTENT_HOST_PATH
    if not host_path.is_file():
        errors.append(
            "ARCH038 MainActivity content host missing: "
            f"{M04D_MAIN_CONTENT_HOST_PATH}"
        )
        return

    declared_package = source_package(host_path)
    if declared_package != M04D_MAIN_CONTENT_HOST_PACKAGE:
        errors.append(
            "ARCH038 content host package differs: "
            f"{declared_package or 'missing'}"
        )

    architecture_root = root / "config/architecture"
    hash_entries = read_snapshot(
        architecture_root / M04D_MAIN_CONTENT_HOST_HASH_SNAPSHOT
    )
    if (
        len(hash_entries) != 1
        or not re.fullmatch(r"[0-9A-Fa-f]{64}", hash_entries[0])
    ):
        raise ValueError("invalid M-04D content host hash snapshot")
    normalized = host_path.read_bytes().replace(b"\r\n", b"\n")
    actual_hash = hashlib.sha256(normalized).hexdigest().upper()
    expected_hash = hash_entries[0].upper()
    if actual_hash != expected_hash:
        errors.append(
            "ARCH038 content host source changed: "
            f"expected {expected_hash}, found {actual_hash}"
        )

    expected_import_entries = read_snapshot(
        architecture_root / M04D_MAIN_CONTENT_HOST_IMPORT_SNAPSHOT
    )
    if len(expected_import_entries) != len(set(expected_import_entries)):
        raise ValueError("duplicate M-04D content host import snapshot entry")
    if any(not is_project_import(imported) for imported in expected_import_entries):
        raise ValueError(
            "invalid non-project M-04D content host import snapshot entry"
        )
    expected_project_imports = Counter(expected_import_entries)
    actual_project_imports = Counter(
        imported
        for _, imported in source_imports(host_path)
        if is_project_import(imported)
    )
    if actual_project_imports != expected_project_imports:
        errors.append(
            "ARCH038 content host project imports differ: "
            f"expected {sorted(expected_project_imports.elements())}, "
            f"found {sorted(actual_project_imports.elements())}"
        )

    main_source_root = root / "app/src/main/java"
    source_paths = (
        sorted(path for path in main_source_root.rglob("*.kt") if path.is_file())
        if main_source_root.is_dir()
        else []
    )
    symbol_patterns = {
        "KiyoriMainContentRequestProjection": (
            r"\bdata\s+class\s+KiyoriMainContentRequestProjection\b"
        ),
        "projectKiyoriMainContentRequests": (
            r"\bfun\s+projectKiyoriMainContentRequests\s*\("
        ),
        "KiyoriMainContentHost": (
            r"\bfun\s+KiyoriMainContentHost\s*\("
        ),
    }
    for symbol, pattern in symbol_patterns.items():
        declaration_sites = [
            path.relative_to(root).as_posix()
            for path in source_paths
            if re.search(
                pattern,
                source_code_mask(path.read_text(encoding="utf-8")),
            )
        ]
        if declaration_sites != [M04D_MAIN_CONTENT_HOST_PATH]:
            errors.append(
                "ARCH038 content host symbol must have one owner: "
                f"{symbol} found {declaration_sites}"
            )

    host_code = source_code_mask(host_path.read_text(encoding="utf-8"))
    required_token_counts = {
        "sharedContentCoordinator.processPendingSharedFiles()": 1,
        "sharedContentCoordinator.processPendingSharedText()": 1,
        "projectKiyoriMainContentRequests(pendingRequests)": 1,
        "CompositionLocalProvider(": 1,
        "LocalPluginLoadingState provides pluginLoadingState": 1,
        "KiyoriApp(": 1,
        "pendingRequests.consumeShortcut(handledRequestId)": 1,
        "pendingRequests.updateCurrentMainNavItem(navItem)": 1,
        "pendingRequests.consumeRoute(handledRequestId)": 1,
        "pendingRequests.consumeBrowser(handledRequestId)": 1,
        "pendingRequests.consumeShell(handledRequestId)": 1,
    }
    for token, expected_count in required_token_counts.items():
        actual_count = host_code.count(token)
        if actual_count != expected_count:
            errors.append(
                "ARCH038 content host contract count differs: "
                f"{token} expected {expected_count}, found {actual_count}"
            )

    required_projection_counts = {
        (
            "initialNavItem = pendingRequests.shortcutNavItem "
            "?: pendingRequests.currentMainNavItem"
        ): 1,
        "shortcutNavRequest = pendingRequests.shortcutNavItem": 1,
        "shortcutNavRequestId = pendingRequests.shortcutRequestId": 1,
        "routeNavRequest = pendingRequests.routeId": 1,
        "routeNavArgs = pendingRequests.routeArgs": 1,
        "routeNavRequestId = pendingRequests.routeRequestId": 1,
        "browserOpenRequest = pendingRequests.browserUrl": 1,
        "browserOpenRequestId = pendingRequests.browserRequestId": 1,
        (
            "kiyoriShellDestinationRequest = "
            "pendingRequests.shellDestination"
        ): 1,
        "kiyoriShellRequestId = pendingRequests.shellRequestId": 1,
    }
    for token, expected_count in required_projection_counts.items():
        actual_count = host_code.count(token)
        if actual_count != expected_count:
            errors.append(
                "ARCH038 content request projection differs: "
                f"{token} expected {expected_count}, found {actual_count}"
            )

    forbidden_tokens = (
        "mutableStateOf",
        "MutableStateFlow",
        "SharedPreferences",
        "DataStore",
        "preferencesDataStore",
        "PluginLoadingStateRegistry",
        "PluginLoadingScreenWithState",
        "startTimeoutCheck",
        "initializeMCPServer",
        "setContent",
        "OperitTheme",
        "KiyoriMainStartupGate",
        "startPluginLoading",
        "lifecycleScope",
        "delay(",
        "KiyoriMainOrientationDialog",
        "recreate(",
        "GitHubOAuthCoordinator",
        "PlayerSession",
        "BrowserDownloadManager",
        "AgreementPreferences",
        "androidPermissionPreferences",
    )
    for forbidden_token in forbidden_tokens:
        if forbidden_token in host_code:
            errors.append(
                "ARCH038 content host absorbed state or Activity/runtime responsibility: "
                f"{forbidden_token}"
            )
    if re.search(r"\bremember\s*\(", host_code):
        errors.append(
            "ARCH038 content host absorbed state or Activity/runtime responsibility: "
            "remember"
        )

    order_pattern = re.compile(
        r"sharedContentCoordinator\.processPendingSharedFiles\s*\(\s*\)"
        r".*sharedContentCoordinator\.processPendingSharedText\s*\(\s*\)"
        r".*projectKiyoriMainContentRequests\s*\(\s*pendingRequests\s*\)"
        r".*CompositionLocalProvider\s*\("
        r".*LocalPluginLoadingState\s+provides\s+pluginLoadingState"
        r".*KiyoriApp\s*\(",
        re.DOTALL,
    )
    if not order_pattern.search(host_code):
        errors.append("ARCH038 content host execution ordering changed")

    main_activity_path = root / M04_MAIN_ACTIVITY_PATH
    if not main_activity_path.is_file():
        errors.append(f"ARCH038 MainActivity host missing: {M04_MAIN_ACTIVITY_PATH}")
        return
    main_imports = Counter(
        imported for _, imported in source_imports(main_activity_path)
    )
    expected_import = (
        f"{M04D_MAIN_CONTENT_HOST_PACKAGE}.KiyoriMainContentHost"
    )
    if main_imports[expected_import] != 1:
        errors.append(
            "ARCH038 MainActivity must import content host exactly once: "
            f"{expected_import}"
        )
    forbidden_imports = (
        "androidx.compose.runtime.CompositionLocalProvider",
        "com.ai.assistance.operit.ui.features.startup.screens.LocalPluginLoadingState",
        "com.kiyori.app.KiyoriApp",
    )
    for forbidden_import in forbidden_imports:
        if main_imports[forbidden_import] != 0:
            errors.append(
                "ARCH038 MainActivity still imports direct content implementation: "
                f"{forbidden_import}"
            )

    main_code = source_code_mask(
        main_activity_path.read_text(encoding="utf-8")
    )
    required_host_counts = {
        "KiyoriMainContentHost(": 1,
        "sharedContentCoordinator = sharedContentCoordinator": 1,
        "pluginLoadingState = pluginLoadingState": 1,
        "sharedContentCoordinator.processPendingSharedFiles()": 1,
        "sharedContentCoordinator.processPendingSharedText()": 1,
    }
    for token, expected_count in required_host_counts.items():
        actual_count = main_code.count(token)
        if actual_count != expected_count:
            errors.append(
                "ARCH038 MainActivity content host call count differs: "
                f"{token} expected {expected_count}, found {actual_count}"
            )
    host_call_pattern = re.compile(
        r"KiyoriMainContentHost\s*\(\s*"
        r"pendingRequests\s*=\s*pendingRequests\s*,\s*"
        r"sharedContentCoordinator\s*=\s*sharedContentCoordinator\s*,\s*"
        r"pluginLoadingState\s*=\s*pluginLoadingState\s*,?\s*"
        r"\)",
        re.DOTALL,
    )
    if len(host_call_pattern.findall(main_code)) != 1:
        errors.append(
            "ARCH038 MainActivity content host call arguments differ"
        )
    for forbidden_token in (
        "KiyoriApp(",
        "CompositionLocalProvider(",
        "LocalPluginLoadingState provides",
        "val shortcutNavItem = pendingRequests.shortcutNavItem",
        "val routeNavRequest = pendingRequests.routeId",
        "val browserOpenRequest = pendingRequests.browserUrl",
        "val kiyoriShellDestinationRequest = pendingRequests.shellDestination",
    ):
        if forbidden_token in main_code:
            errors.append(
                "ARCH038 MainActivity still owns direct content assembly: "
                f"{forbidden_token}"
            )

    test_path = root / M04D_MAIN_CONTENT_HOST_TEST_PATH
    if not test_path.is_file():
        errors.append(
            "ARCH038 content host contract test missing: "
            f"{M04D_MAIN_CONTENT_HOST_TEST_PATH}"
        )
    else:
        test_code = source_code_mask(test_path.read_text(encoding="utf-8"))
        for symbol in (
            "KiyoriMainContentRequestProjection",
            "projectKiyoriMainContentRequests",
        ):
            if symbol not in test_code:
                errors.append(
                    "ARCH038 content host contract test does not use symbol: "
                    f"{symbol}"
                )


def check_m04e_finalization(
    root: Path,
    ownership_path: Path,
    errors: list[str],
) -> None:
    content_host_path = root / M04D_MAIN_CONTENT_HOST_PATH
    if not content_host_path.is_file():
        return

    main_activity_path = root / M04_MAIN_ACTIVITY_PATH
    if not main_activity_path.is_file():
        errors.append(
            f"ARCH039 MainActivity compatibility entry missing: {M04_MAIN_ACTIVITY_PATH}"
        )
        return

    payload = tomllib.loads(ownership_path.read_text(encoding="utf-8"))
    records = payload.get("ownership", [])
    exception_records = payload.get("exception", [])
    if not isinstance(records, list) or not isinstance(exception_records, list):
        errors.append("ARCH039 ownership and exception entries must be arrays")
        return

    matching_records = [
        record
        for record in records
        if isinstance(record, dict)
        and record.get("id") == M04E_MAIN_ACTIVITY_OWNERSHIP_ID
    ]
    if len(matching_records) != 1:
        errors.append(
            "ARCH039 MainActivity compatibility ownership record count differs: "
            f"expected 1, found {len(matching_records)}"
        )
    else:
        record = matching_records[0]
        expected_scalar_fields = {
            "id": M04E_MAIN_ACTIVITY_OWNERSHIP_ID,
            "path": M04_MAIN_ACTIVITY_PATH,
            "owner": M04E_MAIN_ACTIVITY_OWNER,
            "sync_zone": M04E_MAIN_ACTIVITY_SYNC_ZONE,
            "phase": M04E_MAIN_ACTIVITY_PHASE,
        }
        for field, expected in expected_scalar_fields.items():
            actual = record.get(field)
            if actual != expected:
                errors.append(
                    "ARCH039 MainActivity compatibility ownership field differs: "
                    f"{field} expected {expected!r}, found {actual!r}"
                )
        expected_allowed = list(M04E_MAIN_ACTIVITY_ALLOWED_IMPORT_ROOTS)
        actual_allowed = record.get("allowed_import_roots")
        if actual_allowed != expected_allowed:
            errors.append(
                "ARCH039 MainActivity compatibility allowed roots differ: "
                f"expected {expected_allowed}, found {actual_allowed!r}"
            )
        expected_forbidden = list(M04E_MAIN_ACTIVITY_FORBIDDEN_IMPORT_ROOTS)
        actual_forbidden = record.get("forbidden_import_roots")
        if actual_forbidden != expected_forbidden:
            errors.append(
                "ARCH039 MainActivity compatibility forbidden roots differ: "
                f"expected {expected_forbidden}, found {actual_forbidden!r}"
            )
        if not is_exact_ownership_path(str(record.get("path", ""))):
            errors.append(
                "ARCH039 MainActivity compatibility ownership path must be exact"
            )

    stale_exceptions = [
        exception
        for exception in exception_records
        if isinstance(exception, dict)
        and exception.get("rule") == "ARCH001"
        and exception.get("path") == M04_MAIN_ACTIVITY_PATH
    ]
    if stale_exceptions:
        errors.append(
            "ARCH039 expired MainActivity ARCH001 exception remains: "
            f"{len(stale_exceptions)}"
        )

    declared_package = source_package(main_activity_path)
    if declared_package != M04E_MAIN_ACTIVITY_PACKAGE:
        errors.append(
            "ARCH039 MainActivity package differs: "
            f"expected {M04E_MAIN_ACTIVITY_PACKAGE}, "
            f"found {declared_package or 'missing'}"
        )
    main_code = source_code_mask(main_activity_path.read_text(encoding="utf-8"))
    class_pattern = re.compile(
        rf"\bclass\s+{re.escape(M04E_MAIN_ACTIVITY_CLASS)}\s*:"
        r"\s*ComponentActivity\s*\(\s*\)"
    )
    if len(class_pattern.findall(main_code)) != 1:
        errors.append(
            "ARCH039 stable MainActivity class declaration differs: "
            f"{M04E_MAIN_ACTIVITY_PACKAGE}.{M04E_MAIN_ACTIVITY_CLASS}"
        )

    manifest_path = root / "app/src/main/AndroidManifest.xml"
    if not manifest_path.is_file():
        errors.append("ARCH039 AndroidManifest.xml missing")
    else:
        manifest_root = ET.parse(manifest_path).getroot()
        activities = [
            node
            for node in manifest_root.iter("activity")
            if node.get(ANDROID_NAME) == M04E_MAIN_ACTIVITY_MANIFEST_NAME
        ]
        if len(activities) != 1:
            errors.append(
                "ARCH039 stable MainActivity manifest entry count differs: "
                f"expected 1, found {len(activities)}"
            )
        else:
            launcher_filters = 0
            for intent_filter in activities[0].findall("intent-filter"):
                actions = {
                    node.get(ANDROID_NAME)
                    for node in intent_filter.findall("action")
                }
                categories = {
                    node.get(ANDROID_NAME)
                    for node in intent_filter.findall("category")
                }
                if (
                    "android.intent.action.MAIN" in actions
                    and "android.intent.category.LAUNCHER" in categories
                ):
                    launcher_filters += 1
            if launcher_filters != 1:
                errors.append(
                    "ARCH039 stable MainActivity MAIN/LAUNCHER filter count differs: "
                    f"expected 1, found {launcher_filters}"
                )

    architecture_root = root / "config/architecture"
    import_snapshot_path = architecture_root / M04E_MAIN_ACTIVITY_IMPORT_SNAPSHOT
    if not import_snapshot_path.is_file():
        errors.append(
            "ARCH039 MainActivity project import snapshot missing: "
            f"config/architecture/{M04E_MAIN_ACTIVITY_IMPORT_SNAPSHOT}"
        )
    else:
        expected_import_entries = read_snapshot(import_snapshot_path)
        if len(expected_import_entries) != len(set(expected_import_entries)):
            errors.append(
                "ARCH039 MainActivity project import snapshot has duplicate entries"
            )
        if any(
            not is_project_import(imported)
            for imported in expected_import_entries
        ):
            errors.append(
                "ARCH039 MainActivity project import snapshot has non-project entries"
            )
        expected_project_imports = Counter(expected_import_entries)
        actual_project_imports = Counter(
            imported
            for _, imported in source_imports(main_activity_path)
            if is_project_import(imported)
        )
        if actual_project_imports != expected_project_imports:
            errors.append(
                "ARCH039 MainActivity project imports differ: "
                f"expected {sorted(expected_project_imports.elements())}, "
                f"found {sorted(actual_project_imports.elements())}"
            )
        duplicate_imports = sorted(
            imported
            for imported, count in actual_project_imports.items()
            if count > 1
        )
        if duplicate_imports:
            errors.append(
                "ARCH039 MainActivity has duplicate project imports: "
                + ", ".join(duplicate_imports)
            )

    feature_edges = [
        (line_number, imported, reference_kind)
        for line_number, imported, reference_kind in source_dependency_edges(
            main_activity_path
        )
        if import_matches_root(imported, "com.kiyori.feature")
    ]
    if feature_edges:
        errors.append(
            "ARCH039 MainActivity directly depends on Kiyori feature implementation: "
            + ", ".join(
                f"{reference_kind} {imported} at line {line_number}"
                for line_number, imported, reference_kind in feature_edges
            )
        )

    main_imports = Counter(
        imported for _, imported in source_imports(main_activity_path)
    )
    content_host_import = (
        f"{M04D_MAIN_CONTENT_HOST_PACKAGE}.KiyoriMainContentHost"
    )
    if main_imports[content_host_import] != 1:
        errors.append(
            "ARCH039 MainActivity content host import count differs: "
            f"expected 1, found {main_imports[content_host_import]}"
        )
    required_counts = {
        "KiyoriMainContentHost(": 1,
        "KiyoriApp(": 0,
        "CompositionLocalProvider(": 0,
        "LocalPluginLoadingState provides": 0,
    }
    for token, expected_count in required_counts.items():
        actual_count = main_code.count(token)
        if actual_count != expected_count:
            errors.append(
                "ARCH039 MainActivity compatibility content boundary differs: "
                f"{token} expected {expected_count}, found {actual_count}"
            )

    architecture_test_path = root / M04E_ARCHITECTURE_TEST_PATH
    if not architecture_test_path.is_file():
        errors.append(
            f"ARCH039 ownership specificity tests missing: {M04E_ARCHITECTURE_TEST_PATH}"
        )
    else:
        test_code = source_code_mask(
            architecture_test_path.read_text(encoding="utf-8")
        )
        required_tests = (
            "test_exact_ownership_overrides_broad_glob",
            "test_overlapping_broad_ownership_is_rejected",
            "test_m04e_finalization_accepts_compatibility_owner",
            "test_m04e_finalization_rejects_stale_exception",
        )
        for test_name in required_tests:
            if test_name not in test_code:
                errors.append(
                    "ARCH039 ownership specificity test missing: "
                    f"{test_name}"
                )


def check_m05a1_design_theme(
    root: Path,
    ownership_path: Path,
    errors: list[str],
) -> None:
    color_schemes_path = root / M05A1_COLOR_SCHEMES_PATH
    if not color_schemes_path.is_file():
        errors.append(
            "ARCH040 M-05A1 design theme missing: "
            f"{M05A1_COLOR_SCHEMES_PATH}"
        )
        return

    architecture_root = root / "config/architecture"
    available_design_paths: list[tuple[str, str, Path]] = []
    for label, relative_path, hash_snapshot_name in M05A1_DESIGN_FILES:
        source_path = root / relative_path
        if not source_path.is_file():
            errors.append(
                f"ARCH040 M-05A1 {label} source missing: {relative_path}"
            )
            continue
        available_design_paths.append((label, relative_path, source_path))
        declared_package = source_package(source_path)
        if declared_package != M05A1_DESIGN_PACKAGE:
            errors.append(
                f"ARCH040 M-05A1 {label} package differs: "
                f"expected {M05A1_DESIGN_PACKAGE}, "
                f"found {declared_package or 'missing'}"
            )

        project_imports = [
            imported
            for _, imported in source_imports(source_path)
            if is_project_import(imported)
        ]
        if project_imports:
            errors.append(
                f"ARCH040 M-05A1 {label} project imports differ: "
                f"expected [], found {project_imports}"
            )

        hash_snapshot_path = architecture_root / hash_snapshot_name
        if not hash_snapshot_path.is_file():
            errors.append(
                f"ARCH040 M-05A1 {label} hash snapshot missing: "
                f"config/architecture/{hash_snapshot_name}"
            )
            continue
        hash_entries = read_hash_snapshot(hash_snapshot_path)
        if len(hash_entries) != 1 or hash_entries[0][1] != relative_path:
            errors.append(
                f"ARCH040 M-05A1 {label} hash snapshot target differs: "
                f"expected only {relative_path}, found "
                f"{[path for _, path in hash_entries]}"
            )
            continue
        normalized = source_path.read_bytes().replace(b"\r\n", b"\n")
        actual_hash = hashlib.sha256(normalized).hexdigest().upper()
        expected_hash = hash_entries[0][0]
        if actual_hash != expected_hash:
            errors.append(
                f"ARCH040 M-05A1 {label} source changed: "
                f"expected {expected_hash}, found {actual_hash}"
            )

    design_code = "\n".join(
        source_code_mask(source_path.read_text(encoding="utf-8"))
        for _, _, source_path in available_design_paths
    )
    for forbidden_token in M05A1_FORBIDDEN_DESIGN_TOKENS:
        if forbidden_token in design_code:
            errors.append(
                "ARCH040 M-05A1 design source owns forbidden dependency "
                f"or platform state: {forbidden_token}"
            )

    declaration_locations: dict[str, list[str]] = {
        symbol: [] for symbol in M05A1_DECLARATION_OWNERS
    }
    main_source_root = root / "app/src/main/java"
    if main_source_root.is_dir():
        for source_path in sorted(main_source_root.rglob("*.kt")):
            relative_path = source_path.relative_to(root).as_posix()
            code = source_code_mask(source_path.read_text(encoding="utf-8"))
            for symbol in M05A1_DECLARATION_OWNERS:
                declaration_pattern = re.compile(
                    rf"\b(?:data\s+class|class|val|fun)\s+"
                    rf"{re.escape(symbol)}\b"
                )
                declaration_locations[symbol].extend(
                    relative_path
                    for _ in declaration_pattern.finditer(code)
                )
    for symbol, expected_owner in M05A1_DECLARATION_OWNERS.items():
        actual_owners = declaration_locations[symbol]
        if actual_owners != [expected_owner]:
            errors.append(
                f"ARCH040 M-05A1 design declaration owner differs for {symbol}: "
                f"expected [{expected_owner}], found {actual_owners}"
            )

    for old_path in (
        M05A1_OLD_BROWSER_THEME_PATH,
        M05A1_OLD_SETTINGS_THEME_PATH,
    ):
        if (root / old_path).exists():
            errors.append(
                f"ARCH040 M-05A1 old design owner remains: {old_path}"
            )

    moved_symbols = set(M05A1_DECLARATION_OWNERS)
    actual_import_consumers: dict[str, set[str]] = {
        symbol: set() for symbol in M05A1_EXPECTED_IMPORT_CONSUMERS
    }
    if main_source_root.is_dir():
        for source_path in sorted(main_source_root.rglob("*.kt")):
            relative_path = source_path.relative_to(root).as_posix()
            for _, imported in source_imports(source_path):
                imported_symbol = imported.rsplit(".", maxsplit=1)[-1]
                if (
                    imported.startswith(
                        "com.ai.assistance.operit.ui.theme."
                    )
                    and imported_symbol in moved_symbols
                ):
                    errors.append(
                        "ARCH040 M-05A1 consumer still imports old design owner: "
                        f"{relative_path} -> {imported}"
                    )
                if (
                    imported_symbol in actual_import_consumers
                    and imported
                    == f"{M05A1_DESIGN_PACKAGE}.{imported_symbol}"
                ):
                    actual_import_consumers[imported_symbol].add(relative_path)
    for symbol, baseline_expected_consumers in (
        M05A1_EXPECTED_IMPORT_CONSUMERS.items()
    ):
        expected_consumers = set(baseline_expected_consumers)
        if (
            symbol == "resolveKiyoriColorScheme"
            and (root / M05A3_APP_THEME_PATH).is_file()
        ):
            expected_consumers.add(M05A3_APP_THEME_PATH)
        actual_consumers = actual_import_consumers[symbol]
        if actual_consumers != expected_consumers:
            errors.append(
                f"ARCH040 M-05A1 import consumers differ for {symbol}: "
                f"expected {sorted(expected_consumers)}, "
                f"found {sorted(actual_consumers)}"
            )

    old_resolver_path = root / M05A1_OLD_COLOR_RESOLVER_PATH
    if not old_resolver_path.is_file():
        errors.append(
            "ARCH040 M-05A1 preference adapter missing: "
            f"{M05A1_OLD_COLOR_RESOLVER_PATH}"
        )
    else:
        resolver_code = source_code_mask(
            old_resolver_path.read_text(encoding="utf-8")
        )
        overload_count = len(
            re.findall(r"\bfun\s+resolveThemeColorScheme\s*\(", resolver_code)
        )
        if overload_count != 2:
            errors.append(
                "ARCH040 M-05A1 preference adapter overload count differs: "
                f"expected 2, found {overload_count}"
            )
        bool_delegate_pattern = re.compile(
            r"\bfun\s+resolveThemeColorScheme\s*\(\s*"
            r"darkTheme\s*:\s*Boolean\s*,?\s*\)\s*:\s*ColorScheme\s*=\s*"
            r"resolveKiyoriColorScheme\s*\(\s*(?:darkTheme\s*=\s*)?"
            r"darkTheme\s*\)"
        )
        if len(bool_delegate_pattern.findall(resolver_code)) != 1:
            errors.append(
                "ARCH040 M-05A1 bool preference adapter must delegate once "
                "to resolveKiyoriColorScheme"
            )
        for required_token in (
            "resolveDarkTheme(context, snapshot)",
            "snapshot.useSystemTheme",
            "snapshot.themeMode == UserPreferencesManager.THEME_MODE_DARK",
            "Configuration.UI_MODE_NIGHT_MASK",
            "Configuration.UI_MODE_NIGHT_YES",
        ):
            if required_token not in resolver_code:
                errors.append(
                    "ARCH040 M-05A1 preference decision contract differs: "
                    f"{required_token}"
                )

    payload = tomllib.loads(ownership_path.read_text(encoding="utf-8"))
    records = payload.get("ownership", [])
    if not isinstance(records, list):
        errors.append("ARCH040 M-05A1 ownership entries must be an array")
    else:
        ownership_by_id = {
            str(record.get("id")): record
            for record in records
            if isinstance(record, dict)
        }
        operit_ui = ownership_by_id.get("operit-ui")
        expected_operit_ui_allowed = [
            "com.ai.assistance.operit",
            "com.kiyori.capability",
            "com.kiyori.design",
            "com.kiyori.platform",
        ]
        if (
            operit_ui is None
            or operit_ui.get("allowed_import_roots")
            != expected_operit_ui_allowed
        ):
            errors.append(
                "ARCH040 M-05A1 operit-ui design permission differs: "
                f"expected {expected_operit_ui_allowed}, "
                f"found {None if operit_ui is None else operit_ui.get('allowed_import_roots')!r}"
            )
        for record in records:
            if not isinstance(record, dict):
                continue
            identifier = str(record.get("id", ""))
            if identifier.startswith("operit-") and identifier != "operit-ui":
                allowed = record.get("allowed_import_roots", [])
                if (
                    isinstance(allowed, list)
                    and "com.kiyori.design" in allowed
                ):
                    errors.append(
                        "ARCH040 M-05A1 non-UI Operit owner gained design "
                        f"permission: {identifier}"
                    )

        design_record = ownership_by_id.get("kiyori-design")
        expected_design_fields = {
            "path": "app/src/main/java/com/kiyori/design/**",
            "owner": "kiyori-design",
            "sync_zone": "C",
            "phase": "m05a1",
            "allowed_import_roots": [
                "com.kiyori.capability",
                "com.kiyori.design",
                "com.kiyori.platform",
            ],
        }
        if design_record is None:
            errors.append("ARCH040 M-05A1 kiyori-design ownership missing")
        else:
            for field, expected in expected_design_fields.items():
                actual = design_record.get(field)
                if actual != expected:
                    errors.append(
                        "ARCH040 M-05A1 kiyori-design ownership field differs: "
                        f"{field} expected {expected!r}, found {actual!r}"
                    )
            if "planned" in design_record:
                errors.append(
                    "ARCH040 M-05A1 kiyori-design ownership remains planned"
                )

    design_test_path = root / M05A1_DESIGN_TEST_PATH
    if not design_test_path.is_file():
        errors.append(
            f"ARCH040 M-05A1 design contract test missing: {M05A1_DESIGN_TEST_PATH}"
        )
    else:
        design_test_code = source_code_mask(
            design_test_path.read_text(encoding="utf-8")
        )
        if source_package(design_test_path) != M05A1_DESIGN_PACKAGE:
            errors.append(
                "ARCH040 M-05A1 design contract test package differs"
            )
        required_design_tests = (
            "light palette uses the accessible Kiyori accent contract",
            "dark palette keeps blue actions on layered neutral surfaces",
            "browser palettes retain the neutral chrome contract",
            "theme resolver exposes only the fixed Kiyori light and dark palettes",
            "settings palette keeps a neutral hierarchy in both application themes",
            "default text pairs exceed WCAG normal text contrast",
            "light primary remains a visible non text accent on white",
        )
        for test_name in required_design_tests:
            if test_name not in design_test_code:
                errors.append(
                    "ARCH040 M-05A1 fixed design assertion missing: "
                    f"{test_name}"
                )
        for symbol in (
            "resolveKiyoriColorScheme",
            "resolveKiyoriSettingsColors",
            "contrastRatio",
        ):
            if symbol not in design_test_code:
                errors.append(
                    "ARCH040 M-05A1 design contract test symbol missing: "
                    f"{symbol}"
                )

    old_theme_test_path = root / M05A1_OLD_THEME_TEST_PATH
    m05a3_active = (root / M05A3_DESIGN_THEME_PATH).is_file()
    if m05a3_active and old_theme_test_path.exists():
        errors.append(
            "ARCH040 M-05A1 residual theme test remains after M-05A3: "
            f"{M05A1_OLD_THEME_TEST_PATH}"
        )
    elif not m05a3_active and not old_theme_test_path.is_file():
        errors.append(
            f"ARCH040 M-05A1 residual theme test missing: {M05A1_OLD_THEME_TEST_PATH}"
        )
    elif not m05a3_active:
        old_theme_test_code = source_code_mask(
            old_theme_test_path.read_text(encoding="utf-8")
        )
        retained_tests = (
            ("all typography roles use neutral tracking",)
            if (root / M05A2_SEMANTIC_COLORS_PATH).is_file()
            else (
                "application semantic icon tones stay colorful and theme aware",
                "bottom navigation yellow is exact while weather sun remains independent",
                "stable entry ids keep deterministic semantic tones",
                "all typography roles use neutral tracking",
            )
        )
        for retained_test in retained_tests:
            if retained_test not in old_theme_test_code:
                errors.append(
                    "ARCH040 M-05A1 residual theme assertion missing: "
                    f"{retained_test}"
                )

    architecture_test_path = root / M05A1_ARCHITECTURE_TEST_PATH
    if not architecture_test_path.is_file():
        errors.append(
            "ARCH040 M-05A1 architecture tests missing: "
            f"{M05A1_ARCHITECTURE_TEST_PATH}"
        )
    else:
        architecture_test_code = source_code_mask(
            architecture_test_path.read_text(encoding="utf-8")
        )
        for test_name in (
            "test_m05a1_design_theme_accepts_pure_owner",
            "test_m05a1_design_theme_rejects_owner_or_dependency_drift",
        ):
            if test_name not in architecture_test_code:
                errors.append(
                    "ARCH040 M-05A1 architecture test missing: "
                    f"{test_name}"
                )


def check_m05a2_semantic_design(
    root: Path,
    ownership_path: Path,
    errors: list[str],
) -> None:
    semantic_colors_path = root / M05A2_SEMANTIC_COLORS_PATH
    if not semantic_colors_path.is_file():
        errors.append(
            "ARCH041 M-05A2 semantic design missing: "
            f"{M05A2_SEMANTIC_COLORS_PATH}"
        )
        return

    semantic_theme_path = root / M05A2_SEMANTIC_THEME_PATH
    if not semantic_theme_path.is_file():
        errors.append(
            "ARCH041 M-05A2 semantic Compose adapter missing: "
            f"{M05A2_SEMANTIC_THEME_PATH}"
        )
        return

    architecture_root = root / "config/architecture"
    source_specs = (
        (
            "semantic colors",
            M05A2_SEMANTIC_COLORS_PATH,
            semantic_colors_path,
            M05A2_SEMANTIC_COLORS_HASH_SNAPSHOT,
        ),
        (
            "semantic Compose adapter",
            M05A2_SEMANTIC_THEME_PATH,
            semantic_theme_path,
            M05A2_SEMANTIC_THEME_HASH_SNAPSHOT,
        ),
    )
    for label, relative_path, source_path, snapshot_name in source_specs:
        declared_package = source_package(source_path)
        if declared_package != M05A2_DESIGN_PACKAGE:
            errors.append(
                f"ARCH041 M-05A2 {label} package differs: "
                f"expected {M05A2_DESIGN_PACKAGE}, "
                f"found {declared_package or 'missing'}"
            )

        project_imports = [
            imported
            for _, imported in source_imports(source_path)
            if is_project_import(imported)
        ]
        if project_imports:
            errors.append(
                f"ARCH041 M-05A2 {label} project imports differ: "
                f"expected [], found {project_imports}"
            )

        snapshot_path = architecture_root / snapshot_name
        if not snapshot_path.is_file():
            errors.append(
                f"ARCH041 M-05A2 {label} hash snapshot missing: "
                f"config/architecture/{snapshot_name}"
            )
            continue
        hash_entries = read_hash_snapshot(snapshot_path)
        if len(hash_entries) != 1 or hash_entries[0][1] != relative_path:
            errors.append(
                f"ARCH041 M-05A2 {label} hash snapshot target differs: "
                f"expected only {relative_path}, found "
                f"{[path for _, path in hash_entries]}"
            )
            continue
        normalized = source_path.read_bytes().replace(b"\r\n", b"\n")
        actual_hash = hashlib.sha256(normalized).hexdigest().upper()
        expected_hash = hash_entries[0][0]
        if actual_hash != expected_hash:
            errors.append(
                f"ARCH041 M-05A2 {label} source changed: "
                f"expected {expected_hash}, found {actual_hash}"
            )

    colors_code = source_code_mask(
        semantic_colors_path.read_text(encoding="utf-8")
    )
    theme_code = source_code_mask(
        semantic_theme_path.read_text(encoding="utf-8")
    )
    for forbidden_token in (
        "MaterialTheme",
        "@Composable",
        "remember(",
        "mutableStateOf",
        "StateFlow",
        "DataStore",
        "SharedPreferences",
        "UserPreferencesManager",
        "ThemePreferenceSnapshot",
        "Application",
        "Activity",
        "Window",
        "Lifecycle",
        "Permission",
        "OperitPaths",
        "Repository",
        "ViewModel",
        "com.ai.assistance.operit",
    ):
        if forbidden_token in colors_code:
            errors.append(
                "ARCH041 M-05A2 pure semantic source owns forbidden "
                f"dependency or state: {forbidden_token}"
            )

    enum_pattern = re.compile(
        r"enum\s+class\s+KiyoriSemanticTone\s*\{\s*"
        r"BLUE\s*,\s*GREEN\s*,\s*PURPLE\s*,\s*ORANGE\s*,\s*"
        r"RED\s*,\s*CYAN\s*,\s*PINK\s*,?\s*\}",
        re.DOTALL,
    )
    if len(enum_pattern.findall(colors_code)) != 1:
        errors.append("ARCH041 M-05A2 semantic tone order differs")
    for token in (
        "KiyoriSemanticTone.entries[",
        "Math.floorMod(stableId.hashCode(), KiyoriSemanticTone.entries.size)",
    ):
        if colors_code.count(token) != 1:
            errors.append(
                "ARCH041 M-05A2 stable ID tone mapping differs: "
                f"{token}"
            )
    for literal in M05A2_SEMANTIC_COLOR_LITERALS:
        token = f"Color(0x{literal})"
        if colors_code.count(token) != 1:
            errors.append(
                "ARCH041 M-05A2 semantic color literal count differs: "
                f"{token} expected 1, found {colors_code.count(token)}"
            )

    expected_theme_imports = Counter(
        (
            "androidx.compose.material3.MaterialTheme",
            "androidx.compose.runtime.Composable",
            "androidx.compose.ui.graphics.Color",
            "androidx.compose.ui.graphics.luminance",
        )
    )
    actual_theme_imports = Counter(
        imported for _, imported in source_imports(semantic_theme_path)
    )
    if actual_theme_imports != expected_theme_imports:
        errors.append(
            "ARCH041 M-05A2 semantic Compose adapter imports differ: "
            f"expected {sorted(expected_theme_imports.elements())}, "
            f"found {sorted(actual_theme_imports.elements())}"
        )
    for forbidden_token in (
        "Color(0x",
        "remember(",
        "mutableStateOf",
        "StateFlow",
        "Flow<",
        "DataStore",
        "SharedPreferences",
        "UserPreferencesManager",
        "ThemePreferenceSnapshot",
        "Application",
        "Activity",
        "Window",
        "Lifecycle",
        "Permission",
        "OperitPaths",
        "Repository",
        "ViewModel",
        "com.ai.assistance.operit",
    ):
        if forbidden_token in theme_code:
            errors.append(
                "ARCH041 M-05A2 semantic Compose adapter owns forbidden "
                f"dependency or state: {forbidden_token}"
            )
    required_theme_counts = {
        "@Composable": 2,
        "MaterialTheme.colorScheme.background.luminance() < 0.5f": 2,
        "resolveKiyoriSemanticColors(this, isDark)": 1,
        "resolveKiyoriWeatherSunColor(isDark)": 1,
    }
    for token, expected_count in required_theme_counts.items():
        actual_count = theme_code.count(token)
        if actual_count != expected_count:
            errors.append(
                "ARCH041 M-05A2 semantic Compose adapter contract differs: "
                f"{token} expected {expected_count}, found {actual_count}"
            )

    main_source_root = root / "app/src/main/java"
    source_paths = (
        sorted(
            path
            for path in main_source_root.rglob("*")
            if path.is_file() and path.suffix in MANAGED_SOURCE_SUFFIXES
        )
        if main_source_root.is_dir()
        else []
    )
    declaration_patterns = {
        "KiyoriSemanticTone": r"\benum\s+class\s+KiyoriSemanticTone\b",
        "kiyoriSemanticToneForStableId": (
            r"\bfun\s+kiyoriSemanticToneForStableId\s*\("
        ),
        "KiyoriSemanticColors": r"\bdata\s+class\s+KiyoriSemanticColors\b",
        "resolveKiyoriSemanticColors": (
            r"\bfun\s+resolveKiyoriSemanticColors\s*\("
        ),
        "KiyoriBottomNavigationSelectedFillColor": (
            r"\bval\s+KiyoriBottomNavigationSelectedFillColor\b"
        ),
        "resolveKiyoriWeatherSunColor": (
            r"\bfun\s+resolveKiyoriWeatherSunColor\s*\("
        ),
        "resolveColors": (
            r"\bfun\s+KiyoriSemanticTone\.resolveColors\s*\("
        ),
        "kiyoriWeatherSunColor": (
            r"\bfun\s+kiyoriWeatherSunColor\s*\("
        ),
    }
    for symbol, expected_owner in M05A2_DECLARATION_OWNERS.items():
        pattern = declaration_patterns[symbol]
        declaration_sites = [
            path.relative_to(root).as_posix()
            for path in source_paths
            if re.search(
                pattern,
                source_code_mask(path.read_text(encoding="utf-8")),
            )
        ]
        if declaration_sites != [expected_owner]:
            errors.append(
                f"ARCH041 M-05A2 semantic declaration owner differs for {symbol}: "
                f"expected [{expected_owner}], found {declaration_sites}"
            )

    old_source_path = root / M05A2_OLD_SEMANTIC_THEME_PATH
    if old_source_path.exists():
        errors.append(
            "ARCH041 M-05A2 old semantic design owner remains: "
            f"{M05A2_OLD_SEMANTIC_THEME_PATH}"
        )

    import_snapshot_path = architecture_root / M05A2_CONSUMER_IMPORT_SNAPSHOT
    if not import_snapshot_path.is_file():
        errors.append(
            "ARCH041 M-05A2 consumer import snapshot missing: "
            f"config/architecture/{M05A2_CONSUMER_IMPORT_SNAPSHOT}"
        )
        expected_imports: Counter[tuple[str, str]] = Counter()
    else:
        snapshot_entries = read_snapshot(import_snapshot_path)
        parsed_entries: list[tuple[str, str]] = []
        for entry in snapshot_entries:
            fields = entry.split("\t")
            if len(fields) != 2:
                raise ValueError(
                    "invalid M-05A2 consumer import snapshot entry"
                )
            path, imported = fields
            imported_symbol = imported.rsplit(".", maxsplit=1)[-1]
            if (
                not path.startswith("app/src/")
                or imported
                != f"{M05A2_DESIGN_PACKAGE}.{imported_symbol}"
                or imported_symbol not in M05A2_MOVED_IMPORT_SYMBOLS
            ):
                raise ValueError(
                    "invalid M-05A2 consumer import snapshot entry"
                )
            parsed_entries.append((path, imported))
        expected_imports = Counter(parsed_entries)
        if any(count != 1 for count in expected_imports.values()):
            raise ValueError("duplicate M-05A2 consumer import snapshot entry")
        if len(parsed_entries) != M05A2_CONSUMER_IMPORT_COUNT:
            errors.append(
                "ARCH041 M-05A2 consumer import count differs: "
                f"expected {M05A2_CONSUMER_IMPORT_COUNT}, "
                f"found {len(parsed_entries)}"
            )
        production_paths = {
            path
            for path, _ in parsed_entries
            if path.startswith("app/src/main/")
        }
        external_test_paths = {
            path
            for path, _ in parsed_entries
            if path.startswith(("app/src/test/", "app/src/androidTest/"))
        }
        if len(production_paths) != M05A2_PRODUCTION_CONSUMER_COUNT:
            errors.append(
                "ARCH041 M-05A2 production consumer count differs: "
                f"expected {M05A2_PRODUCTION_CONSUMER_COUNT}, "
                f"found {len(production_paths)}"
            )
        if len(external_test_paths) != M05A2_EXTERNAL_TEST_CONSUMER_COUNT:
            errors.append(
                "ARCH041 M-05A2 external test consumer count differs: "
                f"expected {M05A2_EXTERNAL_TEST_CONSUMER_COUNT}, "
                f"found {len(external_test_paths)}"
            )
        for relative_path in production_paths | external_test_paths:
            if not (root / relative_path).is_file():
                errors.append(
                    "ARCH041 M-05A2 consumer snapshot path missing: "
                    f"{relative_path}"
                )

    scan_roots = (
        root / "app/src/main",
        root / "app/src/test",
        root / "app/src/androidTest",
    )
    consumer_source_paths = sorted(
        path
        for scan_root in scan_roots
        for path in source_files(scan_root, MANAGED_SOURCE_SUFFIXES)
    )
    actual_imports: Counter[tuple[str, str]] = Counter()
    old_package_prefix = "com.ai.assistance.operit.ui.theme."
    for source_path in consumer_source_paths:
        relative_path = source_path.relative_to(root).as_posix()
        code = source_code_mask(source_path.read_text(encoding="utf-8"))
        for _, imported in source_imports(source_path):
            imported_symbol = imported.rsplit(".", maxsplit=1)[-1]
            if (
                imported.startswith(old_package_prefix)
                and imported_symbol in M05A2_MOVED_IMPORT_SYMBOLS
            ):
                errors.append(
                    "ARCH041 M-05A2 consumer still imports old semantic owner: "
                    f"{relative_path} -> {imported}"
                )
            if (
                imported_symbol in M05A2_MOVED_IMPORT_SYMBOLS
                and imported
                == f"{M05A2_DESIGN_PACKAGE}.{imported_symbol}"
            ):
                actual_imports[(relative_path, imported)] += 1
        for symbol in M05A2_DECLARATION_OWNERS:
            old_reference = f"{old_package_prefix}{symbol}"
            if old_reference in code:
                errors.append(
                    "ARCH041 M-05A2 old qualified semantic reference remains: "
                    f"{relative_path} -> {old_reference}"
                )
    if actual_imports != expected_imports:
        errors.append(
            "ARCH041 M-05A2 consumer imports differ: "
            f"expected {sorted(expected_imports.elements())}, "
            f"found {sorted(actual_imports.elements())}"
        )

    for symbol, expected_paths in M05A2_EXPECTED_QUALIFIED_REFERENCES.items():
        new_reference = f"{M05A2_DESIGN_PACKAGE}.{symbol}"
        actual_paths: list[str] = []
        for source_path in consumer_source_paths:
            relative_path = source_path.relative_to(root).as_posix()
            code = source_code_mask(source_path.read_text(encoding="utf-8"))
            actual_paths.extend(
                relative_path for _ in range(code.count(new_reference))
            )
        if actual_paths != sorted(expected_paths):
            errors.append(
                f"ARCH041 M-05A2 qualified reference differs for {symbol}: "
                f"expected {sorted(expected_paths)}, found {actual_paths}"
            )

    payload = tomllib.loads(ownership_path.read_text(encoding="utf-8"))
    records = payload.get("ownership", [])
    if not isinstance(records, list):
        errors.append("ARCH041 M-05A2 ownership entries must be an array")
    else:
        for record in records:
            if not isinstance(record, dict):
                continue
            identifier = str(record.get("id", ""))
            allowed = record.get("allowed_import_roots", [])
            if (
                identifier.startswith("operit-")
                and identifier != "operit-ui"
                and isinstance(allowed, list)
                and "com.kiyori.design" in allowed
            ):
                errors.append(
                    "ARCH041 M-05A2 non-UI Operit owner gained design "
                    f"permission: {identifier}"
                )

    design_test_path = root / M05A1_DESIGN_TEST_PATH
    if not design_test_path.is_file():
        errors.append(
            f"ARCH041 M-05A2 design contract test missing: {M05A1_DESIGN_TEST_PATH}"
        )
    else:
        design_test_code = source_code_mask(
            design_test_path.read_text(encoding="utf-8")
        )
        required_tests = (
            "application semantic icon tones stay colorful and theme aware",
            "bottom navigation yellow is exact while weather sun remains independent",
            "stable entry ids keep deterministic semantic tones",
        )
        for test_name in required_tests:
            if test_name not in design_test_code:
                errors.append(
                    "ARCH041 M-05A2 semantic design assertion missing: "
                    f"{test_name}"
                )
        for symbol in (
            "KiyoriSemanticTone",
            "resolveKiyoriSemanticColors",
            "KiyoriBottomNavigationSelectedFillColor",
            "resolveKiyoriWeatherSunColor",
            "kiyoriSemanticToneForStableId",
            "contrastRatio",
        ):
            if symbol not in design_test_code:
                errors.append(
                    "ARCH041 M-05A2 semantic design test symbol missing: "
                    f"{symbol}"
                )

    old_theme_test_path = root / M05A1_OLD_THEME_TEST_PATH
    m05a3_active = (root / M05A3_DESIGN_THEME_PATH).is_file()
    if m05a3_active and old_theme_test_path.exists():
        errors.append(
            "ARCH041 M-05A2 residual theme test remains after M-05A3: "
            f"{M05A1_OLD_THEME_TEST_PATH}"
        )
    elif not m05a3_active and not old_theme_test_path.is_file():
        errors.append(
            f"ARCH041 M-05A2 residual theme test missing: {M05A1_OLD_THEME_TEST_PATH}"
        )
    elif not m05a3_active:
        old_theme_test_code = source_code_mask(
            old_theme_test_path.read_text(encoding="utf-8")
        )
        if "all typography roles use neutral tracking" not in old_theme_test_code:
            errors.append(
                "ARCH041 M-05A2 residual Typography assertion missing"
            )
        for forbidden_test in (
            "application semantic icon tones stay colorful and theme aware",
            "bottom navigation yellow is exact while weather sun remains independent",
            "stable entry ids keep deterministic semantic tones",
        ):
            if forbidden_test in old_theme_test_code:
                errors.append(
                    "ARCH041 M-05A2 old theme test still owns semantic assertion: "
                    f"{forbidden_test}"
                )

    for snapshot_name, replacements in (
        M05A2_M04B_SEMANTIC_SNAPSHOT_REPLACEMENTS.items()
    ):
        snapshot_path = architecture_root / snapshot_name
        if not snapshot_path.is_file():
            errors.append(
                "ARCH041 M-05A2 M-04B semantic snapshot missing: "
                f"config/architecture/{snapshot_name}"
            )
            continue
        entries = read_snapshot(snapshot_path)
        for old_import, new_import in replacements.items():
            if old_import in entries or entries.count(new_import) != 1:
                errors.append(
                    "ARCH041 M-05A2 M-04B semantic snapshot differs: "
                    f"{snapshot_name} expected {new_import} once and "
                    f"{old_import} zero times"
                )

    architecture_test_path = root / M05A2_ARCHITECTURE_TEST_PATH
    if not architecture_test_path.is_file():
        errors.append(
            "ARCH041 M-05A2 architecture tests missing: "
            f"{M05A2_ARCHITECTURE_TEST_PATH}"
        )
    else:
        architecture_test_code = source_code_mask(
            architecture_test_path.read_text(encoding="utf-8")
        )
        for test_name in (
            "test_m05a2_semantic_design_accepts_split_owner",
            "test_m05a2_semantic_design_rejects_contract_or_consumer_drift",
        ):
            if test_name not in architecture_test_code:
                errors.append(
                    "ARCH041 M-05A2 architecture test missing: "
                    f"{test_name}"
                )


def check_m05a3_root_theme(
    root: Path,
    ownership_path: Path,
    errors: list[str],
) -> None:
    design_theme_path = root / M05A3_DESIGN_THEME_PATH
    if not design_theme_path.is_file():
        errors.append(
            "ARCH042 M-05A3 pure root theme missing: "
            f"{M05A3_DESIGN_THEME_PATH}"
        )
        return

    required_paths = {
        "Kiyori Typography": M05A3_TYPOGRAPHY_PATH,
        "Kiyori app theme host": M05A3_APP_THEME_PATH,
        "Kiyori Application system bars": M05A3_SYSTEM_BARS_PATH,
        "configured Typography adapter": M05A3_TYPE_PATH,
        "Liquid Glass owner": M05A3_LIQUID_GLASS_PATH,
        "Water Glass owner": M05A3_WATER_GLASS_PATH,
        "Player Activity": M05A3_PLAYER_ACTIVITY_PATH,
        "Operit utility theme": M05A3_UTILITY_THEME_PATH,
        "Floating window theme": M05A3_FLOATING_THEME_PATH,
        "MainActivity": M04_MAIN_ACTIVITY_PATH,
        "Widget theme host": M05A3_WIDGET_THEME_HOST_PATH,
    }
    missing_paths = [
        relative_path
        for relative_path in required_paths.values()
        if not (root / relative_path).is_file()
    ]
    for label, relative_path in required_paths.items():
        if relative_path in missing_paths:
            errors.append(
                f"ARCH042 M-05A3 {label} missing: {relative_path}"
            )
    if missing_paths:
        return

    architecture_root = root / "config/architecture"
    snapshot_specs = (
        (
            M05A3_ROOT_HASH_SNAPSHOT,
            set(M05A3_HASHED_PATHS),
            "root theme",
        ),
        (
            M05A3_RESOURCE_HASH_SNAPSHOT,
            set(M05A3_THEME_RESOURCE_PATHS),
            "theme resource",
        ),
    )
    for snapshot_name, expected_paths, label in snapshot_specs:
        snapshot_path = architecture_root / snapshot_name
        if not snapshot_path.is_file():
            errors.append(
                f"ARCH042 M-05A3 {label} hash snapshot missing: "
                f"config/architecture/{snapshot_name}"
            )
            continue
        entries = read_hash_snapshot(snapshot_path)
        actual_paths = [relative_path for _, relative_path in entries]
        if len(actual_paths) != len(set(actual_paths)) or set(actual_paths) != expected_paths:
            errors.append(
                f"ARCH042 M-05A3 {label} hash targets differ: "
                f"expected {sorted(expected_paths)}, found {sorted(actual_paths)}"
            )
            continue
        for expected_hash, relative_path in entries:
            source_path = root / relative_path
            if not source_path.is_file():
                errors.append(
                    f"ARCH042 M-05A3 hashed path missing: {relative_path}"
                )
                continue
            normalized = source_path.read_bytes().replace(b"\r\n", b"\n")
            actual_hash = hashlib.sha256(normalized).hexdigest().upper()
            if actual_hash != expected_hash:
                errors.append(
                    "ARCH042 M-05A3 source changed: "
                    f"{relative_path} expected {expected_hash}, found {actual_hash}"
                )

    package_expectations = {
        M05A3_DESIGN_THEME_PATH: M05A3_DESIGN_PACKAGE,
        M05A3_TYPOGRAPHY_PATH: M05A3_DESIGN_PACKAGE,
        M05A3_APP_THEME_PATH: M05A3_APP_THEME_PACKAGE,
        M05A3_SYSTEM_BARS_PATH: M05A3_SYSTEM_BARS_PACKAGE,
    }
    for relative_path, expected_package in package_expectations.items():
        declared_package = source_package(root / relative_path)
        if declared_package != expected_package:
            errors.append(
                "ARCH042 M-05A3 package differs: "
                f"{relative_path} expected {expected_package}, "
                f"found {declared_package or 'missing'}"
            )

    design_code = source_code_mask(
        design_theme_path.read_text(encoding="utf-8")
    )
    typography_path = root / M05A3_TYPOGRAPHY_PATH
    typography_code = source_code_mask(
        typography_path.read_text(encoding="utf-8")
    )
    app_theme_path = root / M05A3_APP_THEME_PATH
    app_theme_code = source_code_mask(
        app_theme_path.read_text(encoding="utf-8")
    )
    system_bars_path = root / M05A3_SYSTEM_BARS_PATH
    system_bars_code = source_code_mask(
        system_bars_path.read_text(encoding="utf-8")
    )

    for relative_path, source_path in (
        (M05A3_DESIGN_THEME_PATH, design_theme_path),
        (M05A3_TYPOGRAPHY_PATH, typography_path),
        (M05A3_SYSTEM_BARS_PATH, system_bars_path),
    ):
        project_imports = [
            imported
            for _, imported in source_imports(source_path)
            if is_project_import(imported)
        ]
        if project_imports:
            errors.append(
                "ARCH042 M-05A3 pure owner project imports differ: "
                f"{relative_path} expected [], found {project_imports}"
            )

    for forbidden_token in (
        "UserPreferencesManager",
        "collectAsState",
        "LocalContext",
        "LocalView",
        "ComponentActivity",
        "Activity",
        "Window",
        "enableEdgeToEdge",
        "WindowInsets",
        "SystemBarStyle",
        "SideEffect",
        "rememberLayerBackdrop",
        "rememberLiquidState",
        "LocalLiquidGlassBackdrop",
        "LocalWaterGlassState",
        "com.ai.assistance.operit",
    ):
        if forbidden_token in design_code:
            errors.append(
                "ARCH042 M-05A3 pure root theme owns forbidden dependency "
                f"or state: {forbidden_token}"
            )
    required_design_counts = {
        "fun KiyoriTheme(": 1,
        "colorScheme: ColorScheme": 1,
        "modifier: Modifier = Modifier": 1,
        "typography: Typography = KiyoriTypography": 1,
        "MaterialTheme(": 1,
        "colorScheme = colorScheme": 1,
        "typography = typography": 1,
        ".fillMaxSize()": 1,
        ".background(colorScheme.background)": 1,
        ".then(modifier)": 1,
        "content()": 1,
    }
    for token, expected_count in required_design_counts.items():
        actual_count = design_code.count(token)
        if actual_count != expected_count:
            errors.append(
                "ARCH042 M-05A3 pure root theme contract differs: "
                f"{token} expected {expected_count}, found {actual_count}"
            )

    for forbidden_token in (
        "Context",
        "Uri",
        "File",
        "AppLogger",
        "UserPreferencesManager",
        "Flow",
        "collectAsState",
        "Activity",
        "Window",
        "SystemBar",
        "com.ai.assistance.operit",
    ):
        if forbidden_token in typography_code:
            errors.append(
                "ARCH042 M-05A3 Typography owns forbidden dependency "
                f"or state: {forbidden_token}"
            )
    required_typography_counts = {
        "val KiyoriTypography": 1,
        "letterSpacing = 0.sp": 15,
        "fun applyFontFamilyToTypography(": 1,
        "if (fontFamily == null)": 1,
        "return baseTypography": 1,
    }
    for token, expected_count in required_typography_counts.items():
        actual_count = typography_code.count(token)
        if actual_count != expected_count:
            errors.append(
                "ARCH042 M-05A3 Typography contract differs: "
                f"{token} expected {expected_count}, found {actual_count}"
            )

    actual_app_host_project_imports = Counter(
        imported
        for _, imported in source_imports(app_theme_path)
        if is_project_import(imported)
    )
    expected_app_host_project_imports = Counter(
        M05A3_EXPECTED_APP_HOST_PROJECT_IMPORTS
    )
    if actual_app_host_project_imports != expected_app_host_project_imports:
        errors.append(
            "ARCH042 M-05A3 app theme host project imports differ: "
            f"expected {sorted(expected_app_host_project_imports.elements())}, "
            f"found {sorted(actual_app_host_project_imports.elements())}"
        )
    required_app_host_counts = {
        "fun KiyoriTheme(content: @Composable () -> Unit)": 1,
        "UserPreferencesManager.getInstance(context)": 1,
        "preferencesManager.useSystemTheme.collectAsState(initial = false)": 1,
        "preferencesManager.themeMode.collectAsState(": 1,
        "initial = UserPreferencesManager.THEME_MODE_LIGHT": 1,
        "preferencesManager.statusBarHidden.collectAsState(initial = false)": 1,
        "preferencesManager.useCustomFont.collectAsState(initial = false)": 1,
        "preferencesManager.fontType.collectAsState(": 1,
        "initial = UserPreferencesManager.FONT_TYPE_SYSTEM": 1,
        "preferencesManager.systemFontName.collectAsState(": 1,
        "initial = UserPreferencesManager.SYSTEM_FONT_DEFAULT": 1,
        "preferencesManager.customFontPath.collectAsState(initial = null)": 1,
        "preferencesManager.fontScale.collectAsState(initial = 1.0f)": 1,
        "remember(useCustomFont, fontType, systemFontName, customFontPath, fontScale)": 1,
        "createCustomTypography(": 1,
        "val systemDarkTheme = isSystemInDarkTheme()": 1,
        "if (useSystemTheme)": 1,
        "themeMode == UserPreferencesManager.THEME_MODE_DARK": 1,
        "resolveKiyoriColorScheme(darkTheme)": 1,
        "KiyoriApplicationSystemBars(": 1,
        "rememberLayerBackdrop()": 1,
        "if (isWaterGlassSupported()) rememberLiquidState() else null": 1,
        "LocalLiquidGlassBackdrop provides liquidGlassBackdrop": 1,
        "LocalWaterGlassState provides waterGlassState": 1,
        ".layerBackdrop(liquidGlassBackdrop)": 1,
        "Modifier.liquefiable(waterGlassState)": 1,
        "com.kiyori.design.theme.KiyoriTheme(": 1,
    }
    for token, expected_count in required_app_host_counts.items():
        actual_count = app_theme_code.count(token)
        if actual_count != expected_count:
            errors.append(
                "ARCH042 M-05A3 app theme host contract differs: "
                f"{token} expected {expected_count}, found {actual_count}"
            )
    for forbidden_token in (
        "enableEdgeToEdge",
        "SystemBarStyle",
        "WindowInsetsCompat",
        "WindowInsetsControllerCompat",
        "isNavigationBarContrastEnforced",
        "Color(0x",
        "KiyoriLightColorScheme",
        "KiyoriDarkColorScheme",
    ):
        if forbidden_token in app_theme_code:
            errors.append(
                "ARCH042 M-05A3 app theme host absorbed design or "
                f"system-bar responsibility: {forbidden_token}"
            )

    for forbidden_token in (
        "UserPreferencesManager",
        "collectAsState",
        "remember(",
        "MaterialTheme",
        "LocalLiquidGlassBackdrop",
        "LocalWaterGlassState",
        "PlayerActivity",
        "com.ai.assistance.operit",
    ):
        if forbidden_token in system_bars_code:
            errors.append(
                "ARCH042 M-05A3 system-bar owner owns forbidden dependency "
                f"or state: {forbidden_token}"
            )
    required_system_bar_counts = {
        "fun KiyoriApplicationSystemBars(": 1,
        "darkTheme: Boolean": 1,
        "navigationBarColor: Color": 1,
        "statusBarHidden: Boolean": 1,
        "if (!view.isInEditMode)": 1,
        "SideEffect {": 1,
        "view.context as ComponentActivity": 1,
        "android.graphics.Color.TRANSPARENT": 1,
        "SystemBarStyle.dark(transparentBarColor)": 1,
        "SystemBarStyle.light(transparentBarColor, transparentBarColor)": 1,
        "navigationBarColor.toArgb()": 1,
        "activity.enableEdgeToEdge(statusBarStyle, navigationBarStyle)": 1,
        "WindowCompat.getInsetsController(window, window.decorView)": 1,
        "insetsController.hide(WindowInsetsCompat.Type.statusBars())": 1,
        "WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE": 1,
        "insetsController.show(WindowInsetsCompat.Type.statusBars())": 1,
        "window.isNavigationBarContrastEnforced = true": 1,
    }
    for token, expected_count in required_system_bar_counts.items():
        actual_count = system_bars_code.count(token)
        if actual_count != expected_count:
            errors.append(
                "ARCH042 M-05A3 Application system-bar contract differs: "
                f"{token} expected {expected_count}, found {actual_count}"
            )

    old_theme_path = root / M05A3_OLD_THEME_PATH
    if old_theme_path.exists():
        errors.append(
            "ARCH042 M-05A3 old root theme owner remains: "
            f"{M05A3_OLD_THEME_PATH}"
        )

    main_source_root = root / "app/src/main/java"
    source_paths = sorted(
        path
        for path in main_source_root.rglob("*")
        if path.is_file() and path.suffix in MANAGED_SOURCE_SUFFIXES
    )
    declaration_expectations = {
        "KiyoriTheme": {
            M05A3_DESIGN_THEME_PATH,
            M05A3_APP_THEME_PATH,
        },
        "KiyoriTypography": {M05A3_TYPOGRAPHY_PATH},
        "applyFontFamilyToTypography": {M05A3_TYPOGRAPHY_PATH},
        "KiyoriApplicationSystemBars": {M05A3_SYSTEM_BARS_PATH},
    }
    declaration_patterns = {
        "KiyoriTheme": r"\bfun\s+KiyoriTheme\s*\(",
        "KiyoriTypography": r"\bval\s+KiyoriTypography\b",
        "applyFontFamilyToTypography": (
            r"\bfun\s+applyFontFamilyToTypography\s*\("
        ),
        "KiyoriApplicationSystemBars": (
            r"\bfun\s+KiyoriApplicationSystemBars\s*\("
        ),
    }
    for symbol, expected_owners in declaration_expectations.items():
        actual_owners = {
            path.relative_to(root).as_posix()
            for path in source_paths
            if re.search(
                declaration_patterns[symbol],
                source_code_mask(path.read_text(encoding="utf-8")),
            )
        }
        if actual_owners != expected_owners:
            errors.append(
                f"ARCH042 M-05A3 declaration owners differ for {symbol}: "
                f"expected {sorted(expected_owners)}, found {sorted(actual_owners)}"
            )

    old_symbol_sites: list[str] = []
    for source_path in source_paths:
        relative_path = source_path.relative_to(root).as_posix()
        code = source_code_mask(source_path.read_text(encoding="utf-8"))
        if re.search(r"\bfun\s+OperitTheme\s*\(", code):
            old_symbol_sites.append(relative_path)
        if re.search(r"\bval\s+Typography\b", code):
            old_symbol_sites.append(f"{relative_path}:Typography")
        if (
            relative_path != M05A3_TYPOGRAPHY_PATH
            and re.search(r"\bfun\s+applyFontFamilyToTypography\s*\(", code)
        ):
            old_symbol_sites.append(
                f"{relative_path}:applyFontFamilyToTypography"
            )
    if old_symbol_sites:
        errors.append(
            "ARCH042 M-05A3 old or duplicate theme/Typography owner remains: "
            + ", ".join(sorted(old_symbol_sites))
        )

    type_path = root / M05A3_TYPE_PATH
    expected_type_project_imports = Counter(
        (
            "com.ai.assistance.operit.util.AppLogger",
            "com.ai.assistance.operit.data.preferences.UserPreferencesManager",
            "com.kiyori.design.theme.KiyoriTypography",
            "com.kiyori.design.theme.applyFontFamilyToTypography",
        )
    )
    actual_type_project_imports = Counter(
        imported
        for _, imported in source_imports(type_path)
        if is_project_import(imported)
    )
    if actual_type_project_imports != expected_type_project_imports:
        errors.append(
            "ARCH042 M-05A3 configured Typography adapter imports differ: "
            f"expected {sorted(expected_type_project_imports.elements())}, "
            f"found {sorted(actual_type_project_imports.elements())}"
        )
    type_code = source_code_mask(type_path.read_text(encoding="utf-8"))
    for token in (
        "fun getSystemFontFamily(",
        "fun loadCustomFontFamily(",
        "fun resolveConfiguredFontFamily(",
        "fun createCustomTypography(",
        "applyFontFamilyToTypography(KiyoriTypography, fontFamily)",
        "return KiyoriTypography",
    ):
        if type_code.count(token) != 1:
            errors.append(
                "ARCH042 M-05A3 configured Typography adapter contract "
                f"differs: {token}"
            )

    consumer_snapshot_path = (
        architecture_root / M05A3_CONSUMER_IMPORT_SNAPSHOT
    )
    expected_consumer_imports: Counter[tuple[str, str]] = Counter()
    if not consumer_snapshot_path.is_file():
        errors.append(
            "ARCH042 M-05A3 consumer import snapshot missing: "
            f"config/architecture/{M05A3_CONSUMER_IMPORT_SNAPSHOT}"
        )
    else:
        parsed_entries: list[tuple[str, str]] = []
        for entry in read_snapshot(consumer_snapshot_path):
            fields = entry.split("\t")
            if len(fields) != 2:
                raise ValueError(
                    "invalid M-05A3 consumer import snapshot entry"
                )
            relative_path, imported = fields
            if (
                not relative_path.startswith("app/src/main/")
                or imported
                not in {
                    "com.kiyori.app.theme.KiyoriTheme",
                    "com.kiyori.design.theme.KiyoriTypography",
                    "com.kiyori.design.theme.applyFontFamilyToTypography",
                }
            ):
                raise ValueError(
                    "invalid M-05A3 consumer import snapshot entry"
                )
            parsed_entries.append((relative_path, imported))
        expected_consumer_imports = Counter(parsed_entries)
        if (
            len(parsed_entries) != M05A3_CONSUMER_IMPORT_COUNT
            or any(count != 1 for count in expected_consumer_imports.values())
        ):
            errors.append(
                "ARCH042 M-05A3 consumer import snapshot count differs: "
                f"expected {M05A3_CONSUMER_IMPORT_COUNT}, "
                f"found {len(parsed_entries)}"
            )

    actual_consumer_imports: Counter[tuple[str, str]] = Counter()
    old_imports: list[str] = []
    for source_path in source_paths:
        relative_path = source_path.relative_to(root).as_posix()
        for _, imported in source_imports(source_path):
            if (
                relative_path != M05A3_TYPE_PATH
                and imported in {
                "com.kiyori.app.theme.KiyoriTheme",
                "com.kiyori.design.theme.KiyoriTypography",
                "com.kiyori.design.theme.applyFontFamilyToTypography",
                }
            ):
                actual_consumer_imports[(relative_path, imported)] += 1
            if imported in {
                "com.ai.assistance.operit.ui.theme.OperitTheme",
                "com.ai.assistance.operit.ui.theme.Typography",
                "com.ai.assistance.operit.ui.theme.applyFontFamilyToTypography",
            }:
                old_imports.append(f"{relative_path} -> {imported}")
    if actual_consumer_imports != expected_consumer_imports:
        errors.append(
            "ARCH042 M-05A3 consumer imports differ: "
            f"expected {sorted(expected_consumer_imports.elements())}, "
            f"found {sorted(actual_consumer_imports.elements())}"
        )
    if old_imports:
        errors.append(
            "ARCH042 M-05A3 old theme/Typography imports remain: "
            + ", ".join(sorted(old_imports))
        )

    for relative_path in (
        M04_MAIN_ACTIVITY_PATH,
        M05A3_WIDGET_THEME_HOST_PATH,
    ):
        source_path = root / relative_path
        imports = Counter(
            imported for _, imported in source_imports(source_path)
        )
        code = source_code_mask(source_path.read_text(encoding="utf-8"))
        if imports["com.kiyori.app.theme.KiyoriTheme"] != 1:
            errors.append(
                "ARCH042 M-05A3 production host import count differs: "
                f"{relative_path}"
            )
        if code.count("KiyoriTheme {") != 1:
            errors.append(
                "ARCH042 M-05A3 production host call count differs: "
                f"{relative_path}"
            )
        if "OperitTheme" in code:
            errors.append(
                "ARCH042 M-05A3 production host still references OperitTheme: "
                f"{relative_path}"
            )

    m04e_snapshot = architecture_root / M04E_MAIN_ACTIVITY_IMPORT_SNAPSHOT
    if not m04e_snapshot.is_file():
        errors.append(
            "ARCH042 M-05A3 ARCH039 MainActivity snapshot missing: "
            f"config/architecture/{M04E_MAIN_ACTIVITY_IMPORT_SNAPSHOT}"
        )
    else:
        entries = read_snapshot(m04e_snapshot)
        if (
            entries.count("com.kiyori.app.theme.KiyoriTheme") != 1
            or "com.ai.assistance.operit.ui.theme.OperitTheme" in entries
        ):
            errors.append(
                "ARCH042 M-05A3 ARCH039 MainActivity theme import differs"
            )

    style_declarations = 0
    old_style_declarations = 0
    for relative_path in M05A3_THEME_RESOURCE_PATHS:
        resource_root = ET.parse(root / relative_path).getroot()
        style_names = [
            node.get("name")
            for node in resource_root.findall("style")
        ]
        style_declarations += style_names.count(M05A3_NEW_STYLE)
        old_style_declarations += style_names.count(M05A3_OLD_STYLE)
        if style_names.count(M05A3_NEW_STYLE) != 1:
            errors.append(
                "ARCH042 M-05A3 theme resource declaration differs: "
                f"{relative_path}"
            )
    if (
        style_declarations != M05A3_STYLE_DECLARATION_COUNT
        or old_style_declarations != 0
    ):
        errors.append(
            "ARCH042 M-05A3 theme style declaration totals differ: "
            f"new={style_declarations}, old={old_style_declarations}"
        )

    manifest_path = root / "app/src/main/AndroidManifest.xml"
    manifest_root = ET.parse(manifest_path).getroot()
    new_style_reference = f"@style/{M05A3_NEW_STYLE}"
    old_style_reference = f"@style/{M05A3_OLD_STYLE}"
    manifest_values = [
        value
        for node in manifest_root.iter()
        for value in node.attrib.values()
    ]
    new_style_count = manifest_values.count(new_style_reference)
    old_style_count = manifest_values.count(old_style_reference)
    if (
        new_style_count != M05A3_MANIFEST_STYLE_REFERENCE_COUNT
        or old_style_count != 0
    ):
        errors.append(
            "ARCH042 M-05A3 Manifest theme references differ: "
            f"new={new_style_count}, old={old_style_count}"
        )
    manifest_hash_snapshot = (
        architecture_root / "manifest-structure-hashes.txt"
    )
    if not manifest_hash_snapshot.is_file():
        errors.append(
            "ARCH042 M-05A3 Manifest semantic hash snapshot missing"
        )
    else:
        expected_manifest_hashes = read_manifest_hash_snapshot(
            manifest_hash_snapshot
        )
        actual_manifest_hash = manifest_semantic_hash(manifest_path)
        if expected_manifest_hashes.get("m03") != actual_manifest_hash:
            errors.append(
                "ARCH042 M-05A3 m03 Manifest semantic hash differs: "
                f"expected {expected_manifest_hashes.get('m03')}, "
                f"found {actual_manifest_hash}"
            )

    player_path = root / M05A3_PLAYER_ACTIVITY_PATH
    player_normalized = player_path.read_bytes().replace(b"\r\n", b"\n")
    player_hash = hashlib.sha256(player_normalized).hexdigest().upper()
    if player_hash != M05A3_PLAYER_BASELINE_HASH:
        errors.append(
            "ARCH042 M-05A3 PlayerActivity changed: "
            f"expected {M05A3_PLAYER_BASELINE_HASH}, found {player_hash}"
        )
    player_code = source_code_mask(
        player_path.read_text(encoding="utf-8")
    )
    for token in (
        "WindowCompat.setDecorFitsSystemWindows(window, false)",
        "WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE",
        "hide(WindowInsetsCompat.Type.systemBars())",
    ):
        if player_code.count(token) != 1:
            errors.append(
                "ARCH042 M-05A3 Player fullscreen system-bar contract "
                f"differs: {token}"
            )

    for relative_path, local_token, support_token in (
        (
            M05A3_LIQUID_GLASS_PATH,
            "val LocalLiquidGlassBackdrop",
            "fun isLiquidGlassSupported()",
        ),
        (
            M05A3_WATER_GLASS_PATH,
            "val LocalWaterGlassState",
            "fun isWaterGlassSupported()",
        ),
    ):
        code = source_code_mask(
            (root / relative_path).read_text(encoding="utf-8")
        )
        if code.count(local_token) != 1 or code.count(support_token) != 1:
            errors.append(
                "ARCH042 M-05A3 Glass owner contract differs: "
                f"{relative_path}"
            )

    payload = tomllib.loads(ownership_path.read_text(encoding="utf-8"))
    records = payload.get("ownership", [])
    exceptions = payload.get("exception", [])
    if not isinstance(records, list) or not isinstance(exceptions, list):
        errors.append(
            "ARCH042 M-05A3 ownership or exception entries must be arrays"
        )
    else:
        records_by_id = {
            str(record.get("id")): record
            for record in records
            if isinstance(record, dict)
        }
        platform_record = records_by_id.get("kiyori-platform")
        expected_platform_fields = {
            "path": "app/src/main/java/com/kiyori/platform/**",
            "owner": "kiyori-platform",
            "sync_zone": "C",
            "phase": "m05a3",
            "allowed_import_roots": [
                "com.kiyori.capability",
                "com.kiyori.platform",
            ],
        }
        if not isinstance(platform_record, dict):
            errors.append(
                "ARCH042 M-05A3 active kiyori-platform ownership missing"
            )
        else:
            for field, expected_value in expected_platform_fields.items():
                if platform_record.get(field) != expected_value:
                    errors.append(
                        "ARCH042 M-05A3 kiyori-platform ownership differs: "
                        f"{field}"
                    )
            if platform_record.get("planned"):
                errors.append(
                    "ARCH042 M-05A3 kiyori-platform remains planned"
                )

        app_record = records_by_id.get("kiyori-app")
        if (
            not isinstance(app_record, dict)
            or "com.ai.assistance.operit"
            in app_record.get("allowed_import_roots", [])
        ):
            errors.append(
                "ARCH042 M-05A3 kiyori-app ownership was broadly relaxed"
            )
        widget_record = records_by_id.get("operit-widget")
        if not isinstance(widget_record, dict):
            errors.append(
                "ARCH042 M-05A3 operit-widget ownership missing"
            )
        else:
            if "com.kiyori.app" in widget_record.get(
                "allowed_import_roots", []
            ):
                errors.append(
                    "ARCH042 M-05A3 operit-widget gained broad app permission"
                )
            if "com.kiyori.app" not in widget_record.get(
                "forbidden_import_roots", []
            ):
                errors.append(
                    "ARCH042 M-05A3 operit-widget app prohibition missing"
                )

        expected_exceptions = {
            M05A3_APP_THEME_PATH: (
                M05A3_APP_THEME_EXCEPTION_RULE,
                M05A3_APP_THEME_EXCEPTION_REASON,
                M05A3_APP_THEME_EXCEPTION_EXPIRY,
                "kiyori-app",
            ),
            M05A3_WIDGET_THEME_HOST_PATH: (
                M05A3_WIDGET_EXCEPTION_RULE,
                M05A3_WIDGET_EXCEPTION_REASON,
                M05A3_WIDGET_EXCEPTION_EXPIRY,
                "compatibility",
            ),
        }
        for relative_path, expected_fields in expected_exceptions.items():
            rule, reason, expiry, owner = expected_fields
            matches = [
                record
                for record in exceptions
                if isinstance(record, dict)
                and record.get("rule") == rule
                and record.get("path") == relative_path
            ]
            if len(matches) != 1:
                errors.append(
                    "ARCH042 M-05A3 exact ownership exception count differs: "
                    f"{relative_path}"
                )
                continue
            record = matches[0]
            if (
                record.get("reason") != reason
                or record.get("expires_after") != expiry
                or record.get("owner") != owner
            ):
                errors.append(
                    "ARCH042 M-05A3 exact ownership exception differs: "
                    f"{relative_path}"
                )

    design_test_path = root / M05A1_DESIGN_TEST_PATH
    if not design_test_path.is_file():
        errors.append(
            "ARCH042 M-05A3 Kiyori design test missing: "
            f"{M05A1_DESIGN_TEST_PATH}"
        )
    else:
        design_test_code = source_code_mask(
            design_test_path.read_text(encoding="utf-8")
        )
        for token in (
            "all typography roles use neutral tracking",
            "KiyoriTypography.displayLarge",
            "KiyoriTypography.labelSmall",
            "assertEquals(0.sp, style.letterSpacing)",
        ):
            if token not in design_test_code:
                errors.append(
                    "ARCH042 M-05A3 Typography design assertion missing: "
                    f"{token}"
                )
    if (root / M05A1_OLD_THEME_TEST_PATH).exists():
        errors.append(
            "ARCH042 M-05A3 old root theme test remains: "
            f"{M05A1_OLD_THEME_TEST_PATH}"
        )

    architecture_test_path = root / M05A3_ARCHITECTURE_TEST_PATH
    if not architecture_test_path.is_file():
        errors.append(
            "ARCH042 M-05A3 architecture tests missing: "
            f"{M05A3_ARCHITECTURE_TEST_PATH}"
        )
    else:
        architecture_test_code = source_code_mask(
            architecture_test_path.read_text(encoding="utf-8")
        )
        for test_name in (
            "test_m05a3_root_theme_accepts_split_owners",
            "test_m05a3_root_theme_rejects_owner_style_or_player_drift",
        ):
            if test_name not in architecture_test_code:
                errors.append(
                    "ARCH042 M-05A3 architecture test missing: "
                    f"{test_name}"
                )


def check_m05b_platform_logging(
    root: Path,
    ownership_path: Path,
    errors: list[str],
) -> None:
    if not (root / M05A3_DESIGN_THEME_PATH).is_file():
        return

    logger_path = root / M05B_LOGGER_PATH
    if not logger_path.is_file():
        errors.append(
            "ARCH043 M-05B platform logger missing: "
            f"{M05B_LOGGER_PATH}"
        )
        return

    required_paths = {
        "log text formatter": M05B_FORMATTER_PATH,
        "legacy AppLogger facade": M05B_LEGACY_FACADE_PATH,
        "crash report store": M05B_CRASH_REPORT_STORE_PATH,
        "memory documents provider": M05B_MEMORY_PROVIDER_PATH,
        "Kiyori Application": M03_APPLICATION_PATH,
        "formatter contract test": M05B_FORMATTER_TEST_PATH,
    }
    for label, relative_path in required_paths.items():
        if not (root / relative_path).is_file():
            errors.append(
                f"ARCH043 M-05B {label} missing: {relative_path}"
            )

    missing_required_paths = [
        relative_path
        for relative_path in required_paths.values()
        if not (root / relative_path).is_file()
    ]
    if missing_required_paths:
        return

    architecture_root = root / "config/architecture"
    hash_snapshot_path = architecture_root / M05B_HASH_SNAPSHOT
    if not hash_snapshot_path.is_file():
        errors.append(
            "ARCH043 M-05B logging hash snapshot missing: "
            f"config/architecture/{M05B_HASH_SNAPSHOT}"
        )
    else:
        hash_entries = read_hash_snapshot(hash_snapshot_path)
        actual_targets = {relative_path for _, relative_path in hash_entries}
        if actual_targets != set(M05B_HASHED_PATHS):
            errors.append(
                "ARCH043 M-05B logging hash targets differ: "
                f"expected {sorted(M05B_HASHED_PATHS)}, "
                f"found {sorted(actual_targets)}"
            )
        for expected_hash, relative_path in hash_entries:
            source_path = root / relative_path
            if not source_path.is_file():
                errors.append(
                    "ARCH043 M-05B hashed source missing: "
                    f"{relative_path}"
                )
                continue
            normalized = source_path.read_bytes().replace(b"\r\n", b"\n")
            actual_hash = hashlib.sha256(normalized).hexdigest().upper()
            if actual_hash != expected_hash:
                errors.append(
                    "ARCH043 M-05B logging source changed: "
                    f"{relative_path} expected {expected_hash}, "
                    f"found {actual_hash}"
                )

    formatter_path = root / M05B_FORMATTER_PATH
    facade_path = root / M05B_LEGACY_FACADE_PATH
    crash_store_path = root / M05B_CRASH_REPORT_STORE_PATH
    provider_path = root / M05B_MEMORY_PROVIDER_PATH
    application_path = root / M03_APPLICATION_PATH

    expected_packages = {
        M05B_LOGGER_PATH: M05B_LOGGER_PACKAGE,
        M05B_FORMATTER_PATH: M05B_LOGGER_PACKAGE,
        M05B_LEGACY_FACADE_PATH: M05B_LEGACY_FACADE_PACKAGE,
    }
    for relative_path, expected_package in expected_packages.items():
        actual_package = source_package(root / relative_path)
        if actual_package != expected_package:
            errors.append(
                "ARCH043 M-05B logging package differs: "
                f"{relative_path} expected {expected_package}, "
                f"found {actual_package or 'missing'}"
            )

    expected_logger_imports = Counter(
        (
            "com.kiyori.platform.android.ApplicationContextAccess",
            "com.kiyori.platform.lifecycle.ApplicationStartupTime",
        )
    )
    actual_logger_imports = Counter(
        imported
        for _, imported in source_imports(logger_path)
        if is_project_import(imported)
    )
    if actual_logger_imports != expected_logger_imports:
        errors.append(
            "ARCH043 M-05B platform logger project imports differ: "
            f"expected {sorted(expected_logger_imports.elements())}, "
            f"found {sorted(actual_logger_imports.elements())}"
        )

    actual_formatter_imports = Counter(
        imported
        for _, imported in source_imports(formatter_path)
        if is_project_import(imported)
    )
    if actual_formatter_imports:
        errors.append(
            "ARCH043 M-05B log formatter owns project dependency: "
            f"{sorted(actual_formatter_imports.elements())}"
        )

    actual_facade_imports = Counter(
        imported
        for _, imported in source_imports(facade_path)
        if is_project_import(imported)
    )
    m05e_active = (root / M05E_KIYORI_PATHS_PATH).is_file()
    expected_facade_imports = Counter((M05B_NEW_LOGGER_IMPORT,))
    if m05e_active:
        expected_facade_imports[M05E_KIYORI_PATHS_IMPORT] += 1
    if actual_facade_imports != expected_facade_imports:
        errors.append(
            "ARCH043 M-05B legacy facade project imports differ: "
            f"expected {sorted(expected_facade_imports.elements())}, "
            f"found {sorted(actual_facade_imports.elements())}"
        )

    logger_text = logger_path.read_text(encoding="utf-8")
    logger_code = source_code_mask(logger_text)
    required_logger_tokens = (
        "object KiyoriLogger",
        "const val VERBOSE: Int = Log.VERBOSE",
        "const val DEBUG: Int = Log.DEBUG",
        "const val INFO: Int = Log.INFO",
        "const val WARN: Int = Log.WARN",
        "const val ERROR: Int = Log.ERROR",
        "const val ASSERT: Int = Log.ASSERT",
        'private const val LOG_DIR_NAME = "logs"',
        'private const val LOG_FILE_NAME = "operit.log"',
        'private const val PACKAGE_LOG_DIR_NAME = "packageLogs"',
        'private const val TOOLPKG_LOG_TAG = "ToolPkg"',
        "private const val MAX_LOG_MESSAGE_CHARS = 12_000",
        "private const val MAX_LOG_THROWABLE_CHARS = 24_000",
        'SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)',
        'SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)',
        "var enableFileLogging: Boolean = true",
        "private var logFile: File? = null",
        "private var packageLogFile: File? = null",
        "private var boundFilesDir: File? = null",
        "private var packageLogRootProvider: (() -> File)? = null",
        "Executors.newSingleThreadExecutor",
        'Thread(runnable, "OperitAppLogger")',
        "fun bindContext(",
        "context: Context",
        "context.applicationContext.filesDir",
        "packageLogRootProvider: () -> File",
        "ApplicationContextAccess.current",
        "ApplicationStartupTime.epochMillis",
        "System.currentTimeMillis()",
        "File(filesDir, LOG_DIR_NAME)",
        "File(rootProvider(), PACKAGE_LOG_DIR_NAME)",
        "private fun resolveFilesDir(): File?",
        "fileLogExecutor.execute",
        "writeToFileSync(priority, tag, msg, tr)",
        "FileWriter(file, true).use",
        "writeToPackageLogIfNeeded(",
        "KiyoriLogTextFormatter.format",
        "KiyoriLogTextFormatter.truncateText",
        "fun getLogFile(): File? = resolveLogFile()",
        "fun resetLogFile()",
    )
    for token in required_logger_tokens:
        if token not in logger_text:
            errors.append(
                "ARCH043 M-05B platform logger contract differs: "
                f"{token}"
            )
    for token in (
        "com.ai.assistance.operit",
        "OperitPaths",
        "ActivityLifecycleManager",
        "UserPreferencesManager",
        "DataStore",
        "SharedPreferences",
        "ViewModel",
        "private var boundContext",
    ):
        if token in logger_code:
            errors.append(
                "ARCH043 M-05B platform logger owns forbidden dependency "
                f"or state: {token}"
            )

    facade_text = facade_path.read_text(encoding="utf-8")
    facade_code = source_code_mask(facade_text)
    facade_root_provider = (
        "KiyoriLogger.bindContext(context, KiyoriPaths::kiyoriRootDir)"
        if m05e_active
        else "KiyoriLogger.bindContext(context, OperitPaths::kiyoriRootDir)"
    )
    required_facade_tokens = (
        "object AppLogger",
        "const val VERBOSE: Int = KiyoriLogger.VERBOSE",
        "const val DEBUG: Int = KiyoriLogger.DEBUG",
        "const val INFO: Int = KiyoriLogger.INFO",
        "const val WARN: Int = KiyoriLogger.WARN",
        "const val ERROR: Int = KiyoriLogger.ERROR",
        "const val ASSERT: Int = KiyoriLogger.ASSERT",
        "get() = KiyoriLogger.enableFileLogging",
        "KiyoriLogger.enableFileLogging = value",
        facade_root_provider,
        "KiyoriLogger.getLogFile()",
        "KiyoriLogger.resetLogFile()",
        "KiyoriLogger.getStackTraceString(tr)",
    )
    for token in required_facade_tokens:
        if token not in facade_text:
            errors.append(
                "ARCH043 M-05B legacy facade contract differs: "
                f"{token}"
            )
    for method in (
        "v",
        "d",
        "i",
        "w",
        "e",
        "wtf",
        "isLoggable",
        "println",
    ):
        if f"KiyoriLogger.{method}(" not in facade_text:
            errors.append(
                "ARCH043 M-05B legacy facade delegate missing: "
                f"{method}"
            )
    for forbidden_token in (
        "@Volatile",
        "Executors.newSingleThreadExecutor",
        "FileWriter",
        "SimpleDateFormat",
        "Pattern.compile",
        "private var logFile",
        "private var packageLogFile",
        "private var boundContext",
        "private var boundFilesDir",
        "private var packageLogRootProvider",
        "writeToFileSync",
        "writeToPackageLogIfNeeded",
    ):
        if forbidden_token in facade_code:
            errors.append(
                "ARCH043 M-05B legacy facade owns logger implementation "
                f"state: {forbidden_token}"
            )

    if (root / M05B_OLD_FORMATTER_PATH).exists():
        errors.append(
            "ARCH043 M-05B old log formatter owner remains: "
            f"{M05B_OLD_FORMATTER_PATH}"
        )
    formatter_code = source_code_mask(
        formatter_path.read_text(encoding="utf-8")
    )
    for token in (
        "internal object KiyoriLogTextFormatter",
        "fun format(",
        "fun truncateText(",
        "MAX_FRAMES_PER_THROWABLE = 64",
        "MAX_CAUSE_DEPTH = 8",
        "Collections.newSetFromMap",
        "IdentityHashMap<Throwable, Boolean>()",
    ):
        if token not in formatter_code:
            errors.append(
                "ARCH043 M-05B log formatter contract differs: "
                f"{token}"
            )

    crash_imports = Counter(
        imported for _, imported in source_imports(crash_store_path)
    )
    if crash_imports[M05B_NEW_FORMATTER_IMPORT] != 1:
        errors.append(
            "ARCH043 M-05B crash store must import the platform "
            "log formatter exactly once"
        )
    if crash_imports[M05B_OLD_FORMATTER_IMPORT]:
        errors.append(
            "ARCH043 M-05B crash store still imports old log formatter"
        )

    consumer_snapshot_path = (
        architecture_root / M05B_KIYORI_CONSUMER_SNAPSHOT
    )
    expected_consumers: set[str] = set()
    if not consumer_snapshot_path.is_file():
        errors.append(
            "ARCH043 M-05B Kiyori logger consumer snapshot missing: "
            f"config/architecture/{M05B_KIYORI_CONSUMER_SNAPSHOT}"
        )
    else:
        consumer_entries = read_snapshot(consumer_snapshot_path)
        expected_consumers = set(consumer_entries)
        if (
            len(consumer_entries) != M05B_KIYORI_CONSUMER_COUNT
            or len(expected_consumers) != M05B_KIYORI_CONSUMER_COUNT
            or any(
                not relative_path.startswith(
                    "app/src/main/java/com/kiyori/app/"
                )
                and not relative_path.startswith(
                    "app/src/main/java/com/kiyori/integration/operit/"
                )
                and relative_path != M03_APPLICATION_PATH
                for relative_path in consumer_entries
            )
        ):
            errors.append(
                "ARCH043 M-05B Kiyori logger consumer snapshot differs: "
                f"expected {M05B_KIYORI_CONSUMER_COUNT} unique Kiyori paths, "
                f"found {len(consumer_entries)}"
            )

    kiyori_source_root = root / "app/src/main/java/com/kiyori"
    kiyori_sources = sorted(
        path
        for path in kiyori_source_root.rglob("*")
        if path.is_file() and path.suffix in MANAGED_SOURCE_SUFFIXES
    )
    actual_consumers = {
        path.relative_to(root).as_posix()
        for path in kiyori_sources
        if Counter(imported for _, imported in source_imports(path))[
            M05B_NEW_LOGGER_IMPORT
        ]
        == 1
    }
    if actual_consumers != expected_consumers:
        errors.append(
            "ARCH043 M-05B Kiyori logger consumers differ: "
            f"expected {sorted(expected_consumers)}, "
            f"found {sorted(actual_consumers)}"
        )
    stale_kiyori_consumers = [
        path.relative_to(root).as_posix()
        for path in kiyori_sources
        if any(
            imported == M05B_OLD_LOGGER_IMPORT
            for _, imported in source_imports(path)
        )
        or M05B_OLD_LOGGER_IMPORT
        in source_code_mask(path.read_text(encoding="utf-8"))
    ]
    if stale_kiyori_consumers:
        errors.append(
            "ARCH043 M-05B Kiyori source still uses legacy logger: "
            + ", ".join(stale_kiyori_consumers)
        )

    application_text = application_path.read_text(encoding="utf-8")
    application_root_provider = (
        "KiyoriLogger.bindContext(this, KiyoriPaths::kiyoriRootDir)"
        if m05e_active
        else "KiyoriLogger.bindContext(this, OperitPaths::kiyoriRootDir)"
    )
    if (
        application_text.count(application_root_provider)
        != 1
    ):
        errors.append(
            "ARCH043 M-05B Kiyori Application logger installation differs"
        )
    provider_text = provider_path.read_text(encoding="utf-8")
    provider_imports = Counter(
        imported for _, imported in source_imports(provider_path)
    )
    if (
        provider_imports[M05B_OLD_LOGGER_IMPORT] != 1
        or provider_text.count("AppLogger.bindContext(context)") != 1
    ):
        errors.append(
            "ARCH043 M-05B Provider legacy logger binding differs"
        )

    mock_static_sites: set[str] = set()
    test_root = root / "app/src/test/java"
    if test_root.is_dir():
        for path in test_root.rglob("*.kt"):
            code = source_code_mask(path.read_text(encoding="utf-8"))
            if "Mockito.mockStatic(AppLogger::class.java)" in code:
                mock_static_sites.add(path.relative_to(root).as_posix())
    expected_mock_static_sites = {
        "app/src/test/java/com/ai/assistance/operit/api/chat/llmprovider/"
        "ApiKeyProviderLogPrivacyTest.kt",
        "app/src/test/java/com/ai/assistance/operit/api/chat/llmprovider/"
        "LlmTransportDiagnosticsTest.kt",
        "app/src/test/java/com/ai/assistance/operit/core/tools/condition/"
        "ConditionEvaluatorTest.kt",
        "app/src/test/java/com/ai/assistance/operit/core/tools/condition/"
        "ConditionEvaluatorParseFailureTest.kt",
        "app/src/test/java/com/ai/assistance/operit/api/chat/llmprovider/"
        "OpenAIResponsesSubmissionFaultInjectionTest.kt",
    }
    if mock_static_sites != expected_mock_static_sites:
        errors.append(
            "ARCH043 M-05B legacy AppLogger static-mock compatibility "
            f"differs: expected {sorted(expected_mock_static_sites)}, "
            f"found {sorted(mock_static_sites)}"
        )

    payload = tomllib.loads(ownership_path.read_text(encoding="utf-8"))
    records = payload.get("ownership", [])
    exceptions = payload.get("exception", [])
    if not isinstance(records, list) or not isinstance(exceptions, list):
        errors.append(
            "ARCH043 M-05B ownership or exception entries must be arrays"
        )
    else:
        platform_records = [
            record
            for record in records
            if isinstance(record, dict)
            and record.get("id") == "kiyori-platform"
        ]
        if len(platform_records) != 1:
            errors.append(
                "ARCH043 M-05B kiyori-platform ownership count differs"
            )
        else:
            platform_record = platform_records[0]
            if (
                platform_record.get("path")
                != "app/src/main/java/com/kiyori/platform/**"
                or platform_record.get("owner") != "kiyori-platform"
                or platform_record.get("sync_zone") != "C"
                or platform_record.get("allowed_import_roots")
                != [
                    "com.kiyori.capability",
                    "com.kiyori.platform",
                ]
                or platform_record.get("planned")
            ):
                errors.append(
                    "ARCH043 M-05B kiyori-platform ownership differs"
                )
        stale_display_exceptions = [
            record
            for record in exceptions
            if isinstance(record, dict)
            and record.get("path") == M05B_DISPLAY_EXCEPTION_PATH
        ]
        if stale_display_exceptions:
            errors.append(
                "ARCH043 M-05B display logger ownership exception remains"
            )

    formatter_test_code = source_code_mask(
        (root / M05B_FORMATTER_TEST_PATH).read_text(encoding="utf-8")
    )
    for token in (
        "unchangedTextRemainsUnchanged",
        "longTextIsBoundedAndMarked",
        "throwableFormattingKeepsCauseAndBound",
    ):
        if token not in formatter_test_code:
            errors.append(
                "ARCH043 M-05B formatter contract assertion missing: "
                f"{token}"
            )

    architecture_test_path = root / M05B_ARCHITECTURE_TEST_PATH
    if not architecture_test_path.is_file():
        errors.append(
            "ARCH043 M-05B architecture tests missing: "
            f"{M05B_ARCHITECTURE_TEST_PATH}"
        )
    else:
        architecture_test_code = source_code_mask(
            architecture_test_path.read_text(encoding="utf-8")
        )
        for test_name in (
            "test_m05b_platform_logging_accepts_single_owner_and_facade",
            "test_m05b_platform_logging_rejects_state_or_compatibility_drift",
        ):
            if test_name not in architecture_test_code:
                errors.append(
                    "ARCH043 M-05B architecture test missing: "
                    f"{test_name}"
                )


def check_m05c_platform_lifecycle(
    root: Path,
    ownership_path: Path,
    errors: list[str],
) -> None:
    if not (root / M05B_LOGGER_PATH).is_file():
        return

    platform_path = root / M05C_PLATFORM_LIFECYCLE_PATH
    if not platform_path.is_file():
        errors.append(
            "ARCH044 M-05C platform lifecycle owner missing: "
            f"{M05C_PLATFORM_LIFECYCLE_PATH}"
        )
        return

    required_paths = {
        "Operit lifecycle integration": M05C_OPERIT_INTEGRATION_PATH,
        "legacy ActivityLifecycleManager facade": M05C_LEGACY_FACADE_PATH,
        "Kiyori Application": M03_APPLICATION_PATH,
        "lifecycle facts contract test": M05C_FACTS_TEST_PATH,
    }
    for label, relative_path in required_paths.items():
        if not (root / relative_path).is_file():
            errors.append(
                f"ARCH044 M-05C {label} missing: {relative_path}"
            )

    missing_required_paths = [
        relative_path
        for relative_path in required_paths.values()
        if not (root / relative_path).is_file()
    ]
    if missing_required_paths:
        return

    architecture_root = root / "config/architecture"
    hash_snapshot_path = architecture_root / M05C_HASH_SNAPSHOT
    if not hash_snapshot_path.is_file():
        errors.append(
            "ARCH044 M-05C lifecycle hash snapshot missing: "
            f"config/architecture/{M05C_HASH_SNAPSHOT}"
        )
    else:
        hash_entries = read_hash_snapshot(hash_snapshot_path)
        actual_targets = {relative_path for _, relative_path in hash_entries}
        if actual_targets != set(M05C_HASHED_PATHS):
            errors.append(
                "ARCH044 M-05C lifecycle hash targets differ: "
                f"expected {sorted(M05C_HASHED_PATHS)}, "
                f"found {sorted(actual_targets)}"
            )
        for expected_hash, relative_path in hash_entries:
            source_path = root / relative_path
            if not source_path.is_file():
                errors.append(
                    "ARCH044 M-05C hashed source missing: "
                    f"{relative_path}"
                )
                continue
            normalized = source_path.read_bytes().replace(b"\r\n", b"\n")
            actual_hash = hashlib.sha256(normalized).hexdigest().upper()
            if actual_hash != expected_hash:
                errors.append(
                    "ARCH044 M-05C lifecycle source changed: "
                    f"{relative_path} expected {expected_hash}, "
                    f"found {actual_hash}"
                )

    integration_path = root / M05C_OPERIT_INTEGRATION_PATH
    facade_path = root / M05C_LEGACY_FACADE_PATH
    application_path = root / M03_APPLICATION_PATH
    facts_test_path = root / M05C_FACTS_TEST_PATH

    expected_packages = {
        M05C_PLATFORM_LIFECYCLE_PATH: M05C_PLATFORM_PACKAGE,
        M05C_OPERIT_INTEGRATION_PATH: M05C_OPERIT_PACKAGE,
        M05C_LEGACY_FACADE_PATH: M05C_OPERIT_PACKAGE,
    }
    for relative_path, expected_package in expected_packages.items():
        actual_package = source_package(root / relative_path)
        if actual_package != expected_package:
            errors.append(
                "ARCH044 M-05C lifecycle package differs: "
                f"{relative_path} expected {expected_package}, "
                f"found {actual_package or 'missing'}"
            )

    platform_text = platform_path.read_text(encoding="utf-8")
    platform_code = source_code_mask(platform_text)
    platform_project_imports = Counter(
        imported
        for _, imported in source_imports(platform_path)
        if is_project_import(imported)
    )
    if platform_project_imports:
        errors.append(
            "ARCH044 M-05C platform lifecycle owns project dependency: "
            f"{sorted(platform_project_imports.elements())}"
        )

    required_platform_tokens = (
        "internal interface KiyoriActivityLifecycleObserver",
        "internal class KiyoriActivityLifecycleFacts",
        "object KiyoriActivityLifecycle : Application.ActivityLifecycleCallbacks",
        "private var currentActivity: WeakReference<Activity>? = null",
        "private var activityCount = 0",
        "private var startedActivityCount = 0",
        "private var isAppInForeground = false",
        "fun isAppInForeground(): Boolean = isAppInForeground",
        "private val facts = KiyoriActivityLifecycleFacts()",
        "private lateinit var observer: KiyoriActivityLifecycleObserver",
        "application.registerActivityLifecycleCallbacks(this)",
        "fun getCurrentActivity(): Activity? = facts.getCurrentActivity()",
        "fun isAppInForeground(): Boolean = facts.isAppInForeground()",
        "observer.onActivityCreated(activity, facts.onActivityCreated())",
        "observer.onActivityStarted(activity, facts.onActivityStarted())",
        "facts.onActivityResumed(activity)",
        "observer.onActivityResumed(activity)",
        "facts.onActivityPaused(activity)",
        "observer.onActivityPaused(activity)",
        "observer.onActivityStopped(activity, facts.onActivityStopped())",
        "observer.onActivityDestroyed(",
        "facts.onActivityDestroyed(activity)",
        "override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle)",
        "(startedActivityCount - 1).coerceAtLeast(0)",
        "currentActivity = WeakReference(activity)",
        "currentActivity?.clear()",
    )
    for token in required_platform_tokens:
        if token not in platform_text:
            errors.append(
                "ARCH044 M-05C platform lifecycle contract differs: "
                f"{token}"
            )
    for token in (
        "com.ai.assistance.operit",
        "ApiPreferences",
        "AppLogger",
        "AIForegroundService",
        "AppLifecycleHookPluginRegistry",
        "ExternalChatHttpAutoStarter",
        "PlayerCrashCoordinator",
        "VirtualDisplayOverlay",
        "ShowerController",
        "WindowManager",
        "CoroutineScope",
        "ApplicationContextAccess",
        "keepScreenOnPreferenceRequestCount",
        "keepScreenOnForcedRequestCount",
        "lastMicEnsureAtMs",
    ):
        if token in platform_code:
            errors.append(
                "ARCH044 M-05C platform lifecycle owns Operit integration "
                f"or non-fact state: {token}"
            )

    integration_text = integration_path.read_text(encoding="utf-8")
    integration_code = source_code_mask(integration_text)
    expected_integration_imports = Counter(
        (
            "com.ai.assistance.operit.api.chat.AIForegroundService",
            "com.ai.assistance.operit.core.tools.agent.ShowerController",
            "com.ai.assistance.operit.data.preferences.ApiPreferences",
            "com.ai.assistance.operit.integrations.http.ExternalChatHttpAutoStarter",
            "com.ai.assistance.operit.plugins.lifecycle.AppLifecycleEvent",
            "com.ai.assistance.operit.plugins.lifecycle.AppLifecycleHookParams",
            "com.ai.assistance.operit.plugins.lifecycle.AppLifecycleHookPluginRegistry",
            "com.ai.assistance.operit.ui.common.displays.VirtualDisplayOverlay",
            "com.ai.assistance.operit.util.AppLogger",
            "com.ai.assistance.operit.util.crash.PlayerCrashCoordinator",
            M05C_PLATFORM_IMPORT,
            M05C_OBSERVER_IMPORT,
        )
    )
    actual_integration_imports = Counter(
        imported
        for _, imported in source_imports(integration_path)
        if is_project_import(imported)
    )
    if actual_integration_imports != expected_integration_imports:
        errors.append(
            "ARCH044 M-05C Operit lifecycle integration imports differ: "
            f"expected {sorted(expected_integration_imports.elements())}, "
            f"found {sorted(actual_integration_imports.elements())}"
        )

    required_integration_tokens = (
        "internal object OperitActivityLifecycleIntegration : KiyoriActivityLifecycleObserver",
        "private lateinit var apiPreferences: ApiPreferences",
        "private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())",
        "private var keepScreenOnPreferenceRequestCount = 0",
        "private var keepScreenOnForcedRequestCount = 0",
        "private var lastMicEnsureAtMs: Long = 0L",
        "ApiPreferences.getInstance(application.applicationContext)",
        "apiPreferences.keepScreenOnFlow.first()",
        "KiyoriActivityLifecycle.getCurrentActivity()",
        "WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON",
        "AppLifecycleEvent.ACTIVITY_CREATE",
        "AppLifecycleEvent.ACTIVITY_START",
        "AppLifecycleEvent.ACTIVITY_RESUME",
        "AppLifecycleEvent.ACTIVITY_PAUSE",
        "AppLifecycleEvent.ACTIVITY_STOP",
        "AppLifecycleEvent.ACTIVITY_DESTROY",
        "AppLifecycleEvent.APPLICATION_FOREGROUND",
        "AppLifecycleEvent.APPLICATION_BACKGROUND",
        'reason = "application_foreground"',
        "PlayerCrashCoordinator.onActivityResumed(activity)",
        "now - lastMicEnsureAtMs >= 2500L",
        "AIForegroundService.ensureMicrophoneForeground(activity.applicationContext)",
        "VirtualDisplayOverlay.hideAll()",
        "ShowerController.shutdown()",
        "if (activeActivityCount <= 0)",
        "context = activity.applicationContext",
    )
    for token in required_integration_tokens:
        if token not in integration_text:
            errors.append(
                "ARCH044 M-05C Operit lifecycle integration contract "
                f"differs: {token}"
            )
    for token in (
        "registerActivityLifecycleCallbacks",
        "WeakReference<Activity>",
        "private var currentActivity",
        "private var activityCount",
        "private var startedActivityCount",
        "private var isAppInForeground",
        "private lateinit var appContext",
        "private var appContext",
    ):
        if token in integration_code:
            errors.append(
                "ARCH044 M-05C Operit lifecycle integration owns platform "
                f"fact or Context state: {token}"
            )

    facade_text = facade_path.read_text(encoding="utf-8")
    facade_code = source_code_mask(facade_text)
    facade_project_imports = Counter(
        imported
        for _, imported in source_imports(facade_path)
        if is_project_import(imported)
    )
    if facade_project_imports != Counter((M05C_PLATFORM_IMPORT,)):
        errors.append(
            "ARCH044 M-05C legacy lifecycle facade imports differ: "
            f"expected {[M05C_PLATFORM_IMPORT]}, "
            f"found {sorted(facade_project_imports.elements())}"
        )

    required_facade_tokens = (
        "object ActivityLifecycleManager : Application.ActivityLifecycleCallbacks",
        "OperitActivityLifecycleIntegration.initialize(application)",
        "KiyoriActivityLifecycle.initialize(",
        "observer = OperitActivityLifecycleIntegration",
        "fun getCurrentActivity(): Activity? =",
        "KiyoriActivityLifecycle.getCurrentActivity()",
        "fun isAppInForeground(): Boolean = KiyoriActivityLifecycle.isAppInForeground()",
        "fun checkAndApplyKeepScreenOn(enable: Boolean)",
        "OperitActivityLifecycleIntegration.checkAndApplyKeepScreenOn(enable)",
        "fun forceKeepScreenOn(enable: Boolean)",
        "OperitActivityLifecycleIntegration.forceKeepScreenOn(enable)",
        "KiyoriActivityLifecycle.onActivityCreated(activity, savedInstanceState)",
        "KiyoriActivityLifecycle.onActivityStarted(activity)",
        "KiyoriActivityLifecycle.onActivityResumed(activity)",
        "KiyoriActivityLifecycle.onActivityPaused(activity)",
        "KiyoriActivityLifecycle.onActivityStopped(activity)",
        "KiyoriActivityLifecycle.onActivitySaveInstanceState(activity, outState)",
        "KiyoriActivityLifecycle.onActivityDestroyed(activity)",
    )
    for token in required_facade_tokens:
        if token not in facade_text:
            errors.append(
                "ARCH044 M-05C legacy lifecycle facade contract differs: "
                f"{token}"
            )
    for token in (
        "registerActivityLifecycleCallbacks",
        "WeakReference<Activity>",
        "CoroutineScope",
        "ApiPreferences",
        "WindowManager",
        "AppLogger",
        "AIForegroundService",
        "AppLifecycleHookPluginRegistry",
        "ExternalChatHttpAutoStarter",
        "PlayerCrashCoordinator",
        "VirtualDisplayOverlay",
        "ShowerController",
        "private var currentActivity",
        "private var activityCount",
        "private var startedActivityCount",
        "private var isAppInForeground",
        "keepScreenOnPreferenceRequestCount",
        "keepScreenOnForcedRequestCount",
        "lastMicEnsureAtMs",
        "private lateinit var appContext",
    ):
        if token in facade_code:
            errors.append(
                "ARCH044 M-05C legacy lifecycle facade owns implementation "
                f"state or side effect: {token}"
            )

    registration_sites: list[str] = []
    source_root = root / "app/src/main/java"
    if source_root.is_dir():
        for path in source_root.rglob("*"):
            if (
                path.is_file()
                and path.suffix in MANAGED_SOURCE_SUFFIXES
                and "registerActivityLifecycleCallbacks("
                in source_code_mask(path.read_text(encoding="utf-8"))
            ):
                registration_sites.append(path.relative_to(root).as_posix())
    if registration_sites != [M05C_PLATFORM_LIFECYCLE_PATH]:
        errors.append(
            "ARCH044 M-05C lifecycle callback registration owner differs: "
            f"expected {[M05C_PLATFORM_LIFECYCLE_PATH]}, "
            f"found {registration_sites}"
        )

    consumer_snapshot_path = (
        architecture_root / M05C_LEGACY_CONSUMER_SNAPSHOT
    )
    expected_consumers: set[str] = set()
    if not consumer_snapshot_path.is_file():
        errors.append(
            "ARCH044 M-05C legacy lifecycle consumer snapshot missing: "
            f"config/architecture/{M05C_LEGACY_CONSUMER_SNAPSHOT}"
        )
    else:
        consumer_entries = read_snapshot(consumer_snapshot_path)
        expected_consumers = set(consumer_entries)
        if (
            len(consumer_entries) != M05C_LEGACY_CONSUMER_COUNT
            or len(expected_consumers) != M05C_LEGACY_CONSUMER_COUNT
            or expected_consumers != set(M05C_LEGACY_CONSUMER_PATHS)
        ):
            errors.append(
                "ARCH044 M-05C legacy lifecycle consumer snapshot differs: "
                f"expected {M05C_LEGACY_CONSUMER_COUNT} exact paths, "
                f"found {len(consumer_entries)}"
            )

    actual_consumers: set[str] = set()
    scan_roots = (root / "app/src/main/java", root / "examples")
    for scan_root in scan_roots:
        if not scan_root.is_dir():
            continue
        for path in scan_root.rglob("*"):
            if (
                path.is_file()
                and path.suffix in {".kt", ".java", ".md", ".js", ".ts"}
                and M05C_LEGACY_IMPORT
                in path.read_text(encoding="utf-8")
            ):
                actual_consumers.add(path.relative_to(root).as_posix())
    if actual_consumers != expected_consumers:
        errors.append(
            "ARCH044 M-05C legacy lifecycle consumers differ: "
            f"expected {sorted(expected_consumers)}, "
            f"found {sorted(actual_consumers)}"
        )

    application_imports = Counter(
        imported for _, imported in source_imports(application_path)
    )
    application_text = application_path.read_text(encoding="utf-8")
    if (
        application_imports[M05C_LEGACY_IMPORT] != 1
        or application_text.count(
            "ActivityLifecycleManager.initialize(this)"
        )
        != 1
        or application_imports[M05C_PLATFORM_IMPORT]
    ):
        errors.append(
            "ARCH044 M-05C Kiyori Application lifecycle bootstrap differs"
        )

    facts_test_code = source_code_mask(
        facts_test_path.read_text(encoding="utf-8")
    )
    for token in (
        "currentActivityTracksResumePauseAndDestroyIdentity",
        "foregroundTransitionsOnlyAtZeroOneBoundaries",
        "activityCountPreservesLastActivityCleanupBoundary",
        "platformRegistrationUsesThePlatformCallbackOwner",
    ):
        if token not in facts_test_code:
            errors.append(
                "ARCH044 M-05C lifecycle facts assertion missing: "
                f"{token}"
            )

    payload = tomllib.loads(ownership_path.read_text(encoding="utf-8"))
    records = payload.get("ownership", [])
    if not isinstance(records, list):
        errors.append(
            "ARCH044 M-05C ownership entries must be an array"
        )
    else:
        platform_records = [
            record
            for record in records
            if isinstance(record, dict)
            and record.get("id") == "kiyori-platform"
        ]
        if len(platform_records) != 1:
            errors.append(
                "ARCH044 M-05C kiyori-platform ownership count differs"
            )
        else:
            platform_record = platform_records[0]
            if (
                platform_record.get("path")
                != "app/src/main/java/com/kiyori/platform/**"
                or platform_record.get("owner") != "kiyori-platform"
                or platform_record.get("sync_zone") != "C"
                or platform_record.get("allowed_import_roots")
                != [
                    "com.kiyori.capability",
                    "com.kiyori.platform",
                ]
                or platform_record.get("planned")
            ):
                errors.append(
                    "ARCH044 M-05C kiyori-platform ownership differs"
                )

    architecture_test_path = root / M05C_ARCHITECTURE_TEST_PATH
    if not architecture_test_path.is_file():
        errors.append(
            "ARCH044 M-05C architecture tests missing: "
            f"{M05C_ARCHITECTURE_TEST_PATH}"
        )
    else:
        architecture_test_code = source_code_mask(
            architecture_test_path.read_text(encoding="utf-8")
        )
        for test_name in (
            "test_m05c_platform_lifecycle_accepts_fact_and_side_effect_split",
            "test_m05c_platform_lifecycle_rejects_state_or_compatibility_drift",
        ):
            if test_name not in architecture_test_code:
                errors.append(
                    "ARCH044 M-05C architecture test missing: "
                    f"{test_name}"
                )


def check_m05d_android_permission_capability(
    root: Path,
    ownership_path: Path,
    errors: list[str],
) -> None:
    if not (root / M05C_PLATFORM_LIFECYCLE_PATH).is_file():
        return

    platform_path = root / M05D_PLATFORM_PERMISSION_PATH
    if not platform_path.is_file():
        errors.append(
            "ARCH045 M-05D notification permission capability missing: "
            f"{M05D_PLATFORM_PERMISSION_PATH}"
        )
        return

    required_paths = {
        "Operit resource bridge": M05D_OPERIT_RESOURCE_BRIDGE_PATH,
        "MainActivity startup coordinator": M05D_COORDINATOR_PATH,
        "Android permission preferences": M05D_PREFERENCES_PATH,
        "platform permission contract test": M05D_PERMISSION_TEST_PATH,
        "MainActivity host": M04_MAIN_ACTIVITY_PATH,
        "Android Manifest": "app/src/main/AndroidManifest.xml",
    }
    for label, relative_path in required_paths.items():
        if not (root / relative_path).is_file():
            errors.append(
                f"ARCH045 M-05D {label} missing: {relative_path}"
            )
    if any(not (root / path).is_file() for path in required_paths.values()):
        return

    architecture_root = root / "config/architecture"
    hash_snapshot_path = architecture_root / M05D_HASH_SNAPSHOT
    if not hash_snapshot_path.is_file():
        errors.append(
            "ARCH045 M-05D permission hash snapshot missing: "
            f"config/architecture/{M05D_HASH_SNAPSHOT}"
        )
    else:
        hash_entries = read_hash_snapshot(hash_snapshot_path)
        actual_targets = {relative_path for _, relative_path in hash_entries}
        if actual_targets != set(M05D_HASHED_PATHS):
            errors.append(
                "ARCH045 M-05D permission hash targets differ: "
                f"expected {sorted(M05D_HASHED_PATHS)}, "
                f"found {sorted(actual_targets)}"
            )
        for expected_hash, relative_path in hash_entries:
            source_path = root / relative_path
            if not source_path.is_file():
                errors.append(
                    "ARCH045 M-05D hashed source missing: "
                    f"{relative_path}"
                )
                continue
            normalized = source_path.read_bytes().replace(b"\r\n", b"\n")
            actual_hash = hashlib.sha256(normalized).hexdigest().upper()
            if actual_hash != expected_hash:
                errors.append(
                    "ARCH045 M-05D permission source changed: "
                    f"{relative_path} expected {expected_hash}, "
                    f"found {actual_hash}"
                )

    expected_packages = {
        M05D_PLATFORM_PERMISSION_PATH: M05D_PLATFORM_PACKAGE,
        M05D_OPERIT_RESOURCE_BRIDGE_PATH: (
            M05D_OPERIT_RESOURCE_BRIDGE_PACKAGE
        ),
        M05D_COORDINATOR_PATH: (
            M04D_MAIN_NOTIFICATION_PERMISSION_COORDINATOR_PACKAGE
        ),
    }
    for relative_path, expected_package in expected_packages.items():
        actual_package = source_package(root / relative_path)
        if actual_package != expected_package:
            errors.append(
                "ARCH045 M-05D permission package differs: "
                f"{relative_path} expected {expected_package}, "
                f"found {actual_package or 'missing'}"
            )

    platform_imports = Counter(
        imported
        for _, imported in source_imports(platform_path)
        if is_project_import(imported)
    )
    if platform_imports:
        errors.append(
            "ARCH045 M-05D platform permission owns project dependency: "
            f"{sorted(platform_imports.elements())}"
        )

    resource_bridge_path = root / M05D_OPERIT_RESOURCE_BRIDGE_PATH
    resource_bridge_imports = Counter(
        imported
        for _, imported in source_imports(resource_bridge_path)
        if is_project_import(imported)
    )
    expected_bridge_imports = Counter(("com.ai.assistance.operit.R",))
    if resource_bridge_imports != expected_bridge_imports:
        errors.append(
            "ARCH045 M-05D Operit permission resource bridge imports differ: "
            f"expected {sorted(expected_bridge_imports.elements())}, "
            f"found {sorted(resource_bridge_imports.elements())}"
        )

    coordinator_path = root / M05D_COORDINATOR_PATH
    coordinator_imports = Counter(
        imported
        for _, imported in source_imports(coordinator_path)
        if is_project_import(imported)
    )
    expected_coordinator_imports = Counter(
        (
            M05D_OPERIT_RESOURCE_BRIDGE_IMPORT,
            M05B_NEW_LOGGER_IMPORT,
            M05D_PLATFORM_ACTION_IMPORT,
            M05D_PLATFORM_CAPABILITY_IMPORT,
        )
    )
    if coordinator_imports != expected_coordinator_imports:
        errors.append(
            "ARCH045 M-05D startup coordinator project imports differ: "
            f"expected {sorted(expected_coordinator_imports.elements())}, "
            f"found {sorted(coordinator_imports.elements())}"
        )

    main_source_root = root / "app/src/main/java"
    source_paths = (
        sorted(path for path in main_source_root.rglob("*.kt") if path.is_file())
        if main_source_root.is_dir()
        else []
    )
    symbol_owners = {
        "KiyoriNotificationPermissionAction": (
            r"\benum\s+class\s+KiyoriNotificationPermissionAction\b",
            M05D_PLATFORM_PERMISSION_PATH,
        ),
        "resolveKiyoriNotificationPermissionAction": (
            r"\bfun\s+resolveKiyoriNotificationPermissionAction\s*\(",
            M05D_PLATFORM_PERMISSION_PATH,
        ),
        "KiyoriNotificationPermissionCapability": (
            r"\bclass\s+KiyoriNotificationPermissionCapability\b",
            M05D_PLATFORM_PERMISSION_PATH,
        ),
        "OperitNotificationPermissionResources": (
            r"\bobject\s+OperitNotificationPermissionResources\b",
            M05D_OPERIT_RESOURCE_BRIDGE_PATH,
        ),
        "KiyoriMainNotificationPermissionCoordinator": (
            r"\bclass\s+KiyoriMainNotificationPermissionCoordinator\b",
            M05D_COORDINATOR_PATH,
        ),
    }
    for symbol, (pattern, expected_path) in symbol_owners.items():
        declaration_sites = [
            path.relative_to(root).as_posix()
            for path in source_paths
            if re.search(
                pattern,
                source_code_mask(path.read_text(encoding="utf-8")),
            )
        ]
        if declaration_sites != [expected_path]:
            errors.append(
                "ARCH045 M-05D permission symbol must have one owner: "
                f"{symbol} found {declaration_sites}"
            )

    platform_code = source_code_mask(
        platform_path.read_text(encoding="utf-8")
    )
    required_platform_call_counts = {
        r"\bregisterForActivityResult\s*\(": (
            "registerForActivityResult",
            1,
        ),
        r"\bActivityResultContracts\.RequestPermission\s*\(": (
            "RequestPermission",
            1,
        ),
        r"\bContextCompat\.checkSelfPermission\s*\(": (
            "ContextCompat.checkSelfPermission",
            1,
        ),
        r"\bshouldShowRequestPermissionRationale\s*\(": (
            "shouldShowRequestPermissionRationale",
            1,
        ),
        r"\bpermissionLauncher\.launch\s*\(\s*permission\s*\)": (
            "permissionLauncher.launch",
            1,
        ),
        r"\bonPermissionAction\s*\(\s*action\s*\)": (
            "onPermissionAction",
            1,
        ),
    }
    for pattern, (label, expected_count) in required_platform_call_counts.items():
        actual_count = len(re.findall(pattern, platform_code))
        if actual_count != expected_count:
            errors.append(
                "ARCH045 M-05D platform permission API count differs: "
                f"{label} expected {expected_count}, found {actual_count}"
            )
    required_platform_tokens = (
        "internal enum class KiyoriNotificationPermissionAction",
        "internal fun resolveKiyoriNotificationPermissionAction(",
        "internal class KiyoriNotificationPermissionCapability(",
        "Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU",
        "@RequiresApi(Build.VERSION_CODES.TIRAMISU)",
        "Manifest.permission.POST_NOTIFICATIONS",
        "PackageManager.PERMISSION_GRANTED",
        "KiyoriNotificationPermissionAction.NOT_REQUIRED",
        "KiyoriNotificationPermissionAction.ALREADY_GRANTED",
        "KiyoriNotificationPermissionAction.SHOW_RATIONALE_AND_REQUEST",
        "KiyoriNotificationPermissionAction.REQUEST",
    )
    for token in required_platform_tokens:
        if token not in platform_code:
            errors.append(
                "ARCH045 M-05D platform permission contract differs: "
                f"{token}"
            )
    for forbidden_token in (
        "com.ai.assistance.operit",
        "com.kiyori.app",
        "com.kiyori.integration",
        "KiyoriLogger",
        "Toast",
        "R.string",
        "AndroidPermissionPreferences",
        "DataStore",
        "SharedPreferences",
        "mutableStateOf",
        "MutableStateFlow",
        "private var isGranted",
        "private var shouldShowRationale",
        "Manifest.permission.CAMERA",
        "Manifest.permission.RECORD_AUDIO",
        "Manifest.permission.ACCESS_FINE_LOCATION",
        "WebSessionPermissionRequestCoordinator",
    ):
        if forbidden_token in platform_code:
            errors.append(
                "ARCH045 M-05D platform permission absorbed forbidden "
                f"dependency, state, or scope: {forbidden_token}"
            )

    bridge_code = source_code_mask(
        resource_bridge_path.read_text(encoding="utf-8")
    )
    for token in (
        "internal object OperitNotificationPermissionResources",
        "@StringRes",
        "val notificationPermissionDenied: Int =",
        "R.string.notification_permission_denied",
        "val notificationPermissionRationale: Int =",
        "R.string.notification_permission_rationale",
    ):
        if token not in bridge_code:
            errors.append(
                "ARCH045 M-05D Operit resource bridge contract differs: "
                f"{token}"
            )
    for token in (
        "Manifest.permission",
        "registerForActivityResult",
        "checkSelfPermission",
        "shouldShowRequestPermissionRationale",
        "Toast",
        "KiyoriLogger",
        "mutableStateOf",
        "MutableStateFlow",
        "DataStore",
        "SharedPreferences",
    ):
        if token in bridge_code:
            errors.append(
                "ARCH045 M-05D Operit resource bridge owns runtime "
                f"permission or state: {token}"
            )

    coordinator_code = source_code_mask(
        coordinator_path.read_text(encoding="utf-8")
    )
    required_coordinator_counts = {
        "KiyoriLogger.d": 6,
        "Toast.makeText": 2,
        "notificationPermissionCapability.checkAndRequest": 1,
        "activity.getString": 2,
        (
            "OperitNotificationPermissionResources."
            "notificationPermissionDenied"
        ): 1,
        (
            "OperitNotificationPermissionResources."
            "notificationPermissionRationale"
        ): 1,
    }
    for token, expected_count in required_coordinator_counts.items():
        actual_count = coordinator_code.count(token)
        if actual_count != expected_count:
            errors.append(
                "ARCH045 M-05D startup coordinator contract count differs: "
                f"{token} expected {expected_count}, found {actual_count}"
            )
    for token in (
        "KiyoriNotificationPermissionAction.NOT_REQUIRED",
        "KiyoriNotificationPermissionAction.ALREADY_GRANTED",
        "KiyoriNotificationPermissionAction.SHOW_RATIONALE_AND_REQUEST",
        "KiyoriNotificationPermissionAction.REQUEST",
        "KiyoriNotificationPermissionCapability(",
        "onPermissionResult = { isGranted ->",
        "notificationPermissionCapability.checkAndRequest { action ->",
        "fun checkAndRequest()",
    ):
        if token not in coordinator_code:
            errors.append(
                "ARCH045 M-05D startup coordinator projection differs: "
                f"{token}"
            )
    for token in (
        "com.ai.assistance.operit.R",
        "R.string",
        "Manifest.permission.POST_NOTIFICATIONS",
        "PackageManager.PERMISSION_GRANTED",
        "Build.VERSION",
        "ActivityResultContracts.RequestPermission",
        "ContextCompat.checkSelfPermission",
        "shouldShowRequestPermissionRationale",
        "permissionLauncher",
        "KiyoriMainNotificationPermissionAction",
        "resolveKiyoriMainNotificationPermissionAction",
        "AndroidPermissionPreferences",
        "mutableStateOf",
        "MutableStateFlow",
    ):
        if token in coordinator_code:
            errors.append(
                "ARCH045 M-05D startup coordinator still owns platform "
                f"permission, resource, or state: {token}"
            )

    main_activity_path = root / M04_MAIN_ACTIVITY_PATH
    main_imports = Counter(
        imported for _, imported in source_imports(main_activity_path)
    )
    expected_main_import = (
        f"{M04D_MAIN_NOTIFICATION_PERMISSION_COORDINATOR_PACKAGE}."
        "KiyoriMainNotificationPermissionCoordinator"
    )
    if main_imports[expected_main_import] != 1:
        errors.append(
            "ARCH045 M-05D MainActivity coordinator import count differs"
        )
    main_code = source_code_mask(
        main_activity_path.read_text(encoding="utf-8")
    )
    field_count = len(
        re.findall(
            r"\bprivate\s+val\s+notificationPermissionCoordinator\s*=\s*"
            r"KiyoriMainNotificationPermissionCoordinator\s*\(\s*this\s*\)",
            main_code,
        )
    )
    if field_count != 1:
        errors.append(
            "ARCH045 M-05D MainActivity early registration bridge differs"
        )
    if (
        main_code.count(
            "notificationPermissionCoordinator.checkAndRequest()"
        )
        != 1
    ):
        errors.append(
            "ARCH045 M-05D MainActivity startup permission call differs"
        )
    for token in (
        "Manifest.permission.POST_NOTIFICATIONS",
        "PackageManager.PERMISSION_GRANTED",
        "ActivityResultContracts.RequestPermission",
        "ContextCompat.checkSelfPermission",
        "shouldShowRequestPermissionRationale",
        "R.string.notification_permission_denied",
        "R.string.notification_permission_rationale",
        "OperitNotificationPermissionResources",
        "KiyoriNotificationPermissionCapability",
    ):
        if token in main_code:
            errors.append(
                "ARCH045 M-05D MainActivity absorbed permission "
                f"implementation or resource mapping: {token}"
            )

    direct_consumer_snapshot_path = (
        architecture_root / M05D_DIRECT_CONSUMER_SNAPSHOT
    )
    expected_direct_consumers: set[str] = set()
    if not direct_consumer_snapshot_path.is_file():
        errors.append(
            "ARCH045 M-05D direct permission consumer snapshot missing: "
            f"config/architecture/{M05D_DIRECT_CONSUMER_SNAPSHOT}"
        )
    else:
        consumer_entries = read_snapshot(direct_consumer_snapshot_path)
        expected_direct_consumers = set(consumer_entries)
        if (
            len(consumer_entries) != len(M05D_DIRECT_CONSUMER_PATHS)
            or expected_direct_consumers != set(M05D_DIRECT_CONSUMER_PATHS)
        ):
            errors.append(
                "ARCH045 M-05D direct permission consumer snapshot "
                f"differs: found {consumer_entries}"
            )
    actual_direct_consumers = {
        path.relative_to(root).as_posix()
        for path in source_paths
        if "Manifest.permission.POST_NOTIFICATIONS"
        in source_code_mask(path.read_text(encoding="utf-8"))
    }
    if actual_direct_consumers != expected_direct_consumers:
        errors.append(
            "ARCH045 M-05D direct notification permission consumers differ: "
            f"expected {sorted(expected_direct_consumers)}, "
            f"found {sorted(actual_direct_consumers)}"
        )

    manifest_text = (
        root / "app/src/main/AndroidManifest.xml"
    ).read_text(encoding="utf-8")
    if manifest_text.count("android.permission.POST_NOTIFICATIONS") != 1:
        errors.append(
            "ARCH045 M-05D Manifest POST_NOTIFICATIONS declaration differs"
        )

    if (root / M05D_OLD_PERMISSION_TEST_PATH).exists():
        errors.append(
            "ARCH045 M-05D old app permission policy test remains: "
            f"{M05D_OLD_PERMISSION_TEST_PATH}"
        )
    permission_test_code = source_code_mask(
        (root / M05D_PERMISSION_TEST_PATH).read_text(encoding="utf-8")
    )
    for token in (
        "class KiyoriNotificationPermissionCapabilityTest",
        "resolveKiyoriNotificationPermissionAction",
        "KiyoriNotificationPermissionAction.NOT_REQUIRED",
        "KiyoriNotificationPermissionAction.ALREADY_GRANTED",
        "KiyoriNotificationPermissionAction.SHOW_RATIONALE_AND_REQUEST",
        "KiyoriNotificationPermissionAction.REQUEST",
        "platforms below android thirteen do not request notification permission",
        "granted permission takes precedence over rationale",
        "denied permission with rationale shows explanation before request",
        "denied permission without rationale requests directly",
    ):
        if token not in permission_test_code:
            errors.append(
                "ARCH045 M-05D permission policy assertion missing: "
                f"{token}"
            )

    payload = tomllib.loads(ownership_path.read_text(encoding="utf-8"))
    records = payload.get("ownership", [])
    exceptions = payload.get("exception", [])
    if not isinstance(records, list) or not isinstance(exceptions, list):
        errors.append(
            "ARCH045 M-05D ownership or exception entries must be arrays"
        )
    else:
        records_by_id = {
            str(record.get("id")): record
            for record in records
            if isinstance(record, dict)
        }
        platform_record = records_by_id.get("kiyori-platform")
        if (
            not isinstance(platform_record, dict)
            or platform_record.get("path")
            != "app/src/main/java/com/kiyori/platform/**"
            or platform_record.get("owner") != "kiyori-platform"
            or platform_record.get("sync_zone") != "C"
            or platform_record.get("planned")
        ):
            errors.append(
                "ARCH045 M-05D kiyori-platform ownership differs"
            )
        integration_record = records_by_id.get("kiyori-integration-operit")
        if (
            not isinstance(integration_record, dict)
            or integration_record.get("path")
            != "app/src/main/java/com/kiyori/integration/operit/**"
            or integration_record.get("owner")
            != "kiyori-operit-integration"
            or "com.ai.assistance.operit"
            not in integration_record.get("allowed_import_roots", [])
        ):
            errors.append(
                "ARCH045 M-05D Operit integration ownership differs"
            )
        stale_resource_exceptions = [
            record
            for record in exceptions
            if isinstance(record, dict)
            and record.get("rule") == "ARCH004"
            and record.get("path") == M05D_COORDINATOR_PATH
        ]
        if stale_resource_exceptions:
            errors.append(
                "ARCH045 M-05D notification resource ownership "
                "exception remains"
            )

    architecture_test_path = root / M05D_ARCHITECTURE_TEST_PATH
    if not architecture_test_path.is_file():
        errors.append(
            "ARCH045 M-05D architecture tests missing: "
            f"{M05D_ARCHITECTURE_TEST_PATH}"
        )
    else:
        architecture_test_code = source_code_mask(
            architecture_test_path.read_text(encoding="utf-8")
        )
        for test_name in (
            "test_m05d_notification_permission_accepts_platform_and_resource_split",
            "test_m05d_notification_permission_rejects_owner_scope_or_resource_drift",
        ):
            if test_name not in architecture_test_code:
                errors.append(
                    "ARCH045 M-05D architecture test missing: "
                    f"{test_name}"
                )


def check_m05e_storage_paths(
    root: Path,
    ownership_path: Path,
    errors: list[str],
) -> None:
    if not any(
        (root / path).is_file()
        for path in (
            M05D_PLATFORM_PERMISSION_PATH,
            KIYORI_FIRST_RUN_PERMISSIONS_PATH,
        )
    ):
        return

    kiyori_paths_path = root / M05E_KIYORI_PATHS_PATH
    if not kiyori_paths_path.is_file():
        errors.append(
            "ARCH046 M-05E Kiyori paths owner missing: "
            f"{M05E_KIYORI_PATHS_PATH}"
        )
        return

    required_paths = {
        "Kiyori backup path projection": M05E_KIYORI_BACKUP_PATHS_PATH,
        "Operit paths compatibility facade": M05E_OPERIT_PATHS_PATH,
        "Operit backup paths compatibility facade": (
            M05E_OPERIT_BACKUP_DIRS_PATH
        ),
        "Kiyori paths contract test": M05E_PATHS_TEST_PATH,
    }
    for label, relative_path in required_paths.items():
        if not (root / relative_path).is_file():
            errors.append(
                f"ARCH046 M-05E {label} missing: {relative_path}"
            )
    if any(not (root / path).is_file() for path in required_paths.values()):
        return

    architecture_root = root / "config/architecture"
    hash_snapshot_path = architecture_root / M05E_HASH_SNAPSHOT
    if not hash_snapshot_path.is_file():
        errors.append(
            "ARCH046 M-05E storage hash snapshot missing: "
            f"config/architecture/{M05E_HASH_SNAPSHOT}"
        )
    else:
        hash_entries = read_hash_snapshot(hash_snapshot_path)
        actual_targets = {relative_path for _, relative_path in hash_entries}
        if actual_targets != set(M05E_HASHED_PATHS):
            errors.append(
                "ARCH046 M-05E storage hash targets differ: "
                f"expected {sorted(M05E_HASHED_PATHS)}, "
                f"found {sorted(actual_targets)}"
            )
        for expected_hash, relative_path in hash_entries:
            source_path = root / relative_path
            if not source_path.is_file():
                errors.append(
                    "ARCH046 M-05E hashed storage source missing: "
                    f"{relative_path}"
                )
                continue
            normalized = source_path.read_bytes().replace(b"\r\n", b"\n")
            actual_hash = hashlib.sha256(normalized).hexdigest().upper()
            if actual_hash != expected_hash:
                errors.append(
                    "ARCH046 M-05E storage source changed: "
                    f"{relative_path} expected {expected_hash}, "
                    f"found {actual_hash}"
                )

    expected_packages = {
        M05E_KIYORI_PATHS_PATH: "com.kiyori.platform.storage",
        M05E_KIYORI_BACKUP_PATHS_PATH: "com.kiyori.platform.storage",
        M05E_OPERIT_PATHS_PATH: "com.ai.assistance.operit.util",
        M05E_OPERIT_BACKUP_DIRS_PATH: (
            "com.ai.assistance.operit.data.backup"
        ),
    }
    for relative_path, expected_package in expected_packages.items():
        actual_package = source_package(root / relative_path)
        if actual_package != expected_package:
            errors.append(
                "ARCH046 M-05E storage package differs: "
                f"{relative_path} expected {expected_package}, "
                f"found {actual_package}"
            )

    kiyori_paths_text = kiyori_paths_path.read_text(encoding="utf-8")
    kiyori_paths_code = source_code_mask(kiyori_paths_text)
    project_imports = [
        imported
        for _, imported in source_imports(kiyori_paths_path)
        if imported.startswith(("com.ai.assistance.operit", "com.kiyori"))
    ]
    if project_imports:
        errors.append(
            "ARCH046 M-05E Kiyori paths owner imports project code: "
            f"{project_imports}"
        )

    required_path_literal_counts = {
        "Kiyori": 1,
        "Download": 1,
        "Pictures": 1,
        "cleanOnExit": 1,
        "plugins": 1,
        "mcp_plugins": 1,
        "bridge": 1,
        "exports": 1,
        "browser": 2,
        "userscripts": 2,
        "player": 1,
        "toolbox": 1,
        "ai-config": 1,
        "conversation-audit": 2,
        "backups": 1,
        "toolpkg": 3,
        "public": 1,
        "workspace": 1,
        "workflow": 1,
        "models": 1,
        "mnn": 1,
        "llama": 1,
        "output images": 1,
        "error": 1,
        "test": 1,
        "websession": 1,
        "skills": 1,
        "downloads": 1,
        "backup": 1,
        "raw_snapshot": 1,
        "room_db": 1,
        "chat": 1,
        "memory": 1,
        "model_config": 1,
        "character_cards": 1,
        "Markdown": 1,
        "Shared": 1,
        "AI": 1,
        "v1": 3,
        "payloads": 1,
        "staging": 1,
        "data": 1,
        "generations": 1,
        "migration-audit": 1,
        "active-generation.json": 1,
        "generation.json": 1,
        "toolpkg-runtime": 1,
        "artifacts": 1,
        "extracted": 1,
        "active": 1,
        "audit": 1,
        "market": 1,
        "toolpkg-build": 1,
        "logs": 1,
        "errors": 1,
        "backup-staging": 1,
        ".sherpa_ncnn_models": 1,
        ".vector_index": 1,
        "image_pool": 1,
        "media_pool": 1,
        "skill_repo_zip_pool": 1,
    }
    for literal, expected_count in required_path_literal_counts.items():
        count = len(
            re.findall(
                rf'"{re.escape(literal)}"',
                kiyori_paths_text,
            )
        )
        if count != expected_count:
            errors.append(
                "ARCH046 M-05E Kiyori path literal count differs: "
                f"{literal} expected {expected_count}, found {count}"
            )

    compatibility_path_api_methods = (
        "downloadsDir",
        "kiyoriRootDir",
        "cleanOnExitDir",
        "pluginsDir",
        "pluginConfigDir",
        "cleanOnExitInternalDir",
        "mcpPluginsDir",
        "bridgeDir",
        "exportsDir",
        "workspaceDir",
        "workflowDir",
        "mnnModelsDir",
        "llamaModelsDir",
        "outputImagesDir",
        "errorDir",
        "testDir",
        "webSessionDir",
        "skillsDir",
        "webSessionUserscriptsDir",
        "privateWebSessionUserscriptsDir",
        "browserDownloadsDir",
        "sherpaNcnnModelsDir",
        "vectorIndexDir",
        "imagePoolDir",
        "mediaPoolDir",
        "skillRepoZipPoolDir",
        "rawSnapshotExcludedFilesTopLevelDirNames",
        "kiyoriRootPathSdcard",
        "cleanOnExitPathSdcard",
        "pluginsPathSdcard",
        "bridgePathSdcard",
        "exportsPathSdcard",
        "workspacePathSdcard",
        "testPathSdcard",
        "webSessionUserscriptsPathSdcard",
    )
    extended_path_api_methods = (
        "exportDir",
        "conversationAuditExportsDir",
        "conversationAuditRootDir",
        "conversationAuditPayloadsDir",
        "conversationAuditStagingDir",
        "browserApplicationDownloadsDir",
        "publicProjection",
        "toolPkgPublicWorkspaceDir",
        "toolPkgExportDir",
        "toolPkgPrivateRootDir",
        "toolPkgPrivateDataDir",
        "toolPkgGenerationsDir",
        "toolPkgGenerationDir",
        "toolPkgGenerationDataDir",
        "toolPkgGenerationMetadataFile",
        "toolPkgMigrationAuditDir",
        "toolPkgMigrationAuditFile",
        "toolPkgActiveGenerationFile",
        "toolPkgCacheDir",
        "toolPkgBuildTransactionDir",
        "toolPkgRuntimeRootDir",
        "toolPkgArtifactsDir",
        "toolPkgExtractedDir",
        "toolPkgActiveDir",
        "toolPkgAuditDir",
        "toolPkgMarketDir",
        "internalLogsDir",
        "internalErrorsDir",
        "internalBackupStagingDir",
        "browserDownloadsRelativePath",
        "backupRootDir",
        "rawSnapshotDir",
        "roomDbDir",
        "chatDir",
        "memoryDir",
        "modelConfigDir",
        "characterCardsDir",
    )
    path_api_methods = (
        compatibility_path_api_methods + extended_path_api_methods
    )
    for method_name in path_api_methods:
        count = len(
            re.findall(
                rf"\bfun\s+{re.escape(method_name)}\s*\(",
                kiyori_paths_code,
            )
        )
        if count < 1:
            errors.append(
                "ARCH046 M-05E Kiyori path API missing: "
                f"{method_name}"
            )
    for token, expected_count in (
        ("object KiyoriPaths", 1),
        (
            "Environment.getExternalStoragePublicDirectory("
            "Environment.DIRECTORY_DOWNLOADS)",
            1,
        ),
        ("private fun ensureDir(", 1),
        ("internal fun pluginConfigDirectoryName(", 1),
        ('.ifBlank { "plugin" }', 1),
        ("Integer.toHexString(trimmed.hashCode())", 1),
        ('return "/sdcard/Download/$KIYORI_DIR_NAME"', 1),
    ):
        count = kiyori_paths_text.count(token)
        if count != expected_count:
            errors.append(
                "ARCH046 M-05E Kiyori path calculation contract differs: "
                f"{token} expected {expected_count}, found {count}"
            )
    for forbidden_token in (
        "com.ai.assistance.operit",
        "ApplicationContextAccess",
        "KiyoriApplication",
        "DataStore",
        "SharedPreferences",
        "RoomDatabase",
        "RawSnapshotBackupManager",
        "KiyoriLogger",
        "runCatching",
    ):
        if forbidden_token in kiyori_paths_code:
            errors.append(
                "ARCH046 M-05E Kiyori paths absorbed forbidden dependency "
                f"or behavior: {forbidden_token}"
            )

    backup_projection_path = root / M05E_KIYORI_BACKUP_PATHS_PATH
    backup_projection_text = backup_projection_path.read_text(encoding="utf-8")
    backup_projection_code = source_code_mask(backup_projection_text)
    backup_methods = (
        "kiyoriRootDir",
        "backupRootDir",
        "rawSnapshotDir",
        "roomDbDir",
        "chatDir",
        "memoryDir",
        "modelConfigDir",
        "characterCardsDir",
    )
    if backup_projection_code.count("object KiyoriBackupPaths") != 1:
        errors.append(
            "ARCH046 M-05E Kiyori backup path projection owner differs"
        )
    for method_name in backup_methods:
        if (
            len(
                re.findall(
                    rf"\bfun\s+{re.escape(method_name)}\s*\(",
                    backup_projection_code,
                )
            )
            != 1
            or backup_projection_code.count(
                f"KiyoriPaths.{method_name}("
            )
            != 1
        ):
            errors.append(
                "ARCH046 M-05E Kiyori backup path projection differs: "
                f"{method_name}"
            )
    for forbidden_token in (
        "Environment.",
        "File(",
        "Regex(",
        "ensureDir",
        "mkdirs",
        "DataStore",
        "SharedPreferences",
    ):
        if forbidden_token in backup_projection_code:
            errors.append(
                "ARCH046 M-05E Kiyori backup projection owns path "
                f"calculation or state: {forbidden_token}"
            )
    for literal in required_path_literal_counts:
        if f'"{literal}"' in backup_projection_text:
            errors.append(
                "ARCH046 M-05E Kiyori backup projection duplicates "
                f"path literal: {literal}"
            )

    operit_paths_path = root / M05E_OPERIT_PATHS_PATH
    operit_paths_text = operit_paths_path.read_text(encoding="utf-8")
    operit_paths_code = source_code_mask(operit_paths_text)
    expected_operit_path_imports = Counter((M05E_KIYORI_PATHS_IMPORT,))
    actual_operit_path_imports = Counter(
        imported
        for _, imported in source_imports(operit_paths_path)
        if imported.startswith(("com.ai.assistance.operit", "com.kiyori"))
    )
    if actual_operit_path_imports != expected_operit_path_imports:
        errors.append(
            "ARCH046 M-05E Operit paths facade project imports differ: "
            f"expected {sorted(expected_operit_path_imports.elements())}, "
            f"found {sorted(actual_operit_path_imports.elements())}"
        )
    if operit_paths_code.count("object OperitPaths") != 1:
        errors.append(
            "ARCH046 M-05E Operit paths facade object differs"
        )
    public_constant_names = (
        "SHERPA_NCNN_MODELS_DIR_NAME",
        "VECTOR_INDEX_DIR_NAME",
        "IMAGE_POOL_DIR_NAME",
        "MEDIA_POOL_DIR_NAME",
        "SKILL_REPO_ZIP_POOL_DIR_NAME",
    )
    for constant_name in public_constant_names:
        if (
            operit_paths_code.count(
                f"const val {constant_name} = KiyoriPaths.{constant_name}"
            )
            != 1
        ):
            errors.append(
                "ARCH046 M-05E Operit paths facade constant differs: "
                f"{constant_name}"
            )
    for method_name in compatibility_path_api_methods:
        if (
            len(
                re.findall(
                    rf"\bfun\s+{re.escape(method_name)}\s*\(",
                    operit_paths_code,
                )
            )
            != 1
            or operit_paths_code.count(f"KiyoriPaths.{method_name}(") != 1
        ):
            errors.append(
                "ARCH046 M-05E Operit paths facade API delegation differs: "
                f"{method_name}"
            )
    for forbidden_token in (
        "Environment.",
        "File(",
        "Regex(",
        "ensureDir",
        "mkdirs",
        "hashCode",
        "ifBlank",
    ):
        if forbidden_token in operit_paths_code:
            errors.append(
                "ARCH046 M-05E Operit paths facade owns calculation: "
                f"{forbidden_token}"
            )
    for literal in required_path_literal_counts:
        if f'"{literal}"' in operit_paths_text:
            errors.append(
                "ARCH046 M-05E Operit paths facade duplicates path literal: "
                f"{literal}"
            )

    operit_backup_path = root / M05E_OPERIT_BACKUP_DIRS_PATH
    operit_backup_text = operit_backup_path.read_text(encoding="utf-8")
    operit_backup_code = source_code_mask(operit_backup_text)
    expected_operit_backup_imports = Counter(
        (M05E_KIYORI_BACKUP_PATHS_IMPORT,)
    )
    actual_operit_backup_imports = Counter(
        imported
        for _, imported in source_imports(operit_backup_path)
        if imported.startswith(("com.ai.assistance.operit", "com.kiyori"))
    )
    if actual_operit_backup_imports != expected_operit_backup_imports:
        errors.append(
            "ARCH046 M-05E Operit backup facade project imports differ: "
            f"expected {sorted(expected_operit_backup_imports.elements())}, "
            f"found {sorted(actual_operit_backup_imports.elements())}"
        )
    if operit_backup_code.count("object OperitBackupDirs") != 1:
        errors.append(
            "ARCH046 M-05E Operit backup facade object differs"
        )
    for method_name in backup_methods:
        if (
            len(
                re.findall(
                    rf"\bfun\s+{re.escape(method_name)}\s*\(",
                    operit_backup_code,
                )
            )
            != 1
            or operit_backup_code.count(
                f"KiyoriBackupPaths.{method_name}("
            )
            != 1
        ):
            errors.append(
                "ARCH046 M-05E Operit backup facade delegation differs: "
                f"{method_name}"
            )
    for forbidden_token in (
        "Environment.",
        "File(",
        "Regex(",
        "ensureDir",
        "mkdirs",
    ):
        if forbidden_token in operit_backup_code:
            errors.append(
                "ARCH046 M-05E Operit backup facade owns calculation: "
                f"{forbidden_token}"
            )
    for literal in required_path_literal_counts:
        if f'"{literal}"' in operit_backup_text:
            errors.append(
                "ARCH046 M-05E Operit backup facade duplicates path "
                f"literal: {literal}"
            )

    application_path = root / M03_APPLICATION_PATH
    if application_path.is_file():
        application_code = source_code_mask(
            application_path.read_text(encoding="utf-8")
        )
        staging_owner_token = (
            "ShowerEnvironment.stagingDirectoryProvider = "
            "KiyoriPaths::kiyoriRootDir"
        )
        staging_owner_count = application_code.count(staging_owner_token)
        if staging_owner_count != 1:
            errors.append(
                "ARCH046 M-05E Shower staging path owner differs: "
                f"expected 1, found {staging_owner_count}"
            )

    main_source_root = root / "app/src/main/java"
    source_paths = sorted(main_source_root.rglob("*.kt"))

    def symbol_consumers(symbol: str, owner_path: str) -> set[str]:
        consumers: set[str] = set()
        pattern = re.compile(rf"\b{re.escape(symbol)}\b")
        for source_path in source_paths:
            relative_path = source_path.relative_to(root).as_posix()
            if relative_path == owner_path:
                continue
            code = source_code_mask(source_path.read_text(encoding="utf-8"))
            if pattern.search(code):
                consumers.add(relative_path)
        return consumers

    snapshot_contracts = (
        (
            M05E_DIRECT_PATH_CONSUMER_SNAPSHOT,
            set(M05E_DIRECT_PATH_CONSUMER_PATHS),
            symbol_consumers("KiyoriPaths", M05E_KIYORI_PATHS_PATH),
            "direct Kiyori path consumers",
        ),
        (
            M05E_DIRECT_BACKUP_CONSUMER_SNAPSHOT,
            set(M05E_DIRECT_BACKUP_CONSUMER_PATHS),
            symbol_consumers(
                "KiyoriBackupPaths",
                M05E_KIYORI_BACKUP_PATHS_PATH,
            ),
            "direct Kiyori backup path consumers",
        ),
        (
            M05E_LEGACY_OPERIT_CONSUMER_SNAPSHOT,
            set(M05E_LEGACY_OPERIT_CONSUMER_PATHS),
            symbol_consumers("OperitPaths", M05E_OPERIT_PATHS_PATH),
            "legacy Operit path consumers",
        ),
    )
    for snapshot_name, expected_paths, actual_paths, label in snapshot_contracts:
        snapshot_path = architecture_root / snapshot_name
        snapshot_paths: set[str] = set()
        if not snapshot_path.is_file():
            errors.append(
                f"ARCH046 M-05E {label} snapshot missing: "
                f"config/architecture/{snapshot_name}"
            )
        else:
            entries = read_snapshot(snapshot_path)
            snapshot_paths = set(entries)
            if (
                len(entries) != len(expected_paths)
                or snapshot_paths != expected_paths
            ):
                errors.append(
                    f"ARCH046 M-05E {label} snapshot differs: "
                    f"found {entries}"
                )
        if actual_paths != snapshot_paths:
            errors.append(
                f"ARCH046 M-05E {label} differ: "
                f"expected {sorted(snapshot_paths)}, "
                f"found {sorted(actual_paths)}"
            )

    legacy_backup_consumers = symbol_consumers(
        "OperitBackupDirs",
        M05E_OPERIT_BACKUP_DIRS_PATH,
    )
    if legacy_backup_consumers:
        errors.append(
            "ARCH046 M-05E legacy Operit backup consumers remain: "
            f"{sorted(legacy_backup_consumers)}"
        )

    declaration_contracts = (
        ("object KiyoriPaths", M05E_KIYORI_PATHS_PATH),
        ("object KiyoriBackupPaths", M05E_KIYORI_BACKUP_PATHS_PATH),
        ("object OperitPaths", M05E_OPERIT_PATHS_PATH),
        ("object OperitBackupDirs", M05E_OPERIT_BACKUP_DIRS_PATH),
    )
    for declaration, expected_path in declaration_contracts:
        owners = [
            source_path.relative_to(root).as_posix()
            for source_path in source_paths
            if declaration
            in source_code_mask(source_path.read_text(encoding="utf-8"))
        ]
        if owners != [expected_path]:
            errors.append(
                "ARCH046 M-05E storage symbol must have one owner: "
                f"{declaration} expected {[expected_path]}, found {owners}"
            )

    paths_test_code = source_code_mask(
        (root / M05E_PATHS_TEST_PATH).read_text(encoding="utf-8")
    )
    for token in (
        "class KiyoriPathsTest",
        "public sdcard paths keep exact Kiyori layout",
        "raw snapshot exclusions keep exact names",
        "plugin directory names keep existing sanitization",
        "backup directories keep exact hierarchy",
        "base directory pools keep exact names",
        "KiyoriPaths.kiyoriRootPathSdcard()",
        "KiyoriPaths.rawSnapshotExcludedFilesTopLevelDirNames()",
        "KiyoriPaths.pluginConfigDirectoryName(",
        "KiyoriPaths.backupRootDir(",
    ):
        if token not in paths_test_code:
            errors.append(
                "ARCH046 M-05E storage path assertion missing: "
                f"{token}"
            )

    ownership_data = tomllib.loads(ownership_path.read_text(encoding="utf-8"))
    ownership_records = ownership_data.get("ownership", [])
    if not isinstance(ownership_records, list):
        errors.append(
            "ARCH046 M-05E ownership entries must be arrays"
        )
    else:
        platform_records = [
            record
            for record in ownership_records
            if isinstance(record, dict)
            and record.get("id") == "kiyori-platform"
        ]
        if len(platform_records) != 1:
            errors.append(
                "ARCH046 M-05E kiyori-platform ownership differs"
            )
        else:
            record = platform_records[0]
            if (
                record.get("path")
                != "app/src/main/java/com/kiyori/platform/**"
                or record.get("owner") != "kiyori-platform"
                or record.get("sync_zone") != "C"
                or record.get("phase") != "m05a3"
                or set(record.get("allowed_import_roots", []))
                != {"com.kiyori.capability", "com.kiyori.platform"}
            ):
                errors.append(
                    "ARCH046 M-05E kiyori-platform ownership scope differs"
                )

    architecture_test_path = root / M05E_ARCHITECTURE_TEST_PATH
    if not architecture_test_path.is_file():
        errors.append(
            "ARCH046 M-05E architecture tests missing: "
            f"{M05E_ARCHITECTURE_TEST_PATH}"
        )
    else:
        architecture_test_code = source_code_mask(
            architecture_test_path.read_text(encoding="utf-8")
        )
        for test_name in (
            "test_m05e_storage_paths_accept_unique_owner_and_facades",
            "test_m05e_storage_paths_reject_duplicate_calculation_or_consumer_drift",
        ):
            if test_name not in architecture_test_code:
                errors.append(
                    "ARCH046 M-05E architecture test missing: "
                    f"{test_name}"
                )


def normalize_m01_text(text: str) -> str:
    return (
        text.replace("\r\n", "\n")
        .replace("KiyoriApplication", "OperitApplication")
        .replace("kiyoriApplication", "operitApplication")
        .replace("Application class for Kiyori", "Application class for Operit")
    )


def candidate_text(root: Path, path: str) -> str:
    return (root / path).read_text(encoding="utf-8")


def base_text(root: Path, base: str, path: str) -> str:
    return git(root, "show", f"{base}:{path}")


def working_tree_app_paths(root: Path, base: str) -> set[str]:
    changed = {
        line.strip()
        for line in git(
            root,
            "diff",
            "--name-only",
            "--find-renames=90%",
            base,
            "--",
            "app",
        ).splitlines()
        if line.strip()
    }
    changed.update(
        line.strip()
        for line in git(
            root,
            "ls-files",
            "--others",
            "--exclude-standard",
            "--",
            "app",
        ).splitlines()
        if line.strip()
    )
    if M01_OLD_PATH in changed and M01_NEW_PATH in changed and not (root / M01_OLD_PATH).exists():
        changed.remove(M01_OLD_PATH)
    return changed


def check_m01(root: Path, base: str, errors: list[str]) -> None:
    changed = working_tree_app_paths(root, base)
    if changed != M01_EXPECTED_CANDIDATE_PATHS:
        errors.append(
            "ARCH016 M-01 implementation paths differ: expected "
            f"{sorted(M01_EXPECTED_CANDIDATE_PATHS)}, found {sorted(changed)}"
        )
        return

    for path in sorted(changed):
        source_path = M01_OLD_PATH if path == M01_NEW_PATH else path
        before = base_text(root, base, source_path)
        after = candidate_text(root, path)
        if normalize_m01_text(after) != normalize_m01_text(before):
            errors.append(f"ARCH016 non-name change in {path}")

    kotlin_files = list((root / "app/src/main/java").rglob("*.kt"))
    old_count = sum(path.read_text(encoding="utf-8").count("OperitApplication") for path in kotlin_files)
    old_count += (root / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8").count(
        "OperitApplication"
    )
    old_count += (root / "app/lint-baseline.xml").read_text(encoding="utf-8").count(
        "OperitApplication"
    )
    new_count = sum(path.read_text(encoding="utf-8").count("KiyoriApplication") for path in kotlin_files)
    new_count += (root / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8").count(
        "KiyoriApplication"
    )
    new_count += (root / "app/lint-baseline.xml").read_text(encoding="utf-8").count(
        "KiyoriApplication"
    )
    if old_count != 0 or new_count != 49:
        errors.append(f"ARCH016 M-01 symbol counts must be old=0/new=49, found {old_count}/{new_count}")

    package_line = next(
        (
            line.strip()
            for line in (root / M01_NEW_PATH).read_text(encoding="utf-8").splitlines()
            if line.startswith("package ")
        ),
        "",
    )
    if package_line != "package com.ai.assistance.operit.core.application":
        errors.append(f"ARCH016 M-01 package changed: {package_line or 'missing'}")


def git_path_exists(root: Path, revision: str, path: str) -> bool:
    result = subprocess.run(
        ["git", "cat-file", "-e", f"{revision}:{path}"],
        cwd=root,
        check=False,
        capture_output=True,
    )
    return result.returncode == 0


def resolve_phase(root: Path, requested: str, base: str | None) -> str:
    if requested != "auto":
        return requested
    if (root / M03_APPLICATION_PATH).is_file():
        return "m03"
    if not (root / M01_NEW_PATH).is_file():
        return "baseline"
    if (
        base
        and git_path_exists(root, base, M01_OLD_PATH)
        and not git_path_exists(root, base, M01_NEW_PATH)
    ):
        return "m01"
    return "post-m01"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repository", type=Path, default=Path.cwd())
    parser.add_argument(
        "--ownership",
        type=Path,
        default=Path("config/architecture/package-ownership.toml"),
    )
    parser.add_argument(
        "--phase",
        choices=("auto", "baseline", "m01", "post-m01", "m03"),
        default="auto",
    )
    parser.add_argument("--base")
    parser.add_argument("--require-main", action="store_true")
    parser.add_argument("--json", action="store_true")
    parser.add_argument(
        "--timings",
        action="store_true",
        help="print elapsed time for each architecture check",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    root = args.repository.resolve()
    phase = resolve_phase(root, args.phase, args.base)
    errors: list[str] = []

    if args.require_main:
        branch = git(root, "branch", "--show-current").strip()
        if branch != "main":
            errors.append(f"ARCH000 current branch must be main, found {branch or 'detached HEAD'}")

    ownership_path = args.ownership
    if not ownership_path.is_absolute():
        ownership_path = root / ownership_path
    architecture_root = root / "config/architecture"
    checks = (
        lambda: check_ownership(root, ownership_path, errors),
        lambda: check_manifest(
            root,
            architecture_root / "manifest-components.txt",
            phase,
            errors,
            architecture_root / "manifest-structure-hashes.txt",
        ),
        lambda: check_debug_manifest(root, architecture_root, errors),
        lambda: check_literals(
            root,
            (
                architecture_root / "stable-identifiers.txt",
                architecture_root / "persistence-names.txt",
                architecture_root / "native-ipc-identifiers.txt",
            ),
            errors,
        ),
        lambda: check_persistence_api_calls(
            root,
            architecture_root / "persistence-api-calls.txt",
            errors,
        ),
        lambda: check_file_hashes(
            root,
            architecture_root / "critical-file-hashes.txt",
            errors,
        ),
        lambda: check_m02_application_access(root, errors),
        lambda: check_m03_application_move(root, architecture_root, errors),
        lambda: check_m04_root_composition(root, errors),
        lambda: check_m04b_operit_navigation_policy(root, errors),
        lambda: check_m04b_browser_exit_contract(root, errors),
        lambda: check_m04b_shell_state_owner(root, errors),
        lambda: check_m04b_app_shell_owner(root, errors),
        lambda: check_m04b_ai_drawer_owner(root, errors),
        lambda: check_m04b_primary_navigation_owner(root, errors),
        lambda: check_m04b_software_home_owner(root, errors),
        lambda: check_m04b_browser_search_owner(root, errors),
        lambda: check_m04c_navigation_integration(root, errors),
        lambda: check_m04d_main_pending_requests(root, errors),
        lambda: check_m04d_main_intent_decoder(root, errors),
        lambda: check_m04d_main_display_coordinator(root, errors),
        lambda: check_m04d_main_shared_content_coordinator(root, errors),
        lambda: check_m04d_main_task_visibility_coordinator(root, errors),
        lambda: check_m04d_main_orientation_coordinator(root, errors),
        lambda: check_kiyori_first_run_flow(root, errors),
        lambda: check_m04d_main_content_host(root, errors),
        lambda: check_m04e_finalization(root, ownership_path, errors),
        lambda: check_m05a1_design_theme(root, ownership_path, errors),
        lambda: check_m05a2_semantic_design(root, ownership_path, errors),
        lambda: check_m05a3_root_theme(root, ownership_path, errors),
        lambda: check_m05b_platform_logging(root, ownership_path, errors),
        lambda: check_m05c_platform_lifecycle(
            root,
            ownership_path,
            errors,
        ),
        lambda: check_m05e_storage_paths(
            root,
            ownership_path,
            errors,
        ),
        lambda: check_tracked_artifacts(root, errors),
        lambda: check_terminal_unchanged(root, args.base, errors),
    )
    for index, check in enumerate(checks, start=1):
        check_name = next(
            (
                name
                for name in check.__code__.co_names
                if name.startswith("check_")
            ),
            f"check_{index}",
        )
        started_at = time.perf_counter()
        if args.timings:
            print(
                f"Architecture timing START {index}/{len(checks)} {check_name}",
                flush=True,
            )
        try:
            check()
        except (
            OSError,
            TypeError,
            ValueError,
            ET.ParseError,
            tomllib.TOMLDecodeError,
            subprocess.CalledProcessError,
        ) as error:
            errors.append(f"ARCH000 checker input error: {error}")
        finally:
            if args.timings:
                print(
                    "Architecture timing END "
                    f"{index}/{len(checks)} {check_name} "
                    f"{time.perf_counter() - started_at:.3f}s",
                    flush=True,
                )
    if phase == "m01":
        if not args.base:
            errors.append("ARCH016 --base is required for M-01")
        else:
            try:
                check_m01(root, args.base, errors)
            except (OSError, ValueError, subprocess.CalledProcessError) as error:
                errors.append(f"ARCH016 M-01 check failed: {error}")

    if args.json:
        print(json.dumps({"phase": phase, "errors": errors}, ensure_ascii=False, indent=2))
    elif errors:
        print("Architecture boundaries: FAIL", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
    else:
        print(f"Architecture boundaries: PASS (phase={phase})")
        print("- package ownership coverage")
        print("- manifest component and semantic structure snapshots")
        print("- stable persistence/native/protocol literals")
        print("- persistence API call surface")
        print("- critical AIDL, Room, ObjectBox, WorkManager, and backup file hashes")
        print("- tracked artifact and terminal boundaries")
        if phase == "m01":
            print("- M-01 exact paths, normalized diff, package, and symbol counts")
    return 1 if errors else 0


if __name__ == "__main__":
    raise SystemExit(main())
