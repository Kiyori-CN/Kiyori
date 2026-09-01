from __future__ import annotations

import json
import re
import subprocess
import sys
import tempfile
import unittest
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
)


class ToolPkgRuntimeFilesTest(unittest.TestCase):
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
        self.assertIn("const result = await executeFromHome(`${pythonBin}", source)
        self.assertIn("const result = await executeFromHome(buildWriteFileCommand", source)
        self.assertNotIn("executeTerminalCommand(`${pythonBin} ${pythonFlags} '${escapedTempFilePath}'", source)

    def test_code_runner_rust_requires_rustc_and_cargo_from_one_explicit_path(self) -> None:
        source = (REPO_ROOT / "examples" / "code_runner.ts").read_text(encoding="utf-8")

        self.assertIn('const RUST_TOOLCHAIN_ENV = \'export PATH="$HOME/.cargo/bin:$PATH"\';', source)
        self.assertIn("rustc --version && cargo --version", source)
        self.assertGreaterEqual(source.count("${RUST_TOOLCHAIN_ENV} && ${CARGO_MIRROR_ENV} && cargo build"), 3)

    def test_code_runner_heredoc_delimiter_is_standalone_before_subshell_close(self) -> None:
        source = (REPO_ROOT / "examples" / "code_runner.ts").read_text(encoding="utf-8")

        self.assertIn(
            "return `cat > ${pathArgument} <<'${marker}'\\n${body}${marker}\\n`;",
            source,
        )
        self.assertNotIn(
            "return `cat > ${pathArgument} <<'${marker}'\\n${body}${marker}`;",
            source,
        )

    def test_super_admin_uses_typed_timeouts_and_unique_background_sessions(self) -> None:
        source = (REPO_ROOT / "examples" / "super_admin.ts").read_text(encoding="utf-8")

        self.assertRegex(source, r'"name": "background"[\s\S]*?"type": "boolean"')
        self.assertRegex(source, r'"name": "timeoutMs"[\s\S]*?"type": "number"')
        self.assertIn("function parseTimeout(timeoutMs: number | undefined, defaultTimeoutMs: number): number", source)
        self.assertIn("Number.isInteger(timeoutMs)", source)
        self.assertIn("backgroundSessionSequence += 1", source)
        self.assertIn("background !== undefined && typeof background !== \"boolean\"", source)
        self.assertIn("isBackground && timeoutMs !== undefined", source)
        self.assertIn("Tools.System.terminal.exec(sessionId, command, timeout)", source)
        self.assertIn("params.input.length === 0", source)

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
