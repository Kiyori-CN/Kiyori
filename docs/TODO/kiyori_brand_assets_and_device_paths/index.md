---
fork: https://github.com/Kiyori-CN/Kiyori
upstream: https://github.com/AAswordman/Operit
status: verification_pending
---

# Kiyori 品牌、图标与设备路径清理

## 当前基线

- 父仓库为 `main@c1947b1fea58b3ce47a4b0637bdf1c0cec66ba47`，开始调查时与 `origin/main` 对齐且工作树干净
- Android application ID 已是 `com.kiyori`，公共数据根目录已是 `Download/Kiyori`
- Launcher、自适应、单色通知、About 和分享图已引用 Kiyori 图标
- 项目尚未正式发布，本轮不为旧的用户可见品牌或旧默认数据增加兼容分支

## 目标

清理仍会让用户、Android 系统界面、文件选择器、WebChat、工作区模板或终端环境看到 Operit 宿主品牌的内容。只替换显示名称、现有品牌图片引用和当前宿主的具体路径或包引用，不做源码结构重命名。

## 必须保留

- `com.ai.assistance.operit` 与 `com.ai.assistance.operit.terminal` namespace、源码目录、类名和文件名
- `operit://`、既有 Intent action、AIDL、provider authority、ToolPkg、MCP、插件 ID 和 JavaScript bridge key
- `.operit/config.json`、备份 schema、市场 wire type、`OperitForge`、Operit 市场版本和外部生态产品名
- `terminal` 的持久化路径、`installed-rootfs/ubuntu`、`.operit_installed_ok`、`OPERIT_*` 环境变量和 native 文件名
- 上游作者、许可证、历史归属和“基于 Operit 开源代码继续开发”的说明

## 实施顺序

1. [Android 与系统可见品牌](1_android_and_system_surfaces.md)
2. [图片、WebChat、模板与终端](2_assets_templates_and_terminal.md)
3. [残留审计与构建验收](3_validation.md)

## 非目标

- 不增加旧目录自动扫描、复制、迁移、删除或其他回退逻辑
- 不改市场协议、更新通道、发布身份、签名或版本号
- 不安装 APK，不操作设备或远端服务
- 不创建额外分支；按用户后续授权将预期改动提交并推送到父仓库与 `terminal` 各自的 `main`

## 完成标准

- 当前宿主的用户可见品牌统一为 Kiyori，保留项中的 Operit 命中均有明确语义
- Android 快捷方式和外部宿主路径使用 `com.kiyori`，兼容 action 与类路径不变
- Kiyori 图标覆盖 Launcher、通知、About、分享、WebChat favicon 和默认角色头像入口
- `terminal` 的文件选择器与 Ubuntu 配置注释显示 Kiyori，持久化路径不变
- WebChat 资产重新生成，最终 `assembleDebug` 成功，APK 元数据与品牌资源完成核验

## 调查补充

- `WorkspaceUtils` 的路径注释与 `apktool` 的当前宿主沙箱路径也属于本轮范围，统一使用 `com.kiyori`
- QQ Bot 中仅存在于既有 `dist/` 包装器的旧 metadata 会随 ToolPkg 打包，因此只同步其展示文案，不改变入口或逻辑

## 验证结论

- 父仓库与 `terminal` 的 `git diff --check` 通过
- WebChat 生产构建与 Android assets 同步通过；源码、dist 与 Android assets 的 favicon SHA-256 一致
- 正式开发准备门禁通过
- `assembleDebug` 在 4 分 17 秒内成功，APK 元数据为 `com.kiyori`、`45 / 0.1.0`、label `Kiyori`
- APK 包含 Kiyori icon/roundIcon、Kiyori WebChat 与 11 个预置 ToolPkg，不包含旧 `assets/operit.png`
- `terminal` 品牌显示改动已提交为 `e3ee8d1` 并推送到 `origin/main`；父仓库品牌清理随本清单进入 `main`
- 未运行 release、签名、发布、安装或设备操作；真机显示仍为待验证
