---
document_type: implementation-plan
status: verification_pending
---

# 哔哩哔哩工具包内置 ToolPkg

## 目标与权威边界

本专项把本机 `bilibili` Codex skill 的可移植、只读能力改造成 Kiyori 内置 ToolPkg：

- 插件显示名固定为“哔哩哔哩工具包”，`toolpkg_id` 固定为
  `com.kiyori.bilibili_toolkit`
- 插件内只有一个脚本子包，`id` 与脚本 `name` 均为 `bilibili`
- 脚本归入 AI 对话左抽屉的 `Media` 分组
- 不打包、不下载、不调用 `yt-dlp`，也不增加 FFmpeg、Python、Node 或其他可执行文件
- 媒体后处理只调用 Kiyori 已有 `Tools.FFmpeg`，即 Android
  `com.kiyori:ffmpeg` 中的 FFmpegKit/FFprobe
- 只声明一个环境变量 `BILIBILI_COOKIE`；它由宿主私密读取，不向 ToolPkg JavaScript
  暴露明文
- 保持安装包体积几乎不变：新增内容仅允许 Kotlin/DEX、manifest 与压缩 JavaScript

本文是本轮设计、实施阶段和验收状态的唯一 TODO 权威来源。长期模块与不变量在实现后同步到
`CONTEXT.md`，用户安装与使用入口同步到根 `README.md`。

## 产品状态与非目标

Kiyori 当前版本尚未正式发布。本轮保持既有 16 个工具名称与参数兼容，新增参数均可选。
`examples/bilibili_tools.ts/js` 是未打包的历史示例，不参与本 ToolPkg 执行；本轮不改变其接口。

以下能力明确不属于本轮：

- 浏览器 Cookie 自动解密或导入；用户只在插件环境变量页面粘贴自己的 Cookie
- 点赞、投币、收藏、追番、评论、发消息等账号写操作
- CAPTCHA、`412`、`-352` 等风控的重试、绕过、代理轮换或设备伪装
- 大会员、付费、区域限制、DRM 或已下架内容的权限绕过
- 历史弹幕抓取；只导出当前可播放 protobuf 分段或显式 XML 快照
- 另建播放器、下载器、网络代理或 FFmpeg runtime
- Release、发布、商店元数据与真机自动登录

## 已核对事实

- 生产 ToolPkg 由 Gradle 按
  `tools/example_packages/packages_whitelist.txt` 从 `examples/` 确定性生成，不把 TypeScript
  源码、测试或构建脚本装入 APK。
- `ToolPkgHostEnvironmentRepository` 已提供按容器身份隔离、JavaScript 不可读的宿主配置。
- `JsEngine` 已有“绑定容器身份 + 活动执行 call + Promise 回调 + owner 结束取消”的宿主服务
  模式，可沿用而不新增第二套插件运行时。
- `Tools.Files.download` 已提供 Android 公共目录下载与 Kiyori 脚本网络路由；播放地址中的授权
  查询参数足以完成媒体分片下载，Cookie 不必交给该工具。
- `Tools.FFmpeg.execute/probe` 已由宿主限制为 Android FFmpegKit，不是 shell，不能接受管道、
  重定向或命令链。
- 当前上游 `yt-dlp` 的 Bilibili extractor 同样以 Bilibili `playurl`/DASH 数据为媒体来源，再交给
  FFmpeg 合并；因此替代 `yt-dlp` 的核心是明确实现目标解析、播放格式解析与下载编排，而不是引入
  另一份下载器二进制。

## 用户与 Agent 使用模型

### 首次使用

1. 用户在“扩展 → 插件”确认“哔哩哔哩工具包”存在。
2. 需要登录态、私人列表或高画质时，只配置 `BILIBILI_COOKIE`；公开搜索和公开信息允许匿名使用。
3. 用户在 AI 对话左抽屉的 `Media` 分组启用 `bilibili`。
4. Agent 先调用 `bilibili_doctor`，只得到 Cookie 是否配置、是否登录、账号概要和宿主能力，不得到
   Cookie 内容。

### 信息检索

Agent 对 BV/av、Bilibili URL、`b23.tv`、EP/SS/MD 输入先调用 `bilibili_resolve`，再按目的调用
信息、字幕、弹幕、评论、用户、账号列表、互动图或 AI 摘要工具。列表调用必须显式给定页数或条数，
宿主与脚本共同执行上限。

### 下载与一次性抓取

Agent 先用 `bilibili_formats` 返回可用清晰度、编码、音轨与会员/预览状态；获得用户对目标、分P、
质量、容器和输出范围的明确意图后调用 `bilibili_download`。`bilibili_capture` 面向单个视频/分P，
一次生成信息、字幕、当前弹幕、限量评论、站内 AI 摘要、可选媒体和可选关键帧，并写出步骤清单。

下载文件使用身份稳定、标题无关的布局，避免标题修改导致重复目录：

```text
Download/Kiyori/Bilibili/library/<BVID-or-EPID>/pNN/
|-- metadata/
|-- subtitles/
|-- danmaku/
|-- comments/
|-- summary/
|-- media/
|-- frames/
`-- manifest.json
```

工具返回紧凑结构化摘要和产物路径；大段字幕、弹幕、评论与图数据写文件，不把完整载荷塞进聊天
上下文。

## 架构决策

| 层 | 唯一职责 | 关键约束 |
| --- | --- | --- |
| ToolPkg manifest | 插件身份、一个私密环境变量、一个子包 | `enabled_by_default=false`；无额外依赖 |
| `bilibili` TypeScript 子包 | 参数验证、目标解析、WBI 签名、API 编排、格式选择、文本导出、下载与 FFmpeg 编排 | 严格类型；无 Cookie 读取；无 shell |
| `ToolPkg.services.bilibili` | 读取私密 Cookie、只读 HTTP、重定向与风控边界 | 只允许绑定容器和活动 call；GET-only；严格 allowlist |
| 现有 `Tools.Files` | 公共媒体 URL 下载、目录与文件写入 | 不传 Cookie；显式 User-Agent/Referer |
| 现有 `Tools.FFmpeg` | DASH 合并、音频转换、片段裁剪、抽帧与探测 | 原样参数，不带 `ffmpeg` 前缀；检查 return code |
| Gradle ToolPkg 生成器 | 从生产白名单生成 `.toolpkg` 并打入 APK | 只打包 manifest 与 `dist` |

不把 Cookie 放进 URL、日志、异常、工具返回、缓存、manifest 或下载 header 参数。宿主响应只返回
状态码、最终 URL、内容类型和响应体，不返回 `Set-Cookie`、请求 header 或 Cookie 修订值。

## 宿主网络合同

### 调用身份与生命周期

- 只有 `boundToolPkgContainerName == com.kiyori.bilibili_toolkit` 能创建 Bilibili bridge。
- 每个请求必须携带仍然活动的 ToolPkg execution call ID。
- 异步请求登记到所属 call；工具完成、超时、取消或引擎关闭时取消 OkHttp `Call`，不允许悬空请求。
- 同一 bridge 的请求最小间隔为 350 ms；不并发冲击 Bilibili 接口。

### URL、重定向与 Cookie

- 只允许 HTTPS 和 GET。
- API 请求的 host 固定为 `api.bilibili.com`，path 必须在只读 allowlist 中。
- `b23.tv` 只用于短链解析且绝不附加 Cookie。
- 允许的公开资源 host 仅覆盖 Bilibili 官方字幕/弹幕/CDN 返回域，公开资源请求也不附加 Cookie。
- 禁用 OkHttp 自动重定向，每一跳重新校验 scheme、host、port、path 和 Cookie 资格，最多 5 跳。
- Cookie 只附加到 `api.bilibili.com` 的获准 API 请求；跳转到 `hdslb.com`、
  `bilivideo.com` 或其他资源域时不继承。
- 禁止调用方自定义 `Cookie`、`Authorization`、`Host`、代理或 TLS 选项。

### 错误与风控

宿主错误包括 `INVALID_ARGUMENT`、`CALLER_NOT_AUTHORIZED`、`NOT_LOGGED_IN`、
`RISK_CONTROL`、`HTTP_ERROR`、`API_ERROR`、`NOT_FOUND`、`ACCESS_RESTRICTED`、
`RESPONSE_TOO_LARGE`、`CANCELLED` 和 `INTERNAL_ERROR`；本地文件冲突返回 `OUTPUT_EXISTS`。
宿主诊断保留 `http_status/api_code/api_message/url/attempts/cause_type`，不打印 Cookie、header
或签名查询串。只有超时和 HTTP 500/502/503/504 最多重试两次，等待 350/700 ms。

HTTP `412/429`、API `-352/-412` 及已知验证码信号统一映射为 `RISK_CONTROL` 并停止当前抓取后续联网。媒体无格式时根据
PGC/UGC 响应明确区分预览、会员、区域限制、下架或无可用流；禁止把空列表当成功，也禁止静默改用
另一条网络路径。

## 目标与协议合同

### 目标解析

- 支持 BV、av/aid、纯 aid、标准视频 URL 和 `?p=N`
- 支持 `b23.tv`，解析后再次走完整 URL 校验
- 支持 EP、SS、MD 与对应番剧/影视 URL
- 支持空间主页与收藏夹 URL 的只读分类
- 拒绝非 Bilibili host、非默认 HTTPS 端口、URL 中的凭据和未知 target
- multipart 默认只处理第 1 分P；选择其他分P必须显式提供 `part`，不支持自动批量下载

### WBI

通过 `/x/web-interface/nav` 获取 `img_url` 与 `sub_url`，按固定 mixin 表生成 key；参数按 UTF-16
之外的普通 URL 编码规则排序并加入当前秒级 `wts`，使用内置纯 TypeScript MD5 生成 `w_rid`。
WBI key 仅放在单次工具执行内存中，不落盘，5 分钟后重新读取。nav 的 `-101` 只有同时包含
`isLogin=false` 和两枚有效图像 key 时才作为未登录数据返回；其他端点的 `-101` 仍为真实错误。

### API 能力

| 工具 | 主要端点/实现 | 上限与输出 |
| --- | --- | --- |
| `bilibili_doctor` | `/x/web-interface/nav` + FFmpeg info | 脱敏状态 |
| `bilibili_resolve` | 本地解析、`b23.tv`、PGC season | 单目标 |
| `bilibili_search` | `/x/web-interface/wbi/search/type` | 1–5 页、最多 100 项 |
| `bilibili_info` | `/x/web-interface/view`、PGC season | 单视频/分P |
| `bilibili_season` | `/pgc/review/user`、`/pgc/view/web/season` | 最多 200 集摘要 |
| `bilibili_subtitles` | `/x/player/wbi/v2` + 字幕资源 | JSON/SRT/VTT/TXT/ASS |
| `bilibili_danmaku` | `/x/v2/dm/web/seg.so`；显式 `source=xml` 使用 XML | XML/JSON/TXT/ASS；最多 5000 条、50 段 |
| `bilibili_comments` | `/x/v2/reply/wbi/main`、`/x/v2/reply/reply` | 1–5 页；回复深度 0/1 |
| `bilibili_user` | space profile/card/relation endpoints | 单 UID |
| `bilibili_account` | 历史、稍后再看、收藏、点赞、投币、追番/影视、投稿 | 登录必需；最多 5 页 |
| `bilibili_interactive` | `/x/stein/edgeinfo_v2` | BFS，最多 100 节点 |
| `bilibili_summary` | `/x/web-interface/view/conclusion/get` | 单分P |
| `bilibili_formats` | UGC/PGC playurl | 只列出，不下载 |
| `bilibili_download` | playurl + Files + FFmpeg | 单视频/分P；明确质量与容器 |
| `bilibili_capture` | 上述单目标编排 | 步骤级结果与 manifest |
| `bilibili_frames` | FFprobe + FFmpegKit | 本地文件；最多 30 帧 |

账号列表类型固定为 `history`、`watch_later`、`favorites`、`favorite_items`、`liked`、`coins`、
`followed_bangumi`、`followed_cinema` 和 `submissions`。公开数据不要求 Cookie；需要登录态的类型若
Cookie 缺失或未登录则明确失败。

## 媒体合同

- UGC 使用 `/x/player/wbi/playurl`；PGC 使用当前 web playurl 接口。
- 请求 DASH 所需 `fnval`、`fnver`、`fourk` 和目标 `qn`，保留返回的实际质量、编码、帧率、
  HDR/Dolby/Hi-Res/预览标志。
- `bilibili_formats` 返回经过脱敏的格式描述，不返回带授权查询参数的完整 CDN URL。
- 下载器按用户指定 `quality` 和 `video_codec` 精确选择一路视频及一路音频。请求的清晰度不可用时
  返回可用清单并失败，不暗中改选其他质量。
- DASH 分别下载到唯一临时路径，再以 `-map 0:v:0 -map 1:a:0 -c copy` 合并；音频提取按用户指定
  `m4a/mp3/opus/flac/wav` 复制或转换。
- progressive `durl` 按服务端返回顺序下载；多段使用 FFmpeg concat demuxer重新封装，禁止二进制
  直接拼接。
- `clip_start`/`clip_end` 在完成源媒体后由 FFmpeg 生成独立目标；不在远端请求中伪造范围。
  当前使用无重编码、关键帧对齐剪辑，不承诺逐帧精确；返回请求区间和 FFprobe 实测时长。
  试看媒体、DRM、不可用清晰度和越界剪辑均返回明确业务错误，不把试看当作完整视频。
- 所有临时媒体位于本次目标目录的 `.work-<id>`，成功或失败均清理；媒体处理通过后才移动至
  正式路径，下载失败不提前删除用户已有媒体。清理失败会记录错误，不伪装成功清理。
- 正式产物默认拒绝覆盖；`overwrite=true` 必须由调用方显式提供。

## 脚本实现与产物

源目录为 `examples/bilibili_toolkit/`：

```text
manifest.json
build.mjs
tsconfig.json
src/main.ts
src/packages/bilibili.ts
src/lib/*.ts
dist/main.js
dist/packages/bilibili.js
```

`build.mjs` 使用仓库已有 `esbuild` 分别生成两个无 runtime 依赖的 CommonJS bundle，保留子包的
`METADATA` banner。`dist` 必须提交以供 Android/Gradle 无 Node 场景构建；CI 重建后执行
`git diff --exit-code` 防止漂移。

回归与真实网络入口位于 `tools/example_packages/bilibili_toolkit.test.mjs` 和
`tools/example_packages/bilibili_toolkit.live.mjs`，不进入生产 ToolPkg。

## 实施阶段

1. **DONE — 研究与设计**
   - 核对仓库、Git、正式开发门禁、ToolPkg、环境变量、网络路由、文件和 FFmpeg 调用链
   - 对照原 skill 命令、测试、安全文档和当前上游 Bilibili extractor
   - 建立本能力矩阵、身份与安全合同
2. **DONE — 宿主服务**
   - 新增 Bilibili bridge policy、HTTP gateway、Cookie binding、错误 envelope 和生命周期取消
   - 接入 `JsEngine` 与 `ToolPkg.services.bilibili`
   - 补充 TypeScript 公共类型和 JVM policy/gateway 测试
3. **DONE — ToolPkg 与脚本**
   - 新增 manifest、严格 TypeScript 源、确定性 build 和单元测试
   - 实现目标/API/导出/媒体/capture/frames 能力
   - 加入生产白名单并删除旧示例
4. **DONE — 文档与 CI**
   - 更新 README、CONTEXT、workflow/package scripts、Markdown 与 ToolPkg 合同测试
   - 审计源码、dist、APK 均不包含 `yt-dlp` 可执行文件或 Cookie
5. **DONE — 验证与交付**
   - TypeScript 类型检查、确定性构建与 Node 单元测试
   - JVM 定向测试、正式门禁、Markdown 链接、`git diff --check`
   - 串行 `:app:assembleDebug`，核对 `.toolpkg`、APK 身份、体积、SHA-256 与基线差值
   - 审计允许清单、敏感内容、异常大文件、生成物和远端竞争状态
   - 提交 `main`、推送 `origin/main`，核对本地/tracking/远端 ref 一致

## 验收标准

- 插件和子包身份、显示名、`Media` 分组、默认启用语义符合本页固定值。
- manifest 只有 `BILIBILI_COOKIE` 一个环境变量，且为
  `package/host_service/sensitive/password`；JS 源与 dist 不出现读取环境变量或 Cookie 明文接口。
- 除已存在的文档历史外，本轮新增 APK 资产、源码调用和生成 ToolPkg 中不存在 `yt-dlp`、
  `yt-dlp.exe` 或新可执行文件；媒体流程可追溯到 `Tools.FFmpeg`。
- 未授权容器、伪造/结束的 call、非 HTTPS、未知 host/path/port、非 GET、重定向越界和敏感 header
  注入均被测试拒绝。
- 目标/WBI/字幕/弹幕/评论/格式选择/路径与 FFmpeg 参数通过无凭据单元测试。
- 匿名公开 API smoke test 可在网络允许时运行；登录、会员和高画质只以用户自己的 Cookie 与权限
  验证，不在自动测试中保存 Cookie。
- 生成 `.toolpkg` 的压缩体积与最终 APK 增量有精确字节证据；APK 增量不得包含新的 native library。
- Debug APK 构建通过并给出绝对路径、大小、SHA-256、基线差值。
- 提交与推送前后工作树和三方 ref 完成核对。

## 2026-09-05 系统缺陷修复计划

- 基线：`main@40fdd6aee`，工作树干净；范围为本工具包、专用宿主桥、模块工厂作用域修复、相关测试/CI与文档。
- 已复现：匿名 nav 的 HTTP 200 / code -101 同时携带 `isLogin=false` 和 `wbi_img`；
  当前宿主在读取签名密钥前拒绝该响应，七类签名工具尚未请求业务接口便失败。
- 阶段一（DONE）：限定 nav 的未登录数据合同；补齐安全 URL、状态码、服务端消息、
  异常类型与有界瞬态重试，不重试登录、权限或风控错误。
- 阶段二（DONE）：补齐可选参数 schema、capture 的 message/data 失败合同、逐步明细与
  overwrite 重跑；复用上下文，修正评论游标、弹幕覆盖、媒体输出验证及发现的边界问题。
- 阶段三（本地/网络 DONE，Android 待验证）：无凭据 Node 回归、JVM 桥回归、真实账号只读与单视频下载；
  不执行账号写操作，不新增 runtime，不修改本机 skill，本地验证不替代 Android 现场验证。
- 阶段四（构建 DONE）：文档、串行 Debug 构建与 APK 核验；提交推送 main 前审阅精确提交树、
  敏感内容与远端变化，最终 Git/远端 ref 以交付报告记录为准。
- 风险：排序、字幕与画质由平台和账号权限决定，旧计数不是断言；风控即停止该路径。
  回滚点为基线提交，本轮不自动回滚，不引入匿名重试、备用 API 或静默降级。

## 本轮验证记录（2026-09-05，Asia/Shanghai）

- Node 专项回归 **18/18**：16 工具参数 schema 与接口逐项一致、capture 首次/覆盖/冲突、部分失败
  和 manifest 写失败、共享上下文、风控停止、评论游标与去重、投币数组、收藏下一页、protobuf
  64 位 ID/UTF-8/截断/界限、五种字幕格式、媒体暂存保护、DRM/清晰度拒绝、抽帧核验和分P参数。
- JVM 宿主桥 **13/13**：调用身份、Cookie 隔离、重定向、精确 nav 例外、其他接口真实 -101、
  503/502 有界重试、超时诊断脱敏、风控、二进制编码和退避期间取消。
- TypeScript `--noEmit`、正式准备检查与 Debug APK 构建通过；生产 APK 中仅包含 manifest 和
  两份 dist JS，脚本字节与源码构建产物相等，工具数为 16，capture 的 overwrite 为可选布尔参数。
- 最终真实网络回归 70 次请求、扩展回归 26 次请求，均无非预期失败；使用用户明确提供的私密
  Cookie，仅在进程内用于获准 API，日志不含 Cookie、请求头或签名 URL，报告与导出均在忽略的
  `work/bilibili-live-*` 下。本轮未调用或打包 yt-dlp。
- `BV1Bxtf6qEmz`：info 连续 10 次成功；搜索命中目标；评论两页去重后导出 30 条；当前分段
  弹幕 50 条；画质返回 360P/480P/720P/1080P/1080P+；字幕真实可用，capture 导出 6 个语言轨的
  5 种格式，不能沿用早期“无字幕”的假设；无字幕分支另有离线回归。
- 360P 媒体 **12,847,551 bytes**，FFprobe 确认为 HEVC 640×360 + AAC、322.466625 秒；
  抽帧 8 张均非空。M4A 为 **8,568,668 bytes**；请求 2–6 秒剪辑实测为 **4.633 秒**，属于已明确
  标识的关键帧无重编码边界，不声称逐帧精确。
- capture 在独立目录首次执行与 overwrite 重跑均 **5/5**；不覆盖重跑返回可操作的
  `OUTPUT_EXISTS`。非互动视频返回 `available=false / NOT_INTERACTIVE`。
- UID/空间 URL 两种 user 输入、短链、doctor、season、9 种账号列表均通过。扩展回归发现的
  coins data 数组解析缺陷已修复后复测通过。
- APK 为 `com.kiyori / 45 / 0.1.0`，v2 签名验证通过；大小 **483,709,823 bytes**，其中本工具包
  **26,577 bytes**。APK 内使用新脚本；预置缓存由资产 SHA-256 持有，不依赖提升插件版本绕过缓存。
- 证据等级：真实 Bilibili 网络 + 桌面 Files/FFmpeg 适配器、JVM 模拟宿主、APK 构建与包内核验。
  **未安装设备、未改变账号数据、未完成 Android 真机端到端验收**，状态保持 `verification_pending`。

### 可复核入口

```text
node --test tools/example_packages/bilibili_toolkit.test.mjs
node node_modules/typescript/bin/tsc --noEmit -p examples/bilibili_toolkit/tsconfig.json --pretty false
node examples/bilibili_toolkit/build.mjs
./gradlew :app:testDebugUnitTest --tests '*ToolPkgBilibiliBridgeTest' --no-daemon --console=plain
./gradlew :app:assembleDebug --no-daemon --console=plain
```

真实网络入口只在得到账号和下载授权后使用：向 `bilibili_toolkit.live.mjs` 显式提供
`--cookie-env`、`--ffmpeg`、`--ffprobe` 的本机既有路径；`--extended-only` 单独检查其他账号
列表、音频和剪辑。脚本遇到风控停止后续联网，预期覆盖冲突单独判定，任何非预期失败返回非零退出码。
它不安装工具，也不模拟 Android 私密变量的真实注入，因此不能代替设备验收。

## 宿主 not a function 复现与修复（2026-09-05）

- 用户补充报告：仅 resolve 可用，其余 15 个工具稳定报 `not a function`。这不是缺少导出：
  16 个导出均存在，宿主缺失导出的错误为 `Function '...' not found in script`。
- 根因已在生产压缩 JS + 实际宿主 JS 片段中复现：esbuild 将 `recordAt` 压缩为函数 `_`，
  `JsExecutionScriptBuilder.createFactory` 把前导代码与包代码拼到同一作用域，前导代码的
  `var _ = globalThis._` 覆盖了提升后的函数声明。此前直接注入模拟服务的源码测试遗漏此路径。
- 修复保持公共接口不变：宿主别名位于外层词法作用域，包源码位于独立 CommonJS 函数作用域；
  主脚本和 require 模块共用修复，保留 exports、模块缓存与调用上下文，恢复源码自身的 strict 指令。
  不关闭压缩、不维护保留变量名清单、不新增运行时或备用业务 API。
- 同时修正文件工具普通对象异常的消息提取，权限错误不再变成不含原始消息的“非标准异常”。
- Node 共 **24/24**：原 18 项 + 6 项宿主集成，覆盖所有 16 个压缩导出、命名冲突、严格模式、
  缓存后的调用上下文、相对 require/JSON、缺失导出、异步拒绝、API 诊断和文件权限错误。
  测试提取实际 Kotlin 中的 JS，不在测试中复制实现；NativeInterface 网络/文件/媒体边界使用适配器。
- 真实网络主回归 **70** 次请求、扩展 **26** 次请求均通过。此次执行的是打包 dist，经实际
  bootstrap/模块工厂/ToolPkg/Tools/NativeInterface 回调链路，在 Node VM 中使用桌面网络与媒体适配器。
  16 工具、9 类账号列表、下载/抽帧、音频/剪辑、capture 首次/覆盖/冲突均完成；不是 Android 真机证据。
- 无凭据专项已加入 PR 与 Android 构建工作流：类型检查、重建 dist 并检查漂移、运行 24 项回归。
  真实 Cookie、联网测试和媒体下载不会进入 CI。
- 作用域修复后的 Debug APK 已重新构建：**483,709,823 bytes**，SHA-256
  `F6E12A6890C4DA7341F1C04776357E0DD7DA56C91BC65EDA2833B561FE72B600`；
  `com.kiyori / 45 / 0.1.0`，v2 签名及 16 KiB zipalign 检查通过。
  包内 ToolPkg **26,603 bytes**，仅 manifest 和两份 dist JS；脚本与当前 dist 字节相等，
  `classes18.dex` 中确认含独立 CommonJS 函数作用域实现。此前 APK 哈希不代表最终产物。

```text
node --test tools/example_packages/bilibili_toolkit.test.mjs tools/example_packages/bilibili_host_runtime.test.mjs
```

## 尚需现场验收

自动化与 Debug 构建不能证明真实账号、不同权限视频、Bilibili 实时风控、运营商/CDN、Android
存储授权、长下载、FFmpeg 处理耗时和 AI 左抽屉交互全部正确。代码、测试、构建、提交与推送完成后，
本专项保持 `verification_pending`，直到至少一台 ARM64 Android 设备完成匿名与登录两套流程、
UGC/PGC、字幕/弹幕/评论、DASH 合并、音频转换、capture、取消和断网复测。
