# 角色卡和提示词页面与环境识别修复

状态：研究完成，进入本轮实现与自动化验证；真机视觉、触摸和首次 Ubuntu 初始化仍需设备验收。

## 目标与边界

- 将“设置 - AI 助手”的用户可见标题统一为“角色卡和提示词”，并让角色卡、标签、群组管理子页面继续共享同一标题 owner。
- 把角色卡、标签、群组切换改为紧凑的 segmented control：使用中性表面承载整行，选中项使用蓝色语义容器和图标/文字，不再暴露刺眼的白底 `PrimaryTabRow`。
- 修复角色卡与群组头像裁剪页的系统 ActionBar 缺失问题，确保取消（返回）和确认（完成）操作可见且可点击。
- 让裁剪选框外保留完整原图，仅叠加半透明暗色遮罩；选框内保持原图亮度，帮助用户判断头像构图。
- 复核终端环境自动安装和识别：保持一个批量 hidden probe、一个可见安装会话、逐步遇错停止和明确 `UNKNOWN` 语义；验证 Python/Node/uv/rust 的实际路径与安装后 shell 激活一致，并保证 `pnpm` 安装先具备 Node 24 与 npm 能力。

非目标：不更换角色卡/群组数据格式、头像存储协议、Cropper 依赖、ToolPkg 环境变量协议、Ubuntu rootfs 路径、AIDL、namespace 或现有导航 route；不增加静默直连、第二终端、第二探针或回退状态。

## 研究结论

1. AI 助手设置入口使用 `kiyori_ai_settings_prompts_roles`，旧导航枚举使用的 `screen_title_model_prompts_settings` 改为引用该资源，避免两个标题来源继续漂移。
2. `CropImageActivity` 的返回/完成菜单依赖 `supportActionBar`。Kiyori 应用主题是 `Theme.MaterialComponents.DayNight.NoActionBar`，库 Activity 未被覆盖主题时 `supportActionBar` 为空，因此现有 `toolbar*` 和 `activityMenu*` 参数不会让按钮出现。
3. CanHub 4.5.0 的 `CropOverlayView` 使用 `CropImageOptions.backgroundColor` 绘制选框外遮罩；传入不透明 surface 色会完全盖住选框外图片。半透明黑色才符合“完整图片 + 淡化”的要求。
4. 环境配置页已具备单次结构化探针和显式规范目录检查。本轮只补齐可回归的首帧路由/安装后识别合同，禁止把 hidden 执行失败改报成“未安装”。

## 实施方案

1. 更新 `strings.xml` 的标题值和描述顺序；新增/修改 Settings 页面源码契约，确认所有入口只引用该资源。
2. 在 `ModelPromptsSettingsScreen.kt` 用 `Surface + Row + Tab` 实现 42dp segmented control，选中项使用 `primaryContainer`/`onPrimaryContainer`，未选中项透明，保留三项原有顺序、状态和触摸目标。
3. 在 `values/themes.xml`、`values-night/themes.xml` 增加带 ActionBar 的 Cropper 专用主题，并在 Manifest 对库 Activity 做精确 theme override；裁剪配置使用半透明 `backgroundColor`、明确的完成标题和高对比度工具栏颜色，角色卡与群组两条入口保持一致。
4. 扩展 UI/源码契约测试，覆盖标题值、segmented control 结构、Cropper theme override、半透明遮罩和双入口配置；扩展 Terminal 环境测试覆盖单次 probe、规范 PATH、安装后激活顺序和失败不误报。
5. 依次运行定向测试、`git diff --check`、`check_formal_readiness.py --repository . --require-main`、必要的父/子模块检查和串行 `:app:assembleDebug --no-daemon --console=plain`，核验 Debug APK 身份、签名与 16 KiB 对齐。
6. 审计最终工作树后提交全部当前允许交付的父仓库/子模块改动并推送各自 `main`，独立核对本地、tracking 与远端 ref；设备未连接时保留 `verification_pending`。

## 验收矩阵

| 项目 | 自动证据 | 设备证据 |
| --- | --- | --- |
| 标题与切换栏 | 资源/源码契约测试、Kotlin 编译 | 设置入口、子页面、浅深主题视觉 |
| 头像裁剪 | Manifest/theme/config 契约测试、APK 资源合并 | 选择图片后返回/完成按钮、拖动缩放、选框外淡化 |
| 环境安装识别 | Terminal 环境探针测试、父仓库定向 JVM、Debug 构建 | 首次 rootfs、混合已安装状态、安装后重进页面和实际命令 |
