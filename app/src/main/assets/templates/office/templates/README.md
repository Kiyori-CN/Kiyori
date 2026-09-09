# templates/

放 docx / pptx / xlsx 模板。模板派生场景请把模板路径传给
`docx_from_template` / `pptx_template_fill` / `xlsx_write`，并在
`office_validate` 里用 `original_path` 传模板作为基线，避免把继承缺陷报成本次回归。
