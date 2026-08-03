# CI checks

`script/` 保存可复现的检查入口，`test/` 保存门禁逻辑的标准库单元测试。GitHub Actions 负责准备 runner，并按变更范围调用这些入口。

## Candidate contract

PR 技术预审只运行在 GitHub 为当前 PR 和最新目标分支生成的 merge candidate 上：

```text
base = candidate^1
head = candidate^2
changed paths = git diff base..candidate
workspace = candidate
```

`ci/script/pr_check.py` 会验证两个父提交与 PR 事件中的 base/head 完全一致。路径分类、静态检查、测试和构建都使用同一个 candidate，不检出贡献者 head，也不比较 base tip 与过时 head。

## Local checks

本地检查需要显式指定比较边界。在干净的功能分支上运行：

```bash
git fetch upstream main
BASE_SHA="$(git merge-base upstream/main HEAD)"
CANDIDATE_SHA="HEAD"

python3 -B -m unittest discover -s ci/test -p 'test_*.py'
python3 -B ci/script/check_architecture_boundaries.py --repository . --require-main
python3 -B ci/script/check_repo_hygiene.py --base "$BASE_SHA" --candidate "$CANDIDATE_SHA"
python3 -B ci/script/check_markdown_links.py --base "$BASE_SHA" --candidate "$CANDIDATE_SHA"
python3 -B ci/script/check_localizations.py --base "$BASE_SHA" --candidate "$CANDIDATE_SHA"
python3 -B ci/script/normalize_lint_baseline.py --check
```

架构门禁默认只输出最终结论。诊断本地运行时间异常时可追加 `--timings`，逐项输出
36 个检查的开始、结束与耗时；该选项不改变断言、扫描范围或退出码。源码枚举会跳过
`app/src/main` 下的 `.cxx`/`build` 生成目录，同一次进程内还会复用相同源码快照的
注释与字符串掩码，避免 native 构建缓存和重复符号搜索把门禁放大为非源码工作量。

本地分支不是 GitHub merge candidate，因此本地结果用于提交前检查；PR 页面上的 `Candidate checks` 才验证与当前 `main` 合并后的实际树。
`ci/test` 只读取 Git tree 中可复现的源码与固定测试夹具，因此可在未物化 Android
依赖的 fresh clone 中运行。被 `.gitignore` 排除的 player AAR 由
`prepare_android_dependencies.py` 生成，并由 Gradle `verifyPlayerNativeInputs`
在 Android JVM/full lane 中严格核对哈希、成员、ABI、native 所有权、TLS 和 C++ 符号。

## PR check lanes

每个 PR 只产生一个 `Candidate checks` 技术状态。快速检查会先收集全部诊断，失败后不启动耗时阶段。

- 所有改动：空白、冲突标记、JSON/XML 语法和门禁单元测试；变更 YAML 时使用 Psych AST 与 actionlint 1.7.12 检查
- 所有改动：按 `config/architecture/` 的机器可读清单检查源码 owner、Kiyori/Operit
  import 与完全限定项目引用的依赖方向、Manifest 组件/Intent/authority/process 合同、
  持久化与 native/IPC 稳定标识、AIDL/Room/ObjectBox 关键文件哈希及 terminal/制品边界；
  M-01 额外核对精确文件集、49 次符号映射和规范化纯改名
- 所有改动：比较 base/candidate 两棵 Git tree，只阻断 candidate 新增的本地断链；删除被文档引用的非 Markdown 文件也会检查
- 本地化：按 locale、资源类型和 key 比较，只阻断 candidate 引入或实际触碰的错误
- 翻译资源：运行 AAPT2 resource compile 检查资源语法，不执行 resource link 或完整 Android 构建
- Kotlin/Java 和普通 Android 资源：运行 JVM unit tests 与 Android lint
- Native、Gradle 和构建输入：运行 assemble、JVM unit tests 与 Android lint
- player dependency preparer：进入 full Android lane，物化 AAR 后执行
  `verifyPlayerNativeInputs`
- WebChat：运行 TypeScript typecheck 与 Vite build
- ToolPkg：重建并核对 GitHub 示例，按独立锁文件编译 WASM 示例，再构建测试集合和生产白名单集合；JSON manifest 声明的入口与 WASM 文件必须存在且进入归档

根项目、`web-chat` 和独立的 `examples/toolpkg_wasm_demo` 分别提交 `package-lock.json`，CI 使用 `npm ci` 安装确定的依赖树。

`tools/example_packages/sync_example_packages.py` 从 Python 子进程调用 pnpm。Windows 使用 `pnpm.cmd`，其他平台使用 `pnpm`，避免 PATH 中无扩展名 shim 阻止 Windows Python 启动预构建命令。

PR workflow 只有 `contents: read` 权限，不读取仓库 secret，也不上传 APK/AAB。`Android Build` 是独立的可信 main/手工构建 workflow。

## Diagnostics

检查器在日志中按规则汇总错误，并通过 GitHub annotation 标记首批文件位置。Step summary 会记录 base、head、candidate、路径作用域及快速检查结果。历史问题只显示计数，不归责给未触碰它们的 PR。

## Android dependencies

JVM lane 只下载 `libs.zip`，完整 Android lane 下载四个固定归档。`download_android_dependencies.sh` 使用固定 Google Drive file ID；`prepare_android_dependencies.py` 限制成员数量、解压大小、压缩比和文件类型，重建固定输出根目录，只验证本次实际解出的文件，并拒绝越界路径、重复成员及符号链接。完整 lane 还必须传入固定 NDK 路径：脚本移除已由 Maven AAR 接管的旧 GIF native 副本、删除 ffmpeg AAR 内重复的旧 arm64 C++ 运行库，并用该 NDK 的 arm64 `libc++_shared.so` 作为唯一运行库。

这些 Drive 归档目前还没有内容 hash。归档内容寻址与许可证清单继续由[外部制品清单计划](../docs/TODO/refactor_building_sys/3_ExternalArtifactManifest.md)跟踪，在取得并审计真实归档前不记录推测值。

## Sanitized third-party JARs

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

## Android lint baseline

Android lint 使用 `app/lint-baseline.xml` 记录启用 PR 检查前已有的问题。新增 error 仍会使 `:app:lintDebug` 失败；新增 warning 按 Android lint 默认策略报告。

初始 baseline 使用 AGP 8.13.2 并启用依赖检查，从上游提交 `1fe3b5eddb1f5c6ed795465f80716dda8c36cc65` 生成，对应 [GitHub Actions 运行](https://github.com/luojiaping/Operit/actions/runs/29661867372)。2026-07-24 在 AGP 9.3.1 下生成临时完整 baseline，与已审阅 baseline 求交集：原样保留 `5843` 条仍存在的记录，删除 `200` 条失效记录，不吸收 `30` 条当时可见问题。2026-07-31 的 M-01 只把 6 个 `OperitApplication.kt` location 路径改为 `KiyoriApplication.kt`；2026-08-01 的 M-03 继续把这 6 个 location 路径移到 `com/kiyori/app/KiyoriApplication.kt`，两次均未改变 issue、message、line、column 或条目数量。2026-08-01 在 M-04 根组合、Shell 与 MainActivity 职责拆分后重新生成完整 lint 结果，通过结构化交集删除当前工具链不再报告的 `51` 条历史记录，保留 `5792` 条原有记录，且不吸收 `328` 条 current-only 问题。2026-08-02 的 M-05B 把 logger 的早期绑定状态从静态 Android `Context` 收敛为提前解析的 `filesDir: File`；新鲜完整 baseline 证明旧 `AppLogger.kt` 的 1 条 `StaticFieldLeak` 已失效，结构化交集只删除该记录，保留 `5791` 条且不吸收 `316` 条 current-only 问题。2026-08-02 的 M-05E 把唯一 `SdCardPath` 既有记录从兼容 facade `OperitPaths.kt` 精确迁到唯一 owner `KiyoriPaths.kt`，只更新 location 与同一代码行中的 owner 常量名，不新增、删除或吸收 issue。2026-08-02 的 QD-03 删除 3 个已无引用且缺少翻译的设置字符串，并把 1 个无引用英文数量字符串移出资源树；临时完整 baseline 为 `5845` 条，结构化交集只删除这 4 条失效记录，保留 `5787` 条原有记录，不吸收 `58` 条 current-only 警报。2026-08-02 的 QD-04 为 reply-proxy 补齐 `WEB_MESSAGE_LISTENER` 正向 feature guard；临时完整 baseline 为 `5814` 条，结构化交集只删除对应 1 条失效 `RequiresFeature`，保留 `5786` 条，不吸收 `28` 条依赖警报。2026-08-03 的 QD-05 升级并净化依赖后，fresh lint current-only 为 0；临时完整 baseline 为 `5776` 条，结构化交集删除 7 条已升级版本记录和 3 条失效版本目录记录，最终 `retained=5776 / stale=0 / current-only=0`。当前归一化 SHA-256 为 `9e557039ef859a818e027196c59734d96e3ca7cca544a389a8715fff8d5bd2d9`。

baseline 维护必须把完整结果写入 `app/build/`，再用结构化 XML 交集脚本只删除失效记录：

```powershell
.\gradlew.bat ":app:updateLintBaseline" "-Pkiyori.lintBaseline=build/lint-baseline-current.xml" --no-daemon --console=plain
.\.venv\Scripts\python.exe -B ci\script\normalize_lint_baseline.py --prune-against app\build\lint-baseline-current.xml app\lint-baseline.xml
.\.venv\Scripts\python.exe -B ci\script\normalize_lint_baseline.py --check --prune-against app\build\lint-baseline-current.xml
```

交集脚本以问题 ID、message 和 location file 计数匹配，原样保留已审阅 XML 块。当前新问题只计数报告，不会进入 baseline。更新后必须记录生成基准、依赖环境与新校验和，并同步脚本中的 `EXPECTED_SHA256`。
