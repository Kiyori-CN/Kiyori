# 扩展与工具使用

Kiyori 内置 Operit AI 的脚本、ToolPkg、Skill 与 MCP 能力。应用页面中的“扩展”是用户入口，内部包名和协议标识用于准确调用，不应随展示名称替换。

## 配置流程

1. 在 AI 页打开左侧菜单，进入“扩展”。
2. 按类型查看脚本、插件、Skill 或 MCP，阅读用途与所需权限。
3. 填写包声明的环境变量和服务配置。需要环境变量的包首次安装默认关闭，配置后由你明确启用。
4. 回到 AI 页选择对应工具或运行入口，再执行一次范围明确的操作。

本地与远程服务、账户及权限相互独立。Key、Token 和 Cookie 不应复制到聊天或公开问题报告中。

## Bilibili 工具包

内置“哔哩哔哩工具包”提供 16 项只读检索、导出与媒体处理工具。在 **扩展 → 插件** 配置 `BILIBILI_COOKIE`，再在 AI 抽屉 Media 分组启用 `bilibili`。Cookie 只由宿主使用，不交给脚本。

- 默认产物位于 `Download/Kiyori/Bilibili`，覆盖已有文件需要显式 `overwrite=true`。
- `bilibili_user full=true` 保留 user 摘要，并按上游实际返回提供 profile。
- 当前分段弹幕和 XML 均不是完整历史；`capture media=false` 不新建媒体目录，也不删除已有下载。
- 媒体请求不向 CDN 发送 Cookie；证书错误、风控与 HTTP 拒绝会明确报告。

工具与验收细节见 [Bilibili 专项](../TODO/bilibili_toolkit/index.md)。

## 搜索与学术工具

OpenAI 搜索使用自己的包配置，不自动沿用当前聊天模型的端点或 Key。Brave 搜索及 arXiv、Crossref、PubMed、Semantic Scholar、OpenAlex 等工具分别遵循声明的接口、权限与限额。

配置后通过明确查询确认；设置中的兼容性探测可能调用付费服务，不在后台自动执行。相关契约见 [OpenAI 搜索](../doc-src/architecture/openai_hosted_web_search.md)。

## 编辑器与远程 Kiyori

内置平台编辑器包为 `kiyori_editor`，远程工具包为 `remote_kiyori`。远程连接使用目标 Kiyori 的 HTTP API 地址和访问令牌：

| 变量 | 用途 |
| --- | --- |
| `REMOTE_KIYORI_BASE_URL` | HTTP(S) 地址，支持 IPv4/IPv6 与路径前缀，未写端口时使用 8094 |
| `REMOTE_KIYORI_TOKEN` | 目标服务访问令牌 |
| `REMOTE_KIYORI_TIMEOUT_MS` | 调用超时配置 |

地址不带账号、查询串或片段。Windows 配套包为 `kiyori-pc-agent`，通过 `kiyori_pc_agent.bat` 启动。跨设备连通性与权限需要在目标环境验证，详见 [迁移与验收说明](../TODO/kiyori_extension_script_brand_migration/index.md)。

## 开发自己的扩展

脚本作者从 [脚本开发指南](../SCRIPT_DEV_GUIDE.md) 开始；ToolPkg 作者继续阅读 [格式指南](../TOOLPKG_FORMAT_GUIDE.md)。普通插件开发不要求修改 Android 主仓；只有新增宿主能力或内置资产时才进入主仓贡献流程。
