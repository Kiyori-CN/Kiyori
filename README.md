<div align="center">
  <img src="app/src/main/assets/logo.svg" width="160" alt="Kiyori Logo">
  <h1>Kiyori</h1>
  <p>Kiyori 品牌的 Android AI 浏览器与智能助手开发项目</p>
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

## 项目简介

Kiyori 是面向 Android 的 AI 浏览器与智能助手项目，当前代码基于 Operit 演进。应用保留设备端聊天、模型配置、工具调用、内置浏览器、工作流、记忆、终端、MCP、Skill、ToolPkg 和本地模型等能力，并逐步建立独立的 Kiyori 产品身份与发行边界。

云模型由用户自行选择服务商并配置 API Key、模型与端点，请求由设备直接发往所选服务商。Kiyori 不提供大语言模型推理或聊天请求中转。MNN 与 llama.cpp 等本地模型在模型文件准备完成后可在设备上运行。

## 主要能力

- AI 对话、角色卡、记忆、上下文与多会话管理
- 内置浏览器、网页访问与自动化工具
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

## 上游与许可证

本项目基于 [Operit](https://github.com/AAswordman/Operit) 源码演进。Operit 的历史作者、贡献者和许可证声明继续保留；Kiyori 的名称、图标、仓库与发行渠道由 Kiyori 项目独立维护。

源码依照仓库中的 [GNU LGPL v3 许可证](LICENSE) 提供。分发修改版本前，请同时核对依赖组件的许可证、品牌与服务条款。

## 反馈

请在 [Kiyori Issues](https://github.com/Kiyori-CN/Kiyori/issues) 提交问题，并附上设备型号、Android 版本、复现步骤和必要日志。不要在 Issue 中上传 API Key、令牌、Cookie、签名材料或私人对话数据。
