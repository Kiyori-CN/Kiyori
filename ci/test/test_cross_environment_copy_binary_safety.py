"""跨环境复制的二进制安全门禁。

历史故障（2026-09-09 真机复测 P0）：`copyFileCrossEnvironment` 用
`FileSystemProvider.readFile` 把 Linux 侧文件按文本读取，再用 UTF-8 重新编码写出。
docx/xlsx/pptx/jpg 等二进制产物因此被替换成 U+FFFD、体积变大，但工具仍返回
success，交付物在用户侧完全打不开。这里锁死该实现必须使用字节 API。
"""

from __future__ import annotations

import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
STANDARD_FILE_SYSTEM_TOOLS = (
    REPO_ROOT
    / "app"
    / "src"
    / "main"
    / "java"
    / "com"
    / "ai"
    / "assistance"
    / "operit"
    / "core"
    / "tools"
    / "defaultTool"
    / "standard"
    / "StandardFileSystemTools.kt"
)


class CrossEnvironmentCopyBinarySafetyTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.source = STANDARD_FILE_SYSTEM_TOOLS.read_text(encoding="utf-8")

    def test_cross_environment_copy_uses_byte_api(self) -> None:
        start = self.source.index("private suspend fun copyFileCrossEnvironment(")
        end = self.source.index("private suspend fun copyDirectoryCrossEnvironment(")
        block = self.source[start:end]

        self.assertIn(
            "readFileBytes(",
            block,
            "跨环境复制必须用 readFileBytes 读取源文件，否则二进制产物会被文本解码损坏",
        )
        self.assertNotIn(
            "readFile(sourcePath)",
            block,
            "跨环境复制不得使用文本读取 API readFile(sourcePath)",
        )
        self.assertNotIn(
            "toByteArray(Charsets.UTF_8)",
            block,
            "跨环境复制不得把读取结果再按 UTF-8 编码，这会改变原始字节",
        )

    def test_cross_environment_copy_writes_bytes_for_linux_target(self) -> None:
        start = self.source.index("private suspend fun copyFileCrossEnvironment(")
        end = self.source.index("private suspend fun copyDirectoryCrossEnvironment(")
        block = self.source[start:end]

        self.assertIn(
            "writeFileBytes(finalDestPath, bytes)",
            block,
            "写回 Linux 目标必须用 writeFileBytes 保持字节原样",
        )


if __name__ == "__main__":
    unittest.main()
