# 思考模式与 gpt-5.6 Codex 五档

## 既有状态

`ApiPreferences` 已是思考开关和五档滑杆的唯一持久化 owner：

- `enable_thinking_mode`
- `thinking_quality_level`
- 质量范围 `1..5`

聊天输入、WebChat bridge 和 provider 请求都读取同一份状态。本轮不新增模型级思考偏好，
不复制设置状态。

## 目标 profile

`ModelCapabilityProfile` 继续负责“模型名 + 协议 + endpoint contract”的能力判断，但把
“质量级别到 wire 值的映射”也写进 profile，避免编译器用一个全局映射覆盖所有模型。

### 普通 OpenAI reasoning profile

适用于 OpenAI Chat/Responses 协议中未命中 `gpt-5.6*` 的可思考模型：

```text
质量 1 -> low
质量 2 -> low
质量 3 -> medium
质量 4 -> high
质量 5 -> high
```

这保持 UI 五档，同时只发 profile 已声明的 `low/medium/high`。关闭时使用 `none`。
不把 `xhigh/max` 发送给没有该合同的模型。

### gpt-5.6* profile

模型名以 `gpt-5.6` 开头时使用：

```text
质量 1 -> low
质量 2 -> medium
质量 3 -> high
质量 4 -> xhigh
质量 5 -> max
```

Responses 请求写入 `reasoning.effort`；Chat Completions 请求写入
`reasoning_effort`。`gpt-5.6* + Responses` 在官方和兼容 endpoint 上都请求
`reasoning.summary=auto`；后台执行、加密 reasoning replay、sequence resume、prompt cache
等额外能力仍只在现有精确官方 endpoint contract 成立时开启。

服务端返回的 `response.reasoning_summary_*`、reasoning output item 和终态 response 快照
进入一个单调 reasoning projection。相同 part 的 delta、text done、part done、item done
和 completed 快照只补齐尚未显示的尾部；多 part 使用稳定分隔，内容分叉直接报告协议错误。
真实 reasoning lifecycle event 在尚无文本时可以开启现有 `<think>` 状态，但 Kiyori 不生成、
推测或显示隐藏的完整思维链。

两套聊天输入栏统一显示“低、中、高、极高、最高”。普通模型仍按既有
`low/low/medium/high/high` 映射，因此只是展示文案变化；xAI Chat 使用同一普通
OpenAI-compatible 能力 owner。帮助文案必须区分 UI 档位、模型声明能力和服务端实际摘要。

## 编译不变量

- reasoning wire format 为 `NONE` 时，不输出 reasoning 参数。
- profile 未声明该 wire 值时，编译必须失败并暴露测试错误，不自行换成另一档。
- 思考关闭与思考开启必须分别有请求体测试。
- 普通 profile 与 `gpt-5.6*` profile 必须分别覆盖 Chat、Responses、官方和兼容 endpoint。
- `gpt-5.6-sol`、`gpt-5.6-terra`、`gpt-5.6-luna` 与带后缀的同系列模型都进入
  `gpt-5.6*` 识别，不再只依赖固定四个字符串。

## 外部合同证据

本设计以 OpenAI 官方 reasoning 文档和当前仓库已有 Responses 编译器为边界。官方文档明确
reasoning effort 可用值取决于具体模型，因此 Kiyori 不把任意模型名称推断为 Codex 五档；
代码仍以本地 profile 和定向请求体测试作为最终证据。
