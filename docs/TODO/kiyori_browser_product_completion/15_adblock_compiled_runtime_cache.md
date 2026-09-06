---
document_type: implementation-plan
status: verification_pending
last_updated: 2026-09-03
source_baseline: main@4926f7a81b67f35225eea37991e108c9d8825056
---

# 广告拦截编译快照与启动缓存方案

## 1. 文档状态与目标

本文由只读源码研究方案转为正式实现与验收记录。当前本地实现已经接入 Kotlin 运行时、设置页状态、
定向测试、`CONTEXT.md` 和浏览器专项文档；未修改 Gradle 依赖、CI 语义或 Browser Runtime
所有权，也未执行设备安装、ADB、真实订阅下载或目标设备性能验收。

目标是解决“设置－广告拦截器”五条内置订阅在每个应用进程启动后都会重新读取、解析和编译的问题：

- 第一次取得有效规则文本时完成一次完整解析与编译；
- 后续应用重启在规则内容和编译合同均未变化时，只校验并加载本地编译快照；
- 某一条订阅内容变化时，只重建该订阅的编译分区；
- 总开关、站点白名单和订阅启停只重建轻量 matcher 配置，不重新处理订阅规则；
- 保持唯一 `BrowserAdBlockStore`、唯一 Browser Runtime、不可变 matcher 和
  `shouldInterceptRequest` 后台线程只读合同；
- 不增加第二广告引擎、第二设置 owner、旧 native ABP 运行时或并行规则来源。

结论：**可以实现**。进程结束后 JVM 对象本身无法保留，因此不能直接保存活的 `Regex`、锁、LRU
或 Kotlin 对象图；可以保存有版本、可校验的“编译语义记录 + 索引分区”，下一次启动只进行二进制
解码、必要的 JVM `Regex` 实例重建和轻量对象装配，不再逐行解析规则文本，也不再重新计算 host/token
索引。

## 2. 当前实现的已验证事实

### 2.1 广告运行时确实位于软件启动主流程

当前启动调用链为：

1. `KiyoriApp.kt:114-117` 在 App 组合时取得 `BrowserPresentationCoordinator`；
2. `BrowserPresentationCoordinator.kt:70-77` 构造时通过 `ToolGetter` 取得共享
   `StandardBrowserSessionTools`；
3. `StandardBrowserSessionTools.kt:192-203` 的初始化逻辑订阅 `adBlockStore.state`；
4. 访问 `adBlockStore` 会创建 `BrowserAdBlockStore`；
5. `BrowserAdBlockStore.kt:285-288` 立即在 IO scope 启动 `initializeRuntime()`。

因此，即使用户没有进入“广告拦截器”设置页，应用主界面建立共享 Browser Runtime 时也会开始广告
规则初始化。重任务已经不在主线程执行，但仍会与应用冷启动阶段争用 CPU、磁盘、堆内存和 GC。

### 2.2 实施前只持久化状态元数据与原始文本

`source_baseline` 对应的实施前持久化结构为：

- `filesDir/browser_ad_block_state.json`
  - schema 为 `2`；
  - 保存总开关、自动更新开关、白名单、自定义规则、订阅元数据和规则数量；
  - 不保存订阅文本摘要、编译器合同版本、编译快照位置或编译快照摘要；
- `filesDir/browser_ad_block_subscriptions/<subscription-id>.txt`
  - 使用 `AtomicFile` 保存原始订阅文本；
  - 五条内置订阅使用稳定 ID；
  - 状态中的 `hasLocalRules` 只根据已记录的规则数量判断。

相关源码：

- `BrowserAdBlockStore.kt:1371-1405`：原始订阅载荷读写；
- `BrowserAdBlockStore.kt:1424-1570`：schema v1/v2 读取与迁移；
- `BrowserAdBlockStore.kt:1645-1727`：schema v2 状态写入；
- `BrowserAdBlockSubscriptionCatalog.kt:17-54`：五条内置订阅及稳定 ID。

### 2.3 实施前每次进程启动都会完成整条解析、编译和索引构造链

实施前的 `BrowserAdBlockStore.initializeRuntime()` 对所有 `hasLocalRules` 的订阅执行：

1. 读取完整 `.txt` 载荷；
2. UTF-8 转换为 `String`；
3. `parseBrowserAdBlockSubscription()` 逐行扫描；
4. 解析阶段为每条候选规则调用一次编译函数进行有效性判断；
5. `BrowserAdBlockCompiledRuleSet.compile()` 再次把有效规则编译为运行时结构；
6. `BrowserAdBlockEngine.compile()` 将全部订阅规则展平，构造 host/token/unindexed 网络索引、
   元素域名索引和 `badfilter` 集合；
7. 创建 matcher 并一次性发布。

相关源码：

- `BrowserAdBlockStore.kt:859-988`：每次启动的完整初始化；
- `BrowserAdBlockPolicy.kt:497-577`：订阅逐行解析与有效性编译；
- `BrowserAdBlockPolicy.kt:128-188`：规则集编译；
- `BrowserAdBlockPolicy.kt:192-258`：Engine 编译与组合；
- `BrowserAdBlockPolicy.kt:791-921`：网络索引和 token bucket 构造；
- `BrowserAdBlockPolicy.kt:1018-1103`：元素索引构造。

实施前的 `BrowserAdBlockStartupContractTest` 只保证重任务异步化、初始化期间请求线程不等待，
以及完成后发布不可变 matcher；本轮已经补入“有效缓存命中时禁止进入解析/编译路径”的合同。

### 2.4 当前运行时已经具备按分区组合的基础

`BrowserAdBlockEngine.combine()` 会连接多个网络索引分区和元素索引分区，并在 matcher 内统一处理：

- `important` 阻断与例外；
- 普通例外与普通阻断；
- 跨订阅 `badfilter`；
- 元素规则和元素例外；
- 激活规则集 ID；
- 页面策略和站点白名单。

现有 `BrowserAdBlockPolicyTest` 已覆盖跨分区规则优先级、`badfilter` 和元素例外。后续可以把每条订阅
保存为独立编译分区，然后复用现有 `combine()` 语义，无需建立第二套匹配逻辑。

### 2.5 已知性能事实与证据边界

现有专项文档记录的历史同机 JVM 探针显示，五条实际列表约包含 `124119` 条网络规则和 `65647`
条元素规则；当前聚合索引阶段的编译堆增量曾记录为 `164696560` bytes。该证据说明启动编译规模
很大，但它不是当前目标 Android 设备的冷启动墙钟、PSS、GC 或掉帧实测。

本轮已增加逐订阅 cache hit/miss/invalid 和实际编译数量；可重复的 Android 阶段耗时、分配量、
PSS、GC 与首屏帧指标仍需在目标设备采集，不能仅凭历史 JVM 探针宣称现场性能问题已经解决。

## 3. 根因结论

根因不是“每次启动错误地下载五条规则”，而是持久化边界停在原始文本：

- 进程结束后，内存中的 `BrowserAdBlockCompiledRuleSet`、`BrowserAdBlockEngine` 和 matcher 消失；
- 状态 schema v2 没有足以证明“原始内容和编译语义均未变化”的缓存键；
- 启动时只能重新读取文本并重建完整运行时；
- 当前订阅刷新即使下载内容逐字节相同，也会重新解析、编译、重建聚合订阅 Engine，并递增
  `ruleRevision`；
- 当前聚合订阅 Engine 会把全部订阅规则重新展平，因此单条订阅更新也会重新构造全部订阅索引。

正确修复需要同时建立：

1. 精确的规则内容身份；
2. 明确的编译合同身份；
3. 可持久化的编译快照格式；
4. 每订阅独立的运行时索引分区；
5. 状态最后提交的原子更新事务；
6. 有效缓存命中、内容变化和缓存损坏的单一状态机。

只保存“上次已经编译”布尔值、文件修改时间或规则数量都不足以证明内容一致，不能作为正式实现。

## 4. 冻结架构

### 4.1 唯一 owner 不变

`BrowserAdBlockStore` 继续持有：

- schema 状态；
- 原始订阅载荷引用；
- 编译快照生命周期；
- 每订阅编译规则集；
- 每订阅 Engine 分区；
- 自定义规则分区；
- 合并后的 Engine；
- 对请求线程发布的唯一不可变 matcher。

设置页、网络请求拦截、网页元素隐藏、网络日志和现有 WebSession 规则重应用继续消费同一个 Store。

### 4.2 每订阅独立编译分区

将当前单个聚合订阅 Engine 调整为：

```text
customRuleSet -> customEngine

subscription A compiled snapshot -> engine partition A
subscription B compiled snapshot -> engine partition B
subscription C compiled snapshot -> engine partition C
subscription D compiled snapshot -> engine partition D
subscription E compiled snapshot -> engine partition E

combinedEngine = BrowserAdBlockEngine.combine(
    customEngine,
    partition A,
    partition B,
    partition C,
    partition D,
    partition E,
)
```

这样能够同时满足：

- 启动时每条订阅独立判断缓存是否命中；
- 单条内容变化只重建一个分区；
- 未变化分区直接复用本地编译快照；
- `combine()` 继续在同一个 matcher 中维持跨分区全局语义；
- 订阅启停只改变 matcher 的 `activeRuleSetIds`；
- 总开关和白名单只创建轻量 matcher，不重建索引。

### 4.3 原始文本仍是权威输入

原始订阅文本是可重新生成编译快照的权威输入。编译快照是派生数据：

- 不能替代原始载荷；
- 不进入用户备份；
- 不参与订阅编辑或导出语义；
- 可以按内容摘要和编译合同确定性重新生成；
- 不能把旧快照静默当作新规则使用。

页面策略 LRU、元素页面决策 LRU、top-private-domain LRU 和当前页面注入状态都与进程和浏览上下文
有关，不能持久化；每次进程启动仍创建空的有界运行时缓存。

## 5. 持久化布局

### 5.1 schema v3 状态

将 `browser_ad_block_state.json` 升级到 schema v3。每条已提交载荷的订阅增加：

```text
payloadSha256
payloadByteCount
payloadStorageVersion
```

现有字段继续保留：

```text
id
name
url
enabled
group
builtIn
lastUpdatedAt
lastError
ignoredLineCount
networkBlockingRuleCount
networkExceptionRuleCount
elementBlockingRuleCount
elementExceptionRuleCount
```

不把下列状态写入缓存键：

- 总开关；
- 自动更新开关；
- 白名单；
- 订阅启停；
- 分组启停；
- 更新时间；
- 错误文案；
- 进程拦截计数。

这些字段不会改变订阅规则的编译结构。

订阅名称进入缓存身份和编译快照元数据，因为当前阻断记录会返回 `sourceName`。设计阶段比较过：

- 首版把规范化订阅名称加入缓存键，名称变化只重建对应订阅分区；
- 后续若需要完全消除名称变化导致的重建，再把 `sourceName` 改为 matcher 发布时注入的不可变
  `ruleSetId -> displayName` 表。

本轮采用第一种，改动更小，也不会影响五条固定名称的内置订阅。

### 5.2 内容寻址的原始载荷

本轮把原始载荷从固定文件：

```text
filesDir/browser_ad_block_subscriptions/<id>.txt
```

迁移为：

```text
filesDir/browser_ad_block_subscriptions/<id>/<payload-sha256>.txt
```

状态只引用已完整写入的摘要。这样可以形成状态最后提交事务：

- 新文件写入期间，旧状态仍引用旧文件；
- 新文件和新编译快照都完成后，状态才切换到新摘要；
- 进程在任意写入阶段终止，都不会让已提交状态引用半成品；
- 未被状态引用的文件可以在后续维护阶段删除。

### 5.3 编译快照目录

编译快照放在：

```text
noBackupFilesDir/browser_ad_block_compiled/<cache-format-version>/
  <compiler-contract-id>/<subscription-id>/
    <payload-sha256>-<subscription-name-sha256>.bin
```

选择 `noBackupFilesDir` 的原因：

- 应用重启和普通版本更新后仍存在；
- Android 数据备份和恢复不携带大型派生文件；
- 恢复后的状态与原始载荷仍可生成新的本机编译快照；
- 不使用 `cacheDir`，避免系统存储回收造成不可预测的频繁重新编译。

## 6. 编译缓存键

单条订阅缓存键由以下字段组成：

```text
cacheFormatVersion
compilerContractId
subscriptionId
normalizedSubscriptionName
payloadSha256
```

### 6.1 `cacheFormatVersion`

只表示二进制文件结构。字段顺序、整数宽度、字符串编码、索引布局或校验方式发生变化时递增。

### 6.2 `compilerContractId`

表示规则解析与匹配语义。以下变化必须更新合同 ID：

- 支持或拒绝的 ABP/AdGuard 选项变化；
- 页面例外语义变化；
- 通配、分隔符、正则或 host anchor 语义变化；
- token 最小/最大长度或 hash 算法变化；
- 资源类型枚举与位图映射变化；
- `badfilter`、`important`、例外优先级变化；
- 元素规则域名匹配变化；
- 编译记录字段的语义变化。

合同 ID 不绑定整个 `versionCode`。与广告编译无关的应用更新不应让五条订阅重新编译。

已新增显式 `BrowserAdBlockCompilerContract`，集中声明合同版本、资源类型序号、token 参数和
支持语法版本；缓存测试固定该合同。后续语义变化必须显式更新合同，不能依赖开发者记忆从任意源码
改动推断缓存是否仍兼容。

### 6.3 `payloadSha256`

使用下载或迁移时对精确原始字节计算的 SHA-256。后续启动直接读取 schema v3 中已提交的摘要和
内容寻址路径，不再为了判断缓存是否命中而扫描五个大文本文件。

## 7. 二进制编译快照

### 7.1 编码原则

首版使用项目内自有、确定性的二进制 codec：

- `DataInputStream` / `DataOutputStream` 或等价的有界 reader/writer；
- 所有字符串使用显式 `Int` 长度 + UTF-8 字节，不使用有 65535 字节限制的 `writeUTF`；
- 所有集合在分配前校验条目上限；
- 枚举使用固定合同序号，不直接使用可能漂移的 ordinal；
- 文件头和正文均有边界校验；
- 解码过程中增量计算正文 SHA-256，不额外读取第二遍；
- 不使用 Java `ObjectInputStream`、Java 对象序列化、反射式任意类型恢复或大型 JSON；
- 首版不新增序列化依赖。

### 7.2 文件头

建议头部至少包含：

```text
magic = "KADBC"
cacheFormatVersion
minimumReaderVersion
compilerContractId
subscriptionId
normalizedSubscriptionName
payloadSha256
payloadByteCount
networkRuleCount
elementRuleCount
badFilterCount
bodyByteCount
bodySha256
```

解码前先检查 magic、版本、ID、摘要、文件大小和条目数量。任何不一致都表示该文件不是当前规则的
有效编译快照。

### 7.3 网络规则记录

每条网络规则保存运行时真正需要的语义字段：

```text
sourceLineIndex
rawRule
exception
hostAnchor
domainIncludes
domainExcludes
resourceIncludeBits
resourceExcludeBits
thirdPartyState
denyAllowDomains
matchCase
important
generic
pagePolicyBits
indexKey
literalPattern
wildcardPattern
regexPatternSource
regexOptionBits
matchAll
```

加载时：

- 普通包含、host anchor 和自有通配规则不再重新解析；
- 显式 `/regex/` 规则从已保存的 pattern source 创建当前 JVM `Regex`；
- `sourceLineIndex` 用于重建稳定 rule ID；
- `rawRule` 用于网络日志和规则详情；
- `pagePolicyBits` 直接还原页面例外语义。

JVM `Regex` 的创建属于必要的运行时装配，不再执行订阅语法判断、选项解析、token 选择或索引计算。

### 7.4 网络索引分区

快照保存已计算好的索引布局：

```text
requiresPartyClassification
hostAnchor -> rule index list
tokenLength -> tokenHash -> rule index list
unindexed rule index list
```

加载时直接构造对应 map/list，不再对全部规则执行 `filter/groupBy/mapValues` 和 token hash 重算。

### 7.5 元素规则与元素索引

元素规则记录：

```text
sourceLineIndex
selector
exception
domainIncludes
domainExcludes
generic
```

元素索引记录：

```text
generic rule index list
domain -> rule index list
```

页面级元素决策 LRU 不写入文件，进程启动后按真实访问页面重新建立。

### 7.6 `badfilter`

保存：

```text
source
canonicalRuleKey
```

rule set ID 由当前快照所属订阅隐含提供。多个订阅分区加载后，`BrowserAdBlockEngine.combine()`
仍把所有 `badfilter` 放入同一个 matcher 决策空间。

## 8. 新启动状态机

运行时 phase 已扩展为：

```text
READING_SETTINGS
LOADING_COMPILED_RULES
COMPILING_RULES
READY
FAILED
```

启动流程：

1. 在现有单一 IO 生命周期读取 schema 状态；
2. 合并五条稳定内置订阅定义；
3. 对每条已提交载荷，根据状态摘要、名称和编译合同计算快照路径；
4. 有效快照进入 `LOADING_COMPILED_RULES`，解码为独立 Engine 分区；
5. 未命中的订阅进入唯一 `COMPILING_RULES` 路径：
   - 读取该订阅原始载荷；
   - 解析、编译并建立该订阅索引；
   - 写入内容寻址编译快照；
6. 自定义规则继续形成小型独立分区；
7. 使用 `BrowserAdBlockEngine.combine()` 连接全部分区；
8. 依据总开关、白名单和已启用订阅创建 matcher；
9. 在锁内一次性发布完整 state、Engine 和 matcher；
10. 进入 `READY` 后再执行当前内置订阅更新周期检查。

完整缓存命中时不得调用：

```text
parseBrowserAdBlockSubscription()
BrowserAdBlockCompiledRuleSet.compile()
compileBrowserAdBlockNetworkRule()
compileBrowserAdBlockElementRule()
BrowserAdBlockNetworkIndex.fromCompiled()
BrowserAdBlockElementIndex.fromCompiled()
```

新 codec 应提供“从快照创建 Engine 分区”的专用入口，不能把解码结果再送回现有完整编译函数。

初始化仍保持当前安全边界：

- WebView 请求线程只读取 volatile matcher；
- 初始化未完成时请求不等待 IO 或编译锁；
- 不在请求线程读取文件；
- 不发布只包含部分订阅的 matcher；
- 所有订阅处理完成后一次性切换。

## 9. 缓存失效矩阵

| 事件 | 订阅编译快照 | 运行时动作 | `ruleRevision` |
| --- | --- | --- | --- |
| 应用重启，规则和合同未变化 | 命中 | 加载并组合分区 | 进程内建立初始 revision |
| 总开关变化 | 不变 | 只创建轻量 matcher | 递增 |
| 白名单变化 | 不变 | 只创建轻量 matcher | 递增 |
| 订阅启停或组启停 | 不变 | 只更新 active rule set IDs | 递增 |
| 自动更新开关变化 | 不变 | 不改变 matcher 结构 | 不递增 |
| 更新时间或错误文案变化 | 不变 | 不改变 matcher | 不递增 |
| 下载结果摘要与已提交摘要相同 | 命中 | 更新同步元数据，不替换 Engine | 不递增 |
| 单条订阅内容摘要变化 | 仅该条失效 | 重建一个分区并重新组合 | 递增一次 |
| 自定义订阅地址变化 | 该条失效 | 新内容同步成功后重建该分区 | 递增一次 |
| 订阅名称变化 | 仅该条失效 | 重建显示来源元数据对应分区 | 递增一次 |
| 编译合同 ID 变化 | 全部失效 | 每条本地载荷生成新合同快照 | 完成后建立新 revision |
| 二进制格式版本变化 | 全部失效 | 生成新格式快照 | 完成后建立新 revision |
| 与广告编译无关的应用更新 | 命中 | 正常加载 | 不因版本号单独失效 |
| 应用数据清除 | 不存在 | 首次同步和首次编译 | 按首次初始化 |
| 数据恢复后编译快照不存在 | 未命中 | 从恢复的原始载荷生成本机快照 | 完成后建立新 revision |
| 单条快照截断、摘要不符或字段越界 | 该条无效 | 记录明确原因并进入该条正常编译事务 | 仅真实规则变化时递增 |
| 原始载荷缺失 | 无法生成 | 订阅形成明确未就绪状态，由既有同步生命周期取得载荷 | 不伪造已生效 |

## 10. 订阅刷新事务

当前 `commitRefreshedSubscription()` 在锁内完成较多编译和聚合工作。实施缓存时应改为“准备在锁外，
验证与发布在锁内”：

1. `subscriptionRefreshMutex` 继续串行化订阅刷新；
2. 读取当前订阅 ID、URL、名称和已提交摘要；
3. 下载并校验原始字节；
4. 计算 SHA-256；
5. 摘要相同：
   - 不解析；
   - 不编译；
   - 不写新的原始载荷或编译快照；
   - 更新 `lastUpdatedAt`、清除错误并保留规则数量；
6. 摘要变化：
   - 在 Store 锁外解析和编译该订阅；
   - 生成该订阅 Engine 分区和二进制快照；
   - 写入新的内容寻址原始载荷；
   - 写入新的内容寻址编译快照；
7. 进入 Store 锁，重新确认订阅仍存在且 URL/名称与准备阶段一致；
8. 原子写入 schema v3 状态，状态最后引用新摘要；
9. 替换该订阅内存分区，使用 `combine()` 重新连接分区；
10. 依据锁内最新启停/白名单状态创建 matcher 并一次性发布；
11. 状态提交完成后删除无引用的旧代文件。

这套顺序保证：

- 进程终止不会让状态引用半写文件；
- 用户在刷新期间修改启停状态时，最终 matcher 使用锁内最新状态；
- 自定义订阅在刷新期间改地址或改名时，旧准备结果不会覆盖新配置；
- 单条刷新不再展平并重建其余订阅索引；
- 内容相同时不会制造无意义的 DOM 规则重应用。

## 11. 自定义规则边界

首要目标是五条大型内置订阅。自定义网址和元素规则通常数量很小，首版继续在进程启动时建立一个
小型 `customEngine`，避免把本轮扩大为所有规则编辑模型重构。

仍应增加计时和数量指标。只有证据显示自定义规则编译成为可见启动成本时，再使用自定义规则的规范化
内容摘要建立同类快照。不能为了形式增加第二套自定义规则存储。

## 12. 内存、磁盘与稳定性约束

### 12.1 能够降低的成本

- 五个大文本文件的 UTF-8 `String` 构造；
- 逐行 trim、分类和选项解析；
- 解析阶段的有效性编译；
- 规则集阶段的第二次编译；
- host/token/domain 索引重新计算；
- 大量短命集合、字符串和中间对象；
- 单条订阅刷新时对全部订阅索引的重建。

### 12.2 仍然存在的成本

- 运行时必须拥有可匹配的规则记录和索引；
- 二进制解码会创建必要的运行时对象；
- 显式 `/regex/` 规则需要在当前 JVM 中创建 `Regex`；
- matcher 的页面级 LRU 在访问页面后逐步建立；
- 稳态规则内存不会仅因持久化快照而消失。

因此完成标准必须同时观察墙钟、CPU、分配量、GC 和 PSS，不能只观察“设置页不再显示编译”。

### 12.3 有界读取

解码器必须限制：

- 单快照最大字节；
- 网络规则、元素规则和 `badfilter` 最大数量；
- 单字符串最大字节；
- 单规则域名集合大小；
- 单索引 bucket 数量；
- 单 bucket rule index 数量；
- rule index 必须落在规则表范围内；
- 重复索引、非法枚举、负数长度和整数溢出。

限制应从现有 `32 MiB` 单订阅载荷上限和真实五列表探针生成，不凭空设置无法容纳当前最大列表的值。

### 12.4 加载并发

首版按单一 IO 生命周期串行加载五个快照，控制峰值内存和 GC。只有目标设备数据显示磁盘延迟成为
主要瓶颈且峰值仍安全时，才评估最多两个分区并发解码。

## 13. 运行时可观测性

本轮已新增以下结构化初始化统计，不记录规则正文：

```text
totalSubscriptionCount
cacheHitCount
cacheMissCount
cacheInvalidCount
compiledSubscriptionCount
```

目标设备性能验收阶段仍需补充或通过外部采样取得：

```text
settingsReadMillis
cacheLoadMillis
payloadReadMillis
parseMillis
ruleCompileMillis
indexBuildMillis
snapshotWriteMillis
combineMillis
readyMillis
runtimeRuleCounts
```

每条缓存无效日志包含：

```text
subscriptionId
cacheFormatVersion
compilerContractId
expectedPayloadSha256
invalidReason
```

禁止记录完整规则文本、完整订阅内容或用户自定义规则正文。

设置页建议显示：

- “读取设置”；
- “加载本地编译规则 x/y”；
- “编译已变化规则 x/y”；
- “已就绪”；
- 明确的初始化失败或编译快照持久化警告。

磁盘空间不足导致快照写入失败时，本进程可以使用已经成功编译的内存分区，但必须显示和记录快照
持久化失败；不能静默声称后续启动已经具备缓存。

## 14. 实际实施文件范围

本轮实际修改和新增：

### Runtime

- `app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/websession/browser/BrowserAdBlockStore.kt`
- `app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/websession/browser/BrowserAdBlockPolicy.kt`
- `app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/websession/browser/BrowserAdBlockCompiledCache.kt`

### UI

- `app/src/main/java/com/ai/assistance/operit/ui/main/shell/KiyoriAdBlockSettingsPage.kt`

### Tests

- `BrowserAdBlockStartupContractTest.kt`
- `BrowserAdBlockPolicyTest.kt`
- `BrowserAdBlockSubscriptionCatalogTest.kt`
- `BrowserAdBlockCompiledCacheTest.kt`

### Governance and docs

- `CONTEXT.md`
- `docs/TODO/README.md`
- `docs/TODO/kiyori_browser_product_completion/7_browser_menu_capabilities.md`
- 本文

架构门禁未要求修改 `config/architecture/persistence-names.txt`；当前新增文件名不改变 DataStore、
SharedPreferences、Room、ObjectBox、WorkManager 或备份 API 的稳定命名表。

## 15. 分阶段实施计划

### 阶段 A：基线与合同

1. 在不改变行为的前提下增加初始化阶段计时、规则数量和缓存统计模型；
2. 使用确定性本地大规则 fixture 记录当前解析、编译、索引、堆分配和 `READY` 时间；
3. 在目标设备记录至少 10 次强制停止后的冷启动基线；
4. 冻结 `BrowserAdBlockCompilerContract`、缓存键和二进制上限；
5. 确认最大真实订阅的快照大小、加载时间和内存峰值可接受。

完成条件：有可重复基线，能够区分读取、解析、规则编译、索引和 matcher 发布时间。

### 阶段 B：纯模型与 codec

1. 定义不依赖 Android Context 的编译快照 DTO；
2. 实现确定性二进制编码和有界解码；
3. 为网络规则全部字段、元素规则、`badfilter` 和索引 bucket 建立 round-trip 测试；
4. 同一输入两次编码必须逐字节一致；
5. 截断、摘要错误、非法长度、非法枚举、越界 index 和过大集合必须明确失败；
6. 从快照创建的 Engine 与现有完整编译 Engine 对同一请求矩阵给出一致结果。

完成条件：codec 单元测试和匹配等价测试通过，尚未接入 Store。

### 阶段 C：schema v3 与内容寻址存储

1. 增加 v3 状态字段和严格校验；
2. 实现 v2 到 v3 的一次性迁移；
3. 把旧固定 `.txt` 迁移到内容寻址载荷；
4. 编译并写入首份本机快照；
5. 状态最后提交；
6. 仅删除已经确认无引用的旧文件；
7. 覆盖进程在每个写入阶段终止后的恢复测试。

完成条件：已有用户规则、五条订阅元数据和启停状态保持，迁移后重启能命中快照。

### 阶段 D：启动缓存接入

1. 增加 `LOADING_COMPILED_RULES`；
2. 有效缓存命中直接创建每订阅 Engine 分区；
3. 未命中仅处理对应订阅；
4. 使用 `BrowserAdBlockEngine.combine()` 组合分区；
5. 完整缓存命中测试禁止调用解析和完整编译入口；
6. matcher 继续一次性发布，请求线程不等待。

完成条件：第二次进程启动的五条订阅解析数和完整编译数均为 `0`。

### 阶段 E：刷新与设置变更

1. 下载后先比较 SHA-256；
2. 内容相同只更新同步元数据；
3. 内容变化只重建一个分区；
4. 总开关、白名单、订阅启停只重建 matcher；
5. 单条更新不重建其余订阅索引；
6. 保留跨分区 `important`、例外、`badfilter` 和元素例外测试；
7. 规则未变化时不递增 `ruleRevision`，避免无意义的页面元素规则重应用。

完成条件：更新矩阵中的重建数量与缓存失效矩阵一致。

### 阶段 F：UI、文档与正式验证

1. 设置页区分加载快照和编译变化规则；
2. 同步 `CONTEXT.md`、浏览器专项文档和 TODO 状态；
3. 运行定向 JVM、Kotlin 编译、正式准备门禁和差异检查；
4. 串行构建并核验 Debug APK；
5. 在目标设备执行冷启动、设置页、真实网页和订阅更新矩阵。

完成条件：本地实现证据和目标设备证据分开记录，未完成现场验证时保持
`verification_pending`。

## 16. 自动验证矩阵

### 16.1 codec 与缓存身份

- 完整字段 round-trip；
- 确定性编码；
- 正文 SHA-256；
- 载荷 SHA-256；
- 编译合同 ID；
- 格式版本；
- 名称变化；
- 载荷单字节变化；
- 与广告无关的版本号变化；
- 截断、越界和超限。

### 16.2 启动

- 全新数据：五条同步后首次编译并写入快照；
- 第二次启动：五条全部命中，解析和完整编译计数为 `0`；
- 一条快照缺失：只处理一条；
- 一条快照损坏：只处理一条并记录原因；
- 合同 ID 变化：所有本地载荷生成新合同快照；
- 数据恢复后无 `noBackupFilesDir` 快照：从原始载荷生成本机快照；
- 初始化期间 WebView 请求不等待；
- 完整 matcher 仍一次性发布。

### 16.3 规则语义

- 普通阻断；
- 普通例外；
- `important`；
- 跨订阅 `badfilter`；
- domain include/exclude；
- first/third party；
- resource type；
- page `document/elemhide/generichide/genericblock`；
- 标准元素阻断和元素例外；
- 通用 selector；
- 显式正则；
- host anchor；
- 自有通配和分隔符；
- 总开关、白名单和订阅启停。

### 16.4 更新事务

- 内容相同不编译、不替换 Engine、不递增 revision；
- 内容变化只替换目标分区；
- 刷新期间启停变化使用最新状态；
- 刷新期间 URL/名称变化拒绝提交旧准备结果；
- 原始载荷写入后、快照写入后、状态写入前的进程终止；
- 状态写入后的旧代文件清理；
- 磁盘空间不足和写入异常明确可观察。

## 17. 本地验证命令

仓库自有 Python 门禁使用项目 `.venv`；以下命令可用于复核本轮实现，Gradle 必须串行执行：

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserAdBlockCompiledCacheTest `
  --tests com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserAdBlockStartupContractTest `
  --tests com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserAdBlockPolicyTest `
  --tests com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserAdBlockSubscriptionCatalogTest `
  --no-daemon --console=plain

.\gradlew.bat :app:compileDebugKotlin --no-daemon --console=plain

.\.venv\Scripts\python.exe -B ci\script\check_formal_readiness.py `
  --repository . --require-main

git diff --check

.\gradlew.bat :app:assembleDebug --no-daemon --console=plain
```

代码完成后还应核验实际 Debug APK，而不仅记录 Gradle 退出码。

## 18. 目标设备验收

目标设备至少覆盖：

1. 清除应用数据后的首次启动与首次编译；
2. 同一 APK 的 10 次强制停止冷启动；
3. 五条快照全部命中的 `READY` 时间、CPU、Java heap、PSS、GC 和首屏帧；
4. 冷启动后立即进入设置页；
5. 冷启动后立即打开真实网页；
6. 总开关、白名单、单条启停和组启停；
7. 手动刷新但内容摘要相同；
8. 仅一条内容发生变化；
9. 应用版本更新但编译合同未变化；
10. 编译合同明确变化后的首次启动；
11. 后台切换、进程回收和再次启动；
12. 长时间浏览、动态 DOM、视频站点和内存压力。

必须观察到：

- 完整缓存命中时五条订阅解析数为 `0`；
- 完整缓存命中时五条订阅完整编译数为 `0`；
- 相同内容刷新不触发 DOM 规则重应用；
- 单条变化只重建一个分区；
- 请求阻断、例外、元素隐藏和网络日志结果与基线一致；
- 无 ANR、OOM、初始化崩溃或持续 GC 峰值；
- 缓存命中启动相对首次编译启动有明确、可重复的改善。

性能目标在阶段 A 取得目标设备基线后冻结，不能在没有实测的情况下编造固定毫秒阈值。

## 19. 风险与控制

| 风险 | 控制 |
| --- | --- |
| 编译快照体积过大 | 使用紧凑二进制、固定枚举位图、索引引用；阶段 A 先测量再冻结上限 |
| 解码仍产生较大稳态内存 | 区分短命编译分配与必要运行时对象；同时测量 PSS、heap 和 GC |
| 编译语义变化但合同 ID 未更新 | 集中 `BrowserAdBlockCompilerContract`，增加合同 fixture 和版本测试 |
| 快照损坏导致异常分配 | 所有长度、数量、索引和正文摘要先校验 |
| 进程在更新中终止 | 内容寻址文件先写，状态最后提交 |
| Android 数据恢复带回旧派生数据 | 快照位于 `noBackupFilesDir`，恢复后从原始载荷生成本机快照 |
| 单条刷新阻塞设置操作 | 大解析、编译和编码在 Store 锁外，锁内只验证、写状态和发布 |
| 分区化破坏跨订阅语义 | 复用现有 `BrowserAdBlockEngine.combine()`，保留跨分区测试 |
| UI 把加载快照误报成编译 | 增加独立 runtime phase 和准确文案 |
| 磁盘写入失败被忽略 | 结构化日志和可见警告，不把未持久化状态表述为已缓存 |

## 20. 完成定义

代码任务只有满足以下条件才能报告本地实现完成：

- schema v3、内容寻址载荷和编译快照均已实现并通过迁移测试；
- 完整缓存命中启动不进入五条订阅的解析和完整编译入口；
- 内容相同的订阅刷新不重新编译、不替换 Engine、不递增 revision；
- 单条内容变化只重建目标分区；
- 跨分区匹配语义与当前实现一致；
- Store、WebView 请求线程和设置页所有权不变；
- 定向测试、Kotlin 编译、正式准备门禁、差异检查和 Debug APK 构建核验通过；
- 目标设备冷启动、真实网页、设置页和长期内存验收未完成时，状态保持
  `verification_pending`。

## 21. 2026-08-18 本地实施记录

已完成的实现：

1. `BrowserAdBlockCompilerContract` 固定二进制格式、编译合同、资源类型编号、token 长度和 hash
   参数；与广告语义无关的应用版本变化不会使缓存失效。
2. `BrowserAdBlockCompiledCacheCodec` 使用确定性有界二进制格式，保存编译网络规则、元素规则、
   `badfilter`、host/token/unindexed 网络索引和元素域索引；文件头校验缓存身份，正文使用
   SHA-256，解码严格限制文件、字符串、集合、bucket 和 rule index。
3. `BrowserAdBlockEngine` 支持从已经持久化的索引引用直接恢复每订阅分区；有效缓存命中不调用
   订阅文本解析、完整规则编译或索引构造。
4. 状态升级为 schema v3；schema v1/v2 的固定 `<id>.txt` 载荷在单一 IO 初始化生命周期迁移到
   `<id>/<payload-sha256>.txt`，新载荷和快照完整写入后才提交状态，旧固定载荷在提交后清理。
5. 启动逐订阅统计 cache hit、miss、invalid 和本次编译数；设置页区分读取设置、加载本地编译
   规则、编译已变化规则和已就绪，并显示本次加载与编译数量。
6. 刷新先比较精确 SHA-256。摘要相同只更新 `lastUpdatedAt` 并清除错误，不替换 Engine、不增加
   `ruleRevision`；摘要变化只准备和替换目标订阅分区，提交时保留刷新期间最新启停状态。
7. 总开关、白名单、订阅启停和组启停继续只创建轻量 matcher；自定义规则保持原有小型独立分区。

本地验证已覆盖确定性编码、字段 round-trip、显式正则、规则语义等价、正文损坏、截断、身份不符、
固定资源类型编号、启动源码合同、内容摘要判断、刷新提交状态合并和既有跨分区规则语义。
`BrowserAdBlockCompiledCacheTest` 等广告/设置/浏览器定向矩阵及 architecture `phase=m03` 已通过。
最终正式门禁、Debug APK 证据已补充到 [历史证据索引](../history/README.md)；提交与推送证据由本任务最终
交付记录和 Git 历史提供，不把尚未发生的 commit 写成已完成事实。

仍未完成：

- 目标设备首次迁移和第二次启动的真实 cache hit 日志；
- 同一 APK 十次强制停止冷启动；
- Android heap、PSS、GC、CPU、首屏帧和设置页进入时延对比；
- 真实网页、订阅自动更新、进程终止点和长时间浏览现场验收。

本轮交付状态为：`LOCAL_IMPLEMENTATION_COMPLETE / VERIFICATION_PENDING`。

## 22. 2026-08-20 广告订阅编译 OOM 根因修复

### 22.1 现场证据

Crash Report `f462782c-9749-4a17-b694-e39004004aba` 显示应用在
`refreshDueBuiltInSubscriptions()` 自动刷新期间达到 512 MiB Java heap 上限。调用链为：

```text
refreshDueBuiltInSubscriptions
-> refreshSubscriptionLocked
-> compileSubscriptionPartition
-> BrowserAdBlockCompiledRuleSet.compile
-> compileBrowserAdBlockElementRule
-> normalizeBrowserAdBlockDomainInput
```

崩溃点只是最后一次 16-byte 分配失败的位置。真实峰值来自刷新路径同时保留下载 `ByteArray`、
完整 UTF-8 `String`、完整 network/element spec 列表、parser 有效性编译、第二轮正式编译以及旧
subscription partition。缓存缺失启动路径具有同样的完整文本、spec 列表和重复编译问题。

### 22.2 实现

- 新增 `compileBrowserAdBlockSubscription(BufferedReader, ...)`，逐行读取并直接产生最终 compiled
  network/element rules、`badfilter` 和五类元数据计数。
- 每条有效规则只调用一次现有 compiler；运行时不再保留完整 parsed spec 列表，也不再先构造完整
  subscription `String`。
- 内容变化刷新与 compiled-cache miss 使用同一单遍入口；现有小型
  `parseBrowserAdBlockSubscription()` API 保留，其分类与忽略语义共用同一行级解析函数。
- `badfilter` 继续计入网络规则统计，但只进入 `badFilters` 集合；普通阻断、例外、元素阻断、
  元素例外、domain/party/resource/page policy 和索引构造仍使用现有 compiled 模型与 partition。
- 没有捕获 `OutOfMemoryError`，没有减少有效规则数量，没有关闭自动更新、内置订阅或广告拦截，
  也没有增加第二个 Store、Engine 或 matcher owner。

### 22.3 自动回归

- 单遍结果与旧 parse + compile 的规则数量、ignored 计数、compiled network/element 记录、
  显式正则、`badfilter` 和请求/元素决策保持一致。
- 大批量混合 fixture 直接返回 compiled rule-set 与 counts，不返回完整 spec 列表。
- `BrowserAdBlockStartupContractTest` 锁定 Store 的刷新和 cache-miss 源码不再调用完整文本转换或
  `parseBrowserAdBlockSubscription()`，且不存在 OOM 捕获。
- 广告策略、compiled cache、启动合同和订阅目录 4 个 suite 为 `41/41`；完整联合矩阵为
  `14` 个 suite、`158/158`。Architecture fixture `109/109`、boundary `phase=m03`、formal
  readiness、Debug APK 和 native 静态审计通过。
- 目标设备上的真实自动刷新、512 MiB 堆压力与长期内存证据仍待复测；完成前继续保持
  `verification_pending`。

## 23. 2026-09-03 自动刷新域集合内存峰值修复计划

### 23.1 新现场证据与旧修复边界

Crash Report `ef5ed670-bcbd-4e8a-bf58-0a2a368062f5` 的调用链为：

```text
initializeRuntime
-> refreshDueBuiltInSubscriptions
-> refreshSubscriptionLocked
-> compileSubscriptionPayload
-> compileBrowserAdBlockSubscription
-> compileBrowserAdBlockElementRule
-> normalizeBrowserAdBlockDomainInput
-> split('.')
```

进程在 512 MiB growth limit 下只剩约 2.5 MiB，GC 后空闲低于 1%，最终连 24-byte 分配也无法
完成。第 22 节的 `BufferedReader` 单遍编译仍然有效：当前刷新路径没有恢复完整 payload
`String`、parsed spec 列表或第二轮规则编译。本次必须继续收敛单遍编译器和运行时表示，不能回退
第 22 节，也不能把最后一次 `split` 当成唯一根因。

2026-09-03 对当前内置 `exceptionrules.txt` 的只读采样得到：

- 响应正文 `18817348` bytes、`16727` 行，最大行长 `20000`；
- `5552` 条 cosmetic 规则包含 `612478` 个域名出现位置、约 `46673` 个唯一域名；
- network `domain/from/denyallow` 选项包含 `474606` 个域名出现位置、约 `48818` 个唯一域名；
- 单条 cosmetic 规则最多包含 `1353` 个逗号分隔域名。

当前实现为每个出现位置生成规范化域名 `String`，再放入每条规则的 `LinkedHashSet`；缓存解码也会
为相同文本重复创建对象。`BrowserAdBlockCompiledPartition` 同时持有 rule set、运行时 Engine、
boxed `Int` index snapshot，而 `BrowserAdBlockStore.subscriptionRuntimePartitions` 在整个进程期间
保留完整 partition。初始化完成后还在 `initializeRuntime()` 的调用帧内直接刷新到期订阅，导致
初始化局部对象、旧运行时、新下载 `ByteArray` 和新分区共同抬高峰值。

### 23.2 冻结实现方案

1. `normalizeBrowserAdBlockDomainInput()` 以单次字符扫描校验 label 长度、连字符和合法字符，不再
   使用 `split('.')`；domain expression 和 network option domain list 以分隔符索引逐项消费，
   不创建包含全部 token 的临时 `List`。
2. 每次 subscription compile 建立作用域内 domain interner；缓存 read 也为整条 partition 建立
   同类 interner。规范化结果相同时复用同一个 `String`，interner 在 partition 建立后释放，
   Engine 中保留的引用继续共享实例。
3. `CompiledBrowserAdBlockNetworkRule`、`CompiledBrowserAdBlockElementRule` 和 network option 中的
   domain include/exclude/denyallow 改为已去重的紧凑 `List<String>`。匹配仍按现有顺序执行
   `any/none`，元素域索引仍逐项建立，语义不依赖 Set 的公开接口。
4. 二进制缓存继续写入相同的 count + sorted UTF-8 字符串序列；读取时校验重复项后返回紧凑列表。
   schema v3、`CACHE_FORMAT_VERSION=1`、`COMPILER_CONTRACT_ID=kiyori-adblock-compiled-v1` 和既有
   cache identity 均不改变，因为磁盘字段布局与匹配语义不变，旧快照可以直接读取。
5. Store 的长期映射改为 `subscription id -> BrowserAdBlockEngine`。首次编译或缓存读取产生的完整
   partition 仅用于校验和快照编码，写完即只投影 Engine；刷新提交仍在同一锁内原子替换目标
   Engine、组合总 Engine、发布 matcher 和 state，不发布部分规则。
6. 初始化 coroutine 在 `initializeRuntime()` 返回后才调用 `refreshDueBuiltInSubscriptions()`；这只
   缩短临时对象生命周期，不改变 READY 后自动刷新、刷新顺序、错误状态或手动刷新入口。

### 23.3 风险、回滚点与验证

- 风险：紧凑列表必须保留每条规则的去重语义。编译和缓存读取均在转换前拒绝或消除重复值，现有
  domain match、element index coverage 和 cache corruption 校验继续覆盖。
- 风险：字符串驻留不能成为进程级永久池。interner 只属于一次订阅编译或一次缓存解码，不进入
  Store 字段；其生命周期结束后只剩 Engine 实际引用的唯一文本。
- 风险：释放 rule set/snapshot 不能影响缓存写入。流程固定为 compile/read partition -> 完成缓存
  校验/写入 -> 投影 Engine -> Store 原子提交，不能提前丢弃序列化状态。
- 回滚点：改动不迁移用户状态、不改磁盘格式、不写第二份配置；回退单个代码提交即可恢复旧内存
  表示，已有 schema v3 payload 和 compiled cache 仍可读取。
- 自动验收：重复域高基数 fixture 证明共享引用和紧凑集合；既有 parse/compile 等价、cache
  deterministic round-trip、跨分区 `badfilter`/important/元素例外、启动非阻塞与无 OOM 捕获合同
  全部通过。
- 交付验收：formal readiness、`git diff --check`、相关 Kotlin/JVM 编译、串行
  `:app:assembleDebug --no-daemon --console=plain`、Debug APK package/version/V2 signer/16 KiB
  zipalign、精确 staged allowlist 和远端三方 ref 对账通过。
- 设备边界：目标设备用同一内置订阅完成到期自动刷新，并采集 heap/PSS/GC；完成前保持
  `verification_pending`。

### 23.4 本地实施与验证结果

实现已完成：

1. `BrowserAdBlockDomainInterner` 只在一次 subscription compile 或 cache decode 内存活；网络与
   元素规则共享规范化域名实例，Store 不持有驻留表。
2. domain include/exclude/denyallow 使用排序去重的 `List<String>`；缓存 writer 继续生成与旧版
   相同的排序字符串序列，reader 通过严格递增校验拒绝乱序/重复字段，并以二分查找验证元素索引，
   避免紧凑列表带来平方级覆盖检查。
3. 域名 label、逗号 cosmetic domain expression、网络 option 及竖线 domain value 都按索引扫描，
   不创建包含整行 token 的 `split` 列表。
4. `BrowserAdBlockStore.subscriptionRuntimeEngines` 只保留 `id -> Engine`；完整 partition 完成 cache
   校验或写入后不进入长期 Store 字段。初始化 coroutine 在 `initializeRuntime()` 返回且状态为
   `READY` 后才调用到期刷新。
5. schema v3、`CACHE_FORMAT_VERSION=1`、`COMPILER_CONTRACT_ID=kiyori-adblock-compiled-v1`、内容
   寻址 payload、快照身份、自动/手动刷新、原子 matcher 发布和全部规则语义保持不变。

自动证据：

- `BrowserAdBlockPolicyTest`、`BrowserAdBlockCompiledCacheTest`、
  `BrowserAdBlockStartupContractTest`、`BrowserAdBlockSubscriptionCatalogTest` 共 `44/44` 通过，
  failures/errors/skipped 均为 `0`；
- 高重复域回归包含 `5000` 条网络例外、`5000` 条元素规则和约 `1000000` 个域引用，单项测试约
  `1.4s`；它验证编译结果跨规则及网络/元素类型共享同一域名实例；
- `check_formal_readiness.py --repository . --require-main` 与 `git diff --check` 通过；
- `check_architecture_boundaries.py --repository . --require-main` 仍因当前基线的 Manifest、AI Drawer、
  主导航、Software Home、主题及旧 exception 清单漂移失败；失败项不包含本专项修改文件，本轮未
  越界修改这些架构资产或批准其新摘要；
- 串行 `:app:assembleDebug --no-daemon --console=plain` 为 `BUILD SUCCESSFUL in 1m 29s`，`235`
  个任务中 `26` 个执行、`209` 个 up-to-date；
- Debug APK 生成于 `2026-09-03 17:40:16 +08:00`，大小为 `496156261` bytes、SHA-256
  `AB52CF1E4CC1FD700332FE15EACCE24EBE2C80CB4D25CE1262AEB869A9A52927`，包/版本/SDK 为
  `com.kiyori / 45 / 0.1.0 / 26 / 34 / 37`，仅 `arm64-v8a`，Android Debug V2 单 signer，
  16 KiB ZIP 对齐通过。

未安装 APK、未运行 ADB/模拟器/真机。目标设备的原到期自动刷新、512 MiB heap、PSS/GC 和长期
浏览验收仍为 `verification_pending`。
