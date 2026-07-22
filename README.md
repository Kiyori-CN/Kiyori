<div align="center">
  <img src="app/src/main/assets/logo.svg" width="160" alt="Kiyori Logo">
  <h1>Kiyori</h1>
  <p>由 Operit AI 驱动的 Android 全能型浏览器开发项目</p>
  <p>
    <a href="README(E).md">English</a> |
    <a href="https://github.com/Kiyori-CN/Kiyori">项目仓库</a> |
    <a href="https://github.com/Kiyori-CN/Kiyori/issues">问题反馈</a>
  </p>
  <p>
    <img src="https://img.shields.io/github/last-commit/Kiyori-CN/Kiyori" alt="Last Commit">
    <img src="https://img.shields.io/github/license/Kiyori-CN/Kiyori" alt="License">
    <img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84" alt="Android 8.0+">
  </p>
</div>

## 项目状态

当前版本为 `0.1.0` 开发基线，Android application ID 为 `com.kiyori`。项目尚未建立公开的正式发布渠道，请从本仓库源码构建开发版，不要将第三方 APK 视为 Kiyori 官方版本。

Kiyori 不连接 Operit 的应用更新、补丁或远程公告服务。当前也不提供应用内自动更新或手动更新检查；项目状态、源码与后续发行信息仅以 [Kiyori 仓库](https://github.com/Kiyori-CN/Kiyori) 为准。

正式开发前的工程门禁、分支规则、兼容性边界和真机验收队列见 [正式开发准备](docs/TODO/formal_development_readiness/index.md)。持续开发只使用 `main` 分支；`terminal` 子模块固定到 KiyoriTerminalCore 的提交。

## 项目简介

Kiyori 是由 Operit AI 驱动的 Android 全能型浏览器，当前代码基于 Operit 演进。浏览器是产品主体，Operit AI 作为内置 AI 子模块负责对话、理解和自动化；网页浏览、视频、音乐、小说阅读、下载、文件管理与广告拦截等能力将逐步形成独立页面，并通过受控接口开放给 AI。

Kiyori 是独立产品，不是 Operit 的品牌替换。项目保留设备端聊天、模型配置、工具调用、工作流、记忆、终端、MCP、Skill、ToolPkg 和本地模型等现有能力，同时由 Kiyori 负责应用壳、全局导航、系统设置、产品发行和后续浏览器能力。

云模型由用户自行选择服务商并配置 API Key、模型与端点，请求由设备直接发往所选服务商。Kiyori 不提供大语言模型推理或聊天请求中转。MNN 与 llama.cpp 等本地模型在模型文件准备完成后可在设备上运行。

## 目标产品结构

以下结构已经确定为正式开发目标，当前仍处于规划和迁移阶段，不表示这些页面已经全部实现：

```text
Kiyori App Shell
├── 软件首页
│   └── 负一屏 ← 软件首页 → AI 首页
├── 浏览器首页
├── 小程序首页
├── 文件管理首页
└── 设置首页
```

应用启动后进入软件首页。中间搜索卡保留独立的“搜索”和“AI”按钮：“搜索”打开只负责网页搜索的全屏搜索页，“AI”进入右侧 AI 首页；历史、书签、文件和小程序分别在自己的页面中提供搜索。底部五入口只在五个根页面显示，负一屏和保持全屏形态的 AI 首页隐藏底栏。AI 首页左上角三横线按钮打开全屏 AI 中心，AI 设置是 AI 中心的子页，也可从设置首页进入。

对话历史、新建和删除对话继续由 AI 首页原有历史选择器负责，不复制到 AI 中心；Terminal 在本阶段继续保留 AI 首页右上角入口。负一屏首期参考旧 Kiyori 设计，至少提供收藏、书签、历史和下载。

AI 中心保留原版“权限”高频入口，但该入口改为打开 Kiyori 权限中心。权限中心以只读总览区分设备能力与 AI 工具授权，点击分项进入各自唯一的设置页面，首页不直接放置权限开关；高影响操作策略与操作记录将在安全合同中继续设计。备份、聊天记录、Token 统计和修改其他应用权限的工具仍保留在各自原有领域。

“权限”高频卡片继续使用 Operit 原版短标签和视觉。徽标只显示当前启用功能缺少的必要设备授权数量，无待处理项时显示“正常”；AI 工具授权策略不计入该徽标。

高影响操作确认与 AI 操作记录共同归入权限中心的“AI 安全”分组。AI 操作按 R0 只读、R1 低影响、R2 高影响、R3 关键操作分级；工具被设为允许也不能绕过 R2 或 R3 的操作确认。对应策略页和记录页完整实现前不显示空入口。

Kiyori 新页面的视觉语言向 Operit 原版 UI 看齐，页面结构、浏览器行为和其他 Kiyori 功能参考 [kiyori-android@24a2dfa9](https://github.com/Kiyori-CN/kiyori-android/tree/24a2dfa91f0a4166dc58e5c4732d11861173f766)。手机、平板与折叠屏共享同一导航状态；手机上的 AI 中心使用全屏页面，较宽窗口在同一路由内使用双栏布局，并避开折叠屏分隔铰链。详细设计见 [Kiyori 产品壳与导航架构](docs/doc-src/architecture/kiyori_product_shell_and_navigation.md)。

## 主要能力

- AI 对话、角色卡、记忆、上下文与多会话管理
- 内置浏览器、网页访问、网页搜索与自动化工具
- 规划中的独立视频、音乐、小说阅读、下载、文件管理和广告拦截页面
- MCP、Skill、ToolPkg、工作流与工具调用
- Ubuntu 终端、文件管理、SSH 与开发工具
- MNN、llama.cpp 本地推理以及可配置的第三方模型服务
- 语音、图片、附件、悬浮窗和 Android 权限集成

部分市场、模型提供方、GitHub 登录、搜索、语音、绘图和用户主动配置的远程能力会访问各自的第三方服务；它们不属于 Kiyori 的更新或公告通道。

## 构建

### 环境要求

- Windows、macOS 或 Linux
- JDK 17
- Android SDK，compile SDK 36
- Git 与 Git submodule 支持

### 获取源码

```bash
git clone --recurse-submodules https://github.com/Kiyori-CN/Kiyori.git
cd Kiyori
```

项目部分大型模型和本地运行库未直接存放在 Git 仓库中。若构建提示缺少 `models`、`subpack`、`jniLibs` 或 `libs` 内容，请按项目内构建配置补齐对应本地依赖。

### 构建 Debug APK

Windows：

```powershell
.\gradlew.bat assembleDebug --console=plain
```

macOS 或 Linux：

```bash
./gradlew assembleDebug --console=plain
```

输出目录为 `app/build/outputs/apk/debug/`。

## 兼容性说明

为避免破坏已有数据和生态兼容，本轮品牌迁移不会直接重命名以下实现标识：

- 源码 namespace `com.ai.assistance.operit`
- `operit://` OAuth 回调及既有 Intent action
- 数据库、偏好、备份、工作区和文件格式标识
- 插件、ToolPkg、MCP 及部分既有文件路径

这些名称不代表当前软件品牌。任何协议级重命名都需要独立的兼容迁移设计。

由于当前 application ID 是新的 `com.kiyori`，历史 `com.ai.assistance.operit` 安装不能直接覆盖。需要先在旧应用中导出备份，再在 Kiyori 中导入；`Download/Operit` 路径和既有备份格式会继续保留。

## 上游与许可证

本项目基于 [Operit](https://github.com/AAswordman/Operit) 源码演进。Operit 的历史作者、贡献者和许可证声明继续保留；Kiyori 的名称、图标、仓库与发行渠道由 Kiyori 项目独立维护。

源码依照仓库中的 [GNU LGPL v3 许可证](LICENSE) 提供。分发修改版本前，请同时核对依赖组件的许可证、品牌与服务条款。

## 反馈

请在 [Kiyori Issues](https://github.com/Kiyori-CN/Kiyori/issues) 提交问题，并附上设备型号、Android 版本、复现步骤和必要日志。不要在 Issue 中上传 API Key、令牌、Cookie、签名材料或私人对话数据。
