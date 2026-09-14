---
status: verification_pending
---

# 记忆与知识库重构

## 任务契约

2026-09-15 首轮开始时基线为 `main` / `5823c60ab39de48e8b198f38e0433db657c131b4`，工作树干净。
同日第二轮接续该基线上的未提交重构并处理现场崩溃，保留既有实现后整体审阅交付。
用户授权深度研究、先设计后实现，最后提交推送。目标覆盖 AI 抽屉记忆入口的 UI、整理、
存取、检索、向量生命周期、稳定性与性能。设计见[记忆与知识架构](../../doc-src/architecture/memory_knowledge.md)。

不改包名、角色卡记忆空间绑定或其他产品领域；不新增远端数据库、第二存储所有者或模型回退；
不安装/操作设备，不调用用户真实模型服务进行验收。根 README 的能力概述仍成立，本轮更新详细指南。

## 阶段计划

| 阶段 | 交付及主要影响 | 验收 | 状态 |
| --- | --- | --- | --- |
| 1 研究设计 | 调用链、公开设计来源、产品边界、失败模式和实现方案 | 源码与公开资料入口见架构文档 | DONE |
| 2 存储与检索 | MemoryRepository、派生向量身份、分块、类型及归档、备份兼容 | native 旧字段读取、分块、向量兼容、备份与范围回归通过 | DONE（本地） |
| 3 Agent 闭环 | 现有工具类型、UUID、来源及自动提取失败传播 | 协议回归及 TypeScript 声明；真实模型效果待验证 | 实现完成 |
| 4 管理界面 | 列表优先、类型/主题/标签/归档、按需索引统计、草稿与独立保存状态 | 编译与源码审阅；设备视觉和交互待验证 | verification_pending |
| 5 交付 | 专项回归、文档、串行 Debug APK、精确树审计、提交推送 | 本地构建核验通过；提交与远端 ref 以当次 Git 交付核验为准 | DONE（本地），现场待验证 |

## 风险与回滚

- ObjectBox 继续作为唯一事实源；新增字段保持默认值，旧条目根据原文档标记分类。
- 原始记录和知识内容必须先可持久恢复，派生索引失败不可伪装为全部成功。
- 模型标识不同即使维度相同也不兼容；旧向量来源未知必须显式标为待重建。
- 自动提取的内容是历史证据，不是新的操作授权；不自动按相似度删除用户记忆。
- Git 回滚点为上述基线；数据增加保持兼容，不以回退旧二进制验证新索引语义。
- 设备视觉、手势、真实大库 PSS/耗时及模型检索质量均为 `verification_pending`。

## 当前证据

### 崩溃修复与第二轮优化计划

2026-09-15 接续同一基线上的未提交重构，用户报告 `Memory.<init>` 的 `libraryKind` null 崩溃。
本轮保留既有改动，先修复 ObjectBox 实际读取兼容，再完善列表、编辑与异步错误状态，最后整体交付。

1. 新增字符串持久化字段接受旧记录缺失值，由统一策略解释类型、主题和向量有效性；保持属性 UID。
2. 使用本地 ObjectBox native 读写/重开测试覆盖缺失元数据，补齐旧格式、文档、向量回归。
3. 隔离索引统计错误与页面查询，避免未处理后台异常；统计复用配置快照并分批读取。
4. 优化筛选重置、来源/标签与索引状态展示、保存草稿和重复点击；保持列表优先与可选图谱。
5. 更新架构与指南，验证专项测试、文档、最终 Debug APK；审计当前所有相关差异后提交推送 main。

回滚沿用基线提交；不清空数据库，不运行设备或真实模型验收。现场旧库升级仍为 `verification_pending`。

### 第二轮实现与验证边界

已修复首轮新增字段的 ObjectBox 构造器 null 崩溃，补充文档分块同类字段兼容；
旧属性 UID、ID、UUID 和关系保持。普通保存、编辑和导入在事实提交前处理索引失效，
避免数据库已写入后返回失败导致重试重复创建；新建标签随主体事务持久化。

列表展示文件夹、标签、来源与明确的本地/语义状态；重置筛选保留当前类型页。
新建默认继承当前文件夹，编辑关闭前确认未保存修改；保存状态与搜索分离。
索引统计从首屏移到设置按需加载，每批 128 项并复用配置；错误独立呈现并可重试。
标签参与基础词法召回，文档通配查询保持片段顺序；搜索模拟传递类型和归档范围。

最终 38 项专项测试全部通过：提取窗口 7、提取协议 8、备份序列化 2、领域策略 12、
ObjectBox native 缺字段兼容 2、Repository 真实数据库回归 4、HNSW 持久化 3。
Repository 覆盖 260 条记忆/130 个区块的跨批统计、新建编辑与标签/归档、
旧文档备份往返、正文/标签检索和类型/归档/文档通配范围。
曾发现缺失 TextButton 导入和测试桩问题，均已修复并由最终整轮通过覆盖；未禁用检查。
文档检查 525 文件、0 问题；TypeScript 声明检查和正式开发准备检查通过。
精确交付清单 42 个文件，旧 schema 属性保持，无异常大文件或高置信凭据命中；
测试解包的桌面 ObjectBox JNI 已明确忽略，不进入交付。`terminal` 无修改，远端 main 无分歧。

设备旧库覆盖安装、视觉/Back/键盘/无障碍、真实模型效果和大规模库性能保持待验证。

### 验证命令

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests '*MemoryObjectBoxCompatibilityTest' --tests '*MemoryRepositoryCompatibilityTest' --tests '*MemoryLibraryPolicyTest' --tests '*MemoryExportCompatibilityTest' --tests '*MemoryAnalysisProtocolTest' --tests '*ChatMemoryWindowPlannerTest' --tests '*VectorIndexPersistenceTest' --no-daemon --console=plain
.\.venv\Scripts\python.exe -B ci/script/check_documentation.py --repository . --base 5823c60ab39de48e8b198f38e0433db657c131b4
.\.venv\Scripts\python.exe -B ci/script/check_formal_readiness.py --repository . --require-main
.\node_modules\.bin\tsc.cmd --noEmit --skipLibCheck --target ES2020 examples/types/memory.d.ts examples/types/results.d.ts
.\gradlew.bat :app:assembleDebug --no-daemon --console=plain
```

本轮未运行无关单元全量、Lint 或 Release；不安装、不操作设备、不调用真实模型进行验收。

### Debug 产物

2026-09-15 最终串行 `:app:assembleDebug` 成功，用时 1 分 15 秒。
标准路径 `app/build/outputs/apk/debug/app-debug.apk`，487,100,891 字节；
`com.kiyori`、`versionCode=45`、`versionName=0.1.0`、min SDK 26、target SDK 34、`arm64-v8a`。

SHA-256：`F29C1BC0232E4AACF17FDE62B9D405876D37DC9CD10D0DC2606D3FED45060AC9`。
`apksigner verify --verbose` 通过（v2，1 个签名者）；`zipalign -c -P 16 4` 通过。
中英文记忆库资源 53 个 key 一致。产物信息只描述该次构建，不代表设备或远端 CI 验收。
