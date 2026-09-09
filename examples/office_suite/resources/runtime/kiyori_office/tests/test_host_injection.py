"""回归：宿主注入字段与工具参数白名单。

历史故障（真机测试）：

- `office_env_check` 任何参数都失败 —— JS 层把宿主注入的 `__operit_*`、
  `containerPackageName`、`toolPkgId` 原样转发给 Python，被
  `additionalProperties: false` 拒绝；
- 同一参数时好时坏 —— 这些注入项只在部分调用上下文出现；
- 包层错误没有字段名 —— 消息里只有「参数不符合 ... JSON Schema」。

本文件锁死修复后的行为，避免再次漂移。
"""

from __future__ import annotations

from kiyori_office import protocol
from kiyori_office.argspec import INTERNAL_FIELDS

HOST_INJECTED = [
    "__operit_package_name",
    "__operit_package_state",
    "__operit_package_caller_name",
    "__operit_package_chat_id",
    "__operit_package_caller_card_id",
    "__operit_toolpkg_runtime_kind",
    "__operit_toolpkg_subpackage_id",
    "__operit_ui_package_name",
    "__operit_script_screen",
    "__operit_execution_context_key",
    "containerPackageName",
    "toolPkgId",
]


def test_internal_fields_are_accepted_by_every_command():
    """task_id / allow_roots 是协议层字段，任何命令都必须接受。"""

    assert INTERNAL_FIELDS == frozenset({"allow_roots", "task_id"})


def test_office_env_check_accepts_js_layer_payload():
    """最小复现集 1：office_env_check 之前任何参数都失败。"""

    for payload in (
        {},
        {"verbose": True},
        {"verbose": False},
        {"task_id": "t1", "allow_roots": ["/tmp/out"]},
        {"verbose": True, "task_id": "t1", "allow_roots": ["/tmp/out"]},
    ):
        result = protocol.run("office_env_check", payload)
        assert result["ok"] is True, (payload, result)


def test_python_schema_ignores_double_underscore_injection():
    """即使 JS 层漏过滤，`__` 前缀的宿主字段也不应导致校验失败。"""

    payload = {"verbose": True}
    payload.update({name: "x" for name in HOST_INJECTED if name.startswith("__")})
    result = protocol.run("office_env_check", payload)
    assert result["ok"] is True, result


def test_unknown_business_field_reports_field_name():
    """包层错误必须给出字段名与原因，而不是只有一句「不符合 Schema」。"""

    result = protocol.run("office_env_check", {"verbose": True, "typo_field": 1})
    assert result["ok"] is False
    error = result["error"]
    assert error["code"] == "E_INPUT_SCHEMA"
    assert "typo_field" in error["message"]
    assert "不是已登记字段" in error["message"]
    issues = result["data"]["issues"]
    assert issues[0]["field"] == "args.typo_field"
    assert issues[0]["reason"] == "UNKNOWN_FIELD"
    assert issues[0]["actual"] == "integer"


def test_missing_required_field_reports_field_name():
    result = protocol.run("docx_outline", {})
    assert result["ok"] is False
    assert "args.path" in result["error"]["message"]
    assert "MISSING" == result["data"]["issues"][0]["reason"]


def test_enum_mismatch_reports_expected_and_actual():
    result = protocol.run(
        "office_convert",
        {"from_path": "a.md", "to_format": "docx", "engine": "word", "allow_roots": ["/tmp"]},
    )
    assert result["ok"] is False
    issue = result["data"]["issues"][0]
    assert issue["reason"] == "ENUM"
    assert "libreoffice" in str(issue["expected"])
    assert issue["actual"] == "word"


def test_write_commands_accept_documented_payloads(tmp_path):
    """最小复现集 5：写操作按文档参数调用不应被 schema 挡掉。"""

    allow = {"allow_roots": [str(tmp_path)]}

    docx = protocol.run(
        "docx_create",
        {"markdown": "# t", "output_path": str(tmp_path / "a.docx"), **allow},
    )
    assert docx["ok"] is True, docx

    pptx = protocol.run(
        "pptx_create",
        {"slides": [{"title": "t", "bullets": ["a"]}], "output_path": str(tmp_path / "a.pptx"), **allow},
    )
    assert pptx["ok"] is True, pptx

    pdf = protocol.run(
        "pdf_create",
        {
            "engine": "reportlab",
            "blocks": [{"type": "paragraph", "text": "t"}],
            "output_path": str(tmp_path / "a.pdf"),
            **allow,
        },
    )
    assert pdf["ok"] is True, pdf


def test_xlsx_write_without_path_creates_new_workbook(tmp_path):
    """最小复现集 4：xlsx_write 省略 path 时新建工作簿，不应报 path MISSING。"""

    result = protocol.run(
        "xlsx_write",
        {
            "file_name": "a.xlsx",
            "rows": [[1, 2]],
            "start_cell": "A1",
            "output_path": str(tmp_path / "a.xlsx"),
            "allow_roots": [str(tmp_path)],
        },
    )
    assert result["ok"] is True, result
    assert result["data"]["cells_written"] == 2


def test_optional_parameters_are_accepted():
    """最小复现集 2 的补充：可选参数不应被 additionalProperties 误伤。"""

    result = protocol.run(
        "office_env_setup",
        {"tier": 1, "components": ["openpyxl"], "confirm": False, "visible": True, "timeout_ms": 60000},
    )
    assert result["ok"] is True, result
    assert result["data"]["executed"] is False
