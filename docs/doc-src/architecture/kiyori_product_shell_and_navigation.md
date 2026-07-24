---
status: accepted_design
implementation: pending
last_updated: 2026-07-24
---

# Kiyori 产品壳与导航架构

本文定义 Kiyori 的目标产品壳、首页层级、模态 AI 左抽屉、网页搜索、设置归属和自适应布局。首个 App Shell 切片已经替换 Operit 顶层导航壳；本文同时记录已落地边界与后续目标，不能把页面骨架视为领域功能完成。

## 当前实现状态

- Kiyori App Shell 已成为唯一顶层导航 owner，并持有五个根目的地、三页首页 Pager、底栏可见性与 Shell Back 状态
- AI 对话作为稳定宿主挂入右侧 AI 首页；按钮触发的模态 AI 左抽屉已经取代全屏 AI Center
- AI 一级根的显式身份、外部入口菜单/返回解析、AI Settings 跨来源根页重置和全局透明状态栏已接受设计，代码与新 Debug 证据待本轮落地
- 旧手机抽屉、平板侧栏、边缘拖动、主内容透视变换、全局手势状态和抽屉专属主题设置已删除
- 全屏网页搜索、负一屏及小程序/文件/设置根页面当前为接线骨架；浏览器首页开始接入现有 WebSession 共享运行时
- 模态 AI 左抽屉、AI 一级路由替换和 AI 设置来源返回已实现并通过自动验证；真机交互保持 `verification_pending`，权限总览与状态徽标尚未完成
- 自动检查不能证明真机手势、Back、旋转、折叠屏或流式对话持续性，以上保持 `verification_pending`

稳定术语以根目录 [CONTEXT.md](../../../CONTEXT.md) 为准。产品定位决策见 [Kiyori 产品定位与 Operit AI 边界](../decisions/0001_kiyori_product_positioning.md)，当前导航决策见 [模态 AI 左抽屉导航](../decisions/0004_modal_ai_drawer_navigation.md)，视觉规则见 [UI 设计来源层级](../decisions/0003_ui_design_source_hierarchy.md)。

## 设计来源层级

Kiyori 不建立与 AI 页面割裂的第二套视觉系统。来源层级固定为：

1. 当前已接受的 Kiyori 产品合同决定功能行为、页面归属和导航语义。
2. [AAswordman/Operit@ef00abc5](https://github.com/AAswordman/Operit/tree/ef00abc5099187b4665957e9697cb743c81fa154) 决定视觉语言，包括主题 token、排版、形状、图标处理、动效、弹窗、设置行、液态玻璃与水波玻璃组件。
3. [Kiyori-CN/kiyori-android@24a2dfa9](https://github.com/Kiyori-CN/kiyori-android/tree/24a2dfa91f0a4166dc58e5c4732d11861173f766) 决定 Kiyori 自有页面的结构、功能布局和浏览器交互基线。

当旧 Kiyori 页面存在硬编码颜色、圆角或其他独立样式时，保留其信息结构，使用 Operit 视觉组件重新表达。两份参考仓库都不是本项目的运行依赖。

## 目标

- 让 Kiyori 而不是 Operit UI 拥有应用启动、顶层导航、系统设置和自适应布局
- 保持 Operit AI 对话、模型、工具、记忆、工作流和运行时能力
- 给浏览器、媒体、阅读、文件、下载和广告拦截建立独立页面与清晰 owner
- 让人工页面和 AI 工具通过稳定能力合同操作同一份领域状态
- 支持手机、平板、横屏和折叠屏，不为不同窗口维护不同导航事实

## 非目标

- 本阶段不重命名 `com.ai.assistance.operit`、`operit://`、数据库、备份、插件、ToolPkg 或 MCP 等兼容标识
- 本阶段不重新实现 Operit AI runtime
- 本阶段不把网页搜索扩展为书签、历史、文件、小程序或 AI 的聚合搜索
- 本阶段不决定视频、音乐、小说和广告拦截器的具体页面细节
- 本阶段不保留全屏 AI Center、边缘手势抽屉或永久侧栏等并行入口

## 扩展与存储边界

- 普通 JS、TS 与 HJSON 项目是脚本包；ToolPkg 容器是插件，可注册工具子包、输入框菜单、消息处理、XML 渲染、抽屉页面和配置页面。
- Operit 市场的 `type="package"` 与 `toolpkg_v2` 是兼容协议值，Kiyori 显示层将其称为插件；协议值、下载地址、ToolPkg ID 与 `com.operit.*` namespace 不改写。
- Kiyori 新建公共数据使用 `Download/Kiyori`。插件配置、MCP、Skill、工作区、导出、备份和临时文件从同一路径所有者派生，不自动读取或删除 `Download/Operit`。

## 导航层级

```text
Kiyori App Shell/
├── 软件首页/
│   ├── 负一屏
│   ├── 软件首页
│   │   └── 软件首页搜索卡/
│   │       ├── 搜索 -> 全屏网页搜索页
│   │       └── AI -> AI 首页
│   └── AI 首页
│       └── 模态 AI 左抽屉 -> AI 一级页面
├── 浏览器首页/
├── 小程序首页/
├── 文件管理首页/
└── 设置首页/
    └── AI 设置
```

模态 AI 抽屉和设置首页进入的是同一个 AI 设置页面与持久状态。来源只决定根页面 Back 返回 AI 首页还是 Kiyori 设置首页。

## 顶层状态模型

产品壳至少区分两组正交状态：

- `PrimaryDestination`：软件首页、浏览器首页、小程序首页、文件管理首页、设置首页
- `SoftwareHomePage`：负一屏、软件首页、AI 首页

`SoftwareHomePage` 只在 `PrimaryDestination` 为软件首页时有效。AI 首页不是第六个底部入口，负一屏也不是独立顶层目的地。

全屏网页搜索页、AI 一级页面、历史页和书签页属于明确的页面路由，不加入首页 Pager。

## 启动与页面可见性

- 正常启动进入软件首页的中间页
- 软件首页位于 Pager 中央，负一屏在左，AI 首页在右
- 软件首页显示底部五入口
- 负一屏隐藏底部五入口
- AI 首页隐藏底部五入口并保持现有全屏对话形态
- 浏览器首页、小程序首页、文件管理首页和设置首页显示同一组底部五入口
- 全屏网页搜索页、子页面和播放器、阅读器、网页内容等沉浸页面隐藏底部五入口
- AI 首页应保持稳定挂载，左右切换不能销毁正在进行的对话、输入草稿或流式状态
- 模态 AI 抽屉覆盖当前 AI 页面；AI Home 在抽屉开关和一级页面切换期间保持持续组合与同一个会话状态
- 三页首页和 AI Home 覆盖层共享同一个 `PagerState` 与 fling 行为。页面位移直接跟随手指，新反向拖动可取消尚未结束的 fling；`SoftwareHomePage` 只在 `settledPage` 后同步

底栏的显示与隐藏通过产品壳的页面状态驱动。切换动画不得通过增删内容高度造成首页主体跳动。

## 手势所有权

迁移前 `PhoneLayout` 在根布局监听水平拖动并打开抽屉，这与软件首页 Pager 使用同一手势轴。当前合同采用以下规则：

- 删除手机端左边缘打开 Operit 抽屉的手势
- 负一屏、软件首页和 AI 首页使用同一 Pager 手势轴、速度、阈值与吸附模型，不以离散阈值触发程序化跳页
- 模态 AI 左抽屉只能由三横线按钮打开，不响应边缘、拖动或横向滑动
- AI 页面内部的代码块、横向列表、选择器等组件需要明确消费自己的横向手势
- 全屏网页搜索、AI 一级页面和其他子页不把手势传给首页 Pager

## Back 契约

- 已打开的模态 AI 抽屉在其他 Back 逻辑之前关闭
- AI 一级页面返回 AI 首页，深层页面返回所属一级页面
- AI 首页和负一屏返回软件首页中间页
- 子页面返回所属根页面，当前根页面的子栈与滚动状态由该根页面保存
- 非软件首页的顶层根页面返回软件首页中间页
- 软件首页中间页再次 Back 时显示退出确认
- 切换底部入口不会清空其他根页面的子栈和滚动状态

输入法展开时的横滑条件、预测性返回动画与浏览器网页历史的优先级仍需在实现前形成细化状态表。

## 全屏网页搜索

软件首页搜索卡包含“搜索”和“AI”两个按钮。“搜索”打开全屏网页搜索页；“AI”把首页 Pager 移动到 AI 首页。全屏网页搜索页只接受网址或网页搜索词，并把结果交给 Kiyori 浏览器。

明确排除：

- 书签搜索
- 浏览历史搜索
- 文件搜索
- 小程序搜索
- AI 提问与 AI 会话搜索

历史页和书签页各自拥有搜索框、筛选状态和结果列表。它们可以复用文本输入组件，但不能与全屏网页搜索共享业务结果模型。

旧 `kiyori-android` 的 `HomeLandingSearch.kt` 是搜索卡结构来源。两个按钮不是同一个结果页的筛选模式：“搜索”进入网页搜索，“AI”直接进入 AI 首页。

旧实现从软件首页调用 `BrowserActivity.start(openSearch = true)`，再显示 `BrowserUrlDropdownOverlay`。进程首次进入浏览器时，`BrowserProcessLaunchPolicy` 会让 `BrowserWindowRepository` 准备一个新的首页窗口，同时保留既有窗口；同一进程后续进入搜索时使用当前活动窗口。打开搜索本身不会每次都新建窗口。

首期采用同一语义：没有本次进程浏览会话时创建并激活一个空白标签，已有活动标签时在该标签提交首页搜索。显式“新标签搜索”继续由浏览器标签管理功能负责，不让软件首页搜索卡暗中改变标签数量。

## AI 首页与模态 AI 左抽屉

AI 首页只承担对话主界面，不再承担 Operit 应用壳。即使当前没有可用模型配置或 API Key，AI 首页仍显示对话内容区和输入控件；模型配置由独立设置页面承载，缺少凭据时只在实际需要模型的操作中报告。AI 首页与 AI 一级页面的左上角三横线按钮打开模态 AI 左抽屉；深层页面显示返回箭头。浏览器、小程序、文件管理和 Kiyori 设置不显示 AI 抽屉按钮。

`ChatHeader` 和 `ChatHistorySelectorPanel` 继续拥有对话历史、搜索、新建、切换和删除对话。抽屉中的“AI 对话”只返回现有 AI Home，不创建会话、不清空草稿，也不复制会话操作或最近对话列表。

原版左抽屉的可见入口库存为：

- 包管理
- 权限
- 工作流
- AI 对话
- 助手配置
- 记忆库
- 工具箱
- 关于
- 使用手册
- 设置

帮助、关于、使用手册和应用语言不进入抽屉；其中帮助、关于和使用手册由 Kiyori 产品页面承接，应用语言由 Kiyori 系统设置承接。“设置”改为 AI 设置。包管理、权限授予、工作流、助手配置、记忆库和工具箱保留为 AI 能力入口，Terminal 仍只位于 AI 首页右上角。

源码对照表明，原抽屉的“权限”入口是 `Screen.ShizukuCommands`，而 `Screen.ToolPermission` 是独立的 AI 工具调用策略页面。两者不能继续共用“权限”这一含混名称：

- 设备能力授权：Android 运行时权限、文件访问、Shizuku、无障碍、悬浮窗、电池优化豁免、Root 和调试能力，由 Kiyori 系统安全设置持有状态
- AI 工具授权：`ALLOW`、`ASK`、`FORBID` 及单工具例外，由 AI 设置持有状态

抽屉“权限”快捷卡直接进入 `Screen.ShizukuCommands`。Kiyori 设置中的权限入口进入权限总览，AI 设置中的 AI 工具授权进入 `Screen.ToolPermission` 并继续由 `ToolPermissionSystem` 持有状态。三者不复制或混合持久状态。

### 权限中心合同

Kiyori 设置首页进入权限中心。权限中心是系统级的状态汇总与导航页，不是第三套权限存储；抽屉“权限”高频卡片仍按已确认合同直接进入 `Screen.ShizukuCommands`。

当前源码和已接受的安全合同涉及四类能力。前两类已有实现，后两类仍处于设计阶段：

| 领域 | 当前来源 | 权限中心职责 | 唯一 owner |
| --- | --- | --- | --- |
| 设备能力 | `Screen.ShizukuCommands`、`ShizukuDemoScreen` 及设备权限状态 | 汇总 Kiyori 自身运行所需能力并进入分项页面 | Kiyori 系统安全设置 |
| AI 工具授权 | `Screen.ToolPermission`、`ToolPermissionSettingsScreen`、`ToolPermissionSystem` | 展示全局策略与例外摘要并进入原设置页 | AI 设置中的 `ToolPermissionSystem` |
| 高影响操作确认 | Kiyori Capability API 的待建安全合同 | 展示确认策略并进入策略页 | Kiyori 系统安全策略 |
| AI 操作记录 | 待建操作记录存储与查看页 | 展示待审阅或最近记录并进入记录页 | AI 操作记录领域 |

当前推荐的信息结构为：

```text
权限中心/
├── 权限总览
│   ├── 需要处理的设备能力
│   ├── AI 工具全局策略与例外数量
│   ├── 高影响操作确认策略
│   └── 待审阅或最近操作记录
├── 设备能力/
│   ├── Android 运行时权限
│   ├── 文件与存储
│   ├── 悬浮窗
│   ├── 电池优化
│   ├── 无障碍
│   ├── 位置
│   ├── Shizuku
│   └── Root / 调试 / Terminal 特权状态
├── AI 工具授权
└── AI 安全/
    ├── 高影响操作确认策略
    └── AI 操作记录
```

总览只展示真实、可解释的事实，不引入安全分数，也不把“未授权但当前功能未使用”描述为故障。尚未实现的高影响操作策略和操作记录在能力落地前不显示占位入口。

权限中心首页采用“只读状态总览 + 分项进入唯一设置页面”。设备能力项进入 Kiyori 系统安全设置，AI 工具授权进入现有 `Screen.ToolPermission`；首页不放置直接权限开关，也不复制 owner 页的设置表单。

首屏沿用 Operit 设置页的紧凑分组、设置行、状态文字、图标和分隔方式，不建立大面积仪表盘或安全评分卡。信息顺序固定为：

1. 设备能力：显示当前选用的设备执行通道、该通道状态和真正需要用户处理的能力数量
2. AI 工具授权：显示全局 `ALLOW`、`ASK` 或 `FORBID`，以及单工具例外数量
3. AI 安全：在同一分组内放置高影响操作确认和 AI 操作记录；各自只在完整策略页或记录页实现后显示

设备能力状态必须基于功能依赖关系，而不是统计系统中所有未授予权限：

- `需要处理`：用户已经启用或正在使用的能力缺少必要授权
- `可用`：当前执行通道或已启用功能所需授权完整
- `未启用`：可选能力尚未开启，不视为异常
- `受限`：用户已经选择该能力，但系统、设备策略或服务状态阻止使用，并显示具体原因
- `检查中`：状态读取尚未完成，只作为短暂加载状态

AI 工具授权不折算成设备权限状态，也不产生“正常”或“异常”判断。`ASK` 是明确策略值，不是警告；总览原样显示全局策略，并分别显示自动允许和禁止的单工具例外数量。

Compact 窗口点击权限总览分项后全屏进入 owner 页面；Medium 与 Expanded 复用 Kiyori 设置的详情区域。返回权限中心时保留其滚动位置，窗口尺寸变化不改变当前 owner 路由。

模态 AI 抽屉的“权限”高频卡片继续使用短标签和 `SidebarQuickActionCard` 信息密度。`resolveSidebarPermissionStatus` 只检查所选 `AndroidPermissionLevel` 的执行通道：标准模式显示正常，调试模式检查 Shizuku 的安装、运行与授权，其他特权模式检查对应 listener；它不统计 Android 运行时权限，也不读取 `ToolPermissionSystem`。

明确不属于权限中心的内容：

- “数据和权限”中的备份、聊天记录管理和 Token 统计
- 工具箱 `Screen.AppPermissions` 对其他已安装应用执行的授权、撤销和重置操作
- 模型服务商凭据、浏览数据清理和普通隐私偏好

`Screen.AppPermissions` 继续属于工具箱。它修改其他应用的权限，未来接入 AI 时必须遵守高影响操作确认与记录合同，但不能因此成为 Kiyori 自身设备授权的一部分。

### 已确认的页面导航模型

- AI Home 与显式标记的 AI 一级根显示三横线，深层页面显示返回箭头；快捷方式、Widget 或外部路由直达一级根时规则相同
- 一级根在 `RouteEntry` 保存注册入口 ID。宿主入口按 route ID 匹配，ToolPkg 插件入口按 route ID 与注册参数共同匹配；不从 route args、返回栈深度或 instance ID 命名推断所有权
- 点击其他抽屉入口先建立目标一级路由，再关闭抽屉；一级页面互相替换，不连续压入同一返回栈
- 点击当前入口只关闭抽屉，不创建页面实例；动作型 ToolPkg 入口只执行一次
- 每个 AI 一级页面保留自己的滚动、筛选、表单和子页面栈
- 宿主一级根使用稳定实例并保存子栈。ToolPkg 一级根每次进入创建新路由实例；只有对应 `RouteSpec.keepAlive=true` 时，`stableScreenKey` 和保存栈才跨抽屉切换保留
- AI 一级页面 Back 返回 AI Home，深层页面 Back 返回所属一级页面
- AI 设置从抽屉进入时属于 AI 一级页面，从 Kiyori 设置进入时返回 Kiyori 设置首页；两种来源共享同一页面、表单、滚动与持久状态，同来源族可恢复子栈，跨来源族始终打开 AI 设置根页
- 窗口尺寸或折叠姿态变化只改变抽屉宽度，不创建新页面实例，也不重置当前页面状态

### 原版左抽屉映射

原版 `DrawerContent` 的有效信息层级为：

1. `SidebarInfoCard` 显示模块名称和网络状态。
2. 三个 `SidebarQuickActionCard` 分别显示包管理、权限和工作流，并携带启用包数量、权限状态和工作流数量。
3. “AI 功能”使用 `CompactNavigationDrawerItem` 展示 AI 对话、助手配置、记忆库和工具箱。
4. “插件”按注册顺序展示 ToolPkg 动态入口。
5. 底部固定显示关于、使用手册和设置。

模态 AI 抽屉沿用该层级，不另造卡片式仪表盘。产品归属调整后的结构为：

```text
模态 AI 左抽屉/
├── 模块状态：Operit AI / 网络状态
├── 高频入口：包管理 / 权限授予 / 工作流
├── AI 功能：AI 对话 / 助手配置 / 记忆库 / 工具箱
├── 插件：ToolPkg 动态入口
└── 固定底部入口：AI 设置
```

“关于”和“使用手册”迁入 Kiyori 产品页面；原“设置”入口改为 AI 设置。“AI 对话”返回现有 AI Home，不创建会话或清空草稿。

### 视觉与组件合同

- 沿用 Operit 的主题 token、字体层级、图标、选中态、状态徽标、分隔线和交互密度
- 模块状态区显示“Operit AI”，不再由 `softwareIdentity` 决定 Kiyori 应用品牌
- 保留包数量、权限状态、工作流数量和动态插件入口，不把可操作状态退化为静态按钮
- 使用原版纵向滚动层级；AI 设置固定在底部安全区上方，不随长列表消失
- `<600dp` 为窗口宽度 `75%`，`600-839dp` 为 `320dp`，`>=840dp` 为 `360dp`；分隔铰链把最大宽度限制在左侧物理区域
- 抽屉遮罩覆盖窗口全高；面板从状态栏底部开始，左侧两角贴屏，右侧两角为 `16dp`
- 面板内容只处理水平、挖孔与底部安全区，不重复加入状态栏顶部 inset
- 遮罩使用主题暗色约 `32%` 不透明度；抽屉保留边缘阴影
- 抽屉与遮罩同步滑入、滑出和淡入、淡出；禁止边缘打开、拖动和滑动关闭
- 不保留原 `PhoneLayout` 对右侧主内容施加的水平与垂直位移、`0.92` 缩放、`-7°` Y 轴旋转、`24dp` 动态圆角和 `18dp` 动态阴影
- 底层页面保持原尺寸、原坐标、正面朝向、圆角与不透明度，继续渲染但不接收触摸
- 不恢复原 `drawerProgress`、`contentTranslationX`、`contentTranslationY`、`contentScale`、`contentRotationY`、`contentCornerRadius` 或 `contentShadowElevation`
- 抽屉专属水玻璃、按钮玻璃、背景色和强调色偏好保持删除；模态抽屉直接使用共享 Operit 主题 token

Terminal 不是这次抽屉迁移的对象。第一阶段保留 AI 首页右上角 Terminal 按钮，不在抽屉、文件管理首页或开发者设置新增入口。未来若增加入口，所有入口必须指向同一 Terminal 页面、会话与持久状态。

### 系统栏所有权

Kiyori App Shell 统一拥有状态栏策略。软件首页、负一屏、五个根目的地和 AI 页面以 edge-to-edge 方式把当前表面背景绘制到手机物理顶边；页面工具栏和列表分别应用系统安全区。模态抽屉的遮罩仍覆盖物理顶边，但抽屉面板明确从状态栏底部开始。

状态栏显示时固定透明，并在 Android API 支持时关闭系统对比度遮罩。图标明暗由 Kiyori Shell 根据当前前景表面设置。继承的透明状态栏、自定义状态栏颜色 UI 与持久化不属于 Kiyori，只保留隐藏状态栏。

## 负一屏

负一屏首期参考旧 `HomeMinusOnePage.kt`、`HomeMinusOneSections.kt` 和 `HomeMinusOneData.kt`。基础数据入口固定包含：

- 收藏
- 书签
- 历史
- 下载

旧参考还包含新版、手册、版本、搜索、工具箱、清理、备份和退出等快捷工具。当前建议首期不展示这组快捷工具：其中多个入口尚无 Kiyori 页面或已迁入其他明确位置，提前展示会形成重复入口或无效按钮。后续只把已有稳定 owner 和真实页面的高频动作加入快捷区。

“收藏”与“书签”必须定义为不同数据域。当前建议“书签”专指浏览器 URL 书签，“收藏”作为视频、音乐、小说和其他内容对象的跨内容收藏入口；在收藏数据模型建立前不展示虚假计数。

## 设置所有权

| Kiyori 系统设置 | AI 设置 |
| --- | --- |
| 应用语言 | 模型服务商与凭据入口 |
| 产品级主题与显示 | 模型选择与功能路由 |
| 通知、隐私与共享权限 | 提示词、角色和用户偏好 |
| 存储与下载 | AI 记忆与上下文 |
| 浏览器、播放器、阅读器 | AI 工具权限 |
| 文件管理和其他内容域 | MCP、Skill、ToolPkg 与工作流 |
| 产品发行、帮助与关于 | 对话策略和 AI 用量信息 |

每项设置只允许一个持久化 owner。另一个页面只能通过导航进入 owner 页面，不能维护镜像开关或重复偏好。

权限中心横跨两列做状态汇总，但不改变这张 owner 表。其设备能力使用 Kiyori 系统安全设置的状态，AI 工具授权使用 AI 设置中的既有工具权限状态。

## 自适应布局

窗口适配基于可用窗口尺寸和折叠特征，不根据设备名称判断“手机”或“平板”。当前工程已经引入 Material 3 Window Size Class 与 AndroidX WindowManager，可以沿用现有依赖。

### Compact

- 底部五入口作为主要顶层导航
- 模态 AI 抽屉宽度为窗口宽度 `75%`
- AI 首页保持全屏对话

### Medium

- 根据有效宽度选择底部导航或 Navigation Rail
- 模态 AI 抽屉宽度为 `320dp`
- 软件首页中央内容限制最大宽度，避免横向拉伸

### Expanded

- 使用 Navigation Rail 或适合宽屏的永久顶层导航区域
- 模态 AI 抽屉宽度为 `360dp`，不把它固定在 AI 首页旁边
- 对话正文、搜索输入和设置表单使用可读的最大内容宽度

### Foldable

- 使用 `FoldingFeature` 判断分隔铰链和遮挡区域
- 模态 AI 抽屉的最大宽度限制在左侧物理区域，不跨越分隔铰链
- 单个搜索框、对话输入框、确认对话框和主要操作按钮不得跨越分隔铰链
- 折叠姿态变化只改变布局和抽屉宽度，不重置当前目的地、Pager 页、AI 会话或输入内容

## AI 能力合同

Operit AI 通过 Kiyori Capability API 操作产品能力，不能直接依赖页面实现。每个能力域至少定义：

- 可观察状态与稳定资源标识
- 明确命令及其输入输出
- 执行进度、完成、取消和错误语义
- 所需权限和用户确认级别
- 操作记录需要保存的事实
- Activity、Service、数据库或播放器的宿主适配器

浏览器、播放器、文件、下载、阅读器等领域分别拥有自己的合同。一个综合 AI 工具可以编排多个合同，但不越过各领域 owner 写入状态。

浏览器的 App Shell 页面和悬浮窗不是两个浏览器。它们只竞争 `APP_SHELL` 或 `OVERLAY` presentation owner，同一时刻由一个宿主挂载活动 WebView。标签、WebView、Cookie、历史、书签、下载和用户脚本始终只有一个领域 owner。切换 presentation 只能转挂 View，禁止调用 `loadUrl`、`reload`、`destroy` 或创建状态副本。

Browser Home 可见时，AI 浏览器工具直接操作当前共享标签，不依赖悬浮窗权限；Browser Home 不可见且 AI 需要 overlay 展示时，继续遵守 overlay 权限合同。关闭最后标签时，可见的 Browser Home 保留无标签页面；没有 App Shell owner 时才关闭无会话 overlay。

AI 操作采用四级风险模型：R0 只读、R1 低影响、R2 高影响、R3 关键操作。风险由具体命令、目标、范围、可逆性、数据敏感度和外部影响共同决定，不能按工具名称固定。`ALLOW` 只能免除 R0 与 R1 的逐次操作确认；R2 默认单次确认，只允许目标与范围固定的显式会话期授权；R3 每次确认，不允许会话期或持久免确认。

`FORBID`、`ASK`、`ALLOW` 是工具调用门，R0-R3 是操作副作用门。`ASK` 与 R2 或 R3 同时命中时合并为一次结构化确认。所有 R2、R3 以及产生需追溯持久变化的 R1 操作写入 AI 操作记录，失败、取消和部分完成记录实际结果。

## 浏览器 source-port

浏览器页面与状态处理以旧 `kiyori-android` 为首轮迁移源，采用可追踪的 source-port，而不是仅凭截图重画。至少覆盖：

- `BrowserActivity` 的启动、恢复、返回与外部 URL 入口
- `BrowserProcessLaunchPolicy` 与 `BrowserWindowRepository` 的窗口生命周期
- `BrowserScreen` 的页面层级、全屏覆盖物和底栏条件
- `BrowserTopBar` 与 `BrowserUrlDropdownOverlay` 的地址输入、搜索引擎和搜索记录
- 历史、书签、窗口预览、下载、网页权限、媒体识别和播放器交接

迁移时保留状态和交互语义，视觉层改用 Operit 原版主题与组件。包名、Activity 宿主、依赖和 Kiyori Capability API 需要按本仓库边界接入，不能把旧工程整体作为模块引用。

首期内核固定为 `android.webkit.WebView` 与设备 WebView provider，不引入旧项目的 X5/TBS。原因是当前 WebSession 与 AI 自动化都基于 Android WebView 类型和回调；并行接入 X5 会产生第二套内核、Cookie 与事件状态。旧项目的窗口、搜索、历史、权限与媒体行为继续作为 source-port 合同，内核实现不随页面一起复制。

首期源码边界为 `core/browser/navigation/`、`core/browser/presentation/` 和 `ui/features/browser/appshell/`。长期浏览器 owner 位于 `core/browser/`，Capability API 位于 `core/capability/browser/`，App Shell 与 overlay 位于 `ui/features/browser/`，AI 工具目录最终只保留 adapter 与兼容入口。

## 上游同步边界

- Operit AI runtime、协议和兼容标识尽量保持上游可识别
- Kiyori App Shell、模态 AI 抽屉、内容域页面和 Capability API 由 Kiyori 拥有
- 上游 UI 更新先判断属于 AI 内部页面还是 Operit 应用壳；应用壳变更不直接覆盖 Kiyori 导航
- Kiyori 通过窄宿主适配器连接 Operit AI，避免内容域状态进入上游页面模型

## 参考实现

旧项目 [kiyori-android@24a2dfa9](https://github.com/Kiyori-CN/kiyori-android/tree/24a2dfa91f0a4166dc58e5c4732d11861173f766) 仅作页面设计与行为参考：

- `app/src/main/java/com/android/kiyori/app/ui/HomeScreen.kt`
- `app/src/main/java/com/android/kiyori/app/ui/HomeMainPager.kt`
- `app/src/main/java/com/android/kiyori/app/ui/HomeNavigation.kt`
- `app/src/main/java/com/android/kiyori/app/ui/HomeLandingPage.kt`
- `app/src/main/java/com/android/kiyori/app/ui/HomeLandingSearch.kt`

可复用的是三页空间关系、AI 根页面稳定挂载、底栏随 Pager 隐藏、搜索与 AI 双按钮、负一屏数据结构和浏览器状态语义。包名、Activity 组织、旧页面的独立视觉样式和 Operit replica 宿主不是本仓库的目标架构。

## 待决策

- R2 与 R3 是否采用结构化单次确认合同，删除泛化的“始终允许”操作
- 预测性返回和输入法展开时的手势规则；浏览器首期 Back 顺序已确定为对话框、外部确认、sheet、地址编辑、网页历史、浏览器根页面
- 全屏网页搜索的建议来源与搜索记录展示
- 是否接受负一屏首期只展示四个基础数据入口，以及“收藏”为跨内容收藏、“书签”为网页书签的定义
- Terminal 是否在未来增加第二入口

这些问题进入 [产品壳 TODO](../../TODO/kiyori_product_shell/index.md)，达成共识后再更新本文。
