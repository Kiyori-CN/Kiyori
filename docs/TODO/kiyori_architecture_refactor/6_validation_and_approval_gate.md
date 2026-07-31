---
status: accepted_design
plan_version: 3
last_reviewed: 2026-07-31
---

# 验证矩阵与批准门禁

## 方案审查门禁

正式源码重构前完成两轮审查。

### 第一轮：正向架构审查

逐项确认：

- 每个目标目录有明确 owner
- 每个旧文件有迁移、保留或删除结论
- 依赖方向可以在当前单模块内实现
- capability 不泄漏 UI 和具体 runtime
- Operit AI 上游同步仍有清晰路径
- 没有第二状态 owner
- namespace、terminal 和数据迁移未混入

### 第二轮：失败路径审查

逐项反查：

- 冷启动和多进程 Application 是否仍正确
- Manifest、快捷方式、Widget、默认助手和通知监听是否仍能解析组件
- WorkManager 是否能实例化已有 worker
- 序列化 JSON 是否仍能读取旧 FQCN
- JNI 与 AIDL 是否仍能链接
- Browser 人工 UI 与 AI 是否仍操作同一 WebView
- Player floating/fullscreen 是否仍共享同一 session 与 mpv core
- raw snapshot 和 `Download/Kiyori` 是否保持可用
- upstream merge 是否能识别移动与保留区

两轮审查和用户批准都通过后，方案状态才可从 `ready_for_approval` 改为
`accepted_design`；静态审查通过本身不等于用户授权。

## 每个里程碑的静态检查

- `git status --short --branch`
- `git diff --check`
- `git diff --summary --find-renames=90%`
- 新旧包名和符号反向搜索
- Manifest component、action、authority 清单对比
- 数据库、偏好、WorkManager、序列化和路径字符串清单对比
- AIDL、Room schema/entity 与 ObjectBox model 的规范化 SHA-256 对比
- architecture ownership 和依赖规则检查
- 无未跟踪构建产物、私密配置或工作区 checkpoint

纯移动提交中，除 package、import、Manifest 精确引用和注释外，不应出现业务逻辑差异。

## 自动验证层级

### 文档或计划里程碑

- Markdown 链接与格式检查
- formal readiness
- `git diff --check`
- 项目要求的 `:app:assembleDebug`
- Debug APK 存在性、大小和 SHA-256

### G-00 架构门禁

- ownership schema 解析测试
- 每条 error 规则的失败样例
- stable contract snapshot 与源码提取一致
- Windows/Linux 路径一致
- 未跟踪候选文件也进入检查
- 不读取私有配置、不修改源码
- M-01 允许文件与规范化纯改名测试

### Application、App Shell 和主题

- 启动、路由、Shell 状态和主题定向 JVM 测试
- `:app:testDebugUnitTest`
- Kotlin 编译
- `:app:assembleDebug`
- APK Manifest 与组件审计

### Browser

- Browser policy、runtime、presentation、download、history、bookmark 和 userscript 定向测试
- 完整 JVM 测试
- Android test 源码编译
- Debug 构建与 APK 审计
- 目标设备上的普通/无痕窗口、AI 共用、下载、脚本、Back 和 presentation 验收

### Player

- Player policy、Surface lease、runtime protocol 和 asset 检查
- 完整 JVM 测试
- native 构建
- Debug 构建与 player packaging 审计
- 目标设备真实视频、floating/fullscreen 往返和 `:player` 崩溃隔离验收

### 数据与组件

- raw snapshot 导出和读取
- 基线数据启动
- WorkManager 任务恢复
- 默认助手、通知监听、Widget、快捷方式和 external Intent
- `Download/Kiyori` 读取与新增写入

## APK 核验

每个代码里程碑至少记录：

- 文件路径
- 生成时间
- 文件大小
- SHA-256
- package name
- versionCode / versionName
- minSdk / targetSdk
- ABI
- Debug 签名
- 16 KB zipalign
- player/native packaging gate 结果

APK hash 变化不代表行为变化；它只用于标识被验证的精确产物。

## 设备验收原则

设备不属于方案 v3 当前实施和备份范围。本节只保留为 Browser、Player、系统组件或数据迁移等
未来行为里程碑的证据边界，不作为 G-00 或 M-01 的开始条件。

本地构建不能证明：

- Android 系统保存的组件仍有效
- 已有数据可读
- Browser WebView 转挂正确
- Player Surface 与 native runtime 正常
- Widget、通知监听、默认助手和快捷方式保持

涉及这些边界的未来里程碑必须保持 `verification_pending`，直到另行获得设备授权并完成验证。

## 提交规则

用户已允许正式实施阶段创建本地里程碑提交，但当前方案阶段不创建提交。

未来每个提交应满足：

- 主题单一
- 差异可审阅
- 验证记录完整
- 不包含 APK、bundle、设备备份、私密配置或 terminal 变化
- 不推送

推荐提交顺序：

```text
docs: accept Kiyori architecture refactor plan
refactor(app): rename application root
refactor(app): move Kiyori root composition
refactor(app): isolate Kiyori shell ownership
...
```

## 用户批准门禁

开始第一源码里程碑前，需要用户明确确认：

1. 接受双包根和 Operit 兼容岛。
2. 接受首阶段不改 namespace，且 namespace 是可选独立阶段。
3. 接受系统持久化组件保留旧 FQCN 兼容入口。
4. 确认当前文件夹、未提交文件、私有配置和基线 APK 的纯本地备份方式。
5. 接受先完成 G-00 通用架构与稳定合同门禁。
6. 确认第一应用源码里程碑只按精确影响清单在原包内执行
   `OperitApplication -> KiyoriApplication`，不移动 Application 包路径。

本轮等待确认的是方案 v3。批准必须针对精确版本；方案修改影响上述六点时，需要重新确认。
