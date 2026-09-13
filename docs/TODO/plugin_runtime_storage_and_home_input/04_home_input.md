---
status: superseded
superseded_by: ../home_pager_gesture_consistency/index.md
---

# AI 首页输入接管（历史切片）

AI Home 与三页首页继续共享同一个 `PagerState` 和 fling behavior。覆盖 AI Home 的水平滚动层对触摸判定隐藏“动画仍在滚动”状态，因此按下不会在 touch slop 之前取消 fling；一旦形成真实水平拖动，滚动仍委托给原 `PagerState`，可立即反向接管。

真机验收必须覆盖：快速滑到 AI Home 后立即点击输入框、立即点击工具按钮、立即反向拖动，以及聊天内容内部的水平手势仲裁。

该历史实现于 2026-07-23 完成源码和构建验证，但当时没有完成目标设备吸附语义验证。

## 2026-08-18 目标设备纠正

目标设备中部慢拖、短促 flick 和 Compose Foundation 1.11.4 源码核对证明：普通
`scrollable` 虽然委托同一 `PagerState` 和 fling 对象，但没有真正 HorizontalPager 的
`dragDirectionDetector`、本次 up/down 差值和完整 Pager 输入节点。默认吸附会读取零值或上一轮
原生 Pager 手势遗留的数据，因此该历史切片没有实现三页一致手感。

当前根因修复、实现阶段和验收矩阵统一由
[三页首页横向手势一致性修复](../home_pager_gesture_consistency/index.md) 管理。本文只保留历史问题和
设计动机，不再作为当前完成状态。

2026-08-18 的后续实现已经删除旧 AI 默认 fling 接线：永久 AI 根保留唯一 `PagerState`，
但每次手势由新的公开 API bridge 建立独立会话并执行统一产品吸附合同；宽表格、代码、公式和
内嵌预览通过多 owner 横向占用状态使首页 bridge 让位。修复后目标设备验收仍由新专项 TODO
维护。

[SUPERSEDED]
