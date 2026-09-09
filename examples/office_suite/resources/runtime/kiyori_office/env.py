"""Tier 环境探测与安装计划。

分层与「缺失即明确失败」原则见设计文档 §4：

- T1 纯 Python 库：读取与生成；
- T2 pandoc / poppler / CJK 字体：转换与渲染；
- T3 LibreOffice：视觉预览与 xlsx 公式重算；
- T4 tesseract / texlive：可选。

本模块只负责探测与生成计划；真正的安装由 JS 层用可见 PTY 会话流式执行，
Python 侧不执行 apt/pip，保证可脱离 Kiyori 独立测试。
"""

from __future__ import annotations

import glob
import os
import shutil
from pathlib import Path
from typing import Any, Dict, List, Optional

from .protocol import OfficeError, engine_version, python_identity, register
from .paths import free_bytes, work_root

TIER_COMPONENTS: Dict[int, List[str]] = {
    1: [
        "python-docx",
        "openpyxl",
        "python-pptx",
        "pypdf",
        "reportlab",
        "lxml",
        "Pillow",
    ],
    2: ["pandoc", "poppler-utils", "cjk-fonts"],
    3: ["libreoffice"],
    4: ["tesseract-ocr", "texlive-xetex"],
}

TIER_ESTIMATED_BYTES: Dict[int, int] = {
    1: 90 * 1024 * 1024,
    2: 350 * 1024 * 1024,
    3: 900 * 1024 * 1024,
    4: 400 * 1024 * 1024,
}

PYTHON_MODULES = {
    "python-docx": "docx",
    "openpyxl": "openpyxl",
    "python-pptx": "pptx",
    "pypdf": "pypdf",
    "pdfplumber": "pdfplumber",
    "reportlab": "reportlab",
    "lxml": "lxml",
    "Pillow": "PIL",
    "pandas": "pandas",
}

CJK_FONT_GLOBS = [
    "/usr/share/fonts/**/NotoSansCJK*.ttc",
    "/usr/share/fonts/**/NotoSerifCJK*.ttc",
    "/usr/share/fonts/**/NotoSansCJK*.otf",
    "/usr/share/fonts/**/NotoSerifCJK*.otf",
    "/usr/share/fonts/**/DroidSansFallback*.ttf",
    "/system/fonts/NotoSansCJK*.ttc",
    "/system/fonts/NotoSerifCJK*.ttc",
    "/system/fonts/DroidSansFallback*.ttf",
    os.path.expanduser("~/.fonts/**/Noto*CJK*"),
    os.path.expanduser("~/.local/share/fonts/**/Noto*CJK*"),
]


def _module_state(module_name: str) -> Dict[str, Any]:
    import importlib

    try:
        module = importlib.import_module(module_name)
    except Exception as exc:  # noqa: BLE001
        return {"available": False, "detail": "%s: %s" % (type(exc).__name__, exc)}
    version = str(getattr(module, "__version__", "") or "")
    return {"available": True, "version": version}


def _binary_state(name: str) -> Dict[str, Any]:
    path = shutil.which(name)
    return {"available": bool(path), "path": path or ""}


def detect_cjk_fonts() -> Dict[str, Any]:
    found: List[str] = []
    for pattern in CJK_FONT_GLOBS:
        for item in glob.glob(pattern, recursive=True):
            if item not in found:
                found.append(item)
    families: List[str] = []
    for item in found:
        base = Path(item).name
        family = base
        for suffix in (".ttc", ".otf", ".ttf"):
            if family.lower().endswith(suffix):
                family = family[: -len(suffix)]
        family = family.replace("NotoSansCJKsc", "Noto Sans CJK SC")
        family = family.replace("NotoSansCJK", "Noto Sans CJK")
        family = family.replace("NotoSerifCJKsc", "Noto Serif CJK SC")
        family = family.replace("NotoSerifCJK", "Noto Serif CJK")
        if family not in families:
            families.append(family)
    android_fonts = [item for item in found if item.startswith("/system/fonts/")]
    reportlab_cid = _reportlab_cid_available()
    if reportlab_cid and "STSong-Light" not in families:
        families.append("STSong-Light")
    return {
        # reportlab 自带的 Adobe CJK CID 字体不需要字体文件即可渲染中文；
        # pandoc/LibreOffice 仍必须有真实字体文件（见 require_cjk_font_files）。
        "cjk_available": bool(found) or reportlab_cid,
        "font_files_available": bool(found),
        "reportlab_cid_available": reportlab_cid,
        "fonts": found,
        "font_families": families,
        "android_fonts_visible": android_fonts,
    }


def _reportlab_cid_available() -> bool:
    try:
        from reportlab.pdfbase import pdfmetrics  # type: ignore
        from reportlab.pdfbase.cidfonts import UnicodeCIDFont  # type: ignore

        pdfmetrics.registerFont(UnicodeCIDFont("STSong-Light"))
        return True
    except Exception:
        return False


def detect(verbose: bool = False) -> Dict[str, Any]:
    tiers: Dict[str, Any] = {}
    for tier, components in TIER_COMPONENTS.items():
        entries: Dict[str, Any] = {}
        complete = True
        for component in components:
            if component in PYTHON_MODULES:
                state = _module_state(PYTHON_MODULES[component])
            elif component == "cjk-fonts":
                fonts = detect_cjk_fonts()
                state = {
                    "available": fonts["cjk_available"],
                    "families": fonts["font_families"],
                }
            elif component == "poppler-utils":
                pdftoppm = _binary_state("pdftoppm")
                pdftotext = _binary_state("pdftotext")
                state = {
                    "available": pdftoppm["available"] and pdftotext["available"],
                    "pdftoppm": pdftoppm["path"],
                    "pdftotext": pdftotext["path"],
                }
            else:
                state = _binary_state(component)
            entries[component] = state
            if not state.get("available"):
                complete = False
        tiers["tier%d" % tier] = {
            "complete": complete,
            "components": entries,
            "estimated_install_bytes": TIER_ESTIMATED_BYTES[tier],
        }

    fonts = detect_cjk_fonts()
    payload: Dict[str, Any] = {
        "python": python_identity(),
        "tiers": tiers,
        "fonts": fonts,
        "disk_free_bytes": free_bytes(),
        "work_root": str(work_root()),
        "optional": {
            "pdfplumber": _module_state("pdfplumber"),
            "pandas": _module_state("pandas"),
        },
    }
    if verbose:
        payload["path"] = os.environ.get("PATH", "")
        payload["tier_components"] = TIER_COMPONENTS
    return payload


def require_tier(tier: int, *, purpose: str) -> Dict[str, Any]:
    """确认某个 Tier 完整可用，否则抛出 E_ENV_MISSING 并给出 remedy。"""

    state = detect(verbose=False)["tiers"].get("tier%d" % tier)
    if state is None:
        raise OfficeError("E_INPUT_SCHEMA", "未知 Tier: %s" % tier)
    if state["complete"]:
        return state
    missing = [
        name
        for name, entry in state["components"].items()
        if not entry.get("available")
    ]
    raise OfficeError(
        "E_ENV_MISSING",
        "%s 需要 Tier%d 组件，当前缺失：%s" % (purpose, tier, ", ".join(missing)),
        detail="tier%d missing=%s" % (tier, ", ".join(missing)),
        remedy="调用 office_env_setup 并指定 tier=%d；不要改用「差不多」的替代方案" % tier,
    )


def require_binary(name: str, *, tier: int, purpose: str) -> str:
    path = shutil.which(name)
    if not path:
        raise OfficeError(
            "E_ENV_MISSING",
            "%s 需要可执行文件 %s（Tier%d）" % (purpose, name, tier),
            detail="%s not found in PATH" % name,
            remedy="调用 office_env_setup 并指定 tier=%d" % tier,
        )
    return path


def require_cjk_fonts(*, purpose: str) -> Dict[str, Any]:
    fonts = detect_cjk_fonts()
    if not fonts["cjk_available"]:
        raise OfficeError(
            "E_ENV_MISSING",
            "%s 需要 CJK 字体，否则中文会渲染成方框" % purpose,
            detail="no Noto CJK / DroidSansFallback font found",
            remedy=(
                "优先用 office_env_setup 从 Android /system/fonts 复制 NotoSansCJK*.ttc "
                "到 Linux ~/.fonts 后执行 fc-cache -f；否则再安装 fonts-noto-cjk"
            ),
        )
    return fonts


def require_cjk_font_files(*, purpose: str) -> Dict[str, Any]:
    """pandoc/LibreOffice 路线需要真实字体文件，reportlab CID 字体不算。"""

    fonts = detect_cjk_fonts()
    if not fonts["font_files_available"]:
        raise OfficeError(
            "E_ENV_MISSING",
            "%s 需要真实 CJK 字体文件，否则中文会渲染成方框" % purpose,
            detail="no Noto CJK / DroidSansFallback font file found",
            remedy=(
                "优先用 office_env_setup 从 Android /system/fonts 复制 NotoSansCJK*.ttc "
                "到 Linux ~/.fonts 后执行 fc-cache -f；否则再安装 fonts-noto-cjk"
            ),
        )
    return fonts


def build_plan(tier: int, components: Optional[List[str]] = None) -> Dict[str, Any]:
    if tier not in TIER_COMPONENTS:
        raise OfficeError(
            "E_INPUT_SCHEMA",
            "tier 必须是 1-4",
            detail="tier=%r" % tier,
        )
    selected = components or TIER_COMPONENTS[tier]
    unknown = [name for name in selected if name not in TIER_COMPONENTS[tier]]
    if unknown:
        raise OfficeError(
            "E_INPUT_SCHEMA",
            "tier%d 不包含这些组件: %s" % (tier, ", ".join(unknown)),
            detail="allowed=%s" % ", ".join(TIER_COMPONENTS[tier]),
        )

    commands: List[str] = []
    python_packages = [name for name in selected if name in PYTHON_MODULES]
    if python_packages:
        commands.append("python3 -m pip install --user " + " ".join(python_packages))
    apt_packages: List[str] = []
    if "pandoc" in selected:
        apt_packages.append("pandoc")
    if "poppler-utils" in selected:
        apt_packages.append("poppler-utils")
    if "libreoffice" in selected:
        apt_packages.append("libreoffice-core libreoffice-writer libreoffice-calc libreoffice-impress")
    if "tesseract-ocr" in selected:
        apt_packages.append("tesseract-ocr tesseract-ocr-chi-sim")
    if "texlive-xetex" in selected:
        apt_packages.append("texlive-xetex texlive-fonts-recommended")
    if "cjk-fonts" in selected:
        android_fonts = detect_cjk_fonts()["android_fonts_visible"]
        if android_fonts:
            commands.append(
                "mkdir -p ~/.fonts && cp %s ~/.fonts/ && fc-cache -f"
                % " ".join(android_fonts)
            )
        else:
            apt_packages.append("fonts-noto-cjk")
    if apt_packages:
        commands.insert(0, "apt-get install -y " + " ".join(apt_packages))

    state = detect(verbose=False)
    return {
        "tier": tier,
        "components": selected,
        "commands": commands,
        "estimated_bytes": TIER_ESTIMATED_BYTES[tier],
        "disk_free_bytes": state["disk_free_bytes"],
        "fonts": state["fonts"],
        "python": state["python"],
        "note": "安装命令需通过可见 PTY 会话流式执行；失败保留现场，不自动回滚或重试。",
    }


@register("office_env_check", schema="office_env_check", next_actions=["office_env_setup"])
def office_env_check(args: Dict[str, Any]) -> Dict[str, Any]:
    verbose = bool(args.get("verbose"))
    state = detect(verbose=verbose)
    return {
        "data": state,
        "next_actions": ["office_env_setup"] if not state["tiers"]["tier1"]["complete"] else [],
    }


@register("office_env_setup", schema="office_env_setup", next_actions=["office_env_check"])
def office_env_setup(args: Dict[str, Any]) -> Dict[str, Any]:
    """只返回安装计划；真正的执行由 JS 层可见 PTY 完成。"""

    tier = int(args.get("tier") or 1)
    components = args.get("components")
    if components is not None and not isinstance(components, list):
        raise OfficeError("E_INPUT_SCHEMA", "components 必须是字符串数组")
    plan = build_plan(tier, components)
    plan["confirm_required"] = True
    plan["execution"] = "visible_pty"
    plan["executed"] = False
    if args.get("confirm") is True:
        plan["executed"] = False
        plan["note"] = (
            "confirm=true 已记录；命令必须由 JS 层在可见终端逐条执行，"
            "Python 侧不直接调用 apt/pip。"
        )
    return {"data": plan}


def engine_versions() -> Dict[str, str]:
    return {
        "python-docx": engine_version("python-docx"),
        "openpyxl": engine_version("openpyxl"),
        "python-pptx": engine_version("python-pptx"),
        "pypdf": engine_version("pypdf"),
        "reportlab": engine_version("reportlab"),
    }
