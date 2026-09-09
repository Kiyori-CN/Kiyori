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
import shlex
import subprocess
import sys
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
        "cryptography",
    ],
    2: ["pandoc", "poppler-utils", "poppler-data", "cjk-fonts"],
    3: ["libreoffice"],
    4: ["tesseract-ocr", "texlive-xetex"],
}

# apt 包标识保持工具契约稳定，探测必须使用实际可执行文件名。
COMPONENT_BINARIES = {'tesseract-ocr': 'tesseract', 'texlive-xetex': 'xelatex'}

# LibreOffice 的 soffice 可执行文件与「文档组件」是分开安装的：只装
# libreoffice-writer 时 soffice 存在、Writer 能转 docx，但 Calc/Impress 缺失，
# xlsx/pptx 转换会以 E_ENGINE_FAILED 失败。检测必须覆盖组件库，否则
# office_env_check 会把「不完整安装」误报成 complete=true。
LIBREOFFICE_COMPONENT_LIBS: Dict[str, str] = {
    "writer": "libswlo.so",
    "calc": "libsclo.so",
    "impress": "libsdlo.so",
}

# 文件后缀 → 处理它所需的 LibreOffice 组件。转换与渲染都必须先按此确认组件，
# 否则「装了 Writer 却转 xlsx」会在引擎里失败，报错也定位不到组件缺失。
LIBREOFFICE_SUFFIX_COMPONENTS: Dict[str, str] = {
    ".doc": "writer",
    ".docx": "writer",
    ".odt": "writer",
    ".rtf": "writer",
    ".txt": "writer",
    ".xls": "calc",
    ".xlsx": "calc",
    ".xlsm": "calc",
    ".ods": "calc",
    ".csv": "calc",
    ".ppt": "impress",
    ".pptx": "impress",
    ".odp": "impress",
}

TIER_ESTIMATED_BYTES: Dict[int, int] = {
    1: 90 * 1024 * 1024,
    2: 350 * 1024 * 1024,
    3: 900 * 1024 * 1024,
    4: 3 * 1024 * 1024 * 1024,
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
    "cryptography": "cryptography",
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

# poppler 渲染含 Adobe-GB1（简体中文 CID）字体的 PDF 时需要 CMap 数据包。
# 缺失时 pdftoppm 只打印 "Missing language pack for 'Adobe-GB1' mapping" 警告
# 并以 0 退出，产出的 JPEG 只有背景、文字与水印全部消失，且不同文档的图
# md5 相同——属于极难察觉的静默错误，必须在生成预览前拦截。
POPPLER_CMAP_GLOBS = [
    "/usr/share/poppler/cMap/**/Adobe-GB1*",
    "/usr/share/poppler/cidToUnicode/Adobe-GB1",
    "/usr/local/share/poppler/cMap/**/Adobe-GB1*",
    "/usr/local/share/poppler/cidToUnicode/Adobe-GB1",
    os.path.expanduser("~/.local/share/poppler/cMap/**/Adobe-GB1*"),
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


def _libreoffice_program_dirs(binary: str) -> List[Path]:
    """按「解析符号链接 → 常见安装前缀」顺序给出候选 program 目录。"""

    candidates: List[Path] = []
    try:
        resolved = Path(binary).resolve()
        candidates.append(resolved.parent)
        candidates.append(resolved.parent / "program")
        candidates.append(resolved.parent.parent / "program")
    except OSError:
        pass
    for prefix in ("/usr/lib", "/usr/lib64", "/opt", "/usr/local/lib"):
        candidates.append(Path(prefix) / "libreoffice" / "program")
    unique: List[Path] = []
    for item in candidates:
        if item not in unique:
            unique.append(item)
    return unique


def detect_libreoffice() -> Dict[str, Any]:
    """探测 soffice 可执行文件与其 Writer/Calc/Impress 组件库。"""

    binary = _binary_state("soffice")
    if not binary["available"]:
        binary = _binary_state("libreoffice")
    if not binary["available"]:
        return {
            "available": False,
            "complete": False,
            "path": "",
            "program_dir": "",
            "components": {name: False for name in LIBREOFFICE_COMPONENT_LIBS},
            "missing_components": sorted(LIBREOFFICE_COMPONENT_LIBS),
        }

    components: Dict[str, bool] = {}
    program_dir = ""
    for directory in _libreoffice_program_dirs(binary["path"]):
        if not directory.is_dir():
            continue
        found = {
            name: (directory / lib).is_file()
            for name, lib in LIBREOFFICE_COMPONENT_LIBS.items()
        }
        if not program_dir and any(found.values()):
            program_dir = str(directory)
        for name, present in found.items():
            components[name] = components.get(name, False) or present
    for name in LIBREOFFICE_COMPONENT_LIBS:
        components.setdefault(name, False)

    return {
        "available": True,
        # complete 表示「Writer/Calc/Impress 三个组件库都在」，tier3 判定用它，
        # 避免只装 Writer 时误报整体可用。
        "complete": all(components.values()),
        "path": binary["path"],
        "program_dir": program_dir,
        "components": components,
        "missing_components": sorted(
            name for name, present in components.items() if not present
        ),
    }


def _fontconfig_fonts() -> Dict[str, Any]:
    binary = shutil.which('fc-list')
    if not binary:
        return {'available': False, 'fonts': [], 'detail': 'fc-list not found in PATH'}
    try:
        result = subprocess.run([binary, '--format', '%{family}\t%{file}\n', ':lang=zh-cn'],
                                capture_output=True, text=True, encoding='utf-8', errors='replace',
                                timeout=10, check=False)
    except (OSError, subprocess.TimeoutExpired) as exc:
        return {'available': False, 'fonts': [], 'detail': '%s: %s' % (type(exc).__name__, exc)}
    if result.returncode != 0:
        return {'available': False, 'fonts': [], 'detail': 'fc-list exit=%d: %s' % (result.returncode, result.stderr[:500])}
    records = []
    seen = set()
    for line in result.stdout.splitlines():
        family_text, separator, filename = line.partition('\t')
        if not separator or not Path(filename).is_file():
            continue
        for family in family_text.split(','):
            family = family.strip()
            if family and (family, filename) not in seen:
                seen.add((family, filename))
                records.append({'family': family, 'file': filename})
    return {'available': True, 'fonts': records, 'detail': ''}


def detect_cjk_fonts() -> Dict[str, Any]:
    found: List[str] = []
    for pattern in CJK_FONT_GLOBS:
        for item in glob.glob(pattern, recursive=True):
            if item not in found and Path(item).is_file():
                found.append(item)
    # TTC 包含多个地区的 face；文件名无法推导真实 family。fc-list 返回
    # 已注册且声明中文覆盖的族名，避免 fc-match 的静默替代被误认成命中。
    fontconfig = _fontconfig_fonts()
    for record in fontconfig['fonts']:
        if record['file'] not in found:
            found.append(record['file'])
    preferred = ['Noto Sans CJK SC', 'Noto Serif CJK SC', 'Droid Sans Fallback']
    system_families = sorted({record['family'] for record in fontconfig['fonts']},
                             key=lambda name: (preferred.index(name) if name in preferred else len(preferred), name))
    families = list(system_families)
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
        "system_font_families": system_families,
        "reportlab_font_families": ['STSong-Light'] if reportlab_cid else [],
        "fontconfig_fonts": fontconfig['fonts'],
        "fontconfig_available": fontconfig['available'],
        "fontconfig_detail": fontconfig['detail'],
        "android_fonts_visible": android_fonts,
    }


def detect_poppler_data() -> Dict[str, Any]:
    """探测 poppler 的 Adobe-GB1 CMap 数据包（Debian/Ubuntu 包名 poppler-data）。"""

    found: List[str] = []
    for pattern in POPPLER_CMAP_GLOBS:
        for item in glob.glob(pattern, recursive=True):
            if item not in found:
                found.append(item)
    return {
        "available": bool(found),
        "files": found,
        "detail": (
            "poppler-data 已安装"
            if found
            else "未找到 Adobe-GB1 CMap；中文 PDF 预览会静默产出空白图"
        ),
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
    fonts = detect_cjk_fonts()
    for tier, components in TIER_COMPONENTS.items():
        entries: Dict[str, Any] = {}
        complete = True
        for component in components:
            if component in PYTHON_MODULES:
                state = _module_state(PYTHON_MODULES[component])
            elif component == "cjk-fonts":
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
            elif component == "poppler-data":
                state = detect_poppler_data()
            elif component == "libreoffice":
                # 只检测可执行文件会把「仅装 Writer」误报为 complete；
                # 必须同时确认 Writer/Calc/Impress 组件库，见 LIBREOFFICE_COMPONENT_LIBS。
                state = detect_libreoffice()
            else:
                state = _binary_state(COMPONENT_BINARIES.get(component, component))
            entries[component] = state
            # 组件自身的 complete 优先于「可执行文件存在」：LibreOffice 只有部分
            # 组件时，soffice 可用但 xlsx/pptx 路线仍然不可用。
            if not state.get("available") or (
                "complete" in state and not state.get("complete")
            ):
                complete = False
        tiers["tier%d" % tier] = {
            "complete": complete,
            "components": entries,
            "estimated_install_bytes": TIER_ESTIMATED_BYTES[tier],
        }

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


def require_libreoffice(
    *, purpose: str, component: Optional[str] = None
) -> Dict[str, Any]:
    """确认 LibreOffice 已装且所需组件可用，否则报 E_ENV_MISSING。

    ``component`` 取 writer/calc/impress；为 None 时只要求任一组件可用。
    """

    state = detect_libreoffice()
    if not state["available"]:
        raise OfficeError(
            "E_ENV_MISSING",
            "%s 需要 LibreOffice（Tier3）" % purpose,
            detail="soffice / libreoffice not found in PATH",
            remedy="调用 office_env_setup 并指定 tier=3",
        )
    if component is not None:
        if component not in LIBREOFFICE_COMPONENT_LIBS:
            raise OfficeError(
                "E_INPUT_SCHEMA", "未知的 LibreOffice 组件: %s" % component
            )
        if not state["components"].get(component):
            raise OfficeError(
                "E_ENV_MISSING",
                "%s 需要 LibreOffice %s 组件（当前缺失）" % (purpose, component),
                detail=(
                    "program_dir=%s missing=%s"
                    % (state["program_dir"], ", ".join(state["missing_components"]))
                ),
                remedy=(
                    "安装 libreoffice-%s 后重跑 office_env_check；"
                    "不要改用其他引擎或「差不多」的方案" % component
                ),
            )
    return state


def libreoffice_component_for_suffix(suffix: str) -> Optional[str]:
    return LIBREOFFICE_SUFFIX_COMPONENTS.get(suffix.lower())


def require_libreoffice_for_source(source_suffix: str, *, purpose: str) -> Dict[str, Any]:
    """按源文件后缀确认所需的 LibreOffice 组件。"""

    return require_libreoffice(
        purpose=purpose,
        component=libreoffice_component_for_suffix(source_suffix),
    )


def require_poppler_data(*, purpose: str) -> Dict[str, Any]:
    """PDF 转图前确认 poppler 的 Adobe-GB1 CMap 数据包存在。

    缺失时 pdftoppm 退出码为 0 但产出的图缺字甚至全空白，不能靠退出码判断。
    """

    state = detect_poppler_data()
    if not state["available"]:
        raise OfficeError(
            "E_ENV_MISSING",
            "%s 需要 poppler-data（Adobe-GB1 CMap），否则中文 PDF 会渲染成空白图" % purpose,
            detail=state["detail"],
            remedy=(
                "调用 office_env_setup 并指定 tier=2（安装 poppler-data）；"
                "安装后重跑 office_env_check 确认 poppler-data 可用"
            ),
        )
    return state


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


def select_system_cjk_font(fonts: Dict[str, Any], requested: Optional[str] = None) -> str:
    families = fonts.get('system_font_families') or []
    if not families:
        raise OfficeError('E_ENV_MISSING', 'XeLaTeX 未发现已注册的真实中文字体族',
                          detail=fonts.get('fontconfig_detail') or 'fc-list returned no usable Chinese font family',
                          remedy='安装 fontconfig 与中文字体并执行 fc-cache -f，再运行 office_env_check；STSong-Light 是 ReportLab CID 字体，不能用作 CJKmainfont。')
    if requested and requested not in families:
        raise OfficeError('E_INPUT_SCHEMA', 'cjk_font 不是已确认的系统中文字体族：%s' % requested,
                          detail='available=%s' % families,
                          remedy='从 office_env_check 的 fonts.system_font_families 选择真实族名，不使用字体文件名。')
    return requested or families[0]


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
        # 与探测/执行使用同一解释器；venv 禁止 --user，否则安装成功也不可导入。
        user_flag = " --user" if sys.prefix == sys.base_prefix else ""
        commands.append(shlex.quote(sys.executable) + " -m pip install" + user_flag + " " + " ".join(python_packages))
    apt_packages: List[str] = []
    if "pandoc" in selected:
        apt_packages.append("pandoc")
    if "poppler-utils" in selected:
        apt_packages.append("poppler-utils")
    if "poppler-data" in selected:
        # 缺它会让中文 PDF 预览静默变成空白图，必须与 poppler-utils 一起装。
        apt_packages.append("poppler-data")
    if "libreoffice" in selected:
        apt_packages.append("libreoffice-core libreoffice-writer libreoffice-calc libreoffice-impress")
    if "tesseract-ocr" in selected:
        apt_packages.append("tesseract-ocr tesseract-ocr-chi-sim")
    if "texlive-xetex" in selected:
        # Pandoc 的 CJKmainfont 会加载 xeCJK，只有 xelatex 二进制并不足够。
        apt_packages.append("texlive-xetex texlive-lang-chinese texlive-fonts-recommended")
    if "cjk-fonts" in selected:
        apt_packages.append('fontconfig')
        android_fonts = detect_cjk_fonts()["android_fonts_visible"]
        if android_fonts:
            commands.append(
                "mkdir -p ~/.fonts && cp %s ~/.fonts/ && fc-cache -f"
                % " ".join(shlex.quote(path) for path in android_fonts)
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
        "estimated_bytes": (200 * 1024 * 1024 if tier == 4 and 'texlive-xetex' not in selected else TIER_ESTIMATED_BYTES[tier]),
        "disk_free_bytes": state["disk_free_bytes"],
        "fonts": state["fonts"],
        "python": state["python"],
        "note": "安装命令需通过可见 PTY 会话流式执行；TeX 的 mktexlsr/updmap/fmtutil 后处理可能持续数分钟，无新输出不表示死锁。体积为保守估计；失败先检查进程、锁和日志，不自动中断、回滚或重试。",
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
