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
    errors: List[str] = []
    _validate(schema, args, "args", errors)
    if errors:
        raise OfficeError(
            "E_INPUT_SCHEMA",
            "参数不符合 %s 的 JSON Schema" % command,
            detail="; ".join(errors[:20]),
            remedy="按 detail 修正参数后重试；不要用字符串强转掩盖类型错误",
        )


def _validate(schema: Dict[str, Any], value: Any, path: str, errors: List[str]) -> None:
    if not isinstance(schema, dict):
        return
    one_of = schema.get("oneOf")
    if isinstance(one_of, list):
        for option in one_of:
            probe: List[str] = []
            _validate(option, value, path, probe)
            if not probe:
                return
        errors.append("%s 不满足 oneOf 中的任一分支" % path)
        return

    expected = schema.get("type")
    if isinstance(expected, str):
        if not _matches_type(expected, value):
            errors.append("%s 期望 %s，实际 %s" % (path, expected, type(value).__name__))
            return
    elif isinstance(expected, list):
        if not any(_matches_type(option, value) for option in expected):
            errors.append("%s 期望 %s，实际 %s" % (path, expected, type(value).__name__))
            return

    enum = schema.get("enum")
    if isinstance(enum, list) and value not in enum:
        errors.append("%s 必须是 %s 之一，实际 %r" % (path, enum, value))

    if isinstance(value, str):
        min_length = schema.get("minLength")
        if isinstance(min_length, int) and len(value) < min_length:
            errors.append("%s 长度小于 %d" % (path, min_length))
        max_length = schema.get("maxLength")
        if isinstance(max_length, int) and len(value) > max_length:
            errors.append("%s 长度大于 %d" % (path, max_length))
        pattern = schema.get("pattern")
        if isinstance(pattern, str) and not re.search(pattern, value):
            errors.append("%s 不匹配 pattern %s" % (path, pattern))

    if isinstance(value, (int, float)) and not isinstance(value, bool):
        minimum = schema.get("minimum")
        if isinstance(minimum, (int, float)) and value < minimum:
            errors.append("%s 小于最小值 %s" % (path, minimum))
        maximum = schema.get("maximum")
        if isinstance(maximum, (int, float)) and value > maximum:
            errors.append("%s 大于最大值 %s" % (path, maximum))

    if isinstance(value, list) and isinstance(schema.get("items"), dict):
        for index, item in enumerate(value):
            _validate(schema["items"], item, "%s[%d]" % (path, index), errors)

    if isinstance(value, dict):
        properties = schema.get("properties")
        properties = properties if isinstance(properties, dict) else {}
        for key in schema.get("required") or []:
            if key not in value:
                errors.append("%s.%s 是必填字段" % (path, key))
        additional = schema.get("additionalProperties", True)
        for key, item in value.items():
            if key in properties:
                _validate(properties[key], item, "%s.%s" % (path, key), errors)
            elif additional is False:
                errors.append("%s.%s 不是已登记字段" % (path, key))


def _matches_type(name: str, value: Any) -> bool:
    python_type = _TYPES.get(name)
    if python_type is None:
        return True
    if name in ("integer", "number") and isinstance(value, bool):
        return False
    return isinstance(value, python_type)
