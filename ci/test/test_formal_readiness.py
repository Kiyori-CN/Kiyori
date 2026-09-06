from __future__ import annotations

import sys
import tempfile
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO_ROOT / "ci" / "script"))

from check_formal_readiness import (  # noqa: E402
    LEGAL_ATTRIBUTION_LINES,
    check_ci_android_toolchain,
    check_generated_native_inputs,
    check_native_source_pins,
    check_package_metadata,
    check_runtime_urls,
    check_ssh_secret_transport,
    check_visible_branding,
)


class FormalReadinessTest(unittest.TestCase):
    def test_attribution_is_allowed_without_exempting_page_business_code(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for relative_path, lines in LEGAL_ATTRIBUTION_LINES.items():
                source = root / relative_path
                source.parent.mkdir(parents=True, exist_ok=True)
                source.write_text("\n".join(sorted(lines)) + "\n", encoding="utf-8")
            errors: list[str] = []
            check_visible_branding(root, errors)
            check_runtime_urls(root, errors)
            self.assertEqual(errors, [])

            for relative_path in LEGAL_ATTRIBUTION_LINES:
                with (root / relative_path).open("a", encoding="utf-8") as stream:
                    stream.write('val title = "OperitTerminal"\n')
                    stream.write('val update = "https://github.com/AAswordman/Operit/releases"\n')
            check_visible_branding(root, errors)
            check_runtime_urls(root, errors)
            self.assertEqual(len(errors), len(LEGAL_ATTRIBUTION_LINES) * 2)

    def test_attribution_copy_and_modified_statement_are_not_exempt(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            relative_path, lines = next(iter(LEGAL_ATTRIBUTION_LINES.items()))
            statement = next(line for line in lines if '"OperitTerminal"' in line)
            original = root / relative_path
            original.parent.mkdir(parents=True)
            original.write_text('val update = ' + statement, encoding="utf-8")
            copy = root / "app/src/main/java/Unrelated.kt"
            copy.write_text(statement, encoding="utf-8")
            errors: list[str] = []
            check_visible_branding(root, errors)
            check_runtime_urls(root, errors)
            self.assertEqual(sum("upstream runtime URL" in error for error in errors), 2)
            self.assertEqual(sum("visible legacy brand" in error for error in errors), 4)

    def test_unquoted_legacy_name_does_not_hide_later_visible_brand(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            path = root / "app/src/main/java/Example.kt"
            path.parent.mkdir(parents=True)
            path.write_text('import example.OperitTerminal\nval title = "OperitTerminal"\n', encoding="utf-8")
            errors: list[str] = []
            check_visible_branding(root, errors)
            self.assertEqual(len(errors), 1)
            self.assertIn(":2 ", errors[0])

    def write_generated_native_fixture(self, root: Path) -> None:
        files = {
            "app/build.gradle.kts": """
abstract class BuildShellIdentityLauncherTask
args("-nostdlib++", "-Wl,-z,max-page-size=16384")
check(!bytes.containsSequence("libc++_shared.so".toByteArray()))
outputDirectory.set(layout.buildDirectory.dir("generated/shellIdentityLauncherAssets"))
assets.addGeneratedSourceDirectory(buildShellIdentityLauncher)
ndkVersion.set(providers.gradleProperty("kiyori.android.ndkVersion"))
""",
            "tools/shell_identity_launcher/native-lib.cpp": """
setgroups(1, groups);
setgid(2000);
setuid(2000);
const char *target_ctx = "u:r:shell:s0";
execvp(argv[1], &argv[1]);
""",
            "tools/shell_identity_launcher/CMakeLists.txt": """
target_link_options(operit_shell_exec PRIVATE
    -nostdlib++
    -Wl,-z,max-page-size=16384
    -Wl,--strip-all)
""",
            "tools/shell_identity_launcher/build_android.bat": """
set "NDK_PROPERTY=kiyori.android.ndkVersion"
-DANDROID_ABI=arm64-v8a
-DANDROID_PLATFORM=android-26
""",
            "terminal/src/main/java/com/ai/assistance/operit/terminal/TerminalManager.kt": """
private fun installSudoShim() {
    Files.deleteIfExists(sudoFile.toPath())
    val script = "#!/system/bin/sh"
}
""",
        }
        for relative_path, content in files.items():
            path = root / relative_path
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content.strip() + "\n", encoding="utf-8")

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

    def test_visible_legacy_terminal_ascii_banner_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = (
                root
                / "terminal/src/main/java/com/ai/assistance/operit/terminal/view/domain/OutputProcessor.kt"
            )
            source.parent.mkdir(parents=True)
            source.write_text(
                'val banner = "| |_| | |_) |  __/ |   | | |_"\n',
                encoding="utf-8",
            )
            errors: list[str] = []

            check_visible_branding(root, errors)

            self.assertEqual(len(errors), 2)
            self.assertTrue(any("product banner" in error for error in errors))
            self.assertTrue(any("screen reset" in error for error in errors))

    def test_terminal_without_product_banner_is_accepted(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = (
                root
                / "terminal/src/main/java/com/ai/assistance/operit/terminal/view/domain/OutputProcessor.kt"
            )
            source.parent.mkdir(parents=True)
            source.write_text(
                'const val INITIAL_SCREEN_RESET_SEQUENCE = "\\u001B[2J\\u001B[H"\n',
                encoding="utf-8",
            )
            errors: list[str] = []

            check_visible_branding(root, errors)

            self.assertEqual(errors, [])

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

    def test_generated_native_source_contract_is_accepted(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write_generated_native_fixture(root)
            errors: list[str] = []

            check_generated_native_inputs(root, errors)

            self.assertEqual(errors, [])

    def test_prebuilt_shell_launcher_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write_generated_native_fixture(root)
            prebuilt = root / "app/src/main/assets/operit_shell_exec"
            prebuilt.parent.mkdir(parents=True, exist_ok=True)
            prebuilt.write_bytes(b"\x7fELF")
            errors: list[str] = []

            check_generated_native_inputs(root, errors)

        self.assertEqual(len(errors), 1)
        self.assertIn("must not exist", errors[0])

    def write_native_source_pin_fixture(
        self,
        root: Path,
        *,
        llama_ref: str = "885c5bbe8e04dc78db25beb911a2715312ad7b54",
        mnn_ref: str = "ea44a3ebd5dd6348eea501047b17c43aa3ecccb6",
    ) -> None:
        files = {
            "llama/CMakeLists.txt": f'''
operit_prepare_git_source(
    LLAMA_SOURCE_DIR
    LLAMA_BINARY_DIR
    llama_cpp
    "https://github.com/ggml-org/llama.cpp.git"
    "{llama_ref}"
)
''',
            "mnn/CMakeLists.txt": f'''
operit_prepare_git_source(
    MNN_SOURCE_DIR
    MNN_BINARY_DIR
    mnn
    "https://github.com/alibaba/MNN.git"
    "{mnn_ref}"
)
''',
        }
        for relative_path, content in files.items():
            path = root / relative_path
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content.strip() + "\n", encoding="utf-8")

    def test_exact_native_source_pins_are_accepted(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write_native_source_pin_fixture(root)
            errors: list[str] = []

            check_native_source_pins(root, errors)

            self.assertEqual(errors, [])

    def test_moving_native_source_refs_are_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write_native_source_pin_fixture(root, llama_ref="master", mnn_ref="master")
            errors: list[str] = []

            check_native_source_pins(root, errors)

            self.assertEqual(len(errors), 2)
            self.assertTrue(all("must pin" in error for error in errors))

    def write_ssh_secret_transport_fixture(self, root: Path, *, use_server_env: bool) -> None:
        path = (
            root
            / "terminal/src/main/java/com/ai/assistance/operit/terminal/utils/SSHFileConnectionManager.kt"
        )
        path.parent.mkdir(parents=True, exist_ok=True)
        server_env = 'channel.setEnv("SSHPASS", config.localSshPassword)\n' if use_server_env else ""
        path.write_text(
            (
                "IFS= read -r SSHPASS\n"
                "channel.outputStream.use\n"
                "config.localSshPassword.toByteArray(Charsets.UTF_8)\n"
                "sshpass -e sshfs\n"
                "StrictHostKeyChecking=accept-new\n"
                f"{server_env}"
            ),
            encoding="utf-8",
        )

    def test_ssh_secret_transport_over_channel_stdin_is_accepted(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write_ssh_secret_transport_fixture(root, use_server_env=False)
            errors: list[str] = []

            check_ssh_secret_transport(root, errors)

            self.assertEqual(errors, [])

    def test_ssh_secret_transport_server_env_dependency_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write_ssh_secret_transport_fixture(root, use_server_env=True)
            errors: list[str] = []

            check_ssh_secret_transport(root, errors)

            self.assertEqual(len(errors), 1)
            self.assertIn("channel.setEnv", errors[0])

    def test_ci_android_platform_matching_compile_sdk_is_accepted(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            app_build = root / "app/build.gradle.kts"
            app_build.parent.mkdir(parents=True)
            app_build.write_text("android { compileSdk = 37 }\n", encoding="utf-8")
            for relative_path in (
                ".github/workflows/android-build.yml",
                ".github/workflows/pr-check.yml",
            ):
                workflow = root / relative_path
                workflow.parent.mkdir(parents=True, exist_ok=True)
                workflow.write_text('run: sdkmanager "platforms;android-37"\n', encoding="utf-8")
            errors: list[str] = []

            check_ci_android_toolchain(root, errors)

            self.assertEqual(errors, [])

    def test_stale_ci_android_platform_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            app_build = root / "app/build.gradle.kts"
            app_build.parent.mkdir(parents=True)
            app_build.write_text("android { compileSdk = 37 }\n", encoding="utf-8")
            for relative_path in (
                ".github/workflows/android-build.yml",
                ".github/workflows/pr-check.yml",
            ):
                workflow = root / relative_path
                workflow.parent.mkdir(parents=True, exist_ok=True)
                workflow.write_text('run: sdkmanager "platforms;android-36"\n', encoding="utf-8")
            errors: list[str] = []

            check_ci_android_toolchain(root, errors)

            self.assertEqual(len(errors), 2)
            self.assertTrue(all("platforms;android-37" in error for error in errors))


if __name__ == "__main__":
    unittest.main()
