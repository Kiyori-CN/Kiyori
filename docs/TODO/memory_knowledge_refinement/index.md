---
status: verification_pending
---

# 记忆与知识库重构

## 2026-09-15 界面精修轮

起点 `main / 11f12ee8476bc369e9b5b2adf1b8aeb0053216b0`，工作区干净。用户提供两张真机截图，
要求专门优化界面和相关逻辑，授权最终提交推送。截图是现状证据，不代表新界面已经验收。

- 目标：缩减首屏固定操作区，突出内容；统一 Material 主题、图标、排版和文案；完善位置、筛选、阅读与编辑闭环。
- 范围：MemoryLibraryContent、FolderNavigator、MemoryScreen、相关详情/编辑对话框、MemoryViewModel，以及必要的目录逻辑与中英文资源。
- 非目标：不改数据库 schema、Agent 协议、全局主题或 AI 抽屉归属，不升级依赖，不安装或操作设备。
- 设计：顶部类型切换与更多菜单，填充式搜索，位置与筛选同排；低频操作进入菜单；位置/筛选使用标准模态底部面板。
  空库直接提供新建或导入，非空列表只保留一个主操作；卡片先标题与摘要，再压缩后的主题/来源/日期，详情承载技术元数据。
- 交互：搜索草稿与已提交查询分离，明确忙碌和错误状态；目录操作更新子路径选择；文档未保存修改离开前确认；保存期间禁止修改。
- 参考：当前 Kiyori 主题 `KiyoriUiTokens.kt` 与本轮截图；本地 Operit `f323d6c50fa661837fad06d4618462861779b562`
  的 `MemoryAppBar.kt` 核对主题使用。页面组合按本轮用户要求重新设计，不照搬旧多排工具栏。
- 风险与回滚：关注筛选范围、草稿丢失、模态 Back、窄屏和大字体；回滚本轮提交即可恢复上一轮，持久数据格式不变。
- 验证计划：对目录与查询状态选择针对性测试，静态审阅布局/资源/差异，串行 Debug APK 构建，文档与候选提交检查；
  深浅色、键盘、TalkBack、横屏和真机视觉保持 `verification_pending`，不以编译代替视觉证据。

本轮实现已完成：移除未使用的旧搜索/顶栏入口；首页三层结构、位置/筛选底部面板、内容卡片、
阅读详情和编辑表单均复用既有 Material/Kiyori token。空文件夹保留添加入口，标签输入未点加号也随保存提交。
导入目标在打开系统选择器前保存空间 ID 与目录，并支持 Activity 重建后恢复目标；处理中显示进度。
普通详情提供归档/恢复与删除菜单，文档详情补齐草稿离开确认、保存锁与归档前草稿约束。
目录删除不删除正文，而是用一次事务解除目录及子目录归属；重命名子路径选择按完整路径段更新，避免匹配相似目录名。

2026-09-15 本地验证结果：

- 专项测试 22 项通过、0 失败/错误：MemoryLibraryPolicy 12、MemoryObjectBoxCompatibility 2、
  MemoryRepositoryCompatibility 5、MemoryUiPolicy 3。新增覆盖父/子目录重命名与删除后文档正文保留、相似目录名隔离。
- 命令：`gradlew.bat :app:testDebugUnitTest --tests '*MemoryUiPolicyTest' --tests '*MemoryRepositoryCompatibilityTest' --tests '*MemoryObjectBoxCompatibilityTest' --tests '*MemoryLibraryPolicyTest' --no-daemon --console=plain`。
- 最终 `gradlew.bat :app:assembleDebug --no-daemon --console=plain` 成功，耗时 1m41s；238 个任务，27 执行。
  Debug 单启动器与脚本代理/播放器打包检查通过。最后的空文件夹入口与导入恢复调整包含在最终 APK 中。
- `check_documentation.py --repository . --base 11f12ee8476bc369e9b5b2adf1b8aeb0053216b0` 检查 525 文件、0 问题；
  `check_formal_readiness.py --repository . --require-main` 通过。中英文记忆库资源 87 键一致，21 文件精确允许清单审计通过。
- APK：`app/build/outputs/apk/debug/app-debug.apk`，生成于 02:46:36 +08:00，487117295 字节；
  `com.kiyori` / `0.1.0` / code 45，min SDK 26、target SDK 34，`arm64-v8a`。
- SHA-256：`097A47A8C7E9B3AFB6AB3131C56EEE282EBB88B5333EE4C6E23A063F2C3E02E4`；
  `apksigner verify --verbose` v2 有效、1 个签名者；`zipalign -c -P 16 4` 通过。
- 未运行安装、设备操作、真实模型、Release 或远端 CI；真实视觉、键盘、横屏、TalkBack 和 Back/草稿恢复保持 `verification_pending`。
  下一步使用此 APK 覆盖安装并按上述矩阵验收；不能把本地编译和 native 测试等同于真机通过。

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
