# Android Lint 基线历史

本文保留已记录的基线演进，2026-09-12 从 CI 入口迁入并补充本轮交集证据。
版本、数量与摘要只描述各次观察，不是当前报告。现行维护入口见 [CI 指南](../../../ci/README.md#android-lint-基线)。

## 2026-09-12 全仓质量整理

在 `main@b8a0caa1f425da613fd579f957deac3555164ff1` 基础上完成源码、翻译与资源修复后，
使用 AGP 9.3.1 / Gradle 9.5.0 生成 `app/build/lint-baseline-current.xml`。完整结果为
917 errors、4111 warnings、126 hints；其中多数是早已记录的历史问题，不能表述为全仓零诊断。

结构化交集为 `retained=5089 / stale=74 / current-only=65`：

| 删除的失效诊断 | 数量 |
| --- | --- |
| LocalContextGetResourceValueCall | 33 |
| TypographyEllipsis | 14 |
| UnusedResources | 9 |
| AutoboxingStateCreation | 5 |
| SdCardPath / MissingTranslation | 各 3 |
| NewerVersionAvailable / StaticFieldLeak / UseKtx | 各 2 |
| PluralsCandidate | 1 |

与本轮起点独立对比确认新增签名为 0；再次求交集为 stale=0。归一化 SHA-256：
`cb2ead863ecfbe118e2a2043363b3d840b083c78568ac396734a2fbb0e2d2495`。
65 条 current-only 保持可见，不进入 baseline；其保留原因见本轮专项记录。

## 2026-08 及更早记录

初始 baseline 使用 AGP 8.13.2 并启用依赖检查，从上游提交 `1fe3b5eddb1f5c6ed795465f80716dda8c36cc65` 生成，对应 [GitHub Actions 运行](https://github.com/luojiaping/Operit/actions/runs/29661867372)。

2026-07-24 在 AGP 9.3.1 下生成临时完整 baseline，与已审阅 baseline 求交集：原样保留 `5843` 条仍存在的记录，删除 `200` 条失效记录，不吸收 `30` 条当时可见问题。

2026-07-31 的 M-01 只把 6 个 `OperitApplication.kt` location 路径改为 `KiyoriApplication.kt`；2026-08-01 的 M-03 继续把这 6 个 location 路径移到 `com/kiyori/app/KiyoriApplication.kt`，两次均未改变 issue、message、line、column 或条目数量。

2026-08-01 在 M-04 根组合、Shell 与 MainActivity 职责拆分后重新生成完整 lint 结果，通过结构化交集删除当前工具链不再报告的 `51` 条历史记录，保留 `5792` 条原有记录，且不吸收 `328` 条 current-only 问题。

2026-08-02 的 M-05B 把 logger 的早期绑定状态从静态 Android `Context` 收敛为提前解析的 `filesDir: File`；新鲜完整 baseline 证明旧 `AppLogger.kt` 的 1 条 `StaticFieldLeak` 已失效，结构化交集只删除该记录，保留 `5791` 条且不吸收 `316` 条 current-only 问题。

2026-08-02 的 M-05E 把唯一 `SdCardPath` 既有记录从兼容 facade `OperitPaths.kt` 精确迁到唯一 owner `KiyoriPaths.kt`，只更新 location 与同一代码行中的 owner 常量名，不新增、删除或吸收 issue。

2026-08-02 的 QD-03 删除 3 个已无引用且缺少翻译的设置字符串，并把 1 个无引用英文数量字符串移出资源树；临时完整 baseline 为 `5845` 条，结构化交集只删除这 4 条失效记录，保留 `5787` 条原有记录，不吸收 `58` 条 current-only 警报。

2026-08-02 的 QD-04 为 reply-proxy 补齐 `WEB_MESSAGE_LISTENER` 正向 feature guard；临时完整 baseline 为 `5814` 条，结构化交集只删除对应 1 条失效 `RequiresFeature`，保留 `5786` 条，不吸收 `28` 条依赖警报。

2026-08-03 的 QD-05 升级并净化依赖后，fresh lint current-only 为 0；临时完整 baseline 为 `5776` 条，结构化交集删除 7 条已升级版本记录和 3 条失效版本目录记录。

2026-08-03 的 QD-06/QD-07 又完成 Locale、WebKit feature、receiver、Shizuku listener、URI flag、PTY 私有 API、native asset 与 Android 定时器根因清理；最终完整 baseline 为 `5606` 条，交集 `retained=5606 / stale=92 / current-only=0`。

2026-08-09 浏览器设置与 Lint 收口补齐 77 个五语言翻译键、Compose 可观察资源读取、KTX、Modifier、数量文案、无引用资源和 Media3 稳定版本，完整 Lint 为 `0 errors / 0 warnings / 0 hints`；结构化交集只删除 `39` 条失效记录，得到 `retained=5567 / stale=39 / current-only=0`。

2026-08-11 的全仓健康审计继续得到 `retained=5306 / stale=261 / current-only=2`，SHA-256 为 `9ecd07d023005a6732f2f56f110675ec6ac64bfec8c64107e8780b7c42396fe5`。

随后 `main@96259e2a` 的源码修复精确删除 26 条旧记录，baseline 为 `5280` 条，SHA-256 为 `a0950846d4d41eca6d7e3b302b21bd7c8317490fd2d9fb1c88e9ba57fd5cc00e`。

2026-08-14 的全项目整理把依赖声明收口到 version catalog，并删除重复资源；当前临时完整 baseline 为 `5279` 条，结构化交集 `retained=5256 / stale=24 / current-only=23`。24 条 stale 为 `GradleDependency 1 / NewerVersionAvailable 7 / UseTomlInstead 16`，23 条 current-only 继续保持可见且没有进入 baseline。

2026-08-18 的 AI 对话详情实现把 `ChatBackupSettingsScreen` 组合范围内的字符串读取统一改为 `LocalResources`，并同步修正 `MessageEditor` 的可观察资源读取；临时完整 baseline 与已审阅 baseline 的结构化交集为 `retained=5194 / stale=62 / current-only=29`，62 条 stale 全部是上述两个本任务文件的 `LocalContextGetResourceValueCall`，29 条 current-only 保持可见且未被吸收。当前归一化 SHA-256 为 `bec377afcf42f368fcac27c53ad624cdce21625f38ca63e2a8552597d023b097`。

2026-08-29 的 Operit v1.12.1 后续适配先修复当前改动与高风险运行时诊断，再生成构建目录临时完整 baseline。首次结构化交集删除 `29` 条已失效记录，保留 `5164` 条已审阅记录，不吸收 `74` 条当时的 current-only 诊断；随后源码继续清理 renderer 终止、WebKit feature、Compose 状态/资源与无引用多语言资源，第二次结构化交集仅再删除 `1` 条失效 `UseKtx` 记录，最终得到 `retained=5163 / stale=1 / current-only=35`。归一化 SHA-256 为 `b03b05c065fec1e90a0dee9dc70a8515e85ab05fc0526b007683faa10286aa1a`；35 条可见 warning 保持在正式 `lintDebug` 报告中，依赖族升级和同步 `SharedPreferences.commit()` 合同不由 lint 建议机械改写。
