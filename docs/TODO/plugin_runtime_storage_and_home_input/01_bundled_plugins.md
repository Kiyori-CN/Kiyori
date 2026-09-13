# 预置插件资产

从 `tools/example_packages/packages_whitelist.txt` 读取生产清单。普通 `.js` 继续使用已提交的主 assets；含 manifest 的目录由 Gradle 任务按稳定顺序生成 `.toolpkg` 到构建目录，并通过 Android Variant Sources API 接入各变体。

生成任务必须拒绝缺失 manifest、符号链接和越出 `examples/` 的路径。CI 可以继续构建示例源码和测试专用 ToolPkg，但不再把生产 `.toolpkg` 写回 `app/src/main/assets/packages/`。

`assembleDebug` 已执行生成任务；APK 的 `assets/packages/` 包含白名单中的 11 个 `.toolpkg`，每个包都有 manifest 与运行入口，且与构建目录生成文件逐项哈希一致。

[DONE]
