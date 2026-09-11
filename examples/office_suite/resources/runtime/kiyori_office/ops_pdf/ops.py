"""pdf_* 命令：信息、提取、页级操作、表单、水印、加解密、转图、生成。"""

from __future__ import annotations

import io
import shutil
import subprocess
from pathlib import Path
from typing import Any, Dict, List, Optional

from ..budget import bounded_text
from ..env import (
    require_binary,
    require_cjk_font_files,
    require_cjk_fonts,
    require_poppler_data,
)
from ..paths import (
    atomic_output,
    artifact,
    resolve_output_dir,
    resolve_output_path,
    resolve_path,
    resolve_task_id,
    task_dir,
)
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
        return _extract_tables(source, pages if pages is not None else list(range(1, info["pages"] + 1)), directory, args)
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
            "pages": pages,
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
    with atomic_output(output) as temporary:
        with temporary.open("wb") as handle:
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
    target_dir = resolve_output_dir(
        args.get("output_path"),
        args=args,
        field="output_path",
        default_dir=directory / "out" / (source.stem + "-split"),
        overwrite=bool(args.get("overwrite")),
    )
    reader = _open_reader(source)
    artifacts: List[Dict[str, Any]] = []
    for page_number in pages:
        writer = _writer()
        writer.add_page(reader.pages[page_number - 1])
        destination = target_dir / ("%s-page-%d.pdf" % (source.stem, page_number))
        with atomic_output(destination) as temporary:
            with temporary.open("wb") as handle:
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
    with atomic_output(output) as temporary:
        with temporary.open("wb") as handle:
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
    with atomic_output(output) as temporary:
        with temporary.open("wb") as handle:
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
    with atomic_output(output) as temporary:
        with temporary.open("wb") as handle:
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
    with atomic_output(output) as temporary:
        with temporary.open("wb") as handle:
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
    try:
        writer.encrypt(password, algorithm=str(args.get("algorithm") or "AES-256"))
    except __import__('pypdf').errors.DependencyError as exc:
        raise OfficeError("E_ENV_MISSING", "PDF AES 加密缺少 cryptography", remedy="用 office_env_setup(tier=1, components=['cryptography']) 获取安装计划") from exc
    output.parent.mkdir(parents=True, exist_ok=True)
    with atomic_output(output) as temporary:
        with temporary.open("wb") as handle:
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
    with atomic_output(output) as temporary:
        with temporary.open("wb") as handle:
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
        font_name = _register_reportlab_cjk_font(args.get("cjk_font"), purpose="PDF 中文水印")
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
                remedy="中文水印使用 STSong-Light；系统字体族不能直接当作 ReportLab 已注册字体。",
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
    # 先把原文档交给 writer，保留表单/元数据，并在 writer 管理的页面上合成。
    # pypdf 不保证在 reader 页面上 replace_contents 后再拷贝关系的可靠性。
    writer.clone_document_from_reader(reader)
    for page in writer.pages:
        page.merge_page(watermark_page)
    output.parent.mkdir(parents=True, exist_ok=True)
    with atomic_output(output) as temporary:
        with temporary.open("wb") as handle:
            writer.write(handle)
    return {
        "artifacts": [artifact(output, role="in_place" if args.get("in_place") else "output")],
        "data": {"text": text, "font": font_name, "pages": info["pages"], "work_dir": str(directory)},
        "engine_version": engine_version("pypdf"),
    }


@register("pdf_to_images", schema="pdf_to_images", engine="pdftoppm", next_actions=["office_read"])
def pdf_to_images_command(args: Dict[str, Any]) -> Dict[str, Any]:
    pdftoppm = require_binary("pdftoppm", tier=2, purpose="PDF 转图")
    # 同 office_render_preview：缺 CMap 时退出码为 0 但产出空白图。
    require_poppler_data(purpose="PDF 转图")
    task_id = resolve_task_id(args)
    directory = task_dir(task_id)
    source = resolve_path(args.get("path"), args=args, field="path", must_exist=True)
    info = pdf_info(source)
    pages = parse_page_range(args.get("range"), info["pages"]) or list(
        range(1, min(info["pages"], int(args.get("max_pages") or 8)) + 1)
    )
    dpi = int(args.get("dpi") or 150)
    target_dir = resolve_output_dir(
        args.get("output_path"),
        args=args,
        field="output_path",
        default_dir=directory / "out" / (source.stem + "-images"),
        overwrite=bool(args.get("overwrite")),
    )
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
        produced = Path(str(prefix) + ".jpg")
        from ..engines import run_output_command
        run_output_command(command, produced, -1, timeout=300,
                           purpose="pdftoppm 第 %d 页渲染" % page, output_is_prefix=True)
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

    # 必须覆盖 items：否则「只有 bullet 列表的中文文档」会漏检 CJK，
    # 用 Helvetica 渲染出方框。
    text_payload_parts: List[str] = []
    for block in blocks:
        if not isinstance(block, dict):
            continue
        text_payload_parts.append(str(block.get("text") or ""))
        items = block.get("items")
        if isinstance(items, list):
            text_payload_parts.extend(str(item) for item in items)
        if isinstance(block.get("rows"), list):
            text_payload_parts.extend(str(cell) for row in block["rows"] if isinstance(row, list) for cell in row)
        text_payload_parts.append(str(block.get("caption") or ""))
    text_payload = "".join(text_payload_parts)
    has_cjk = any(ord(char) > 127 for char in text_payload)
    font_name = "Helvetica"
    if has_cjk:
        font_name = _register_reportlab_cjk_font(args.get("cjk_font"), purpose="中文 PDF 生成")

    styles = getSampleStyleSheet()
    body_style = ParagraphStyle(
        "KiyoriBody", parent=styles["Normal"], fontName=font_name, fontSize=11, leading=17
    )
    heading_style = ParagraphStyle(
        "KiyoriHeading", parent=styles["Heading1"], fontName=font_name, fontSize=18, leading=24
    )
    bullet_style = ParagraphStyle(
        "KiyoriBullet",
        parent=body_style,
        leftIndent=14,
        bulletIndent=2,
        spaceAfter=2,
    )

    def bullet_items(block: Dict[str, Any], index: int) -> List[str]:
        """bullet/list 块接受 items 数组或单个 text。"""

        raw = block.get("items")
        if raw is None:
            text = str(block.get("text") or "")
            return [text] if text else []
        if not isinstance(raw, list):
            raise OfficeError("E_INPUT_SCHEMA", "blocks[%d].items 必须是数组" % index)
        return [str(item) for item in raw]

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
        elif kind in ("bullet", "bullets", "list"):
            # 真机报告：bullet 曾报「不支持的 blocks 类型」；列表是文档生成的基础能力。
            for item in bullet_items(block, index):
                flowables.append(
                    Paragraph(item, bullet_style, bulletText=str(block.get("bullet") or "\u2022"))
                )
        elif kind == "spacer":
            flowables.append(Spacer(1, float(block.get("height_cm") or 0.5) * cm))
        elif kind == "image":
            from reportlab.platypus import Image as PdfImage, KeepTogether
            from PIL import Image
            from ..ops_docx.layout import number
            image = resolve_path(block.get("image_path"), args=args, field="image_path", must_exist=True)
            usable_width = A4[0] - 2 * float(args.get("margin_cm") or 2) * cm
            usable_height = A4[1] - 2 * float(args.get("margin_cm") or 2) * cm - 24
            with Image.open(image) as picture:
                ratio = picture.height / picture.width
            width = number(block["width_cm"], "image.width_cm", 0.1, 100)*cm if "width_cm" in block else min(usable_width, usable_height/ratio)
            if width > usable_width or width*ratio > usable_height:
                raise OfficeError("E_INPUT_SCHEMA", "图片尺寸超出 PDF 正文区域")
            objects = [PdfImage(str(image), width=width, height=width*ratio)]
            if block.get("caption"):
                objects.append(Paragraph(block["caption"], body_style))
            flowables.append(KeepTogether(objects))
        elif kind == "table":
            from reportlab.platypus import Table, TableStyle
            from reportlab.lib import colors
            from ..ops_docx.layout import table_rows, number
            rows = block.get("rows")
            columns = table_rows(rows)
            if any(len(row) != columns for row in rows):
                raise OfficeError("E_INPUT_SCHEMA", "PDF table.rows 必须是矩形数组")
            usable_width = A4[0] - 2 * float(args.get("margin_cm") or 2) * cm
            widths = block.get("column_widths_cm")
            if widths is not None:
                if not isinstance(widths,list) or len(widths) != columns:
                    raise OfficeError("E_INPUT_SCHEMA", "column_widths_cm 数量必须与列数一致")
                widths = [number(value,"column_widths_cm",0.1,50)*cm for value in widths]
                if sum(widths)>usable_width:
                    raise OfficeError("E_INPUT_SCHEMA", "表格宽度超出正文")
            else:
                widths = [usable_width/columns]*columns
            from xml.sax.saxutils import escape
            table = Table([[Paragraph(escape('' if value is None else str(value)),body_style) for value in row] for row in rows], colWidths=widths, repeatRows=1)
            table.setStyle(TableStyle([('VALIGN',(0,0),(-1,-1),'TOP'),('LINEABOVE',(0,0),(-1,0),1,colors.black),
                                      ('LINEBELOW',(0,0),(-1,0),0.5,colors.black),('LINEBELOW',(0,-1),(-1,-1),1,colors.black),
                                      ('BOTTOMPADDING',(0,0),(-1,-1),6)]))
            flowables.append(table)
        else:
            raise OfficeError(
                "E_INPUT_SCHEMA",
                "不支持的 blocks 类型: %s" % kind,
                remedy="使用 heading/title/paragraph/bullet/spacer",
            )
        if kind not in ("bullet", "bullets", "list"):
            flowables.append(Spacer(1, 0.2 * cm))

    output.parent.mkdir(parents=True, exist_ok=True)
    with atomic_output(output) as temporary:
        document = SimpleDocTemplate(
            str(temporary),
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


def _register_reportlab_cjk_font(requested, *, purpose: str) -> str:
    from reportlab.pdfbase import pdfmetrics
    from reportlab.pdfbase.cidfonts import UnicodeCIDFont
    from reportlab.pdfbase.ttfonts import TTFont

    fonts = require_cjk_fonts(purpose=purpose)
    family = str(requested or 'STSong-Light')
    if family in ('STSong', 'STSong-Light'):
        pdfmetrics.registerFont(UnicodeCIDFont('STSong-Light'))
        return 'STSong-Light'
    # Fontconfig 的族名与文件名不是一回事；先精确解析已探测的族名，禁止静默换字体。
    path = next((item['file'] for item in fonts.get('fontconfig_fonts', [])
                 if item['family'].casefold() == family.casefold()), None)
    path = path or _find_font_file(fonts.get('fonts', []), family)
    if not path:
        raise OfficeError('E_INPUT_SCHEMA', '找不到已探测的中文字体族: %s' % family,
                          remedy='运行 office_env_check 查看字体；可显式使用 STSong-Light，或安装兼容的 TrueType 中文字体。')
    try:
        font = TTFont(family, path)
        with open(path, 'rb') as handle:
            is_collection = handle.read(4) == b'ttcf'
        # TTC 的首个 face 可能是 JP 而请求的是 SC；不能把首个 face 改名后冒充目标字体。
        actual_family = font.face.familyName
        if isinstance(actual_family, bytes):
            actual_family = actual_family.decode('utf-8', errors='replace')
        if is_collection and actual_family.casefold() != family.casefold():
            raise ValueError('TTC 首个 face 为 %s，无法确认请求的 %s' % (actual_family, family))
        pdfmetrics.registerFont(font)
    except Exception as exc:
        raise OfficeError('E_FORMAT_UNSUPPORTED', 'ReportLab 不支持此字体文件: %s' % family,
                          detail=str(exc), remedy='CFF/部分 TTC 字体无法嵌入；请显式使用 STSong-Light 或 TrueType 中文字体。') from exc
    return family


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
    fonts = require_cjk_font_files(purpose="中文 PDF 生成")
    if engine == "pandoc":
        pandoc = require_binary("pandoc", tier=2, purpose="pandoc 生成 PDF")
        xelatex = require_binary("xelatex", tier=4, purpose="pandoc 生成 PDF")
        from ..env import select_system_cjk_font
        command = [
            pandoc,
            str(source),
            "-o",
            str(output),
            "--pdf-engine",
            xelatex,
            "-V",
            "CJKmainfont=%s" % select_system_cjk_font(fonts, args.get('cjk_font')),
        ]
    else:
        weasyprint = require_binary("weasyprint", tier=2, purpose="weasyprint 生成 PDF")
        command = [weasyprint, str(source), str(output)]
    from ..engines import run_output_command
    run_output_command(command, output, command.index("-o") + 1 if engine == "pandoc" else -1,
                       timeout=600, purpose="%s 生成 PDF" % engine, validate=pdf_info)
    return {
        "artifacts": [artifact(output)],
        "data": {"engine": engine, "pages": pdf_info(output)["pages"], "work_dir": str(directory)},
    }
