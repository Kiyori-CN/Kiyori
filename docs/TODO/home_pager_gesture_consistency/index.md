---
document_type: implementation-plan
status: verification_pending
created: 2026-08-18
implementation: local_implemented
device_acceptance: verification_pending
---

# 三页首页横向手势一致性修复

## 文档状态与权威边界

本文定义“负一屏 ← 软件首页 → AI 首页”三页横向手势的根因修复方案。当前状态是：

- 根因调查已完成；
- 目标设备已完成第一轮复现；
- 方案已经冻结；
- 本地应用代码已经按单一路径实现；
- 专项 JVM `85/85` 与 AndroidTest Kotlin/Java 编译已通过；
- 完整 App JVM、AndroidTest Kotlin/Java 编译、架构/正式准备门禁和 Debug APK 静态审计已通过；
- 修复后目标设备验收尚未执行。

本文是当前缺陷、实施阶段和验收边界的唯一专项进度载体。目标产品合同继续由以下正式文档
定义：

- [Kiyori 产品壳与导航架构](../../doc-src/architecture/kiyori_product_shell_and_navigation.md)
- [模态 AI 左抽屉导航决策](../../doc-src/decisions/0004_modal_ai_drawer_navigation.md)
- [Kiyori App Shell 与首页导航](../kiyori_product_shell/1_app_shell_and_home_navigation.md)

历史的 [AI 首页输入接管](../plugin_runtime_storage_and_home_input/4_home_input.md) 只记录
2026-07-23 为解决“动画未结束时点击被抢占”而加入的状态包装。本次目标设备复现已经证明该历史
切片没有实现与原生 `HorizontalPager` 等价的吸附语义，因此其“输入修复完成”结论由本文取代。

原始方案观察基线为 `main@430364e4cc714d8291db8eb08bf8dabe793ece7d`。本次正式实现基线为
`main@b8f5d500c99f00c69f52e276a4b1d0511ec3cbb0`，与 `origin/main` 零分歧，实际解析
Compose Foundation `1.11.4`。工作树同时保留此前 Browser 来源设置 Back owner 修复；本任务
在同一有效交付树上逐项审阅，不覆盖或拆散该修改。

## 本地实现结果

- `KiyoriHomePagerGesture.kt` 建立当前手势会话、纯目标页策略、AI 专用 fling 与 bounded
  spring；原生 `HorizontalPager` 继续使用框架默认路径，AI 覆盖层不再直接复用其默认 fling。
- AI bridge 使用唯一 `PagerState` 跟手移动；释放固定为严格 `> 0.5`、`>= 400dp/s`、
  一次最多一页、LTR/RTL 与边界合同。
- Shell 通过 `shouldAcceptKiyoriHomePagerInput` 统一排除 child、Settings、Browser、AI drawer、
  bookmark/history/download drawer 和非根 AI route；内容 owner 活跃或 page size 未建立时
  同样不启用 AI bridge。
- Full-Screen Web Search child 与设置 overlay 分离判断但共享承载层，搜索 child 打开时页面
  实际可见，底栏隐藏，Back 关闭 child 后恢复软件首页。该页作为独立 Shell child 显式传入
  `systemBackEnabled = true`；Browser 内搜索继续传入 retained Browser 子树的共享 owner。
  `WebSessionBrowserSearchScreen` 不再自行读取隐式 Local，因此每个新增宿主都必须在编译期
  声明 Back 所有权。
- 表格、关闭自动换行的 Canvas 代码、横向公式、Mermaid 和 HTML WebView 接入同一多 owner
  横向手势占用状态；该状态与聊天历史快速滚动 owner 合并后通过既有 `onGestureConsumed`
  边界上报。
- 旧 `currentDrag`、`verticalDrag`、`dragThreshold` 与 AI 默认 fling 接线已删除，不保留
  新旧双路径或运行时开关。

## 问题定义

三页顺序固定为：

```text
MINUS_ONE(0) ← HOME(1) → AI_HOME(2)
```

产品合同要求：

- 三页位移都直接跟随手指；
- 同样距离和速度产生同样的目标页决策；
- 一次释放最多切换一页；
- 新的水平拖动能够中断尚未结束的吸附动画；
- 普通点击、纵向滚动和 AI 内容自己的横向交互不能被首页手势提前消费；
- `SoftwareHomePage` 只在 `PagerState.settledPage` 后同步；
- Android 系统 Back 与内容 Pager 是两套独立输入合同。

目标设备当前表现不符合该合同：

1. 从屏幕中部约 `50%` 起手，AI 首页向右只移动到约 `75%` 后就容易进入软件首页；
2. 软件首页向右到负一屏，以及负一屏向左到软件首页、软件首页向左到 AI 首页，从约
   `50%` 拉到屏幕边缘仍会回到原页；
3. 屏幕中部短促快速手势只有 AI 首页容易立即进入软件首页；
4. AI 首页从物理右边缘向左拖仍会进入软件首页，该方向与内容 Pager 的几何方向相反；
5. AI 消息列表做以纵向为主的斜向手势时没有首页水平位移，而软件首页会显示水平分量。

## 目标

### G1：统一中部触摸语义

AI 首页与真正 `HorizontalPager` 必须在以下输入上得到等价结果：

- 慢拖不足半页；
- 慢拖正好半页；
- 慢拖超过半页；
- 短距离低速释放；
- 短距离高速甩动；
- 拖动中反向；
- 动画未结束时立即反向拖动；
- 动画未结束时普通点击；
- 手势取消和多指中止。

### G2：消除手势历史依赖

AI 首页每次手势必须在按下时建立新的手势会话。目标页计算只能读取：

- 本次按下与抬起的真实位移；
- 本次释放速度；
- 本次起始页与页面尺寸；
- 当前布局方向；
- 当前可滚动边界。

上一轮负一屏或软件首页手势留下的内部 Pager 数据不得影响下一次 AI 首页释放结果。

### G3：保护永久 AI 根

AI 对话组合树继续永久挂载在 App Shell 根部。左右切换、打开抽屉、进入 AI 一级页面和切换
其他根目的地都不能重建正在进行的流式回答、工具调用、附件、输入草稿、对话滚动状态或 AI
Router。

### G4：保持内容手势和可访问性

- 点击在超过水平 touch slop 前不得被首页手势消费；
- AI 消息列表的纵向滚动继续拥有纵向手势；
- 文本选择、代码块、横向列表、快速滚动条、工作区和其他显式横向组件继续拥有自己的手势；
- TalkBack、键盘、旋转、RTL、分屏、折叠屏和不同页面宽度不能因修复失去现有导航能力。

### G5：分离系统 Back

物理边缘返回继续遵守 Shell Back 合同：

- AI 首页 Back 进入软件首页；
- 负一屏 Back 进入软件首页；
- 软件首页 Back 请求退出。

本任务不通过改变 Pager 阈值、扩大边缘拦截区或屏蔽系统返回来处理 Android Back。

## 非目标

- 不改变三页顺序、底栏显示规则或 Shell Back 顺序；
- 不恢复 AI 抽屉的边缘打开、拖动关闭或页面透视变换；
- 不重写 Operit AI runtime、Chat ViewModel、消息列表或 Router；
- 不移动完整 AI 组合树进入 `HorizontalPager`；
- 不增加第二个 `PagerState`、第二份页面状态或持久化手势状态；
- 不通过把 `snapPositionalThreshold` 改成另一个数字掩盖输入管线差异；
- 不复制 Compose Foundation 的完整 Pager 源码；
- 不使用反射访问 `PagerState` 内部字段；
- 不升级 Compose、Kotlin、AGP 或其他依赖；
- 不引入运行时开关、双实现或保留旧输入路径；
- 不改变 Android 系统手势灵敏度或设备导航设置。

## 已验证事实

### 当前应用输入管线

`KiyoriAppShell` 创建唯一：

```text
PagerState
PagerDefaults.flingBehavior(state = pagerState)
```

真正 `HorizontalPager` 组合：

```text
MINUS_ONE
HOME
AI_HOME 空占位
```

负一屏和软件首页的触摸命中真正 `HorizontalPager`。完整 AI 组合树位于 Pager 上方，通过
`PagerState` 的实时 offset 计算水平投影，并在根 `Box` 上单独挂载普通
`Modifier.scrollable`。

因此当前真实结构是：

```text
负一屏 / 软件首页
    └─ HorizontalPager 内建输入节点
       ├─ scrollableArea
       ├─ PagerWrapperFlingBehavior
       ├─ dragDirectionDetector
       ├─ pageNestedScrollConnection
       └─ PagerState

AI 首页
    └─ 根覆盖层 Modifier.scrollable
       ├─ 包装后的 ScrollableState
       ├─ PagerDefaults.flingBehavior
       └─ PagerState
```

两条路径共享状态对象和 fling 对象，但不共享完整手势上下文。

### Compose Foundation 1.11.4 的关键行为

当前解析版本的 `HorizontalPager` 会在每次手势按下时清空
`PagerState.upDownDifference`，抬起时写入本次抬起位置与按下位置的差值。Pager 吸附算法使用
该值完成两项决策：

1. 本次拖动方向；
2. 本次拖动距离占页面大小的比例。

低速释放的默认位置门槛为 `0.5f`，判断条件是严格大于：

```text
abs(dragDistance / pageSize) > 0.5
```

因此从屏幕约 `50%` 拉到 `0%` 或 `100%` 的位移约为半页，正好半页时回到原页符合当前
Compose 合同。高速释放的框架门槛为 `400dp/s`；达到门槛后按照释放方向最多进入相邻一页。

AI 覆盖层没有 Pager 内部的 `dragDirectionDetector`。其触摸位于 Pager 上方，底层
HorizontalPager 不在本次命中链上，所以 AI 手势不会清空或更新
`PagerState.upDownDifference`。AI 释放时，默认 Pager fling 可能读取零值，也可能读取上一轮
真正 Pager 手势留下的数据；这使目标页决策具备手势历史依赖。

### 目标设备复现与结论

| 场景 | 观察 | 结论 |
| --- | --- | --- |
| 中部慢拖，AI 首页约四分之一页 | 容易进入软件首页 | AI 释放没有按本次真实距离执行半页门槛 |
| 中部慢拖，真正 Pager 约半页 | 回到原页 | 与严格 `> 0.5f` 一致 |
| 中部短促快速拖动 | 只有 AI 首页容易切页 | 排除物理边缘 Back，支持 AI 独立输入管线差异 |
| AI 右边缘向左拖 | 仍进入软件首页 | 方向与内容 Pager 相反，属于 Android 系统 Back |
| AI 消息列表纵向斜拖 | 无首页水平位移 | 纵向列表能够赢得方向竞争，不是当前主因 |
| 软件首页斜拖 | 出现首页水平位移 | 软件首页没有纵向滚动容器竞争，属于正常水平分量 |

## 必须保持的不变量

1. `KiyoriAppShell` 继续只有一个 `PagerState`。
2. `SoftwareHomePage` 继续只从 `settledPage` 同步。
3. 完整 AI 组合树继续位于永久根，不跨 `HorizontalPager` 的 `SubcomposeLayout` 搬移。
4. `AI_HOME` 的 Pager 页面继续只承担位置和测量锚点。
5. 负一屏和软件首页继续由原生 `HorizontalPager` 处理触摸、可访问性与嵌套滚动。
6. AI 首页只在根对话页、软件首页主目的地、无 Shell 子页、无共享抽屉且无显式内容手势锁时
   接受首页横向手势。
7. 简单按下和点击不因上一段 Pager 动画仍在运行而被提前消费。
8. 真实水平拖动可以使用用户输入优先级中断未结束动画。
9. 一次手势最多进入相邻一页，不跨两页。
10. 页面大小取 `PagerState.layoutInfo.pageSize` 或 `pageSizeWithSpacing`，不能用物理屏幕宽度
    代替。
11. LTR 与 RTL 的物理方向只通过一个纯方向映射函数转换。
12. 任何框架内部数据都不能通过反射、复制内部类型或不可见 API 修改。
13. 修复完成后删除旧 AI 输入接线，不保留并行运行路径。

## 方案比较

### 方案 A：把完整 AI 组合树放入 `AI_HOME` Pager 页面

不采用。

优点是触摸天然经过真正 Pager，但会破坏永久 AI 根和深层 AI 路由的前景层级。历史上跨
`HorizontalPager` / `SubcomposeLayout` 搬移完整 AI 组合树已经在目标设备触发
`LayoutNode already has a parent`。即使避免动态搬移，AI 一级页面、设置来源覆盖、
Browser presentation 和长期保留的 AI Router 也会被限制在 Pager 的 z-order 与裁剪边界中。

### 方案 B：继续使用当前 AI `scrollable`，只修改位置阈值

不采用。

当前问题不是阈值数值不同，而是默认 Pager fling 没有拿到本次 AI 手势的位移和方向。调整
`0.5f` 不能修复陈旧数据、短促 flick、方向判断和连续手势，也会让真正 Pager 与 AI 路径产生
明确的两套产品参数。

### 方案 C：修改或伪造 `PagerState.upDownDifference`

不采用。

该字段属于 Compose Foundation 内部实现。反射、复制内部包名或依赖非公开符号会把 App Shell
绑定到框架实现细节，Compose 小版本更新即可使行为或二进制兼容失效。

### 方案 D：关闭 HorizontalPager 用户输入，由 App Shell 根重写三页全部触摸

不采用。

该方案可以建立一个绝对单一的触摸 owner，但会同时替换负一屏和软件首页已经正常工作的原生
Pager 语义，并需要自行补齐 TalkBack、键盘、鼠标、嵌套滚动、overscroll 和语义 actions。
影响面明显大于当前缺陷，不符合最小完整修复原则。

### 方案 E：为永久 AI 覆盖层建立公开 API 的完整等价手势桥

采用。

该方案保留：

- 原生 `HorizontalPager`；
- 唯一 `PagerState`；
- 永久 AI 根；
- 当前页面投影和 Shell 状态同步；
- 标准 `scrollable` 的 touch slop、方向竞争、嵌套滚动和手指跟随。

AI 路径不再把 `PagerDefaults.flingBehavior` 直接交给普通 `scrollable`。项目建立一个边界很窄的
AI Pager 手势桥，记录本次真实手势，并使用项目自有的纯吸附策略和 spring 完成本次释放。

## 目标架构

```text
KiyoriAppShell
├─ rememberPagerState()                         唯一位置状态
├─ HorizontalPager                             负一屏 / 软件首页原生输入
│  ├─ MINUS_ONE
│  ├─ HOME
│  └─ AI_HOME position placeholder
├─ Permanent AI Host
│  ├─ Pager offset projection
│  └─ KiyoriAiHomePagerGestureBridge           AI 根页等价输入
│     ├─ standard scrollable
│     ├─ per-gesture pointer observer
│     ├─ KiyoriHomePagerSnapPolicy
│     └─ bounded spring settle
└─ Shell BackHandler                           Android Back 独立路由
```

### 组件一：`KiyoriHomePagerGestureSession`

建议新增进程内、不可保存的手势会话：

```text
pointerId
originPage
originTargetPage
downPosition
upPosition
pageSizePx
layoutDirection
releaseVelocity
cancelled
```

规则：

- 每次按下立即重置；
- 从 Pointer `Initial` pass 观察按下与最终抬起，但不消费事件；
- 只保存当前手势所需最小数据；
- 手势取消、多指失效、页面失去输入权或组合销毁时立即清空；
- 不写入 `SavedState`、ViewModel、Shell state 或持久化存储；
- 上一手势的数据绝不能进入下一手势。

动画仍在执行时，手势起始锚点不能机械使用旧 `settledPage`。桥接应读取：

1. 自身正在执行的吸附目标；
2. 否则读取真实 `PagerState.targetPage`；
3. 静止时读取 `settledPage`。

这样从软件首页滑向 AI 首页但尚未落定时，用户在已出现的 AI 表面向右反拖，会以 AI 目标页作为
本次锚点并连续返回软件首页；普通点击则不取消原动画。

### 组件二：`KiyoriHomePagerSnapPolicy`

建议使用纯 Kotlin 函数实现目标页决策，不访问 Compose 节点：

```text
输入：
  originPage
  pageCount
  physicalDragX
  logicalReleaseVelocity
  pageSizePx
  layoutDirection

输出：
  targetPage
  decisionReason
```

固定合同：

- `abs(dragX) / pageSizePx > 0.5f` 才算越过位置门槛；
- 正好 `0.5f` 不切页；
- `abs(velocity) >= 400.dp/s` 时按速度方向进入相邻页；
- 高速与低速都最多移动一页；
- 不存在相邻页时保持边界页；
- 速度方向和位移方向不一致时，高速释放使用最终释放方向，低速释放使用最终净位移；
- LTR 下向右对应前一页、向左对应后一页，RTL 反向；
- `pageSizePx <= 0`、手势取消或缺少完整会话时停止本次吸附并暴露开发期错误，不能继续使用上一
  会话数据；
- 纯策略不读取 `PagerState.upDownDifference`。

`decisionReason` 只用于测试和开发诊断，可取：

```text
POSITION
VELOCITY
RETURN_TO_ORIGIN
BOUNDARY
CANCELLED
```

它不是用户状态，也不持久化。

### 组件三：`KiyoriAiHomePagerGestureBridge`

建议新增：

```text
app/src/main/java/com/kiyori/app/shell/KiyoriHomePagerGesture.kt
```

该文件集中拥有：

- 手势会话；
- LTR / RTL 方向归一化；
- AI 路径 `ScrollableState` 适配；
- AI 专用 fling / settle；
- spring motion spec；
- 启用条件的纯函数；
- 供 JVM 测试使用的纯目标页策略。

桥接的标准 `scrollable` 继续使用唯一 `PagerState` 执行拖动，因此页面位移仍直接跟随手指，并由
Pager 的互斥滚动机制处理中断与并发。区别只在释放阶段：

- 不再传入 `PagerDefaults.flingBehavior`；
- 使用本次 `KiyoriHomePagerGestureSession`；
- 在当前 `ScrollScope` 内以同一 spring 吸附到纯策略返回的相邻页；
- 动画开始前记录桥接自己的活动目标，结束或取消后清除；
- 最终页面仍由现有 `snapshotFlow { pagerState.settledPage }` 同步到 Shell。

Compose 单页 Pager 默认 spring 合同应保持：

```text
Spring.StiffnessMediumLow
Int.VisibilityThreshold.toFloat()
```

由于首页一次最多移动一页，不实现多页 decay。

### 组件四：点击与未结束动画

当前 `aiHostPagerGestureState` 把暴露给 `scrollable` 的
`isScrollInProgress` 固定为 `false`，目的是避免上一段动画尚未结束时普通点击在 touch slop 前被
滚动层截获。该需求继续有效，但必须由新桥接内部集中持有：

- 对 `scrollable` 的预 touch-slop 判定继续报告未滚动；
- 目标页、动画状态和中断判断读取真实 `PagerState` 与桥接活动目标；
- 普通点击不消费位置变化，也不取消动画；
- 水平位移超过 touch slop 后，真实用户滚动取得互斥权并中断动画；
- 实现完成后删除 `KiyoriAppShell` 中独立的匿名 `ScrollableState` 包装，不能同时保留两处状态
  适配。

### 组件五：AI 内容手势排除

首页桥接只能在以下条件全部成立时启用：

```text
aiHostIsRoot
primaryDestination == SOFTWARE_HOME
child == null
AI drawer closed
bookmark/history/download drawers closed
settings overlay absent
aiHomeGestureBlocked == false
```

标准方向竞争规则：

- touch slop 前不消费；
- 子节点已经消费当前变化时，本次首页桥接退出；
- 纵向分量先越过阈值时，本次首页桥接退出；
- 只有水平分量先形成明确手势时才接管；
- 文本选择、代码块横向滚动、聊天历史快速滚动条等显式交互继续通过
  `aiHomeGestureBlocked` 或子节点消费拥有本次手势；
- 手势锁在 `UP`、`CANCEL`、组合退出和异常结束时都必须恢复。

本任务不把现有失效的 `currentDrag`、`verticalDrag` 或 `dragThreshold = 40f` 接回页面切换。
这些旧参数在实现批次中应按引用审计结果删除；若仍有其他真实消费者，只保留实际消费者所需的最小
状态。

### 组件六：Android 系统 Back

以下路径保持不变：

```text
系统边缘 Back
    → Shell BackHandler
    → softwareHomePage = HOME
    → pagerState.animateScrollToPage(HOME)
```

右边缘向左滑在 AI 首页进入软件首页，是系统导航语义，不是内容 Pager 的方向证据。测试、日志和
验收报告必须把起手区域标记为：

```text
CONTENT_CENTER
SYSTEM_LEFT_EDGE
SYSTEM_RIGHT_EDGE
```

系统边缘结果不能用于调整内容手势阈值。

## 预计代码影响

### 必须修改

- `app/src/main/java/com/kiyori/app/shell/KiyoriAppShell.kt`
  - 移除 AI 覆盖层直接复用 `pagerFlingBehavior` 的接线；
  - 移除独立匿名 `aiHostPagerGestureState`；
  - 接入新的 AI Pager 手势桥；
  - 保持 `HorizontalPager`、页面投影、z-index、Back 和 settled 同步不变。
- `app/src/main/java/com/kiyori/app/shell/KiyoriHomePagerGesture.kt`
  - 新增手势会话、纯吸附策略、方向映射和 AI bridge。
- `app/src/test/java/com/ai/assistance/operit/ui/main/shell/KiyoriShellStateTest.kt`
  - 保留已有方向约定测试；
  - 增加 bridge 启用条件和 Shell 非回归合同。
- 新增纯策略测试：
  - 建议路径：
    `app/src/test/java/com/ai/assistance/operit/ui/main/shell/KiyoriHomePagerGesturePolicyTest.kt`
- 新增 Compose Android 手势测试：
  - 建议路径：
    `app/src/androidTest/java/com/ai/assistance/operit/ui/main/shell/KiyoriHomePagerGestureAndroidTest.kt`

### 按引用审计决定

- `AIChatScreen.kt`
- `ChatScreenContent.kt`
- `ChatHistorySelector.kt`

只处理与 `aiHomeGestureBlocked`、失效拖动状态和真实横向子组件所有权直接相关的内容，不借机重构
聊天页面。

### 实现完成后同步

- `CONTEXT.md`
  - 将“共享 Pager state 和 fling physics”收紧为“原生 Pager 与 AI 等价桥共享同一产品吸附
    合同”，避免再次把共享对象误写成共享完整输入管线。
- `docs/doc-src/architecture/kiyori_product_shell_and_navigation.md`
- `docs/doc-src/decisions/0004_modal_ai_drawer_navigation.md`
- `docs/TODO/kiyori_product_shell/`
- `docs/TODO/plugin_runtime_storage_and_home_input/`
- `docs/TODO/README.md`
- `config/architecture/m04b-app-shell-normalized-sha256.txt`
- 与 App Shell 结构哈希和依赖边界直接相关的 architecture manifest / tests。

本规划阶段不修改上述正式行为文档，因为产品行为尚未实现。

## 分阶段实施

### Phase 0：重新建立当前基线

状态：`LOCAL DONE / DEVICE RECHECK PENDING`

1. 重新读取 `AGENTS.md`、本文、正式开发准备六个入口文件和相关任务日记；
2. 运行 `git status --short --branch`、记录 HEAD、相关已暂存/未暂存/未跟踪差异；
3. 核对当前 Compose Foundation 实际解析版本，而不是只读取 BOM 标签；
4. 核对 `KiyoriAppShell.kt`、`AIChatScreen.kt` 和测试文件是否存在并行修改；
5. 确认 Kiyori 在实施时仍未发布。若发布状态已经改变，停止方案替换并重新确认兼容要求；
6. 用目标设备再次执行“慢拖后停住一秒再释放”矩阵，记录有无前置原生 Pager 手势时的结果。

完成信号：

- 基线、并行改动、依赖版本和设备复现被写入任务记录；
- 没有未解决的文件所有权冲突；
- 本文仍与当前源码相符。

### Phase 1：纯策略和方向合同

状态：`DONE`

1. 建立 `KiyoriHomePagerSnapPolicy`；
2. 建立唯一 LTR / RTL 物理方向到页面方向的转换；
3. 固定严格半页、`400dp/s`、最多一页和边界规则；
4. 建立手势会话生命周期的纯数据结构；
5. 先完成 JVM 参数化测试，不连接 UI。

完成信号：

- 纯策略测试覆盖全部阈值、速度、方向、边界和取消组合；
- 正好半页与半页以上的边界测试使用相邻浮点值；
- 测试证明上一会话数据不会影响下一会话。

### Phase 2：AI bridge 接入

状态：`DONE`

1. 在新文件内实现 AI bridge；
2. 标准 `scrollable` 继续驱动唯一 `PagerState`；
3. 项目手势观察器在每次按下时重置并记录本次 up/down 差值；
4. AI 释放使用项目纯策略和 bounded spring；
5. 普通点击保持透传，水平拖动可以中断动画；
6. 在同一批次删除当前 AI `pagerFlingBehavior` 接线和匿名状态包装；
7. 不改变真正 HorizontalPager 的输入、页面内容和 `snapshotFlow`。

完成信号：

- 生产源码只剩一个 AI 输入桥；
- `rg` 不再找到 AI 覆盖层直接把 `PagerDefaults.flingBehavior` 交给普通 `scrollable`；
- `PagerState.upDownDifference` 不出现在项目源码；
- 旧离散阈值实现和失效状态没有可达引用。

### Phase 3：Compose 差分手势测试

状态：`PARTIAL`

纯策略 JVM 已覆盖半页边界、速度优先、RTL、边界、取消、无效会话和会话一次性消费。
Compose Android 测试已加入低于/超过半页与内容 owner 阻断场景，并通过 Kotlin 编译；当前任务
未获设备操作授权，因此 instrumentation 尚未执行，完整中间帧和差分矩阵仍待目标设备或受控
Android 测试环境运行。

建立一个受控测试宿主，分别让真正 `HorizontalPager` 与 AI bridge 从同一页面宽度、相同时间轴和
相同物理轨迹开始。使用 `performTouchInput` 和可控测试时钟覆盖：

| 轨迹 | 预期 |
| --- | --- |
| 慢拖 `25%`，停住后释放 | 两条路径都回到原页 |
| 慢拖 `49%` | 两条路径都回到原页 |
| 慢拖 `50%` | 两条路径都回到原页 |
| 慢拖 `51%` | 两条路径都进入相邻页 |
| 短距离低速 | 两条路径都回到原页 |
| 短距离速度低于 `400dp/s` | 两条路径都回到原页 |
| 短距离速度达到或超过 `400dp/s` | 两条路径都进入速度方向的相邻页 |
| 拖动超过半页后反向回到不足半页 | 两条路径都回到原页 |
| 动画中立即反向拖动 | 位移连续，进入反向目标 |
| 动画中点击按钮 | 点击到达，页面不因点击回吸 |
| 以纵向为主的斜拖 | AI 列表滚动，Pager 不移动 |
| 以横向为主的斜拖 | Pager 跟手移动 |
| 子横向组件已消费 | 首页 bridge 不移动 |
| 前一手势方向相反 | 当前结果不受前一手势影响 |
| RTL | 物理方向与页面方向按合同反转 |
| 第一页和最后一页越界方向 | 保持边界页 |

测试时钟需要在拖动中采样 `currentPageOffsetFraction`，不能只断言最终 `settledPage`。至少验证：

- 首次有效拖动帧没有跳变；
- 手指反向时 offset 同帧改变方向；
- 抬手后的 spring 从当前 offset 连续开始；
- 没有先回原点再执行程序化跳页。

### Phase 4：Shell、可访问性和内容回归

状态：`PARTIAL`

`KiyoriShellStateTest 68/68` 已覆盖 Full-Screen Web Search overlay、来源保持型 Settings、
drawer、Browser 与非根 AI route 的输入 gate；`KiyoriSoftwareHomeSearchTest 9/9` 锁定两个
真实搜索宿主必须显式声明 Back owner，并保持 Browser CompositionLocal 的 strict 合同。
Compose Android smoke test 已加入“无 Browser provider 的 Full-Screen Search 仍可组合”场景并
通过 Kotlin/Java 编译。TalkBack、键盘、旋转、IME、窗口尺寸和真实内容滚动仍属于设备层验收。

1. 运行完整 `KiyoriShellStateTest`；
2. 验证抽屉、设置、Browser、子页和非根 AI 页面不会启用首页 bridge；
3. 验证 TalkBack 页面切换 actions、焦点顺序和可读页面标题；
4. 验证键盘方向键、旋转、窗口尺寸变化和进程状态恢复；
5. 验证 AI 输入框选择、长按、代码块横向滚动、历史快速滚动条、附件和工具按钮；
6. 验证底栏 alpha 继续跟随实时 Pager offset；
7. 验证系统 Back 与内容手势使用独立测试入口。

完成信号：

- Shell 状态、Compose 手势和可访问性测试全部通过；
- 没有新的第二状态 owner；
- AI 流式对话、草稿和滚动位置在页面往返后保持。

### Phase 5：项目门禁与 Debug APK

状态：`LOCAL DONE`

本地最终证据：

- `git diff --check`：通过；
- `check_formal_readiness.py --repository . --require-main`：PASS；
- `check_architecture_boundaries.py ... --require-main`：PASS，`phase=m03`；
- 专项 JVM：`85/85`，零 failure/error/skip；
- 完整 App JVM：`239 suites / 1404 tests`，零 failure/error/skip；
- AndroidTest Kotlin/Java：编译通过；新增 Compose 手势和 Full-Screen Search smoke test
  未在设备执行；
- `:app:assembleDebug`：在全部源码、文档和架构快照修改后通过；唯一 launcher 与 player
  runtime packaging gate 均通过；
- APK：`app/build/outputs/apk/debug/app-debug.apk`，`467107608` bytes，SHA-256
  `67F8F4D73367981591A2C0C73C25CB4F3B698C658238C682DA040E8E4533B16D`；
- package/version/SDK：`com.kiyori / 45 / 0.1.0 / min 26 / target 34 / compile 37`；
- launcher/ABI：唯一 `com.ai.assistance.operit.ui.main.MainActivity`，`arm64-v8a`；
- 签名/对齐：APK Signature Scheme v2，单一 Android Debug signer，16 KB `zipalign` PASS。

实现批次至少执行：

```text
git diff --check
.\.venv\Scripts\python.exe -B ci\script\check_formal_readiness.py --repository . --require-main
.\.venv\Scripts\python.exe -B ci\script\check_fresh_clone.py --repository .
.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain
.\gradlew.bat :app:compileDebugAndroidTestKotlin --no-daemon --console=plain
.\.venv\Scripts\python.exe -B ci\script\check_architecture_boundaries.py --repository . --ownership config\architecture\package-ownership.toml --require-main
.\gradlew.bat :app:assembleDebug --no-daemon --console=plain
```

`check_markdown_links.py` 只接受 Git revision。未提交工作树先运行本地相对链接存在性扫描；形成
candidate revision 后再执行：

```text
.\.venv\Scripts\python.exe -B ci\script\check_markdown_links.py --base <base-commit> --candidate <candidate-commit>
```

检查最终 APK，而不是只看 Gradle 退出码：

- 路径；
- 写入时间；
- 大小；
- SHA-256；
- 包名、versionCode、versionName；
- Debug 签名；
- `zipalign`；
- 唯一 launcher 和既有 runtime packaging 门禁。

### Phase 6：目标设备验收

状态：`PENDING`

每轮设备记录：

```text
设备型号
Android 版本
导航模式
窗口方向与尺寸
APK SHA-256
起手区域
轨迹距离
持续时间
是否停住后释放
最终页面
是否出现误点击、跳帧或回吸
```

必须覆盖：

1. 屏幕中部从约 `50%` 开始的 `25% / 49% / 50% / 51%` 慢拖；
2. 慢拖后停住一秒再释放；
3. 中部短促低速与高速手势；
4. 有无前置原生 Pager 手势的重复矩阵；
5. Home → Minus-One、Minus-One → Home、Home → AI、AI → Home 四个方向；
6. AI 消息列表纵向斜拖；
7. 代码块、文本选择、输入框、历史快速滚动条；
8. 滑向 AI 后立即点击输入框、工具按钮；
9. 滑向 AI 后立即反向；
10. 手势导航下左右物理边缘 Back；
11. 三键导航下同样的内容中部手势；
12. IME 展开与收起；
13. 浅色、深色、竖屏、横屏、分屏；
14. TalkBack；
15. 至少一台 Android 8+ ARM64 设备和当前报告问题的目标设备。

验收通过条件：

- 四个内容方向在相同轨迹下得到一致目标页；
- 正好半页不切页，超过半页切页；
- 高速门槛两侧结果稳定；
- AI 结果不再受前一手势影响；
- 普通点击、纵向滚动和内容横向手势没有新增误消费；
- 物理边缘 Back 的结果与 Shell 合同一致，并明确标记为系统输入；
- 连续执行每个场景至少十次，没有偶发历史相关结果。

## 自动测试详细矩阵

### 纯策略测试

- `0f`；
- `0.01f`；
- `0.499f`；
- `0.5f`；
- `0.501f`；
- `0.999f`；
- 正负方向；
- `399dp/s`；
- `399.999dp/s`；
- `400dp/s`；
- `401dp/s`；
- 位移与速度同向；
- 位移与速度反向；
- 第 0、1、2 页；
- LTR 与 RTL；
- `pageSize=0`；
- 取消；
- 手势会话重置；
- 上一手势为相反方向；
- 活动吸附目标与 settled page 不同。

### Compose 中间帧测试

- touch slop 前 offset 不变化；
- 越过水平 slop 后 offset 连续；
- 纵向 slop 先发生时 offset 不变化；
- 子节点消费后 bridge 终止；
- 反向拖动没有方向延迟；
- 抬手 spring 没有位置跳变；
- 新水平手势取消旧 spring；
- 新普通点击不取消旧 spring；
- 页面 settle 后只产生一次 Shell 状态同步。

### 静态结构测试

- AI 覆盖层不再使用 `pagerFlingBehavior`；
- `HorizontalPager` 仍只有一个；
- `rememberPagerState` 仍只有一个 Shell owner；
- `AI_HOME` Pager 页面仍是位置占位；
- AI 根仍位于 Pager 外；
- 不存在 `movableContentOf` 跨 Pager 搬移；
- 不存在反射访问 Pager 内部状态；
- 不存在第二套页面索引或第二 `PagerState`；
- 不存在旧 `kiyoriAiHomeSwipeToCenter`；
- 不存在失效手势字段的无引用传递。

## 风险与控制

### R1：物理方向与 Pager 逻辑方向符号相反

控制：

- 只允许一个方向归一化函数；
- LTR / RTL、四个页面方向和边界全部参数化测试；
- 生产代码不分散书写正负号判断。

### R2：页面尺寸使用错误

控制：

- 使用 `PagerState.layoutInfo.pageSize` / `pageSizeWithSpacing`；
- 覆盖状态栏、导航栏、横屏、分屏和折叠屏；
- 禁止使用设备物理屏幕宽度作为吸附分母。

### R3：普通点击仍被动画状态抢占

控制：

- touch slop 前不消费；
- 对 scrollable 暴露的预判状态与真实动画状态分离；
- 自动测试动画中点击输入框和按钮；
- 目标设备重复快速点击。

### R4：AI 内容横向组件被首页 bridge 抢占

控制：

- 尊重子节点消费；
- 保留显式 `aiHomeGestureBlocked`；
- 建立代码块、文本选择、快速滚动条和工作区的定向测试；
- 不使用覆盖全屏且立即消费 DOWN 的 detector。

### R5：动画中反向出现跳帧

控制：

- 手势起点读取活动目标而非机械读取旧 settled page；
- 新用户水平滚动通过 Pager 的互斥滚动机制取消旧动画；
- 测试时采样中间帧，不只检查最终页面。

### R6：Compose 版本更新改变默认 Pager 合同

控制：

- 文档记录当前参考版本和常量；
- 依赖升级任务必须重新运行真实 Pager 与 AI bridge 差分测试；
- 不读取或修改框架内部字段；
- 若产品决定改变阈值，原生 Pager 与 AI bridge 必须在同一任务中一起更新并重新验收。

### R7：系统 Back 被误判为 Pager 失败

控制：

- 设备报告必须记录起手区域和导航模式；
- 中部内容手势与左右边缘 Back 分表验收；
- 不用系统边缘结果调整内容滑动参数。

### R8：并行工作树覆盖

控制：

- 实施前重新检查相关文件的未提交差异；
- 使用精确文件与精确 hunk；
- 不清理、重置或覆盖其他任务；
- architecture hash 只在最终实现稳定后更新。

## 禁止实现

- 给 AI 路径单独设置更低或更高的随意阈值；
- 在 AI 页手势结束后直接调用 `showSoftwareHomePage(HOME)` 而不跟手拖动；
- 只依据 `currentPageOffsetFraction` 猜测本次手势起点；
- 读取上一轮手势会话；
- 反射或复制 Compose 内部 `PagerState` 字段；
- 把完整 AI 组合树放入或移出 Pager；
- 增加第二个 Pager 或第二个 AI Host；
- 用延迟、节流或点击禁用掩盖页面回吸；
- 屏蔽系统 Back；
- 用测试豁免、禁用检查或 architecture allowlist 扩张替代真实修复；
- 保留旧 AI 输入接线与新 bridge 并行运行。

## 完成定义

只有以下条件全部满足，任务才可以标记为实现完成：

1. AI 覆盖层不再直接使用依赖内部 Pager 手势元数据的默认 fling；
2. 每次 AI 手势建立并清理自己的完整会话；
3. 原生 Pager 与 AI bridge 的差分矩阵通过；
4. 静态结构、JVM、Compose Android、Shell、architecture 和正式开发门禁通过；
5. Debug APK 构建及产物审计通过；
6. 目标设备四方向、中部阈值、速度、连续手势、点击、纵向滚动和内容横向组件全部通过；
7. 系统边缘 Back 单独通过；
8. 正式架构、`CONTEXT.md`、TODO 和历史输入修复文档已同步；
9. 工作树差异经过审阅，没有混入其他任务；
10. 未经用户授权不提交、不推送、不安装、不操作远端。

自动检查和 Debug APK 通过后，若目标设备矩阵尚未完成，状态必须保持
`verification_pending`。
