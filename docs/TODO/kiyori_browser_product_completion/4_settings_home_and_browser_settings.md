# 设置首页与浏览器设置

## 旧实现

Settings Home 只有一条 AI 设置入口，无法体现 Kiyori 作为浏览器产品的能力所有权。浏览器菜单中的“浏览器设置”仍是说明页。

## 设置首页

只显示已有真实状态 owner 或本里程碑同步实现的入口：

- 网页浏览器
- 下载中心
- 文件下载器设置
- Operit AI 设置
- 权限与系统能力入口，在现有真实路由可直接接通时展示
- 视频播放器只在播放器基础里程碑完成后加入

不展示音乐、小说、广告拦截、备份等尚未建立 Kiyori 页面和状态 owner 的空入口。

## UI

- 顶部使用紧凑标题和说明，不复制旧参考页无功能的搜索、扫描、刷新和太阳按钮
- 入口按“浏览与内容”“AI 与系统”分组，使用 Operit theme 的圆角卡片、图标色和右箭头
- 手机为单列列表；840dp 及以上为两列分组卡片，内容最大宽度受限
- 页面与子设置使用同一个 Shell child stack；系统 Back 和顶栏返回都回到 Settings Home

## 浏览器设置

首期只加入能直接约束现有 Browser Runtime 的设置：

- 默认搜索引擎，复用 `WebSessionHistoryStore`
- 新窗口默认 Profile，在 Multi-Profile 可用时选择普通或无痕
- 默认 UA 模式，复用现有 desktop/mobile 设置 owner
- 搜索记录管理入口，进入同一搜索记录数据
- 网站数据说明，显示普通与无痕生命周期，不提供尚未实现的批量清理按钮

浏览器菜单“浏览器设置”和 Settings Home 必须打开同一页面、同一 store，不复制状态。

## 自问自答

### 为什么播放器入口不能先放出来？

设置项代表真实功能 owner。播放器尚未建立会话、Activity 和持久化设置时展示入口只会形成空页面，违反本轮清理占位能力的目标。

### 浏览器设置是否应该包含所有 WebView setting？

不应该。只暴露用户能够理解且具备稳定产品语义的选项；调试、缓存细节和内部 WebView flags 不成为设置。

## 预计文件

- `KiyoriShellPages.kt`、`KiyoriShellState.kt`、`KiyoriAppShell.kt`
- 新的 Kiyori settings 页面与 browser settings store/adapter
- 浏览器菜单 callback 和 Host 路由
- Shell navigation 与 settings store 测试
- `README.md`、`CONTEXT.md`

## 验收

- Settings Home 只包含真实入口，手机与平板布局稳定
- 浏览器设置从 Settings Home 和浏览器菜单进入同一页面并共享状态
- 改变引擎、默认 Profile 和 UA 后新窗口行为与 UI 同步
- Back 不丢失原 Settings Home 或 Browser Home 来源
- Debug APK、提交和推送门禁通过
