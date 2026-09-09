"""office_convert：显式引擎的格式转换。

引擎必须由调用方显式指定（libreoffice / pandoc），缺失时报 E_ENV_MISSING；
绝不自动切换引擎或用「差不多的方案」代替。
"""

from __future__ import annotations

import subprocess
from pathlib import Path
from typing import Any, Dict, List

from .env import require_binary
from .paths import artifact, resolve_output_path, resolve_path, resolve_task_id, task_dir
from .protocol import OfficeError, engine_version

PANDOC_INPUTS = {".md", ".markdown", ".html", ".htm", ".rst", ".tex", ".txt", ".csv", ".docx", ".odt"}
PANDOC_OUTPUTS = {".md", ".html", ".docx", ".odt", ".rst", ".tex", ".pdf", ".epub"}


def convert(args: Dict[str, Any]) -> Dict[str, Any]:
    engine = str(args.get("engine") or "")
    if engine not in ("libreoffice", "pandoc"):
        raise OfficeError(
            "E_INPUT_SCHEMA",
            "engine 必须显式指定为 libreoffice 或 pandoc",
            detail="engine=%r" % args.get("engine"),
            remedy="不要依赖默认引擎；不同引擎的保真度与字体行为不同",
        )
    task_id = resolve_task_id(args)
    directory = task_dir(task_id)
    source = resolve_path(args.get("from_path"), args=args, field="from_path", must_exist=True)
    to_format = str(args.get("to_format") or "").lower().lstrip(".")
    if not to_format:
        raise OfficeError("E_INPUT_SCHEMA", "to_format 不能为空")
    output = resolve_output_path(
        args.get("output_path"),
        args=args,
        field="output_path",
        default_dir=directory / "out",
        default_name="%s.%s" % (source.stem, to_format),
        overwrite=bool(args.get("overwrite")),
    )
    if engine == "libreoffice":
        return _convert_libreoffice(source, output, to_format, directory, args)
    return _convert_pandoc(source, output, to_format, directory, args)


def _convert_libreoffice(
    source: Path,
    output: Path,
    to_format: str,
    directory: Path,
    args: Dict[str, Any],
) -> Dict[str, Any]:
    soffice = require_binary("soffice", tier=3, purpose="office_convert(libreoffice)")
    filter_map = {
        "pdf": "pdf",
        "docx": "docx:MS Word 2007 XML",
        "xlsx": "xlsx:Calc MS Excel 2007 XML",
        "pptx": "pptx:Impress MS PowerPoint 2007 XML",
        "csv": "csv:Text - txt - csv (StarCalc)",
        "html": "html:HTML (StarWriter)",
        "odt": "odt",
        "ods": "ods",
        "odp": "odp",
    }
    if to_format not in filter_map:
        raise OfficeError(
            "E_FORMAT_UNSUPPORTED",
            "libreoffice 不支持目标格式: %s" % to_format,
            detail="supported=%s" % ", ".join(sorted(filter_map)),
        )
    target_dir = directory / "tmp" / "convert"
    target_dir.mkdir(parents=True, exist_ok=True)
    profile = directory / "tmp" / "lo-profile"
    profile.mkdir(parents=True, exist_ok=True)
    command = [
        soffice,
        "--headless",
        "--norestore",
        "--nolockcheck",
        "--nodefault",
        "-env:UserInstallation=file://%s" % profile.resolve(),
        "--convert-to",
        filter_map[to_format],
        "--outdir",
        str(target_dir),
        str(source),
    ]
    env = dict(__import__("os").environ)
    env.setdefault("HOME", str(profile.parent))
    try:
        completed = subprocess.run(
            command, capture_output=True, text=True, timeout=int(args.get("timeout_ms") or 600000) // 1000 or 600, env=env, check=False
        )
    except subprocess.TimeoutExpired as exc:
        raise OfficeError("E_TIMEOUT", "LibreOffice 转换超时", detail=str(exc)) from exc
    if completed.returncode != 0:
        raise OfficeError(
            "E_ENGINE_FAILED",
            "LibreOffice 转换失败",
            detail="exit=%s stderr=%s" % (completed.returncode, (completed.stderr or "")[-2000:]),
            remedy="检查源文件与 LibreOffice 安装；不要自动切换引擎",
        )
    produced = target_dir / ("%s.%s" % (source.stem, to_format))
    if not produced.is_file():
        candidates = sorted(target_dir.glob("*"))
        raise OfficeError(
            "E_ENGINE_FAILED",
            "LibreOffice 未产出目标格式",
            detail="expected=%s produced=%s" % (produced, [str(item) for item in candidates]),
        )
    output.parent.mkdir(parents=True, exist_ok=True)
    import shutil

    shutil.copy2(produced, output)
    return {
        "artifacts": [artifact(output, role="in_place" if args.get("in_place") else "output")],
        "data": {
            "engine": "libreoffice",
            "to_format": to_format,
            "work_dir": str(directory),
        },
        "engine_version": engine_version("libreoffice"),
        "next_actions": ["office_validate", "office_render_preview"],
    }


def _convert_pandoc(
    source: Path,
    output: Path,
    to_format: str,
    directory: Path,
    args: Dict[str, Any],
) -> Dict[str, Any]:
    pandoc = require_binary("pandoc", tier=2, purpose="office_convert(pandoc)")
    if source.suffix.lower() not in PANDOC_INPUTS:
        raise OfficeError(
            "E_FORMAT_UNSUPPORTED",
            "pandoc 不支持该输入格式: %s" % source.suffix,
            detail="supported=%s" % ", ".join(sorted(PANDOC_INPUTS)),
        )
    if to_format not in PANDOC_OUTPUTS:
        raise OfficeError(
            "E_FORMAT_UNSUPPORTED",
            "pandoc 不支持该目标格式: %s" % to_format,
            detail="supported=%s" % ", ".join(sorted(PANDOC_OUTPUTS)),
        )
    command = [pandoc, str(source), "-o", str(output)]
    if to_format == "pdf":
        from .env import require_cjk_font_files

        xelatex = require_binary("xelatex", tier=4, purpose="pandoc 生成 PDF")
        fonts = require_cjk_font_files(purpose="pandoc 生成 PDF")
        command += [
            "--pdf-engine",
            xelatex,
            "-V",
            "CJKmainfont=%s" % (args.get("cjk_font") or fonts["font_families"][0]),
        ]
    for option in args.get("options") or []:
        if not isinstance(option, str) or not option:
            raise OfficeError("E_INPUT_SCHEMA", "options 必须是字符串数组")
        command.append(option)
    output.parent.mkdir(parents=True, exist_ok=True)
    try:
        completed = subprocess.run(
            command,
            capture_output=True,
            text=True,
            timeout=int(args.get("timeout_ms") or 600000) // 1000 or 600,
            check=False,
        )
    except subprocess.TimeoutExpired as exc:
        raise OfficeError("E_TIMEOUT", "pandoc 转换超时", detail=str(exc)) from exc
    if completed.returncode != 0:
        raise OfficeError(
            "E_ENGINE_FAILED",
            "pandoc 转换失败",
            detail="exit=%s stderr=%s" % (completed.returncode, (completed.stderr or "")[-2000:]),
        )
    if not output.is_file():
        raise OfficeError("E_ENGINE_FAILED", "pandoc 未产出目标文件", detail=str(output))
    return {
        "artifacts": [artifact(output, role="in_place" if args.get("in_place") else "output")],
        "data": {
            "engine": "pandoc",
            "to_format": to_format,
            "work_dir": str(directory),
        },
        "engine_version": engine_version("pandoc"),
        "next_actions": ["office_validate", "office_render_preview"],
    }
