# AI 首页输入接管

AI Home 与三页首页继续共享同一个 `PagerState` 和 fling behavior。覆盖 AI Home 的水平滚动层对触摸判定隐藏“动画仍在滚动”状态，因此按下不会在 touch slop 之前取消 fling；一旦形成真实水平拖动，滚动仍委托给原 `PagerState`，可立即反向接管。

真机验收必须覆盖：快速滑到 AI Home 后立即点击输入框、立即点击工具按钮、立即反向拖动，以及聊天内容内部的水平手势仲裁。

源码实现已完成，上述触摸场景仍为 `verification_pending`。

[DONE]
