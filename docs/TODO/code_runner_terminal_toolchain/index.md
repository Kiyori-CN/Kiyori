# code_runner 与终端工具链收口

状态：2026-08-31 现场回归修复的本地实现、自动化验证与 Debug APK 静态审计已完成；真机验收仍为 `verification_pending`。
目标是让 Agent 能准确区分 code_runner、super_admin、可见终端、Ubuntu/proot、
Android Shell、Python venv 和 Node 工作区，并消除隐藏执行器超时后遗留进程、输出失控和
`params must be a valid JSON object` 这组三类现场问题。

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
CRLF 结束，避免 marker 与下一条提示符粘连而丢失真实退出码。首次 rootfs 解压和会话 READY 等待
上限统一为 180 秒，防止慢速 Android 存储在 30 秒时被永久关闭。

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

## 非目标

- 不启用 Android VPN、第二代理核心、自动 provider 切换或无声直连旁路。
- 不把 Android 全局 Python、Ubuntu 系统 Python 与 code_runner venv 合并。
- 不修改产品 application ID、兼容 namespace、ToolPkg ID、终端 AIDL 或 Ubuntu 发行版身份。
- 不启用 GitHub Actions 总开关，不安装 APK，不代替真机验收。
