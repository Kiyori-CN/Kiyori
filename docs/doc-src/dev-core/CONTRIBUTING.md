# Kiyori 贡献指南

感谢你为 Kiyori 提交问题、文档、脚本、插件或代码。本指南说明问题反馈、开发分支、验证、
Pull Request 和安全边界。环境准备与构建步骤见 [Kiyori Android 构建指南](./BUILDING.md)。

## 项目定位

Kiyori 是面向 Android 的人机协作应用，浏览器与内容处理构成核心工作环境，Operit AI
提供内置智能子系统。贡献时请保持：

- Browser Home 与 AI 浏览器工具继续共用唯一 Browser Runtime
- 播放器、下载器、设置、存储和权限继续使用既有唯一状态所有者
- `com.ai.assistance.operit`、`operit://`、AIDL、JNI、Intent、数据库、备份和 ToolPkg 等稳定
  兼容标识不做机械品牌替换
- 用户数据、公开协议和外部调用行为的变化具有明确兼容与迁移说明

术语、模块所有权和不变量以 [`CONTEXT.md`](../../../CONTEXT.md) 为准。

## 贡献类型

| 领域 | 主要目录 |
| --- | --- |
| Android 应用、浏览器、AI、设置与系统集成 | `app/` |
| Android/native library 模块 | `dragonbones/`、`terminal/`、`mnn/`、`llama/`、`mmd/`、`fbx/`、`showerclient/`、`quickjs/` |
| WebChat | `web-chat/` |
| 脚本、ToolPkg 与示例 | `examples/` |
| 构建、检查和仓库工具 | `.github/`、`ci/`、`tools/` |
| Gradle 自定义任务及行为测试 | `buildSrc/` |
| 正式文档和专项计划 | `docs/` |

脚本作者还应阅读 [脚本开发指南](../../SCRIPT_DEV_GUIDE.md) 和
[ToolPkg 格式指南](../../TOOLPKG_FORMAT_GUIDE.md)。

## 提交 Issue

提交前先搜索 [Kiyori Issues](https://github.com/Kiyori-CN/Kiyori/issues)，并选择匹配的
Issue Form：

- Bug report：可复现错误、崩溃、卡死或异常行为
- Feature request：具体功能需求或行为改进
- Plugin, Skill or MCP report：外部包、Skill、工具或 MCP 服务问题
- Question：使用、配置和项目行为咨询

高质量问题至少包含：

- Kiyori 完整版本、构建日期或 commit hash
- 设备型号、Android 版本或 WebChat 浏览器环境
- 实际行为、预期行为和影响
- 可重复的最短步骤
- 相关模型、Provider、工具、插件或设置
- 必要且已脱敏的截图、录屏或日志

不要提交 API Key、Token、Cookie、手机号、私人对话、签名材料、私有端点或其他敏感数据。
只有标题、缺少上下文或未经脱敏的反馈会显著增加处理时间。

## 开发环境

项目使用 JDK 21、Node.js 22 与 npm lockfile、Android Platform 37、Build Tools 36.0.0、
NDK 28.2.13676358、CMake 3.22.1 和 Rust 1.88.0。请按
[构建指南](./BUILDING.md)准备工具链、大型 Android 输入、WebChat 和 ToolPkg 依赖。

不要递归初始化所有子模块。常规开发只需要：

```bash
git submodule sync -- terminal
git submodule update --init --recursive terminal
```

## Fork 与分支

Fork Kiyori 后克隆自己的仓库：

```bash
git clone https://github.com/<your-account>/Kiyori.git
cd Kiyori
git remote add upstream https://github.com/Kiyori-CN/Kiyori.git
git fetch upstream
git switch -c fix/short-description upstream/main
git submodule update --init --recursive terminal
```

推荐使用清晰的分支前缀：

- `feat/`：新功能
- `fix/`：问题修复
- `perf/`：有证据的性能优化
- `docs/`：文档
- `ci/`：构建和自动化
- `refactor/`：不改变公开行为的结构调整
- `test/`：测试

所有 Pull Request 以 `main` 为目标分支。

## 开发原则

代码、资源、工具和文档命名遵循 [仓库布局](REPOSITORY_LAYOUT.md) 的分类规则。
修改前从 [仓库与源码架构](../architecture/repository_architecture.md) 确认实际 owner，
不要把规划中的包或模块当作已迁移实现。

- 先阅读项目规则、相关实现、测试和正式文档，再修改代码
- 修复根因，不通过异常吞噬、隐式重试、禁用检查、扩大 Lint baseline 或 suppression 掩盖问题
- 保持改动聚焦，不混入无关格式化、依赖升级、重命名或重构
- 优先复用已有状态所有者、协议和构建入口，不创建平行实现
- 数据、配置、公开 API、Intent、AIDL、JNI 或存储变化必须说明兼容与迁移
- 新增用户文字时同步维护所有支持的语言资源和本地化检查
- UI 与交互变化提供截图、录屏或设备说明；无法运行设备验证时明确标记
- native、播放器和 ABI 改动必须说明工具链、owner、符号、ELF 与 16 KB 对齐证据
- 不直接修改第三方子模块来绕过主仓问题；子模块变更应独立审计和交付

## 文档职责

不同文档具有不同职责，避免复制出多份权威来源：

| 文件或目录 | 职责 |
| --- | --- |
| `README.md` | 面向用户和新贡献者的项目入口、构建与导航 |
| `CONTEXT.md` | 术语、状态所有者、协议、兼容性和不变量 |
| `AGENTS.md` | Agent 与维护者工作规则 |
| `docs/doc-src/` | 正式架构、开发、协议、测试和决策文档 |
| `docs/TODO/` | 专项计划、阶段状态与历史验证证据 |

行为、配置或协议变化必须同步修改对应权威文档。历史验证数字只代表当时观察点，不应伪装为
当前结果。

## 本地验证

按本轮风险选择相关验证；每轮仓库修改默认最终串行构建 Debug APK。下方完整矩阵用于
涉及相应领域或明确要求全量审查的改动，不要求仅修改一份文档就运行所有测试与 Lint。
修改自定义 Gradle 任务时先执行 `./gradlew :buildSrc:test --no-daemon --console=plain`，再验证 APK。

仓库 Python 命令使用项目 `.venv`。Windows 示例：

```powershell
.\.venv\Scripts\python.exe -B -m unittest discover -s ci\test -p "test_*.py"
.\.venv\Scripts\python.exe -B ci\script\check_formal_readiness.py --repository . --require-main
.\.venv\Scripts\python.exe -B ci\script\check_architecture_boundaries.py --repository . --require-main
.\.venv\Scripts\python.exe -B ci\script\normalize_lint_baseline.py --check
```

WebChat：

```bash
npm --prefix web-chat ci --no-audit --no-fund
npm --prefix web-chat run typecheck
npm run build:webchat
```

示例与 ToolPkg：

```bash
npm ci --no-audit --no-fund
npm run build:examples:github
git diff --exit-code -- examples/github.js
npm --prefix examples/toolpkg_wasm_demo ci --no-audit --no-fund
npm --prefix examples/toolpkg_wasm_demo run pack:toolpkg
```

完整 Android 仓库矩阵：

```bash
./gradlew \
  testDebugUnitTest \
  compileDebugAndroidTestKotlin \
  compileDebugAndroidTestJavaWithJavac \
  lintDebug \
  --stacktrace \
  --no-build-cache \
  --no-daemon \
  --console=plain
```

最终 Debug APK：

```bash
./gradlew :app:assembleDebug --no-daemon --console=plain
```

Windows 使用同名的 `gradlew.bat`。候选提交的 Markdown、本地化、仓库卫生、YAML 和
fresh-clone 检查见 [`ci/README.md`](../../../ci/README.md)。

## Pull Request 要求

提交前把分支更新到最新 `upstream/main`，解决分歧并重跑相关验证：

```bash
git fetch upstream
git rebase upstream/main
git push --set-upstream origin fix/short-description
```

PR 应说明：

- 背景、目标、改动范围和明确非目标
- 关联 Issue；纯文档或维护改动可写 `N/A` 并解释背景
- 用户行为、数据、协议、配置、性能、安全和包体影响
- 运行过的精确命令、环境、变体和结果
- 未运行的设备、Release、远端或用户验收及原因
- 截图、录屏、日志、构建产物或前后对比等必要证据

推荐使用 Conventional Commits 风格标题，例如：

```text
fix(browser): preserve tab state after media playback
perf(packaging): deduplicate shared workspace toolchains
docs: rebuild contributor and build guides
```

## 提交前安全检查

禁止提交：

- `local.properties`、`.env`、API Key、Token、Cookie、私钥和签名文件
- `.venv/`、`node_modules/`、Gradle/CMake 缓存和 `build/`
- APK、AAB、生成 `.toolpkg`、WebChat 构建输出和临时报告
- 未审计的大型二进制、Junction、符号链接或嵌套 `.git`
- 与当前 PR 无关的用户工作或本机配置

提交前检查 `git status`、`git diff --check`、暂存路径、异常大文件和敏感内容。不要使用
`git add -A` 代替对最终交付范围的理解。

## 许可证与上游归属

Kiyori 基于 Operit 演进，并继续保留上游作者、贡献者、源码归属和兼容标识。本仓库按
[GNU GPL v3 或更高版本](../../../LICENSE)提供。提交代码、文档或资源前，请确认你有权按该
许可证贡献，并保留适用的第三方许可证与 NOTICE。
