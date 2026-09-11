# AI 产物存储契约

Kiyori 对 AI 产生的可交付文件采用单一的 `KiyoriArtifactStoragePolicy`。工具实现、角色卡和系统提示词只能引用该策略提供的路径投影，不能自行把文件写入当前目录、终端 home 或应用私有目录。

默认根目录为：

- Android：`Download/Kiyori/workspace`（运行时显示为 `/storage/emulated/0/Download/Kiyori/workspace` 或系统实际 external storage 路径）。
- Ubuntu：`/workspace`，该路径位于 Ubuntu rootfs 内，避免在 `/root` 产生脚手架和临时项目文件。

策略要求工具先解析绝对路径，再以匹配的 `environment=android|linux` 执行；写入成功后返回并验证最终路径。用户明确给出的路径或已选工作区优先。默认产物应使用任务子目录和描述性文件名，禁止用相对路径、仅时间戳、`test`/`new`/`output` 等无意义名称或覆盖已有文件。

根目录由 `kiyori_artifact_storage` 偏好保存，写入前拒绝 Android 绝对路径、Linux `..` 路径和非绝对 Linux 根。设置 UI 接入时必须调用该 owner，不能复制偏好键或路径拼接逻辑。

AI 设置中的“AI 产物保存位置”页面是该偏好的唯一用户入口。浏览器页面快照、控制台日志、网络日志和截图默认写入配置的 Android 根目录下的 `browser/`，并在同名文件存在时自动分配递增后缀；用户传入绝对路径时保留用户选择。

提示词是模型行为指导，工具层仍应继续校验路径和环境；二者缺一不可。终端初始 cwd、下载器、浏览器导出、Office/媒体导出和 ToolPkg 若提供默认输出路径，均应委托此策略或显式要求用户目标。
