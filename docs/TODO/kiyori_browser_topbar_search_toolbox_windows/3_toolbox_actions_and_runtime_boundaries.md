# 工具抽屉、按钮文案与运行时边界

## 工具网格

工具抽屉保留参考版的圆角白色 surface、3 列/5 列密度和底部动作区的视觉语言，但只渲染已经接通真实能力的入口。网格按屏幕宽度使用 5 列，未满一行时不填充无动作的空按钮。

第一组：

- 加书签
- 书签
- 历史
- 下载
- 插件

第二组：

- UA标识
- 网络日志
- 刷新
- 查看源码

关闭动作放在网格下方，分别为关闭当前标签页、收起工具箱、关闭全部标签页；危险动作使用 `error` 色。它们不改变 AI 工具的 session 标识或 active-session 选择规则。

## 文案迁移

只修改用户可见资源值，不修改内部兼容标识：

- `web_session_history`：`历史记录` → `历史`
- `web_session_bookmarks`：`收藏` → `书签`
- `web_session_userscripts`：`Userscripts` → `插件`
- 电脑/手机的动作入口标题：由模式名改为 `UA标识`；具体选择页仍显示“电脑模式”“手机模式”

`userscript`、`StandardBrowserSessionTools`、AI tool name 和持久化 key 继续保持原样。

## 网络日志

当前 session 已在 `BrowserNetworkRequestEntry` 中记录 method、URL、是否主框架、是否静态资源、headers 和 timestamp。本轮把这些只读数据投影为 UI model：

- 页面加载时清空当前 session 事件，保持现有行为
- `shouldInterceptRequest` 写入后在主线程刷新 Host projection，AI 发起的导航也能看到同一列表
- UI 默认最新在前，URL 单行省略，点击只展开详情，不修改请求
- 没有请求时显示明确空状态；不伪造 HTTP status，因为当前记录模型没有 status 字段

## 页面源码

“查看源码”在活动 WebView 上异步执行 `document.documentElement.outerHTML`：

- 请求开始显示 loading
- 回调成功显示可滚动源码并提供复制
- WebView 已销毁、未附着或 JS 失败时显示真实错误并记录日志
- 不通过 IO 阻塞主线程，不重新加载页面，不把源码写入持久化历史

## 不显示的参考按钮

以下按钮当前没有真实能力，故不进入 UI：

- 悬浮嗅探：当前 Kiyori 没有参考版的播放器/嗅探 candidate/runtime
- 工具箱：第五按钮已经是浏览器工具箱，没有第二个浏览器 owner
- 无痕模式：没有 per-profile Cookie/cache/WebStorage 隔离
- 阅读模式：没有 reader state/转换管线
- 标记广告：没有页面级广告规则和注入状态
- 网站配置：没有 per-site 设置 store 与 owner

不通过 disabled item、toast 假装成功或跳到空页面来补齐列表。它们应作为独立能力任务先设计 runtime，再加入工具网格。

## AI 共用合同

- UI、浏览器悬浮窗和 AI browser_* 工具都通过 `StandardBrowserSessionTools` 的 `sessions/sessionOrder/activeSessionId` 操作
- 新增搜索、网络日志和源码状态属于 presentation/只读投影，不改变 AI `browser_tabs`、`browser_snapshot`、`browser_network_requests` 的协议
- 人类在窗口页切换 session 后，AI 立即读取同一个 active session；AI 创建/关闭/导航后，UI 通过 `syncProjectedBrowserStateOnMain` 更新
- 所有异步异常必须 `AppLogger` 记录；不吞掉 `catch` 的错误文本

