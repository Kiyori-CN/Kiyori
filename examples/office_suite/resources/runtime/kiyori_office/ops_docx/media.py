"""docx_insert_image / docx_extract_media。"""

from __future__ import annotations

import zipfile
from pathlib import Path
from typing import Any, Dict, List

from ..paths import (
    artifact,
    atomic_write_bytes,
    resolve_output_path,
    resolve_path,
    resolve_task_id,
    sanitize_filename,
    task_dir,
)
from ..protocol import OfficeError, engine_version, register
from ..readers.docx_reader import require_docx

@register(
    "docx_insert_image",
    schema="docx_insert_image",
    engine="python-docx",
    next_actions=["office_validate", "office_render_preview"],
)
def docx_insert_image(args: Dict[str, Any]) -> Dict[str, Any]:
    require_docx()
    import docx  # type: ignore
    from docx.enum.text import WD_ALIGN_PARAGRAPH  # type: ignore
    from docx.shared import Cm  # type: ignore

    task_id = resolve_task_id(args)
    directory = task_dir(task_id)
    source = resolve_path(args.get("path"), args=args, field="path", must_exist=True)
    image = resolve_path(args.get("image_path"), args=args, field="image_path", must_exist=True)
    output = resolve_output_path(
        args.get("output_path"),
        args=args,
        field="output_path",
        default_dir=directory / "out",
        default_name=source.name,
        overwrite=bool(args.get("overwrite")),
        in_place=bool(args.get("in_place")),
    )
    document = docx.Document(str(source))
    paragraph = document.add_paragraph()
    run = paragraph.add_run()
    width = Cm(float(args["width_cm"])) if args.get("width_cm") else None
    if width is not None and width > document.sections[0].page_width:
        width = None
    run.add_picture(str(image), width=width)
    if args.get("alignment"):
        paragraph.alignment = getattr(WD_ALIGN_PARAGRAPH, str(args["alignment"]).upper())
    output.parent.mkdir(parents=True, exist_ok=True)
    document.save(str(output))
    return {
        "artifacts": [artifact(output)],
        "data": {"image": str(image), "work_dir": str(directory)},
        "engine_version": engine_version("python-docx"),
    }


@register(
    "docx_extract_media",
    schema="docx_extract_media",
    engine="zipfile",
    next_actions=["office_read"],
)
def docx_extract_media(args: Dict[str, Any]) -> Dict[str, Any]:
    task_id = resolve_task_id(args)
    directory = task_dir(task_id)
    source = resolve_path(args.get("path"), args=args, field="path", must_exist=True)
    target_dir = directory / "out" / sanitize_filename(
        str(args.get("target_dir_name") or (source.stem + "-media"))
    )
    target_dir.mkdir(parents=True, exist_ok=True)
    extracted: List[Dict[str, Any]] = []
    try:
        with zipfile.ZipFile(source) as archive:
            for name in archive.namelist():
                if not name.startswith("word/media/") or name.endswith("/"):
                    continue
                payload = archive.read(name)
                destination = target_dir / sanitize_filename(Path(name).name)
                atomic_write_bytes(destination, payload)
                extracted.append(artifact(destination))
    except zipfile.BadZipFile as exc:
        raise OfficeError(
            "E_DOC_CORRUPT", "DOCX 不是有效的 ZIP 容器", detail=str(exc)
        ) from exc
    return {
        "artifacts": extracted,
        "data": {
            "count": len(extracted),
            "target_dir": str(target_dir),
            "work_dir": str(directory),
        },
    }
