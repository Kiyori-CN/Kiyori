---
status: completed
---

# 共享运行时、验证与交付

## 人与 AI 共用合同

### 不变量

- `ToolGetter.getBrowserSessionTools()` 继续返回 `StandardBrowserSessionTools.getSharedInstance()`
- `StandardBrowserSessionTools.sessions` 和 `activeSessionId` 继续是进程内唯一标签 registry
- `BrowserPresentationCoordinator` 继续只管理 presentation lease，不拥有标签
- `WebSessionBrowserHost.attachActiveWebView()` 继续把同一个 WebView 指向 APP_SHELL 或 OVERLAY 容器
- `ToolRegistration` 的所有 `browser_*` 工具继续调用相同 tool executor
- UI 通过 callbacks 发命令并观察 host projection，不直接读取或写入 AI 对话状态

### AI 操作与可见反馈

| AI 动作 | Browser Home 可见结果 |
| --- | --- |
| `browser_navigate` | 当前地址、标题、加载状态和 WebView 同步变化 |
| `browser_tabs` 新建或切换 | 标签总数和活动卡片同步变化 |
| `browser_close` | 当前标签消失，runtime 选择下一活动标签 |
| `browser_close_all` | 计数归零并显示无标签状态 |
| `browser_type`、`browser_click` | 直接作用于当前可见 WebView |
| `browser_resize` | viewport 投影更新，不改变 App Shell owner |
| userscript 触发 UI | 共享 `sheetRoute` 打开 Userscripts 抽屉 |

本轮不增加“AI 正在操作”遮罩。人与 AI 同时操作时以 runtime 实际命令顺序为准，UI 只反映最终共享状态。

## 实施切片

### 切片一：App Shell 沉浸式边界

- `showsBottomBar` 排除 Browser Home
- Browser Home 删除 `80dp` 底部 padding
- 更新 Shell 合同测试和产品文档

### 切片二：浏览器底栏与标签总览

- 替换底栏中央新建标签为主页
- 标签按钮显示总数
- 建立全屏标签卡片网格和底部三动作区
- 删除旧列表式 tab sheet

### 切片三：工具箱与抽屉

- 建立 `chrome/` 目录和三态抽屉
- 建立五列工具箱、下载计数和动态 userscript 命令区
- 重接历史、收藏、下载、Userscripts、模式和关闭 callback
- 删除旧纵向菜单 sheet

### 切片四：受约束内容和文档

- 调整四个功能页在固定抽屉高度中的滚动和 empty state
- 更新 `CONTEXT.md`、架构文档和 TODO 状态
- 静态审计不变量和旧 UI 零引用

## 自动验证

用户已明确授权最终 Debug APK 构建。验证顺序固定为：

1. `python -B ci/script/check_formal_readiness.py --repository . --require-main`
2. `git diff --check`
3. `rg` 检查旧 bottom toolbar、menu sheet 和 tab sheet 不再被引用
4. 审计 `ToolGetter`、`BrowserPresentationCoordinator`、`WebSessionBrowserHost` 和 `ToolRegistration` 的共享实例路径未改变
5. 串行运行 Debug APK 构建，不运行 release 或其他变体
6. 核对 `app/build/outputs/apk/debug/app-debug.apk` 的时间、大小和 SHA-256
7. 使用 Android build tools 核对包名、版本、minSdk、targetSdk 和 Debug v2 签名

正式准备脚本或构建失败时修复真实原因。不得跳过检查、扩大 allowlist、保留旧 UI 分支或加入运行时降级路径。

## 代码审计清单

- [x] Browser Home 外层无全局底栏和 `80dp` 占位
- [x] 浏览器底栏只有冻结的五项，顺序正确
- [x] 主页动作复用活动标签和现有导航 callback
- [x] 标签数字表示总数而不是活动序号
- [x] tab overview 只读取 host projection
- [x] 工具箱五列映射和底部三动作区完整
- [x] 下载进行中和失败计数来自同一 browser state
- [x] Userscripts 完整说明和动态页面命令保留
- [x] 抽屉 drag 不安装到可滚动内容
- [x] Back 优先级与 host `handleBack()` 一致
- [x] AndroidView 在覆盖层出现时不被条件移除
- [x] 没有新的 WebView、session map、Cookie owner 或数据库
- [x] 没有 X5/TBS 依赖、兼容开关或旧 UI 路由

## 本地交付证据

- 正式开发准备：`.venv/Scripts/python.exe -B ci/script/check_formal_readiness.py --repository . --require-main` 通过
- 补丁卫生：`git diff --check` 通过；换行提示来自既有 CRLF 文件与仓库 LF 契约，不是空白错误
- 旧界面：源码与测试中 `WebSessionBottomToolbar`、`WebSessionMenuSheet`、`WebSessionTabSheet` 零引用，三个定义文件已删除
- 共享运行时：`ToolGetter.getBrowserSessionTools()` 仍返回 `StandardBrowserSessionTools.getSharedInstance(context)`；`ToolRegistration` 的 `browser_*` executor 仍调用该入口；`core/tools` 与 `core/browser/presentation` 无本轮差异
- Debug 构建：`gradlew.bat :app:assembleDebug --no-parallel --stacktrace` 最终通过，`230` 个任务中 `29` 个执行、`201` 个为最新状态
- APK：`app/build/outputs/apk/debug/app-debug.apk`，本地时间 `2026-07-24 14:01:21 +08:00`，大小 `442753228` 字节，SHA-256 `0C90E5EF720F7618EC632BA4DDAE9E11DE522D10F16D1D7C93ABC8AAD3BB4B38`
- Manifest：包名 `com.kiyori`，`versionCode 45`，`versionName 0.1.0`，`minSdk 26`，`targetSdk 34`，`compileSdk 36`
- 签名与对齐：Android Debug 证书，APK Signature Scheme v2 验证通过，v1/v3/v3.1/v4 均未使用；`zipalign -c -P 16 -v 4` 验证成功
- 未运行 release、单元测试、安装、ADB、MuMu 或设备自动化；用户可见与设备行为继续按下方实测清单验收

## 用户实测清单

- 从软件首页点击底部第二项，确认全局五项底栏消失且浏览器底栏贴合系统导航区
- 后退、前进、主页、标签页和工具箱五项均可点击且无误触
- 打开多个标签，计数、切换、单独关闭、新建和关闭全部正常
- 工具箱五列在常用手机宽度无重叠，下载状态和模式标题正确
- Partial 抽屉可上拉全屏、下拉回 Partial、再次下拉关闭；列表滚动不带动抽屉
- 历史、收藏、下载和 Userscripts 的内容与操作正常
- 地址输入时键盘、旋转屏幕和系统 Back 顺序正确
- Browser Home 返回 Software Home 后全局底栏恢复
- 切出再返回时网页不刷新，滚动、输入、登录和媒体状态不丢失
- Browser Home 可见时让 AI 导航、点击、输入、切换和关闭标签，确认人与 AI 看到同一页面
- overlay 与 Browser Home 往返时没有白屏、重复标签、WebView 崩溃或页面 reload

## 用户验收结果

用户于 `2026-07-24` 完成本轮实测并反馈“测试没什么问题”。该反馈关闭本轮沉浸式 Browser Home 切片；后续顶栏、全屏搜索、工具抽屉和普通/无痕窗口属于新的独立重构任务。

## 交付边界

Debug 构建只能证明代码和资源可以打包，不能证明抽屉手感、系统 Back、WebView 页面保持、输入法、横屏或人与 AI 并发操作已通过。实现和构建完成后：

- 实现交付时日记以 `verification_pending` 关闭；用户实测通过后，本 TODO 更新为 `completed`
- Goal 在所有本地要求完成后标记完成
- 工作树保留未提交状态
- 不提交、不推送、不安装 APK
- 最短恢复动作是用户安装报告中的 Debug APK并执行上述实测清单
