---
fork: https://github.com/Kiyori-CN/Kiyori
status: verification_pending
baseline: c036a03e
---

# 设置页信息架构与统一视觉

## 目标

设置首页继续保持固定 `4/4/4/4` 十六入口，但第一组不再保留没有真实页面的“剪贴板口令”和
独立“小程序订阅”。本阶段把原综合 AI 设置按实际状态所有者拆分，并让新增设置根页统一使用
文件下载器设置页已经确立的视觉与交互组件。

第一组从上到下固定为：

1. 我的账号
2. AI助手
3. 语音服务
4. 小程序

第二组从上到下固定为：

1. 网页浏览器
2. 视频播放器
3. 音乐播放器
4. 文档阅读器

最后一组从上到下固定为：

1. 界面定制
2. 数据备份
3. 开发手册
4. 更多功能

## 信息归属

| 设置根 | 内容 | 唯一状态所有者 |
| --- | --- | --- |
| 我的账号 | 进入标题同名的账号根页；GitHub 登录状态、登录与退出 | `GitHubAuthPreferences` |
| AI助手 | 进入现有“AI 助手”根页；用户偏好、模型/API、功能模型、提示词、人设、分句模式、上下文总结、AI 工具授权、Token 统计、外部 HTTP 对话 | 原有 AI preference、repository 与 `ToolPermissionSystem` |
| 语音服务 | TTS、STT、语音测试入口 | `SpeechServicesPreferences` 与现有语音 runtime |
| 小程序 | 本轮只保留 Settings Home 空按钮，等待底部第三个“小程序”产品域建立真实管理页 | 尚未建立 |
| 界面定制 | 语言、主题与外观、全局显示、布局调整 | 原有显示与主题 preference |
| 数据备份 | 进入现有数据根页；聊天及记忆数据备份、聊天历史管理 | 原有备份与聊天 repository |
| 开发手册 | 保留开发文档产品入口，不建立第二套终端、工具箱或开发模式状态 | 尚未建立 |
| 更多功能 | 统一设置风格的系统能力子页；“权限”进入原设备能力页面 | `Screen.ShizukuCommands` 及其现有权限 owner |
| 网页浏览器 | 普通网站 Cookie 清理 | `CookiePrivacyManager` |

移动入口只改变信息架构和导航，不复制、迁移或改写任何持久化状态。“小程序”严禁连接
AI 包管理、脚本包、ToolPkg、插件市场或 AI 抽屉路由；小程序订阅能力以后如有真实 owner，应
进入底部第三个“小程序”产品域内部，不重新占用设置首页入口。

## 视觉合同

- AI助手、我的账号、语音服务、界面定制和数据备份继续进入现有
  `KiyoriCollapsingSettingsPage`
- 分组统一使用标题、说明、`16dp` 白色圆角卡片、双行设置项和 `0.6dp` 分隔线
- 可选择项继续使用文件下载器设置页的 `26dp` 圆角底部面板
- 模态 AI 抽屉底部显示通用“设置”，启动 `KiyoriSettingsOrigin.AI_HOST` 的来源保持型设置会话；
  AI 助手详情由设置首页同名入口进入并显示返回
- 账号、语音、界面和备份根页使用 `RouteEntrySource.KIYORI_SETTINGS`，并携带活动设置
  `navigationContextId/sessionId`；根页面 Back 恢复同一 Settings Home，内部子页 Back 先返回
  对应设置根，来源保持会话最后再返回原浏览器或 AI 页面
- 小程序和开发手册保持空动作；更多功能进入 `KiyoriSettingsRoute.MORE_FEATURES`，其中“权限”
  使用当前 settings `sessionId` 和 `RouteEntrySource.KIYORI_SETTINGS` 打开原
  `Screen.ShizukuCommands`，不建立第二权限页面或状态
- 首页 16 个入口分别使用 16 个不同图标和 16 组固定浅深色图标容器；详情页继续使用全应用
  `KiyoriSemanticTone` 身份，但通过 Settings Surface 专用低饱和浅深色对渲染，不把首页专用
  色板扩散到业务状态语义，也不改变文件管理、工具箱等非设置界面
- 顶栏第 4 个按钮在自身下方展开“跟随系统 / 浅色模式 / 深色模式”，直接写入唯一
  `UserPreferencesManager` 主题 owner；菜单固定宽度为 `156dp`，按钮按当前有效主题显示太阳或月亮
- 网页浏览器、视频播放器和文件下载器详情标题直接复用设置首页同名文案，不再追加“设置”
- 浏览器菜单与 AI 抽屉共用 `KiyoriSettingsNavigationState`，不切换到底部设置主目的地；
  覆盖式设置首页隐藏底部五入口，分类和子页按 `KiyoriSettingsRoute` 栈逐级返回

## 串行实施

1. [DONE] 更新 Settings Home 入口、动作与路由来源
2. [DONE] 把旧综合 AI 设置收敛为 AI 助手专属分组
3. [DONE] 新增账号与连接、界面定制、数据备份与同步设置根
4. [DONE] 把语音服务外壳统一为文件下载器设置页风格
5. [DONE] 把 Cookie 清理迁入网页浏览器“网站权限与数据”
6. [DONE] 更新语义文档、README、定向测试并执行正式门禁和 Debug APK 构建核验
7. [DONE] 将设置首页短标签收敛为两组固定四项，并建立 16 图标、16 色的一一对应合同
8. [DONE] 将账号首页与详情标题统一为“我的账号”，将数据根标题统一为“数据备份”，并新增
   主题快捷菜单与 Settings Surface 详情图标色板
9. [DONE] 将第二组末项改为“文档阅读器”，统一浏览器/播放器/下载器同名标题，缩窄
   主题菜单，并建立浏览器与 AI 来源保持型设置首页返回链
10. [DONE] 用 `KiyoriSettingsNavigationState` 和 capability-level `KiyoriSettingsRoute`
    替代设置类 `KiyoriShellChild`、`childBackTarget` 与浏览器局部子页状态；Browser/AI 来源最终
    恢复原页面和原路由栈
11. [DONE] 新增“更多功能”设置子页和“权限”导航项，复用原设备能力 owner，并建立
    权限页 -> 更多功能 -> 设置首页的逐级 Back 合同

## 验收边界

自动测试和 Debug APK 不能证明手机、平板、横屏下的折叠标题、长表单、登录弹窗、返回手势和
输入法体验。实现与本地构建完成后仍需目标设备验收。

## 本地验证证据

- `KiyoriSettingsPagesTest` 为 `10/10`，`KiyoriShellStateTest` 为 `42/42`，零失败、零错误、
  零跳过；覆盖设置首页顺序、小程序空动作、拆分后的能力归属、浏览器 Cookie 行与新增根路由恢复
- `python -B ci/script/check_formal_readiness.py --repository . --require-main` 通过
- `git diff --check` 通过；工作树仅保留本轮未提交修改
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` 在 `94.6s` 内完成，
  `233` 个任务，`BUILD SUCCESSFUL`
- Debug APK：
  `app/build/outputs/apk/debug/app-debug.apk`，`482597235` bytes，SHA-256
  `B69DBAD0C2A69733D67759F54FF9DE68A55BE83C78D012D9A72108405A406315`
- APK 为 `com.kiyori`、`45 / 0.1.0`、min 26、target 34、`arm64-v8a`；Android Debug v2
  签名与 `zipalign -c -P 16 -v 4` 验证通过
- 未安装 APK、未操作设备；折叠标题、长语音表单、GitHub 登录弹窗、来源相关返回和横竖屏视觉
  保持 `verification_pending`

### 2026-08-14 增量验证

- 设置/Shell、浏览器菜单/布局和外部入口定向 JVM 共 `82/82`，architecture boundary
  `107/107`，Kotlin 编译、formal readiness、七语种 XML、worktree Markdown 链接和
  `git diff --check` 均通过
- Debug 构建为 `232` 个任务、`22 executed / 210 up-to-date`；APK 为 `471035851` bytes，
  SHA-256 `28BFBC295AEA0433C279FC7EFCF9BE6FF37468E306B5E3C33CCBF4384CAD821C`
- APK 身份为 `com.kiyori / 45 / 0.1.0 / 26 / 34 / 37`，Android Debug V2 单 signer，
  16 KB ZIP 对齐通过；目标设备视觉与交互仍保持 `verification_pending`

### 2026-08-19 更多功能与权限入口增量

- 设置首页“更多功能”已从空动作改为 `KiyoriSettingsRoute.MORE_FEATURES`，使用与其他设置详情
  一致的折叠标题、系统能力分组卡和双行导航项
- “权限”通过当前 settings `sessionId` 与 `RouteEntrySource.KIYORI_SETTINGS` 打开原
  `Screen.ShizukuCommands`；权限根页 Back 恢复更多功能，再 Back 返回设置首页
- `KiyoriSettingsPagesTest` `14/14`、`KiyoriShellStateTest` `71/71`，architecture
  `PASS (phase=m03)`、architecture 单元测试 `109/109`、formal readiness 和
  `git diff --check` 通过
- 规定 Debug 构建为 `BUILD SUCCESSFUL in 2m 2s`；最终 APK 为
  `app/build/outputs/apk/debug/app-debug.apk`，`472553854` bytes，SHA-256
  `8C08C8D7150BBDB56EE1018BFE1C24FDB07C70F453A3F0AB9ABA74DF267A87E1`，
  包/版本/SDK、唯一 launcher、arm64-only、Android Debug V2 单 signer 与 16 KB ZIP 对齐通过
- 未安装或操作设备；真实点击、逐级 Back 与浅深主题视觉保持 `verification_pending`
