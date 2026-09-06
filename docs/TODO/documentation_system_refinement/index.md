---
status: in_progress
updated: 2026-09-06
---

# 文档体系整理与开发入口优化

## 目标与范围

整理父仓库维护的全部开发文档，重点重构根 README、CONTEXT、AGENTS 与 docs。中文作为开发文档主语言；英文用户入口与明确用于协议、测试、上游归属的原文保留其用途。同步审查并交付任务开始时已有的开源许可页面、测试及准备检查改动。

不改变无关产品行为，不修改子模块和第三方原始文档，不执行设备安装或正式发布。

## 基线与问题

- 分支：`main`；起点：`145f378900d663b812d9df3c73de15fbad56139c`。
- 已有修改：AGENTS、两份开源许可页面、一份许可测试及 `check_formal_readiness.py`；用户已明确授权合并审查、验证并提交。
- CONTEXT 混用语言、超长段落和阶段证据，不适合默认上下文注入。
- TODO 总索引超过六千行，重复承载详细历史与当前状态，检索和维护成本过高。
- README 堆积内部契约，用户上手路径被实现细节打断；AGENTS 存在重复和冲突规则。

## 实施计划

| 阶段 | 工作 | 验收 | 状态 |
| --- | --- | --- | --- |
| 01 调查 | 全量文档清单、格式与链接基线、引用和源码事实核对 | 区分自有文档、协议样例、子模块及第三方材料 | 已完成 |
| 02 入口 | 重写 README、精简 CONTEXT、规范 AGENTS | 用户路径完整、职责不重复、规则无冲突 | 已完成 |
| 03 分层 | 提取领域契约、拆分 TODO 历史、补齐分类导航 | 重要契约与验收证据可追溯，旧引用同步迁移 | 已完成 |
| 04 一致性 | 统一标题、排版、术语，修正失效链接与文档检查缺陷 | 全量自有文档扫描无新增缺陷，检查器回归通过 | 已完成 |
| 05 验证交付 | 审查许可修复、串行 Debug 构建、产物核验、提交推送 | 精确变更清单、父仓与远端 ref 一致 | 进行中 |

## 决策与风险

- 保留已被代码、检查脚本和外部链接使用的稳定目录名；通过分层索引改善结构，迁移必要的超长入口。
- 详细契约迁入正式主题文档；历史计划与验收证据留在 TODO，不能因整理而标记成已完成。
- 文档格式化必须识别代码围栏、协议示例与许可证原文，避免修改机器消费的样例语义。
- 许可归属允许显示上游名称，但检查器不得因此放行整份文件中的产品身份或业务 URL 错误。
- 恢复基点为上述 Git 提交和任务开始时的工作区快照；不使用破坏性重置。

## 已交付内容

- 重写中英文用户入口，新增首次使用、扩展、网络媒体与隐私指南。
- CONTEXT 收敛为 63 行高频契约，七份领域文档承接详细所有权与生命周期；AGENTS 消除重复与发布状态冲突。
- TODO 总入口收敛为 54 行导航；122 个旧章节按日期保留，规范化相对链接和空白后正文对账无差异。
- 正式开发准备分离当前清单与历史 APK/门禁证据；架构控制面、播放器、示例与工具说明统一中文正文。
- 修复断链、示例文件缺失引用、供应商显示名错误、Windows 命令路径和错误的 Operit 上游检查基线；接口示例转向真实类型权威入口。
- 新增确定性全文档/专项目录与检查器；修复 HTML 标题提取、围栏隔离、空白行与非法 UTF-8 处理，保留协议样例和第三方原文。
- 许可页补齐四个项目归属；品牌与 URL 例外限定到已审阅的精确语句，普通业务源码继续检查。

## 验证记录

已取得的验证记录（2026-09-06）：

- 文档检查：`.\.venv\Scripts\python.exe -B ci/script/check_documentation.py --repository . --write-catalogs`，扫描 491 个文件，0 个问题；独立核对 8 处显式 Markdown 本地锚点，无失效目标。
- Python 回归：`.\.venv\Scripts\python.exe -B -m unittest ci.test.test_documentation ci.test.test_formal_readiness ci.test.test_markdown_links`，41/41 通过。
- 正式准备：`.\.venv\Scripts\python.exe -B ci/script/check_formal_readiness.py --repository . --require-main`，PASS。
- 许可 JVM：`.\gradlew.bat :app:testDebugUnitTest --tests com.ai.assistance.operit.ui.features.about.screens.OpenSourceLicensesTest --no-daemon --console=plain`，BUILD SUCCESSFUL；测试任务复用 UP-TO-DATE 结果，2026-09-06 XML 记录 4 个测试、0 失败/错误/跳过。
- 最终 Debug：`.\gradlew.bat :app:assembleDebug --no-daemon --console=plain`，`BUILD SUCCESSFUL in 1m 44s`，235 个任务中 24 个执行、211 个 up-to-date；launcher、脚本代理运行时和播放器运行时打包门禁通过。
- APK：`app/build/outputs/apk/debug/app-debug.apk`，写入时间 `2026-09-06 14:17:24 +08:00`，502,520,560 bytes，SHA-256 `55A38A0C25B3FD7C33547D22C38F695325C2A6AA0162B28D2DD46EB13850B908`；`com.kiyori / 45 / 0.1.0 / minSdk 26 / targetSdk 34 / compileSdk 37`、仅 arm64、V2 单 signer 与 16 KiB ZIP 对齐通过。
- 候选提交 Markdown 链接检查、fresh clone 检查和远端 ref 对齐在提交形成后执行并回填；设备、远端 Actions 和正式发行仍不属于本轮关闭范围。

设备、远端 Actions 和正式发行的历史待验收状态不由本轮文档整理关闭。
