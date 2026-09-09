import zipfile
from pathlib import Path
from kiyori_office.protocol import dispatch

def test_docx_outline(tmp_path):
    p=tmp_path/'a.docx'
    xml=b'<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body><w:p><w:r><w:t>A</w:t></w:r><w:r><w:t>B</w:t></w:r></w:p></w:body></w:document>'
    with zipfile.ZipFile(p,'w') as z: z.writestr('word/document.xml',xml)
    assert dispatch('docx_outline', {'path':str(p)})['paragraphs']==['AB']

def test_xlsx_info(tmp_path):
    p=tmp_path/'a.xlsx'; xml='<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheets><sheet name="数据"/></sheets></workbook>'.encode()
    with zipfile.ZipFile(p,'w') as z: z.writestr('xl/workbook.xml',xml)
    assert dispatch('xlsx_info', {'path':str(p)})['sheets']==['数据']
