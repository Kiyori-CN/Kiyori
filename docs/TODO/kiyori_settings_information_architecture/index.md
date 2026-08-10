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
| 更多功能 | 保留未来产品能力入口，不聚合或复制现有设置状态 | 尚未建立 |
| 网页浏览器 | 普通网站 Cookie 清理 | `CookiePrivacyManager` |

移动入口只改变信息架构和导航，不复制、迁移或改写任何持久化状态。“小程序”严禁连接
AI 包管理、脚本包、ToolPkg、插件市场或 AI 抽屉路由；小程序订阅能力以后如有真实 owner，应
进入底部第三个“小程序”产品域内部，不重新占用设置首页入口。

## 视觉合同

- AI助手、我的账号、语音服务、界面定制和数据备份继续进入现有
  `KiyoriCollapsingSettingsPage`
- 分组统一使用标题、说明、`16dp` 白色圆角卡片、双行设置项和 `0.6dp` 分隔线
- 可选择项继续使用文件下载器设置页的 `26dp` 圆角底部面板
- AI 助手详情页从模态抽屉进入时折叠标题左侧显示菜单；从设置首页进入时显示返回
- 账号、语音、界面和备份根页使用 `RouteEntrySource.KIYORI_SETTINGS`，根页面 Back 直接返回
  Settings Home，内部子页 Back 先返回对应设置根
- 小程序、开发手册和更多功能保持空动作，不建立页面、状态或与 AI 包管理之间的路由
- 首页 16 个入口分别使用 16 个不同图标和 16 组固定浅深色图标容器；详情页继续使用全应用
  `KiyoriSemanticTone` 身份，但通过 Settings Surface 专用低饱和浅深色对渲染，不把首页专用
  色板扩散到业务状态语义，也不改变文件管理、工具箱等非设置界面
- 顶栏第 4 个按钮在自身下方展开“跟随系统 / 浅色模式 / 深色模式”，直接写入唯一
  `UserPreferencesManager` 主题 owner；按钮按当前有效主题显示太阳或月亮

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
