---
fork: https://github.com/Kiyori-CN/Kiyori
status: verification_pending
baseline: c036a03e
date: 2026-07-29
---

# 设置 UI、主题边界与现代化配色统一

> 当前设置首页信息架构与子页面视觉收口已由
> [`kiyori_settings_information_architecture`](../kiyori_settings_information_architecture/index.md)
> 取代。本文件保留 2026-07-29 主题统一阶段的历史范围与设备验收记录；其中旧的首页入口数量、
> 语音根页名称和首页色板不再是当前合同。

## 目标

本阶段在上一轮设置页信息架构拆分基础上，取消用户颜色对 Kiyori 全应用的控制，保留固定
Kiyori 浅色、深色和跟随系统三种主题模式。AI 对话继续允许背景、消息、头像、聊天头部和输入区
等局部个性化，但这些状态不能改变设置页、应用壳、工具页或浏览器色域。

设置首页、账号与连接、AI 助手、文本转语音、语音转文本、界面定制、数据备份与同步，以及浏览器、下载器和
播放器设置共用同一套浅深色视觉 token。设置入口使用有明确语义的蓝、绿、紫、橙、红、青和粉色
图标，不使用随机彩虹色，也不把大面积高饱和颜色作为页面背景。

## 唯一状态所有者

| 视觉域 | 唯一状态所有者 | 用户可配置内容 | 明确不允许 |
| --- | --- | --- | --- |
| Kiyori 应用主题 | `UserPreferencesManager.themeMode` 与 `useSystemTheme` | 跟随系统、浅色、深色 | 自定义全局 primary、secondary、on-color、AppBar 颜色 |
| 设置视觉 | `KiyoriSettingsTheme` 与固定语义 token | 不提供用户颜色入口，只跟随应用浅深模式 | 读取角色卡颜色、聊天气泡颜色或旧全局颜色 |
| 浏览器保护色域 | `KiyoriBrowserTheme` | 不提供用户颜色入口，只跟随应用浅深模式 | 使用应用强调色污染浏览器 chrome |
| AI 对话外观 | 现有 AI 外观 preference 与角色卡、群组绑定 | 背景、气泡、文本、头像、聊天头部、输入区、局部玻璃效果 | 改变设置页、应用壳、工具页、状态栏或应用主题模式 |

## 设计文档

- [主题状态与个性化边界](1_theme_boundary.md)
- [设置视觉系统与语义配色](2_settings_visual_system.md)
- [实施顺序、反向检查与验收](3_implementation_and_validation.md)

## 串行里程碑

1. [DONE] 固定应用主题并清理旧全局颜色状态、解析、WebChat 字段和界面入口
2. [DONE] 把背景媒体从应用根主题迁入 AI 对话局部背景层
3. [DONE] 建立设置浅深色 token、语义图标色阶和统一共享组件
4. [DONE] 统一 Settings Home、拆分设置根、语音页和关键子页的颜色与结构
5. [DONE] 更新测试、`CONTEXT.md`、`README.md`、正式决策与架构文档
6. [DONE] 执行定向 JVM、Kotlin 编译、formal readiness、差异检查和 Debug APK 核验
7. [PENDING] 在目标设备验收浅色、深色、系统切换、长表单、底部面板和角色外观切换

## 非目标

- 不重构项目根目录或建立第二套设置路由
- 不复制任何 preference、repository、WebSession、播放器或下载器 owner
- 不修改设置首页“小程序管理”的空动作约束
- 不新增动态取色、壁纸动态色或用户自定义应用主色
- 不提交、不推送、不安装 APK、不操作设备
