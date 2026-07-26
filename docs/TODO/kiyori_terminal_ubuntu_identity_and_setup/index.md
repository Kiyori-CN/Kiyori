---
fork: https://github.com/Kiyori-CN/Kiyori
upstream: https://github.com/AAswordman/Operit
status: verification_pending
---

# Kiyori 终端与 Ubuntu 身份及首装环境优化

## 当前基线

- 父仓库为 `main@1ce1f6433da8ae09d9ee401cb324c529de164b8d`，工作树包含用户正在进行的浏览器下载与设置修改
- `terminal` 子模块为干净的 `main@6883ecb4a2e88ad038ad052dff0c1f53d17f9064`
- Kiyori 从未发布，不保留旧 Operit 用户可见横幅或旧展示名称
- Ubuntu rootfs 是标准 Ubuntu 24.04.1 LTS 资产；发行版名称、系统目录和 root 用户配置不属于 Kiyori 品牌迁移范围

## 问题

终端在 Ubuntu 会话就绪后仍绘制 Operit ASCII 横幅。Node.js 一键配置又在 npm 安装 pnpm 后改用 pnpm 的独立全局目录安装 TypeScript；该目录不在终端固定 PATH 中，导致首次安装报错。安装页和父应用只验证 pnpm 文件或命令，可能在 TypeScript 不可用时误报环境就绪。

## 目标

- 将终端会话欢迎横幅改为适合手机宽度的 Kiyori 标识
- 保持 Ubuntu 发行版身份真实，不修改 `os-release`、`issue`、hostname 或 rootfs 目录
- 让 Node.js、pnpm 与全局 TypeScript 通过一份共享契约完成安装和就绪判断
- 用单元测试与正式开发门禁阻止旧横幅和不完整工具链再次出现
- 保留父仓库现有用户修改，不提交、不推送、不安装 APK

## 兼容边界

必须保留：

- `com.ai.assistance.operit`、`com.ai.assistance.operit.terminal`、AIDL 和现有外部调用契约
- `installed-rootfs/ubuntu`、`.operit_installed_ok`、`OPERIT_*`、隐藏命令 marker、chroot 临时文件名和 native 库名
- Ubuntu 发行版名称、标准系统目录和上游许可证归属

允许修改：

- 用户可见欢迎横幅、终端说明和 Kiyori 自有展示文字
- Node 工具链安装命令、就绪判断及其测试
- 与本次语义变化直接相关的父子仓库文档和正式门禁

## 实施顺序

1. [身份与兼容边界](1_identity_and_compatibility.md)
2. [Node 工具链首装契约](2_node_toolchain_setup.md)
3. [验证与 APK 交付](3_validation.md)

## 完成标准

- 新终端会话不再显示 Operit 横幅，并明确显示 Kiyori Ubuntu 环境
- 一键配置后 `node`、`pnpm`、`tsc` 均可执行，可见会话与 AI 后台命令使用同一 npm 全局目录
- 安装页和父应用不会在 TypeScript 缺失时误报就绪
- 定向测试、正式准备门禁、`git diff --check` 和父仓库 Debug APK 构建通过
- rootfs、持久化路径和兼容标识没有发生不必要改名

## 当前结果

- Kiyori 欢迎横幅、Node 工具链共享契约、父应用就绪判断、回归测试和正式品牌门禁已完成
- `terminal:testDebugUnitTest`、正式门禁 Python 测试、`check_formal_readiness.py --require-main` 与父子仓库 `git diff --check` 通过
- `:app:assembleDebug` 成功；APK 为 `com.kiyori`、`45 / 0.1.0`、label `Kiyori`
- 真机首次安装、NodeSource 联网安装和新横幅实际字符宽度仍待用户验收
