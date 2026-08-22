from __future__ import annotations

import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
JAVA_ROOT = (
    REPO_ROOT
    / "app"
    / "src"
    / "main"
    / "java"
    / "com"
    / "ai"
    / "assistance"
    / "operit"
)


class ScriptNetworkProxyContractTest(unittest.TestCase):
    def read(self, relative_path: str) -> str:
        return (JAVA_ROOT / relative_path).read_text(encoding="utf-8")

    def test_all_standard_script_network_entries_consume_the_host_route(self) -> None:
        http = self.read("core/tools/defaultTool/standard/StandardHttpTools.kt")
        visit = self.read("core/tools/defaultTool/standard/StandardWebVisitTool.kt")
        files = self.read("core/tools/defaultTool/standard/StandardFileSystemTools.kt")

        self.assertIn("ScriptNetworkCallIdentity.INTERNAL_PACKAGE_PARAMETER", http)
        self.assertIn("applyScriptRoute(builder, scriptPackageName)", http)
        self.assertIn("prepareHttpRequest", http)
        self.assertIn("multipartRequest", http)

        self.assertIn("ScriptNetworkCallIdentity.INTERNAL_PACKAGE_PARAMETER", visit)
        self.assertIn("visitScriptWebPage", visit)
        self.assertIn("applyScriptRoute(builder, scriptPackageName)", visit)
        self.assertIn("Jsoup.parse", visit)

        self.assertIn("ScriptNetworkCallIdentity.INTERNAL_PACKAGE_PARAMETER", files)
        self.assertIn("downloadScriptNetworkFile", files)
        self.assertIn("applyScriptRoute(builder, scriptPackageName)", files)
        self.assertIn("StandardCopyOption.ATOMIC_MOVE", files)

    def test_script_identity_is_host_owned_and_toolpkg_is_excluded(self) -> None:
        manager = self.read("core/tools/javascript/JsToolManager.kt")
        engine = self.read("core/tools/javascript/JsEngine.kt")
        runtime = self.read("core/tools/javascript/JsInitRuntimeScriptBuilder.kt")
        delegates = self.read("core/tools/javascript/JsNativeInterfaceDelegates.kt")
        screen = self.read("ui/features/packages/screens/PackageManagerScreen.kt")
        network_screen = self.read(
            "ui/features/packages/screens/ScriptNetworkSettingsScreen.kt"
        )

        self.assertIn("if (toolPkgRuntime == null)", manager)
        self.assertIn("RUNTIME_ELIGIBLE_PARAMETER", manager)
        self.assertIn("resolveExecutionSession", engine)
        self.assertIn("ScriptNetworkCallIdentity.trustedParameters", engine)
        self.assertIn("callToolAsyncForExecution", runtime)
        self.assertIn("callToolAsyncStreamingForExecution", runtime)
        self.assertIn("ScriptNetworkCallIdentity.mergeTrustedParameters", delegates)
        self.assertIn("resolveToolPkgSubpackageRuntimeInternal", screen)
        self.assertIn("draftRuntimeTouched", network_screen)
        self.assertIn("networkFactory.stopEmbeddedRuntime()", network_screen)

    def test_non_script_network_owners_do_not_read_the_proxy_store_or_factory(self) -> None:
        excluded_roots = (
            "api/chat/llmprovider",
            "core/player",
            "core/tools/defaultTool/websession",
            "core/tools/mcp",
            "core/tools/packTool",
        )
        forbidden = ("ScriptNetworkConfigStore", "ScriptNetworkHttpClientFactory")
        violations: list[str] = []
        for relative_root in excluded_roots:
            root = JAVA_ROOT / relative_root
            if not root.exists():
                continue
            for source in root.rglob("*.kt"):
                text = source.read_text(encoding="utf-8")
                if any(symbol in text for symbol in forbidden):
                    violations.append(str(source.relative_to(REPO_ROOT)))

        self.assertEqual([], violations)

    def test_runtime_plaintext_cleanup_is_bound_to_process_start_and_core_readiness(self) -> None:
        manager = self.read("core/tools/javascript/JsToolManager.kt")
        runtime = self.read("core/tools/javascript/network/ScriptProxyRuntime.kt")

        self.assertIn("scheduleStaleRuntimeCleanup", manager)
        self.assertIn("startupCleanup.await()", runtime)
        self.assertIn("configFile.exists() && !configFile.delete()", runtime)
        self.assertIn("clearRuntimeDirectory()", runtime)


if __name__ == "__main__":
    unittest.main()
