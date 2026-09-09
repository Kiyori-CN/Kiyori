"""kiyori_office：可脱离 Kiyori 独立运行的办公文档运行时。"""

from .protocol import BEGIN, END, OfficeError, main, parse_sentinel, run

__all__ = ["BEGIN", "END", "OfficeError", "main", "parse_sentinel", "run"]
