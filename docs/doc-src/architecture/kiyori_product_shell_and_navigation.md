---
status: accepted_design
implementation: partial
last_updated: 2026-07-29
---

# Kiyori 产品壳与导航架构

本文定义 Kiyori 的目标产品壳、首页层级、模态 AI 左抽屉、网页搜索、设置归属和自适应布局。首个 App Shell 切片已经替换 Operit 顶层导航壳；本文同时记录已落地边界与后续目标，不能把页面骨架视为领域功能完成。

## 当前实现状态

- Kiyori App Shell 已成为唯一顶层导航 owner，并持有五个根目的地、三页首页 Pager、底栏可见性与 Shell Back 状态
- AI 对话作为稳定宿主挂入右侧 AI 首页；按钮触发的模态 AI 左抽屉已经取代全屏 AI Center
- AI 一级根的显式身份、外部入口菜单/返回解析和全局透明状态栏已落地；用户可见的“AI 助手”设置由模态 AI 抽屉和设置首页进入同一页面，并按入口来源返回
- 旧手机抽屉、平板侧栏、边缘拖动、主内容透视变换、全局手势状态和抽屉专属主题设置已删除
- 全屏网页搜索已经接入共享 Browser Runtime；负一屏、文件管理和设置根页面按固定旧版提交完成静态复刻，小程序根页面仍是接线骨架
- 模态 AI 左抽屉、AI 一级路由替换和 AI 助手设置双来源返回已实现并通过自动验证；设置首页已经拆出账号、语音、界面和数据根页。全应用 `KiyoriSemanticTone` 已统一负一屏、AI 抽屉、浏览器内容抽屉、包管理、权限和工作流的图标与状态，工作流画布也已适配浅深主题；抽屉快捷入口状态徽标已实现且拥有独立布局区域，权限中心总览仍未完成，真机交互保持 `verification_pending`
- 自动检查不能证明真机手势、Back、旋转、折叠屏或流式对话持续性，以上保持 `verification_pending`

稳定术语以根目录 [CONTEXT.md](../../../CONTEXT.md) 为准。产品定位决策见 [Kiyori 产品定位与 Operit AI 边界](../decisions/0001_kiyori_product_positioning.md)，当前导航决策见 [模态 AI 左抽屉导航](../decisions/0004_modal_ai_drawer_navigation.md)，视觉规则见 [UI 设计来源层级](../decisions/0003_ui_design_source_hierarchy.md)。

## 目标包所有权与迁移状态

[Kiyori 项目架构与 Operit 命名重构方案 v3](../../TODO/kiyori_architecture_refactor/index.md)
已经接受双包根方向：

- Kiyori App Shell、首页、产品导航和启动装配长期迁入 `com.kiyori.app`
- Browser、Player、Files、Settings 等产品域迁入对应 `com.kiyori.feature`
- Operit AI screen registry、ToolPkg 动态页面和 AI 对话路由继续属于 `com.ai.assistance.operit`
- 两侧路由转换只通过 `com.kiyori.integration.operit.navigation`
- capability、platform 和 integration 不能拥有第二份 Shell、Browser、Player 或设置状态

当前源码尚未完成上述路径迁移。本节只声明所有权方向，不改变本文既有 Pager、Back、抽屉、
AI Home、window inset、presentation、主题或 UI 行为合同。历史文档中的旧路径继续记录当时
真实实现，不追溯改写。

## 设计来源层级

Kiyori 不建立与 AI 页面割裂的第二套视觉系统。来源层级固定为：

1. 当前已接受的 Kiyori 产品合同决定功能行为、页面归属和导航语义。
2. [AAswordman/Operit@ef00abc5](https://github.com/AAswordman/Operit/tree/ef00abc5099187b4665957e9697cb743c81fa154) 决定视觉语言，包括主题 token 的使用方式、排版、形状、图标处理、动效、弹窗、设置行、液态玻璃与水波玻璃组件；默认 token 值由 [专业浏览器灰白默认主题](../decisions/0007_professional_browser_theme.md) 决定。
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
│   │   ├── 左上定位天气 -> 当前城市天气网页
│   │   ├── 右上真实窗口计数 -> Browser Host 原窗口总览
│   │   └── 同背景渐变描边搜索框/
│   │       ├── Search 模式 + 主框点击 -> 全屏网页搜索页
│   │       ├── AI 模式 + 主框点击 -> 保留会话与草稿的 AI 首页
│   │       └── 附件/语音/相机 -> AI 首页原输入能力
│   └── AI 首页
│       └── 模态 AI 左抽屉 -> AI 一级页面
├── 浏览器首页/
│   ├── 浏览器地址栏
│   ├── 共享活动 WebView
│   ├── 浏览器专属底栏
│   ├── 全屏标签总览
│   └── 浏览器底部抽屉
├── 小程序首页/
├── 文件管理首页/（固定布局，实际存储容量，其余空动作）
└── 设置首页/（固定布局，账号 / AI / 语音 / 浏览器 / 播放器 / 下载 / 界面 / 数据已接入）
```

AI 助手设置由模态 AI 抽屉或设置首页进入同一页面与持久状态。从抽屉进入时作为 AI 一级页面返回 AI 首页；从设置首页进入时显示返回语义并回到设置首页。跨来源进入时从 AI 助手根页开始，不恢复另一来源的深层子页。

## 顶层状态模型

产品壳至少区分两组正交状态：

- `PrimaryDestination`：软件首页、浏览器首页、小程序首页、文件管理首页、设置首页
- `SoftwareHomePage`：负一屏、软件首页、AI 首页

`SoftwareHomePage` 只在 `PrimaryDestination` 为软件首页时有效。AI 首页不是第六个底部入口，负一屏也不是独立顶层目的地。

全屏网页搜索页和 AI 一级页面属于明确的页面路由，不加入首页 Pager。书签与历史由 App Shell
挂载共享下拉抽屉，不进入首页 Pager，也不创建第二页面 owner。

## 启动与页面可见性

- 正常启动进入软件首页的中间页
- 软件首页位于 Pager 中央，负一屏在左，AI 首页在右
- 软件首页显示底部五入口
- 负一屏隐藏底部五入口
- AI 首页隐藏底部五入口并保持现有全屏对话形态
- 首次进入正式内容前，如果持久化当前对话为空或对应数据库记录已不存在，则由唯一
  `ChatHistoryManager` 创建并选中一个真实空白对话；已有有效对话时仅由
  `startWithNewChat` 偏好决定是否在本次启动另建空白对话
- 浏览器首页隐藏 Kiyori 底部五入口，改为后退、前进、主页、标签页和工具箱组成的浏览器专属底栏
- 小程序首页、文件管理首页和设置首页显示 Kiyori 底部五入口
- 全屏网页搜索页、子页面和播放器、阅读器、网页内容等沉浸页面隐藏底部五入口
- AI 首页应保持稳定挂载，左右切换不能销毁正在进行的对话、输入草稿或流式状态
- 模态 AI 抽屉覆盖当前 AI 页面；AI Home 在抽屉开关和一级页面切换期间保持持续组合与同一个会话状态
- 三页首页和 AI Home 覆盖层共享同一个 `PagerState` 与 fling 行为。页面位移直接跟随手指，新反向拖动可取消尚未结束的 fling；`SoftwareHomePage` 只在 `settledPage` 后同步

底栏的显示与隐藏通过产品壳的页面状态驱动。Browser Home 直接占满 presentation 约束，不为已隐藏的 Kiyori 底栏保留高度；切换动画不得通过增删内容高度造成首页主体跳动。

底部五入口未选中时继续使用 Legacy Kiyori 空心 Vector 和中性 `onSurfaceVariant`。选中时不增加
胶囊、圆形容器或蓝色描边，而是绘制精确 `#FFC153` 的封闭填充层，再以当前页面背景色重绘内部
细节。Home、Browser、Mini App、Files 与 Settings 按各自可见边界设置终点缩放，最终视觉尺寸与
原空心图标一致；Settings 的背景色细节层只包含内部圆环，不重绘外缘。每次点击先用 `70ms` 连续
压回较小填充态，再以低阻尼弹簧放大到终点；重复点击当前目的地也递增激活序号并完整重播。Home、
Browser、Mini App 与 Files 使用 `0.42` 阻尼扩大黄色填充的动画峰值，Settings 保持 `0.55`；
五项落定尺寸、点击热区、导航状态与页面切换合同不变。

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
- Browser Home 依次处理网页对话框、外部打开确认、标签总览、浏览器抽屉、地址编辑和网页历史；以上均不能消费时返回软件首页。浏览器底栏的后退只操作网页历史
- 非软件首页的顶层根页面返回软件首页中间页
- 软件首页中间页再次 Back 时显示退出确认
- 切换底部入口不会清空其他根页面的子栈和滚动状态

输入法展开时的横滑条件与预测性返回动画仍需后续设备级设计和验收；浏览器 Back 优先级已由 Browser Host 状态机固定。

## 全屏网页搜索

软件首页搜索框与页面使用相同背景，常规高度为 `114dp`、短布局为 `96dp`，只由约 `1dp` 的彩色渐变描边定义边界，不绘制外侧扩散、模糊或阴影。常规窗口先测量标题与搜索框，再把搜索框顶部描边放在可用首页高度的 `38.2%`；短窗口将顶部位置限制在标题完整可见所需的最小位置。Compact、Medium 与 Expanded 的内容宽度上限分别为 `544dp`、`584dp` 和 `624dp`，最小水平留白分别为 `24dp`、`48dp` 和 `72dp`。标题圆环为 `18dp`。底部“搜索 / AI”位于同一个 `120×32dp` 圆角分段框内，左右严格等宽并由中央细线分隔，文字与选项图标保持原尺寸；选中项使用固定明暗蓝色的图标、文字和轻量圆角底，按压水波裁切在各自圆角选区内。分段不是立即导航按钮，只决定主框提示和点击目标。搜索模式显示“搜索网页或输入网址”，主框点击后打开全屏网页搜索页；AI 模式显示“把问题和任务发给AI”，主框点击后进入现有 AI Home，保留其对话和草稿，并在 Pager settled 后让真实输入框获得焦点和显示输入法。模式通过可保存 Compose 状态在返回首页和旋转后保持，冷启动默认搜索。右侧附件、语音和相机仍为 AI Home 一次性动作，同时把首页模式切为 AI，只有 Pager settled 后才交给现有附件状态、麦克风权限 launcher 或相机 launcher 消费。全屏网页搜索页只接受网址或网页搜索词，并把结果交给 Kiyori 浏览器。

明确排除：

- 书签搜索
- 浏览历史搜索
- 文件搜索
- 小程序搜索
- AI 提问与 AI 会话搜索

历史抽屉和书签抽屉各自拥有搜索框、筛选状态和结果列表。它们可以复用文本输入组件，但不能与
全屏网页搜索共享业务结果模型。

旧 `kiyori-android` 的 `HomeLandingSearch.kt` 只作为交互结构参考。当前 Search/AI 是同一个搜索框内的两种入口语义，不共享结果页：选项只切换模式，主框再按模式进入网页搜索或 AI 首页；两项在同一个圆角边框内严格等宽，不分别绘制外部按钮轮廓。

软件首页或天气提交不会覆盖正在浏览的活动窗口，由 `BrowserPresentationCoordinator.openUrlInNewSession` 在唯一 Browser Runtime 中创建并激活新 WebSession，随后进入 Browser Home。软件首页和浏览器顶栏共用强制显示的浏览 Profile 控件，睁眼表示普通、闭眼表示无痕；控件不绘制边框、底色或阴影，切换后只显示短时 `inverseSurface/inverseOnSurface` 反馈，随亮暗主题保持反相高对比。浏览器顶栏搜索在所选 Profile 与活动窗口一致时继续当前窗口，不一致时创建对应 Profile 的新 WebSession。全屏搜索首行直接复用 Browser Home 的 `8dp` 横纵边距、`6dp` 三槽间距、`40dp` 两侧动作区和搜索框宽度；输入框单行基准高 `42dp`，保持顶部与宽度不变，在一至三行内自动换行并平滑向下增高，超过三行后只纵向滚动。全屏搜索框只显示选中引擎图标；左右动作和引擎按钮始终沿输入框垂直中心同步移动。引擎面板覆盖搜索页内容，当前网页区显示标题和网址并提供复制/编辑动作，搜索历史以 `FlowRow` 标签展示。垃圾桶进入编辑模式后，标签叉号只暂存单条删除并由“完成”提交；“清空”显示底部确认框，确认后直接清空共享历史并退出编辑模式。普通搜索记录写入共享 `WebSessionHistoryStore`，无痕搜索与网页访问、标题更新均不写入共享历史；AI `browser_tabs list` 可立即发现两类窗口。

全屏搜索页主体继续遵循浏览器下拉抽屉菜单的紧凑基准。复制/编辑图标为 `16dp`，并向标签文字轻微
靠近；历史垃圾桶为 `26dp`，标题行固定为 `34dp`，切换垃圾桶与“清空 / 完成”时标题不发生垂直位移。
历史标题、空状态和编辑动作分别为 `18sp`、`14sp` 和 `15sp`，历史标签保持 `12sp`。
引擎面板的透明后置点击层负责周围区域收起，面板本身仍覆盖内容而不改变下方布局。当前网页信息区只
关闭搜索页并返回已挂载的活动 WebView，不调用 `loadUrl` 或 `reload`。

Browser Home 右侧动作固定为刷新：加载期间仍显示刷新图标，点击始终重新加载活动 WebView，不复用停止
加载语义。返回、刷新和全屏搜索 Profile 动作共用圆形裁剪的按压反馈，不绘制正方形水波区域。

Browser Home 在文本搜索提交后保存最后一次 query 的 presentation 状态，并在顶栏下方显示可横向滚动
的九引擎切换条。点击其他引擎会更新 `WebSessionHistoryStore` 的当前引擎，并在活动 Profile 中用同一
query 重新导航；网址提交或右侧关闭动作隐藏切换条。浏览器菜单第 4 行使用 `2:1:2` 三槽权重，使两侧
按钮向上方五列之间的中心线内收。

`WebSessionBrowserSettingsStore.allowWebPageOpenApp` 是网页外部应用导航的唯一授权 owner。运行时不再
创建一次性外部打开请求或 Browser Home/indicator 提示；只有设置开启、主框架且带明确用户手势时才直接
执行外部 Intent，网页自动触发的外部 scheme 会被消费。

同一时间存活的无痕窗口共享一个唯一命名的 AndroidX Profile 代际。关闭最后一个无痕窗口后，Browser Runtime 销毁 WebView、清理 Cookie、WebStorage 和定位授权，并立即退休代际；下一段无痕会话创建全新代际。AndroidX 禁止在同一进程删除已加载 Profile，因此启动清理会在下一次冷启动、任何 Kiyori 无痕 Profile 加载前物理删除全部退休代际。窗口总览的缩略图固定为 `320×512`，使用两个方向的最小缩放值居中绘制完整当前 WebView 视口；UI 以 `5:8` 纵向比例和 `ContentScale.Fit` 显示，不裁切网页。

首页右上角窗口按钮的数量由 `StandardBrowserSessionTools.syncProjectedBrowserStateOnMain` 同步发布。首页与浏览器底栏第四项共同使用 `WebSessionBrowserWindowCountIcon` 的 `23×21dp` 方框数字视觉；点击首页按钮后先在同一个 `WebSessionBrowserHost` 上设置 `WebSessionBrowserSheetRoute.TABS`，再进入 Browser Home，由 App presentation 获取流程创建或连接活动 session。该入口不先挂一次后台 anchor，因此两处进入完全相同的总览页面且不制造无意义的跨窗口往返。

首页左上角天气只在已有定位授权时自动刷新；未授权时点击请求 Android 定位权限。`LocationManager.NETWORK_PROVIDER` 提供城市级定位，`Geocoder` 的 `locality` 是唯一城市字段；定位、城市解析、网络或响应失败都会显示明确的不可用状态，不展示猜测值。Open-Meteo 当前天气接口接收经纬度并返回摄氏温度与 WMO code，前台每 30 分钟最多刷新一次。晴天图标独立于底栏选中色：浅色主题使用 `#C57C00`，深色主题使用 `#FFD166`；局部云和雨使用蓝色，阴天、雾和雪使用青色，雷暴使用紫色。权限缺失使用红色，数据不可用使用橙色。免费接口仅限非商业用途并受服务限制，坐标会发送给该第三方；参见 <https://open-meteo.com/en/terms>。

## AI 首页与模态 AI 左抽屉

AI 首页只承担对话主界面，不再承担 Operit 应用壳。即使当前没有可用模型配置或 API Key，AI 首页仍显示对话内容区和输入控件；模型配置由独立设置页面承载，缺少凭据时只在实际需要模型的操作中报告。启动宿主直接读取聊天仓库的持久化当前对话 ID，核对对应数据库记录，并在 ID 为空或记录缺失时创建且选中一个真实空白对话。不能读取惰性 `StateFlow` 的初始 `null` 作为持久化事实，否则会让已有安装在每次进程启动时错误新建会话。已有有效当前对话时保持原会话，只有用户明确开启“每次启动新建空白聊天”才创建新的当前会话。AI 首页与 AI 一级页面的左上角三横线按钮打开模态 AI 左抽屉；深层页面显示返回箭头。浏览器、小程序、文件管理和 Kiyori 设置不显示 AI 抽屉按钮。

`ChatHeader` 和 `ChatHistorySelectorPanel` 继续拥有对话历史、搜索、新建、切换和删除对话。抽屉中的“AI 对话”只返回现有 AI Home，不创建会话、不清空草稿，也不复制会话操作或最近对话列表。

AI 首页右上角 App Shell 动作从左到右为 Browser、Terminal、Workspace，分别使用
`KiyoriSemanticTone.BLUE / CYAN / PURPLE`。Browser 是进入共享 Browser Home 的一次性动作；
Terminal 与 Workspace 是切换动作，活动时只增加各自低饱和容器，图标颜色保持稳定。

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

帮助、关于、使用手册和应用语言不进入抽屉；其中帮助、关于和使用手册由 Kiyori 产品页面承接，应用语言由 Kiyori 系统设置承接。“设置”改为 AI 助手。包管理、权限授予、工作流、助手配置、记忆库和工具箱保留为 AI 能力入口，Terminal 仍只位于 AI 首页右上角。包管理继续属于 AI 抽屉，不进入设置首页为底部小程序产品域保留的“小程序”空入口。

源码对照表明，原抽屉的“权限”入口是 `Screen.ShizukuCommands`，而 `Screen.ToolPermission` 是独立的 AI 工具调用策略页面。两者不能继续共用“权限”这一含混名称：

- 设备能力授权：Android 运行时权限、文件访问、Shizuku、无障碍、悬浮窗、电池优化豁免、Root 和调试能力，由 Kiyori 系统安全设置持有状态
- AI 工具授权：`ALLOW`、`ASK`、`FORBID` 及单工具例外，由 AI 助手设置持有状态

抽屉“权限”快捷卡直接进入 `Screen.ShizukuCommands`。AI 助手设置中的 AI 工具授权进入 `Screen.ToolPermission` 并继续由 `ToolPermissionSystem` 持有状态。权限中心仍是系统设置目标，但当前设置首页没有导航到它；这些领域不复制或混合持久状态。

### 权限中心合同

权限中心的目标仍是系统级状态汇总与导航页，不是第三套权限存储；当前静态设置首页尚未接入该页面。抽屉“权限”高频卡片继续按已确认合同直接进入 `Screen.ShizukuCommands`。

当前源码和已接受的安全合同涉及四类能力。前两类已有实现，后两类仍处于设计阶段：

| 领域 | 当前来源 | 权限中心职责 | 唯一 owner |
| --- | --- | --- | --- |
| 设备能力 | `Screen.ShizukuCommands`、`ShizukuDemoScreen` 及设备权限状态 | 汇总 Kiyori 自身运行所需能力并进入分项页面 | Kiyori 系统安全设置 |
| AI 工具授权 | `Screen.ToolPermission`、`ToolPermissionSettingsScreen`、`ToolPermissionSystem` | 展示全局策略与例外摘要并进入原设置页 | AI 助手设置中的 `ToolPermissionSystem` |
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
- AI 助手设置从抽屉进入时属于 AI 一级页面，根页面 Back 返回 AI 首页；从 Kiyori 设置首页进入时使用 `RouteEntrySource.KIYORI_SETTINGS`，显示返回语义并返回设置首页
- AI 助手设置只有一份页面、表单和持久状态；同来源族可恢复自己的子页面栈，跨来源族进入时从 AI 助手根页开始
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
└── 固定底部入口：AI 助手
```

“关于”和“使用手册”迁入 Kiyori 产品页面；原“设置”入口改为 AI 助手。“AI 对话”返回现有 AI Home，不创建会话或清空草稿。

### 视觉与组件合同

- 沿用 Operit 对语义主题 token 的使用方式、字体层级、图标、选中态、状态徽标、分隔线和交互密度；默认 token 值遵守 [专业浏览器灰白默认主题](../decisions/0007_professional_browser_theme.md)
- 模块状态区显示“Operit AI”，不再由 `softwareIdentity` 决定 Kiyori 应用品牌
- 保留包数量、权限状态、工作流数量和动态插件入口，不把可操作状态退化为静态按钮
- 包管理、权限和工作流快捷卡为右上角数量/状态徽标保留独立顶部区域，图标与标签位于其下方；窄屏下“正常”、未授权和未运行等文本不得覆盖图标
- 包管理保留宿主顶栏标题，右侧动作严格按“环境变量 / 市场 / 添加 / 刷新”排列；搜索框与“插件 / 脚本包 / 技能 / MCP”页签分别固定在顶栏下方，只有内容列表滚动。插件和脚本包不再放置右下角浮动按钮或 `120dp` 遮挡预留，包加载错误通过页签下方紧凑提示条进入现有详情。首次进入、手动刷新、市场成功安装脚本或 ToolPkg、删除包和删除冲突源共用同一个完整 `PackageManagerSnapshot` 重载入口
- 环境变量顶栏入口使用共享三态底部抽屉：标题、搜索和 `ToolPackage.category` 动态类型条固定在上方，类型按英文名称 A 到 Z 排列，工具包按英文或中文拼音首字母稳定排序。脚本包页与环境变量抽屉共用 17 类独立浅深配色和语义图标；环境变量包头使用 `32dp` 类型徽标与紧凑两行文字，变量输入区高 `42dp`。每次打开时，初始状态展开所有存在未填写必填变量的工具包，必填项完整或只有可选变量的工具包默认收起；进入后仍可独立切换分组。取消与保存固定在底部；搜索不读取变量值，保存继续只写入 `EnvPreferences`
- 使用原版纵向滚动层级；AI 助手固定在底部安全区上方，不随长列表消失
- `<600dp` 为窗口宽度 `75%`，`600-839dp` 为 `320dp`，`>=840dp` 为 `360dp`；分隔铰链把最大宽度限制在左侧物理区域
- 抽屉遮罩覆盖窗口全高；面板从状态栏底部开始，左侧两角贴屏，右侧两角为 `16dp`
- 面板内容只处理水平、挖孔与底部安全区，不重复加入状态栏顶部 inset
- 遮罩使用主题暗色约 `32%` 不透明度；抽屉保留边缘阴影
- 抽屉与遮罩同步滑入、滑出和淡入、淡出；禁止边缘打开、拖动和滑动关闭
- 不保留原 `PhoneLayout` 对右侧主内容施加的水平与垂直位移、`0.92` 缩放、`-7°` Y 轴旋转、`24dp` 动态圆角和 `18dp` 动态阴影
- 底层页面保持原尺寸、原坐标、正面朝向、圆角与不透明度，继续渲染但不接收触摸
- 不恢复原 `drawerProgress`、`contentTranslationX`、`contentTranslationY`、`contentScale`、`contentRotationY`、`contentCornerRadius` 或 `contentShadowElevation`
- 抽屉专属水玻璃、按钮玻璃、背景色和强调色偏好保持删除；模态抽屉直接使用共享 `OperitTheme`，其默认 token 值遵守 [专业浏览器灰白默认主题](../decisions/0007_professional_browser_theme.md)

Terminal 不是这次抽屉迁移的对象。第一阶段保留 AI 首页右上角 Terminal 按钮，不在抽屉、文件管理首页或开发者设置新增入口。未来若增加入口，所有入口必须指向同一 Terminal 页面、会话与持久状态。

### 系统栏所有权

Kiyori App Shell 统一拥有状态栏策略。软件首页、负一屏、五个根目的地和 AI 页面以 edge-to-edge 方式把当前表面背景绘制到手机物理顶边；页面工具栏和列表分别应用系统安全区。模态抽屉的遮罩仍覆盖物理顶边，但抽屉面板明确从状态栏底部开始。

状态栏显示时固定透明，并在 Android API 支持时关闭系统对比度遮罩。图标明暗由 Kiyori Shell 根据当前前景表面设置。继承的透明状态栏、自定义状态栏颜色 UI 与持久化不属于 Kiyori，只保留隐藏状态栏。

## 负一屏

负一屏按固定提交中的 `HomeMinusOnePage.kt`、`HomeMinusOneSections.kt` 和 `HomeMinusOneData.kt` 静态复刻。基础数据卡固定包含：

- 收藏
- 书签
- 历史
- 下载

四张数据卡的计数当前固定为 `0`。快捷工具完整保留新版、手册、版本、搜索、工具箱、清理、备份和退出，所有数据卡、快捷工具和顶栏关闭按钮均为空动作，不读取浏览器或文件状态。

“收藏”与“书签”未来仍必须定义为不同数据域；在 owner 和数据模型建立前，静态页不会展示虚假动态计数。

## 文件管理与设置首页合同

文件管理首页按固定旧版提交保留搜索顶栏、八个文件分类、七个快捷访问和四个存储位置。分类计数固定为 `0项`；手机存储使用应用实际所在数据卷的 `StatFs.availableBytes` 与 `totalBytes`，在首次组合和宿主恢复前台时刷新，其余按钮为空动作。分类图标到标题为 `5dp`，标题与计数使用明确行高且不再加入额外间隔，网格行距为 `10dp`。

设置首页保留四个旧版 PNG 顶栏图标、四张 `16dp` 圆角卡片和 `4/4/4/4` 共 16 个入口的信息结构。页面、卡片、文字、分隔线、开关、禁用态和底部选择面板由 `KiyoriSettingsTheme` 统一适配浅色与深色；首页 16 个入口由设计层 `KiyoriSettingsHomeIconPalette` 分别提供独立的图标前景与低饱和容器色，不使用随机颜色或大面积高饱和背景。第一张卡固定为“账号连接 / AI助手 / 语音服务 / 小程序”，最后一张卡固定为“界面定制 / 数据备份 / 开发手册 / 更多功能”；16 个图标也必须互不重复。前三项进入现有真实设置根，“小程序”保持空动作，等待底部第三个小程序产品域建立自己的管理页，禁止连接 AI 包管理、脚本包、ToolPkg 或插件市场。网页浏览器、视频播放器、文件下载器、界面定制和数据备份也进入各自唯一 owner；其余入口和顶栏动作保持为空。

## 设置所有权

| Kiyori 系统设置 | AI 助手设置 |
| --- | --- |
| 应用语言 | 模型服务商与凭据入口 |
| 产品级主题与显示 | 模型选择与功能路由 |
| 通知、隐私与共享权限 | 提示词、角色和用户偏好 |
| 存储与下载 | AI 记忆与上下文 |
| 浏览器、播放器、阅读器 | AI 工具权限 |
| 文件管理和其他内容域 | 对话策略和 AI 用量信息 |
| 产品发行、帮助与关于 | 外部 HTTP 对话配置 |

每项设置只允许一个持久化 owner。另一个页面只能通过导航进入 owner 页面，不能维护镜像开关或重复偏好。

当前设置首页只把现有设置能力按 owner 重新分组，不复制持久化状态。账号使用 `GitHubAuthPreferences`，语音使用现有语音偏好和 runtime，界面与数据根仅导航到原 owner；普通网站 Cookie 清理迁入浏览器设置并继续调用 `CookiePrivacyManager`。应用主题只由浅色、深色和跟随系统模式拥有；AI 背景、气泡、头像、聊天头部、输入区与局部字体只影响 AI 对话。小程序管理不建立页面、状态或 AI 路由。

权限中心横跨两列做状态汇总，但不改变这张 owner 表。其设备能力使用 Kiyori 系统安全设置的状态，AI 工具授权使用 AI 助手设置中的既有工具权限状态。MCP、Skill、ToolPkg、包管理和工作流仍是独立 AI 子系统目的地，不属于小程序管理。

## 自适应布局

窗口适配基于可用窗口尺寸和折叠特征，不根据设备名称判断“手机”或“平板”。当前工程已经引入 Material 3 Window Size Class 与 AndroidX WindowManager，可以沿用现有依赖。

### Compact

- 底部五入口作为主要顶层导航
- 模态 AI 抽屉宽度为窗口宽度 `75%`
- AI 首页保持全屏对话

### Medium

- 根据有效宽度选择底部导航或 Navigation Rail
- 模态 AI 抽屉宽度为 `320dp`
- 软件首页保持标题在上、搜索框在下的居中单列，中央内容最大宽度为 `584dp`，避免横向拉伸

### Expanded

- 使用 Navigation Rail 或适合宽屏的永久顶层导航区域
- 软件首页仍保持同一居中单列，不切换为品牌/搜索左右双栏，中央内容最大宽度为 `624dp`
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

浏览器只有一个完整可见宿主：`MainActivity` 中的 App Shell Browser Home。活动 WebView 只在 `APP_SHELL` 与 `BACKGROUND_ANCHOR` presentation owner 之间转挂；后者是系统层 1×1、不可见、不可触摸、不可聚焦的后台 attach 点，不组合浏览器 chrome、抽屉、文本选择操作条或播放器。标签、WebView、Cookie、历史、书签、下载和用户脚本始终只有一个领域 owner。Browser Home 持有一次性 App presentation lease，显式退出后 Compose disposal 的重复 release 不再执行。重复请求当前 owner 直接保持原挂载；真实跨 ViewRoot 切换必须从旧 parent 移除 WebView、移除旧 background window、确认 parent 为空，等待一个 `Choreographer` 渲染帧并确认旧 anchor ViewRoot 已脱离后，才把同一 View 挂入目标 host。Browser Home 活跃期间不保留空 anchor 窗口。整个事务禁止调用 `loadUrl`、`reload`、`destroy` 或创建状态副本。

Browser Home 可见时，AI 浏览器工具直接操作当前共享标签，不依赖悬浮窗权限。Browser Home 不可见时，AI 仍操作同一 session 和 WebView：已有 overlay 权限时挂到后台 anchor；缺少权限时返回明确权限错误，不创建 headless WebView、第二个 session 或覆盖人工页面。最小 indicator 点击后通过显式 action 打开现有 Browser Home。关闭最后标签时，可见的 Browser Home 保留无标签页面；后台 anchor 和 indicator 不拥有会话状态。

Browser Home 是沉浸式根页面。其浏览器专属底栏、全屏标签总览和底部抽屉只观察共享 host projection 并调用现有 runtime 命令。打开或关闭这些覆盖层不能释放 presentation lease、重建 AndroidView、复制标签状态或触发网页导航。详细方案见 [浏览器沉浸式 UI 重构](../../TODO/kiyori_browser_ui_refactor/index.md)。

播放器同样只有一个 `PlayerSession` 和一个 mpv core。floating 与 fullscreen `PlayerSurfaceView` 只是 Surface lease owner：每个 owner 使用明确 role、实例 token 和单调 generation，状态机同时记录 current/pending owner、native detach 是否完成、transfer target 以及一次性 Activity launch/finish request。新 owner 可以先登记为 pending，但旧 owner 未通过 `surfaceDestroyed` 完成 native detach 前不能 attach。Surface 转挂不重新执行 `loadfile`，不改变 request、URL、headers、位置、暂停、速度、轨道、字幕、Anime4K 或 `loadGeneration`。

书签管理同样属于这一个 Browser Runtime。`WebSessionHistoryStore` 在原有 `bookmarks_json` 上兼容增加稳定 ID、图标、文件夹、手动顺序和秘密空间字段，并在同一 DataStore 的 `bookmark_folders_json` 中保存目录树。浏览器菜单的加书签图标固定不变，未收藏页面先打开四字段编辑弹窗，已收藏页面只切换文字并移除普通空间中的当前网址；秘密空间状态不暴露给普通菜单。书签文件夹选择以 `/` 为首行，随后按同级手动顺序深度优先展开，只显示节点名称并按深度缩进。书签子抽屉继续使用下载抽屉的三态 viewport owner；搜索、路径、排序、长按菜单和秘密空间只是同一持久化状态的展示与 mutation 入口。两种抽屉的锚定菜单统一使用 `40dp` 选项、触发点定位和抽屉内遮罩，居中弹窗统一使用全窗口遮罩。负一屏书签卡观察普通空间书签数并挂载同一个书签抽屉。当前标签打开、后台新标签和前台新标签均调用现有 WebSession registry，禁止创建第二个浏览器或书签仓库。

浏览历史也由同一个 `WebSessionHistoryStore` 持有。普通 Profile 的主框架访问写入 `WEB`，
`PlayerSession` 接受媒体 request 时写入 `VIDEO` 并标记 `ONLINE` 或 `LOCAL`；媒体写入会移除同 URL
的网页重复项。历史记录不保存 Cookie、Authorization 或 request headers。在线重播只在用户点击时，
从活动 WebSession 读取当前 User-Agent、对应 Profile Cookie，并把记录的来源页作为 Referer；本地重播
继续使用播放器现有同目录队列解析。浏览器菜单和负一屏挂载同一个三态历史抽屉，统一提供搜索、
`全部 / 网页 / 视频 / 音乐 / 小说 / 其他` 筛选和按当前分类执行的一小时、24 小时、一周或所有时间删除。

网络日志也属于单个 WebSession，而不是跨窗口持久化诊断库。现有 Android WebView `shouldInterceptRequest` 只记录当前请求能够确认的 method、URL、主框架标记、请求头与时间，并把最多 500 条内存记录投影给 App Shell Browser Home 与 AI 共用的 host；1×1 background anchor 不组合日志 UI。页面导航与用户清空只清该 session；搜索、六类筛选、第三方 host 提示和操作弹窗属于 presentation 状态。列表禁止加载远端缩略图，避免观察行为污染日志。复制和外部打开使用已记录 HTTP/HTTPS URL，下载复用 `BrowserDownloadManager`、当前默认 engine 与活动 Profile 请求身份。Android WebView 没有提供的响应状态、响应 MIME、拦截和播放器状态不得从 URL 猜测或从旧版 X5/hikerView 复制。

AI 操作采用四级风险模型：R0 只读、R1 低影响、R2 高影响、R3 关键操作。风险由具体命令、目标、范围、可逆性、数据敏感度和外部影响共同决定，不能按工具名称固定。`ALLOW` 只能免除 R0 与 R1 的逐次操作确认；R2 默认单次确认，只允许目标与范围固定的显式会话期授权；R3 每次确认，不允许会话期或持久免确认。

`FORBID`、`ASK`、`ALLOW` 是工具调用门，R0-R3 是操作副作用门。`ASK` 与 R2 或 R3 同时命中时合并为一次结构化确认。所有 R2、R3 以及产生需追溯持久变化的 R1 操作写入 AI 操作记录，失败、取消和部分完成记录实际结果。

## 浏览器 source-port

浏览器页面与状态处理以旧 `kiyori-android` 为首轮迁移源，采用可追踪的 source-port，而不是仅凭截图重画。至少覆盖：

- `BrowserActivity` 的启动、恢复、返回与外部 URL 入口
- `BrowserProcessLaunchPolicy` 与 `BrowserWindowRepository` 的窗口生命周期
- `BrowserScreen` 的页面层级、全屏覆盖物和底栏条件
- `BrowserTopBar` 与 `BrowserUrlDropdownOverlay` 的地址输入、搜索引擎和搜索记录
- 历史、书签、窗口预览、下载、网页权限、媒体识别和播放器交接

迁移时保留状态和交互语义，组件、形状、排版、图标与动效改用 Operit 原版视觉语言，默认色板遵守 [专业浏览器灰白默认主题](../decisions/0007_professional_browser_theme.md)。包名、Activity 宿主、依赖和 Kiyori Capability API 需要按本仓库边界接入，不能把旧工程整体作为模块引用。

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

可复用的是三页空间关系、AI 根页面稳定挂载、底栏随 Pager 隐藏、搜索与 AI 双按钮、浏览器状态语义，以及负一屏、文件管理和设置首页的固定视觉布局。包名、Activity 组织和 Operit replica 宿主不是本仓库的目标架构。

## Kiyori 首次启动合同

首次启动是 Kiyori 产品壳的独立门禁，不复用旧 Operit 的 PermissionGuide 页面或启动后
通知权限回调。`KiyoriMainStartupGateCoordinator` 只投影三种目的地：
`ONBOARDING`、协议重新确认用的 `AGREEMENT` 和正式内容用的 `CONTENT`。

`com.kiyori.integration.operit.onboarding` 负责首启状态机、首启偏好、系统权限事实读取、
运行时权限集中 launcher 和特殊访问动作；协议正文与阅读页位于 Operit UI 的协议路由中，
因为当前 Gradle namespace 仍由 Operit 兼容入口提供。`2026-08-10-r8` 保持用户协议与隐私政策
为两个独立文档；首启接受条件只由一个明确同意复选框控制，不要求先打开文档或滚动到末尾。
设置内入口只提供两份文档的只读选择与阅读，不显示首启确认动作。
该集成不拥有 AI 模型凭据、聊天状态、浏览器状态或第二份权限事实。

顶部把步骤文案放在进度条下方并限制为单行。前四页共享紧凑插画、标题槽、介绍槽和
2×2 能力卡片的版式基线，标题下方正文使用两个中文字符宽度的首行缩进。第一页删除眉题后
将“欢迎使用 Kiyori”单独居中，并把等量纵向节奏转移到介绍与能力网格之间；带眉题的后三页
标题继续左对齐，四页能力网格基线保持一致。四页分别表达
AI 浏览器总览、浏览与内容工作台、AI 与连接服务、本地文件/终端/小程序工作区。
产品文案覆盖网页浏览器、文件下载器、视频播放器、音乐播放器、小说阅读器、广告拦截器、
AI 助手、语音服务、账号与连接、工具箱、文件管理器、终端、小程序管理和日志记录器；对当前
只有产品域入口的模块不宣称已经拥有完整详情页。当前逐页精修只维护中文默认资源，其余语言在
六个中文页面全部定稿后统一同步。

授权页使用一个不分组的统一清单显示真实状态：

- Android runtime 权限通过唯一 launcher 一次请求用户选中的当前 SDK 适用集合；
- 所有文件、悬浮窗、系统设置、使用情况、未知来源安装、精确闹钟、电池优化、通知读取和默认
  助手逐项进入对应系统设置；
- Kiyori 无障碍支持、Shizuku 和 Root 复用既有真实 owner。屏幕捕获只在实际使用时由 Android
  确认，因此在清单中标记为 `ON_DEMAND`，不能伪造为已预授权。

拒绝状态永远重新读取 Android、系统设置、Shizuku、Root 或提供者事实，不写成成功。用户
可以明确选择保留未授权项并进入 Kiyori；对应功能保持未授权状态。模型服务和 API Key 不再
作为首启硬门禁，使用前由用户在相应 AI 设置中配置。

内置 `accessibility.apk` 的用户可见应用名、服务名、说明、主题名和启动图标使用 Kiyori 品牌，
当前版本为 `1.7`。包名 `com.ai.assistance.operit.provider`、服务类、authority 和
`com.ai.assistance.operit.provider.IAccessibilityProvider` action 保持不变，因为这些是主应用
与独立支持应用之间的 IPC 合同。ARCH045 同时锁定支持 APK 的版本标记、SHA-256、Kiyori 文案、
品牌图标入口和旧 `Accessibility Operit Support` 文案的缺失。

完整页面、资源、系统入口和验证合同见
`docs/TODO/kiyori_first_run_experience/`；历史 2026-07-15 Operit 协议见
`docs/TODO/agreement_version_confirmation/`，不得恢复为当前实现。

## 待决策

- R2 与 R3 是否采用结构化单次确认合同，删除泛化的“始终允许”操作
- 预测性返回和输入法展开时的手势规则；浏览器首期 Back 顺序已确定为对话框、外部确认、sheet、地址编辑、网页历史、浏览器根页面
- 全屏网页搜索的建议来源与搜索记录展示
- Terminal 是否在未来增加第二入口

这些问题进入 [产品壳 TODO](../../TODO/kiyori_product_shell/index.md)，达成共识后再更新本文。
