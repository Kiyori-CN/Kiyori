"""sentinel 信封、错误码与参数 Schema。"""

from __future__ import annotations

import json
from pathlib import Path

import pytest

from kiyori_office import protocol
from kiyori_office.protocol import END, BEGIN, OfficeError


def test_error_codes_are_registered_and_reject_unknown():
    with pytest.raises(ValueError):
        OfficeError("E_NOT_REGISTERED", "boom")
    assert "E_ENV_MISSING" in protocol.ERROR_CODES


def test_parse_sentinel_tolerates_noise():
    payload = {"ok": True, "command": "office_env_check"}
    noisy = "pip is outdated\n" + BEGIN + json.dumps(payload) + END + "\nsoffice warning\n"
    assert protocol.parse_sentinel(noisy) == payload


def test_parse_sentinel_reports_protocol_error():
    with pytest.raises(OfficeError) as excinfo:
        protocol.parse_sentinel("no markers here")
    assert excinfo.value.code == "E_PROTOCOL"


def test_unknown_command_is_input_schema_error():
    result = protocol.run("office_not_a_command", {})
    assert result["ok"] is False
    assert result["error"]["code"] == "E_INPUT_SCHEMA"


def test_missing_required_argument_is_reported():
    result = protocol.run("docx_outline", {})
    assert result["ok"] is False
    assert result["error"]["code"] == "E_INPUT_SCHEMA"
    assert "path" in result["error"]["detail"]


def test_every_command_has_a_schema():
    from kiyori_office.argspec import load_schema

    missing = [
        name for name, entry in protocol.commands().items() if load_schema(entry.schema) is None
    ]
    assert missing == []


def test_cli_emits_single_envelope(tmp_path, capsys):
    args_file = tmp_path / "args.json"
    args_file.write_text(json.dumps({"verbose": False}), encoding="utf-8")
    exit_code = protocol.main(["office_env_check", "--args-file", str(args_file)])
    assert exit_code == 0
    captured = capsys.readouterr().out
    payload = protocol.parse_sentinel(captured)
    assert payload["ok"] is True
    assert payload["command"] == "office_env_check"
    assert captured.count(BEGIN) == 1
