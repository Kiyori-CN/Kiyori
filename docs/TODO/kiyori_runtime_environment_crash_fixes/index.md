---
fork: https://github.com/Kiyori-CN/Kiyori
status: verification_pending
---

# 环境检测与运行时崩溃修复

## 目标

本轮在已发布到 `main` 的下载抽屉基线上修复运行时环境和悬浮浏览器 owner 问题：环境配置页面误判 pnpm 未安装、APK 缺少 native ripgrep、搜索页缺少返回分发所有者，以及下载页组合目录选择 launcher 时缺少 Activity Result owner。

## 根因与实施边界

### pnpm 检测

终端命令事件先发送逐行输出，完成时再发送一次权威完整输出。环境配置页面把两类事件直接拼接，导致唯一完成 marker 重复。Node.js 版本检查使用正则，因此重复文本仍能通过；pnpm 检查使用精确 marker，因而稳定误判。

- 页面只采用完成事件中的完整输出作为检测结果
- 保留 Node.js、pnpm 与 TypeScript 的既有共享安装和就绪合同
- 不安装替代 pnpm、不创建 wrapper，也不按文件类型猜测可执行性

### native ripgrep

`NativeRipgrep` 运行时强制加载 `liboperit_ripgrep.so`，但现有 Cargo 构建只存在于 CI shell 步骤和手工脚本中，本地 `assembleDebug` 没有任务依赖。CI 又把产物复制到被 Git 忽略的源码目录，构建合同因此不可复现。

- 建立 Gradle 原生生成任务，以 Cargo、锁文件、Rust 源码和固定 NDK 为输入
- 产物写入 `app/build/generated/`，通过 Android Variant Sources API 接入所有 APK 变体
- CI 只准备固定 Rust 工具链与 Android target，实际编译和打包统一由 Gradle 持有
- 删除写回 `app/src/main/jniLibs` 的旧手工构建入口
- 不在 Kotlin 搜索层捕获缺库异常，也不增加第二种搜索实现

### 悬浮浏览器 Back

悬浮浏览器的 `ComposeView` 已安装生命周期、保存状态和 ViewModel 所有者，但没有 `OnBackPressedDispatcherOwner`。搜索页组合 `BackHandler` 时因此抛出 `IllegalStateException`。

- overlay owner 实现并安装 `OnBackPressedDispatcherOwner`
- Compose `BackHandler`、Android 13 系统 Back 与旧版按键 Back 进入同一 dispatcher
- dispatcher 无 Compose callback 时调用现有 `WebSessionBrowserHost.handleBack()` 状态机
- 不删除搜索页 `BackHandler`，不判空跳过返回处理

### 悬浮浏览器目录选择

共享下载页原先在组合阶段直接创建 `rememberLauncherForActivityResult(OpenDocumentTree)`。应用内页面具备 Activity owner，但 `TYPE_APPLICATION_OVERLAY` 的 `ComposeView` 不属于 Activity View tree，因此打开下载抽屉即抛出 `No ActivityResultRegistryOwner`。

- 下载页不再在组合阶段依赖 `LocalActivityResultRegistryOwner`
- 独立透明 `WebSessionDirectoryPickerActivity` 持有真实 `ComponentActivity` registry 并启动 SAF 目录选择器
- 一次性协调器把目录 URI 返回原下载动作，继续执行持久化 URI 权限、文件移动及未使用授权释放逻辑
- 不借用可能暂停或销毁的 `MainActivity`，不吞掉异常，也不把“修改文件夹”改为空动作

## 实施步骤

1. 修正 terminal 完成事件消费并增加回归测试
2. 将 native ripgrep 编译和 generated JNI source 接入 Gradle，清理旧 CI 与脚本路径
3. 为悬浮浏览器安装返回 dispatcher owner 并统一平台 Back 入口
4. 把悬浮下载页目录选择迁移到透明 Activity owner
5. 更新构建、终端和浏览器相关文档
6. 运行定向测试、正式准备门禁、Debug APK 构建与 native 制品审计

## 验收

- terminal 定向测试证明完成输出不会与逐行输出重复拼接
- Gradle 构建生成 arm64 `liboperit_ripgrep.so`，最终 APK 含该库
- 悬浮搜索页可正常组合，系统与 Compose 返回共用现有状态机
- 悬浮下载页可正常组合，“修改文件夹”可打开 SAF 目录选择并收到唯一结果
- `git diff --check`、正式准备门禁和 `:app:assembleDebug` 通过
- APK 包名、版本、Debug V2 签名、16 KB ZIP 对齐和 native ELF 对齐完成核验
- pnpm、ripgrep 搜索和悬浮浏览器返回仍由目标真机完成最终验收

## 实施结果

- terminal 环境检查只读取完成事件的权威完整输出，新增回归测试覆盖逐行事件不会参与最终判定
- `BuildNativeRipgrepTask` 固定 Rust 1.88.0、`aarch64-linux-android`、Android API 26 和项目 NDK，输出通过 Variant Sources API 接入所有变体
- GitHub Actions 只准备 Rust 工具链和 target；Cargo 编译、NDK linker、输出检查及 APK 打包统一由 Gradle 执行
- 写回 `app/src/main/jniLibs` 的旧 PowerShell 与批处理入口已删除
- 悬浮 WebSession owner 已实现并安装 `OnBackPressedDispatcherOwner`，Compose、Android 13 与旧版 Back 共用 dispatcher 和既有 host 状态机
- 悬浮下载页不再创建依赖 Activity View tree 的 Compose launcher；目录选择由透明 `ComponentActivity` 持有并把结果送回同一下载 manager 动作

## 本地验证

- `:terminal:testDebugUnitTest --tests com.ai.assistance.operit.terminal.TerminalEnvironmentContractTest`：PASS，4 项测试
- `:app:compileDebugKotlin`：PASS
- `:app:testDebugUnitTest --tests com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserOverlayWindowPolicyTest`：PASS
- `:app:buildNativeRipgrep`：PASS；未剥离 arm64 产物 `3745256` 字节，AArch64 ELF，四个 `PT_LOAD` 均为 `0x4000`
- `ci.test.test_pr_check`：PASS，12 项测试
- `check_formal_readiness.py --repository . --require-main`：PASS
- `git diff --check`：父仓库与 terminal 均通过
- `:app:assembleDebug --no-daemon --console=plain`：`BUILD SUCCESSFUL in 1m 35s`，231 个任务、零失败
- APK：`app/build/outputs/apk/debug/app-debug.apk`，时间 `2026-07-27 13:31:19 +08:00`，大小 `449493090` 字节，SHA-256 `89CADC2171FC9B07202F9CB084CBC87F2A48ECB7AA17BBA830978C38AF5C924C`
- APK 元数据：`com.kiyori`、versionCode `45`、versionName `0.1.0`、minSdk `26`、targetSdk `34`、compileSdk `36`
- Android Debug V2 签名和 16 KB ZIP 对齐通过
- APK 仅包含一份 `lib/arm64-v8a/liboperit_ripgrep.so`；剥离后大小 `2574136` 字节，SHA-256 `5A84187EE5945B1ABE1E534ADCB48DE6A77119B01F2A8B6176380E97B66AF311`，仍为 AArch64 ELF 且四个 `PT_LOAD` 均为 `0x4000`

## 剩余验收

未安装 APK、未操作设备。环境配置页面 PNPM 卡、AI 文件搜索和悬浮浏览器搜索页 Back 仍为目标真机 `verification_pending`。

## 2026-07-27 悬浮下载页目录选择增量验证

- `WebSessionDirectoryPickerCoordinatorTest`：PASS，2 项测试锁定成功与取消结果都只消费一次
- `BrowserOverlayWindowPolicyTest`：PASS，3 项测试；定向任务合计 `5/5`，零失败、零错误
- 定向任务完成 `:app:compileDebugKotlin` 和 Debug Manifest 合并，且没有新增 Kotlin 编译警告
- 共享下载页对 `rememberLauncherForActivityResult` 与 `LocalActivityResultRegistryOwner` 为零引用，合并 Manifest 和最终 APK 均包含 `WebSessionDirectoryPickerActivity`
- `check_formal_readiness.py --repository . --require-main`、`git diff --check` 和新增差异禁用降级语义扫描通过
- `:app:assembleDebug --no-daemon --console=plain`：`BUILD SUCCESSFUL in 1m`，231 个任务零失败
- APK：`app/build/outputs/apk/debug/app-debug.apk`，时间 `2026-07-27 14:13:14 +08:00`，大小 `449493090` 字节，SHA-256 `34136CDBA8958FA702E0FEA14476DE4FED226D93B5F224261F618271E7D3BA66`
- APK 元数据仍为 `com.kiyori`、`45 / 0.1.0`、minSdk `26`、targetSdk `34`、compileSdk `36`；Android Debug V2 签名与 16 KB ZIP 对齐通过
- 悬浮下载抽屉实际打开、取消和完成 SAF 目录选择仍需目标真机验收
