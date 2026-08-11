# 验证、上线与完成标准

## 自动验证

### 路径与包身份

- package-key 对相同 ToolPkg ID 稳定，对净化后相同的不同 ID 不碰撞
- 子包与 container 使用同一私有命名空间
- 无绑定 registration engine 不能访问 private/cache
- JavaScript 不能提供其他 package ID
- `/`、`..`、反斜杠、盘符、NUL、空段、超深和超长路径全部拒绝
- private/cache API 不返回内部绝对路径

### 原子性与迁移

- 单文件写入中断时旧文件保持完整
- generation 在 active pointer 更新前中断时旧 generation 保持 active
- 重复 source tree digest 不重复应用
- 缺少包专属迁移器时拒绝导入
- 用户取消时 source 不变，staging 被清理

### Scanner 与 builder

- `.backup`、`.git`、`.env`、私钥、旧 Operit 绝对路径被拒绝
- ZIP 绝对路径、`..`、盘符、反斜杠、NUL 和大小写重复条目被拒绝
- manifest 重复、缺失或超限被拒绝
- entry count、总解压量、单文件、压缩比、深度和路径长度预算生效
- schema v2 未声明条目被拒绝
- 同一输入重复构建获得相同 SHA-256

### 安装与更新

- 下载 SHA 不匹配时 active 不变
- scanner、manifest、ID、registration 失败时 active 不变
- 成功更新后 private data 保持不变
- 外部旧制品只在新 active 验证成功后清理
- cache signature 等于 artifact SHA-256
- market marker 位于内部 runtime store

### 公共存储

- MediaStore pending 正常提交，失败时 URI 被删除
- 同名文件生成唯一名称
- userscript、player、browser、toolbox、AI export 进入固定子目录
- Markdown、Shared、AI 图片进入 `Pictures/Kiyori` 对应子目录
- 普通 WebView 与 Browser Runtime 下载进入同一下载 owner

## 架构门禁

- ARCH046 保持 `KiyoriPaths` 和兼容 facade 的现有所有权检查
- 新增存储合同检查，禁止生产代码继续拼接 `Download/Kiyori` 和 `Pictures/Kiyori`
- 新增 ToolPkg 制品合同检查，锁定 blocked entries、预算、scanner、builder、active store 与市场事务
- 最终 APK 的活动代码和内置 ToolPkg 中不得出现旧 Operit 物理路径

## 本轮本地完成标准

- 正式 TODO、`CONTEXT.md`、`README.md`、ToolPkg 开发文档和类型定义与实现一致
- 定向 JVM/Python 检查通过
- ARCH046 定向检查通过
- formal readiness 通过
- `git diff --check` 无 whitespace error
- 串行执行 `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain`
- 核验 Debug APK 路径、时间、大小、SHA-256、application ID、版本、ABI、Debug 签名和 16 KB ZIP 对齐

## 2026-08-11 本地收口证据

### 源码、合同与文档

- `pnpm.cmd exec tsc -p examples/tsconfig.json --pretty false` 通过
- `:app:generateBundledToolPkgAssets` 通过
- `ToolPkgArtifactScannerTest`、`ToolPkgArtifactBuilderTest`、`ToolPkgArtifactStoreTest`、
  `ToolPkgStorageContractTest`、`ToolPkgLegacyMigrationEngineTest` 和 `KiyoriPathsTest`
  共 `19/19` 通过，失败、错误和跳过均为 `0`
- ARCH046 两项正反向合同测试 `2/2` 通过；完整 architecture `m03` 的 `35/35` 项检查
  通过，JSON 输出为 `errors: []`
- formal readiness 为 `PASS`；工作树 Markdown 链接检查覆盖 `12` 份相关文件，问题数为 `0`
- `node --check examples/operit_editor.js` 与
  `node --check app/src/main/assets/packages/operit_editor.js` 通过
- `git diff --check` 无 whitespace error；仅报告工作树既有换行转换提示
- `:app:compileDebugKotlin` 为 `BUILD SUCCESSFUL`，`83` 个任务中 `4` 个执行、`79` 个为最新状态

### Debug APK

- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` 为 `BUILD SUCCESSFUL`；
  `238` 个任务中 `33` 个执行、`205` 个为最新状态
- `verifySingleDebugLauncher` 确认唯一 launcher 为
  `com.ai.assistance.operit.ui.main.MainActivity`；
  `verifyDebugPlayerRuntimePackaging` 通过
- APK：`app/build/outputs/apk/debug/app-debug.apk`
- 生成时间：`2026-08-11 21:56:09.376 +08:00`
- 大小：`485889789` 字节
- SHA-256：`7446903F31154187668B0A3DC556EFC6BE0947987B0A03A2F76A67E47894CAC5`
- application ID：`com.kiyori`
- 版本：`versionCode 45`、`versionName 0.1.0`
- SDK：min `26`、target `34`、compile `37`
- ABI：仅 `arm64-v8a`；APK 包含 `51` 个 native `.so`
- Android Debug V2 单 signer 签名通过；证书 SHA-256 为
  `e72ad950d07adbedfb9c909c48d922fddb3560677012da79b686a127867ae902`
- `zipalign -c -P 16 -v 4` 为 `Verification successful`

### APK 内置 ToolPkg 嵌套审计

APK 的 `assets/packages` 包含 `42` 个文件，其中 `11` 个为 `.toolpkg`。逐个在内存中打开嵌套
ZIP 后确认：

- 每个包恰有一个根级 `manifest.json`，全部可以解析，schema 均为 v1
- 不存在绝对路径、反斜杠、盘符、空路径段、`.`、`..`、重复规范化路径、大小写冲突或符号链接
- 不存在 `.git`、`.backup`、`.history`、`__pycache__`、`node_modules`、`.gradle`、`.idea`、
  `.env`、私钥、keystore 或临时文件
- 内置 JavaScript 和 ToolPkg 内容中不存在 `/sdcard/Download/Operit`、
  `/storage/emulated/<id>/Download/Operit` 或缺少 `files/` 层的
  `/sdcard/Android/data/com.kiyori/js_temp`

| ToolPkg ID | 版本 | ZIP 文件数 |
| --- | --- | ---: |
| `com.operit.apk_reverse_toolkit` | `1.0.3` | 8 |
| `com.operit.context_limiter_c` | `1.0.0` | 4 |
| `com.operit.deepsearching_bundle` | `0.1.0` | 14 |
| `com.operit.linux_ssh_bundle` | `0.1.0` | 8 |
| `com.operit.message_insert_bundle` | `0.3.0` | 4 |
| `com.operit.plan_mode_bundle` | `0.1.0` | 20 |
| `com.operit.qqbot_bundle` | `0.3.0` | 13 |
| `com.operit.remote_operit_bundle` | `0.1.0` | 8 |
| `com.operit.thinking_guidance` | `1.0.0` | 2 |
| `com.operit.windows_bundle` | `0.2.0` | 46 |
| `com.operit.worldbook` | `1.1.0` | 11 |

## 设备与生态验收

以下内容不能由本地构建替代，完成前保持 `verification_pending`：

- Android 8、10、14/15 和 vivo Android 16 的 MediaStore/公开目录行为
- Browser 普通、Blob、系统下载器和内置下载器的同一任务 owner
- ToolPkg private/cache 跨包隔离和进程重建
- 市场安装、更新失败、成功切换与旧制品清理
- 用户通过 SAF 选择旧目录后的包专属导入
- 文件管理器可见性、打开、分享和删除
- 第三方“记忆系统”新版本停止访问 `Download/Operit`
