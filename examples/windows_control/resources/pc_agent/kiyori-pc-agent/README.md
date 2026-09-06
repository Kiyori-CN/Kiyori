# Kiyori PC Agent（Windows 代理）

本项目是 Kiyori `windows_control` 的 Windows 端 HTTP 代理，提供预设命令、原始命令和文件操作。默认配置界面为 `http://127.0.0.1:58321`，当前 HTTP 转发模式不依赖 OpenSSH。

## 开始使用

1. 安装 Node.js 18 或以上版本。
2. 双击 `kiyori_pc_agent.bat`。启动器清理原服务进程，启动服务并打开浏览器。
3. 在界面完成转发验证，复制生成的配置文本到移动端。

启动器会改变本机进程和服务状态。它是唯一用户启动入口；内部脚本 `scripts/launch_agent.ps1` 负责清理、启动与打开界面。

## 日志

| 文件 | 内容 |
| --- | --- |
| `logs/launcher.log` | 启动流程 |
| `logs/agent.runtime.log` | HTTP、API 与进程运行日志 |
| `logs/agent.out.log` | Node 标准输出 |
| `logs/agent.err.log` | Node 标准错误 |

## 前端结构

| 文件 | 职责 |
| --- | --- |
| `public/index.html` | 页面入口 |
| `public/styles/tokens.css` | 设计变量 |
| `public/styles/base.css` | 布局与响应式规则 |
| `public/styles/components.css` | 复用组件样式 |
| `public/scripts/main.js` | 声明式布局树与事件绑定 |
| `public/scripts/ui/runtime.js` | 轻量声明式渲染器 |
| `public/scripts/ui/widgets.js` | 布局与控件 |
| `public/scripts/i18n/strings.js` | 中英文资源 |
| `public/scripts/services/api.js` | API 请求层 |

## 后端结构

| 文件 | 职责 |
| --- | --- |
| `src/server.js` | 组件装配与生命周期 |
| `src/config/paths.js` | 项目路径常量 |
| `src/config/constants.js` | 默认配置、预设与静态 MIME 映射 |
| `src/lib/logger.js` | 运行日志 |
| `src/lib/http-utils.js` | JSON 解析、响应与静态文件 |
| `src/stores/config-store.js` | 配置加载、保存与预设规范化 |
| `src/stores/runtime-store.js` | `runtime.json` 写入与移除 |
| `src/services/process-service.js` | 进程执行、网络与用户快照 |
| `src/handlers/api-handler.js` | `/api/*` 路由 |

## 本地 API

公开读取入口：

- `GET /api/health`
- `GET /api/config`
- `GET /api/presets`
- `GET /api/startup/state`

配置与命令入口：

- `POST /api/config`
- `POST /api/command/execute`：要求 `token`。
- `POST /api/startup/apply_recommended_bind`

文件入口均使用 POST，JSON 请求体必须携带 `token` 与 `path`；`path` 可以是 Windows 绝对路径或相对路径。其余参数如下：

| 入口 | 其余参数示例 |
| --- | --- |
| `/api/file/list` | `depth: 1` |
| `/api/file/read` | `encoding: "utf8"` |
| `/api/file/read_segment` | `offset: 0`、`length: 65536`、`encoding: "utf8"` |
| `/api/file/write` | `content: "..."`、`encoding: "utf8"` |
| `/api/file/edit` | `old_text: "..."`、`new_text: "..."`、`expected_replacements: 1`、`encoding: "utf8"` |
| `/api/file/read_base64` | `offset: 0`、`length: 1024` |
| `/api/file/write_base64` | `base64: "..."` |

## 配置边界

默认绑定 `127.0.0.1`。`apiToken` 始终启用；缺失时自动生成。向导的一键填充仅在 token 缺失时生成，已有值继续复用。运行配置和日志包含本机信息，应留在本地。

## 排障

浏览器未打开时，先用 `node -v` 确认 Node.js，再检查启动日志、标准错误与运行日志。重新双击启动器会清理旧服务进程后重启。

当已配置的 `bindAddress` 不再可用（例如局域网 IP 变化）时，现有启动器进入临时 `127.0.0.1` 本地模式。配置界面显示启动恢复面板，由用户点击按钮应用推荐 IPv4 并重启。
