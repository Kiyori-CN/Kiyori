# 软件首页与全屏网页搜索

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

- 页面背景延伸到状态栏；首行由返回、搜索输入卡和无痕按钮组成
- 输入卡内显示当前搜索引擎、网址或关键词、清空和提交按钮
- 搜索引擎面板、搜索记录、删除、清空和当前引擎高亮直接复用 `WebSessionHistoryStore`
- 无痕按钮只改变新窗口默认 Profile；在 Multi-Profile 不可用时禁用并展示原因
- 手机记录使用单列或两列紧凑卡片；宽度达到 600dp 后使用两列内容区，搜索框最大宽度受限，避免横向拉伸
- 页面打开时自动聚焦并显示键盘；返回先关闭引擎面板，再关闭页面

## 提交逻辑

1. trim 输入，空输入不产生任何窗口或历史
2. 使用 `BrowserAddressResolver` 与当前搜索引擎解析 URL
3. 创建一个使用当前新窗口 Profile 的 WebSession
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

### 无痕按钮是否立刻转换当前窗口？

不会。窗口 Profile 在 WebView 创建前确定，转换已存在 WebView 会破坏数据隔离。按钮只影响后续新窗口。

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
