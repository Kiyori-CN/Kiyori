# Kiyori 开发工具

本目录保存按职责归档的宿主工具、Android 辅助工程和构建输入。仓库检查入口位于
[`ci/script/`](../ci/README.md)，Gradle 任务实现在 [`buildSrc/`](../buildSrc/README.md)，
可分发脚本与 ToolPkg 示例位于 [`examples/`](../examples)。三个位置不重复持有同一入口。

## 目录与边界

| 目录或文件 | 用途与入口 | 副作用与输入输出 |
| --- | --- | --- |
| `adb/` | [ADB 脚本执行](adb/JS_ADB_README_zh.md) | 连接设备并运行脚本，需明确目标与授权 |
| `compose_dsl/` | [Compose DSL 生成与调试](compose_dsl/README.md) | 生成绑定或从授权设备读取 UI |
| `environment/` | `sync_submodule_symlinks.py` | 默认检查；`--apply` 才修复已核对的子模块链接 |
| `example_packages/` | `sync_example_packages.py`、离线测试与 `*.live.mjs` | 同步脚本可能构建并改写 assets；live 入口连接外部服务 |
| `ffmpegkit_native_build/` | source lock、闭包 manifest、补丁和 overlay | 受审计的 native 构建输入；构建编排在 `ci/script/` |
| `github/` | Issue 查询、提交辅助与 Skill 导入 | 可能读取私密 `.env`、调用模型或 GitHub；运行前核对具体命令 |
| `hotbuild/` | 补丁生成、应用、安装与夜间工具 | 可选私有子模块独立管理；不属于常规 Debug 准备 |
| `localization/` | 字符串查询、统计、补齐与翻译 | 查询只读；写入和翻译入口会修改资源或调用外部模型 |
| `mcp_bridge/` | [终端 MCP 桥](mcp_bridge/README.md) | Node.js 子工程；运行时连接按配置建立 |
| `mihomo_parent_launcher/` | parent-death launcher C++ 源码 | 由 Gradle 编译，输出进入 app/build |
| `native_ripgrep/` | 固定 Cargo lock 与 Rust JNI 源码 | 由 Gradle 构建 arm64 运行库 |
| `player_native_build/` | 播放器闭包 manifest、pkg-config 与 Meson 辅助 | 由播放器依赖准备与 native 构建脚本消费 |
| `search_packages/` | `live_verify.mjs` | 真实搜索服务验证，不作为默认离线测试 |
| `shell_identity_launcher/` | shell launcher 源码与手动构建/推送脚本 | 正常 APK 由 Gradle 生成；push 脚本会操作设备 |
| `shower/` | 独立 Android 虚拟显示辅助工程 | 自有 Wrapper 与构建；启动/停止服务脚本会操作设备 |
| `toolpkg/` | ToolPkg 调试、hook runner | 可能打包、安装、热更新或从设备获取结果 |
| `sandboxpackage_dev_install_or_update.js` | 固定公开开发安装脚本 | 应用下载地址的稳定入口，目录整理不改 URL |

## 常用只读入口

在项目根目录使用仓库 `.venv`：

```powershell
.\.venv\Scripts\python.exe -B tools/localization/search_string.py --help
.\.venv\Scripts\python.exe -B tools/localization/check_strings.py --simple
.\.venv\Scripts\python.exe -B tools/localization/fill_missing_translations.py --report-only
.\.venv\Scripts\python.exe -B tools/environment/sync_submodule_symlinks.py --help
```

`fill_missing_translations.py --report-only` 只报告资源差异；默认翻译模式会使用
`tools/github/.env` 的既有接口配置，配置存在不代表获准外发字符串。
子模块链接检查需要相应子模块可用，可选夜间子模块缺失会明确报告；不自动下载或修复。

## 维护约定

- 目录使用明确的小写 `snake_case`，Python/Shell 入口使用描述性 `snake_case`，既有稳定公开入口保留。
- 同一工具的源码、模板和说明相邻；缓存、凭据、构建输出和临时报告必须被 Git 忽略。
- 工具从自身路径或显式参数定位仓库；新入口不依赖启动时的偶然工作目录。
- 移动前检查调用方、Gradle 输入、CI 分类、下载 URL 和文档；迁移后删除旧入口，不添加转发副本。
- 第三方源码、补丁与子模块保留来源和许可证；不因规范命名而重写上游目录或协议标识。

规范与迁移依据见 [仓库布局](../docs/doc-src/dev-core/REPOSITORY_LAYOUT.md) 和
[工具重整记录](../docs/TODO/tools_directory_reorganization/index.md)。
