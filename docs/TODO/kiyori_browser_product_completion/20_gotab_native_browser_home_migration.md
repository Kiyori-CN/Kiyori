---
fork: https://github.com/Kiyori-CN/Kiyori
upstream: https://github.com/AAswordman/Operit
status: verification_pending
implementation: three_mode_home_policy
---

# GoTab 原生浏览器主页迁移

## 目标

把 GoTab 的新标签页信息架构和可取的视觉方向转化为 Kiyori 自己的浏览器主页。主页不是扩展中心中的
CRX，也不是把原生页面伪装成某个 URL，而是由设置控制的三模式策略：Kiyori 原生主页、自定义网址和纯空白页。
当前开发验证阶段保留 `https://web.gotab.cn/` 作为新安装的自定义网址初值；原生主页已成为可立即切换的正式模式，
待目标设备验收后再把新安装初值切换为原生模式。

## 三模式合同

| 模式 | 设置持久化 | 冷启动/新标签 | 主页按钮 | Back 根语义 |
| --- | --- | --- | --- | --- |
| `NATIVE` Kiyori 原生主页 | `home_mode=native`，保留上次自定义网址 | 创建共享会话并显示本地 Dashboard；WebView 仅使用内部 `about:blank` seed | 显示 Dashboard；从网页进入时可返回原页面 | 根 Dashboard 再次 Back 退出 Browser Home |
| `CUSTOM_URL` 自定义网址 | `home_mode=custom_url` + `custom_home_url` | 加载记忆的 HTTP/HTTPS 地址 | 导航当前标签到该地址 | 遵循网页历史，历史耗尽后导航到该地址 |
| `BLANK` 纯空白页 | `home_mode=blank`，保留上次自定义网址 | 加载 `about:blank`，不发起主页网络请求 | 导航当前标签到 `about:blank` | `about:blank` 是明确网页根，历史耗尽后离开 Browser Home |

模式切换立即写入设置并刷新投影，但不重写用户正在浏览的普通网页。下次主页按钮、新标签或冷启动按新模式执行；
切换时如果 Dashboard 已经可见而新模式不是 `NATIVE`，只关闭 Dashboard，不销毁会话或 WebView。所有路径继续复用
唯一 `StandardBrowserSessionTools`、`BrowserToolSession`、Profile、历史、书签、下载、用户脚本和标签注册表。

旧版本只有 `home_url` 时执行一次显式迁移：`about:blank` -> `BLANK`，合法 HTTP/HTTPS -> `CUSTOM_URL` 并保留原值；
损坏或不支持的值直接报告为设置读取错误，不静默改成原生主页。迁移完成后只保留新键 `home_mode` 与 `custom_home_url`。

## 范围

- 新安装浏览器主页验证地址：`https://web.gotab.cn/`
- GoTab 的搜索、分类、网址卡片、背景和小组件作为需求与视觉参考
- Kiyori 原生主页与现有 Browser Home、标签、窗口、书签、历史、搜索和 AI 共用同一 Browser Runtime
- 手机、平板、横屏和窗口尺寸变化下的自适应布局
- 原生主页自己的本地数据、导入/导出、备份和隐私边界

## 非目标

- 不直接安装、解压或执行 `D:\03_Default\下载\GoTab 新标签页 2.1.0.10.crx`
- 不把 GoTab CRX 当成 Kiyori 扩展中心的可安装插件
- 不创建第二个 WebView、第二个标签页注册表、第二个 Cookie/历史/书签 owner 或独立扩展运行时
- 不把 GoTab 官网账号、服务端数据库、远程 API 或后台管理能力当作 Kiyori 本地状态
- 不把 GoTab CRX 放入扩展中心，也不把远程官网当作 Kiyori 原生主页的数据 owner

## 事实基线

- Kiyori 的唯一浏览器运行时由 `StandardBrowserSessionTools`、`BrowserToolSession` 和共享 WebView 持有；
  Browser Home 与 AI 浏览器工具通过同一状态工作。
- `WebSessionBrowserSettingsStore` 以 `BrowserHomeMode` 和 `customHomeUrl` 持有主页设置；`about:blank` 是明确的空白
  seed/document，不再承担原生主页语义。
- 本轮把旧 `home_url` 迁移为显式模式，并把没有主页键的新安装初始化为 `CUSTOM_URL + https://web.gotab.cn/`；已有
  用户保存的主页地址保持自定义网址语义，不由新模式默认值覆盖。
- GoTab 当前仓库发布物主要是编译后的 `web` 目录与 Go 后端二进制；CRX 为 Manifest V3，依赖
  `chrome_url_overrides.newtab`、service worker、书签/历史/标签页/搜索/存储/favicon 权限和广泛主机权限。
- Kiyori 浏览器扩展架构要求 CRX 先经过兼容性分析和转换，不允许直接执行；`.kbx` 的 `NewTabProvider` 是后续
  平台阶段的扩展点，不是本轮官网接入的替代入口。

## 阶段与验收

### M1：官网主页验证

- [x] 新安装主页设置为 `https://web.gotab.cn/`
- [x] 更新设置 JVM 断言、README、CONTEXT 和连续开发 TODO
- [ ] 目标 Android WebView 首帧与移动布局
- [ ] 登录、localStorage、刷新和进程恢复
- [ ] 触摸滚动、拖拽卡片、弹窗、文件选择和壁纸入口
- [ ] Kiyori 地址栏、主页、Back、新标签、标签总览和窗口恢复行为
- [ ] 无网络、代理未就绪、远程 4xx/5xx 和页面脚本错误的可观察结果

### M2：原生主页设计

- [ ] 读取 GoTab 网页版真实功能表现，冻结 Kiyori 需要保留的功能集合
- [x] 定义 `BrowserHomeDashboard` 的页面 owner、状态模型和共享数据投影合同
- [x] 定义快捷访问/最近访问/官网验证入口与 Kiyori 搜索、书签、历史、标签和窗口的映射
- [ ] 定义导入/导出、备份、删除、无痕 Profile 和权限边界
- [ ] 完成手机/平板/横屏和窗口尺寸设计，以及 Back/恢复/进程重建合同

### M3：原生主页实现

- [x] 在现有 Browser Home presentation 内接入原生主页 surface，不复制 WebView 或 session 状态
- [x] 接入唯一导航、搜索、书签、历史、窗口计数和 AI 浏览器能力的现有回调
- [x] 实现主页模式持久化迁移、三模式运行策略和损坏值的显式错误边界
- [x] 增加定向 JVM、架构边界、`git diff --check` 和 formal readiness 验证
- [ ] 串行构建并核验 Debug APK
- [ ] 目标 Android 设备完成触摸、旋转、进程恢复、无痕和真实 WebView 验收

### 当前实现切片

`BrowserHomeDashboard` 位于现有 `WebSessionBrowserScreen` 的 Browser Home 内容区域，由浏览器底栏“主页”动作
打开。显示时 `WebSessionWebViewHost` 会按原有生命周期从容器撤下，但对应 `BrowserToolSession`、Profile、历史、
书签、标签页和导航状态继续由唯一 Browser Runtime 持有。Dashboard 固定提供 GoTab 官网验证入口，书签、历史和
新标签动作关闭 Dashboard 后，通过既有 `onOpenUrl`/`onNewTab` 回调重新挂载同一个 WebView；系统 Back 根据 Dashboard
是否从现有网页进入决定关闭或退出。快捷访问只投影共享书签和网页历史，不新增持久化 owner，也不读取或写入 Cookie。

设置页“网页主页”使用三项可访问的单选行，选择变化立即生效并显示短 Toast；自定义网址编辑器只在 `CUSTOM_URL`
下出现，页面摘要显示 `原生主页`、`自定义网址 · host/path` 或 `纯空白页`，不在设置列表展示完整长 URL。

新安装默认仍是 `CUSTOM_URL + https://web.gotab.cn/`，因此冷启动可以继续直接观察官网 WebView；用户可以立即选择
`NATIVE`，目标设备完成 M1 后再把新安装初值切换为原生模式。

## 风险与决策

- 官网版本依赖远程服务和 GoTab 自己的账号/数据协议，只能作为验证入口；不把网络可达性当作原生主页完成条件。
- GoTab CRX 的 Chrome API 与 Android System WebView 能力边界不同；任何需要扩展权限的功能必须重新映射到
  Kiyori 的显式能力合同，不能通过页面 JavaScript 或特权桥绕过权限。
- 原生主页必须保持 Browser Home 的沉浸式页面、Back 和 presentation lease 合同；主页卡片导航不能创建新的
  session，也不能改变现有窗口的历史根语义。
- Kiyori 当前尚未公开发行，因此本轮允许调整新安装默认值；已保存的用户主页设置仍由偏好 owner 保持，后续
  若进入发布准备，需单独审阅默认主页、隐私、第三方服务和迁移说明。

## 本轮记录

- 2026-09-04：确认 GoTab GitHub `main` HEAD 为 `0aae099`；官网 `https://web.gotab.cn/` 返回 HTTP 200，
  页面包含移动 viewport 和远程 `siteConfig.server_url`。
- 2026-09-04：确认 CRX 文件为约 2.8 MiB 的 CRX3/ZIP，Manifest V3 `version=2.1.0.10`；直接执行排除。
- 2026-09-05：完成三模式状态、一次性迁移、设置 UI、运行时入口与定向 JVM 覆盖；设备、登录、触摸、网络和最终
  原生默认初值仍为 `verification_pending`。
