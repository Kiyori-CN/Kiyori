---
name: kiyori-office-core
description: 办公文档任务总入口，先检查环境并按格式路由。
---
# 办公文档总纲
先调用 `office_env_check`，缺依赖时报告 remedy；已有文件先 outline/read；产物经过 validate 和预览且不原地覆盖。DOCX→kiyori-docx，XLSX→kiyori-xlsx，PPTX→kiyori-pptx，PDF→kiyori-pdf。
