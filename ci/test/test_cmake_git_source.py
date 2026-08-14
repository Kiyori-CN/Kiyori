from __future__ import annotations

import os
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]


def find_cmake() -> str | None:
    executable = shutil.which("cmake")
    if executable is not None:
        return executable

    executable_name = "cmake.exe" if os.name == "nt" else "cmake"
    sdk_roots = {
        Path(value)
        for key in ("ANDROID_HOME", "ANDROID_SDK_ROOT")
        if (value := os.environ.get(key))
    }
    for sdk_root in sdk_roots:
        candidates = sorted(
            (sdk_root / "cmake").glob(f"*/bin/{executable_name}"),
            reverse=True,
        )
        if candidates:
            return str(candidates[0])

    return None


class CMakeGitSourceTest(unittest.TestCase):
    def test_commit_sha_detection_is_exact(self) -> None:
        cmake = find_cmake()
        if cmake is None:
            self.skipTest("cmake is not available")

        helper = (REPO_ROOT / "cmake" / "operit_git_source.cmake").as_posix()
        script = f'''
include("{helper}")

function(assert_commit_sha token expected)
    operit_is_commit_sha(actual "${{token}}")
    if(expected AND NOT actual)
        message(FATAL_ERROR "Expected commit SHA: ${{token}}")
    endif()
    if(NOT expected AND actual)
        message(FATAL_ERROR "Unexpected commit SHA: ${{token}}")
    endif()
endfunction()

assert_commit_sha("0123456789abcdef0123456789abcdef01234567" TRUE)
assert_commit_sha("ABCDEF0123456789ABCDEF0123456789ABCDEF01" TRUE)
assert_commit_sha("0123456789abcdef0123456789abcdef0123456" FALSE)
assert_commit_sha("0123456789abcdef0123456789abcdef012345678" FALSE)
assert_commit_sha("0123456789abcdef0123456789abcdef0123456g" FALSE)
'''

        with tempfile.TemporaryDirectory() as directory:
            script_path = Path(directory) / "test_commit_sha.cmake"
            script_path.write_text(script.strip() + "\n", encoding="utf-8")
            result = subprocess.run(
                [cmake, "-P", str(script_path)],
                cwd=REPO_ROOT,
                capture_output=True,
                text=True,
            )

        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)


if __name__ == "__main__":
    unittest.main()
