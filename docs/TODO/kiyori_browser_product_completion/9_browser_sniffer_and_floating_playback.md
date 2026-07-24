# 浏览器资源嗅探与悬浮播放

## 状态模型

建立浏览器与播放器共享的显式状态机：

- `BROWSER_ONLY`：网页自行播放，player session 不存在
- `FLOATING_PLAYER`：player session 持有媒体，浏览器页面和 WebView 保持原样
- `FULLSCREEN_PLAYER`：同一 player session 转挂到全屏 surface

三种状态之间只转移 player surface 和播放 owner，禁止刷新、重载、重新嗅探或修改当前网页。返回全屏播放器时直接 finish Activity，让原 Browser Home/overlay 恢复，不重新启动浏览器 Activity。

## 媒体 candidate

- 收集 WebView 请求 URL、响应 MIME、页面 media 元素、MSE/manifest 线索和用户明确播放事件
- candidate 保存原始 URL、headers、referer、cookie scope、format、来源页面和发现时间
- 排序优先真实直链、可播放格式、明确 MIME 和最近用户播放；接口 URL 不能因 `Content-Type=video/mp4` 被错误当作文件直链
- 不重写、猜测或替换媒体 URL；无法解析时明确报告

## 悬浮嗅探 UI

- 浏览器菜单“悬浮嗅探”打开 candidate sheet，显示格式、来源、时长线索和播放/下载动作
- 选择播放后创建唯一 player session，并转挂到浏览器内悬浮 player surface
- 悬浮播放器可拖动、缩放、播放暂停、进全屏和关闭；不遮挡浏览器顶底栏的关键操作
- 进入全屏和返回悬浮保持同一 position、pause 状态、音轨和字幕
- 关闭 player 后只结束播放 session，不导航或刷新网页

## AI 能力

- 后续 Kiyori Capability API 可列出 candidate、选择播放、暂停、seek、全屏和关闭
- AI 操作 player 不直接驱动 Activity 或 Compose；UI 与 AI 观察同一 session
- 嗅探到的隐私 URL、Cookie 和 headers 不进入无关日志或对外服务

## 验收

- 网页到悬浮、悬浮到全屏、全屏回悬浮和关闭均不刷新或重载当前网页
- candidate 选择保留原始 URL 和必要 headers，下载仍进入同一下载 manager
- 快速切换和返回不会重复创建播放器或并发加载同一媒体
- 浏览器系统 Back、overlay 收缩和 player Back 的优先级明确
- Debug APK、提交、推送和远端 SHA 门禁通过；站点兼容和真机硬解保持待验证
