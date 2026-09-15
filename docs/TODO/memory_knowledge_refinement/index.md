---
status: verification_pending
---

# 记忆与知识库重构

## 2026-09-15 日记内容类型引入轮

起点 `main / bb1bcfb5d`，工作区带上一轮遗留的日记分段草稿；用户授权实现、构建、提交推送。

- 目标：把记忆库拆成「记忆 / 日记 / 知识」三个内容类型；日记支持一天多篇、按时间线续写与结束，
  并在 Agent 侧提供只追加的写入工具。
- 范围：`MemoryDiaryPolicy`（新增）、`MemoryLibraryPolicy`、`MemoryRepository` 的创建/更新/追加路径、
  `MemoryViewModel` 与记忆库页面、日记详情与卡片、筛选面板、目录抽屉、`MemoryQueryToolExecutor`、
  工具提示与注册、`JsTools.Memory`，以及中英文资源。
- 非目标：不改 ObjectBox schema、备份格式与检索打分算法；不新增第二套存储；不安装或操作设备，不调用真实模型。
- 关键决定：
  - 日记正文内联保存结构，不新增实体。备份、导出、嵌入和检索因此沿用同一条正文路径，
    旧库与旧备份不需要迁移；代价是单篇正文会持续变长，所以单次追加有 20000 字符上限并明确拒绝超限。
  - 小节标题里的阶段是与界面语言无关的稳定键，界面渲染时才翻译；写入本地化文案会让历史记录在切换语言后含义漂移。
  - 完结状态由「最后一节是否为 `closed`」推导，不新增状态字段，避免出现和正文对不上的第二份事实。
  - 上一轮遗留的日记分段草稿在仓库层会被 `require(libraryKind in [memory, knowledge])` 拒绝，
    本轮改为统一的 `MemoryLibraryPolicy.requireWritableKind`，类型表只有一份。
  - 续写走与普通编辑相同的写入路径，复用已有的并发校验、重新嵌入和索引失效；
    但追加前重新读取库中正文，否则两次续写之间会丢掉先写入的那条。
- 风险与回滚：关注长日记的正文规模、时间线渲染性能、键盘遮挡续写区、旋转后的草稿保留，
  以及模型把 `append_diary` 当成改写工具。回滚本轮提交即可，持久化格式未变，
  已写入的日记在回滚后仍是可读的普通记忆正文。
- 验收：本地源码审阅、日记结构单测、Debug APK 与候选树/远端 ref；
  真机视觉、TalkBack、横屏、大字体、IME 与真实模型的工具调用保持 `verification_pending`。

同轮完成的界面细节：

- 分段控件由两段改三段，去掉选中打勾图标并压缩字号，窄屏下三个标签不被截断。
- 搜索占位文案按内容类型区分，不再用一句话覆盖三种内容。
- 筛选面板按类型给出条件：日记显示进行中/已完结，其余显示主题；顶栏筛选徽标计入日记状态。
- 目录抽屉的目录菜单新增「新建子文件夹」，建嵌套目录不再需要手打完整路径。
- 日记详情的续写草稿改为仅在追加成功后清空；此前的写法会在旋转屏幕时吞掉已恢复的草稿。

### 本轮实际验证结果

2026-09-15 本地验证（串行执行，均通过）：

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests '*MemoryDiaryPolicyTest' --tests '*MemoryLibraryPolicyTest' --tests '*MemoryUiPolicyTest' --tests '*MemoryExportCompatibilityTest' --tests '*MemoryRepositoryCompatibilityTest' --tests '*MemoryObjectBoxCompatibilityTest' :app:assembleDebug --no-daemon --console=plain
.\.venv\Scripts\python.exe -B ci/script/check_documentation.py --repository . --base bb1bcfb5d
.\.venv\Scripts\python.exe -B ci/script/check_formal_readiness.py --repository . --require-main
.\node_modules\.bin\tsc.cmd --noEmit --skipLibCheck --target ES2020 examples/types/memory.d.ts examples/types/results.d.ts
```

- 新增 `MemoryDiaryPolicyTest` 9 项：旧纯文本落入开篇、追加保留全部历史、完结由末节推导、
  空正文仅收尾可用、未知阶段保留原文、摘要跟随最新有内容记录、超长拒绝、
  尾部扫描的 `status` 与完整 `parse` 结果一致、三种可写类型校验。
- `:app:compileDebugKotlin` 无本轮新增警告；首轮出现的 `Icons.Outlined.EventNote` 弃用提示已改用
  `Icons.AutoMirrored.Outlined.EventNote` 消除。
- 文档检查 525 文件、0 问题；正式开发准备检查 PASS；`memory.d.ts` / `results.d.ts` 类型检查通过。
- `extended_memory_tools.js` 的 METADATA JSON 与脚本体分别用 `JSON.parse` 和 `new Function` 解析验证。
- 未运行：单元全量、Lint、Release 构建、设备安装与真实模型的工具调用。
  `memory_library.xml` 仍只提供 `values` 与 `values-en`，本轮新增 40 个 key、移除 1 个被按类型文案取代的 key，沿用该模块既有约定，
  其余 5 个语种的缺失与基线一致，不在本轮机器生成。

### Debug 产物

2026-09-15 最终串行 `:app:assembleDebug` 成功，用时 5 分 54 秒。
标准路径 `app/build/outputs/apk/debug/app-debug.apk`，487,148,155 字节。

SHA-256：`28810c1d583eb04e66c847a0237ffe60b38e396058738aec98141d93fae5d1b8`。
产物信息只描述该次构建，不代表设备或远端 CI 验收。

### 已知限制

- 日记正文随续写单调增长，长期使用的单篇日记会让每次追加重算整篇嵌入；
  当前只有单条 20000 字符上限，没有整篇上限或分段落盘，长库表现待现场观察。
- 时间戳按写入时的设备本地时间落盘，跨时区补写会显示为当时设备的时间；不做二次换算是刻意选择。
- 日记的完结状态、分组日期与摘要都来自正文解析；用户手工改写全文后，这些派生显示会随之改变。
- 续写区在小屏 + 输入法弹出时依赖 `imePadding`，真机 IME、横屏与大字体表现保持 `verification_pending`。

## 2026-09-15 文件夹模式与工具缺陷修复轮

起点 `main / 37672dd7793d633df73a1fc13b70ee3103001d18`，工作区干净；用户授权实现、构建、提交推送。
用户提供了一份 43 用例的记忆工具真机测试报告（D1 同名死锁、D2 报错与入参矛盾、D3 可信度/重要性不可读回、
D4 自环未校验、D5 空匹配移动报失败），本轮把报告结论当作缺陷输入，不当作已验收结论。

- 目标：修复目录身份与同名条目的结构性问题；把非图谱模式改成可逐级进入/回退的文件夹模式；
  同步修复报告中的五项工具缺陷，并完成用户点名的若干界面细节。
- 范围：`MemoryRepository` 目录与创建/更新校验、`MemoryQueryToolExecutor`、`JsTools.Memory`、
  `extended_memory_tools` 包、记忆库首页与位置抽屉、记忆与检索设置抽屉页脚，以及中英文资源。
- 非目标：不改 ObjectBox schema、备份格式、检索打分算法与其他领域；不安装或操作设备，不调用真实模型。
- 关键决定：
  - 「未分类」不再是目录。旧实现把本地化文案当路径返回给树、下拉框和工具参数，
    切换界面语言会改变目录身份。现在根用空路径表达，工具侧显式标识是与语言无关的 `(root)`。
  - 目录列表补全中间层级，逐级浏览不会缺层；目录占位记忆改用稳定 `source` 标识，不再只靠本地化标题识别。
  - 同一目录内标题唯一，创建和改名/移动在写入前校验并返回已存在条目的 UUID；
    同名定位报错同时列出候选 UUID，配合工具包新增的 `uuid` / `uuids` 入参解除 D1 死锁。
  - 目录创建失败改为抛出真实原因，不再吞异常返回 false；删除根目录明确拒绝。
- 风险与回滚：关注深层目录、长路径、窄屏面包屑、Back 归属与空目录展示；
  历史数据若曾把「未分类」写成真实路径，会显示为同名普通目录，可用目录删除把内容移回根目录，内容不丢。
  回滚本轮提交即可恢复上一轮，持久化格式未变。
- 验收：本地源码审阅、目录浏览纯逻辑单测、Debug APK 与候选树/远端 ref；
  真机视觉、TalkBack、横屏、大字体与工具回归重跑保持 `verification_pending`。

同轮完成的界面细节（用户逐条点名，均不改动所在行的行高）：

- AI 对话第二栏左侧两枚图标改为会话（Forum）与小窗（PictureInPictureAlt）并常亮语义色，灰色只留给禁用态；
  右侧统计入口圆环内显示取整到个位的上下文占用读数，面板仍保留一位小数。
- 浏览器顶栏刷新与返回统一使用 chrome 中性墨色，触控尺寸保持 48dp，栏高不变。
- 文件管理首页「存储位置」四个入口的图标徽标由 22/14dp 放大到 32/20dp，仍小于 48dp 行高下限。
- 文件管理器顶栏「筛选」与「排序」分属 ORGANIZE / 新增的 ARRANGE 语义色，相邻图标不再同色。

## 2026-09-15 顶栏与抽屉统一轮

起点 `main / c583ef0d1ceb265bed2cfb1ca7858baa8a76e7dc`，工作区干净；用户授权实现、构建、提交推送。

- 目标：标题右侧固定“关系图谱 / 筛选 / 设置 / 刷新”；文件夹位于类型切换左侧，搜索独占下一行。
- 参考：当前 `AIChatScreen` 的顶栏动作、`KiyoriUiTokens` 和语义彩色图标；浏览器历史使用的
  `KiyoriModalBottomDrawer` / `KiyoriDraggableBottomDrawer`，统一三态、遮罩、安全区和退出动画。
- 范围：记忆库首页、文件夹和筛选抽屉、相关筛选状态与无障碍；保留原有编辑/确认对话框的职责。
- 非目标：不改数据库、Agent 协议、主题 token 或其他领域；不安装或操作设备，不调用真实模型。
- 阶段：核对参考与状态调用链 → 顶栏/布局/抽屉实现 → 针对性状态回归、文档与串行 Debug 构建 → 审计并推送 main。
- 风险与回滚：关注窄屏长目录、大字体、抽屉可见高度、关闭动画、后台页面动作与空间切换；
  回滚本轮提交恢复原布局，不涉及数据迁移。
- 验收：本地源码审阅、筛选状态回归、Debug APK 与候选树/远端 ref；真机视觉、TalkBack、拖动、横屏与键盘保持 `verification_pending`。

本轮实现覆盖四个彩色顶栏动作、两行内容导航、位置与筛选的共享三态抽屉，以及关闭动画和可见高度。
筛选重置保留目录与搜索草稿；已选标签不因零匹配而消失；空间变化清除旧菜单目标，目录忙碌时禁止操作。
最终反向审阅发现图谱筛选后旧框选 ID 仍可进入批量删除，现由查询启动统一清理框选/关联和旧删除确认；
图谱加载期间隐藏旧图谱及相关操作，范围变化后必须重新选择。相关状态和共享抽屉契约均纳入本轮回归。

2026-09-15 本地验证：

- `:app:testDebugUnitTest --tests '*MemoryUiPolicyTest' --tests '*KiyoriBottomDrawerMigrationContractTest' --tests '*KiyoriModalBottomDrawerContractTest' --no-daemon --console=plain`
  通过：7 项、0 失败/错误；其中 MemoryUiPolicy 5 项、全应用抽屉迁移 1 项、模态宿主契约 1 项。
- `check_documentation.py --repository . --base c583ef0d1ceb265bed2cfb1ca7858baa8a76e7dc` 检查 525 文件、0 问题；
  `check_formal_readiness.py --repository . --require-main` 通过；12 个文件精确允许清单、UTF-8/LF、凭据模式和子模块审查通过。
- 未安装或操作设备。真机视觉、手势、TalkBack、键盘、大字体和横屏保持 `verification_pending`。
- 最终 `:app:assembleDebug --no-daemon --console=plain` 成功，43 秒、238 任务（23 执行）；
  单启动器、脚本代理与播放器打包检查通过。最终 APK 包含本轮图谱选择失效修复。
- APK：`app/build/outputs/apk/debug/app-debug.apk`，2026-09-15 03:29:59 +08:00，487117295 字节；
  `com.kiyori` / `0.1.0` / code 45，min SDK 26、target SDK 34、`arm64-v8a`。
  SHA-256：`A099DCCF3ED222DFA48FA2B126A8AB2ECF474CCC79BD4ACAFD5142E1BBD6AED9`。
  `apksigner verify --verbose` v2 有效、1 个签名者；`zipalign -c -P 16 4` 通过。

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

## 2026-09-15 系统优化轮

- 基线：`219043fbdabb256da32c87e67e50448171dccfce`，`main`，工作区干净。
- 目标：记忆/知识与 AI Tool 一致性优先；统一语义顶栏与固定 Footer 抽屉，补齐文件夹排序持久化。
- 阶段：数据与工具审计 → 公共组件及页面接线 → 持久化与并发修复 → 回归、Lint、Debug APK → 精确提交推送。
- 范围：既有 Repository、ViewModel、偏好、页面及相关契约；不更改协议身份、设备、服务或依赖版本。
- 风险及回滚：保护现有库与稳定 UUID；配置只新增兼容字段；按本轮提交可回退代码。
- 验收：针对性行为测试、工具契约、静态与文档检查、Lint；设备视觉、IME、手势与真实 AI 调用保持 `verification_pending`。

### 本轮实际验证结果

- `:app:compileDebugKotlin` 通过，变更文件无新增编译警告。
- `:app:testDebugUnitTest` 通过：2981 项，0 失败，1 跳过。其中新增 12 项（文件夹排序作用域 8 项、文档编码回退 4 项）。
- `FileManagerPreferencesTest` 中一条断言按新的排序作用域契约重写：全局默认现在会作用到没有局部覆盖的目录，快捷排序写的是当前路径的覆盖而非全局值。
- `:app:lintDebug` 失败，唯一未过项为 `MissingTranslation`（104 条，全部位于 `memory_library.xml`）。
  该模块自建库起只提供 `values` 与 `values-en`，缺 es/id/ko/ms/pt；基线提交 `219043fbd` 的文件构成相同，
  且 `library_memories` 等 key 不在 `app/lint-baseline.xml` 中，因此 **基线同样无法通过 lintDebug**，本轮未引入该失败。
  其中 87 条为既有 key，17 条为本轮新增 key（沿用该模块既有的 zh+en 约定）。
  本轮修复了唯一一条可归因于本次改动的 lint 错误（`LocalContextGetResourceValueCall`）。
- 未运行：Release 构建、APK 打包签名、设备安装、真机视觉/IME/手势验证、真实模型的 Tool 调用。

### 已知限制

- `memory_library.xml` 仍缺 5 个语种，需要人工或本地化流程补齐，不适合在本轮机器生成。
- 导入使用非持久化的 `OpenMultipleDocuments` URI 授权，进程重建后「重试失败资料」可能因授权失效而再次失败。
- 语义检索首次会顺带补写文档索引路径，可能多触发一轮列表刷新；补写后条件不再成立，不会持续自激。
