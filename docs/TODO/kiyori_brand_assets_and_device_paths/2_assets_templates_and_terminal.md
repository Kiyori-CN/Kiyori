# 图片、WebChat、模板与终端

## 图片与 WebChat

- 保留现有 Kiyori Launcher、通知和应用图标资源
- WebChat 页面标题、application name 和 favicon 改为 Kiyori
- 默认角色改用现有 Kiyori 应用图标；旧 Operit 角色图片不再打包
- WebChat package name、本地 token key 和主题字体 key 属于内部标识，不改名

## 工作区模板

用户从 Kiyori 新建的 Web、Node.js、TypeScript、Python、Go、Java、Android、Flutter 和办公文档模板，其欢迎语、README、页面标题、应用显示名和“由某应用创建”的描述使用 Kiyori。

以下内容不因品牌清理改名：

- `.operit/config.json`
- 模板源码目录、Java/Kotlin package、构建产物名和既有项目 identifier
- 模板文件名与目录名

## 预置 ToolPkg

- QQ Bot、侧栏记账本和 Windows 控制等直接描述当前宿主的文案使用 Kiyori
- `remote_operit` 中“当前应用”改称 Kiyori，远端 Operit、工具名、包名和环境变量继续保留
- Windows companion 的展示名使用 Kiyori PC Agent，`operit-pc-agent` 目录、批处理文件名、环境变量、互斥锁和存储 key 保持不变
- `operit_editor`、worldbook 的 Operit 格式说明和市场生态名称不做机械替换
- QQ Bot 既有 `dist/` metadata 包装器仍会进入 ToolPkg，展示文案与 TypeScript 主源码同步使用 Kiyori，入口和逻辑不变

## Terminal 与 Ubuntu

- DocumentsProvider 标题和摘要使用 `Kiyori Ubuntu`
- 环境配置界面的 `Operit Required` 改为 `Kiyori Required`
- 写入 Ubuntu 软件源文件的来源注释使用 `From Kiyori Settings`
- namespace、AIDL、rootfs 目录、安装标记、chroot 临时文件、环境变量和 native 库名保持不变

## 预期结果

新创建的工作区、WebChat 和终端文件入口不再暴露旧宿主品牌，同时不扩大上游合并冲突到源码结构或兼容契约。

## 状态 [DONE]

WebChat、模板、预置 ToolPkg 与 Terminal/Ubuntu 显示层已完成最小替换；生成资产与 APK 内容在第三阶段验证。
