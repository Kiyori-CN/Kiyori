# 浏览器菜单、按钮接线与运行时边界

## 主菜单布局

“浏览器菜单按钮”是底栏第五个按钮；它打开“浏览器下拉抽屉菜单”。主菜单固定高度、不上下拖动、不使用可滚动列，左右边缘贴屏。

- 前三行：每行五个等宽 cell，图标在上、文字在下
- 第四行：三个等宽 cell，只绘制图标；文字只进入 contentDescription，保证无障碍可用但不占视觉高度
- 主菜单不再显示“关闭当前标签页”和“关闭全部标签页”
- 主菜单视觉参考旧 Kiyori 的白色圆角 surface、黑色线性图标和紧凑间距，但颜色跟随 Kiyori theme
- `工具箱`仍作为用户要求的入口保留，点击进入说明页；第五按钮本身是唯一浏览器菜单 owner，不复制第二个菜单状态

固定顺序：

| 第一行 | 第二行 | 第三行 |
| --- | --- | --- |
| 加书签 | 悬浮嗅探 | 无痕模式 |
| 书签 | UA标识 | 阅读模式 |
| 历史 | 网络日志 | 查看源码 |
| 下载 | AI对话 | 标记广告 |
| 插件 | 工具箱 | 网站配置 |

第四行从左到右为：退出浏览器、收起抽屉、浏览器设置。

## 全量按钮的实现策略

每个 cell 必须具备明确的 `onClick`：

- 已有能力直接调用现有 callback，所有网页数据仍来自当前 active session
- 尚未接通内核的能力进入 `PLACEHOLDER` 子抽屉，显示标题、图标、当前状态、为什么尚未接入，以及它不会改变网页/session/AI 状态
- 说明页不是 Toast、不是无动作 disabled cell，也不是伪造成功状态；用户点击后始终看到一个稳定页面并可通过 Back/抽屉返回
- 不为未接通能力创建假 DataStore、假 WebView、假 Cookie profile、假广告规则或假播放器 candidate

说明页清单：

- 悬浮嗅探：没有浏览器资源 candidate、播放器 handoff 和下载/播放 owner
- 工具箱：浏览器菜单已是唯一工具 owner，独立第二工具箱尚未定义
- 无痕模式：没有 per-profile Cookie/cache/WebStorage 和 AI profile 字段
- 阅读模式：没有 reader state、正文提取和转换管线
- 标记广告：没有页面级规则、注入脚本和持久化标记 owner
- 网站配置：没有 per-site 配置 store、站点匹配和设置继承合同
- 浏览器设置：没有独立 Browser Settings route；产品设置 owner 仍属于 Kiyori Settings

## 共享 runtime 合同

- UI、悬浮 overlay 和 AI `browser_*` 工具都通过 `StandardBrowserSessionTools.sessions/sessionOrder/activeSessionId`
- App Shell 与 overlay 通过 `BrowserPresentationCoordinator` 借用同一个 `WebSessionBrowserHost`，只重新挂载同一个 `WebView`
- 人类在窗口页选择/关闭/新建 session 后，AI 读取同一 active session；AI 导航、点击、关闭或刷新后，`syncProjectedBrowserStateOnMain` 刷新 UI
- 搜索引擎、搜索记录、menu route、window mode、placeholder 和 page source 是 presentation/store 状态，不进入 `browser_tabs`/`browser_snapshot` 协议
- `网络日志`读当前 session 的真实 `BrowserNetworkRequestEntry`；不凭空补 HTTP status
- `查看源码`通过活动 WebView 异步读取 DOM；不阻塞主线程，不写访问历史
- 任何异步异常都使用 `AppLogger` 记录并保留可见错误文本

## AI 协同退出语义

### overlay 展开网页

1. AI `browser_*` 首次需要展示时创建最小化 overlay 和 indicator
2. 用户点击 indicator，`setExpanded(true)`，WebView 重新挂载到 overlay
3. 左上角返回调用 `setExpanded(false)`，indicator 回到原位置；不调用 `onBack`/`goBack`
4. AI 仍然持有同一个 session、snapshot 和活动 WebView，可继续执行操作

### 从 AI Home 手动打开浏览器

1. AI Home 顶栏浏览器 icon 调用 coordinator，确保已有 overlay lease/session；随后 Shell 记录 `AI_HOME` 返回来源并进入 Browser Home
2. App presentation 隐藏 indicator，WebView 不 reload
3. Browser Home 顶栏返回或菜单 AI对话释放 app presentation，Shell 回到 AI Home，indicator 恢复
4. 菜单退出浏览器释放 app presentation 并销毁 indicator，但保留 session registry

### 清空全部窗口

窗口总览清空按钮和 AI `browser_close_all` 继续复用 `closeSession`。最后一个 session 被删除后，WebView 被清理，overlay/presentation owner 被销毁，AI 后续读取到空标签列表。

## 文案与兼容标识

用户可见文案固定为：历史、书签、插件、UA标识、悬浮嗅探、AI对话、浏览器菜单按钮、浏览器下拉抽屉菜单、退出浏览器。以下内部标识保持原样：

- `userscript`、`StandardBrowserSessionTools`
- `browser_*` 工具名和参数
- `WebSessionHistoryStore` DataStore key
- `com.ai.assistance.operit` namespace、`operit://` 和插件生态协议
