# Kiyori PC Agent

Windows 工具包的电脑端。让手机上的 Kiyori AI 读取、编辑、复制与移动电脑文件，执行命令并管理持续进程。
本版为 `1.1.2`。手机工具包 `1.1.2` 支持稳定版 `1.1.x` 电脑协议，双方补丁号无需相同。
`1.1.x` 补丁保持认证、文件及进程接口兼容；破坏性协议变更必须进入新的主/次版本并显式适配。
手机工具包 `1.1.0/1.1.1` 曾错误地只接受电脑 `1.1.0`，请更新手机包，不能仅更新电脑。
源码源于 Operit，Kiyori 定制连接、界面、鉴权和文件行为。

## 开始使用

1. 手机 AI 左抽屉 → 扩展 → Windows 工具包，打开“连接 Windows”，展开“准备电脑端”，导出并分享压缩包。
2. 在 Windows 解压，双击 `kiyori_pc_agent.bat`。已有 Node.js 18+ 时直接使用；缺少运行时或依赖时，启动器会从官方 Node.js / npm 来源准备本地依赖。
3. 自动打开的页面选择“局域网”或“FRP / 远程连接”，从当前网卡选择地址，点击“保存应用并继续”。监听在同一进程内生效，管理页面地址和终端会话保持不变。
4. 第二步复制连接配置，在手机“从电脑导入配置”中粘贴并连接。手机也支持直接填写地址、令牌、Shell 和超时。
5. 看到“已连接并通过认证”后，向 AI 下达范围明确的任务。该状态证明本次地址、令牌和协议验证通过，不代表任意命令、路径或权限均可用。

升级旧版时保留本地 `data/config.json`。旧版以相对路径启动的进程不能被新启动器可靠识别，需先手动关闭旧进程。
新启动器只停止命令行指向本目录绝对 `src/server.js` 入口的 Node 进程，不会按端口或未经核实的 PID 终止其他程序。
从命令行使用 `node src/server.js` 或 `npm start` 启动时，用该终端的 Ctrl+C 停止。
启动器默认使用当前用户权限，不自动请求管理员权限。旧版若以管理员启动而当前用户无法核对其命令行，
需手动关闭旧 Agent，或在相同权限下运行停止脚本；不会冒险终止看不到来源的进程。

监听 IP 失效或端口占用时，只有本机管理页面可用，不启动替代执行端口。在页面核对保存与实际地址，
选择当前网卡后应用。绑定或配置落盘失败会明确报错并尝试恢复原监听；恢复失败也会展示，不冒充成功。
监听切换会短暂停止接收新请求，已开始的请求继续完成且不会重放。复制前重新核对监听和令牌；
看到其他页面修改配置的提示时先刷新，不要反复重启。

## 两个连接入口

| 入口 | 地址与用途 |
| --- | --- |
| 本机管理页面 | 仅监听 `127.0.0.1` 的临时端口，启动器自动打开；运行时地址见 `data/runtime.json` 的 `managementUrl` |
| 远程执行服务 | 默认端口 `58321`，由监听地址决定可达范围；手机与 FRP 连接此端口 |

管理页面与执行服务共用一个配置、文件服务和进程会话所有者。执行端口不提供管理网页、配置读取、配置修改或启动恢复接口。
本机管理端口检查 Host、Origin、跨站请求和 JSON 写入类型。**不要将管理端口加入 FRP 映射。**
管理页面可读取令牌；它属于已登录 Windows 用户的本机信任边界，不用于隔离同机恶意进程。

令牌具有当前 Windows 用户权限下的文件与命令访问能力，不能交给不可信设备。
“允许的预设”只限制预设命令，不限制原始命令；不是文件沙箱或全局命令白名单。

## 局域网连接

- 电脑选择实际可达的局域网地址，例如 `192.168.1.8`，默认端口 `58321`。
- 手机填写 `http://192.168.1.8:58321`。不要使用手机的 `127.0.0.1`、`localhost` 或监听通配地址 `0.0.0.0`。
- Windows 防火墙允许该端口及需要的网络配置文件；访客 Wi-Fi、AP 隔离、公司网络或 VPN 可能阻止设备互通。
- 多网卡机器核对手机能访问的真实网卡，不能仅凭推荐地址判断可达。
- 监听 `0.0.0.0` 会开放所有 IPv4 网卡；只在明确需要时手动选择。

HTTP 不加密手机到电脑的数据，只用于可信网络。公网接入使用 HTTPS 或已建立的受保护私有通道。

### 电脑开启 Clash / Mihomo 等代理时

- 系统 HTTP 代理不决定 Agent 绑定地址。启动器的就绪探测固定直连本机管理端口，禁用代理及重定向，并核对本次启动 PID。
- TUN 会新增网卡并修改路由；工具包识别 Mihomo/Clash、常见隧道与 `198.18.0.0/15` 地址，不自动推荐为手机 LAN 地址。
- Hyper-V、WSL、虚拟交换机、VPN 私网地址不会被自动当成物理 LAN。仍列出供用户明确选择，选择前确认手机确实加入相应私网。
- 优先选真实 WLAN/以太网地址。只有虚拟网卡时不给出伪造的可用推荐；连接能力最终由路由、防火墙和手机网络决定。
- 若本机能认证但手机不能连接，分别检查 Windows 网络配置文件/防火墙、Wi-Fi AP 隔离与 TUN 的局域网路由规则。
  工具包不会关闭代理、开启 Clash 的 LAN 代理入口或自动改防火墙。Clash 的“允许局域网连接”是代理端口设置，不等于开放 Agent 端口。

## FRP 与 HTTPS

当 frpc 运行在同一台电脑时，执行服务可保持 `127.0.0.1:58321`。
手机填写云服务器的最终 HTTPS 地址，例如 `https://pc.example.com` 或 `https://pc.example.com/bridge`。
显式 HTTPS 默认端口为 443，显式 HTTP 为 80；裸主机名默认补为 HTTP 和端口 58321。

推荐链路：手机 HTTPS → 云端反向代理 → 云端仅回环可达的 FRP 转发端口 → PC 执行服务。
FRP 自身的传输 TLS 只保护 frpc 与 frps，不能代替手机到云服务器的 HTTPS。

以下为需要自行部署和替换占位符的参考，不会由工具包自动修改服务器、防火墙或安装 FRP。
字段于 2026-09-08 对照 [FRP 官方 frpc 示例](https://github.com/fatedier/frp/blob/dev/conf/frpc_full_example.toml)；实际使用与安装版本匹配的配置。

电脑 `frpc.toml`：

```toml
serverAddr = "frp.example.com"
serverPort = 7000
auth.method = "token"
auth.token = "replace-with-your-frp-token"
transport.tls.enable = true

[[proxies]]
name = "kiyori-pc"
type = "tcp"
localIP = "127.0.0.1"
localPort = 58321
remotePort = 15832
```

云端 frps 可将代理监听约束到回环地址（`proxyBindAddr = "127.0.0.1"`），再用具有有效证书的 HTTPS 站点转发：

```nginx
# 放入已配置有效 TLS 证书的 server 中。
location /bridge/ {
    proxy_pass http://127.0.0.1:15832/;
    proxy_http_version 1.1;
    proxy_connect_timeout 15s;
    proxy_read_timeout 610s;
    proxy_send_timeout 610s;
    client_max_body_size 24m;
}
```

手机地址为 `https://pc.example.com/bridge`。结尾斜线决定 Nginx 是否移除路径前缀，需保留示例中的配对关系。
如果使用整个域名而无 `/bridge`，将 location 改为 `/`，手机填写域名根地址。
使用直接 TCP 外网映射时填写实际映射端口，但该 TCP 入口本身不会自动提供 HTTPS。
连接错误不会通过关闭证书检查、跟随重定向、自动直连或切换端点解决。

## Agent 文件工作流

建议先定位与读取，再做小范围修改，最后核对结果。路径属于电脑；相对路径基于 PC Agent 解压根目录，优先使用绝对路径。

| 手机工具 | 功能与限制 |
| --- | --- |
| `windows_list` | 列目录，深度 1–5，总计最多 5000 项；不跟随子目录符号链接；子目录权限错误显式返回 |
| `windows_stat` | 路径、类型、大小与修改时间 |
| `windows_mkdir` | 创建目录与缺少的父目录，已有目录返回 `created=false` |
| `read` | 全文或行范围最多 4 MiB；字节分段单段最多 2 MiB，返回实际消费字节数 |
| `write` | 写入最多 4 MiB 文本，允许空字符串；可能覆盖原文件，调用前应读取并确认范围 |
| `edit` | 精确匹配 `old_text`，默认期望一次替换，数量不符时保持原文件；保留未修改的换行和 BOM |
| `windows_copy` | 复制一个文件到完整目标路径，父目录须存在；已有目标直接失败，不支持目录递归复制 |
| `windows_move` | 文件或目录移动/重命名，父目录须存在；拒绝已有目标与移入自身；跨卷 `EXDEV` 明确失败 |

UTF-8/UTF-16 分段返回完整字符；下一段使用 `offset + length`，不要假定消费了请求的所有字节。
内部文件接口还提供 Base64 分段读取与写入，二进制上限 16 MiB。大目录需缩小路径与深度，不将截断结果冒充完整列表。

文件完整写入同目录临时文件后再发布；Windows 已存在文件用 `File.Replace` 保留目标 ACL 等元数据。
符号链接写入解析到真实目标；硬链接目标明确拒绝替换。Windows 文件锁、权限、只读文件和跨卷限制会返回失败。
这不是跨进程事务：不能保证与外部编辑器同时修改同一文件无竞争。不要并发安排多个写入同一目标的任务。

示例任务：

> 先列出 D:\Work\Inbox，读取计划.txt，在授权段落内精确修改；创建 D:\Work\Archive，
> 将计划.txt 移动过去，最后读取目标并确认原路径已不存在。遇到同名文件先报告，不覆盖。

## 命令、进程与错误

- `windows_exec` 用于短命令，支持 `powershell`、`pwsh`、`cmd`；工具不静默替换错误的 Shell。
- 长任务用 `windows_process_start/read/write/list/terminate`，保存返回的 session ID 和输出偏移。
- 电脑终端不重发失败的输入。会话已消失（如服务重启或其他页面关闭）时清空队列与失效选择，不必刷新整个网页。
  其他通信错误暂停输入与轮询；先核对执行状态，再点击刷新会话列表，只恢复后续新输入。
  已退出会话也可关闭移除；“正在等待进程退出”只代表终止信号已发送，不冒充已关闭。
  切换终端时回放历史输出不向 PTY 发送历史协议应答。
  终止只使用服务持有的 PTY/进程句柄；失败明确报告，不按可能已复用的裸 PID 再启动 `taskkill`。
- PowerShell 使用 UTF-8 并将非终止错误提升为失败；需要处理的错误应在命令内显式捕获。
- 短命令输出有界，超过上限返回 `OUTPUT_TRUNCATED`；需要完整输出时使用会话或明确重定向到文件。
- 请求不自动重试、跟随重定向或携带共享 Cookie。`SUBMISSION_UNKNOWN` 表示操作可能已执行，必须先检查目标文件或进程状态。
- 认证探测默认 15 秒；手机配置页最多使用 30 秒探测。普通请求默认 30 秒，可配置 1–600 秒；各网络阶段有独立超时，并非严格的全链路计时器。
- `UNAUTHORIZED`：重新复制匹配令牌；`REDIRECT_REJECTED`：使用最终 URL；旧协议或 404：核对前缀并更新电脑端。
- 手机连接失败显示具体保存/启用/验证阶段与脱敏详情；`PROTOCOL_INCOMPATIBLE` 是版本兼容错误，不是 Wi-Fi 或 Clash 故障。
- 配置损坏时保留原文件并拒绝启动，不自动重置令牌。备份并人工修正 `data/config.json` 后重试。
- 监听 IP 失效时，管理页展示当前运行错误，由用户应用当前网卡地址，无需重启进程；不将管理页可用显示为手机已连接。

## API 与本地数据

远程 `GET /api/health` 只公开版本、模式与端口。`POST /api/connection/test` 验证 `token`。
命令、文件与进程 POST 接口保留请求体 `token` 的兼容方式；缺少或错误令牌返回 401。
管理入口 `/api/config`、`/api/startup/*` 只能通过独立本机管理端口访问。

文件 POST 路由为 `/api/file/list|stat|mkdir|move|copy|read|read_segment|read_lines|write|edit|read_base64|write_base64`。
共用 `path`，复制/移动增加 `destination`；其余字段与上述工具一致。
响应保持 `ok` 和已有字段，新接口失败带 `code`；手机工具将结果投影为 `success`。

`data/config.json` 保存配置与令牌；`runtime.json`、`agent.pid`、启动状态、日志、下载的 Node 和 `node_modules`
均为本地运行数据，不应分享或提交。分享使用手机导出的干净资源包。
日志记录请求路径、状态、耗时与输出长度，不记录命令参数和令牌；配置预览隐藏令牌，复制内容包含真实令牌。

## 开发与验收

在仓库根目录：

```powershell
node node_modules/typescript/bin/tsc -p examples/windows_control/tsconfig.json --pretty false
node --test tools/example_packages/windows_control.test.mjs
powershell.exe -NoProfile -File tools/example_packages/windows_agent_launcher.test.ps1
```

测试在临时目录与随机端口验证实际文件、HTTP 转发和双监听服务，结束后移除测试数据。
现场手机、真实公网 FRP、有效 TLS 证书、复杂 ACL、杀毒软件与长期 PTY 会话分别验收，状态见
[专项计划](../../../../../docs/TODO/windows_toolkit_refinement/index.md)。
