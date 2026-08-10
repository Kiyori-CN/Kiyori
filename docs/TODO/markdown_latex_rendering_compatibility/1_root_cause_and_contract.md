# 1. 根因与兼容合同

## [DONE] 已确认的后端事实

- 公式主绘制路径是 `JLatexMathDrawable`，依赖版本为 `jlatexmath-android 0.2.0`
- 核心 `TeXFormula.VERSION` 为 `1.0.3`
- `langle`、`rangle`、`mid`、`hbar`、`hat`、`qquad`、`therefore`、`because`
  和指定集合符号在依赖资产中有定义
- `lvert`、`rvert`、`lVert`、`rVert` 没有对应定义
- 解析器对反斜杠后的非字母字符按单字符命令处理；控制空格 `\ ` 与反斜杠紧接
  物理换行都会形成未知命令
- 当前后端已经支持 `\;`、`\mathopen`、`\mathclose`、`\vert` 和 `\Vert`
- 当前后端没有 `ce` 命令或 `mhchem` 扩展

## 实施合同

兼容层只做结构化、局部的输入变换：

- `\lvert` -> `\mathopen{\vert}`
- `\rvert` -> `\mathclose{\vert}`
- `\lVert` -> `\mathopen{\Vert}`
- `\rVert` -> `\mathclose{\Vert}`
- `\ ` -> `\;`
- 单反斜杠加 `CRLF`/`LF` -> `\;` 加保留的物理换行
- `\ce{...}` -> 当前后端可解析的标准 LaTeX 化学表达式

代码段、代码块和非公式 Markdown 文本不进入该兼容层。双反斜杠命令、已支持的
`\langle`/`\rangle` 等内容保持原样。

## 需要保持的现有不变量

- Markdown splitter 继续决定公式边界；渲染层不重新扫描整条消息
- 流式未闭合公式继续保持节点未完成状态，不显示为最终源码
- 代码内的 `$`、`$$`、`\(`、`\)`、`\[`、`\]` 继续是字面文本
- 公式失败仍保留用户原始源码，便于复制和诊断，但界面必须明确标记失败并完整展示内容
