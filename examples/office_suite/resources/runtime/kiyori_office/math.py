"""安全解析可编辑 Office Math；不把 LaTeX 文本假装成公式。"""

from .protocol import OfficeError

MATH_NS = "http://schemas.openxmlformats.org/officeDocument/2006/math"


def parse_omml(value):
    from lxml import etree
    if not isinstance(value, str) or len(value) > 100000 or "<!" in value:
        raise OfficeError("E_INPUT_SCHEMA", "omml 必须是不含 DTD/实体的 Office Math XML，最多 100000 字符")
    try:
        root = etree.fromstring(value.encode("utf-8"), parser=etree.XMLParser(resolve_entities=False, no_network=True))
    except etree.XMLSyntaxError as exc:
        raise OfficeError("E_INPUT_SCHEMA", "OMML XML 无效", detail=str(exc)) from exc
    if root.tag != "{%s}oMath" % MATH_NS:
        raise OfficeError("E_INPUT_SCHEMA", "omml 根元素必须为 m:oMath")
    allowed_ns = {MATH_NS, "http://schemas.openxmlformats.org/wordprocessingml/2006/main"}
    for node in root.iter():
        if not isinstance(node.tag, str) or etree.QName(node).namespace not in allowed_ns:
            raise OfficeError("E_INPUT_SCHEMA", "OMML 仅接受数学与文字格式节点")
        if etree.QName(node).namespace != MATH_NS and etree.QName(node).localname not in {
            "rPr", "rFonts", "b", "i", "color", "sz", "szCs", "lang", "bCs", "iCs"}:
            raise OfficeError("E_INPUT_SCHEMA", "OMML 不接受嵌入对象、外部关系或文档结构")
        if any("relationships" in key for key in node.attrib):
            raise OfficeError("E_INPUT_SCHEMA", "OMML 不接受关系引用")
    return root
