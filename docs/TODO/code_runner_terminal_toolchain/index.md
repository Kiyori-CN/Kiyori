# code_runner 与终端工具链收口

## 环境配置/设置同步路由增量（2026-09-06）

状态：已完成代码与本地自动化验证；目标 Android 设备上的重复点击、SurfaceView 合成和输入法行为仍保持 `verification_pending`。

### 本轮问题与可验证目标

- 现场反馈表明，上一轮的 `requestedRoute` 只约束了 Back 和重复请求，实际内容仍由 `NavHost` 异步 back stack 组合；旧的 `TerminalHome` 及其 `SurfaceView` 在切换帧中仍可能覆盖环境配置/设置页，造成回退到终端、输入法重新出现或点击没有反馈。
- 环境配置和右侧设置必须共享同一个同步路由 owner：任何点击只产生一次目标路由，目标页一旦提交就不再继续组合终端首页；系统 Back、页面 Back 和安装完成必须回到终端首页。
- 终端输入连接、焦点和软键盘必须在路由提交前释放，目标页组合后不能被旧的 SurfaceView/IME 回调重新夺回。

### 冻结方案与边界

1. 移除 `TerminalScreen` 内部 `NavHost`/`NavController` 作为可见页面状态源，保留现有 route 常量和 Back 语义，用单一同步状态 `activeRoute` 通过 `when` 只组合一个页面。
2. 环境配置和设置按钮都经过 `TerminalHome.prepareForNavigation()`，随后由 `TerminalScreen.requestRoute()` 原子地锁定目标 route；重复点击在目标页已组合后不会再次入栈或重新创建页面。
3. 保留现有 `CanvasTerminalView` 焦点/IME 释放合同和 AI 电脑宿主的黑色不透明层；不新增终端实例、并行导航器、延迟重试或回退逻辑。
4. 增加路由状态纯函数/源码合同，覆盖 home -> setup/settings、setup/settings -> home 和同目标幂等；运行 Terminal 定向测试、正式门禁、差异检查与串行 Debug APK 构建。

### 风险与验收边界

- 该改动只改变终端内部页面组合方式，不改变环境包版本合同、安装命令、终端会话、设置数据或外层 AI 返回行为。
- 本地 JVM 与 APK 只能证明同步状态和资源生命周期合同；首次启动连续点击、厂商 SurfaceView 合成、真实输入法以及 Ubuntu 实际安装仍需真机复测，完成前保持 `verification_pending`。

### 本轮实现与验证

- `TerminalScreen` 已移除内部 `NavHost`/`NavController`，以 `activeRoute` 作为唯一同步可见页面 owner；环境配置与右侧设置均先由 `TerminalHome.prepareForNavigation()` 清理焦点/IME，再提交目标 route。设置页返回不会修改首次启动偏好，环境配置页返回/安装完成继续确认配置完成。
- terminal 子模块提交 `9073eab` 已推送 `origin/main`；`:terminal:testDebugUnitTest` 通过（18 actionable tasks，5 executed），terminal `git diff --check` 通过。
- 父仓库 `:app:assembleDebug --no-daemon --console=plain` 通过（235 actionable tasks，30 executed）；Debug APK 为 `app/build/outputs/apk/debug/app-debug.apk`，大小 `512626322` bytes，SHA-256 `7286BC5034F283CBF5440952F2E2FB62C7D9006B90570606B77E347D31B19471`。
- 正式开发准备检查通过；候选 revision 的 Markdown 链接检查和父仓库最终 ref 对账在提交后执行。目标 Android 设备上的连续快速点击、设置按钮命中、SurfaceView 合成、输入法及 Ubuntu 实际安装仍为 `verification_pending`。

## 环境配置版本与首帧导航稳定性（2026-09-06）

状态：本地实现、自动化验证和 Debug APK 已完成；目标 Android 设备上的首次点击/输入法/Ubuntu 实际安装仍为 `verification_pending`。

### 目标与验收

- 环境配置页的 Gradle 文案只保留“Gradle 官方稳定版”，不再显示 Ubuntu 旧包说明；自动配置的
  Node.js、npm、pnpm、TypeScript、OpenJDK 与 Gradle 合同必须与 2026-09-06 官方稳定版本一致，且
  安装命令继续使用 Ubuntu 兼容的非交互、官方归档校验和明确路径。
- AI 对话中任何时刻点击“环境配置”按钮，都在同一帧锁定 setup 路由，不能回到终端主页、重复入栈或
  让终端 SurfaceView/输入法重新取得焦点。
- 版本探针、安装命令、资源文案、README/CONTEXT 和定向测试只能引用同一份版本合同；环境页路由和
  SurfaceView 生命周期必须有可回归的 JVM/源码合同证据。

### 研究事实

- `TerminalScreen` 当前用 `NavController.currentDestination` 判断是否已在 setup；导航调用发生后该值
  可能仍是 home，连续点击因此不能作为幂等闸门。`TerminalHome` 在回调前清理 Compose 焦点，但
  `CanvasTerminalScreen` 的 `AndroidView.factory` 仍会 `post { requestFocus() }`，释放路径也没有清除
  native 焦点和输入连接；这解释了首几次点击时回到 home/输入法弹出的时序窗口。
- Gradle Services `current=9.7.1`、官方 checksum 与 `TerminalEnvironmentContract` 一致；Node 官方
  索引的 `24.20.0` 是当前 LTS（`26.8.1` 为 current），OpenJDK 25 最新 LTS 为 `25.0.4`，TypeScript
  最新为 `7.0.2`。npm registry 当前稳定 pnpm 为 `12.3.4`，现有 `11.25.0` 已过期。
- Ubuntu apt 管理的 Python、Go、Ruby、SSH 继续由 Resolute 源按包管理器提供稳定版本；不改成第二下载源，
  避免在弱网和设备架构上引入无法验证的安装路径。

### 实施方案

1. 将 pnpm 合同更新为 `12.3.4`，同步中文/英文资源、Terminal README/CONTEXT、版本证据和现有契约测试；
   保留 Node 24 LTS、OpenJDK 25 和 Gradle 9.7.1 的已验证 SHA/兼容边界。
2. 在 `TerminalScreen` 增加单一的请求路由状态：点击 setup/settings 时先发布目标路由，再调用 NavController；
   回退和安装完成也通过同一状态 owner，重复点击、异步 back-stack 发布和首帧系统 Back 均按目标路由处理。
3. 删除全屏终端 `AndroidView` 的首帧自动 `requestFocus`，在 `CanvasTerminalView.release()` 和 Compose
   `onRelease` 清理焦点、输入连接和软键盘，保留用户点击终端时的正常输入行为。
4. 补充纯函数/源码合同测试，覆盖请求路由幂等、setup/settings Back 顺序、稳定版本探针和禁止 Ubuntu
   `gradle` 4.4.1 安装；运行 terminal 定向测试、文档/差异检查、正式门禁，并按项目规则串行构建 Debug APK。

### 风险与边界

- 不新增第二个终端、路由或环境状态 owner，不修改 Ubuntu rootfs、协议标识、APT 包名和已有用户数据。
- Node 仍选择 LTS 而不是 current，保证 Android/Ubuntu 工具链兼容；pnpm 的升级会使旧版本探针重新显示未就绪，
  用户可通过同一环境页按新合同安装。
- 本地 JVM/构建不能替代目标 Android 设备上的首次点击、输入法和 Ubuntu 实际安装验收；设备完成前状态保持
  `verification_pending`。

### 实现与验证结果

- 终端子模块提交 `f3a3a30` 已推送 `origin/main`。环境页版本合同为 Node.js `24.20.0` LTS、npm
  `11.19.0`、pnpm `12.3.4`、TypeScript `7.0.2`、OpenJDK 25 和 Gradle `9.7.1`；Gradle
  文案已收敛为“Gradle 官方稳定版”，不再显示括号说明。
- `:terminal:testDebugUnitTest` 通过（18 actionable tasks，9 executed）；`git diff --check`、
  `check_formal_readiness.py --repository . --require-main`、`check_fresh_clone.py --repository .`
  均通过。测试覆盖版本探针、非交互安装、Gradle 禁止 Ubuntu `4.4.1`、路由请求幂等和 Back 顺序。
- 串行 `:app:assembleDebug --no-daemon --console=plain` 通过（235 tasks，33 executed）。最终 APK
  `app/build/outputs/apk/debug/app-debug.apk` 为 `512626322` bytes，SHA-256
  `3D421B8BEC6CA01A4A46517DF043805D3293CF866E76F0A36A39F3B6AAA190A3`；包名/版本为
  `com.kiyori / 45 / 0.1.0`，min/target/compile SDK 为 `26/34/37`，仅 `arm64-v8a`，唯一 launcher，
  Android Debug V2 单 signer 与 `zipalign -c -P 16 -v 4` 通过。Ubuntu APK packaging 通过，Resolute
  资产 1 份、Noble 资产 0 份。

状态：2026-09-01 现场回归增量的本地实现、自动化验证与 Debug APK 已完成；真机验收仍为 `verification_pending`。
目标是让 Agent 能准确区分 code_runner、super_admin、可见终端、Ubuntu/proot、
Android Shell、Python venv 和 Node 工作区，并消除隐藏执行器超时后遗留进程、输出失控和
`params must be a valid JSON object` 这组三类现场问题。

## TAB 输入保真与失效 cwd 修复（2026-09-05）

状态：根因、修复、本地回归与 Debug APK 已验证，Android/proot 现场验收保持
`verification_pending`；本轮提交/推送与候选克隆结果由最终交付记录提供。

1. 修复前分别检查 ToolPkg 字符串参数、code_runner here-document、可见 PTY 包装及真实 Bash
   Readline 行为，使用 Go 源码和 heredoc 字节对比区分参数损坏与交互按键解释。
2. 在现有命令包装 owner 内实现保真传输，审查 TAB、控制字符、引号、反斜杠、中文与多行边界；
   不更换终端、会话、AIDL、超时/取消或后台执行 owner，不改原始键盘输入合同。
3. 按用户明确要求，失效 cwd 在提交命令时恢复到 `$HOME`；恢复失败必须保留失败结果，
   不执行依赖有效目录的用户命令。正常 cwd、export 和退出码保持原语义。
4. 运行修复前后 PTY 对照、参数/命令定向测试、正式准备检查、文档链接、Terminal 构建与
   Debug APK；审阅父仓和子模块差异，先发布子模块，再验证父仓 gitlink 并提交推送。

风险与恢复点：交互 shell 会先解释输入按键；命令载荷不能直接暴露给 Readline。保留基线
`terminal@7ec4cfb` 的协议、会话队列及完成 marker；本轮不安装或操控设备，不混入并行重构。

### 已确认根因与实现

- 参数边界 `JsToolManager.convertToolParameterValue` 对字符串返回 `rawValue`；
  `JsEngine` 使用 JSON 序列化，code_runner here-document 和 super_admin 转发均保留 TAB。
  `ToolPkg invocation argument was rejected` 对应缺失参数或类型不符，不是字符替换路径。
- 旧 `buildCommandWithExitMarkerProtocol` 把原始多行内容放入 `eval '…'` 后写入交互 PTY。
  Bash Readline 在 shell 解析之前处理 TAB，即使 TAB 位于尚未闭合的单引号中也会补全。
  WSL Bash 5.1 的隔离目录只含 `.bashrc`、`.profile` 时，实际文件从 `09 66 6d 74` 变成
  `2e 66 6d 74`；本机 Go 随后报 `syntax error: unexpected ., expected }`。空目录则吞掉 TAB。
- 新 `CommandEnvelope.kt` 使用单行 ASCII 的 Bash ANSI-C quoting：编码 UTF-8 字节、
  控制字符、引号、反斜杠及历史展开 `!`，由原 shell 在 Readline 收完输入后解码并 `eval`。
  不使用外部 base64 进程、临时执行文件或第二个 shell。原先命令包装对 CR 和尾部 LF 的改写
  已移除；code_runner 自身既有源文件换行规范仍为 CRLF/CR → LF 并补齐最后一个 LF。
- NUL 不能由 Bash 字符串表示，故在队列/命令状态变更前明确拒绝。按键 `sendInput` 不编码，
  Ctrl+C 和 TAB 补全仍是交互输入语义。实际目录查询失败才执行用户要求的 `$HOME` 恢复；
  失败时不执行用户命令，通过既有 OSC marker 返回失败退出码。
- 官方机制参考：[Bash 命令行编辑](https://www.gnu.org/software/bash/manual/html_node/Command-Line-Editing.html)
  与 [Bash 手册的 ANSI-C Quoting](https://www.gnu.org/s/bash/manual/bash.html)。

### 本地回归证据

| 验收项 | 2026-09-05 证据 | 边界 |
| --- | --- | --- |
| Go TAB 缩进 | 旧 PTY 文件编译失败；新生产 Kotlin 包装经真实 PTY 后字节相同，Go 输出 `hi` | 编译器为 Windows Go，非设备上的 `run_go` |
| terminal heredoc / Makefile | 文件逐字节包含 `0x09`，Makefile recipe 与预期完全相等 | WSL Bash PTY |
| Python TAB / 空格缩进 | 实际 Python 输出 `中文 hi`、`space hi` | WSL Python |
| Node 中文/引号/反斜杠/美元/反引号/感叹号 | 新包装经 PTY 写入的 JS 使用本机 Node 执行，输出精确相等 | Windows Node |
| 其他控制字符 / CR / 中文 / emoji | 1–31 与 DEL 的文件字节、混合引号及 Unicode 精确相等；NUL 明确拒绝 | JVM + WSL PTY |
| 长输入 | 1800 次 TAB/中文/引号混合载荷完整落盘 | WSL PTY |
| cwd / export / jobs | 跨命令保留；后台任务完成后读取正确 | WSL PTY |
| cwd 删除 / HOME 无效 | 前者恢复并继续，后者退出码 1 且用户命令未执行 | WSL PTY |
| Ctrl+C / 会话继续使用 | PTY 注入 `0x03` 中断 `sleep`，下一命令成功 | Android 超时回调与 UI 仍待验收 |
| 7 种语言脚本与终端前后台/输入转发 | `node --test tools/example_packages/terminal_input.test.mjs`，9/9 | 生产 JS + 宿主调用边界 mock，非伪称设备运行 |
| 终端测试 / 库构建 | `:terminal:testDebugUnitTest :terminal:assembleDebug`，64/64、零跳过，构建通过 | Windows 显式设置 `KIYORI_PTY_WSL_DISTRO=Ubuntu-22.04` |
| ToolPkg / 正式准备 | `ci.test.test_toolpkg_sync` 15/15，formal readiness PASS | 本地 |
| Debug APK | `:app:assembleDebug --no-daemon --console=plain`，3m51s / 235 tasks，构建通过 | 开发包，非正式发行 |

Debug APK：2026-09-05 13:28:36 +08:00，`app/build/outputs/apk/debug/app-debug.apk`，
483,708,439 bytes，SHA-256
`9DC0F990E83E2D6416DBCCA9393DAF5F3643AB81DD855EC0EB3249F51C3A0345`。
包名/版本为 `com.kiyori / 45 / 0.1.0`，min/target/compile SDK 为 26/34/37，
Android Debug V2 单 signer、16 KiB zipalign、唯一 ZIP entry 和 arm64 ABI 通过。
DEX 包含新的 `CommandEnvelopeKt`，code_runner/super_admin 资产与源码逐字节相同；
Ubuntu APK 检查确认一份 Resolute、零 Noble。Terminal 修复提交为
`6180a86e4e2c45f8f05a3f93d20a0ef4a97d11cd`，已核对 local/tracking/remote 相同。

设备剩余验收：安装本轮 APK 后，在 Ubuntu 26.04/aarch64 的真实 ToolPkg 中运行 `run_go`、
`run_python`、Node、多行 heredoc/Makefile、前后台会话、Ctrl+C、工具超时和 deleted cwd。
本地证据不能代替 Android/proot 的端到端结果。

## 研究结论

| 面 | 唯一 owner | 执行形态 | 数据/环境边界 | 可见性 |
| --- | --- | --- | --- | --- |
| `super_admin:terminal` | `TerminalManager` 的普通 PTY session | Ubuntu/proot 中的交互 shell | 会话 cwd、环境和 shell 状态连续 | 终端 tab、PTY 屏幕和历史可见 |
| `super_admin:shell` | Android Shell 工具 | Shizuku/Root 的 Android shell | 不进入 Ubuntu rootfs，不共享 Ubuntu venv | 不进入 Ubuntu 终端 |
| `code_runner` | `code_runner_session` 普通 PTY session | 同一 Ubuntu/proot 终端链路 | Python 固定 `~/.code_runner/py`，Node 固定 `~/.code_runner/node` | 终端 tab、屏幕和命令历史可见 |
| `execute_hidden_terminal_command` | `LocalTerminalProvider` hidden executor | 无 PTY 的复用 shell + `setsid` 子进程 | 仍是同一 Ubuntu/proot 文件系统，但不继承可见 PTY | 仅工具结果和日志可见 |

可见终端与 code_runner 共享 Linux/proot 文件系统和挂载，但不是同一个 shell 会话；shell 的
局部 cwd、临时变量和交互状态不能作为跨会话契约。`super_admin:shell` 直接作用于 Android
系统，不能用来判断 Ubuntu 内的 Python/Node 安装位置。

可见命令超时通过目标 session 的 PTY 发送 Ctrl+C，不改变当前选中的终端 tab；若 PTY writer
已失效，命令会立即失败并清理执行状态，避免后续队列永久等待。

Python 包安装命令统一使用 `~/.code_runner/py/bin/python -m pip`，执行脚本也使用同一个绝对
解释器；因此 code_runner 安装的包不等于 Android/Ubuntu 系统 `python3` 的全局 site-packages。
Node 包统一写入 `~/.code_runner/node` 的 `package.json` 和 `node_modules`。新增的环境探针
会返回 `sys.executable`、`sys.prefix`、`sys.base_prefix`、pip 位置、Node/npm 位置、cwd、
PATH、rootfs 和会话 ID，避免 Agent 依据 PATH 猜测包归属。

## 实施阶段

1. 共享解析 `package_proxy.params`：接受一个完整 JSON object 或明确的 JSON 字符串包装，拒绝
   数组、数字、截断内容和额外文本；保持 `tool_name`、provider call ID 与转发顺序不变。
2. hidden exec 生命周期：移除外层与收集器的重复竞速超时；记录 active PID，超时按进程组终止，
   仅在协议无法收敛时关闭 shell；输出 channel 使用有界缓冲，捕获和日志保留有界预览。
3. code_runner 可见会话：使用一个固定标题的普通 PTY session，所有命令显式传入超时；安装和
   执行结果报告真实会话与环境路径；包名、文件路径采用 shell quoting；名称级创建锁保证并发
   调用只复用一个 `code_runner_session`，每条可见命令通过随机 UUID 退出标记返回真实 exit code，
   不改变持久 cwd/环境。
4. 资产、类型、测试和本文档同步；不新增第二套终端、Python 或 Node 状态 owner，不改变
   ToolPkg/Package ID、AIDL、namespace、Ubuntu rootfs 和现有 API 名称。

## 环境配置回归修复（2026-08-31）

### 已确认根因

- 环境配置页创建 `setup-check` 可见会话，并在每个包检查中调用 `switchToSession`；检查会话会
  抢占用户当前 tab，关闭时又依赖“当前会话”选择结果。
- `TerminalEnv.onSetup()` 读取当前会话后把全部步骤拼成一条超长命令；会话尚未 READY 时，旧的
  `sendCommand()` 路径会把整批内容当作原始输入发送，且无法报告具体失败步骤。
- 确认回调没有提交闸门。导航动画完成前的重复点击会产生不同 command ID 的两次完整安装，现场
  输出中的两组 UUID 退出标记与该行为一致。
- 包检测依赖可见 PTY 完成输出文本，不能以 shell 退出码作为权威结果，也会与用户命令队列互相
  干扰。

### 修复合同

1. 包检测只使用 `TerminalManager.executeHiddenCommand()` 的同一 Ubuntu/proot provider，按固定
   `executorKey` 串行探测，不切换可见 tab；安装状态以 `HiddenExecResult.exitCode` 与结构化输出
   共同判定。
2. 环境配置确认在 UI 与 `TerminalEnv` 两层均只允许一次提交。提交时锁定一个已有会话 ID；若会话
   尚未 READY，等待其 READY 后再执行，不能把安装命令作为初始化输入发送。
3. 安装步骤按列表逐条发送到目标可见 PTY，并等待每步完成；任一步非零退出或超时立即停止后续
   步骤，保留真实历史、输出和退出码。
4. 取消 `setup-check` 可见会话及其相关切换逻辑，不新增第二套环境状态 owner；SSH provider 仍
   复用既有 hidden exec 合同。

### 环境变量分层

| 变量 | 赋值层 | 实际作用 |
| --- | --- | --- |
| `HOME` | Android launcher 为 app `filesDir`；进入 Ubuntu 后由 `env -i` 固定为 `/root` | 外层用于定位 `common.sh` 和资源；Ubuntu 内决定 pip/npm/uv、`~/.code_runner` 的持久位置 |
| `PREFIX`、`TERMUX_PREFIX` | launcher 的 `filesDir/usr` | 提供 proot/busybox/native launcher 的宿主前缀，不代表 Ubuntu 包安装目录 |
| `PATH` | launcher 先把 `filesDir/usr/bin` 放首位；Ubuntu 内重置为 `/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin`，登录 shell 再加载 root profile | 防止 Android busybox 与 Ubuntu 命令混用；Node 全局 bin 不依赖 PATH，而由 `npm prefix -g` 解析 |
| `LD_LIBRARY_PATH`、`PROOT_LOADER` | launcher | 仅供宿主 native `proot`/loader 链路使用；不会改变 Python/Node site-packages |
| `TMPDIR`、`PROOT_TMP_DIR` | launcher 的 app `filesDir/tmp` | proot 临时文件、hidden executor 脚本和跨进程共享临时目录；不是 Ubuntu `/tmp` 的包目录 |
| `UBUNTU_PATH`、`USE_CHROOT` | `common.sh` | 指向 app 私有 rootfs 和选择 proot/chroot 启动路径；包管理器始终在该 Ubuntu rootfs 内运行 |
| `OPERIT_UID/GID/GROUPS` | `common.sh` | 传递 Android 进程身份用于 rootfs 权限/组映射，不是 Android Shell 的 root 授权 |
| `TERM`、`LANG` | launcher 与 Ubuntu `env -i` | 终端渲染和 locale；不表示存在真实 TTY 或改变安装来源 |

因此“全局”必须带边界理解：`apt`/`npm -g` 是 Ubuntu rootfs 内的系统/全局安装，`pipx` 默认写入
Ubuntu `/root/.local`，code_runner Python 包只写入 `/root/.code_runner/py`，Node 项目依赖只写入
`/root/.code_runner/node`。Android `super_admin:shell` 看到的是另一个 Shell/Root 命名空间，不能
用来判定这些目录是否存在。

### 验收补充

- 重复确认只能生成一组安装命令；目标会话切换或初始化期间不会丢命令、误发原始输入或永久排队。
- `command -v`/`dpkg`/Node toolchain 检测均覆盖成功、非零退出和异常输出；检测过程不改变用户
  当前 tab。
- 环境配置命令逐步出现在目标终端历史，失败步骤可定位，重新打开页面能通过 hidden probe 识别
  已安装状态。

### 现场回归修复（2026-08-31）

现场输出显示环境页探针和安装流程均没有可见进展，首次进入还把 Ubuntu 判定为未安装。根因是
hidden executor 通过 `login_ubuntu '/bin/bash --noprofile --norc'` 启动了一个没有 `-s`/`-i` 的
非交互 Bash；它在管道中立即退出，后续探针只能得到空结果。该启动路径也没有先执行
`install_ubuntu`，因此首次 rootfs 尚未完成时所有包检测都会失败。

现行契约是：hidden executor 先复用 `install_ubuntu`、`configure_sources` 和 `fix_permissions`，
再以 `/bin/bash --noprofile --norc -s` 进入 Ubuntu。`-s` 保证 shell 持续读取同一 stdin 管道，且
不产生交互提示符噪声；启动阶段确认进程仍存活后才接受后续命令。可见 PTY 的退出标记以显式
`marker + $?` 两段格式参数输出，并以显式换行结束；此前只有一个 `%s` 时 `$?` 会被
`printf` 丢弃，现场只显示 UUID 冒号而没有退出码，导致安装步骤被判定为未完成。首次 rootfs 解压和会话 READY 等待
上限统一为 180 秒，防止慢速 Android 存储在 30 秒时被永久关闭。

命令退出标记随后改为 ANSI OSC `1337` 隐形载荷：新包络传输
`__KIYORI_COMMAND_EXIT__:<uuid>:<exit-code>`，Canvas/ANSI 解析器消费该控制序列，不再把
`OPERIT` 或 Kiyori 协议文本绘制到用户终端；解析器仍接受旧版 `__OPERIT_COMMAND_EXIT__` 文本，
用于已存在会话的兼容。标记在原始缓冲区层先解析，因此跨读取 chunk 仍能得到真实退出码；会话级
显示过滤器同时跨 chunk 吞掉未闭合 OSC，防止 ANSI 扫描器把标记尾巴绘制到 Canvas。

同一现场还暴露了首帧路由竞态：`TerminalScreen` 不能先以终端主页组合，再由异步 effect
纠正到环境页；用户首个点击可能在纠正前打开 `setup`，随后被 effect 导航回主页。现行实现
在组合阶段一次性读取 `terminal_prefs` 决定起始路由，并只允许当前路由不是目标页时执行环境/设置导航，
因此不会重放或抢占用户已经开始的导航。

### 输入法与环境页现场增量（2026-08-31）

AI 电脑模式原先把终端嵌在 AI 对话浮层中，却让宿主窗口回落到 `ADJUST_PAN` 并启用全局
`imePadding`；终端输入框获得焦点时 Android 会平移整个窗口，造成顶栏和终端内容“上移再恢复”。
现行策略由终端独占：AI 电脑模式请求 `ADJUST_NOTHING`、关闭宿主全局 IME padding，
`TerminalHome` 本地把 IME 高度作为布局底部 padding，终端 `SurfaceView` 与工具栏因此保持
互不重叠，不再用仅移动像素的 `graphicsLayer` 平移底部输入/工具栏区域。环境/设置按钮在导航前
取消延迟弹键盘、隐藏 IME、强制清除焦点；AI 顶栏终端开关在打开和关闭时也释放当前输入连接，
并在终端浮层销毁时再次清理原生输入连接，避免环境页在转场中闪退或返回后聊天输入框残留
终端 IME 位移。
启动权限修复仍执行原有 group 校正，但仅在实际追加 group 时显示进度，已修复的 Ubuntu 会话
重新打开时不再反复显示 `Fixing permissions`。同步安装入口还会检查目标会话存在、READY 且不处于
交互输入态，避免命令未接受时静默等待超时；环境初始化完成标记在 hidden probe 与可见 PTY 并发
访问下保持可见。

自动配置命令另有独立的非交互合同：`dpkg`、`apt-get install/update/upgrade` 与 NodeSource
安装均显式设置 `DEBIAN_FRONTEND=noninteractive`；脚本安装使用 `apt-get`，防止 debconf 或维护
脚本等待不存在的键盘输入。源设置的 selected ID 在读取时校验当前目录，历史上已删除或损坏的
自定义源会原子修复到对应内置默认源，并记录警告，不再把异常延迟到环境页点击时的空指针。

本次现场回归又确认 AI 电脑模式的宿主层存在点击与合成竞争：外层空操作 `detectTapGestures`
会在 `SurfaceView` 与 Compose 工具栏之间争夺指针，默认 `NavHost` 转场还会让旧终端 surface
在 setup 页面进入期间继续参与合成。宿主因此保持黑色不透明背景，工具栏使用布局级 inset 与
native surface 分离，并关闭 setup/home/settings 的内部转场动画。后续纵向滚动复测证明
Final-pass 位移消费仍会干扰 sub-slop 累积，现行实现只保留被命中的前景 pointer node，不消费
未决方向事件；这样既隔离下层 AI 对话 sibling，又保留子页纵向滚动和祖先 pager 横向导航。

### 环境/设置页滚动、Pager 与系统 Back 增量（2026-09-01）

最新安装包复测仍能在 AI 电脑的环境页和终端设置页触发三类问题：纵向拖动偶发没有响应，
用户需要的右滑返回软件首页被禁用；通过系统 Back 返回软件首页后，首页也无法再横向进入负一屏
或 AI 对话页。同时首次安装后的包状态会长时间保持旋转，Python 链接、虚拟环境和 pip 即使
已安装也不打勾。

根因已分别落实到代码合同：终端宿主在 Final pass 消费子控件尚未认领的 sub-slop 移动，导致
`LazyColumn`/`verticalScroll` 偶发无法积累到纵向拖动阈值；上一增量又把 `showAiComputer`
直接映射为 Shell pager 全程锁定，因而取消了产品要求的右滑返回。终端内部 `NavHost` 没有自己的
系统 Back owner，事件会先改写外层 Shell 页面，而 `ChatPanelMode.TERMINAL` 仍保持打开，最终把
手势锁带回软件首页。环境探针另外曾逐包提交 hidden 命令，并把 `dpkg-query -f='${Status}'`
真实输出 `install ok installed` 错判成带 `Status:` 前缀。

现行单一路径方案：终端宿主只建立前景 sibling 的命中路径，不消费方向尚未决出的移动；环境页
`LazyColumn`、设置页 `verticalScroll` 与唯一 Shell pager 分别持有纵向和横向手势。终端显示时
忽略底层聊天内容的旧 gesture owner，但不禁用 pager。终端 `NavHost` 按“环境/设置 -> 终端主页
-> 关闭 AI 电脑”逐级持有系统 Back，并只在 AI Home 真正 settled 且可见时启用，滑到软件首页后
不能从屏外抢 Back。环境和齿轮入口使用稳定的 40dp 触控目标。探针合并为单次结构化 hidden
command，返回每个包的明确 `0/1`；Python 项目按 `python`/`python3` 实际链接、
`python3 -m venv` 和 `python3 -m pip` 能力检查，uv 在规范化的 `$HOME/.local/bin` 路径下校验
版本；探针协议缺失或执行失败显示“无法识别”，不伪装为“未安装”。

实施与验收计划：

1. [DONE] 保留唯一 `PagerState`、AI Host、Terminal `NavController` 和 `ChatPanelMode` owner，移除
   终端面板全生命周期 pager 锁与 Final-pass 位移消费；
2. [DONE] 增加可见性受控的终端系统 Back 链，关闭面板时同步释放 IME 与聊天手势状态；
3. [DONE] 扩大环境/齿轮入口触控区域，保留现有功能、路由名、AIDL 和持久化目录；
4. [DONE] 保留单次环境探针与 Python/DPKG 能力识别，并覆盖路由、pager owner 和识别回归；
5. [DONE] `LocalKiyoriAiHostSystemBackEnabled` 位于 Operit UI 宿主组件包，由 Kiyori Shell 只提供
   可见性值，避免 Operit UI 反向依赖 Shell；ARCH024 App Shell 规范化哈希从
   `1EC7AF44BD739009519193F9BE9AE986F5958CB7663A26CDEA736E12061EF17D` 更新为
   `2D785F8C7C2C54219BCE3FB1A53E5A0AEA952C2E7E3B28162A997072D5D6DBE2`，完整 import 快照只新增
   该宿主 Local；
6. [DONE] 完成父/子仓库测试、正式门禁、ARCH024、Debug APK 与最终差异审计；Git 提交和远端
   ref 状态由本次交付核对单独记录；
7. [PENDING] 真机连续上下拖动、左右回首页/AI Home、系统 Back、输入法、首次识别耗时与安装后勾选。

## 验收矩阵

- `package_proxy`：对象、转义对象、fenced JSON、数组/标量/截断文本和重复字段均有 JVM 回归。
- hidden exec：协议解析、超时状态、进程组 PID 处理和输出截断有 JVM 回归；目标设备仍需复测
  真正的长命令、忽略 SIGTERM 的子进程和应用进程回收。
- code_runner：资产与 examples 字节一致，TypeScript 类型检查通过；运行时探针能明确显示
  venv、Node workspace、cwd、PATH 与 sessionId；可见 PTY 返回真实退出码，脚本临时路径按调用
  唯一化；真实终端 UI、包安装和跨进程回收保持待验证。
- 工程：`git diff --check`、相关 JVM/Terminal 测试、formal readiness、串行
  `:app:assembleDebug --no-daemon --console=plain` 已完成，并核验 Debug APK 元数据、签名和 16 KiB
  对齐；目标设备上的会话可见性、包安装、进程回收和高输出表现仍待现场验收。

### 2026-09-01 现场回归增量验证

- 修复首路由竞态、源 ID 修复、非交互安装合同、命令 ID 绑定以及 AI 电脑层 SurfaceView 点击竞争后，Terminal 全套 `37/37` JVM 测试通过；父仓库
  `AiChatImePolicyTest`、`ToolExecutionManagerTest`、`PackageProxyParamsTest` 定向测试共 `12/12`
  通过。
- `npm exec -- tsc -p examples/tsconfig.json --pretty false` 与
  `check_formal_readiness.py --repository . --require-main` 通过。
- `:app:assembleDebug --no-daemon --console=plain` 串行成功；Debug APK 为
  `app/build/outputs/apk/debug/app-debug.apk`，`503695441` bytes，SHA-256
  `827910FC5AF01D50C7133ACEF2D891C2D5A41EEAA5AD08EE4B758EA03EA84A73`。
- 本轮后续增量将非全屏终端的 IME 处理改为布局级 bottom padding，移除工具栏的
  `graphicsLayer` 平移，释放 AndroidView 前先隐藏 `SurfaceView`，并在终端浮层销毁时清理
  原生输入连接；同时为 Ubuntu 注入 `USER/LOGNAME/SHELL`，APT 源补齐 `noble-security`。
- 本轮增量后的 Terminal JVM、父仓库定向 JVM、formal readiness、`git diff --check` 与
  `:app:assembleDebug` 均通过；真机首次启动、环境页重复进入/反复点击、Node.js 安装、真实
  PTY marker 画面、SurfaceView 合成与点击消费、超时进程回收和 code_runner 环境探针仍未在设备
  上复测，状态保持 `verification_pending`。

### 2026-09-01 手势与 Back 最终本地证据

- Terminal 全套为 `9 suites / 38 tests`，App 最终全量为 `321 suites / 1915 tests`，均为零
  failure、error 和 skip；手势、Back、ToolExecution 与代理参数四个定向 App 套件为 `89/89`。
- formal readiness、父/子仓库 `git diff --check` 与本轮触及的 ARCH024 精确 hash/import 检查
  通过。完整 architecture `phase=m03` 仍报告 `origin/main` 已记录的 ARCH025/026/027/040/042
  AI Drawer、主导航、Software Home 与主题快照漂移；本轮没有修改或批量批准这些文件。
- 规定的 `:app:assembleDebug --no-daemon --console=plain` 为 `BUILD SUCCESSFUL in 2m 47s`，
  `235` 个任务中 `26 executed / 209 up-to-date`；唯一 launcher、脚本代理 runtime 与播放器
  runtime packaging 门禁通过。
- 最终 APK 为 `app/build/outputs/apk/debug/app-debug.apk`，写入时间
  `2026-09-01 05:55:04 +08:00`，`503695441` bytes，SHA-256
  `57602FA925B1C5E37E3EFB3C36DDEB8A103A2CB188DF7261E8AB81A11F070C5C`；包/版本/SDK 为
  `com.kiyori / 45 / 0.1.0 / min 26 / target 34 / compile 37`，唯一 launcher 为
  `com.ai.assistance.operit.ui.main.MainActivity`，仅 `arm64-v8a`，Android Debug V2 单 signer 与
  `zipalign -c -P 16 4` 通过。
- 未安装或操作设备，因此环境/设置页连续纵向拖动、右滑返回软件首页、首页再次左右切换、三层
  系统 Back、IME、首次 rootfs 探针耗时和 Python/venv/pip/uv 安装后勾选仍为
  `verification_pending`。

### 环境探针脚本与安装后环境变量修复（2026-09-01）

状态：根因修复、环境路径修复、Terminal/App 全量 JVM 测试、formal readiness 与 Debug APK 审计
已完成；Git 交付状态以本轮最终 ref 核对为准，真机验收仍为 `verification_pending`。

#### 现场事实与根因

- 环境配置页的唯一批量探针由 `SetupScreen.packageProbeCommand()` 生成，再由
  `LocalTerminalProvider` 原样写入临时 Bash 脚本。生成器用 `"\\n"` 拼接脚本语句，产物包含的
  是反斜杠和字母 `n`，不是物理换行。Bash 因而无法把 begin marker、各包 `if` 和 end marker
  解析为独立语句，整段脚本以非零状态结束。
- `packageProbeStatuses()` 对 hidden command 非零、缺少完整 begin/end frame 的结果统一返回
  `UNKNOWN`，这是正确的失败语义，也与“所有选项始终显示无法识别”的现场表现完全一致；问题位于
  上游脚本生成，不应通过把 `UNKNOWN` 改成“未安装”掩盖。
- 现有 `SetupEnvironmentProbeTest` 只解析手写 marker，并明确断言生成命令以字面量 `"\\n"`
  结尾，因此 `8/8` 测试通过仍没有覆盖实际 Bash 的物理行合同。
- hidden shell 使用 `/bin/bash --noprofile --norc -s`，Ubuntu 内 `HOME=/root` 且基础 `PATH` 不读取
  profile。uv 探针已经显式加入 `$HOME/.local/bin`，但 rust 探针仍只依赖继承的 `PATH`；rustup
  安装成功后，当前 hidden shell 仍可能找不到 `$HOME/.cargo/bin/rustc`。
- 可见安装命令通过 `eval` 在同一个交互 shell 中逐步执行，环境变量修改能够保留。uv 当前以
  `source ~/.profile` 激活 PATH，会执行与本次安装无关的 profile 内容；rust 安装后没有显式激活
  `$HOME/.cargo/env`。

#### 冻结方案

1. 保留唯一的 `executeHiddenCommand(executorKey = "environment-setup-check")` 和一次批量探针，不新增
   逐包命令、第二状态源或失败回退路径。
2. 生成的 Bash 脚本使用真实 `LF` 分隔 begin、每个包检查和 end；每条结果采用显式
   `__KIYORI_ENV_PROBE__:<package-id>:<0|1>` 字段，避免把状态位黏在包 ID 末尾。
3. 解析器只接受完整 frame 内、已知包 ID、字段数和值均正确的记录。hidden command 失败或 frame
   不完整时全部保持 `UNKNOWN`；单个记录缺失、重复或损坏时只把对应包保持 `UNKNOWN`，不得误报
   `INSTALLED` 或 `NOT_INSTALLED`。
4. Python 继续按能力识别：`python`/`python3` 的最终链接一致、`python3 -m venv` 可用、
   `python3 -m pip` 可用；Node 继续解析 `npm prefix -g` 的唯一全局 bin。uv 与 rust 分别在
   `$HOME/.local/bin`、`$HOME/.cargo/bin` 的规范路径上执行真实版本命令，识别不依赖 profile 是否
   被 non-interactive shell 加载。
5. 自动安装保持逐步、非交互和遇错停止。uv 用 `pipx ensurepath` 持久化后，直接向当前 shell
   `export PATH="$HOME/.local/bin:$PATH"`；rustup 成功后显式 `source "$HOME/.cargo/env"`，使当前
   终端和后续探针使用相同安装位置，不执行整份 profile 作为安装步骤。
6. 不改变包 ID、AIDL、namespace、rootfs 路径、安装源持久化或 UI 路由。该增量没有数据迁移；
   回滚点是 terminal 子模块修复提交及父仓库对应 gitlink 提交。

#### 实施与验证计划

1. [DONE] 核对父/子仓库、正式准备文档、hidden executor、安装队列、环境注入和现有测试，确认统一
   根因与测试缺口。
2. [DONE] 冻结真实换行、显式字段、严格失败语义和规范安装路径方案。
3. [DONE] 修改探针生成/解析、rust/uv 环境激活并补齐源码意图注释；同步 terminal `CONTEXT.md`
   和本 TODO 的实际状态。
4. [DONE] 增加生成脚本物理行、完整/缺失/重复/损坏 frame、hidden result 失败、
   Python/uv/rust 路径及安装命令顺序测试；定向 `19/19`、Terminal 全量 `9 suites / 42 tests` 和
   App 全量 `321 suites / 1915 tests` 均为零失败、零错误、零跳过。
5. [DONE] formal readiness、父/子 `git diff --check`、候选树 Markdown 链接检查和规定的串行
   `:app:assembleDebug --no-daemon --console=plain` 已通过；APK 身份、哈希、签名与 16 KiB 对齐已核验。
6. [DONE] 审计并依次提交推送 Terminal 子模块和父仓库 `main`：Terminal 实现提交为
   `ea3d4f01a15f2babf0fad00962871525bdbce606`，父仓库实现/gitlink 提交为
   `ec39598a4a7b4f757a56b9f3b6c22d47933e2b8f`；最终父仓库文档 ref 由本轮交付核对记录。真机首次
   rootfs、已安装/未安装混合状态、安装后立即重进页面和实际命令可用性保持
   `verification_pending`。

#### 本地验证证据

- 定向环境探针与共享环境合同为 `19/19`；Terminal 全量为 `9 suites / 42 tests`，App 全量为
  `321 suites / 1915 tests`，均为零 failure、error 和 skip。
- `check_formal_readiness.py --repository . --require-main`、父/子 `git diff --check` 与候选树
  `check_markdown_links.py` 通过。Markdown 检查首次未传必需的 `--base/--candidate` 而退出；按脚本
  合同重跑后的实际结果为 `0 errors / 0 warnings`。
- 规定的 `:app:assembleDebug --no-daemon --console=plain` 为 `BUILD SUCCESSFUL in 35s`，`235` 个
  tasks 中 `23 executed / 212 up-to-date`；唯一 Launcher、脚本代理 runtime 与播放器 runtime
  packaging 门禁通过。
- Debug APK 为 `app/build/outputs/apk/debug/app-debug.apk`，写入时间
  `2026-09-01 13:04:54 +08:00`，`503695441` bytes，SHA-256
  `FE483805D93431A4D9D230612C14516FFA02BE346E0734394961380657FDA900`；包/版本/SDK 为
  `com.kiyori / 45 / 0.1.0 / min 26 / target 34 / compile 37`，唯一 Launcher 是
  `com.ai.assistance.operit.ui.main.MainActivity`，仅 `arm64-v8a`，包含 `53` 个无重复 basename 的
  `.so`，Android Debug V2 单 signer 与 `zipalign -c -P 16 4` 通过。
- 未安装或操作设备，因此首次 rootfs 初始化耗时、已安装/未安装混合结果、Python 链接/venv/pip/uv
  勾选、rust/uv 当前终端 PATH、安装完成后立即重进页面和系统/手势回归仍为
  `verification_pending`。

## 非目标

- 不启用 Android VPN、第二代理核心、自动 provider 切换或无声直连旁路。
- 不把 Android 全局 Python、Ubuntu 系统 Python 与 code_runner venv 合并。
- 不修改产品 application ID、兼容 namespace、ToolPkg ID、终端 AIDL 或 Ubuntu 发行版身份。
- 不启用 GitHub Actions 总开关，不安装 APK，不代替真机验收。

### 2026-09-01 code_runner / super_admin 现场测试复核

状态：本地实现与自动化验证已完成；真机验收仍保持 `verification_pending`。

#### 复核结论

- code_runner 的 Python venv、pip 和解释器本身没有损坏。Go/Rust 临时工程使用 `cd <tmp>`
  修改了持久 `code_runner_session` 的 cwd，随后清理该目录，导致下一次 Python/pip 启动在已删除
  cwd 中调用 `os.getcwd()` 失败；旧检查把这个执行上下文错误误判为“持久 venv 不完整”。
- Rust 首次文件测试的 `unclosed delimiter` 来自测试文件同步到 Android 存储时的内容损坏，重写同一
  文件后工具链通过；代码仍需保证自身所有临时构建目录不会留下失效 cwd，避免再次制造同类假象。
- super_admin 的 Ubuntu PTY、后台等待、屏幕读取和交互输入在报告中均通过。复核发现参数 metadata
  仍把 `background`/`timeoutMs` 声明为字符串，超时使用 `parseInt` 会接受尾随垃圾字符，且后台会话
  名仅使用毫秒时间戳，存在并发碰撞窗口；这些属于可确定修复的输入与会话隔离问题。
- Android Shell 的普通应用权限限制是设备授权边界，不把 `settings`/`su` 失败改写为工具故障，
  也不添加 Root、Shizuku 或静默直连兜底。

#### 本轮修复合同

1. 所有 code_runner 临时 Go/Rust 构建命令使用带明确工作目录的子 shell；父 PTY 的 cwd 和环境保持
   不变，清理目录后下一条命令仍可启动 Python/pip。
2. Python venv 的存在性、创建后校验、包安装、环境信息探针和内置 Python 自检在 `$HOME` 稳定工作
   目录中执行；用户提供的文件路径命令继续由调用方当前 cwd 解析，不改变公开文件运行语义。
3. super_admin metadata 与 TypeScript 参数改用 `background: boolean`、`timeoutMs: number`；只接受
   有限整数且不低于 3000ms，输入至少包含非空文本或控制键；后台 session 名加入进程内单调序列。
   后台命令不设置工具截止时间；显式传入的 `timeoutMs` 只按同一严格规则校验后忽略，并在启动结果
   中以 `timeoutPolicy="ignored"`/`timeoutMsIgnored` 明确报告，不能静默杀掉已脱离调用方等待的任务。
4. 保持唯一 code_runner 可见 PTY、唯一 super_admin 默认/后台会话 owner、既有 ToolPkg ID、AIDL、
   Ubuntu/Android Shell 边界和 `UNKNOWN` 环境探针语义，不新增并行执行器或状态源。

#### 验收补充

- TypeScript 编译必须通过，源码与生产 `app/src/main/assets/packages` 字节一致。
- 增加静态合同测试，覆盖临时构建子 shell、稳定 Python 工作目录、严格 super_admin 参数和后台
  会话唯一性；随后执行 Terminal/App 相关测试、formal readiness、`git diff --check` 与串行
  `:app:assembleDebug --no-daemon --console=plain`。

### 2026-09-02 字符串模式 heredoc 回归复核

最新安装包复测发现，venv 完整性和 pip 安装已经恢复，但所有需要先写临时文件的字符串模式
（Python、Node、Go、Rust、C、C++、Ruby）统一返回 Bash 的 `here-document ... delimited by
end-of-file`。根因不是脚本中的单引号，而是 `buildWriteFileCommand()` 生成的 delimiter 行没有
结尾 LF；`executeFromHome()` 随后把命令包进 `(cd "$HOME" && ... )`，使实际文本成为
`__CODE_RUNNER_FILE_xxx__)`，delimiter 不再是独立行。文件模式和直接求值的 ES5 模式不经过该
写入路径，因此没有受到影响。

修复合同：正文统一为 LF 并至少保留一个正文换行，delimiter 必须单独占一行且后跟 LF，之后才
允许 subshell 的闭合括号；delimiter 仍由进程内序列和内容碰撞检查生成，脚本内容保持 quoted
heredoc 原样写入。静态合同测试锁定该换行边界；生产资产必须重新由 TypeScript 编译同步，不能
只改 APK 内生成文件。该修复不改变用户文件模式的 cwd 语义，也不增加第二写入器或 shell 兜底。

### 2026-09-02 Rust 文件模式长输出截断复核

状态：根因修复与本地自动化验证完成；目标设备复测仍为 `verification_pending`。

#### 现场事实与根因

- 最新安装包中 `run_rust_file` 对约 255 字节以上的文件报告 Rust `unclosed delimiter`，而同一文件
  直接由 `rustc` 编译成功；短 Rust 文件以及 Python/Go/C/C++/Ruby 大文件均正常，排除了 Rust
  编译器、FUSE 挂载和统一的文件大小限制。
- `run_rust_file` 通过 `executeTerminalCommand("cat <path>")` 取得源文件，再写入临时 Cargo 工程。
  `OutputProcessor` 按行累积命令输出并在完成时生成完整历史；每个输出事件却由
  `TerminalManager` 的 callback 单独 `launch` 后再 emit 到共享流。完成事件可能先于仍在调度的正文
  事件到达 `Terminal.executeCommandFlow`，收集器看到完成后立即停止，于是只拿到前约 10 行（约 255B）
  的内容，最终把截断源交给 `cargo`。
- 终端历史分页上限为每命令 100 页、每页 10 行，原始缓冲上限为 256KiB；这些上限不会在本现场
  规模下截断 800B Rust 文件，问题属于事件发布顺序而非存储容量。

#### 冻结方案

1. 在 `TerminalManager` 内建立单一有序事件通道；开始、正文和完成事件全部按 callback 入队顺序由
   一个 dispatcher 发布到现有 `commandExecutionEvents`，移除逐事件独立协程造成的竞态。
2. 保持 `CommandExecutionEvent` 字段、OSC 退出标记、唯一可见 PTY、会话队列和输出分页协议不变；
   不引入第二结果源、额外执行器或改变用户可见终端历史。
3. 增加终端回归测试：构造超过 10 行的连续正文事件，断言完成事件最后到达且收集到的输出字节完整；
   增加 code_runner 静态合同，确保 `run_rust_file` 仍使用完整 `cat` 结果写入临时工程。
4. 复核环境探针、Python venv/pip、Node workspace、uv/rust PATH 和安装后勾选的既有合同；只有
   发现当前源码可重现的缺陷才在本轮一并修复，设备权限失败继续按真实权限展示。

#### 实施与验证计划

1. [DONE] 核对现场复测矩阵、终端输出模型、历史上限和当前 Git 基线，确认事件乱序根因。
2. [DONE] 冻结有序事件分发和测试边界，更新本专项 TODO 与任务日记。
3. [DONE] 修改 `TerminalManager` 事件发布并补齐终端顺序回归测试；terminal `45/45`、ToolPkg `12/12` 通过。
4. [DONE] 运行 TypeScript 编译、终端与父仓库相关测试、正式开发准备检查和 `git diff --check`。
5. [DONE] 串行构建 `:app:assembleDebug --no-daemon --console=plain`，核验 APK 内容、签名和 16 KiB 对齐。
6. [DONE] 审计精确差异、敏感内容、子模块和远端状态，提交并推送 `main`；真机长 Rust 文件、七类
   字符串模式及环境安装/识别仍单独保持 `verification_pending`。

#### 本地实现证据

- 新增 `OrderedCommandExecutionEventDispatcher`，以单一 FIFO Channel 串行发布终端命令开始、正文和
  完成事件；`TerminalManager` 清理时关闭队列，未改变 `CommandExecutionEvent`、OSC 退出标记、AIDL
  或可见 PTY 协议。
- 新增 `OrderedCommandExecutionEventDispatcherTest`，发送开始事件、25 行正文和完成事件，确认完成
  事件最后到达且退出码保留为 `0`；terminal 定向构建与测试共 `45/45` 通过。
- code_runner 源码与生产资产仍保持字节同步，heredoc、稳定 venv/cwd、Rust PATH、环境探针和
  super_admin 参数合同测试 `12/12` 通过；CI 全量为 `239/239`，父仓库 `:app:testDebugUnitTest`
  与 TypeScript 编译均通过。
- 本轮串行 `:app:assembleDebug --no-daemon --console=plain` 为 `BUILD SUCCESSFUL`（235 tasks，
  26 executed / 209 up-to-date）。Debug APK 为 `app/build/outputs/apk/debug/app-debug.apk`，
  503695485 bytes，SHA-256 `3A48EA39FBFC13A8A05D416381D3C965A3C2FE2A5FEFF88486031FA728807C8F`；
  `aapt` 确认 `com.kiyori`、`45/0.1.0`、compile/target SDK `37/34`、唯一 launcher
  `com.ai.assistance.operit.ui.main.MainActivity`、ABI `arm64-v8a`，`apksigner` V2 单 signer
  与 `zipalign -P 16` 均通过。APK 内 `code_runner.js`/`super_admin.js` 与源码 SHA-256 分别为
  `5127A7C4A97E42CCE68FBAB9C95E5D60530B1E889CC4A202DA02FBD7F8992A14` 和
  `DB0452BCE24C5F75F4BB8182903FEC60674EE1B3CB90397DA2813861C1047F2F`，逐字节一致。
- 真实 Android 设备上的长 Rust 文件、七类字符串模式、环境安装后立即重进页面和工具可用性仍需
  现场复测，状态保持 `verification_pending`。

### 2026-09-04 现场工具报告复核与 Ruby/时区/JS 语义收口

状态：已完成源码根因修复与本地自动化验证计划；目标设备上的环境安装、时区显示、ES5 completion
value 和 Ubuntu 文件路径仍需现场复测，保持 `verification_pending`。

#### 已确认问题

- `run_javascript_file` 唯一使用 Android `Tools.Files.read`，而 Node/Python/Ruby/Go/Rust/C/C++
  文件运行器都在同一可见 Ubuntu/proot PTY 中检查和读取路径，导致同一个 `/root` 文件对 JS 不可见、
  `/sdcard` 文件却只有 JS 可见。
- ES5 执行器把脚本放进 IIFE，只能接收显式 `return`；没有显式 `return` 的最后表达式 completion
  value 在 IIFE 中被丢弃，和工具元数据的“最终返回值”描述不一致。
- 环境配置页的唯一包清单没有 Ruby，`run_ruby`/`run_ruby_file` 却直接调用 `ruby`；设备上临时
  `apt install ruby` 不能成为重装或换机后的可复现首装合同。
- 所有 Ubuntu/chroot/proot 的 `env -i` 入口都未传递宿主时区；Resolute rootfs 已包含 `tzdata`，
  因而 `date` 显示 UTC 是可修复的环境注入缺口。fake `/proc` 的静态 `btime` 是为受限 Android
  接口准备的历史测试数据，不在本轮伪装成实时值。
- `super_admin:terminal` 的后台任务使用独立 session 是并发隔离设计；返回不同 `sessionId` 是可追踪
  的预期行为，文档继续明确后台任务不由默认前台 shell 或 `terminal_wait` 追踪。

#### 修复合同

1. Ruby 通过现有 `TerminalEnvironmentContract` 和 `SetupScreen` 唯一安装/探针链加入 `ruby` apt
   包，探针要求 `command -v ruby` 与 `ruby --version` 成功，双语 UI 与 README/CONTEXT 同步。
2. `run_javascript_file` 复用 Ubuntu PTY 的 `test -f`/`cat`，保持用户当前 Ubuntu cwd 和完整文件
   内容语义；不再调用 Android 文件 API，也不新增环境参数或第二读取 owner。
3. ES5 先以标准直接 `eval` 返回脚本 completion value；仅在解析到顶层 `return` 时使用隔离的
   `Function` 体执行显式返回。日志、错误文本、对象格式化和工具名称保持不变。
4. `TerminalManager` 为 chroot、proot 探针和实际 shell 的每个 `env -i` 传递宿主
   `TimeZone.getDefault().id`，让同一 Ubuntu 会话的 `date`/Ruby/Python 时间 API 使用设备时区；不
   修改 rootfs 身份、fake `/proc` 数据或 Android Shell 边界。

#### 本轮验证计划

1. [DONE] 增加 Ruby 探针/安装和时区/ES5/JS 文件路径合同测试，刷新 examples 与生产 asset 字节同步。
2. [DONE] 运行 TypeScript、Terminal 定向 JVM、ToolPkg/正式开发准备和 `git diff --check`。
3. [DONE] 串行运行 `./gradlew :app:assembleDebug --no-daemon --console=plain` 并核验 Debug APK。
4. [DONE] 审计 staged 内容、子模块 gitlink、敏感文件和远端 refs；terminal 子模块以
   `7ec4cfb10c94992adb79e6e281ba61878d7bcd8c` 提交并推送；父仓库使用本次提交完成 `main`
   交付，最终 refs 在收尾核对中保持一致。
5. [PENDING] 在 Android 设备复测 Ruby 首装/重进识别、Ubuntu `/root` JS 文件、ES5 最后表达式、`date`
   时区和后台 session 追踪；本地构建不能替代这些验收。

#### 本轮本地验证证据

- `npm.cmd exec -- tsc -p examples/tsconfig.json --pretty false`、两份 `code_runner.js` 的
  `node --check` 和 ES5 wrapper smoke test 均通过；`1 + 41` 与显式 `return 42` 均返回
  `Return value: 42`。
- `ci.test.test_toolpkg_sync` 为 `15/15`；`Terminal` 的 `:terminal:testDebugUnitTest` 与 App 的
  `:app:compileDebugKotlin` 均为 `BUILD SUCCESSFUL`；正式开发准备检查为 `PASS`；父/子仓库
  `git diff --check` 通过。
- `./gradlew :app:assembleDebug --no-daemon --console=plain` 为 `BUILD SUCCESSFUL`，`235`
  个 actionable tasks 中 `21` 个执行、`214` 个 up-to-date；唯一 launcher、脚本代理和播放器
  runtime packaging 门禁通过。
- Debug APK 为 `app/build/outputs/apk/debug/app-debug.apk`，大小 `483701469` bytes，SHA-256
  `C0B701D98F306E707FD142580C5F06005F823905A07DE09D5D9634517E34DCD5`；包/版本为
  `com.kiyori / 45 / 0.1.0`，仅包含 `arm64-v8a` 的 `53` 个 `.so`；Android Debug V2 单 signer
  与 `zipalign -c -P 16 4` 通过。APK 内 `assets/packages/code_runner.js` 与源码哈希均为
  `03C931F4A1030E4F6A5B1A5962C209BA749EE7767EEDEA74344BC74DA7460F74`。

### 2026-09-03 super_admin 现场反馈收口（本轮）

现场复测确认前台大输出、前台超时取消、连续超时恢复、`exit`/`kill` 后同一 `sessionId` 重建均
正常；新增问题集中在后台工具语义和文档表达：

- `background=true` 已改为使用同一终端执行器的显式 `timeout_policy="none"`，因此真正不设置工具
  截止时间；普通前台调用仍保留执行器缺省的 30 分钟截止时间。显式 `timeoutMs` 仍严格校验
  （保持输入合同），但启动结果现在返回 `timeoutPolicy="ignored"` 与 `timeoutMsIgnored`；未传值
  返回 `timeoutPolicy="none"`。这样后台 `sleep`、批处理和常驻服务不会在调用方已经收到
  `started=true` 后被无声取消。
- `terminal_wait` 的唯一状态源仍是同一会话的 shell 队列 marker。结果增加 `waitScope="shell_idle"`，
  文案明确它只确认 shell 已回到可接收命令的边界，不跟踪 detached/background 进程；后台任务必须
  使用自身副作用或完成标记验证。
- 完成事件正文仍固定为空，正文唯一来自增量事件。OSC 标记现在在原始缓冲中被精确消费，保留与
  标记同一物理行上的无换行正文，避免边界字符被状态解析吞掉；不对真实命令输出做引号过滤。
- `OutputProcessor` 对单次大块 PTY 读取先消费完整行和 OSC，再施加 256 KiB 未终止缓冲上限，避免
  许多完整行在进入工具捕获器前被提前丢弃；工作区增量消费者只去掉分隔符产生的末尾空项，真实
  空行仍保留。
- clean-on-exit 大输出阈值固定为 `12,000` 个 JavaScript 字符（UTF-16 code units）。落盘结果同时
  返回 `output_saved_to`、`output_chars`、`output_bytes`、`output_lines` 和 `output_is_preview`；
  底层 4 MiB 捕获上限触发时，文件明确是首尾预览而非完整输出。文件本身仍随 clean-on-exit 生命周期
  清理，完整结果应由命令自行重定向后分段读取。

本轮新增/修正的自动化合同覆盖后台超时忽略、shell-idle wait 语义、UTF-8 字节/换行统计，以及
OSC 标记与无换行正文相邻的消费边界。源码与生产 asset 必须继续通过字节同步检查；真实 Android/
proot 设备仍保持 `verification_pending`。

### 2026-09-02 super_admin 终端输出与会话恢复

状态：本地实现、相关自动化、formal readiness 与 Debug APK 审计已完成；Android/proot 现场验收保持
`verification_pending`。

#### 已确认根因

- `OutputProcessor` 每页保留 10 行、每条命令最多保留 100 页；完成事件因此只携带尾部
  约 1000 行。`StandardTerminalCommandExecutor` 又优先使用这个完成快照，覆盖了订阅期间
  已收到的完整增量行，形成无提示的头部丢失。
- 正文事件的 `outputChunk` 不包含行分隔符，超时分支以空字符串拼接，因此已捕获的多行
  输出会粘连。超时还会先取消 Flow 订阅，再发送 Ctrl+C，使取消过程中的尾部事件无法
  进入结果。
- Ctrl+C 可中断整条交互式 Bash 输入，因而 `eval` 后的 OSC 退出标记可能不再执行。
  现行提示符处理依然要求先看到标记，会把真实的 shell 提示符当作中间输出忽略，让
  `currentExecutingCommand` 或交互态永久残留。
- PTY EOF/进程退出只结束当前命令状态，既不重建底层 shell，也不移除逻辑会话。同名
  `terminal.create` 因此会继续返回已失效的 sessionId。

#### 冻结方案

1. 保留唯一 `TerminalManager` / PTY / OSC / sessionId owner。超时取消必须绑定精确 commandId；
   发送 Ctrl+C 前把该命令标记为取消，使随后的真实 shell 提示符能够以 `exitCode=-1`
   收敛同一命令；不将后续新命令当作裸输入。
2. 超时期间保持事件收集直到取消完成事件，返回已捕获的真实输出并按事件边界恢复
   换行。工具输出使用明确容量上限；超限时保留首尾、返回 `outputTruncated=true`，并在结果中
   说明重定向至文件后分段读取。
3. 命令取消在规定时间内不能收敛时，终止该 PTY 并在同一逻辑 sessionId 内重建 shell；
   该路径是会话生命周期的显式失败恢复，不创建第二执行器或伪造命令成功。重建会保留逻辑
   标题/sessionId，但应明确报告 shell cwd/导出变量等上下文已重置。
4. shell 自然 `exit`/崩溃时也执行同 sessionId 的生命周期重建；新命令在会话重新
   `READY` 前不得写入旧 writer。正常命令、超时取消与自然退出都使用同一状态机。
5. 同步 `TerminalCommandResultData`、TypeScript 类型与 `super_admin` 结果投影；保留现有工具名、
   AIDL、namespace、Ubuntu/rootfs 路径和 chatId 会话命名协议。

#### 实施与验证计划

1. [DONE] 核对父/子仓库基线、正式开发门禁、ToolPkg 调用链、事件顺序、输出分页、取消与
   PTY EOF 生命周期，确认上述共享根因。
2. [DONE] 冻结唯一会话 owner、命令级取消、同 sessionId 重建、有界完整输出与显式截断合同。
3. [DONE] 实施 Terminal 子模块状态机、App 工具收集器、类型与 `super_admin` 投影，
   补齐大输出、换行、超时取消、同 commandId 收敛和死会话重建回归。
4. [DONE] 运行 Terminal/App/ToolPkg 定向测试、TypeScript 编译与源码/生产资产同步检查。
5. [DONE] 运行父/子 `git diff --check`、formal readiness 与规定的串行 `:app:assembleDebug`，
   核验 APK 身份、签名、16 KiB 对齐及内置 `super_admin.js`。
6. [DONE] 反向审查最终差异、用户现有改动、敏感内容和 Git 状态；不提交、不推送。
7. [PENDING] 在 Android 40×60 Ubuntu/proot 现场执行简单命令、3000/5000 行、超时长命令、
   超时后紧接简单命令和 shell exit 后自动重建验收。

#### 本地实现与验证证据（2026-09-03）

- 完成事件现在只携带命令边界和真实 `exitCode`，`outputChunk` 固定为空；工具、工作区、
  MCP 和环境等待入口均从非完成增量事件组装正文，不再用最多约 1000 行的 UI 历史尾部
  快照覆盖已收到的完整输出。MCP 部署命令同时以完成事件的 `exitCode` 判定成败。
- 前台与流式工具共用一个 commandId 捕获器；超时后 collector 保持到精确取消收敛和事件排空，
  截止边界已观察到完成事件时不误报超时。输出默认上限为 4 MiB，超限保留 head/tail 并返回
  `outputTruncated/originalOutputChars`；`super_admin` 写文件时会明确说明底层已截断的内容仅是预览。
- Ctrl+C 先标记精确命令，允许真实提示符在 OSC 被中断时收敛；无法收敛、writer 写失败或
  PTY EOF 均通过同一 `TerminalManager` 在同一逻辑 `sessionId` 下重建 shell。旧 reader 按 PTY 实例校验，
  并发恢复按 shell generation 去重；队列只在新 shell `READY` 后按 FIFO 继续，重建时显式报告
  `sessionRecovered=true/contextPreserved=false`。
- `:terminal:testDebugUnitTest`（52 项）通过；App `TerminalOutputCaptureTest`（8 项）与工作区
  增量换行回归（2 项）定向通过；ToolPkg `12/12`、TypeScript、两份 JS `node --check`、资产
  SHA-256 一致、formal readiness 通过；fresh-clone 仅验证当前 `HEAD` 的可克隆性，不包含本地
  未提交修改。App 全量运行 1936 项，1935 通过；唯一失败是 `KiyoriBottomDrawerMigrationContractTest` 要求
  `FileContextMenu.kt` 使用共享抽屉，而两个文件的工作树 blob 都与当前 `HEAD` 完全一致；该已存在
  UI 基线不一致与本专项无关，本轮未越界修改。
- 串行 `:app:assembleDebug --no-daemon --console=plain` 为 `BUILD SUCCESSFUL in 44s`，235 个任务中
  `28 executed / 207 up-to-date`。APK 为 `493547606` bytes，SHA-256
  `CB435BF6752C608E1F4C737A32D94A663809E1B4233C5A917763B2784696AFC9`；包/版本/SDK 为
  `com.kiyori / 45 / 0.1.0 / min 26 / target 34 / compile 37`，唯一 launcher 为
  `com.ai.assistance.operit.ui.main.MainActivity`，仅 `arm64-v8a` 的 53 个 `.so`（basename 无重复），
  加 shell launcher 共 54 个 ELF64/AArch64，161 个 `PT_LOAD` 为 `0x4000 × 159 + 0x10000 × 2`；
  Android Debug V2 单签名与 `zipalign -c -P 16 4` 通过。APK 内 `super_admin.js` 与源码 SHA-256
  均为 `C6AE12CD3EF331E37B801B024AED03701446B17F77FF7C557D958593F65474F5`，并确认新
  `timeoutPolicy`/`waitScope` 字段已进入 asset。
- 未安装 APK、未操作 Android 设备。简单命令、3000/5000 行、超时取消后立即执行、shell `exit`
  后同 sessionId 重建、Ubuntu/proot 实际输出与状态展示仍为 `verification_pending`。
