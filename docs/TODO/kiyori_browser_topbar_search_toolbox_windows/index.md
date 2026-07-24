---
fork: https://github.com/Kiyori-CN/Kiyori
upstream: https://github.com/AAswordman/Operit
reference: D:/10_Project/kiyori-android@24a2dfa91f0a4166dc58e5c4732d11861173f766
status: verification_pending
---

# 浏览器顶栏、全屏搜索、工具抽屉与窗口重构

本轮以 `D:/10_Project/kiyori-android@24a2dfa91f0a4166dc58e5c4732d11861173f766` 为唯一旧 Kiyori 浏览器布局参考，以当前 Kiyori 的 `WebSessionBrowserHost` 和 `StandardBrowserSessionTools` 为运行时事实来源。参考提交已核对为 `24a2dfa91f0a4166dc58e5c4732d11861173f766`，不修改参考仓库。

## 当前状态

- 基线为 `main@f5d7003c325f28aca1dfbf524c90792e42313854`，与 `origin/main` 同步，父仓库和 `terminal` gitlink 干净
- 上一轮 Browser Home 沉浸式布局已经提交；本轮新改动在用户实测前保持未提交、未推送
- 浏览器顶栏已替换为返回、可点击搜索框和刷新/停止；搜索框进入全屏搜索状态
- 第四底栏入口已作为普通 WebSession 窗口总览，第五入口保留三态工具抽屉并接入真实能力范围
- 人和 AI 继续共用同一进程级 `StandardBrowserSessionTools`、session 注册表、活动 `WebView`、Cookie、历史、书签、下载和 userscript 状态

## 本轮目标

按以下顺序实现一个完整、可真实使用的浏览器 UI 切片：

1. 顶栏改为“返回、搜索框、刷新”，点击搜索框进入全屏搜索页
2. 全屏搜索页复刻参考版的输入、搜索引擎选择、当前网址操作、搜索记录和返回行为
3. 第五按钮保留 Hidden/Partial/Expanded 三态拖动抽屉，并把工具网格改为参考布局中确有运行时能力的按钮
4. 第四按钮改为普通浏览窗口/标签总览，复刻参考版的卡片、关闭、新建和关闭全部动作；普通窗口仍代表当前 WebSession session
5. 重新设计关闭当前标签页和关闭全部标签页动作，并保持现有 close 语义
6. 所有操作继续走同一 Browser Runtime，不复制 WebView，不创建第二套 AI 浏览器状态

## 明确边界

- 不实现没有可验证内核隔离的真正无痕窗口。当前 WebView 使用 `CookieManager.getInstance()`，session 模型没有 profile 字段；只停止写历史不能称为无痕
- 不显示悬浮嗅探、阅读模式、标记广告、网站配置或独立系统工具箱按钮，除非本轮先接通真实能力；禁止占位、禁用后无动作、伪造结果和兼容回退
- `网络日志`只显示当前 WebSession 已经记录的真实请求；`查看源码`只显示当前 WebView 异步读取的真实 DOM 源码
- UA 入口的用户可见名称为“UA标识”，本轮只暴露当前已经存在且可验证的电脑模式/手机模式切换
- 不修改 `D:/10_Project/kiyori-android`，不运行 Release、安装 APK、ADB、MuMu 或设备自动化，不提交、不推送

## 实施顺序

1. `WebSessionBrowserState`、搜索持久化、网络日志和页面源码的状态/回调契约
2. 顶栏与全屏搜索页面
3. UA、网络日志、源码和工具网格
4. 普通窗口总览、关闭动作与 Back/转场整合
5. 文档、静态检查和 Debug APK 验证

详细证据、状态机和逐项能力映射见：

- [1_source_mapping_and_capability_matrix.md](1_source_mapping_and_capability_matrix.md)
- [2_search_chrome_and_window_state.md](2_search_chrome_and_window_state.md)
- [3_toolbox_actions_and_runtime_boundaries.md](3_toolbox_actions_and_runtime_boundaries.md)
- [4_self_qa_and_validation.md](4_self_qa_and_validation.md)

## 实施结果

- [DONE] `WebSessionBrowserHostState`、`WebSessionHistoryStore`、`BrowserAddressResolver` 已接入搜索引擎、最近记录、网络日志和页面源码状态
- [DONE] 顶栏、全屏搜索、五列工具抽屉、UA/网络日志/源码路由和窗口总览已接入现有 WebSession callbacks
- [DONE] 未接通的悬浮嗅探、无痕、阅读模式、标记广告和网站配置仍不显示
- [DONE] `:app:compileDebugKotlin` 与定向 JVM 单测已通过
- [DONE] 项目 `.venv` 正式开发准备门禁与 `git diff --check` 通过；`assembleDebug` 成功，APK 为 `app/build/outputs/apk/debug/app-debug.apk`，大小 `450458986` 字节，SHA-256 `D19250A397A3B88FBBE34BDA1ED24E7EFF57F8D22C81619B30727AF2302A1EEB`，包名 `com.kiyori`，版本 `45 / 0.1.0`，ZIP 16KB 对齐检查通过
- [PENDING] 顶栏视觉、抽屉拖动、窗口切换、Back、IME、旋转、真实网页刷新及 AI 并发操控的真机验收
