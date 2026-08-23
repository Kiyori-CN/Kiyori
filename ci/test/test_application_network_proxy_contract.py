from __future__ import annotations

import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
JAVA_ROOT = REPO_ROOT / "app" / "src" / "main" / "java"
OPERIT_ROOT = JAVA_ROOT / "com" / "ai" / "assistance" / "operit"
KIYORI_ROOT = JAVA_ROOT / "com" / "kiyori"


class ApplicationNetworkProxyContractTest(unittest.TestCase):
    def read_operit(self, relative_path: str) -> str:
        return (OPERIT_ROOT / relative_path).read_text(encoding="utf-8")

    def read_kiyori(self, relative_path: str) -> str:
        return (KIYORI_ROOT / relative_path).read_text(encoding="utf-8")

    def test_single_application_owner_and_all_modules_are_declared(self) -> None:
        models = self.read_kiyori("platform/network/KiyoriNetworkProxyModels.kt")
        manager = self.read_kiyori("platform/network/KiyoriNetworkProxyManager.kt")

        for module in (
            "AI_SERVICES",
            "AI_TOOLS",
            "BROWSER",
            "DOWNLOADS",
            "PLAYER",
            "SCRIPTS",
            "APP_SERVICES",
        ):
            self.assertIn(module, models)
        self.assertIn("class KiyoriNetworkProxyManager", manager)
        self.assertIn("KiyoriNetworkProxyConfigStore", manager)
        self.assertIn("KiyoriMihomoRuntime", manager)
        self.assertNotIn("ScriptNetworkConfigStore", manager)
        old_network_dir = OPERIT_ROOT / "core/tools/javascript/network"
        self.assertEqual([], list(old_network_dir.glob("*.kt")), "the removed script-only owner must not return")

    def test_subscription_negotiates_clash_meta_yaml_and_preserves_safe_boundaries(self) -> None:
        client = self.read_kiyori("platform/network/MihomoSubscriptionClient.kt")
        sanitizer = self.read_kiyori("platform/network/MihomoConfigSanitizer.kt")
        models = self.read_kiyori("platform/network/KiyoriNetworkProxyModels.kt")
        store = self.read_kiyori("platform/network/KiyoriNetworkProxyConfigStore.kt")
        runtime = self.read_kiyori("platform/network/KiyoriMihomoRuntime.kt")
        manager = self.read_kiyori("platform/network/KiyoriNetworkProxyManager.kt")
        log_store = self.read_kiyori("platform/network/KiyoriNetworkProxyLogStore.kt")

        self.assertIn('CLASH_META_USER_AGENT = "Clash.Meta"', client)
        self.assertIn('Accept", "application/yaml,text/yaml,text/plain,*/*"', client)
        self.assertIn("SUBSCRIPTION_FORMAT", client)
        self.assertIn("MAX_YAML_BYTES", client)
        self.assertIn("isolatedProxyCount", sanitizer)
        self.assertIn("ROUTE_GROUP_NAME", sanitizer)
        self.assertIn("127.0.0.1", sanitizer)
        self.assertIn("allow-lan", sanitizer)
        self.assertIn("CURRENT_SCHEMA_VERSION = 2", models)
        self.assertIn("subscriptions: List<KiyoriProxySubscription>", models)
        self.assertIn("activeSubscriptionId", models)
        self.assertIn("SETTINGS_WRITE_FAILED", models)
        self.assertIn("cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())", store)
        self.assertNotIn("GCMParameterSpec(GCM_TAG_BITS, iv)\n        cipher.init(Cipher.ENCRYPT_MODE", store)
        for operation in (
            "addSubscriptionUrl",
            "addLocalYaml",
            "updateUrlSubscription",
            "editUrlSubscription",
            "renameSubscription",
            "replaceLocalSubscription",
            "duplicateSubscription",
            "switchActiveSubscription",
            "removeSubscription",
            "refreshSubscriptionNodes",
            "testSubscriptionNode",
            "testSubscriptionGroup",
        ):
            self.assertIn("fun " + operation, manager)
        self.assertIn("probeSnapshot", runtime)
        self.assertIn('listOf("group", groupName, "delay")', runtime)
        self.assertIn('"delay"', runtime)
        self.assertIn("collectProcessOutput", runtime)
        self.assertNotIn("drainProcessOutput", runtime)
        self.assertIn("MAX_ENTRIES = 300", log_store)
        self.assertIn("fun exportText", log_store)
        self.assertIn("fun clear", log_store)
        self.assertIn("LONG_CREDENTIAL", log_store)
        self.assertIn("val logEntries", manager)

    def test_network_stacks_use_explicit_module_identity(self) -> None:
        expected = {
            "api/chat/llmprovider/AIServiceFactory.kt": "AI_SERVICES",
            "core/tools/defaultTool/standard/StandardHttpTools.kt": "AI_TOOLS",
            "core/tools/defaultTool/standard/StandardWebVisitTool.kt": None,
            "core/tools/defaultTool/websession/browser/BrowserDownloadTransport.kt": "DOWNLOADS",
            "core/player/runtime/MpvPlayerEngine.kt": "PLAYER",
            "data/api/GitHubApiService.kt": "APP_SERVICES",
        }
        for relative_path, module in expected.items():
            source = self.read_operit(relative_path)
            if module is None:
                self.assertIn("KiyoriNetworkModule", source, relative_path)
            else:
                self.assertIn("KiyoriNetworkModule." + module, source, relative_path)
            self.assertIn("KiyoriNetworkProxy", source, relative_path)

    def test_script_identity_is_host_owned_and_toolpkg_is_excluded(self) -> None:
        manager = self.read_operit("core/tools/javascript/JsToolManager.kt")
        engine = self.read_operit("core/tools/javascript/JsEngine.kt")
        delegates = self.read_operit("core/tools/javascript/JsNativeInterfaceDelegates.kt")
        package_screen = self.read_operit("ui/features/packages/screens/PackageManagerScreen.kt")

        self.assertIn("if (toolPkgRuntime == null)", manager)
        self.assertIn("RUNTIME_ELIGIBLE_PARAMETER", manager)
        self.assertIn("KiyoriScriptNetworkCallIdentity.trustedParameters", engine)
        self.assertIn("KiyoriScriptNetworkCallIdentity.mergeTrustedParameters", delegates)
        self.assertIn("LocalOpenKiyoriNetworkProxy", package_screen)
        self.assertNotIn("ScriptSettingsChooserDialog", package_screen)
        self.assertNotIn("ScriptNetworkSettingsDialog", package_screen)

    def test_settings_route_and_environment_drawer_are_the_public_entry(self) -> None:
        route = self.read_kiyori("capability/settings/navigation/KiyoriSettingsRoute.kt")
        more = self.read_operit("ui/main/shell/KiyoriMoreFeaturesSettingsPage.kt")
        page = self.read_operit("ui/main/shell/KiyoriNetworkProxySettingsPage.kt")
        drawer = self.read_operit("ui/features/packages/screens/PackageEnvironmentVariablesSheet.kt")

        self.assertIn("NETWORK_PROXY", route)
        self.assertIn("OPEN_NETWORK_PROXY", more)
        self.assertIn("KiyoriNetworkProxyManager", page)
        self.assertIn("导入 YAML 文件", page)
        self.assertIn("onOpenNetworkProxy", drawer)
        self.assertIn("网络代理", drawer)

    def test_old_external_mixed_port_form_is_not_in_the_new_page(self) -> None:
        page = self.read_operit("ui/main/shell/KiyoriNetworkProxySettingsPage.kt")
        self.assertNotIn("External HTTP / Clash mixed-port", page)
        self.assertNotIn("用户名（可选）", page)
        self.assertNotIn("密码（可选）", page)

    def test_network_proxy_page_uses_compact_child_page_contract(self) -> None:
        page = self.read_operit("ui/main/shell/KiyoriNetworkProxySettingsPage.kt")
        for label in (
            "启用应用内代理",
            "默认连接",
            "当前节点",
            "订阅管理",
            "模块连接模式",
            "逐脚本连接模式",
            "代理日志",
            "代理局域网地址",
            "允许与系统 VPN 并存",
            "重置网络代理",
            "添加订阅地址",
            "导入 YAML 文件",
            "DropdownMenuItem(text = { Text(\"更新\") }",
            "DropdownMenuItem(text = { Text(\"编辑\") }",
            "DropdownMenuItem(text = { Text(\"复制\") }",
            "DropdownMenuItem(text = { Text(\"删除\") }",
        ):
            self.assertIn(label, page)
        for redundant in ("当前项目", "刷新节点与分组", "节点布局", "单列", "双列", "多列", "测试地址"):
            self.assertNotIn(redundant, page)
        self.assertIn("NetworkProxyPageSection.CURRENT_NODE", page)
        self.assertIn("NetworkProxyPageSection.SUBSCRIPTIONS", page)
        self.assertIn("NetworkProxyPageSection.LOGS", page)
        self.assertIn("ActivityResultContracts.CreateDocument", page)
        self.assertIn("copyPlainTextToClipboard", page)
        self.assertIn("清空代理日志？", page)
        self.assertIn("private fun NetworkProxyNodeList", page)

    def test_runtime_is_loopback_only_and_does_not_take_over_android_vpn(self) -> None:
        runtime = self.read_kiyori("platform/network/KiyoriMihomoRuntime.kt")
        sanitizer = self.read_kiyori("platform/network/MihomoConfigSanitizer.kt")
        self.assertIn('"127.0.0.1"', sanitizer)
        self.assertIn('filter["geoip"] = false', sanitizer)
        self.assertIn('matcher.startsWith("geosite:")', sanitizer)
        self.assertIn('matcher.startsWith("rule-set:")', sanitizer)
        self.assertIn("PROBE_DIRECTORY_PREFIX", runtime)
        self.assertNotIn("VpnService", runtime)
        self.assertNotIn("TUN", runtime)


if __name__ == "__main__":
    unittest.main()
