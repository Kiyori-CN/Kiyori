"""格式读取器：把各类文档归一化成文本或结构 JSON。"""

from .docx_reader import docx_outline, docx_read_text
from .pdf_reader import pdf_extract_text, pdf_info
from .pptx_reader import pptx_outline, pptx_read_text
from .tabular import read_tabular
from .xlsx_reader import xlsx_info, xlsx_read

__all__ = [
    "docx_outline",
    "docx_read_text",
    "pdf_extract_text",
    "pdf_info",
    "pptx_outline",
    "pptx_read_text",
    "read_tabular",
    "xlsx_info",
    "xlsx_read",
]
