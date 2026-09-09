"""路径安全、原子写入与工作区管理。

规则（设计文档 §5.2/§5.3）：

- 运行时只处理 Linux 路径；Android 文件由 JS 层先复制到 Linux 暂存区；
- canonicalize 后必须落在允许根内，拒绝符号链接与 ``..``；
- 产物先写临时文件再原子替换，目标已存在且未 ``overwrite=true`` 时报 E_PATH_EXISTS。
"""

from __future__ import annotations

import hashlib
import os
import re
import shutil
import tempfile
import uuid
from contextlib import contextmanager
from pathlib import Path
from typing import Any, Dict, Optional, Sequence

from .protocol import OfficeError

DEFAULT_WORK_ROOT = "~/kiyori_office/work"
ILLEGAL_FILENAME_CHARS = re.compile(r"[\x00-\x1f<>:\"|?*/\\]")


def work_root() -> Path:
    raw = os.environ.get("KIYORI_OFFICE_WORK_ROOT") or DEFAULT_WORK_ROOT
    return Path(os.path.expanduser(raw)).resolve()


def resolve_task_id(args: Dict[str, Any]) -> str:
    raw = str(args.get("task_id") or "").strip()
    if not raw:
        return uuid.uuid4().hex[:12]
    if raw in (".", "..") or not re.fullmatch(r"[A-Za-z0-9._-]{1,64}", raw):
        raise OfficeError(
            "E_INPUT_SCHEMA",
            "task_id 只能包含字母、数字、点、下划线和连字符，且不超过 64 字符",
            detail="task_id=%s" % raw,
        )
    return raw


def task_dir(task_id: str, *, create: bool = True) -> Path:
    resolve_task_id({"task_id": task_id})
    directory = work_root() / task_id
    _reject_symlink(directory)
    if create:
        (directory / "in").mkdir(parents=True, exist_ok=True)
        (directory / "out").mkdir(parents=True, exist_ok=True)
        (directory / "tmp").mkdir(parents=True, exist_ok=True)
    return directory


def allowed_roots(args: Dict[str, Any]) -> Sequence[Path]:
    roots = [work_root()]
    extra = args.get("allow_roots")
    if extra is not None:
        if not isinstance(extra, list) or not all(isinstance(item, str) for item in extra):
            raise OfficeError("E_INPUT_SCHEMA", "allow_roots 必须是字符串数组")
        for item in extra:
            if not item.strip():
                raise OfficeError("E_INPUT_SCHEMA", "allow_roots 不能包含空字符串")
            roots.append(Path(os.path.expanduser(item)).resolve())
    return roots


def _reject_symlink(path: Path) -> None:
    current = Path(path.anchor)
    for part in path.parts[1:]:
        current = current / part
        try:
            if current.is_symlink():
                raise OfficeError(
                    "E_PATH_INVALID",
                    "路径包含符号链接，拒绝处理",
                    detail=str(current),
                    remedy="把文件复制到暂存区后重试，不要依赖符号链接",
                )
        except OSError:
            continue


def resolve_path(
    raw: Any,
    *,
    args: Dict[str, Any],
    field: str,
    must_exist: bool = False,
    create_parent: bool = False,
) -> Path:
    if not isinstance(raw, str) or not raw.strip():
        raise OfficeError("E_INPUT_SCHEMA", "%s 必须是非空字符串" % field, detail=repr(raw))
    text = raw.strip()
    if "\x00" in text:
        raise OfficeError("E_PATH_INVALID", "%s 含 NUL 字符" % field)
    candidate = Path(os.path.expanduser(text))
    if not candidate.is_absolute():
        candidate = work_root() / candidate
    # 必须在 resolve() 之前检查：resolve 会跟随符号链接，之后再检查就看不到原链接。
    _reject_symlink(candidate)
    try:
        resolved = candidate.resolve(strict=False)
    except OSError as exc:
        raise OfficeError(
            "E_PATH_INVALID", "无法解析路径: %s" % text, detail=str(exc)
        ) from exc

    roots = allowed_roots(args)
    if not any(resolved == root or root in resolved.parents for root in roots):
        raise OfficeError(
            "E_PATH_INVALID",
            "%s 越出允许根目录" % field,
            detail="resolved=%s allowed=%s" % (resolved, [str(root) for root in roots]),
            remedy="把文件放到工作区内，或用 allow_roots 显式声明额外根目录",
        )
    if must_exist and not resolved.is_file():
        raise OfficeError(
            "E_PATH_INVALID",
            "%s 文件不存在" % field,
            detail=str(resolved),
            remedy="先用 office_read/outline 确认输入文件，并检查 JS 层跨环境复制是否成功",
        )
    if create_parent:
        resolved.parent.mkdir(parents=True, exist_ok=True)
    return resolved


def resolve_output_path(
    raw: Any,
    *,
    args: Dict[str, Any],
    field: str,
    default_dir: Path,
    default_name: str,
    overwrite: bool,
    in_place: bool = False,
) -> Path:
    if raw:
        target = resolve_path(raw, args=args, field=field, create_parent=True)
    else:
        target = default_dir / sanitize_filename(default_name)
    if in_place:
        return target
    if target.exists() and not overwrite:
        raise OfficeError(
            "E_PATH_EXISTS",
            "目标文件已存在且未设置 overwrite=true",
            detail=str(target),
            remedy="改用新的 output_path，或显式传入 overwrite=true",
        )
    return target


def resolve_output_dir(
    raw: Any,
    *,
    args: Dict[str, Any],
    field: str,
    default_dir: Path,
    overwrite: bool,
) -> Path:
    """多产物命令的输出目录（office_render_preview / pdf_split / pdf_to_images 等）。

    与 ``resolve_output_path`` 同语义，只是目标是一个目录：省略时用默认暂存目录，
    显式传入时要求落在 allow_roots 内且非空目录冲突时报 E_PATH_EXISTS。
    """

    if raw:
        target = resolve_path(raw, args=args, field=field, create_parent=True)
    else:
        target = default_dir
    if target.exists() and not target.is_dir():
        raise OfficeError(
            "E_PATH_INVALID",
            "%s 已存在且不是目录" % field,
            detail=str(target),
            remedy="改用目录路径，或换一个不冲突的 output_path",
        )
    # 只对调用方显式指定的目录做非空保护：默认暂存目录在同一 task_id 复用时
    # 允许追加产物，避免 office_render_preview 二次调用被自己的上次输出挡住。
    if raw and target.is_dir() and any(target.iterdir()) and not overwrite:
        raise OfficeError(
            "E_PATH_EXISTS",
            "目标目录非空且未设置 overwrite=true",
            detail=str(target),
            remedy="改用新的 output_path，或显式传入 overwrite=true",
        )
    target.mkdir(parents=True, exist_ok=True)
    return target


def atomic_write_bytes(target: Path, payload: bytes) -> Path:
    target.parent.mkdir(parents=True, exist_ok=True)
    handle = tempfile.NamedTemporaryFile(dir=str(target.parent), prefix=".tmp-", delete=False)
    try:
        with handle:
            handle.write(payload)
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(handle.name, target)
    except BaseException:
        try:
            os.unlink(handle.name)
        except OSError:
            pass
        raise
    return target


@contextmanager
def atomic_output(target: Path):
    """同目录临时文件完成写入后才替换，保存失败不得截断原始文档。"""
    target.parent.mkdir(parents=True, exist_ok=True)
    fd, name = tempfile.mkstemp(dir=target.parent, prefix=".tmp-", suffix=target.suffix)
    os.close(fd)
    temporary = Path(name)
    try:
        yield temporary
        with temporary.open("rb+") as handle:
            os.fsync(handle.fileno())
        os.replace(temporary, target)
    finally:
        temporary.unlink(missing_ok=True)


def atomic_save(document, target: Path) -> None:
    with atomic_output(target) as temporary:
        document.save(str(temporary))


def atomic_write_text(target: Path, text: str) -> Path:
    return atomic_write_bytes(target, text.encode("utf-8"))


def sha256_of(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def artifact(path: Path, *, role: str = "output", env: str = "linux") -> Dict[str, Any]:
    return {
        "role": role,
        "path": str(path),
        "env": env,
        "bytes": path.stat().st_size,
        "sha256": sha256_of(path),
    }


def sanitize_filename(name: str, *, fallback: str = "output") -> str:
    """过滤非法字符，但保留中文、空格与 emoji。"""

    cleaned = ILLEGAL_FILENAME_CHARS.sub("_", str(name or "")).strip().strip(".")
    return cleaned or fallback


def copy_into_staging(source: Path, task_id: str) -> Path:
    """把输入复制到暂存区，保证绝不在用户原始文件上原地修改。"""

    staging = task_dir(task_id) / "in" / sanitize_filename(source.name)
    if staging.resolve() == source.resolve():
        return staging
    shutil.copy2(source, staging)
    return staging


def clean_task(task_id: str) -> Dict[str, Any]:
    # 兼容 Python 辅助入口也只返回计划，不留下绕过确认的直接递归删除通道。
    from .storage import clean
    return clean(task_id)


def free_bytes(path: Optional[Path] = None) -> Optional[int]:
    try:
        return shutil.disk_usage(str(path or work_root())).free
    except OSError:
        return None
