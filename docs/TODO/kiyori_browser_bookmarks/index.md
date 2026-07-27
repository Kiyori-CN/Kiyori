---
fork: https://github.com/Kiyori-CN/Kiyori
upstream: https://github.com/AAswordman/Operit
reference: D:/10_Project/kiyori-android@24a2dfa91f0a4166dc58e5c4732d11861173f766
status: verification_pending
---

# 浏览器书签菜单与书签抽屉

本轮在现有 Browser Runtime 内完成加书签弹窗、书签文件夹树和书签管理子抽屉。页面结构深度参考旧 Kiyori 固定提交、`hikerView` 的书签模型与用户提供的六张图片；状态所有权、主题和抽屉行为以当前 Kiyori 为准。

## 基线

- 实施基线为 `main@536c428160ca9dc50359d42d9243bcf5ed1ef788`，开始时与 `origin/main` 同步且工作树干净
- `WebSessionHistoryStore` 已持有扁平书签列表，`WebSessionBookmarkSheet` 只显示书签卡片
- 主菜单第一行已包含加书签和书签，但加书签直接切换状态，图标也随状态变化
- `WebSessionBrowserBottomDrawer` 已提供 Hidden、Partial 和 Expanded 三态，并按当前可见高度测量内容
- 本轮不提交、不推送，不安装 APK，不改变设备状态

## 目标

1. 加书签图标固定使用现有添加书签轮廓；当前网页已存在书签时，只把文字改为“移除书签”
2. 新增书签时显示四行可编辑字段：网站名称、网址、网址图标和书签文件夹；前三项从当前页面自动识别，文件夹默认根目录
3. 书签按钮打开与下载相同宿主的三态子抽屉，半展开和全展开都能滚动到最后一项
4. 顶部无返回键，标题在根目录为“我的书签”，进入文件夹后显示文件夹名；右侧固定更多按钮
5. 标题下方提供“搜索书签标题、链接”搜索框和可点击路径，搜索结果覆盖当前层级但不改变路径状态
6. 文件夹和书签使用紧凑列表行；文件夹显示后代书签数量，书签显示 favicon、标题和链接
7. 文件夹与书签长按菜单使用用户指定文案，并接通已有 Browser Runtime 能力与本地书签操作
8. 顶部菜单固定显示：新建书签、新建文件夹、排序方式、拖拽排序、书签导出、书签导入、秘密空间、保存成功、失效检测

## 约束

- 不创建第二个 WebView、session registry、浏览器 Activity、书签仓库或 presentation owner
- `WebSessionHistoryStore` 继续持有 `web_session_browser_store`，旧扁平书签 JSON 必须可直接读取
- 不改 `browser_*` 工具名、WebSession Profile、下载状态机或子抽屉三态手势
- 不实现网络探测式失效扫描；本轮失效检测只验证书签 URL 是否为可导航的 HTTP/HTTPS 地址，避免菜单点击触发未授权批量联网
- “保存成功”按用户给定菜单文字保留，用于确认当前书签数据已由 DataStore 持久化

## 实施步骤

1. [数据与运行时合同](1_data_and_runtime_contract.md)
2. [抽屉交互与验证](2_drawer_interaction_and_validation.md)

## 当前状态

- [DONE] 完成规则、Git、正式准备门禁、历史日记、当前代码、legacy、hikerView 和图片取证
- [DONE] 扩展书签模型与唯一持久化 owner
- [DONE] 实现新增书签弹窗、动态菜单名称和管理抽屉
- [DONE] 完成定向测试、文档一致性和 Debug APK 构建
- [DONE] 按书签树手动顺序统一文件夹选择、弹窗密度与遮罩，并把负一屏书签卡接入同一抽屉
- [ ] 真机验收半/全抽屉、长按菜单、IME、旋转、favicon 和打开窗口行为
