---
fork: https://github.com/Kiyori-CN/Kiyori
upstream: https://github.com/AAswordman/Operit
status: verification_pending
observed_at: 2026-09-03 Asia/Shanghai
---

# AI 对话渲染可靠性与工具内容边界

## 目标与范围

修复 AI 对话中工具调用/结果被原样拆散、块公式误识别并显示“渲染失败”的问题，覆盖流式与静态
消息、协议 XML、工具 JSON/命令中的 `$`/`$$`/反斜杠/引号/重定向、思考段、错误结果和普通输入框
显示。唯一渲染链仍为 native Markdown splitter → Compose `StreamMarkdownRenderer` →
`CanvasMarkdownNodeRenderer`/`CustomXmlRenderer`；输入框继续由现有 Agent/Classic/Fullscreen
组件持有状态。

非目标：不新增第二套 Markdown/XML/LaTeX 渲染器，不改变工具协议身份、Provider 请求、会话状态
所有权或工具执行语义，不用静默吞错或任意回退掩盖解析失败。

## 现场证据（2026-09-03）

- AI 诊断导出中的工具标签形如 `<tool_result_XXXX>…<content>{"command":"… $$ …"}</content>…`；
  截图显示同一段内容被拆成原始标签和红色“渲染失败”块。
- `DisplayMathBlock` 是该红色标记的唯一界面来源；块级 LaTeX 插件此前不检查行边界，普通说明
  中的 `kill -9 $$` 会跨越后续工具 XML 直到下一个 `$$` 才结束。
- native XML 插件此前只在行首或标点后启动；协议标签紧贴正文时会漏识别，后续特殊符号再次被
  Markdown 插件抢占。

## 分阶段方案与验收

1. **解析边界（DONE）**：块 `$$`/`\[` 仅从行首（含缩进）开启；已知聊天协议 XML 标签可在
   正文任意位置开始，未知 XML-like 文本仍保持普通文本。native splitter、legacy XML 插件和
   `splitByXml` 的边界已对齐，闭合标签按大小写不敏感匹配。
2. **节点与组件（DONE）**：流式→静态的未闭合判定、属性引号中的 `>`、嵌套 `<content>`/参数、
   工具 JSON/命令中的同名闭合片段和 `/>` 均已在 `CustomXmlRenderer` 与
   `StructuredAssistantContentParser` 收敛；新增 Android 结构化解析回归覆盖成功、大小写和未闭合路径。
3. **输入显示（DONE）**：Agent/Fullscreen 输入框在工具处理中禁用编辑时保持草稿、占位符和尾部动作
   可读；Classic 输入框沿用既有显式颜色合同。窄屏、大字体、IME、旋转和 TalkBack 仍需真机确认。
4. **验证与交付（进行中）**：native/Kotlin 编译、定向 JVM、formal readiness 和差异检查通过；
   完整 JVM 回归发现一个基线外的 `FileContextMenu.kt` 抽屉迁移契约失败，未改动该既有专项；Debug
   APK、最终审计、提交和推送待收口。

## 当前实现记录

- native block splitter 已增加协议 XML 非行首识别和块公式行首约束；Kotlin legacy splitter 同步
  了块公式边界，并重写为属性引号感知、任意位置 XML 流解析。
- `CustomXmlRenderer`、`StructuredAssistantContentParser` 与 native XML splitter 共享同一组边界
  规则，避免工具 JSON/命令中的 `$`、引号、`<`、`>`、`&`、反斜杠和 `/>` 影响节点完成态。
- 新增 Android 回归：shell PID `$$` 不开块公式、缩进公式仍可识别、紧贴正文的工具 XML 包含
  JSON 特殊符号时保持两个 XML 节点、属性引号角括号、大小写闭合、非法 tool 后缀和未知 XML-like
  文本保持普通文本；结构化解析器另覆盖属性提取和未闭合正文。
- native/Kotlin 编译、定向 JVM、formal readiness 和 `git diff --check` 已通过；完整 JVM 尚有
  一个既有 `FileContextMenu.kt` 抽屉迁移契约失败，设备未连接，最终状态保持 `verification_pending`。

## 风险与边界

- 块公式现在遵循块级 Markdown 边界；原先依赖“行内 `$$…$$`”的非标准内容不会再被当作块公式，
  应使用行内 `$…$` 或 `\(…\)`。
- native 与 Kotlin 两条 splitter 实现必须保持同一边界，否则静态复制与流式显示会产生 AST 漂移。
- 本地测试和 APK 不能替代目标设备上的长工具输出、真实 Provider 流式输入、浅深色、TalkBack、
  IME 和旋转验收。
