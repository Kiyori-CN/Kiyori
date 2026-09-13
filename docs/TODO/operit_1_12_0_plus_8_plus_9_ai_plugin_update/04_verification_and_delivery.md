# 验证与交付

## 定向验证

- ToolPkg 注册、解析、ChatMessage Hook 和 Hook 超时预算
- 角色卡 `SoftwareSettings` 工具与 JavaScript/TypeScript 映射
- 市场 `1.12.0+9` 最小版本判断、协议确认和修订提交
- 待发送消息队列、超大消息读取、选中角色卡发送
- Tool Call 默认、DeepSeek low、粘贴附件和记忆规则
- 内置 ToolPkg 源码与发布产物一致性

## 项目门禁

- `git diff --check`
- 项目 Markdown 与资源检查
- `python -B ci/script/check_formal_readiness.py --repository . --require-main`
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain`
- Debug APK 路径、时间、大小、SHA-256、包名、版本、唯一 Launcher、签名和 16 KB 对齐

## 设备边界

本轮不安装 APK。市场下载/安装、聊天消息 Hook、超时 Toast、长消息切换和第三方插件 UI
在目标设备上的结果保持 `verification_pending`。

## 当前证据

- `message_insert` TypeScript 编译通过，`src/main.ts`、`src/shared.ts` 与
  `src/ui/index.ui.ts` 已同步生成三个 `dist` 文件
- ToolPkg、市场兼容、队列隔离与粘贴识别共 4 个 JVM suite、`14/14` 项通过
- 上述 JVM 命令同时完成 Debug Kotlin、Room/KAPT 与资源编译
- 正式开发准备门禁通过，Markdown 检查器单测 `7/7`，`git diff --check` 无错误
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` 为
  `BUILD SUCCESSFUL in 1m 31s`，238 个任务中 34 executed / 204 up-to-date；
  `verifySingleDebugLauncher` 与 `verifyDebugPlayerRuntimePackaging` 通过
- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`，生成时间
  `2026-08-08 22:55:52 +08:00`，大小 `471598782` 字节，SHA-256
  `D0B162DAD2AC9669F09292DCC108C9C858C74CFEC30DF9EE3711365A4C48FFE4`
- APK 为 `com.kiyori / versionCode 45 / versionName 0.1.0 / compileSdk 37 / minSdk 26 /
  targetSdk 34 / arm64-v8a`；唯一 Launcher 为
  `com.ai.assistance.operit.ui.main.MainActivity`，Android Debug V2 单签名，
  `zipalign -c -P 16 -v 4` 通过
- 生成和 APK 内嵌的 `message_insert.toolpkg` 均为 `18320` 字节，SHA-256
  `A1EB5B57BBC5168D75A3E6FCBD2906EC1A64B1D0576F0274B0D34D9FFA4CED28`，
  两者逐字节一致；内嵌包已确认包含并行采集、可配置截止时间、天气不请求地址、
  脱敏诊断日志和设置界面

[DONE]
