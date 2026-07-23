# 品牌与兼容性边界

## 采用的设计

Kiyori 是用户可见的产品品牌、仓库名、Gradle 根项目名和 Android application ID。Operit 只在确有兼容、互操作或归属意义时保留。

允许保留的实现或生态标识包括：

- `com.ai.assistance.operit` 和 `com.ai.assistance.operit.terminal`
- `operit://`、既有 Intent action、AIDL 名称和外部调用契约
- 数据库、偏好、备份格式、工作区格式、插件、ToolPkg、MCP 和文件格式标识
- `OperitForge`、Operit 市场协议、`operit_editor` 等外部生态名称
- `.operit/config.json`、`com.operit.*` ToolPkg ID、`remote_operit`、`operit-pc-agent` 文件与目录名、`OPERIT_*` 环境变量
- 独立于 Kiyori 产品版本的 Operit 市场兼容版本，用于插件 `minAppVer` 和 `maxAppVer` 范围判断
- 上游作者、许可证、历史归属和源码来源
- 内部资源 key、类名、日志标识和 native 文件名，除非它们直接出现在用户界面

必须使用 Kiyori 的位置包括：

- 应用名称、启动图标、About、帮助、反馈和项目链接
- Android 系统界面、默认角色、文件选择器根标题、通知和快捷方式的当前宿主引用
- WebChat、工作区模板展示文字以及 Kiyori 自有 companion 的界面标题
- 用户可见的终端产品名称和示例命令输出
- 当前应用沙箱 `/data/data/com.kiyori`；源码 namespace、action、provider 与 ToolPkg ID 不随之改名
- Kiyori 新建公共数据的 `Download/Kiyori` 根目录
- CI 产物名称、仓库工具包元数据和新建的开发文档

## 为什么不做全局替换

全局替换会破坏已存在的数据导入、插件发现、OAuth 回调、AIDL、第三方市场和工作区。它也会把上游归属改写成不准确的历史描述。任何协议级改名都必须先提供独立的版本兼容和数据迁移方案。公共数据根目录是产品所有权，不是协议 ID；Kiyori 未发布版本直接使用 `Download/Kiyori`，旧 Operit 数据只允许从显式导入入口读取。

Kiyori `versionName` 标识产品发布，不能代替 Operit 插件运行时兼容级别。当前 `OPERIT_MARKET_COMPAT_VERSION` 为 `1.12.0+4`，与本仓库采用的 Operit 源码能力基线一致；升级该值前必须确认对应插件 API、Hook 和 ToolPkg 生命周期契约已同步。

## 门禁规则

`ci/script/check_formal_readiness.py` 对用户可见终端文案、Kiyori application ID、根项目名、子模块来源和敏感/运行产物路径进行检查。它不以“搜索结果中不应出现 Operit”作为通过条件；允许列表由本文件定义。

新增用户可见文案或链接时，必须同时更新本文件、`CONTEXT.md` 和对应语言资源，并说明是否为兼容标识。内部标识迁移不得混入品牌文案变更。

默认角色直接引用现有 Kiyori drawable，WebChat favicon 复用仓库 Kiyori logo。旧 Operit 角色图片在零引用后删除，不保留并行品牌资产。Terminal 的 `installed-rootfs/ubuntu`、安装标记与内部挂载路径不属于产品品牌路径，继续保持稳定。
