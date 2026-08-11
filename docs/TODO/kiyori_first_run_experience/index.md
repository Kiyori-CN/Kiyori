---
fork: https://github.com/Kiyori-CN/Kiyori
status: verification_pending
---

# Kiyori 完整首次启动体验

## 改造前状况

改造前的首次启动沿用 Operit 的“协议页 + 三张宣传页 + 重复欢迎页 + 基础权限 +
权限级别”六页结构。页面只做过 Kiyori 语义色适配，产品定位、授权范围、导航和启动时序仍以
Operit AI 为中心。

旧协议页同时承担首次确认与设置内查看，使用固定五秒倒计时；权限页只覆盖文件、悬浮窗、电池和
位置，却把四项全部描述为正常运行所必需。通知权限可能在协议尚未确认时弹出，协议与权限完成
回调还会重复调用 `setContent`。协议资源把仓库实际的 GPL-3.0-or-later 错写成 LGPLv3。

## 目标

- 用六页中文流程准确表达 Kiyori 的 AI 浏览器定位，以及浏览、下载、播放、AI 对话、文件管理、
  本地工具和扩展生态
- 建立简洁、现代、响应式的统一视觉，每页只承担一个任务并只保留一个主操作
- 用户协议与隐私政策保持两个独立查看入口，但不设置打开、阅读进度或滚动到底门槛
- 权限页平铺当前全部权限和设备能力，以真实状态和选择框让用户决定本次需要处理的项目
- 完成用户所选授权后直接进入现有 Software Home，不再保留独立完成页
- 保留 Android 12+ 透明系统启动窗口、单次 Compose 挂载和首帧后初始化
- Kiyori 尚未发布，旧 Operit 页面语义、旧状态枚举、旧资源和旧测试合同直接删除
- 当前只维护中文默认资源，其他语言在六页中文完成视觉验收后统一处理

## 流程

1. 欢迎使用 Kiyori
2. 从发现到播放，内容自然流动
3. 让 AI 读懂上下文，也能继续行动
4. 文件、终端与扩展能力，一处展开
5. 用户协议与隐私政策
6. 权限设置并进入 Kiyori

第 1 页使用四张产品域卡片建立完整定位；第 2 至第 4 页使用主题导语和 2×2 能力卡片继续展开。
页面不把当前只有产品域入口的模块写成已经拥有完整详情页，但会准确展示 Kiyori 的浏览、内容、
AI、语音、连接、文件、终端、工具箱、小程序和日志等工作空间方向。

顶部始终显示 Kiyori、进度条和单行步骤文案。第 1 页系统返回退出；第 2 至第 6 页可以返回上
一步。打开协议正文时隐藏首启顶部，只显示文档自己的标题和返回入口。

六页共用一个 `HorizontalPager`。第 1 至第 4 页可左右滑动，单次手势最多移动一页；第 5 页
在用户明确同意协议前只允许向右返回，不能向左滑入权限页，确认后恢复双向滑动；第 6 页可向右
返回协议页。授权队列处理中同时锁定 Pager 和顶部返回，系统设置返回后刷新真实权限状态再继续。

## 授权范围

### 运行时权限

- 通知
- 媒体音频与视频
- 相机
- 麦克风
- 精确与粗略位置
- 蓝牙扫描与连接
- 电话拨号
- 短信发送、读取与接收
- Android 12 及以下读取外部存储；Android 9 及以下写入外部存储

### 特殊访问

- 所有文件访问
- 在其他应用上层显示
- 修改系统设置
- 使用情况访问
- 安装未知来源应用
- 忽略电池优化
- 通知读取
- 系统默认助手

### 高级设备能力

- Kiyori 无障碍支持 / Kiyori UI 自动化服务
- Shizuku / ADB
- Root

当前工作流调度使用 WorkManager，普通设备闹钟使用 `ACTION_SET_ALARM`，首启不声明或请求
`SCHEDULE_EXACT_ALARM`。精确 `AlarmManager` 调度没有当前产品消费者，未来需要时必须另立
功能合同和目标 OEM 验收范围。

屏幕捕获等 Android 不提供长期预授权的能力只在清单中说明“使用时由系统确认”，不得保存虚假的
永久授权状态。

## 作用域

```text
app/src/main/java/com/kiyori/integration/operit/onboarding/
app/src/main/java/com/kiyori/app/startup/
app/src/main/java/com/ai/assistance/operit/ui/features/agreement/screens/
app/src/main/java/com/ai/assistance/operit/ui/main/MainActivity.kt
app/src/main/java/com/ai/assistance/operit/ui/main/screens/OperitScreens.kt
app/src/main/assets/accessibility.apk
app/src/main/assets/accessibility_version.txt
app/src/main/res/values*/
config/architecture/
ci/script/check_architecture_boundaries.py
ci/test/test_architecture_boundaries.py
CONTEXT.md
README.md
docs/doc-src/
docs/TODO/
```

不改变数据库、备份、ToolPkg、MCP、OAuth、Intent、AIDL、JNI 或其他稳定兼容标识。不提交、不推送，
不安装应用或操作设备。

## 验收

- 全新安装按六页新顺序进入 Software Home，生产 UI 不出现旧 Operit 首启文案
- 第 1 至第 4 页分别覆盖产品总览、浏览器与媒体、AI 协作、文件与扩展，信息不重复
- 每页底部只有一个主按钮，返回使用顶部入口，不再堆叠跳过、刷新、继续下一项和摘要按钮
- 第 5 页只由一个同意复选框控制主按钮，不要求打开或滚动两份协议
- 第 1 至第 6 页的主按钮、顶部返回、左右滑动、步骤进度和持久化当前页指向同一个 Pager 状态
- 单次滑动不能跨页；协议未同意不能滑入权限页；授权处理中不能切换页面
- 设置内协议入口继续提供两份只读文档
- 第 6 页不显示权限分区或权限等级，所有项目使用统一卡片和选择框
- 已授权、不适用和使用时确认状态真实显示；只对用户选中的可操作项目发起处理
- 没有选择权限时可以直接进入；完成所选授权后直接进入，不存在 `READY` 页面
- 首启中断后恢复当前步骤；系统设置往返后刷新状态和继续处理用户选择
- 协议版本、首启步骤和完成版本拥有唯一持久 owner
- `MainActivity.setContent` 只有一个启动挂载入口，通知权限不在协议前自动请求
- 定向 JVM、首启正反向架构测试、正式开发准备、差异检查和 Debug APK 构建通过
- 真机视觉、OEM 系统设置、授权拒绝和进程回收路径保持 `verification_pending`
- 系统安装页、应用列表和无障碍设置中只显示 Kiyori 品牌；内部 provider 包名和 AIDL action
  保持现有 IPC 合同
