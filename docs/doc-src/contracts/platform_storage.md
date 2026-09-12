# 平台、存储与服务生命周期契约

本页定义跨产品基础能力。依赖构建见 [构建指南](../dev-core/BUILDING.md)，设备状态以各专项实际验收为准。

## 路径与备份

启动后的 `cleanOnExit` 清理由应用的 IO scope 发起，`TemporaryDirectoryCleaner` 不跟随
目录内符号链接，拒绝作为根的符号链接，保留根目录及按需保留根 `.nomedia`。枚举、删除
失败向日志调用方传播；不能把部分清理写为全部成功。它不提供与外部并发写入共同的文件系统事务。

文件管理器由既有 `FileManagerViewModel` 持有双位置会话，Shell 保留其设置往返和应用内最小化生命周期；文件执行仍经 `AIToolHandler`。
ApiPreferences 持有书签、目录工作区、网络分组及加密网络配置；网络文件工具只支持原生目录浏览，其他操作明确拒绝。
文件管理器删除默认同卷移至回收站，恢复不覆盖并复核确认指纹。共享卷使用 `.kiyori-recycle-bin/<applicationId>`，
避开共享目录到 `Android/data` 的跨挂载移动；内部与旧外部专属回收记录继续读取。共享回收内容需手动清理，
内部与旧专属回收内容受应用数据清理影响。未加密 ZIP 可经暂存校验后解压发布，详细限制见文件管理器设计。
文件工具每次执行解析当前权限，Shizuku 授权后不继续使用注册时的普通身份；普通目录枚举失败保持错误，
不能显示为空目录。特权目录读取使用真实 Shizuku/Root Shell，严格写入仍限应用身份可访问路径。
固定左右双栏消费同一窗格状态，非活动栏由淡灰内阴影衬托；存储位置、路径和统计位于顶栏下方的独立行。目录内筛选只投影已加载条目；选择随成功快照核对，
历史导航清除旧目录选择，已隐藏或被筛掉的项目不得继续参与批量操作。根边界不提供无效上级动作。
新建通过 `create_file` / `make_directory` 的显式 `create_mode=no_replace` 原子创建空项目；仅支持
应用可访问的 Android 普通路径，同名返回冲突，既有未传模式的工具行为保持兼容。不支持的环境
明确拒绝，不能忽略模式退回旧创建链；未知结果阻止原弹窗再次提交。
创建捕获弹窗打开时的位置，结果只刷新仍显示该位置的窗格；搜索独立持有取消和结果代际，
搜索结果跳转保留原环境和窗格。名称只允许单个目录项。产品与后续数据安全目标见
[内置文件管理器设计](../architecture/kiyori_file_manager.md)，阶段证据见对应专项。

顶栏统一搜索消费同一 ViewModel 表单，Android `find_files search_mode=manager` 支持当前目录/递归、
名称/大小/时间/UTF-8内容/大小写/正则/隐藏项，元数据随结果一次返回。扫描、结果、读取和匹配预算必须可观察，
取消与代际阻止晚到发布；条件错误不能执行。Linux/SAF 名称搜索保持原工具路径，未实现的高级条件显式拒绝。
文件结果定位筛选与书签共用现有窗格投影；删除 A/B 第三行，筛选调整/清除进入顶栏搜索。

文本编辑复用现有 `CodeEditor`，草稿、读取代际及保存状态属于 `FileManagerViewModel`。
`read_file_full` 的显式 `read_mode=bounded_utf8` 限制为应用身份可读的 Android 普通路径，
最多读取 1 MiB 加一字节判断超限，严格 UTF-8 解码，拒绝直接链接、二进制和观察到的读取中变化；
Linux/SAF 明确拒绝此模式。未传模式的旧工具调用保持兼容。
文本另存使用 `create_file` 的 `create_mode=no_replace_text`，只接受同一大小上限的 UTF-8 内容，
写入目标父目录的 `.kiyori-text-*` 暂存并刷盘，再复用原子不覆盖提交；原文件不写入。
同名、清理失败与未知结果必须可见，不自动重试。该增量不承诺断电事务、进程恢复或原地编辑保存。

Android 标准文件工具的递归复制统一调用 `copyLocalDirectory`：枚举失败、子目录失败和类型冲突
向上传播，拒绝源等于目标、目标位于源树内以及直接遇到的符号链接。既有同名普通文件替换语义
暂时保留；这不构成跨进程原子不覆盖保证，也不覆盖 Linux/SAF/特权 Shell 的其他实现。
文件管理器复制显式使用 `copy_mode=no_replace`，Android 应用可访问路径通过同卷暂存、内容校验和
`renameat2(RENAME_NOREPLACE)` 提交；其余后端拒绝不支持模式，不进入旧覆盖分支。传输会话捕获
确认时的批次与位置，提供跳过/改名保留冲突决策、完成当前项后停止及逐项结果；未知结果不自动重试。
同目录重命名通过 `move_mode=rename_no_replace` 复用原子提交，失败不会执行复制后删源；名称与位置
固定、未知结果禁止原弹窗重复提交。跨目录移动使用 `move_mode=move_no_replace`，仅同文件系统内原子不覆盖移动，
`EXDEV` 明确失败，不做复制后删源。双栏传输统一捕获来源、目标和批次，未知移动项不继续保留在待执行剪切列表。
`delete_mode=checked` 按确认的元数据树指纹复核，原子隔离至同目录暂存后再核对并删除；部分失败保留确切隔离位置，
不自动删除或覆盖原路径后来出现的项目。`zip_mode=no_replace` 校验源树、保留根项目和空目录、同卷暂存刷盘后原子提交。
`info_mode=manager/manager_sha256` 返回有界树统计或普通文件 SHA-256；上述新模式仅支持应用可访问的 Android 路径，
不支持的后端明确失败。树检查拒绝链接/特殊文件，最多 100000 项和 128 层；元数据指纹不是内容快照或恶意并发事务保证。
普通书签复用 `ApiPreferences` 的独立 DataStore 键，与 SAF 授权分离；分享复用 FileProvider 只读 URI 与系统选择器。
覆盖替换与任务持久恢复尚未开放，回收站支持恢复及永久删除，完整限制与错误码见
[文件管理器设计](../architecture/kiyori_file_manager.md)。

- `com.kiyori.platform.storage.KiyoriPaths` 持有公开 Downloads/Pictures、内部/cache/files/backup、Browser 下载、ToolPkg 私有 generation、构建事务、内容寻址运行目录和创建/校验逻辑。
- `KiyoriPublicStore` 是 MediaStore 与 Android 8/9 公共文件唯一写入者，`KiyoriStorageService` 暴露该能力及 host-bound ToolPkg 存储。
- ToolPkg 私有/cache 操作不接受任意调用方 package ID，不返回内部绝对路径。显式 SAF 导入经过包专属 migrator 和原子 generation 激活。
- scanner、确定性 builder、content-addressed artifacts、审计和 active record 共同持有制品安装事务；页面不能另建安装状态源。
- `KiyoriBackupPaths` 是无状态投影，`OperitPaths / OperitBackupDirs` 保持 JVM 兼容门面，不重复计算路径。
- 不自动读取、合并或删除 `Download/Operit`；旧公共 ToolPkg 数据只在用户选择 SAF 输入并通过包专属验证后导入。

### 代码搜索的路径空间

- `grep_code` 与 `grep_context` 默认使用 Android 路径。本地 Ubuntu 的 `/root/...`、`/home/...`、`~/...` 必须显式传 `environment=linux`；不根据文件是否存在自动切换环境。
- Linux native 搜索复用 `PRootMountMapping` 的 rootfs 与挂载映射，先在 guest 空间规范化路径，再交给 Android JNI ripgrep。结果文件路径还原为 Linux 路径，`env=linux`，可直接用于同环境的 `read_file` / `read_file_part`；不返回宿主 rootfs 前缀作为文件路径。
- 单文件上下文搜索直接以该文件为搜索根，不把文件名解释成 glob。目录检索继续使用现有 native 正则、过滤和结果限制。
- native 搜索只支持 Android 与本地 Ubuntu。当前文件提供者为 SSH 时明确失败并建议同环境读取或在对应 SSH 终端运行 `grep`；不搜索本机同名文件，不安装远端依赖，不静默替换搜索引擎。
- 未知环境、未安装 Ubuntu、无效路径、路径不存在或不可读均提供原因与下一步。路径失败后 AI 应先用同环境的 `file_exists` / `list_files` 核实；真实错误仍保持失败，不能把不可访问当成零命中。设备上的 PRoot、挂载和符号链接行为需单独验收。

## 生命周期与日志

- `KiyoriActivityLifecycle` 通过一个 facts 实例持有 Activity 弱引用、created/started 计数与前台状态，唯一注册 Android 回调。
- `OperitActivityLifecycleIntegration` 消费这些事实执行保持亮屏、插件回调、前台聊天/麦克风、PlayerCrash 与 VirtualDisplay/Shower 清理；旧 ActivityLifecycleManager 是兼容门面。
- `KiyoriLogger` 持有执行器、日志根和写文件状态，路径为 `files/logs/kiyori.log`；`KiyoriLogTextFormatter` 处理有界消息/Throwable，旧 AppLogger 保持无状态兼容。
- 工具箱的显式清除与导出快照排入同一日志写入队列；清除失败传播给页面，不复用启动容错重置作为成功证据。只清当前应用日志，已导出文件及独立插件日志保留。快照完成后释放日志队列，公开导出继续由 `KiyoriPublicStore` 持有；计数与正文读取同一快照，临时快照随操作清理。
- 同目录 `operit.log` 一次性完整迁移，写后核验才删除旧文件；失败保留旧文件并记录。namespace、URI、AIDL、ToolPkg/MCP、native 与历史数据标识不随日志名称改变。

## Android 特权与权限

- `super_admin:shell` 是 Android Shell/Root 能力，区别于 Ubuntu/proot 的 `super_admin:terminal`。显式 Root 选 Root、Debugger 选 Debugger，无偏好时需要真实 Shizuku 授权的 Debugger。
- 权限不足明确失败，普通 application UID 不冒充 Shell/Root；报告配置级别、执行器、Binder/service、Shizuku UID、授权、可用性、exit code 和有界原因。
- 权限中心和首启共用真实快照；成功授权保存 `AndroidPermissionLevel.DEBUGGER` 并清执行器选择缓存。系统工具每次调用时解析执行器，权限变化无需重启。
- Intent/广播特权路径编译安全引用的结构化 `am` 参数，同时核对退出码和输出；不支持 extras 或受保护操作错误明确失败，非特权 Context API 独立保持。
- 当前 23 项目录中，“读取已安装应用列表”使用 Android 11+ 的真实 `QUERY_ALL_PACKAGES` 状态，不弹运行时授权；Android 10 以下视为系统已有。
- “解除设置限制”在 Android 13+ 打开应用详情，是否出现按钮及身份验证由系统/OEM 决定；低版本不适用，不伪造授权。

## Ubuntu 与代码执行

- terminal 子模块保留唯一会话与 CommandEnvelope。UTF-8 payload 在 Readline 前编码，由同一 Bash session 解码执行；TAB、引号、Unicode、CR、尾部 LF 保真，NUL 入队前拒绝。
- cwd 物理失效时先显式恢复到 `$HOME`，失败不执行用户命令；正常 cwd/export/jobs 与原始键盘 TAB/Ctrl+C 语义保持。code_runner 源文件仍按 LF 约定写入。
- `code_runner` 通过既有 Linux 文件提供者直接写入 UTF-8 源码，`$HOME/` 内部路径投影为该
  提供者支持的 `~/` 路径；PTY 只承载执行命令与输出，避免大段中文源码经命令包装膨胀后截断。
  文件写入失败明确终止，不执行残缺源码；环境和默认产物目录继续由原所有者解析。
- `code_runner_session` 是可见 PTY。Python/pip 使用 `~/.code_runner/py/bin/python`，Node 安装位于 `~/.code_runner/node`；环境信息工具报告实际会话、cwd、解释器、包路径与 rootfs。
- `run_python` 接收原始 Python 源码并以临时文件执行，无需调用方进行 Shell 转义。它与 `run_python_file` 都是非交互批处理：stdin 为 EOF、输出默认 `-u` 无缓冲；需要按键输入或 REPL 时使用 `super_admin:terminal` / `terminal_input`。
- `python_flags` 按空白、引号及反斜杠解析为独立参数，再逐项 Shell 引用，不展开变量、命令替换或重定向。接受脚本解释器开关及 `-W`、`-X`、`--check-hash-based-pycs` 的值；交互、`-c`、`-m`、帮助/版本模式、位置参数、缺值、未闭合引号和 NUL 在终端调用前失败。
- Python 文件路径保留 cwd 语义，前导 `-` 按文件处理；结果以真实退出码及 `timedOut` 判断，普通输出中的 Shell 错误示例不改变成功状态。每条命令默认限时 120000 ms，执行超时明确报告并保留已捕获输出；不把本地测试的中断/复用等同于 Android 现场验收。
- venv/探针在稳定 `$HOME` 子 shell 执行；用户相对文件路径保持调用方 cwd 语义。Go/Rust 临时构建使用每调用唯一目录；带引号 here-document 在独立 LF 行关闭。
- 文件型工具共享 Ubuntu 文件系统，Android 文件必须先复制或挂载；ES5 最终表达式与显式 return 继续按各自执行语义处理。
- 随机 OSC 命令 marker 解析真实退出码，FIFO 事件派发保证输出先于完成；超时作用于目标 session，不切用户标签；writer 失败清状态。
- hidden executor 是独立无 PTY 诊断面，限制输出并在超时清理进程组；环境安装捕获一个可见目标 PTY，逐步执行，失败停止，重复确认不重复提交。
- 环境配置展示实际本地/SSH provider、目标身份及工具路径/诊断，分批独立限时检查，缺失、需配置与未知分开；单项超时不会覆盖其他有效结果，用户可以重新检测。
- SSH hidden probe 读取目标登录交互 Bash 初始化，不复制当前 PTY 临时 export 或虚拟环境。自动安装只支持 APT Linux arm64/x64 和 root/免交互 sudo；每一步比对目标系统、架构、UID、HOME、主机和 machine-id 指纹，SSH 断线不得进入本地 Shell，远端负责发出就绪标记。
- Node 官方归档按架构及 SHA-256 固定，已可执行的兼容 Node 和独立升级 npm 不因随附版本差异重装。pnpm 安装启用原生可选包/必要脚本，通过自身配置接口设置全局 copy 导入方式；确认框说明持久变更和空间代价，项目配置覆盖仍需项目自身核对。TypeScript 就绪包含实际编译与 Node 运行，不能仅依赖 `--version`。
- 后台任务使用独立递增命名会话并返回 ID；显式后台 timeout 先验证再按协议忽略，省略时不设置限时。

## Rootfs 与工具链

- 新 APK 只包含 Resolute rootfs，Noble 保留在 `terminal/tools/rootfs/legacy`，不打包。安装路径仍为 `installed-rootfs/ubuntu`，活动 marker 为 `.kiyori_installed_ok`，旧 marker 仅用于一次迁移。
- staging 先验证 release、目录、链接和 executable mode，失败保留旧 rootfs。使用 BusyBox stat 位，不能用 Android host 的 access 检查替代；真实执行另做 PRoot 验收。
- 解包隔离 `umask 022`，防止 app 的 `077` 改写归档模式；这只能证明文件模式保留，不能证明设备执行。
- 终端环境版本统一读取 `terminal/TerminalEnvironmentContract` 源码；当前 pnpm 是 `12.3.4`，旧 CONTEXT 的 `11.25.0` 已失效。工具准备需要真实命令执行和官方归档 SHA-256，不能仅凭目录存在报告就绪。
- Android 主仓构建、Ubuntu 开发工具、通用 Android/Java 工作区及 Flutter 模板是不同版本合同，不机械统一 Gradle/JDK/Node 版本。
- Ubuntu/chroot/proot 的 `env -i` 入口携带 Android IANA 时区；静态 fake `/proc` fixture 不表示当前启动时刻。

## 语音服务

- `SpeechServiceFactory` / `VoiceServiceFactory` 各自通过 `SpeechProfileServiceOwner` 持有唯一配置与实例缓存。读配置、替换和 reset 串行，key 包括 profile ID 与构造参数。
- 替换先摘缓存再关闭旧实例，异常记录并传播，不返回已关闭实例、不切供应商；直接 create 的实例由调用方释放，本地 STT lease 和唤醒边界保持。
- `SpeechServiceProfilesPreferences` 初次建立时，仅在旧 TTS DataStore 没有已保存 key 的情况下建立 `builtin-next-tts`；已有配置迁为 `legacy-tts-profile` 并保留端点、音色、规则、速率和音高，不覆盖 profile 列表。
- Next 使用 `zh-CN-XiaoxiaoNeural`、`audio/mpeg` 和 `OFFSET_PERCENT`，`1.0x` 编码为 0；旧 HTTP `DIRECT` 模板语义保持，服务可用性和 Android 播放单独验收。

## 记忆图谱

`com.kiyori.capability.ai.memory` 持有 UUID、标题、文档/主标签分类与边的 ID/端点/权重/跨文件夹事实。MemoryRepository 继续持有 ObjectBox 与图选择；UI 后台映射颜色并复用边快照，备份统计不创建 Compose 对象，不改变 schema、搜索扩展或备份格式。
