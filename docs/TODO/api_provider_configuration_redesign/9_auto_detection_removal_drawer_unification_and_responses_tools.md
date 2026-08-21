# 自动识别删除、统一底部抽屉与 Responses 工具调用修复

## 目标、范围与状态

状态：`IMPLEMENTATION COMPLETE / DEVICE VERIFICATION PENDING`。

本文件承接已经交付的供应商与协议重构，以及
[`8_followup_interaction_and_reasoning_design.md`](8_followup_interaction_and_reasoning_design.md)
中的后续实现。用户在 2026-08-21 明确改变上一轮决定：不再保留“API 协议自动识别”，而是
彻底删除整套可见入口和仅为该入口服务的实现；同时要求所有底部“下拉抽屉”统一为浏览器
书签、历史和下载使用的抽屉交互，并修复 DeepSeek 及其他 Responses 供应商的工具结果关联。

本轮交付范围：

- 删除 API 协议自动识别的 UI、Compose 状态、业务类型、检测算法、专用 catalog 元数据、
  七份语言资源、测试和当前文档语义；
- 保留显式 `ApiProtocol` 选择、供应商默认协议、协议序列化值、旧配置读取、端点补全和
  模型列表路由；
- 新增全应用统一的模态底部抽屉宿主，复用浏览器现有
  `KiyoriDraggableBottomDrawer` 的三态、弹簧动画、手柄拖拽、遮罩、圆角和安全区合同；
- 迁移当前仓库全部 12 处 Material `ModalBottomSheet`；
- 修复共享 Responses 输入编译器中 `function_call` 与匹配
  `function_call_output` 被普通消息拆开的顺序错误；
- 用本地请求体与路由测试覆盖全部声明 `OPENAI_RESPONSES` 的供应商；
- 更新 `CONTEXT.md`、本专项 TODO、合同测试和 Debug APK 证据，最后精确提交并推送
  `main`。

非目标：

- 不新增 `ApiProtocol.AUTO`，不发送协议探测请求，不根据请求失败切换协议；
- 不为 DeepSeek 增加供应商特判，不改变工具执行器或伪造缺失工具结果；
- 不新增第二份模型配置、抽屉或工具调用状态 owner；
- 不把锚定在按钮旁的 `DropdownMenu`、`ExposedDropdownMenu` 或确认型 `AlertDialog`
  改成底部抽屉；
- 不操作设备、不安装 APK、不执行真实供应商请求、不做 Release 构建、依赖升级或无关重构。

## 当前事实与根因

### 自动识别是一整套旧方案

当前自动识别不只是一张卡片。它还包括：

- `ProviderProtocolDetectionSource`；
- `ProviderProtocolDetectionResult`；
- `ApiProviderConfigs.detectProtocol()`；
- endpoint 规范化、协议路径推导和 base endpoint 计算；
- `ProviderProtocolConfig.knownBaseEndpoints` 及其 catalog 参数；
- `ModelApiSettingsSection` 中的 `protocolDetectionMessage`、检测回调和结果通知；
- 七份语言资源中的 `api_protocol_auto_detect*` 与
  `api_protocol_detection_source_*`；
- `ApiProviderConfigProtocolTest` 和
  `ModelApiSettingsSelectionSheetContractTest` 中的检测合同；
- `CONTEXT.md` 和本专项文档中把自动识别描述为当前能力的语义。

Kiyori 尚未公开发行，因此用户可见旧方案可以彻底移除，不保留隐藏开关或并行入口。但
`ApiProtocol` 的四个序列化值、旧 `ApiProviderType` 兼容标识和旧配置读取仍是数据合同，
不得随 UI 删除。

### Responses 工具调用错误是共享排序问题

现场错误：

```text
No tool output found for tool call call_00_ZhEF5zkZ7sVTlidjA7e31224.
```

事件证据：

- `eventId=bc2b19f7-5a79-488d-b27d-6929dd99632d`
- `eventSha256=abf0bd292cad3823dbc020db2560279f3d6fd4048782a0db28c88b988f7bcfe9`
- 堆栈落在 `OpenAIProvider.kt:3612-3634` 的 Responses 请求提交与 HTTP 错误处理。

当前 `OpenAIResponsesPayloadAdapter.convertMessagesToResponsesInput()` 对 assistant 消息的
输出顺序是：

1. reasoning item；
2. `function_call`；
3. assistant 普通 `message`；
4. 后续 tool role 才输出 `function_call_output`。

当助手在调用工具前还输出普通文本时，请求会形成：

```text
function_call(call_id=A)
message
function_call_output(call_id=A)
```

DeepSeek Responses 要求匹配的工具输出紧跟对应调用，因此即使输出存在，也会把上述输入判定
为“没有找到工具输出”。所有声明 `ApiProtocol.OPENAI_RESPONSES` 的供应商都经过同一个
`OpenAIResponsesProvider` 和 payload adapter，所以修复必须位于共享协议层。

### 底部抽屉目前有两个实现体系

浏览器书签、历史和下载使用 `KiyoriDraggableBottomDrawer`：

- `HIDDEN / PARTIAL / EXPANDED` 三态；
- 无回弹的 spring 动画；
- 只由顶部手柄接管纵向拖动；
- 遮罩点击关闭；
- `26.dp` 顶部圆角；
- `0.dp` tonal/shadow elevation；
- 内容 viewport 跟随真实可见高度；
- navigation bars 和 IME 安全区。

仓库其余位置仍有 12 处 Material `ModalBottomSheet`。它们各自维护 sheet state、圆角、
scrim、drag handle 和高度，已经产生视觉、手势和关闭动画分叉。

## 目标合同

### Responses 输入编译

转换器必须满足：

```text
前置 reasoning/message
function_call(call_id=A)
function_call_output(call_id=A)
function_call(call_id=B)
function_call_output(call_id=B)
后续无关 input item
```

具体规则：

1. assistant 普通文本位于该 assistant 的工具调用之前；
2. 以原始 `call_id` 为唯一配对身份；
3. tool role 到达后，按原调用顺序把调用和输出相邻写入；
4. 多个并行调用保持稳定顺序；
5. 重复且内容相同的调用或输出只写一次；
6. 相同 `call_id` 的名称、参数或输出冲突继续抛出协议错误；
7. 不为缺失输出创建假结果，不重试其他协议，不改变工具执行次数；
8. 无工具调用的普通 Responses 历史保持原始消息顺序。

### 统一模态抽屉

新增 `ui/components/KiyoriModalBottomDrawer.kt`，职责仅包括：

- 通过全屏 `Dialog` 脱离调用方的 `LazyColumn`、`Card` 或滚动 `Column` 测量树，保证自身
  是独立的模态窗口层；
- 内部复用唯一 `KiyoriDraggableBottomDrawer`；
- Settings 选项抽屉的标题和取消操作保持固定，选项集合在独立的 `LazyColumn` 视口内上下
  滚动，不能因选项数量超过可见高度而裁掉底部选项；
- 从 Hidden 动画进入 Partial；
- 遮罩、手柄下拉和系统 Back 都先进入 Hidden；
- Hidden 完成后才调用外部 `onDismissRequest`；
- partial fraction 与浏览器宽高策略共用一个纯函数；
- 不持有供应商、协议、模型、文件、SQL、历史或网页元素等业务状态；
- 内容继续由原调用方持有唯一滚动 owner 和选择状态。

`KiyoriDraggableBottomDrawer` 继续是唯一几何和手势实现。不得新增另一套 swipeable、sheet
state 或延迟展开逻辑。

### 自动识别删除

删除后：

- 协议抽屉只展示当前供应商实际支持的协议；
- 用户点击协议后显式写入并持久化；
- 供应商切换仍使用 catalog 的明确默认协议；
- 不显示“自动识别”、识别依据或无法唯一识别消息；
- `rg` 对专用类型、函数和资源键结果为 0；
- catalog 不保留只为检测服务的 `knownBaseEndpoints`。

## 12 处迁移矩阵

| 序号 | 当前文件 / 面板 | 业务状态 owner | 迁移要求 |
| --- | --- | --- | --- |
| 1 | `ChatArea.kt` / 消息复制预览 | `copyPreviewText` 与预览模式 | 保留正文滚动和复制行为，统一外层抽屉 |
| 2 | `ModelNameTagEditor.kt` / 模型排序 | `orderedModels` | 保留拖拽排序和确认提交 |
| 3 | `UpstreamModelPickerSheet.kt` / 从上游获取 | 模型集合、搜索、选择差异 | 保留唯一列表滚动和固定操作区 |
| 4 | `UserPreferencesSettingsScreen.kt` / 旧偏好归档 | `archiveSheetMarkdown` | 保留只读滚动、复制和 Snackbar |
| 5 | `ModelApiSettingsSection.kt` / API 协议 | 当前草稿协议 | 删除自动识别，仅保留显式选项 |
| 6 | `ModelApiSettingsSection.kt` / API 提供商 | 当前草稿供应商与搜索 | 保留分组、搜索、当前选中态 |
| 7 | `FileContextMenu.kt` / 文件操作 | 文件管理页面当前选中项 | 保留动作和后续确认 Dialog |
| 8 | `SqlViewerScreen.kt` / SQL 控制面板 | SQL 文本、分页和 ViewModel | 保留运行、清空、分页和错误信息 |
| 9 | `WebSessionHistorySheet.kt` / 删除时间范围 | `showDeleteRangeSheet` | 保留历史语义色和范围选择 |
| 10 | `WebSessionWebElementOverlays.kt` / 网页元素动作 | action state 与计划 | 保留动作分组和网页操作回调 |
| 11 | `WebSessionWebElementOverlays.kt` / 广告标记跳转策略 | 当前 policy | 保留选择语义和显式取消 |
| 12 | `KiyoriSettingsUi.kt` / Settings 通用选择器 | 页面持有的 selection | 覆盖浏览器、下载、播放器等设置子页 |

验收时：

```text
rg "ModalBottomSheet\(" app/src/main/java
```

必须为 0。锚定菜单和确认 Dialog 不在该计数中。

## 实施顺序与回滚点

### M10 方案与协议测试

1. 写入本文件并更新专项入口；
2. 给 Responses adapter 增加当前失败顺序的回归测试；
3. 修复共享输入排序；
4. 增加并行调用、重复去重、冲突和供应商路由矩阵测试。

回滚点：只涉及共享 adapter 与测试，不改变 UI。

### M11 自动识别删除

1. 删除检测模型、算法和专用 catalog 字段；
2. 删除 Compose 状态、回调、卡片和通知；
3. 删除七份语言资源；
4. 更新 catalog 与选择器合同测试；
5. 更新 `CONTEXT.md` 和本专项当前语义。

回滚点：显式协议选择、默认协议和旧配置测试必须继续通过。

### M12 统一抽屉宿主

1. 增加 `KiyoriModalBottomDrawer`；
2. 把浏览器 partial fraction 规则提升为共享纯函数；
3. 增加生命周期、布局和静态合同测试；
4. 先迁移 API 提供商、API 协议、从上游获取和 Settings 通用选择器；
5. 再迁移其余 8 处；
6. 反向检查 Material `ModalBottomSheet` 为 0。

回滚点：每个面板只替换外层宿主，业务内容和回调保持独立。

### M13 验证与交付

1. 运行 Responses、catalog、设置和抽屉定向 JVM 测试；
2. 运行与本轮改动相称的完整 JVM 回归；
3. 检查七份资源 XML、资源键集合、Markdown links、架构边界、
   formal readiness、fresh clone 和 `git diff --check`；
4. 串行执行 `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain`；
5. 核验 APK 路径、时间、大小、SHA-256、包名、版本、Launcher、签名、ABI 和 16 KB 对齐；
6. 审计精确候选树、敏感内容、构建产物、子模块、异常大文件和远端竞争；
7. 提交并推送 `main`，核对 local、tracking 和 remote ref。

当前实现状态：

- M10 Responses 输入排序与共享供应商合同：`DONE`；
- M11 自动识别整套删除：`DONE`；
- M12 统一模态底部抽屉与 12 处迁移：`DONE`；
- M13 文档收口、完整回归、Debug APK、Git 提交推送：`DONE`；
- M14 修复统一抽屉被页面测量树裁剪、以及长选项列表无法滚动的问题：`DONE`；
  真机手势与视觉验收仍待执行。

## 自动化验收矩阵

| 验收项 | 本地证据 | 现场边界 |
| --- | --- | --- |
| 单调用文本 + 工具结果相邻 | adapter 请求体测试 | DeepSeek 真实请求待验 |
| 并行工具按原顺序成对 | adapter 请求体测试 | 各供应商真实并行调用待验 |
| 重复调用/输出去重 | adapter 测试 | 无 |
| 调用或输出冲突显式失败 | adapter 测试 | 无 |
| 所有 Responses 供应商进入共享 route | catalog/routing 矩阵 | 真实供应商请求待验 |
| 自动识别整套删除 | `rg`、资源和合同测试 | 无 |
| 显式协议与兼容读取保留 | catalog/迁移测试 | 旧备份现场读取待验 |
| 12 处统一抽屉 | `rg`、编译、静态合同 | 真机手势与视觉待验 |
| Hidden 后再卸载 | 宿主合同测试 | 真机退出动画待验 |
| Back、IME、横屏、大字体 | 编译与布局策略测试 | 真机待验 |
| Debug APK | 构建与产物审计 | 安装运行待验 |

## 风险与停止条件

- 若某个面板依赖 Material sheet 的隐藏回调或特定 nested-scroll 行为，先把该依赖显式建模，
  不在统一宿主中增加按调用方分支。
- 若纯 JVM 环境无法直接执行 `org.json` adapter 测试，抽取不依赖 Android 的排序策略并
  测试其完整输入输出；关键合同不能只留在 `androidTest`。
- 若完整回归发现现有调用依赖“工具调用与输出不相邻”，以 Responses 协议合同为准修正调用
  编译器，不增加供应商开关。
- 若设备行为无法在本轮授权内验证，交付状态保持
  `LOCAL IMPLEMENTATION AND AUTOMATED VALIDATION COMPLETE / DEVICE AND REAL-SERVICE VERIFICATION PENDING`。
