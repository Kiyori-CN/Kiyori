"""office_convert：显式引擎的格式转换。

引擎必须由调用方显式指定（libreoffice / pandoc），缺失时报 E_ENV_MISSING；
绝不自动切换引擎或用「差不多的方案」代替。
"""

from __future__ import annotations

import subprocess
import tempfile
from pathlib import Path
from typing import Any, Dict, List

from .env import require_binary, require_libreoffice, require_libreoffice_for_source
from .paths import atomic_output, artifact, resolve_output_path, resolve_path, resolve_task_id, task_dir
from .protocol import OfficeError, engine_version
from .engines import run_output_command

# 后缀 → pandoc 读写器名。调用方传 to_format 时不带点（如 "md"），
# 历史实现把带点的集合直接与之比较，导致 pandoc 路线永远判为「不支持」。
PANDOC_INPUTS = {
    ".md": "markdown",
    ".markdown": "markdown",
    ".html": "html",
    ".htm": "html",
    ".rst": "rst",
    ".tex": "latex",
    ".txt": "markdown",
    ".csv": "csv",
    ".docx": "docx",
    ".odt": "odt",
    ".epub": "epub",
}
PANDOC_OUTPUTS = {
    "md": "markdown",
    "markdown": "markdown",
    "html": "html",
    "htm": "html",
    "txt": "plain",
    "plain": "plain",
    "docx": "docx",
    "odt": "odt",
    "rst": "rst",
    "tex": "latex",
    "latex": "latex",
    "pdf": "pdf",
    "epub": "epub",
    "csv": "csv",
}

# PDF → 这些目标格式时，soffice 必须显式加 --infilter=writer_pdf_import，
# 否则无头模式把 PDF 当 Draw 文档打开，找不到 Writer 语义的导出过滤器（D3）。
# 只覆盖 filter_map 中实际存在的 Writer 家族目标；xlsx/pptx 等不适用。
WRITER_TARGET_FORMATS = {"docx", "odt", "html"}

# to_format 允许写 "plain"/"markdown"/"latex" 这类 pandoc 读写器名，
# 但产物文件名要用真实后缀，否则会写出 document.plain 这种无法打开的路径。
PANDOC_OUTPUT_EXTENSIONS = {
    "md": "md",
    "markdown": "md",
    "html": "html",
    "htm": "html",
    "txt": "txt",
    "plain": "txt",
    "docx": "docx",
    "odt": "odt",
    "rst": "rst",
    "tex": "tex",
    "latex": "tex",
    "pdf": "pdf",
    "epub": "epub",
    "csv": "csv",
}


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
    output_extension = PANDOC_OUTPUT_EXTENSIONS.get(to_format, to_format)
    output = resolve_output_path(
        args.get("output_path"),
        args=args,
        field="output_path",
        default_dir=directory / "out",
        default_name="%s.%s" % (source.stem, output_extension),
        overwrite=bool(args.get("overwrite")),
    )
    if output.suffix.lower() != "." + output_extension:
        raise OfficeError("E_INPUT_SCHEMA", "output_path 后缀必须与目标格式一致：.%s" % output_extension)
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
    # 先确认源文件所需的 LibreOffice 组件（Writer/Calc/Impress），
    # 只装 Writer 时转 xlsx/pptx 会以 E_ENGINE_FAILED 失败，必须先报 E_ENV_MISSING。
    writer_pdf_import = source.suffix.lower() == ".pdf" and to_format in WRITER_TARGET_FORMATS
    if writer_pdf_import:
        # PDF 没有固定源组件；Writer 导入路线必须确认 Writer，而非任意已装组件。
        state = require_libreoffice(purpose="office_convert(libreoffice)", component="writer")
    else:
        state = require_libreoffice_for_source(source.suffix, purpose="office_convert(libreoffice)")
    soffice = state["path"]
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
    target_dir = Path(tempfile.mkdtemp(prefix="convert-", dir=directory / "tmp"))
    profile = target_dir / "lo-profile"
    profile.mkdir(parents=True, exist_ok=True)
    command = [
        soffice,
        "--headless",
        "--norestore",
        "--nolockcheck",
        "--nodefault",
        "-env:UserInstallation=%s" % profile.resolve().as_uri(),
    ]
    # soffice 无头模式对 PDF 源文件默认以 Draw 文档打开，导出过滤器只能选
    # Draw 语义的目标（pdf/odg/svg…）；目标是 Writer 语义格式时必须显式加
    # --infilter=writer_pdf_import 让它以 Writer 文档导入，否则必然报
    # "no export filter ... found, aborting."（见缺陷 D3）。
    if writer_pdf_import:
        command.append("--infilter=writer_pdf_import")
    command += [
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
            detail="exit=%s stderr_tail=%s" % (completed.returncode, (completed.stderr or "")[-2000:]),
            remedy="检查源文件与 LibreOffice 安装；不要自动切换引擎",
        )
    produced = target_dir / ("%s.%s" % (source.stem, to_format))
    if not produced.is_file() or produced.stat().st_size == 0:
        candidates = sorted(target_dir.glob("*"))
        raise OfficeError(
            "E_ENGINE_FAILED",
            "LibreOffice 未产出目标格式",
            detail="expected=%s produced=%s" % (produced, [str(item) for item in candidates]),
        )
    output.parent.mkdir(parents=True, exist_ok=True)
    import shutil

    with atomic_output(output) as temporary:
        shutil.copy2(produced, temporary)
    return {
        # 顶层 engine 必须显式给出：office_convert 以静态 engine="external"
        # 注册（见 protocol.py dispatch），只有 handler 自己在结果顶层写
        # "engine" 才不会被那个占位值覆盖（同一类问题见 D7）。
        "engine": "libreoffice",
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
    command = [pandoc, "-f", PANDOC_INPUTS[source.suffix.lower()], str(source), "-o", str(output)]
    if to_format == "pdf":
        from .env import require_cjk_font_files, select_system_cjk_font

        xelatex = require_binary("xelatex", tier=4, purpose="pandoc 生成 PDF")
        fonts = require_cjk_font_files(purpose="pandoc 生成 PDF")
        command += [
            "--pdf-engine",
            xelatex,
            "-V",
            "CJKmainfont=%s" % select_system_cjk_font(fonts, args.get('cjk_font')),
        ]
    elif PANDOC_OUTPUTS[to_format] != output.suffix.lower().lstrip("."):
        # 产物后缀（如 .txt）与 pandoc 读写器名（plain）不同时必须显式指定 -t，
        # 否则 pandoc 会按后缀推断出未知格式。pdf 由 --pdf-engine 处理，不走这里。
        command += ["-t", PANDOC_OUTPUTS[to_format]]
    for option in args.get("options") or []:
        if not isinstance(option, str) or not option:
            raise OfficeError("E_INPUT_SCHEMA", "options 必须是字符串数组")
        if option in ("-o", "--output", "-t", "--to", "--write", "--pdf-engine") or option.startswith(("--output=", "--to=", "--write=", "--pdf-engine=", "-o")):
            raise OfficeError("E_INPUT_SCHEMA", "options 不能覆盖工具管理的输出路径、格式或 PDF 引擎")
        command.append(option)
    run_output_command(command, output, command.index("-o") + 1,
                       timeout=int(args.get("timeout_ms") or 600000) / 1000, purpose="pandoc 转换")
    return {
        "engine": "pandoc",
        "artifacts": [artifact(output, role="in_place" if args.get("in_place") else "output")],
        "data": {
            "engine": "pandoc",
            "to_format": to_format,
            "work_dir": str(directory),
        },
        "engine_version": engine_version("pandoc"),
        "next_actions": ["office_validate", "office_render_preview"],
    }
