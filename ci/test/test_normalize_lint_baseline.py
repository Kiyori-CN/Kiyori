from __future__ import annotations

import sys
import unittest
from pathlib import Path
from xml.etree import ElementTree


REPO_ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO_ROOT / "ci" / "script"))

from normalize_lint_baseline import normalize, prune_stale_issues  # noqa: E402


def baseline(*issues: str) -> str:
    body = "".join(issues)
    return (
        '<?xml version="1.0" encoding="UTF-8"?>\n'
        '<issues format="6" by="lint test">\n\n'
        f"{body}"
        "</issues>\n"
    )


def issue(issue_id: str, message: str, file: str, line: int) -> str:
    return (
        "    <issue\n"
        f'        id="{issue_id}"\n'
        f'        message="{message}">\n'
        "        <location\n"
        f'            file="{file}"\n'
        f'            line="{line}"\n'
        '            column="1"/>\n'
        "    </issue>\n\n"
    )


class NormalizeLintBaselineTest(unittest.TestCase):
    def test_environment_specific_version_catalog_path_is_normalized(self) -> None:
        source = '<location file="$HOME/runner/gradle/libs.versions.toml"/>'

        self.assertEqual(
            normalize(source),
            '<location file="../gradle/libs.versions.toml"/>',
        )

    def test_prune_keeps_only_reviewed_issues_still_reported(self) -> None:
        reviewed = baseline(
            issue("StillPresent", "same problem", "src/main/A.kt", 10),
            issue("Fixed", "old problem", "src/main/B.kt", 20),
        )
        current = baseline(
            issue("StillPresent", "same problem", "src/main/A.kt", 35),
            issue("NewProblem", "new problem", "src/main/C.kt", 40),
        )

        output, stale, current_only, retained = prune_stale_issues(reviewed, current)
        root = ElementTree.fromstring(output)
        output_issues = root.findall("issue")

        self.assertEqual((stale, current_only, retained), (1, 1, 1))
        self.assertEqual([item.attrib["id"] for item in output_issues], ["StillPresent"])
        self.assertEqual(output_issues[0].find("location").attrib["line"], "10")

    def test_prune_preserves_duplicate_counts_without_absorbing_more(self) -> None:
        reviewed = baseline(issue("Duplicate", "same", "src/main/A.kt", 10))
        current = baseline(
            issue("Duplicate", "same", "src/main/A.kt", 20),
            issue("Duplicate", "same", "src/main/A.kt", 30),
        )

        output, stale, current_only, retained = prune_stale_issues(reviewed, current)
        root = ElementTree.fromstring(output)

        self.assertEqual((stale, current_only, retained), (0, 1, 1))
        self.assertEqual(root.find("issue/location").attrib["line"], "10")


if __name__ == "__main__":
    unittest.main()
