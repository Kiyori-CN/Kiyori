"""office_render_preview：文档 → PDF → 分页 JPEG。

这是整套方案的验收闭环：把「模型自称写好了」变成「模型自己看过了」。
返回的图片路径是 Linux 侧路径，JS 层负责回搬到 Android 后交给
``Tools.Files.read({ path, direct_image: true })``。
"""

from __future__ import annotations

import os
import subprocess
import tempfile
from pathlib import Path
from typing import Any, Dict, List

from .env import require_binary, require_libreoffice_for_source, require_poppler_data
from .paths import artifact, resolve_output_dir, resolve_path, resolve_task_id, task_dir
from .protocol import OfficeError, engine_version
from .readers.pdf_reader import pdf_info
from .engines import run_output_command

SOFFICE_SUFFIXES = {".docx", ".xlsx", ".xlsm", ".pptx", ".doc", ".xls", ".ppt", ".odt", ".ods", ".odp"}


def render_preview(args: Dict[str, Any]) -> Dict[str, Any]:
    task_id = resolve_task_id(args)
    directory = task_dir(task_id)
    source = resolve_path(args.get("path"), args=args, field="path", must_exist=True)
    max_pages = int(args.get("max_pages") or 8)
    dpi = int(args.get("dpi") or 150)
    if max_pages < 1:
        raise OfficeError("E_INPUT_SCHEMA", "max_pages 必须 >= 1")
    if dpi < 60 or dpi > 400:
        raise OfficeError("E_INPUT_SCHEMA", "dpi 必须在 60-400 之间", detail=str(dpi))

    pdf_path = source
    if source.suffix.lower() != ".pdf":
        if source.suffix.lower() not in SOFFICE_SUFFIXES:
            raise OfficeError(
                "E_FORMAT_UNSUPPORTED",
                "预览不支持该格式: %s" % source.suffix,
                remedy="先用 office_convert 转换到 PDF 或受支持格式",
            )
        pdf_path = _convert_to_pdf(source, directory)

    info = pdf_info(pdf_path)
    from .readers.pdf_reader import parse_page_range

    requested = parse_page_range(args.get("pages"), info["pages"])
    pages = requested or list(range(1, min(info["pages"], max_pages) + 1))
    pages = pages[:max_pages]

    pdftoppm = require_binary("pdftoppm", tier=2, purpose="office_render_preview")
    # pdftoppm 缺 Adobe-GB1 CMap 时退出码仍为 0，但中文 PDF 会渲染成空白图。
    require_poppler_data(purpose="office_render_preview")
    target_dir = resolve_output_dir(
        args.get("output_path"),
        args=args,
        field="output_path",
        default_dir=directory / "out" / (source.stem + "-preview"),
        overwrite=bool(args.get("overwrite")),
    )
    artifacts: List[Dict[str, Any]] = []
    for page in pages:
        prefix = target_dir / ("preview-%03d" % page)
        command = [
            pdftoppm,
            "-f",
            str(page),
            "-l",
            str(page),
            "-r",
            str(dpi),
            "-jpeg",
            "-singlefile",
            str(pdf_path),
            str(prefix),
        ]
        produced = Path(str(prefix) + ".jpg")
        run_output_command(command, produced, -1, timeout=300,
                           purpose="第 %d 页渲染" % page, output_is_prefix=True)
        artifacts.append(artifact(produced))
    return {
        "artifacts": artifacts,
        "data": {
            "source": str(source),
            "pdf": str(pdf_path),
            "pages": pages,
            "page_count": info["pages"],
            "dpi": dpi,
            "target_dir": str(target_dir),
            "work_dir": str(directory),
        },
        "engine_version": engine_version("poppler-utils"),
        "next_actions": ["office_validate"],
    }


def _convert_to_pdf(source: Path, directory: Path) -> Path:
    # 预览同样受组件限制：xlsx 需要 Calc、pptx 需要 Impress。
    soffice = require_libreoffice_for_source(
        source.suffix, purpose="office_render_preview 视觉预览"
    )["path"]
    target_dir = Path(tempfile.mkdtemp(prefix="render-", dir=directory / "tmp"))
    profile = target_dir / "lo-profile"
    profile.mkdir(parents=True, exist_ok=True)
    command = [
        soffice,
        "--headless",
        "--norestore",
        "--nolockcheck",
        "--nodefault",
        "-env:UserInstallation=%s" % profile.resolve().as_uri(),
        "--convert-to",
        "pdf",
        "--outdir",
        str(target_dir),
        str(source),
    ]
    env = dict(os.environ)
    env.setdefault("HOME", str(profile.parent))
    try:
        completed = subprocess.run(
            command, capture_output=True, text=True, timeout=600, env=env, check=False
        )
    except subprocess.TimeoutExpired as exc:
        raise OfficeError(
            "E_TIMEOUT",
            "LibreOffice 转 PDF 超时",
            detail=str(exc),
            remedy="缩小文档范围或提高 timeoutMs；不要自动切换引擎",
        ) from exc
    if completed.returncode != 0:
        raise OfficeError(
            "E_ENGINE_FAILED",
            "LibreOffice 转 PDF 失败",
            detail="exit=%s stderr=%s" % (completed.returncode, (completed.stderr or "")[-2000:]),
        )
    produced = target_dir / (source.stem + ".pdf")
    if not produced.is_file():
        raise OfficeError(
            "E_ENGINE_FAILED",
            "LibreOffice 未产出 PDF",
            detail="expected=%s stdout=%s" % (produced, (completed.stdout or "")[-1000:]),
        )
    return produced
