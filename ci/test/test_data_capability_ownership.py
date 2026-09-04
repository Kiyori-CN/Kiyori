from __future__ import annotations

import json
import sys
import tempfile
import tomllib
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO_ROOT / "ci" / "script"))
from check_architecture_boundaries import check_ownership  # noqa: E402


class DataCapabilityOwnershipTest(unittest.TestCase):
    def check(self, owner_id: str, dependency: str) -> list[str]:
        config = tomllib.loads(
            (REPO_ROOT / "config/architecture/package-ownership.toml").read_text(encoding="utf-8")
        )
        record = next(item for item in config["ownership"] if item["id"] == owner_id)
        relative_path = record["path"].replace("**", "Contract.kt")
        package = ".".join(Path(relative_path).relative_to("app/src/main/java").parent.parts)
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / relative_path
            source.parent.mkdir(parents=True)
            source.write_text(f"package {package}\nimport {dependency}\n", encoding="utf-8")
            ownership = root / "ownership.toml"
            ownership.write_text(
                "schema_version = 1\n[[ownership]]\n"
                + "\n".join(f"{key} = {json.dumps(value)}" for key, value in record.items()),
                encoding="utf-8",
            )
            errors: list[str] = []
            check_ownership(root, ownership, errors)
            return errors

    def test_data_owners_can_consume_their_shared_capability(self) -> None:
        for owner, dependency in (
            ("memory-repository-data", "com.kiyori.capability.ai.memory.MemoryGraph"),
            ("market-api-data", "com.kiyori.capability.extensions.market.normalizeMarketArtifactId"),
        ):
            with self.subTest(owner=owner):
                self.assertEqual(self.check(owner, dependency), [])

    def test_data_owners_reject_ui_and_compose_dependencies(self) -> None:
        for owner in ("memory-repository-data", "market-api-data"):
            for dependency in (
                "com.ai.assistance.operit.ui.features.memory.screens.graph.model.Graph",
                "androidx.compose.ui.graphics.Color",
            ):
                with self.subTest(owner=owner, dependency=dependency):
                    errors = self.check(owner, dependency)
                    self.assertEqual(len(errors), 1, errors)
                    self.assertIn("forbidden import", errors[0])

    def test_pure_capabilities_reject_platform_and_ui_dependencies(self) -> None:
        for owner in ("kiyori-capability-ai", "kiyori-capability-extensions"):
            for dependency in (
                "android.content.Context",
                "androidx.compose.ui.graphics.Color",
                "com.ai.assistance.operit.ui.features.packages.market.ArtifactMarketMetadata",
            ):
                with self.subTest(owner=owner, dependency=dependency):
                    errors = self.check(owner, dependency)
                    self.assertEqual(len(errors), 1, errors)
                    self.assertTrue(errors[0].startswith("ARCH003 "), errors)


if __name__ == "__main__":
    unittest.main()
