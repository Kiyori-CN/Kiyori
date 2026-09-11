"""轻量 JSON Schema 校验器。

刻意不引入 jsonschema 依赖：T1 组件列表固定，本校验器只覆盖
schemas/*.json 实际用到的关键字
（type/required/properties/enum/minimum/maximum/minLength/maxLength/items/pattern/oneOf）。
"""

from __future__ import annotations

import json
import re
from pathlib import Path
from typing import Any, Dict, List, Optional

from .protocol import OfficeError

SCHEMA_DIR = Path(__file__).with_name("schemas")
SCHEMA_INDEX = SCHEMA_DIR / "commands.json"
_SCHEMA_CACHE: Dict[str, Dict[str, Any]] = {}
_INDEX_CACHE: Optional[Dict[str, Any]] = None

# 协议层内部字段：由 JS 层注入，不属于业务参数，任何命令都必须接受。
# 统一在这里放行，避免「同一字段在某些命令被拒、某些命令通过」的两层职责错位。
# 注意：in_place 等有业务语义的字段不在此列，必须由命令 schema 显式声明。
INTERNAL_FIELDS = frozenset({"allow_roots", "task_id"})

_TYPES = {
    "string": str,
    "integer": int,
    "number": (int, float),
    "boolean": bool,
    "array": list,
    "object": dict,
    "null": type(None),
}


def load_schema(name: Optional[str]) -> Optional[Dict[str, Any]]:
    if not name:
        return None
    if name in _SCHEMA_CACHE:
        return _SCHEMA_CACHE[name]
    path = SCHEMA_DIR / ("%s.json" % name)
    if path.is_file():
        try:
            schema = json.loads(path.read_text(encoding="utf-8"))
        except json.JSONDecodeError as exc:
            raise OfficeError("E_PROTOCOL", "JSON Schema 文件损坏", detail=str(exc)) from exc
    else:
        index = _load_index()
        schema = index.get(name)
        if schema is None:
            raise OfficeError(
                "E_PROTOCOL",
                "命令缺少已登记的 JSON Schema",
                detail="%s 或 %s 中的 %s" % (path, SCHEMA_INDEX, name),
            )
    _SCHEMA_CACHE[name] = schema
    return schema


def _load_index() -> Dict[str, Any]:
    """命令 Schema 注册表。

    设计文档的目录示例是「每个命令一个 schemas/*.json」；这里保留同名单文件
    优先，同时用 commands.json 集中登记，避免 48 个碎片文件难以审计。
    该差异已记录在专项 index。
    """

    global _INDEX_CACHE
    if _INDEX_CACHE is not None:
        return _INDEX_CACHE
    if not SCHEMA_INDEX.is_file():
        _INDEX_CACHE = {}
        return _INDEX_CACHE
    try:
        payload = json.loads(SCHEMA_INDEX.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        raise OfficeError(
            "E_PROTOCOL", "Schema 注册表损坏", detail=str(exc)
        ) from exc
    if not isinstance(payload, dict):
        raise OfficeError("E_PROTOCOL", "Schema 注册表根必须是对象")
    _INDEX_CACHE = payload
    return _INDEX_CACHE


def validate_args(command: str, args: Dict[str, Any], schema_name: Optional[str]) -> None:
    schema = load_schema(schema_name)
    if schema is None:
        return
    issues: List[Dict[str, Any]] = []
    _validate(schema, args, "args", issues)
    if issues:
        # 消息本身必须带字段名：宿主可能只展示 message 而丢弃 detail，
        # 之前「参数不符合 ... JSON Schema」无法自查的问题即源于此。
        summary = "; ".join(_describe(issue) for issue in issues[:5])
        if len(issues) > 5:
            summary += "（其余 %d 项见 data.issues）" % (len(issues) - 5)
        raise OfficeError(
            "E_INPUT_SCHEMA",
            "参数不符合 %s 的 JSON Schema：%s" % (command, summary),
            detail="; ".join(_describe(issue) for issue in issues[:20]),
            data={"issues": issues, "command": command},
            remedy="按 field/expected/actual 修正参数后重试；不要用字符串强转掩盖类型错误",
        )


def _describe(issue: Dict[str, Any]) -> str:
    field = issue.get("field", "args")
    reason = issue.get("reason")
    if reason == "UNKNOWN_FIELD":
        # D10：之前只说「不是已登记字段」，不给出该层级实际支持哪些字段，
        # 调用方（尤其是把字段放错层级，如把 transition 放到顶层而不是
        # slides[i] 内）只能去翻 schema 源文件。expected 此处是该层级
        # properties 的字段名列表（见下方 _issue 调用处）。
        available = issue.get("expected") or []
        hint = "、".join(available) if available else "（该层级不接受任何字段）"
        return "%s 不是已登记字段；该层级可用字段：%s" % (field, hint)
    if reason == "MISSING":
        return "%s 缺少必填字段（期望 %s）" % (field, issue.get("expected"))
    if reason == "ENUM":
        return "%s 必须是 %s 之一，实际 %r" % (
            field,
            issue.get("expected"),
            issue.get("actual"),
        )
    return "%s 期望 %s，实际 %s" % (field, issue.get("expected"), issue.get("actual"))


def _issue(
    field: str,
    reason: str,
    *,
    expected: Any = None,
    actual: Any = None,
) -> Dict[str, Any]:
    return {
        "field": field,
        "reason": reason,
        "expected": expected,
        "actual": actual,
    }


def _validate(
    schema: Dict[str, Any], value: Any, path: str, issues: List[Dict[str, Any]]
) -> None:
    if not isinstance(schema, dict):
        return
    one_of = schema.get("oneOf")
    if isinstance(one_of, list):
        for option in one_of:
            probe: List[Dict[str, Any]] = []
            _validate(option, value, path, probe)
            if not probe:
                return
        issues.append(_issue(path, "INVALID_TYPE", expected="oneOf", actual=_actual(value)))
        return

    expected = schema.get("type")
    if isinstance(expected, str):
        if not _matches_type(expected, value):
            issues.append(
                _issue(path, "INVALID_TYPE", expected=expected, actual=_actual(value))
            )
            return
    elif isinstance(expected, list):
        if not any(_matches_type(option, value) for option in expected):
            issues.append(
                _issue(path, "INVALID_TYPE", expected=expected, actual=_actual(value))
            )
            return

    enum = schema.get("enum")
    if isinstance(enum, list) and value not in enum:
        issues.append(_issue(path, "ENUM", expected=enum, actual=value))

    if isinstance(value, str):
        min_length = schema.get("minLength")
        if isinstance(min_length, int) and len(value) < min_length:
            issues.append(
                _issue(path, "TOO_SHORT", expected="length>=%d" % min_length, actual=len(value))
            )
        max_length = schema.get("maxLength")
        if isinstance(max_length, int) and len(value) > max_length:
            issues.append(
                _issue(path, "TOO_LONG", expected="length<=%d" % max_length, actual=len(value))
            )
        pattern = schema.get("pattern")
        if isinstance(pattern, str) and not re.search(pattern, value):
            issues.append(_issue(path, "PATTERN", expected=pattern, actual=value))

    if isinstance(value, (int, float)) and not isinstance(value, bool):
        minimum = schema.get("minimum")
        if isinstance(minimum, (int, float)) and value < minimum:
            issues.append(_issue(path, "TOO_SMALL", expected=minimum, actual=value))
        maximum = schema.get("maximum")
        if isinstance(maximum, (int, float)) and value > maximum:
            issues.append(_issue(path, "TOO_LARGE", expected=maximum, actual=value))

    if isinstance(value, list) and isinstance(schema.get("items"), dict):
        for index, item in enumerate(value):
            _validate(schema["items"], item, "%s[%d]" % (path, index), issues)

    if isinstance(value, dict):
        properties = schema.get("properties")
        properties = properties if isinstance(properties, dict) else {}
        for key in schema.get("required") or []:
            if key not in value:
                field_schema = properties.get(key) or {}
                issues.append(
                    _issue(
                        "%s.%s" % (path, key),
                        "MISSING",
                        expected=field_schema.get("type", "any"),
                        actual=None,
                    )
                )
        additional = schema.get("additionalProperties", True)
        for key, item in value.items():
            if key in INTERNAL_FIELDS or key.startswith("__"):
                continue
            if key in properties:
                _validate(properties[key], item, "%s.%s" % (path, key), issues)
            elif additional is False:
                issues.append(
                    _issue(
                        "%s.%s" % (path, key),
                        "UNKNOWN_FIELD",
                        expected=sorted(properties),
                        actual=_actual(item),
                    )
                )


def _actual(value: Any) -> str:
    if isinstance(value, bool):
        return "boolean"
    if isinstance(value, int):
        return "integer"
    if isinstance(value, float):
        return "number"
    if isinstance(value, str):
        return "string"
    if isinstance(value, list):
        return "array"
    if isinstance(value, dict):
        return "object"
    if value is None:
        return "null"
    return type(value).__name__


def _matches_type(name: str, value: Any) -> bool:
    python_type = _TYPES.get(name)
    if python_type is None:
        return True
    if name in ("integer", "number") and isinstance(value, bool):
        return False
    return isinstance(value, python_type)
