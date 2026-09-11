# AI 产物存储契约

本文定义 AI 生成文件的默认位置、显式目标优先级和实际执行边界。`KiyoriArtifactStoragePolicy` 是唯一偏好所有者，`ArtifactPathRules` 负责路径规则与浏览器文件的原子占位。它们规范默认输出，不构成任意 Shell、Java、第三方 ToolPkg 或 MCP 代码的文件系统沙箱。

默认根目录为：

- Android：系统共享存储根下的 `Download/Kiyori/workspace`，通常为 `/storage/emulated/0/Download/Kiyori/workspace`；提示词、读取 API 与设置页均展示绝对路径。
- Ubuntu：`/workspace`，该路径位于 Ubuntu rootfs 内，避免在 `/root` 产生脚手架和临时项目文件。

两者是独立文件系统，不是共享挂载别名。用户明确指定目标时保留原目标；脚本调用的 `getArtifactPaths()` 从已有 chat ID 读取工作区绑定，在匹配环境覆盖默认根，不持久化第二份绑定。SAF/网络工作区不能伪装成本地路径，默认交付不支持时明确要求指定匹配环境。普通文件工具仍要求绝对路径，保留 `repo:`、网络和显式 Linux `~` 的既有语义；不把读取、覆盖编辑、安装、删除等操作静默重定向到新文件。

## 设置与校验

「AI 设置 → AI 产物保存位置」是偏好 `kiyori_artifact_storage` 的用户入口。保存前同时校验两端路径，使用同步持久化结果判断成功，磁盘操作在 IO 调度器上执行。界面保留未保存输入，错误可见；恢复默认走同一保存流程。

- Android 接受共享存储内的绝对目录，并兼容旧值 `Download/Kiyori/workspace` 与 `Kiyori/workspace`；`/sdcard` 别名解析到系统实际共享存储根。
- 拒绝共享存储根、Download 根及 Android 私有目录作为默认产物根。Linux 必须为专用绝对目录，拒绝 `/`、`/root` 等系统或 home 根；允许明确配置 `/root/project` 等专用子目录。
- 两端均拒绝 `..` 路径段、反斜杠和控制字符，归一重复斜杠与 `.`；不错误拒绝 `v1..2`，允许中文、空格等合法名称。
- 设置不申请权限、不迁移旧文件，不检测或初始化 Ubuntu。旧非法偏好会产生可诊断错误；设置页仍可打开修复，系统提示告知修复或指定绝对目标，不静默换目录。

## 运行时接线

| 入口 | 默认行为与覆盖语义 |
| --- | --- |
| 系统提示与 `get_artifact_paths` | 内置或自定义系统模板均获得存储指导；只读工具返回最新默认绝对根，不创建目录。明确目标与已绑定工作区优先 |
| JavaScript `getArtifactPaths()` | 每次调用经 NativeInterface 读取当前设置并投影该调用的工作区，不缓存成初始化常量；返回 `android`、`linux`、`linuxIsLocal`。`linuxIsLocal` 以运行中的终端 provider 为准，provider 尚未建立时才回退到当前会话与已保存的 SSH 偏好 |
| Office | 默认 Android 交付至 `<root>/office/<task_id>/`；显式 `output_env=linux` 且未给路径时也交付到对应根。文件名及扩展名取实际产物，复制到临时副本并校验大小后发布；已有文件要求 `overwrite=true`，原地编辑保留原语义 |
| Bilibili | 默认 `<android-root>/bilibili/library/<BV或EP>/pNN/`，采集清单、字幕、弹幕、评论、媒体和抽帧复用现有布局；显式 `output_root` 优先，重跑覆盖仍由 `overwrite` 决定 |
| 浏览器快照、日志与截图 | 默认 `<android-root>/browser/`，相对名称保留子目录与 Unicode，同名文件通过 `createNewFile()` 原子分配数字后缀；拒绝穿越及父目录符号链接越界。显式绝对文件名保留既有覆盖语义。写入或编码失败清理本次预留文件并报错 |
| 新建 AI 本地终端会话 | READY 后执行带 shell 引号保护的 `mkdir -p && cd`，等待真实退出码成功才交付会话；失败关闭本次新会话。复用已有会话不强制重置 cwd，不修改 SSH 或人工新建会话 |
| code_runner 内联源码 | Python、Node、Ruby、Go、Rust、C、C++ 的用户代码在 `<linux-root>/code-runner` 中执行，相对产物留在那里；编译暂存仍在临时区，Node/Python 依赖仍在受管 HOME 目录。文件执行工具保留显式路径及现有终端上下文。SSH 不套用本地默认输出目录 |
| 通用文件下载、FFmpeg、第三方/MCP | 保留工具的显式绝对目标契约；模型先读取默认根再传目标参数。MCP 服务端和远端 SSH 不共享本机目录，不能靠提示词或正则声称阻止任意脚本写入其他位置 |

手动浏览器下载、配置备份、会话审计导出和 ToolPkg 私有存储继续由各自产品所有者管理。环境安装、虚拟环境、包缓存不属于交付物，不能为统一目录而迁移它们。设置变更仅影响新默认输出请求及新 AI 本地会话，已有文件与会话不移动。

## 验证边界

路径解析、并发同名分配、Office 两端交付、Bilibili 动态设置和代码运行器 cwd 有本地回归。真实 Android 权限、SAF、Ubuntu/PRoot 会话启动、真实下载、第三方脚本及模型是否持续遵循目录指导仍需现场验收。进度与命令证据见[存储专项](../../TODO/kiyori_storage_and_toolpkg_data_governance/index.md)。
