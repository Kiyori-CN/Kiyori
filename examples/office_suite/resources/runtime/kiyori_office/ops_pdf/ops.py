"""pdf_* 命令：信息、提取、页级操作、表单、水印、加解密、转图、生成。"""

from __future__ import annotations

import io
import shutil
import subprocess
from pathlib import Path
from typing import Any, Dict, List, Optional

from ..budget import bounded_text
from ..env import require_binary, require_cjk_font_files, require_cjk_fonts
from ..paths import artifact, resolve_output_path, resolve_path, resolve_task_id, task_dir
from ..protocol import OfficeError, engine_version, register
from ..readers.pdf_reader import (
    parse_page_range,
    pdf_extract_text,
    pdf_info,
    require_pypdf,
)

def _open_reader(source: Path):
    from pypdf import PdfReader  # type: ignore

    require_pypdf()
    try:
        reader = PdfReader(str(source))
    except Exception as exc:
        raise OfficeError(
            "E_DOC_CORRUPT",
            "PDF 打开失败",
            detail="%s: %s" % (type(exc).__name__, exc),
        ) from exc
    if reader.is_encrypted:
        try:
            if not reader.decrypt(str(_password(source))):
                raise OfficeError(
                    "E_DOC_CORRUPT",
                    "PDF 已加密，需要密码",
                    remedy="先用 pdf_decrypt 提供密码，或让用户提供未加密副本",
                )
        except OfficeError:
            raise
        except Exception as exc:
            raise OfficeError(
                "E_DOC_CORRUPT", "PDF 解密失败", detail=str(exc)
            ) from exc
    return reader


def _password(source: Path) -> str:
    return ""


def _writer():
    from pypdf import PdfWriter  # type: ignore

    return PdfWriter()


@register("pdf_info", schema="pdf_info", next_actions=["pdf_extract", "pdf_to_images"])
def pdf_info_command(args: Dict[str, Any]) -> Dict[str, Any]:
    source = resolve_path(args.get("path"), args=args, field="path", must_exist=True)
    return {"data": pdf_info(source)}


@register("pdf_extract", schema="pdf_extract", next_actions=["pdf_to_images"])
def pdf_extract_command(args: Dict[str, Any]) -> Dict[str, Any]:
    task_id = resolve_task_id(args)
    directory = task_dir(task_id)
    source = resolve_path(args.get("path"), args=args, field="path", must_exist=True)
    info = pdf_info(source)
    pages = parse_page_range(args.get("range"), info["pages"])
    mode = str(args.get("mode") or "text")
    if mode not in ("text", "layout", "tables"):
        raise OfficeError("E_INPUT_SCHEMA", "mode 必须是 text/layout/tables")
    if mode == "tables":
        return _extract_tables(source, pages, directory, args)
    result = pdf_extract_text(source, pages=pages, layout=(mode == "layout"))
    budget = bounded_text(
        result["text"],
        int(args.get("max_chars") or 20000),
        full_output_dir=directory / "out",
        full_output_name="%s-text.txt" % source.stem,
        navigation=[{"page": page} for page in result["pages"]],
    )
    return {
        "data": {
            **result,
            "text": budget["value"],
            "navigation": [{"page": page} for page in result["pages"]],
            "work_dir": str(directory),
        },
        "truncated": budget["truncated"],
        "full_output_path": budget["full_output_path"],
    }


def _extract_tables(source: Path, pages: List[int], directory: Path, args: Dict[str, Any]) -> Dict[str, Any]:
    try:
        import pdfplumber  # type: ignore
    except Exception as exc:
        raise OfficeError(
            "E_ENV_MISSING",
            "表格提取需要 pdfplumber（Tier1）",
            detail=str(exc),
            remedy="调用 office_env_setup 安装 pdfplumber，或改用 mode=text",
        ) from exc
    tables: List[Dict[str, Any]] = []
    with pdfplumber.open(str(source)) as pdf:
        for page_number in pages:
            for table_index, table in enumerate(pdf.pages[page_number - 1].extract_tables() or []):
                tables.append(
                    {
                        "page": page_number,
                        "index": table_index,
                        "rows": table,
                    }
                )
    return {
        "data": {
            "tables": tables,
            "table_count": len(tables),
            "work_dir": str(directory),
        }
    }


def _prepare_output(args: Dict[str, Any], default_name: str):
    task_id = resolve_task_id(args)
    directory = task_dir(task_id)
    output = resolve_output_path(
        args.get("output_path"),
        args=args,
        field="output_path",
        default_dir=directory / "out",
        default_name=default_name,
        overwrite=bool(args.get("overwrite")),
        in_place=bool(args.get("in_place")),
    )
    return task_id, directory, output


@register("pdf_merge", schema="pdf_merge", engine="pypdf", next_actions=["office_validate"])
def pdf_merge_command(args: Dict[str, Any]) -> Dict[str, Any]:
    paths = args.get("paths")
    if not isinstance(paths, list) or len(paths) < 2:
        raise OfficeError("E_INPUT_SCHEMA", "paths 至少需要两个 PDF")
    sources = [
        resolve_path(item, args=args, field="paths[%d]" % index, must_exist=True)
        for index, item in enumerate(paths)
    ]
    task_id, directory, output = _prepare_output(args, "merged.pdf")
    writer = _writer()
    for source in sources:
        reader = _open_reader(source)
        for page in reader.pages:
            writer.add_page(page)
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("wb") as handle:
        writer.write(handle)
    return {
        "artifacts": [artifact(output)],
        "data": {
            "merged": len(sources),
            "pages": len(_open_reader(output).pages),
            "work_dir": str(directory),
        },
        "engine_version": engine_version("pypdf"),
    }


@register("pdf_split", schema="pdf_split", engine="pypdf", next_actions=["office_validate"])
def pdf_split_command(args: Dict[str, Any]) -> Dict[str, Any]:
    source = resolve_path(args.get("path"), args=args, field="path", must_exist=True)
    info = pdf_info(source)
    pages = parse_page_range(args.get("range"), info["pages"])
    if not pages:
        raise OfficeError("E_INPUT_SCHEMA", "range 至少要包含一页")
    task_id = resolve_task_id(args)
    directory = task_dir(task_id)
    target_dir = directory / "out" / (source.stem + "-split")
    target_dir.mkdir(parents=True, exist_ok=True)
    reader = _open_reader(source)
    artifacts: List[Dict[str, Any]] = []
    for page_number in pages:
        writer = _writer()
        writer.add_page(reader.pages[page_number - 1])
        destination = target_dir / ("%s-page-%d.pdf" % (source.stem, page_number))
        with destination.open("wb") as handle:
            writer.write(handle)
        artifacts.append(artifact(destination))
    return {
        "artifacts": artifacts,
        "data": {
            "pages": pages,
            "target_dir": str(target_dir),
            "work_dir": str(directory),
        },
        "engine_version": engine_version("pypdf"),
    }


@register("pdf_reorder", schema="pdf_reorder", engine="pypdf", next_actions=["office_validate"])
def pdf_reorder_command(args: Dict[str, Any]) -> Dict[str, Any]:
    source = resolve_path(args.get("path"), args=args, field="path", must_exist=True)
    info = pdf_info(source)
    order = args.get("order")
    if not isinstance(order, list) or len(order) != info["pages"]:
        raise OfficeError(
            "E_INPUT_SCHEMA",
            "order 必须列出全部页码且数量与页数一致",
            detail="pages=%d order=%r" % (info["pages"], order),
        )
    normalized = []
    for value in order:
        if not isinstance(value, int) or isinstance(value, bool) or value < 1 or value > info["pages"]:
            raise OfficeError("E_INPUT_SCHEMA", "order 含非法页码: %r" % value)
        normalized.append(value)
    if sorted(normalized) != list(range(1, info["pages"] + 1)):
        raise OfficeError("E_INPUT_SCHEMA", "order 必须是 1..N 的一个排列", detail=str(normalized))
    task_id, directory, output = _prepare_output(args, source.name)
    reader = _open_reader(source)
    writer = _writer()
    for page_number in normalized:
        writer.add_page(reader.pages[page_number - 1])
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("wb") as handle:
        writer.write(handle)
    return {
        "artifacts": [artifact(output, role="in_place" if args.get("in_place") else "output")],
        "data": {"order": normalized, "work_dir": str(directory)},
        "engine_version": engine_version("pypdf"),
    }


@register("pdf_delete_pages", schema="pdf_delete_pages", engine="pypdf", next_actions=["office_validate"])
def pdf_delete_pages_command(args: Dict[str, Any]) -> Dict[str, Any]:
    source = resolve_path(args.get("path"), args=args, field="path", must_exist=True)
    info = pdf_info(source)
    pages = parse_page_range(args.get("range"), info["pages"]) or []
    if not pages:
        raise OfficeError("E_INPUT_SCHEMA", "range 至少要包含一页")
    if len(pages) >= info["pages"]:
        raise OfficeError("E_INPUT_SCHEMA", "不能删除全部页面")
    task_id, directory, output = _prepare_output(args, source.name)
    reader = _open_reader(source)
    writer = _writer()
    keep = [page for page in range(1, info["pages"] + 1) if page not in pages]
    for page_number in keep:
        writer.add_page(reader.pages[page_number - 1])
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("wb") as handle:
        writer.write(handle)
    return {
        "artifacts": [artifact(output, role="in_place" if args.get("in_place") else "output")],
        "data": {"deleted": pages, "remaining": len(keep), "work_dir": str(directory)},
        "engine_version": engine_version("pypdf"),
    }


@register("pdf_rotate", schema="pdf_rotate", engine="pypdf", next_actions=["office_validate"])
def pdf_rotate_command(args: Dict[str, Any]) -> Dict[str, Any]:
    source = resolve_path(args.get("path"), args=args, field="path", must_exist=True)
    info = pdf_info(source)
    angle = int(args.get("angle") or 0)
    if angle % 90 != 0:
        raise OfficeError("E_INPUT_SCHEMA", "angle 必须是 90 的倍数", detail=str(angle))
    pages = parse_page_range(args.get("range"), info["pages"]) or list(range(1, info["pages"] + 1))
    task_id, directory, output = _prepare_output(args, source.name)
    reader = _open_reader(source)
    writer = _writer()
    for index in range(info["pages"]):
        page = reader.pages[index]
        if index + 1 in pages:
            page.rotate(angle)
        writer.add_page(page)
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("wb") as handle:
        writer.write(handle)
    return {
        "artifacts": [artifact(output, role="in_place" if args.get("in_place") else "output")],
        "data": {"angle": angle, "pages": pages, "work_dir": str(directory)},
        "engine_version": engine_version("pypdf"),
    }


@register("pdf_form_list", schema="pdf_form_list", next_actions=["pdf_form_fill"])
def pdf_form_list_command(args: Dict[str, Any]) -> Dict[str, Any]:
    source = resolve_path(args.get("path"), args=args, field="path", must_exist=True)
    reader = _open_reader(source)
    fields = {}
    try:
        fields = reader.get_fields() or {}
    except Exception as exc:
        raise OfficeError("E_ENGINE_FAILED", "表单字段读取失败", detail=str(exc)) from exc
    entries = []
    for name, field in fields.items():
        entries.append(
            {
                "name": str(name),
                "type": str(field.get("/FT", "")),
                "value": str(field.get("/V", "")),
                "flags": int(field.get("/Ff", 0) or 0),
                "options": [str(item) for item in field.get("/Opt", []) or []],
            }
        )
    return {"data": {"fields": entries, "field_count": len(entries)}}


@register("pdf_form_fill", schema="pdf_form_fill", engine="pypdf", next_actions=["office_validate"])
def pdf_form_fill_command(args: Dict[str, Any]) -> Dict[str, Any]:
    source = resolve_path(args.get("path"), args=args, field="path", must_exist=True)
    values = args.get("values")
    if not isinstance(values, dict) or not values:
        raise OfficeError("E_INPUT_SCHEMA", "values 必须是非空对象")
    task_id, directory, output = _prepare_output(args, source.name)
    reader = _open_reader(source)
    available = set((reader.get_fields() or {}).keys())
    unknown = [name for name in values if name not in available]
    if unknown and bool(args.get("strict", True)):
        raise OfficeError(
            "E_ANCHOR_NOT_FOUND",
            "表单字段不存在",
            detail="unknown=%s available=%s" % (unknown, sorted(available)),
            remedy="先用 pdf_form_list 获取字段名，或设置 strict=false",
        )
    writer = _writer()
    writer.append(reader)
    for page in writer.pages:
        try:
            writer.update_page_form_field_values(page, values)
        except Exception as exc:
            raise OfficeError(
                "E_ENGINE_FAILED", "表单填写失败", detail=str(exc)
            ) from exc
    try:
        writer.set_need_appearances_writer(True)
    except Exception:
        pass
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("wb") as handle:
        writer.write(handle)
    return {
        "artifacts": [artifact(output, role="in_place" if args.get("in_place") else "output")],
        "data": {"filled": sorted(values), "unknown": unknown, "work_dir": str(directory)},
        "engine_version": engine_version("pypdf"),
    }


@register("pdf_encrypt", schema="pdf_encrypt", engine="pypdf", next_actions=["office_validate"])
def pdf_encrypt_command(args: Dict[str, Any]) -> Dict[str, Any]:
    source = resolve_path(args.get("path"), args=args, field="path", must_exist=True)
    password = str(args.get("password") or "")
    if not password:
        raise OfficeError("E_INPUT_SCHEMA", "password 不能为空")
    task_id, directory, output = _prepare_output(args, source.name)
    reader = _open_reader(source)
    writer = _writer()
    writer.append(reader)
    writer.encrypt(password, algorithm=str(args.get("algorithm") or "AES-256"))
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("wb") as handle:
        writer.write(handle)
    return {
        "artifacts": [artifact(output, role="in_place" if args.get("in_place") else "output")],
        "data": {"algorithm": str(args.get("algorithm") or "AES-256"), "work_dir": str(directory)},
        "engine_version": engine_version("pypdf"),
    }


@register("pdf_decrypt", schema="pdf_decrypt", engine="pypdf", next_actions=["office_validate"])
def pdf_decrypt_command(args: Dict[str, Any]) -> Dict[str, Any]:
    from pypdf import PdfReader  # type: ignore

    source = resolve_path(args.get("path"), args=args, field="path", must_exist=True)
    password = str(args.get("password") or "")
    task_id, directory, output = _prepare_output(args, source.name)
    try:
        reader = PdfReader(str(source))
        if reader.is_encrypted and not reader.decrypt(password):
            raise OfficeError(
                "E_DOC_CORRUPT",
                "密码错误，无法解密 PDF",
                remedy="确认用户提供的密码；不要反复尝试",
            )
    except OfficeError:
        raise
    except Exception as exc:
        raise OfficeError("E_DOC_CORRUPT", "PDF 解密失败", detail=str(exc)) from exc
    writer = _writer()
    writer.append(reader)
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("wb") as handle:
        writer.write(handle)
    return {
        "artifacts": [artifact(output, role="in_place" if args.get("in_place") else "output")],
        "data": {"work_dir": str(directory)},
        "engine_version": engine_version("pypdf"),
    }


@register("pdf_watermark", schema="pdf_watermark", engine="reportlab+pypdf", next_actions=["office_validate"])
def pdf_watermark_command(args: Dict[str, Any]) -> Dict[str, Any]:
    from reportlab.lib.pagesizes import A4  # type: ignore
    from reportlab.pdfgen import canvas  # type: ignore

    source = resolve_path(args.get("path"), args=args, field="path", must_exist=True)
    text = str(args.get("text") or "")
    if not text:
        raise OfficeError("E_INPUT_SCHEMA", "text 不能为空")
    task_id, directory, output = _prepare_output(args, source.name)
    info = pdf_info(source)
    font_name = "Helvetica"
    if any(ord(char) > 127 for char in text):
        fonts = require_cjk_fonts(purpose="PDF 中文水印")
        font_name = str(args.get("cjk_font") or (fonts["font_families"][0] if fonts["font_families"] else "STSong-Light"))
    watermark_buffer = io.BytesIO()
    pdf_canvas = canvas.Canvas(watermark_buffer, pagesize=A4)
    if font_name in ("STSong-Light", "STSong"):
        from reportlab.pdfbase import pdfmetrics  # type: ignore
        from reportlab.pdfbase.cidfonts import UnicodeCIDFont  # type: ignore

        pdfmetrics.registerFont(UnicodeCIDFont("STSong-Light"))
        pdf_canvas.setFont("STSong-Light", float(args.get("size") or 42))
    else:
        try:
            pdf_canvas.setFont(font_name, float(args.get("size") or 42))
        except Exception as exc:
            raise OfficeError(
                "E_ENGINE_FAILED",
                "水印字体不可用: %s" % font_name,
                detail=str(exc),
                remedy="先用 office_env_check 确认 fonts.font_families，再传入 cjk_font",
            ) from exc
    pdf_canvas.setFillGray(0.6)
    pdf_canvas.setFillAlpha(float(args.get("opacity") or 0.3))
    pdf_canvas.saveState()
    pdf_canvas.translate(A4[0] / 2, A4[1] / 2)
    pdf_canvas.rotate(float(args.get("angle") or 45))
    pdf_canvas.drawCentredString(0, 0, text)
    pdf_canvas.restoreState()
    pdf_canvas.save()
    watermark_buffer.seek(0)

    from pypdf import PdfReader  # type: ignore

    watermark_page = PdfReader(watermark_buffer).pages[0]
    reader = _open_reader(source)
    writer = _writer()
    for page in reader.pages:
        page.merge_page(watermark_page)
        writer.add_page(page)
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("wb") as handle:
        writer.write(handle)
    return {
        "artifacts": [artifact(output, role="in_place" if args.get("in_place") else "output")],
        "data": {"text": text, "font": font_name, "pages": info["pages"], "work_dir": str(directory)},
        "engine_version": engine_version("pypdf"),
    }


@register("pdf_to_images", schema="pdf_to_images", engine="pdftoppm", next_actions=["office_read"])
def pdf_to_images_command(args: Dict[str, Any]) -> Dict[str, Any]:
    pdftoppm = require_binary("pdftoppm", tier=2, purpose="PDF 转图")
    task_id = resolve_task_id(args)
    directory = task_dir(task_id)
    source = resolve_path(args.get("path"), args=args, field="path", must_exist=True)
    info = pdf_info(source)
    pages = parse_page_range(args.get("range"), info["pages"]) or list(
        range(1, min(info["pages"], int(args.get("max_pages") or 8)) + 1)
    )
    dpi = int(args.get("dpi") or 150)
    target_dir = directory / "out" / (source.stem + "-images")
    target_dir.mkdir(parents=True, exist_ok=True)
    artifacts: List[Dict[str, Any]] = []
    for page in pages:
        prefix = target_dir / ("page-%03d" % page)
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
            str(source),
            str(prefix),
        ]
        completed = subprocess.run(command, capture_output=True, text=True, timeout=300, check=False)
        if completed.returncode != 0:
            raise OfficeError(
                "E_ENGINE_FAILED",
                "pdftoppm 第 %d 页渲染失败" % page,
                detail=(completed.stderr or "")[-1000:],
            )
        produced = Path(str(prefix) + ".jpg")
        if not produced.is_file():
            raise OfficeError(
                "E_ENGINE_FAILED",
                "pdftoppm 未产出图片",
                detail=str(produced),
            )
        artifacts.append(artifact(produced))
    return {
        "artifacts": artifacts,
        "data": {
            "pages": pages,
            "dpi": dpi,
            "target_dir": str(target_dir),
            "work_dir": str(directory),
        },
        "engine_version": engine_version("poppler-utils"),
    }


@register("pdf_create", schema="pdf_create", engine="reportlab", next_actions=["office_validate", "office_render_preview"])
def pdf_create_command(args: Dict[str, Any]) -> Dict[str, Any]:
    engine = str(args.get("engine") or "")
    if engine not in ("reportlab", "pandoc", "weasyprint"):
        raise OfficeError(
            "E_INPUT_SCHEMA",
            "engine 必须显式指定为 reportlab/pandoc/weasyprint",
            remedy="不要依赖默认引擎；不同引擎的中文与排版行为不同",
        )
    if engine != "reportlab":
        return _create_with_external_engine(args, engine)

    from reportlab.lib.pagesizes import A4  # type: ignore
    from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet  # type: ignore
    from reportlab.lib.units import cm  # type: ignore
    from reportlab.pdfbase import pdfmetrics  # type: ignore
    from reportlab.pdfbase.cidfonts import UnicodeCIDFont  # type: ignore
    from reportlab.pdfbase.ttfonts import TTFont  # type: ignore
    from reportlab.platypus import Paragraph, SimpleDocTemplate, Spacer  # type: ignore

    task_id = resolve_task_id(args)
    directory = task_dir(task_id)
    output = resolve_output_path(
        args.get("output_path"),
        args=args,
        field="output_path",
        default_dir=directory / "out",
        default_name=str(args.get("file_name") or "document.pdf"),
        overwrite=bool(args.get("overwrite")),
    )
    blocks = args.get("blocks")
    if not isinstance(blocks, list) or not blocks:
        raise OfficeError("E_INPUT_SCHEMA", "blocks 必须是非空数组")

    text_payload = "".join(
        str(block.get("text") or "") for block in blocks if isinstance(block, dict)
    )
    has_cjk = any(ord(char) > 127 for char in text_payload)
    font_name = "Helvetica"
    if has_cjk:
        fonts = require_cjk_fonts(purpose="中文 PDF 生成")
        font_name = str(args.get("cjk_font") or "STSong-Light")
        if font_name in ("STSong-Light", "STSong"):
            pdfmetrics.registerFont(UnicodeCIDFont("STSong-Light"))
        else:
            ttf_path = _find_font_file(fonts["fonts"], font_name)
            if not ttf_path:
                raise OfficeError(
                    "E_ENV_MISSING",
                    "找不到可用于 ReportLab 的 CJK 字体文件: %s" % font_name,
                    detail="fonts=%s" % fonts["fonts"][:5],
                    remedy="用 TTF/OTF 字体文件并通过 cjk_font 指定字体族，或使用 STSong-Light",
                )
            pdfmetrics.registerFont(TTFont(font_name, ttf_path))

    styles = getSampleStyleSheet()
    body_style = ParagraphStyle(
        "KiyoriBody", parent=styles["Normal"], fontName=font_name, fontSize=11, leading=17
    )
    heading_style = ParagraphStyle(
        "KiyoriHeading", parent=styles["Heading1"], fontName=font_name, fontSize=18, leading=24
    )
    flowables: List[Any] = []
    for index, block in enumerate(blocks):
        if not isinstance(block, dict):
            raise OfficeError("E_INPUT_SCHEMA", "blocks[%d] 必须是对象" % index)
        kind = str(block.get("type") or "paragraph")
        text = str(block.get("text") or "")
        if kind in ("heading", "title"):
            flowables.append(Paragraph(text, heading_style))
        elif kind == "paragraph":
            flowables.append(Paragraph(text, body_style))
        elif kind == "spacer":
            flowables.append(Spacer(1, float(block.get("height_cm") or 0.5) * cm))
        else:
            raise OfficeError(
                "E_INPUT_SCHEMA",
                "不支持的 blocks 类型: %s" % kind,
                remedy="使用 heading/title/paragraph/spacer",
            )
        flowables.append(Spacer(1, 0.2 * cm))

    output.parent.mkdir(parents=True, exist_ok=True)
    document = SimpleDocTemplate(
        str(output),
        pagesize=A4,
        leftMargin=float(args.get("margin_cm") or 2) * cm,
        rightMargin=float(args.get("margin_cm") or 2) * cm,
        topMargin=float(args.get("margin_cm") or 2) * cm,
        bottomMargin=float(args.get("margin_cm") or 2) * cm,
    )
    document.build(flowables)
    return {
        "artifacts": [artifact(output)],
        "data": {
            "font": font_name,
            "pages": pdf_info(output)["pages"],
            "work_dir": str(directory),
        },
        "engine_version": engine_version("reportlab"),
    }


def _find_font_file(candidates: List[str], family: str) -> Optional[str]:
    lowered = family.lower().replace(" ", "")
    for item in candidates:
        if lowered in Path(item).name.lower().replace(" ", ""):
            return item
    return None


def _create_with_external_engine(args: Dict[str, Any], engine: str) -> Dict[str, Any]:
    task_id = resolve_task_id(args)
    directory = task_dir(task_id)
    source = resolve_path(args.get("source_path"), args=args, field="source_path", must_exist=True)
    output = resolve_output_path(
        args.get("output_path"),
        args=args,
        field="output_path",
        default_dir=directory / "out",
        default_name=source.stem + ".pdf",
        overwrite=bool(args.get("overwrite")),
    )
    require_cjk_font_files(purpose="中文 PDF 生成")
    if engine == "pandoc":
        pandoc = require_binary("pandoc", tier=2, purpose="pandoc 生成 PDF")
        xelatex = require_binary("xelatex", tier=4, purpose="pandoc 生成 PDF")
        fonts = require_cjk_font_files(purpose="中文 PDF 生成")
        command = [
            pandoc,
            str(source),
            "-o",
            str(output),
            "--pdf-engine",
            xelatex,
            "-V",
            "CJKmainfont=%s" % (args.get("cjk_font") or fonts["font_families"][0]),
        ]
    else:
        weasyprint = require_binary("weasyprint", tier=2, purpose="weasyprint 生成 PDF")
        command = [weasyprint, str(source), str(output)]
    output.parent.mkdir(parents=True, exist_ok=True)
    completed = subprocess.run(command, capture_output=True, text=True, timeout=600, check=False)
    if completed.returncode != 0:
        raise OfficeError(
            "E_ENGINE_FAILED",
            "%s 生成 PDF 失败" % engine,
            detail="exit=%s stderr=%s" % (completed.returncode, (completed.stderr or "")[-2000:]),
            remedy="检查字体与引擎安装；不要自动切换引擎",
        )
    return {
        "artifacts": [artifact(output)],
        "data": {"engine": engine, "pages": pdf_info(output)["pages"], "work_dir": str(directory)},
    }
