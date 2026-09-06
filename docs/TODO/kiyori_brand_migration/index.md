---
fork: https://github.com/Kiyori-CN/Kiyori
upstream: https://github.com/AAswordman/Operit
status: verification_pending
---

# Kiyori 品牌迁移

本页保留 2026-07-22 品牌迁移的范围与验证记录。下文的起点和权限只描述原任务；当前规则见 [品牌与兼容性](../formal_development_readiness/1_brand_and_compatibility.md)，设备验收仍待完成。

## 迁移基线

仓库从 Operit `ef00abc5099187b4665957e9697cb743c81fa154` 复制而来。当时 application ID 已改为 `com.kiyori`，Compose 依赖升级已验证但尚未提交；应用展示身份、项目链接、启动图标、更新与公告仍指向 Operit。

## 目标与范围

建立展示名、图标和仓库入口一致的 Kiyori 开发基线，应用不再访问 Operit 更新或远程公告。

- 将 `versionCode` 设为 `45`、`versionName` 设为 `0.1.0`，使其可更新当时已安装的同签名 `com.kiyori` 版本 `44`。
- 更新应用壳、关于页、分享图片、服务名称和支持语言中的用户可见品牌。
- 项目、帮助、发行与反馈链接统一指向 `https://github.com/Kiyori-CN/Kiyori`。
- 移除启动更新检查、关于页更新动作、补丁/完整包下载界面、远程公告轮询和弹窗。
- 从原任务指定的本地 `kiyori-android` 参考项目同步启动、自适应、圆形、应用内与 README 图标。
- 同步中英文 README 与项目上下文。

## 原任务非目标

- 不重命名源码 namespace 或 Kotlin/Java 类名。
- 不改写持久化、备份、ToolPkg/插件协议、Intent action 和 `operit://` 兼容 URI。
- 更新与公告停用不影响第三方供应商、Package/Skill/MCP 市场。
- 原任务不包含提交、推送、发行签名或 Release APK。

## 验收与原记录结果

| 验收项 | 2026-07-22 记录 |
| --- | --- |
| 启动与关于页不再主动获取 Operit 更新或公告 | 静态审查通过；运行时也未保留远程 denylist 或上游 quick-setup 下载入口 |
| Manifest、应用资源与项目链接使用 Kiyori | 静态审查通过 |
| 差异无空白错误 | `git diff --check` 通过 |
| Debug APK 可构建 | `assembleDebug` 通过，包为 `com.kiyori / 45 / 0.1.0` |
| 启动器、关于页、分享图片、通知和助手选择 | 待 Android 真机验收 |

资产与入口细节见 [身份、渠道与资产](1_IdentityChannelsAndAssets.md)。历史构建通过不代表当前提交或当前设备已验收。
