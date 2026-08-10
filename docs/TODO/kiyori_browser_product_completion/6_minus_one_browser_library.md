# 负一屏浏览器资料入口

## 旧实现

负一屏显示收藏、书签、历史和下载四张无点击能力卡片。收藏没有独立 owner，书签、历史和下载已经存在于 Browser Runtime。

## 目标 UI

- 删除没有状态 owner 的“收藏”重复入口
- 保留书签、历史和下载三张主卡，并显示真实摘要：书签数量、最近访问、活动下载数量
- 手机使用一张主摘要卡加三条入口；600dp 以上可使用三列卡片
- 页面顶部保持负一屏身份，不添加独立大导航栏

## 页面接线

- 书签进入全屏书签页，复用 `WebSessionHistoryStore` 的 bookmark 数据和打开、删除动作
- 历史进入共享历史下拉抽屉，复用统一访问/播放历史、分类筛选和分时段删除动作
- 下载进入上一里程碑的全屏下载中心
- 从书签或历史打开 URL 时创建使用当前新窗口默认 Profile 的 session，进入 Browser Home
- Back 返回负一屏，并保持 Home Pager 在左页

## 数据与 AI

- 负一屏只观察 Browser Runtime/store，不复制列表或数据库
- AI 对书签、历史和下载产生的真实变化自动反映到摘要
- 负一屏打开网页后，AI 能通过 `browser_tabs list` 发现同一新窗口

## 验收

- 三个入口均可点击，页面、删除、清空和打开行为真实可用
- 不存在收藏与书签的重复伪状态
- 负一屏、Browser Home 和 AI 观察同一数据与 session
- 手机、平板和 Home Pager 转场稳定
- Debug APK、提交和推送门禁通过

## 2026-07-30 共享历史抽屉接入计划

本轮保留当前负一屏四张数据卡的已接受布局，只把“历史”从零计数占位接入真实能力；“收藏”仍不
借本轮创建平行 owner。

- `KiyoriMinusOneDataAction` 增加历史抽屉动作，“历史”卡观察
  `WebSessionHistoryStore.historyFlow` 的真实总数
- App Shell 增加与书签、下载同级的 `KiyoriHistoryDrawerHost`，三个共享抽屉继续互斥并由
  `KiyoriShellState`、Saver 和 Back 状态机统一持有
- 负一屏点击网页记录时，使用当前新窗口默认 Profile 创建共享 WebSession 后进入 Browser Home
- 点击在线视频或本地视频记录时，进入唯一 `PlayerSession` 的全屏 presentation，不创建第二播放器
- 浏览器菜单与负一屏必须组合同一个 `WebSessionHistorySheet`，筛选、删除和条目数量不能各自缓存
- 设备验收前，抽屉拖动、Pager 手势互斥、Back、在线请求身份和本地 URI 可访问性保持
  `verification_pending`

## 2026-08-10 历史网页与视频点击修复

- [DONE] 网页、小说和其他 URL 条目只由 App Shell 执行一次“关闭历史抽屉并进入 Browser Home”
  状态转换，历史抽屉不再用重组前的旧状态重复关闭并覆盖导航结果
- [DONE] 视频和音乐条目从负一屏直接启动唯一 `PlayerActivity`；浏览器页面内的历史与媒体入口继续
  使用 Browser presentation 的一次性全屏请求，不创建第二播放器或第二播放状态
- [DONE] 在线历史使用独立 `HISTORY_REPLAY` 来源；点击时从当前浏览器设置、普通 Profile Cookie
  和记录的来源页重建 User-Agent、Cookie 与 Referer，不再要求应用内仍存在活动 WebSession
- [DONE] 浏览器候选继续严格绑定真实 `sourceSessionId`；无痕候选不写入共享历史。历史重播不启用
  候选下载，也不在全屏退出后转为悬浮播放器；本地历史继续使用 `EXTERNAL_INTENT` 恢复同目录队列
- [DONE] 历史、Profile、播放器、Surface lease 与 Shell 的 6 个定向测试类共 `126/126` 通过，
  失败、错误和跳过均为 `0`；正式开发准备门禁与完整 `git diff --check` 通过
- [DONE] `:app:assembleDebug --no-daemon --console=plain` 为 `BUILD SUCCESSFUL in 43s`，
  `238` 个任务中 `28` 个执行、`210` 个为最新状态；`verifySingleDebugLauncher` 与
  `verifyDebugPlayerRuntimePackaging` 通过
- [DONE] Debug APK 为 `app/build/outputs/apk/debug/app-debug.apk`，大小 `475435609` 字节，
  SHA-256 `BDDD1DFB558BE6E9BE8A5AB8B133917A15FA17C66A081C4B0A446AC7EF61ECB3`
- [DONE] APK 为 `com.kiyori`、`45 / 0.1.0`、min/target/compile SDK `26 / 34 / 37`、仅
  `arm64-v8a`；Android Debug v2 单 signer 签名和 `zipalign -c -P 16 -v 4` 验证通过
- [PENDING] 在目标设备复测负一屏历史网页直达、在线视频、本地视频和 Back 返回
