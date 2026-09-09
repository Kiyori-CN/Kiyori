# 平台、存储与服务生命周期契约

本页定义跨产品基础能力。依赖构建见 [构建指南](../dev-core/BUILDING.md)，设备状态以各专项实际验收为准。

## 路径与备份

- `com.kiyori.platform.storage.KiyoriPaths` 持有公开 Downloads/Pictures、内部/cache/files/backup、Browser 下载、ToolPkg 私有 generation、构建事务、内容寻址运行目录和创建/校验逻辑。
- `KiyoriPublicStore` 是 MediaStore 与 Android 8/9 公共文件唯一写入者，`KiyoriStorageService` 暴露该能力及 host-bound ToolPkg 存储。
- ToolPkg 私有/cache 操作不接受任意调用方 package ID，不返回内部绝对路径。显式 SAF 导入经过包专属 migrator 和原子 generation 激活。
- scanner、确定性 builder、content-addressed artifacts、审计和 active record 共同持有制品安装事务；页面不能另建安装状态源。
- `KiyoriBackupPaths` 是无状态投影，`OperitPaths / OperitBackupDirs` 保持 JVM 兼容门面，不重复计算路径。
- 不自动读取、合并或删除 `Download/Operit`；旧公共 ToolPkg 数据只在用户选择 SAF 输入并通过包专属验证后导入。

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
- `code_runner_session` 是可见 PTY。Python/pip 使用 `~/.code_runner/py/bin/python`，Node 安装位于 `~/.code_runner/node`；环境信息工具报告实际会话、cwd、解释器、包路径与 rootfs。
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
