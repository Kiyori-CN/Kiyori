---
status: verification_pending
owner: PackageManager + examples
---

# 内置脚本与插件规范化

## 目标与范围

本专项统一 AI 对话左抽屉“扩展”下随包分发的普通 JS/TS 脚本和目录型 ToolPkg 插件。范围以
`tools/example_packages/packages_whitelist.txt` 为准，覆盖 `examples/` 可维护源码、生成的 JS、
`app/src/main/assets/packages/` 生产资产、目录型 `manifest.json`/注册脚本以及 PackageManager 的
静态合同测试。每个包的说明都是注入模型上下文的 Agent 能力提示，必须短、准确、以调用条件和结果
为中心，不写作者、宣传语、教程或凭据。

用户可见版本按 Kiyori 尚未公开发行处理。内部包名、ToolPkg ID、环境变量名、宿主服务 API、市场
协议、存储键和历史数据保持兼容；本专项不重命名协议标识，也不增加别名、回退或第二状态所有者。

## 规范合同

### 普通脚本

- 文件名、metadata `name`、导出函数名使用稳定 `snake_case`；Search 分组包名统一以 `_search` 结尾。
- metadata 必须包含双语 `display_name`、一至两句双语 `description`、`category`、显式
  `enabledByDefault` 和完整 `tools`；不包含 `author`。
- `description` 只说明供应商/能力、适用时机和返回内容；工具说明先写何时调用，再写关键限制或前置
  结果；参数名、类型和必填性与实现一致。
- 只要 `env` 非空，`enabledByDefault` 必须为 `false`。这样首次安装不会在凭据或地址未配置时
  激活网络/设备能力；用户保存配置后仍可在扩展页主动启用。
- `catch` 必须记录脱敏上下文并返回结构化失败，不在 metadata 中暴露 API Key、作者或内部路径。

### 目录型 ToolPkg 插件

- manifest 必须包含双语 `display_name`、简洁双语 `description`、显式 `enabled_by_default`；
  不包含 `author`。子包 metadata 遵循普通脚本合同。
- Kiyori 尚未公开发行，所有示例 ToolPkg manifest 及其关联运行时 `package.json`/握手常量统一使用
  `1.0.0`；依赖版本、协议 schema revision 和业务数据版本不属于此产品版本合同。
- `environment` 非空时容器必须 `enabled_by_default: false`；子包是否默认启用仍由其自身状态和
  容器开关共同决定。
- Plugin/Script 的用户可见分类和文案使用同一词汇；ToolPkg 内部 ID、注册 route、Hook 和宿主服务
  名称不随可见文案迁移。

## OpenAI 搜索迁移

`com.kiyori.openai_web_search`、`openai_web_search`、`ToolPkg.services.openAIWebSearch` 和
`OPENAI_WEB_SEARCH_*` 是兼容标识，继续保留。manifest 与子包显示名改为“OpenAI 搜索”/“OpenAI
Search”，子包工具改为 `openai_search`，完整工具名为 `openai_web_search:openai_search`；宿主解析、
结果卡和持久化历史只按内部包 ID 工作，因此不需要协议别名或数据迁移。

## 统一分类与命名

分类使用现有稳定键：`Search`、`Academic`、`Draw`、`Chat`、`File`、`Network`、`Memory`、
`Media`、`Life`、`Automatic`、`Development`、`System`、`ToolPkg` 等。中文显示名中的品牌/服务名
与能力词之间使用一个空格（例如“Brave 搜索”“Google 搜索”“OpenAI 搜索”“Tavily 搜索”）；英文
显示名使用自然 Title Case。业务数据字段中的 `author`（论文、评论、GitHub 内容等）不是 metadata
作者字段，必须保留。

## 实施阶段与验收

1. **M0 研究与冻结**：完成白名单、资产、manifest、默认启用路径和 OpenAI 宿主合同盘点，记录本文件。
2. **M1 元数据规范化**：修正所有生产普通脚本和 ToolPkg manifest 的字段、说明、分类、作者字段和
   环境变量默认启用状态；生成 JS/assets 只能通过项目同步入口更新。
3. **M2 OpenAI 搜索**：更新显示名、子包工具名、UI 文案和调用合同；宿主服务/内部 ID 保持不变。
4. **M3 自动审计**：新增静态合同检查，覆盖白名单闭包、examples/assets 字节一致、双语文案、无作者、
   环境变量默认关闭、分类白名单、`1.0.0` 版本、Search 命名、工具/exports 对应、manifest 子包闭包
   和敏感内容。
5. **M4 构建与交付**：运行定向测试、正式准备检查、差异检查和 `:app:assembleDebug`，核验 APK 资产；
   审计本轮授权的全部当前改动后提交 `main`、推送 `origin/main` 并核对三方 ref。

## 验收矩阵

| 项目 | 自动证据 | 仍需现场证据 |
| --- | --- | --- |
| 首次安装环境脚本关闭 | metadata/manifest 合同测试与 PackageManager 默认状态测试 | 设备清数据安装后扩展页开关状态 |
| OpenAI 搜索命名与调用 | 源码、生成 JS、assets、宿主合同测试 | 设备模型真实调用与结果卡展示 |
| 全部内置资产规范 | 白名单闭包、HJSON/JSON 解析、文案/作者/分类/exports 审计 | 抽屉视觉密度与本地化显示 |
| 代码质量与错误路径 | TypeScript 编译、定向 JVM、静态反向检查 | 真实第三方额度、设备权限与网络行为 |
| 交付 | Debug APK、敏感内容/暂存树审计、local/tracking/remote ref 对账 | Release、远端 Actions、真机验收不由本专项替代 |

状态在设备或用户验收完成前保持 `verification_pending`。

## 本地完成证据（2026-08-28）

- 同步入口最终报告 `whitelist=49`、`resolved=49`、`copied=0`、`packed=0`、`missing=0`；37 个普通
  JS 和 12 个 ToolPkg 与 APK 源资产无漂移。
- `:app:testDebugUnitTest` 为 `1815` tests，failures/errors/skipped 均为 `0`；新增合同覆盖版本、分类、
  双语简介、无作者、环境变量首装关闭、外部 ToolPkg 启用语义和文件转换 Shell 参数边界。
- `check_formal_readiness.py --require-main`、`check_fresh_clone.py`、网络代理 Python 合同 13 tests 和
  `git diff --check` 全部通过。
- `:app:assembleDebug` 成功；`app-debug.apk` 为 `com.kiyori / 45 / 0.1.0`，V2 Debug 单签名和 16 KiB
  ZIP 对齐通过。APK 内 49 个扩展资产与各自源资产 SHA-256 一致，12 个 ToolPkg 均为 `1.0.0`、无
  manifest 作者，且不包含 `.env` 或 `src/` 条目。
- 未执行 Release、远端 Actions、ADB、模拟器或真机安装。首次安装开关、抽屉显示、真实 OpenAI
  调用和第三方服务现场行为继续保持 `verification_pending`。
