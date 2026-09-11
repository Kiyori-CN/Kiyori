# AI 产物存储契约

本文定义 AI 生成文件的默认位置、显式目标优先级和实际执行边界。`KiyoriArtifactStoragePolicy` 是唯一偏好所有者，`ArtifactPathRules` 负责路径规则与浏览器文件的原子占位。它们规范默认输出，不构成任意 Shell、Java、第三方 ToolPkg 或 MCP 代码的文件系统沙箱。

默认根目录为：

- Android：系统共享存储根下的 `Download/Kiyori/workspace`，通常为 `/storage/emulated/0/Download/Kiyori/workspace`；提示词、读取 API 与设置页均展示绝对路径。
- Ubuntu：`/workspace`，该路径位于 Ubuntu rootfs 内，避免在 `/root` 产生脚手架和临时项目文件。

两者是独立文件系统，不是共享挂载别名。用户明确指定目标时保留原目标；`ArtifactStorageAccess` 从调用所属 chat ID 读取工作区绑定，在匹配环境覆盖默认根，不持久化第二份绑定。原生工具与脚本异步桥携带该调用上下文，不读取界面当前选中的对话。SAF/网络工作区不能伪装成本地路径，默认交付不支持时明确要求指定匹配环境。普通文件工具仍要求绝对路径，保留 `repo:`、网络和显式 Linux `~` 的既有语义；不把读取、覆盖编辑、安装、删除等操作静默重定向到新文件。

## 设置与校验

「设置 → AI 助手 → AI 产物保存位置」是偏好 `kiyori_artifact_storage` 的用户入口。Android、Ubuntu 分组展示，提供路径复制、输出示例、字段错误与固定保存区。未保存输入跨重建保留；恢复默认只填入草稿，点击保存才生效；离开时可保存、放弃或继续编辑。Back 与软键盘沿用当前设置页面及 AppContent 的所有权，退出动画中的旧页不拦截返回。

保存前同时校验两端路径，在 IO 调度器上执行同步持久化；Snackbar 不占用保存状态。Android `commit()` 失败可能已更新内存，因此失败时恢复此前两个键，避免界面报告失败而工具使用新位置。单环境消费者只解析自己使用的偏好；已有同环境工作区优先，不依赖被覆盖的默认值是否合法。

Android「选择文件夹」使用系统 DocumentsUI，仅接受内部共享存储的 `primary:` 文件夹并转成绝对路径，不保留不使用的 SAF 授权。它不等于取得直接 File/Shell 权限。「检查写入」由用户点击后创建目录、写入并读回随机临时文件、清理临时文件；成功只证明本应用当前直接文件访问。普通保存不创建目录、请求权限或启动 Ubuntu。

- Android 接受共享存储内的绝对目录，并兼容旧值 `Download/Kiyori/workspace` 与 `Kiyori/workspace`；`/sdcard` 别名解析到系统实际共享存储根。
- 拒绝共享存储根、Download 根及 Android 私有目录作为默认产物根。Linux 必须为专用绝对目录，拒绝 `/`、`/root` 等系统或 home 根；允许明确配置 `/root/project` 等专用子目录。
- 两端均拒绝 `..` 路径段、反斜杠和控制字符，归一重复斜杠与 `.`；不错误拒绝 `v1..2`，允许中文、空格等合法名称。Android 拒绝 URI scheme 与共享存储保留字符；Ubuntu 拒绝 `/proc`、`/sys`、`/dev` 及其子路径等虚拟文件系统。
- 设置不申请权限、不迁移旧文件，不检测或初始化 Ubuntu。旧非法偏好会产生可诊断错误；设置页仍可打开修复，系统提示告知修复或指定绝对目标，不静默换目录。

## 运行时接线

| 入口 | 默认行为与覆盖语义 |
| --- | --- |
| 系统提示与 `get_artifact_paths` | 内置或自定义系统模板均获得存储指导；只读工具返回本次调用的工作区/默认绝对根及 `linuxIsLocal`，不创建目录。可选 `environment=android/linux` 时只解析该环境，另一端非法设置不阻断查询 |
| JavaScript `getArtifactPaths()` / `getArtifactPath(environment)` | 每次调用经 NativeInterface 读取当前设置并投影该调用的工作区，不缓存成初始化常量；前者兼容返回 `android`、`linux`、`linuxIsLocal`；后者供输出消费者按需读取单端根，Linux 默认输出在 SSH provider 下明确拒绝。`linuxIsLocal` 以运行中的终端 provider 为准，provider 尚未建立时才读取当前会话与已保存的 SSH 偏好 |
| Office | 默认 Android 交付至 `<root>/office/<task_id>/`；显式 `output_env=linux` 且未给路径时也交付到对应根。默认目标在生成前解析并保持至本次交付；文件名及扩展名取实际产物，复制到临时副本并校验大小后发布；已有文件要求 `overwrite=true`，原地编辑保留原语义。交付失败返回原 Linux 产物和任务目录，不丢失恢复入口；环境检查/清理可单独报告默认目录错误，不伪装成管理操作失败 |
| Bilibili | 默认 `<android-root>/bilibili/library/<BV或EP>/pNN/`，采集清单、字幕、弹幕、评论、媒体和抽帧复用现有布局；显式 `output_root` 优先，重跑覆盖仍由 `overwrite` 决定 |
| 浏览器快照、日志与截图 | 默认 `<android-root>/browser/`，读取本次调用绑定的 Android 工作区；相对名称保留子目录与 Unicode，同名文件通过 `createNewFile()` 原子分配数字后缀；以配置根为边界拒绝穿越及分类目录/父目录符号链接越界。显式绝对文件名保留既有覆盖语义。写入或编码失败清理本次预留文件并报错 |
| 新建 AI 终端会话 | 本地默认使用本次 Linux 工作区/产物根；READY 后执行带 shell 引号保护的 `mkdir -p && cd`，等待真实退出码成功才交付会话；失败关闭本次新会话。`create_terminal_session.working_directory` / `Tools.System.terminal.create(name, directory)` 可显式指定新会话绝对目录或 `~`（HOME），支持环境安装等受管操作，也可指定新 SSH 会话目录。复用已有会话不强制重置 cwd；未指定目录的 SSH 与人工新建会话保持原语义 |
| code_runner 内联源码 | Python、Node、Ruby、Go、Rust、C、C++ 的用户代码在 `<linux-root>/code-runner` 中执行，相对产物留在那里；编译暂存仍在临时区，Node/Python 依赖仍在受管 HOME 目录。文件执行工具保留显式路径及现有终端上下文。SSH 不套用本地默认输出目录 |
| 通用文件下载、FFmpeg、第三方/MCP | 保留工具的显式绝对目标契约；模型先读取默认根再传目标参数。MCP 服务端和远端 SSH 不共享本机目录，不能靠提示词或正则声称阻止任意脚本写入其他位置 |

手动浏览器下载、配置备份、会话审计导出和 ToolPkg 私有存储继续由各自产品所有者管理。环境安装、虚拟环境、包缓存不属于交付物，不能为统一目录而迁移它们。设置变更仅影响新默认输出请求及新 AI 本地会话，已有文件与会话不移动。

## 验证边界

路径解析、目录选择 ID、并发同名分配、符号链接、持久化失败回滚、跨协程调用上下文、Office 两端交付与失败恢复、Bilibili 动态设置和代码运行器 cwd 有本地回归。真实 Android 权限、SAF 选择器、软键盘、返回/重建、Ubuntu/PRoot 会话启动、真实下载、第三方脚本及模型是否持续遵循目录指导仍需现场验收。进度与命令证据见[存储专项](../../TODO/kiyori_storage_and_toolpkg_data_governance/index.md)。
