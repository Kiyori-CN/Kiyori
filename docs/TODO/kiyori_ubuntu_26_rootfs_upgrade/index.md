---
status: in_progress
owner: Kiyori terminal / rootfs migration
---

# Ubuntu 26.04 内置环境升级

## 目标

将 Kiyori 内置的 Ubuntu 24.04.1 Noble rootfs 分阶段切换到 Ubuntu 26.04.1 Resolute，保持终端、
`super_admin:terminal`、`code_runner`、hidden executor、PTY、AIDL、namespace、挂载协议和用户
工作区的外部行为。该文件是本专项唯一的项目状态与验收载体；任务日记只负责跨压缩恢复。

## 当前状态

- 阶段：`P0_BASELINE_AND_DESIGN`、`P1_ASSET_BUILD` 已完成（新候选已消除 Android tar 硬链接），
  `P2_RUNTIME_CONTRACT` 已通过首次 Android 解包/staging/activation 现场门槛但仍在运行时收口；
  `P3_USER_DATA_MIGRATION`、完整 `P5_DEVICE_ACCEPTANCE` 继续为 `verification_pending`。
- 本轮补充开始前父仓库基线：`main@e4d8f0c2d80a38e03fff9fca1a3a6a28a1319c03`，与
  `origin/main` 一致；`terminal` 子模块基线：`main@5cb5371081253384552af661653dae34663debb9`，
  与 `origin/main` 一致。本轮工具链实现提交为 `ee5ab0001c886f5ca9dc0c3feba16a2f7f5f7767`，其后续
  文档一致性提交也均已推送，并完成 local/tracking/remote 三方 ref 对账。
- 正式开发准备：`check_formal_readiness.py --repository . --require-main` 通过。
- 旧资产：`ubuntu-noble-aarch64-pd-v4.18.0.tar.xz`，Canonical/上游历史资产，SHA-256
  `91ACAA786B8E2FBBA56A9FD0F8A1188CEE482B5C7BAEED707B29DDAA9A294DAA`，压缩约 61.16 MiB，
  展开约 300.39 MiB，24.04.1 LTS。
- 新输入：Canonical Ubuntu Base 26.04.1 arm64，下载地址
  `https://cdimage.ubuntu.com/ubuntu-base/releases/26.04/release/ubuntu-base-26.04.1-base-arm64.tar.gz`，
  官方 SHA-256 `5a1906794ced63a71a8119c3f211ef5f0bbe0a243001b4bbd41fdf80c5b219fd`，
  版本代号 `resolute`。
- 上一份候选（已拒绝）：`terminal/src/main/assets/ubuntu-resolute-arm64-kiyori-v1.tar.xz`，SHA-256
  `de7bc812627665138b55578654c1ed79ec275f7f4e42c0ca0c29a325ffb69514`，包含 `116` 个 tar
  hardlink member（其中 `usr/lib/cargo/bin/coreutils/*` 占 `114` 个），在 Android 首次解包时
  于 `usr/bin/perl5.40.1 -> usr/bin/perl` 失败；该候选不能作为设备或 P1 通过证据。
- 已实施修复：构建时切换 GNU coreutils 后显式清除不再需要的 `rust-coreutils`，并以
  `tar --hard-dereference` 生成无 hardlink member 的归档；新 manifest 已同步，Android 现场复测
  仍是独立验收门槛。
- 当前候选（已通过构建机门禁）：同一受控脚本两次独立构建 SHA-256 均为
  `e1efa3879bd06902eddc5c4e73642e68c7f08149135ec353a5c915a211398a11`，压缩 `53,788,140`
  字节，归档 `10,731` 个成员、`0` 个 hardlink member，验证解包约 `292,822,634` 字节，包锁
  `164` 项（SHA-256 `a1bf6699642ebddca4d6766c89c6fe2fe6d449f2f37713a29a478e5056a5a3b3`）。两次
  `verify.sh`、实际 arm64 BusyBox 完整解包和 GNU coreutils/Python 3.14/pip/SSH/curl 探针均通过；
  这证明归档格式已修复，但不替代 Android 真机安装证据。
- 用户已在手机上安装此前的 Debug 候选并触发首次初始化；ADB 当前没有列出该设备，后续证据来自用户现场
  截图与粘贴的终端输出，而非本机 ADB。
  安装脚本现已把解包失败、staging 健康检查、数据迁移、DNS、marker、backup/activation 和回滚失败写入可见进度，
  同时保留 tar 的错误输出，便于现场区分“仍在解包”和“已失败”。2026-09-04 的现场截图确认
  解包已成功、随后在 staging 健康检查失败；此前的 tar hardlink 错误不再出现。为下一次设备复测，
  `TerminalManager` 已增加逐项失败原因和关键目录/符号链接/权限的有限诊断，仍保持原有通过条件。
- 2026-09-04 03:08 生成的 `549,958,365` 字节 Debug APK 是上一轮现场候选，SHA-256 为
  `F10E499488ED7390449527BBD80E9E529116CCB6A3B24686E7A7CB50FC56ED1A`。本地资产、安装合同（含
  模式位正反例）、Terminal JVM、正式开发准备、签名、重复 entry 和 16 KiB 对齐均通过；APK 内
  Resolute 资产为单份且与 manifest SHA-256 一致。该候选已在用户设备上完成首次覆盖安装和 rootfs
  activation，但尚未取得完整 Android 运行时矩阵结果，不能据此标记 P2/P5 完成。
- 当前交付候选于 2026-09-04 07:29:36 由本轮最终源码重新构建：
  `app/build/outputs/apk/debug/app-debug.apk`，大小 `476,215,605` 字节，SHA-256 为
  `308B26E4BCC627EB2ED397DBF8CCC4C8A14322960162C1BA0B683241385AD4D8`。该 APK 通过 V2 签名、
  16 KiB `zipalign`、Resolute 单份/Noble 零份、模板资产与源文件逐字节 hash 对账；它尚未在设备上安装，
  因此无 banner、marker 迁移、完整工具链和长期稳定性仍为 `verification_pending`。
- 2026-09-04 02:45 的设备诊断显示 `bin/bash` 与 `usr/bin/gnuenv` 为 `0700`、`usr/lib/os-release`
  为 `0600`，目录层级和三个关键符号链接均存在。结合独立 BusyBox 复现实验（`umask 077` 会将
  归档 `0755/0644` 变为 `0700/0600`），根因锁定为 Android 应用进程 umask 参与解包；安装脚本已
  在 tar 解包子进程中固定 `umask 022`，并保留调用方 umask。
- 2026-09-04 02:54 的后续设备诊断确认上述模式保真修复已生效：`bin/bash`、`usr/bin/gnuenv` 与目录为
  `0755`，`usr/lib/os-release` 为 `0644`，但旧版 staging 仍通过 Android host `[ -x]` 报告两个命令
  “not executable”。因此 `umask` 是已修复的第一个问题，不是完整的运行时结论；当前代码改为从随包
  BusyBox `stat -c '%a'` 解析三位/四位八进制模式并要求 owner execute bit，既不删除健康检查，也不把
  模式正确等同于 PRoot 可执行。包含该修正的新 APK 已于 03:08 构建并完成本地审计，随后现场已成功安装。
- 2026-09-04 03:10 左右的用户现场输出显示安装链路完整到达 `Ubuntu rootfs installed` 并进入 `root@localhost:~#`：
  `/bin -> usr/bin`、`/lib -> usr/lib`、`/sbin -> usr/sbin` 正常，`bin/bash`、`usr/bin/bash`、`usr/bin/gnuenv`
  为 `0755`，`usr/bin/env -> gnuenv`，两个兼容 marker 存在；四条 APT suite 均为 `resolute*`，清华源 HTTPS
  返回 `200`。这确认首次解包、staging、安装脚本的用户数据阶段执行、DNS 写入、marker 和 activation 在该设备通过；
  当前设备没有旧 Noble 用户项目样本，因此不把空 `/root` 迁移当作 legacy 数据迁移验收。
- 同一现场尚未完成可选工具链安装：Python 3.14.4、git 2.53.0、curl、wget 可用，Node/npm、vim、压缩工具、
  ffmpeg、GCC、Go、Rust、Java 均未安装；这不构成安装失败，但 P3/code_runner/super_admin 验收仍未完成。
  百度 HTTPS 在 8 秒内返回 `HTTP 000`，需与清华源、DNS、IPv4/IPv6 和 Android 网络策略分开诊断，不能直接归因
  于 Resolute。

## 后续深度优化路线（2026-09-04）

本节是当前切换专项在首次设备安装成功后的唯一后续执行顺序。每一项都必须以当前代码、产物或设备
证据收口；不得用“能启动一次”替代兼容性、资源峰值或长期稳定性验收。未完成项保持
`in_progress`/`verification_pending`，不提前清理旧设备数据或备份。

### O1 — 先消除重复打包（最高优先级，低行为风险）

- 事实基线：O1 初始 Debug APK 为 `549,958,365` B；2026-07-31 基线 APK 为 `494,148,842` B；
  当前 Resolute 归档在 APK 中为 `53,788,140` B，仓库保留的旧 Noble 归档为 `64,133,552` B。
- 运行时 `TerminalManager` 只由 `ubuntu-rootfs-manifest.json` 选择 Resolute 资产，旧 Noble 归档不再是
  新安装或升级的输入；因此先将旧 Noble 从 Android `assets` 打包输入移出，保留在构建/迁移证据目录，
  不删除设备上的旧 rootfs、backup 或用户数据。
- 验收：APK 中只出现一份 Resolute 资产；安装合同、manifest/hash、terminal JVM、Debug APK 对账通过；
  体积变化按 ZIP 成员实测记录，若收益低于预期则停止并审计 Gradle 资产来源，不盲目删其他能力。
- 本地审计入口：`\.venv\\Scripts\\python.exe -B ci\\script\\check_ubuntu_apk_packaging.py --repository .`
- 回滚：恢复受控的 legacy 构建输入即可，不触碰设备已激活目录；旧归档只有在另一个明确授权的清理阶段
  才允许从仓库工作树删除。

### O2 — 收口终端可见品牌（高优先级，协议不变）

- Kiyori 未对外发布，终端顶部的欢迎 ASCII/品牌标识不再属于当前产品方案；删除 READY 首屏
  的整段 ASCII 和附加品牌句，只保留初始化状态机与清屏语义，不以另一段 banner 替代。
- 终端设置、环境配置和安装进度中的用户可见旧品牌全部清理；协议、namespace、AIDL、native
  文件名和生态标识单独按兼容清单审计，不做无证据的全局文本替换。
- 不改动 `com.ai.assistance.operit` 源 namespace、AIDL、`operit://`、`OPERIT_*`、
  `liboperit_*`、ToolPkg/MCP/备份格式等互操作标识；这些先进入“兼容标识清单”，避免把品牌清理误做成
  协议破坏。
- 验收：终端 READY 首帧不再注入任何 banner；初始化、命令输出、清屏和首个 prompt 的 JVM/设备
  回归通过，生产文案扫描只允许兼容清单中的标识。

### O3 — 执行未发布版本 marker 迁移（高优先级，已获用户确认）

- 用户已确认版本从未对外发布；`.kiyori_installed_ok` 成为活动 rootfs 唯一新写入 marker，
  `.operit_installed_ok` 只作为一次性历史迁移输入，不再被写入、复制或作为健康状态判定依据。
- 迁移顺序固定为：验证旧 marker 为普通文件且内容与当前 Resolute manifest 一致 → 在同一目录原子
  生成并校验新 marker → 删除旧 marker；任一步失败都停止并保持可恢复目录，不静默吞错、不双写。
- 覆盖活动 Resolute、旧 Noble、损坏 marker、迁移中断、权限错误、staging 失败、备份保留和重复启动；
  备份目录仍保留旧树作为明确恢复材料，不把历史备份误当作活动接口。
- 验收：安装合同与 JVM 测试确认活动目录只有新 marker；设备复测确认 `ls -la /` 不再显示旧 marker，
  重复启动幂等，旧 root 数据白名单和 backup 仍可恢复。

### O4 — 一键工具链版本收口（高优先级，中等行为风险）

- 预装 rootfs 继续使用已锁定的 Resolute 快照；当前 164 项核心包来自 `20260827T205830Z`，
  这是“在可复现边界内尽可能新”的预装基线，而不是运行时动态漂移。每次更新必须重新生成
  package lock、双构建和 archive/架构审计，不能把运行时网络更新伪装成预装版本证据。大型
  Node/JDK/Gradle 不随 APK 预装，以控制手机首装磁盘峰值和常驻内存；它们由环境配置按需安装。
- Environment Setup 的 Node 选择固定到 Node.js 24 LTS `24.20.0`（官方 arm64 tar.xz 与 SHA-256
  固定），不选 Node 26 current；pnpm 固定 `12.3.4`，TypeScript 固定 `7.0.2`，readiness
  probe 对 Node/npm/pnpm/tsc 的实际版本和可执行路径逐项精确校验。
- Java 选择 Ubuntu Resolute 的 OpenJDK 25 LTS（当前包 `25.0.4+7-1~26.04`）；Gradle 不再调用
  Ubuntu 的 `4.4.1` stale 包，改用 Gradle 官方 `9.7.1` binary distribution，固定 SHA-256、
  下载临时文件、校验后原子安装到用户目录，并让 hidden probe 使用同一绝对 bin 目录。选择
  Gradle 且 JDK 25 未被确认安装时，批次会显式加入 `openjdk-25-jdk` 依赖，避免安装结束后才
  暴露 Java 缺失。
- Rust/uv/Go/SSH 等其余能力保持各自唯一安装 owner；下一步逐项核验上游稳定版本、arm64 可用性、
  安装体积和离线/弱网失败可见性，不在本切片引入第二下载源或静默降级。
- 验收：命令生成合同、版本探针、非交互 apt、Gradle SHA/路径安全测试通过；设备上分别验证
  `node/npm/pnpm/tsc/java/gradle` 版本、PATH、重复安装和手机存储峰值。

### O5 — 在能力矩阵锁定后瘦身 Resolute rootfs（中高风险）

- 以当前 164 包锁和 `code_runner`/`super_admin`/PTY/SSH/APT 能力为基线，逐包分析依赖和安装体积；
  优先移出文档、缓存、未使用 locale/调试符号及仅首次配置需要的可下载工具，不直接移除 Python、git、
  OpenSSH、证书、动态链接器、shell、apt/dpkg、tar/xz 等已验证能力。
- 每个候选做双次可复现构建、展开内容/链接/hardlink/架构审计，再以设备首次安装磁盘峰值和工具链回归
  决定是否放行。任何工具缺失或首启下载增加都记录为取舍，不以 APK 变小掩盖用户体验退化。

### O6 — Android 资源峰值与长期稳定性（中高风险）

- 记录首次解包、hash 校验、staging、迁移和 activation 的耗时、Java/native PSS、CPU、磁盘峰值；
  优先消除重复临时副本和不必要的全量内存缓冲，保持 `umask 022`、无 hardlink 和旧目录保护门禁。
- 完成 visible PTY、hidden executor、`super_admin:terminal`、`code_runner`、挂载、APT/TLS/SSH、
  超时/取消/并发/PTY 重建及至少两档内存设备矩阵；没有真机证据不得把本地构建标为完成。

### O7 — 收尾与清理（最后）

- 仅在 O1–O6 停止条件全部解除、用户数据/backup 可恢复且设备验收通过后，才评估删除旧 Noble 构建
  输入和历史备份；删除前做路径解析和清单审计，并在日记与本文件记录可恢复性。
- 任何范围外 UI/BrowserAdBlock 并行改动不纳入本路线，也不因父工程范围外测试失败而修改。

#### 用户确认后的当前执行切片

`O2 + O3 + O4`：删除 READY 首屏 banner，完成未发布版本 marker 迁移，并把 Node/JDK/Gradle
一键安装合同收口到可审计的稳定版本；每个小切片先跑 terminal 定向测试，再串行构建 Debug APK。

当前进度：O1、O2、O3 已在源码层完成，O4 已完成版本合同、精确探针和 Gradle-JDK 依赖闭环；
最新源码已完成 Debug 构建和本地审计。当前 APK 为 `476,215,605` B，SHA-256 为
`308B26E4BCC627EB2ED397DBF8CCC4C8A14322960162C1BA0B683241385AD4D8`，较 O1 初始候选
`549,958,365` B 减少 `73,742,760` B，较 2026-07-31 基线 `494,148,842` B 减少 `17,933,237` B。
APK 内 Resolute 成员 1 份、Noble 成员 0 份，Resolute 源 hash 与 manifest 一致；版本合同测试、terminal
定向测试、正式门禁、规定 Debug 构建、V2 签名、16 KiB 对齐和最终模板资产对账均通过。候选尚未安装到设备，
O2/O3/O4 的真机复测仍待完成。

### 版本证据与更新策略

本轮按 2026-09-04 的公开官方端点核验并记录目标版本：Gradle Services `current=9.7.1`（已发布，
2026-08-19 构建，官方 checksum 与仓库常量一致）；Gradle 9.7.1 兼容性矩阵声明可在 JVM 17–26
运行，因此 Resolute `openjdk-25-jdk=25.0.4+7-1~26.04` 是兼容的 LTS 选择；Node 官方发行索引显示
`v24.20.0` 为 LTS、`v26.8.1` 为 current，Node 24.20.0 arm64 SHA-256 已固定，随包 npm 为
`11.19.0`；npm registry 的目标版本为 pnpm `12.3.4` 与 TypeScript `7.0.2`。Ubuntu Resolute
软件包页显示 `gradle=4.4.1-22ubuntu1`（明显落后），所以一键安装不再使用该包。
这些是本次安装合同的观察点，不代表永远不更新；后续更新必须重新核验官方版本、兼容矩阵、SHA、arm64
设备表现和磁盘/内存预算，再修改合同常量与文档。

### O4.1 — 工作区一键工具链对齐（本次补充）

- 版本审计发现，Ubuntu Terminal 的通用环境合同已经是 Gradle `9.7.1`，但三个工作区入口仍有
  独立旧值：Java 初始化命令为 `8.5`，Android 模板为 `9.5.0`，Flutter 模板为 `8.14`，且
  Flutter 模板的 AGP/Kotlin 仍为 `8.11.1/2.2.20`。
- 已按生态边界收口：Java 初始化与通用 Android 模板使用 Gradle `9.7.1`；Android 模板保留
  JDK 17（AGP 官方默认/最低运行基线），并对官方 Gradle binary 做 SHA-256 校验。Flutter 当前
  stable `3.47.2` 的官方 `gradle_utils.dart` 明确给出 Gradle `9.3.1`、AGP `9.1.0`、Kotlin
  `2.4.0`，因此 Flutter 模板按该组合更新，不强行使用 Ubuntu 通用的 `9.7.1`。
- Android/Flutter 的 Wrapper 及持久化环境标记同步更新为对应版本与 Kiyori 文案；写入新标记前会
  一次性移除同一脚本旧的 `operit` 环境块，避免升级后重复注入 PATH。旧下载归档不会被复用为新版本，
  校验失败会停止并暴露错误。根项目 Wrapper、`tools/shower` 和其他独立构建
  工具不在本补充切片内，避免改变既有主工程构建基线。
- 验收：模板/命令版本合同测试、两个 shell 脚本语法检查、Wrapper URL/SHA 对账、相关 App JVM
  测试、正式门禁和 Debug APK 构建；设备上的实际 Flutter/Android/Java 编译仍需现场验证。

## 不可破坏的兼容边界

- 保留 `com.ai.assistance.operit.terminal` namespace、AIDL、`installed-rootfs/ubuntu`、
  活动 `.kiyori_installed_ok` 与一次性历史 `.operit_installed_ok` 输入、`OPERIT_*`、native 文件名、PTY/OSC marker、`TerminalService` 和现有
  `/dev`、`/proc`、`/sys`、`/dev/pts`、`/sdcard`、app-data、`/data/local/tmp` bind 协议。
- 保留 Ubuntu 真实发行版身份；新 rootfs 的 `/etc/os-release` 必须明确为 26.04 LTS / Resolute，
  不注入 Kiyori 或 Operit 品牌到发行版文件。
- 保留 `/root` 用户数据语义，但不得复制旧的 `/etc`、`/usr`、`/var/lib/dpkg` 或整个 rootfs。
- 不捕获 `OutOfMemoryError`，不删除用户规则/包，不增加第二终端、第二数据源、静默直连或无证据
  降级路径。

## 分阶段实施与停止条件

### P0_BASELINE_AND_DESIGN — DONE

核对 Git、规则、rootfs 资产、安装/启动调用链、官方版本变化和工具链路径；形成 side-by-side
迁移决策。不得把旧 rootfs 原地 `apt dist-upgrade` 或直接覆盖作为升级方案。

### P1_ASSET_BUILD — DONE (ANDROID_DEVICE_RECHECK_PENDING)

1. 固定 Canonical 26.04.1 arm64 输入、SHA-256、架构、来源和许可证。
2. 从 Ubuntu Base 构建可运行的 Kiyori rootfs：保留当前能力所需的 `bash`、`coreutils`、`busybox`
   外部工具、`apt/dpkg`、CA 证书、locale、Python 3.14、`tar/xz`、网络工具、OpenSSH client、
   `ffmpeg` 等当前已安装包的能力；不安装 systemd 为 PID 1 的服务。
3. 生成后检查 `/etc/os-release`、动态链接器、`/bin/bash`、`/usr/bin/env`、`apt-get`、`dpkg`、
   `python3`、`tar`、`xz`、`ca-certificates`、`/etc/apt/sources.list.d`、架构和无异常绝对符号链接。
4. 以确定性 tar/xz 参数生成候选资产，记录字节数、展开大小、成员数、hardlink 数、SHA-256
   和包清单；归档不得含 tar hardlink member，未通过本地检查不得加入 Android assets。

停止条件：官方哈希不匹配、无法证明 arm64、基础命令缺失、包安装需要交互、压缩内容存在越界链接、
存在任意 tar hardlink member、或构建结果无法复现时，停在 P1，不放行 Android 资产。当前候选已
通过这些构建机停止条件；前一候选虽可复现且通过 Linux/QEMU 验证，但已被 Android 现场硬链接错误
否决。`/var/cache/ldconfig/aux-cache` 仍在归档前移除并由验证器禁止残留，Android 真机复测归入
P2/P5，不把构建机通过误写成设备通过。

### P2_RUNTIME_CONTRACT — IN_PROGRESS (DEVICE_FIRST_INSTALL_GATE_PASSED)

1. 新增 `ubuntu-rootfs-manifest.json` 与 `UbuntuRootfsManifest`（schema、发行版、版本、codename、
   架构、asset filename、asset SHA-256、expanded bytes、archive member/hardlink 数、生成输入
   digest、最小能力版本）。
2. `TerminalManager` 读取并校验 manifest；资产提取到临时文件后核对尺寸和 SHA-256，再用
   `ATOMIC_MOVE` 落盘，脚本文件仍每次更新。
3. 安装脚本锁定 `${UBUNTU_PATH}.install.lock`，解包到独立 staging；健康检查和用户 root 数据迁移
   通过后先将旧 rootfs 改名为带 PID 的可恢复备份，再移动新目录；任何失败保留旧目录并清理 staging。
4. 只有新目录健康检查和 `.kiyori_installed_ok` 写入成功后才删除新资产压缩包；旧备份保留到明确的
   后续清理动作，不由初始化流程隐式删除。
5. `UBUNTU_NAME`、`UBUNTU` 和安装标识兼容旧调用方；APT 源从硬编码 `noble*` 改为 `resolute*`，
   `SourceManager` 与 `TerminalManager` 共用当前 Resolute 发行版值。
6. `CacheManager` 覆盖新旧资产、staging、备份和锁目录，清理前检查挂载与活动会话。
7. 安装锁在 PID 文件缺失时不再提前删除；只有确认记录的 PID 已失效且 PID 文件未变化时才清理孤儿锁，其余情况
   等待并在超时后报告 owner 状态。安装合同检查覆盖 legacy 升级、同版本幂等、损坏归档、staging 健康检查失败后的
   旧目录恢复、锁/临时目录清理和用户 root 白名单迁移。
8. 解包成功后进度依次显示“extracted / finalizing”“Migrating Ubuntu user data”“Preparing Ubuntu network settings”
   和“installed”，避免大文件迁移或后续校验期间长期停留在单一的 Extracting 文案；tar 解包在隔离子进程中使用
    `umask 022` 保持归档模式；staging 使用 BusyBox `stat` 的模式位检查 required command owner execute
    bit，避免 Android host `[ -x]`/`access(X_OK)` 对 app-data 路径的误判。健康检查失败时另外输出失败
    条件、`bin/bash`、`etc/os-release`、`usr/bin/env` 及其物理目标的有限状态，诊断输出不改变门禁；
    实际 PRoot 启动仍必须在设备阶段验证。

停止条件：manifest/hash/架构校验失败、manifest 宣称或实际含 hardlink、旧目录保护无法证明、并发安装锁失效、路径逃逸、
APT 源仍写入 Noble、安装合同检查失败或任何现有 AIDL/PTY 合同测试失败。状态机、staging/backup 脚本、错误可观测性、
安装合同模拟（含 `stat` 模式位正反例）、Resolute 源和无 hardlink 资产门禁已通过本地检查；现场已验证首次实际切换，
但仍需确认旧 Noble→Resolute 备份保留、重复启动幂等、磁盘峰值和挂载链路后才能把 P2 标为 DONE。

### P3_USER_DATA_MIGRATION — IN_PROGRESS / verification_pending

只迁移白名单数据：用户项目、`/root/.code_runner`（按解释器版本重建）、`/root/.local`、
`/root/.cargo`、`/root/.rustup`、`.npmrc`、pip/uv 配置、`.ssh`、shell 配置和外部 `/sdcard`/
app-data 映射。迁移前生成清单与大小，迁移后逐项校验路径、权限和可读性。

现场为新环境且未提供旧 rootfs 用户项目样本；必须重建或验证：Python 3.14 venv、Node native addon/Node 24、pnpm/tsc、Rust/Cargo、Go/cgo、
C/C++、Ruby、OpenJDK 25/Gradle 9.7.1、ffmpeg、SSH。禁止整体复制旧 dpkg 数据库、`/etc`、`/usr` 或
旧 `/var`。

### P4_AUTOMATED_VALIDATION — IN_PROGRESS

依次执行 `git diff --check`、terminal 定向 JVM、父仓库定向 JVM、正式开发准备、必要的架构/新鲜克隆
检查、串行 `./gradlew :app:assembleDebug --no-daemon --console=plain`，并核验 APK 包名、版本、
arm64、签名、16 KiB 对齐和新资产是否只出现一份。新增 rootfs manifest/脚本/安装链路测试，覆盖
旧资产首次安装、已安装旧资产升级、同版本幂等、hash 错误、staging 失败、并发锁、异常退出和
备份保留。

当前 P2 安装合同验证入口：

```powershell
.\.venv\Scripts\python.exe -B ci\script\check_ubuntu_install_contract.py --repository .
```

该检查从 `TerminalManager.kt` 提取实际生成的 `install_ubuntu` 函数，执行 shell 语法检查，并在隔离 WSL 临时目录中
验证 legacy rootfs 升级、用户数据白名单、同版本幂等、损坏归档、模式位正反例和 staging 健康检查失败恢复；它不触碰
Android 设备或仓库运行目录。

### P5_DEVICE_ACCEPTANCE — IN_PROGRESS / verification_pending

至少 Android 8/API 26 与 Android 16 两个平台、两台 arm64 手机、4/6/8 GiB RAM 档位。覆盖冷启动、
首次解包、旧 24.04→26.04 迁移、visible PTY、hidden executor、`super_admin:terminal`、
`code_runner` 全工具链、文件挂载、APT 国内源/TLS、SSH、超时/取消/重建/并发，并记录
`dumpsys meminfo`、Java/native PSS/RSS、GC、CPU、磁盘峰值和初始化时延。

## 回滚点与数据安全

- P1 仅产生构建机临时目录和候选资产，不触碰设备。
- P2 切换前旧 rootfs 必须仍可读，备份目录和 manifest 一起保留；切换失败只删除 staging，不能删除
  旧目录或用户白名单数据。
- P3 迁移按文件白名单和原子批次执行；任何一批校验失败停止，不标记迁移完成。
- P5 设备验收失败时保持旧资产/旧默认路径可恢复，不能用“能启动一次”替代稳定性结论。

## 完成定义

只有 P1–P5 的停止条件全部解除、新环境成为默认 `installed-rootfs/ubuntu`、工具链和 Android
矩阵通过且证据已记录，状态才能改为 `completed`。没有设备时最多为 `verification_pending`，
不得宣称“完全切换完成”。
