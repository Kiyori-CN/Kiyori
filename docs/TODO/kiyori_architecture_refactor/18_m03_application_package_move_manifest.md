---
status: completed
plan_version: 3
milestone: M-03
baseline: 176f803e683307aa8e182fb357f35f84755c5f8b
m02_apk_sha256: F63AEFAADA4C712AFBD3FDE4D851490058D0A8E0E799C8104ACD13516F209DC9
device_scope: excluded
last_reviewed: 2026-08-01
---

# M-03 KiyoriApplication 包迁移精确清单

## 当前结论

M-03 只把唯一 Application 从：

```text
com.ai.assistance.operit.core.application.KiyoriApplication
```

移动到：

```text
com.kiyori.app.KiyoriApplication
```

M-02 已保证 Operit AI 不再直接依赖 Application concrete class。M-03 不再次调整
platform contract、初始化逻辑、锁、线程、异常、多进程、数据、协议、UI 或功能。迁移后的
源码只新增原同包隐式依赖 `ActivityLifecycleManager` 的显式 import；调用目标和实现不变。

Kiyori 尚未发布，Application FQCN 也不是第三方调用入口，因此不保留旧包 shim、委派类或
双 Application 实现。Manifest 直接改为绝对新类名。

## 里程碑合同

```text
Milestone ID: M-03
Title: KiyoriApplication 移入 com.kiyori.app
Owner: Kiyori app
Plan version: 3
Baseline commit: 176f803e683307aa8e182fb357f35f84755c5f8b + M-02 working tree
Upstream baseline: 0921f749a087c4a52a2202abdf32491ae335dd41
Scope: Application 文件纯移动、package、Manifest、Lint path、机器 snapshot
Non-goals: 启动逻辑抽取、Operit integration 清理、MainActivity/OperitApp/UI/data/native/namespace
Source paths: 本文“精确文件清单”
Target paths: app/src/main/java/com/kiyori/app/KiyoriApplication.kt
Rename map: old Application FQCN -> com.kiyori.app.KiyoriApplication
Stable contracts touched: Manifest Application class；无数据、协议、IPC 或 native 变化
State owners: 唯一 KiyoriApplication、M-02 platform contract 和原初始化 owner 不变
Upstream sync class: Application B -> Kiyori C；保留精确 transitional import snapshot
Risk IDs: R-01、R-04、R-05、R-09、R-10、R-11、R-14、R-15、R-17
Rollback point: M-02 tracked patch + new-files archive + main@176f803e bundle
Required commands: ARCH018、architecture、formal、Python、JVM、assembleDebug、APK audit
Device acceptance: excluded
Commit boundary: 未获提交授权；不得提交或推送
Open questions: 无实质歧义
```

## 为什么需要文件精确的过渡 exception

M-03 开始前，Application 有 42 个显式 `com.ai.assistance.operit` import，另有一个原同包
隐式依赖 `ActivityLifecycleManager`。它们都是 M-02 已验证 Application 方法体的一部分，
覆盖启动、WorkManager、ImageLoader、插件、备份、语言和退出清理。

候选方案：

1. 扩大 `kiyori-app` 的 `allowed_import_roots` 到整个 `com.ai.assistance.operit`：
   拒绝。它会让未来所有 app 文件都能穿透 Operit 内部。
2. 在 M-03 同时把这些依赖和全部启动方法搬入 `integration.operit`：
   拒绝。它把纯 package move 与状态 owner/启动编排重构混为一批。
3. 为新 Application 文件增加一个 `ARCH004` 文件精确 exception：
   采用。exception 只抑制该文件现有的跨根 import，并由 43-import snapshot 与规范化源码
   SHA-256 锁死，不能新增或修改依赖，且在 `M-07-operit-integration` 到期。

该 exception 不是运行时 fallback，也不创建第二实现。后续 M-07 必须把 Application 的 Operit
装配职责迁入 `com.kiyori.integration.operit`，删除 exception。

## 精确文件清单

### Android 运行时

| 操作 | 路径 |
| --- | --- |
| 删除旧路径 | `app/src/main/java/com/ai/assistance/operit/core/application/KiyoriApplication.kt` |
| 新增新路径 | `app/src/main/java/com/kiyori/app/KiyoriApplication.kt` |
| 修改 package | `com.ai.assistance.operit.core.application` -> `com.kiyori.app` |
| 修改 Manifest | `.core.application.KiyoriApplication` -> `com.kiyori.app.KiyoriApplication` |

M-03 封板时，除 package declaration 和将原同包 `ActivityLifecycleManager` 依赖改成显式
import 外，Application 方法体与声明与 M-02 完成状态逐字节一致：

```text
35E9A93906503936E19CE703491E0ACF59719DBBA2E9919327F9F5704844FE95
```

2026-08-19 网络日志 SVG 显示修复在唯一 `KiyoriApplication` 图片 owner 内增加了受控功能变更：
全局 Coil `ImageLoader` 的既有 `components` 注册表新增 `SvgDecoder.Factory()`，并增加对应
`coil.decode.SvgDecoder` import。该变更不创建第二 ImageLoader、缓存、网络客户端或浏览器状态，
而是让缩略图、共享全屏查看、网页元素看图和二维码识别继续消费同一个进程级 loader。更新后的
package 归一化 Application SHA-256 为：

```text
FE81FB2D78D46E2EB86E3BF21D71B5B50A5B173B0C9BF5F3662272CE546B3C44
```

ARCH018 同步锁定该值；上面的 M-03 纯移动哈希继续作为历史封板证据。

2026-08-20 启动资源分段优化把唯一全局 Coil `ImageLoader` 的显式预热从完整初始化同步段移入
既有 `800ms` 后串行后台队列。`ImageLoaderFactory.newImageLoader()` 仍按需返回同一个 lazy
实例，组件、缓存、OkHttp 配置和全部消费者不变，不创建第二图片 owner。更新后的 package
归一化 Application SHA-256 为：

```text
06E70550B5CB01683C70ED103CF5A27E90FEA16556B0CF73E71CC83CA9FB0891
```

ARCH018 同步锁定该值；M-03 纯移动与 2026-08-19 SVG 注册哈希继续作为历史证据。

### Lint baseline

`app/lint-baseline.xml` 只有 6 个 location file 路径改变：

```text
src/main/java/com/ai/assistance/operit/core/application/KiyoriApplication.kt
-> src/main/java/com/kiyori/app/KiyoriApplication.kt
```

issue ID、message、line、column、条目数量均不变。机械路径替换后的归一化 SHA-256 必须为：

```text
1bd42772d79c8cad6781838552aac2b57b4c8df3e780861a7afba92f826945e7
```

以上是 M-03 封板时的纯移动证据。2026-08-01 在 M-04 根组合、Shell 与
`MainActivity` 职责拆分后重新生成完整 lint 结果，结构化交集证明原 6 条中的
`AppBundleLocaleChanges` 已不再由当前工具链报告，因此删除该失效历史记录。当前
baseline 中旧路径为 0、新路径为 5，归一化 SHA-256 为
`a71ab39486275083a30c1db51f0533162e2db9054be8d00bad1eb2878fe8ce4c`；
ARCH018 现在锁定这个当前状态，同时保留本文对 M-03 当时 6 条机械迁移的历史记录。

同步更新：

- `ci/script/normalize_lint_baseline.py`
- `ci/README.md`

### 架构控制面

更新：

- `config/architecture/package-ownership.toml`
- `config/architecture/manifest-components.txt`
- `config/architecture/manifest-structure-hashes.txt`
- `config/architecture/m03-application-operit-imports.txt`
- `config/architecture/m03-application-normalized-sha256.txt`
- `ci/script/check_architecture_boundaries.py`
- `ci/test/test_architecture_boundaries.py`

M-03 目标 Manifest semantic hash：

```text
E4345BF5CF33FA33AFA0A7F3B69DC9E2092EE0406C1354985EA9D7BA683F96D1
```

## ARCH018

目标 Application 文件出现后，门禁必须检查：

1. 旧 Application 文件不存在。
2. 新文件 package 为 `com.kiyori.app`。
3. package 归一化后的源码 SHA-256 与 M-02 snapshot 完全一致。
4. 迁移后的 43 个 Operit import（原 42 个显式 import 加
   `ActivityLifecycleManager`）的精确集合和重复次数完全一致。
5. `package-ownership.toml` 中存在且实际使用一个文件精确 `ARCH004` exception。
6. Manifest Application 为绝对新 FQCN。
7. 当前 Lint baseline 旧路径为 0、新路径为 5；M-03 封板时迁移的第 6 条
   `AppBundleLocaleChanges` 已在后续完整 lint 再生成中被证实为失效记录。
8. `ARCH017` 继续通过，Operit AI concrete Application dependency 仍为 0。
9. 旧 FQCN 不出现在运行时代码、Manifest、ProGuard 或 Android 资源中。

## 不允许触碰

- Application 方法体、除必需 `ActivityLifecycleManager` 显式 import 外的 imports 集合，
  以及声明顺序之外的业务代码
- M-02 四个 platform contract
- `MainActivity`、`OperitApp`、主题、Browser、Player、设置、数据与备份实现
- Gradle namespace、application ID、version 或依赖
- WorkManager、AIDL、JNI、native、序列化和 persistence contract
- terminal 内容与 gitlink
- 手机、模拟器或 ADB

## 实施顺序

1. [DONE] M-03 只读影响、Manifest、Lint、ProGuard、多进程和 ownership 审计
2. [DONE] 确认采用文件精确、import snapshot 锁定、M-07 到期的过渡 exception
3. [DONE] 先实现 ARCH018、M-03 phase 和失败测试，M-02 当前状态保持通过
4. [DONE] 纯移动 Application 文件并修改 package declaration；按编译证据补充
   `ActivityLifecycleManager` 显式 import
5. [DONE] 修改 Manifest、6 个 Lint path 和对应 checksum
6. [DONE] 增加文件精确 ARCH004 exception
7. [DONE] 运行反向搜索、architecture、formal、Python、JVM 与 Debug APK
8. [DONE] 核验新 Manifest Application、多进程、签名、ABI、16 KB 和 native packaging
9. [DONE] 更新 CONTEXT、状态文档、日记和 M-03 恢复点

## 停止条件

- 规范化 Application SHA-256 发生变化
- 锁定的 43 个 Operit import 出现新增、删除或替换
- exception 需要目录通配、第二路径或无到期里程碑
- 需要旧包兼容 shim、第二 Application 或运行时 fallback
- Manifest 除 Application class 外发生语义变化
- Lint baseline 除 6 个 file path 外发生变化
- namespace、数据、协议、WorkManager、AIDL、JNI、native 或 terminal 漂移
- `:crash`、`:repair`、`:player` 进程合同变化
- architecture、formal、测试、构建或 APK 审计失败

## 完成验证

- `ARCH018` 与完整 architecture gate：PASS，`phase=m03`。
- Python：109 tests，零失败。
- Android Kotlin 编译：PASS。首次编译准确暴露原同包隐式依赖
  `ActivityLifecycleManager`；补成显式 import 后通过，未发现第二个隐式依赖。
- 完整 JVM：124 suites，`773/773`，零失败、零错误、零跳过。
- formal readiness：PASS。
- M-03 封板时 Lint baseline：旧路径 0、新路径 6，归一化 SHA-256 为
  `1bd42772d79c8cad6781838552aac2b57b4c8df3e780861a7afba92f826945e7`。
- 当前 Lint baseline：旧路径 0、新路径 5；后续结构化交集只删除失效记录，
  当前归一化 SHA-256 为
  `a71ab39486275083a30c1db51f0533162e2db9054be8d00bad1eb2878fe8ce4c`。
- `:app:assembleDebug`：PASS，233 tasks，28 executed、205 up-to-date。
- `verifyDebugPlayerRuntimePackaging`：PASS。
- APK：
  - 路径：`app/build/outputs/apk/debug/app-debug.apk`
  - 大小：469184988 bytes
  - SHA-256：`244519AE6087C89E21A5D0682D4E66AACE2EE3E0339C2E6C2AF176DAE117A143`
  - package：`com.kiyori`
  - versionCode/versionName：`45` / `0.1.0`
  - label：`Kiyori`
  - Manifest Application：`com.kiyori.app.KiyoriApplication`
  - 进程：`:crash`、`:repair`、`:player` 保持
  - ABI：`arm64-v8a`
  - native entries：53
  - APK Signature Scheme v2：true
  - 16 KB zipalign：Verification successful
- 恢复点：
  `D:\10_Project\Kiyori-backups\2026-08-01-m03-completed-176f803e`
- Git：未提交、未推送；`origin/main` 未改变。
- device/ADB：未授权，`verification_pending`。
