# CI 检查与验证指南

`script/` 保存可复现的检查入口，`test/` 保存门禁逻辑的标准库单元测试。GitHub Actions 负责准备 runner，并按变更范围调用这些入口。

宿主工具按职责放在 [`tools/`](../tools/README.md)，Gradle 任务实现在
[`buildSrc/`](../buildSrc/README.md)。不为未实施流水线预建空的 `build_apps`、`debug_test`
或 `ci/tools` 目录；草稿目标结构不代表已有命令。

## 候选提交契约

PR 技术预审只运行在 GitHub 为当前 PR 和最新目标分支生成的 merge candidate 上：

```text
base = candidate^1
head = candidate^2
changed paths = git diff base..candidate
workspace = candidate
```

`ci/script/pr_check.py` 会验证两个父提交与 PR 事件中的 base/head 完全一致。路径分类、静态检查、测试和构建都使用同一个 candidate，不检出贡献者 head，也不比较 base tip 与过时 head。

## 本地检查

需要全仓审查时使用 [完整验证矩阵](../docs/doc-src/dev-core/QUALITY_VALIDATION.md)，覆盖
Python、Node、办公引擎、示例、九个 Android 模块和最终 APK。本页负责候选树、CI 路由与基线维护。

本地检查需要显式指定比较边界。Kiyori 持续开发使用 `main`；提交前记录本轮起点 SHA，候选提交形成后在干净工作区检查。`upstream` 指向 Operit，不能把它当作 Kiyori 本轮交付基线；推送后才计算 `origin/main...HEAD` 也可能把比较范围缩成空集。

以下为 Linux/macOS 命令目录，替换起点 SHA，并按当前任务影响面选择检查项：

```bash
BASE_SHA="<本轮开始时记录的Kiyori提交SHA>"
CANDIDATE_SHA="HEAD"

.venv/bin/python -B -m unittest discover -s ci/test -p 'test_*.py'
.venv/bin/python -B ci/script/check_architecture_boundaries.py --repository . --require-main
.venv/bin/python -B ci/script/check_repo_hygiene.py --base "$BASE_SHA" --candidate "$CANDIDATE_SHA"
.venv/bin/python -B ci/script/check_markdown_links.py --base "$BASE_SHA" --candidate "$CANDIDATE_SHA"
.venv/bin/python -B ci/script/check_localizations.py --base "$BASE_SHA" --candidate "$CANDIDATE_SHA"
.venv/bin/python -B ci/script/normalize_lint_baseline.py --check
```

Windows PowerShell 使用 `.\.venv\Scripts\python.exe` 和 `$baseSha`、`$candidateSha` 变量。尚未提交的文档先使用工作区检查，不以它代替候选树检查：

```powershell
.\.venv\Scripts\python.exe -B ci/script/check_documentation.py --repository .
```

新增或移动文档后添加 `--write-catalogs` 更新两份派生目录；脚本只维护目录并检查格式/文件链接，不重写正文。编码、代码围栏、HTML 标题与目录行为由 `ci.test.test_documentation` 回归验证。

架构门禁默认只输出最终结论。诊断本地运行时间异常时可追加 `--timings`，逐项输出
全部已注册检查的开始、结束与耗时；该选项不改变断言、扫描范围或退出码。源码枚举会跳过
`app/src/main` 下的 `.cxx`/`build` 生成目录，同一次进程内还会复用相同源码快照的
注释与字符串掩码，避免 native 构建缓存和重复符号搜索把门禁放大为非源码工作量。

本地分支不是 GitHub merge candidate，因此本地结果用于提交前检查；PR 页面上的 `Candidate checks` 才验证与当前 `main` 合并后的实际树。
`ci/test` 只读取 Git tree 中可复现的源码与固定测试夹具，因此可在未物化 Android
依赖的 fresh clone 中运行。被 `.gitignore` 排除的 player AAR 由
`prepare_android_dependencies.py` 生成，并由 Gradle `verifyPlayerNativeInputs`
在 Android JVM/full lane 中严格核对哈希、成员、ABI、native 所有权、TLS 和 C++ 符号。
正式 readiness 还锁定 llama.cpp 与 MNN 的精确 commit；`test_cmake_git_source.py`
使用 CMake 自身验证 40 位十六进制 SHA 的识别边界，避免固定 commit 被错误地当作移动
远端 ref 解析。

## PR 检查分层

每个 PR 只产生一个 `Candidate checks` 技术状态。快速检查会先收集全部诊断，失败后不启动耗时阶段。

- 所有改动：空白、冲突标记、JSON/XML 语法和门禁单元测试；变更 YAML 时使用 Psych AST 与 actionlint 1.7.12 检查
- 所有改动：按 `config/architecture/` 的机器可读清单检查源码 owner、Kiyori/Operit
  import 与完全限定项目引用的依赖方向、Manifest 组件/Intent/authority/process 合同、
  持久化与 native/IPC 稳定标识、AIDL/Room/ObjectBox 关键文件哈希及 terminal/制品边界；
  M-01 额外核对精确文件集、49 次符号映射和规范化纯改名
- 所有改动：比较 base/candidate 两棵 Git tree，只阻断 candidate 新增的本地断链；删除被文档引用的非 Markdown 文件也会检查
- 跨子模块的本地链接按对应父树记录的 gitlink 提交验证，可递归读取嵌套 gitlink；子模块当前 HEAD 不参与判断。只检查父仓库自身的 Markdown，且仅在链接实际跨入子模块时要求其对象可读。缺少所需子模块或提交会明确报出 `markdown-submodule`，不会按目录前缀放行，也不会由检查器联网初始化。
- PR 的 Markdown 步骤会先准备 terminal，并获取 base/candidate 各自记录的 terminal 提交；即使 shallow checkout 或本次更新了 gitlink，也能验证两棵确定的树。其他可选子模块不在该准备步骤内。
- 本地化：按 locale、资源类型和 key 比较，只阻断 candidate 引入或实际触碰的错误
- 翻译资源：运行 AAPT2 resource compile 检查资源语法，不执行 resource link 或完整 Android 构建
- Kotlin/Java 和普通 Android 资源：运行 JVM unit tests 与 Android lint
- Native、Gradle 和构建输入：运行 assemble、JVM unit tests 与 Android lint
- `buildSrc/**`：进入完整 Android lane；任务行为测试由 `:buildSrc:test` 执行，应用注册和 Variant 接线由 APK 构建验证
- `tools/localization/**`：进入本地化检查；环境软链接检查在 `tools/environment`，不自动修复 checkout
- player dependency preparer：进入 full Android lane，物化 AAR 后执行
  `verifyPlayerNativeInputs`
- WebChat：运行 TypeScript typecheck 与 Vite build
- ToolPkg：重建并核对 GitHub 示例，按独立锁文件编译 WASM 示例，再构建测试集合和生产白名单集合；JSON manifest 声明的入口与 WASM 文件必须存在且进入归档

根项目、`web-chat` 和独立的 `examples/toolpkg_wasm_demo` 分别提交 `package-lock.json`，CI 使用 `npm ci` 安装确定的依赖树。

`tools/example_packages/sync_example_packages.py` 复用根 `package-lock.json` 对应的 npm 安装树。
Windows 从 Python 子进程调用 `npm.cmd`，其他平台调用 `npm`；TypeScript 预构建使用
`npm exec -- tsc`，避免混用包管理器改写 `node_modules` 或生成未受控 lockfile。

PR workflow 只有 `contents: read` 权限，不读取仓库 secret，也不上传 APK/AAB。`Android Build` 是独立的可信 main/手工构建 workflow。

## 诊断

检查器在日志中按规则汇总错误，并通过 GitHub annotation 标记首批文件位置。Step summary 会记录 base、head、candidate、路径作用域及快速检查结果。历史问题只显示计数，不归责给未触碰它们的 PR。

## Android 依赖

JVM lane 只下载 `libs.zip`，完整 Android lane 下载四个固定归档。`download_android_dependencies.sh` 使用固定 Google Drive file ID；`prepare_android_dependencies.py` 限制成员数量、解压大小、压缩比和文件类型，重建固定输出根目录，只验证本次实际解出的文件，并拒绝越界路径、重复成员及符号链接。完整 lane 还必须传入固定 NDK 路径：脚本移除已由 Maven AAR 接管的旧 GIF native 副本、删除 ffmpeg AAR 内重复的旧 arm64 C++ 运行库，并用该 NDK 的 arm64 `libc++_shared.so` 作为唯一运行库。

这些 Drive 归档目前还没有内容 hash。归档内容寻址与许可证清单继续由[外部制品清单计划](../docs/TODO/refactor_building_sys/3_ExternalArtifactManifest.md)跟踪，在取得并审计真实归档前不记录推测值。

正式准备检查会拒绝任意层级已跟踪的 `__pycache__`、`.pyc` 与 `.pyo`；`.gitignore` 只阻止
新文件被普通添加，已经跟踪的缓存必须从索引中移除。检查本身不删除本机文件。

## 第三方 JAR 净化

`app: sanitizePoiOoxml`、`app:sanitizeBcpkix` 和 `terminal:sanitizeMinaCore` 从固定的
非传递 Maven 输入生成 `build/generated/sanitized-dependencies/` 下的本地 JAR。每个任务
都必须：

- 先确认预期的不安全入口仍存在；上游布局变化时立即失败
- 扫描保留 class 的字节码，拒绝任何对待删除能力的引用
- 只删除经源码使用面证明封闭且无消费者的 trust-all 包/类，以及失效的 JPMS
  descriptor 和 JAR 签名元数据
- 使用可复现成员顺序和固定时间戳，并在输出端再次断言目标入口为 0
- 保留上游许可证与 NOTICE；生成 JAR 只存在于忽略的 `build/`，不得提交

POI 与 BouncyCastle 由 app 直接持有；MINA 由直接使用 FTPServer 的 terminal 持有，
父 app 只通过 `project(":terminal")` 消费 terminal AAR。不得重新引入原始 MINA
传递依赖、复制第二个 sanitizer owner、关闭 dependency lint 或以 suppress 代替任务断言。

## Android Lint 基线

Android lint 使用 `app/lint-baseline.xml` 记录启用 PR 检查前已有的问题。新增 error 仍会使 `:app:lintDebug` 失败；新增 warning 按 Android lint 默认策略报告。

历史生成基准、条目数和摘要见 [基线历史](../docs/TODO/documentation_system_refinement/02_lint_baseline_history.md)。
当前 baseline 与归一化脚本中的摘要共同定义已审阅输入；实际新增诊断以本轮 Lint 报告为准。

baseline 维护必须把完整结果写入 `app/build/`，再用结构化 XML 交集脚本只删除失效记录：

```powershell
.\gradlew.bat ":app:updateLintBaseline" "-Pkiyori.lintBaseline=build/lint-baseline-current.xml" --no-daemon --console=plain
.\.venv\Scripts\python.exe -B ci\script\normalize_lint_baseline.py --prune-against app\build\lint-baseline-current.xml app\lint-baseline.xml
.\.venv\Scripts\python.exe -B ci\script\normalize_lint_baseline.py --check --prune-against app\build\lint-baseline-current.xml
```

交集脚本以问题 ID、message 和 location file 计数匹配，原样保留已审阅 XML 块。当前新问题只计数报告，不会进入 baseline。更新后必须记录生成基准、依赖环境与新校验和，并同步脚本中的 `EXPECTED_SHA256`。
