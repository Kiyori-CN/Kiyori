---
fork: https://github.com/Kiyori-CN/Kiyori
upstream: https://github.com/AAswordman/Operit
reference: D:/10_Project/kiyori-android@24a2dfa91f0a4166dc58e5c4732d11861173f766
status: verification_pending
---

# 浏览器顶栏、全屏搜索、浏览器菜单与窗口重构

本轮以旧 Kiyori `24a2dfa91f0a4166dc58e5c4732d11861173f766` 为页面结构和视觉密度参考，以当前 Kiyori 的 `StandardBrowserSessionTools`、`WebSessionBrowserHost`、App Shell 和 AI `browser_*` 调用链为运行时事实来源。参考仓库不修改，也不作为运行时依赖。

## 基线与授权

- 基线为 `main@fda9982eaf7344dbc8b0d722b7526dcd15bce13b`，当前与 `origin/main` 同步且干净
- 上一轮 Browser Home 沉浸式 UI 已由用户手动提交推送；本轮源码改动在用户实测前保持未提交、未推送
- 本轮允许修改 Kiyori 源码、测试和相称文档；不修改参考仓库，不运行 Release、安装 APK、ADB、MuMu 或设备自动化
- 只使用唯一 Browser Runtime：session 注册表、活动 WebView、Cookie、历史、书签、下载、userscript 和 AI `browser_*` 工具均保持同源

## 本轮目标

按以下顺序实现一套可正常使用、可由人和 AI 共同操控的浏览器界面：

1. 顶栏固定为返回、搜索框、刷新/停止；顶栏与系统状态栏、网页和底栏视觉融合，不画额外分隔线
2. 点击搜索框进入全屏搜索页，复刻参考版的自动聚焦、搜索引擎选择、当前网址操作、两列搜索记录、提交和转场
3. AI Home 顶栏在终端按钮左侧增加浏览器按钮；从 AI Home 打开浏览器后，顶栏退出回到 AI Home 并保留悬浮球
4. 浏览器底栏第五个入口命名为“浏览器菜单按钮”，打开固定高度、不可拖动的“浏览器下拉抽屉菜单”
5. 主菜单固定为四行：前三行各五个带图标和文字的按钮，第四行三个仅图标按钮；所有按钮均有真实点击结果
6. 主菜单中的未接通能力进入真实空白/说明页，不伪造内核能力，不静默吞掉点击，不改变共享 runtime
7. 浏览器菜单中的“AI对话”回到 AI Home；“退出浏览器”只移除浏览器展示层，保留 session/runtime；清空全部窗口才清理最后 session 和悬浮球
8. 浏览器底栏第四个入口进入普通/无痕窗口总览；普通窗口复用真实 WebSession， 无痕窗口展示隔离能力说明而不伪造 Cookie/cache/WebStorage 隔离
9. 删除旧菜单中的“关闭当前标签页”和“关闭全部标签页”按钮；窗口总览卡片关闭和清空窗口动作继续保留，因为它们是窗口管理的必要操作

## 固定菜单与文案

主菜单四行固定为：

| 行 | 内容 |
| --- | --- |
| 一 | 加书签、书签、历史、下载、插件 |
| 二 | 悬浮嗅探、UA标识、网络日志、AI对话、工具箱 |
| 三 | 无痕模式、阅读模式、查看源码、标记广告、网站配置 |
| 四 | 退出浏览器、收起抽屉、浏览器设置 |

用户在不同消息中使用过“关闭浏览器”和“退出浏览器”两种说法。本轮采用“退出浏览器”作为唯一可见文案，因为该动作退出的是浏览器展示层而不是关闭标签；真正关闭全部 session 仍只存在于窗口总览的清空动作和 AI `browser_close_all` 合同中。

## 重要行为边界

- AI 操控网页时，悬浮球表示同一个 Browser Runtime 的最小化展示。点击悬浮球展开网页后，左上角返回只收缩悬浮球，不调用 WebView `goBack()`，AI 对话和 AI 浏览器操作继续保持
- 浏览器 App Shell 顶栏返回只退出 Browser Home。进入来源存储在 `KiyoriShellState`，从 AI Home 进入时返回 AI Home，从普通根入口进入时返回软件首页
- 网页后退/前进只由底栏后退、底栏前进和 AI `browser_navigate_back` 负责；搜索、窗口总览、菜单和子页拥有自己的返回动作
- “退出浏览器”销毁 overlay/app presentation 的展示对象但保留 session registry；清空全部窗口删除所有 session，最后一个 session 的关闭逻辑才移除悬浮球
- 主菜单固定高度且不响应上下拖动；历史、下载、书签、插件、UA、网络日志、源码和说明页等子页面继续使用 Hidden/Partial/Expanded 三态可拖动抽屉
- 所有抽屉左边缘和右边缘贴屏，不在宽屏上保留居中缝隙

## 实施顺序

1. App Shell：浏览器入口、来源记录、AI Home 顶栏 action、退出/AI 对话回跳
2. chrome：顶栏、全屏搜索、透明融合背景、浏览器菜单固定布局
3. 抽屉/标签：子页拖动、全量菜单按钮、说明页、普通/无痕窗口总览
4. 文档与验证：状态/语义文档、单测和静态检查、正式开发准备门禁、Debug APK 构建与产物核验

## 非目标

- 不在本轮实现真正的无痕 profile、阅读模式转换、页面广告规则、网页嗅探 candidate/runtime 或 per-site 配置 store
- 不新建第二个 WebView、Cookie 管理器、session registry、presentation owner 或 AI 浏览器协议
- 不改变 `browser_*` 工具名、参数、返回结构、Operit 兼容 namespace、持久化 key 和现有 userscript 内部标识
- 不把构建产物、凭据、`work/` checkpoint 或私有配置写入 Git；不提交、不推送

详细证据、状态机、按钮接线、自问自答和验证顺序见：

- [1_source_mapping_and_capability_matrix.md](1_source_mapping_and_capability_matrix.md)
- [2_search_chrome_and_window_state.md](2_search_chrome_and_window_state.md)
- [3_toolbox_actions_and_runtime_boundaries.md](3_toolbox_actions_and_runtime_boundaries.md)
- [4_self_qa_and_validation.md](4_self_qa_and_validation.md)

## 当前状态

- [DONE] 只读核对当前 Kiyori、参考仓库、AI 共用 runtime、悬浮球和 presentation owner
- [DONE] 冻结返回优先级、菜单/子抽屉生命周期、普通/无痕边界和全部按钮清单
- [DONE] Goal `019f931b-720b-74d0-94c5-c264f9f1c269` 已创建并完成 App Shell、chrome、抽屉/标签实施
- [DONE] 正式开发准备门禁、定向 Shell 状态测试、Kotlin 编译和 Debug APK 构建
- [ ] 真机验收：视觉、拖动、系统 Back、IME、旋转、网页刷新、AI 并发操控

## 本地验证证据

- `python -B ci/script/check_formal_readiness.py --repository . --require-main`：PASS（使用项目 `.venv`）
- `git diff --check`：PASS
- `:app:testDebugUnitTest --tests com.ai.assistance.operit.ui.main.shell.KiyoriShellStateTest`：PASS
- `:app:compileDebugKotlin`：PASS
- `:app:assembleDebug --no-daemon --console=plain`：PASS
- APK：`app/build/outputs/apk/debug/app-debug.apk`，449465146 bytes，SHA-256 `F72E6C8A6D61FE9575F3DAB71AE0856727C59E0DD3E6FBF817A14C1AC1968B04`
- APK 元数据：`com.kiyori`，versionCode `45`，versionName `0.1.0`，minSdk `26`，targetSdk `34`
- Android Debug v2 签名：通过；ZIP `16KB` 对齐：通过
- 未运行 Release、安装、ADB、MuMu 或设备自动化；工作树保留未提交状态等待真机实测
