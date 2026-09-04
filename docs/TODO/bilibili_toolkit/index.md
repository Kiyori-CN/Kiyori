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

Kiyori 当前版本尚未正式发布，本轮直接删除仓库中未打包、未被引用的旧
`examples/bilibili_tools.ts` 与 `examples/bilibili_tools.js`，不保留两套 Bilibili 脚本。

以下能力明确不属于本轮：

- 浏览器 Cookie 自动解密或导入；用户只在插件环境变量页面粘贴自己的 Cookie
- 点赞、投币、收藏、追番、评论、发消息等账号写操作
- CAPTCHA、`412`、`-352` 等风控的重试、绕过、代理轮换或设备伪装
- 大会员、付费、区域限制、DRM 或已下架内容的权限绕过
- 历史弹幕抓取；只导出当前可播放弹幕 XML 快照
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
- Cookie 只附加到 `bilibili.com` 或其子域的获准 API 请求；跳转到 `hdslb.com`、
  `bilivideo.com` 或其他资源域时不继承。
- 禁止调用方自定义 `Cookie`、`Authorization`、`Host`、代理或 TLS 选项。

### 错误与风控

稳定错误种类至少包含：`InvalidArgument`、`CallerNotAuthorized`、`CookieRequired`、
`NotLoggedIn`、`RiskControl`、`HttpError`、`ApiError`、`NotFound`、`UnsupportedTarget`、
`AccessRestricted`、`ResponseTooLarge`、`Cancelled` 和 `InternalError`。

HTTP `412`、API `-352` 及已知验证码信号统一映射为 `RiskControl` 并结束当前操作。媒体无格式时根据
PGC/UGC 响应明确区分预览、会员、区域限制、下架或无可用流；禁止把空列表当成功，也禁止静默改用
另一条网络路径。

## 目标与协议合同

### 目标解析

- 支持 BV、av/aid、纯 aid、标准视频 URL 和 `?p=N`
- 支持 `b23.tv`，解析后再次走完整 URL 校验
- 支持 EP、SS、MD 与对应番剧/影视 URL
- 支持空间主页与收藏夹 URL 的只读分类
- 拒绝非 Bilibili host、非默认 HTTPS 端口、URL 中的凭据和未知 target
- multipart 默认只处理第 1 分P；多项处理必须显式提供 `part`/`max_items`

### WBI

通过 `/x/web-interface/nav` 获取 `img_url` 与 `sub_url`，按固定 mixin 表生成 key；参数按 UTF-16
之外的普通 URL 编码规则排序并加入当前秒级 `wts`，使用内置纯 TypeScript MD5 生成 `w_rid`。
WBI key 仅放在单次工具执行内存中，不落盘。

### API 能力

| 工具 | 主要端点/实现 | 上限与输出 |
| --- | --- | --- |
| `bilibili_doctor` | `/x/web-interface/nav` + FFmpeg info | 脱敏状态 |
| `bilibili_resolve` | 本地解析、`b23.tv`、PGC season | 单目标 |
| `bilibili_search` | `/x/web-interface/wbi/search/type` | 1–5 页、最多 100 项 |
| `bilibili_info` | `/x/web-interface/view`、PGC season | 单视频/分P |
| `bilibili_season` | `/pgc/review/user`、`/pgc/view/web/season` | 最多 200 集摘要 |
| `bilibili_subtitles` | `/x/player/wbi/v2` + 字幕资源 | JSON/SRT/VTT/TXT/ASS |
| `bilibili_danmaku` | `comment.bilibili.com/{cid}.xml` | XML/JSON/TXT/ASS；可限条数 |
| `bilibili_comments` | `/x/v2/reply/wbi/main`、`/x/v2/reply/reply` | 1–5 页；回复深度 0/1 |
| `bilibili_user` | space profile/card/relation endpoints | 单 UID |
| `bilibili_account` | 历史、稍后再看、收藏、点赞、投币、追番/影视、投稿 | 登录必需；最多 5 页 |
| `bilibili_interactive` | `/x/stein/edgeinfo_v2` | BFS，最多 100 节点 |
| `bilibili_summary` | `/x/web-interface/view/conclusion/get` | 单分P |
| `bilibili_formats` | UGC/PGC playurl | 只列出，不下载 |
| `bilibili_download` | playurl + Files + FFmpeg | 单视频/分P；明确质量与容器 |
| `bilibili_capture` | 上述单目标编排 | 步骤级结果与 manifest |
| `bilibili_frames` | FFprobe + FFmpegKit | 本地文件；最多 30 帧 |

账号列表类型固定为 `history`、`watch_later`、`favorites`、`liked`、`coins`、
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
- 所有临时文件位于本次目标目录的 `.work-<id>`，成功或失败均删除；不删除用户已有文件。
- 正式产物默认拒绝覆盖；`overwrite=true` 必须由调用方显式提供。

## 脚本实现与产物

源目录为 `examples/bilibili_toolkit/`：

```text
manifest.json
README.md
build.mjs
tsconfig.json
src/main.ts
src/packages/bilibili.ts
src/lib/*.ts
tests/*.test.mjs
dist/main.js
dist/packages/bilibili.js
```

`build.mjs` 使用仓库已有 `esbuild` 分别生成两个无 runtime 依赖的 CommonJS bundle，保留子包的
`METADATA` banner。`dist` 必须提交以供 Android/Gradle 无 Node 场景构建；CI 重建后执行
`git diff --exit-code` 防止漂移。

旧 `examples/bilibili_tools.ts/js` 在新 ToolPkg 编译产物与合同测试通过后删除。

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

## 尚需现场验收

自动化与 Debug 构建不能证明真实账号、不同权限视频、Bilibili 实时风控、运营商/CDN、Android
存储授权、长下载、FFmpeg 处理耗时和 AI 左抽屉交互全部正确。代码、测试、构建、提交与推送完成后，
本专项保持 `verification_pending`，直到至少一台 ARM64 Android 设备完成匿名与登录两套流程、
UGC/PGC、字幕/弹幕/评论、DASH 合并、音频转换、capture、取消和断网复测。
