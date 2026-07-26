# 软件首页与全屏网页搜索

> 状态：本地实现、定向 JVM 测试、正式开发门禁与 Debug APK 已验证；提交和推送在本轮交付阶段执行，真机视觉、输入法和转场保持待验收。

## 实现记录

- `KiyoriSoftwareHomePage` 使用 `<600dp`、`600-839dp`、`>=840dp` 三档布局；手机为居中单列，平板和大屏为品牌/搜索双区
- 首页直接复用 `ic_kiyori_app_icon`、`MaterialTheme` 和 24dp 搜索 surface，没有引入独立色板或无 owner 的相机、语音、附件入口
- `KiyoriFullScreenWebSearchPage` 复用 `WebSessionBrowserSearchScreen`、`WebSessionHistoryStore` 与 `BrowserAddressResolver`；搜索引擎、记录、删除和清空都与 Browser Home 观察同一数据
- `BrowserPresentationCoordinator.openUrlInNewSession` 通过同一 `StandardBrowserSessionTools` 创建并激活新 WebSession，首页提交不会覆盖当前窗口，AI 无需额外同步即可发现该 session
- 搜索框右下角附件入口使用 AI 首页默认 Agent 输入栏同一 `Icons.Default.Add` 与 `24dp` 图标尺寸；点击仍通过既有一次性动作进入同一附件面板
- 搜索页进入时自动聚焦；返回会先关闭搜索引擎面板；空输入不产生窗口或记录；提交后 Shell 以 Software Home 为返回目标进入 Browser Home
- App Shell 使用淡入与轻微上移动画显示搜索页，Home Pager 和 AI Home 保持原有 composition/state
- 无痕按钮不在本阶段伪造；它将在第三里程碑与 AndroidX WebKit Multi-Profile、默认新窗口 Profile 和设备支持判断同时启用

## 本地验证

- 定向 JVM：`KiyoriSoftwareHomeSearchTest`、`KiyoriShellStateTest`、`BrowserAddressResolverTest` 通过
- 正式开发准备：`python -B ci/script/check_formal_readiness.py --repository . --require-main` 通过
- 差异检查：`git diff --check` 通过
- Debug 构建：`assembleDebug` 成功，Gradle 325 个任务零失败
- APK：`app/build/outputs/apk/debug/app-debug.apk`
- 构建时间：`2026-07-24 23:23:47 +08:00`
- 大小：`449466022` bytes
- SHA-256：`8E1E873DBA921464A2BC5949DB3277783192DCE969917595C0AD594DB31A5497`
- 包名与版本：`com.kiyori`，`45 / 0.1.0`
- Debug 签名：APK Signature Scheme v2 验证通过
- ZIP 对齐：`zipalign -c -P 16 4` 通过；该结果不等于 native ELF `PT_LOAD` 16 KB 兼容性证明

## 旧实现

- 软件首页只有纯文本 `Kiyori` 和 112dp 矩形搜索卡，平板仅用最大宽度约束
- Shell 全屏搜索页只有标题和空输入框，没有提交、搜索引擎、搜索记录、窗口 Profile 或真实 Browser Runtime 接线
- 浏览器内部已经有可工作的全屏搜索 UI、搜索引擎和搜索记录 store，但软件首页另建了一份空状态

## 首页 UI

- 手机使用居中的单列 hero；平板和横屏使用受限宽度的双区布局，左侧品牌与说明，右侧搜索卡
- 品牌区使用现有 Kiyori 资产与主题排版，不新增第二套硬编码色板
- 主标题固定为“Kiyori”，副标题描述“浏览、理解与处理网页内容”，避免把输入框误写成 AI 专属提示
- 搜索卡使用 20dp 以上圆角、清晰边框和低层级 surface，整卡点击进入全屏网页搜索
- 卡内保留“搜索”和“AI”两个明确动作；搜索进入全屏页，AI 移动到 AI Home
- 不展示没有功能 owner 的相机、语音或附件按钮

## 全屏搜索 UI

- 页面背景延伸到状态栏；首行由返回和搜索输入卡组成
- 输入卡内显示当前搜索引擎、网址或关键词、清空和提交按钮
- 搜索引擎面板、搜索记录、删除、清空和当前引擎高亮直接复用 `WebSessionHistoryStore`
- 无痕按钮与新窗口默认 Profile 由第三里程碑同时接入；在真实 Multi-Profile owner 存在前不显示临时开关
- 手机记录使用单列或两列紧凑卡片；宽度达到 600dp 后使用两列内容区，搜索框最大宽度受限，避免横向拉伸
- 页面打开时自动聚焦并显示键盘；返回先关闭引擎面板，再关闭页面

## 提交逻辑

1. trim 输入，空输入不产生任何窗口或历史
2. 使用 `BrowserAddressResolver` 与当前搜索引擎解析 URL
3. 在当前普通 Browser Runtime 中创建并激活一个新 WebSession；第三里程碑再把显式 Profile 加入同一创建命令
4. 写入搜索记录或网址访问记录
5. Shell 进入 Browser Home，并将新 session 设为 active
6. 关闭全屏搜索、清 focus 和输入法；不先创建 overlay

软件首页搜索始终创建新窗口，因为它是产品级新任务入口。Browser Home 顶栏搜索继续导航当前窗口，不改变这一语义。

## 转场

- 首页到搜索页使用同一 Shell 上的淡入与轻微上移，不重建 Home Pager 或 AI Home
- 提交后搜索页退出，Browser Home 从 Shell 根路由进入；不做 WebView 截图假转场
- 返回首页恢复原 Pager 位置和滚动状态

## 自问自答

### 为什么不直接复用浏览器 Host 的搜索 overlay？

浏览器搜索 overlay 的提交语义是导航当前窗口，软件首页的语义是创建新窗口。两者应复用解析器、引擎和记录 store，但保留不同的路由 owner。

### 为什么本里程碑不先显示无痕按钮？

窗口 Profile 必须在 WebView 创建前确定，转换已存在 WebView 会破坏数据隔离。在 AndroidX WebKit Multi-Profile 支持判断、Profile runtime 和默认新窗口 Profile 尚未成为同一个真实 owner 前显示按钮，只会产生无作用入口或共享 Cookie 的伪无痕，因此按钮与实现一起留到第三里程碑。

## 预计文件

- `ui/main/shell/KiyoriShellPages.kt`
- `ui/main/shell/KiyoriAppShell.kt`
- `ui/main/shell/KiyoriShellState.kt`
- Browser presentation/runtime 的新窗口命令
- 主题字符串与对应 Shell/JVM 测试
- `README.md`、`CONTEXT.md`

## 验收

- 首页在手机、600dp 平板和 840dp 大屏均有稳定布局
- 点击搜索框进入真实全屏搜索，提交网址和关键词均创建新窗口并进入 Browser Home
- 搜索引擎和记录与浏览器内部搜索保持同一份数据
- AI Home、Home Pager 和 Browser Runtime 不因搜索页打开关闭而重建
- Debug APK、提交和推送门禁通过
