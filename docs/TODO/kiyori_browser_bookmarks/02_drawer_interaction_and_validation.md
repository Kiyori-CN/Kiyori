# 抽屉交互与验证

## 页面结构

```text
书签子抽屉/
├── 拖动把手
├── 标题行：动态标题 + 更多
├── 搜索框
├── 可点击路径
└── 自适应列表
    ├── 文件夹行
    └── 书签行
```

标题行不提供返回键。返回上层通过路径完成；系统 Back 在文件夹内先回父级，搜索中先清空搜索，根目录再关闭抽屉。

## 新增书签

- 加书签图标始终使用 `ic_kiyori_tool_bookmark_add`
- 未收藏显示“加书签”，点击后先关闭主菜单，再打开“新增书签”对话框
- 已收藏显示“移除书签”，点击后删除当前 URL 的书签，不显示新增对话框
- 名称取当前页面标题，网址取归一化当前 URL，图标取同源 `/favicon.ico`，文件夹默认根目录
- 四行均可编辑；文件夹行既能选择现有层级，也能输入新文件夹名称
- 文件夹选择第一行固定显示 `/`；其后按书签树中每级手动顺序做深度优先展开，只显示当前文件夹名，并按层级逐级缩进

## 管理抽屉

- 根目录标题为“我的书签”，进入文件夹后显示该文件夹名称
- 搜索提示固定为“搜索书签标题、链接”
- 路径从“根目录”开始，每段都可点击跳转
- 列表使用 `LazyColumn.fillMaxSize()`，由 `WebSessionBrowserBottomDrawer` 的可见 viewport 约束，不设置独立固定高度
- 半展开、全展开、IME 出现和导航栏 inset 下都保留底部 content padding

## 菜单

顶部更多菜单从右上角锚定，严格显示用户指定的九行文字。文件夹和书签长按菜单分别显示用户指定动作；不会把后台打开、新窗口打开或移动书签改成复制链接提示。

书签与下载的锚定菜单统一使用 `40dp` 紧凑选项行、圆角表面和抽屉内遮罩。菜单跟随实际触发按钮或列表行定位，不使用相对屏幕顶部的固定偏移；居中编辑、排序、确认和操作弹窗统一使用全屏遮罩，避免半抽屉与全抽屉状态下位置脱节。拖拽模式禁用长按菜单，避免生成只有遮罩而没有可见菜单的阻塞状态。

负一屏书签卡只打开这一份书签抽屉。书签列表和文件夹继续观察 `WebSessionHistoryStore`，打开书签继续交给共享 Browser Runtime，不创建独立页面状态或数据副本。

排序方式提供手动、标题和时间。拖拽排序进入明确排序模式，并复用项目现有 `sh.calvin.reorderable` 手势实现同类条目的真实拖动；搜索结果和非手动排序状态不接受拖拽写入。

## 验收

- `git diff --check`
- 定向 JVM 测试覆盖书签策略和旧数据兼容
- 项目 `.venv` 运行正式开发准备检查
- `./gradlew :app:assembleDebug --no-daemon --console=plain`
- 核验 `app/build/outputs/apk/debug/app-debug.apk` 的存在、大小、修改时间和 SHA-256

## 本地验证结果

- [DONE] `git diff --check`
- [DONE] `WebSessionBookmarkPolicyTest`：7 个用例通过
- [DONE] `KiyoriShellStateTest`：39 个用例通过
- [DONE] `python -B ci/script/check_formal_readiness.py --repository . --require-main`
- [DONE] `./gradlew :app:assembleDebug --no-daemon --console=plain`
- [DONE] Debug APK：`449493413` bytes，`2026-07-27 20:53:04 +08:00`，SHA-256 `2B03E7A94718BE30A34FBCF5A62101DA10177275D23DA96CA141A40742A4C9FF`

真机仍需验证：主菜单文字切换、四行输入和软键盘、长按菜单锚点、半/全抽屉滑到底、深层路径、旋转恢复、favicon 加载、后台/新窗口打开，以及普通/秘密空间切换。

[DONE]
