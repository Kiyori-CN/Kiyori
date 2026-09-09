"""统一 CLI 协议：sentinel 信封、固定错误码、命令注册与分发。

约束（设计文档 §3.2/§3.3）：

- stdout 只输出一段被 sentinel 包裹的 JSON，其余输出一律视为噪声；
- 失败必须返回已登记的错误码，不允许临时新增未登记的码；
- 格式逻辑一律在 Python 侧，JS 层只做参数校验与结果归一化。
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import time
import traceback
from pathlib import Path
from typing import Any, Callable, Dict, Iterable, List, Optional

BEGIN = "__KIYORI_OFFICE_BEGIN__"
END = "__KIYORI_OFFICE_END__"

# 设计文档 §3.3 固定错误码表。新增错误码必须先更新设计文档与专项 index。
ERROR_CODES = (
    "E_ENV_MISSING",
    "E_PATH_INVALID",
    "E_PATH_EXISTS",
    "E_INPUT_SCHEMA",
    "E_FORMAT_UNSUPPORTED",
    "E_DOC_CORRUPT",
    "E_ANCHOR_NOT_FOUND",
    "E_VALIDATION_FAILED",
    "E_ENGINE_FAILED",
    "E_TIMEOUT",
    "E_BUDGET_EXCEEDED",
    "E_PROTOCOL",
)


class OfficeError(Exception):
    """携带固定错误码、remedy 与结构化 detail 的运行时错误。"""

    def __init__(
        self,
        code: str,
        message: str,
        *,
        detail: Optional[str] = None,
        remedy: Optional[str] = None,
        data: Optional[Dict[str, Any]] = None,
    ) -> None:
        if code not in ERROR_CODES:
            raise ValueError("unregistered office error code: %s" % code)
        super().__init__(message)
        self.code = code
        self.message = message
        self.detail = detail
        self.remedy = remedy
        self.data = data or {}

    def to_error(self) -> Dict[str, Any]:
        payload: Dict[str, Any] = {"code": self.code, "message": self.message}
        if self.detail:
            payload["detail"] = self.detail
        if self.remedy:
            payload["remedy"] = self.remedy
        return payload


class Command:
    """一条命令的注册信息。"""

    __slots__ = ("name", "handler", "schema", "next_actions", "engine")

    def __init__(
        self,
        name: str,
        handler: Callable[[Dict[str, Any]], Dict[str, Any]],
        *,
        schema: Optional[str] = None,
        next_actions: Iterable[str] = (),
        engine: Optional[str] = None,
    ) -> None:
        self.name = name
        self.handler = handler
        self.schema = schema
        self.next_actions: List[str] = list(next_actions)
        self.engine = engine


_COMMANDS: Dict[str, Command] = {}
_REGISTRY_LOADED = False


def register(
    name: str,
    *,
    schema: Optional[str] = None,
    next_actions: Iterable[str] = (),
    engine: Optional[str] = None,
):
    """注册命令。schema 为 schemas/<name>.json 的基名。"""

    def decorator(handler: Callable[[Dict[str, Any]], Dict[str, Any]]):
        if name in _COMMANDS:
            raise ValueError("duplicate office command: %s" % name)
        _COMMANDS[name] = Command(
            name, handler, schema=schema, next_actions=next_actions, engine=engine
        )
        return handler

    return decorator


def commands() -> Dict[str, Command]:
    _ensure_registry()
    return _COMMANDS


def _ensure_registry() -> None:
    global _REGISTRY_LOADED
    if _REGISTRY_LOADED:
        return
    # 延迟导入，避免命令实现与协议模块互相 import。
    from . import office as _office  # noqa: F401
    from . import convert as _convert  # noqa: F401
    from . import render as _render  # noqa: F401
    from . import validate as _validate  # noqa: F401
    from .ops_docx import register_commands as _docx
    from .ops_pdf import register_commands as _pdf
    from .ops_pptx import register_commands as _pptx
    from .ops_xlsx import register_commands as _xlsx

    _docx()
    _xlsx()
    _pptx()
    _pdf()
    _REGISTRY_LOADED = True


def engine_version(distribution: str) -> str:
    """读取引擎版本；缺失时返回空串，不伪造版本号。"""

    try:
        from importlib.metadata import version

        return version(distribution)
    except Exception:
        return ""


def build_success(
    command: str,
    *,
    data: Optional[Dict[str, Any]] = None,
    artifacts: Optional[List[Dict[str, Any]]] = None,
    warnings: Optional[List[Dict[str, str]]] = None,
    engine_name: Optional[str] = None,
    engine: Optional[str] = None,
    next_actions: Optional[Iterable[str]] = None,
    truncated: bool = False,
    full_output_path: Optional[str] = None,
    elapsed_ms: int = 0,
) -> Dict[str, Any]:
    payload: Dict[str, Any] = {
        "ok": True,
        "command": command,
        "artifacts": artifacts or [],
        "data": data or {},
        "warnings": warnings or [],
        "metrics": {"elapsed_ms": elapsed_ms},
        "truncated": bool(truncated),
        "full_output_path": full_output_path,
    }
    if engine_name:
        payload["engine"] = engine_name
    if engine:
        payload["engine_version"] = engine
    if next_actions:
        payload["next_actions"] = list(next_actions)
    return payload


def build_failure(command: str, error: OfficeError, elapsed_ms: int = 0) -> Dict[str, Any]:
    payload: Dict[str, Any] = {
        "ok": False,
        "command": command,
        "error": error.to_error(),
        "metrics": {"elapsed_ms": elapsed_ms},
    }
    if error.data:
        payload["data"] = error.data
    return payload


def dispatch(command: str, args: Dict[str, Any]) -> Dict[str, Any]:
    """执行一条命令；未知命令按输入错误上报，不静默返回空结果。"""

    registry = commands()
    entry = registry.get(command)
    if entry is None:
        raise OfficeError(
            "E_INPUT_SCHEMA",
            "unknown command: %s" % command,
            detail="registered commands: %s" % ", ".join(sorted(registry)),
            remedy="使用 office_workflow_guide 或 SKILL.md 中列出的已登记命令",
        )
    from .argspec import validate_args

    validate_args(command, args, entry.schema)
    result = entry.handler(args)
    if not isinstance(result, dict):
        raise OfficeError(
            "E_ENGINE_FAILED",
            "command handler returned a non-object result: %s" % command,
        )
    if entry.engine and "engine" not in result:
        result["engine"] = entry.engine
    if entry.next_actions and "next_actions" not in result:
        result["next_actions"] = list(entry.next_actions)
    return result


def load_args_file(path: str) -> Dict[str, Any]:
    args_path = Path(path)
    if not args_path.is_file():
        raise OfficeError(
            "E_PATH_INVALID",
            "args file not found: %s" % path,
            remedy="JS 层必须先用 Tools.Files.write 把 JSON 参数写入 Linux 侧文件",
        )
    try:
        raw = args_path.read_text(encoding="utf-8")
    except UnicodeDecodeError as exc:
        raise OfficeError(
            "E_INPUT_SCHEMA", "args file is not valid UTF-8", detail=str(exc)
        ) from exc
    try:
        payload = json.loads(raw)
    except json.JSONDecodeError as exc:
        raise OfficeError(
            "E_INPUT_SCHEMA", "args file is not valid JSON", detail=str(exc)
        ) from exc
    if isinstance(payload, dict) and isinstance(payload.get("args"), dict):
        payload = payload["args"]
    if not isinstance(payload, dict):
        raise OfficeError("E_INPUT_SCHEMA", "args file root must be a JSON object")
    return payload


def run(command: str, args: Dict[str, Any]) -> Dict[str, Any]:
    started = time.monotonic()
    try:
        raw = dispatch(command, args)
    except OfficeError as exc:
        return build_failure(command, exc, _elapsed_ms(started))
    except Exception as exc:  # noqa: BLE001 - 统一转成 E_ENGINE_FAILED
        detail = "".join(traceback.format_exception_only(type(exc), exc)).strip()
        return build_failure(
            command,
            OfficeError(
                "E_ENGINE_FAILED",
                "%s: %s" % (type(exc).__name__, exc),
                detail=detail,
            ),
            _elapsed_ms(started),
        )
    if raw.get("ok") is True:
        # 命令可以自行返回完整信封（保留兼容），否则统一归一化。
        raw.setdefault("metrics", {})["elapsed_ms"] = _elapsed_ms(started)
        return raw
    result = build_success(
        command,
        data=raw.get("data"),
        artifacts=raw.get("artifacts"),
        warnings=raw.get("warnings"),
        engine_name=raw.get("engine"),
        engine=raw.get("engine_version"),
        next_actions=raw.get("next_actions"),
        truncated=bool(raw.get("truncated")),
        full_output_path=raw.get("full_output_path"),
        elapsed_ms=_elapsed_ms(started),
    )
    return result


def _elapsed_ms(started: float) -> int:
    return int(round((time.monotonic() - started) * 1000))


def emit(payload: Dict[str, Any]) -> None:
    """输出 sentinel 信封；ensure_ascii=False 保证中文可读。"""

    sys.stdout.write(BEGIN + json.dumps(payload, ensure_ascii=False) + END + "\n")
    sys.stdout.flush()


def parse_sentinel(output: str) -> Dict[str, Any]:
    """从含噪声的输出中提取信封，供测试与 JS 侧对照。"""

    start = output.find(BEGIN)
    if start < 0:
        raise OfficeError("E_PROTOCOL", "office runtime sentinel begin marker missing")
    end = output.find(END, start + len(BEGIN))
    if end < 0:
        raise OfficeError("E_PROTOCOL", "office runtime sentinel end marker missing")
    raw = output[start + len(BEGIN) : end]
    try:
        parsed = json.loads(raw)
    except json.JSONDecodeError as exc:
        raise OfficeError(
            "E_PROTOCOL", "office runtime envelope is not valid JSON", detail=str(exc)
        ) from exc
    if not isinstance(parsed, dict):
        raise OfficeError("E_PROTOCOL", "office runtime envelope root must be an object")
    return parsed


def main(argv: Optional[List[str]] = None) -> int:
    parser = argparse.ArgumentParser(prog="kiyori_office")
    parser.add_argument("command")
    parser.add_argument("--args-file", required=True)
    namespace = parser.parse_args(argv)

    try:
        args = load_args_file(namespace.args_file)
    except OfficeError as exc:
        emit(build_failure(namespace.command, exc))
        return 0
    emit(run(namespace.command, args))
    return 0


def python_identity() -> Dict[str, Any]:
    return {
        "executable": sys.executable,
        "version": sys.version.split()[0],
        "platform": sys.platform,
        "home": os.path.expanduser("~"),
    }
