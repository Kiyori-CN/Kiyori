from __future__ import annotations

import json
import re
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch
import zipfile
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO_ROOT / "tools" / "example_packages"))

from sync_example_packages import (  # noqa: E402
    _manifest_runtime_files,
    _npm_executable,
    _pack_toolpkg_folder,
    _read_whitelist_file,
    _resolve_plan_item_from_roots,
    _prebuild_examples,
    _prebuild_planned_child_names,
    SyncPlanItem,
)


class ToolPkgRuntimeFilesTest(unittest.TestCase):
    def test_copied_bundle_builds_its_source_project_and_invalidates_on_source_changes(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            examples = root / "examples"
            child = examples / "bundle"
            child.mkdir(parents=True)
            (examples / "tsconfig.json").write_text("{}", encoding="utf-8")
            (child / "tsconfig.json").write_text("{}", encoding="utf-8")
            (child / "package.json").write_text('{"scripts":{"build":"node build.js"}}', encoding="utf-8")
            source = child / "index.ts"
            source.write_text("export const value = 1;", encoding="utf-8")
            plan = SyncPlanItem("copy", examples / "bundle.js", "bundle.js")
            state = {}
            self.assertEqual(_prebuild_planned_child_names(examples, [plan]), {"bundle"})
            with patch("sync_example_packages._run_checked_command") as run:
                _prebuild_examples(root, examples, [plan], dry_run=False, local_state=state)
                self.assertEqual(run.call_count, 2)
                self.assertEqual(run.call_args.args[0][-2:], ["run", "build"])
                self.assertEqual(run.call_args.kwargs["cwd"], child)
                run.reset_mock()
                _prebuild_examples(root, examples, [plan], dry_run=False, local_state=state)
                run.assert_not_called()
                source.write_text("export const value = 2;", encoding="utf-8")
                _prebuild_examples(root, examples, [plan], dry_run=False, local_state=state)
                self.assertEqual(run.call_count, 2)

    def test_npm_executable_uses_windows_command_shim(self) -> None:
        self.assertEqual(_npm_executable("win32"), "npm.cmd")

    def test_npm_executable_uses_posix_command(self) -> None:
        self.assertEqual(_npm_executable("linux"), "npm")

    def test_production_copy_assets_match_their_whitelisted_sources(self) -> None:
        whitelist = _read_whitelist_file(
            REPO_ROOT / "tools" / "example_packages" / "packages_whitelist.txt"
        )
        source_roots = [REPO_ROOT / "examples", REPO_ROOT]
        packages_dir = REPO_ROOT / "app" / "src" / "main" / "assets" / "packages"

        for item in whitelist:
            with self.subTest(item=item):
                plan = _resolve_plan_item_from_roots(source_roots, item)
                self.assertIsNotNone(plan, f"Production ToolPkg whitelist item is missing: {item}")
                if plan is None or plan.mode != "copy":
                    continue

                destination = packages_dir / plan.destination_name
                self.assertTrue(
                    destination.is_file(),
                    f"Production ToolPkg asset is missing: {destination}",
                )
                self.assertEqual(
                    plan.source.read_bytes(),
                    destination.read_bytes(),
                    f"Production ToolPkg asset drifted from its source: {plan.destination_name}",
                )

    def test_code_runner_keeps_persistent_terminal_cwd_valid(self) -> None:
        source = (REPO_ROOT / "examples" / "code_runner.ts").read_text(encoding="utf-8")

        self.assertIn('return `(cd ${directoryArgument} && ${command})`;', source)
        self.assertIn('executeTerminalCommand(buildSubshellCommand(tempDirPath, `go build ${buildFlags} -o main main.go`))', source)
        self.assertIn('executeTerminalCommand(buildSubshellCommand(tempDirPath, `${RUST_TOOLCHAIN_ENV} && ${CARGO_MIRROR_ENV} && cargo build ${cargoFlags}`))', source)
        self.assertNotRegex(source, r'executeTerminalCommand\(`cd \$\{temp(?:DirPath|GoDir|RustDir)\}')

    def test_code_runner_python_environment_commands_use_stable_home(self) -> None:
        source = (REPO_ROOT / "examples" / "code_runner.ts").read_text(encoding="utf-8")

        self.assertGreaterEqual(source.count("executeFromHome("), 8)
        self.assertIn("const exists = await executeFromHome", source)
        self.assertIn("const setup = await executeFromHome", source)
        self.assertIn("const r2 = await executeFromHome", source)
        self.assertIn("const result = await executeFromHome", source)
        self.assertIn("const result = await executeInArtifactDirectory(`${pythonBin}", source)
        self.assertIn('await Tools.Files.write(path, normalized, false, "linux")', source)
        self.assertNotIn("executeTerminalCommand(`${pythonBin} ${pythonFlags} '${escapedTempFilePath}'", source)

    def test_code_runner_rust_requires_rustc_and_cargo_from_one_explicit_path(self) -> None:
        source = (REPO_ROOT / "examples" / "code_runner.ts").read_text(encoding="utf-8")

        self.assertIn('const RUST_TOOLCHAIN_ENV = \'export PATH="$HOME/.cargo/bin:$PATH"\';', source)
        self.assertIn("rustc --version && cargo --version", source)
        self.assertGreaterEqual(source.count("${RUST_TOOLCHAIN_ENV} && ${CARGO_MIRROR_ENV} && cargo build"), 3)

    def test_code_runner_source_writes_do_not_pass_through_pty(self) -> None:
        source = (REPO_ROOT / "examples" / "code_runner.ts").read_text(encoding="utf-8")

        self.assertNotIn("buildWriteFileCommand", source)
        self.assertNotIn("__CODE_RUNNER_FILE_", source)
        self.assertIn("if (!result.successful)", source)

    def test_code_runner_javascript_files_use_ubuntu_and_capture_completion_values(self) -> None:
        source = (REPO_ROOT / "examples" / "code_runner.ts").read_text(encoding="utf-8")

        self.assertIn("return eval(${JSON.stringify(script)});", source)
        self.assertIn("const executeFunctionBody = new Function('console'", source)
        self.assertIn("const fileResult = await executeTerminalCommand(`cat '${escapedPath}'`);", source)
        self.assertNotIn("Tools.Files.read(filePath)", source)

    def test_terminal_manager_passes_host_timezone_to_every_isolated_ubuntu_entry(self) -> None:
        source = (REPO_ROOT / "terminal" / "src" / "main" / "java" / "com" / "ai" / "assistance" / "operit" / "terminal" / "TerminalManager.kt").read_text(encoding="utf-8")

        self.assertIn("val hostTimeZone = TimeZone.getDefault().id", source)
        self.assertGreaterEqual(source.count("TZ=$hostTimeZone"), 6)

    def test_super_admin_uses_typed_timeouts_and_unique_background_sessions(self) -> None:
        source = (REPO_ROOT / "examples" / "super_admin.ts").read_text(encoding="utf-8")

        self.assertRegex(source, r'"name": "background"[\s\S]*?"type": "boolean"')
        self.assertRegex(source, r'"name": "timeoutMs"[\s\S]*?"type": "number"')
        self.assertIn("function parseTimeout(timeoutMs: number | undefined, defaultTimeoutMs: number): number", source)
        self.assertIn("Number.isInteger(timeoutMs)", source)
        self.assertIn("backgroundSessionSequence += 1", source)
        self.assertIn("background !== undefined && typeof background !== \"boolean\"", source)
        self.assertIn("isBackground && timeoutMs !== undefined", source)
        self.assertIn("timeoutMsIgnored = parseTimeout(timeoutMs, MIN_TIMEOUT_MS)", source)
        self.assertIn(
            'await Tools.System.terminal.exec(sessionId, command, undefined, { timeoutPolicy: "none" });',
            source,
        )
        self.assertEqual(source.count("await Tools.System.terminal.exec(sessionId, command, timeout);"), 1)
        self.assertIn('timeoutPolicy: timeoutMsIgnored === undefined ? "none" : "ignored"', source)
        self.assertIn("waitScope: \"shell_idle\"", source)
        self.assertIn("output_bytes", source)
        self.assertIn("output_lines", source)
        self.assertIn("output_is_preview", source)
        self.assertIn("params.input.length === 0", source)

    def test_sensitive_toolpkg_values_are_not_written_to_internal_console_logs(self) -> None:
        super_admin = (REPO_ROOT / "examples" / "super_admin.ts").read_text(encoding="utf-8")
        file_converter = (REPO_ROOT / "examples" / "file_converter.ts").read_text(encoding="utf-8")

        self.assertIn("describeSensitiveTextForLog(command)", super_admin)
        self.assertNotIn("执行终端命令: ${command}", super_admin)
        self.assertNotIn("执行Shell命令: ${command}", super_admin)
        self.assertNotIn("console.error(error.stack)", super_admin)

        self.assertIn("describeSensitiveTextForLog(converter.command)", file_converter)
        self.assertIn("describeSensitiveTextForLog(updateResult.output)", file_converter)
        self.assertIn("describeSensitiveTextForLog(installResult.output)", file_converter)
        self.assertNotIn("Executing conversion command: ${converter.command}", file_converter)
        self.assertNotIn("Output: ${updateResult.output}", file_converter)
        self.assertNotIn("${installResult.output}", file_converter)
        self.assertNotIn('console.error(`Function ${func.name} failed: ${message}`, error)', file_converter)

    def test_ignored_runtime_files_are_included_in_archive(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            repository = Path(directory)
            subprocess.run(
                ["git", "init", "-b", "main"],
                cwd=repository,
                check=True,
                capture_output=True,
            )
            package = repository / "example"
            package.mkdir()
            (package / "modules").mkdir()
            (package / ".gitignore").write_text("main.js\nmodules/\n", encoding="utf-8")
            (package / "manifest.json").write_text(
                json.dumps(
                    {
                        "toolpkg_id": "com.operit.test",
                        "main": "main.js",
                        "wasm_modules": [{"id": "core", "path": "modules/core.wasm"}],
                    }
                ),
                encoding="utf-8",
            )
            (package / "main.js").write_text("exports.test = true;\n", encoding="utf-8")
            (package / "modules" / "core.wasm").write_bytes(b"\x00asm")
            archive = repository / "example.toolpkg"

            _pack_toolpkg_folder(repository, package, archive)

            with zipfile.ZipFile(archive) as stream:
                names = set(stream.namelist())
            self.assertIn("main.js", names)
            self.assertIn("modules/core.wasm", names)

    def test_missing_runtime_file_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            package = Path(directory)
            (package / "manifest.json").write_text(
                json.dumps({"toolpkg_id": "com.operit.test", "main": "dist/main.js"}),
                encoding="utf-8",
            )

            with self.assertRaisesRegex(FileNotFoundError, "Missing ToolPkg runtime file"):
                _manifest_runtime_files(package)

    def test_runtime_path_cannot_escape_package(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            package = Path(directory)
            (package / "manifest.json").write_text(
                json.dumps({"toolpkg_id": "com.operit.test", "main": "../outside.js"}),
                encoding="utf-8",
            )

            with self.assertRaisesRegex(ValueError, "escapes the package directory"):
                _manifest_runtime_files(package)

    def test_runtime_symlink_cannot_escape_package(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            package = root / "package"
            package.mkdir()
            outside = root / "outside.js"
            outside.write_text("outside\n", encoding="utf-8")
            (package / "main.js").symlink_to(outside)
            (package / "manifest.json").write_text(
                json.dumps({"toolpkg_id": "com.operit.test", "main": "main.js"}),
                encoding="utf-8",
            )

            with self.assertRaisesRegex(ValueError, "symbolic link"):
                _manifest_runtime_files(package)


if __name__ == "__main__":
    unittest.main()
