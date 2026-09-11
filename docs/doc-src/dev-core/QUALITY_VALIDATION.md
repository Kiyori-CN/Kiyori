# 全仓质量验证

本指南用于明确要求全面检查的维护轮次，普通改动按影响面选择。CI 的候选树与路径分层
以 [CI 指南](../../../ci/README.md) 和 workflow 为准。命令在仓库根目录执行。

## 准备与执行顺序

1. 记录分支、HEAD、父仓及 `terminal` 改动，按 [构建指南](BUILDING.md) 准备输入。
2. 静态检查与离线测试可独立运行；示例重建和资产同步必须在 Gradle 打包前完成。
3. Gradle 命令串行执行，超时后先等待原进程，不能用第二次构建覆盖报告。
4. 修复失败并复跑相关测试，生成最终 APK，形成候选提交后验证该树与远端 ref。

日志放入忽略的 `work/`，摘要写入当前专项，不提交生成产物。

## Python 与文档

```powershell
.\.venv\Scripts\python.exe -B -m unittest discover -s ci/test -p 'test_*.py'
.\.venv\Scripts\python.exe -B ci/script/check_documentation.py --repository .
.\.venv\Scripts\python.exe -B ci/script/check_formal_readiness.py --repository . --require-main
.\.venv\Scripts\python.exe -B ci/script/check_architecture_boundaries.py --repository . --require-main
.\.venv\Scripts\python.exe -B ci/script/normalize_lint_baseline.py --check
```

新增或移动文档后添加 `--write-catalogs` 更新目录。检查器不证明内容与源码一致、外部链接
可用或渲染正确；迁移还需核对锚点、机器消费路径及状态边界。

## Node、示例与办公引擎

依赖按已提交 lockfile 准备。以下测试使用 mock、临时文件或回环 HTTP 夹具；真实账号与
`*.live.mjs` 服务测试分别执行。项目 Python 使用 `.venv`，办公测试需要其中已准备的办公库。

```powershell
node --test tools/example_packages/*.test.mjs tools/compose_dsl/*.test.mjs
npm run test:toolpkg:types
npm --prefix web-chat run typecheck
npm run build:webchat
.\.venv\Scripts\python.exe -B -m pytest examples/office_suite/resources/runtime/kiyori_office/tests -q
```

`browser_extension_runtime.fixture.mjs` 由 JVM `BrowserExtensionBootstrapTest` 注入生产
Kotlin 生成的源码，不能独立启动。Windows 真实 Bash PTY 测试需要显式设置
`KIYORI_PTY_WSL_DISTRO`；未配置时的跳过不能报告为通过。

重建示例后审阅源码与已提交分发文件的一致性：

```powershell
npm run build:examples:academic
npm run build:examples:github
node node_modules/typescript/bin/tsc -p examples/bilibili_toolkit/tsconfig.json --noEmit --pretty false
node examples/bilibili_toolkit/build.mjs
node node_modules/typescript/bin/tsc -p examples/remote_kiyori/tsconfig.json --pretty false
node node_modules/typescript/bin/tsc -p examples/windows_control/tsconfig.json --pretty false
npm --prefix examples/toolpkg_wasm_demo run pack:toolpkg
.\.venv\Scripts\python.exe -B tools/example_packages/sync_example_packages.py --mode test --no-hot-reload
.\.venv\Scripts\python.exe -B tools/example_packages/sync_example_packages.py --mode normal --no-hot-reload
```

测试集合生成后恢复生产白名单并检查 `git diff`；不直接接受未解释的输出漂移。
目录型生产 ToolPkg 最终由 Gradle 打包，源码 JS 与确定性 dist 按各工程约定维护。

## Android 完整矩阵

```powershell
.\gradlew.bat :buildSrc:test testDebugUnitTest compileDebugAndroidTestKotlin compileDebugAndroidTestJavaWithJavac lintDebug --continue --stacktrace --no-build-cache --no-daemon --console=plain
```

根聚合任务覆盖九个 Android 模块。`--continue` 收集独立任务的失败，不改变通过条件。
AndroidTest 编译没有在设备执行 instrumentation。检查实际 XML 的测试、失败、错误、跳过数量
及各模块 Lint 报告；`:app:lintDebug` 单独通过不能代表全仓通过。

修复必要警告的根因；保留的依赖建议、平台限制或历史 baseline 说明原因，不扩大基线、
新增 suppression 或降低断言。基线维护见 [CI 流程](../../../ci/README.md#android-lint-基线)。

## 最终构建与提交

```powershell
.\gradlew.bat :app:assembleDebug --no-daemon --console=plain
```

核对 APK 时间、大小、SHA-256、身份、ABI、签名、launcher、ToolPkg 与 native 打包。
16 KiB ZIP 与 ELF `PT_LOAD` 对齐分别验证，命令见 [APK 核验](BUILDING.md#9-独立核验-apk)。

候选提交形成后，以本轮起点与候选 SHA 执行 `check_repo_hygiene.py`、`check_markdown_links.py`
与 `check_localizations.py`，再运行 `check_fresh_clone.py --repository .`，参数见 CI 指南。
fresh clone 检查提交与固定 gitlink，未提交修改不进入验证。

## 证据边界

| 证据 | 可证明 | 仍需分别确认 |
| --- | --- | --- |
| 静态、单元、本地夹具 | 对应约束、分支与离线行为 | 未覆盖场景、真实服务、设备生命周期 |
| Debug APK | 构建与已检查产物属性 | 安装、触控、性能、权限、网络与解码 |
| Git / fresh clone | 候选树、gitlink 与源码可复现性 | 远端 Actions 实际结果 |
| 设备实测 | 指定设备、版本及操作范围的结果 | 其他设备、OEM 与未测环境 |

性能结论需给出输入、前后测量与环境。源码风险修复不自动产生帧率或耗时改善证据；
发布签名、服务部署、真实账号及设备操作使用各自授权与验收。
