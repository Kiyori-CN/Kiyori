# 软件首页与全屏网页搜索

> 状态：本地实现、定向 JVM 测试、正式开发门禁与 Debug APK 已验证；本轮不提交、不推送，真机视觉、输入法和转场保持待验收。

## 2026-07-28 全屏搜索页现代化完善计划

本轮沿用现有 `WebSessionBrowserSearchScreen`、`WebSessionHistoryStore`、Browser Runtime 和
Multi-Profile owner，不创建第二套搜索页、搜索记录或无痕状态。Kiyori 尚未发布，被替代且不再使用的
搜索页旧布局直接删除，不保留并行界面、兼容开关或回退路径。

### 视觉与交互目标

- 搜索框左侧只显示当前搜索引擎图标和展开箭头，不再显示引擎名称
- 搜索引擎选择器使用淡蓝背景、白色选项卡、黑色文字和真实品牌图标；展开时覆盖下方内容，不改变
  当前网页与搜索记录的位置
- 当前网页区域显示页面标题和网址两行；右侧只保留复制链接、编辑链接两个纵向图文动作，编辑链接
  将网址回填到输入框
- 搜索记录使用 `FlowRow` 自适应标签，标签内容保留用户原始输入，因此普通文字和网址都能按同一规则
  展示与打开
- 搜索记录标题右侧默认只显示垃圾桶；点击后切换为“清空 / 完成”，标签右上角显示删除叉号
- 标签删除先进入当前页面的待提交集合，点击“完成”后才写入 `WebSessionHistoryStore`；退出编辑模式前
  返回不会误删持久记录
- “清空”不加入标签待删除集合；它显示底部确认框，确认后直接调用现有历史 owner 清空记录并退出
  编辑模式，不再要求点击“完成”
- 无痕按钮继续固定在搜索框右侧并使用现有真实 Profile 支持判断，不改变普通与无痕窗口语义

### 美学门禁

- 浏览器中性主题保持不变，蓝色只用于选择态和语义动作
- 页面通过一致的圆角、留白、图标比例、黑灰文字层级和克制阴影建立现代感，不使用厚重描边、
  高饱和大色块或无意义装饰
- 手机、平板和横屏继续复用同一共享搜索页；引擎面板与历史标签在受限宽度内容区内稳定排版
- 所有点击目标保留清晰的无障碍说明和不低于现有浏览器 chrome 的可操作面积

### 实施与验证

1. 复用 legacy Kiyori 已有的九个 `64×64` 搜索引擎图标，并把资源 ID 接入
   `WebSessionSearchEngine`
2. 重构 `WebSessionBrowserSearchScreen` 的覆盖层、当前网页卡和标签式历史编辑状态
3. 让 Browser Home 传入真实页面标题；Software Home 没有活动网页时不伪造当前网页信息
4. 更新 `README.md`、`CONTEXT.md`、浏览器架构文档和本计划的完成证据
5. 执行定向测试、Kotlin 编译、formal readiness、`git diff --check`，最后串行构建并核验
   `app/build/outputs/apk/debug/app-debug.apk`

真机视觉、键盘、旋转、触控反馈和 AndroidX Multi-Profile 现场行为仍由设备验收决定。本轮不安装、
不使用 ADB 或 MuMu，不创建提交，不推送远端。

## 本轮现代化实现证据

- 九个搜索引擎图标来自 legacy Kiyori 的既有 `64×64` PNG，逐文件 SHA-256 一致
- `WebSessionSearchUiPolicyTest`、`KiyoriSoftwareHomeSearchTest`、
  `WebSessionBrowserChromeLayoutTest` 共 `18/18` 通过
- `:app:compileDebugKotlin` 通过
- `python -B ci/script/check_formal_readiness.py --repository . --require-main` 通过
- `git diff --check` 通过
- `:app:assembleDebug --no-daemon --console=plain` 通过，`233` 个任务中 `27` 个执行、
  `206` 个 `UP-TO-DATE`；`verifyDebugPlayerRuntimePackaging` 通过
- APK：`app/build/outputs/apk/debug/app-debug.apk`
- 构建时间：`2026-07-28 21:59:30 +08:00`
- 大小：`463723827` bytes
- SHA-256：`72B83A4A80B98D6F5582D0F68913F86FC7C4D172752C2DD84359C54A2F6D45B5`
- 包名与版本：`com.kiyori`，`45 / 0.1.0`
- `apksigner verify --verbose` 的 APK v2 签名验证通过
- `zipalign -c -P 16 -v 4` 验证通过

## 2026-07-28 紧凑化修订

首轮真机观感反馈确认全屏搜索页整体尺寸偏大。本轮以浏览器下拉抽屉菜单为统一基准重新压缩：

- 顶栏搜索框高 `38dp`，返回与无痕动作区为 `34dp`，当前引擎图标为 `18dp`
- 搜索引擎卡高 `42dp`，图标 `19dp`，名称使用菜单 `11sp` 文字基准
- 当前网页操作宽 `46dp`，蓝色图标使用菜单 `21dp` 基准；左侧标题与网址分别为 `13sp` 和
  `11sp`，点击整个信息区直接返回活动网页
- 搜索历史标题改用紧凑标题尺度；“清空 / 完成”为小型文字动作，“完成”固定使用蓝色
- 历史标签为 `12sp`、`7dp` 垂直内边距、最大 `250dp`；用户输入为网址时继续记录原始网址，
  通过单行省略和 `FlowRow` 自适应宽度展示
- 引擎面板后方增加透明点击层，点击面板周围会收起；面板仍覆盖下方内容，不改变页面布局

紧凑化最终证据：

- `WebSessionSearchUiPolicyTest`、`KiyoriSoftwareHomeSearchTest`、
  `WebSessionBrowserChromeLayoutTest` 共 `19/19` 通过
- `:app:compileDebugKotlin`、formal readiness 与 `git diff --check` 通过
- `:app:assembleDebug --no-daemon --console=plain` 通过，`233` 个任务中 `27` 个执行、
  `206` 个 `UP-TO-DATE`；`verifyDebugPlayerRuntimePackaging` 通过
- APK：`app/build/outputs/apk/debug/app-debug.apk`
- 构建时间：`2026-07-28 22:33:10 +08:00`
- 大小：`463723827` bytes
- SHA-256：`6036895FBDDD0B0DED5D44E4ED92629C4CE0E22AA28456944FED1DFEF99449FF`
- 包名与版本：`com.kiyori`，`45 / 0.1.0`
- APK v2 签名与 `zipalign -c -P 16 -v 4` 验证通过

## 2026-07-28 历史清理流程与当前网页行二次压缩

- 用户现场纠正后，搜索历史标题放大为 `18sp`，空状态放大为 `14sp`，“清空”和“完成”放大为
  `15sp`
- 垃圾桶图标进一步放大为 `28dp`，点击目标保持 `34dp`
- “清空”显示带遮罩的底部白色圆角确认框；确认按钮使用红色描边，确认后立即清空并退出编辑模式
- 标签右上角叉号只暂存单条删除，仍必须点击“完成”才提交
- 当前网页标题与网址缩小为 `12sp / 10sp`，外层和信息区垂直内边距均压缩为 `4dp`
- 复制链接、编辑链接图标进一步缩小为 `16dp`，图标与文字间距保持 `1dp`

本次尺寸方向纠正按用户要求不运行 Gradle、测试或 APK 构建。以下证据只对应此前已经完成构建的
清理流程和旧尺寸，不覆盖本次 `18sp / 14sp / 15sp / 28dp / 16dp` 调整：

- `WebSessionSearchUiPolicyTest`、`KiyoriSoftwareHomeSearchTest`、
  `WebSessionBrowserChromeLayoutTest` 共 `19/19` 通过
- `:app:compileDebugKotlin`、formal readiness 与 `git diff --check` 通过
- `:app:assembleDebug --no-daemon --console=plain` 通过，`233` 个任务中 `31` 个执行、
  `202` 个 `UP-TO-DATE`；`verifyDebugPlayerRuntimePackaging` 通过
- APK：`app/build/outputs/apk/debug/app-debug.apk`
- 构建时间：`2026-07-28 22:53:51 +08:00`
- 大小：`463724743` bytes
- SHA-256：`84F8CF960BC03CEDBE021AF0656DDC528EE210C636B8277FC9C94D321F57B516`
- 包名与版本：`com.kiyori`，`45 / 0.1.0`
- APK v2 签名与 `zipalign -c -P 16 -v 4` 验证通过

## 2026-07-28 顶栏共享几何与三行自适应输入

本轮覆盖前述 `38dp / 34dp / 28dp` 的临时顶栏和垃圾桶尺寸：

- 全屏搜索首行直接复用 Browser Home 的 `8dp` 横纵边距、`6dp` 三槽间距、`40dp` 两侧动作区和
  `42dp` 单行搜索框基准，因此返回、搜索框、无痕按钮的绝对位置和中间宽度保持一致
- 搜索输入由单行改为最多三行的软换行输入，不产生水平滚动；输入框顶部和左右宽度不变，
  `animateContentSize` 只推动底边与下方内容，超过三行后由 `BasicTextField` 保持三行视口并纵向滚动
- 左右动作、引擎按钮、清除和搜索动作都位于同一垂直居中 Row，随搜索框增高同步下移
- 搜索历史标题行固定为 `34dp`，垃圾桶缩为 `26dp`，进入“清空 / 完成”状态不再改变标题纵向位置
- 复制与编辑图标保持 `16dp`，并向下偏移 `1dp`、取消图文间额外间距
- Browser Home 右侧固定为刷新动作；加载期间不切换叉号，回调始终执行活动 WebView `reload`
- Browser Home 返回/刷新与全屏搜索无痕动作共用 `40dp` 圆形裁剪按压区域，不再显示方形水波纹

本轮本地证据：

- `:app:compileDebugKotlin` 通过
- `WebSessionSearchUiPolicyTest`、`WebSessionBrowserChromeLayoutTest` 与
  `KiyoriSoftwareHomeSearchTest` 合计 `19/19`，零失败、零错误、零跳过
- formal readiness、`git diff --check`、7 份 `strings.xml` 解析和旧刷新/停止引用清理检查通过
- `:app:assembleDebug --no-daemon --console=plain` 通过，`233` 个任务中 `39` 个执行、
  `194` 个 `UP-TO-DATE`；`verifyDebugPlayerRuntimePackaging` 通过
- APK：`app/build/outputs/apk/debug/app-debug.apk`
- 大小：`463722319` bytes
- SHA-256：`26BC4B4ED56BD735AE7F89EA3EF6C9FD316E29EEA9F221DA35934475075347BF`
- 包名与版本：`com.kiyori`，`45 / 0.1.0`，min SDK `26`，target SDK `34`
- Android Debug APK v2 签名和 `zipalign -c -P 16 -v 4` 验证通过

真机多行输入、输入法、动画、触控反馈、旋转和刷新行为仍为待验收。

## 实现记录

- `KiyoriSoftwareHomePage` 使用 `<600dp`、`600-839dp`、`>=840dp` 三档布局；手机为居中单列，平板和大屏为品牌/搜索双区
- 首页直接复用 `ic_kiyori_app_icon`、`MaterialTheme` 和 24dp 搜索 surface，没有引入独立色板或无 owner 的相机、语音、附件入口
- `KiyoriFullScreenWebSearchPage` 复用 `WebSessionBrowserSearchScreen`、`WebSessionHistoryStore` 与 `BrowserAddressResolver`；搜索引擎、记录、删除和清空都与 Browser Home 观察同一数据
- `BrowserPresentationCoordinator.openUrlInNewSession` 通过同一 `StandardBrowserSessionTools` 创建并激活新 WebSession，首页提交不会覆盖当前窗口，AI 无需额外同步即可发现该 session
- 搜索框右下角附件入口使用 AI 首页默认 Agent 输入栏同一 `Icons.Default.Add` 与 `24dp` 图标尺寸；点击仍通过既有一次性动作进入同一附件面板
- 搜索页进入时自动聚焦；返回会先关闭搜索引擎面板；空输入不产生窗口或记录；提交后 Shell 以 Software Home 为返回目标进入 Browser Home
- App Shell 使用淡入与轻微上移动画显示搜索页，Home Pager 和 AI Home 保持原有 composition/state
- 无痕按钮继续使用第三里程碑接入的 AndroidX WebKit Multi-Profile、默认新窗口 Profile 和设备支持判断，
  本轮只调整其在搜索框右侧的布局位置与视觉相邻关系

## 2026-07-30 无痕入口统一实施记录

- 全屏搜索右上角无痕按钮与浏览器菜单“无痕模式”共用同一个默认 Profile 切换动作
- 两个入口只改变后续新窗口默认 Profile；当前标签的不可变 `WebSession.profile` 不转换
- 切换成功后共用“已开启无痕模式 / 已关闭无痕模式”短时提示
- 菜单入口不打开窗口总览、不关闭菜单，也不创建新标签
- 无痕不可用时沿用 AndroidX Multi-Profile 可用性门禁，不使用普通 Profile 模拟
- 共享 Profile 切换策略与搜索 UI 定向 JVM 回归已通过；设备上的菜单保持、短时提示位置和
  Multi-Profile 实际可用性仍保留为 `verification_pending`

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

## 2026-07-28 搜索后的引擎快速切换条

- 仅 `BrowserAddressResolver` 判定为文本搜索的提交会保存 `lastSearchQuery` 并显示顶栏下方切换条
- 切换条复用九个 `WebSessionSearchEngine` 图标和既有 `WebSessionHistoryStore` 引擎 owner，不创建新 store
- 点击其他引擎以当前活动 Profile 和同一 query 重新提交；网址提交、空输入和右侧关闭按钮隐藏该栏
- 视觉严格参考 legacy Kiyori：`28dp` chip、`12dp` 图标、右侧 `22dp` 关闭目标与水平滚动列表
- `BrowserAddressResolverTest` 与 `WebSessionSearchUiPolicyTest` 覆盖文本/地址判定和几何常量；真机滑动、
  长文本、旋转、触控反馈与实际搜索站点仍待验收
- 本轮四组浏览器定向 JVM 合计 `15/15`，formal readiness、7 份 `strings.xml` 解析和
  `git diff --check` 通过；最终 Debug APK 证据与第七阶段的同轮记录一致

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
