# code_runner 与终端工具链收口

状态：2026-09-01 现场回归增量的本地实现、自动化验证与 Debug APK 已完成；真机验收仍为 `verification_pending`。
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
6. [PENDING] 审计并依次提交推送 Terminal 子模块和父仓库 `main`，独立核对本地、tracking 与 GitHub
   远端 ref；真机首次 rootfs、已安装/未安装混合状态、安装后立即重进页面和实际命令可用性保持
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
