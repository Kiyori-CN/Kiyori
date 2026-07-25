---
fork: https://github.com/Kiyori-CN/Kiyori
upstream: https://github.com/AAswordman/Operit
status: completed
baseline: b3a1b8ef4309c0a2577cb776de8e201553370aba
legacy_design_reference: 24a2dfa91f0a4166dc58e5c4732d11861173f766
---

# 浏览器沉浸式 UI 重构

## 任务定位

本轮把 Browser Home 从“WebSession 浏览器加 Kiyori 五项全局底栏”改为完整的浏览器根页面。进入底部第二项后，Kiyori 五项全局底栏隐藏，浏览器地址栏、网页区域、浏览器专属底栏、标签总览和工具抽屉构成同一套浏览器界面。

页面结构主要参考 `D:/10_Project/kiyori-android` 的浏览器布局，参考提交固定为 `24a2dfa91f0a4166dc58e5c4732d11861173f766`。视觉继续使用 Operit 原版的语义 token 使用方式、排版和形状，默认 token 值遵守 [专业浏览器灰白默认主题](../../doc-src/decisions/0007_professional_browser_theme.md)；App Shell 底栏、浏览器底栏和浏览器四行菜单采用该参考提交的 Kiyori 空心描边 Vector 与几何，不复制旧项目的硬编码白色主题、Activity 宿主、X5 类型或页面私有状态。

## 已确认基线

- 上一轮共享 Browser Runtime 已由用户完成真机冒烟测试，反馈“没什么问题”
- 父仓库和 `terminal` 在本轮开始时均位于各自 `main`，与远端前后差为 `0/0`
- 当前 Browser Home 通过 `BrowserPresentationCoordinator` 获取唯一 App Shell 展示租约
- `ToolGetter.getBrowserSessionTools()`、AI 的全部 `browser_*` 工具和 Browser Home 使用同一个 `StandardBrowserSessionTools` 进程级实例
- 当前浏览器内核为 `android.webkit.WebView` 和设备 WebView provider；本轮不引入 X5/TBS、GeckoView 或第二套 Chromium
- 当前冲突来自呈现层：`KiyoriAppShell` 为 Browser Home 预留 `80dp` 并继续显示全局底栏，浏览器内部又显示一组五项工具栏
- Kiyori 从未正式发布，因此本轮彻底替换旧 Browser Home chrome，不保留并行旧界面或开关

## 冻结目标

1. Browser Home 成为沉浸式根页面，隐藏 Kiyori 五项全局底栏并占满可用窗口。
2. 浏览器底栏固定为后退、前进、主页、标签页、工具箱五项，顺序与旧 Kiyori 一致。
3. 标签页入口打开浏览器内全屏标签总览，保留同一 WebView 和标签注册表，不创建截图或状态副本。
4. 工具箱采用五列网格和底部三动作区；历史、收藏、下载、Userscripts、模式切换、关闭当前标签和关闭全部标签均有稳定位置。
5. 历史、收藏、下载和 Userscripts 继续进入现有功能页，但统一由可拖动底部抽屉承载。
6. 菜单、标签总览、地址编辑、网页历史和 Browser Home 根页面遵守明确的 Back 优先级。
7. AI 在任何 UI 状态下继续观察和操作同一活动标签；UI 重构不改变工具名、参数、WebView、Cookie、下载或 userscript owner。
8. 只实施本轮 chrome、抽屉和接口接线；旧项目的全屏搜索页留到后续独立 source-port。

## 自问式设计结论

### Browser Home 是否仍是普通五入口根页面

它仍是 `PrimaryDestination.BROWSER_HOME`，但视觉上属于沉浸式浏览器根。App Shell 继续拥有根路由和返回 Software Home 的能力，不再在 Browser Home 上绘制 Kiyori 五项全局底栏。

### 用户如何离开浏览器

- 网页、地址编辑、抽屉和标签总览均无可消费状态时，系统 Back 返回 Software Home
- 地址栏右侧的最小化按钮在 App Shell presentation 中返回 Software Home，在 overlay presentation 中继续缩成悬浮 indicator
- 浏览器工具箱不复制 Kiyori 全局导航；进入其他产品根页面必须先退出 Browser Home

### 浏览器主页按钮做什么

本轮调用现有导航接口打开 `about:blank`，复用活动标签；没有标签时由现有 runtime 创建标签。旧项目完整的全屏搜索首页和搜索历史不在本轮提前实现。

### 新建标签放在哪里

底栏中央不再承担新建标签。新建入口移入全屏标签总览的中央主按钮，与旧 Kiyori 窗口页一致。这样底栏中央可以稳定表示浏览器主页。

### 标签按钮显示什么数字

显示当前标签总数，不显示活动标签在列表中的序号。数字上限显示为 `99+`，语义描述始终包含真实标签总数。

### 工具入口如何排布

- 五列主网格：历史记录、收藏、下载、Userscripts、手机或电脑模式
- 动态区域：仅在当前网页注册 userscript 页面命令时显示“当前页面脚本菜单”
- 底部动作区：关闭当前标签页、收起抽屉、关闭全部标签页
- 刷新继续位于地址栏，不在工具箱重复
- 下载格显示进行中和失败数；Userscripts 详情页保留完整说明“管理当前浏览器内安装的原生油猴脚本”

### 为什么关闭操作不和普通工具混排

关闭标签改变 session 集合，关闭全部标签具有更高误触成本。二者进入抽屉底部固定动作区，关闭全部使用主题错误色；普通浏览和资料入口保持在上方五列网格。

### 标签总览为什么不是第二个 session 页面

它只读取 `WebSessionBrowserState.tabs` 并调用现有 select、close、new 和 close-all callback。活动 WebView 仍在原页面树中保持挂载，标签总览只是覆盖层，不创建 WebView、预览数据库或另一份活动标签状态。

### UI 打开时 AI 改变页面怎么办

所有覆盖层观察共享 host state。AI 导航、切换或关闭标签后，底栏计数、标签卡片、地址和网页状态随同一投影更新；UI 不缓存业务快照，也不抢占 active session owner。

### 横屏、平板和折叠窗口怎么办

- 浏览器主体始终填满当前 presentation 约束
- 标签总览在 Compact 使用两列，Medium 使用三列，Expanded 使用四列
- 抽屉在 Compact 接近全宽，在更宽窗口限制最大宽度并居中
- 抽屉最大高度避开状态栏，内容处理导航栏和输入法 inset
- 宽度、方向或窗口姿态改变只重新布局，`sheetRoute` 和 Browser Runtime 位于 host state，不因重组清空

## 工作分解

```text
kiyori_browser_ui_refactor/
	index.md
	1_source_mapping_and_architecture.md
	2_ui_navigation_and_adaptive_layout.md
	3_drawer_tabs_motion_and_back.md
	4_runtime_ai_validation_and_handoff.md
```

1. [源映射与目录架构](1_source_mapping_and_architecture.md)
2. [页面、按钮与自适应布局](2_ui_navigation_and_adaptive_layout.md)
3. [抽屉、标签、转场与 Back](3_drawer_tabs_motion_and_back.md)
4. [共享运行时、验证与交付](4_runtime_ai_validation_and_handoff.md)

## 非目标

- 不移植旧 Kiyori 的全屏搜索页、搜索引擎面板、搜索记录或无痕窗口
- 不移植 X5/TBS、窗口预览文件、媒体嗅探、播放器交接或网络日志
- 不修改 `browser_*` 工具协议、风险授权、页面快照或坐标接口
- 不复制历史、收藏、下载、Userscripts、Cookie 或标签持久化状态
- 不增加旧 UI 开关、兼容分支、降级路径或替代实现
- 不运行 release、安装、ADB、MuMu 或设备自动化
- 本轮实现完成后不提交、不推送，等待用户安装 Debug APK 实测

## 本轮实现结果

- `KiyoriShellState.showsBottomBar` 已排除 Browser Home，`KiyoriAppShell` 已删除浏览器页面的 `80dp` 底部预留
- `ui/features/websession/browser/chrome/` 已建立冻结的五文件结构，承载浏览器底栏、三态抽屉、工具箱、标签总览和纯布局规则
- 标签总览继续覆盖原 `AndroidView`，工具与详情页继续调用现有 WebSession callback；共享 runtime、presentation coordinator 和 AI `browser_*` 工具路径未修改
- 历史、收藏、下载和 Userscripts 已按固定抽屉高度获得受约束滚动区域
- 旧 `WebSessionBottomToolbar.kt`、`WebSessionMenuSheet.kt` 和 `WebSessionTabSheet.kt` 已删除，源码与测试中无残留引用
- 项目 `.venv` 正式准备门禁、`git diff --check` 和两次串行 `:app:assembleDebug` 均通过；最终 APK 证据记录在 [共享运行时、验证与交付](4_runtime_ai_validation_and_handoff.md)
- 用户于 `2026-07-24` 完成本轮实测并反馈“测试没什么问题”，当前沉浸式 Browser Home 切片转为 `completed`

## 完成定义

- Browser Home 不显示 Kiyori 五项全局底栏，也没有 `80dp` 人工占位
- 浏览器专属底栏顺序、按钮职责和标签计数符合冻结设计
- 标签总览、工具抽屉和四个功能抽屉可正常打开、切换、关闭和响应 Back
- 指定功能的原有 callback 全部接通，动态 userscript 页面命令没有丢失
- 切换展示层和打开覆盖层不调用 `reload`、`loadUrl`、`destroy` 或创建第二 WebView
- 正式开发准备检查与 `git diff --check` 通过
- 用户授权的 Debug APK 构建成功，并核对时间、大小、SHA-256、包名、版本和签名
- 用户完成本轮真机冒烟验收并确认没有发现问题
