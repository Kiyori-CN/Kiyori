"""外部引擎的文件发布边界：非零退出、超时和空产物都不能覆盖已有文件。"""
import subprocess
from pathlib import Path

from .paths import atomic_output
from .protocol import OfficeError


def run_output_command(command, output: Path, output_index: int, *, timeout: float,
                       purpose: str, output_is_prefix: bool = False, validate=None):
    with atomic_output(output) as temporary:
        invocation = list(command)
        invocation[output_index] = str(temporary.with_suffix("") if output_is_prefix else temporary)
        try:
            result = subprocess.run(invocation, capture_output=True, text=True, timeout=timeout, check=False)
        except subprocess.TimeoutExpired as exc:
            raise OfficeError("E_TIMEOUT", "%s 超时，未发布当前产物" % purpose,
                              remedy="先核对暂存区与引擎进程状态，再缩小范围或调整超时") from exc
        if result.returncode != 0:
            raise OfficeError("E_ENGINE_FAILED", "%s 失败" % purpose,
                              detail="exit=%s stderr_tail=%s" % (result.returncode, (result.stderr or "")[-2000:]))
        if not temporary.is_file() or temporary.stat().st_size == 0:
            raise OfficeError("E_ENGINE_FAILED", "%s 未产出有效文件" % purpose)
        if validate is not None:
            validate(temporary)
