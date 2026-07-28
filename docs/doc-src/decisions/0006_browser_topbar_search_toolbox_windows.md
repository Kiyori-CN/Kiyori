---
status: accepted
reference: D:/10_Project/kiyori-android@24a2dfa91f0a4166dc58e5c4732d11861173f766
---

# 浏览器顶栏、全屏搜索、浏览器菜单与窗口重构决策

## 决策摘要

本轮继续使用旧 Kiyori 固定提交作为页面结构参考，使用 Kiyori 当前 WebSession runtime 作为能力边界。Browser Home、overlay 和 AI `browser_*` 工具始终共用一套 `StandardBrowserSessionTools`，不因 UI 重构复制 WebView、Cookie 或 session registry。

## AI 协同展示与退出

- AI 工具首次展示网页时创建最小化 overlay/indicator；用户点击 indicator 后展开同一个 WebView
- overlay 展开态左上角返回只收缩 indicator，不调用 WebView history，保证 AI 操控不被人类 chrome 动作打断
- AI Home 顶栏在终端左侧加入浏览器按钮。它确保现有 overlay lease/session 存在，记录 `AI_HOME` Browser return source，再把同一个 WebView 借给 App Shell Browser Home
- App Shell Browser Home 顶栏返回按来源退出：AI Home 来源回 AI Home，普通根入口回 Software Home；释放 app presentation 后 indicator 恢复
- 浏览器菜单 `AI对话` 与上述 AI Home 回跳语义相同
- 浏览器菜单 `退出浏览器` 只移除展示层/indicator，保留 sessions 和 activeSessionId；窗口总览清空或 AI `browser_close_all` 才删除所有 session

## 顶栏与网页融合

顶栏从左到右为返回、搜索框、刷新/停止。底栏仍为后退、前进、主页、窗口、浏览器菜单。网页后退不再由顶栏负责，避免 overlay 返回打断 AI。顶栏、底栏、透明系统栏和网页共用 edge-to-edge 背景，移除 1dp 分隔线、tonal elevation 和 shadow 分割；WebView 不新增颜色猜测协议。

## 浏览器下拉抽屉菜单

底栏第五入口称为“浏览器菜单按钮”，弹出的 host 称为“浏览器下拉抽屉菜单”。主菜单固定高度、全宽、不可拖动，四行内容固定为：

1. 加书签、书签、历史、下载、插件
2. 悬浮嗅探、UA标识、网络日志、AI对话、工具箱
3. 无痕模式、阅读模式、查看源码、标记广告、网站配置
4. 退出浏览器、收起抽屉、浏览器设置

前三行 cell 显示图标和文字，第四行只显示图标但保留无障碍 contentDescription。旧的关闭当前/全部标签动作从主菜单删除；窗口总览仍保留卡片关闭和清空窗口，因为清空窗口是明确的 runtime 清理动作。

历史、下载、书签、插件、网络日志、源码和说明页属于可拖动子抽屉；主菜单与子抽屉使用不同 composable host，所有抽屉左右贴屏。UA 标识在产品补全第七里程碑中改为主菜单收起后直接显示的居中模态弹窗，不再进入子抽屉。未接通能力进入真实说明页，不使用无动作按钮、Toast 或假状态。

## 普通/无痕窗口

第四底栏入口复刻普通/无痕 selector 与窗口卡片页。普通模式投影真实 `browserState.tabs`，新建/选择/关闭/清空调用现有 session callbacks。无痕模式只呈现 selector、空状态和隔离说明；由于当前 `CookieManager.getInstance()`、WebView data store 和 session 模型没有 profile 隔离，不能创建伪无痕。真正无痕另立 runtime/profile 设计。

## 全屏搜索

搜索页是 host presentation overlay，不改变活动 WebView。它复刻参考版的自动聚焦 URI 输入和九个搜索引擎，并以当前引擎图标、覆盖式淡蓝引擎面板、页面标题/网址双行操作区和自适应历史标签呈现现代化布局。历史删除由垃圾桶显式进入编辑模式，标签叉号只暂存单条删除，点击“完成”后再写入 `WebSessionHistoryStore`；“清空”显示底部确认框，确认后直接清空同一 store 并退出编辑模式。搜索提交通过 `BrowserAddressResolver` 和 `openUrlOnMain` 进入共享 runtime，搜索引擎与记录继续由 `WebSessionHistoryStore` 持久化。

搜索页内部尺寸对齐浏览器下拉抽屉菜单，而不是内容页面大标题：普通操作图标约 `19–21dp`、历史垃圾桶
`23dp`、标签文字约 `10–12sp`，引擎卡与所有垂直间距保持紧凑。点击引擎面板周围区域会关闭面板；
点击当前网页标题与网址区域只返回已存在的活动 WebView，不重新导航。

## 验收边界

- 源码实现与 Debug APK 构建是本轮本地验收；真机视觉、拖动、系统 Back、IME、旋转、真实网页刷新和 AI 并发操作仍为 `verification_pending`
- 本轮不运行 Release、安装、ADB、MuMu 或设备自动化
- 本轮改动不提交、不推送，等待用户实测

完整证据、自问自答和逐项接线见 [专项 TODO](../../TODO/kiyori_browser_topbar_search_toolbox_windows/index.md)。
