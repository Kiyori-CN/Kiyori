# Windows 代理资源

`kiyori-pc-agent/` 是 Windows 代理资产的源码入口。

`manifest.json` 将其声明为目录资源（`inode/directory`）。调用 `ToolPkg.readResource("pc_agent_zip")` 时，运行时把该目录导出为 ZIP，无须另行预打包。
