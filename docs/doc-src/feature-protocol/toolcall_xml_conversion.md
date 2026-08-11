# Tool Call 与 XML 内部协议说明（项目现状）

本文档用于明确本项目在 **启用 Tool Call** 与 **不启用 Tool Call** 两种配置下的真实行为。

> 结论先行：
> - 项目内部的工具调用与工具结果，统一以 **XML 形态** 表达与处理。
> - `enableToolCall` 的本质是 **对外协议转换开关**（XML ↔ Provider 原生 Tool Call JSON），不是替换内部执行模型。

---

## 1. 核心概念

### 1.1 内部规范（Canonical Form）

项目内部工具调用/结果的主通道是 XML：

- 工具调用（assistant 输出）：

```xml
<tool name="read_file">
<param name="path">README.md</param>
</tool>
```

- 工具结果（tool 角色写回历史）：

```xml
<tool_result name="read_file" status="success"><content>...</content></tool_result>
```

上层工具执行链（如 `EnhancedAIService` + `ToolExecutionManager`）按这个 XML 规范解析和执行。

### 1.2 Tool Call 开关的含义

`enableToolCall=true` 时，并不是把上层改成 JSON 处理，而是：

1. 请求前把内部 XML 映射成 Provider 的原生 `tool_calls` 格式。
2. 响应后再把 `tool_calls` 映射回内部 XML。

因此上层始终看到 XML，兼容旧逻辑。

---

## 2. 不启用 Tool Call（enableToolCall = false）

### 2.1 请求阶段

- 请求体不注入 `tools` / `tool_choice`。
- 历史消息按普通 `role + content` 发送。
- 系统提示词保留 XML 工具说明与可用工具清单（由 `useToolCallApi=false` 分支控制）。

### 2.2 响应阶段

- 主要处理普通 `content`（及思考内容字段）。
- 不走 `tool_calls` 增量/非增量转换分支。
- 如果模型直接输出 XML 工具标签，上层会正常解析并执行。

---

## 3. 启用 Tool Call（enableToolCall = true）

### 3.1 生效前提

不是“开关一开就必生效”，还要求存在可用工具：

- `availableTools != null`
- `availableTools.isNotEmpty()`

在 `OpenAIProvider` 中通过 `effectiveEnableToolCall` 决定最终是否进入 Tool Call 模式。

### 3.2 请求阶段（XML -> Tool Call JSON）

### A. 注入工具定义

请求体会自动加入：

- `tools`: 由 `ToolPrompt` + 结构化参数构建的 JSON Schema
- `tool_choice`: `"auto"`

工具参数 schema 中的 `required` 固定输出为数组；无必填参数时输出空数组，避免兼容 OpenAI-style 校验器时被识别为 null。

### B. 历史消息转换

在 `buildMessagesAndCountTokens(..., useToolCall=true)` 中：

- assistant 历史中的 XML `<tool ...>` 会被转成 `tool_calls` 数组。
- user/tool 历史中的 XML `<tool_result ...>` 会被转成 `role="tool"` 消息。
- 内部仍保留 tool_call_id 追踪与匹配逻辑。
- 相同 provider 与 `provider_call_id` 的同名同参 XML 只生成一个 `tool_calls` 项；冲突工具名
  或参数直接失败。
- Responses 输入中每个 `call_id` 最多写入一个 `function_call` 和一个内容一致的
  `function_call_output`；同一调用出现不同输出时直接失败。

这一步是“对外兼容”，不是改内部存储结构。

### 3.3 响应阶段（Tool Call JSON -> XML）

### A. 流式

当收到 `delta.tool_calls`（或 Responses API 的 function_call 事件）时：

- 增量参数通过 `StreamingJsonXmlConverter` 逐步转为 XML 参数片段。
- 输出形态依然是 `<tool ...><param ...>...</param></tool>`。
- 工具切换/收尾时自动补齐关闭标签。
- Responses 流使用原始 `response.output` 位置作为投影坐标，并以 `provider_call_id` 绑定
  流事件和 `response.completed` 终态快照。过滤后的工具数组位置和随机 XML 标签不是身份。
- `response.function_call_arguments.done.arguments` 是最终参数快照，在关闭 XML 前进入同一
  解析器；终态快照只补齐尚未交付的参数。
- 调用在开始标签保留原始 `provider_call_id` 与 `provider_name`，供当前回合规范化和下一 hop
  重放。只有取得真实远端 response ID 时才写入 `provider_response_id`；这些属性不显示为
  工具参数。
- 相同身份同名同参只投影一次；同一位置改变 call ID，或同一 call ID 改变工具名或参数时，
  按协议错误失败。

### B. 非流式

当 `message.tool_calls` 存在时：

- 使用 `convertToolCallsToXml` 转回 XML。
- 上层继续按 XML 路径处理工具调用。

---

## 4. 统一执行链（两种模式共享）

不论是否启用 Tool Call，对上层而言最终一致：

1. 模型输出（或转换后输出）XML 工具调用。
2. `ToolExecutionManager.extractToolInvocations` 解析 XML。
3. 当前回合先按 provider 与原始 call ID 规范化调用。相同身份同名同参只保留第一份，冲突在
   权限检查、Hook 和副作用前失败；没有 `provider_call_id` 的普通 XML 工具保持原行为。
4. 具备完整 provider、真实 response ID 与原始 call ID 的可恢复 Responses 调用再进入
   `tool_invocation_ledger`，同一 `call_id` 只有取得 `PENDING -> RUNNING` 所有权后才能
   执行；`COMPLETED/RUNNING/FAILED` 不会再次执行副作用。没有真实 response ID 的调用只
   保留 call ID，不构造 execution ledger 身份。
5. 工具执行结果被格式化为 `<tool_result ...>` 写回对话历史。
6. 下一轮再按配置决定是否做协议转换，并复用原始 provider `call_id`。

所以“启用 Tool Call”只是 I/O 协议层变化，执行层不变。

---

## 5. 与系统提示词（System Prompt）的关系

`useToolCallApi` 会影响提示词呈现策略：

- `true`：使用更简化的工具说明，通常不再内嵌完整工具列表（因为 `tools` 已在请求体传入）。
- `false`：保留 XML 工具说明和可用工具列表。

这也是为什么某些 Provider 若尚未接入完整转换链路，不能只“放开开关”而不补齐逻辑。

---

## 6. Provider 适配现状（当前代码语义）

- `OpenAIProvider` 及其兼容派生 Provider：已实现较完整的双向转换（XML ↔ Tool Call JSON）。
- `LLAMA_CPP`：
  - **非 ToolCall 模式**：仍是提示词约束的 XML 路径。
  - **ToolCall 模式**：除提示词外，已新增 JNI 原生 grammar 约束（`llama_sampler_init_grammar_lazy_patterns`），输出在 native 采样阶段被约束为 `tool_calls` 结构，再转换回项目内部 XML。
  - 上层执行链仍保持 XML 规范，不改执行器协议。
- `MNN`：当前仍未接入等价的原生 ToolCall 能力。

---

## 7. 一句话总结

本项目的 Tool Call 设计是：

- **内部永远以 XML 为核心协议**；
- **外部按 Provider 能力做 Tool Call 协议转换**；
- **启用 Tool Call = 传输层升级，不是执行层重写**。
