# 协议识别、供应商抽屉与 Codex 推理摘要后续设计

## 目标、范围与状态

状态：`LOCAL IMPLEMENTATION, AUTOMATED VALIDATION, DEBUG APK AND MAIN DELIVERY COMPLETE / DEVICE AND REAL-SERVICE VERIFICATION PENDING`。

本文件是 2026-08-21 供应商/协议基础重构之后的延伸专项设计，承接上一轮已经提交到
`main` 的协议 catalog、运行时路由、模型列表路由和 Responses 执行持久化。它只描述本轮
新增或修复的用户可见行为，不替代 `3_target_data_and_protocol_contract.md`、
`4_ui_information_architecture.md` 和 `5_reasoning_and_codex_tiers.md` 中仍然有效的
数据、兼容和执行合同。

本轮不操作设备、不安装 APK、不执行真实供应商生成请求。仓库权威文档显示 Kiyori 尚未公开
发行，因此当前 UI 允许直接迭代；`ModelConfigData`、旧 `ApiProviderType`、审计键、
Responses execution state、ToolPkg provider ID 和既有 route 标识仍需保持兼容读取与运行时
语义。

## 需求归一化

用户提出的需求拆为以下八个可验收工作项：

1. **API 协议自动识别**
   - 复现并修复点击“自动识别”始终提示无法确定的问题。
   - 自动识别是配置动作，成功后保存明确的 `ApiProtocol`。
   - 不发送探测生成请求，不在请求失败后切换协议，不引入 `ApiProtocol.AUTO`。
   - 识别失败时保留当前选择器和当前协议，并明确说明需要手动选择的原因。

2. **供应商目录**
   - 国内供应商分组显示在国际供应商分组之前。
   - 国际供应商新增 xAI（Grok 系列），使用独立 canonical provider identity。
   - xAI 的端点、模型列表、Chat/Responses 路由和旧配置兼容边界必须完整登记，不能只
     添加一个列表项或静态名称。

3. **海外供应商提示**
   - 选择海外供应商时不再弹出底部通知。
   - “API 协议”下方的页面提示继续保留，但改为解释性提示：它说明位置判断、可能需要
     中转站或其他可用网络通道，并明确这不是配置失败，也不会自动变更协议。
   - 国际供应商判断必须来自展示 catalog 的分组，不再维护一份容易遗漏新供应商的手写名单。

4. **API 提供商与 API 协议选择器**
   - 两个 Dialog 改成底部下拉抽屉。
   - 抽屉要支持搜索、分组、当前选择、高度约束、键盘安全区、Back 和点击外部收起。
   - 抽屉内部的状态 owner 只能是当前选择器，不复制模型配置持久化状态。

5. **上游模型抽屉抖动**
   - 修复“从上游获取”抽屉上拉后反复上下抽搐。
   - 保留可拖拽、可滚动、搜索、批量选择、刷新和底部应用操作。
   - 根因修复必须保持一个 `ModalBottomSheetState` 和一个内容高度约束，不增加延迟、
     多份 sheet state 或基于帧时序的清空补丁。

6. **思考程度文案**
   - 五个用户档位显示为：低、中、高、极高、最高。
   - 显式 wire 映射保持：`低 -> low`、`中 -> medium`、`高 -> high`、`极高 -> xhigh`、
     `最高 -> max` 用于 gpt-5.6 系列。
   - 非 gpt-5.6 模型只改变用户看到的文案，仍按既有普通模型 profile 使用
     `low / low / medium / high / high`。
   - Classic 与 Agent 两套输入栏必须使用同一套档位标签，不再显示数字或“原有四档”这类
     已与当前五档状态不符的说明。

7. **思考模式帮助文案**
   - 优化“思考模式”和“思考程度”的介绍弹窗。
   - 说明“已声明能力的供应商/模型”与“用户界面档位”是两件事。
   - 说明 gpt-5.6 的五档会发送对应 reasoning effort；普通模型的五档可能映射到重复的
     wire 值；不能把 UI 档位当作供应商保证。
   - 不把隐藏的完整内部推理链承诺为可见内容。

8. **gpt-5.6 Codex 风格推理摘要**
   - gpt-5.6 系列在官方和兼容 Responses endpoint 上发送
     `reasoning.summary=auto`；既有 Responses 执行持久化与
     `reasoning.encrypted_content` 重放元数据仍只按精确官方合同启用。
   - 将服务端返回的 reasoning summary 增量事件投影为已有 `<think>` 可见消息片段，
     让用户在生成期间知道模型正在思考。
   - 只展示服务端提供的摘要或状态事件，不生成、猜测或拼接隐藏完整思维链。
   - 摘要显示必须和普通正文、工具调用、Responses sequence、历史重放及复制清理保持
     兼容。

## 当前代码事实与根因

### 自动识别

当前 `ApiProviderConfigs.detectProtocol()` 的顺序是：

1. 单协议供应商直接返回唯一协议；
2. 空端点返回 catalog 的 `defaultProtocol`；
3. 仅匹配完整的 catalog 默认 endpoint 或 endpoint option；
4. 仅匹配路径结尾 `/chat/completions`、`/responses`、`/messages`；
5. 其余情况要求手动选择。

这套边界本身没有运行时协议切换风险，但用户填写或历史配置常见的是供应商 base URL，例如
`https://api.example.com/v1`，而不是完整请求路径。当前 catalog 没有把“可唯一对应的
已知 base endpoint”登记为可识别证据，因此多协议供应商会直接进入第 5 步。

修复方向：

- 为协议 catalog 增加结构化、可审计的已知 endpoint/base endpoint 证据，而不是用字符串
  猜测或发网络探测；
- 只有一个协议能匹配时才返回 `Resolved`；
- 多个协议共享同一个 base endpoint 时仍返回 `RequiresManualSelection`，不能把当前选择
  伪装成识别结果；
- 识别结果需要携带可展示的 source/reason，成功和失败文案都说明命中了什么证据；
- 识别输入统一去除控制性尾部标记和尾部斜杠，并保留 query/fragment 的协议语义；不把任意
  host 名称或模型名当成协议证据。

### 海外提示

当前 `ModelApiSettingsSection` 在 `LaunchedEffect(selectedProviderTypeId, selectedApiProtocol)`
中按手写 provider 名单调用 `showNotification()`，同时在 API 卡中显示同一文本的
`SettingsInfoBanner`。这会导致切换供应商时出现底部通知和页面提示两份信息，并且新增国际
供应商时容易漏进手写名单。

修复方向：

- 删除该 Effect 中的 `showNotification()` 调用；
- 页面 banner 的可见性由 `ModelApiProviderPresentationPolicy.section()` 是否为
  `INTERNATIONAL` 决定；
- banner 文案说明网络可达性和中转站场景，不暗示应用已经判断代理是否开启；
- 不为本提示新增网络探测、代理配置 owner 或自动网络切换。

### 两个配置选择器

当前供应商和协议均使用自定义 `Dialog`，列表、搜索和选择逻辑与 Kiyori Settings 的底部
抽屉体系不一致。协议识别失败还依赖抽屉关闭前的通知反馈，导致用户难以理解当前选择是否
已经改变。

修复方向：

- 供应商使用独立 `ApiProviderSelectionSheet`，协议使用独立
  `ApiProtocolSelectionSheet`，两者均以 `ModalBottomSheet` 为唯一窗口；
- 选择成功先更新当前 Compose 草稿，再关闭抽屉；识别失败不关闭、不修改协议；
- 保留供应商搜索和分组标题；协议抽屉增加当前 endpoint 摘要、识别说明和选中状态；
- 抽屉内容使用稳定的最大高度与 `imePadding()/navigationBarsPadding()`，避免内容变化
  反向影响 sheet anchor。

### 上游模型抽屉

`UpstreamModelPickerSheet` 已有 `skipPartiallyExpanded=true`，但内容使用
`fillMaxHeight(0.92f)`，并将动态列表、底部操作栏和 `LazyColumn.weight(1f)` 组合在可变
测量约束中。上拉过程中 sheet anchor 与内部可滚动内容的测量边界可能重复参与拖拽，表现为
抽屉上下震荡。

修复方向：

- 使用稳定的 `heightIn(max = ...)`/`fillMaxWidth()` 内容约束，不在 sheet 内容根部使用按
  父约束比例变化的 `fillMaxHeight()`；
- 把列表区限定为唯一可滚动区域，底部操作栏固定在列表之外；
- 保留唯一 `rememberModalBottomSheetState(skipPartiallyExpanded = true)`；
- 将选择同步 Effect 限定为输入集合真正变化时执行，并避免在拖拽期间重建集合；
- 增加静态契约与状态计算测试，确认不引入第二 sheet state、`Dialog` 或高度抖动补丁。

### gpt-5.6 推理摘要

当前实现已经具备以下基础：

- `ModelCapabilityResolver` 对 `gpt-5.6*` 进入五档 profile；
- 官方 Responses profile 编译 `reasoning.summary=auto`；
- 官方 Responses profile 编译 `include=reasoning.encrypted_content`；
- `OpenAIProvider.processResponsesStreamingEvent()` 已处理若干 reasoning event 名称，
  并调用 `emitThinkContent()` 或 `processContentDelta()`；
- `CustomXmlRenderer` 与两种消息样式已经能够渲染 `<think>` 内容。

当前缺少的是“实际事件集合和摘要内容字段”的完整回归闭环。需要重点核对：

- summary 增量事件、summary part 完成事件和 output item 完成快照是否被统一去重；
- 摘要是否在正文开始后仍可安全投影为已存在的 thinking block；
- `response.completed` 的完整 output 是否会重复追加已发送摘要；
- 没有摘要文本但已经观察到 reasoning event 时，UI 是否至少有一个真实事件驱动的“正在思考”
  状态，而不是静默等待；
- Responses resume 时 replay event 是否再次向用户重复投影；
- 复制、历史请求重放和 metadata tag 是否仍然只保留协议所需的 encrypted reasoning
  元数据，不把内部标记泄漏到用户复制内容。

实现边界：

- 使用一个 reasoning projection accumulator，以协议 event/sequence 为身份去重；
- 增量文本进入已有 `StreamEmitter`，通过 `<think>` 渲染；
- 摘要只有在服务端事件或终态快照提供时才进入正文流；
- 真实 reasoning event 没有文本时，可显示一个由事件触发的短状态标题，但不能伪造摘要正文；
- 不能改变官方 Responses 的 POST 次数、sequence resume、execution repository 或
  at-most-once 合同。

## 方案取舍

### 自动识别：保留并修复

删除选项可以消除错误提示，但会丢失“已知供应商/明确 endpoint 一键确认协议”的价值，也
会使用户在多个协议之间重复手选。保留并修复能把确定性 catalog 证据、协议路径和默认协议
继续集中在同一 owner 中，同时对真正歧义的 base URL 保持诚实的手动选择。

不采用运行时失败切换。协议请求失败可能已经被上游接收、计费或产生工具副作用，不能以失败
类型猜测协议。

### 底部抽屉：复用 Material 组件，不新建通用导航层

新建自定义拖拽容器会复制 sheet 状态和手势所有权；复用项目已有 `ModalBottomSheet` 与
Kiyori Settings 的圆角、scrim、navigation bar、Back 合同，改动范围小且可用静态检查覆盖。

### 推理摘要：服务端摘要投影，不生成“伪思考”

Codex 风格的用户体验目标是让用户看到可解释的推理摘要，而不是暴露模型内部完整思维
链。服务端摘要事件可以被审计、重放和去重；本地生成的“正在思考……”只能作为真实
reasoning event 的状态提示，不能替代摘要内容。

## 2026-08-21 官方协议复核结论

- xAI 官方当前列出 `grok-4.6`，并提供 OpenAI-compatible Chat、Responses 与 models
  端点；因此使用独立 `XAI` identity、Chat/Responses 两协议和 `grok-4.6` 默认模型。
- Anthropic 官方 OpenAI SDK 兼容层、Google OpenAI compatibility、DeepSeek
  Chat/Responses/Anthropic、OpenRouter Responses、小米 MiMo 三协议、硅基流动
  Anthropic Messages、火山方舟 Responses、LM Studio 与 Ollama 的兼容协议均有正式资料
  支撑，现有 catalog 不删除这些选项。
- 阿里云 Anthropic Messages 需要带 WorkspaceId 的独立 endpoint，智谱 Responses 属于
  独立 Coding Plan endpoint；它们不加入当前通用供应商 row，避免把不同凭据/地址合同伪装
  成一键默认协议。
- 自动识别继续只使用静态 catalog 和 endpoint 结构证据，不调用上述服务做生成探测。

复核入口：

- [OpenAI reasoning](https://developers.openai.com/api/docs/guides/reasoning)
- [xAI models](https://docs.x.ai/developers/models)
- [xAI API reference](https://docs.x.ai/developers/api-reference)
- [Anthropic OpenAI SDK compatibility](https://docs.anthropic.com/en/api/openai-sdk)
- [Google OpenAI compatibility](https://ai.google.dev/gemini-api/docs/openai)
- [DeepSeek Anthropic API](https://api-docs.deepseek.com/guides/anthropic_api)
- [OpenRouter Responses API](https://openrouter.ai/docs/api/reference/responses/overview)
- [小米 MiMo OpenAI Responses API](https://platform.xiaomimimo.com/docs/en-US/api/chat/openai-response-api)
- [硅基流动 Anthropic-compatible API](https://docs.siliconflow.cn/en/userguide/guides/anthropic-compatible-api)
- [火山方舟 Responses API](https://www.volcengine.com/docs/82379/1857548)
- [LM Studio OpenAI-compatible endpoints](https://lmstudio.ai/docs/developer/openai-compat)
- [Ollama OpenAI compatibility](https://docs.ollama.com/api/openai-compatibility)
- [Ollama Anthropic compatibility](https://docs.ollama.com/api/anthropic-compatibility)

## 实施里程碑

1. **M5 识别与目录（DONE）**
   - 增加 endpoint/base endpoint 识别证据与 detection reason；
   - 修复自动识别成功/失败状态；
   - 新增 xAI/Grok provider、catalog、模型列表和服务路由；
   - 国内分组移动到国际分组之前；
   - 增加 catalog、识别、xAI 路由和模型列表测试。

2. **M6 选择器与海外提示（DONE）**
   - 供应商 Dialog 改为底部抽屉；
   - 协议 Dialog 改为底部抽屉；
   - 删除海外供应商切换的底部通知；
   - banner 由国际分组统一控制并更新文案；
   - 增加抽屉静态合同、搜索/分组和 banner policy 测试。

3. **M7 模型抽屉稳定性（DONE）**
   - 修复 `UpstreamModelPickerSheet` 的根测量/滚动约束；
   - 保留列表选择、刷新、搜索、绑定影响确认；
   - 增加高度约束和唯一状态 owner 的静态/状态测试。

4. **M8 思考文案与推理摘要（DONE）**
   - 两套聊天输入栏显示五个中文档位标签；
   - 同步七份语言资源；
   - 更新思考模式与思考程度帮助说明；
   - 完善 Responses reasoning summary 事件覆盖、去重和状态投影；
   - 增加 request compiler、stream event、摘要投影和 metadata replay 测试。

5. **M9 回归与交付（DONE）**
   - 执行改动相关的 JVM 测试与 Kotlin 编译；
   - 执行 `git diff --check`、formal readiness 和 fresh clone；
   - 按项目规则串行执行 Debug APK 构建并核验身份、签名、对齐、ABI、native/runtime
     关键产物；
   - 审计精确候选树、敏感内容、构建产物、子模块和远端竞争；
   - 提交并推送 `main`，核对 local/tracking/remote refs；
   - 设备和真实供应商服务不在本轮操作范围，最终若未实测保持
     `verification_pending`。

## 验收矩阵

| 验收项 | 自动化证据 | 设备/真实服务边界 |
| --- | --- | --- |
| 单协议供应商自动识别 | detection source/unit test | 无 |
| 已知完整 endpoint 识别 | catalog/unit test | 无 |
| 已知 base endpoint 的唯一识别 | base evidence/unit test | 无 |
| 共享 base endpoint 的歧义 | manual-selection/unit test | 无 |
| xAI canonical row、排序、协议、模型列表 | catalog/presentation/routing tests | 真实 xAI 请求不执行 |
| 国内分组位于国际分组前 | presentation test | 真机视觉待验收 |
| 海外切换不发底部通知 | static contract test | 真机提示位置待验收 |
| API provider/protocol bottom sheet | source contract/compile | 真机拖拽、Back、IME 待验收 |
| 上游模型抽屉不改变 owner/高度 | source/state contract/compile | 真机快速上拉待验收 |
| 五档中文标签 | resource/source contract | 真机字体和窄屏待验收 |
| 普通模型 wire 映射未改变 | compiler tests | 真实供应商请求不执行 |
| gpt-5.6 五档 wire 映射 | compiler tests | 真实 OpenAI 请求不执行 |
| Responses 摘要事件可见 | event/projection tests | 真机真实流式事件待验收 |
| 摘要重放不重复 | replay/dedup tests | 真实断线续接不执行 |

## 停止条件与风险

- 若新增 xAI 需要新的认证头、请求格式或独立模型列表 parser，先扩展 catalog/adapter
  合同，不把请求强行塞进错误的 provider owner。
- 若 OpenAI Responses 实际事件不能由现有协议解析器表达，先增加事件模型与测试，再调整
  UI；不得用固定文字冒充服务端摘要。
- 若 bottom sheet 的实际抖动只能在设备上确认，自动化可交付“测量约束根因修复 + 待设备
  验证”，不能声称已完成现场验收。
- 不修改 Responses persistence、provider request audit、ToolPkg provider ID、模型绑定、
  旧 JSON 读取和外部接口 route。
