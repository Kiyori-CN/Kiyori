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

## 代码块、公式与图表专项方案（2026-09-03 增量）

### 现场与根因假设

- 诊断导出的 Provider 原文中 `python`、`javascript`、`bash` 和无语言代码块围栏成对且顺序正确；
  目标设备截图却出现首个围栏泄漏到正文、第二个围栏进入黑色代码卡、后续标题/HTML/列表被代码卡吞并，
  因此优先判定为本地块拆分/节点投影边界错误，而非模型输出损坏。
- native 与 legacy block splitter 都把行内代码插件放在 fenced-code 之后，但 fenced-code 直到换行才进入
  `PROCESSING`；三级反引号后的语言首字符会先让行内代码插件进入 `PROCESSING`，从而把合法 fenced code
  误分类为 `INLINE_CODE`。这是首个 Mermaid/python 块显示为正文的直接冲突路径。
- fenced-code 当前未约束行首、开闭围栏长度、尾随字符和 `CRLF`；节点消费端又以 `trimAll`、`dropWhile`、
  `dropLastWhile` 猜测首尾行，导致语言标签、空白、未闭合 EOF 和代码内反引号无法保持原样。
- `CanvasMarkdownNodeRenderer` 使用 `remember(content)` 与按 index 的节点 key/cache；节点插入或类型变化时，
  内容长度相同即可复用错误的旧节点/组件，能够把正文组件与黑色代码组件互换。
- JLatexMath 后端不识别 `equation` 等 amsmath 环境包装；现有 `DisplayMathBlock` 正确记录失败但会显示原始
  LaTeX，因此需要在既有 `prepareLatexForJLatexMath` 兼容层中去除可验证的环境外壳并保留 `\\tag` 语义。
- Mermaid 预览必须只接收已剥离围栏和语言信息的代码载荷；流式阶段仍显示源码，静态阶段的既有预览按钮和
  单一 WebView 入口继续负责图表渲染，代码内容不得再次进入 Markdown/XML/LaTeX 解析。

### 目标、非目标与边界

目标是让思考段、普通回复、工具结果和错误路径在流式/静态切换时共享同一块级语义：正文永不进入
`CODE_BLOCK`，代码永不泄漏到正文；语言标识只作为代码卡元数据；公式环境、编号、化学式和 Mermaid
源码在不改变原文字节（除明确的后端兼容转换）前提下可预测渲染。

非目标仍为：不增加第二 Markdown AST、第二 XML/LaTeX/Mermaid 渲染器、隐藏回退/降级、协议改名、Provider
重试或工具执行旁路；不把未验证的真机视觉结果写成自动化通过。

### 统一 fenced-code 合同（native 与 legacy 必须字面等价）

1. 仅支持反引号围栏；开头位于行首或最多三个 ASCII 空格之后，连续反引号至少三个。围栏前的正文字符、
   四格以上缩进和行内三反引号不启动代码块。
2. 开头反引号连续运行长度作为 `openingFenceLength`；同一行剩余部分是 info string，禁止把其中的反引号
   当作新的围栏。语言取 info string 的第一个非空白 token（大小写原样保留），其余 info 只留在原始块边界
   中，不进入代码正文。
3. 结束围栏只能出现在新行行首（同样允许最多三个空格），连续反引号长度必须大于等于开头长度，之后只能
   有空格或制表符直到 `LF`/`CRLF`/EOF。代码正文中的反引号、较短运行和带其它字符的伪围栏保持不透明。
4. 解析器必须跨 `push`/字符串分片保留状态；`LF`、`CRLF` 和没有最终换行的 EOF 均有确定结果。未闭合块
   仍是一个 `CODE_BLOCK` 节点，消费端只移除已确认的开头行，不吞掉后续节点或伪造结束围栏。
5. `CODE_BLOCK` 激活后，`$`/`$$`、反斜杠、HTML/XML、Markdown、引号、重定向、Unicode 和 Mermaid 语法
   全部按纯文本传递，不再调用任何其它块/内联插件；思考与回复不设不同规则。

### 节点与渲染消费方案

- 新增单一 fenced payload 提取函数：逐行识别并只剥离一对已验证的开头/结束围栏，保留正文的缩进、空行、
  尾随空格和 CRLF 语义；语言仅取首 token。静态与流式 `CanvasMarkdownNodeRenderer` 共用它，Mermaid/HTML
  预览与复制均消费同一 `code` 字符串。
- 为 `MarkdownNode`/`MarkdownNodeStable` 建立稳定身份并让转换缓存、Compose `key` 和 XML stream 索引按身份
  对账；缓存命中同时校验身份、类型、内容与子树，禁止仅凭 index 或长度复用节点。节点类型/子节点变化必须
  触发结构更新，插入前后原组件状态不能错位。
- 代码卡不再通过 `trimAll` 猜测边界；黑色背景、行号、高亮、Mermaid/HTML 按 `CODE_BLOCK` 节点和提取后的
  language/code 唯一触发。代码载荷内部不执行 Markdown、LaTeX、XML 或链接高亮。
- 公式兼容层仅对有测试证据的 `equation`/`equation*`/`displaymath`/`align` 外壳做结构化去壳，保留主体和
  顶层 `\\tag`；未知环境继续记录可观察失败，不吞错。现有 JLatexMath、化学式转换、横向滚动和编号布局不
  改变所有权。

### 回归矩阵与验收

- native AndroidTest 与 legacy stream AndroidTest：行首/缩进/行内围栏、3/4/5 反引号、尾随空格、伪围栏、
  CRLF、EOF 未闭合、分片切在任意字符、代码内 `$`/`$$`/`\\[`/XML/HTML/Markdown、连续多个代码块、思考/工具
  内容和 Mermaid 多语言。
- JVM：fenced payload 语言/正文提取、空白保留、节点稳定身份与等长类型替换、流式→静态一致性、公式环境
  兼容转换、未知环境可观察失败、Mermaid HTML 只接收纯代码且特殊字符安全编码。
- 自动化顺序：定向 JVM → native/AndroidTest 源码编译 → `git diff --check` → formal readiness → 串行
  `:app:assembleDebug` 与 APK 包名/签名/16 KiB 对齐核验；完整 JVM 中既有 FileContextMenu 契约失败单独
  记录，不混入本专项。
- 设备验收：真实流式思考/回复、工具调用及结果、深浅色、旋转/窄屏、Mermaid 预览按钮、公式编号/长公式、
  IME 与 TalkBack。无设备时状态保持 `verification_pending`，最短下一步是连接目标 Android 设备重复矩阵。

### 回滚与审计边界

首次项目编辑前基线为 `main@12ce55b173673c17efe9a09f0ce0d3bfb7b09e42`；预存
`docs/TODO/ai_interrupted_turn_recovery/` 不属于本专项。所有修改限定在现有 Markdown/LaTeX/节点测试和
本 TODO；提交前只暂存本轮允许文件，审计敏感内容、构建产物、子模块和远端 ref，失败时保留可审阅差异并
不得使用 reset/clean/强制推送。

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
5. **代码块/公式/图表专项（实现完成，设备验收待验证）**：native/legacy fenced-code 状态机、严格
   payload 提取、稳定节点身份、公式环境兼容和 Mermaid 纯载荷已实现；定向 JVM/native 编译通过，
   真机视觉与流式交互保持 `verification_pending`。

## 当前实现记录

- native block splitter 已增加协议 XML 非行首识别和块公式行首约束；Kotlin legacy splitter 同步
  了块公式边界，并重写为属性引号感知、任意位置 XML 流解析。
- `CustomXmlRenderer`、`StructuredAssistantContentParser` 与 native XML splitter 共享同一组边界
  规则，避免工具 JSON/命令中的 `$`、引号、`<`、`>`、`&`、反斜杠和 `/>` 影响节点完成态。
- 新增 Android 回归：shell PID `$$` 不开块公式、缩进公式仍可识别、紧贴正文的工具 XML 包含
  JSON 特殊符号时保持两个 XML 节点、属性引号角括号、大小写闭合、非法 tool 后缀和未知 XML-like
  文本保持普通文本；结构化解析器另覆盖属性提取和未闭合正文。
- native/Kotlin 编译、定向 JVM、formal readiness 和 `git diff --check` 已通过；完整 JVM 尚有
  一个既有 `FileContextMenu.kt` 抽屉迁移契约失败；本轮相关包回归通过，设备未连接，最终状态保持
  `verification_pending`。
- 本轮新增 `FencedCodeBlockContent` 统一消费函数，移除代码块 `trimAll`/启发式首尾删除；native 与
  legacy 围栏均要求行首最多三空格、开闭长度匹配、尾随空白约束，并在第三个反引号立即隔离其它插件。
- `MarkdownNode`/`MarkdownNodeStable` 通过稳定 ID 对账转换缓存、节点 key 和分组状态；JLatexMath 兼容层
  只对 `equation`、`equation*`、`displaymath`、`align` 外壳去壳并保留主体/tag；Mermaid 预览保持纯
  code 载荷和原始空白。

## 风险与边界

- 块公式现在遵循块级 Markdown 边界；原先依赖“行内 `$$…$$`”的非标准内容不会再被当作块公式，
  应使用行内 `$…$` 或 `\(…\)`。
- native 与 Kotlin 两条 splitter 实现必须保持同一边界，否则静态复制与流式显示会产生 AST 漂移。
- 本地测试和 APK 不能替代目标设备上的长工具输出、真实 Provider 流式输入、浅深色、TalkBack、
  IME 和旋转验收。
