---
scope: tools
---

# 分类布局与入口迁移

## 旧实现

多个独立工具直接位于 `tools/` 根目录。部分脚本从自身位置推导项目根目录，移动后会指向错误位置；文档、测试和应用内开发脚本下载地址也引用旧入口。

## 修改意图

- 将 ADB、ToolPkg、示例包、Shower、Compose DSL 与 native-ripgrep 入口分别归位
- 让每个移动后的脚本继续正确定位仓库根目录与同目录资源
- 将所有仓库内调用切换到新入口

## 当前布局

```text
tools/
	adb/
	compose_dsl/
	environment/
	example_packages/
	ffmpegkit_native_build/
	github/
	hotbuild/
	localization/
	mcp_bridge/
	mihomo_parent_launcher/
	native_ripgrep/
	player_native_build/
	search_packages/
	shell_identity_launcher/
	shower/
	toolpkg/
	README.md
	sandboxpackage_dev_install_or_update.js
```

## 预期结果

除固定公开安装脚本外，根目录不再存放分散的可执行工具文件。每个新入口具有单一、可追踪的位置，且不存在旧路径兼容脚本。

2026-09-06 核对后的入口及副作用见 [工具索引](../../../tools/README.md)。旧 `string` 和
`repair_repo_enviroment/windows` 路径不保留转发；前者深度不变，后者通过 `--root` 或当前目录
确定仓库，不依赖脚本旧位置。

[DONE]
